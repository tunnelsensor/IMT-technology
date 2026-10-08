package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.network.OsActionPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * De Stark OS-desktop (jouw wens, 28 september): een echte desktop met icoontjes, sleepbare
 * vensters en een taakbalk, op de beveiligingskern. Het spel loopt door terwijl hij openstaat.
 *
 * Alles wat je ziet komt van de server (een momentopname in een CompoundTag, zie {@link #update});
 * dit scherm tekent het alleen. Wat je doet (een wachtwoord zetten, een map maken) gaat als een
 * opdracht terug naar de server, die altijd zelf controleert of het mag.
 *
 * Dit bestand is de vensterbeheerder: de achtergrond, de icoontjes links, de taakbalk onderaan, en
 * het slepen, focussen en sluiten van vensters. Elke app ({@link App}) tekent zichzelf in zijn
 * venster en handelt zijn eigen kliks af. De grotere apps hebben een eigen bestand
 * ({@link FilesApp}, {@link NotesApp}); de muis en het toetsenbord gaan naar het venster bovenop.
 */
public class StarkOsScreen extends Screen {
    /** De apps op de desktop. {@code glyph} is de letter op het icoontje. */
    public enum App {
        STATUS("Status", "i", 240, 120),
        CAMERAS("Cameras", "C", 336, 196),
        DEVICES("Devices", "D", 330, 196),
        ACCESS("Access", "A", 280, 190),
        KEYCARDS("Keycards", "K", 300, 190),
        /** De diagrammen (redstone programmeren); dit venster vult het hele scherm. */
        LOGIC("Logic", "{}", 420, 240),
        FILES("Files", "F", 300, 190),
        NOTES("Notes", "N", 320, 196),
        LOGS("Logs", "L", 330, 200),
        SETTINGS("Settings", "S", 270, 160);

        final String label;
        final String glyph;
        final int w;
        final int h;

        App(String label, String glyph, int w, int h) {
            this.label = label;
            this.glyph = glyph;
            this.w = w;
            this.h = h;
        }
    }

    /** Eén open venster: welke app, waar het staat, hoe groot, en hoe ver het is doorgescrold. */
    private static final class Win {
        final App app;
        int x;
        int y;
        final int w;
        final int h;
        int scroll;

        Win(App app, int x, int y, int w, int h) {
            this.app = app;
            this.w = w;
            this.h = h;
            this.x = x;
            this.y = y;
        }
    }

    /** Het vak binnen een venster waar een app in tekent (onder de titelbalk). */
    record Area(int x, int y, int w, int h) {}

    private static final int TITLE_H = 13;
    /**
     * Elk venster ligt een laagje (diepte) hoger dan het venster eronder. Tekst met een schaduw
     * ligt in Minecraft net iets naar voren; zonder die lagen zou je hem door een venster erboven
     * heen zien.
     */
    private static final int LAYER = 10;
    private static final int TASKBAR_H = 16;
    private static final int ICON = 30;
    private static final int LINE = 10;
    private static final int BG = 0x0A1016;
    private static final int PANEL = 0x12202B;
    private static final int BAR = 0x0E1A24;

    private final BlockPos core;
    private final boolean remote;
    private CompoundTag data = new CompoundTag();

    private final List<Win> windows = new ArrayList<>();
    private @Nullable Win dragging;
    private int dragDx;
    private int dragDy;
    /** Het venster waar de muisknop in werd ingedrukt (voor slepen binnen een app). */
    private @Nullable Win pressedIn;

    /** De tekst die je in het wachtwoordvak typt (alleen Settings gebruikt dit nu). */
    private boolean editingPassword;
    private String passwordText = "";

    /**
     * De apps met een eigen bestand. Ze bestaan altijd (ook als hun venster dicht is), zodat ze de
     * gegevens van de server bijhouden.
     */
    private final FilesApp files = new FilesApp(this);
    private final NotesApp notes = new NotesApp(this);
    private final DevicesApp devices = new DevicesApp(this);
    private final CamerasApp cameras = new CamerasApp(this);
    private final AccessApp access = new AccessApp(this);
    private final KeycardsApp keycards = new KeycardsApp(this);
    private final LogicApp logic = new LogicApp(this, net.minecraft.client.Minecraft.getInstance().font);
    /** Kwam de eerste momentopname al binnen? (Dan weten we of er al een wachtwoord is.) */
    private boolean gotFirstSnapshot;

    /** Een melding van de server (rechts in de taakbalk) en tot wanneer hij blijft staan. */
    private String notice = "";
    private boolean noticeError;
    private long noticeUntil;

    public StarkOsScreen(BlockPos core, boolean remote) {
        super(Component.literal("Stark OS"));
        this.core = core;
        this.remote = remote;
    }

    public BlockPos core() {
        return core;
    }

    /**
     * Een nieuwe momentopname van de server. De mappenlijst zit er alleen in als hij veranderde, een
     * geopend bestand en een melding maar één keer: die geven we meteen door.
     */
    public void update(CompoundTag snapshot) {
        this.data = snapshot;
        // Jouw eigen kern, nog zonder wachtwoord: meteen Settings open, klaar om er een te typen.
        if (!gotFirstSnapshot) {
            gotFirstSnapshot = true;
            if (snapshot.getBoolean("isOwner") && !snapshot.getBoolean("hasPassword")) {
                openApp(App.SETTINGS);
                editingPassword = true;
            }
        }
        if (snapshot.contains("fs")) {
            files.update(snapshot.getList("fs", Tag.TAG_COMPOUND));
        }
        if (snapshot.contains("logic")) {
            logic.update(snapshot.getCompound("logic"));
        }
        if (snapshot.contains("file")) {
            CompoundTag file = snapshot.getCompound("file");
            Win win = openApp(App.NOTES);
            notes.load(font != null ? font : net.minecraft.client.Minecraft.getInstance().font, contentArea(win),
                    file.getString("p"), file.getString("c"), file.getBoolean("s"));
        }
        if (snapshot.contains("notice")) {
            CompoundTag message = snapshot.getCompound("notice");
            showNotice(message.getString("x"), message.getBoolean("err"));
        }
    }

    /** Een melding rechts in de taakbalk, een paar seconden lang. */
    void showNotice(String text, boolean error) {
        notice = text;
        noticeError = error;
        noticeUntil = Util.getMillis() + 4000;
    }

    /** Stuurt een opdracht naar de server (die controleert altijd zelf of het mag). */
    void send(String op, CompoundTag args) {
        PacketDistributor.sendToServer(new OsActionPayload(core, op, args));
    }

    /** Ben jij de eigenaar van dit systeem? (Alleen die ziet en zet valstrikken.) */
    boolean isOwner() {
        return data.getBoolean("isOwner");
    }

    FilesApp files() {
        return files;
    }

    /** De laatste momentopname van de server (de apps lezen hier hun gegevens uit). */
    CompoundTag data() {
        return data;
    }

    /**
     * Alleen voor de screenshot-rondleiding (VisualCheck): het Files-venster openen in deze map, met
     * dit bestand gekozen, en eventueel het rechtsklik-menu erbij.
     */
    public void tourFiles(String folder, String select, boolean menu) {
        Win win = openApp(App.FILES);
        files.tourShow(contentArea(win), folder, select, menu);
    }

    /** Alleen voor de screenshot-rondleiding: de app Keycards, met een naam en niveau al ingevuld. */
    public void tourKeycards(String label, int level) {
        openApp(App.KEYCARDS);
        keycards.tourFill(label, level);
    }

    /** Alleen voor de screenshot-rondleiding: de app Logic met deze code (en eventueel Help open). */
    public void tourLogic(String name, String code, boolean help) {
        openApp(App.LOGIC);
        logic.tourCode(name, code, help);
    }

    /** Alleen voor de screenshot-rondleiding: de app Logic in de blokjes, met eventueel een blokje gekozen. */
    public void tourLogicBlocks(@Nullable com.senne.starktech.logic.program.Op select) {
        logic.tourBlocks(select);
    }

    /** Alleen voor de screenshot-rondleiding: Ctrl+Spatie in de code van de app Logic. */
    public void tourLogicComplete(String lineText, String after) {
        logic.tourComplete(lineText, after);
    }

    /** Alleen voor de screenshot-rondleiding: de knop Save van de app Logic. */
    public void tourSaveLogic() {
        logic.tourSave();
    }

    /** Alleen voor de screenshot-rondleiding: de vraag "Sure?" bij Delete blocked in de app Keycards. */
    public void tourAskDeleteBlocked() {
        openApp(App.KEYCARDS);
        keycards.tourAskDeleteBlocked();
    }

    /** Alleen voor de screenshot-rondleiding: in de app Devices de eerste lezer een andere naam aan het geven. */
    public void tourRenameInput(String name) {
        openApp(App.DEVICES);
        devices.tourRename(name);
    }

    /** Alleen voor de screenshot-rondleiding: die nieuwe naam bewaren (Save). */
    public void tourSaveRename() {
        devices.tourSave();
    }

    /** Alleen voor de screenshot-rondleiding: een app openen (of bovenop zetten). */
    public void tourOpen(App app) {
        openApp(app);
    }

    /** Alleen voor de screenshot-rondleiding: de bewakingsmuur, of het grote beeld van deze camera. */
    public void tourCameras(@Nullable BlockPos camera) {
        openApp(App.CAMERAS);
        cameras.tourBig(camera);
    }

    /** Alleen voor de screenshot-rondleiding: het grote beeld van deze camera, met {@code typed} in het naamvak. */
    public void tourRenameCamera(BlockPos camera, String typed) {
        tourCameras(camera);
        cameras.tourRename(typed);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (windows.isEmpty()) {
            openApp(App.STATUS);
        }
    }

    // ================================================================ Vensters openen/focussen

    /** Opent een app (of zet zijn venster bovenop als het al open is). */
    private Win openApp(App app) {
        for (Win win : windows) {
            if (win.app == app) {
                focus(win);
                return win;
            }
        }
        // Rechts van de icoontjes, elk nieuw venster een stukje verder; maar altijd helemaal in beeld.
        int offset = windows.size() * 16;
        int left = iconsRight() + 8;
        int x = Math.max(0, Math.min(left + offset, width - app.w - 4));
        int y = Math.min(30 + offset, Math.max(24, height - app.h - TASKBAR_H - 4));
        int w = app.w;
        int h = app.h;
        if (app == App.LOGIC) {
            // Schermvullend (code heeft ruimte nodig); via de taakbalk kom je bij de andere apps.
            x = 4;
            y = 17;
            w = Math.max(260, width - 8);
            h = Math.max(150, height - TASKBAR_H - 20);
        }
        Win win = new Win(app, x, y, w, h);
        windows.add(win);
        focus(win);
        return win;
    }

    /** Zet een venster bovenop (achteraan in de lijst = als laatste getekend = bovenop). */
    private void focus(Win win) {
        windows.remove(win);
        windows.add(win);
        if (win.app != App.NOTES) {
            notes.blur();
        }
        if (win.app != App.ACCESS) {
            access.blur();
        }
        if (win.app != App.CAMERAS) {
            cameras.blur();
        }
        if (win.app != App.KEYCARDS) {
            keycards.blur();
        }
        if (win.app != App.LOGIC) {
            logic.blur();
        }
        if (win.app != App.DEVICES) {
            devices.blur();
        }
    }

    private @Nullable Win focused() {
        return windows.isEmpty() ? null : windows.getLast();
    }

    /** Het bovenste venster op dit punt, of null. */
    private @Nullable Win windowAt(int mx, int my) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            Win win = windows.get(i);
            if (mx >= win.x && mx < win.x + win.w && my >= win.y && my < win.y + win.h) {
                return win;
            }
        }
        return null;
    }

    /** Het vak binnen een venster waar zijn app in tekent. */
    private static Area contentArea(Win win) {
        return new Area(win.x + 6, win.y + TITLE_H + 3, win.w - 12, win.h - TITLE_H - 6);
    }

    private void closeWindow(Win win) {
        windows.remove(win);
        if (win.app == App.SETTINGS) {
            editingPassword = false;
        }
        if (win.app == App.CAMERAS) {
            cameras.blur(); // een half getypte naam niet bewaren
        }
        if (win.app == App.DEVICES) {
            devices.blur(); // hier ook niet
        }
        if (win.app == App.NOTES) {
            notes.save(); // wat je typte, gaat niet verloren
            notes.blur();
        }
    }

    // ================================================================ Muis

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        // Taakbalk onderaan.
        if (my >= height - TASKBAR_H) {
            int tx = 4;
            for (Win win : windows) {
                int chip = 8 + font.width(win.app.label);
                if (mx >= tx && mx < tx + chip) {
                    focus(win);
                    return true;
                }
                tx += chip + 4;
            }
            return true;
        }
        // Vensters, van boven (achteraan) naar onder.
        Win win = windowAt(mx, my);
        if (win != null) {
            focus(win);
            // Het kruisje rechtsboven.
            if (mx >= win.x + win.w - TITLE_H && my < win.y + TITLE_H) {
                closeWindow(win);
                return true;
            }
            // De titelbalk: slepen.
            if (my < win.y + TITLE_H) {
                dragging = win;
                dragDx = mx - win.x;
                dragDy = my - win.y;
                return true;
            }
            pressedIn = win;
            clickContent(win, mx, my, button);
            return true;
        }
        // Icoontjes links (onder de vensters).
        App icon = iconAt(mx, my);
        if (icon != null) {
            openApp(icon);
            return true;
        }
        // Op de lege desktop: stop met typen.
        editingPassword = false;
        notes.blur();
        return true;
    }

    /**
     * Waar het icoontje van deze app staat: {x, y}. Onder elkaar langs de linkerkant; past het niet
     * meer boven de taakbalk, dan begint een nieuwe kolom.
     */
    private int[] iconPos(int index) {
        int perColumn = Math.max(1, (height - TASKBAR_H - 24) / (ICON + 14));
        return new int[] {6 + (index / perColumn) * (ICON + 18), 24 + (index % perColumn) * (ICON + 14)};
    }

    /** Waar de kolommen met icoontjes ophouden (nieuwe vensters openen rechts daarvan). */
    private int iconsRight() {
        int[] last = iconPos(App.values().length - 1);
        return last[0] + ICON;
    }

    private @Nullable App iconAt(int mx, int my) {
        for (App app : App.values()) {
            int[] pos = iconPos(app.ordinal());
            if (mx >= pos[0] && mx < pos[0] + ICON && my >= pos[1] && my < pos[1] + ICON) {
                return app;
            }
        }
        return null;
    }

    /** Een klik binnen de inhoud van een venster (de app zelf). */
    private void clickContent(Win win, int mx, int my, int button) {
        switch (win.app) {
            case FILES -> files.mouseClicked(font, contentArea(win), mx, my, button);
            case NOTES -> notes.mouseClicked(font, contentArea(win), mx, my, button);
            case DEVICES -> devices.mouseClicked(font, contentArea(win), mx, my);
            case CAMERAS -> cameras.mouseClicked(font, contentArea(win), mx, my);
            case ACCESS -> access.mouseClicked(font, contentArea(win), mx, my);
            case KEYCARDS -> keycards.mouseClicked(font, contentArea(win), mx, my);
            case LOGIC -> logic.mouseClicked(font, contentArea(win), mx, my, button);
            case SETTINGS -> {
                int cx = win.x + 6;
                int fieldY = win.y + TITLE_H + 60;
                if (mx >= cx && mx < cx + 140 && my >= fieldY && my < fieldY + 14) {
                    editingPassword = true;
                    return;
                }
                if (mx >= cx + 146 && mx < cx + 216 && my >= fieldY && my < fieldY + 14) {
                    submitPassword();
                    return;
                }
                editingPassword = false;
            }
            default -> { }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null) {
            dragging.x = Math.max(0, Math.min(width - 20, (int) mouseX - dragDx));
            dragging.y = Math.max(0, Math.min(height - TASKBAR_H - 6, (int) mouseY - dragDy));
            return true;
        }
        // Slepen binnen een app (een bestand naar een map, tekst selecteren).
        if (pressedIn != null && pressedIn.app == App.FILES) {
            return files.mouseDragged((int) mouseX, (int) mouseY);
        }
        if (pressedIn != null && pressedIn.app == App.NOTES) {
            return notes.mouseDragged((int) mouseX, (int) mouseY, button, dragX, dragY);
        }
        if (pressedIn != null && pressedIn.app == App.LOGIC) {
            return logic.mouseDragged((int) mouseX, (int) mouseY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        Win win = pressedIn;
        pressedIn = null;
        if (win != null && win.app == App.FILES) {
            files.mouseReleased(contentArea(win), (int) mouseX, (int) mouseY);
        } else if (win != null && win.app == App.NOTES) {
            notes.mouseReleased((int) mouseX, (int) mouseY, button);
        } else if (win != null && win.app == App.CAMERAS) {
            cameras.mouseReleased((int) mouseX, (int) mouseY);
        } else if (win != null && win.app == App.LOGIC) {
            logic.mouseReleased((int) mouseX, (int) mouseY);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Het venster onder de muis scrolt (of anders het venster bovenop).
        Win win = windowAt((int) mouseX, (int) mouseY);
        if (win == null) {
            win = focused();
        }
        if (win == null) {
            return true;
        }
        switch (win.app) {
            case FILES -> files.mouseScrolled(scrollY);
            case NOTES -> notes.mouseScrolled((int) mouseX, (int) mouseY, scrollY);
            case DEVICES -> devices.mouseScrolled(scrollY);
            case CAMERAS -> cameras.mouseScrolled(scrollY);
            case ACCESS -> access.mouseScrolled(scrollY);
            case KEYCARDS -> keycards.mouseScrolled(scrollY);
            case LOGIC -> logic.mouseScrolled(contentArea(win), (int) mouseX, (int) mouseY, scrollY);
            default -> win.scroll = Math.max(0, win.scroll - (int) Math.signum(scrollY) * 3);
        }
        return true;
    }

    // ================================================================ Toetsen

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        Win top = focused();
        if (top != null && top.app == App.FILES && files.charTyped(codePoint, modifiers)
                || top != null && top.app == App.NOTES && notes.charTyped(codePoint, modifiers)
                || top != null && top.app == App.ACCESS && access.charTyped(codePoint, modifiers)
                || top != null && top.app == App.CAMERAS && cameras.charTyped(codePoint, modifiers)
                || top != null && top.app == App.DEVICES && devices.charTyped(codePoint, modifiers)
                || top != null && top.app == App.KEYCARDS && keycards.charTyped(codePoint, modifiers)
                || top != null && top.app == App.LOGIC && logic.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (editingPassword && passwordText.length() < 8
                && (codePoint >= 'a' && codePoint <= 'z' || codePoint >= '0' && codePoint <= '9')) {
            passwordText += codePoint;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Eerst de app in het venster bovenop (zo sluit Esc in een tekstvak alleen dat vak).
        Win top = focused();
        if (top != null && top.app == App.FILES && files.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.NOTES && notes.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.ACCESS && access.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.CAMERAS && cameras.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.DEVICES && devices.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.KEYCARDS && keycards.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.LOGIC && logic.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (editingPassword) {
            if (keyCode == 259 && !passwordText.isEmpty()) { // backspace
                passwordText = passwordText.substring(0, passwordText.length() - 1);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // enter
                submitPassword();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submitPassword() {
        if (passwordText.isEmpty()) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putString("pw", passwordText);
        send("passwd", args);
        passwordText = "";
        editingPassword = false;
    }

    @Override
    public void removed() {
        // Een notitie die nog niet bewaard is, eerst bewaren; dan de sessie op de server netjes
        // sluiten (hij sluit anders vanzelf als je wegloopt).
        notes.save();
        logic.saveIfValid(); // een diagram dat klopt, bewaren we vanzelf (zoals een notitie)
        send("close", new CompoundTag());
        super.removed();
    }

    // ================================================================ Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, HudStyle.argb(255, BG));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // Kopbalk: de naam van het systeem, en of je op afstand zit.
        String host = data.getString("host");
        String ip = data.getString("ip");
        g.fill(0, 0, width, 14, HudStyle.argb(255, BAR));
        g.drawString(font, "STARK OS  " + host + (ip.isEmpty() ? "" : "  " + ip), 6, 3,
                HudStyle.argb(255, HudStyle.CYAN), false);
        boolean online = data.getBoolean("online");
        String power = online ? "ONLINE" : "OFFLINE";
        g.drawString(font, power, width - font.width(power) - 6, 3, HudStyle.argb(255, online ? HudStyle.GREEN : HudStyle.RED), false);
        if (remote) {
            String tag = "REMOTE SESSION";
            g.drawString(font, tag, (width - font.width(tag)) / 2, 3, HudStyle.argb(255, HudStyle.ORANGE), false);
        }
        // Icoontjes links.
        for (App app : App.values()) {
            int[] pos = iconPos(app.ordinal());
            int ix = pos[0];
            int iy = pos[1];
            boolean open = windows.stream().anyMatch(win -> win.app == app);
            g.fill(ix, iy, ix + ICON, iy + ICON, HudStyle.argb(open ? 255 : 180, open ? PANEL : 0x0E1822));
            HudStyle.brackets(g, ix, iy, ix + ICON, iy + ICON, 5, HudStyle.argb(200, open ? HudStyle.CYAN : 0x33505F));
            g.drawString(font, app.glyph, ix + (ICON - font.width(app.glyph)) / 2, iy + ICON / 2 - 4,
                    HudStyle.argb(255, HudStyle.WHITE), false);
            HudStyle.smallText(g, font, app.label, ix + ICON / 2, iy + ICON + 1, HudStyle.argb(220, HudStyle.WHITE));
        }
        // De vensters, onderste eerst, elk een laagje hoger. Alleen het venster onder de muis krijgt
        // de muis (voor wat oplicht).
        Win under = windowAt(mouseX, mouseY);
        for (int i = 0; i < windows.size(); i++) {
            Win win = windows.get(i);
            boolean mouseHere = win == under;
            g.pose().pushPose();
            g.pose().translate(0, 0, (i + 1) * LAYER);
            renderWindow(g, win, mouseHere ? mouseX : -1, mouseHere ? mouseY : -1, partialTick);
            g.pose().popPose();
        }
        // Taakbalk onderaan, boven alle vensters.
        g.pose().pushPose();
        g.pose().translate(0, 0, (windows.size() + 1) * LAYER);
        g.fill(0, height - TASKBAR_H, width, height, HudStyle.argb(255, BAR));
        int tx = 4;
        Win top = focused();
        for (Win win : windows) {
            int chip = 8 + font.width(win.app.label);
            boolean active = win == top;
            g.fill(tx, height - TASKBAR_H + 2, tx + chip, height - 2, HudStyle.argb(active ? 255 : 160, active ? PANEL : 0x0E1822));
            g.drawString(font, win.app.label, tx + 4, height - TASKBAR_H + 5, HudStyle.argb(255, active ? HudStyle.CYAN : HudStyle.WHITE), false);
            tx += chip + 4;
        }
        // Een melding van de server, rechts in de taakbalk.
        if (!notice.isEmpty() && Util.getMillis() < noticeUntil) {
            g.drawString(font, notice, width - font.width(notice) - 6, height - TASKBAR_H + 5,
                    HudStyle.argb(255, noticeError ? HudStyle.RED : HudStyle.GREEN), false);
        }
        g.pose().popPose();
    }

    private void renderWindow(GuiGraphics g, Win win, int mouseX, int mouseY, float partialTick) {
        boolean active = win == focused();
        g.fill(win.x, win.y, win.x + win.w, win.y + win.h, HudStyle.argb(255, PANEL));
        HudStyle.brackets(g, win.x, win.y, win.x + win.w, win.y + win.h, 7,
                HudStyle.argb(active ? 235 : 150, active ? HudStyle.CYAN : 0x33505F));
        // Titelbalk.
        g.fill(win.x, win.y, win.x + win.w, win.y + TITLE_H, HudStyle.argb(255, active ? 0x16303F : 0x111E28));
        g.drawString(font, win.app.label, win.x + 5, win.y + 3, HudStyle.argb(255, active ? HudStyle.CYAN : HudStyle.WHITE), false);
        // Kruisje.
        g.drawString(font, "x", win.x + win.w - 9, win.y + 3, HudStyle.argb(255, HudStyle.RED), false);
        // Inhoud, afgeknipt.
        Area area = contentArea(win);
        g.enableScissor(win.x + 2, win.y + TITLE_H, win.x + win.w - 2, win.y + win.h - 2);
        switch (win.app) {
            case STATUS -> renderStatus(g, area.x(), area.y());
            case DEVICES -> devices.render(g, font, area, mouseX, mouseY, partialTick);
            case CAMERAS -> cameras.render(g, font, area, mouseX, mouseY, partialTick);
            case ACCESS -> access.render(g, font, area, mouseX, mouseY, partialTick);
            case KEYCARDS -> keycards.render(g, font, area, mouseX, mouseY, partialTick);
            case LOGIC -> logic.render(g, font, area, mouseX, mouseY, partialTick);
            case FILES -> files.render(g, font, area, mouseX, mouseY, partialTick);
            case NOTES -> notes.render(g, font, area, mouseX, mouseY, partialTick);
            case LOGS -> renderLogs(g, area.x(), area.y(), area.h(), win.scroll);
            case SETTINGS -> renderSettings(g, area.x(), area.y());
        }
        g.disableScissor();
    }

    private void renderStatus(GuiGraphics g, int cx, int cy) {
        line(g, cx, cy, "Host:  " + data.getString("host"), HudStyle.WHITE);
        line(g, cx, cy + LINE, "IP:    " + data.getString("ip"), HudStyle.WHITE);
        line(g, cx, cy + LINE * 2, "SSH:   port " + data.getInt("port"), HudStyle.WHITE);
        line(g, cx, cy + LINE * 3, "Owner: " + data.getString("owner"), HudStyle.WHITE);
        boolean pw = data.getBoolean("hasPassword");
        line(g, cx, cy + LINE * 4, "Password: " + (pw ? "set" : "NOT SET"), pw ? HudStyle.GREEN : HudStyle.ORANGE);
        boolean online = data.getBoolean("online");
        line(g, cx, cy + LINE * 5, "Power: " + (online ? "online" : "offline"), online ? HudStyle.GREEN : HudStyle.RED);
        // De server: draait hij, en zo niet, waarom niet.
        CompoundTag server = data.getCompound("server");
        if (server.getBoolean("online")) {
            int racks = server.getInt("racks");
            line(g, cx, cy + LINE * 6, "Server: online, " + racks + (racks == 1 ? " rack" : " racks"), HudStyle.GREEN);
        } else {
            line(g, cx, cy + LINE * 6, "Server: " + server.getString("problem"), HudStyle.ORANGE);
        }
    }

    private void renderLogs(GuiGraphics g, int cx, int cy, int ch, int scroll) {
        List<Tag> log = data.getList("log", Tag.TAG_COMPOUND);
        int rows = Math.max(1, ch / LINE);
        int total = log.size();
        int first = Math.max(0, total - rows - scroll);
        int end = Math.min(total, first + rows);
        int y = cy;
        for (int i = first; i < end; i++) {
            CompoundTag entry = (CompoundTag) log.get(i);
            String text = entry.getInt("ago") + "s  " + entry.getString("x");
            line(g, cx, y, text, logColour(entry.getString("k")));
            y += LINE;
        }
        if (total == 0) {
            line(g, cx, cy, "(no events yet)", 0x5A6B72);
        }
    }

    private void renderSettings(GuiGraphics g, int cx, int cy) {
        line(g, cx, cy, "System settings", HudStyle.CYAN);
        line(g, cx, cy + 16, "IP:   " + data.getString("ip"), HudStyle.WHITE);
        line(g, cx, cy + 26, "Host: " + data.getString("host"), HudStyle.WHITE);
        line(g, cx, cy + 36, "SSH port: " + data.getInt("port"), HudStyle.WHITE);
        line(g, cx, cy + 47, data.getBoolean("hasPassword") ? "Change password:" : "Set a password:", HudStyle.WHITE);
        // Het invoervak.
        int fieldY = cy + 57;
        g.fill(cx, fieldY, cx + 140, fieldY + 14, HudStyle.argb(255, 0x0A141B));
        HudStyle.brackets(g, cx, fieldY, cx + 140, fieldY + 14, 4,
                HudStyle.argb(255, editingPassword ? HudStyle.CYAN : 0x33505F));
        String shown = passwordText + (editingPassword && (System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "");
        g.drawString(font, shown, cx + 4, fieldY + 3, HudStyle.argb(255, HudStyle.WHITE), false);
        // De knop.
        g.fill(cx + 146, fieldY, cx + 216, fieldY + 14, HudStyle.argb(255, 0x16303F));
        HudStyle.brackets(g, cx + 146, fieldY, cx + 216, fieldY + 14, 4, HudStyle.argb(255, HudStyle.CYAN));
        String button = data.getBoolean("hasPassword") ? "Change" : "Set";
        g.drawString(font, button, cx + 146 + (70 - font.width(button)) / 2, fieldY + 3, HudStyle.argb(255, HudStyle.CYAN), false);
        line(g, cx, cy + 82, "4-8 chars, a-z and 0-9 only.", 0x8A9BA0);
        if (!data.getBoolean("hasPassword")) {
            line(g, cx, cy + 96, "No password yet: anyone nearby can open this.", HudStyle.ORANGE);
        }
    }

    private void line(GuiGraphics g, int x, int y, String text, int rgb) {
        g.drawString(font, text, x, y, HudStyle.argb(255, rgb), false);
    }

    private static int logColour(String type) {
        return switch (type) {
            case "HACK", "TRAP", "LOGIN_FAIL", "ALARM" -> HudStyle.RED;
            case "LOGIC" -> 0xB89CFF;
            case "OWNER", "LOGIN" -> HudStyle.GREEN;
            case "SHOT", "ENTRY" -> HudStyle.ORANGE;
            case "DEVICE" -> HudStyle.CYAN;
            default -> HudStyle.WHITE;
        };
    }
}
