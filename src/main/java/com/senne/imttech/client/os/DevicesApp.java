package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.logic.InputDeviceBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Het Devices-venster van de Security OS-desktop (jouw wens, 28 september: de torentjes, drones,
 * laserwanden en camera's bekijken en bedienen). Bovenaan drie schakelaars voor het hele netwerk
 * (drones, laserwanden, camera's), daaronder elk apparaat met zijn status:
 * <ul>
 *   <li>torentjes: aan/uit (een eigen knop per torentje), stroom, raketten, waar hij op richt;</li>
 *   <li>drones: levens, wat hij doet, op wie hij schiet;</li>
 *   <li>laserpoorten: aan of open;</li>
 *   <li>laserbruggen (hoofdstuk 38): ligt hij er ("active"), is hij ingetrokken ("retracted"), of heeft hij geen stroom;</li>
 *   <li>camera's: beeld of niet (het beeld zelf zie je in de app Cameras);</li>
 *   <li>keycardlezers en codepanelen: of de server ze hoort, in hoeveel diagrammen ze staan, en een
 *       knop Rename (jouw wens van 8 oktober). Typ de naam, Enter bewaart, Esc laat het zoals het was.</li>
 * </ul>
 * De server zoekt zelf welke apparaten bij de kern horen (zie SecurityDevices) en stuurt de lijst
 * mee met elke momentopname.
 *
 * Sinds 19 oktober een tabel (jouw notitie: "er moet een lijn tussen elke device staan, soort van tabel, ... de online
 * status en knop moet naast elkaar staan"): één regel per apparaat, met lijntjes ertussen.
 * <pre>
 *  NAME              WHERE         STATUS
 *  TURRETS
 *  Security turret   -104 -60 40   online  12 rockets       [Turn off]
 *  ─────────────────────────────────────────────────────────────────
 *  Lab reader        -98 -60 44    online  in 1 diagram       [Rename]
 * </pre>
 */
final class DevicesApp {
    /**
     * Een regel in de lijst: een kopje ("TURRETS"), een apparaat, of (hoofdstuk 38, {@code ports}) de regel onder een
     * Network Switch met zijn zes poorten.
     */
    private record Row(@Nullable String header, @Nullable CompoundTag device, boolean ports) {}

    private static final int HEADER_H = 13;
    private static final int DEVICE_H = 14;
    /** Waar de kolommen beginnen, vanaf de linkerkant van de lijst: naam, plek, toestand. */
    private static final int COL_NAME = 4;
    private static final int COL_WHERE = 104;
    private static final int COL_STATUS = 172;

    private final SecurityOsScreen screen;
    private int scroll;
    /** Welke lezer, welk paneel of welke switch je een andere naam aan het geven bent (de plek), of null. */
    private @Nullable BlockPos renaming;
    private @Nullable EditBox nameBox;
    /** Van welke Network Switch de poorten open staan (de knop Ports), of null. */
    private @Nullable BlockPos portsOpen;

    DevicesApp(SecurityOsScreen screen) {
        this.screen = screen;
    }

    private CompoundTag access() {
        return screen.data().getCompound("access");
    }

    /** De lijst: per soort een kopje en dan de apparaten van die soort. */
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        String[][] kinds = {{"turret", "TURRETS"}, {"drone", "DRONES"}, {"laser", "LASER GATES"}, {"bridge", "LASER BRIDGES"},
                {"camera", "CAMERAS"}, {"reader", "KEYCARD READERS"}, {"panel", "CODE PANELS"}, {"button", "BUTTONS"},
                {"lever", "LEVERS"}, {"switch", "NETWORK SWITCHES"}};
        List<Tag> devices = screen.data().getList("devices", Tag.TAG_COMPOUND);
        for (String[] kind : kinds) {
            boolean any = false;
            for (Tag element : devices) {
                CompoundTag device = (CompoundTag) element;
                if (!device.getString("k").equals(kind[0])) {
                    continue;
                }
                if (!any) {
                    rows.add(new Row(kind[1], null, false));
                    any = true;
                }
                rows.add(new Row(null, device, false));
                // Staan de poorten van deze switch open? Dan een regel eronder met de zes poorten.
                if (kind[0].equals("switch") && posOf(device).equals(portsOpen)) {
                    rows.add(new Row(null, device, true));
                }
            }
        }
        return rows;
    }

    private static int height(Row row) {
        return row.header() != null ? HEADER_H : DEVICE_H;
    }

    private static int listTop(SecurityOsScreen.Area a) {
        return a.y() + OsDraw.BUTTON_H + 5;
    }

    /** Waar de rijen beginnen: onder de kopjes van de kolommen. */
    private static int rowsTop(SecurityOsScreen.Area a) {
        return listTop(a) + 15;
    }

    /**
     * De knop rechts op de rij van dit apparaat (Turn off, Turn on, Rename, Save; bij een switch Ports of Close), of null
     * als hij er geen heeft.
     */
    private @Nullable String buttonLabel(CompoundTag device) {
        if (switchable(device)) {
            return device.getBoolean("on") ? "Turn off" : "Turn on";
        }
        if (device.getString("k").equals("switch")) {
            return isRenaming(device) ? "Save" : posOf(device).equals(portsOpen) ? "Close" : "Ports";
        }
        return renamable(device) ? renameLabel(device) : null;
    }

    /** Een torentje, of (hoofdstuk 39) een laserpoort of laserbrug: die hebben een knop Turn off / Turn on. */
    private static boolean switchable(CompoundTag device) {
        String kind = device.getString("k");
        return kind.equals("turret") || kind.equals("laser") || kind.equals("bridge");
    }

    private int buttonColour(CompoundTag device) {
        if (switchable(device)) {
            return device.getBoolean("on") ? HudStyle.RED : HudStyle.GREEN;
        }
        return isRenaming(device) ? HudStyle.GREEN : HudStyle.CYAN;
    }

    /**
     * Een tweede knop, links van de gewone: bij een switch Rename naast Ports (terwijl je de naam typt is er alleen Save).
     * Of null als het apparaat er geen heeft.
     */
    private @Nullable String extraLabel(CompoundTag device) {
        return device.getString("k").equals("switch") && !isRenaming(device) ? "Rename" : null;
    }

    // ================================================================ De poorten van een switch (hoofdstuk 38)

    /**
     * De opschriften van de zes poortknoppen: de naam van de kant en hoe hij staat ("Front ON"). Passen ze niet naast
     * elkaar (een klein venster), dan alleen de naam van de kant; de kleur zegt dan nog steeds aan of uit.
     */
    private static List<String> portLabels(Font font, SecurityOsScreen.Area a, CompoundTag device) {
        List<String> full = new ArrayList<>();
        List<String> sides = new ArrayList<>();
        int width = 0;
        for (Tag element : device.getList("ports", Tag.TAG_COMPOUND)) {
            CompoundTag port = (CompoundTag) element;
            String side = Component.translatable(port.getString("n")).getString();
            String label = side + (port.getBoolean("on") ? " ON" : " OFF");
            full.add(label);
            sides.add(side);
            width += OsDraw.buttonWidth(font, label) + 3;
        }
        return width <= a.w() - COL_NAME - 8 - 4 ? full : sides;
    }

    /** De regel onder een switch: zes knoppen, een per poort (groen = aan, rood = uit, grijs = er zit niets aan). */
    private void renderPorts(GuiGraphics g, Font font, SecurityOsScreen.Area a, int y, CompoundTag device) {
        List<String> labels = portLabels(font, a, device);
        ListTag ports = device.getList("ports", Tag.TAG_COMPOUND);
        int x = a.x() + COL_NAME + 8;
        for (int i = 0; i < ports.size() && i < labels.size(); i++) {
            CompoundTag port = ports.getCompound(i);
            int colour = port.getBoolean("on") ? HudStyle.GREEN : HudStyle.RED;
            x += OsDraw.button(g, font, x, y + 1, labels.get(i), port.getBoolean("w"), colour) + 3;
        }
    }

    /** Een klik op de regel met poorten: een poort omzetten. Geeft true als er een poort geraakt werd. */
    private boolean portsClicked(Font font, SecurityOsScreen.Area a, int y, CompoundTag device, int mx, int my) {
        List<String> labels = portLabels(font, a, device);
        ListTag ports = device.getList("ports", Tag.TAG_COMPOUND);
        int x = a.x() + COL_NAME + 8;
        for (int i = 0; i < ports.size() && i < labels.size(); i++) {
            CompoundTag port = ports.getCompound(i);
            int w = OsDraw.buttonWidth(font, labels.get(i));
            if (OsDraw.inside(mx, my, x, y + 1, w, OsDraw.BUTTON_H)) {
                CompoundTag args = new CompoundTag();
                args.putInt("x", device.getInt("x"));
                args.putInt("y", device.getInt("y"));
                args.putInt("z", device.getInt("z"));
                args.putInt("side", port.getInt("s"));
                args.putBoolean("on", !port.getBoolean("on"));
                screen.send("sw_port", args);
                return true;
            }
            x += w + 3;
        }
        return false;
    }

    private static BlockPos posOf(CompoundTag device) {
        return new BlockPos(device.getInt("x"), device.getInt("y"), device.getInt("z"));
    }

    /** Een keycardlezer, codepaneel of (hoofdstuk 38) netwerkknop of -hendel? Die kun je een andere naam geven. */
    private static boolean renamable(CompoundTag device) {
        String kind = device.getString("k");
        return kind.equals("reader") || kind.equals("panel") || kind.equals("button") || kind.equals("lever");
    }

    private boolean isRenaming(CompoundTag device) {
        return renaming != null && renaming.equals(posOf(device));
    }

    /** De knop rechts bij een lezer of paneel: Rename, of Save terwijl je de naam typt. */
    private String renameLabel(CompoundTag device) {
        return isRenaming(device) ? "Save" : "Rename";
    }

    private EditBox nameBox() {
        if (nameBox == null) {
            nameBox = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 10, Component.literal("Device name"));
            nameBox.setBordered(false);
            nameBox.setMaxLength(InputDeviceBlockEntity.MAX_NAME);
            // Dezelfde tekens die de server weghaalt (zie InputDeviceBlockEntity.cleanName): wat je ziet, wordt de naam.
            nameBox.setFilter(value -> value.indexOf('§') < 0 && value.indexOf('"') < 0 && value.indexOf('\\') < 0);
            nameBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
        }
        return nameBox;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
        // De schakelaars van het hele netwerk.
        CompoundTag access = access();
        int bx = a.x();
        bx = switchButton(g, font, bx, a.y(), "Drones", access.getBoolean("drones")) + 6;
        bx = switchButton(g, font, bx, a.y(), "Lasers", access.getBoolean("lasers")) + 6;
        switchButton(g, font, bx, a.y(), "Cameras", access.getBoolean("cameras"));
        // De lijst.
        int top = listTop(a);
        int bottom = a.y() + a.h();
        OsDraw.field(g, a.x(), top - 1, a.x() + a.w(), bottom, false);
        List<Row> rows = rows();
        if (renaming != null && rows.stream().noneMatch(row -> row.device() != null && isRenaming(row.device()))) {
            renaming = null; // de lezer is weg (afgebroken)
        }
        if (rows.isEmpty()) {
            g.drawString(font, "No turrets, drones, laser gates, bridges, cameras,", a.x() + 5, top + 4, HudStyle.argb(255, OsDraw.GREY), false);
            // Hoofdstuk 38: apparaten horen bij deze desktop via een netwerkkabel (de config van de server zegt of dat moet).
            g.drawString(font, com.senne.imttech.config.ImtConfig.Network.REQUIRED
                            ? "readers, code panels or switches on this network" : "readers, code panels or switches in this area yet.",
                    a.x() + 5, top + 14, HudStyle.argb(255, OsDraw.GREY), false);
            if (com.senne.imttech.config.ImtConfig.Network.REQUIRED) {
                g.drawString(font, "yet: connect them to this core with a Network Cable.", a.x() + 5, top + 24,
                        HudStyle.argb(255, OsDraw.GREY), false);
            }
            return;
        }
        OsDraw.tableHeader(g, font, a.x() + 2, a.x() + a.w() - 2, top + 2,
                new int[] {a.x() + COL_NAME, a.x() + COL_WHERE, a.x() + COL_STATUS}, new String[] {"NAME", "WHERE", "STATUS"});
        scroll = Math.max(0, Math.min(scroll, rows.size() - 1));
        int y = rowsTop(a);
        for (int i = scroll; i < rows.size() && y + height(rows.get(i)) <= bottom; i++) {
            Row row = rows.get(i);
            if (row.header() != null) {
                // Een kopje (TURRETS): een donkere band over de hele breedte.
                g.fill(a.x() + 2, y, a.x() + a.w() - 2, y + HEADER_H - 1, HudStyle.argb(255, 0x10222D));
                g.drawString(font, row.header(), a.x() + COL_NAME, y + 2, HudStyle.argb(255, HudStyle.CYAN), false);
            } else if (row.ports()) {
                renderPorts(g, font, a, y, row.device());
                OsDraw.rowLine(g, a.x() + 2, a.x() + a.w() - 2, y + DEVICE_H - 1);
            } else {
                renderDevice(g, font, a, y, row.device(), mx, my, partialTick);
                OsDraw.rowLine(g, a.x() + 2, a.x() + a.w() - 2, y + DEVICE_H - 1);
            }
            y += height(row);
        }
    }

    /** Een knop "Drones: ON" (groen) of "Drones: OFF" (rood). Geeft de rechterkant terug. */
    private static int switchButton(GuiGraphics g, Font font, int x, int y, String name, boolean on) {
        String label = name + ": " + (on ? "ON" : "OFF");
        return x + OsDraw.button(g, font, x, y, label, true, on ? HudStyle.GREEN : HudStyle.RED);
    }

    /** Eén apparaat op één regel: naam, plek, toestand (met wat erbij hoort) en rechts zijn knop. */
    private void renderDevice(GuiGraphics g, Font font, SecurityOsScreen.Area a, int y, CompoundTag device,
                              int mx, int my, float partialTick) {
        String kind = device.getString("k");
        // Rechts de knop: aan/uit van een torentje, Rename (of Save) van een lezer of paneel.
        String button = buttonLabel(device);
        int buttonX = a.x() + a.w() - 4 - (button == null ? 0 : OsDraw.buttonWidth(font, button));
        if (button != null) {
            OsDraw.button(g, font, buttonX, y + 1, button, true, buttonColour(device));
        }
        String extra = extraLabel(device);
        if (extra != null) {
            buttonX -= OsDraw.buttonWidth(font, extra) + 4;
            OsDraw.button(g, font, buttonX, y + 1, extra, true, HudStyle.CYAN);
        }
        if (isRenaming(device)) {
            // Het vak waar je de nieuwe naam typt (over de naam en de plek), en in de kolom ernaast hoe het werkt.
            int right = a.x() + COL_STATUS - 6;
            OsDraw.field(g, a.x() + COL_NAME - 1, y, right, y + DEVICE_H - 2, true);
            EditBox box = nameBox();
            box.setX(a.x() + COL_NAME + 2);
            box.setY(y + 2);
            box.setWidth(right - a.x() - COL_NAME - 4);
            box.render(g, mx, my, partialTick);
            g.drawString(font, OsDraw.fitRight(font, "Enter: save, Esc: cancel", buttonX - a.x() - COL_STATUS - 4),
                    a.x() + COL_STATUS, y + 3, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        String where = device.getInt("x") + " " + device.getInt("y") + " " + device.getInt("z");
        g.drawString(font, OsDraw.fitRight(font, device.getString("name"), COL_WHERE - COL_NAME - 6), a.x() + COL_NAME, y + 3,
                HudStyle.argb(255, HudStyle.WHITE), false);
        g.drawString(font, OsDraw.fitRight(font, where, COL_STATUS - COL_WHERE - 6), a.x() + COL_WHERE, y + 3,
                HudStyle.argb(255, OsDraw.GREY), false);
        // De toestand, en wat erbij hoort (tot aan de knop).
        String status = device.getString("st");
        int colour = switch (status) {
            case "online", "patrolling", "active", "ready" -> HudStyle.GREEN;
            case "attacking", "standby" -> HudStyle.ORANGE;
            case "open", "retracted" -> HudStyle.CYAN;
            default -> HudStyle.RED;
        };
        StringBuilder details = new StringBuilder();
        if (device.contains("ammo")) {
            details.append("  ").append(device.getInt("ammo")).append(" rockets");
        }
        if (kind.equals("drone") && device.contains("hp")) {
            details.append("  hp ").append(device.getInt("hp")).append("/").append(device.getInt("max"));
        }
        if (device.contains("bat")) {
            // Een security drone (hoofdstuk 25): zijn batterij.
            details.append("  battery ").append(device.getInt("bat")).append("%");
        }
        if (device.contains("tgt")) {
            details.append("  > ").append(device.getString("tgt"));
        }
        if (kind.equals("lever")) {
            details.append(device.getBoolean("on") ? "  on" : "  off"); // hoe de hendel nu staat
        }
        if (device.contains("uses")) {
            // Zie je hier "in no diagram" na een andere naam, dan gebruikt je diagram nog de oude naam.
            int uses = device.getInt("uses");
            details.append(uses == 0 ? "  in no diagram" : uses == 1 ? "  in 1 diagram" : "  in " + uses + " diagrams");
        }
        int room = buttonX - a.x() - COL_STATUS - 4;
        String shownStatus = OsDraw.fitRight(font, status, room);
        g.drawString(font, shownStatus, a.x() + COL_STATUS, y + 3, HudStyle.argb(255, colour), false);
        int detailsX = a.x() + COL_STATUS + font.width(shownStatus);
        g.drawString(font, OsDraw.fitRight(font, details.toString(), Math.max(0, buttonX - detailsX - 4)), detailsX, y + 3,
                HudStyle.argb(255, OsDraw.GREY), false);
    }

    // ================================================================ Muis

    void mouseClicked(Font font, SecurityOsScreen.Area a, int mx, int my) {
        CompoundTag access = access();
        // De twee schakelaars bovenaan.
        String drones = "Drones: " + (access.getBoolean("drones") ? "ON" : "OFF");
        int dronesW = OsDraw.buttonWidth(font, drones);
        if (OsDraw.inside(mx, my, a.x(), a.y(), dronesW, OsDraw.BUTTON_H)) {
            flag("drones", !access.getBoolean("drones"));
            renaming = null;
            return;
        }
        String lasers = "Lasers: " + (access.getBoolean("lasers") ? "ON" : "OFF");
        int lasersW = OsDraw.buttonWidth(font, lasers);
        if (OsDraw.inside(mx, my, a.x() + dronesW + 6, a.y(), lasersW, OsDraw.BUTTON_H)) {
            flag("lasers", !access.getBoolean("lasers"));
            renaming = null;
            return;
        }
        String cameras = "Cameras: " + (access.getBoolean("cameras") ? "ON" : "OFF");
        if (OsDraw.inside(mx, my, a.x() + dronesW + lasersW + 12, a.y(), OsDraw.buttonWidth(font, cameras), OsDraw.BUTTON_H)) {
            flag("cameras", !access.getBoolean("cameras"));
            renaming = null;
            return;
        }
        // De knop rechts op de rij van een apparaat: aan/uit van een torentje, Rename of Save van een lezer of paneel.
        List<Row> rows = rows();
        int y = rowsTop(a);
        int bottom = a.y() + a.h();
        for (int i = scroll; i < rows.size() && y + height(rows.get(i)) <= bottom; i++) {
            CompoundTag device = rows.get(i).device();
            if (rows.get(i).ports()) {
                if (portsClicked(font, a, y, device, mx, my)) {
                    return;
                }
                y += height(rows.get(i));
                continue;
            }
            String button = device == null ? null : buttonLabel(device);
            if (button != null) {
                int w = OsDraw.buttonWidth(font, button);
                if (OsDraw.inside(mx, my, a.x() + a.w() - 4 - w, y + 1, w, OsDraw.BUTTON_H)) {
                    if (device.getString("k").equals("switch") && !isRenaming(device)) {
                        // Ports / Close: de regel met de zes poorten open of dicht.
                        portsOpen = posOf(device).equals(portsOpen) ? null : posOf(device);
                        renaming = null;
                    } else if (switchable(device)) {
                        CompoundTag args = new CompoundTag();
                        args.putInt("x", device.getInt("x"));
                        args.putInt("y", device.getInt("y"));
                        args.putInt("z", device.getInt("z"));
                        args.putBoolean("on", !device.getBoolean("on"));
                        screen.send("dev_toggle", args);
                        renaming = null;
                    } else if (isRenaming(device)) {
                        submitName();
                    } else {
                        startRename(device);
                    }
                    return;
                }
                // De tweede knop links ervan (Rename bij een switch).
                String extra = extraLabel(device);
                int extraW = extra == null ? 0 : OsDraw.buttonWidth(font, extra);
                if (extra != null && OsDraw.inside(mx, my, a.x() + a.w() - 4 - w - 4 - extraW, y + 1, extraW, OsDraw.BUTTON_H)) {
                    startRename(device);
                    return;
                }
            }
            if (device != null && isRenaming(device)
                    && OsDraw.inside(mx, my, a.x() + COL_NAME - 1, y, COL_STATUS - COL_NAME - 5, DEVICE_H - 2)) {
                nameBox().mouseClicked(mx, my, 0); // de cursor verplaatsen
                return;
            }
            y += height(rows.get(i));
        }
        renaming = null; // ergens anders geklikt: de naam blijft zoals hij was
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
    }

    private void flag(String name, boolean on) {
        CompoundTag args = new CompoundTag();
        args.putString("name", name);
        args.putBoolean("on", on);
        screen.send("flag", args);
    }

    // ================================================================ Een andere naam

    /** De knop Rename: het naamvak met de huidige naam, helemaal geselecteerd (typen vervangt hem). */
    private void startRename(CompoundTag device) {
        EditBox box = nameBox();
        box.setValue(device.getString("name"));
        box.moveCursorToEnd(false);
        box.setHighlightPos(0);
        box.setFocused(true);
        renaming = posOf(device);
    }

    /** De nieuwe naam naar de server (die maakt hem netjes, en zegt het als hij niet mag). */
    private void submitName() {
        BlockPos pos = renaming;
        renaming = null;
        if (pos == null) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putInt("x", pos.getX());
        args.putInt("y", pos.getY());
        args.putInt("z", pos.getZ());
        args.putString("name", nameBox().getValue());
        screen.send("input_rename", args);
    }

    /** Typ je een naam, dan gaan de toetsen naar het naamvak: Enter bewaart, Esc laat het zoals het was. */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renaming == null) {
            return false;
        }
        switch (keyCode) {
            case 257, 335 -> submitName(); // enter
            case 256 -> renaming = null; // esc
            default -> nameBox().keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    /** Een letter getypt: alleen als je een naam aan het typen bent. */
    boolean charTyped(char codePoint, int modifiers) {
        if (renaming == null) {
            return false;
        }
        nameBox().charTyped(codePoint, modifiers);
        return true;
    }

    /** Een ander venster kwam bovenop, of dit venster ging dicht: stop met typen (de naam blijft zoals hij was). */
    void blur() {
        renaming = null;
    }

    /** Alleen voor de screenshot-rondleiding: de naam van de eerste lezer of het eerste paneel aan het veranderen. */
    void tourRename(String name) {
        for (Row row : rows()) {
            if (row.device() != null && renamable(row.device())) {
                startRename(row.device());
                nameBox().setValue(name);
                return;
            }
        }
    }

    /** Alleen voor de screenshot-rondleiding: op Save klikken (de naam gaat echt naar de server). */
    void tourSave() {
        submitName();
    }

    /** Alleen voor de screenshot-rondleiding: de lijst naar beneden tot dit kopje (bovenaan). */
    void tourScroll(String header) {
        List<Row> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            if (header.equals(rows.get(i).header())) {
                scroll = i;
                return;
            }
        }
    }

    /** Alleen voor de screenshot-rondleiding: de poorten van de eerste Network Switch open (de knop Ports). */
    void tourPorts() {
        for (Row row : rows()) {
            if (row.device() != null && row.device().getString("k").equals("switch")) {
                portsOpen = posOf(row.device());
                return;
            }
        }
    }
}
