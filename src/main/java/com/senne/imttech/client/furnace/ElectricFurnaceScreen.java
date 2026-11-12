package com.senne.imttech.client.furnace;

import com.senne.imttech.ImtTech;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.furnace.ElectricFurnaceBlockEntity;
import com.senne.imttech.furnace.ElectricFurnaceMenu;
import com.senne.imttech.registry.ModItems;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Het scherm van de Electric Furnace (hoofdstuk 87).
 *
 * <pre>
 *  Electric Furnace
 *        [erin]  =====&gt;  [eruit]
 *                /\/\/\/\
 *  Smelting...
 * </pre>
 * Links het vakje erin (leeg: om de beurt een vaag ruw ijzer, zand en polymeer, zodat je ziet wat erin kan), rechts het
 * vakje eruit, en daartussen een pijl die oranje vol loopt terwijl hij smelt. Onder de pijl de gloeispiraal: oranje als hij
 * werkt, donker als hij wacht. Onderaan wat hij doet, of waarom hij wacht. De achtergrond is die van de laders (alleen je
 * inventaris); de rest tekenen we hier zelf, in dezelfde stijl als de Crusher.
 */
public class ElectricFurnaceScreen extends AbstractContainerScreen<ElectricFurnaceMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/gui/charger.png");

    /** De pijl tussen de twee vakjes (net als bij de Crusher). */
    private static final int ARROW_X = 80, ARROW_Y = 37, ARROW_W = 26, ARROW_H = 11;
    /** De gloeispiraal onder de pijl. */
    private static final int COIL_X = 80, COIL_Y = 51, COIL_W = 26;

    // Dezelfde kleuren als het scherm van de laders en de Crusher.
    private static final int FRAME = HudStyle.argb(255, 0x465460);
    private static final int WELL = HudStyle.argb(255, 0x0E1216);
    private static final int SLOT = HudStyle.argb(255, 0x141A20);
    private static final int GLOW = HudStyle.argb(255, 0xFF8A2A);
    private static final int GLOW_HOT = HudStyle.argb(255, 0xFFD25A);

    public ElectricFurnaceScreen(ElectricFurnaceMenu menu, Inventory inventory, Component title) {
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

        slot(g, x + ElectricFurnaceMenu.IN_X, y + ElectricFurnaceMenu.IN_Y, menu.input().isEmpty() ? example() : ItemStack.EMPTY);
        slot(g, x + ElectricFurnaceMenu.OUT_X, y + ElectricFurnaceMenu.OUT_Y, ItemStack.EMPTY);
        arrow(g, x + ARROW_X, y + ARROW_Y, menu.progress());
        coil(g, x + COIL_X, y + COIL_Y, menu.status() == ElectricFurnaceBlockEntity.Status.WORKING);
    }

    /** Het vage voorbeeld in het lege vakje erin: elke anderhalve seconde het volgende. */
    private static ItemStack example() {
        List<ItemStack> examples = List.of(new ItemStack(Items.RAW_IRON), new ItemStack(Items.SAND),
                new ItemStack(ModItems.POLYMER.get()));
        return examples.get((int) (Util.getMillis() / 1500 % examples.size()));
    }

    /** Een pijl naar rechts: een balk en een punt, die van links naar rechts oranje vol loopt. */
    private static void arrow(GuiGraphics g, int x, int y, float progress) {
        int shaft = ARROW_W - 7;
        int middle = y + ARROW_H / 2;
        g.fill(x, middle - 2, x + shaft, middle + 3, WELL);
        for (int i = 0; i < 7; i++) {
            g.fill(x + shaft + i, middle - 5 + i, x + shaft + i + 1, middle + 6 - i, WELL);
        }
        int filled = Math.round(ARROW_W * progress);
        g.fill(x, middle - 2, x + Math.min(filled, shaft), middle + 3, GLOW);
        for (int i = 0; i < 7 && shaft + i < filled; i++) {
            g.fill(x + shaft + i, middle - 5 + i, x + shaft + i + 1, middle + 6 - i, GLOW);
        }
    }

    /**
     * De gloeispiraal: een zigzag van 2 pixels dik over de breedte van de pijl. Werkt hij, dan gloeit hij oranje, met
     * lichtere punten die langzaam opschuiven (zo zie je dat hij heet is); anders is hij donker.
     */
    private static void coil(GuiGraphics g, int x, int y, boolean hot) {
        int shift = (int) (Util.getMillis() / 120 % 6);
        for (int i = 0; i < COIL_W; i++) {
            int t = i % 6;
            int dy = t < 3 ? t : 6 - t;
            int colour = !hot ? WELL : (i + shift) % 6 == 0 ? GLOW_HOT : GLOW;
            g.fill(x + i, y + dy, x + i + 1, y + dy + 2, colour);
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

    /**
     * De naam bovenaan (licht, want de achtergrond is donker), en onderaan wat hij doet. Is die regel te lang voor het
     * scherm, dan breekt hij af over twee regels (de onderste blijft op dezelfde plek).
     */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, HudStyle.argb(230, HudStyle.WHITE), false);
        ElectricFurnaceBlockEntity.Status status = menu.status();
        Component text = switch (status) {
            case WORKING -> Component.translatable("screen.imttech.electric_furnace.working");
            case NO_POWER -> Component.translatable("screen.imttech.crack_machine.no_power");
            case FULL -> Component.translatable("screen.imttech.crack_machine.full");
            case EMPTY -> Component.translatable("screen.imttech.electric_furnace.empty");
        };
        int colour = switch (status) {
            case WORKING -> HudStyle.argb(255, HudStyle.GREEN);
            case NO_POWER, FULL -> HudStyle.argb(230, HudStyle.ORANGE);
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
