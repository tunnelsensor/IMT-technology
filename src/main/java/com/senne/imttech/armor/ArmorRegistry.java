package com.senne.imttech.armor;

import com.senne.imttech.ImtTech;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Alles wat Minecraft van de body armor (hoofdstuk 109) moet kennen, op één plek: het vest, de vier plates, wat er in een
 * vest zit (een data component), het scherm en de twee geluiden. Net als de rugzakken (BackpackRegistry) heeft dit zijn eigen
 * lijsten, die {@link #register} bij het opstarten aanmeldt; voor Minecraft zijn het gewoon items van imttech.
 */
public final class ArmorRegistry {
    private ArmorRegistry() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ImtTech.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ImtTech.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ImtTech.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ImtTech.MODID);

    // ================================================================ Het vest en de plates

    /** De Plate Carrier: het vest (zie VestItem). */
    public static final DeferredItem<VestItem> PLATE_CARRIER = ITEMS.registerItem("plate_carrier", VestItem::new,
            new Item.Properties().rarity(Rarity.UNCOMMON));

    /** De vier plates, van zwak naar sterk (zie PlateTier). */
    private static final Map<PlateTier, DeferredItem<PlateItem>> PLATES = new EnumMap<>(PlateTier.class);

    static {
        for (PlateTier tier : PlateTier.values()) {
            Item.Properties properties = new Item.Properties();
            if (tier == PlateTier.TITANIUM) {
                properties.rarity(Rarity.UNCOMMON);
            } else if (tier == PlateTier.NETHERITE) {
                properties.rarity(Rarity.RARE).fireResistant();
            }
            PLATES.put(tier, ITEMS.registerItem(tier.itemId(), p -> new PlateItem(p, tier), properties));
        }
    }

    /** De plate van deze soort. */
    public static PlateItem plate(PlateTier tier) {
        return PLATES.get(tier).get();
    }

    // ================================================================ Data components en het scherm

    /** Wat er in een vest zit: de twee plates, de drie magazijnen en de tekst op je rug (zie VestData). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VestData>> VEST =
            COMPONENTS.registerComponentType("vest", builder -> builder
                    .persistent(VestData.CODEC)
                    .networkSynchronized(VestData.STREAM_CODEC));

    /** Het scherm van een vest (zie VestMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<VestMenu>> VEST_MENU =
            MENUS.register("plate_carrier", () -> IMenuTypeExtension.create(VestMenu::new));

    // ================================================================ Geluiden (tools/generate_sounds.py)

    /** Een treffer op een plate: een korte, doffe metalen tik (zo hoor je dat je vest het tegenhield, of dat jij een vest raakte). */
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATE_HIT = sound("plate_hit");
    /** Een plate barst: een droge krak. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATE_CRACK = sound("plate_crack");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, name)));
    }

    /** Bij het opstarten (Vests.register): alle lijsten aanmelden. */
    static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        SOUNDS.register(modEventBus);
    }
}
