package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.security.os.VirtualFs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Het Files-venster van de Stark OS-desktop (jouw wens, 28 september: je eigen mappen beheren,
 * security.conf verstoppen en valstrikken zetten). Het werkt als de Verkenner van Windows:
 * <ul>
 *   <li>bovenaan knoppen: een map omhoog, nieuwe map, nieuw bestand, hernoemen, verwijderen en
 *       (alleen voor de eigenaar) een valstrik maken;</li>
 *   <li>dubbelklik op een map gaat erin, dubbelklik op een bestand opent het in Notes;</li>
 *   <li>sleep een bestand of map op een andere map (of op "..") om het te verplaatsen;</li>
 *   <li>rechtsklik geeft een menu met dezelfde keuzes, ook "Mark as trap".</li>
 * </ul>
 * Dit venster verandert zelf niets: elke keuze gaat als opdracht naar de server (StarkOs), die
 * controleert of het mag en daarna de nieuwe mappenlijst terugstuurt.
 *
 * Wie niet de eigenaar is, ziet niet welke bestanden valstrikken zijn (dat stuurt de server niet).
 */
final class FilesApp {
    /** Eén map of bestand zoals de server het stuurde. */
    record Entry(String path, boolean dir, boolean system, boolean trap, int size) {
        String name() {
            return VirtualFs.nameOf(path);
        }
    }

    /** Eén regel in de lijst: een map of bestand, of ".." (een map omhoog). */
    private record Row(Entry entry, boolean up) {}

    /** Wat je aan het typen bent in het naamvak onderaan. */
    private enum Input { NONE, NEW_FOLDER, NEW_FILE, RENAME }

    /** De knoppen bovenaan. */
    private enum Tool { UP, FOLDER, FILE, RENAME, DELETE, TRAP }

    /** De keuzes in het rechtsklik-menu. */
    private enum MenuItem { OPEN, RENAME, DELETE, TRAP }

    private static final int ROW = 11;
    /** Twee kliks binnen deze tijd (ms) op hetzelfde = dubbelklik. */
    private static final long DOUBLE_CLICK_MS = 350;
    /** Zo lang (ms) wacht "Sure?" op de tweede klik bij verwijderen. */
    private static final long CONFIRM_MS = 3000;

    private final StarkOsScreen screen;
    /** Alle mappen en bestanden (de laatste lijst van de server). */
    private final List<Entry> entries = new ArrayList<>();
    /** De map die je nu bekijkt. */
    private String cwd = "/";
    private @Nullable String selected;
    private int scroll;
    /** Hoeveel regels er bij de laatste keer tekenen in de lijst pasten (voor de pijltjestoetsen). */
    private int visibleRows = 1;

    // Het naamvak onderaan (nieuwe map, nieuw bestand, hernoemen)
    private Input input = Input.NONE;
    private @Nullable EditBox nameBox;
    private @Nullable String renameFrom;

    // Dubbelklikken
    private long lastClickTime;
    private @Nullable String lastClickPath;

    // Slepen
    private @Nullable String pressed;
    private int pressX;
    private int pressY;
    private boolean dragging;
    private int mouseX;
    private int mouseY;

    // Het rechtsklik-menu
    private @Nullable String menuFor;
    private int menuX;
    private int menuY;

    // Verwijderen bevestigen
    private @Nullable String confirmDelete;
    private long confirmUntil;

    FilesApp(StarkOsScreen screen) {
        this.screen = screen;
    }

    // ================================================================ Gegevens van de server

    /** Een nieuwe mappenlijst (de server stuurt hem alleen als er iets veranderde). */
    void update(ListTag list) {
        entries.clear();
        for (Tag element : list) {
            CompoundTag item = (CompoundTag) element;
            entries.add(new Entry(item.getString("p"), item.getBoolean("d"), item.getBoolean("s"),
                    item.getBoolean("t"), item.getInt("n")));
        }
        // Bestaat de map waar je stond niet meer (weggegooid of verplaatst)? Dan terug naar /.
        if (!cwd.equals("/") && find(cwd) == null) {
            cwd = "/";
            scroll = 0;
        }
        if (selected != null && find(selected) == null) {
            selected = null;
        }
    }

    @Nullable Entry find(String path) {
        for (Entry entry : entries) {
            if (entry.path().equals(path)) {
                return entry;
            }
        }
        return null;
    }

    /** De map die je nu bekijkt (Notes maakt daar een nieuwe notitie). */
    String cwd() {
        return cwd;
    }

    /** {@code /home} + {@code a.txt} = {@code /home/a.txt} (en in de bovenste map {@code /a.txt}). */
    static String child(String dir, String name) {
        return (dir.equals("/") ? "" : dir) + "/" + name;
    }

    /** Wat er in de map staat die je bekijkt: eerst ".." (als je niet bovenaan bent), dan de mappen, dan de bestanden. */
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        if (!cwd.equals("/")) {
            rows.add(new Row(new Entry(VirtualFs.parentOf(cwd), true, false, false, 0), true));
        }
        entries.stream()
                .filter(entry -> VirtualFs.parentOf(entry.path()).equals(cwd))
                .sorted(Comparator.comparing((Entry entry) -> !entry.dir())
                        .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)))
                .forEach(entry -> rows.add(new Row(entry, false)));
        return rows;
    }

    private @Nullable Entry selectedEntry() {
        return selected == null ? null : find(selected);
    }

    // ================================================================ Het vak indelen

    private static int listTop(StarkOsScreen.Area a) {
        return a.y() + 27;
    }

    private static int listBottom(StarkOsScreen.Area a) {
        return a.y() + a.h() - OsDraw.BUTTON_H - 3;
    }

    private @Nullable Row rowAt(StarkOsScreen.Area a, int mx, int my) {
        int top = listTop(a);
        if (mx < a.x() || mx >= a.x() + a.w() || my < top || my >= listBottom(a)) {
            return null;
        }
        List<Row> rows = rows();
        int index = scroll + (my - top) / ROW;
        return index < rows.size() ? rows.get(index) : null;
    }

    private List<Tool> tools() {
        return screen.isOwner() ? List.of(Tool.values())
                : List.of(Tool.UP, Tool.FOLDER, Tool.FILE, Tool.RENAME, Tool.DELETE);
    }

    private String label(Tool tool) {
        Entry selection = selectedEntry();
        return switch (tool) {
            case UP -> "Up";
            case FOLDER -> "+Folder";
            case FILE -> "+File";
            case RENAME -> "Rename";
            case DELETE -> confirming(selected) ? "Sure?" : "Delete";
            case TRAP -> selection != null && selection.trap() ? "Untrap" : "Trap";
        };
    }

    private boolean enabled(Tool tool, @Nullable Entry selection) {
        return switch (tool) {
            case UP -> !cwd.equals("/");
            case FOLDER, FILE -> true;
            case RENAME -> selection != null;
            case DELETE -> selection != null && !holdsSystemFile(selection);
            case TRAP -> selection != null && !selection.dir() && !selection.system();
        };
    }

    /** Is dit security.conf, of zit het erin? Dan kan het niet weg (wel verplaatsen). */
    private boolean holdsSystemFile(Entry entry) {
        return entry.system() || entry.dir() && entries.stream()
                .anyMatch(other -> other.system() && other.path().startsWith(entry.path() + "/"));
    }

    private @Nullable Tool toolAt(Font font, StarkOsScreen.Area a, int mx, int my) {
        int bx = a.x();
        for (Tool tool : tools()) {
            int w = OsDraw.buttonWidth(font, label(tool));
            if (OsDraw.inside(mx, my, bx, a.y(), w, OsDraw.BUTTON_H)) {
                return tool;
            }
            bx += w + 3;
        }
        return null;
    }

    private boolean confirming(@Nullable String path) {
        return path != null && path.equals(confirmDelete) && Util.getMillis() < confirmUntil;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my, float partialTick) {
        mouseX = mx;
        mouseY = my;
        Entry selection = selectedEntry();
        // De knoppen.
        int bx = a.x();
        for (Tool tool : tools()) {
            int accent = tool == Tool.DELETE || tool == Tool.TRAP ? HudStyle.RED : HudStyle.CYAN;
            bx += OsDraw.button(g, font, bx, a.y(), label(tool), enabled(tool, selection), accent) + 3;
        }
        // Waar je bent.
        g.drawString(font, OsDraw.fitLeft(font, cwd, a.w()), a.x(), a.y() + 16, HudStyle.argb(255, HudStyle.CYAN), false);
        // De lijst.
        int top = listTop(a);
        int bottom = listBottom(a);
        OsDraw.field(g, a.x(), top - 1, a.x() + a.w(), bottom + 1, false);
        List<Row> rows = rows();
        visibleRows = Math.max(1, (bottom - top) / ROW);
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows));
        Row hover = rowAt(a, mx, my);
        for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows); i++) {
            Row row = rows.get(i);
            Entry entry = row.entry();
            int ry = top + (i - scroll) * ROW;
            boolean isSelected = !row.up() && entry.path().equals(selected);
            boolean isDrop = dragging && row.equals(hover) && entry.dir() && !entry.path().equals(pressed);
            if (isDrop) {
                g.fill(a.x() + 1, ry, a.x() + a.w() - 1, ry + ROW, HudStyle.argb(255, 0x1E5A3A));
            } else if (isSelected) {
                g.fill(a.x() + 1, ry, a.x() + a.w() - 1, ry + ROW, HudStyle.argb(255, 0x1E4A60));
            } else if (row.equals(hover)) {
                g.fill(a.x() + 1, ry, a.x() + a.w() - 1, ry + ROW, HudStyle.argb(255, 0x14303D));
            }
            icon(g, a.x() + 3, ry + 1, entry, row.up());
            String name = row.up() ? ".." : entry.name();
            g.drawString(font, name, a.x() + 15, ry + 2, HudStyle.argb(255, row.up() ? OsDraw.GREY : HudStyle.WHITE), false);
            // Rechts: de vlaggen en de grootte.
            if (!entry.dir()) {
                String size = entry.size() + " B";
                int sx = a.x() + a.w() - 4 - font.width(size);
                g.drawString(font, size, sx, ry + 2, HudStyle.argb(255, OsDraw.GREY), false);
                if (entry.trap()) {
                    sx -= font.width("TRAP") + 6;
                    g.drawString(font, "TRAP", sx, ry + 2, HudStyle.argb(255, HudStyle.RED), false);
                }
                if (entry.system()) {
                    sx -= font.width("SYS") + 6;
                    g.drawString(font, "SYS", sx, ry + 2, HudStyle.argb(255, HudStyle.ORANGE), false);
                }
            }
        }
        if (rows.isEmpty() || rows.size() == 1 && rows.getFirst().up()) {
            g.drawString(font, "(empty folder)", a.x() + 15, top + 2 + (rows.isEmpty() ? 0 : ROW),
                    HudStyle.argb(255, OsDraw.GREY), false);
        }
        // Onderaan: het naamvak, of een korte uitleg.
        int by = a.y() + a.h() - OsDraw.BUTTON_H;
        if (input != Input.NONE && nameBox != null) {
            renderInput(g, font, a, by, mx, my, partialTick);
        } else {
            g.drawString(font, "Double-click: open   Drag: move   Right-click: menu", a.x(), by + 2,
                    HudStyle.argb(255, OsDraw.GREY), false);
        }
        // Slepen: de naam hangt aan de muis.
        if (dragging && pressed != null) {
            String name = VirtualFs.nameOf(pressed);
            g.fill(mx + 6, my + 2, mx + 12 + font.width(name), my + 13, HudStyle.argb(220, 0x16303F));
            g.drawString(font, name, mx + 9, my + 4, HudStyle.argb(255, HudStyle.CYAN), false);
        }
        // Het rechtsklik-menu, bovenop.
        if (menuFor != null) {
            renderMenu(g, font, a, mx, my);
        }
    }

    private void renderInput(GuiGraphics g, Font font, StarkOsScreen.Area a, int by, int mx, int my, float partialTick) {
        String label = switch (input) {
            case NEW_FOLDER -> "Folder:";
            case NEW_FILE -> "File:";
            default -> "Name:";
        };
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

    private static void icon(GuiGraphics g, int x, int y, Entry entry, boolean up) {
        if (entry.dir()) {
            int colour = HudStyle.argb(255, up ? OsDraw.GREY : 0xE8C25A);
            g.fill(x, y + 1, x + 4, y + 2, colour); // het lipje
            g.fill(x, y + 2, x + 9, y + 8, colour); // de map
        } else {
            int colour = HudStyle.argb(255, entry.trap() ? HudStyle.RED : entry.system() ? HudStyle.ORANGE : HudStyle.WHITE);
            int line = HudStyle.argb(255, OsDraw.FIELD);
            g.fill(x + 1, y, x + 8, y + 9, colour); // het blad
            g.fill(x + 2, y + 2, x + 7, y + 3, line); // regeltjes tekst
            g.fill(x + 2, y + 4, x + 7, y + 5, line);
            g.fill(x + 2, y + 6, x + 5, y + 7, line);
        }
    }

    // ---------------------------------------------------------------- Het rechtsklik-menu

    private List<MenuItem> menuItems(Entry entry) {
        List<MenuItem> items = new ArrayList<>(List.of(MenuItem.OPEN, MenuItem.RENAME));
        if (!holdsSystemFile(entry)) {
            items.add(MenuItem.DELETE);
        }
        if (screen.isOwner() && !entry.dir() && !entry.system()) {
            items.add(MenuItem.TRAP);
        }
        return items;
    }

    private String menuLabel(MenuItem item, Entry entry) {
        return switch (item) {
            case OPEN -> "Open";
            case RENAME -> "Rename";
            case DELETE -> confirming(entry.path()) ? "Click again to delete" : "Delete";
            case TRAP -> entry.trap() ? "Remove trap" : "Mark as trap";
        };
    }

    /** Waar het menu staat en hoe groot het is: {x, y, breedte, hoogte}. Het blijft binnen het venster. */
    private int[] menuBox(Font font, StarkOsScreen.Area a, Entry entry) {
        List<MenuItem> items = menuItems(entry);
        int w = 0;
        for (MenuItem item : items) {
            w = Math.max(w, font.width(menuLabel(item, entry)));
        }
        w += 10;
        int h = items.size() * ROW + 2;
        int x = Math.min(menuX, a.x() + a.w() - w);
        int y = Math.min(menuY, a.y() + a.h() - h);
        return new int[] {x, y, w, h};
    }

    private void renderMenu(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my) {
        Entry entry = find(menuFor);
        if (entry == null) {
            menuFor = null;
            return;
        }
        int[] box = menuBox(font, a, entry);
        g.fill(box[0], box[1], box[0] + box[2], box[1] + box[3], HudStyle.argb(250, 0x0E1A24));
        HudStyle.brackets(g, box[0], box[1], box[0] + box[2], box[1] + box[3], 4, HudStyle.argb(255, HudStyle.CYAN));
        int y = box[1] + 1;
        for (MenuItem item : menuItems(entry)) {
            if (OsDraw.inside(mx, my, box[0], y, box[2], ROW)) {
                g.fill(box[0] + 1, y, box[0] + box[2] - 1, y + ROW, HudStyle.argb(255, 0x1E4A60));
            }
            int colour = item == MenuItem.DELETE || item == MenuItem.TRAP ? HudStyle.RED : HudStyle.WHITE;
            g.drawString(font, menuLabel(item, entry), box[0] + 5, y + 2, HudStyle.argb(255, colour), false);
            y += ROW;
        }
    }

    // ================================================================ Muis

    boolean mouseClicked(Font font, StarkOsScreen.Area a, int mx, int my, int button) {
        // Een open menu krijgt de klik eerst: een keuze, of ergens anders = menu dicht.
        if (menuFor != null) {
            Entry entry = find(menuFor);
            if (entry != null) {
                int[] box = menuBox(font, a, entry);
                if (OsDraw.inside(mx, my, box[0], box[1], box[2], box[3])) {
                    int index = (my - box[1] - 1) / ROW;
                    List<MenuItem> items = menuItems(entry);
                    if (index >= 0 && index < items.size()) {
                        pickMenu(items.get(index), entry);
                    }
                    return true;
                }
            }
            menuFor = null;
            return true;
        }
        // Het naamvak onderaan.
        if (input != Input.NONE && nameBox != null) {
            int by = a.y() + a.h() - OsDraw.BUTTON_H;
            if (my >= by && my < by + OsDraw.BUTTON_H) {
                int okX = nameBox.getX() + nameBox.getWidth() + 6;
                int okW = OsDraw.buttonWidth(font, "OK");
                if (mx >= okX && mx < okX + okW) {
                    submitName();
                } else if (mx >= okX + okW + 3) {
                    cancelInput();
                } else {
                    nameBox.mouseClicked(mx, my, 0);
                }
                return true;
            }
        }
        // De knoppen bovenaan.
        Tool tool = toolAt(font, a, mx, my);
        if (tool != null) {
            press(tool);
            return true;
        }
        // De lijst.
        Row row = rowAt(a, mx, my);
        if (row == null) {
            if (my >= listTop(a) && my < listBottom(a)) {
                selected = null;
            }
            return true;
        }
        if (row.up()) {
            goUp();
            return true;
        }
        Entry entry = row.entry();
        selected = entry.path();
        if (button == 1) {
            menuFor = entry.path();
            menuX = mx;
            menuY = my;
            return true;
        }
        long now = Util.getMillis();
        boolean doubleClick = entry.path().equals(lastClickPath) && now - lastClickTime < DOUBLE_CLICK_MS;
        lastClickPath = entry.path();
        lastClickTime = now;
        if (doubleClick) {
            lastClickPath = null;
            open(entry);
            return true;
        }
        // Misschien begint hier een sleep.
        pressed = entry.path();
        pressX = mx;
        pressY = my;
        dragging = false;
        return true;
    }

    boolean mouseDragged(int mx, int my) {
        mouseX = mx;
        mouseY = my;
        if (pressed != null && !dragging && Math.abs(mx - pressX) + Math.abs(my - pressY) > 4) {
            dragging = true;
        }
        return pressed != null;
    }

    /** Losgelaten: lag je een sleep op een map? Dan gaat het erin. */
    boolean mouseReleased(StarkOsScreen.Area a, int mx, int my) {
        String from = pressed;
        boolean wasDragging = dragging;
        pressed = null;
        dragging = false;
        if (from == null || !wasDragging) {
            return false;
        }
        Row target = rowAt(a, mx, my);
        if (target != null && target.entry().dir() && !target.entry().path().equals(from)
                && !target.entry().path().equals(VirtualFs.parentOf(from))) {
            CompoundTag args = new CompoundTag();
            args.putString("from", from);
            args.putString("to", target.entry().path());
            screen.send("fs_move", args);
            selected = child(target.entry().path(), VirtualFs.nameOf(from));
        }
        return true;
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 2);
    }

    // ================================================================ Toetsen

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (input != Input.NONE && nameBox != null) {
            if (keyCode == 257 || keyCode == 335) { // enter
                submitName();
            } else if (keyCode == 256) { // esc: alleen het naamvak dicht
                cancelInput();
            } else {
                nameBox.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        if (menuFor != null && keyCode == 256) {
            menuFor = null;
            return true;
        }
        Entry selection = selectedEntry();
        switch (keyCode) {
            case 261 -> press(Tool.DELETE); // delete
            case 291 -> press(Tool.RENAME); // F2
            case 257, 335 -> { // enter
                if (selection != null) {
                    open(selection);
                }
            }
            case 259 -> goUp(); // backspace
            case 264, 265 -> moveSelection(keyCode == 265 ? -1 : 1); // pijltjes
            default -> {
                return false;
            }
        }
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (input != Input.NONE && nameBox != null) {
            nameBox.charTyped(codePoint, modifiers);
            return true;
        }
        return false;
    }

    private void moveSelection(int step) {
        List<Row> rows = rows();
        int current = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).up() && rows.get(i).entry().path().equals(selected)) {
                current = i;
            }
        }
        int first = !rows.isEmpty() && rows.getFirst().up() ? 1 : 0;
        if (rows.size() <= first) {
            return;
        }
        int next = current < 0 ? first : Math.max(first, Math.min(rows.size() - 1, current + step));
        selected = rows.get(next).entry().path();
        // In beeld houden.
        if (next < scroll) {
            scroll = next;
        } else if (next >= scroll + visibleRows) {
            scroll = next - visibleRows + 1;
        }
    }

    // ================================================================ Doen

    private void press(Tool tool) {
        Entry selection = selectedEntry();
        if (!enabled(tool, selection)) {
            return;
        }
        switch (tool) {
            case UP -> goUp();
            case FOLDER -> startInput(Input.NEW_FOLDER, "");
            case FILE -> startInput(Input.NEW_FILE, "");
            case RENAME -> startInput(Input.RENAME, selection.name());
            case DELETE -> delete(selection);
            case TRAP -> setTrap(selection, !selection.trap());
        }
    }

    private void pickMenu(MenuItem item, Entry entry) {
        switch (item) {
            case OPEN -> {
                menuFor = null;
                open(entry);
            }
            case RENAME -> {
                menuFor = null;
                startInput(Input.RENAME, entry.name());
            }
            case DELETE -> {
                // De eerste klik vraagt "zeker?" (het menu blijft open), de tweede gooit het weg.
                boolean sure = confirming(entry.path());
                delete(entry);
                if (sure) {
                    menuFor = null;
                }
            }
            case TRAP -> {
                menuFor = null;
                setTrap(entry, !entry.trap());
            }
        }
    }

    /** Een map: erin. Een bestand: openen in Notes (de server stuurt de inhoud). */
    private void open(Entry entry) {
        if (entry.dir()) {
            cwd = entry.path();
            selected = null;
            scroll = 0;
        } else {
            CompoundTag args = new CompoundTag();
            args.putString("path", entry.path());
            screen.send("fs_open", args);
        }
    }

    private void goUp() {
        if (!cwd.equals("/")) {
            selected = cwd;
            cwd = VirtualFs.parentOf(cwd);
            scroll = 0;
        }
    }

    private void delete(Entry entry) {
        if (confirming(entry.path())) {
            CompoundTag args = new CompoundTag();
            args.putString("path", entry.path());
            screen.send("fs_delete", args);
            confirmDelete = null;
            selected = null;
        } else {
            confirmDelete = entry.path();
            confirmUntil = Util.getMillis() + CONFIRM_MS;
        }
    }

    private void setTrap(Entry entry, boolean on) {
        CompoundTag args = new CompoundTag();
        args.putString("path", entry.path());
        args.putBoolean("on", on);
        screen.send("fs_trap", args);
    }

    /** Alleen voor de screenshot-rondleiding: naar een map, een bestand kiezen, en eventueel het menu openen. */
    void tourShow(StarkOsScreen.Area a, String folder, String select, boolean menu) {
        cwd = folder;
        selected = select;
        scroll = 0;
        menuFor = null;
        if (menu) {
            List<Row> rows = rows();
            for (int i = 0; i < rows.size(); i++) {
                if (!rows.get(i).up() && rows.get(i).entry().path().equals(select)) {
                    menuFor = select;
                    menuX = a.x() + 70;
                    menuY = listTop(a) + i * ROW + 6;
                }
            }
        }
    }

    // ---------------------------------------------------------------- Het naamvak

    private void startInput(Input kind, String text) {
        if (nameBox == null) {
            nameBox = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 10, Component.literal("Name"));
            nameBox.setBordered(false);
            nameBox.setMaxLength(VirtualFs.MAX_NAME);
            // Alleen tekens die in een naam mogen (zie VirtualFs.validName).
            nameBox.setFilter(value -> value.chars().allMatch(c -> c < 128 && Character.isLetterOrDigit(c)
                    || c == '.' || c == '-' || c == '_'));
            nameBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
        }
        input = kind;
        renameFrom = kind == Input.RENAME ? selected : null;
        nameBox.setValue(text);
        nameBox.setFocused(true);
        menuFor = null;
    }

    private void cancelInput() {
        input = Input.NONE;
        renameFrom = null;
        if (nameBox != null) {
            nameBox.setFocused(false);
        }
    }

    private void submitName() {
        String name = nameBox == null ? "" : nameBox.getValue().strip();
        if (name.isEmpty()) {
            cancelInput();
            return;
        }
        String dir = input == Input.RENAME && renameFrom != null ? VirtualFs.parentOf(renameFrom) : cwd;
        String target = child(dir, name);
        if (find(target) != null && !target.equals(renameFrom)) {
            screen.showNotice(name + " already exists", true);
            return;
        }
        CompoundTag args = new CompoundTag();
        switch (input) {
            case NEW_FOLDER -> {
                args.putString("path", target);
                screen.send("fs_mkdir", args);
            }
            case NEW_FILE -> {
                args.putString("path", target);
                screen.send("fs_touch", args);
                // En meteen openen in Notes, zodat je kunt beginnen met typen.
                screen.send("fs_open", args);
            }
            case RENAME -> {
                if (renameFrom != null && !target.equals(renameFrom)) {
                    args.putString("from", renameFrom);
                    args.putString("to", target);
                    screen.send("fs_move", args);
                }
            }
            case NONE -> { }
        }
        selected = target;
        cancelInput();
    }
}
