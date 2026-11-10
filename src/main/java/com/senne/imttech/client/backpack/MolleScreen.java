package com.senne.imttech.client.backpack;

import com.senne.imttech.ImtTech;
import com.senne.imttech.backpack.MolleItem;
import com.senne.imttech.backpack.MolleMenu;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Het scherm van een open MOLLE-pouch (hoofdstuk 72, stap 2, zie MolleMenu): bovenaan zijn vakjes (met een vaag voorbeeld
 * in een leeg vakje, zodat je ziet wat erin hoort), eronder je inventaris. Dezelfde stijl als het scherm van de holster en
 * de pouches (GearPouchScreen).
 */
public class MolleScreen extends AbstractContainerScreen<MolleMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/gui/charger.png");
    private static final int FRAME = HudStyle.argb(255, 0x465460);
    private static final int SLOT = HudStyle.argb(255, 0x141A20);

    public MolleScreen(MolleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = -1000;
        titleLabelY = 6;
    }

    /** Wat er in een leeg vakje hoort, als vaag plaatje. */
    private ItemStack ghost() {
        return switch (menu.kind()) {
            case MAG -> MagazineItem.of(FirearmType.GLOCK_19, 15);
            case GRENADE -> new ItemStack(ModItems.FRAG_GRENADE.get());
            case DUMP -> new ItemStack(ModItems.CASING_9MM.get());
            case IFAK -> new ItemStack(Items.GOLDEN_APPLE);
            case UTILITY -> new ItemStack(Items.SHEARS);
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        HudStyle.brackets(g, x + 2, y + 2, x + imageWidth - 3, y + 80, 6, HudStyle.argb(255, HudStyle.ORANGE));
        ItemStack ghost = ghost();
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
        MolleItem.Kind kind = menu.kind();
        String count = MolleItem.filled(menu.pouch()) + " / " + kind.size();
        g.drawString(font, count, imageWidth - 8 - font.width(count), titleLabelY, HudStyle.argb(220, HudStyle.WHITE), false);
        g.pose().pushPose();
        g.pose().translate(8, 60, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, Component.translatable("screen.imttech.molle.hint_" + kind.id), 0, 0, HudStyle.argb(170, HudStyle.WHITE), false);
        g.drawString(font, Component.translatable("screen.imttech.molle.hint_close"), 0, 10, HudStyle.argb(130, HudStyle.WHITE), false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
