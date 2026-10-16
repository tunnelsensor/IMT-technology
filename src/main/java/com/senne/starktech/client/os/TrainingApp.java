package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.training.TrainingReport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * De app Training van de Stark OS-desktop (hoofdstuk 26): de training die nu bezig is (wie, hoeveel levens, treffers,
 * hoe lang al), met een knop Stop, en daaronder de rapporten van de laatste trainingen (de nieuwste eerst).
 *
 * <pre>
 *  NOW  Kamino: Senne                                   [Stop]
 *       training shots   2/3 lives   1 hit   1:12 / 2:00
 *  REPORTS
 *  Kamino  Senne  PASSED  reached the goal          2:14
 *     3 hits: Guard 2 x2, Guard 1 x1; 2 robots down
 * </pre>
 * Alles komt van de server (zie training/Trainings.snapshot), om de seconde opnieuw.
 */
final class TrainingApp {
    private static final int REPORT_H = 22;

    private final StarkOsScreen screen;
    private int scroll;

    TrainingApp(StarkOsScreen screen) {
        this.screen = screen;
    }

    private CompoundTag data() {
        return screen.data().getCompound("training");
    }

    private List<CompoundTag> reports() {
        List<CompoundTag> list = new ArrayList<>();
        for (Tag element : data().getList("reports", Tag.TAG_COMPOUND)) {
            list.add((CompoundTag) element);
        }
        return list;
    }

    void render(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my) {
        OsDraw.field(g, a.x(), a.y(), a.x() + a.w(), a.y() + a.h(), false);
        int x = a.x() + 5;
        int y = a.y() + 4;
        CompoundTag now = data().getCompound("now");
        g.drawString(font, "NOW", x, y, HudStyle.argb(255, HudStyle.ORANGE), false);
        if (now.isEmpty()) {
            g.drawString(font, "No training running. Click a Training Start block to start one.", x + 26, y,
                    HudStyle.argb(255, OsDraw.GREY), false);
        } else {
            g.drawString(font, now.getString("name") + ": " + now.getString("trainee"), x + 26, y, HudStyle.argb(255, HudStyle.WHITE), false);
            OsDraw.button(g, font, a.x() + a.w() - 4 - OsDraw.buttonWidth(font, "Stop"), y - 2, "Stop", true, HudStyle.RED);
            String state = !now.getBoolean("running") ? "counting down..."
                    : now.getString("mode").toLowerCase(java.util.Locale.ROOT) + "   " + now.getInt("lives") + "/" + now.getInt("max")
                    + " lives   " + now.getInt("hits") + (now.getInt("hits") == 1 ? " hit   " : " hits   ")
                    + TrainingReport.time(now.getLong("ticks")) + (now.getInt("limit") > 0 ? " / " + TrainingReport.time(now.getInt("limit") * 20L) : "");
            g.drawString(font, state, x + 26, y + 11, HudStyle.argb(255, now.getBoolean("running") ? HudStyle.GREEN : HudStyle.ORANGE), false);
        }
        y += 26;
        g.drawString(font, "REPORTS", x, y, HudStyle.argb(255, HudStyle.ORANGE), false);
        y += 11;
        List<CompoundTag> reports = reports();
        if (reports.isEmpty()) {
            g.drawString(font, "No trainings yet.", x, y, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        scroll = Math.max(0, Math.min(scroll, reports.size() - 1));
        for (int i = scroll; i < reports.size() && y + REPORT_H <= a.y() + a.h(); i++) {
            CompoundTag report = reports.get(i);
            boolean passed = report.getBoolean("passed");
            String head = report.getString("Name") + "  " + report.getString("Trainee");
            g.drawString(font, head, x, y, HudStyle.argb(255, HudStyle.WHITE), false);
            int rx = x + font.width(head) + 6;
            String verdict = passed ? "PASSED" : report.getString("Result").equals("STOPPED") ? "STOPPED" : "FAILED";
            g.drawString(font, verdict, rx, y, HudStyle.argb(255, passed ? HudStyle.GREEN : verdict.equals("STOPPED") ? HudStyle.ORANGE
                    : HudStyle.RED), false);
            rx += font.width(verdict) + 6;
            String time = TrainingReport.time(report.getLong("Ticks"));
            g.drawString(font, OsDraw.fitLeft(font, report.getString("text"), a.x() + a.w() - 8 - font.width(time) - rx), rx, y,
                    HudStyle.argb(255, OsDraw.GREY), false);
            g.drawString(font, time, a.x() + a.w() - 5 - font.width(time), y, HudStyle.argb(255, OsDraw.GREY), false);
            int hits = report.getInt("Hits");
            String details = hits + (hits == 1 ? " hit" : " hits") + (report.getString("HitsBy").isEmpty() ? "" : ": " + report.getString("HitsBy"))
                    + (report.getInt("Downed") > 0 ? "; " + report.getInt("Downed") + (report.getInt("Downed") == 1 ? " robot down" : " robots down") : "");
            g.drawString(font, OsDraw.fitLeft(font, details, a.w() - 18), x + 8, y + 10, HudStyle.argb(255, 0x9FC9D6), false);
            y += REPORT_H;
        }
    }

    void mouseClicked(Font font, StarkOsScreen.Area a, int mx, int my) {
        if (data().getCompound("now").isEmpty()) {
            return;
        }
        int w = OsDraw.buttonWidth(font, "Stop");
        if (OsDraw.inside(mx, my, a.x() + a.w() - 4 - w, a.y() + 2, w, OsDraw.BUTTON_H)) {
            screen.send("training_stop", new CompoundTag());
        }
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
    }
}
