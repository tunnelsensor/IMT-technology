package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.logic.program.Op;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Een kleine code-editor voor de app Logic (jouw wens, 7 oktober: "regels gaat vlotter maar is
 * moeilijker te leren"). Om het makkelijker te maken:
 * <ul>
 *   <li><b>kleuren</b> per soort woord (zoals in VS Code): when/if/else roze, triggers cyaan, acties
 *       lichtblauw, gegevens (card.level) geel, tekst groen, getallen oranje, commentaar grijs;</li>
 *   <li><b>regelnummers</b> links, en de regel met een fout in het rood;</li>
 *   <li><b>inspringen</b>: Enter houdt de inspringing van de regel, en springt na een {@code :}
 *       vanzelf 4 spaties verder; Tab = 4 spaties (met een selectie: alle regels verder),
 *       Shift+Tab terug; Backspace aan het begin van een inspringing haalt 4 spaties weg;</li>
 *   <li>selecteren met de muis of Shift+pijltjes, Ctrl+A/C/X/V, Ctrl+Z (ongedaan maken) en
 *       Ctrl+Y (opnieuw).</li>
 * </ul>
 * Regels worden niet afgebroken: is een regel te lang, dan schuift het beeld opzij mee.
 */
final class CodeEditor {
    static final int LINE_H = 10;
    /** Zoveel tekens mag de code hooguit hebben. */
    static final int MAX_LENGTH = 8000;
    private static final int UNDO_LIMIT = 100;

    private static final int BACKGROUND = 0x0A141B;
    private static final int GUTTER = 0x0E1A22;
    private static final int SELECTION = 0x24506A;
    private static final int ERROR_LINE = 0x4A1A1E;

    private static final Set<String> KEYWORDS = Set.of("when", "if", "elif", "else", "pass", "and", "or", "not");
    private static final Set<String> CONSTANTS = Set.of("true", "false", "True", "False");
    private static final Set<String> VARIABLES = Set.of("card.level", "card.number", "card.valid", "card.name", "code",
            "player.name", "player.trusted", "player.owner", "is_night", "is_day", "is_on");

    private final Font font;
    private List<String> lines = new ArrayList<>(List.of(""));
    private int cursorLine;
    private int cursorCol;
    /** Waar de selectie begon (of -1 als er niets geselecteerd is). */
    private int anchorLine = -1;
    private int anchorCol;
    private int scrollLine;
    private int scrollX;
    private boolean focused;
    private boolean readOnly;
    private int errorLine;
    private boolean dragging;

    private int x;
    private int y;
    private int w;
    private int h;

    /** Ongedaan maken: hoe de tekst eerder was (en waar de cursor stond). */
    private record Snapshot(String text, int line, int col) {}

    private final ArrayDeque<Snapshot> undo = new ArrayDeque<>();
    private final ArrayDeque<Snapshot> redo = new ArrayDeque<>();
    private long lastEdit;

    CodeEditor(Font font) {
        this.font = font;
    }

    // ================================================================ Tekst

    String text() {
        return String.join("\n", lines);
    }

    /** Nieuwe tekst (een ander diagram): de cursor naar het begin, en niets om ongedaan te maken. */
    void setText(String text) {
        lines = new ArrayList<>(List.of(text.replace("\r", "").replace("\t", "    ").split("\n", -1)));
        cursorLine = 0;
        cursorCol = 0;
        anchorLine = -1;
        scrollLine = 0;
        scrollX = 0;
        undo.clear();
        redo.clear();
    }

    void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    /** Welke regel een fout heeft (vanaf 1), of 0. */
    void setErrorLine(int line) {
        this.errorLine = line;
    }

    void setFocused(boolean focused) {
        this.focused = focused;
    }

    boolean focused() {
        return focused;
    }

    void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /** Springt naar deze regel (vanaf 1), bijvoorbeeld de regel met een fout. */
    void goToLine(int line) {
        cursorLine = Math.max(0, Math.min(lines.size() - 1, line - 1));
        cursorCol = indentOf(lines.get(cursorLine));
        anchorLine = -1;
        keepCursorVisible();
    }

    /**
     * Voegt tekst in op de cursor (in plaats van wat geselecteerd is). Meerdere regels krijgen de
     * inspringing van de regel waar de cursor staat.
     */
    void insert(String text) {
        if (readOnly) {
            return;
        }
        remember(true);
        deleteSelection();
        String indent = " ".repeat(indentOf(lines.get(cursorLine)));
        String[] parts = text.replace("\r", "").replace("\t", "    ").split("\n", -1);
        StringBuilder joined = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            joined.append('\n').append(indent).append(parts[i]);
        }
        insertRaw(joined.toString());
    }

    // ================================================================ Tekenen

    private int gutterWidth() {
        return font.width(String.valueOf(Math.max(99, lines.size()))) + 8;
    }

    private int visibleLines() {
        return Math.max(1, (h - 4) / LINE_H);
    }

    void render(GuiGraphics g, int mx, int my) {
        g.fill(x, y, x + w, y + h, HudStyle.argb(255, BACKGROUND));
        int gutter = gutterWidth();
        g.fill(x, y, x + gutter, y + h, HudStyle.argb(255, GUTTER));
        HudStyle.brackets(g, x, y, x + w, y + h, 4, HudStyle.argb(255, focused ? HudStyle.CYAN : OsDraw.DIM));
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        int textX = x + gutter + 4 - scrollX;
        int last = Math.min(lines.size(), scrollLine + visibleLines() + 1);
        for (int i = scrollLine; i < last; i++) {
            int ly = y + 2 + (i - scrollLine) * LINE_H;
            if (i + 1 == errorLine) {
                g.fill(x + gutter, ly - 1, x + w - 1, ly + LINE_H - 1, HudStyle.argb(255, ERROR_LINE));
            }
            renderSelection(g, i, textX, ly);
            String number = String.valueOf(i + 1);
            g.drawString(font, number, x + gutter - 4 - font.width(number), ly,
                    HudStyle.argb(255, i + 1 == errorLine ? HudStyle.RED : 0x46606C), false);
            renderLine(g, lines.get(i), textX, ly, gutter);
        }
        // De cursor (knippert).
        if (focused && !readOnly && (Util.getMillis() / 500) % 2 == 0
                && cursorLine >= scrollLine && cursorLine < scrollLine + visibleLines()) {
            int cx = textX + font.width(lines.get(cursorLine).substring(0, cursorCol));
            int cy = y + 2 + (cursorLine - scrollLine) * LINE_H;
            if (cx >= x + gutter) {
                g.fill(cx, cy - 1, cx + 1, cy + LINE_H - 1, HudStyle.argb(255, HudStyle.WHITE));
            }
        }
        g.disableScissor();
        // Een schuifbalkje rechts als niet alles past.
        if (lines.size() > visibleLines()) {
            int track = h - 4;
            int thumb = Math.max(8, track * visibleLines() / lines.size());
            int top = y + 2 + (track - thumb) * scrollLine / Math.max(1, lines.size() - visibleLines());
            g.fill(x + w - 3, top, x + w - 1, top + thumb, HudStyle.argb(160, HudStyle.CYAN));
        }
    }

    private void renderSelection(GuiGraphics g, int line, int textX, int ly) {
        if (anchorLine < 0) {
            return;
        }
        int[] range = selection();
        if (line < range[0] || line > range[2]) {
            return;
        }
        String text = lines.get(line);
        int from = line == range[0] ? range[1] : 0;
        int to = line == range[2] ? range[3] : text.length();
        int x0 = textX + font.width(text.substring(0, from));
        int x1 = textX + font.width(text.substring(0, to)) + (line < range[2] ? 3 : 0);
        if (x1 > x0) {
            g.fill(x0, ly - 1, x1, ly + LINE_H - 1, HudStyle.argb(255, SELECTION));
        }
    }

    /** Eén regel met kleuren per stukje (zie {@link #colours}). */
    private void renderLine(GuiGraphics g, String line, int textX, int ly, int gutter) {
        int[] colours = colours(line);
        int start = 0;
        int px = textX;
        while (start < line.length()) {
            int end = start;
            while (end < line.length() && colours[end] == colours[start]) {
                end++;
            }
            String part = line.substring(start, end);
            int width = font.width(part);
            if (px + width >= x + gutter && px <= x + w) {
                g.drawString(font, part, px, ly, HudStyle.argb(255, colours[start]), false);
            }
            px += width;
            start = end;
        }
    }

    /** De kleur van elk teken van een regel. */
    static int[] colours(String line) {
        int[] colours = new int[line.length()];
        java.util.Arrays.fill(colours, 0xE8EEF2);
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '#') {
                java.util.Arrays.fill(colours, i, line.length(), 0x6E8A96);
                break;
            }
            if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < line.length() && line.charAt(end) != c) {
                    end += line.charAt(end) == '\\' ? 2 : 1;
                }
                end = Math.min(line.length(), end + 1);
                java.util.Arrays.fill(colours, i, end, 0x6BE88B);
                i = end;
            } else if (Character.isDigit(c)) {
                int end = i;
                while (end < line.length() && (Character.isDigit(line.charAt(end)) || line.charAt(end) == '.')) {
                    end++;
                }
                java.util.Arrays.fill(colours, i, end, 0xFFB86C);
                i = end;
            } else if (Character.isLetter(c) || c == '_') {
                int end = i;
                while (end < line.length() && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '_'
                        || line.charAt(end) == '.')) {
                    end++;
                }
                String word = line.substring(i, end);
                Op op = Op.byCode(word);
                int colour = KEYWORDS.contains(word) ? 0xFF79C6
                        : CONSTANTS.contains(word) ? 0xC09CFF
                        : VARIABLES.contains(word) ? 0xF1E87C
                        : op != null && op.isTrigger() ? HudStyle.CYAN
                        : op != null ? 0x9ED8FF : 0xE8EEF2;
                java.util.Arrays.fill(colours, i, end, colour);
                i = end;
            } else {
                if ("=<>!".indexOf(c) >= 0) {
                    colours[i] = 0xFF79C6;
                }
                i++;
            }
        }
        return colours;
    }

    // ================================================================ Muis

    boolean mouseClicked(int mx, int my, int button) {
        boolean inside = mx >= x && mx < x + w && my >= y && my < y + h;
        focused = inside;
        if (!inside || button != 0) {
            return inside;
        }
        int[] at = positionAt(mx, my);
        if (Screen.hasShiftDown()) {
            if (anchorLine < 0) {
                anchorLine = cursorLine;
                anchorCol = cursorCol;
            }
        } else {
            anchorLine = at[0];
            anchorCol = at[1];
        }
        cursorLine = at[0];
        cursorCol = at[1];
        dragging = true;
        return true;
    }

    boolean mouseDragged(int mx, int my) {
        if (!dragging) {
            return false;
        }
        int[] at = positionAt(mx, my);
        cursorLine = at[0];
        cursorCol = at[1];
        keepCursorVisible();
        return true;
    }

    void mouseReleased() {
        dragging = false;
        if (anchorLine == cursorLine && anchorCol == cursorCol) {
            anchorLine = -1; // gewoon geklikt, niets geselecteerd
        }
    }

    void mouseScrolled(double scrollY) {
        scrollLine = Math.max(0, Math.min(Math.max(0, lines.size() - visibleLines()), scrollLine - (int) Math.signum(scrollY) * 3));
    }

    /** Welke regel en welk teken onder de muis liggen. */
    private int[] positionAt(int mx, int my) {
        int line = Math.max(0, Math.min(lines.size() - 1, scrollLine + (my - y - 2) / LINE_H));
        String text = lines.get(line);
        int textX = x + gutterWidth() + 4 - scrollX;
        int col = 0;
        while (col < text.length()) {
            int mid = textX + font.width(text.substring(0, col)) + font.width(text.substring(col, col + 1)) / 2;
            if (mx < mid) {
                break;
            }
            col++;
        }
        return new int[] {line, col};
    }

    // ================================================================ Toetsen

    boolean keyPressed(int key, int scan, int modifiers) {
        if (!focused) {
            return false;
        }
        boolean shift = Screen.hasShiftDown();
        boolean control = Screen.hasControlDown();
        if (Screen.isSelectAll(key)) {
            anchorLine = 0;
            anchorCol = 0;
            cursorLine = lines.size() - 1;
            cursorCol = lines.get(cursorLine).length();
            return true;
        }
        if (Screen.isCopy(key)) {
            String selected = selectedText();
            if (!selected.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(selected);
            }
            return true;
        }
        switch (key) {
            case 263, 262, 265, 264, 268, 269, 266, 267 -> {
                move(key, shift, control);
                return true;
            }
            case 256 -> {
                focused = false; // Esc: alleen uit de editor (nog een keer Esc sluit de desktop)
                return true;
            }
            default -> {
            }
        }
        if (readOnly) {
            return true;
        }
        if (Screen.isCut(key)) {
            String selected = selectedText();
            if (!selected.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(selected);
                remember(true);
                deleteSelection();
            }
            return true;
        }
        if (Screen.isPaste(key)) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        if (control && key == 90) { // Ctrl+Z
            step(undo, redo);
            return true;
        }
        if (control && key == 89) { // Ctrl+Y
            step(redo, undo);
            return true;
        }
        switch (key) {
            case 259 -> backspace();
            case 261 -> delete();
            case 257, 335 -> enter();
            case 258 -> tab(shift);
            default -> {
                return true;
            }
        }
        keepCursorVisible();
        return true;
    }

    boolean charTyped(char c, int modifiers) {
        if (!focused || readOnly || c < ' ' || c == 127) {
            return focused;
        }
        if (text().length() >= MAX_LENGTH) {
            return true;
        }
        boolean typing = Util.getMillis() - lastEdit < 800 && anchorLine < 0;
        remember(!typing);
        deleteSelection();
        insertRaw(String.valueOf(c));
        keepCursorVisible();
        return true;
    }

    private void move(int key, boolean shift, boolean control) {
        if (shift && anchorLine < 0) {
            anchorLine = cursorLine;
            anchorCol = cursorCol;
        } else if (!shift && anchorLine >= 0 && (key == 263 || key == 262)) {
            // Pijl zonder Shift met een selectie: naar dat uiteinde, selectie weg.
            int[] range = selection();
            cursorLine = key == 263 ? range[0] : range[2];
            cursorCol = key == 263 ? range[1] : range[3];
            anchorLine = -1;
            keepCursorVisible();
            return;
        }
        if (!shift) {
            anchorLine = -1;
        }
        String line = lines.get(cursorLine);
        switch (key) {
            case 263 -> { // links
                if (cursorCol > 0) {
                    cursorCol = control ? wordStart(line, cursorCol) : cursorCol - 1;
                } else if (cursorLine > 0) {
                    cursorLine--;
                    cursorCol = lines.get(cursorLine).length();
                }
            }
            case 262 -> { // rechts
                if (cursorCol < line.length()) {
                    cursorCol = control ? wordEnd(line, cursorCol) : cursorCol + 1;
                } else if (cursorLine < lines.size() - 1) {
                    cursorLine++;
                    cursorCol = 0;
                }
            }
            case 265 -> moveVertical(-1);
            case 264 -> moveVertical(1);
            case 266 -> moveVertical(-visibleLines());
            case 267 -> moveVertical(visibleLines());
            case 268 -> { // Home: eerst naar het begin van de tekst, dan naar het echte begin
                int indent = indentOf(line);
                cursorCol = cursorCol == indent ? 0 : indent;
            }
            case 269 -> cursorCol = line.length();
            default -> {
            }
        }
        keepCursorVisible();
    }

    private void moveVertical(int delta) {
        String from = lines.get(cursorLine);
        int pixel = font.width(from.substring(0, cursorCol));
        cursorLine = Math.max(0, Math.min(lines.size() - 1, cursorLine + delta));
        String to = lines.get(cursorLine);
        int col = 0;
        while (col < to.length() && font.width(to.substring(0, col + 1)) <= pixel + 1) {
            col++;
        }
        cursorCol = col;
    }

    private static int wordStart(String line, int col) {
        int i = col - 1;
        while (i > 0 && line.charAt(i) == ' ') {
            i--;
        }
        while (i > 0 && Character.isLetterOrDigit(line.charAt(i - 1))) {
            i--;
        }
        return Math.max(0, i);
    }

    private static int wordEnd(String line, int col) {
        int i = col;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        while (i < line.length() && Character.isLetterOrDigit(line.charAt(i))) {
            i++;
        }
        return Math.max(col + 1, i);
    }

    private void backspace() {
        remember(true);
        if (deleteSelection()) {
            return;
        }
        String line = lines.get(cursorLine);
        if (cursorCol > 0) {
            // Aan het eind van een inspringing: een hele stap (4 spaties) terug.
            int remove = 1;
            if (line.substring(0, cursorCol).isBlank() && cursorCol % 4 == 0) {
                remove = 4;
            }
            lines.set(cursorLine, line.substring(0, cursorCol - remove) + line.substring(cursorCol));
            cursorCol -= remove;
        } else if (cursorLine > 0) {
            String previous = lines.get(cursorLine - 1);
            lines.set(cursorLine - 1, previous + line);
            lines.remove(cursorLine);
            cursorLine--;
            cursorCol = previous.length();
        }
    }

    private void delete() {
        remember(true);
        if (deleteSelection()) {
            return;
        }
        String line = lines.get(cursorLine);
        if (cursorCol < line.length()) {
            lines.set(cursorLine, line.substring(0, cursorCol) + line.substring(cursorCol + 1));
        } else if (cursorLine < lines.size() - 1) {
            lines.set(cursorLine, line + lines.get(cursorLine + 1));
            lines.remove(cursorLine + 1);
        }
    }

    /** Enter: een nieuwe regel met dezelfde inspringing, en 4 verder na een regel die op : eindigt. */
    private void enter() {
        remember(true);
        deleteSelection();
        String line = lines.get(cursorLine);
        String before = line.substring(0, cursorCol);
        // Staat de cursor nog in de inspringing, dan schuift de regel gewoon een regel omlaag.
        int indent = cursorCol <= indentOf(line) ? cursorCol : indentOf(line);
        String code = before.contains("#") ? before.substring(0, before.indexOf('#')) : before;
        if (code.stripTrailing().endsWith(":")) {
            indent += 4;
        }
        insertRaw("\n" + " ".repeat(indent));
    }

    /** Tab: 4 spaties (of alle geselecteerde regels 4 verder); Shift+Tab: 4 terug. */
    private void tab(boolean back) {
        remember(true);
        int from = anchorLine >= 0 ? Math.min(anchorLine, cursorLine) : cursorLine;
        int to = anchorLine >= 0 ? Math.max(anchorLine, cursorLine) : cursorLine;
        if (back || anchorLine >= 0 && from != to) {
            for (int i = from; i <= to; i++) {
                String line = lines.get(i);
                if (back) {
                    int remove = Math.min(4, indentOf(line));
                    lines.set(i, line.substring(remove));
                    if (i == cursorLine) {
                        cursorCol = Math.max(0, cursorCol - remove);
                    }
                    if (i == anchorLine) {
                        anchorCol = Math.max(0, anchorCol - remove);
                    }
                } else {
                    lines.set(i, "    " + line);
                    if (i == cursorLine) {
                        cursorCol += 4;
                    }
                    if (i == anchorLine) {
                        anchorCol += 4;
                    }
                }
            }
            return;
        }
        deleteSelection();
        insertRaw(" ".repeat(4 - cursorCol % 4));
    }

    // ================================================================ Hulpjes

    private static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    /** De selectie als {beginregel, beginteken, eindregel, eindteken}. */
    private int[] selection() {
        boolean anchorFirst = anchorLine < cursorLine || anchorLine == cursorLine && anchorCol <= cursorCol;
        return anchorFirst ? new int[] {anchorLine, anchorCol, cursorLine, cursorCol}
                : new int[] {cursorLine, cursorCol, anchorLine, anchorCol};
    }

    private String selectedText() {
        if (anchorLine < 0) {
            return "";
        }
        int[] r = selection();
        if (r[0] == r[2]) {
            return lines.get(r[0]).substring(r[1], r[3]);
        }
        StringBuilder text = new StringBuilder(lines.get(r[0]).substring(r[1]));
        for (int i = r[0] + 1; i < r[2]; i++) {
            text.append('\n').append(lines.get(i));
        }
        return text.append('\n').append(lines.get(r[2]), 0, r[3]).toString();
    }

    /** Haalt de selectie weg (als die er is). */
    private boolean deleteSelection() {
        if (anchorLine < 0) {
            return false;
        }
        int[] r = selection();
        anchorLine = -1;
        if (r[0] == r[2] && r[1] == r[3]) {
            return false;
        }
        String head = lines.get(r[0]).substring(0, r[1]);
        String tail = lines.get(r[2]).substring(r[3]);
        for (int i = r[2]; i > r[0]; i--) {
            lines.remove(i);
        }
        lines.set(r[0], head + tail);
        cursorLine = r[0];
        cursorCol = r[1];
        return true;
    }

    /** Tekst invoegen op de cursor (mag regeleinden bevatten). */
    private void insertRaw(String text) {
        int room = MAX_LENGTH - text().length();
        if (room <= 0) {
            return;
        }
        if (text.length() > room) {
            text = text.substring(0, room);
        }
        String line = lines.get(cursorLine);
        String tail = line.substring(cursorCol);
        String[] parts = text.split("\n", -1);
        lines.set(cursorLine, line.substring(0, cursorCol) + parts[0]);
        for (int i = 1; i < parts.length; i++) {
            lines.add(cursorLine + i, parts[i]);
        }
        cursorLine += parts.length - 1;
        cursorCol = lines.get(cursorLine).length();
        lines.set(cursorLine, lines.get(cursorLine) + tail);
        lastEdit = Util.getMillis();
    }

    /** Onthoudt hoe het nu is, voor Ctrl+Z ({@code force} = ook als je net aan het typen was). */
    private void remember(boolean force) {
        String now = text();
        if (!force && !undo.isEmpty()) {
            return; // midden in een woord: één stap ongedaan maken haalt het hele woord weg
        }
        if (undo.isEmpty() || !undo.peekLast().text().equals(now)) {
            undo.addLast(new Snapshot(now, cursorLine, cursorCol));
            if (undo.size() > UNDO_LIMIT) {
                undo.removeFirst();
            }
        }
        redo.clear();
    }

    private void step(ArrayDeque<Snapshot> from, ArrayDeque<Snapshot> to) {
        if (from.isEmpty()) {
            return;
        }
        to.addLast(new Snapshot(text(), cursorLine, cursorCol));
        Snapshot snapshot = from.removeLast();
        lines = new ArrayList<>(List.of(snapshot.text().split("\n", -1)));
        cursorLine = Math.min(snapshot.line(), lines.size() - 1);
        cursorCol = Math.min(snapshot.col(), lines.get(cursorLine).length());
        anchorLine = -1;
        keepCursorVisible();
    }

    /** Schuift het beeld zodat de cursor te zien is. */
    private void keepCursorVisible() {
        if (cursorLine < scrollLine) {
            scrollLine = cursorLine;
        } else if (cursorLine >= scrollLine + visibleLines()) {
            scrollLine = cursorLine - visibleLines() + 1;
        }
        int textWidth = w - gutterWidth() - 10;
        int cx = font.width(lines.get(cursorLine).substring(0, cursorCol));
        if (cx < scrollX) {
            scrollX = Math.max(0, cx - 20);
        } else if (cx > scrollX + textWidth) {
            scrollX = cx - textWidth + 20;
        }
    }
}
