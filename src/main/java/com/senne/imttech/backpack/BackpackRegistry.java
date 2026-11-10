package com.senne.imttech.backpack;

import com.mojang.serialization.Codec;
import com.senne.imttech.ImtTech;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Alles wat Minecraft van de rugzakken (hoofdstuk 72) moet kennen, op één plek: de drie rugzakken, het blok van een
 * neergezette rugzak, zijn menu, de data components en het verfrecept. De rest van de mod registreert in registry/
 * (ModItems, ModBlocks, ...); de rugzakken hebben hun eigen lijsten, die {@link #register} bij het opstarten aanmeldt.
 * Voor Minecraft maakt dat niets uit: het zijn gewoon items en blokken van imttech.
 */
public final class BackpackRegistry {
    private BackpackRegistry() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ImtTech.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ImtTech.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ImtTech.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ImtTech.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ImtTech.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ImtTech.MODID);

    // ================================================================ De rugzakken

    public static final DeferredItem<BackpackItem> ASSAULT_PACK = ITEMS.registerItem("assault_pack",
            properties -> new BackpackItem(properties, BackpackItem.Size.ASSAULT), new Item.Properties());
    public static final DeferredItem<BackpackItem> PATROL_PACK = ITEMS.registerItem("patrol_pack",
            properties -> new BackpackItem(properties, BackpackItem.Size.PATROL), new Item.Properties());
    public static final DeferredItem<BackpackItem> RUCKSACK = ITEMS.registerItem("rucksack",
            properties -> new BackpackItem(properties, BackpackItem.Size.RUCKSACK), new Item.Properties());

    /** De rugzak van deze maat. */
    public static BackpackItem item(BackpackItem.Size size) {
        return switch (size) {
            case ASSAULT -> ASSAULT_PACK.get();
            case PATROL -> PATROL_PACK.get();
            case RUCKSACK -> RUCKSACK.get();
        };
    }

    // ================================================================ Een neergezette rugzak

    /** Het blok (geen item: je zet een rugzak neer met sneak + rechtsklik, zie BackpackItem.useOn). */
    public static final DeferredBlock<BackpackBlock> BACKPACK_BLOCK = BLOCKS.registerBlock("backpack", BackpackBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(0.6f)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .sound(SoundType.WOOL));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BackpackBlockEntity>> BACKPACK_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("backpack",
                    () -> new BlockEntityType<>(BackpackBlockEntity::new, java.util.Set.of(BACKPACK_BLOCK.get()), null));

    /** Het scherm van een open rugzak (zie BackpackMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<BackpackMenu>> BACKPACK_MENU =
            MENUS.register("backpack", () -> IMenuTypeExtension.create(BackpackMenu::new));

    // ================================================================ Data components

    /** Camo (jouw wens: bruin + lichtgroen + donkergroen, "een kleur op zich"). Zonder: de gewone kleur (dyed_color). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> CAMO =
            COMPONENTS.registerComponentType("camo", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /**
     * Een eigen nummer per rugzak, dat hij krijgt zodra iemand hem opent. Twee mensen die dezelfde rugzak open hebben
     * (jij en een dief, of twee mensen bij een neergezette) delen zo één inhoud, en een scherm sluit vanzelf als zijn
     * rugzak ergens anders heen gaat (zie BackpackInventory).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> PACK_ID =
            COMPONENTS.registerComponentType("pack_id", builder -> builder
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC));

    // ================================================================ Recepten

    /** Verven: de kleurstoffen van Minecraft (gemengd zoals leer), of bruin + lichtgroen + groen = camo. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BackpackDyeRecipe>> DYE_RECIPE =
            RECIPES.register("backpack_dye", () -> new SimpleCraftingRecipeSerializer<>(BackpackDyeRecipe::new));

    /** Bij het opstarten (ImtTech): alle lijsten aanmelden. */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        RECIPES.register(modEventBus);
    }
}
