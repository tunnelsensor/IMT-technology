package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.logic.InputDeviceBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Het Devices-venster van de Stark OS-desktop (jouw wens, 28 september: de torentjes, drones,
 * laserwanden en camera's bekijken en bedienen). Bovenaan drie schakelaars voor het hele netwerk
 * (drones, laserwanden, camera's), daaronder elk apparaat met zijn status:
 * <ul>
 *   <li>torentjes: aan/uit (een eigen knop per torentje), stroom, raketten, waar hij op richt;</li>
 *   <li>drones: levens, wat hij doet, op wie hij schiet;</li>
 *   <li>laserpoorten: aan of open;</li>
 *   <li>camera's: beeld of niet (het beeld zelf zie je in de app Cameras);</li>
 *   <li>keycardlezers en codepanelen: of de server ze hoort, in hoeveel diagrammen ze staan, en een
 *       knop Rename (jouw wens van 8 oktober). Typ de naam, Enter bewaart, Esc laat het zoals het was.</li>
 * </ul>
 * De server zoekt zelf welke apparaten bij de kern horen (zie SecurityDevices) en stuurt de lijst
 * mee met elke momentopname.
 */
final class DevicesApp {
    /** Een regel in de lijst: een kopje ("TURRETS") of een apparaat. */
    private record Row(@Nullable String header, @Nullable CompoundTag device) {}

    private static final int HEADER_H = 12;
    private static final int DEVICE_H = 21;

    private final StarkOsScreen screen;
    private int scroll;
    /** Welke lezer of welk paneel je een andere naam aan het geven bent (de plek), of null. */
    private @Nullable BlockPos renaming;
    private @Nullable EditBox nameBox;

    DevicesApp(StarkOsScreen screen) {
        this.screen = screen;
    }

    private CompoundTag access() {
        return screen.data().getCompound("access");
    }

    /** De lijst: per soort een kopje en dan de apparaten van die soort. */
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        String[][] kinds = {{"turret", "TURRETS"}, {"drone", "DRONES"}, {"laser", "LASER GATES"}, {"camera", "CAMERAS"},
                {"reader", "KEYCARD READERS"}, {"panel", "CODE PANELS"}};
        List<Tag> devices = screen.data().getList("devices", Tag.TAG_COMPOUND);
        for (String[] kind : kinds) {
            boolean any = false;
            for (Tag element : devices) {
                CompoundTag device = (CompoundTag) element;
                if (!device.getString("k").equals(kind[0])) {
                    continue;
                }
                if (!any) {
                    rows.add(new Row(kind[1], null));
                    any = true;
                }
                rows.add(new Row(null, device));
            }
        }
        return rows;
    }

    private static int height(Row row) {
        return row.header() != null ? HEADER_H : DEVICE_H;
    }

    private static int listTop(StarkOsScreen.Area a) {
        return a.y() + OsDraw.BUTTON_H + 5;
    }

    private static BlockPos posOf(CompoundTag device) {
        return new BlockPos(device.getInt("x"), device.getInt("y"), device.getInt("z"));
    }

    /** Een keycardlezer of codepaneel? Die kun je een andere naam geven. */
    private static boolean renamable(CompoundTag device) {
        String kind = device.getString("k");
        return kind.equals("reader") || kind.equals("panel");
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

    void render(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my, float partialTick) {
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
            g.drawString(font, "No turrets, drones, laser gates, cameras, readers", a.x() + 5, top + 4, HudStyle.argb(255, OsDraw.GREY), false);
            g.drawString(font, "or code panels in this network's area yet.", a.x() + 5, top + 14, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        scroll = Math.max(0, Math.min(scroll, rows.size() - 1));
        int y = top + 2;
        for (int i = scroll; i < rows.size() && y + height(rows.get(i)) <= bottom; i++) {
            Row row = rows.get(i);
            if (row.header() != null) {
                g.drawString(font, row.header(), a.x() + 4, y + 2, HudStyle.argb(255, HudStyle.CYAN), false);
            } else {
                renderDevice(g, font, a, y, row.device(), mx, my, partialTick);
            }
            y += height(row);
        }
    }

    /** Een knop "Drones: ON" (groen) of "Drones: OFF" (rood). Geeft de rechterkant terug. */
    private static int switchButton(GuiGraphics g, Font font, int x, int y, String name, boolean on) {
        String label = name + ": " + (on ? "ON" : "OFF");
        return x + OsDraw.button(g, font, x, y, label, true, on ? HudStyle.GREEN : HudStyle.RED);
    }

    private void renderDevice(GuiGraphics g, Font font, StarkOsScreen.Area a, int y, CompoundTag device,
                              int mx, int my, float partialTick) {
        String kind = device.getString("k");
        // Een lezer of paneel heeft rechts de knop Rename (of Save).
        if (renamable(device)) {
            String label = renameLabel(device);
            OsDraw.button(g, font, a.x() + a.w() - OsDraw.buttonWidth(font, label) - 4, y + 2, label, true,
                    isRenaming(device) ? HudStyle.GREEN : HudStyle.CYAN);
        }
        if (isRenaming(device)) {
            // Het vak waar je de nieuwe naam typt (in plaats van de naam), en eronder hoe het werkt.
            int right = a.x() + a.w() - OsDraw.buttonWidth(font, renameLabel(device)) - 10;
            OsDraw.field(g, a.x() + 4, y - 1, right, y + 10, true);
            EditBox box = nameBox();
            box.setX(a.x() + 7);
            box.setY(y + 1);
            box.setWidth(right - a.x() - 10);
            box.render(g, mx, my, partialTick);
            g.drawString(font, "Enter = save   Esc = cancel", a.x() + 10, y + 11, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        String where = device.getInt("x") + " " + device.getInt("y") + " " + device.getInt("z");
        g.drawString(font, device.getString("name"), a.x() + 6, y + 1, HudStyle.argb(255, HudStyle.WHITE), false);
        g.drawString(font, where, a.x() + 10 + font.width(device.getString("name")), y + 1,
                HudStyle.argb(255, OsDraw.GREY), false);
        // Tweede regel: de status, en wat erbij hoort.
        String status = device.getString("st");
        int colour = switch (status) {
            case "online", "patrolling", "active", "ready" -> HudStyle.GREEN;
            case "attacking" -> HudStyle.ORANGE;
            case "open" -> HudStyle.CYAN;
            default -> HudStyle.RED;
        };
        StringBuilder details = new StringBuilder();
        if (device.contains("ammo")) {
            details.append("  ").append(device.getInt("ammo")).append(" rockets");
        }
        if (kind.equals("drone")) {
            details.append("  hp ").append(device.getInt("hp")).append("/").append(device.getInt("max"));
        }
        if (device.contains("tgt")) {
            details.append("  > ").append(device.getString("tgt"));
        }
        if (device.contains("uses")) {
            // Zie je hier "in no diagram" na een andere naam, dan gebruikt je diagram nog de oude naam.
            int uses = device.getInt("uses");
            details.append(uses == 0 ? "  in no diagram" : uses == 1 ? "  in 1 diagram" : "  in " + uses + " diagrams");
        }
        g.drawString(font, status, a.x() + 10, y + 10, HudStyle.argb(255, colour), false);
        g.drawString(font, details.toString(), a.x() + 10 + font.width(status), y + 10,
                HudStyle.argb(255, OsDraw.GREY), false);
        // Een torentje heeft zijn eigen aan/uit-knop.
        if (kind.equals("turret")) {
            boolean on = device.getBoolean("on");
            String label = on ? "Turn off" : "Turn on";
            OsDraw.button(g, font, a.x() + a.w() - OsDraw.buttonWidth(font, label) - 4, y + 2, label, true,
                    on ? HudStyle.RED : HudStyle.GREEN);
        }
    }

    // ================================================================ Muis

    void mouseClicked(Font font, StarkOsScreen.Area a, int mx, int my) {
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
        // De knoppen bij een apparaat: aan/uit van een torentje, Rename of Save van een lezer of paneel.
        List<Row> rows = rows();
        int y = listTop(a) + 2;
        int bottom = a.y() + a.h();
        for (int i = scroll; i < rows.size() && y + height(rows.get(i)) <= bottom; i++) {
            CompoundTag device = rows.get(i).device();
            if (device != null && device.getString("k").equals("turret")) {
                boolean on = device.getBoolean("on");
                String label = on ? "Turn off" : "Turn on";
                int w = OsDraw.buttonWidth(font, label);
                if (OsDraw.inside(mx, my, a.x() + a.w() - w - 4, y + 2, w, OsDraw.BUTTON_H)) {
                    CompoundTag args = new CompoundTag();
                    args.putInt("x", device.getInt("x"));
                    args.putInt("y", device.getInt("y"));
                    args.putInt("z", device.getInt("z"));
                    args.putBoolean("on", !on);
                    screen.send("dev_toggle", args);
                    renaming = null;
                    return;
                }
            }
            if (device != null && renamable(device)) {
                int w = OsDraw.buttonWidth(font, renameLabel(device));
                if (OsDraw.inside(mx, my, a.x() + a.w() - w - 4, y + 2, w, OsDraw.BUTTON_H)) {
                    if (isRenaming(device)) {
                        submitName();
                    } else {
                        startRename(device);
                    }
                    return;
                }
                if (isRenaming(device) && OsDraw.inside(mx, my, a.x() + 4, y - 1, a.w() - w - 14, 11)) {
                    nameBox().mouseClicked(mx, my, 0); // de cursor verplaatsen
                    return;
                }
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
}
