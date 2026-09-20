package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.smallText;

import com.mojang.math.Axis;
import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.client.WorldProjection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/** Gekleurde ruitjes op de ertsen die de scan heeft gevonden. */
public final class OreMarkerWidget implements HudWidget {
    /** Bij zoveel ertsen zetten we de naam en de afstand erbij; de rest blijft een ruitje. */
    private static final int MAX_LABELS = 6;

    @Override
    public boolean visible(HudContext c) {
        return c.powered && EdithClientState.hasOreScan();
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        // Laatste seconde: langzaam vervagen.
        long left = EdithClientState.oreScanEndMillis - c.now;
        int baseAlpha = left < 1000 ? (int) (255 * left / 1000.0) : 255;
        int labelled = 0;
        for (EdithClientState.OreMarker marker : EdithClientState.oreMarkers) {
            WorldProjection.ScreenPoint p = c.projection.project(Vec3.atCenterOf(marker.pos()));
            if (p == null || !c.projection.isOnScreen(p, 0)) {
                continue;
            }
            int color = marker.type().color();
            g.pose().pushPose();
            g.pose().translate(p.x(), p.y(), 0.0f);
            g.pose().mulPose(Axis.ZP.rotation((float) (Math.PI / 4)));
            g.fill(-3, -3, 3, 3, argb(baseAlpha * 3 / 4, color));
            g.fill(-2, -2, 2, 2, argb(baseAlpha / 3, 0x000000));
            g.pose().popPose();

            // Naam + afstand bij de dichtstbijzijnde paar ertsen.
            if (labelled < MAX_LABELS) {
                double dist = Math.sqrt(marker.pos().distToCenterSqr(c.player.position()));
                String label = marker.type().displayName().getString() + " " + Math.round(dist) + "m";
                smallText(g, c.font, label, (int) p.x(), (int) p.y() + 5, argb(baseAlpha, color));
                labelled++;
            }
        }
    }
}
