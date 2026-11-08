package com.senne.imttech.workbench;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * De vier stukken van het hologram boven de werkbank: de drie stukken van het harnas, en sinds hoofdstuk 54 (jouw
 * notitie) ook de bril (die bouw je hier niet, maar je legt hem erop en bouwt er upgrades in).
 *
 * Boven de werkbank zweeft één stuk tegelijk: het gekozen stuk (hoofdstuk 62), op ware grootte (iets kleiner,
 * SCALE) en langzaam ronddraaiend. Jouw tweede opmerking van hoofdstuk 62: "zorg dat elk ding op dezelfde hoogte
 * staat als je erdoor scrolt, een halve block boven de workbench". Daarom zweeft elk stuk met zijn ONDERKANT op
 * {@link #HOVER}, een halve blok boven de werkbank, en niet meer op zijn plek op een heel harnas (toen zaten de
 * laarzen vlak op de werkbank en de bril hoog in de lucht).
 *
 * Elk stuk heeft een vak waarin je kunt klikken ({@link #box()}), gemeten vanaf de hoek van het werkbankblok (0..1
 * is het blok zelf, dus y 1.0 is de bovenkant van de werkbank). Omdat het stuk draait, is het vak zo breed dat het
 * er in elke stand in past, en een klein stuk (de bril) krijgt een wat hoger vak, zodat je niet hoeft te mikken.
 *
 * <pre>
 *  y 2.40  ┌──────────┐   tot hier komt het hoogste stuk (de borstplaat met de armen; de broek bijna even hoog)
 *          │ gekozen  │
 *          │   stuk   │   de laarzen tot y 1.89, de bril tot 1.71
 *  y 1.50  └──────────┘   de onderkant van elk stuk: een halve blok boven de werkbank
 *
 *  y 1.00  ════════════   bovenkant van de werkbank
 * </pre>
 *
 * Hoe hoog een stuk is, staat in pixels van het model ({@code top} en {@code bottom}: hoeveel pixels onder de nek
 * van het harnas het zichtbare deel begint en eindigt; een blok is 16 pixels). Gemeten aan TechSuitModel en aan
 * de plaatjes: de borstplaat van de schouderplaten (-1.8) tot de laser cannons onder de handen (13.5), de broek van
 * de riem (9.25, alleen de onderste rijen van het lijf hebben pixels) tot onder de benen (24.4), de laarzen van het
 * zichtbare deel van het been (18) tot de zool (25), en de bril alleen de band om de ogen (-5.25 tot -1.5).
 */
public enum HoloPiece {
    CHEST(Blueprint.CHESTPLATE, EquipmentSlot.CHEST, ArmorItem.Type.CHESTPLATE, 0.5f, -1.8f, 13.5f),
    LEGS(Blueprint.LEGGINGS, EquipmentSlot.LEGS, ArmorItem.Type.LEGGINGS, 0.3f, 9.25f, 24.4f),
    BOOTS(Blueprint.BOOTS, EquipmentSlot.FEET, ArmorItem.Type.BOOTS, 0.33f, 18.0f, 25.0f),
    /** De bril (hoofdstuk 54): geen blauwdruk (die vind je in de toren), wel upgrades. */
    GLASSES(null, EquipmentSlot.HEAD, ArmorItem.Type.HELMET, 0.28f, -5.25f, -1.5f);

    /** Hoe groot het hologram is tegenover een echt harnas. */
    public static final float SCALE = 0.9f;
    /** Een pixel van het model in blokken, op de grootte van het hologram. */
    public static final float PIXEL = SCALE / 16.0f;
    /** Waar de onderkant van elk stuk zweeft: een halve blok boven de werkbank (de bovenkant is y 1.0). */
    public static final float HOVER = 1.5f;
    /** Een klein stuk krijgt toch een klikvak van zo hoog (in blokken). */
    private static final float MIN_CLICK_HEIGHT = 0.35f;
    /** Zoveel ruimte rond het stuk telt nog als erop klikken (in blokken). */
    private static final float CLICK_MARGIN = 0.04f;

    private final @Nullable Blueprint blueprint;
    private final EquipmentSlot slot;
    private final ArmorItem.Type type;
    /** Hoeveel pixels onder de nek de onderkant van het zichtbare deel ligt. */
    private final float bottom;
    private final AABB box;

    HoloPiece(@Nullable Blueprint blueprint, EquipmentSlot slot, ArmorItem.Type type, float halfWidth, float top,
              float bottom) {
        this.blueprint = blueprint;
        this.slot = slot;
        this.type = type;
        this.bottom = bottom;
        float height = (bottom - top) * PIXEL;
        this.box = new AABB(0.5 - halfWidth, HOVER - CLICK_MARGIN, 0.5 - halfWidth,
                0.5 + halfWidth, HOVER + Math.max(height + CLICK_MARGIN, MIN_CLICK_HEIGHT), 0.5 + halfWidth);
    }

    /**
     * Op welke hoogte de nek van het harnasmodel moet staan (daar zit het nulpunt van het model), zodat de onderkant
     * van dit stuk precies op {@link #HOVER} zweeft.
     */
    public float neckY() {
        return HOVER + bottom * PIXEL;
    }

    /** Wat je bouwt als je op dit stuk klikt terwijl het nog een hologram is (null: de bril bouw je hier niet). */
    public @Nullable Blueprint blueprint() {
        return blueprint;
    }

    /** Het volgende stuk (na het laatste weer het eerste): een klik op de werkbank (hoofdstuk 62). */
    public HoloPiece next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Het vorige stuk (voor het eerste weer het laatste): sneak + klik op de werkbank (hoofdstuk 62). */
    public HoloPiece previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
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

    /** Het klikvak, vanaf de hoek van het werkbankblok (de onderkant net onder {@link #HOVER}). */
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
