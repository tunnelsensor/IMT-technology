package com.senne.imttech.backpack;

import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.EnergyCellItem;
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
 * rest van wat hij laat vallen, zie raven/Bodies). Wat erin zit: munitie voor zijn wapen (patronen en soms een magazijn, of
 * een energiecel), eten, en soms een onderdeel van een Glock.
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

    /** Dezelfde rugzak, nu vol buit (als de soldaat valt). {@code laser}: had hij een lasergeweer (dan een cel)? */
    public static ItemStack filled(ItemStack pack, RandomSource random, boolean laser) {
        ItemStack out = pack.copy();
        List<ItemStack> loot = new ArrayList<>();
        if (laser) {
            loot.add(EnergyCellItem.withCharge(30 + random.nextInt(51)));
        } else {
            loot.add(new ItemStack(ModItems.ROUND_9MM.get(), 12 + random.nextInt(21)));
            if (random.nextFloat() < 0.4f) {
                loot.add(MagazineItem.of(FirearmType.GLOCK_19, 5 + random.nextInt(11)));
            }
        }
        loot.add(random.nextBoolean() ? new ItemStack(Items.BREAD, 2 + random.nextInt(4)) : new ItemStack(Items.COOKED_BEEF, 1 + random.nextInt(3)));
        if (random.nextFloat() < 0.15f) {
            GunPart[] parts = GunPart.values();
            loot.add(GunPartItem.of(parts[random.nextInt(parts.length)], GunMaterial.STEEL));
        }
        if (random.nextFloat() < 0.3f) {
            loot.add(new ItemStack(Items.TORCH, 4 + random.nextInt(9)));
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
}
