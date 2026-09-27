package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.CYAN;
import static com.senne.starktech.client.hud.HudStyle.GREEN;
import static com.senne.starktech.client.hud.HudStyle.RED;
import static com.senne.starktech.client.hud.HudStyle.WHITE;
import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.brackets;

import com.mojang.blaze3d.systems.RenderSystem;
import com.senne.starktech.client.EdithClientEvents;
import com.senne.starktech.client.EdithClientConfig;
import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.client.voice.EdithSpeech;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithOwner;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModSounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
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
 * De opstart-animatie duurt ruim vijf seconden en staat helemaal in het midden, onder elkaar
 * (tijden in ms):
 * <pre>
 *    0 -  450  het scherm gaat aan: een lichtstreep die openvouwt
 *  250 - 4900  het logo: groot E.D.I.T.H. in reactorringen, letter voor letter
 *  900 - 4900  de laadbalk, met eronder wat er geladen wordt (je echte drones en batterij)
 * 3100 - 4900  de identiteitscontrole: je gezicht wordt gescand; de balk wacht op 90%
 * 4100         bevestigd: E.D.I.T.H. zegt haar welkomstzin (en die staat ook in beeld)
 * 4700 - 5600  "ONLINE": de hoeken schuiven naar de rand en de HUD komt tevoorschijn
 * </pre>
 *
 * De welkomstzin komt van de server ("Welcome back, ..."). Die houden we vast tot het moment
 * van bevestigen (zie EdithClientState.bootGreeting), zodat je hem hoort op het goede moment.
 */
public final class BootOverlay {
    private BootOverlay() {}

    /** Hoe lang de opstart-animatie duurt (ms). */
    public static final long BOOT_MILLIS = 5600;
    /** Het moment dat je identiteit bevestigd is en E.D.I.T.H. je begroet (ms). */
    public static final long CONFIRMED_AT = 4100;

    private static final String LOGO = "E.D.I.T.H.";
    /** Tekens voor het "glitchen" van letters die nog niet vaststaan. */
    private static final String GLITCH = "#%&@$?!/\\<>*+=01";
    private static final long BAR_START = 900;
    private static final long SCAN_START = 3100;
    private static final long FADE_FROM = 4700;
    private static final long FADE_TO = 5100;

    /** Welke opstart we het laatst tekenden, en welke geluidjes daarvan al klonken. */
    private static long bootSeen = -1;
    private static int stepsPlayed;
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
            stepsPlayed = 0;
            confirmPlayed = false;
        }
        int cx = w / 2;
        // Alles staat onder elkaar rond het midden, en blijft boven de hotbar (die zit onderaan,
        // en in survival staan je hartjes er nog boven).
        int cy = Math.min(h / 2, h - 110);

        powerOn(g, w, h, cx, h / 2, age);
        logo(g, font, cx, cy - 56, age);
        loadingBar(g, font, cx, cy - 8, age, player, glasses);
        identity(g, font, cx, cy + 18, age, player, glasses);
        greeting(g, font, w, cx, cy + 70, age, bootStart, player);
        online(g, font, w, h, cx, h / 2, age);
    }

    /** 1. Het scherm gaat aan, zoals een oude tv: een lichtstreep die openvouwt. */
    private static void powerOn(GuiGraphics g, int w, int h, int cx, int cy, long age) {
        // Tot het einde een zachte donkere waas, zodat de letters goed leesbaar zijn.
        int veil = age < 450 ? (int) (170 - 90 * age / 450.0) : (int) (80 * fadeOut(age, FADE_FROM, FADE_TO) / 255.0);
        g.fill(0, 0, w, h, argb(veil, 0x000812));
        if (age > 450) {
            return;
        }
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

    /** 2. Het logo: groot E.D.I.T.H., in drie ringen die zich rondtekenen zoals een reactor. */
    private static void logo(GuiGraphics g, Font font, int cx, int y, long age) {
        if (age < 250 || age > FADE_TO) {
            return;
        }
        int fade = fadeOut(age, FADE_FROM, FADE_TO);
        float grow = Mth.clamp((age - 250) / 700.0f, 0, 1);
        arc(g, cx, y, 28, age / 500.0, grow * 0.8f, argb(fade * 120 / 255, CYAN));
        arc(g, cx, y, 34, -age / 700.0, grow * 0.6f, argb(fade * 90 / 255, CYAN));
        arc(g, cx, y, 40, age / 1000.0 + 1.0, grow * 0.9f, argb(fade * 60 / 255, CYAN));

        // E.D.I.T.H. letter voor letter, drie keer zo groot; letters die nog komen, glitchen.
        long typed = age - 450;
        if (typed > 0) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < LOGO.length(); i++) {
                long letterAt = i * 60L;
                if (typed >= letterAt + 110) {
                    text.append(LOGO.charAt(i));
                } else if (typed >= letterAt) {
                    text.append(GLITCH.charAt((int) ((age / 40 + i * 7) % GLITCH.length())));
                }
            }
            int jitter = typed < 800 && (age / 60) % 5 == 0 ? 1 : 0;
            float scale = 3.0f;
            g.pose().pushPose();
            g.pose().translate(cx + jitter, y - 4.5f * scale + 1, 0);
            g.pose().scale(scale, scale, 1.0f);
            g.drawCenteredString(font, text.toString(), 0, 0, argb(fade, CYAN));
            g.pose().popPose();
        }
        // De ondertitel, getypt.
        long subtitle = age - 1000;
        if (subtitle > 0) {
            String full = Component.translatable("hud.starktech.boot.motto").getString();
            int shown = (int) Math.min(full.length(), subtitle / 18);
            HudStyle.smallText(g, font, full.substring(0, shown), cx, y + 20, argb(fade * 190 / 255, WHITE));
        }
    }

    /**
     * Hoe ver de laadbalk is (0 tot 1) op dit moment. Niet netjes gelijkmatig: hij hapert af en
     * toe (zoals een echte laadbalk), wacht op 90% tijdens de identiteitscontrole en loopt dan
     * in één keer vol.
     */
    private static float progress(long age) {
        long[] at = {BAR_START, 1300, 1500, 2000, 2300, 2700, SCAN_START, CONFIRMED_AT, CONFIRMED_AT + 350};
        float[] value = {0.0f, 0.22f, 0.25f, 0.48f, 0.55f, 0.74f, 0.90f, 0.90f, 1.0f};
        if (age <= at[0]) {
            return 0;
        }
        for (int i = 1; i < at.length; i++) {
            if (age <= at[i]) {
                float t = (float) (age - at[i - 1]) / (at[i] - at[i - 1]);
                float eased = t * t * (3 - 2 * t);
                return value[i - 1] + (value[i] - value[i - 1]) * eased;
            }
        }
        return 1.0f;
    }

    /** 3. De laadbalk, met eronder wat E.D.I.T.H. op dat moment laadt (en een piepje per stap). */
    private static void loadingBar(GuiGraphics g, Font font, int cx, int y, long age, Player player, ItemStack glasses) {
        if (age < BAR_START || age > FADE_TO) {
            return;
        }
        int fade = fadeOut(age, FADE_FROM, FADE_TO);
        float appear = Mth.clamp((age - BAR_START) / 250.0f, 0, 1);
        int width = Math.round(180 * appear);
        float done = progress(age);
        boolean confirmed = age >= CONFIRMED_AT;
        int colour = confirmed ? GREEN : CYAN;

        // De goot, de gevulde balk met een lichte rand bovenop, en een glimlichtje dat meeloopt.
        g.fill(cx - width / 2 - 1, y - 1, cx + width / 2 + 1, y + 6, argb(fade * 70 / 255, colour));
        g.fill(cx - width / 2, y, cx + width / 2, y + 5, argb(fade * 170 / 255, 0x02121C));
        int filled = Math.round(width * done);
        g.fill(cx - width / 2, y, cx - width / 2 + filled, y + 5, argb(fade * 230 / 255, colour));
        g.fill(cx - width / 2, y, cx - width / 2 + filled, y + 1, argb(fade * 255 / 255, 0xDFF9FF));
        if (filled > 2 && done < 1) {
            int shine = cx - width / 2 + filled;
            g.fill(shine - 2, y - 1, shine, y + 6, argb(fade, 0xFFFFFF));
        }
        String percent = Math.round(done * 100) + "%";
        HudStyle.smallText(g, font, percent, cx + width / 2 + 12, y, argb(fade * 220 / 255, colour));

        // Wat er nu geladen wordt; bij elke nieuwe stap een piepje.
        int drones = player.getData(ModAttachments.ARSENAL).availableCount();
        int energy = Math.round(100f * EdithData.getEnergy(glasses) / Math.max(1, EdithData.getMaxEnergy(glasses)));
        long[] stepAt = {BAR_START, 1500, 2000, 2300, 2700, SCAN_START, CONFIRMED_AT, CONFIRMED_AT + 350};
        Component[] steps = {
                Component.translatable("hud.starktech.boot.optics"),
                Component.translatable("hud.starktech.boot.neural"),
                Component.translatable("hud.starktech.boot.uplink"),
                Component.translatable("hud.starktech.boot.drones", drones),
                Component.translatable("hud.starktech.boot.power", energy),
                Component.translatable("hud.starktech.boot.verifying"),
                Component.translatable("hud.starktech.boot.confirmed"),
                Component.translatable("hud.starktech.boot.ready"),
        };
        int step = 0;
        for (int i = 0; i < stepAt.length; i++) {
            if (age >= stepAt[i]) {
                step = i;
            }
        }
        if (stepsPlayed <= step && step < SCAN_STEP) {
            stepsPlayed = step + 1;
            EdithClientEvents.playUi(ModSounds.EDITH_BLIP.get(), 1.0f + step * 0.06f);
        }
        boolean scanning = step == SCAN_STEP;
        int textColour = step >= SCAN_STEP + 1 ? GREEN : scanning && (age / 250) % 2 == 0 ? WHITE : CYAN;
        String text = steps[step].getString() + (step < SCAN_STEP ? dots(age) : "");
        HudStyle.smallText(g, font, text.toUpperCase(), cx, y + 10, argb(fade * 230 / 255, textColour));
    }

    /** Bij welke stap van de laadbalk de identiteitscontrole hoort. */
    private static final int SCAN_STEP = 5;

    /** "", ".", ".." of "..." om de beurt: iets is bezig. */
    private static String dots(long age) {
        return ".".repeat((int) ((age / 250) % 4));
    }

    /**
     * 4. De identiteitscontrole: je gezicht (je eigen skin) als hologram in een kader, met een
     * scanlijn die erover heen en weer gaat. Bevestigd: groen, in echte kleur, met je naam.
     */
    private static void identity(GuiGraphics g, Font font, int cx, int y, long age, Player player, ItemStack glasses) {
        if (age < SCAN_START || age > FADE_TO) {
            return;
        }
        int fade = fadeOut(age, FADE_FROM, FADE_TO);
        boolean confirmed = age >= CONFIRMED_AT;
        int colour = confirmed ? GREEN : CYAN;
        float appear = Mth.clamp((age - SCAN_START) / 250.0f, 0, 1);
        int size = 32;
        int x0 = cx - size / 2;
        int y0 = y;

        // Het gezicht: tijdens het scannen blauw doorzichtig (hologram), daarna in echte kleur.
        if (player instanceof AbstractClientPlayer clientPlayer) {
            float alpha = appear * fade / 255.0f;
            RenderSystem.enableBlend();
            if (confirmed) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
            } else {
                // Een beetje flakkeren, zoals een hologram.
                float flicker = (age / 70) % 7 == 0 ? 0.55f : 0.8f;
                RenderSystem.setShaderColor(0.35f, 0.85f, 1.0f, alpha * flicker);
            }
            PlayerFaceRenderer.draw(g, clientPlayer.getSkin(), x0, y0, size);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }
        // Het kader, iets groter dan het gezicht.
        int pad = Math.round(10 - 6 * appear);
        brackets(g, x0 - pad, y0 - pad, x0 + size + pad, y0 + size + pad, 6, argb(fade, colour));
        if (!confirmed) {
            // De scanlijn, met een zachte gloed eromheen, en een raster van puntjes.
            int line = y0 + (int) ((Math.sin((age - SCAN_START) / 160.0) * 0.5 + 0.5) * size);
            g.fill(x0 - 4, line, x0 + size + 4, line + 1, argb(fade * 230 / 255, 0xDFF9FF));
            g.fill(x0 - 4, line - 2, x0 + size + 4, line + 3, argb(fade * 60 / 255, CYAN));
            for (int gx = 0; gx <= size; gx += 8) {
                for (int gy = 0; gy <= size; gy += 8) {
                    g.fill(x0 + gx, y0 + gy, x0 + gx + 1, y0 + gy + 1, argb(fade * 120 / 255, CYAN));
                }
            }
        } else if (!confirmPlayed) {
            confirmPlayed = true;
            EdithClientEvents.playUi(ModSounds.EDITH_CONFIRM.get(), 1.0f);
        }
        // Onder het kader: wie je bent (zodra het bevestigd is).
        if (confirmed) {
            EdithOwner owner = EdithData.getOwner(glasses);
            String name = owner != null ? owner.name() : player.getGameProfile().getName();
            g.drawCenteredString(font, name, cx, y0 + size + 7, argb(fade, WHITE));
        }
    }

    /**
     * 5. De welkomstzin: E.D.I.T.H. zegt hem (met haar stem, als die geïnstalleerd is) op het
     * moment dat je identiteit bevestigd is, en hij verschijnt getypt onderin, met een golfje
     * dat laat zien dat ze praat.
     */
    private static void greeting(GuiGraphics g, Font font, int w, int cx, int y, long age, Player player) {
        if (age < CONFIRMED_AT || age > BOOT_MILLIS) {
            return;
        }
        Component text = EdithClientState.bootGreeting != null
                ? EdithClientState.bootGreeting
                : Component.translatable("edith.starktech.msg.welcome_back", player.getGameProfile().getName());
        if (!EdithClientState.bootGreetingSaid) {
            EdithClientState.bootGreetingSaid = true;
            // Nu pas uitspreken (en in de meldingen zetten, voor na het opstarten).
            EdithSpeech.say(text, EdithMessagePayload.Level.BOOT);
            EdithClientState.addNotification(text, EdithMessagePayload.Level.BOOT, false);
        }
        // "Voice text" uit: dan alleen het golfje (ze praat), zonder de zin eronder.
        boolean showText = EdithClientConfig.voiceTextInHud() || !EdithSpeech.available();
        int fade = fadeOut(age, BOOT_MILLIS - 400, BOOT_MILLIS);
        String full = showText ? "“" + text.getString() + "”" : "";
        int shown = (int) Math.min(full.length(), (age - CONFIRMED_AT) / 22);
        String typed = font.plainSubstrByWidth(full.substring(0, shown), w - 40);
        g.drawCenteredString(font, typed, cx, y, argb(fade, WHITE));
        // Het golfje links van de zin: vijf staafjes die op en neer gaan.
        int left = cx - font.width(font.plainSubstrByWidth(full, w - 40)) / 2 - 14;
        for (int i = 0; i < 5; i++) {
            double wave = Math.abs(Math.sin(age / 90.0 + i * 1.3));
            int bar = 1 + (int) Math.round(wave * 4);
            g.fill(left + i * 2, y + 4 - bar, left + i * 2 + 1, y + 4 + bar, argb(fade * 200 / 255, CYAN));
        }
    }

    /** 6. Klaar: ONLINE, en de hoeken schuiven naar de rand van het scherm. */
    private static void online(GuiGraphics g, Font font, int w, int h, int cx, int cy, long age) {
        if (age < FADE_FROM) {
            return;
        }
        float t = Mth.clamp((age - FADE_FROM) / 900.0f, 0, 1);
        float eased = 1 - (1 - t) * (1 - t);
        int grow = (int) (eased * (Math.min(w, h) / 2 - 4));
        int spreadX = grow * w / Math.max(1, h);
        brackets(g, cx - spreadX, cy - grow, cx + spreadX, cy + grow, 22, argb(220, CYAN));
        if (age < FADE_FROM + 800) {
            int alpha = age < FADE_FROM + 200 ? (int) ((age - FADE_FROM) / 200.0 * 255) : fadeOut(age, FADE_FROM + 400, FADE_FROM + 800);
            g.pose().pushPose();
            g.pose().translate(cx, cy - 6, 0);
            g.pose().scale(2.0f, 2.0f, 1.0f);
            g.drawCenteredString(font, Component.translatable("hud.starktech.boot.online"), 0, 0, argb(alpha, CYAN));
            g.pose().popPose();
        }
        // Een laatste zachte flits.
        if (age > BOOT_MILLIS - 400) {
            g.fill(0, 0, w, h, argb((int) (50 * (1 - (age - (BOOT_MILLIS - 400)) / 400.0)), 0xDFF9FF));
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
