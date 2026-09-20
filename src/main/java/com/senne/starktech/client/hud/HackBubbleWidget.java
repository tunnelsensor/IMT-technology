package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.GREEN;
import static com.senne.starktech.client.hud.HudStyle.ORANGE;
import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.brackets;

import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.client.WorldProjection;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

/**
 * De onderschepte privéberichten van gehackte spelers: een ballonnetje boven hun hoofd.
 * Je ziet ze alleen zolang je die speler in beeld hebt (de server stuurt ze ook alleen dan).
 */
public final class HackBubbleWidget implements HudWidget {

    @Override
    public boolean visible(HudContext c) {
        return c.powered && !EdithClientState.hackBubbles.isEmpty() && c.mc.level != null;
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        EdithClientState.hackBubbles.removeIf(bubble -> c.now > bubble.endMillis());
        // Per speler stapelen we de regels boven elkaar.
        Map<Integer, Integer> lines = new HashMap<>();
        for (EdithClientState.HackBubble bubble : EdithClientState.hackBubbles) {
            if (!(c.mc.level.getEntity(bubble.entityId()) instanceof LivingEntity entity) || !entity.isAlive()) {
                continue;
            }
            int line = lines.merge(bubble.entityId(), 1, Integer::sum) - 1;
            WorldProjection.ScreenPoint point = c.projection.project(
                    entity.position().add(0, entity.getBbHeight() + 0.8, 0));
            if (point == null || !c.projection.isOnScreen(point, 0)) {
                continue;
            }
            String text = bubble.text().getString();
            int width = c.font.width(text);
            int x = (int) point.x() - width / 2;
            int y = (int) point.y() - 12 - line * 11;
            // Laatste seconde: langzaam wegvagen.
            long left = bubble.endMillis() - c.now;
            int alpha = (int) Math.min(255, Math.max(40, left / 4));
            int color = bubble.outgoing() ? ORANGE : GREEN;
            g.fill(x - 3, y - 2, x + width + 3, y + 9, argb(alpha * 3 / 5, 0x0A1520));
            brackets(g, x - 3, y - 2, x + width + 3, y + 9, 3, argb(alpha, color));
            g.drawString(c.font, text, x, y, argb(alpha, color), false);
        }
    }
}
