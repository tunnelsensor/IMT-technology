package com.senne.imttech.client.os;

import com.senne.imttech.client.RelayLabels;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.RelayActionPayload;
import com.senne.imttech.redstone.ImtRelays;
import com.senne.imttech.redstone.Relay;
import com.senne.imttech.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het Groups-venster van de Security OS-desktop (14 oktober: "deze groepen configureer je in de desktop").
 * Je relais als een boom van groepen, zoals mappen in de Verkenner:
 * <pre>
 *   Your relays (top level)
 *     base                     5 devices
 *       visitors               2 devices
 *         cafe                 1 device
 *           Coffee machine     12 64 -30
 *       restricted
 *         security room
 *     Front door               (in geen groep)
 * </pre>
 * <ul>
 *   <li><b>Slepen</b>: sleep een relais of een groep op een groep, dan zit hij daarin. Sleep hem op de
 *       bovenste regel ("top level") en hij zit in geen groep meer. Een groep kan niet in een groep die er
 *       zelf al in zit ("base" in "temp" terwijl "temp" in "base" zit): die regel kleurt dan rood.</li>
 *   <li><b>New group</b>: een nieuwe groep in de groep die je gekozen hebt (of bovenaan).</li>
 *   <li><b>Rename</b>: een andere naam voor de gekozen groep of het gekozen relais.</li>
 *   <li><b>Delete</b>: de gekozen groep weg; wat erin zat, schuift een laag omhoog.</li>
 * </ul>
 * Het zijn altijd JOUW relais (een relais is van wie het neerzette), ook aan de desktop van een ander. De
 * lijst komt uit je eigen spel (de server stuurt hem al, zie ModAttachments); wat je verandert, gaat als
 * een gewoon relais-bericht naar de server, die zelf controleert of het mag (geen rondjes, niet te veel
 * groepen).
 */
final class GroupsApp {
    private enum Kind { TOP, GROUP, RELAY }

    /** Een regel in de boom: de bovenste regel, een groep, of een relais (index in je lijst). */
    private record Row(Kind kind, String group, int relay, int depth) {
        /** Dezelfde groep of hetzelfde relais (waar hij in de boom staat, maakt niet uit). */
        boolean same(@Nullable Row other) {
            return other != null && kind == other.kind
                    && (kind == Kind.RELAY ? relay == other.relay : group.equalsIgnoreCase(other.group));
        }
    }

    /** Wat je aan het typen bent: niets, de naam van een nieuwe groep, of een nieuwe naam. */
    private enum Input { NONE, NEW_GROUP, RENAME }

    private static final int ROW = 11;
    private static final int INDENT = 9;
    private static final int MAX_DEPTH = 8;

    private final SecurityOsScreen screen;
    private int scroll;
    private int visibleRows = 1;
    /** De gekozen regel, of null. */
    private @Nullable Row selected;
    /** Waar je op drukte (misschien begint daar een sleep), en of je al sleept. */
    private @Nullable Row pressed;
    private int pressX;
    private int pressY;
    private boolean dragging;
    private Input input = Input.NONE;
    private @Nullable EditBox nameBox;

    GroupsApp(SecurityOsScreen screen) {
        this.screen = screen;
    }

    private static ImtRelays data() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? ImtRelays.EMPTY : player.getData(ModAttachments.RELAYS);
    }

    // ================================================================ De boom

    private static List<Row> rows(ImtRelays data) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Kind.TOP, "", -1, 0));
        for (String group : data.topGroups()) {
            addGroup(rows, data, group, 1);
        }
        // Relais zonder groep: onderaan, direct onder "top level".
        for (int i = 0; i < data.relays().size(); i++) {
            if (!data.relays().get(i).hasGroup()) {
                rows.add(new Row(Kind.RELAY, "", i, 1));
            }
        }
        return rows;
    }

    private static void addGroup(List<Row> rows, ImtRelays data, String group, int depth) {
        if (depth > MAX_DEPTH) {
            return; // een rondje kan niet, maar zo loopt het nooit vast
        }
        rows.add(new Row(Kind.GROUP, group, -1, depth));
        for (String child : data.childrenOf(group)) {
            addGroup(rows, data, child, depth + 1);
        }
        for (int i = 0; i < data.relays().size(); i++) {
            if (data.relays().get(i).isIn(group)) {
                rows.add(new Row(Kind.RELAY, group, i, depth + 1));
            }
        }
    }

    private static int listTop(SecurityOsScreen.Area a) {
        return a.y() + OsDraw.BUTTON_H + 5;
    }

    private static int listBottom(SecurityOsScreen.Area a) {
        return a.y() + a.h() - OsDraw.BUTTON_H - 4;
    }

    private @Nullable Row rowAt(SecurityOsScreen.Area a, List<Row> rows, int mx, int my) {
        int top = listTop(a);
        if (mx < a.x() || mx >= a.x() + a.w() || my < top || my >= listBottom(a)) {
            return null;
        }
        int index = scroll + (my - top) / ROW;
        return index >= 0 && index < rows.size() && index < scroll + visibleRows ? rows.get(index) : null;
    }

    /** Is de gekozen regel (nog) een groep die bestaat? Geeft die groep, of null. */
    private @Nullable String selectedGroup(ImtRelays data) {
        return selected != null && selected.kind() == Kind.GROUP && data.hasGroup(selected.group()) ? selected.group() : null;
    }

    private @Nullable Relay selectedRelay(ImtRelays data) {
        return selected != null && selected.kind() == Kind.RELAY ? data.get(selected.relay()) : null;
    }

    // ================================================================ Slepen

    /** De groep waar je op zou loslaten ("" = top level), of null als het geen plek is om iets in te zetten. */
    private static @Nullable String dropGroup(@Nullable Row target) {
        if (target == null || target.kind() == Kind.RELAY) {
            return null;
        }
        return target.kind() == Kind.TOP ? "" : target.group();
    }

    /** Mag wat je sleept hier in? Een groep niet in zichzelf of in een groep die er al in zit. */
    private static boolean canDrop(ImtRelays data, Row from, @Nullable String into) {
        if (into == null) {
            return false;
        }
        if (from.kind() == Kind.GROUP) {
            return !data.wouldLoop(from.group(), into) && !data.parentOf(from.group()).equalsIgnoreCase(into);
        }
        Relay relay = data.get(from.relay());
        return from.kind() == Kind.RELAY && relay != null && !relay.isIn(into);
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
        ImtRelays data = data();
        String group = selectedGroup(data);
        Relay relay = selectedRelay(data);
        // De knoppen.
        int bx = a.x();
        bx += OsDraw.button(g, font, bx, a.y(), "New group", true, HudStyle.CYAN) + 3;
        bx += OsDraw.button(g, font, bx, a.y(), "Rename", group != null || relay != null, HudStyle.CYAN) + 3;
        OsDraw.button(g, font, bx, a.y(), "Delete", group != null, HudStyle.RED);
        String count = data.groups().size() + "/" + Math.round(ImtConfig.Relays.MAX_GROUPS) + " groups";
        g.drawString(font, count, a.x() + a.w() - font.width(count) - 2, a.y() + 2, HudStyle.argb(255, OsDraw.GREY), false);

        // De boom.
        int top = listTop(a);
        int bottom = listBottom(a);
        OsDraw.field(g, a.x(), top - 1, a.x() + a.w(), bottom + 1, false);
        List<Row> rows = rows(data);
        visibleRows = Math.max(1, (bottom - top) / ROW);
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows));
        Row hover = rowAt(a, rows, mx, my);
        String into = dragging ? dropGroup(hover) : null;
        for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows); i++) {
            Row row = rows.get(i);
            int ry = top + (i - scroll) * ROW;
            if (dragging && pressed != null && row.same(hover) && into != null) {
                // Hier loslaten: groen als het mag, rood als het een rondje zou maken.
                boolean ok = canDrop(data, pressed, into);
                g.fill(a.x() + 1, ry, a.x() + a.w() - 1, ry + ROW, HudStyle.argb(255, ok ? 0x1E5A3A : 0x5A1E24));
            } else if (row.same(selected)) {
                g.fill(a.x() + 1, ry, a.x() + a.w() - 1, ry + ROW, HudStyle.argb(255, 0x1E4A60));
            } else if (row.same(hover)) {
                g.fill(a.x() + 1, ry, a.x() + a.w() - 1, ry + ROW, HudStyle.argb(255, 0x14303D));
            }
            renderRow(g, font, a, data, row, ry);
        }
        if (data.relays().isEmpty() && data.groups().isEmpty()) {
            g.drawString(font, "No relays yet. Place an I.M.T relay and", a.x() + 6, top + ROW + 2, HudStyle.argb(255, OsDraw.GREY), false);
            g.drawString(font, "right-click it, or make a group with New group.", a.x() + 6, top + ROW + 12, HudStyle.argb(255, OsDraw.GREY), false);
        }

        // Onderaan: het naamvak, of een korte uitleg.
        int by = a.y() + a.h() - OsDraw.BUTTON_H;
        if (input != Input.NONE && nameBox != null) {
            renderInput(g, font, a, by, mx, my, partialTick);
        } else {
            String hint = dragging && pressed != null && into != null && !canDrop(data, pressed, into) && pressed.kind() == Kind.GROUP
                    && data.wouldLoop(pressed.group(), into)
                    ? "Can't: '" + into + "' is inside '" + pressed.group() + "' (that would make a loop)"
                    : "Drag a relay or group onto a group. Top line = no group.";
            g.drawString(font, OsDraw.fitRight(font, hint, a.w()), a.x(), by + 2,
                    HudStyle.argb(255, hint.startsWith("Can't") ? HudStyle.RED : OsDraw.GREY), false);
        }
        // Slepen: de naam hangt aan de muis.
        if (dragging && pressed != null) {
            String name = label(data, pressed);
            g.fill(mx + 6, my + 2, mx + 12 + font.width(name), my + 13, HudStyle.argb(220, 0x16303F));
            g.drawString(font, name, mx + 9, my + 4, HudStyle.argb(255, HudStyle.CYAN), false);
        }
    }

    private static String label(ImtRelays data, Row row) {
        return switch (row.kind()) {
            case TOP -> "Your relays (top level)";
            case GROUP -> row.group();
            case RELAY -> {
                Relay relay = data.get(row.relay());
                yield relay == null ? "?" : relay.name();
            }
        };
    }

    private void renderRow(GuiGraphics g, Font font, SecurityOsScreen.Area a, ImtRelays data, Row row, int ry) {
        int x = a.x() + 3 + Math.min(row.depth(), MAX_DEPTH) * INDENT;
        switch (row.kind()) {
            case TOP -> g.drawString(font, label(data, row), x, ry + 2, HudStyle.argb(255, OsDraw.GREY), false);
            case GROUP -> {
                // Een mapje (zoals in Files), de naam, en rechts hoeveel apparaten de groep schakelt.
                int colour = HudStyle.argb(255, HudStyle.CYAN);
                g.fill(x, ry + 2, x + 4, ry + 3, colour);
                g.fill(x, ry + 3, x + 9, ry + 9, colour);
                int devices = data.inGroup(row.group()).size();
                String right = devices == 1 ? "1 device" : devices + " devices";
                int rx = a.x() + a.w() - 4 - font.width(right);
                g.drawString(font, right, rx, ry + 2, HudStyle.argb(255, OsDraw.GREY), false);
                g.drawString(font, OsDraw.fitRight(font, row.group(), rx - x - 18), x + 13, ry + 2, HudStyle.argb(255, HudStyle.WHITE), false);
            }
            case RELAY -> {
                Relay relay = data.get(row.relay());
                if (relay == null) {
                    return;
                }
                // Een lampje (groen = aan), de naam, en rechts waar het staat.
                g.fill(x + 2, ry + 3, x + 7, ry + 8, HudStyle.argb(255, RelayLabels.isOn(relay) ? HudStyle.GREEN : 0x40525C));
                String where = relay.pos().getX() + " " + relay.pos().getY() + " " + relay.pos().getZ();
                int rx = a.x() + a.w() - 4 - font.width(where);
                g.drawString(font, where, rx, ry + 2, HudStyle.argb(255, OsDraw.GREY), false);
                g.drawString(font, OsDraw.fitRight(font, relay.name(), rx - x - 18), x + 13, ry + 2, HudStyle.argb(255, 0xCFE3EA), false);
            }
        }
    }

    private void renderInput(GuiGraphics g, Font font, SecurityOsScreen.Area a, int by, int mx, int my, float partialTick) {
        String label = input == Input.NEW_GROUP ? "Group:" : "Name:";
        g.drawString(font, label, a.x(), by + 2, HudStyle.argb(255, HudStyle.WHITE), false);
        int fx = a.x() + font.width(label) + 4;
        int okW = OsDraw.buttonWidth(font, "OK");
        int cancelW = OsDraw.buttonWidth(font, "Cancel");
        int fw = a.w() - (fx - a.x()) - okW - cancelW - 6;
        OsDraw.field(g, fx, by, fx + fw, by + OsDraw.BUTTON_H, true);
        nameBox.setX(fx + 3);
        nameBox.setY(by + 2);
        nameBox.setWidth(fw - 6);
        nameBox.render(g, mx, my, partialTick);
        OsDraw.button(g, font, fx + fw + 3, by, "OK", true, HudStyle.CYAN);
        OsDraw.button(g, font, fx + fw + 6 + okW, by, "Cancel", true, HudStyle.CYAN);
    }

    // ================================================================ Muis

    void mouseClicked(Font font, SecurityOsScreen.Area a, int mx, int my) {
        ImtRelays data = data();
        // Het naamvak onderaan.
        if (input != Input.NONE && nameBox != null) {
            int by = a.y() + a.h() - OsDraw.BUTTON_H;
            if (my >= by && my < by + OsDraw.BUTTON_H) {
                int okX = nameBox.getX() + nameBox.getWidth() + 6;
                int okW = OsDraw.buttonWidth(font, "OK");
                if (mx >= okX && mx < okX + okW) {
                    submit();
                } else if (mx >= okX + okW + 3) {
                    input = Input.NONE;
                } else {
                    nameBox.mouseClicked(mx, my, 0);
                }
                return;
            }
        }
        // De knoppen bovenaan.
        if (my >= a.y() && my < a.y() + OsDraw.BUTTON_H) {
            int newW = OsDraw.buttonWidth(font, "New group");
            int renameW = OsDraw.buttonWidth(font, "Rename");
            int deleteW = OsDraw.buttonWidth(font, "Delete");
            if (OsDraw.inside(mx, my, a.x(), a.y(), newW, OsDraw.BUTTON_H)) {
                startInput(Input.NEW_GROUP, "");
            } else if (OsDraw.inside(mx, my, a.x() + newW + 3, a.y(), renameW, OsDraw.BUTTON_H)) {
                String group = selectedGroup(data);
                Relay relay = selectedRelay(data);
                if (group != null || relay != null) {
                    startInput(Input.RENAME, group != null ? group : relay.name());
                }
            } else if (OsDraw.inside(mx, my, a.x() + newW + renameW + 6, a.y(), deleteW, OsDraw.BUTTON_H)) {
                String group = selectedGroup(data);
                if (group != null) {
                    send(RelayActionPayload.group(RelayActionPayload.Op.GROUP_DELETE, group, ""));
                    String parent = data.parentOf(group);
                    screen.showNotice("Deleted '" + group + "'" + (parent.isEmpty() ? "" : ", its contents are now in '" + parent + "'"), false);
                    selected = null;
                }
            }
            return;
        }
        // De boom: kiezen, en misschien begint hier een sleep.
        Row row = rowAt(a, rows(data), mx, my);
        input = Input.NONE;
        selected = row == null || row.kind() == Kind.TOP ? null : row;
        pressed = row == null || row.kind() == Kind.TOP ? null : row;
        pressX = mx;
        pressY = my;
        dragging = false;
    }

    boolean mouseDragged(int mx, int my) {
        if (pressed != null && !dragging && Math.abs(mx - pressX) + Math.abs(my - pressY) > 4) {
            dragging = true;
        }
        return pressed != null;
    }

    /** Losgelaten: lag je een sleep op een groep (of op "top level")? Dan gaat het erin. */
    void mouseReleased(SecurityOsScreen.Area a, int mx, int my) {
        Row from = pressed;
        boolean wasDragging = dragging;
        pressed = null;
        dragging = false;
        if (from == null || !wasDragging) {
            return;
        }
        ImtRelays data = data();
        String into = dropGroup(rowAt(a, rows(data), mx, my));
        if (into == null) {
            return;
        }
        if (!canDrop(data, from, into)) {
            if (from.kind() == Kind.GROUP && data.wouldLoop(from.group(), into)) {
                screen.showNotice("'" + from.group() + "' can't go inside '" + into + "': that would make a loop", true);
            }
            return;
        }
        String where = into.isEmpty() ? "no group" : "'" + into + "'";
        if (from.kind() == Kind.GROUP) {
            send(RelayActionPayload.group(RelayActionPayload.Op.GROUP_PARENT, from.group(), into));
            screen.showNotice("'" + from.group() + "' is now in " + where, false);
            selected = new Row(Kind.GROUP, from.group(), -1, 0);
        } else {
            Relay relay = data.get(from.relay());
            if (relay != null) {
                send(RelayActionPayload.rename(from.relay(), relay.name(), into));
                screen.showNotice("'" + relay.name() + "' is now in " + where, false);
            }
            selected = null;
        }
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 2);
    }

    // ================================================================ Typen

    private EditBox nameBox() {
        if (nameBox == null) {
            nameBox = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 10, Component.literal("Group name"));
            nameBox.setBordered(false);
            nameBox.setMaxLength(Math.round(ImtConfig.Relays.MAX_NAME));
            nameBox.setFilter(value -> value.indexOf('§') < 0);
            nameBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
        }
        return nameBox;
    }

    private void startInput(Input kind, String value) {
        EditBox box = nameBox();
        box.setValue(value);
        box.moveCursorToEnd(false);
        box.setHighlightPos(0);
        box.setFocused(true);
        input = kind;
    }

    /** OK of Enter: de nieuwe groep maken, of de nieuwe naam bewaren. */
    private void submit() {
        Input kind = input;
        input = Input.NONE;
        String text = nameBox().getValue().strip();
        ImtRelays data = data();
        if (text.isEmpty()) {
            return;
        }
        if (kind == Input.NEW_GROUP) {
            // In de gekozen groep, of in de groep van het gekozen relais, of bovenaan.
            String group = selectedGroup(data);
            Relay relay = selectedRelay(data);
            String parent = group != null ? group : relay != null ? relay.group() : "";
            if (data.hasGroup(text)) {
                screen.showNotice("There is already a group called '" + data.spelling(text) + "'", true);
                return;
            }
            send(RelayActionPayload.group(RelayActionPayload.Op.GROUP_CREATE, text, parent));
            screen.showNotice("Group '" + text + "' created" + (parent.isEmpty() ? "" : " in '" + parent + "'"), false);
            selected = new Row(Kind.GROUP, text, -1, 0);
        } else if (kind == Input.RENAME) {
            String group = selectedGroup(data);
            Relay relay = selectedRelay(data);
            if (group != null) {
                if (data.hasGroup(text) && !text.equalsIgnoreCase(group)) {
                    screen.showNotice("There is already a group called '" + data.spelling(text) + "'", true);
                    return;
                }
                send(RelayActionPayload.group(RelayActionPayload.Op.GROUP_RENAME, group, text));
                selected = new Row(Kind.GROUP, text, -1, 0);
            } else if (relay != null && selected != null) {
                send(RelayActionPayload.rename(selected.relay(), text, relay.group()));
            }
        }
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (input == Input.NONE) {
            return false;
        }
        switch (keyCode) {
            case 257, 335 -> submit(); // enter
            case 256 -> input = Input.NONE; // esc
            default -> nameBox().keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (input == Input.NONE) {
            return false;
        }
        nameBox().charTyped(codePoint, modifiers);
        return true;
    }

    /** Een ander venster kwam bovenop, of dit venster ging dicht: stop met typen en slepen. */
    void blur() {
        input = Input.NONE;
        pressed = null;
        dragging = false;
    }

    private static void send(RelayActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    // ================================================================ Rondleiding

    /** Alleen voor de screenshot-rondleiding: deze groep kiezen (of niets, met null). */
    void tourSelect(@Nullable String group) {
        selected = group == null ? null : new Row(Kind.GROUP, group, -1, 0);
    }
}
