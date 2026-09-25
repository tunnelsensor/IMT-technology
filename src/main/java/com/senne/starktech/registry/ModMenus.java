package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.station.ChargerMenu;
import com.senne.starktech.station.ChargingStationMenu;
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

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, StarkTech.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ChargingStationMenu>> CHARGING_STATION =
            MENUS.register("charging_station", () -> new MenuType<>(ChargingStationMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /**
     * Het scherm van het zonnepaneel en de warmtegenerator (ze delen er een). Met "extra
     * gegevens": de server stuurt mee welk van de twee het is.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<ChargerMenu>> CHARGER =
            MENUS.register("charger", () -> IMenuTypeExtension.create(ChargerMenu::new));
}
