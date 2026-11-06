package com.senne.imttech.client.guns;

import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.MagazineItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * De kant van jouw spel van de holster en de pouches (hoofdstuk 52, stap 2): R met een magazijnpouch opent het
 * keuzescherm (zie MagPickerScreen); zonder pouch (of met een lege) gooit R het magazijn uit het pistool.
 */
public final class GunGearClient {
    private GunGearClient() {}

    /** R met een magazijnpouch: het keuzescherm openen. Geeft false als je geen pouch (met magazijnen) hebt. */
    public static boolean openMagazinePicker(LocalPlayer player, ItemStack gun) {
        FirearmType type = FirearmItem.typeOf(gun);
        if (type == null || magazinesInPouch(player, type) == 0) {
            return false;
        }
        Minecraft.getInstance().setScreen(new MagPickerScreen(type));
        return true;
    }

    /** Zoveel magazijnen (met patronen) voor dit pistool zitten er in je magazijnpouch. */
    public static int magazinesInPouch(LocalPlayer player, FirearmType type) {
        int count = 0;
        for (ItemStack stack : Gear.contents(player, GearItem.Kind.MAG_POUCH)) {
            if (MagazineItem.typeOf(stack) == type && MagazineItem.rounds(stack) > 0) {
                count++;
            }
        }
        return count;
    }
}
