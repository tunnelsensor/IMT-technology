package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.CYAN;
import static com.senne.starktech.client.hud.HudStyle.GREEN;
import static com.senne.starktech.client.hud.HudStyle.PURPLE;
import static com.senne.starktech.client.hud.HudStyle.RED;
import static com.senne.starktech.client.hud.HudStyle.WHITE;
import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.smallText;

import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.registry.ModAttachments;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Het kompas bovenaan in het midden, met je waypoints, pings, het doelwit van de
 * spionagedrone en je bestemming erop.
 */
public final class CompassWidget implements HudWidget {
    private static final int WIDTH = 150;
    private static final int TOP = 8;
    /** Hoeveel pixels één graad op het kompas is. */
    private static final float PIXELS_PER_DEGREE = 1.5f;
    private static final String[] LABELS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    @Override
    public boolean visible(HudContext c) {
        return c.powered && !c.menuOpen;
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        Font font = c.font;
        Player player = c.player;
        int cx = c.width / 2;
        // In Minecraft is yaw 0 = zuid. Wij willen 0 = noord (zoals een echt kompas).
        float bearing = Mth.positiveModulo(player.getYRot() + 180.0f, 360.0f);

        g.fill(cx - WIDTH / 2, TOP + 10, cx + WIDTH / 2, TOP + 11, argb(120, CYAN));
        for (int deg = 0; deg < 360; deg += 15) {
            float diff = Mth.wrapDegrees(deg - bearing);
            float px = cx + diff * PIXELS_PER_DEGREE;
            if (Math.abs(diff) * PIXELS_PER_DEGREE > WIDTH / 2f) {
                continue;
            }
            int alpha = (int) (255 * (1.0f - Math.abs(diff) * PIXELS_PER_DEGREE / (WIDTH / 2f)));
            if (deg % 45 == 0) {
                String label = LABELS[deg / 45];
                int color = label.equals("N") ? RED : WHITE;
                g.drawCenteredString(font, label, (int) px, TOP, argb(alpha, color));
                g.fill((int) px, TOP + 8, (int) px + 1, TOP + 12, argb(alpha, CYAN));
            } else {
                g.fill((int) px, TOP + 9, (int) px + 1, TOP + 12, argb(alpha / 2, CYAN));
            }
        }
        // Andere waypoints in deze wereld: kleine streepjes in hun eigen kleur.
        Vec3 from = c.cameraPosition();
        EdithWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        for (Waypoint waypoint : data.waypoints()) {
            if (waypoint.dimension() == player.level().dimension() && waypoint != data.activeWaypoint()) {
                marker(c, cx, bearing, bearingTo(from, Vec3.atCenterOf(waypoint.pos())), waypoint.color(), null, false);
            }
        }
        // Pings: groen.
        for (EdithClientState.Ping ping : EdithClientState.pings) {
            marker(c, cx, bearing, bearingTo(from, ping.pos()), GREEN, null, true);
        }
        // Het doelwit van de spionagedrone: paars.
        if (EdithClientState.spyActive && EdithClientState.spyPos != null
                && EdithClientState.spyDimension == player.level().dimension()) {
            marker(c, cx, bearing, bearingTo(from, EdithClientState.spyPos), PURPLE, EdithClientState.spyTarget, true);
        }
        // De bestemming: groot, met pijltje aan de rand als hij buiten het kompas valt.
        NavTarget target = NavTarget.of(player);
        if (target != null && target.sameDimension()) {
            marker(c, cx, bearing, bearingTo(from, target.pos()), target.color(), target.name(), true);
        }

        // Middenstreepje en graden
        g.fill(cx - 1, TOP + 11, cx + 1, TOP + 16, argb(255, CYAN));
        g.drawCenteredString(font, Math.round(bearing) % 360 + "°", cx, TOP + 17, argb(200, WHITE));
    }

    /** Een markering op het kompas. Valt hij erbuiten, dan een pijltje aan de rand (alleen als important). */
    private void marker(HudContext c, int cx, float bearing, float targetBearing,
                        int color, @Nullable Component label, boolean important) {
        GuiGraphics g = c.g;
        float diff = Mth.wrapDegrees(targetBearing - bearing);
        float px = cx + diff * PIXELS_PER_DEGREE;
        float half = WIDTH / 2f;
        if (Math.abs(px - cx) <= half) {
            int x = (int) px;
            if (important) {
                g.fill(x - 2, TOP + 9, x + 3, TOP + 14, argb(255, color));
                if (label != null) {
                    smallText(g, c.font, label.getString(), x, TOP - 7, argb(230, color));
                }
            } else {
                g.fill(x, TOP + 9, x + 1, TOP + 14, argb(200, color));
            }
        } else if (important) {
            // Pijltje aan de linker- of rechterkant van het kompas.
            int x = diff < 0 ? (int) (cx - half - 6) : (int) (cx + half + 2);
            g.drawString(c.font, diff < 0 ? "<" : ">", x, TOP + 6, argb(255, color), true);
        }
    }

    /** Kompasrichting (0 = noord, 90 = oost) van een punt naar een ander punt. */
    private static float bearingTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        return Mth.positiveModulo(yaw + 180.0f, 360.0f);
    }
}
