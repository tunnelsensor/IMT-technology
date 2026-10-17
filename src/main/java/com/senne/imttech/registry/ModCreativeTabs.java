package com.senne.imttech.registry;

import com.senne.imttech.ImtTech;
import com.senne.imttech.imt.ImtData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Het eigen "I.M.T Technology"-tabblad in het creative-menu.
 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ImtTech.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> IMT_TECH = TABS.register("imt_tech", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.imttech"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ModItems.IMT_GLASSES.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // Een lege bril (zoals je hem in survival craft)...
                output.accept(ModItems.IMT_GLASSES.get());
                // ...en een volledig opgeladen bril, handig om te testen.
                ItemStack charged = new ItemStack(ModItems.IMT_GLASSES.get());
                ImtData.setEnergy(charged, ImtData.getMaxEnergy(charged));
                output.accept(charged);

                output.accept(ModItems.COMPUTER_CHIP.get());
                output.accept(ModItems.DRONE.get());
                output.accept(ModItems.DRONE_DOCK.get());
                output.accept(ModItems.TECH_CHESTPLATE.get());
                output.accept(ModItems.TECH_LEGGINGS.get());
                output.accept(ModItems.TECH_BOOTS.get());
                output.accept(ModItems.ROCKET_PACK.get());
                output.accept(ModItems.SUIT_WORKBENCH.get());
                output.accept(ModItems.LANDING_PAD.get());
                output.accept(ModItems.THRUSTER_UPGRADE.get());
                output.accept(ModItems.ARMOR_PLATING.get());
                output.accept(ModItems.LASER_CANNON_CAPACITOR.get());
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
                output.accept(ModItems.ROCKET_TURRET.get());
                output.accept(ModItems.ROCKET_AMMO.get());
                output.accept(ModItems.RAVEN_CIRCUIT.get());
                output.accept(ModItems.RAVEN_TURRET.get());
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
                output.accept(ModItems.HACKING_LAPTOP.get());
                output.accept(ModItems.RAVEN_SOLDIER_SPAWN_EGG.get());
                output.accept(ModItems.GUARD_ROBOT_SPAWN_EGG.get());
                output.accept(ModItems.ROBOT_ASSEMBLER.get());
                output.accept(ModItems.GUARD_ROBOT.get());
                output.accept(ModItems.ROBOT_DOCK.get());
                output.accept(ModItems.GUARD_DRONE.get());
                output.accept(ModItems.ROBOT_CIRCUIT_BOARD.get());
                output.accept(ModItems.SOLDERING_IRON.get());
                output.accept(ModItems.ROBOT_AMMO.get());
                output.accept(ModItems.ION_BLASTER.get());
                output.accept(ModItems.TRAINING_START.get());
                output.accept(ModItems.TRAINING_END.get());
                output.accept(ModItems.TRAINING_GOAL.get());
                for (com.senne.imttech.bot.BotModule module : com.senne.imttech.bot.BotModule.values()) {
                    output.accept(ModItems.robotModule(module));
                }
                // De onderdelen van een guard robot: per stijl hoofd, lijf, armen en benen.
                for (com.senne.imttech.robot.RobotStyle style : com.senne.imttech.robot.RobotStyle.values()) {
                    for (com.senne.imttech.robot.RobotSlot slot : com.senne.imttech.robot.RobotSlot.values()) {
                        output.accept(ModItems.robotPart(slot, style));
                    }
                }
            })
            .build());
}
