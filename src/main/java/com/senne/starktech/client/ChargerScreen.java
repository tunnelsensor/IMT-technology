package com.senne.starktech.client;

import com.senne.starktech.StarkTech;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.station.ChargerMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Het scherm van het zonnepaneel en de warmtegenerator.
 *
 * Links een verticale balk met wat het apparaat zelf nog heeft opgeslagen, in het midden je
 * bril met daaronder zijn eigen balk, en eronder de tekst hoeveel het op dit moment oplevert.
 * Levert het niets op (nacht, geen brandstof, geen hitte), dan zie je dat meteen.
 */
public class ChargerScreen extends AbstractContainerScreen<ChargerMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/gui/charger.png");

    private static final int BUFFER_BAR_X = 10, BUFFER_BAR_Y = 17, BUFFER_BAR_W = 10, BUFFER_BAR_H = 52;
    private static final int GLASSES_BAR_X = 62, GLASSES_BAR_Y = 47, GLASSES_BAR_W = 52, GLASSES_BAR_H = 4;

    public ChargerScreen(ChargerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        // Wat het apparaat zelf heeft opgeslagen.
        float fraction = capacity() <= 0 ? 0 : (float) menu.getEnergy() / capacity();
        int fill = Math.round(BUFFER_BAR_H * Math.min(1.0f, fraction));
        if (fill > 0) {
            g.fillGradient(x + BUFFER_BAR_X, y + BUFFER_BAR_Y + BUFFER_BAR_H - fill,
                    x + BUFFER_BAR_X + BUFFER_BAR_W, y + BUFFER_BAR_Y + BUFFER_BAR_H,
                    EdithHud.argb(255, 0xFFE07F), EdithHud.argb(255, 0xB56B12));
        }

        // De bril die erin ligt.
        ItemStack glasses = menu.getGlasses();
        if (EdithData.isGlasses(glasses)) {
            float glassesFraction = (float) EdithData.getEnergy(glasses) / EdithData.getMaxEnergy(glasses);
            int width = Math.round(GLASSES_BAR_W * Math.min(1.0f, glassesFraction));
            g.fill(x + GLASSES_BAR_X, y + GLASSES_BAR_Y,
                    x + GLASSES_BAR_X + GLASSES_BAR_W, y + GLASSES_BAR_Y + GLASSES_BAR_H,
                    EdithHud.argb(120, 0x10323F));
            if (width > 0) {
                g.fill(x + GLASSES_BAR_X, y + GLASSES_BAR_Y,
                        x + GLASSES_BAR_X + width, y + GLASSES_BAR_Y + GLASSES_BAR_H,
                        EdithHud.argb(255, EdithHud.CYAN));
            }
        }
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
                    menu.getRate() > 0
                            ? Component.translatable("screen.starktech.charger.rate", menu.getRate())
                            : Component.translatable(menu.usesFuel()
                                    ? "screen.starktech.charger.idle_heat"
                                    : "screen.starktech.charger.idle_solar")),
                    mouseX, mouseY);
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

    /** Onder de balk: wat het nu oplevert, of waarom het niets doet. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        int rate = menu.getRate();
        Component line = rate > 0
                ? Component.translatable("screen.starktech.charger.rate", rate)
                : Component.translatable(menu.usesFuel()
                        ? "screen.starktech.charger.idle_heat"
                        : "screen.starktech.charger.idle_solar");
        g.drawString(font, line, 8, 70, rate > 0 ? EdithHud.argb(255, EdithHud.GREEN)
                : EdithHud.argb(200, EdithHud.ORANGE), false);
    }
}
