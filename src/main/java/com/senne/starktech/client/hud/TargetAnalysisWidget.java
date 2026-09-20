package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.CYAN;
import static com.senne.starktech.client.hud.HudStyle.GREEN;
import static com.senne.starktech.client.hud.HudStyle.RED;
import static com.senne.starktech.client.hud.HudStyle.WHITE;
import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.bar;
import static com.senne.starktech.client.hud.HudStyle.brackets;

import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.client.WorldProjection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Doelwit-analyse: beugels om het wezen waar je naar kijkt, plus een info-paneel rechts. */
public final class TargetAnalysisWidget implements HudWidget {

    @Override
    public boolean visible(HudContext c) {
        LivingEntity target = EdithClientState.target;
        return c.powered && target != null && target.isAlive();
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        LivingEntity target = EdithClientState.target;
        ThreatLevel threat = ThreatLevel.of(target);

        // Beugels rond het doelwit in de wereld.
        WorldProjection.ScreenRect rect = c.projection.entityBox(target);
        if (rect != null) {
            int pad = 3;
            brackets(g, (int) rect.minX() - pad, (int) rect.minY() - pad, (int) rect.maxX() + pad,
                    (int) rect.maxY() + pad, 5, argb(230, threat.color));
        }

        // Info-paneel rechts (niet als het G-menu open is: dan zit het in de weg)
        if (c.menuOpen) {
            return;
        }
        int panelW = 128;
        int x = c.width - panelW - 10;
        int y = c.height / 2 - 50;
        g.fill(x - 4, y - 4, x + panelW, y + 70, argb(60, 0x001018));
        brackets(g, x - 4, y - 4, x + panelW, y + 70, 6, argb(160, CYAN));

        g.drawString(c.font, Component.translatable("hud.starktech.target"), x, y, argb(255, CYAN), true);
        y += 11;
        g.drawString(c.font, target.getDisplayName(), x, y, argb(255, WHITE), true);
        y += 11;

        float health = target.getHealth();
        float maxHealth = Math.max(1.0f, target.getMaxHealth());
        bar(g, x, y + 2, 70, 4, health / maxHealth, health / maxHealth < 0.3f ? RED : GREEN);
        g.drawString(c.font, String.format("%.0f/%.0f", health, maxHealth), x + 75, y, argb(220, WHITE), true);
        y += 11;

        g.drawString(c.font, Component.translatable("hud.starktech.armor", target.getArmorValue()), x, y, argb(220, WHITE), true);
        y += 10;
        g.drawString(c.font, Component.translatable("hud.starktech.distance", String.format("%.1f", c.player.distanceTo(target))),
                x, y, argb(220, WHITE), true);
        y += 10;
        g.drawString(c.font, Component.translatable("hud.starktech.threat_level", threat.label()), x, y, argb(255, threat.color), true);
        y += 10;

        ItemStack held = target.getMainHandItem();
        if (!held.isEmpty()) {
            g.drawString(c.font, Component.translatable("hud.starktech.holding", held.getHoverName()), x, y, argb(200, WHITE), true);
        }
    }
}
