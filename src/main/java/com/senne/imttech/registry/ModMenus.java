package com.senne.imttech.registry;

import com.senne.imttech.ImtTech;
import com.senne.imttech.station.ChargerMenu;
import com.senne.imttech.station.ChargingStationMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Menu's (de "server-kant" van een scherm met slots, zoals het laadstation).
 */
public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ImtTech.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ChargingStationMenu>> CHARGING_STATION =
            MENUS.register("charging_station", () -> new MenuType<>(ChargingStationMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /**
     * Het scherm van het zonnepaneel en de warmtegenerator (ze delen er een). Met "extra
     * gegevens": de server stuurt mee welk van de twee het is.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<ChargerMenu>> CHARGER =
            MENUS.register("charger", () -> IMenuTypeExtension.create(ChargerMenu::new));
    /** Het scherm van een Crusher of Compactor (hoofdstuk 11): met welke machine het is erbij (zie CrackMachineMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<com.senne.imttech.repair.CrackMachineMenu>> CRACK_MACHINE =
            MENUS.register("crack_machine", () -> IMenuTypeExtension.create(com.senne.imttech.repair.CrackMachineMenu::new));
    /** Het scherm van de Polymerizer (hoofdstuk 85): kool, een emmer water, zijn tank en het polymeer. */
    public static final DeferredHolder<MenuType<?>, MenuType<com.senne.imttech.plastic.PolymerizerMenu>> POLYMERIZER =
            MENUS.register("polymerizer", () -> IMenuTypeExtension.create(com.senne.imttech.plastic.PolymerizerMenu::new));
    /** Het scherm van de Electric Furnace (hoofdstuk 87): erin, eruit en hoe ver hij is. */
    public static final DeferredHolder<MenuType<?>, MenuType<com.senne.imttech.furnace.ElectricFurnaceMenu>> ELECTRIC_FURNACE =
            MENUS.register("electric_furnace", () -> IMenuTypeExtension.create(com.senne.imttech.furnace.ElectricFurnaceMenu::new));
    /** Het scherm van een landingsplatform: zijn batterij (zie LandingPadMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<com.senne.imttech.suit.LandingPadMenu>> LANDING_PAD =
            MENUS.register("landing_pad", () -> IMenuTypeExtension.create(com.senne.imttech.suit.LandingPadMenu::new));
    /** Het scherm van de Robot Assembler (hoofdstuk 21, zie RobotAssemblerMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<com.senne.imttech.robot.RobotAssemblerMenu>> ROBOT_ASSEMBLER =
            MENUS.register("robot_assembler", () -> IMenuTypeExtension.create(com.senne.imttech.robot.RobotAssemblerMenu::new));
    /** Hoofdstuk 52: de Gunsmith Table (slepen, geen vakjes; zie GunsmithMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<com.senne.imttech.guns.GunsmithMenu>> GUNSMITH_TABLE =
            MENUS.register("gunsmith_table", () -> IMenuTypeExtension.create(com.senne.imttech.guns.GunsmithMenu::new));
    /** Hoofdstuk 54: een open holster of pouch (magazijnen en granaten erin en eruit; zie GearPouchMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<com.senne.imttech.guns.GearPouchMenu>> GEAR_POUCH =
            MENUS.register("gear_pouch", () -> IMenuTypeExtension.create(com.senne.imttech.guns.GearPouchMenu::new));
    /** Hoofdstuk 55: de Laser Workbench: hetzelfde menu als de Gunsmith Table, met de onderdelen van een lasergeweer. */
    public static final DeferredHolder<MenuType<?>, MenuType<com.senne.imttech.guns.GunsmithMenu>> LASER_WORKBENCH =
            MENUS.register("laser_workbench", () -> IMenuTypeExtension.create(com.senne.imttech.guns.GunsmithMenu::laser));
    /** Hoofdstuk 52: de Reloading Bench (zie ReloadingBenchMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<com.senne.imttech.guns.ReloadingBenchMenu>> RELOADING_BENCH =
            MENUS.register("reloading_bench", () -> IMenuTypeExtension.create(com.senne.imttech.guns.ReloadingBenchMenu::new));
}
