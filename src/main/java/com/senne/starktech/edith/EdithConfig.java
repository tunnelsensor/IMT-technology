package com.senne.starktech.edith;

import com.senne.starktech.arsenal.ArsenalBalance;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Instellingen van de wereld/server: hoeveel drones er maximaal mogen, hoe ver E.D.I.T.H.
 * redstone mag aansturen, enzovoort. Ze staan in het bestand config/starktech-server.toml (voor
 * al je werelden); een kopie in <wereldmap>/serverconfig/ geldt alleen voor die wereld.
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
            .comment("Number of attack drones in a swarm without drone upgrades")
            .defineInRange("drones.swarmBase", 4, 1, 64);

    public static final ModConfigSpec.IntValue SWARM_MAX = BUILDER
            .comment("Maximum number of attack drones in a swarm (with all upgrades)")
            .defineInRange("drones.swarmMax", 16, 1, 64);

    public static final ModConfigSpec.IntValue GUARD_BASE = BUILDER
            .comment("Number of guard drones (escort) without drone upgrades")
            .defineInRange("drones.guardBase", 2, 0, 32);

    public static final ModConfigSpec.IntValue GUARD_MAX = BUILDER
            .comment("Maximum number of guard drones (with all upgrades)")
            .defineInRange("drones.guardMax", 8, 0, 32);

    public static final ModConfigSpec.IntValue DRONES_PER_UPGRADE = BUILDER
            .comment("How many extra attack drones each drone upgrade adds")
            .defineInRange("drones.swarmPerUpgrade", 2, 0, 8);

    public static final ModConfigSpec.IntValue GUARDS_PER_UPGRADE = BUILDER
            .comment("How many extra guard drones each drone upgrade adds")
            .defineInRange("drones.guardsPerUpgrade", 1, 0, 8);

    public static final ModConfigSpec.IntValue MAX_DRONE_UPGRADES = BUILDER
            .comment("How many drone upgrades fit in one pair of glasses")
            .defineInRange("drones.maxUpgrades", 6, 0, 16);

    public static final ModConfigSpec.IntValue DRONE_DROP_HEIGHT = BUILDER
            // Andere naam dan het oude "dropHeight": de betekenis is veranderd (drones komen nu
            // van veel hoger), dus moeten bestaande werelden de nieuwe waarde krijgen in plaats
            // van hun oude 24 te houden.
            .comment("How high above you drones dive in from, and climb away to (in blocks)")
            .defineInRange("drones.orbitHeight", ArsenalBalance.ORBIT_HEIGHT, 0, 320);

    // ---------------------------------------------------------------- Raids (fase 15)

    public static final ModConfigSpec.BooleanValue RAIDS_ENABLED = BUILDER
            .comment("Do hostile raid drones come after players wearing the glasses?")
            .define("raids.enabled", true);

    public static final ModConfigSpec.IntValue RAID_MIN_MINUTES = BUILDER
            .comment("Shortest wait between two raids (in minutes)")
            .defineInRange("raids.minMinutes", 20, 1, 1440);

    public static final ModConfigSpec.IntValue RAID_MAX_MINUTES = BUILDER
            .comment("Longest wait between two raids (in minutes)")
            .defineInRange("raids.maxMinutes", 45, 1, 1440);

    public static final ModConfigSpec.IntValue RAID_SIZE = BUILDER
            .comment("How many drones come in one raid")
            .defineInRange("raids.size", 4, 1, 32);

    // ---------------------------------------------------------------- Hacken

    public static final ModConfigSpec.BooleanValue HACK_ALLOWED = BUILDER
            .comment("May E.D.I.T.H. intercept other players' private messages (hack player)?",
                    "Set this to false on a server where players do not want that.")
            .define("hack.allowed", true);

    public static final ModConfigSpec.BooleanValue PICKPOCKET_ALLOWED = BUILDER
            .comment("May players pick keycards out of other players' pockets (sneak behind them, empty hand, right-click)?",
                    "Set this to false on a server where players do not want that.")
            .define("pickpocket.allowed", true);

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
        return value(DRONE_DROP_HEIGHT, ArsenalBalance.ORBIT_HEIGHT);
    }

    public static boolean raidsEnabled() {
        return !SPEC.isLoaded() || RAIDS_ENABLED.get();
    }

    public static int raidMinMinutes() {
        return value(RAID_MIN_MINUTES, 20);
    }

    public static int raidMaxMinutes() {
        return value(RAID_MAX_MINUTES, 45);
    }

    public static int raidSize() {
        return value(RAID_SIZE, 4);
    }

    /** Mag de hack-functie gebruikt worden? */
    public static boolean hackAllowed() {
        return !SPEC.isLoaded() || HACK_ALLOWED.get();
    }

    /** Mag je keycards uit de zakken van andere spelers pikken (hoofdstuk 12)? */
    public static boolean pickpocketAllowed() {
        return !SPEC.isLoaded() || PICKPOCKET_ALLOWED.get();
    }
}
