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
 * Linker Alt (hoofdstuk 52, stap 2): het wiel openen; sinds hoofdstuk 56 staat het er alleen zolang je Alt vasthoudt (het
 * scherm zelf sluit bij het loslaten, zie GearWheelScreen). En sinds hoofdstuk 54 (jouw notitie) de eigen toetsen voor elke
 * quickdraw (het pistool, een granaat, en sinds hoofdstuk 55 het geweer op je rug of voor je borst) en om elke pouch
 * te openen; standaard zitten die op geen toets.
 */
public final class GearControl {
    private GearControl() {}

    private static boolean wasDown;
    /**
     * Na het sluiten moet je Alt eerst echt loslaten voor het wiel opnieuw opent (hoofdstuk 56): Minecraft zet een
     * ingehouden toets bij elke toetsherhaling weer "aan", en anders sprong het wiel meteen weer open na een klik op een
     * tegel of Esc.
     */
    private static boolean waitForRelease;

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
        if (mc.screen instanceof GearWheelScreen) {
            waitForRelease = true;
        } else if (waitForRelease && !GearWheelScreen.keyHeld()) {
            waitForRelease = false;
        }
        if (down && !wasDown && mc.screen == null && !waitForRelease) {
            mc.setScreen(new GearWheelScreen());
        }
        wasDown = down;

        while (ImtKeys.DRAW_GUN.consumeClick()) {
            send(Gear.ACTION_DRAW_GUN, 0);
        }
        while (ImtKeys.DRAW_SLING_BACK.consumeClick()) {
            drawSling(player, 0);
        }
        while (ImtKeys.DRAW_SLING_CHEST.consumeClick()) {
            drawSling(player, 1);
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

    /** Het geweer uit dit sling-vakje trekken (hoofdstuk 55); leeg = een melding, zonder de server lastig te vallen. */
    private static void drawSling(LocalPlayer player, int index) {
        if (Gear.sling(player, index).isEmpty()) {
            player.displayClientMessage(Component.translatable("gear.imttech.no_sling_gun").withStyle(ChatFormatting.RED), true);
        } else {
            send(Gear.ACTION_DRAW_SLING, index);
        }
    }

    private static void send(int action, int arg) {
        PacketDistributor.sendToServer(new GearActionPayload(action, arg));
    }

    /** De soort van de eerste granaat in je pouch (als nummer), of -1 als hij leeg is. */
    private static int firstGrenadeKind(LocalPlayer player) {
        // Hoofdstuk 72, stap 2: ook de granaten in je MOLLE-granaatpouches.
        for (ItemStack stack : com.senne.imttech.backpack.Molle.grenades(player)) {
            if (stack.getItem() instanceof GrenadeItem grenade) {
                return grenade.kind().ordinal();
            }
        }
        return -1;
    }
}
