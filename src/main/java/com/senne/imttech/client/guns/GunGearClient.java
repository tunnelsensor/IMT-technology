package com.senne.imttech.client.guns;

import com.senne.imttech.guns.FirearmType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * De kant van jouw spel van de holster en de pouches (hoofdstuk 52, stap 2). Zolang die er nog niet zijn: er is geen
 * magazijnpouch, dus R gooit het magazijn uit het pistool.
 */
public final class GunGearClient {
    private GunGearClient() {}

    /** R met een magazijnpouch: het keuzescherm openen. Geeft false als je geen pouch (met magazijnen) hebt. */
    public static boolean openMagazinePicker(LocalPlayer player, ItemStack gun) {
        return false;
    }

    /** Zoveel magazijnen (met patronen) voor dit pistool zitten er in je magazijnpouch. */
    public static int magazinesInPouch(LocalPlayer player, FirearmType type) {
        return 0;
    }
}
