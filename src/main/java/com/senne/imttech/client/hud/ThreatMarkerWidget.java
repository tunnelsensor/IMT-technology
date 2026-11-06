package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.RED;
import static com.senne.imttech.client.hud.HudStyle.argb;
import static com.senne.imttech.client.hud.HudStyle.smallText;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.client.ImtClientState;
import com.senne.imttech.client.WorldProjection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Wat jou aanvalt, in beeld gebracht.
 *
 * Staat de dreiging op je scherm, dan krijgt hij een rood pijltje boven zijn hoofd met de
 * afstand erbij. Zit hij achter je of buiten beeld, dan verschijnt er een pijl op een ovaal
 * rond het midden van je scherm die de kant op wijst waar hij vandaan komt - een kompas dus,
 * zodat je meteen weet welke kant je op moet kijken.
 *
 * Sinds hoofdstuk 54 (jouw showcase-notitie van 6 november): echte pijlpunten (een driehoek
 * met een donker randje, gedraaid in de richting), en het ovaal blijft onderaan boven je
 * hartjes en je werkbalk, zodat een dreiging achter je niet onder de hotbar verdwijnt.
 */
public final class ThreatMarkerWidget implements HudWidget {
    /** Meer dan zoveel dreigingen tekenen we niet; anders staat je scherm vol pijltjes. */
    private static final int MAX_DRAWN = 16;
    /** Het ovaal van de kompaspijlen: hooguit zo ver van het midden (pixels)... */
    private static final int MAX_RX = 150;
    private static final int MAX_RY = 90;
    /** ...en zoveel ruimte houden we vrij onderaan (hartjes, honger, werkbalk) en opzij. */
    private static final int BOTTOM_ROOM = 64;
    private static final int SIDE_ROOM = 36;
    private static final int OUTLINE = argb(210, 0x1A0000);

    @Override
    public boolean visible(HudContext c) {
        return c.powered && !ImtClientState.threats.isEmpty();
    }

    @Override
    public void render(HudContext c) {
        int drawn = 0;
        for (Entity threat : ImtClientState.threats) {
            if (drawn >= MAX_DRAWN) {
                break;
            }
            drawn++;
            Vec3 at = c.projection.drawnPosition(threat);
            Vec3 top = new Vec3(at.x, at.y + threat.getBbHeight() + 0.4, at.z);
            WorldProjection.ScreenPoint point = c.projection.project(top);
            if (point != null && c.projection.isOnScreen(point, 0)) {
                markOnScreen(c, threat, point);
            } else {
                markOffScreen(c, threat, at);
            }
        }
    }

    /** Een pijltje boven het doelwit dat naar hem wijst, met hoe ver hij van je af is erboven. */
    private void markOnScreen(HudContext c, Entity threat, WorldProjection.ScreenPoint point) {
        int x = (int) point.x();
        int y = (int) point.y();
        arrowAt(c.g, x, y - 9, (float) Math.PI, argb(235, RED));
        smallText(c.g, c.font, Math.round(c.player.distanceTo(threat)) + "m", x, y - 22, argb(220, RED));
    }

    /**
     * Een kompaspijl op het ovaal rond het midden van je scherm: hij wijst naar de dreiging die je
     * niet ziet. We rekenen de hoek uit tussen de kant waar jij naar kijkt en de kant waar de
     * dreiging vandaan komt, zetten die hoek op het ovaal en tekenen daar een pijl die naar
     * buiten wijst; de afstand staat aan de binnenkant van de pijl.
     */
    private void markOffScreen(HudContext c, Entity threat, Vec3 threatPos) {
        Player player = c.player;
        double dx = threatPos.x - player.getX();
        double dz = threatPos.z - player.getZ();
        if (dx * dx + dz * dz < 0.01) {
            return;
        }
        // Hoek van de dreiging ten opzichte van waar je naartoe kijkt (0 = recht vooruit).
        float toThreat = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float relative = Mth.wrapDegrees(toThreat - player.getYRot()) * Mth.DEG_TO_RAD;

        int centerX = c.width / 2;
        int centerY = c.height / 2;
        int rx = Math.max(40, Math.min(MAX_RX, c.width / 2 - SIDE_ROOM));
        int ry = Math.max(30, Math.min(MAX_RY, c.height / 2 - BOTTOM_ROOM));
        // Rechts van je = positieve x op het scherm, vóór je = omhoog.
        int x = centerX + (int) Math.round(Math.sin(relative) * rx);
        int y = centerY - (int) Math.round(Math.cos(relative) * ry);
        arrowAt(c.g, x, y, relative, argb(235, RED));
        int tx = centerX + (int) Math.round(Math.sin(relative) * (rx - 17));
        int ty = centerY - (int) Math.round(Math.cos(relative) * (ry - 17));
        smallText(c.g, c.font, Math.round(player.distanceTo(threat)) + "m", tx, ty - 3, argb(210, RED));
    }

    /**
     * Een pijlpunt op (x, y) die in richting {@code angle} wijst (0 = omhoog, met de klok mee): een
     * driehoek met een donker randje en een inkeping onderaan, gedraaid met de PoseStack.
     */
    private static void arrowAt(GuiGraphics g, int x, int y, float angle, int colour) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.mulPose(Axis.ZP.rotation(angle));
        // De punt op (0, -7), de voet op y = +2. Eerst het randje (één pixel breder), dan de pijl zelf.
        for (int row = 0; row < 11; row++) {
            int half = row * 6 / 10 + 1;
            g.fill(-half - 1, -8 + row, half + 2, -7 + row, OUTLINE);
        }
        for (int row = 0; row < 9; row++) {
            int half = row * 6 / 10;
            g.fill(-half, -7 + row, half + 1, -6 + row, colour);
        }
        g.fill(-2, 0, 3, 2, OUTLINE);
        pose.popPose();
    }
}
