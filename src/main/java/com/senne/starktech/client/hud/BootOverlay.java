package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.CYAN;
import static com.senne.starktech.client.hud.HudStyle.RED;
import static com.senne.starktech.client.hud.HudStyle.WHITE;
import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.bar;
import static com.senne.starktech.client.hud.HudStyle.brackets;

import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithOwner;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * De twee schermen die álles vervangen: de opstart-animatie als je de bril opzet, en
 * "toegang geweigerd" als de bril niet van jou is.
 *
 * Dit zijn geen gewone onderdelen (HudWidget), want zolang een van de twee in beeld is,
 * tekent de rest van de HUD niets.
 */
public final class BootOverlay {
    private BootOverlay() {}

    /** Hoe lang de opstart-animatie duurt (ms). */
    public static final long BOOT_MILLIS = 1600;

    /** Hoekbeugels rond het hele scherm. */
    public static void frame(GuiGraphics g, int w, int h, int color, int alpha) {
        int margin = 4;
        brackets(g, margin, margin, w - margin, h - margin, 22, argb(alpha, color));
    }

    /** Opstart-animatie als je de bril opzet. */
    public static void boot(GuiGraphics g, Font font, int w, int h, long age) {
        float t = (float) age / BOOT_MILLIS;
        // Een scanlijn die van boven naar beneden gaat.
        int lineY = (int) (h * t);
        g.fill(0, lineY, w, lineY + 1, argb(160, CYAN));
        g.fill(0, lineY - 6, w, lineY, argb(30, CYAN));
        // Beugels die van het midden naar buiten schuiven.
        int grow = (int) (Math.min(1.0f, t * 1.5f) * (Math.min(w, h) / 2 - 4));
        brackets(g, w / 2 - grow * w / h, h / 2 - grow, w / 2 + grow * w / h, h / 2 + grow, 22, argb(200, CYAN));

        g.drawCenteredString(font, Component.literal("E.D.I.T.H."), w / 2, h / 2 - 20, argb(255, CYAN));
        g.drawCenteredString(font, Component.translatable("hud.starktech.booting"), w / 2, h / 2 - 8, argb(220, WHITE));
        bar(g, w / 2 - 50, h / 2 + 6, 100, 3, t, CYAN);
    }

    /** Wat je ziet als je een bril van iemand anders opzet. */
    public static void accessDenied(GuiGraphics g, Font font, ItemStack glasses, int w, int h, long now) {
        int alpha = (int) (170 + 80 * Math.sin(now / 180.0));
        frame(g, w, h, RED, alpha);
        g.fill(0, h / 3 - 14, w, h / 3 + 26, argb(90, 0x200000));
        g.drawCenteredString(font, Component.translatable("hud.starktech.access_denied"), w / 2, h / 3 - 6, argb(255, RED));
        EdithOwner owner = EdithData.getOwner(glasses);
        if (owner != null) {
            g.drawCenteredString(font, Component.translatable("hud.starktech.registered_to", owner.name()),
                    w / 2, h / 3 + 8, argb(220, WHITE));
        }
    }
}
