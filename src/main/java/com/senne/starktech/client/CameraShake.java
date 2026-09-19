package com.senne.starktech.client;

import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Laat het beeld even schudden, bijvoorbeeld als de satelliet dichtbij inslaat.
 *
 * Hoe het werkt: we onthouden hoe hard en tot wanneer er geschud wordt. Elke frame
 * tellen we een klein beetje bij de kijkrichting op (met sinussen, zodat het vloeiend
 * heen en weer gaat) en dat wordt steeds minder tot de tijd om is.
 *
 * Het schudt alleen het BEELD; je speler draait niet echt (je mikpunt blijft kloppen).
 */
public final class CameraShake {
    private CameraShake() {}

    private static long startMillis;
    private static long endMillis;
    private static float strength;

    /**
     * Start het schudden (of maakt het erger als er al geschud wordt).
     *
     * @param power  hoe hard, in graden (1 = licht, 5 = flinke klap)
     * @param millis hoe lang het duurt
     */
    public static void shake(float power, long millis) {
        long now = Util.getMillis();
        // Een nieuwe, zwaardere klap overschrijft een licht trilletje.
        if (now > endMillis || power > strength) {
            startMillis = now;
            endMillis = now + millis;
            strength = power;
        }
    }

    public static void stop() {
        endMillis = 0;
        strength = 0;
    }

    /** Elke frame: het beeld een klein beetje verdraaien. */
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        long now = Util.getMillis();
        if (now >= endMillis || strength <= 0) {
            return;
        }
        // Van 1 naar 0 tijdens het schudden: het zakt dus netjes weg.
        float left = 1.0f - (float) (now - startMillis) / (endMillis - startMillis);
        float amount = strength * Mth.clamp(left, 0.0f, 1.0f);
        event.setYaw(event.getYaw() + (float) Math.sin(now / 37.0) * amount);
        event.setPitch(event.getPitch() + (float) Math.cos(now / 29.0) * amount);
        event.setRoll(event.getRoll() + (float) Math.sin(now / 23.0) * amount * 1.5f);
    }
}
