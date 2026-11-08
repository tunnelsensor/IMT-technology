package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.ORANGE;
import static com.senne.imttech.client.hud.HudStyle.RED;
import static com.senne.imttech.client.hud.HudStyle.WHITE;
import static com.senne.imttech.client.hud.HudStyle.argb;

import com.senne.imttech.client.WorldProjection;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.raven.RavenAlert;
import com.senne.imttech.raven.Watcher;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Het oogje (hoofdstuk 60, jouw antwoord 2: "een oogje met een pijl naar de bewaker dat volloopt, en boven
 * zijn hoofd een ? en dan een !", zoals de witte pijl in Hitman). Sinds 8 november zie je het ook <b>zonder bril</b>
 * (jouw opmerking: "je ziet dit enkel met je bril"): het tekent in een eigen laag, GuardLayer, en niet meer op de HUD
 * van de bril; en het is kleiner ("de iconen iets kleiner": het oog 11 bij 5 in plaats van 17 bij 7; het teken eerst anderhalf
 * keer zo groot als gewone tekst in plaats van twee keer, en na je tweede opmerking "de ? en ! kleiner" zo groot als
 * gewone tekst). Voor elke bewaker (een Raven-soldaat, en sinds stap 6
 * een robot of drone) die JOU in de gaten begint te krijgen, tekenen we een oog dat van links naar rechts volloopt met
 * zijn meter: wit zolang hij alleen maar kijkt, oranje als hij achterdochtig is, rood als hij je heeft. Staat hij op je
 * scherm, dan zweeft het oog boven zijn hoofd; staat hij erbuiten (achter je), dan staat het op het ovaal rond het
 * midden van je scherm aan de kant waar hij is, net als de kompaspijlen van de dreigingen (ThreatMarkerWidget).
 *
 * Boven elke bewaker die op zijn hoede is (ook om iets anders dan jou: lawaai, een lichaam) staat zijn teken: een geel
 * <b>?</b> (achterdochtig, zoekend) of een rood <b>!</b> (hij heeft iemand, of roept versterking), boven het oog als dat er
 * ook is. Dat tekenen we hier, in dezelfde projectie als het oog, en niet als tekst in de wereld: die kwam precies
 * achter het oog terecht.
 *
 * Meer niet: geen minimap en geen tekst "ze zoeken je" (jouw wens: alleen het bewakersgedeelte).
 */
public final class DetectionWidget implements HudWidget {
    /** Het ovaal, dezelfde maten als bij de dreigingen. */
    private static final int MAX_RX = 150;
    private static final int MAX_RY = 90;
    private static final int BOTTOM_ROOM = 64;
    private static final int SIDE_ROOM = 36;
    /** Verder dan dit tekenen we geen oog (zo ver ziet geen bewaker). */
    private static final double MAX_DISTANCE = 64;
    private static final int OUTLINE = argb(230, 0x0E1216);
    private static final int EMPTY = argb(150, 0x2E3A44);
    /** De halve breedte van elke rij van het oog (van boven naar beneden): 11 breed en 5 hoog. */
    private static final int[] HALVES = {2, 4, 5, 4, 2};
    /** Hoe groot het "?" of "!" is, tegenover gewone tekst (8 november, tweede keer "kleiner": gewone tekstgrootte). */
    private static final float MARK_SCALE = 1.0f;

    @Override
    public boolean visible(HudContext c) {
        return c.powered && !c.menuOpen && c.mc.level != null;
    }

    @Override
    public void render(HudContext c) {
        int me = c.player.getId();
        for (Entity entity : c.mc.level.entitiesForRendering()) {
            if (!(entity instanceof Watcher watcher) || !entity.isAlive()) {
                continue;
            }
            boolean watchingMe = watcher.suspectId() == me && watcher.suspicion() > 0;
            if (!watchingMe && !watcher.alertState().alerted()) {
                continue;
            }
            if (entity.distanceToSqr(c.player) > MAX_DISTANCE * MAX_DISTANCE) {
                continue;
            }
            draw(c, entity, watcher, watchingMe);
        }
    }

    private void draw(HudContext c, Entity guard, Watcher watcher, boolean watchingMe) {
        float fraction = Mth.clamp(watcher.suspicion() / 100.0f, 0.0f, 1.0f);
        RavenAlert alert = watcher.alertState();
        int colour = alert == RavenAlert.COMBAT || alert == RavenAlert.CALLING || fraction >= 1.0f ? RED
                : fraction * 100 >= ImtConfig.Guards.SUSPICIOUS_AT || alert.alerted() ? ORANGE : WHITE;
        // Hoe voller, hoe duidelijker: een oog dat net begint is nog half doorzichtig.
        int alpha = 140 + Math.round(115 * fraction);
        Vec3 at = c.projection.drawnPosition(guard);
        Vec3 top = new Vec3(at.x, at.y + guard.getBbHeight() + 0.9, at.z);
        WorldProjection.ScreenPoint point = c.projection.project(top);
        if (point != null && c.projection.isOnScreen(point, 0)) {
            int x = Math.round(point.x());
            int y = Math.round(point.y());
            if (watchingMe) {
                eye(c.g, x, y, fraction, argb(alpha, colour));
            }
            if (alert.alerted()) {
                mark(c, x, watchingMe ? y - HALVES.length / 2 - 3 : y + 4, alert);
            }
            return;
        }
        if (!watchingMe) {
            return;
        }
        Player player = c.player;
        double dx = at.x - player.getX();
        double dz = at.z - player.getZ();
        if (dx * dx + dz * dz < 0.01) {
            return;
        }
        float toGuard = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float relative = Mth.wrapDegrees(toGuard - player.getYRot()) * Mth.DEG_TO_RAD;
        int rx = Math.max(40, Math.min(MAX_RX, c.width / 2 - SIDE_ROOM));
        int ry = Math.max(30, Math.min(MAX_RY, c.height / 2 - BOTTOM_ROOM));
        int x = c.width / 2 + (int) Math.round(Math.sin(relative) * rx);
        int y = c.height / 2 - (int) Math.round(Math.cos(relative) * ry);
        eye(c.g, x, y, fraction, argb(alpha, colour));
    }

    /** Het teken boven een bewaker: een geel "?" of een rood "!", {@link #MARK_SCALE} keer zo groot als gewone tekst, met een schaduw. */
    private static void mark(HudContext c, int cx, int bottom, RavenAlert alert) {
        boolean danger = alert == RavenAlert.COMBAT || alert == RavenAlert.CALLING;
        String text = danger ? "!" : "?";
        int colour = danger ? RED : 0xF0C040;
        // Een lichte pulse zodat het opvalt.
        float bob = (float) Math.sin(c.now / 160.0) * 0.5f;
        var pose = c.g.pose();
        pose.pushPose();
        pose.translate(cx, bottom - c.font.lineHeight * MARK_SCALE + bob, 0);
        pose.scale(MARK_SCALE, MARK_SCALE, 1.0f);
        int width = c.font.width(text);
        c.g.drawString(c.font, text, -width / 2 + 1, 1, argb(200, 0x101418), false);
        c.g.drawString(c.font, text, -width / 2, 0, argb(255, colour), false);
        pose.popPose();
    }

    /**
     * Een oog van 11 bij 5 pixels rond (cx, cy) met een donkere rand en een pupil, dat van links naar rechts volloopt
     * met {@code fraction}.
     */
    static void eye(GuiGraphics g, int cx, int cy, float fraction, int colour) {
        int top = cy - HALVES.length / 2;
        int edge = HALVES[0] + 1;
        // De rand: elke rij een pixel breder, en een rij extra boven en onder.
        g.fill(cx - edge, top - 1, cx + edge + 1, top, OUTLINE);
        g.fill(cx - edge, top + HALVES.length, cx + edge + 1, top + HALVES.length + 1, OUTLINE);
        for (int row = 0; row < HALVES.length; row++) {
            int half = HALVES[row] + 1;
            int y = top + row;
            g.fill(cx - half, y, cx + half + 1, y + 1, OUTLINE);
        }
        // Het wit van het oog: leeg (grijs) met het gevulde deel erover, van links naar rechts.
        for (int row = 0; row < HALVES.length; row++) {
            int half = HALVES[row];
            int y = top + row;
            g.fill(cx - half, y, cx + half + 1, y + 1, EMPTY);
            int width = 2 * half + 1;
            int filled = Math.round(width * fraction);
            if (filled > 0) {
                g.fill(cx - half, y, cx - half + filled, y + 1, colour);
            }
        }
        // De pupil.
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, OUTLINE);
    }
}
