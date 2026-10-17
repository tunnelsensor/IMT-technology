package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.ORANGE;
import static com.senne.imttech.client.hud.HudStyle.RED;
import static com.senne.imttech.client.hud.HudStyle.argb;
import static com.senne.imttech.client.hud.HudStyle.brackets;

import com.senne.imttech.client.ImtClientState;
import com.senne.imttech.client.WorldProjection;
import com.senne.imttech.imt.ImtData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Rood richtkruis en aftelling voor een satelliet-aanval die aan het opladen is. */
public final class SatelliteStrikeWidget implements HudWidget {

    @Override
    public boolean visible(HudContext c) {
        Vec3 target = ImtClientState.strikeTarget;
        return c.powered && target != null && c.now <= ImtClientState.strikeAtMillis + 500;
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        Vec3 target = ImtClientState.strikeTarget;
        float secondsLeft = Math.max(0, (ImtClientState.strikeAtMillis - c.now) / 1000.0f);
        int pulse = (int) (170 + 85 * Math.sin(c.now / 90.0));
        Component countdown = Component.translatable("hud.imttech.impact", String.format("%.1f", secondsLeft));

        WorldProjection.ScreenPoint p = c.projection.project(target);
        if (p != null && c.projection.isOnScreen(p, 0)) {
            int x = (int) p.x();
            int y = (int) p.y();
            int size = 10 + (int) (6 * Math.sin(c.now / 120.0));
            brackets(g, x - size, y - size, x + size, y + size, 5, argb(pulse, RED));
            g.fill(x - 1, y - 1, x + 1, y + 1, argb(255, RED));
            g.drawCenteredString(c.font, countdown, x, y + size + 4, argb(255, RED));
        }
        // Ook onder het vizier, voor als het doel niet in beeld is (niet als het G-menu open is).
        if (!c.menuOpen) {
            g.drawCenteredString(c.font, countdown, c.width / 2, c.height / 2 + 24, argb(pulse, RED));
            // In welke stand de satelliet staat (precisie, bombardement of schuivende laser).
            g.drawCenteredString(c.font, Component.translatable(ImtData.getSatelliteMode(c.glasses).translationKey()),
                    c.width / 2, c.height / 2 + 34, argb(pulse, ORANGE));
        }
    }
}
