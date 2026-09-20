package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.GREEN;
import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.smallText;

import com.senne.starktech.client.RelayLabels;
import com.senne.starktech.client.WorldProjection;
import com.senne.starktech.redstone.Relay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/**
 * Naamplaatjes bij je redstone-relais in de buurt: groen als hij aan staat, grijs als hij
 * uit staat. Zo zie je meteen welk kastje "voordeur" is.
 */
public final class RelayLabelWidget implements HudWidget {
    /** De kleur van een relais dat uit staat. */
    private static final int OFF = 0x9FC9D6;

    @Override
    public boolean visible(HudContext c) {
        return c.powered;
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        for (Relay relay : RelayLabels.nearby()) {
            WorldProjection.ScreenPoint point = c.projection.project(Vec3.atCenterOf(relay.pos()).add(0, 0.5, 0));
            if (point == null || !c.projection.isOnScreen(point, 0)) {
                continue;
            }
            int color = RelayLabels.isOn(relay) ? GREEN : OFF;
            int px = (int) point.x();
            int py = (int) point.y();
            g.fill(px - 2, py - 2, px + 3, py + 3, argb(225, color));
            smallText(g, c.font, relay.name(), px, py - 13, argb(235, color));
            smallText(g, c.font, relay.group(), px, py + 5, argb(150, color));
        }
    }
}
