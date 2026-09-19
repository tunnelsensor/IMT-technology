package com.senne.starktech.client;

import com.senne.starktech.StarkTech;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.station.ChargingStationMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Het scherm van het laadstation. De achtergrond is een plaatje
 * (textures/gui/charging_station.png); de balken tekenen we er zelf overheen.
 */
public class ChargingStationScreen extends AbstractContainerScreen<ChargingStationMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(StarkTech.MODID, "textures/gui/charging_station.png");

    // Posities van de balken (moeten kloppen met het plaatje).
    private static final int STATION_BAR_X = 10, STATION_BAR_Y = 17, STATION_BAR_W = 10, STATION_BAR_H = 52;
    private static final int GLASSES_BAR_X = 62, GLASSES_BAR_Y = 56, GLASSES_BAR_W = 52, GLASSES_BAR_H = 4;
    private static final int ARROW_X = 104, ARROW_Y = 37, ARROW_W = 26, ARROW_H = 8;

    public ChargingStationScreen(ChargingStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos;
        int y = topPos;
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0f, 0.0f, imageWidth, imageHeight, 256, 256);

        // Energie van het station: verticale balk die van onder naar boven vult.
        int stationFill = Math.round(STATION_BAR_H * menu.getStationEnergyFraction());
        if (stationFill > 0) {
            g.fillGradient(x + STATION_BAR_X, y + STATION_BAR_Y + STATION_BAR_H - stationFill,
                    x + STATION_BAR_X + STATION_BAR_W, y + STATION_BAR_Y + STATION_BAR_H,
                    EdithHud.argb(255, 0x7FEBFF), EdithHud.argb(255, 0x1283B5));
        }

        // Energie van de bril: horizontale balk onder het bril-slot.
        ItemStack glasses = menu.getGlasses();
        if (EdithData.isGlasses(glasses)) {
            int glassesFill = Math.round(GLASSES_BAR_W * EdithData.getEnergyFraction(glasses));
            g.fill(x + GLASSES_BAR_X, y + GLASSES_BAR_Y, x + GLASSES_BAR_X + glassesFill, y + GLASSES_BAR_Y + GLASSES_BAR_H,
                    EdithHud.argb(255, EdithHud.CYAN));
        }

        // Upgrade-pijl: vult van rechts (upgrade) naar links (bril).
        int arrowFill = Math.round(ARROW_W * menu.getUpgradeProgress());
        if (arrowFill > 0) {
            g.fill(x + ARROW_X + ARROW_W - arrowFill, y + ARROW_Y + 2, x + ARROW_X + ARROW_W, y + ARROW_Y + ARROW_H - 2,
                    EdithHud.argb(255, EdithHud.ORANGE));
        }
    }

    /** Titels in Stark-blauw in plaats van het standaard donkergrijs. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, EdithHud.argb(255, EdithHud.CYAN), false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, EdithHud.argb(255, 0x9FC9D6), false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);

        // Uitleg als je met de muis over een balk gaat.
        if (isHovering(STATION_BAR_X, STATION_BAR_Y, STATION_BAR_W, STATION_BAR_H, mouseX, mouseY)) {
            g.setTooltipForNextFrame(font, Component.translatable("screen.starktech.station.energy",
                    menu.getStationEnergy(), EdithBalance.STATION_CAPACITY), mouseX, mouseY);
        }
        ItemStack glasses = menu.getGlasses();
        if (EdithData.isGlasses(glasses) && isHovering(GLASSES_BAR_X, GLASSES_BAR_Y - 2, GLASSES_BAR_W, GLASSES_BAR_H + 4, mouseX, mouseY)) {
            g.setTooltipForNextFrame(font, Component.translatable("screen.starktech.station.glasses_energy",
                    EdithData.getEnergy(glasses), EdithData.getMaxEnergy(glasses)), mouseX, mouseY);
        }
        if (isHovering(ARROW_X, ARROW_Y, ARROW_W, ARROW_H, mouseX, mouseY)) {
            g.setTooltipForNextFrame(font, Component.translatable("screen.starktech.station.upgrade_hint"), mouseX, mouseY);
        }
    }
}
