package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.CYAN;
import static com.senne.imttech.client.hud.HudStyle.WHITE;
import static com.senne.imttech.client.hud.HudStyle.argb;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Wat je in de bril ziet na een EMP-granaat (hoofdstuk 35): geen HUD meer, maar ruis. Zachte sneeuw over je hele beeld,
 * een paar horizontale strepen die verspringen (zoals een kapotte tv), en in het midden "EMP" met hoeveel seconden het
 * nog duurt. Daarna start de bril opnieuw op (de gewone opstart-animatie, zie ImtHud).
 *
 * Het blijft licht genoeg om erdoor te kijken: je moet nog kunnen vluchten.
 */
public final class EmpStaticOverlay {
    private EmpStaticOverlay() {}

    /** Zo groot is één "korrel" sneeuw (in GUI-pixels). */
    private static final int GRAIN = 4;

    public static void render(GuiGraphics g, Font font, int w, int h, long now, int ticksLeft) {
        // Elke 50 ms een nieuw beeld ruis; tussendoor hetzelfde (anders flikkert het te fel).
        RandomSource random = RandomSource.create(now / 50);
        // De sneeuw: lang niet elke korrel, en heel doorzichtig.
        for (int y = 0; y < h; y += GRAIN) {
            for (int x = 0; x < w; x += GRAIN) {
                int roll = random.nextInt(100);
                if (roll < 12) {
                    int grey = 120 + random.nextInt(136);
                    g.fill(x, y, x + GRAIN, y + GRAIN, argb(25 + random.nextInt(35), grey << 16 | grey << 8 | grey));
                }
            }
        }
        // Een paar verspringende strepen.
        for (int i = 0; i < 4; i++) {
            int y = random.nextInt(Math.max(1, h));
            int height = 1 + random.nextInt(6);
            g.fill(0, y, w, y + height, argb(30 + random.nextInt(40), i % 2 == 0 ? CYAN : WHITE));
        }
        // Een rand die zwak knippert, net als bij een lege bril.
        BootOverlay.frame(g, w, h, CYAN, 40 + random.nextInt(60));

        // In het midden: wat er aan de hand is.
        int seconds = Mth.ceil(ticksLeft / 20f);
        boolean blink = (now / 400) % 2 == 0;
        g.fill(0, h / 3 - 14, w, h / 3 + 26, argb(110, 0x001020));
        g.drawCenteredString(font, Component.translatable("hud.imttech.emp"), w / 2, h / 3 - 6,
                argb(blink ? 255 : 170, CYAN));
        g.drawCenteredString(font, Component.translatable("hud.imttech.emp_reboot", seconds), w / 2, h / 3 + 8,
                argb(220, WHITE));
    }
}
