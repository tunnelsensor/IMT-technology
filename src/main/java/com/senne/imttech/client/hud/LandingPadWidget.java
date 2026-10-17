package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.GREEN;
import static com.senne.imttech.client.hud.HudStyle.WHITE;
import static com.senne.imttech.client.hud.HudStyle.argb;
import static com.senne.imttech.client.hud.HudStyle.smallText;

import com.mojang.math.Axis;
import com.senne.imttech.client.SuitFlight;
import com.senne.imttech.client.WorldProjection;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.suit.LandingPadBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Tijdens het vliegen met het harnas: de weg naar huis. Een groen ruitje op je dichtstbijzijnde
 * landingsplatform, met de afstand. Ben je dichtbij, dan tekent I.M.T ook het landingsvak
 * (5 x 5 blokken) als een gestippeld vierkant in de wereld.
 */
public final class LandingPadWidget implements HudWidget {
    /** Binnen deze afstand (blokken) tekenen we het landingsvak. */
    private static final double ZONE_DISTANCE = 80;

    @Override
    public boolean visible(HudContext c) {
        return c.powered && !c.menuOpen && SuitFlight.isEngaged() && nearest(c) != null;
    }

    private static @Nullable GlobalPos nearest(HudContext c) {
        return c.player.getData(ModAttachments.LANDING_PADS).nearest(c.player.level().dimension(), c.player.position());
    }

    @Override
    public void render(HudContext c) {
        GlobalPos pad = nearest(c);
        if (pad == null) {
            return;
        }
        GuiGraphics g = c.g;
        Vec3 top = Vec3.atBottomCenterOf(pad.pos()).add(0, 1.02, 0);
        double distance = Math.sqrt(c.cameraPosition().distanceToSqr(top));
        int pulse = (int) (170 + 70 * Math.sin(c.now / 220.0));

        if (distance < ZONE_DISTANCE) {
            double r = LandingPadBlockEntity.ZONE;
            WorldProjection.ScreenPoint[] corners = {
                    c.projection.project(top.add(-r, 0, -r)), c.projection.project(top.add(r, 0, -r)),
                    c.projection.project(top.add(r, 0, r)), c.projection.project(top.add(-r, 0, r))};
            for (int i = 0; i < 4; i++) {
                dotted(g, corners[i], corners[(i + 1) % 4], argb(pulse, GREEN));
            }
        }

        WorldProjection.ScreenPoint p = c.projection.project(top.add(0, 0.6, 0));
        if (p == null || !c.projection.isOnScreen(p, 0)) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(p.x(), p.y(), 0.0f);
        g.pose().mulPose(Axis.ZP.rotation((float) (Math.PI / 4)));
        g.fill(-4, -4, 4, 4, argb(pulse, GREEN));
        g.fill(-2, -2, 2, 2, argb(160, 0x000000));
        g.pose().popPose();
        int x = (int) p.x();
        int y = (int) p.y();
        smallText(g, c.font, Component.translatable("hud.imttech.landing_pad").getString(), x, y - 14, argb(255, GREEN));
        smallText(g, c.font, Math.round(distance) + "m", x, y + 7, argb(220, WHITE));
    }

    /** Een stippellijn van a naar b (niet als een van beide achter je ligt). */
    private static void dotted(GuiGraphics g, @Nullable WorldProjection.ScreenPoint a,
                               @Nullable WorldProjection.ScreenPoint b, int color) {
        if (a == null || b == null) {
            return;
        }
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        int steps = (int) Math.min(300, Math.hypot(dx, dy) / 4);
        for (int i = 0; i <= steps; i++) {
            int x = (int) (a.x() + dx * i / Math.max(1, steps));
            int y = (int) (a.y() + dy * i / Math.max(1, steps));
            g.fill(x, y, x + 2, y + 2, color);
        }
    }
}
