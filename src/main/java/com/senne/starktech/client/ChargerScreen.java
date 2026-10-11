package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.StarkTech;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.station.ChargerMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Het scherm van het zonnepaneel en de warmtegenerator.
 *
 * <pre>
 *  [balk]   Glasses [bril]     STORED
 *  [    ]   ======= (balk)     1234 / 5000
 *  [    ]   Fuel    [kolen]    MADE SO FAR
 *  [    ]                      56789
 *  Producing: 4 per tick                    [/tick]
 * </pre>
 * Links een verticale balk met wat het apparaat zelf heeft opgeslagen, in het midden je bril met
 * daaronder zijn eigen balk, rechts de getallen, en onderaan wat het nu oplevert (of waarom het
 * niets doet: nacht, geen brandstof, geen hitte).
 *
 * Alleen de warmtegenerator heeft een vakje voor brandstof; het zonnepaneel niet. Die vakjes
 * tekenen we hier zelf (niet in het achtergrondplaatje), met een vaag voorbeeld erin zolang ze
 * leeg zijn, zodat je ziet wat erin hoort.
 */
public class ChargerScreen extends AbstractContainerScreen<ChargerMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/gui/charger.png");

    private static final int BUFFER_BAR_X = 10, BUFFER_BAR_Y = 17, BUFFER_BAR_W = 10, BUFFER_BAR_H = 52;
    private static final int GLASSES_BAR_X = 62, GLASSES_BAR_Y = 45, GLASSES_BAR_W = 52, GLASSES_BAR_H = 4;
    /** Waar de getallen rechts beginnen (zo dat "20000 / 20000" er nog net in past). */
    private static final int STATS_X = 118;

    // Dezelfde kleuren als het achtergrondplaatje (tools/generate_textures.py).
    private static final int FRAME = HudStyle.argb(255, 0x465460);
    private static final int WELL = HudStyle.argb(255, 0x0E1216);
    private static final int SLOT = HudStyle.argb(255, 0x141A20);

    public ChargerScreen(ChargerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        // Geen "Inventory" boven je eigen vakjes: daar staat nu de regel met wat het oplevert.
        inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        // Rechtsboven: per tick, per seconde, per minuut of per uur (geldt voor alle stroomschermen).
        addRenderableWidget(PowerUnit.button(leftPos + imageWidth - 7 - 34, topPos + 4, 34, 12));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        // Wat het apparaat zelf heeft opgeslagen.
        box(g, x + BUFFER_BAR_X - 1, y + BUFFER_BAR_Y - 1, BUFFER_BAR_W + 2, BUFFER_BAR_H + 2, WELL);
        float fraction = capacity() <= 0 ? 0 : (float) menu.getEnergy() / capacity();
        int fill = Math.round(BUFFER_BAR_H * Math.min(1.0f, fraction));
        if (fill > 0) {
            g.fillGradient(x + BUFFER_BAR_X, y + BUFFER_BAR_Y + BUFFER_BAR_H - fill,
                    x + BUFFER_BAR_X + BUFFER_BAR_W, y + BUFFER_BAR_Y + BUFFER_BAR_H,
                    HudStyle.argb(255, 0xFFE07F), HudStyle.argb(255, 0xB56B12));
        }

        // De bril die erin ligt (of een vaag voorbeeld), met zijn eigen balk eronder.
        ItemStack glasses = menu.getGlasses();
        slot(g, x + ChargerMenu.GLASSES_X, y + ChargerMenu.GLASSES_Y,
                glasses.isEmpty() ? new ItemStack(ModItems.EDITH_GLASSES.get()) : ItemStack.EMPTY);
        g.fill(x + GLASSES_BAR_X, y + GLASSES_BAR_Y, x + GLASSES_BAR_X + GLASSES_BAR_W,
                y + GLASSES_BAR_Y + GLASSES_BAR_H, WELL);
        if (EdithData.isGlasses(glasses)) {
            float glassesFraction = (float) EdithData.getEnergy(glasses) / EdithData.getMaxEnergy(glasses);
            int width = Math.round(GLASSES_BAR_W * Math.min(1.0f, glassesFraction));
            if (width > 0) {
                g.fill(x + GLASSES_BAR_X, y + GLASSES_BAR_Y,
                        x + GLASSES_BAR_X + width, y + GLASSES_BAR_Y + GLASSES_BAR_H,
                        HudStyle.argb(255, HudStyle.CYAN));
            }
        }

        // Alleen de warmtegenerator: het vakje voor brandstof.
        if (menu.usesFuel()) {
            boolean empty = menu.getSlot(1).getItem().isEmpty();
            slot(g, x + ChargerMenu.FUEL_X, y + ChargerMenu.FUEL_Y, empty ? new ItemStack(Items.COAL) : ItemStack.EMPTY);
        }
    }

    /** Een vakje (18 x 18 rond de plek van het voorwerp), met desnoods een vaag voorbeeld erin. */
    private void slot(GuiGraphics g, int itemX, int itemY, ItemStack ghost) {
        box(g, itemX - 1, itemY - 1, 18, 18, SLOT);
        if (!ghost.isEmpty()) {
            g.renderFakeItem(ghost, itemX, itemY);
            // Een doorzichtige laag erover: dan is het een schim, geen echt voorwerp.
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(itemX, itemY, itemX + 16, itemY + 16, HudStyle.argb(170, 0x141A20));
            g.pose().popPose();
        }
    }

    /** Een vlak met een randje. */
    private static void box(GuiGraphics g, int x, int y, int width, int height, int colour) {
        g.fill(x, y, x + width, y + height, colour);
        g.renderOutline(x, y, width, height, FRAME);
    }

    private int capacity() {
        return menu.getCapacity();
    }

    /**
     * Ga je met je muis over de oranje balk, dan zie je de getallen: hoeveel er nu in zit,
     * hoeveel erin past, en hoeveel dit apparaat in totaal al heeft opgewekt.
     */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        ItemStack glasses = menu.getGlasses();
        if (EdithData.isGlasses(glasses) && over(mouseX, mouseY, GLASSES_BAR_X, GLASSES_BAR_Y,
                GLASSES_BAR_W, GLASSES_BAR_H)) {
            g.renderTooltip(font, Component.translatable("screen.starktech.charger.glasses",
                    EdithData.getEnergy(glasses), EdithData.getMaxEnergy(glasses)), mouseX, mouseY);
        }
        if (overBuffer(mouseX, mouseY)) {
            g.renderComponentTooltip(font, List.<Component>of(
                    Component.translatable("screen.starktech.charger.stored", menu.getEnergy(), capacity()),
                    Component.translatable("screen.starktech.charger.total", menu.getTotalProduced()),
                    status()), mouseX, mouseY);
        }
    }

    /** Staat de muis op de energiebalk van het apparaat? */
    private boolean overBuffer(int mouseX, int mouseY) {
        return over(mouseX, mouseY, BUFFER_BAR_X, BUFFER_BAR_Y, BUFFER_BAR_W, BUFFER_BAR_H);
    }

    /** Staat de muis binnen dit vlak van het scherm? */
    private boolean over(int mouseX, int mouseY, int x, int y, int width, int height) {
        int left = leftPos + x;
        int top = topPos + y;
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    /** Wat het nu oplevert, of waarom het niets doet. */
    private Component status() {
        float rate = menu.getRate();
        return rate > 0
                ? Component.translatable("screen.starktech.charger.rate", PowerUnit.current().rate(rate))
                : Component.translatable(menu.usesFuel()
                        ? "screen.starktech.charger.idle_heat"
                        : "screen.starktech.charger.idle_solar");
    }

    /** De namen bij de vakjes, de getallen rechts, en onderaan wat het nu oplevert. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        int label = HudStyle.argb(170, HudStyle.WHITE);
        smallRight(g, Component.translatable("screen.starktech.charger.slot_glasses").getString(),
                ChargerMenu.GLASSES_X - 4, ChargerMenu.GLASSES_Y + 5, label);
        if (menu.usesFuel()) {
            smallRight(g, Component.translatable("screen.starktech.charger.slot_fuel").getString(),
                    ChargerMenu.FUEL_X - 4, ChargerMenu.FUEL_Y + 5, label);
        }

        int dim = HudStyle.argb(150, HudStyle.ORANGE);
        int value = HudStyle.argb(230, HudStyle.WHITE);
        small(g, Component.translatable("screen.starktech.charger.label_stored").getString().toUpperCase(),
                STATS_X, 20, dim);
        small(g, menu.getEnergy() + " / " + capacity(), STATS_X, 28, value);
        small(g, Component.translatable("screen.starktech.charger.label_total").getString().toUpperCase(),
                STATS_X, 42, dim);
        small(g, String.valueOf(menu.getTotalProduced()), STATS_X, 50, value);

        g.drawString(font, status(), 8, 71, menu.getRate() > 0 ? HudStyle.argb(255, HudStyle.GREEN)
                : HudStyle.argb(200, HudStyle.ORANGE), false);
    }

    /** Kleine tekst (75%), links uitgelijnd. */
    private void small(GuiGraphics g, String text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    /** Kleine tekst (75%), rechts uitgelijnd tegen {@code right}. */
    private void smallRight(GuiGraphics g, String text, int right, int y, int colour) {
        small(g, text, right - (int) Math.ceil(font.width(text) * 0.75f), y, colour);
    }
}
