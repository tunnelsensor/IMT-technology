package com.senne.starktech.client.shield;

import com.senne.starktech.client.EdithClientConfig;
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
 * Zolang een laserzender of een koepelgenerator in de buurt aan staat, zoemt hij af en toe: een paar
 * seconden een zachte brom (het geluid {@code rayshield_hum}, zie tools/generate_sounds.py), dan een
 * tijdje stil (jouw wens, 7 oktober: "zachter en minder vaak"; eerst zoemde hij aan één stuk door).
 * Hoe hard en hoe lang de stilte duurt, stel je in in config/starktech-client.toml
 * (rayshieldHumVolume, rayshieldHumPause). Gaat het schild uit, dan sterft het zoemen meteen weg.
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
    /** Hooguit zoveel schilden zoemen tegelijk (een muur vol zenders hoeft niet twintig keer te zoemen). */
    private static final int MAX_HUMS = 4;
    /**
     * Zenders dichter dan dit bij elkaar (blokken, opgeteld over x, y en z) delen één zoem: een
     * laserpoort met zenders aan twee kanten zoemt dus als één poort, niet twee keer zo vaak.
     */
    private static final int SHARE_DISTANCE = 8;

    /** Zo lang (ticks) zoemt een schild per keer: 3 seconden, zacht aan en zacht uit. */
    private static final int HUM_TICKS = 60;
    private static final int FADE_IN = 15;
    private static final int FADE_OUT = 25;
    /** Hoe hard een laserwand zoemt, en een koepel (die is groter: dieper en verder te horen). */
    private static final float WALL_VOLUME = 0.18f;
    private static final float DOME_VOLUME = 0.35f;

    private static final List<Hum> HUMS = new ArrayList<>();

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        HUMS.removeIf(hum -> hum.isStopped() || !mc.getSoundManager().isActive(hum) && hum.started);
        if (player == null || level == null || level.getGameTime() % SCAN_INTERVAL != 0) {
            return;
        }
        if (EdithClientConfig.rayshieldHumVolume() <= 0) {
            clear(); // het zoemen staat uit in de instellingen
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
     * Hoe hard het zoemen is, {@code tick} ticks na het begin van een zoem: zacht omhoog, even vol,
     * zacht weer weg, en daarna stil (0) tot de volgende zoem.
     */
    static float envelope(int tick) {
        if (tick < 0 || tick >= HUM_TICKS) {
            return 0f;
        }
        float in = Math.min(1f, tick / (float) FADE_IN);
        float out = Math.min(1f, (HUM_TICKS - tick) / (float) FADE_OUT);
        float s = Math.min(in, out);
        return s * s * (3 - 2 * s); // "smoothstep": geen harde hoek bij het begin en einde
    }

    /**
     * Het zoemen van één schild op een vaste plek. Het geluid zelf loopt als lus door, maar meestal
     * op volume 0: alleen tijdens een zoem (HUM_TICKS lang) hoor je het, daarna is het een tijdje
     * stil (rayshieldHumPause seconden, elke keer iets anders, zodat twee schilden niet tegelijk
     * zoemen). Elke tick kijkt hij of het schild er nog staat en aan is; zo niet, dan sterft hij weg.
     */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final BlockPos pos;
        private final float baseVolume;
        /** Is hij al eens gaan spelen? (Daarvoor zegt de geluidsmanager nog "niet actief".) */
        private boolean started;
        /** Ticks sinds het begin van de huidige zoem, en hoe lang de stilte erna duurt. */
        private int phase;
        private int pauseTicks;

        Hum(ClientLevel level, BlockPos pos, boolean dome) {
            super(ModSounds.RAYSHIELD_HUM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.level = level;
            this.pos = pos.immutable();
            this.looping = true;
            this.delay = 0;
            this.baseVolume = dome ? DOME_VOLUME : WALL_VOLUME;
            this.pitch = dome ? 0.7f : 1.0f;
            this.volume = 0f;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
            this.pauseTicks = nextPause();
        }

        /** Hoe lang de volgende stilte duurt: rond de ingestelde pauze, tussen 70% en 130%. */
        private int nextPause() {
            return (int) (EdithClientConfig.rayshieldHumPause() * 20 * (0.7f + 0.6f * random.nextFloat()));
        }

        @Override
        public void tick() {
            started = true;
            LocalPlayer player = Minecraft.getInstance().player;
            boolean far = player == null || !player.blockPosition().closerThan(pos, RANGE + 8);
            if (Minecraft.getInstance().level != level || far || !isActiveShield(level.getBlockState(pos))) {
                volume *= 0.6f;
                if (volume < 0.005f) {
                    stop();
                }
                return;
            }
            // Eerst een zoem (vlak na de "vwoem" van het aangaan), dan stilte, dan weer een zoem.
            phase++;
            if (phase >= HUM_TICKS + pauseTicks) {
                phase = 0;
                pauseTicks = nextPause();
            }
            volume = baseVolume * (float) EdithClientConfig.rayshieldHumVolume() * envelope(phase);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
