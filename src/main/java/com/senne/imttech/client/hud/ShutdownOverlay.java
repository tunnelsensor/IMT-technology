package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.RED;
import static com.senne.imttech.client.hud.HudStyle.WHITE;
import static com.senne.imttech.client.hud.HudStyle.argb;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * De bril valt uit omdat de batterij leeg is (hoofdstuk 96, jouw keuze: "uitvalbeeld en nog 1 zin"). Het begint pas als
 * I.M.T haar laatste zin gezegd heeft (hoofdstuk 102, zie ImtClientEvents.trackBattery). Het omgekeerde van de
 * opstart-animatie, in iets minder dan twee seconden:
 * <ol>
 *   <li>0 - 1,1 s: de rand knippert rood, en in het midden staat "BATTERY DEPLETED". Op het eind gaat de tekst haperen.</li>
 *   <li>1,1 - 1,5 s: zoals een oude tv die uitgaat: het beeld wordt een lichtstreep die naar het midden krimpt.</li>
 *   <li>1,5 - 1,8 s: een puntje in het midden dat uitdooft.</li>
 * </ol>
 * Daarna tekent de HUD helemaal niets meer, tot de bril weer stroom heeft (dan start hij opnieuw op).
 */
public final class ShutdownOverlay {
    private ShutdownOverlay() {}

    /** Zo lang duurt het uitvallen (ms). */
    public static final long MILLIS = 1800;
    private static final long TEXT_UNTIL = 1100;
    private static final long LINE_UNTIL = 1500;

    /** @param age hoe lang geleden de batterij leegliep (ms) */
    public static void render(GuiGraphics g, Font font, int w, int h, long age) {
        int cx = w / 2;
        int cy = h / 2;
        if (age < TEXT_UNTIL) {
            // Elke 60 ms een ander "knipperbeeld": de rand en de tekst haperen, zoals een scherm zonder stroom.
            RandomSource random = RandomSource.create(age / 60);
            boolean flicker = random.nextInt(4) == 0;
            BootOverlay.frame(g, w, h, RED, flicker ? 40 : 90 + random.nextInt(80));
            // Op het eind hapert de tekst steeds vaker.
            boolean glitch = age > TEXT_UNTIL - 350 && random.nextInt(3) == 0;
            if (!glitch) {
                int y = Math.min(cy - 30, h - 120);
                g.fill(0, y - 6, w, y + 24, argb(90, 0x140004));
                g.drawCenteredString(font, Component.translatable("hud.imttech.shutdown"), cx, y, argb(flicker ? 150 : 255, RED));
                g.drawCenteredString(font, Component.translatable("hud.imttech.shutdown_sub"), cx, y + 12, argb(170, WHITE));
            }
        } else if (age < LINE_UNTIL) {
            // De lichtstreep: eerst nog een smalle band, dan een lijn die naar het midden krimpt.
            float t = (age - TEXT_UNTIL) / (float) (LINE_UNTIL - TEXT_UNTIL);
            int half = Math.max(1, Math.round(cx * (1 - t * t)));
            int thick = t < 0.3f ? Math.max(1, Math.round(6 * (1 - t / 0.3f))) : 1;
            g.fill(cx - half, cy - thick, cx + half, cy + thick, argb(Math.round(220 - 60 * t), 0xFFD6D6));
        } else {
            // Het puntje dat uitdooft.
            float t = Mth.clamp((age - LINE_UNTIL) / (float) (MILLIS - LINE_UNTIL), 0, 1);
            int alpha = Math.round(200 * (1 - t));
            if (alpha > 0) {
                g.fill(cx - 1, cy - 1, cx + 1, cy + 1, argb(alpha, 0xFFFFFF));
            }
        }
    }
}
