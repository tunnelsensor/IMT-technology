package com.senne.imttech.workbench;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * De vier stukken van het hologram boven de werkbank: de drie stukken van het harnas, en sinds hoofdstuk 54 (jouw
 * notitie) ook de bril, boven de borstplaat (die bouw je hier niet, maar je legt hem erop en bouwt er upgrades in).
 *
 * Het hologram is een harnas op ware grootte (iets kleiner, SCALE) dat boven de werkbank zweeft
 * en langzaam ronddraait. Elk stuk heeft een vak waarin je kunt klikken ({@link #box()}), gemeten
 * vanaf de hoek van het werkbankblok (0..1 is het blok zelf, dus y 1.0 is de bovenkant van de
 * werkbank). Omdat het hologram draait, zijn de vakken zo breed dat het stuk er in elke stand in
 * past.
 *
 * <pre>
 *  y 2.84  ┌──────────┐
 *          │  bril    │   de bril, ter hoogte van de ogen
 *  y 2.50  ├──────────┤
 *          │ borst    │   borstplaat met armen (breed)
 *  y 1.80  └─┬──────┬─┘
 *            │ broek│
 *  y 1.36   ┌┴──────┴┐
 *           │laarzen │
 *  y 1.02   └────────┘
 *  y 1.00  ════════════  bovenkant van de werkbank
 * </pre>
 */
public enum HoloPiece {
    CHEST(Blueprint.CHESTPLATE, EquipmentSlot.CHEST, ArmorItem.Type.CHESTPLATE,
            new AABB(0.0, 1.80, 0.0, 1.0, 2.50, 1.0)),
    LEGS(Blueprint.LEGGINGS, EquipmentSlot.LEGS, ArmorItem.Type.LEGGINGS,
            new AABB(0.2, 1.36, 0.2, 0.8, 1.80, 0.8)),
    BOOTS(Blueprint.BOOTS, EquipmentSlot.FEET, ArmorItem.Type.BOOTS,
            new AABB(0.17, 1.02, 0.17, 0.83, 1.36, 0.83)),
    /** De bril (hoofdstuk 54): geen blauwdruk (die vind je in de toren), wel upgrades. */
    GLASSES(null, EquipmentSlot.HEAD, ArmorItem.Type.HELMET,
            new AABB(0.22, 2.50, 0.22, 0.78, 2.84, 0.78));

    /** Hoe groot het hologram is tegenover een echt harnas. */
    public static final float SCALE = 0.9f;
    /** Waar de voeten van het hologram zweven (net boven de werkbank). */
    public static final float FEET = 1.08f;

    private final @Nullable Blueprint blueprint;
    private final EquipmentSlot slot;
    private final ArmorItem.Type type;
    private final AABB box;

    HoloPiece(@Nullable Blueprint blueprint, EquipmentSlot slot, ArmorItem.Type type, AABB box) {
        this.blueprint = blueprint;
        this.slot = slot;
        this.type = type;
        this.box = box;
    }

    /** Wat je bouwt als je op dit stuk klikt terwijl het nog een hologram is (null: de bril bouw je hier niet). */
    public @Nullable Blueprint blueprint() {
        return blueprint;
    }

    /** Het volgende stuk (na het laatste weer het eerste): voor sneak + klik op de werkbank. */
    public HoloPiece next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Bij welk stuk hoort dit item: een stuk harnas, of de bril (anders null). */
    public static @Nullable HoloPiece ofItem(ItemStack stack) {
        if (com.senne.imttech.imt.ImtData.isGlasses(stack)) {
            return GLASSES;
        }
        return stack.getItem() instanceof com.senne.imttech.suit.TechSuitItem suit ? of(suit.getType()) : null;
    }

    /** Waar je dit stuk draagt. */
    public EquipmentSlot slot() {
        return slot;
    }

    /** Het klikvak, vanaf de hoek van het werkbankblok. */
    public AABB box() {
        return box;
    }

    /** De naam voor op het scherm ("Chestplate", ...). */
    public String langKey() {
        return "workbench.imttech.piece." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Bij welk stuk hoort dit soort harnas? (De bril niet: die is geen harnas, zie {@link #ofItem}.) */
    public static @Nullable HoloPiece of(ArmorItem.Type type) {
        for (HoloPiece piece : values()) {
            if (piece.type == type && piece != GLASSES) {
                return piece;
            }
        }
        return null;
    }

    public static @Nullable HoloPiece byId(int id) {
        HoloPiece[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }
}
