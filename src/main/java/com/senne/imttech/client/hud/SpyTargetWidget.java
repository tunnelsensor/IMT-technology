package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.PURPLE;
import static com.senne.imttech.client.hud.HudStyle.argb;
import static com.senne.imttech.client.hud.HudStyle.smallText;

import com.senne.imttech.client.ImtClientState;
import com.senne.imttech.client.WorldProjection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/**
 * Markering op het doelwit van de spionagedrone: een paarse ruit met naam en afstand.
 * Die zie je door muren heen en van elke afstand (de drone stuurt de plek door).
 */
public final class SpyTargetWidget implements HudWidget {

    @Override
    public boolean visible(HudContext c) {
        return c.powered && ImtClientState.spyActive && ImtClientState.spyPos != null
                && ImtClientState.spyDimension == c.player.level().dimension();
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        Vec3 pos = ImtClientState.spyPos;
        WorldProjection.ScreenPoint point = c.projection.project(pos.add(0, 2.2, 0));
        if (point == null) {
            return;
        }
        int px = (int) point.x();
        int py = (int) point.y();
        int alpha = (int) (170 + 60 * Math.sin(c.now / 300.0));
        // Een klein ruitje (vier streepjes rond het midden).
        g.fill(px - 1, py - 5, px + 2, py - 4, argb(alpha, PURPLE));
        g.fill(px - 1, py + 4, px + 2, py + 5, argb(alpha, PURPLE));
        g.fill(px - 5, py - 1, px - 4, py + 2, argb(alpha, PURPLE));
        g.fill(px + 4, py - 1, px + 5, py + 2, argb(alpha, PURPLE));
        int distance = (int) Math.round(Math.sqrt(c.player.distanceToSqr(pos)));
        smallText(g, c.font, ImtClientState.spyTarget.getString() + "  " + distance + "m", px, py - 16, argb(230, PURPLE));
    }
}
