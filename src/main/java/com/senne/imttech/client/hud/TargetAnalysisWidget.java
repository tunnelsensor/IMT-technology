package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.GREEN;
import static com.senne.imttech.client.hud.HudStyle.RED;
import static com.senne.imttech.client.hud.HudStyle.WHITE;
import static com.senne.imttech.client.hud.HudStyle.argb;
import static com.senne.imttech.client.hud.HudStyle.bar;
import static com.senne.imttech.client.hud.HudStyle.brackets;

import com.senne.imttech.client.ImtClientState;
import com.senne.imttech.client.WorldProjection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Doelwit-analyse: beugels om het wezen waar je naar kijkt, en een lijntje naar een klein
 * kaartje ernaast met wat de bril over hem weet (zoals in de films). Het kaartje hangt vast aan
 * het wezen en loopt mee als het beweegt.
 *
 * <pre>
 *   ┌─      ─┐  ___ ZOMBIE
 *              /     ▮▮▮▮▮▮▯▯ 14/20
 *     (wezen)        ARMOR 2 · 12.4 M
 *                    THREAT: MEDIUM
 *   └─      ─┘       HOLDING: IRON SWORD
 * </pre>
 *
 * Past het kaartje rechts niet meer op je scherm, dan komt het links. Een nieuw doelwit: eerst
 * groeit het lijntje, dan verschijnt het kaartje.
 */
public final class TargetAnalysisWidget implements HudWidget {
    /** Hoe lang het lijntje groeit en het kaartje verschijnt (ms). */
    private static final long LINE_MILLIS = 140;
    private static final long CARD_MILLIS = 320;
    private static final float SMALL = 0.75f;

    /** Welk wezen we het laatst analyseerden, en sinds wanneer (voor het verschijnen). */
    private int lastTargetId = -1;
    private long acquiredMillis;

    @Override
    public boolean visible(HudContext c) {
        LivingEntity target = ImtClientState.target;
        return c.powered && target != null && target.isAlive();
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        LivingEntity target = ImtClientState.target;
        ThreatLevel threat = ThreatLevel.of(target);
        if (target.getId() != lastTargetId) {
            lastTargetId = target.getId();
            acquiredMillis = c.now;
        }
        long age = c.now - acquiredMillis;

        // Beugels rond het doelwit in de wereld. Staat het niet op je scherm, dan niets.
        WorldProjection.ScreenRect rect = c.projection.entityBox(target);
        if (rect == null) {
            return;
        }
        int pad = 3;
        int x0 = (int) rect.minX() - pad;
        int y0 = (int) rect.minY() - pad;
        int x1 = (int) rect.maxX() + pad;
        int y1 = (int) rect.maxY() + pad;
        brackets(g, x0, y0, x1, y1, 5, argb(230, threat.color));

        // Het G-menu open? Dan geen kaartje: dat zou over het menu vallen.
        if (c.menuOpen) {
            return;
        }

        // ---- Wat er op het kaartje komt
        Font font = c.font;
        String name = target.getDisplayName().getString().toUpperCase();
        float health = target.getHealth();
        float maxHealth = Math.max(1.0f, target.getMaxHealth());
        String healthText = String.format("%.0f/%.0f", health, maxHealth);
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Component.translatable("hud.imttech.target_card.stats", target.getArmorValue(),
                String.format("%.1f", c.player.distanceTo(target))).getString().toUpperCase(), argb(220, WHITE)));
        lines.add(new Line(Component.translatable("hud.imttech.threat_level", threat.label()).getString().toUpperCase(),
                argb(255, threat.color)));
        ItemStack held = target.getMainHandItem();
        if (!held.isEmpty()) {
            lines.add(new Line(Component.translatable("hud.imttech.holding", held.getHoverName()).getString().toUpperCase(),
                    argb(200, WHITE)));
        }

        int barW = 50;
        int innerW = Math.max(font.width(name), barW + 4 + small(font, healthText));
        for (Line line : lines) {
            innerW = Math.max(innerW, small(font, line.text()));
        }
        int cardW = innerW + 8;
        int cardH = 10 + 7 + lines.size() * 7 + 3;

        // ---- Waar het kaartje komt: rechts van de beugels, of links als het rechts niet past
        int gap = 18;
        boolean right = x1 + gap + cardW <= c.width - 4 || x0 - gap - cardW < 4;
        int cardX = right ? x1 + gap : x0 - gap - cardW;
        int cardY = Mth.clamp(y0 - 14, 4, c.height - cardH - 4);
        int cornerX = right ? x1 : x0;
        int elbowX = right ? x1 + 8 : x0 - 8;
        int lineY = cardY + 4;

        // ---- Het lijntje: schuin van de hoek van de beugels omhoog, dan recht naar het kaartje
        int lineColour = argb(200, threat.color);
        float grow = Mth.clamp(age / (float) LINE_MILLIS, 0, 1);
        int diagEndX = Math.round(Mth.lerp(Math.min(1, grow * 2), cornerX, elbowX));
        int diagEndY = Math.round(Mth.lerp(Math.min(1, grow * 2), y0, lineY));
        line(g, cornerX, y0, diagEndX, diagEndY, lineColour);
        if (grow > 0.5f) {
            int edge = right ? cardX : cardX + cardW;
            int tip = Math.round(Mth.lerp((grow - 0.5f) * 2, elbowX, edge));
            g.fill(Math.min(elbowX, tip), lineY, Math.max(elbowX, tip) + 1, lineY + 1, lineColour);
        }
        if (age < LINE_MILLIS) {
            return;
        }

        // ---- Het kaartje zelf: doorzichtig, met een streep in de kleur van de dreiging
        float shown = Mth.clamp((age - LINE_MILLIS) / (float) (CARD_MILLIS - LINE_MILLIS), 0, 1);
        int alpha = Math.round(255 * shown);
        if (alpha < 8) {
            return;
        }
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, argb(alpha * 120 / 255, 0x001018));
        g.fill(right ? cardX : cardX + cardW - 1, cardY, right ? cardX + 1 : cardX + cardW, cardY + cardH,
                argb(alpha, threat.color));
        int tx = cardX + 4;
        int ty = cardY + 1;
        // Letter voor letter verschijnen, zoals de meldingen.
        String typedName = name.substring(0, Math.min(name.length(), Math.round(name.length() * shown)));
        g.drawString(font, typedName, tx, ty, argb(alpha, WHITE), true);
        ty += 10;
        float fraction = health / maxHealth;
        bar(g, tx, ty + 1, barW, 3, fraction * shown, fraction < 0.3f ? RED : GREEN);
        smallString(g, font, healthText, tx + barW + 4, ty, argb(alpha * 220 / 255, WHITE));
        ty += 7;
        for (Line line : lines) {
            smallString(g, font, line.text(), tx, ty, (line.colour() & 0x00FFFFFF) | ((alpha * (line.colour() >>> 24) / 255) << 24));
            ty += 7;
        }
    }

    private record Line(String text, int colour) {}

    /** Hoe breed kleine tekst is (in schermpixels). */
    private static int small(Font font, String text) {
        return Math.round(font.width(text) * SMALL);
    }

    /** Kleine tekst (75%), links uitgelijnd. */
    private static void smallString(GuiGraphics g, Font font, String text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0f);
        g.pose().scale(SMALL, SMALL, 1.0f);
        g.drawString(font, text, 0, 0, colour, true);
        g.pose().popPose();
    }

    /** Een dun, schuin lijntje van pixel tot pixel (Bresenham: telkens één stapje opzij of omhoog). */
    private static void line(GuiGraphics g, int xa, int ya, int xb, int yb, int colour) {
        int dx = Math.abs(xb - xa);
        int dy = -Math.abs(yb - ya);
        int sx = xa < xb ? 1 : -1;
        int sy = ya < yb ? 1 : -1;
        int error = dx + dy;
        while (true) {
            g.fill(xa, ya, xa + 1, ya + 1, colour);
            if (xa == xb && ya == yb) {
                return;
            }
            int e2 = 2 * error;
            if (e2 >= dy) {
                error += dy;
                xa += sx;
            }
            if (e2 <= dx) {
                error += dx;
                ya += sy;
            }
        }
    }
}
