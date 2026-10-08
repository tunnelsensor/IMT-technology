package com.senne.starktech.client;

import com.senne.starktech.StarkTech;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.logic.Datacenter;
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

/**
 * Het scherm van een serverrack.
 *
 * <pre>
 *  [=========]  Server Rack
 *  [CPU] [GPU]  SERVER        ONLINE
 *  [RAM] [FAN]  RACKS 3   CPU 2   GPU 1
 *  [SSD] [   ]  RAM 3   SSD 1   FAN 2
 *               REACTS IN     0.20 s
 *               E.D.I.T.H.    +2.7 s
 *               AT ONCE       6 diagrams
 *               STORAGE       24 blocks
 *               TEMP          48°  > 61°
 *               HEAT/COOL     9.5 / 8 W
 *               Add 1 fan: it gets too hot when busy.
 * </pre>
 * Links het rack zelf: zes vakjes in twee kolommen van drie (zoals de bays van een echt rack), met
 * een lampje naast elk vakje. Rechts wat de HELE server kan (alle racks die elkaar raken samen), en
 * zo niet, waarom niet. Zelfde achtergrond als het zonnepaneel (alleen je rugzak staat erop), maar
 * EXTRA_HEIGHT hoger, zodat alle getallen erboven passen.
 */
public class ServerRackScreen extends AbstractContainerScreen<ServerRackMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/gui/charger.png");

    private static final int STATS_X = 66;
    /** Hier begint in charger.png de streep boven je rugzak. */
    private static final int TEXTURE_SPLIT = 79;
    private static final int FRAME = HudStyle.argb(255, 0x465460);
    private static final int RACK = HudStyle.argb(255, 0x15171C);
    private static final int SLOT = HudStyle.argb(255, 0x1E2128);

    public ServerRackScreen(ServerRackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166 + ServerRackMenu.EXTRA_HEIGHT;
        titleLabelX = STATS_X;
        inventoryLabelY = -1000; // daar staat de uitleg
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // De achtergrond in drie stukken: het lege bovenstuk, nog een stuk leeg paneel (zo wordt het
        // scherm hoger), en je rugzak.
        int extra = ServerRackMenu.EXTRA_HEIGHT;
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, TEXTURE_SPLIT, 256, 256);
        g.blit(TEXTURE, x, y + TEXTURE_SPLIT, 0, 40, imageWidth, extra, 256, 256);
        g.blit(TEXTURE, x, y + TEXTURE_SPLIT + extra, 0, TEXTURE_SPLIT, imageWidth, 166 - TEXTURE_SPLIT, 256, 256);
        // Het rack: een zwarte kast met twee kolommen van drie bays.
        int rackX = x + ServerRackMenu.BAY_X - 5;
        int rackY = y + ServerRackMenu.BAY_Y - 5;
        int rackW = ServerRackMenu.COLUMN_STEP + 16 + 13;
        int rackH = LogicBalance.RACK_ROWS * ServerRackMenu.BAY_STEP + 8;
        g.fill(rackX, rackY, rackX + rackW, rackY + rackH, RACK);
        g.renderOutline(rackX, rackY, rackW, rackH, FRAME);
        boolean online = menu.status() == ServerRackBlockEntity.Status.ONLINE;
        for (int i = 0; i < LogicBalance.RACK_SLOTS; i++) {
            int sx = x + ServerRackMenu.bayX(i);
            int sy = y + ServerRackMenu.bayY(i);
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
        small(g, "RACKS " + menu.racks() + "   CPU " + menu.cpus() + "   GPU " + menu.gpus(), STATS_X, y, value);
        small(g, "RAM " + menu.ram() + "   SSD " + menu.ssd() + "   FAN " + menu.fans(), STATS_X, y + 7, value);
        y += 16;
        // Warm? Dan werkt alles op halve snelheid (zie Datacenter): de kracht telt dan maar half.
        boolean warm = menu.temperature() >= LogicBalance.WARM_TEMPERATURE;
        double slowdown = warm ? Math.max(1.0, LogicBalance.WARM_SLOWDOWN) : 1.0;
        double logic = (menu.cpus() * LogicBalance.logicPower(ServerPartItem.Kind.CPU)
                + menu.gpus() * LogicBalance.logicPower(ServerPartItem.Kind.GPU)) / slowdown;
        double brain = (menu.cpus() * LogicBalance.brainPower(ServerPartItem.Kind.CPU)
                + menu.gpus() * LogicBalance.brainPower(ServerPartItem.Kind.GPU)) / slowdown;
        stat(g, "REACTS IN", logic > 0
                ? String.format(Locale.ROOT, "%.2f s", LogicBalance.reactionTicks(logic) / 20.0) : "-", y);
        int delay = LogicBalance.brainDelayTicks(brain);
        stat(g, "E.D.I.T.H.", delay < 0 ? "can't think" : delay == 0 ? "no delay"
                : String.format(Locale.ROOT, "+%.1f s", delay / 20.0), y + 7);
        stat(g, "AT ONCE", menu.ram() * LogicBalance.RUNS_PER_RAM + " diagrams", y + 14);
        stat(g, "STORAGE", menu.ssd() * LogicBalance.BLOCKS_PER_SSD + " blocks", y + 21);
        // De temperatuur: nu, en waar hij naartoe gaat (als dat meer dan een graad scheelt).
        double temp = menu.temperature();
        double target = Datacenter.target(menu.heat(), menu.cooling());
        String tempText = Datacenter.degrees(temp) + (Math.abs(target - temp) >= 1.0 ? "  > " + Datacenter.degrees(target) : "");
        small(g, "TEMP", STATS_X, y + 28, label);
        small(g, tempText, STATS_X + 46, y + 28, HudStyle.argb(255, temperatureColour(Math.max(temp, target))));
        stat(g, "HEAT/COOL", number(menu.heat()) + " / " + watts(menu.cooling()), y + 35);
        y += 21;
        // Waarom hij niet draait (of anders: waar je hem programmeert).
        String problem = switch (status) {
            case NOT_CONNECTED -> "Not touching the main server racks.";
            case NO_POWER -> "This rack needs power (a cable).";
            default -> !menu.coreOnline() ? "The security core has no power."
                    : menu.cpus() == 0 && menu.gpus() == 0 ? "Add a CPU or GPU." : menu.ram() == 0 ? "Add RAM."
                    : menu.ssd() == 0 ? "Add an SSD." : "";
        };
        int fans = Datacenter.fansNeeded(menu.fullHeat(), menu.cooling());
        if (!problem.isEmpty()) {
            wrapped(g, problem, y + 23, HudStyle.argb(230, HudStyle.RED));
        } else if (warm) {
            wrapped(g, "Too hot: everything works at half speed." + (fans > 0 ? " Add " + fanText(fans) + "!" : ""), y + 23,
                    HudStyle.argb(230, HudStyle.RED));
        } else if (fans > 0) {
            wrapped(g, "Add " + fanText(fans) + ": it gets too hot when it works hard.", y + 23,
                    HudStyle.argb(230, HudStyle.ORANGE));
        } else {
            wrapped(g, "Program it in the Logic app on your Stark OS desktop.", y + 23, HudStyle.argb(140, HudStyle.WHITE));
        }
    }

    /** Groen als het koel is, oranje als het warm wordt, rood als het heet is. */
    static int temperatureColour(double temperature) {
        if (temperature >= LogicBalance.HOT_TEMPERATURE) {
            return HudStyle.RED;
        }
        if (temperature >= LogicBalance.WARM_TEMPERATURE) {
            return HudStyle.ORANGE;
        }
        return temperature >= LogicBalance.coolAgainTemperature() ? 0xE8D060 : HudStyle.GREEN;
    }

    /** 9.5 wordt "9.5 W", 8.0 wordt "8 W". */
    static String watts(double value) {
        return number(value) + " W";
    }

    /** 9.54 wordt "9.5", 8.0 wordt "8". */
    static String number(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }

    private static String fanText(int fans) {
        return fans == 1 ? "1 fan" : fans + " fans";
    }

    /** Draait de hele server (rekenkracht, RAM en een SSD, en de kern heeft stroom)? */
    private boolean online() {
        return menu.coreOnline() && menu.cpus() + menu.gpus() > 0 && menu.ram() > 0 && menu.ssd() > 0;
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
            int sx = leftPos + ServerRackMenu.bayX(i);
            int sy = topPos + ServerRackMenu.bayY(i);
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
