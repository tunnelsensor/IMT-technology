package com.senne.imttech.client.guns;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.network.GearClickPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * De drie eigen vakjes in je inventaris (hoofdstuk 52, stap 2, jouw wens: "als je die mod (Curios) niet hebt, in een
 * slot dat mijn mod toevoegt"): rechts van je poppetje, onder elkaar: de holster, de granaatpouch en de magazijnpouch.
 * Ze zijn geen echte vakjes van het inventarisscherm (dat is van Minecraft), maar we tekenen ze erbij en vangen de
 * klik: wat je aan je muis hebt gaat erin, wat erin zat komt aan je muis (zie Gear.click). Met Curios erbij zie je ze
 * niet: dan zitten ze in de Curios-slots.
 */
public final class GearSlotsOverlay {
    private GearSlotsOverlay() {}

    private static final int X = 77;
    private static final int Y = 8;
    private static final int STEP = 18;

    private static boolean shown(Screen screen) {
        return screen instanceof InventoryScreen && !Gear.curios();
    }

    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || !shown(screen)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int x0 = screen.getGuiLeft() + X;
        int y0 = screen.getGuiTop() + Y;
        ItemStack hoveredStack = ItemStack.EMPTY;
        GearItem.Kind hoveredKind = null;
        for (GearItem.Kind kind : GearItem.Kind.values()) {
            int y = y0 + kind.ordinal() * STEP;
            ItemStack worn = Gear.get(player, kind);
            boolean hover = event.getMouseX() >= x0 && event.getMouseX() < x0 + 18 && event.getMouseY() >= y && event.getMouseY() < y + 18;
            g.fill(x0, y, x0 + 18, y + 18, HudStyle.argb(255, 0x1E2128));
            g.renderOutline(x0, y, 18, 18, HudStyle.argb(255, hover ? HudStyle.CYAN : 0x465460));
            if (worn.isEmpty()) {
                // Een vaag plaatje van wat erin hoort.
                g.pose().pushPose();
                g.pose().translate(x0 + 1, y + 1, 0);
                g.renderItem(new ItemStack(switch (kind) {
                    case HOLSTER -> com.senne.imttech.registry.ModItems.HOLSTER.get();
                    case GRENADE_POUCH -> com.senne.imttech.registry.ModItems.GRENADE_POUCH.get();
                    case MAG_POUCH -> com.senne.imttech.registry.ModItems.MAG_POUCH.get();
                }), 0, 0);
                g.fill(0, 0, 16, 16, HudStyle.argb(170, 0x1E2128));
                g.pose().popPose();
            } else {
                g.renderItem(worn, x0 + 1, y + 1);
                int filled = GearItem.filled(worn);
                if (filled > 0) {
                    g.pose().pushPose();
                    g.pose().translate(x0 + 12, y + 11, 200);
                    g.pose().scale(0.6f, 0.6f, 1);
                    g.drawString(Minecraft.getInstance().font, String.valueOf(filled), 0, 0, HudStyle.argb(255, 0xD6AC58), true);
                    g.pose().popPose();
                }
            }
            if (hover) {
                hoveredStack = worn;
                hoveredKind = kind;
            }
        }
        if (hoveredKind != null && screen.getMenu().getCarried().isEmpty()) {
            if (hoveredStack.isEmpty()) {
                g.renderTooltip(Minecraft.getInstance().font, Component.translatable("screen.imttech.gear_slot." + hoveredKind.id),
                        event.getMouseX(), event.getMouseY());
            } else {
                g.renderTooltip(Minecraft.getInstance().font, Screen.getTooltipFromItem(Minecraft.getInstance(), hoveredStack),
                        hoveredStack.getTooltipImage(), event.getMouseX(), event.getMouseY());
            }
        }
    }

    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || !shown(screen) || event.getButton() != 0) {
            return;
        }
        int x0 = screen.getGuiLeft() + X;
        int y0 = screen.getGuiTop() + Y;
        for (GearItem.Kind kind : GearItem.Kind.values()) {
            int y = y0 + kind.ordinal() * STEP;
            if (event.getMouseX() >= x0 && event.getMouseX() < x0 + 18 && event.getMouseY() >= y && event.getMouseY() < y + 18) {
                PacketDistributor.sendToServer(new GearClickPayload(kind.ordinal()));
                event.setCanceled(true);
                return;
            }
        }
    }
}
