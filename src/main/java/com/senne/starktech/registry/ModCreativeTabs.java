package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.edith.EdithData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Het eigen "Stark Tech"-tabblad in het creative-menu.
 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StarkTech.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> STARK_TECH = TABS.register("stark_tech", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.starktech"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ModItems.EDITH_GLASSES.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // Een lege bril (zoals je hem in survival craft)...
                output.accept(ModItems.EDITH_GLASSES.get());
                // ...en een volledig opgeladen bril, handig om te testen.
                ItemStack charged = new ItemStack(ModItems.EDITH_GLASSES.get());
                EdithData.setEnergy(charged, EdithData.getMaxEnergy(charged));
                output.accept(charged);

                output.accept(ModItems.STARK_CHIP.get());
                output.accept(ModItems.STARK_DRONE.get());
                output.accept(ModItems.STARK_CHESTPLATE.get());
                output.accept(ModItems.STARK_LEGGINGS.get());
                output.accept(ModItems.STARK_BOOTS.get());
                output.accept(ModItems.ROCKET_PACK.get());
                output.accept(ModItems.BATTERY_UPGRADE.get());
                output.accept(ModItems.SCANNER_UPGRADE.get());
                output.accept(ModItems.DRONE_UPGRADE.get());
                output.accept(ModItems.CHARGING_STATION.get());
                output.accept(ModItems.SOLAR_PANEL.get());
                output.accept(ModItems.CABLE.get());
                output.accept(ModItems.HEAT_GENERATOR.get());
                output.accept(ModItems.RELAY.get());
                output.accept(ModItems.EMP.get());
                output.accept(ModItems.LASER_EMITTER.get());
                output.accept(ModItems.DOME_GENERATOR.get());
                output.accept(ModItems.HOLO_TABLE.get());
                output.accept(ModItems.STARK_TURRET.get());
                output.accept(ModItems.ROCKET_AMMO.get());
                output.accept(ModItems.HYDRA_CIRCUIT.get());
                output.accept(ModItems.HYDRA_TURRET.get());
                output.accept(ModItems.SECURITY_TURRET.get());
                output.accept(ModItems.SECURITY_CORE.get());
                output.accept(ModItems.STARK_LAPTOP.get());
                output.accept(ModItems.HYDRA_SOLDIER_SPAWN_EGG.get());
            })
            .build());
}
