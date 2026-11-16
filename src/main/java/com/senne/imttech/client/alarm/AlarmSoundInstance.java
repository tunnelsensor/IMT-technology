package com.senne.imttech.client.alarm;

import com.senne.imttech.alarm.AlarmSpeakerBlock;
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
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Het geluid van één Alarm Speaker die afgaat (hoofdstuk 126), in je eigen spel.
 *
 * Twee soorten:
 * <ul>
 *   <li>zonder eigen geluid ({@code clip == null}): de ingebouwde sirene, het bestand {@code sounds/alarm_siren.ogg} van de
 *       mod;</li>
 *   <li>met een geluid dat iemand uploadde: de monsters die de server ons stuurde, via {@link PcmAudioStream}.</li>
 * </ul>
 * Beide als "stroom" (zoals muziek), met als bereik het aantal blokken dat in de speaker is ingesteld: daarom maken we
 * hier zelf het {@link Sound}-object (in sounds.json staat maar één vast bereik).
 *
 * Elke tick kijkt hij of de speaker nog afgaat en of je nog in de buurt bent; zo niet, dan sterft hij in een paar ticks
 * weg. Hoe hard: het volume van de speaker maal je eigen knop (alarmSpeakerVolume in config/imttech-client.toml).
 */
final class AlarmSoundInstance extends AbstractTickableSoundInstance {
    /** De ondertitel ("Alarm Speaker sounds"), voor wie ondertitels aan heeft staan. */
    private static final String SUBTITLE = "subtitles.imttech.alarm_speaker";
    /** Het bestand van de ingebouwde sirene: assets/imttech/sounds/alarm_siren.ogg. */
    private static final ResourceLocation SIREN_FILE = ResourceLocation.fromNamespaceAndPath(com.senne.imttech.ImtTech.MODID, "alarm_siren");

    final ClientLevel level;
    final BlockPos pos;
    /** Welk geluid: het id van de upload, of "" voor de ingebouwde sirene. */
    final String soundId;
    private final @Nullable short[] clip;
    final int range;
    final float speakerVolume;
    /** Is hij al eens gaan spelen? (Daarvoor zegt de geluidsmotor nog "niet actief".) */
    boolean started;
    /** Wordt hij stil (de speaker ging uit, of je liep weg)? */
    private boolean fading;

    AlarmSoundInstance(ClientLevel level, BlockPos pos, String soundId, @Nullable short[] clip, float speakerVolume, int range) {
        super(ModSounds.ALARM_SIREN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.level = level;
        this.pos = pos.immutable();
        this.soundId = soundId;
        this.clip = clip;
        this.range = Math.max(1, range);
        this.speakerVolume = speakerVolume;
        this.looping = true;
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
     * geüpload geluid gebruikt {@link #getStream} dat bestand niet.
     */
    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        this.sound = new Sound(SIREN_FILE, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, true, false, range);
        return new WeighedSoundEvents(getLocation(), SUBTITLE);
    }

    /** De stukjes geluid: uit het ogg-bestand (de sirene) of uit het geüploade geluid. */
    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        if (clip == null) {
            return buffers.getStream(sound.getPath(), looping);
        }
        return CompletableFuture.completedFuture(new PcmAudioStream(clip, AlarmAudio.RATE, looping));
    }

    /**
     * Niet stil beginnen: staat "Blocks" (of alles) op 0, dan start de geluidsmotor hem niet. (Wel gestart op volume 0 bleef
     * hij in de geluidsmotor hangen tot je naar een andere wereld ging: gevonden door de nalezing.)
     */
    @Override
    public boolean canStartSilent() {
        return false;
    }

    /** Meteen stoppen (zonder wegsterven). */
    void end() {
        stop();
    }

    /** Laat hem wegsterven (de speaker is uit, je liep weg, of zijn geluid veranderde). */
    void fadeOut() {
        fading = true;
    }

    /** Sterft hij weg? */
    boolean fading() {
        return fading;
    }

    @Override
    public void tick() {
        started = true;
        Minecraft minecraft = Minecraft.getInstance();
        boolean gone = minecraft.level != level || minecraft.player == null
                || !minecraft.player.blockPosition().closerThan(pos, range + 8.0) || !sounding(level.getBlockState(pos));
        if (fading || gone) {
            volume *= 0.5f;
            if (volume < 0.01f) {
                stop();
            }
            return;
        }
        volume = targetVolume();
    }

    /** Gaat de speaker op deze plek af? */
    static boolean sounding(BlockState state) {
        return state.getBlock() instanceof AlarmSpeakerBlock && state.getValue(AlarmSpeakerBlock.SOUNDING);
    }
}
