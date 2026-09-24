package com.senne.starktech.satellite;

import com.senne.starktech.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Wat je aan je satelliet kunt verbeteren. Elk onderdeel gaat van niveau 0 tot 10.
 *
 * Drie onderdelen zijn de schietstanden: op niveau 0 zit die stand op slot, niveau 1 speelt
 * hem vrij, en hogere niveaus maken hem sterker. De precisie-straal is er vanaf het begin
 * (niveau 1), anders kon een nieuwe speler de satelliet helemaal niet gebruiken.
 *
 * De andere drie gelden voor alles wat de satelliet doet.
 *
 * Elk niveau kost onderdelen uit je rugzak, en drones om die naar de satelliet te brengen:
 * de satelliet hangt in de ruimte, dus alles moet omhoog. Hoe hoger het niveau, hoe meer
 * drones er nodig zijn (die komen daarna gewoon terug in je arsenaal).
 */
public enum SatelliteUpgrade {
    /** De precisie-straal: meer schade en een iets bredere straal. */
    PRECISION("precision", 1),
    /** Het bombardement: vrijspelen, dan meer inslagen en grotere explosies. */
    BOMBARDMENT("bombardment", 0),
    /** De schuivende laser: vrijspelen, dan langer en harder. */
    SWEEP("sweep", 0),
    /** Opladen tussen twee aanvallen gaat sneller (kortere wachttijd). */
    RECHARGE("recharge", 0),
    /** Meer schade, voor alle standen. */
    POWER("power", 0),
    /** Sneller richten (korter opladen voor de inslag) en verder weg kunnen richten. */
    TARGETING("targeting", 0);

    public static final int MAX_LEVEL = 10;

    private final String id;
    private final int startLevel;

    SatelliteUpgrade(String id, int startLevel) {
        this.id = id;
        this.startLevel = startLevel;
    }

    public int startLevel() {
        return startLevel;
    }

    /** Is dit een schietstand (die op slot kan zitten)? */
    public boolean isMode() {
        return this == PRECISION || this == BOMBARDMENT || this == SWEEP;
    }

    public static SatelliteUpgrade of(SatelliteMode mode) {
        return switch (mode) {
            case PRECISION -> PRECISION;
            case BOMBARDMENT -> BOMBARDMENT;
            case SWEEP -> SWEEP;
        };
    }

    public Component label() {
        return Component.translatable("satellite.starktech.upgrade." + id);
    }

    /** Wat dit onderdeel op dit niveau doet, kort (voor het scherm). */
    public Component effect(int level) {
        if (isMode() && level == 0) {
            return Component.translatable("satellite.starktech.upgrade.locked");
        }
        return switch (this) {
            case PRECISION -> Component.translatable("satellite.starktech.effect.precision",
                    Math.round(SatelliteStats.precisionDamage(level, 0)));
            case BOMBARDMENT -> Component.translatable("satellite.starktech.effect.bombardment",
                    SatelliteStats.bombardmentShots(level));
            case SWEEP -> Component.translatable("satellite.starktech.effect.sweep",
                    SatelliteStats.sweepTicks(level) / 20.0f);
            case RECHARGE -> Component.translatable("satellite.starktech.effect.recharge",
                    SatelliteStats.cooldownTicks(level) / 20);
            case POWER -> Component.translatable("satellite.starktech.effect.power",
                    Math.round((SatelliteStats.powerFactor(level) - 1) * 100));
            case TARGETING -> Component.translatable("satellite.starktech.effect.targeting",
                    SatelliteStats.chargeTicks(level) / 20.0f, Math.round(SatelliteStats.range(level)));
        };
    }

    /** Hoeveel drones het vervoer naar niveau {@code nextLevel} nodig heeft. */
    public static int dronesFor(int nextLevel) {
        return 1 + (nextLevel + 1) / 2; // niveau 1: 2 drones ... niveau 10: 6 drones
    }

    /**
     * Wat niveau {@code nextLevel} kost. Eerst ijzer en redstone, dan goud en diamant, en
     * bovenaan netherite. Een stand vrijspelen kost ook iets dat bij die stand past.
     */
    public List<ItemStack> costFor(int nextLevel) {
        List<ItemStack> cost = new ArrayList<>();
        if (isMode() && nextLevel == 1) {
            cost.add(new ItemStack(Items.DIAMOND, 4));
            cost.add(new ItemStack(ModItems.STARK_CHIP.get(), 2));
            cost.add(this == BOMBARDMENT ? new ItemStack(Items.TNT, 8) : new ItemStack(Items.BLAZE_ROD, 4));
            return cost;
        }
        cost.add(new ItemStack(Items.IRON_INGOT, 6 + nextLevel * 2));
        cost.add(new ItemStack(Items.REDSTONE, 4 + nextLevel * 2));
        cost.add(new ItemStack(ModItems.STARK_CHIP.get(), 1 + nextLevel / 3));
        if (nextLevel >= 4) {
            cost.add(new ItemStack(Items.GOLD_INGOT, nextLevel));
        }
        if (nextLevel >= 5) {
            cost.add(new ItemStack(Items.DIAMOND, nextLevel - 3));
        }
        if (nextLevel >= 8) {
            cost.add(new ItemStack(Items.NETHERITE_SCRAP, nextLevel - 7));
        }
        if (nextLevel == MAX_LEVEL) {
            cost.add(new ItemStack(Items.NETHERITE_INGOT, 1));
        }
        return cost;
    }

    public static SatelliteUpgrade byId(int id) {
        SatelliteUpgrade[] all = values();
        return all[Math.floorMod(id, all.length)];
    }
}
