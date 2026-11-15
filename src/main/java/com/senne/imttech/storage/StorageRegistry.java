package com.senne.imttech.storage;

import com.senne.imttech.ImtTech;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Wat Minecraft moet kennen van het opslagsysteem (hoofdstuk 113): de Storage Interface, de Chest Sensor en de Charging
 * Pad (elk een blok, een item en een binnenkant), en het scherm van de interface. Net als de rugzakken en de body armor
 * heeft dit zijn eigen lijsten, die {@link #register} bij het opstarten aanmeldt; voor Minecraft zijn het gewoon blokken
 * en items van imttech.
 */
public final class StorageRegistry {
    private StorageRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ImtTech.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ImtTech.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ImtTech.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ImtTech.MODID);

    // ================================================================ De blokken

    /** De kast met het scherm waar je bestelt en inlegt (zie StorageInterfaceBlock). */
    public static final DeferredBlock<StorageInterfaceBlock> STORAGE_INTERFACE = BLOCKS.registerBlock("storage_interface",
            StorageInterfaceBlock::new, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Het platte kastje dat je op een kist plakt (zie ChestSensorBlock). Je loopt er gewoon langs. */
    public static final DeferredBlock<ChestSensorBlock> CHEST_SENSOR = BLOCKS.registerBlock("chest_sensor",
            ChestSensorBlock::new, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0f, 3.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .noCollission());

    /** De laadplaat voor een RC Car (zie ChargingPadBlock); zijn strepen geven een beetje licht als hij laadt. */
    public static final DeferredBlock<ChargingPadBlock> CHARGING_PAD = BLOCKS.registerBlock("charging_pad",
            ChargingPadBlock::new, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ChargingPadBlock.CHARGING) ? 6 : 0));

    // ================================================================ De items

    public static final DeferredItem<BlockItem> STORAGE_INTERFACE_ITEM = item("storage_interface", STORAGE_INTERFACE);
    public static final DeferredItem<BlockItem> CHEST_SENSOR_ITEM = item("chest_sensor", CHEST_SENSOR);
    public static final DeferredItem<BlockItem> CHARGING_PAD_ITEM = item("charging_pad", CHARGING_PAD);

    /** Het item van een blok, met een regel uitleg eronder (tooltip.imttech.naam). */
    private static DeferredItem<BlockItem> item(String name, DeferredBlock<? extends Block> block) {
        return ITEMS.registerItem(name, properties -> new BlockItem(block.get(), properties) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                tooltip.add(Component.translatable("tooltip.imttech." + name).withStyle(ChatFormatting.GRAY));
            }
        }, new Item.Properties());
    }

    // ================================================================ De binnenkanten

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageInterfaceBlockEntity>> STORAGE_INTERFACE_ENTITY =
            BLOCK_ENTITIES.register("storage_interface",
                    () -> new BlockEntityType<>(StorageInterfaceBlockEntity::new, Set.of(STORAGE_INTERFACE.get()), null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChestSensorBlockEntity>> CHEST_SENSOR_ENTITY =
            BLOCK_ENTITIES.register("chest_sensor",
                    () -> new BlockEntityType<>(ChestSensorBlockEntity::new, Set.of(CHEST_SENSOR.get()), null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargingPadBlockEntity>> CHARGING_PAD_ENTITY =
            BLOCK_ENTITIES.register("charging_pad",
                    () -> new BlockEntityType<>(ChargingPadBlockEntity::new, Set.of(CHARGING_PAD.get()), null));

    // ================================================================ Het scherm

    /** Het scherm van de Storage Interface (zie StorageMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<StorageMenu>> STORAGE_MENU =
            MENUS.register("storage_interface", () -> IMenuTypeExtension.create(StorageMenu::new));

    /** Bij het opstarten (Storages.register): alle lijsten aanmelden. */
    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
    }
}
