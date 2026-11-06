package com.senne.imttech.client.guns;

import com.senne.imttech.client.ImtKeys;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Linker Alt (hoofdstuk 52, stap 2): het wiel openen. */
public final class GearControl {
    private GearControl() {}

    private static boolean wasDown;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
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
    }
}
