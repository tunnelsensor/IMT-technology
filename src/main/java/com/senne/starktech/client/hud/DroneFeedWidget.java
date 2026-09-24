package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.GREEN;
import static com.senne.starktech.client.hud.HudStyle.ORANGE;
import static com.senne.starktech.client.hud.HudStyle.RED;
import static com.senne.starktech.client.hud.HudStyle.WHITE;
import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.bar;
import static com.senne.starktech.client.hud.HudStyle.brackets;

import com.senne.starktech.client.DroneCamera;
import com.senne.starktech.client.illusion.IllusionRenderer;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithBalance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Het "camerabeeld" als je door de verkenningsdrone kijkt: scanlijnen, een knipperende
 * REC-stip, signaalsterkte, de levens van de drone en de afstand tot je lichaam.
 */
public final class DroneFeedWidget implements HudWidget {
    /** Kleur van een streepje dat niet meer oplicht. */
    private static final int DIM = 0x404850;

    @Override
    public boolean visible(HudContext c) {
        return c.powered && !c.menuOpen && DroneCamera.isActive() && DroneCamera.drone() != null;
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        StarkDroneEntity drone = DroneCamera.drone();
        int w = c.width;
        int h = c.height;

        // Scanlijnen
        for (int y = 0; y < h; y += 3) {
            g.fill(0, y, w, y + 1, argb(18, 0x000000));
        }
        brackets(g, 20, 20, w - 20, h - 20, 40, argb(220, WHITE));

        // Vizier
        int cx = w / 2;
        int cy = h / 2;
        g.fill(cx - 12, cy, cx - 4, cy + 1, argb(220, WHITE));
        g.fill(cx + 5, cy, cx + 13, cy + 1, argb(220, WHITE));
        g.fill(cx, cy - 12, cx + 1, cy - 4, argb(220, WHITE));
        g.fill(cx, cy + 5, cx + 1, cy + 13, argb(220, WHITE));

        // REC + titel (rechtsboven, onder het kompas)
        int x = w - 150;
        int y = 30;
        if ((c.now / 500) % 2 == 0) {
            g.fill(x, y + 1, x + 6, y + 7, argb(255, RED));
        }
        // Richt je een illusie, dan staat dat erboven (anders "DRONE FEED").
        boolean aiming = IllusionRenderer.isAiming();
        g.drawString(c.font, Component.translatable(aiming ? "hud.starktech.illusion_aim" : "hud.starktech.drone_feed"),
                x + 10, y, argb(255, WHITE), true);
        y += 12;

        // Signaalsterkte: hoe verder van je lichaam, hoe zwakker.
        double distance = Math.sqrt(drone.distanceToSqr(c.player));
        float signal = 1.0f - (float) Math.min(1.0, distance / EdithBalance.SCOUT_RANGE);
        int bars = 1 + Math.round(signal * 4);
        for (int i = 0; i < 5; i++) {
            int barHeight = 3 + i * 2;
            int color = i < bars ? (signal < 0.25f ? RED : signal < 0.5f ? ORANGE : GREEN) : DIM;
            g.fill(x + i * 4, y + 12 - barHeight, x + i * 4 + 3, y + 12, argb(230, color));
        }
        g.drawString(c.font, Component.translatable("hud.starktech.drone_distance", Math.round(distance)),
                x + 24, y + 2, argb(220, WHITE), true);
        y += 16;

        // Levens van de drone
        float health = drone.getHealth() / Math.max(1.0f, drone.getMaxHealth());
        g.drawString(c.font, Component.translatable("hud.starktech.drone_hull"), x, y, argb(220, WHITE), true);
        bar(g, x + 40, y + 2, 60, 4, health, health < 0.3f ? RED : GREEN);
        y += 11;
        g.drawString(c.font, Component.translatable("hud.starktech.drone_altitude", Math.round(drone.getY())),
                x, y, argb(200, WHITE), true);

        // Uitleg onderaan: hoe je vliegt, en (tijdens het richten) hoe je neerzet of annuleert.
        g.drawCenteredString(c.font, Component.translatable(aiming ? "hud.starktech.illusion_aim_controls"
                : "hud.starktech.drone_controls"), cx, h - 34, argb(200, WHITE));
    }
}
