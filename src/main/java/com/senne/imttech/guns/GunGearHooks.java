package com.senne.imttech.guns;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Waar de holster en de pouches (hoofdstuk 52, stap 2) aan de vuurwapens hangen: herladen uit de magazijnpouch, en een
 * uitgeworpen magazijn in de pouch stoppen. Zie {@link Gear}.
 */
public final class GunGearHooks {
    private GunGearHooks() {}

    /** R met een magazijnpouch: dit magazijn ({@code index}) uit de pouch erin. Geeft true als het begint. */
    public static boolean reloadFromPouch(ServerPlayer player, int index) {
        return Gear.reloadFromPouch(player, index);
    }

    /** Het herladen uit de pouch is klaar: wisselen. */
    public static void finishPouchReload(ServerPlayer player, ItemStack gun, int index) {
        Gear.finishPouchReload(player, gun, index);
    }

    /** Een uitgeworpen magazijn in de pouch stoppen; geeft false als dat niet kan (dan gaat het in je inventaris). */
    public static boolean storeMagazine(ServerPlayer player, ItemStack magazine) {
        return Gear.storeMagazine(player, magazine);
    }
}
