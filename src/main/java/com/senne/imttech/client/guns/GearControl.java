package com.senne.imttech.client.guns;

import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.network.GearActionPayload;
import com.senne.imttech.weapons.GrenadeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Linker Alt (hoofdstuk 52, stap 2): het wiel openen. En sinds hoofdstuk 54 (jouw notitie) de eigen toetsen voor elke
 * quickdraw (het pistool, een granaat) en om elke pouch te openen; standaard zitten die op geen toets.
 */
public final class GearControl {
    private GearControl() {}

    private static boolean wasDown;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            wasDown = false;
            return;
        }
        while (ImtKeys.GEAR_WHEEL.consumeClick()) {
            // De klikken zelf hoeven we niet: we kijken of de toets net ingedrukt werd.
        }
        boolean down = ImtKeys.GEAR_WHEEL.isDown();
        if (down && !wasDown && mc.screen == null) {
            mc.setScreen(new GearWheelScreen());
        }
        wasDown = down;

        while (ImtKeys.DRAW_GUN.consumeClick()) {
            send(Gear.ACTION_DRAW_GUN, 0);
        }
        while (ImtKeys.DRAW_GRENADE.consumeClick()) {
            int kind = firstGrenadeKind(player);
            if (kind < 0) {
                player.displayClientMessage(Component.translatable("gear.imttech.no_grenades").withStyle(ChatFormatting.RED), true);
            } else {
                send(Gear.ACTION_DRAW_GRENADE, kind);
            }
        }
        while (ImtKeys.OPEN_HOLSTER.consumeClick()) {
            send(Gear.ACTION_OPEN, GearItem.Kind.HOLSTER.ordinal());
        }
        while (ImtKeys.OPEN_GRENADE_POUCH.consumeClick()) {
            send(Gear.ACTION_OPEN, GearItem.Kind.GRENADE_POUCH.ordinal());
        }
        while (ImtKeys.OPEN_MAG_POUCH.consumeClick()) {
            send(Gear.ACTION_OPEN, GearItem.Kind.MAG_POUCH.ordinal());
        }
    }

    private static void send(int action, int arg) {
        PacketDistributor.sendToServer(new GearActionPayload(action, arg));
    }

    /** De soort van de eerste granaat in je pouch (als nummer), of -1 als hij leeg is. */
    private static int firstGrenadeKind(LocalPlayer player) {
        for (ItemStack stack : Gear.contents(player, GearItem.Kind.GRENADE_POUCH)) {
            if (stack.getItem() instanceof GrenadeItem grenade) {
                return grenade.kind().ordinal();
            }
        }
        return -1;
    }
}
