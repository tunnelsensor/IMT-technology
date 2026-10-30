package com.senne.imttech.registry;

import com.senne.imttech.ImtTech;
import com.senne.imttech.imt.ImtData;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * De acht I.M.T-tabbladen in het creative-menu (26 oktober), in deze volgorde: Resources, Equipment,
 * Weapons, Power, Interior, Security, Robots en Datacenter. Elk tabblad komt achter het vorige, het
 * eerste achter het vanilla-tabblad Combat. Een nieuw item moet in precies één tabblad (dat
 * controleert de test {@code every_item_is_in_one_creative_tab}).
 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ImtTech.MODID);

    /** Grondstoffen: de chips en alle upgrades. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RESOURCES = tab("resources", CreativeModeTabs.COMBAT,
            () -> new ItemStack(ModItems.COMPUTER_CHIP.get()), (parameters, output) -> {
                output.accept(ModItems.COMPUTER_CHIP.get());
                output.accept(ModItems.RAVEN_CIRCUIT.get());
                // De upgrades voor de bril (in het laadstation)...
                output.accept(ModItems.BATTERY_UPGRADE.get());
                output.accept(ModItems.SCANNER_UPGRADE.get());
                output.accept(ModItems.DRONE_UPGRADE.get());
                // ...en die voor het harnas (in de Suit Workbench).
                output.accept(ModItems.THRUSTER_UPGRADE.get());
                output.accept(ModItems.ARMOR_PLATING.get());
                output.accept(ModItems.LASER_CANNON_CAPACITOR.get());
                output.accept(ModItems.ROCKET_WARHEAD.get());
                output.accept(ModItems.ROCKET_MAGAZINE.get());
                output.accept(ModItems.REPAIR_PASTE.get());
            });

    /** Wat je draagt of bij je hebt: de bril, het harnas, het raketpakket en de drone. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EQUIPMENT = tab("equipment", RESOURCES.getKey(),
            () -> new ItemStack(ModItems.IMT_GLASSES.get()), (parameters, output) -> {
                // Een lege bril (zoals je hem in survival krijgt)...
                output.accept(ModItems.IMT_GLASSES.get());
                // ...en een volledig opgeladen bril, handig om te testen.
                ItemStack charged = new ItemStack(ModItems.IMT_GLASSES.get());
                ImtData.setEnergy(charged, ImtData.getMaxEnergy(charged));
                output.accept(charged);
                output.accept(ModItems.TECH_CHESTPLATE.get());
                output.accept(ModItems.TECH_LEGGINGS.get());
                output.accept(ModItems.TECH_BOOTS.get());
                output.accept(ModItems.ROCKET_PACK.get());
                output.accept(ModItems.DRONE.get());
                output.accept(ModItems.EMP.get());
            });

    /** Alle geweren, dan de granaten en de ladingen die je op afstand laat afgaan. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WEAPONS = tab("weapons", EQUIPMENT.getKey(),
            () -> new ItemStack(ModItems.LASER_RIFLE.get()), (parameters, output) -> {
                output.accept(ModItems.LASER_PISTOL.get());
                output.accept(ModItems.LASER_RIFLE.get());
                output.accept(ModItems.LASER_SNIPER.get());
                output.accept(ModItems.LASER_SHOTGUN.get());
                output.accept(ModItems.ION_BLASTER.get());
                output.accept(ModItems.ENERGY_CELL.get());
                output.accept(com.senne.imttech.weapons.EnergyCellItem.withCharge(com.senne.imttech.weapons.EnergyCellItem.FULL));
                output.accept(ModItems.EMP_GRENADE.get());
                output.accept(ModItems.STICKY_EMP_GRENADE.get());
                output.accept(ModItems.FRAG_GRENADE.get());
                output.accept(ModItems.STICKY_FRAG_GRENADE.get());
                output.accept(ModItems.SMOKE_GRENADE.get());
                output.accept(ModItems.FLASHBANG.get());
                output.accept(ModItems.REMOTE_EMP.get());
                output.accept(ModItems.EMP_TRIGGER.get());
                output.accept(ModItems.REMOTE_FRAG.get());
                output.accept(ModItems.FRAG_TRIGGER.get());
            });

    /** De kabels (stroom, netwerk, PoE) en wat stroom maakt of opslaat. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> POWER = tab("power", WEAPONS.getKey(),
            () -> new ItemStack(ModItems.SOLAR_PANEL.get()), (parameters, output) -> {
                output.accept(ModItems.CABLE.get());
                output.accept(ModItems.NETWORK_CABLE.get());
                output.accept(ModItems.POE_CABLE.get());
                output.accept(ModItems.SOLAR_PANEL.get());
                output.accept(ModItems.HEAT_GENERATOR.get());
                output.accept(ModItems.POWER_CABINET.get());
                output.accept(ModItems.NETWORK_SWITCH.get());
            });

    /** De machines en werkbanken voor in je basis, en het licht en de relais. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> INTERIOR = tab("interior", POWER.getKey(),
            () -> new ItemStack(ModItems.HOLO_TABLE.get()), (parameters, output) -> {
                output.accept(ModItems.HOLO_TABLE.get());
                output.accept(ModItems.SUIT_WORKBENCH.get());
                output.accept(ModItems.CRUSHER.get());
                output.accept(ModItems.COMPACTOR.get());
                output.accept(ModItems.CHARGING_STATION.get());
                output.accept(ModItems.DRONE_DOCK.get());
                output.accept(ModItems.LANDING_PAD.get());
                output.accept(ModItems.ARC_LAMP.get());
                output.accept(ModItems.LIGHT_SWITCH.get());
                output.accept(ModItems.RELAY.get());
                output.accept(ModItems.RELAY_EMITTER.get());
            });

    /** De beveiliging van je basis (kern, lezers, lasers, torentjes) en wat je nodig hebt om erdoor te komen. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SECURITY = tab("security", INTERIOR.getKey(),
            () -> new ItemStack(ModItems.KEYCARD_READER.get()), (parameters, output) -> {
                output.accept(ModItems.SECURITY_CORE.get());
                output.accept(ModItems.COMPUTER.get());
                output.accept(ModItems.MONITOR.get());
                output.accept(ModItems.ZONE_MARKER.get());
                output.accept(ModItems.KEYCARD.get());
                output.accept(ModItems.KEYCARD_READER.get());
                output.accept(ModItems.CODE_PANEL.get());
                output.accept(ModItems.NETWORK_BUTTON.get());
                output.accept(ModItems.NETWORK_LEVER.get());
                output.accept(ModItems.LASER_EMITTER.get());
                output.accept(ModItems.LASER_BRIDGE.get());
                output.accept(ModItems.DOME_GENERATOR.get());
                output.accept(ModItems.SECURITY_CAMERA.get());
                output.accept(ModItems.SECURITY_TURRET.get());
                output.accept(ModItems.ROCKET_TURRET.get());
                output.accept(ModItems.RAVEN_TURRET.get());
                output.accept(ModItems.ROCKET_AMMO.get());
                output.accept(ModItems.BYPASS_KIT.get());
                output.accept(ModItems.HACKING_LAPTOP.get());
            });

    /** Eerst alle robotonderdelen, dan alle chips, de training, en daarna de rest voor robots. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ROBOTS = tab("robots", SECURITY.getKey(),
            () -> new ItemStack(ModItems.GUARD_ROBOT.get()), (parameters, output) -> {
                // De onderdelen van een guard robot: per stijl hoofd, lijf, armen en benen.
                for (com.senne.imttech.robot.RobotStyle style : com.senne.imttech.robot.RobotStyle.values()) {
                    for (com.senne.imttech.robot.RobotSlot slot : com.senne.imttech.robot.RobotSlot.values()) {
                        output.accept(ModItems.robotPart(slot, style));
                    }
                }
                // De chips: de printplaat en alle modules.
                output.accept(ModItems.ROBOT_CIRCUIT_BOARD.get());
                for (com.senne.imttech.bot.BotModule module : com.senne.imttech.bot.BotModule.values()) {
                    output.accept(ModItems.robotModule(module));
                }
                output.accept(ModItems.TRAINING_START.get());
                output.accept(ModItems.TRAINING_END.get());
                output.accept(ModItems.TRAINING_GOAL.get());
                output.accept(ModItems.ROBOT_ASSEMBLER.get());
                output.accept(ModItems.ROBOT_DOCK.get());
                output.accept(ModItems.GUARD_ROBOT.get());
                output.accept(ModItems.GUARD_DRONE.get());
                output.accept(ModItems.SOLDERING_IRON.get());
                output.accept(ModItems.ROBOT_AMMO.get());
                output.accept(ModItems.GUARD_ROBOT_SPAWN_EGG.get());
                output.accept(ModItems.RAVEN_SOLDIER_SPAWN_EGG.get());
            });

    /** Het serverrek en alles wat erin gaat. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DATACENTER = tab("datacenter", ROBOTS.getKey(),
            () -> new ItemStack(ModItems.SERVER_RACK.get()), (parameters, output) -> {
                output.accept(ModItems.SERVER_RACK.get());
                output.accept(ModItems.SERVER_CPU.get());
                output.accept(ModItems.SERVER_GPU.get());
                output.accept(ModItems.SERVER_RAM.get());
                output.accept(ModItems.SERVER_SSD.get());
                output.accept(ModItems.SERVER_FAN.get());
            });

    /** Eén tabblad, met als naam {@code itemGroup.imttech.<name>}, direct achter het tabblad {@code after}. */
    private static DeferredHolder<CreativeModeTab, CreativeModeTab> tab(String name, ResourceKey<CreativeModeTab> after,
            Supplier<ItemStack> icon, CreativeModeTab.DisplayItemsGenerator items) {
        return TABS.register(name, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.imttech." + name))
                .withTabsBefore(after)
                .icon(icon)
                .displayItems(items)
                .build());
    }
}
