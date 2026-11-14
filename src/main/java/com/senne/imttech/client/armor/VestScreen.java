package com.senne.imttech.client.armor;

import com.senne.imttech.armor.ArmorRegistry;
import com.senne.imttech.armor.PlateTier;
import com.senne.imttech.armor.VestData;
import com.senne.imttech.armor.VestMenu;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Het scherm van een Plate Carrier (hoofdstuk 109, sinds hoofdstuk 111 eenvoudiger, jouw wens: "geen 3d beeld van de armor,
 * de plates vakjes naast elkaar en de mags daarnaast, de groene tekst weg, je ziet de damage enkel op het item zelf"): op één
 * rij de plate voor, de plate achter en de drie magazijnen, met een klein woord erboven; eronder je inventaris. Hoe versleten
 * een plate is, zie je aan het balkje op de plate. De tekst op je rug, verven en herstellen doe je op de Armor Workbench.
 * Dezelfde donkere stijl als de andere schermen van de mod.
 */
public class VestScreen extends AbstractContainerScreen<VestMenu> {
    private static final int PANEL = 0x10141A;
    private static final int FRAME = 0x465460;
    private static final int SLOT = 0x1A2028;

    public VestScreen(VestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = VestMenu.HEIGHT;
        inventoryLabelY = VestMenu.INVENTORY_Y - 11;
        titleLabelY = 6;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, HudStyle.argb(245, PANEL));
        g.renderOutline(x, y, imageWidth, imageHeight, HudStyle.argb(255, FRAME));
        HudStyle.brackets(g, x + 2, y + 2, x + imageWidth - 3, y + VestMenu.INVENTORY_Y - 14, 6, HudStyle.argb(255, HudStyle.ORANGE));
        for (Slot slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, HudStyle.argb(255, SLOT));
            g.renderOutline(sx - 1, sy - 1, 18, 18, HudStyle.argb(255, FRAME));
        }
        // Een vaag voorbeeld in een leeg vakje: een plate, of een magazijn.
        for (int i = 0; i < VestMenu.SIZE; i++) {
            Slot slot = menu.getSlot(i);
            if (!slot.hasItem()) {
                ItemStack ghost = i < VestMenu.MAGS ? new ItemStack(ArmorRegistry.plate(PlateTier.STEEL))
                        : new ItemStack(ModItems.GLOCK_19_MAGAZINE.get());
                int sx = x + slot.x;
                int sy = y + slot.y;
                g.renderFakeItem(ghost, sx, sy);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(sx, sy, sx + 16, sy + 16, HudStyle.argb(180, SLOT));
                g.pose().popPose();
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, HudStyle.argb(255, HudStyle.ORANGE), false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, HudStyle.argb(170, HudStyle.WHITE), false);
        int labelY = VestMenu.ROW_Y - 10;
        small(g, Component.translatable("screen.imttech.vest.plates"), VestMenu.FRONT_X - 1, labelY);
        small(g, Component.translatable("screen.imttech.vest.mags_label"), VestMenu.MAGS_X - 1, labelY);
        // Onder de twee plates, elk in het midden onder zijn vakje: welke voor en welke achter zit.
        centred(g, Component.translatable("screen.imttech.vest.front_short"), VestMenu.FRONT_X + 8, VestMenu.ROW_Y + 20);
        centred(g, Component.translatable("screen.imttech.vest.back_short"), VestMenu.BACK_X + 8, VestMenu.ROW_Y + 20);
    }

    /** Kleine tekst (driekwart) met zijn midden op x. */
    private void centred(GuiGraphics g, Component text, int x, int y) {
        small(g, text, Math.round(x - font.width(text) * 0.75f / 2), y);
    }

    /** Kleine tekst (driekwart). */
    private void small(GuiGraphics g, Component text, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, text, 0, 0, HudStyle.argb(200, HudStyle.WHITE), false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
