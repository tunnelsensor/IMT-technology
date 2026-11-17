package com.senne.imttech.client.alarm;

import com.senne.imttech.alarm.AlarmSpeakerBlock;
import com.senne.imttech.alarm.AlarmSpeakerBlockEntity;
import com.senne.imttech.alarm.audio.AlarmAudio;
import com.senne.imttech.client.ImtClientConfig;
import com.senne.imttech.client.voice.ImtSpeech;
import com.senne.imttech.config.ImtConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

/**
 * De Speakers die je in je spel hoort (hoofdstuk 126; sinds hoofdstuk 136 speelt een speaker wat een diagram zegt).
 *
 * Elke kwart seconde kijken we in de stukjes wereld rond je of er een speaker staat die iets speelt (dat gaat snel: elk
 * stukje weet welke soorten blokken erin zitten). Wat hij speelt, staat in zijn blok-entiteit (de server stuurt het mee).
 * <ul>
 *   <li><b>Een geluid</b> ({@code playsound}): uit /sounds van de kern (dat haalt je spel één keer op, zie AlarmSoundCache)
 *       of de ingebouwde sirene. Kom je later in de buurt, dan hoor je het vanaf het goede moment; maar de tijd die je spel
 *       nodig heeft om het geluid op te halen, telt niet mee (dan hoor je het gewoon iets later, en helemaal). Een geluid
 *       dat klaar is, begint niet opnieuw (ook niet als je spel even achterloopt).</li>
 *   <li><b>Een zin</b> ({@code playtext}): heb je de stem van I.M.T (G-menu, Systems), dan maakt je spel de zin zelf
 *       (ImtSpeech.synthesize) en klinkt hij uit de dichtstbijzijnde speaker die hem zegt, helemaal, één keer; zolang is het
 *       geluid van die speaker stil. Heb je de stem niet, of is hij na {@link #SPEECH_WAIT} ticks nog niet klaar, dan staat
 *       de zin in je chat ("Speaker 1: Alarm armed"; jouw keuze), één keer per omroep.</li>
 *   <li>Hooguit {@link #MAX_PLAYING} geluiden tegelijk (Minecraft heeft maar een paar kanalen voor lange geluiden, gedeeld
 *       met de muziek), de dichtstbijzijnde eerst. Speakers vlak bij elkaar die hetzelfde spelen, klinken als één.</li>
 * </ul>
 * Een geluid dat de geluidsmotor niet startte (alle geluiden of "Blocks" op 0, geen kanaal vrij), onthouden we niet: de
 * volgende keer proberen we het gewoon opnieuw.
 */
final class AlarmSpeakerSounds {
    private AlarmSpeakerSounds() {}

    /** Zo vaak (ticks) kijken we rond. */
    private static final int SCAN_INTERVAL = 5;
    private static final int MAX_PLAYING = 3;
    /** Speakers dichter dan dit bij elkaar (blokken) die hetzelfde spelen, klinken als één. */
    private static final int SHARE_DISTANCE = 6;
    /** Zo ver buiten zijn bereik zoeken we nog naar speakers (blokken). */
    private static final int MARGIN = 16;
    /** Zo lang (ticks) wachten we op de stem van I.M.T; daarna komt de zin in de chat (dan is hij al lang gezegd). */
    private static final int SPEECH_WAIT = 100;
    /** Zoveel gemaakte zinnen onthouden we (een omroep komt vaak terug). */
    private static final int SPEECH_CACHE = 16;
    /** Hooguit zoveel zinnen tegelijk laten maken (een lus met steeds een andere zin mag de stem niet laten vollopen). */
    private static final int MAX_MAKING = 3;
    /** Zoveel omroepen en geluiden onthouden we als "gedaan". */
    private static final int REMEMBER = 256;

    private static final List<AlarmSoundInstance> PLAYING = new ArrayList<>();
    /** De zinnen die nu klinken (los van PLAYING: een zin speelt helemaal uit, wat de server ook schat). */
    private static final List<AlarmSoundInstance> SPEAKING = new ArrayList<>();
    /** Zinnen die je spel al maakte (tekst naar monsters), het langst niet gebruikte gaat eruit. */
    private static final Map<String, short[]> SPEECH = new LinkedHashMap<>(SPEECH_CACHE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, short[]> eldest) {
            return size() > SPEECH_CACHE;
        }
    };
    /** Zinnen die de stem nu maakt, en zinnen waarvan dat niet lukte. */
    private static final Set<String> MAKING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();
    /** Omroepen die we zagen en nog niet lieten horen (of in de chat zetten), met wanneer we ze zagen. */
    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();
    /** Omroepen (tekst en begin) en geluiden (plek en volgnummer) die al gedaan zijn: nooit nog eens. */
    private static final Set<String> DONE = new LinkedHashSet<>();
    /** Wanneer (onze klok) we een geluid (plek en volgnummer) voor het eerst zagen: daar begint hij. */
    private static final Map<String, Long> FIRST_SEEN = new LinkedHashMap<>();
    /** Een eigen klok in ticks. */
    private static long clock;

    /** Een speaker in de buurt. */
    private record Found(BlockPos pos, BlockState state, double distance) {}

    /** Een geluid dat nu moet klinken. */
    private record Wanted(BlockPos pos, AlarmSoundInstance.Kind kind, int token, String what, @Nullable short[] clip, int start,
                          float volume, int range) {}

    /** Een omroep die we zagen: van welke speaker (de dichtstbijzijnde), wat, en wanneer we hem zagen. */
    private record Pending(String key, BlockPos pos, int token, String text, String name, float volume, int range, double distance,
                           long seen) {}

    static void tick() {
        clock++;
        Minecraft minecraft = Minecraft.getInstance();
        for (List<AlarmSoundInstance> list : List.of(PLAYING, SPEAKING)) {
            list.removeIf(sound -> {
                if (sound.isStopped()) {
                    return true;
                }
                if (!minecraft.getSoundManager().isActive(sound)) {
                    if (!sound.fading() && sound.kind != AlarmSoundInstance.Kind.SPEECH) {
                        remember(soundKey(sound.pos, sound.token)); // uitgespeeld: dit geluid niet nog eens beginnen
                    }
                    sound.end();
                    return true;
                }
                return false;
            });
        }
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || level.getGameTime() % SCAN_INTERVAL != 0) {
            return;
        }
        boolean muted = ImtClientConfig.alarmSpeakerVolume() <= 0 || minecraft.options.getSoundSourceVolume(SoundSource.MASTER) <= 0
                || minecraft.options.getSoundSourceVolume(SoundSource.BLOCKS) <= 0;
        if (muted) {
            stopAll(); // stil gezet: niets starten (een geluid op volume 0 zou in de geluidsmotor blijven hangen)
        }
        boolean custom = ImtClientConfig.customAlarmSounds();
        long now = level.getGameTime();
        List<Wanted> sounds = new ArrayList<>();
        for (Found found : find(level, player.blockPosition())) {
            if (!(level.getBlockEntity(found.pos()) instanceof AlarmSpeakerBlockEntity speaker) || found.distance() > speaker.range()) {
                continue;
            }
            if (speaker.speakingAt(now)) {
                notice(found, speaker);
            }
            if (muted || !speaker.playingAt(now)) {
                continue;
            }
            String key = soundKey(found.pos(), speaker.playToken());
            if (DONE.contains(key)) {
                continue;
            }
            long seen = FIRST_SEEN.computeIfAbsent(key, k -> now);
            if (speaker.playKind() == AlarmSpeakerBlockEntity.PLAY_SOUND && custom) {
                String id = speaker.playId();
                short[] clip = AlarmSoundCache.get(id);
                if (clip == null) {
                    AlarmSoundCache.keep(id); // het komt eraan (meestal binnen een seconde)
                    continue;
                }
                // Alleen hoe laat je het geluid ZAG telt (je kwam later in de buurt), niet hoe lang het ophalen duurde.
                int start = (int) Math.min(Integer.MAX_VALUE, Math.max(0, seen - speaker.playStart()) * AlarmAudio.RATE / 20);
                if (start >= clip.length) {
                    remember(key); // je kwam pas na het einde in de buurt
                    continue;
                }
                sounds.add(new Wanted(found.pos(), AlarmSoundInstance.Kind.SOUND, speaker.playToken(), "sound:" + id, clip, start,
                        speaker.volume(), speaker.range()));
            } else {
                // De ingebouwde sirene (ook als je de geluiden van anderen uit zette: customAlarmSounds = false).
                sounds.add(new Wanted(found.pos(), AlarmSoundInstance.Kind.DEFAULT, speaker.playToken(), "default", null, 0,
                        speaker.volume(), speaker.range()));
            }
        }
        speak(level, muted);
        if (!muted) {
            play(level, choose(sounds));
        }
        trim();
    }

    // ================================================================ Zinnen

    /** Een speaker zegt iets: onthoud de omroep (één per tekst en begin; de dichtstbijzijnde speaker wint). */
    private static void notice(Found found, AlarmSpeakerBlockEntity speaker) {
        String key = speaker.speechText() + "@" + speaker.speechStart();
        if (DONE.contains(key)) {
            return;
        }
        Pending old = PENDING.get(key);
        if (old != null && old.distance() <= found.distance()) {
            return;
        }
        String name = speaker.name().isEmpty() ? Component.translatable("block.imttech.alarm_speaker").getString() : speaker.name();
        PENDING.put(key, new Pending(key, found.pos(), speaker.speechToken(), speaker.speechText(), name, speaker.volume(),
                speaker.range(), found.distance(), old != null ? old.seen() : clock));
    }

    /**
     * De omroepen afhandelen: met de stem van I.M.T de zin laten maken en hem helemaal laten horen zodra hij klaar is;
     * zonder de stem (of als hij te lang op zich laat wachten) de zin in de chat.
     */
    private static void speak(ClientLevel level, boolean muted) {
        boolean voice = ImtSpeech.available();
        Iterator<Pending> it = PENDING.values().iterator();
        while (it.hasNext()) {
            Pending pending = it.next();
            if (!voice || muted && clock - pending.seen() > SPEECH_WAIT) {
                if (!voice) {
                    chat(pending);
                }
                remember(pending.key());
                it.remove();
                continue;
            }
            if (muted) {
                continue;
            }
            short[] clip = speechClip(pending.text());
            if (clip == null) {
                if (FAILED.contains(pending.text()) || clock - pending.seen() > SPEECH_WAIT) {
                    chat(pending); // de stem lukte niet (of niet op tijd): dan toch in de chat
                    remember(pending.key());
                    it.remove();
                }
                continue;
            }
            AlarmSoundInstance sound = new AlarmSoundInstance(level, pending.pos(), AlarmSoundInstance.Kind.SPEECH, pending.token(), clip,
                    0, pending.volume(), pending.range());
            Minecraft.getInstance().getSoundManager().play(sound);
            if (Minecraft.getInstance().getSoundManager().isActive(sound)) {
                SPEAKING.add(sound);
            }
            remember(pending.key());
            it.remove();
        }
    }

    /**
     * De monsters van deze zin, als je spel ze al maakte; anders vraagt het de stem van I.M.T erom (één keer, hooguit
     * {@link #MAX_MAKING} tegelijk) en is het er straks.
     */
    private static @Nullable short[] speechClip(String text) {
        short[] clip = SPEECH.get(text);
        if (clip != null || MAKING.contains(text) || FAILED.contains(text) || MAKING.size() >= MAX_MAKING) {
            return clip;
        }
        if (ImtSpeech.synthesize(text, samples -> {
            MAKING.remove(text);
            if (samples == null || samples.length == 0) {
                FAILED.add(text);
            } else {
                SPEECH.put(text, samples);
            }
        })) {
            MAKING.add(text);
        }
        return null;
    }

    /** De zin in je chat ("Speaker 1: Alarm armed"), één keer per omroep. */
    private static void chat(Pending pending) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("chat.imttech.speaker_says",
                    Component.literal(pending.name()).withStyle(ChatFormatting.AQUA), pending.text()), false);
        }
    }

    /** Klinkt er nu een zin uit de speaker op deze plek? Dan is zijn geluid even stil (zie AlarmSoundInstance). */
    static boolean speakingAt(BlockPos pos) {
        for (AlarmSoundInstance sound : SPEAKING) {
            if (sound.pos.equals(pos) && !sound.isStopped()) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ Geluiden

    private static String soundKey(BlockPos pos, int token) {
        return pos.asLong() + "#" + token;
    }

    private static void remember(String key) {
        DONE.add(key);
    }

    /** De lijsten klein houden. */
    private static void trim() {
        while (DONE.size() > REMEMBER) {
            DONE.remove(DONE.iterator().next());
        }
        while (FIRST_SEEN.size() > REMEMBER) {
            FIRST_SEEN.remove(FIRST_SEEN.keySet().iterator().next());
        }
        while (FAILED.size() > SPEECH_CACHE) {
            FAILED.remove(FAILED.iterator().next());
        }
    }

    /**
     * Wat klinkt: de dichtstbijzijnde eerst (de lijst is al op afstand), hooguit {@link #MAX_PLAYING}, en iets vlak bij iets
     * gekozens dat hetzelfde speelt niet (dat klinkt al mee).
     */
    private static List<Wanted> choose(List<Wanted> candidates) {
        List<Wanted> chosen = new ArrayList<>();
        for (Wanted candidate : candidates) {
            if (chosen.size() >= MAX_PLAYING) {
                break;
            }
            boolean shared = chosen.stream().anyMatch(other -> other.what().equals(candidate.what())
                    && other.pos().distManhattan(candidate.pos()) <= SHARE_DISTANCE);
            if (!shared) {
                chosen.add(candidate);
            }
        }
        return chosen;
    }

    /** Laat precies het gekozene klinken: wat niet (meer) gekozen is, sterft weg; wat al klinkt, loopt door. */
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
            AlarmSoundInstance sound = new AlarmSoundInstance(level, want.pos(), want.kind(), want.token(), want.clip(), want.start(),
                    want.volume(), want.range());
            Minecraft.getInstance().getSoundManager().play(sound);
            // Alleen onthouden als de geluidsmotor hem echt startte; anders proberen we het de volgende keer opnieuw.
            if (Minecraft.getInstance().getSoundManager().isActive(sound)) {
                PLAYING.add(sound);
            }
        }
    }

    private static boolean matches(AlarmSoundInstance sound, Wanted want) {
        return sound.pos.equals(want.pos()) && sound.kind == want.kind() && sound.token == want.token()
                && sound.range == want.range() && sound.speakerVolume == want.volume();
    }

    /** Alle speakers in de buurt (in geladen stukjes wereld), dichtstbij eerst. */
    private static List<Found> find(ClientLevel level, BlockPos center) {
        List<Found> found = new ArrayList<>();
        int reach = Math.max(8, Math.round(ImtConfig.Alarms.MAX_RANGE)) + MARGIN;
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
        return PLAYING.stream().anyMatch(sound -> sound.pos.equals(pos) && !sound.fading()) || speakingAt(pos);
    }

    static void stopAll() {
        for (List<AlarmSoundInstance> list : List.of(PLAYING, SPEAKING)) {
            for (AlarmSoundInstance sound : list) {
                sound.end();
                Minecraft.getInstance().getSoundManager().stop(sound);
            }
            list.clear();
        }
    }

    /** Uitloggen: ook de gemaakte zinnen, de omroepen en wat al gedaan was vergeten. */
    static void clear() {
        stopAll();
        SPEECH.clear();
        MAKING.clear();
        FAILED.clear();
        PENDING.clear();
        DONE.clear();
        FIRST_SEEN.clear();
    }
}
