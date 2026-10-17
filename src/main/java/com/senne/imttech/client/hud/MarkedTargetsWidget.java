package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.ORANGE;
import static com.senne.imttech.client.hud.HudStyle.argb;
import static com.senne.imttech.client.hud.HudStyle.brackets;
import static com.senne.imttech.client.hud.HudStyle.smallText;

import com.senne.imttech.client.ImtClientState;
import com.senne.imttech.client.WorldProjection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** Oranje beugels rond gemarkeerde doelwitten (ook door muren), genummerd op volgorde. */
public final class MarkedTargetsWidget implements HudWidget {

    @Override
    public boolean visible(HudContext c) {
        return c.powered && c.mc.level != null && !ImtClientState.markedIds.isEmpty();
    }

    @Override
    public void render(HudContext c) {
        int number = 0;
        for (int id : ImtClientState.markedIds) {
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
            smallText(c.g, c.font, Component.translatable("hud.imttech.marked_label", number).getString(),
                    (x0 + x1) / 2, y0 - 8, argb(240, ORANGE));
        }
    }
}
