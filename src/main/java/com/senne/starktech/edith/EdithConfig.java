package com.senne.starktech.edith;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Instellingen van de wereld/server: hoeveel drones er maximaal mogen, hoe ver E.D.I.T.H.
 * redstone mag aansturen, enzovoort. Ze staan in het bestand
 * <wereldmap>/serverconfig/starktech-server.toml (of in de map serverconfig van je server).
 *
 * Waarom een config en geen vast getal? Op een server met veel spelers kunnen honderden
 * drones het spel laten haperen. De eigenaar van de wereld kan hier dus zelf kiezen
 * hoe groot een zwerm mag worden.
 *
 * Belangrijk: deze instellingen worden ook naar de spelers gestuurd, zodat het menu
 * en de HUD dezelfde getallen laten zien als de server gebruikt. Zolang er geen wereld
 * open is (bv. in het hoofdmenu) gebruiken we de standaardwaarden; daarvoor staan bij
 * elke instelling kleine hulpmethodes met een vaste "als het nog niet geladen is"-waarde.
 */
public final class EdithConfig {
    private EdithConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---------------------------------------------------------------- Drones

    public static final ModConfigSpec.IntValue SWARM_BASE = BUILDER
            .comment("Aantal aanvalsdrones in een zwerm zonder drone-upgrades")
            .defineInRange("drones.swarmBase", 4, 1, 64);

    public static final ModConfigSpec.IntValue SWARM_MAX = BUILDER
            .comment("Maximum aantal aanvalsdrones in een zwerm (met alle upgrades)")
            .defineInRange("drones.swarmMax", 16, 1, 64);

    public static final ModConfigSpec.IntValue GUARD_BASE = BUILDER
            .comment("Aantal beschermende drones (escorte) zonder drone-upgrades")
            .defineInRange("drones.guardBase", 2, 0, 32);

    public static final ModConfigSpec.IntValue GUARD_MAX = BUILDER
            .comment("Maximum aantal beschermende drones (met alle upgrades)")
            .defineInRange("drones.guardMax", 8, 0, 32);

    public static final ModConfigSpec.IntValue DRONES_PER_UPGRADE = BUILDER
            .comment("Hoeveel aanvalsdrones er per drone-upgrade bijkomen")
            .defineInRange("drones.swarmPerUpgrade", 2, 0, 8);

    public static final ModConfigSpec.IntValue GUARDS_PER_UPGRADE = BUILDER
            .comment("Hoeveel beschermende drones er per drone-upgrade bijkomen")
            .defineInRange("drones.guardsPerUpgrade", 1, 0, 8);

    public static final ModConfigSpec.IntValue MAX_DRONE_UPGRADES = BUILDER
            .comment("Hoeveel drone-upgrades er maximaal in één bril passen")
            .defineInRange("drones.maxUpgrades", 6, 0, 16);

    public static final ModConfigSpec.IntValue DRONE_DROP_HEIGHT = BUILDER
            .comment("Van hoe hoog drones uit de lucht komen duiken (in blokken)")
            .defineInRange("drones.dropHeight", 24, 0, 96);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ---------------------------------------------------------------- Hulpmethodes
    // Deze geven de standaardwaarde terug zolang de config nog niet geladen is
    // (bijvoorbeeld in het hoofdmenu of tijdens de automatische tests).

    private static int value(ModConfigSpec.IntValue config, int fallback) {
        return SPEC.isLoaded() ? config.get() : fallback;
    }

    public static int swarmBase() {
        return value(SWARM_BASE, 4);
    }

    public static int swarmMax() {
        return value(SWARM_MAX, 16);
    }

    public static int guardBase() {
        return value(GUARD_BASE, 2);
    }

    public static int guardMax() {
        return value(GUARD_MAX, 8);
    }

    public static int swarmPerUpgrade() {
        return value(DRONES_PER_UPGRADE, 2);
    }

    public static int guardsPerUpgrade() {
        return value(GUARDS_PER_UPGRADE, 1);
    }

    public static int maxDroneUpgrades() {
        return value(MAX_DRONE_UPGRADES, 6);
    }

    public static int droneDropHeight() {
        return value(DRONE_DROP_HEIGHT, 24);
    }
}
