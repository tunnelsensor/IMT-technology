package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.CYAN;
import static com.senne.starktech.client.hud.HudStyle.GREEN;
import static com.senne.starktech.client.hud.HudStyle.RED;
import static com.senne.starktech.client.hud.HudStyle.WHITE;
import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.brackets;

import com.senne.starktech.client.EdithClientEvents;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithOwner;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModSounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * De twee schermen die álles vervangen: de opstart-animatie als je de bril opzet, en
 * "toegang geweigerd" als de bril niet van jou is.
 *
 * Dit zijn geen gewone onderdelen (HudWidget), want zolang een van de twee in beeld is,
 * tekent de rest van de HUD niets.
 *
 * De opstart-animatie duurt ruim vier seconden en heeft vijf stukken (tijden in ms):
 * <pre>
 *    0 -  350  het scherm gaat aan: een lichtstreep die openvouwt
 *  250 - 1400  het logo: ringen die zich rondtekenen, E.D.I.T.H. letter voor letter
 * 1200 - 3400  de systeemcheck links: regel voor regel, met een balkje en "OK"
 * 1600 - 3600  de irisscan in het midden, dan "IDENTITY CONFIRMED"; rechts een datastroom
 * 3500 - 4600  "ONLINE": de hoeken schuiven naar de rand en de HUD komt tevoorschijn
 * </pre>
 */
public final class BootOverlay {
    private BootOverlay() {}

    /** Hoe lang de opstart-animatie duurt (ms). */
    public static final long BOOT_MILLIS = 4600;

    private static final String LOGO = "E.D.I.T.H.";
    /** Tekens voor het "glitchen" van letters die nog niet vaststaan. */
    private static final String GLITCH = "#%&@$?!/\\<>*+=01";
    private static final long CHECK_START = 1200;
    private static final long CHECK_STEP = 300;
    private static final long SCAN_DONE = 3000;

    /** Welke opstart we het laatst tekenden, en hoeveel piepjes daarvan al klonken. */
    private static long bootSeen = -1;
    private static int blipsPlayed;
    private static boolean confirmPlayed;

    /** Hoekbeugels rond het hele scherm. */
    public static void frame(GuiGraphics g, int w, int h, int color, int alpha) {
        int margin = 4;
        brackets(g, margin, margin, w - margin, h - margin, 22, argb(alpha, color));
    }

    // ================================================================ Opstarten

    /**
     * Opstart-animatie als je de bril opzet.
     *
     * @param age       hoe lang geleden de bril opging (ms)
     * @param bootStart wanneer dat was (om per opstart de geluidjes maar één keer te spelen)
     */
    public static void boot(GuiGraphics g, Font font, int w, int h, long age, long bootStart,
                            Player player, ItemStack glasses) {
        if (bootStart != bootSeen) {
            bootSeen = bootStart;
            blipsPlayed = 0;
            confirmPlayed = false;
        }
        int cx = w / 2;
        int cy = h / 2;

        powerOn(g, w, h, cx, cy, age);
        logo(g, font, cx, cy, age);
        checklist(g, font, h, age, player, glasses);
        irisScan(g, font, cx, cy, age, glasses);
        dataStream(g, font, w, h, age);
        online(g, font, w, h, cx, cy, age);
    }

    /** 1. Het scherm gaat aan, zoals een oude tv: een lichtstreep die openvouwt. */
    private static void powerOn(GuiGraphics g, int w, int h, int cx, int cy, long age) {
        if (age > 450) {
            return;
        }
        // Eerst donker, dan trekt het donker weg.
        g.fill(0, 0, w, h, argb((int) (170 * (1 - age / 450.0)), 0x000812));
        if (age < 200) {
            int half = (int) (w / 2.0 * age / 200.0);
            g.fill(cx - half, cy, cx + half, cy + 1, argb(255, 0xDFF9FF));
        } else {
            int spread = (int) (h / 2.0 * (age - 200) / 250.0);
            int alpha = (int) (200 * (1 - (age - 200) / 250.0));
            g.fill(0, cy - spread, w, cy - spread + 1, argb(alpha, CYAN));
            g.fill(0, cy + spread, w, cy + spread + 1, argb(alpha, CYAN));
        }
    }

    /** 2. Het logo: drie ringen die zich rondtekenen, en E.D.I.T.H. dat verschijnt. */
    private static void logo(GuiGraphics g, Font font, int cx, int cy, long age) {
        if (age < 250 || age > 3700) {
            return;
        }
        int fade = fadeOut(age, 3300, 3700);
        float grow = Mth.clamp((age - 250) / 700.0f, 0, 1);
        // Ringen met een gat erin, elk de andere kant op draaiend, zoals een reactor.
        // Het logo staat bovenaan: onder de ringen komt de systeemcheck, in het midden de irisscan.
        int ly = cy - 92;
        arc(g, cx, ly, 14, age / 400.0, grow * 0.8f, argb(fade * 200 / 255, CYAN));
        arc(g, cx, ly, 19, -age / 600.0, grow * 0.65f, argb(fade * 150 / 255, CYAN));
        arc(g, cx, ly, 24, age / 900.0 + 1.0, grow * 0.9f, argb(fade * 90 / 255, CYAN));
        g.fill(cx - 2, ly - 2, cx + 2, ly + 2, argb(fade, 0xDFF9FF));

        // E.D.I.T.H. letter voor letter; letters die nog komen, glitchen nog even.
        long typed = age - 450;
        if (typed > 0) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < LOGO.length(); i++) {
                long letterAt = i * 55L;
                if (typed >= letterAt + 90) {
                    text.append(LOGO.charAt(i));
                } else if (typed >= letterAt) {
                    text.append(GLITCH.charAt((int) ((age / 40 + i * 7) % GLITCH.length())));
                }
            }
            int jitter = typed < 700 && (age / 60) % 5 == 0 ? 1 : 0;
            g.drawCenteredString(font, text.toString(), cx + jitter, cy - 66, argb(fade, CYAN));
        }
        // De ondertitel, getypt.
        long subtitle = age - 900;
        if (subtitle > 0) {
            String full = Component.translatable("hud.starktech.boot.motto").getString();
            int shown = (int) Math.min(full.length(), subtitle / 18);
            HudStyle.smallText(g, font, full.substring(0, shown), cx, cy - 56, argb(fade * 200 / 255, WHITE));
        }
    }

    /** 3. De systeemcheck: regels die één voor één verschijnen en "OK" krijgen, met een piepje. */
    private static void checklist(GuiGraphics g, Font font, int h, long age, Player player, ItemStack glasses) {
        if (age < CHECK_START || age > 3900) {
            return;
        }
        int fade = fadeOut(age, 3500, 3900);
        int drones = player.getData(ModAttachments.ARSENAL).availableCount();
        int energy = Math.round(100f * EdithData.getEnergy(glasses) / Math.max(1, EdithData.getMaxEnergy(glasses)));
        Component[] lines = {
                Component.translatable("hud.starktech.boot.optics"),
                Component.translatable("hud.starktech.boot.neural"),
                Component.translatable("hud.starktech.boot.uplink"),
                Component.translatable("hud.starktech.boot.drones", drones),
                Component.translatable("hud.starktech.boot.power", energy),
                Component.translatable("hud.starktech.boot.threats"),
        };
        int x = 14;
        int y = Math.max(20, h / 2 - 50);
        g.drawString(font, Component.translatable("hud.starktech.boot.check"), x, y - 14, argb(fade, CYAN), true);
        for (int i = 0; i < lines.length; i++) {
            long lineAge = age - CHECK_START - i * CHECK_STEP;
            if (lineAge < 0) {
                break;
            }
            String text = lines[i].getString();
            int shown = (int) Math.min(text.length(), lineAge / 8);
            g.drawString(font, text.substring(0, shown), x, y + i * 13, argb(fade * 220 / 255, WHITE), true);
            float filled = Mth.clamp((lineAge - 120) / 160.0f, 0, 1);
            if (lineAge > 120) {
                // Zelf getekend in plaats van HudStyle.bar: die vervaagt niet mee met de rest.
                int barY = y + i * 13 + 3;
                g.fill(x + 112, barY, x + 138, barY + 3, argb(fade * 60 / 255, CYAN));
                g.fill(x + 112, barY, x + 112 + Math.round(26 * filled), barY + 3, argb(fade * 230 / 255, CYAN));
            }
            if (filled >= 1) {
                g.drawString(font, "OK", x + 142, y + i * 13, argb(fade, GREEN), true);
                if (blipsPlayed <= i) {
                    blipsPlayed = i + 1;
                    EdithClientEvents.playUi(ModSounds.EDITH_BLIP.get(), 1.0f + i * 0.05f);
                }
            }
        }
    }

    /** 4. De irisscan rond je vizier, dan: dit is de eigenaar. */
    private static void irisScan(GuiGraphics g, Font font, int cx, int cy, long age, ItemStack glasses) {
        if (age < 1600 || age > 3700) {
            return;
        }
        int fade = fadeOut(age, 3300, 3700);
        int radius = 30;
        boolean confirmed = age >= SCAN_DONE;
        int colour = confirmed ? GREEN : CYAN;
        // De cirkel, en een draaiende scanlijn zolang hij bezig is.
        arc(g, cx, cy, radius, 0, 1.0f, argb(fade * 140 / 255, colour));
        arc(g, cx, cy, radius - 6, age / 300.0, 0.3f, argb(fade * 90 / 255, colour));
        if (!confirmed) {
            double angle = age / 140.0;
            for (int r = 4; r < radius; r += 2) {
                int x = cx + (int) Math.round(Math.cos(angle) * r);
                int y = cy + (int) Math.round(Math.sin(angle) * r);
                g.fill(x, y, x + 1, y + 1, argb(fade * 200 / 255, CYAN));
            }
            // Een lijn die over het oog heen en weer gaat.
            int sweep = (int) (Math.sin(age / 180.0) * radius * 0.8);
            g.fill(cx - radius + 6, cy + sweep, cx + radius - 6, cy + sweep + 1, argb(fade * 120 / 255, CYAN));
            HudStyle.smallText(g, font, Component.translatable("hud.starktech.boot.scanning").getString(),
                    cx, cy + radius + 6, argb(fade * 200 / 255, CYAN));
        } else {
            if (!confirmPlayed) {
                confirmPlayed = true;
                EdithClientEvents.playUi(ModSounds.EDITH_CONFIRM.get(), 1.0f);
            }
            EdithOwner owner = EdithData.getOwner(glasses);
            g.drawCenteredString(font, Component.translatable("hud.starktech.boot.confirmed"), cx, cy + radius + 6,
                    argb(fade, GREEN));
            if (owner != null) {
                HudStyle.smallText(g, font, owner.name(), cx, cy + radius + 18, argb(fade * 220 / 255, WHITE));
            }
        }
    }

    /** Rechts: een stroom getallen, alsof E.D.I.T.H. gegevens binnenhaalt. */
    private static void dataStream(GuiGraphics g, Font font, int w, int h, long age) {
        if (age < 1400 || age > 3700) {
            return;
        }
        int fade = fadeOut(age, 3300, 3700);
        int x = w - 58;
        int top = Math.max(20, h / 2 - 50);
        long row = age / 70; // elke 70 ms schuift de stroom een regel op
        for (int i = 0; i < 9; i++) {
            long seed = (row + i) * 2654435761L;
            String hex = String.format("%04X %04X", (seed >>> 8) & 0xFFFF, (seed >>> 24) & 0xFFFF);
            int alpha = fade * (60 + i * 18) / 255;
            HudStyle.smallText(g, font, hex, x + 20, top + i * 9, argb(alpha, CYAN));
        }
    }

    /** 5. Klaar: ONLINE, en de hoeken schuiven naar de rand van het scherm. */
    private static void online(GuiGraphics g, Font font, int w, int h, int cx, int cy, long age) {
        if (age < 3500) {
            return;
        }
        float t = Mth.clamp((age - 3500) / 900.0f, 0, 1);
        float eased = 1 - (1 - t) * (1 - t);
        int grow = (int) (eased * (Math.min(w, h) / 2 - 4));
        int spreadX = grow * w / Math.max(1, h);
        brackets(g, cx - spreadX, cy - grow, cx + spreadX, cy + grow, 22, argb(220, CYAN));
        if (age < 4300) {
            int alpha = age < 3700 ? (int) ((age - 3500) / 200.0 * 255) : fadeOut(age, 3900, 4300);
            g.pose().pushPose();
            g.pose().translate(cx, cy - 6, 0);
            g.pose().scale(2.0f, 2.0f, 1.0f);
            g.drawCenteredString(font, Component.translatable("hud.starktech.boot.online"), 0, 0, argb(alpha, CYAN));
            g.pose().popPose();
        }
        // Een laatste zachte flits.
        if (age > 4200) {
            g.fill(0, 0, w, h, argb((int) (50 * (1 - (age - 4200) / 400.0)), 0xDFF9FF));
        }
    }

    /** Een boog van een cirkel, als losse pixels: {@code part} = welk deel (0 tot 1) er staat. */
    private static void arc(GuiGraphics g, int cx, int cy, int radius, double start, float part, int colour) {
        int steps = Math.max(12, radius * 5);
        int drawn = (int) (steps * part);
        for (int i = 0; i < drawn; i++) {
            double angle = start + i * (Math.PI * 2 / steps);
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            g.fill(x, y, x + 1, y + 1, colour);
        }
    }

    /** 255 tot {@code from}, daarna aflopend naar 0 bij {@code to}. */
    private static int fadeOut(long age, long from, long to) {
        if (age <= from) {
            return 255;
        }
        return (int) Mth.clamp(255 * (1 - (double) (age - from) / (to - from)), 0, 255);
    }

    // ================================================================ Toegang geweigerd

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
