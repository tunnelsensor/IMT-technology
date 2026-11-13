package com.senne.imttech.backpack;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.Caliber;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.raven.RavenArms;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.LaserGunItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * De rugzakken van de Raven-soldaten (hoofdstuk 72, stap 5, jouw keuze: "Raven-soldaten in de bunker dragen soms een
 * Assault Pack; als ze gevallen zijn, vind je er buit in"). Een soldaat krijgt bij het verschijnen soms een antracietgrijze Assault
 * Pack op zijn rug ({@code [backpacks] ravenPackChance}); valt hij, dan zit die vol buit en komt hij in zijn lichaam (met de
 * rest van wat hij laat vallen, zie raven/Bodies). Wat erin zit: munitie voor het wapen dat hij droeg (patronen van zijn
 * kaliber en soms een magazijn van zijn wapen, of een energiecel), eten, soms een onderdeel van zijn wapen, en sinds
 * hoofdstuk 103 (stap 8, jouw keuze: "attachments in buit") af en toe een los attachment.
 */
public final class RavenPacks {
    private RavenPacks() {}

    /** De kleur van een Raven-rugzak: antracietgrijs (zwart zag je op hun zwarte uniform amper). */
    public static final int RAVEN_GREY = 0x45484D;

    /** Een lege antracietgrijze Assault Pack (voor op de rug van een soldaat). */
    public static ItemStack pack() {
        ItemStack pack = new ItemStack(BackpackRegistry.ASSAULT_PACK.get());
        pack.set(DataComponents.DYED_COLOR, new DyedItemColor(RAVEN_GREY, true));
        return pack;
    }

    /**
     * Dezelfde rugzak, nu vol buit (als de soldaat valt). {@code weapon}: het wapen dat hij droeg; daar is de munitie voor
     * (een lasergeweer: een energiecel; zonder wapen: 9mm, zoals voor een Glock).
     */
    public static ItemStack filled(ItemStack pack, RandomSource random, ItemStack weapon) {
        ItemStack out = pack.copy();
        List<ItemStack> loot = new ArrayList<>();
        boolean laser = LaserGunItem.typeOf(weapon) != null;
        FirearmType carried = FirearmItem.typeOf(weapon);
        FirearmType type = carried == null ? FirearmType.GLOCK_19 : carried;
        if (laser) {
            loot.add(EnergyCellItem.withCharge(30 + random.nextInt(51)));
        } else {
            loot.add(new ItemStack(type.caliber.round(), rounds(type.caliber, random)));
            // Soms een magazijn van zijn wapen, voor een derde tot helemaal vol (de Remington 870 heeft er geen).
            if (!type.tube() && random.nextFloat() < 0.4f) {
                int least = Math.max(1, type.capacity() / 3);
                loot.add(MagazineItem.of(type, least + random.nextInt(type.capacity() - least + 1)));
            }
        }
        loot.add(random.nextBoolean() ? new ItemStack(Items.BREAD, 2 + random.nextInt(4)) : new ItemStack(Items.COOKED_BEEF, 1 + random.nextInt(3)));
        if (random.nextFloat() < 0.15f) {
            // Een onderdeel van zijn wapen (een lasersoldaat heeft een onderdeel van een Glock bij zich), zoals het in het
            // echte wapen zit: bij de Glock een polymeer frame en trekker, de rest staal.
            GunPart[] parts = GunPart.values();
            GunPart part = parts[random.nextInt(parts.length)];
            PartState real = GunParts.classic(type).get(part);
            if (real != null) {
                loot.add(GunPartItem.of(type, part, real.material()));
            }
        }
        if (random.nextFloat() < 0.3f) {
            loot.add(new ItemStack(Items.TORCH, 4 + random.nextInt(9)));
        }
        // Hoofdstuk 103, stap 8: af en toe een los attachment (een red dot, een greep, een demper...).
        if (random.nextFloat() < ImtConfig.Raven.PACK_ATTACHMENT_CHANCE) {
            loot.add(RavenArms.lootAttachment(random));
        }
        // Over de vakjes verspreid, zoals iemand een rugzak inpakt.
        List<ItemStack> slots = new ArrayList<>();
        int size = BackpackItem.sizeOf(out) == null ? 18 : BackpackItem.sizeOf(out).slots();
        for (int i = 0; i < size; i++) {
            slots.add(ItemStack.EMPTY);
        }
        for (ItemStack stack : loot) {
            int at = random.nextInt(size);
            while (!slots.get(at).isEmpty()) {
                at = (at + 1) % size;
            }
            slots.set(at, stack);
        }
        BackpackItem.setContents(out, slots);
        return out;
    }

    /** Hoeveel losse patronen van dit kaliber: veel kleine 9mm, weinig van de grote .338. */
    private static int rounds(Caliber caliber, RandomSource random) {
        return switch (caliber) {
            case MM9 -> 12 + random.nextInt(21);
            case R762 -> 10 + random.nextInt(16);
            case L338 -> 3 + random.nextInt(6);
            case G12 -> 4 + random.nextInt(9);
        };
    }
}
