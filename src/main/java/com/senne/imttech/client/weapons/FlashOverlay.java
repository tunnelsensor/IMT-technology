package com.senne.imttech.client.weapons;

import com.senne.imttech.registry.ModSounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Wat je ziet en hoort na een flashbang (hoofdstuk 35, jouw keuze: "zoals in CS"): je hele scherm wordt wit (ook je HUD
 * en je hand), blijft even wit en trekt dan langzaam weg. Je hoort een hoge piep in je oren, en alle andere geluiden
 * klinken gedempt tot de piep wegebt. Hoe erg en hoe lang, zegt de server (zie weapons/Flashbang).
 */
public final class FlashOverlay implements LayeredDraw.Layer {
    /** Van de tijd blijft het scherm eerst zoveel helemaal wit; daarna trekt het weg. */
    private static final float HOLD = 0.4f;

    private static long startMillis;
    private static long durationMillis;
    private static float strength;

    /** Een flashbang raakt je: {@code strength} (0 tot 1), {@code ticks} lang. Een zwakkere flits telt niet over een sterkere. */
    public static void flash(float strength, int ticks) {
        float now = level();
        if (strength < now) {
            return;
        }
        FlashOverlay.strength = Mth.clamp(strength, 0, 1);
        startMillis = Util.getMillis();
        durationMillis = ticks * 50L;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.FLASHBANG_RING.get(), 1.0f, 0.4f + 0.6f * strength));
    }

    /** Hoe wit het scherm nu is (0 = niets, 1 = helemaal). */
    public static float level() {
        if (durationMillis <= 0) {
            return 0;
        }
        float t = (Util.getMillis() - startMillis) / (float) durationMillis;
        if (t >= 1 || t < 0) {
            return 0;
        }
        if (t < HOLD) {
            return strength;
        }
        float fade = 1 - (t - HOLD) / (1 - HOLD);
        return strength * fade * fade;
    }

    public static void clear() {
        durationMillis = 0;
        strength = 0;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        float white = level();
        if (white <= 0.003f) {
            return;
        }
        int alpha = Mth.clamp(Math.round(white * 255), 0, 255);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), alpha << 24 | 0xFFFFFF);
    }

    // ================================================================ Gedempt geluid

    /**
     * Elk geluid dat begint terwijl je verblind bent, klinkt zachter (behalve de piep zelf): tot 85% zachter vlak na
     * de flits, en weer gewoon als het wit weg is.
     */
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        float white = level();
        if (sound == null || white <= 0.05f || sound.getLocation().equals(ModSounds.FLASHBANG_RING.getId())
                || sound.getSource() == SoundSource.MASTER || sound instanceof TickableSoundInstance) {
            return;
        }
        event.setSound(new Muffled(sound, 1 - 0.85f * white));
    }

    /** Een geluid, maar zachter (de rest blijft hetzelfde). */
    private record Muffled(SoundInstance inner, float factor) implements SoundInstance {
        @Override
        public ResourceLocation getLocation() {
            return inner.getLocation();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return inner.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return inner.getSound();
        }

        @Override
        public SoundSource getSource() {
            return inner.getSource();
        }

        @Override
        public boolean isLooping() {
            return inner.isLooping();
        }

        @Override
        public boolean isRelative() {
            return inner.isRelative();
        }

        @Override
        public int getDelay() {
            return inner.getDelay();
        }

        @Override
        public float getVolume() {
            return inner.getVolume() * factor;
        }

        @Override
        public float getPitch() {
            return inner.getPitch();
        }

        @Override
        public double getX() {
            return inner.getX();
        }

        @Override
        public double getY() {
            return inner.getY();
        }

        @Override
        public double getZ() {
            return inner.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return inner.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return inner.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return inner.canPlaySound();
        }
    }
}
