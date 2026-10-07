package com.senne.starktech.client.shield;

import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.shield.DomeGeneratorBlock;
import com.senne.starktech.shield.LaserEmitterBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Het zoemen van de rayshields (jouw wens, 7 oktober: "geef de rayshields de Star Wars-geluiden").
 *
 * Zolang een laserzender of een koepelgenerator in de buurt aan staat, hoor je een brommende lus
 * (het geluid {@code rayshield_hum}, zie tools/generate_sounds.py). Gaat hij uit, dan sterft het
 * zoemen in een paar tikken weg.
 *
 * Dit gebeurt alleen in jouw spel: de server weet niets van dit geluid. Elke seconde kijken we in de
 * stukjes wereld (16x16x16) rond je of er een zender of generator in zit. Dat gaat snel: elk stukje
 * weet welke soorten blokken erin zitten, dus de meeste slaan we meteen over (net als
 * SecurityDevices op de server).
 */
public final class RayshieldHum {
    private RayshieldHum() {}

    /** Zo vaak (ticks) kijken we of er nieuwe schilden in de buurt aan staan. */
    private static final int SCAN_INTERVAL = 20;
    /** Zover (blokken) zoeken we; verder weg hoor je het toch niet. */
    private static final int RANGE = 24;
    /** Hooguit zoveel zoem-lussen tegelijk (een muur vol zenders hoeft niet twintig keer te zoemen). */
    private static final int MAX_HUMS = 6;
    /** Twee zenders dichter dan dit bij elkaar delen één zoem-lus (een stapel zenders). */
    private static final int SHARE_DISTANCE = 3;

    private static final List<Hum> HUMS = new ArrayList<>();

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        HUMS.removeIf(hum -> hum.isStopped() || !mc.getSoundManager().isActive(hum) && hum.started);
        if (player == null || level == null || level.getGameTime() % SCAN_INTERVAL != 0) {
            return;
        }
        BlockPos center = player.blockPosition();
        for (BlockPos pos : findShields(level, center)) {
            if (HUMS.size() >= MAX_HUMS) {
                return;
            }
            boolean covered = HUMS.stream().anyMatch(hum -> hum.pos.distManhattan(pos) <= SHARE_DISTANCE);
            if (!covered) {
                Hum hum = new Hum(level, pos, level.getBlockState(pos).is(ModBlocks.DOME_GENERATOR.get()));
                HUMS.add(hum);
                mc.getSoundManager().play(hum);
            }
        }
    }

    /** Staat hier een rayshield aan (een zender met een wand, of een generator met een koepel)? */
    static boolean isActiveShield(BlockState state) {
        return state.is(ModBlocks.LASER_EMITTER.get()) && state.getValue(LaserEmitterBlock.POWERED)
                || state.is(ModBlocks.DOME_GENERATOR.get()) && state.getValue(DomeGeneratorBlock.POWERED);
    }

    /** Alle schilden die aan staan in de buurt, dichtstbij eerst. */
    private static List<BlockPos> findShields(ClientLevel level, BlockPos center) {
        List<BlockPos> found = new ArrayList<>();
        int minCx = SectionPos.blockToSectionCoord(center.getX() - RANGE);
        int maxCx = SectionPos.blockToSectionCoord(center.getX() + RANGE);
        int minCz = SectionPos.blockToSectionCoord(center.getZ() - RANGE);
        int maxCz = SectionPos.blockToSectionCoord(center.getZ() + RANGE);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    if (baseY + 16 < center.getY() - RANGE || baseY > center.getY() + RANGE
                            || section.hasOnlyAir() || !section.maybeHas(RayshieldHum::isActiveShield)) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (isActiveShield(section.getBlockState(x, y, z))) {
                                    BlockPos pos = new BlockPos(SectionPos.sectionToBlockCoord(cx) + x, baseY + y,
                                            SectionPos.sectionToBlockCoord(cz) + z);
                                    if (pos.closerThan(center, RANGE)) {
                                        found.add(pos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        found.sort((a, b) -> Double.compare(a.distSqr(center), b.distSqr(center)));
        return found;
    }

    /** Uitloggen of een andere wereld: alles stil. */
    public static void clear() {
        for (Hum hum : HUMS) {
            Minecraft.getInstance().getSoundManager().stop(hum);
        }
        HUMS.clear();
    }

    /**
     * Eén zoem-lus op een vaste plek. Elke tick kijkt hij of het schild er nog staat en aan is;
     * zo niet, dan wordt hij snel zachter en stopt hij.
     */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final BlockPos pos;
        private final float fullVolume;
        /** Is hij al eens gaan spelen? (Daarvoor zegt de geluidsmanager nog "niet actief".) */
        private boolean started;

        Hum(ClientLevel level, BlockPos pos, boolean dome) {
            super(ModSounds.RAYSHIELD_HUM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.level = level;
            this.pos = pos.immutable();
            this.looping = true;
            this.delay = 0;
            // Een koepel is groter: dieper en harder (verder te horen).
            this.fullVolume = dome ? 1.0f : 0.45f;
            this.pitch = dome ? 0.7f : 1.0f;
            this.volume = 0.01f;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
        }

        @Override
        public void tick() {
            started = true;
            LocalPlayer player = Minecraft.getInstance().player;
            boolean far = player == null || !player.blockPosition().closerThan(pos, RANGE + 8);
            if (Minecraft.getInstance().level != level || far || !isActiveShield(level.getBlockState(pos))) {
                volume *= 0.6f;
                if (volume < 0.01f) {
                    stop();
                }
                return;
            }
            // Zacht beginnen (de "vwoem" van het aangaan speelt al), dan op volle sterkte.
            volume = Math.min(fullVolume, volume + fullVolume * 0.08f);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
