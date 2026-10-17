package com.senne.imttech.workbench;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * De drie stukken van het harnas-hologram boven de werkbank.
 *
 * Het hologram is een harnas op ware grootte (iets kleiner, SCALE) dat boven de werkbank zweeft
 * en langzaam ronddraait. Elk stuk heeft een vak waarin je kunt klikken ({@link #box()}), gemeten
 * vanaf de hoek van het werkbankblok (0..1 is het blok zelf, dus y 1.0 is de bovenkant van de
 * werkbank). Omdat het hologram draait, zijn de vakken zo breed dat het stuk er in elke stand in
 * past.
 *
 * <pre>
 *  y 2.56  ┌──────────┐
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
            new AABB(0.0, 1.80, 0.0, 1.0, 2.56, 1.0)),
    LEGS(Blueprint.LEGGINGS, EquipmentSlot.LEGS, ArmorItem.Type.LEGGINGS,
            new AABB(0.2, 1.36, 0.2, 0.8, 1.80, 0.8)),
    BOOTS(Blueprint.BOOTS, EquipmentSlot.FEET, ArmorItem.Type.BOOTS,
            new AABB(0.17, 1.02, 0.17, 0.83, 1.36, 0.83));

    /** Hoe groot het hologram is tegenover een echt harnas. */
    public static final float SCALE = 0.9f;
    /** Waar de voeten van het hologram zweven (net boven de werkbank). */
    public static final float FEET = 1.08f;

    private final Blueprint blueprint;
    private final EquipmentSlot slot;
    private final ArmorItem.Type type;
    private final AABB box;

    HoloPiece(Blueprint blueprint, EquipmentSlot slot, ArmorItem.Type type, AABB box) {
        this.blueprint = blueprint;
        this.slot = slot;
        this.type = type;
        this.box = box;
    }

    /** Wat je bouwt als je op dit stuk klikt terwijl het nog een hologram is. */
    public Blueprint blueprint() {
        return blueprint;
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

    /** Bij welk stuk hoort dit soort harnas? */
    public static @Nullable HoloPiece of(ArmorItem.Type type) {
        for (HoloPiece piece : values()) {
            if (piece.type == type) {
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
