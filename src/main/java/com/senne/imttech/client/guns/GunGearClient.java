package com.senne.imttech.client.guns;

import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * De kant van jouw spel van de holster en de pouches (hoofdstuk 52, stap 2): R met een magazijnpouch opent het
 * keuzescherm (zie MagPickerScreen); zonder pouch (of met een lege) gooit R het magazijn uit het pistool. Sinds
 * hoofdstuk 55 ook voor een lasergeweer: R met een cel in je pouch die voller is dan die in het geweer toont de cellen.
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

    /**
     * R met een lasergeweer (hoofdstuk 55): het keuzescherm van de cellen in je pouch openen. Geeft false als er geen cel
     * in je pouch zit die voller is dan de cel in het geweer (dan pakt R de volste uit je inventaris, zoals altijd).
     */
    public static boolean openCellPicker(LocalPlayer player, ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        if (type == null || cellsInPouch(player, LaserGunItem.cell(gun)) == 0) {
            return false;
        }
        Minecraft.getInstance().setScreen(MagPickerScreen.cells(type));
        return true;
    }

    /** Zoveel cellen in je magazijnpouch zijn voller dan {@code current} (de cel in het geweer; -1 = geen). */
    public static int cellsInPouch(LocalPlayer player, float current) {
        int count = 0;
        for (ItemStack stack : Gear.contents(player, GearItem.Kind.MAG_POUCH)) {
            if (stack.is(ModItems.ENERGY_CELL.get()) && EnergyCellItem.charge(stack) >= Math.max(0.001f, current + 0.001f)) {
                count++;
            }
        }
        return count;
    }
}
