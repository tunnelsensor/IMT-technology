package com.senne.starktech.client.hud;

import com.senne.starktech.client.DroneCamera;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithFeature;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Wat de bril nu kost en hoe lang hij het nog volhoudt.
 *
 * Dit is dezelfde berekening als op de server (zie EdithServer), maar dan aan jouw kant,
 * zodat de HUD en E.D.I.T.H.' antwoorden ("hoe lang nog?") hetzelfde getal laten zien.
 */
public final class PowerReadout {
    private PowerReadout() {}

    /** Hoeveel energie (SE) de bril nu per seconde verbruikt. */
    public static double drainPerSecond(ItemStack glasses) {
        double drain = StarkConfig.Glasses.DRAIN_HUD;
        for (EdithFeature feature : EdithFeature.values()) {
            if (EdithData.isEnabled(glasses, feature)) {
                drain += feature.drainPerSecond();
            }
        }
        if (DroneCamera.isActive()) {
            drain += StarkConfig.Recon.SCOUT_DRAIN;
        }
        return drain;
    }

    /** Hoe lang de batterij nog meegaat bij dit verbruik, bv. "1u 20m" of "4m 10s". */
    public static Component timeLeft(int energy, double drainPerSecond) {
        int seconds = (int) Math.min(Integer.MAX_VALUE, energy / Math.max(0.01, drainPerSecond));
        return seconds >= 3600
                ? Component.translatable("hud.starktech.sys.time_hours", seconds / 3600, seconds % 3600 / 60)
                : Component.translatable("hud.starktech.sys.time_minutes", seconds / 60, seconds % 60);
    }
}
