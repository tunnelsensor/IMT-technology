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
    /** Het scherm van een serverrack: zijn vier vakjes en wat de server kan (zie ServerRackMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<com.senne.imttech.logic.ServerRackMenu>> SERVER_RACK =
            MENUS.register("server_rack", () -> IMenuTypeExtension.create(com.senne.imttech.logic.ServerRackMenu::new));
    /** Het scherm van een Crusher of Compactor (hoofdstuk 11): met welke machine het is erbij (zie CrackMachineMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<com.senne.imttech.repair.CrackMachineMenu>> CRACK_MACHINE =
            MENUS.register("crack_machine", () -> IMenuTypeExtension.create(com.senne.imttech.repair.CrackMachineMenu::new));
    /** Het scherm van een landingsplatform: zijn batterij (zie LandingPadMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<com.senne.imttech.suit.LandingPadMenu>> LANDING_PAD =
            MENUS.register("landing_pad", () -> IMenuTypeExtension.create(com.senne.imttech.suit.LandingPadMenu::new));
    /** Het scherm van de Robot Assembler (hoofdstuk 21, zie RobotAssemblerMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<com.senne.imttech.robot.RobotAssemblerMenu>> ROBOT_ASSEMBLER =
            MENUS.register("robot_assembler", () -> IMenuTypeExtension.create(com.senne.imttech.robot.RobotAssemblerMenu::new));
}
