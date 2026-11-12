package com.senne.imttech.client.plastic;

import com.senne.imttech.ImtTech;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.plastic.PolymerizerBlockEntity;
import com.senne.imttech.plastic.PolymerizerMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Het scherm van de Polymerizer (hoofdstuk 85).
 *
 * <pre>
 *  Polymerizer
 *   |~~|  [kool ]
 *   |~~|          =====&gt;  [eruit]
 *   |~~|  [emmer]
 *  Making polymer...
 * </pre>
 * Links de watertank (blauw, loopt van onder vol; met de muis erop zie je hoeveel mB), dan het koolvakje en daaronder het
 * emmervakje (leeg: een vage kool en een vage emmer water), een pijl die vol loopt terwijl hij werkt, en rechts het
 * polymeer. Onderaan wat hij doet, of waarom hij wacht. De achtergrond is die van de laders (alleen je inventaris); de rest
 * tekenen we hier zelf, in dezelfde stijl als de Crusher.
 */
public class PolymerizerScreen extends AbstractContainerScreen<PolymerizerMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/gui/charger.png");

    /** De pijl tussen de vakjes erin en het vakje eruit (midden op de hoogte van het vakje eruit). */
    private static final int ARROW_X = 80, ARROW_Y = 31, ARROW_W = 26, ARROW_H = 11;
    /** De watertank: even hoog als de twee vakjes erin samen. */
    private static final int TANK_X = 32, TANK_Y = 17, TANK_W = 14, TANK_H = 39;

    // Dezelfde kleuren als het scherm van de laders en de Crusher.
    private static final int FRAME = HudStyle.argb(255, 0x465460);
    private static final int WELL = HudStyle.argb(255, 0x0E1216);
    private static final int SLOT = HudStyle.argb(255, 0x141A20);
    private static final int WATER = HudStyle.argb(255, 0x3F76E4);
    private static final int WATER_TOP = HudStyle.argb(255, 0x6F9CF5);

    public PolymerizerScreen(PolymerizerMenu menu, Inventory inventory, Component title) {
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

        tank(g, x + TANK_X, y + TANK_Y);
        slot(g, x + PolymerizerMenu.COAL_X, y + PolymerizerMenu.COAL_Y,
                menu.coal().isEmpty() ? new ItemStack(Items.COAL) : ItemStack.EMPTY);
        slot(g, x + PolymerizerMenu.BUCKET_X, y + PolymerizerMenu.BUCKET_Y,
                menu.bucket().isEmpty() ? new ItemStack(Items.WATER_BUCKET) : ItemStack.EMPTY);
        slot(g, x + PolymerizerMenu.OUT_X, y + PolymerizerMenu.OUT_Y, ItemStack.EMPTY);
        arrow(g, x + ARROW_X, y + ARROW_Y, menu.progress());
    }

    /** De watertank: een donkere bak met een rand, van onder blauw gevuld, en streepjes per emmer. */
    private void tank(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + TANK_W, y + TANK_H, WELL);
        g.renderOutline(x - 1, y - 1, TANK_W + 2, TANK_H + 2, FRAME);
        int filled = Math.round(TANK_H * Math.min(1.0f, (float) menu.water() / menu.tank()));
        if (filled > 0) {
            g.fill(x, y + TANK_H - filled, x + TANK_W, y + TANK_H, WATER);
            g.fill(x, y + TANK_H - filled, x + TANK_W, y + TANK_H - filled + 1, WATER_TOP);
        }
        // Een streepje per emmer (1000 mB), zodat je ziet hoeveel emmers erin passen.
        int buckets = menu.tank() / 1000;
        for (int i = 1; i < buckets && buckets <= 16; i++) {
            int lineY = y + TANK_H - Math.round(TANK_H * (i * 1000.0f / menu.tank()));
            g.fill(x, lineY, x + 3, lineY + 1, FRAME);
        }
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
        // Met de muis op de tank: hoeveel water erin zit.
        int tx = leftPos + TANK_X;
        int ty = topPos + TANK_Y;
        if (mouseX >= tx - 1 && mouseX <= tx + TANK_W && mouseY >= ty - 1 && mouseY <= ty + TANK_H) {
            g.renderTooltip(font, Component.translatable("screen.imttech.polymerizer.water", menu.water(), menu.tank()),
                    mouseX, mouseY);
        }
    }

    /**
     * De naam bovenaan (licht, want de achtergrond is donker), en onderaan wat hij doet. Is die regel te lang voor het
     * scherm, dan breekt hij af over twee regels (de onderste blijft op dezelfde plek).
     */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, HudStyle.argb(230, HudStyle.WHITE), false);
        PolymerizerBlockEntity.Status status = menu.status();
        Component text = switch (status) {
            case WORKING -> Component.translatable("screen.imttech.polymerizer.working");
            case NO_WATER -> Component.translatable("screen.imttech.polymerizer.no_water");
            case NO_POWER -> Component.translatable("screen.imttech.crack_machine.no_power");
            case FULL -> Component.translatable("screen.imttech.crack_machine.full");
            case EMPTY -> Component.translatable("screen.imttech.polymerizer.empty");
        };
        int colour = switch (status) {
            case WORKING -> HudStyle.argb(255, HudStyle.GREEN);
            case NO_WATER, NO_POWER, FULL -> HudStyle.argb(230, HudStyle.ORANGE);
            case EMPTY -> HudStyle.argb(170, HudStyle.WHITE);
        };
        List<FormattedCharSequence> lines = font.split(text, imageWidth - 16);
        int lineY = 71 - (lines.size() - 1) * 10;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, 8, lineY, colour, false);
            lineY += 10;
        }
    }
}
