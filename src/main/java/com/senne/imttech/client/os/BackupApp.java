package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * De app Backup op de Security OS-desktop (jouw wens, 8 oktober: "een manier om backups te nemen naar
 * een ander datacenter, omdat als alles afbrandt anders je hele config weg is").
 *
 * <pre>
 *  Back up to:  [<] dev-lab (10.4.2.9) [>]                 30/48 free
 *  This server: 12 blocks                              [Backup now]
 *  Automatic:   [Off] [5 min] [15 min] [60 min]
 *  Last backup: 3 min ago
 *  YOUR BACKUPS (2)
 *   dev-sec on dev-lab  12 blocks  3 min ago  auto       [Restore] [Delete]
 *   dev-sec on dev-lab  11 blocks  18 min ago            [Restore] [Delete]
 * </pre>
 *
 * Alles komt uit de momentopname (het stuk "backup", zie Backups.snapshot); de knoppen sturen een
 * opdracht naar de server, die zegt onderaan of het lukte. Restore en Delete vragen eerst "Sure?"
 * (nog een keer klikken binnen 4 seconden).
 */
final class BackupApp {
    private static final int ROW = 13;
    private static final long CONFIRM_MILLIS = 4000;

    /** Een knop op het scherm (bij het tekenen onthouden, zodat een klik weet wat hij raakte). */
    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final SecurityOsScreen screen;
    private final List<Hit> hits = new ArrayList<>();
    private int scroll;
    /** Welke knop "Sure?" vraagt ("restore:12", "delete:12"), en tot wanneer. */
    private String asking = "";
    private long askingUntil;

    BackupApp(SecurityOsScreen screen) {
        this.screen = screen;
    }

    private CompoundTag backup() {
        return screen.data().getCompound("backup");
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
        hits.clear();
        CompoundTag data = backup();
        List<Tag> targets = data.getList("targets", Tag.TAG_COMPOUND);
        String targetKey = data.getString("target");
        int x = a.x();
        int y = a.y();
        int labelW = font.width("This server: ") + 4;

        // Waarheen.
        label(g, font, "Back up to:", x, y + 2);
        if (targets.isEmpty()) {
            g.drawString(font, "(no other datacenter of yours)", x + labelW, y + 2, HudStyle.argb(255, OsDraw.GREY), false);
        } else {
            int index = indexOf(targets, targetKey);
            int bx = x + labelW;
            bx += button(g, font, bx, y, "<", true, HudStyle.CYAN, () -> choose(targets, index - 1)) + 3;
            String name = "(choose one)";
            int nameColour = OsDraw.GREY;
            if (index >= 0) {
                CompoundTag target = (CompoundTag) targets.get(index);
                name = target.getString("host") + " (" + target.getString("ip") + ")";
                nameColour = target.getBoolean("online") ? HudStyle.WHITE : HudStyle.ORANGE;
                String free = target.getBoolean("online") ? target.getInt("free") + "/" + target.getInt("cap") + " free" : "offline";
                g.drawString(font, free, x + a.w() - font.width(free), y + 2,
                        HudStyle.argb(255, target.getBoolean("online") ? OsDraw.GREY : HudStyle.ORANGE), false);
            }
            g.drawString(font, name, bx, y + 2, HudStyle.argb(255, nameColour), false);
            bx += font.width(name) + 3;
            button(g, font, bx, y, ">", true, HudStyle.CYAN, () -> choose(targets, index + 1));
        }

        // Nu een backup maken.
        y += 15;
        label(g, font, "This server:", x, y + 2);
        g.drawString(font, data.getInt("size") + " blocks", x + labelW, y + 2, HudStyle.argb(255, HudStyle.WHITE), false);
        String now = "Backup now";
        button(g, font, x + a.w() - OsDraw.buttonWidth(font, now), y, now, !targetKey.isEmpty(), HudStyle.CYAN,
                () -> screen.send("backup_now", new CompoundTag()));

        // Automatisch.
        y += 15;
        label(g, font, "Automatic:", x, y + 2);
        int bx = x + labelW;
        int minutes = data.getInt("minutes");
        for (int choice : new int[] {0, 5, 15, 60}) {
            String text = choice == 0 ? "Off" : choice + " min";
            boolean active = choice == minutes;
            CompoundTag args = new CompoundTag();
            args.putInt("minutes", choice);
            bx += button(g, font, bx, y, text, true, active ? HudStyle.GREEN : HudStyle.CYAN, () -> screen.send("backup_auto", args)) + 3;
        }

        // Hoe het gaat.
        y += 15;
        String error = data.getString("error");
        int lastAgo = data.getInt("lastAgo");
        if (!error.isEmpty()) {
            g.drawString(font, OsDraw.fitRight(font, "Automatic backup failed: " + error, a.w()), x, y + 1,
                    HudStyle.argb(255, HudStyle.ORANGE), false);
        } else {
            g.drawString(font, lastAgo < 0 ? "No backup yet: if your datacenter burns, everything is gone."
                    : "Last backup: " + ago(lastAgo), x, y + 1, HudStyle.argb(255, lastAgo < 0 ? HudStyle.ORANGE : OsDraw.GREY), false);
        }

        // Alle backups van de eigenaar.
        y += 14;
        List<Tag> list = data.getList("list", Tag.TAG_COMPOUND);
        g.drawString(font, "YOUR BACKUPS (" + list.size() + ")", x, y, HudStyle.argb(255, HudStyle.CYAN), false);
        int top = y + 11;
        int bottom = a.y() + a.h();
        OsDraw.field(g, x, top - 1, x + a.w(), bottom, false);
        int rows = Math.max(1, (bottom - top - 2) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        if (list.isEmpty()) {
            g.drawString(font, "(none yet)", x + 5, top + 3, HudStyle.argb(255, OsDraw.GREY), false);
        }
        String sure = "Sure?";
        int deleteW = Math.max(OsDraw.buttonWidth(font, "Delete"), OsDraw.buttonWidth(font, sure));
        int restoreW = Math.max(OsDraw.buttonWidth(font, "Restore"), OsDraw.buttonWidth(font, sure));
        for (int i = scroll; i < list.size() && i - scroll < rows; i++) {
            CompoundTag item = (CompoundTag) list.get(i);
            int ry = top + 2 + (i - scroll) * ROW;
            int id = item.getInt("id");
            boolean online = item.getBoolean("online");
            String text = item.getString("from") + " on " + item.getString("at") + "  " + item.getInt("size") + " blocks  "
                    + ago(item.getInt("ago")) + (item.getBoolean("auto") ? "  auto" : "");
            int textW = a.w() - deleteW - restoreW - 14;
            g.drawString(font, OsDraw.fitRight(font, text, textW), x + 4, ry + 2,
                    HudStyle.argb(255, item.getBoolean("same") ? HudStyle.WHITE : OsDraw.GREY), false);
            int dx = x + a.w() - deleteW - 3;
            int rx = dx - restoreW - 3;
            String restoreKey = "restore:" + id;
            String deleteKey = "delete:" + id;
            button(g, font, rx, ry, asking(restoreKey) ? sure : "Restore", online, HudStyle.GREEN, () -> {
                if (confirm(restoreKey)) {
                    CompoundTag args = new CompoundTag();
                    args.putInt("id", id);
                    screen.send("backup_restore", args);
                }
            });
            button(g, font, dx, ry, asking(deleteKey) ? sure : "Delete", true, HudStyle.RED, () -> {
                if (confirm(deleteKey)) {
                    CompoundTag args = new CompoundTag();
                    args.putInt("id", id);
                    screen.send("backup_delete", args);
                }
            });
        }
    }

    /** Een knop tekenen en onthouden waar hij staat; geeft zijn breedte terug. Een grijze knop doet niets. */
    private int button(GuiGraphics g, Font font, int x, int y, String text, boolean enabled, int colour, Runnable action) {
        int w = OsDraw.button(g, font, x, y, text, enabled, colour);
        if (enabled) {
            hits.add(new Hit(x, y, w, OsDraw.BUTTON_H, action));
        }
        return w;
    }

    private static void label(GuiGraphics g, Font font, String text, int x, int y) {
        g.drawString(font, text, x, y, HudStyle.argb(255, OsDraw.GREY), false);
    }

    /** Het volgende (of vorige) datacenter kiezen; voorbij het einde begint het weer vooraan. */
    private void choose(List<Tag> targets, int index) {
        if (targets.isEmpty()) {
            return;
        }
        int wrapped = Math.floorMod(index, targets.size());
        CompoundTag args = new CompoundTag();
        args.putString("key", ((CompoundTag) targets.get(wrapped)).getString("key"));
        screen.send("backup_target", args);
    }

    private static int indexOf(List<Tag> targets, String key) {
        for (int i = 0; i < targets.size(); i++) {
            if (((CompoundTag) targets.get(i)).getString("key").equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** "now", "40 s ago", "3 min ago", "2 h ago". */
    private static String ago(int seconds) {
        if (seconds < 5) {
            return "just now";
        }
        if (seconds < 60) {
            return seconds + " s ago";
        }
        if (seconds < 3600) {
            return seconds / 60 + " min ago";
        }
        return seconds / 3600 + " h ago";
    }

    // ================================================================ "Sure?"

    private boolean asking(String key) {
        return asking.equals(key) && Util.getMillis() < askingUntil;
    }

    /** De eerste klik vraagt "Sure?" (4 seconden lang), de tweede doet het echt. */
    private boolean confirm(String key) {
        if (asking(key)) {
            asking = "";
            return true;
        }
        asking = key;
        askingUntil = Util.getMillis() + CONFIRM_MILLIS;
        return false;
    }

    // ================================================================ Muis

    void mouseClicked(Font font, SecurityOsScreen.Area a, int mx, int my) {
        for (Hit hit : List.copyOf(hits)) {
            if (OsDraw.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                hit.action().run();
                return;
            }
        }
    }

    void mouseScrolled(double amount) {
        scroll = Math.max(0, scroll - (int) Math.signum(amount));
    }
}
