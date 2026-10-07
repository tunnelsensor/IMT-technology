package com.senne.starktech.client;

import com.senne.starktech.StarkTech;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.logic.LogicBalance;
import com.senne.starktech.logic.ServerPartItem;
import com.senne.starktech.logic.ServerRackBlockEntity;
import com.senne.starktech.logic.ServerRackMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Het scherm van een serverrack.
 *
 * <pre>
 *  [=====]  Server Rack
 *  [ CPU ]  SERVER        ONLINE
 *  [ RAM ]  RACKS 3   CPU 2   RAM 3   SSD 1
 *  [ RAM ]  REACTS IN     0.35 s
 *  [     ]  AT ONCE       6 diagrams
 *           STORAGE       24 blocks
 * </pre>
 * Links het rack zelf: vier vakjes onder elkaar (zoals de bays van een echt rack), met naast elk
 * vakje wat erin zit. Rechts wat de HELE server kan (alle racks die elkaar raken samen), en zo
 * niet, waarom niet. Zelfde achtergrond als het zonnepaneel (alleen je rugzak staat erop).
 */
public class ServerRackScreen extends AbstractContainerScreen<ServerRackMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/gui/charger.png");

    private static final int STATS_X = 66;
    private static final int FRAME = HudStyle.argb(255, 0x465460);
    private static final int RACK = HudStyle.argb(255, 0x15171C);
    private static final int SLOT = HudStyle.argb(255, 0x1E2128);

    public ServerRackScreen(ServerRackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        titleLabelX = STATS_X;
        inventoryLabelY = -1000; // daar staat de uitleg
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        // Het rack: een zwarte kast met vier bays.
        int rackX = x + ServerRackMenu.BAY_X - 5;
        int rackY = y + ServerRackMenu.BAY_Y - 4;
        int rackW = 26;
        int rackH = LogicBalance.RACK_SLOTS * ServerRackMenu.BAY_STEP + 6;
        g.fill(rackX, rackY, rackX + rackW, rackY + rackH, RACK);
        g.renderOutline(rackX, rackY, rackW, rackH, FRAME);
        boolean online = menu.status() == ServerRackBlockEntity.Status.ONLINE;
        for (int i = 0; i < LogicBalance.RACK_SLOTS; i++) {
            int sx = x + ServerRackMenu.BAY_X;
            int sy = y + ServerRackMenu.BAY_Y + i * ServerRackMenu.BAY_STEP;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT);
            g.renderOutline(sx - 1, sy - 1, 18, 18, FRAME);
            // Een lampje naast elke bay: groen als het rack meedraait en er iets in zit.
            boolean filled = !menu.getSlot(i).getItem().isEmpty();
            int led = online && filled ? HudStyle.GREEN : filled ? 0x5A3A20 : 0x30363C;
            g.fill(sx + 18, sy + 7, sx + 20, sy + 9, HudStyle.argb(255, led));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, HudStyle.argb(255, HudStyle.CYAN), false);
        int label = HudStyle.argb(150, HudStyle.ORANGE);
        int value = HudStyle.argb(230, HudStyle.WHITE);

        // Naast elke bay: wat erin zit.
        for (int i = 0; i < LogicBalance.RACK_SLOTS; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            String text = stack.getItem() instanceof ServerPartItem part ? part.kind().name() : "";
            small(g, text, ServerRackMenu.BAY_X + 23, ServerRackMenu.BAY_Y + i * ServerRackMenu.BAY_STEP + 6,
                    HudStyle.argb(200, HudStyle.WHITE));
        }

        // Rechts: de server.
        ServerRackBlockEntity.Status status = menu.status();
        int y = 17;
        small(g, "SERVER", STATS_X, y, label);
        String state = switch (status) {
            case ONLINE -> online() ? "ONLINE" : "NOT RUNNING";
            case NO_NETWORK -> "NO NETWORK";
            case NOT_CONNECTED -> "NOT CONNECTED";
            case NO_POWER -> "NO POWER";
        };
        small(g, state, STATS_X + 40, y, HudStyle.argb(255, status == ServerRackBlockEntity.Status.ONLINE && online()
                ? HudStyle.GREEN : HudStyle.RED));
        if (status == ServerRackBlockEntity.Status.NO_NETWORK) {
            wrapped(g, "Place it within 30 blocks of a Security Core: the server runs on your Stark OS.", y + 10,
                    HudStyle.argb(140, HudStyle.WHITE));
            return;
        }
        y += 10;
        small(g, "RACKS " + menu.racks() + "   CPU " + menu.cpus(), STATS_X, y, value);
        small(g, "RAM " + menu.ram() + "   SSD " + menu.ssd(), STATS_X, y + 7, value);
        y += 16;
        stat(g, "REACTS IN", menu.cpus() > 0
                ? String.format(Locale.ROOT, "%.2f s", LogicBalance.reactionTicks(menu.cpus()) / 20.0) : "-", y);
        stat(g, "AT ONCE", menu.ram() * LogicBalance.RUNS_PER_RAM + " diagrams", y + 7);
        stat(g, "STORAGE", menu.ssd() * LogicBalance.BLOCKS_PER_SSD + " blocks", y + 14);
        // Waarom hij niet draait (of anders: waar je hem programmeert).
        String problem = switch (status) {
            case NOT_CONNECTED -> "Not touching the main server racks.";
            case NO_POWER -> "This rack needs power (a cable).";
            default -> !menu.coreOnline() ? "The security core has no power."
                    : menu.cpus() == 0 ? "Add a CPU." : menu.ram() == 0 ? "Add RAM." : menu.ssd() == 0 ? "Add an SSD." : "";
        };
        if (!problem.isEmpty()) {
            wrapped(g, problem, y + 23, HudStyle.argb(230, HudStyle.RED));
        } else {
            wrapped(g, "Program it in the Logic app on your Stark OS desktop.", y + 23, HudStyle.argb(140, HudStyle.WHITE));
        }
    }

    /** Draait de hele server (er is van elk onderdeel minstens één, en de kern heeft stroom)? */
    private boolean online() {
        return menu.coreOnline() && menu.cpus() > 0 && menu.ram() > 0 && menu.ssd() > 0;
    }

    private void stat(GuiGraphics g, String name, String value, int y) {
        small(g, name, STATS_X, y, HudStyle.argb(150, HudStyle.ORANGE));
        small(g, value, STATS_X + 46, y, HudStyle.argb(230, HudStyle.WHITE));
    }

    /** Kleine tekst over meerdere regels, zodat hij rechts in het scherm past. */
    private void wrapped(GuiGraphics g, String text, int y, int colour) {
        int width = (int) ((imageWidth - STATS_X - 6) / 0.75f);
        for (net.minecraft.util.FormattedCharSequence line : font.split(Component.literal(text), width)) {
            g.pose().pushPose();
            g.pose().translate(STATS_X, y, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            g.drawString(font, line, 0, 0, colour, false);
            g.pose().popPose();
            y += 7;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        // Boven een leeg vakje: wat erin mag.
        for (int i = 0; i < LogicBalance.RACK_SLOTS; i++) {
            int sx = leftPos + ServerRackMenu.BAY_X;
            int sy = topPos + ServerRackMenu.BAY_Y + i * ServerRackMenu.BAY_STEP;
            if (menu.getSlot(i).getItem().isEmpty() && mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                g.renderTooltip(font, Component.translatable("screen.starktech.server_rack.bay_tooltip"), mouseX, mouseY);
            }
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
