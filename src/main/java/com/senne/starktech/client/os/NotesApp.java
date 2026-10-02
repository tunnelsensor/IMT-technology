package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.security.os.VirtualFs;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Het Notes-venster van de Stark OS-desktop: een simpele teksteditor voor één bestand (jouw wens,
 * 28 september: notities schrijven). Je opent een bestand door erop te dubbelklikken in Files, of
 * maakt een nieuwe notitie met "New".
 *
 * Het tekstvak is het meerregelige vak van Minecraft zelf ({@link MultiLineEditBox}): selecteren,
 * kopiëren en plakken (Ctrl+C/V), Ctrl+A en woordomslag werken dus vanzelf. Alleen de rand is
 * anders, in de kleuren van E.D.I.T.H.
 *
 * Bewaren: de Save-knop of Ctrl+S. Sluit je het venster of de desktop met wijzigingen, dan bewaart
 * hij ze vanzelf. security.conf (een systeembestand) kun je alleen lezen.
 */
final class NotesApp {
    /** Zo hoog is de knoppenbalk bovenaan (pixels). */
    private static final int TOOLBAR = OsDraw.BUTTON_H + 4;

    /** Het tekstvak: het vak van Minecraft, met een rand in de E.D.I.T.H.-kleuren. */
    private static final class Box extends MultiLineEditBox {
        Box(Font font, int width, int height) {
            super(font, 0, 0, width, height, Component.literal("Type your note here..."), Component.literal("Note"));
            setCharacterLimit(VirtualFs.MAX_FILE_LENGTH);
        }

        @Override
        protected void renderBorder(GuiGraphics g, int x, int y, int width, int height) {
            OsDraw.field(g, x, y, x + width, y + height, isFocused());
        }
    }

    private final StarkOsScreen screen;
    /** Het bestand dat open is (null = niets open). */
    private @Nullable String path;
    /** Een systeembestand: alleen lezen. */
    private boolean readOnly;
    /** De tekst zoals hij het laatst bewaard (of geopend) is: zo zie je of er iets veranderde. */
    private String saved = "";
    private @Nullable Box box;

    NotesApp(StarkOsScreen screen) {
        this.screen = screen;
    }

    /** Het tekstvak, gemaakt bij het eerste gebruik (het venster heeft altijd dezelfde grootte). */
    private Box box(Font font, StarkOsScreen.Area a) {
        if (box == null) {
            // Rechts 9 pixels voor de schuifbalk, onderaan een regel voor de teller (123/4096).
            box = new Box(font, a.w() - 9, a.h() - TOOLBAR - 13);
        }
        return box;
    }

    /** De server stuurde een bestand om te openen. */
    void load(Font font, StarkOsScreen.Area a, String path, String content, boolean system) {
        this.path = path;
        this.readOnly = system;
        this.saved = content;
        Box editor = box(font, a);
        editor.setValue(content);
        editor.keyPressed(266, 0, 0); // "Page Up": de cursor (en het beeld) naar het begin
        editor.setFocused(!system);
    }

    /** Zijn er wijzigingen die nog niet bewaard zijn? */
    boolean modified() {
        return path != null && box != null && !readOnly && !box.getValue().equals(saved);
    }

    /** Bewaren (de Save-knop, Ctrl+S, of vanzelf bij sluiten). */
    void save() {
        if (!modified()) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putString("path", path);
        args.putString("text", box.getValue());
        screen.send("fs_save", args);
        saved = box.getValue();
    }

    /** Het tekstvak verliest de focus (een ander venster is aangeklikt). */
    void blur() {
        if (box != null) {
            box.setFocused(false);
        }
    }

    /** Een nieuwe notitie (note1.txt, note2.txt, ...) in de map die Files nu toont, en meteen openen. */
    private void newNote() {
        FilesApp files = screen.files();
        String dir = files.cwd();
        int n = 1;
        while (files.find(FilesApp.child(dir, "note" + n + ".txt")) != null) {
            n++;
        }
        save();
        CompoundTag args = new CompoundTag();
        args.putString("path", FilesApp.child(dir, "note" + n + ".txt"));
        screen.send("fs_touch", args);
        screen.send("fs_open", args);
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my, float partialTick) {
        int bx = a.x();
        bx += OsDraw.button(g, font, bx, a.y(), "Save", modified(), HudStyle.CYAN) + 3;
        bx += OsDraw.button(g, font, bx, a.y(), "New", true, HudStyle.CYAN) + 6;
        if (path == null) {
            g.drawString(font, "No file open.", a.x(), a.y() + TOOLBAR + 4, HudStyle.argb(255, HudStyle.WHITE), false);
            g.drawString(font, "Double-click a file in Files,", a.x(), a.y() + TOOLBAR + 18, HudStyle.argb(255, OsDraw.GREY), false);
            g.drawString(font, "or press New for a new note.", a.x(), a.y() + TOOLBAR + 28, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        // De naam van het bestand, met een sterretje als er iets veranderde.
        String title = readOnly ? path + "  (read-only)" : path + (modified() ? " *" : "");
        g.drawString(font, OsDraw.fitLeft(font, title, a.x() + a.w() - bx), bx, a.y() + 2,
                HudStyle.argb(255, readOnly ? HudStyle.ORANGE : HudStyle.CYAN), false);
        Box editor = box(font, a);
        editor.setX(a.x());
        editor.setY(a.y() + TOOLBAR);
        editor.render(g, mx, my, partialTick);
        // Is het bestand intussen weg (verplaatst of verwijderd)? Bewaren maakt het dan opnieuw.
        if (screen.files().find(path) == null) {
            g.drawString(font, "File moved or deleted", a.x(), editor.getY() + editor.getHeight() + 4,
                    HudStyle.argb(255, HudStyle.ORANGE), false);
        }
    }

    // ================================================================ Muis en toetsen

    boolean mouseClicked(Font font, StarkOsScreen.Area a, int mx, int my, int button) {
        int saveW = OsDraw.buttonWidth(font, "Save");
        if (OsDraw.inside(mx, my, a.x(), a.y(), saveW, OsDraw.BUTTON_H)) {
            save();
            return true;
        }
        if (OsDraw.inside(mx, my, a.x() + saveW + 3, a.y(), OsDraw.buttonWidth(font, "New"), OsDraw.BUTTON_H)) {
            newNote();
            return true;
        }
        if (path == null || box == null) {
            return false;
        }
        // In het tekstvak (of op zijn schuifbalk, die er rechts naast staat).
        boolean inBox = mx >= box.getX() && mx < box.getX() + box.getWidth() + 8
                && my >= box.getY() && my < box.getY() + box.getHeight();
        box.setFocused(inBox);
        return inBox && box.mouseClicked(mx, my, button);
    }

    boolean mouseDragged(int mx, int my, int button, double dragX, double dragY) {
        return box != null && path != null && box.mouseDragged(mx, my, button, dragX, dragY);
    }

    void mouseReleased(int mx, int my, int button) {
        if (box != null) {
            box.mouseReleased(mx, my, button);
        }
    }

    void mouseScrolled(int mx, int my, double scrollY) {
        if (box != null && path != null) {
            box.mouseScrolled(mx, my, 0, scrollY);
        }
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown() && keyCode == 83) { // Ctrl+S
            save();
            return true;
        }
        if (box == null || path == null || !box.isFocused()) {
            return false;
        }
        if (keyCode == 256) { // Esc: eerst alleen uit het tekstvak (nog een keer Esc sluit de desktop)
            box.setFocused(false);
            return true;
        }
        // Alleen lezen: wel rondlopen, selecteren en kopiëren, niets veranderen.
        boolean navigation = keyCode >= 262 && keyCode <= 269 || Screen.isSelectAll(keyCode) || Screen.isCopy(keyCode);
        if (!readOnly || navigation) {
            box.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (box == null || path == null || readOnly || !box.isFocused()) {
            return false;
        }
        box.charTyped(codePoint, modifiers);
        return true;
    }
}
