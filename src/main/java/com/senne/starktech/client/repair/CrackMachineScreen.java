package com.senne.starktech.client.repair;

import com.senne.starktech.StarkTech;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.repair.CrackMachineBlockEntity;
import com.senne.starktech.repair.CrackMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Het scherm van de Crusher (hoofdstuk 11).
 *
 * <pre>
 *  Crusher
 *        [erin]  =====>  [eruit]
 *  Crushing...
 * </pre>
 * Links het vakje erin (leeg: een vaag voorbeeld van wat erin hoort), rechts het vakje eruit, en
 * daartussen een pijl die vol loopt terwijl hij werkt. Onderaan wat hij doet, of waarom hij wacht.
 * De achtergrond is die van de laders (alleen je inventaris); de rest tekenen we hier zelf.
 */
public class CrackMachineScreen extends AbstractContainerScreen<CrackMachineMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/gui/charger.png");

    /** De pijl tussen de twee vakjes. */
    private static final int ARROW_X = 80, ARROW_Y = 37, ARROW_W = 26, ARROW_H = 11;

    // Dezelfde kleuren als het scherm van de laders.
    private static final int FRAME = HudStyle.argb(255, 0x465460);
    private static final int WELL = HudStyle.argb(255, 0x0E1216);
    private static final int SLOT = HudStyle.argb(255, 0x141A20);

    public CrackMachineScreen(CrackMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        // Geen "Inventory" boven je eigen vakjes: daar staat de regel met wat hij doet.
        inventoryLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        slot(g, x + CrackMachineMenu.IN_X, y + CrackMachineMenu.IN_Y,
                menu.input().isEmpty() ? menu.kind().example() : ItemStack.EMPTY);
        slot(g, x + CrackMachineMenu.OUT_X, y + CrackMachineMenu.OUT_Y, ItemStack.EMPTY);
        arrow(g, x + ARROW_X, y + ARROW_Y, menu.progress());
    }

    /** Een pijl naar rechts: een balk en een punt, die van links naar rechts blauw vol loopt. */
    private static void arrow(GuiGraphics g, int x, int y, float progress) {
        int shaft = ARROW_W - 7;
        int middle = y + ARROW_H / 2;
        g.fill(x, middle - 2, x + shaft, middle + 3, WELL);
        for (int i = 0; i < 7; i++) {
            g.fill(x + shaft + i, middle - 5 + i, x + shaft + i + 1, middle + 6 - i, WELL);
        }
        int filled = Math.round(ARROW_W * progress);
        int colour = HudStyle.argb(255, HudStyle.CYAN);
        g.fill(x, middle - 2, x + Math.min(filled, shaft), middle + 3, colour);
        for (int i = 0; i < 7 && shaft + i < filled; i++) {
            g.fill(x + shaft + i, middle - 5 + i, x + shaft + i + 1, middle + 6 - i, colour);
        }
    }

    /** Een vakje (18 x 18 rond de plek van het voorwerp), met desnoods een vaag voorbeeld erin. */
    private void slot(GuiGraphics g, int itemX, int itemY, ItemStack ghost) {
        g.fill(itemX - 1, itemY - 1, itemX + 17, itemY + 17, SLOT);
        g.renderOutline(itemX - 1, itemY - 1, 18, 18, FRAME);
        if (!ghost.isEmpty()) {
            g.renderFakeItem(ghost, itemX, itemY);
            // Een doorzichtige laag erover: dan is het een schim, geen echt voorwerp.
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(itemX, itemY, itemX + 16, itemY + 16, HudStyle.argb(170, 0x141A20));
            g.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    /** De naam bovenaan (licht, want de achtergrond is donker), en onderaan wat hij doet. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, HudStyle.argb(230, HudStyle.WHITE), false);
        CrackMachineBlockEntity.Status status = menu.status();
        String kind = menu.kind().getSerializedName();
        Component text = switch (status) {
            case WORKING -> Component.translatable("screen.starktech." + kind + ".working");
            case NO_POWER -> Component.translatable("screen.starktech.crack_machine.no_power");
            case FULL -> Component.translatable("screen.starktech.crack_machine.full");
            case EMPTY -> Component.translatable("screen.starktech." + kind + ".empty");
        };
        int colour = switch (status) {
            case WORKING -> HudStyle.argb(255, HudStyle.GREEN);
            case NO_POWER, FULL -> HudStyle.argb(230, HudStyle.ORANGE);
            case EMPTY -> HudStyle.argb(170, HudStyle.WHITE);
        };
        g.drawString(font, text, 8, 71, colour, false);
    }
}
