package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.network.OsActionPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * dit scherm tekent het alleen. Wat je doet (een wachtwoord zetten, straks apparaten aan/uit) gaat
 * als een opdracht terug naar de server, die altijd zelf controleert of het mag.
 *
 * Dit bestand is de vensterbeheerder: de achtergrond, de icoontjes links, de taakbalk onderaan, en
 * het slepen, focussen en sluiten van vensters. Elke app ({@link App}) tekent zichzelf in zijn
 * venster en handelt zijn eigen kliks af.
 */
public class StarkOsScreen extends Screen {
    /** De apps op de desktop. Later komen er meer bij (Devices, Access, Files, Notes, Cameras). */
    public enum App {
        STATUS("Status", 240, 120),
        LOGS("Logs", 330, 200),
        SETTINGS("Settings", 270, 160);

        final String label;
        final int w;
        final int h;

        App(String label, int w, int h) {
            this.label = label;
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

        Win(App app, int x, int y) {
            this.app = app;
            this.w = app.w;
            this.h = app.h;
            this.x = x;
            this.y = y;
        }
    }

    private static final int TITLE_H = 13;
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

    /** De tekst die je in het wachtwoordvak typt (alleen Settings gebruikt dit nu). */
    private boolean editingPassword;
    private String passwordText = "";

    public StarkOsScreen(BlockPos core, boolean remote) {
        super(Component.literal("Stark OS"));
        this.core = core;
        this.remote = remote;
    }

    public BlockPos core() {
        return core;
    }

    /** Een nieuwe momentopname van de server. */
    public void update(CompoundTag snapshot) {
        this.data = snapshot;
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

    private void openApp(App app) {
        for (Win win : windows) {
            if (win.app == app) {
                focus(win);
                return;
            }
        }
        int offset = windows.size() * 16;
        int x = Math.min(50 + offset, Math.max(50, width - app.w - 10));
        int y = Math.min(30 + offset, Math.max(24, height - app.h - TASKBAR_H - 4));
        Win win = new Win(app, x, y);
        windows.add(win);
    }

    /** Zet een venster bovenop (achteraan in de lijst = als laatste getekend = bovenop). */
    private void focus(Win win) {
        windows.remove(win);
        windows.add(win);
    }

    private @Nullable Win focused() {
        return windows.isEmpty() ? null : windows.getLast();
    }

    private void closeWindow(Win win) {
        windows.remove(win);
        if (win.app == App.SETTINGS) {
            editingPassword = false;
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
        // Icoontjes links.
        App icon = iconAt(mx, my);
        if (icon != null) {
            openApp(icon);
            return true;
        }
        // Vensters, van boven (achteraan) naar onder.
        for (int i = windows.size() - 1; i >= 0; i--) {
            Win win = windows.get(i);
            if (mx < win.x || mx >= win.x + win.w || my < win.y || my >= win.y + win.h) {
                continue;
            }
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
            clickContent(win, mx, my);
            return true;
        }
        // Op de lege desktop: stop met typen.
        editingPassword = false;
        return true;
    }

    private @Nullable App iconAt(int mx, int my) {
        int iy = 24;
        for (App app : App.values()) {
            if (mx >= 6 && mx < 6 + ICON && my >= iy && my < iy + ICON) {
                return app;
            }
            iy += ICON + 14;
        }
        return null;
    }

    /** Een klik binnen de inhoud van een venster (de app zelf). */
    private void clickContent(Win win, int mx, int my) {
        if (win.app == App.SETTINGS) {
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
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null) {
            dragging.x = Math.max(0, Math.min(width - 20, (int) mouseX - dragDx));
            dragging.y = Math.max(0, Math.min(height - TASKBAR_H - 6, (int) mouseY - dragDy));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Win win = focused();
        if (win != null) {
            win.scroll = Math.max(0, win.scroll - (int) Math.signum(scrollY) * 3);
        }
        return true;
    }

    // ================================================================ Toetsen (typen in het wachtwoordvak)

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editingPassword && passwordText.length() < 8
                && (codePoint >= 'a' && codePoint <= 'z' || codePoint >= '0' && codePoint <= '9')) {
            passwordText += codePoint;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
        PacketDistributor.sendToServer(new OsActionPayload(core, "passwd", args));
        passwordText = "";
        editingPassword = false;
    }

    @Override
    public void removed() {
        // De sessie op de server netjes sluiten (hij sluit anders vanzelf als je wegloopt).
        PacketDistributor.sendToServer(new OsActionPayload(core, "close", new CompoundTag()));
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
        int iy = 24;
        for (App app : App.values()) {
            boolean open = windows.stream().anyMatch(win -> win.app == app);
            g.fill(6, iy, 6 + ICON, iy + ICON, HudStyle.argb(open ? 255 : 180, open ? PANEL : 0x0E1822));
            HudStyle.brackets(g, 6, iy, 6 + ICON, iy + ICON, 5, HudStyle.argb(200, open ? HudStyle.CYAN : 0x33505F));
            g.drawString(font, app.label.substring(0, 1), 6 + ICON / 2 - 2, iy + ICON / 2 - 4,
                    HudStyle.argb(255, HudStyle.WHITE), false);
            HudStyle.smallText(g, font, app.label, 6 + ICON / 2, iy + ICON + 1, HudStyle.argb(220, HudStyle.WHITE));
            iy += ICON + 14;
        }
        // De vensters, onderste eerst.
        for (Win win : windows) {
            renderWindow(g, win, mouseX, mouseY);
        }
        // Taakbalk onderaan.
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
    }

    private void renderWindow(GuiGraphics g, Win win, int mouseX, int mouseY) {
        boolean active = win == focused();
        g.fill(win.x, win.y, win.x + win.w, win.y + win.h, HudStyle.argb(245, PANEL));
        HudStyle.brackets(g, win.x, win.y, win.x + win.w, win.y + win.h, 7,
                HudStyle.argb(active ? 235 : 150, active ? HudStyle.CYAN : 0x33505F));
        // Titelbalk.
        g.fill(win.x, win.y, win.x + win.w, win.y + TITLE_H, HudStyle.argb(255, active ? 0x16303F : 0x111E28));
        g.drawString(font, win.app.label, win.x + 5, win.y + 3, HudStyle.argb(255, active ? HudStyle.CYAN : HudStyle.WHITE), false);
        // Kruisje.
        g.drawString(font, "x", win.x + win.w - 9, win.y + 3, HudStyle.argb(255, HudStyle.RED), false);
        // Inhoud, afgeknipt.
        int cx = win.x + 6;
        int cy = win.y + TITLE_H + 3;
        int cw = win.w - 12;
        int ch = win.h - TITLE_H - 6;
        g.enableScissor(win.x + 2, win.y + TITLE_H, win.x + win.w - 2, win.y + win.h - 2);
        switch (win.app) {
            case STATUS -> renderStatus(g, cx, cy);
            case LOGS -> renderLogs(g, cx, cy, ch, win.scroll);
            case SETTINGS -> renderSettings(g, cx, cy);
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
        line(g, cx, cy + 52, data.getBoolean("hasPassword") ? "Change password:" : "Set a password:", HudStyle.WHITE);
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
            case "HACK", "TRAP", "LOGIN_FAIL" -> HudStyle.RED;
            case "OWNER", "LOGIN" -> HudStyle.GREEN;
            case "SHOT", "ENTRY" -> HudStyle.ORANGE;
            case "DEVICE" -> HudStyle.CYAN;
            default -> HudStyle.WHITE;
        };
    }
}
