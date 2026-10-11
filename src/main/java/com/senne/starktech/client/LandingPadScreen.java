package com.senne.starktech.client;

import com.senne.starktech.StarkTech;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.suit.LandingPadBlockEntity;
import com.senne.starktech.suit.LandingPadMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Het scherm van een landingsplatform: zijn eigen batterij.
 *
 * <pre>
 *  Landing Pad
 *  [    ]  BATTERY 12400 / 40000        GRID       8200
 *  [ ba ]  ===================          FILLING    300/s
 *  [ tt ]  Cells [cel]                  CHARGING   1600/s
 *  [ er ]
 *  [ ij ]  uitleg in kleine letters
 * </pre>
 * Links een grote, liggende batterij die zich vult; daaronder het vakje voor batterijcellen
 * (Battery Upgrades); rechts wat het net heeft, hoe snel de batterij vult en hoe snel hij jou
 * oplaadt. Zelfde achtergrond als het zonnepaneel (alleen je rugzak staat erop getekend).
 */
public class LandingPadScreen extends AbstractContainerScreen<LandingPadMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/gui/charger.png");

    /** De batterij: een liggende "accu" met een knopje rechts. */
    private static final int BAT_X = 10, BAT_Y = 24, BAT_W = 88, BAT_H = 18;
    private static final int STATS_X = 110;

    private static final int FRAME = HudStyle.argb(255, 0x465460);
    private static final int WELL = HudStyle.argb(255, 0x0E1216);
    private static final int SLOT = HudStyle.argb(255, 0x141A20);

    public LandingPadScreen(LandingPadMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = -1000; // daar staat de uitleg
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        // De batterij: een omlijnde bak met een knopje, gevuld van links naar rechts, met streepjes
        // per kwart zodat je in één oogopslag ziet hoe vol hij is.
        box(g, x + BAT_X - 1, y + BAT_Y - 1, BAT_W + 2, BAT_H + 2, WELL);
        g.fill(x + BAT_X + BAT_W + 1, y + BAT_Y + 6, x + BAT_X + BAT_W + 4, y + BAT_Y + BAT_H - 6, FRAME);
        float fraction = menu.capacity() <= 0 ? 0 : Math.min(1.0f, (float) menu.energy() / menu.capacity());
        int fill = Math.round(BAT_W * fraction);
        if (fill > 0) {
            g.fillGradient(x + BAT_X, y + BAT_Y, x + BAT_X + fill, y + BAT_Y + BAT_H,
                    HudStyle.argb(255, 0x7FE8FF), HudStyle.argb(255, 0x1A7FB5));
        }
        // Laadt hij je op? Dan loopt er een lichtje door de batterij.
        if (menu.charged() > 0 && fill > 4) {
            int run = (int) ((System.currentTimeMillis() / 12) % fill);
            g.fill(x + BAT_X + run, y + BAT_Y, x + BAT_X + Math.min(fill, run + 4), y + BAT_Y + BAT_H,
                    HudStyle.argb(140, 0xFFFFFF));
        }
        for (int quarter = 1; quarter < 4; quarter++) {
            int qx = x + BAT_X + BAT_W * quarter / 4;
            g.fill(qx, y + BAT_Y, qx + 1, y + BAT_Y + BAT_H, HudStyle.argb(90, 0x000000));
        }

        // Het vakje voor batterijcellen, met een vage cel erin zolang het leeg is.
        int cx = x + LandingPadMenu.CELLS_X;
        int cy = y + LandingPadMenu.CELLS_Y;
        box(g, cx - 1, cy - 1, 18, 18, SLOT);
        if (menu.cellsItem().isEmpty()) {
            g.renderFakeItem(new ItemStack(ModItems.BATTERY_UPGRADE.get()), cx, cy);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(cx, cy, cx + 16, cy + 16, HudStyle.argb(170, 0x141A20));
            g.pose().popPose();
        }
    }

    private static void box(GuiGraphics g, int x, int y, int width, int height, int colour) {
        g.fill(x, y, x + width, y + height, colour);
        g.renderOutline(x, y, width, height, FRAME);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, HudStyle.argb(255, HudStyle.CYAN), false);
        int dim = HudStyle.argb(150, HudStyle.ORANGE);
        int value = HudStyle.argb(230, HudStyle.WHITE);

        // Boven de batterij: hoeveel erin zit.
        small(g, Component.translatable("screen.starktech.landing_pad.battery").getString(), BAT_X, BAT_Y - 7, dim);
        String stored = Component.translatable("screen.starktech.landing_pad.stored", menu.energy(), menu.capacity()).getString();
        small(g, stored, BAT_X + BAT_W - (int) Math.ceil(font.width(stored) * 0.75f), BAT_Y - 7, value);
        // Het percentage midden in de batterij.
        String percent = Math.round(100.0f * menu.energy() / Math.max(1, menu.capacity())) + "%";
        g.drawString(font, percent, BAT_X + BAT_W / 2 - font.width(percent) / 2, BAT_Y + BAT_H / 2 - 4,
                HudStyle.argb(255, HudStyle.WHITE), true);

        // Naast het vakje: wat erin hoort.
        small(g, Component.translatable("screen.starktech.landing_pad.cells").getString().toUpperCase(),
                LandingPadMenu.CELLS_X - 34, LandingPadMenu.CELLS_Y + 5, HudStyle.argb(170, HudStyle.WHITE));

        // Rechts: het net, het vullen en het opladen.
        int rowY = 20;
        stat(g, "screen.starktech.landing_pad.label_grid",
                menu.grid() > 0 ? String.valueOf(menu.grid()) : Component.translatable("screen.starktech.landing_pad.no_grid").getString(),
                rowY, menu.grid() > 0 ? value : HudStyle.argb(230, HudStyle.RED));
        stat(g, "screen.starktech.landing_pad.label_filling",
                menu.energy() >= menu.capacity() ? Component.translatable("screen.starktech.landing_pad.full").getString()
                        : Component.translatable("screen.starktech.landing_pad.per_second", menu.filled()).getString(),
                rowY + 16, value);
        stat(g, "screen.starktech.landing_pad.label_charging",
                menu.charged() > 0 ? Component.translatable("screen.starktech.landing_pad.per_second", menu.charged()).getString()
                        : Component.translatable("screen.starktech.landing_pad.off_pad").getString(),
                rowY + 32, menu.charged() > 0 ? HudStyle.argb(255, HudStyle.GREEN) : HudStyle.argb(170, HudStyle.WHITE));

        // Onderaan: de uitleg, klein en over twee regels.
        List<FormattedCharSequence> lines = font.split(Component.translatable("screen.starktech.landing_pad.hint"),
                (int) ((imageWidth - 16) / 0.75f));
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.pose().pushPose();
            g.pose().translate(8, 68 + i * 6, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            g.drawString(font, lines.get(i), 0, 0, HudStyle.argb(140, HudStyle.WHITE), false);
            g.pose().popPose();
        }
    }

    /** Eén regel rechts: een oranje naam en daaronder de waarde. */
    private void stat(GuiGraphics g, String key, String value, int y, int colour) {
        small(g, Component.translatable(key).getString().toUpperCase(), STATS_X, y, HudStyle.argb(150, HudStyle.ORANGE));
        small(g, value, STATS_X, y + 7, colour);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int cx = leftPos + LandingPadMenu.CELLS_X;
        int cy = topPos + LandingPadMenu.CELLS_Y;
        if (menu.cellsItem().isEmpty() && mouseX >= cx && mouseX < cx + 16 && mouseY >= cy && mouseY < cy + 16) {
            g.renderTooltip(font, Component.translatable("screen.starktech.landing_pad.cells_tooltip",
                    (int) StarkConfig.Power.PAD_BATTERY_PER_CELL, LandingPadBlockEntity.MAX_CELLS), mouseX, mouseY);
        }
    }

    /** Kleine tekst (75%), links uitgelijnd. */
    private void small(GuiGraphics g, String text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
