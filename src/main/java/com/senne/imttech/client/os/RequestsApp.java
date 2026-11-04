package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * De app Requests (hoofdstuk 49): wie er bij een Auth Console op de knop drukte en op een antwoord wacht. Per verzoek
 * wie, bij welke console, hoeveel seconden er nog zijn, en de knoppen Accept en Deny. Het antwoord gaat naar de server
 * ({@code req_answer}); je diagram hoort het met {@code when request("Gate"):} en {@code request.accepted}.
 */
final class RequestsApp {
    private static final int ROW = 15;

    private final SecurityOsScreen screen;
    private int scroll;

    RequestsApp(SecurityOsScreen screen) {
        this.screen = screen;
    }

    private List<Tag> requests() {
        return screen.data().getList("requests", Tag.TAG_COMPOUND);
    }

    private static int listTop(SecurityOsScreen.Area a) {
        return a.y() + 14;
    }

    void render(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
        g.drawString(font, "Access requests", a.x(), a.y() + 1, HudStyle.argb(255, HudStyle.CYAN), false);
        g.drawString(font, "from the Auth Consoles", a.x() + font.width("Access requests") + 6, a.y() + 1,
                HudStyle.argb(255, OsDraw.GREY), false);
        List<Tag> list = requests();
        int top = listTop(a);
        int bottom = a.y() + a.h() - 12;
        OsDraw.field(g, a.x(), top - 1, a.x() + a.w(), bottom, false);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - 1)));
        int y = top + 2;
        int acceptW = OsDraw.buttonWidth(font, "Accept");
        int denyW = OsDraw.buttonWidth(font, "Deny");
        boolean mayUse = screen.mayUse(SecurityOsScreen.App.REQUESTS);
        for (int i = scroll; i < list.size() && y + ROW <= bottom; i++) {
            CompoundTag request = (CompoundTag) list.get(i);
            String who = request.getString("p");
            String where = request.getString("d");
            int left = request.getInt("s");
            g.drawString(font, who, a.x() + 5, y + 2, HudStyle.argb(255, HudStyle.WHITE), false);
            g.drawString(font, "at " + where, a.x() + 5 + font.width(who) + 6, y + 2, HudStyle.argb(255, OsDraw.GREY), false);
            String timer = left + " s";
            int timerX = a.x() + a.w() - acceptW - denyW - 12 - font.width(timer);
            g.drawString(font, timer, timerX, y + 2, HudStyle.argb(255, left <= 5 ? HudStyle.RED : HudStyle.ORANGE), false);
            OsDraw.button(g, font, a.x() + a.w() - acceptW - denyW - 6, y, "Accept", mayUse, HudStyle.GREEN);
            OsDraw.button(g, font, a.x() + a.w() - denyW - 3, y, "Deny", mayUse, HudStyle.RED);
            y += ROW;
        }
        if (list.isEmpty()) {
            g.drawString(font, "(nobody is asking right now)", a.x() + 5, top + 3, HudStyle.argb(255, OsDraw.GREY), false);
        }
        g.drawString(font, "No answer in time = denied; see when request(...)", a.x(), bottom + 3,
                HudStyle.argb(255, OsDraw.GREY), false);
    }

    void mouseClicked(Font font, SecurityOsScreen.Area a, int mx, int my) {
        if (!screen.mayUse(SecurityOsScreen.App.REQUESTS)) {
            return;
        }
        List<Tag> list = requests();
        int y = listTop(a) + 2;
        int bottom = a.y() + a.h() - 12;
        int acceptW = OsDraw.buttonWidth(font, "Accept");
        int denyW = OsDraw.buttonWidth(font, "Deny");
        for (int i = scroll; i < list.size() && y + ROW <= bottom; i++) {
            CompoundTag request = (CompoundTag) list.get(i);
            if (OsDraw.inside(mx, my, a.x() + a.w() - acceptW - denyW - 6, y, acceptW, OsDraw.BUTTON_H)) {
                answer(request.getInt("id"), true);
                return;
            }
            if (OsDraw.inside(mx, my, a.x() + a.w() - denyW - 3, y, denyW, OsDraw.BUTTON_H)) {
                answer(request.getInt("id"), false);
                return;
            }
            y += ROW;
        }
    }

    private void answer(int id, boolean accepted) {
        CompoundTag args = new CompoundTag();
        args.putInt("id", id);
        args.putBoolean("ok", accepted);
        screen.send("req_answer", args);
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
    }

    /** Het eerste verzoek beantwoorden (voor de showcase en de screenshots). */
    void tourAnswer(boolean accepted) {
        List<Tag> list = requests();
        if (!list.isEmpty()) {
            answer(((CompoundTag) list.getFirst()).getInt("id"), accepted);
        }
    }
}
