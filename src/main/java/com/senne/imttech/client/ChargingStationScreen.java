package com.senne.imttech.client;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.station.ChargingStationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Het scherm van het laadstation. De achtergrond is een plaatje (textures/gui/charging_station.png); de balk tekenen
 * we er zelf overheen. Sinds hoofdstuk 54 (jouw notitie) is het kaal: links de balk met wat het station heeft, in het
 * midden het vakje voor de bril (of een energiecel) met ernaast hoe vol die is. Geen redstone-vakje, geen balk onder
 * de bril en geen upgrade-pijl meer: upgrades bouw je in aan de harnas-werkbank.
 */
public class ChargingStationScreen extends AbstractContainerScreen<ChargingStationMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/gui/charging_station.png");

    // De plek van de balk (moet kloppen met het plaatje).
    private static final int STATION_BAR_X = 10, STATION_BAR_Y = 17, STATION_BAR_W = 10, STATION_BAR_H = 52;

    public ChargingStationScreen(ChargingStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    /** De achtergrond: eerst het plaatje, dan onze eigen balk erover. */
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        // Energie van het station: verticale balk die van onder naar boven vult.
        int stationFill = Math.round(STATION_BAR_H * menu.getStationEnergyFraction());
        if (stationFill > 0) {
            g.fillGradient(x + STATION_BAR_X, y + STATION_BAR_Y + STATION_BAR_H - stationFill,
                    x + STATION_BAR_X + STATION_BAR_W, y + STATION_BAR_Y + STATION_BAR_H,
                    HudStyle.argb(255, 0x7FEBFF), HudStyle.argb(255, 0x1283B5));
        }
    }

    /** Titels in I.M.T-blauw, en naast het vakje hoe vol de bril (of de cel) is. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, HudStyle.argb(255, HudStyle.CYAN), false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, HudStyle.argb(255, 0x9FC9D6), false);
        int dim = HudStyle.argb(170, HudStyle.WHITE);
        small(g, Component.translatable("screen.imttech.station.slot_glasses").getString().toUpperCase(),
                ChargingStationMenu.GLASSES_X - 1, ChargingStationMenu.GLASSES_Y + 20, dim);
        ItemStack glasses = menu.getGlasses();
        int textX = ChargingStationMenu.GLASSES_X + 24;
        if (ImtData.isGlasses(glasses)) {
            small(g, Component.translatable("screen.imttech.station.glasses_energy", ImtData.getEnergy(glasses),
                    ImtData.getMaxEnergy(glasses)).getString(), textX, ChargingStationMenu.GLASSES_Y + 2, HudStyle.argb(230, HudStyle.WHITE));
            HudStyle.bar(g, textX, ChargingStationMenu.GLASSES_Y + 11, 60, 3, ImtData.getEnergyFraction(glasses), HudStyle.CYAN);
        } else if (glasses.getItem() instanceof com.senne.imttech.weapons.EnergyCellItem) {
            small(g, glasses.getHoverName().getString(), textX, ChargingStationMenu.GLASSES_Y + 2, HudStyle.argb(230, HudStyle.WHITE));
        } else {
            small(g, Component.translatable("screen.imttech.station.empty").getString(), textX, ChargingStationMenu.GLASSES_Y + 2, dim);
        }
        small(g, Component.translatable("screen.imttech.station.upgrades_moved").getString(), 8, 63, HudStyle.argb(140, HudStyle.ORANGE));
    }

    /** Kleine tekst (75%). */
    private void small(GuiGraphics g, String text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (isHovering(STATION_BAR_X, STATION_BAR_Y, STATION_BAR_W, STATION_BAR_H, mouseX, mouseY)) {
            g.renderTooltip(font, Component.translatable("screen.imttech.station.energy",
                    menu.getStationEnergy(), (int) ImtConfig.Power.STATION_CAPACITY), mouseX, mouseY);
        }
    }
}
