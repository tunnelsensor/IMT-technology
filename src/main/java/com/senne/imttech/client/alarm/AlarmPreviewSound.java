package com.senne.imttech.client.alarm;

import com.senne.imttech.ImtTech;
import com.senne.imttech.alarm.audio.AlarmAudio;
import com.senne.imttech.client.ImtClientConfig;
import com.senne.imttech.registry.ModSounds;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.jetbrains.annotations.Nullable;

/**
 * De knop Preview in het scherm van een Alarm Speaker (hoofdstuk 126): je hoort het geluid zelf, zonder plek in de wereld
 * (gewoon in je oren, zoals een menu-geluid), even hard als de speaker staat. Alleen voor jou: niemand anders hoort het.
 * Hooguit een paar seconden, en meteen stil als je het scherm sluit.
 */
final class AlarmPreviewSound extends AbstractTickableSoundInstance {
    private static final ResourceLocation SIREN_FILE = ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "alarm_siren");

    private final @Nullable short[] clip;
    private int ticksLeft;

    AlarmPreviewSound(@Nullable short[] clip, float speakerVolume, int maxTicks) {
        super(ModSounds.ALARM_SIREN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.clip = clip;
        this.ticksLeft = maxTicks;
        this.volume = Math.max(0f, Math.min(1f, speakerVolume * (float) ImtClientConfig.alarmSpeakerVolume()));
        this.looping = true;
        this.delay = 0;
        this.relative = true; // aan je oren vast, niet op een plek
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        this.sound = new Sound(SIREN_FILE, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, true, false, 16);
        return new WeighedSoundEvents(getLocation(), "subtitles.imttech.alarm_speaker");
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        if (clip == null) {
            return buffers.getStream(sound.getPath(), looping);
        }
        return CompletableFuture.completedFuture(new PcmAudioStream(clip, AlarmAudio.RATE, looping));
    }

    @Override
    public void tick() {
        // Hoofdstuk 136: je beluistert een geluid in Files (het bureaublad van de kern); gaat dat dicht, dan stopt het.
        if (--ticksLeft <= 0 || Minecraft.getInstance().screen == null) {
            stop();
        }
    }

    void end() {
        stop();
    }
}
