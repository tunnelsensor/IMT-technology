package com.senne.imttech.client.howto;

import com.senne.imttech.client.hud.HudStyle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Het knopje "How it works" in je eigen inventaris (hoofdstuk 63, jouw antwoord 1): rechtsboven, boven het craftraster.
 * Het opent het overzicht van alle onderwerpen. Net als de eigen vakjes van de holster (GearSlotsOverlay) is het geen
 * echte knop van het inventarisscherm: we tekenen het erbij en vangen de klik. Zo schuift het vanzelf mee als het
 * receptenboek opengaat.
 */
public final class HowToButton {
    private HowToButton() {}

    /** Waar het knopje staat, gerekend vanaf de linkerbovenhoek van het inventarisscherm. */
    private static final int X = 158;
    private static final int Y = 5;
    private static final int SIZE = 12;

    private static boolean over(InventoryScreen screen, double mouseX, double mouseY) {
        int x = screen.getGuiLeft() + X;
        int y = screen.getGuiTop() + Y;
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }

    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int x = screen.getGuiLeft() + X;
        int y = screen.getGuiTop() + Y;
        boolean over = over(screen, event.getMouseX(), event.getMouseY());
        g.pose().pushPose();
        // Boven de voorwerpen in het scherm (die tekent Minecraft een stukje naar voren).
        g.pose().translate(0, 0, 250);
        g.fill(x, y, x + SIZE, y + SIZE, HudStyle.argb(255, over ? 0x1A2733 : 0x0E1216));
        g.renderOutline(x, y, SIZE, SIZE, HudStyle.argb(255, over ? HudStyle.CYAN : 0x465460));
        g.drawString(Minecraft.getInstance().font, "?", x + 4, y + 2, HudStyle.argb(255, HudStyle.CYAN), false);
        g.pose().popPose();
        if (over) {
            List<FormattedCharSequence> lines = List.of(
                    Component.translatable("screen.imttech.howto.button").getVisualOrderText(),
                    Component.translatable("screen.imttech.howto.button.info").withStyle(net.minecraft.ChatFormatting.GRAY)
                            .getVisualOrderText());
            g.renderTooltip(Minecraft.getInstance().font, lines, event.getMouseX(), event.getMouseY());
        }
    }

    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == 0 && event.getScreen() instanceof InventoryScreen screen
                && over(screen, event.getMouseX(), event.getMouseY())) {
            HowToScreen.show(new HowToIndexScreen());
            event.setCanceled(true);
        }
    }
}
