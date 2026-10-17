package com.senne.imttech.client.hud;

import com.senne.imttech.client.DroneCamera;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtFeature;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Wat de bril nu kost en hoe lang hij het nog volhoudt.
 *
 * Dit is dezelfde berekening als op de server (zie ImtServer), maar dan aan jouw kant,
 * zodat de HUD en I.M.T' antwoorden ("hoe lang nog?") hetzelfde getal laten zien.
 */
public final class PowerReadout {
    private PowerReadout() {}

    /** Hoeveel energie (SE) de bril nu per seconde verbruikt. */
    public static double drainPerSecond(ItemStack glasses) {
        double drain = ImtConfig.Glasses.DRAIN_HUD;
        for (ImtFeature feature : ImtFeature.values()) {
            if (ImtData.isEnabled(glasses, feature)) {
                drain += feature.drainPerSecond();
            }
        }
        if (DroneCamera.isActive()) {
            drain += ImtConfig.Recon.SCOUT_DRAIN;
        }
        return drain;
    }

    /** Hoe lang de batterij nog meegaat bij dit verbruik, bv. "1u 20m" of "4m 10s". */
    public static Component timeLeft(int energy, double drainPerSecond) {
        int seconds = (int) Math.min(Integer.MAX_VALUE, energy / Math.max(0.01, drainPerSecond));
        return seconds >= 3600
                ? Component.translatable("hud.imttech.sys.time_hours", seconds / 3600, seconds % 3600 / 60)
                : Component.translatable("hud.imttech.sys.time_minutes", seconds / 60, seconds % 60);
    }
}
