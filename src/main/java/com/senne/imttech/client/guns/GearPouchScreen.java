package com.senne.imttech.client.guns;

import com.senne.imttech.ImtTech;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.GearPouchMenu;
import com.senne.imttech.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Het scherm van een open holster of pouch (hoofdstuk 54, zie GearPouchMenu): bovenaan zijn vakjes (met een vaag
 * voorbeeld in een leeg vakje, zodat je ziet wat erin hoort), eronder je inventaris. Dezelfde donkere stijl als de
 * andere schermen van de mod (het achtergrondplaatje van de laders, met alleen de inventarisvakjes erop).
 */
public class GearPouchScreen extends AbstractContainerScreen<GearPouchMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/gui/charger.png");
    private static final int FRAME = HudStyle.argb(255, 0x465460);
    private static final int SLOT = HudStyle.argb(255, 0x141A20);

    public GearPouchScreen(GearPouchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = -1000;
        titleLabelY = 6;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        HudStyle.brackets(g, x + 2, y + 2, x + imageWidth - 3, y + 80, 6, HudStyle.argb(255, HudStyle.ORANGE));
        ItemStack ghost = new ItemStack(switch (menu.kind()) {
            case HOLSTER -> ModItems.GLOCK_19.get();
            case GRENADE_POUCH -> ModItems.FRAG_GRENADE.get();
            case MAG_POUCH -> ModItems.GLOCK_19_MAGAZINE.get();
        });
        for (int i = 0; i < menu.kind().size(); i++) {
            Slot slot = menu.getSlot(i);
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT);
            g.renderOutline(sx - 1, sy - 1, 18, 18, FRAME);
            if (!slot.hasItem()) {
                g.renderFakeItem(ghost, sx, sy);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(sx, sy, sx + 16, sy + 16, HudStyle.argb(170, 0x141A20));
                g.pose().popPose();
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, HudStyle.argb(255, HudStyle.ORANGE), false);
        GearItem.Kind kind = menu.kind();
        String count = GearItem.filled(menu.gear()) + " / " + kind.size();
        g.drawString(font, count, imageWidth - 8 - font.width(count), titleLabelY, HudStyle.argb(220, HudStyle.WHITE), false);
        g.pose().pushPose();
        g.pose().translate(8, 60, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, Component.translatable("screen.imttech.pouch.hint_" + kind.id), 0, 0, HudStyle.argb(170, HudStyle.WHITE), false);
        g.drawString(font, Component.translatable("screen.imttech.pouch.hint_close"), 0, 10, HudStyle.argb(130, HudStyle.WHITE), false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
