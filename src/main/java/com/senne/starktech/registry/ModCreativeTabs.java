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
                output.accept(ModItems.DRONE_DOCK.get());
                output.accept(ModItems.STARK_CHESTPLATE.get());
                output.accept(ModItems.STARK_LEGGINGS.get());
                output.accept(ModItems.STARK_BOOTS.get());
                output.accept(ModItems.ROCKET_PACK.get());
                output.accept(ModItems.SUIT_WORKBENCH.get());
                output.accept(ModItems.LANDING_PAD.get());
                output.accept(ModItems.THRUSTER_UPGRADE.get());
                output.accept(ModItems.ARMOR_PLATING.get());
                output.accept(ModItems.REPULSOR_CAPACITOR.get());
                output.accept(ModItems.ROCKET_WARHEAD.get());
                output.accept(ModItems.ROCKET_MAGAZINE.get());
                output.accept(ModItems.BATTERY_UPGRADE.get());
                output.accept(ModItems.SCANNER_UPGRADE.get());
                output.accept(ModItems.DRONE_UPGRADE.get());
                output.accept(ModItems.CHARGING_STATION.get());
                output.accept(ModItems.SOLAR_PANEL.get());
                output.accept(ModItems.CABLE.get());
                output.accept(ModItems.HEAT_GENERATOR.get());
                output.accept(ModItems.RELAY.get());
                output.accept(ModItems.RELAY_EMITTER.get());
                output.accept(ModItems.EMP.get());
                output.accept(ModItems.LASER_EMITTER.get());
                output.accept(ModItems.DOME_GENERATOR.get());
                output.accept(ModItems.POWER_CABINET.get());
                output.accept(ModItems.HOLO_TABLE.get());
                output.accept(ModItems.STARK_TURRET.get());
                output.accept(ModItems.ROCKET_AMMO.get());
                output.accept(ModItems.HYDRA_CIRCUIT.get());
                output.accept(ModItems.HYDRA_TURRET.get());
                output.accept(ModItems.SECURITY_TURRET.get());
                output.accept(ModItems.SECURITY_CORE.get());
                output.accept(ModItems.ZONE_MARKER.get());
                output.accept(ModItems.TIMED_C4.get());
                output.accept(ModItems.REMOTE_C4.get());
                output.accept(ModItems.DETONATOR.get());
                output.accept(ModItems.REPAIR_PASTE.get());
                output.accept(ModItems.CRUSHER.get());
                output.accept(ModItems.COMPACTOR.get());
                output.accept(ModItems.ARC_LAMP.get());
                output.accept(ModItems.LIGHT_SWITCH.get());
                output.accept(ModItems.SECURITY_CAMERA.get());
                output.accept(ModItems.SERVER_RACK.get());
                output.accept(ModItems.SERVER_CPU.get());
                output.accept(ModItems.SERVER_GPU.get());
                output.accept(ModItems.SERVER_RAM.get());
                output.accept(ModItems.SERVER_SSD.get());
                output.accept(ModItems.SERVER_FAN.get());
                output.accept(ModItems.KEYCARD.get());
                output.accept(ModItems.KEYCARD_READER.get());
                output.accept(ModItems.CODE_PANEL.get());
                output.accept(ModItems.BYPASS_KIT.get());
                output.accept(ModItems.STARK_LAPTOP.get());
                output.accept(ModItems.HYDRA_SOLDIER_SPAWN_EGG.get());
                output.accept(ModItems.GUARD_ROBOT_SPAWN_EGG.get());
                output.accept(ModItems.ROBOT_ASSEMBLER.get());
                output.accept(ModItems.GUARD_ROBOT.get());
                output.accept(ModItems.ROBOT_DOCK.get());
                output.accept(ModItems.ROBOT_CIRCUIT_BOARD.get());
                output.accept(ModItems.SOLDERING_IRON.get());
                output.accept(ModItems.ROBOT_AMMO.get());
                output.accept(ModItems.ION_BLASTER.get());
                for (com.senne.starktech.bot.BotModule module : com.senne.starktech.bot.BotModule.values()) {
                    output.accept(ModItems.robotModule(module));
                }
                // De onderdelen van een guard robot: per stijl hoofd, lijf, armen en benen.
                for (com.senne.starktech.robot.RobotStyle style : com.senne.starktech.robot.RobotStyle.values()) {
                    for (com.senne.starktech.robot.RobotSlot slot : com.senne.starktech.robot.RobotSlot.values()) {
                        output.accept(ModItems.robotPart(slot, style));
                    }
                }
            })
            .build());
}
