package com.senne.imttech.client.guns;

import com.senne.imttech.ImtTech;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.CrushedGunpowderItem;
import com.senne.imttech.guns.ReloadingBenchBlockEntity;
import com.senne.imttech.guns.ReloadingBenchMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Het scherm van de Reloading Bench (hoofdstuk 52): drie vakjes op een rij (huls, kogelpunt, en sinds hoofdstuk 76
 * Crushed Gunpowder), een pijl naar de pers, en het vakje met de patroon die klaarligt. Onder de vakjes staat hoeveel je
 * er nog kunt persen.
 */
public class ReloadingBenchScreen extends AbstractContainerScreen<ReloadingBenchMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/gui/charger.png");
    private static final int FRAME = HudStyle.argb(255, 0x465460);
    private static final int SLOT = HudStyle.argb(255, 0x1E2128);

    public ReloadingBenchScreen(ReloadingBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        int[] xs = {ReloadingBenchMenu.CASING_X, ReloadingBenchMenu.BULLET_X, ReloadingBenchMenu.POWDER_X, ReloadingBenchMenu.RESULT_X};
        for (int i = 0; i < xs.length; i++) {
            int sx = x + xs[i];
            int sy = y + ReloadingBenchMenu.INPUT_Y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT);
            g.renderOutline(sx - 1, sy - 1, 18, 18, i == 3 ? HudStyle.argb(255, HudStyle.ORANGE) : FRAME);
        }
        // Plusjes tussen de ingrediënten en een pijl naar de pers.
        int py = y + ReloadingBenchMenu.INPUT_Y + 7;
        for (int px : new int[]{x + ReloadingBenchMenu.CASING_X + 18, x + ReloadingBenchMenu.BULLET_X + 18}) {
            g.fill(px + 1, py + 1, px + 5, py + 2, FRAME);
            g.fill(px + 2, py, px + 3, py + 3, FRAME);
        }
        int ax = x + ReloadingBenchMenu.POWDER_X + 22;
        g.fill(ax, py, ax + 30, py + 2, FRAME);
        g.fill(ax + 26, py - 2, ax + 28, py + 4, FRAME);
        g.fill(ax + 28, py - 1, ax + 30, py + 3, FRAME);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, HudStyle.argb(255, HudStyle.ORANGE), false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, HudStyle.argb(200, HudStyle.WHITE), false);
        int label = HudStyle.argb(170, HudStyle.ORANGE);
        small(g, "CASING", ReloadingBenchMenu.CASING_X - 3, ReloadingBenchMenu.INPUT_Y - 9, label);
        small(g, "BULLET", ReloadingBenchMenu.BULLET_X - 3, ReloadingBenchMenu.INPUT_Y - 9, label);
        small(g, "POWDER", ReloadingBenchMenu.POWDER_X - 4, ReloadingBenchMenu.INPUT_Y - 9, label);
        small(g, "9MM", ReloadingBenchMenu.RESULT_X + 2, ReloadingBenchMenu.INPUT_Y - 9, label);
        int count = ReloadingBenchBlockEntity.pressable(menu.items());
        Component line = count == 0 ? Component.translatable("screen.imttech.reloading_bench.need")
                : Component.translatable("screen.imttech.reloading_bench.ready", count);
        // Te lang voor één regel (sinds hoofdstuk 76 staat er "crushed gunpowder"): dan op twee regels, nooit over de rand.
        int left = ReloadingBenchMenu.CASING_X - 4;
        List<FormattedCharSequence> lines = font.split(line, Math.round((imageWidth - left - 8) / 0.75f));
        int colour = HudStyle.argb(220, count == 0 ? HudStyle.WHITE : HudStyle.GREEN);
        g.pose().pushPose();
        g.pose().translate(left, ReloadingBenchMenu.INPUT_Y + 22, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), 0, i * 9, colour, false);
        }
        g.pose().popPose();
    }

    private void small(GuiGraphics g, String text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.6f, 0.6f, 1.0f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        Slot slot = hoveredSlot;
        if (slot != null && !slot.hasItem() && slot.index <= ReloadingBenchMenu.RESULT_SLOT && menu.getCarried().isEmpty()) {
            String what = switch (slot.index) {
                case ReloadingBenchBlockEntity.SLOT_CASING -> "9mm Casing";
                case ReloadingBenchBlockEntity.SLOT_BULLET -> "9mm Bullet";
                case ReloadingBenchBlockEntity.SLOT_POWDER -> "Crushed Gunpowder (Crusher: 1 gunpowder = "
                        + CrushedGunpowderItem.perGunpowder() + ")";
                default -> "The pressed 9mm Round comes here (shift-click for a stack)";
            };
            g.renderTooltip(font, Component.literal(what), mouseX, mouseY);
            return;
        }
        super.renderTooltip(g, mouseX, mouseY);
    }
}
