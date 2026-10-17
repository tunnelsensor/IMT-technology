package com.senne.imttech.client;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.RelayActionPayload;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.redstone.ImtRelays;
import com.senne.imttech.redstone.Relay;
import com.senne.imttech.registry.ModAttachments;
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
 * Het redstone-scherm: al je I.M.T-relais, als een boom van groepen.
 *
 * Per soort apparaat (relais, koepels, torentjes, stroomkasten) zie je je groepen, met de groepen die
 * erin zitten eronder (14 oktober: "base > restricted > security room"):
 * <pre>
 *   base                  [ON] [OFF] [PULSE]
 *     restricted          [ON] [OFF] [PULSE]
 *       security room     [ON] [OFF] [PULSE]
 *         deur1           [ON] [OFF] [PULSE] [rename]
 *         deur2           ...
 * </pre>
 * Een relais zit in hooguit één groep, dus het staat er één keer in. Relais zonder groep staan onderaan
 * onder "No group". Per relais kun je AAN, UIT of PULS kiezen, en per groep alles wat erin zit (ook in
 * groepen eronder). Met "rename" geef je een relais een andere naam of groep; met "Groups" onderaan maak
 * je groepen en zet je ze in elkaar.
 *
 * Openen: G-menu > tabblad Tech > Redstone (of de sneltoets).
 */
public class RelayListScreen extends Screen {
    private static final int PANEL_W = 340;
    private static final int ROW_H = 20;
    private static final int ROWS_PER_PAGE = 8;
    /** Zo ver springt elke laag van de boom in (pixels). */
    private static final int INDENT = 10;
    /** Zo diep tekenen we de boom hooguit (een rondje kan niet, maar zo loopt het nooit vast). */
    private static final int MAX_DEPTH = 8;

    /** Wat er op een regel staat. */
    private enum Type { KIND, GROUP, NO_GROUP, RELAY }

    /** Een regel in de lijst: een kopje (soort apparaat of groep) of een relais. */
    private record Row(Type type, String label, int index, int depth, int y) {}

    private final List<Row> rows = new ArrayList<>();
    private ImtRelays shown = ImtRelays.EMPTY;
    private int page;
    private int left;
    private int top;

    public RelayListScreen() {
        super(Component.translatable("screen.imttech.relays"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    private static ImtRelays data() {
        LocalPlayer player = player();
        return player == null ? ImtRelays.EMPTY : player.getData(ModAttachments.RELAYS);
    }

    // ---------------------------------------------------------------- Opbouw

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(8, (height - (ROWS_PER_PAGE + 2) * ROW_H - 60) / 2);
        shown = data();
        rows.clear();

        // Alle regels bedenken. Eerst per soort apparaat (relais, koepels, ...) en daarbinnen de boom
        // van groepen: zo staan je deuren niet tussen je torentjes.
        List<Row> all = new ArrayList<>();
        for (RelayLabels.Kind kind : RelayLabels.Kind.values()) {
            List<Row> ofKind = new ArrayList<>();
            for (String group : shown.topGroups()) {
                addGroup(ofKind, kind, group, 0);
            }
            boolean noGroupHeader = false;
            for (int i = 0; i < shown.relays().size(); i++) {
                Relay relay = shown.relays().get(i);
                if (!relay.hasGroup() && RelayLabels.kindOf(relay) == kind) {
                    if (!noGroupHeader) {
                        ofKind.add(new Row(Type.NO_GROUP, Component.translatable("screen.imttech.relays.no_group").getString(), -1, 0, 0));
                        noGroupHeader = true;
                    }
                    ofKind.add(new Row(Type.RELAY, relay.name(), i, 1, 0));
                }
            }
            if (!ofKind.isEmpty()) {
                all.add(new Row(Type.KIND, Component.translatable(kind.key).getString(), -1, 0, 0));
                all.addAll(ofKind);
            }
        }
        int pages = Math.max(1, (all.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.min(page, pages - 1);

        int y = top + 28;
        for (int i = page * ROWS_PER_PAGE; i < Math.min(all.size(), (page + 1) * ROWS_PER_PAGE); i++) {
            Row row = all.get(i);
            rows.add(new Row(row.type(), row.label(), row.index(), row.depth(), y));
            switch (row.type()) {
                case GROUP -> addGroupButtons(row.label(), y);
                case RELAY -> addRelayButtons(row.index(), y);
                default -> { } // een kopje: daar hoort geen knop bij
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
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.relays.groups"),
                        button -> Minecraft.getInstance().setScreen(new RelayGroupsScreen()))
                .bounds(left + PANEL_W - 172, bottom, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_W - 88, bottom, 80, 20).build());
    }

    /**
     * Een groep met alles eronder: eerst de groepen die erin zitten, dan zijn eigen relais. Alleen als er
     * (ergens eronder) een apparaat van deze soort in zit.
     */
    private void addGroup(List<Row> out, RelayLabels.Kind kind, String group, int depth) {
        if (depth > MAX_DEPTH || shown.inGroup(group).stream().noneMatch(relay -> RelayLabels.kindOf(relay) == kind)) {
            return;
        }
        out.add(new Row(Type.GROUP, group, -1, depth, 0));
        for (String child : shown.childrenOf(group)) {
            addGroup(out, kind, child, depth + 1);
        }
        for (int i = 0; i < shown.relays().size(); i++) {
            Relay relay = shown.relays().get(i);
            if (relay.isIn(group) && RelayLabels.kindOf(relay) == kind) {
                out.add(new Row(Type.RELAY, relay.name(), i, depth + 1, 0));
            }
        }
    }

    /** De knoppen van één relais: aan, uit, puls en wijzigen. */
    private void addRelayButtons(int index, int y) {
        int x = left + PANEL_W - 190;
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.relays.on"),
                button -> send(RelayActionPayload.switchOne(index, ImtRedstone.Op.ON))).bounds(x, y, 34, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.relays.off"),
                button -> send(RelayActionPayload.switchOne(index, ImtRedstone.Op.OFF))).bounds(x + 36, y, 34, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.relays.pulse"),
                button -> send(RelayActionPayload.switchOne(index, ImtRedstone.Op.PULSE))).bounds(x + 72, y, 40, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.relays.edit"),
                button -> Minecraft.getInstance().setScreen(new RelayEditScreen(index))).bounds(x + 114, y, 66, 18).build());
    }

    /** De knoppen van een hele groep (met alles wat erin zit, ook in groepen eronder). */
    private void addGroupButtons(String group, int y) {
        int x = left + PANEL_W - 190;
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.relays.on"),
                button -> send(RelayActionPayload.switchGroup(group, ImtRedstone.Op.ON))).bounds(x, y, 34, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.relays.off"),
                button -> send(RelayActionPayload.switchGroup(group, ImtRedstone.Op.OFF))).bounds(x + 36, y, 34, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.relays.pulse"),
                button -> send(RelayActionPayload.switchGroup(group, ImtRedstone.Op.PULSE))).bounds(x + 72, y, 40, 18).build());
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
        if (!ImtClientState.authorized) {
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
        String count = shown.relays().size() + "/" + Math.round(ImtConfig.Relays.MAX_RELAYS);
        g.drawString(font, count, left + PANEL_W - 8 - font.width(count), top + 8, HudStyle.argb(180, HudStyle.WHITE), true);

        LocalPlayer player = player();
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.imttech.relays.empty"), left + PANEL_W / 2,
                    top + 40, HudStyle.argb(170, HudStyle.WHITE));
            HudStyle.wrappedCentered(g, font, Component.translatable("screen.imttech.relays.hint"),
                    left + PANEL_W / 2, top + 54, PANEL_W - 24, HudStyle.argb(140, HudStyle.WHITE));
            return;
        }
        // Tekst mag niet onder de knoppen doorlopen.
        int textRight = left + PANEL_W - 194;
        for (Row row : rows) {
            int x = left + 8 + row.depth() * INDENT;
            switch (row.type()) {
                case KIND -> {
                    // Kopje van een soort apparaat (relais / koepels / torentjes / stroomkasten).
                    g.fill(left + 4, row.y() - 2, left + PANEL_W - 4, row.y() + 16, HudStyle.argb(70, HudStyle.ORANGE));
                    g.drawString(font, row.label(), left + 8, row.y() + 3, HudStyle.argb(255, HudStyle.ORANGE), true);
                }
                case GROUP, NO_GROUP -> {
                    // Een groep (of "No group"): een streepje cyaan, iets lichter naarmate hij dieper zit.
                    g.fill(x - 4, row.y() - 2, left + PANEL_W - 4, row.y() + 16, HudStyle.argb(Math.max(18, 45 - row.depth() * 8), HudStyle.CYAN));
                    String label = row.type() == Type.GROUP
                            ? Component.translatable("screen.imttech.relays.group", row.label()).getString()
                            : row.label();
                    g.drawString(font, font.plainSubstrByWidth(label, textRight - x), x, row.y() + 3,
                            HudStyle.argb(255, HudStyle.CYAN), true);
                }
                case RELAY -> {
                    Relay relay = shown.get(row.index());
                    if (relay == null || player == null) {
                        continue;
                    }
                    boolean on = RelayLabels.isOn(relay);
                    g.fill(x + 2, row.y() + 5, x + 8, row.y() + 11, HudStyle.argb(255, on ? HudStyle.GREEN : 0x40525C));
                    g.drawString(font, font.plainSubstrByWidth(relay.name(), textRight - x - 14), x + 14, row.y() + 1,
                            HudStyle.argb(255, HudStyle.WHITE), true);
                    String where = NavigationScreen.locationText(player, relay.dimension(), relay.pos()).getString();
                    g.drawString(font, font.plainSubstrByWidth(where, textRight - x - 14), x + 14, row.y() + 10,
                            HudStyle.argb(165, 0x9FC9D6), false);
                }
            }
        }
    }
}
