package com.senne.imttech.armor;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModItems;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * De vier soorten plate (hoofdstuk 109), van zwak naar sterk, met de materialen van de mod (dezelfde als bij de
 * wapenonderdelen). Jouw wens: "hoe sterker de plate, hoe minder damage je neemt". Achter elke soort staat het echte
 * beschermingsniveau (NIJ) in de tooltip, zodat je ziet wat hij ongeveer zou tegenhouden:
 * <ul>
 *   <li><b>Polymer</b>: Level IIIA (een zachte plate, houdt pistoolkogels tegen), goedkoop;</li>
 *   <li><b>Steel</b>: Level III (stopt geweerkogels), zwaar en taai;</li>
 *   <li><b>Titanium</b>: Level III+;</li>
 *   <li><b>Netherite</b>: Level IV, het beste wat er is.</li>
 * </ul>
 * Hoeveel elke soort tegenhoudt en hoe lang hij meegaat, staat in de config ([armor]); een gebarsten plate herstel je op
 * een aambeeld met zijn eigen materiaal (zie PlateItem).
 */
public enum PlateTier {
    POLYMER("polymer", "IIIA", 0xDAD6C7),
    STEEL("steel", "III", 0x8F969E),
    TITANIUM("titanium", "III+", 0xA4BCD2),
    NETHERITE("netherite", "IV", 0x4C4448);

    public final String id;
    /** Het echte beschermingsniveau (NIJ), voor in de tooltip. */
    public final String level;
    /** De kleur van het materiaal (voor het plaatje en de rand in de HUD). */
    public final int colour;

    PlateTier(String id, String level, int colour) {
        this.id = id;
        this.level = level;
        this.colour = colour;
    }

    /** Het item-id van deze plate: polymer_plate, steel_plate... */
    public String itemId() {
        return id + "_plate";
    }

    /** Zoveel van een treffer houdt hij tegen (0 tot 0,95), uit de config. */
    public float reduction() {
        float[] values = ImtConfig.Armor.PLATE_REDUCTION;
        return Mth.clamp(ordinal() < values.length ? values[ordinal()] : 0, 0, 0.95f);
    }

    /** Zoveel schade (halve hartjes) houdt hij in totaal tegen voor hij barst, uit de config. */
    public float durability() {
        float[] values = ImtConfig.Armor.PLATE_DURABILITY;
        return Math.max(1, ordinal() < values.length ? values[ordinal()] : 1);
    }

    /** Waarmee je hem op een aambeeld herstelt: zijn eigen materiaal. */
    public Item repairItem() {
        return switch (this) {
            case POLYMER -> ModItems.POLYMER.get();
            case STEEL -> ModItems.STEEL_INGOT.get();
            case TITANIUM -> ModItems.TITANIUM_INGOT.get();
            case NETHERITE -> Items.NETHERITE_INGOT;
        };
    }
}
