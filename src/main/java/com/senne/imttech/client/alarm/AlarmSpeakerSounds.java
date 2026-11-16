package com.senne.imttech.client.alarm;

import com.senne.imttech.alarm.AlarmSpeakerBlock;
import com.senne.imttech.alarm.AlarmSpeakerBlockEntity;
import com.senne.imttech.client.ImtClientConfig;
import com.senne.imttech.config.ImtConfig;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

/**
 * De Alarm Speakers die je in je spel hoort (hoofdstuk 126).
 *
 * Elke halve seconde kijken we in de stukjes wereld rond je of er een speaker staat (dat gaat snel: elk stukje weet welke
 * soorten blokken erin zitten, net als bij het zoemen van de rayshields).
 * <ul>
 *   <li>Een speaker met een eigen geluid dat je nog niet hebt, binnen zijn bereik plus wat marge: dan vraagt je spel het
 *       alvast op (zie AlarmSoundCache), zodat het klaarstaat als het alarm afgaat. Hooguit de acht dichtstbijzijnde
 *       verschillende geluiden.</li>
 *   <li>De speakers die afgaan en die je kunt horen: daarvan klinken de {@link #MAX_PLAYING} dichtstbijzijnde (Minecraft
 *       heeft maar een paar kanalen voor lange geluiden, en die deelt het met de muziek). Speakers met hetzelfde geluid vlak
 *       bij elkaar klinken als één. Loop je naar een andere speaker, dan neemt die het over van de verste.</li>
 * </ul>
 * Een geluid dat de geluidsmotor niet startte (alle geluiden of "Blocks" op 0, geen kanaal vrij), onthouden we niet: de
 * volgende keer proberen we het gewoon opnieuw. (Gevonden door de nalezing: anders bleef zo'n speaker stil tot je opnieuw
 * inlogde.)
 */
final class AlarmSpeakerSounds {
    private AlarmSpeakerSounds() {}

    /** Zo vaak (ticks) kijken we rond. */
    private static final int SCAN_INTERVAL = 10;
    private static final int MAX_PLAYING = 3;
    /** Speakers dichter dan dit bij elkaar (blokken) met hetzelfde geluid, klinken als één. */
    private static final int SHARE_DISTANCE = 6;
    /** Zo ver voor zijn bereik halen we het geluid van een speaker alvast op (blokken). */
    private static final int PREFETCH_MARGIN = 24;
    /** Van hooguit zoveel verschillende geluiden in de buurt houden we er een klaar (de cache houdt er 12). */
    private static final int PREFETCH_SOUNDS = 8;

    private static final List<AlarmSoundInstance> PLAYING = new ArrayList<>();

    /** Een speaker in de buurt. */
    private record Found(BlockPos pos, BlockState state, double distance) {}

    /** Een speaker die nu moet klinken: waar, welk geluid (of "" voor de sirene), en met welke instellingen. */
    private record Wanted(BlockPos pos, String id, @Nullable short[] clip, float volume, int range) {}

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        PLAYING.removeIf(sound -> {
            if (sound.isStopped()) {
                return true;
            }
            if (!minecraft.getSoundManager().isActive(sound)) {
                sound.end(); // de geluidsmotor speelt hem niet meer (bijvoorbeeld "Blocks" op 0): weg ermee
                return true;
            }
            return false;
        });
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || level.getGameTime() % SCAN_INTERVAL != 0) {
            return;
        }
        if (ImtClientConfig.alarmSpeakerVolume() <= 0 || minecraft.options.getSoundSourceVolume(SoundSource.MASTER) <= 0
                || minecraft.options.getSoundSourceVolume(SoundSource.BLOCKS) <= 0) {
            stopAll(); // stil gezet: niets starten (een geluid op volume 0 zou in de geluidsmotor blijven hangen)
            return;
        }
        boolean custom = ImtClientConfig.customAlarmSounds();
        List<Wanted> wanted = new ArrayList<>();
        Set<String> nearbySounds = new LinkedHashSet<>();
        for (Found found : find(level, player.blockPosition())) {
            if (!(level.getBlockEntity(found.pos()) instanceof AlarmSpeakerBlockEntity speaker)) {
                continue;
            }
            String id = custom ? speaker.soundId() : "";
            if (!id.isEmpty() && found.distance() <= speaker.range() + PREFETCH_MARGIN && nearbySounds.size() < PREFETCH_SOUNDS) {
                nearbySounds.add(id);
            }
            if (!found.state().getValue(AlarmSpeakerBlock.SOUNDING) || found.distance() > speaker.range()) {
                continue;
            }
            short[] clip = id.isEmpty() ? null : AlarmSoundCache.get(id);
            if (!id.isEmpty() && clip == null) {
                if (!AlarmSoundCache.failed(id)) {
                    continue; // het komt eraan: even wachten (meestal minder dan een seconde)
                }
                id = ""; // de server heeft het niet: de sirene
            }
            wanted.add(new Wanted(found.pos(), id, clip, speaker.volume(), speaker.range()));
        }
        // De geluiden van de dichtstbijzijnde speakers klaarzetten (de dichtste eerst), en ze vers houden in de cache.
        for (String id : nearbySounds) {
            AlarmSoundCache.keep(id);
        }
        play(level, choose(wanted));
    }

    /**
     * Welke speakers klinken: de dichtstbijzijnde eerst (de lijst is al op afstand), hooguit {@link #MAX_PLAYING}, en een
     * speaker vlak bij een gekozen speaker met hetzelfde geluid niet (die klinkt al mee).
     */
    private static List<Wanted> choose(List<Wanted> candidates) {
        List<Wanted> chosen = new ArrayList<>();
        for (Wanted candidate : candidates) {
            if (chosen.size() >= MAX_PLAYING) {
                break;
            }
            boolean shared = chosen.stream().anyMatch(other -> other.id().equals(candidate.id())
                    && other.pos().distManhattan(candidate.pos()) <= SHARE_DISTANCE);
            if (!shared) {
                chosen.add(candidate);
            }
        }
        return chosen;
    }

    /** Laat precies de gekozen speakers klinken: wat niet (meer) gekozen is of iets anders speelt, sterft weg. */
    private static void play(ClientLevel level, List<Wanted> chosen) {
        for (AlarmSoundInstance sound : PLAYING) {
            if (!sound.fading() && chosen.stream().noneMatch(want -> matches(sound, want))) {
                sound.fadeOut();
            }
        }
        for (Wanted want : chosen) {
            boolean playing = PLAYING.stream().anyMatch(sound -> !sound.fading() && matches(sound, want));
            if (playing) {
                continue;
            }
            AlarmSoundInstance sound = new AlarmSoundInstance(level, want.pos(), want.id(), want.clip(), want.volume(), want.range());
            Minecraft.getInstance().getSoundManager().play(sound);
            // Alleen onthouden als de geluidsmotor hem echt startte; anders proberen we het de volgende keer opnieuw.
            if (Minecraft.getInstance().getSoundManager().isActive(sound)) {
                PLAYING.add(sound);
            }
        }
    }

    private static boolean matches(AlarmSoundInstance sound, Wanted want) {
        return sound.pos.equals(want.pos()) && sound.soundId.equals(want.id()) && sound.range == want.range()
                && sound.speakerVolume == want.volume();
    }

    /** Alle speakers in de buurt (in geladen stukjes wereld), dichtstbij eerst. */
    private static List<Found> find(ClientLevel level, BlockPos center) {
        List<Found> found = new ArrayList<>();
        int reach = Math.max(8, Math.round(ImtConfig.Alarms.MAX_RANGE)) + PREFETCH_MARGIN;
        int minCx = SectionPos.blockToSectionCoord(center.getX() - reach);
        int maxCx = SectionPos.blockToSectionCoord(center.getX() + reach);
        int minCz = SectionPos.blockToSectionCoord(center.getZ() - reach);
        int maxCz = SectionPos.blockToSectionCoord(center.getZ() + reach);
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
                    if (baseY + 16 < center.getY() - reach || baseY > center.getY() + reach || section.hasOnlyAir()
                            || !section.maybeHas(state -> state.getBlock() instanceof AlarmSpeakerBlock)) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (state.getBlock() instanceof AlarmSpeakerBlock) {
                                    BlockPos pos = new BlockPos(SectionPos.sectionToBlockCoord(cx) + x, baseY + y,
                                            SectionPos.sectionToBlockCoord(cz) + z);
                                    double distance = Math.sqrt(pos.distToCenterSqr(center.getX() + 0.5, center.getY() + 0.5,
                                            center.getZ() + 0.5));
                                    if (distance <= reach) {
                                        found.add(new Found(pos, state, distance));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        found.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        return found;
    }

    /** Klinkt er nu een speaker op deze plek in je spel? (Voor de rondleiding.) */
    static boolean playingAt(BlockPos pos) {
        return PLAYING.stream().anyMatch(sound -> sound.pos.equals(pos) && !sound.fading());
    }

    static void stopAll() {
        for (AlarmSoundInstance sound : PLAYING) {
            sound.end();
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
        PLAYING.clear();
    }
}
