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
 * Het oogje op je bril (hoofdstuk 60, jouw antwoord 2: "een oogje met een pijl naar de bewaker dat volloopt", zoals de
 * witte pijl in Hitman). Voor elke bewaker (een Raven-soldaat, en sinds stap 6 een robot of drone) die JOU in de gaten
 * begint te krijgen, tekenen we een oog dat van links naar rechts volloopt met zijn meter: wit zolang hij alleen maar
 * kijkt, oranje als hij achterdochtig is, rood als hij je heeft. Staat hij op je scherm, dan zweeft het oog boven zijn
 * hoofd; staat hij erbuiten (achter je), dan staat het op het ovaal rond het midden van je scherm aan de kant waar hij
 * is, net als de kompaspijlen van de dreigingen (ThreatMarkerWidget).
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
    /** De halve breedte van elke rij van het oog (van boven naar beneden). */
    private static final int[] HALVES = {2, 5, 7, 8, 7, 5, 2};

    @Override
    public boolean visible(HudContext c) {
        return c.powered && !c.menuOpen && c.mc.level != null;
    }

    @Override
    public void render(HudContext c) {
        int me = c.player.getId();
        for (Entity entity : c.mc.level.entitiesForRendering()) {
            if (!(entity instanceof Watcher watcher) || !entity.isAlive() || watcher.suspectId() != me || watcher.suspicion() <= 0) {
                continue;
            }
            if (entity.distanceToSqr(c.player) > MAX_DISTANCE * MAX_DISTANCE) {
                continue;
            }
            draw(c, entity, watcher);
        }
    }

    private void draw(HudContext c, Entity guard, Watcher watcher) {
        float fraction = Mth.clamp(watcher.suspicion() / 100.0f, 0.0f, 1.0f);
        RavenAlert alert = watcher.alertState();
        int colour = alert == RavenAlert.COMBAT || alert == RavenAlert.CALLING || fraction >= 1.0f ? RED
                : fraction * 100 >= ImtConfig.Guards.SUSPICIOUS_AT || alert.alerted() ? ORANGE : WHITE;
        // Hoe voller, hoe duidelijker: een oog dat net begint is nog half doorzichtig.
        int alpha = 140 + Math.round(115 * fraction);
        Vec3 at = c.projection.drawnPosition(guard);
        Vec3 top = new Vec3(at.x, at.y + guard.getBbHeight() + 1.15, at.z);
        WorldProjection.ScreenPoint point = c.projection.project(top);
        if (point != null && c.projection.isOnScreen(point, 0)) {
            eye(c.g, Math.round(point.x()), Math.round(point.y()), fraction, argb(alpha, colour));
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

    /**
     * Een oog van 17 bij 7 pixels rond (cx, cy) met een donkere rand en een pupil, dat van links naar rechts volloopt
     * met {@code fraction}.
     */
    static void eye(GuiGraphics g, int cx, int cy, float fraction, int colour) {
        // De rand: elke rij een pixel breder, en een rij extra boven en onder.
        g.fill(cx - 3, cy - 4, cx + 4, cy - 3, OUTLINE);
        g.fill(cx - 3, cy + 4, cx + 4, cy + 5, OUTLINE);
        for (int row = 0; row < HALVES.length; row++) {
            int half = HALVES[row] + 1;
            int y = cy - 3 + row;
            g.fill(cx - half, y, cx + half + 1, y + 1, OUTLINE);
        }
        // Het wit van het oog: leeg (grijs) met het gevulde deel erover, van links naar rechts.
        for (int row = 0; row < HALVES.length; row++) {
            int half = HALVES[row];
            int y = cy - 3 + row;
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
