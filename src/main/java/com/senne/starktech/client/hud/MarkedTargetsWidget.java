package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.ORANGE;
import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.brackets;
import static com.senne.starktech.client.hud.HudStyle.smallText;

import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.client.WorldProjection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** Oranje beugels rond gemarkeerde doelwitten (ook door muren), genummerd op volgorde. */
public final class MarkedTargetsWidget implements HudWidget {

    @Override
    public boolean visible(HudContext c) {
        return c.powered && c.mc.level != null && !EdithClientState.markedIds.isEmpty();
    }

    @Override
    public void render(HudContext c) {
        int number = 0;
        for (int id : EdithClientState.markedIds) {
            number++;
            if (!(c.mc.level.getEntity(id) instanceof LivingEntity entity) || !entity.isAlive()) {
                continue;
            }
            WorldProjection.ScreenRect rect = c.projection.entityBox(entity);
            if (rect == null) {
                continue;
            }
            int pad = 5;
            int x0 = (int) rect.minX() - pad;
            int y0 = (int) rect.minY() - pad;
            int x1 = (int) rect.maxX() + pad;
            int y1 = (int) rect.maxY() + pad;
            brackets(c.g, x0, y0, x1, y1, 4, argb(240, ORANGE));
            smallText(c.g, c.font, Component.translatable("hud.starktech.marked_label", number).getString(),
                    (x0 + x1) / 2, y0 - 8, argb(240, ORANGE));
        }
    }
}
