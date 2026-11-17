package com.senne.imttech.client.alarm;

import com.senne.imttech.alarm.AlarmSpeakerBlockEntity;
import com.senne.imttech.alarm.audio.AlarmAudio;
import com.senne.imttech.client.ImtClientConfig;
import com.senne.imttech.registry.ModSounds;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.jetbrains.annotations.Nullable;

/**
 * Wat één Speaker laat horen (hoofdstuk 126; sinds hoofdstuk 136 wat een diagram hem laat spelen), in je eigen spel.
 *
 * Drie soorten ({@link Kind}):
 * <ul>
 *   <li>{@code SOUND}: een geluid uit /sounds van de kern ({@code playsound("siren", ...)}), één keer, met de monsters die
 *       de server ons stuurde (via {@link PcmAudioStream}). Kwam je later in de buurt, dan begint het halverwege.</li>
 *   <li>{@code DEFAULT}: de ingebouwde sirene ({@code playsound("default", ...)} of Test), het bestand
 *       {@code sounds/alarm_siren.ogg} van de mod, in een lus tot de speaker zegt dat het voorbij is.</li>
 *   <li>{@code SPEECH}: een zin met de stem van I.M.T ({@code playtext(...)}), die je eigen spel net maakte.</li>
 * </ul>
 * Alle drie als "stroom" (zoals muziek), met als bereik het aantal blokken dat in de speaker is ingesteld: daarom maken we
 * hier zelf het {@link Sound}-object (in sounds.json staat maar één vast bereik).
 *
 * Elke tick kijkt hij of de speaker nog speelt wat hij speelt (hetzelfde volgnummer) en of je nog in de buurt bent; zo niet,
 * dan sterft hij in een paar ticks weg. Zegt de speaker een zin, dan is zijn geluid zolang stil (jouw keuze: "Sirene even
 * stil"). Hoe hard: het volume van de speaker maal je eigen knop (alarmSpeakerVolume in config/imttech-client.toml).
 */
final class AlarmSoundInstance extends AbstractTickableSoundInstance {
    /** Wat hij speelt. */
    enum Kind { SOUND, DEFAULT, SPEECH }

    /** De ondertitel ("Speaker plays"), voor wie ondertitels aan heeft staan. */
    private static final String SUBTITLE = "subtitles.imttech.alarm_speaker";
    /** Het bestand van de ingebouwde sirene: assets/imttech/sounds/alarm_siren.ogg. */
    private static final ResourceLocation SIREN_FILE = ResourceLocation.fromNamespaceAndPath(com.senne.imttech.ImtTech.MODID, "alarm_siren");

    final ClientLevel level;
    final BlockPos pos;
    final Kind kind;
    /** Het volgnummer van wat de speaker speelt (playToken of speechToken): verandert het, dan is dit voorbij. */
    final int token;
    /** De monsters (SOUND en SPEECH), of null voor de ingebouwde sirene. */
    private final @Nullable short[] clip;
    /** Bij welk monster hij begint. */
    private final int start;
    final int range;
    final float speakerVolume;
    /** Wordt hij stil (de speaker speelt iets anders, of je liep weg)? */
    private boolean fading;

    AlarmSoundInstance(ClientLevel level, BlockPos pos, Kind kind, int token, @Nullable short[] clip, int start, float speakerVolume,
                       int range) {
        super(ModSounds.ALARM_SIREN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.level = level;
        this.pos = pos.immutable();
        this.kind = kind;
        this.token = token;
        this.clip = clip;
        this.start = start;
        this.range = Math.max(1, range);
        this.speakerVolume = speakerVolume;
        this.looping = kind == Kind.DEFAULT;
        this.delay = 0;
        this.volume = targetVolume();
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
    }

    private float targetVolume() {
        return Math.max(0f, Math.min(1f, speakerVolume * (float) ImtClientConfig.alarmSpeakerVolume()));
    }

    /**
     * Ons eigen geluid-object: altijd als stroom, met het bereik van de speaker. Het wijst naar het sirene-bestand; bij een
     * geluid uit Files of een zin gebruikt {@link #getStream} dat bestand niet.
     */
    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        this.sound = new Sound(SIREN_FILE, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, true, false, range);
        return new WeighedSoundEvents(getLocation(), SUBTITLE);
    }

    /** De stukjes geluid: uit het ogg-bestand (de sirene) of uit onze eigen monsters. */
    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        if (clip == null) {
            return buffers.getStream(sound.getPath(), looping);
        }
        return CompletableFuture.completedFuture(new PcmAudioStream(clip, AlarmAudio.RATE, false, start));
    }

    /**
     * Niet stil beginnen: staat "Blocks" (of alles) op 0, dan start de geluidsmotor hem niet. (Wel gestart op volume 0 bleef
     * hij in de geluidsmotor hangen tot je naar een andere wereld ging: gevonden door de nalezing van hoofdstuk 126.)
     */
    @Override
    public boolean canStartSilent() {
        return false;
    }

    /** Meteen stoppen (zonder wegsterven). */
    void end() {
        stop();
    }

    /** Laat hem wegsterven. */
    void fadeOut() {
        fading = true;
    }

    boolean fading() {
        return fading;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        AlarmSpeakerBlockEntity speaker = level.getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity found ? found : null;
        long now = level.getGameTime();
        boolean gone = minecraft.level != level || minecraft.player == null || speaker == null
                || !minecraft.player.blockPosition().closerThan(pos, range + 8.0) || !stillPlaying(speaker, now);
        if (fading || gone) {
            volume *= 0.5f;
            if (volume < 0.01f) {
                stop();
            }
            return;
        }
        // Zegt de speaker een zin? Dan is zijn geluid even stil (bijna 0: helemaal 0 laten we de geluidsmotor niet zien):
        // zolang de zin in je spel echt klinkt, of (zonder stem) zolang de server schat dat hij duurt.
        boolean hush = kind != Kind.SPEECH && (AlarmSpeakerSounds.speakingAt(pos) || speaker.speakingAt(now));
        volume = hush ? Math.max(0.0001f, volume * 0.4f) : targetVolume();
    }

    /** Speelt de speaker nog wat deze klank speelt? */
    private boolean stillPlaying(AlarmSpeakerBlockEntity speaker, long now) {
        return switch (kind) {
            case SOUND, DEFAULT -> speaker.playToken() == token && speaker.playingAt(now);
            // Een zin loopt helemaal uit (hoe lang hij precies duurt, weet alleen dit spel); een nieuwe zin stopt hem.
            case SPEECH -> speaker.speechToken() == token;
        };
    }
}
