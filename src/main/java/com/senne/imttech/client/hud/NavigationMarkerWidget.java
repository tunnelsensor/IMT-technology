package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.WHITE;
import static com.senne.imttech.client.hud.HudStyle.argb;
import static com.senne.imttech.client.hud.HudStyle.smallText;

import com.mojang.math.Axis;
import com.senne.imttech.client.WorldProjection;
import net.minecraft.client.gui.GuiGraphics;

/** Ruitje met naam en afstand op de bestemming, daar waar hij in de wereld ligt. */
public final class NavigationMarkerWidget implements HudWidget {

    @Override
    public boolean visible(HudContext c) {
        NavTarget target = NavTarget.of(c.player);
        return c.powered && target != null && target.sameDimension();
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        NavTarget target = NavTarget.of(c.player);
        WorldProjection.ScreenPoint p = c.projection.project(target.pos().add(0, 1.0, 0));
        if (p == null || !c.projection.isOnScreen(p, 0)) {
            return;
        }
        int x = (int) p.x();
        int y = (int) p.y();
        int pulse = (int) (200 + 55 * Math.sin(c.now / 250.0));
        g.pose().pushPose();
        g.pose().translate(p.x(), p.y(), 0.0f);
        g.pose().mulPose(Axis.ZP.rotation((float) (Math.PI / 4)));
        g.fill(-4, -4, 4, 4, argb(pulse, target.color()));
        g.fill(-2, -2, 2, 2, argb(160, 0x000000));
        g.pose().popPose();
        int distance = (int) Math.round(Math.sqrt(c.cameraPosition().distanceToSqr(target.pos())));
        smallText(g, c.font, target.name().getString(), x, y - 14, argb(255, target.color()));
        smallText(g, c.font, distance + "m", x, y + 7, argb(220, WHITE));
    }
}
