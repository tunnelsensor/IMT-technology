package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.network.RelayActionPayload;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.redstone.EdithRelays;
import com.senne.starktech.redstone.Relay;
import com.senne.starktech.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Het redstone-scherm: al je E.D.I.T.H.-relais, netjes per groep.
 *
 * Per relais kun je AAN, UIT of PULS kiezen, en per groep kun je alles in één keer schakelen.
 * Met "wijzig" geef je een relais een andere naam of groep.
 *
 * Openen: G-menu > tabblad Tech > Redstone (of de sneltoets).
 */
public class RelayListScreen extends Screen {
    private static final int PANEL_W = 340;
    private static final int ROW_H = 20;
    private static final int ROWS_PER_PAGE = 8;

    /** Een regel in de lijst: een groepskopje (index -1) of een relais. */
    private record Row(String group, int index, int y) {}

    private final List<Row> rows = new ArrayList<>();
    private EdithRelays shown = EdithRelays.EMPTY;
    private int page;
    private int left;
    private int top;

    public RelayListScreen() {
        super(Component.translatable("screen.starktech.relays"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    private static EdithRelays data() {
        LocalPlayer player = player();
        return player == null ? EdithRelays.EMPTY : player.getData(ModAttachments.RELAYS);
    }

    // ---------------------------------------------------------------- Opbouw

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(8, (height - (ROWS_PER_PAGE + 2) * ROW_H - 60) / 2);
        shown = data();
        rows.clear();

        // Alle regels bedenken. Eerst per soort apparaat (relais, laserzenders, koepels) en
        // daarbinnen per groep: zo staan je deuren niet tussen je lampen.
        List<Row> all = new ArrayList<>();
        for (RelayLabels.Kind kind : RelayLabels.Kind.values()) {
            boolean anyOfKind = false;
            for (String group : shown.groups()) {
                boolean headerDone = false;
                for (int i = 0; i < shown.relays().size(); i++) {
                    Relay relay = shown.relays().get(i);
                    if (!relay.group().equals(group) || RelayLabels.kindOf(relay) != kind) {
                        continue;
                    }
                    if (!anyOfKind) {
                        all.add(new Row(Component.translatable(kind.key).getString(), -2, 0));
                        anyOfKind = true;
                    }
                    if (!headerDone) {
                        all.add(new Row(group, -1, 0));
                        headerDone = true;
                    }
                    all.add(new Row(group, i, 0));
                }
            }
        }
        int pages = Math.max(1, (all.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.min(page, pages - 1);

        int y = top + 28;
        for (int i = page * ROWS_PER_PAGE; i < Math.min(all.size(), (page + 1) * ROWS_PER_PAGE); i++) {
            Row row = all.get(i);
            rows.add(new Row(row.group(), row.index(), y));
            if (row.index() == -2) {
                // Kopje van een soort apparaat: daar hoort geen knop bij.
                y += ROW_H;
                continue;
            }
            if (row.index() < 0) {
                // Groepskopje: schakel de hele groep.
                addGroupButtons(row.group(), y);
            } else {
                addRelayButtons(row.index(), y);
            }
            y += ROW_H;
        }

        int bottom = top + 28 + ROWS_PER_PAGE * ROW_H + 6;
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                    .bounds(left + 8, bottom, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                    .bounds(left + 32, bottom, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_W - 88, bottom, 80, 20).build());
    }

    /** De knoppen van één relais: aan, uit, puls en wijzigen. */
    private void addRelayButtons(int index, int y) {
        int x = left + PANEL_W - 190;
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relays.on"),
                button -> send(RelayActionPayload.switchOne(index, EdithRedstone.Op.ON))).bounds(x, y, 34, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relays.off"),
                button -> send(RelayActionPayload.switchOne(index, EdithRedstone.Op.OFF))).bounds(x + 36, y, 34, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relays.pulse"),
                button -> send(RelayActionPayload.switchOne(index, EdithRedstone.Op.PULSE))).bounds(x + 72, y, 40, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relays.edit"),
                button -> Minecraft.getInstance().setScreen(new RelayEditScreen(index))).bounds(x + 114, y, 66, 18).build());
    }

    /** De knoppen van een hele groep. */
    private void addGroupButtons(String group, int y) {
        int x = left + PANEL_W - 190;
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relays.on"),
                button -> send(RelayActionPayload.switchNamed(group, EdithRedstone.Op.ON))).bounds(x, y, 34, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relays.off"),
                button -> send(RelayActionPayload.switchNamed(group, EdithRedstone.Op.OFF))).bounds(x + 36, y, 34, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relays.pulse"),
                button -> send(RelayActionPayload.switchNamed(group, EdithRedstone.Op.PULSE))).bounds(x + 72, y, 40, 18).build());
    }

    private void changePage(int delta) {
        page = Math.max(0, page + delta);
        rebuildWidgets();
    }

    private void send(RelayActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** Verandert er iets aan je relais (antwoord van de server)? Dan het scherm opnieuw opbouwen. */
    @Override
    public void tick() {
        super.tick();
        if (!EdithClientState.authorized) {
            onClose();
            return;
        }
        if (!data().equals(shown)) {
            rebuildWidgets();
        }
    }

    // ---------------------------------------------------------------- Tekenen

    /**
     * Het paneel hoort bij de achtergrond. Minecraft tekent in render() eerst een wazige
     * achtergrond; wat daarvóór getekend was, werd dus mee vervaagd en verduisterd.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        int panelH = 28 + ROWS_PER_PAGE * ROW_H + 32;
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + panelH, HudStyle.argb(205, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + panelH, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);

        g.drawString(font, title, left + 8, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);
        String count = shown.relays().size() + "/" + Math.round(StarkConfig.Relays.MAX_RELAYS);
        g.drawString(font, count, left + PANEL_W - 8 - font.width(count), top + 8, HudStyle.argb(180, HudStyle.WHITE), true);

        LocalPlayer player = player();
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.starktech.relays.empty"), left + PANEL_W / 2,
                    top + 40, HudStyle.argb(170, HudStyle.WHITE));
            HudStyle.wrappedCentered(g, font, Component.translatable("screen.starktech.relays.hint"),
                    left + PANEL_W / 2, top + 54, PANEL_W - 24, HudStyle.argb(140, HudStyle.WHITE));
            return;
        }
        for (Row row : rows) {
            if (row.index() == -2) {
                // Kopje van een soort apparaat (relais / laserzenders / koepels).
                g.fill(left + 4, row.y() - 2, left + PANEL_W - 4, row.y() + 16, HudStyle.argb(70, HudStyle.ORANGE));
                g.drawString(font, row.group(), left + 8, row.y() + 3, HudStyle.argb(255, HudStyle.ORANGE), true);
                continue;
            }
            if (row.index() < 0) {
                // Groepskopje
                g.fill(left + 4, row.y() - 2, left + PANEL_W - 4, row.y() + 16, HudStyle.argb(45, HudStyle.CYAN));
                g.drawString(font, Component.translatable("screen.starktech.relays.group", row.group()),
                        left + 8, row.y() + 3, HudStyle.argb(255, HudStyle.CYAN), true);
                continue;
            }
            Relay relay = shown.get(row.index());
            if (relay == null || player == null) {
                continue;
            }
            boolean on = RelayLabels.isOn(relay);
            g.fill(left + 10, row.y() + 5, left + 16, row.y() + 11, HudStyle.argb(255, on ? HudStyle.GREEN : 0x40525C));
            g.drawString(font, relay.name(), left + 22, row.y() + 1, HudStyle.argb(255, HudStyle.WHITE), true);
            g.drawString(font, NavigationScreen.locationText(player, relay.dimension(), relay.pos()),
                    left + 22, row.y() + 10, HudStyle.argb(165, 0x9FC9D6), false);
        }
    }
}
