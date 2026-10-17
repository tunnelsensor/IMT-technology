package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.CYAN;
import static com.senne.imttech.client.hud.HudStyle.ORANGE;
import static com.senne.imttech.client.hud.HudStyle.WHITE;
import static com.senne.imttech.client.hud.HudStyle.argb;

import com.senne.imttech.client.SuitFlight;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Tijdens het vliegen met het harnas, links van je vizier: hoe hard de stuwraketten werken,
 * je snelheid en je hoogte.
 */
public final class FlightWidget implements HudWidget {
    private static final int BAR_H = 34;

    @Override
    public boolean visible(HudContext c) {
        return c.powered && !c.menuOpen && SuitFlight.isEngaged();
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        int x = c.width / 2 - 88;
        int y = c.height / 2 - BAR_H / 2;
        boolean boost = SuitFlight.isBoosting();
        int accent = boost ? ORANGE : CYAN;

        // Een staande balk: hoe hard de stuwraketten werken.
        int filled = Math.round(SuitFlight.thrust() * BAR_H);
        g.fill(x, y, x + 3, y + BAR_H, argb(60, accent));
        g.fill(x, y + BAR_H - filled, x + 3, y + BAR_H, argb(220, accent));

        // Snelheid in meter per seconde: blokken per tick keer 20. We meten hoe ver je echt
        // bewoog; de "deltaMovement" van Minecraft bevat al de zwaartekracht voor de volgende
        // tick, en dan stond er 2 m/s terwijl je stilhing.
        Vec3 motion = new Vec3(c.player.getX() - c.player.xo, c.player.getY() - c.player.yo,
                c.player.getZ() - c.player.zo);
        int speed = (int) Math.round(motion.length() * 20.0);
        int tx = x + 7;
        g.drawString(c.font, Component.translatable(boost ? "hud.imttech.flight.boost" : "hud.imttech.flight"),
                tx, y, argb(230, accent), true);
        g.drawString(c.font, Component.translatable("hud.imttech.flight.speed", speed),
                tx, y + 12, argb(230, WHITE), true);
        g.drawString(c.font, Component.translatable("hud.imttech.flight.altitude", (int) Math.floor(c.player.getY())),
                tx, y + 24, argb(190, WHITE), true);
    }
}
