package com.senne.imttech.client.howto;

import com.mojang.math.Axis;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.howto.HowToCue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Alles wat over het wereldje van een "How it works"-animatie heen komt (hoofdstuk 63): de tekstballonnen met hun lijntje,
 * de muis die klikt (met wat je vasthoudt), de balkjes, en het plaatje van een echt scherm met de pijltjes erop.
 * Alles in de kleuren van de HUD van I.M.T.
 */
final class SceneOverlays {
    private SceneOverlays() {}

    private static final int PANEL = 0x0E1216;
    private static final int EDGE = 0x465460;
    /** Of een plaatje echt in de mod zit (de rondleiding maakt ze; zonder plaatje tekenen we een vak met uitleg). */
    private static final Map<ResourceLocation, Boolean> PICTURE_EXISTS = new HashMap<>();

    /** Tekent alle overlays van dit moment in het vak ({@code x}, {@code y}, {@code w}, {@code h}). */
    static void render(GuiGraphics g, Font font, SceneRenderer renderer, ScenePlayer player, float time, int x, int y, int w,
                       int h) {
        List<int[]> taken = new ArrayList<>();
        List<HowToCue.Text> texts = new ArrayList<>();
        HowToCue.Picture picture = null;
        for (HowToCue cue : player.scene.cues()) {
            if (cue.tick() > time) {
                break;
            }
            switch (cue) {
                case HowToCue.Text text -> texts.add(text);
                case HowToCue.Click click -> click(g, font, renderer, click, time);
                case HowToCue.Meter meter -> meter(g, font, renderer, meter, time);
                case HowToCue.Picture p -> {
                    if (ScenePlayer.fade(time, p.tick(), p.duration()) > 0.0f) {
                        picture = p;
                    }
                }
                default -> {
                }
            }
        }
        // De tekstballonnen boven de muis en zijn voorwerp (dat tekent Minecraft een stukje naar voren): de tekst gaat voor.
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (HowToCue.Text text : texts) {
            text(g, font, renderer, text, time, x, y, w, h, taken);
        }
        g.pose().popPose();
        if (picture != null) {
            picture(g, font, picture, time, x, y, w, h);
        }
    }

    // ================================================================ Tekst

    /**
     * Maakt van een tekst met {@code *woorden*} en {@code [toetsen]} een tekst met kleuren: tussen sterretjes in het
     * cyaan van I.M.T, tussen haken (met de haken) in oranje, de rest wit.
     */
    static MutableComponent styled(String text) {
        MutableComponent out = Component.empty();
        StringBuilder part = new StringBuilder();
        int colour = HudStyle.WHITE;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '*') {
                flush(out, part, colour);
                colour = colour == HudStyle.CYAN ? HudStyle.WHITE : HudStyle.CYAN;
            } else if (c == '[' && colour == HudStyle.WHITE) {
                flush(out, part, colour);
                colour = HudStyle.ORANGE;
                part.append(c);
            } else if (c == ']' && colour == HudStyle.ORANGE) {
                part.append(c);
                flush(out, part, colour);
                colour = HudStyle.WHITE;
            } else {
                part.append(c);
            }
        }
        flush(out, part, colour);
        return out;
    }

    private static void flush(MutableComponent out, StringBuilder part, int colour) {
        if (!part.isEmpty()) {
            out.append(Component.literal(part.toString()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(colour))));
            part.setLength(0);
        }
    }

    private static void text(GuiGraphics g, Font font, SceneRenderer renderer, HowToCue.Text text, float time, int x, int y,
                             int w, int h, List<int[]> taken) {
        float fade = ScenePlayer.fade(time, text.tick(), text.duration());
        if (fade <= 0.0f) {
            return;
        }
        int alpha = Math.max(8, Math.round(fade * 255));
        int maxW = Math.max(110, Math.min(210, (int) (w * 0.42f)));
        List<FormattedCharSequence> lines = font.split(styled(text.text().getString()), maxW - 12);
        int bw = 12;
        for (FormattedCharSequence line : lines) {
            bw = Math.max(bw, font.width(line) + 12);
        }
        int bh = lines.size() * 10 + 7;
        float[] anchor = text.anchor() == null ? null : renderer.project(text.anchor());
        int bx;
        int by;
        if (anchor == null) {
            bx = x + (w - bw) / 2;
            by = y + 6;
        } else {
            bx = text.side() == HowToCue.Side.RIGHT ? x + w - bw - 6 : x + 6;
            by = Mth.clamp(Math.round(anchor[1]) - bh / 2, y + 6, y + h - bh - 6);
        }
        // Niet over een andere ballon heen: dan schuiven we hem eronder.
        for (int[] other : taken) {
            if (bx < other[2] && bx + bw > other[0] && by < other[3] + 3 && by + bh + 3 > other[1]) {
                by = other[3] + 4;
            }
        }
        taken.add(new int[] {bx, by, bx + bw, by + bh});
        int a = Math.round(fade * 235);
        if (anchor != null) {
            int fromX = text.side() == HowToCue.Side.RIGHT ? bx : bx + bw;
            line(g, fromX, by + bh / 2.0f, anchor[0], anchor[1], HudStyle.argb(Math.round(fade * 200), HudStyle.CYAN));
            dot(g, anchor[0], anchor[1], HudStyle.argb(alpha, HudStyle.CYAN));
        }
        g.fill(bx, by, bx + bw, by + bh, HudStyle.argb(a, PANEL));
        g.renderOutline(bx, by, bw, bh, HudStyle.argb(a, EDGE));
        // Het streepje van I.M.T aan de kant van het lijntje.
        int stripe = text.side() == HowToCue.Side.RIGHT || anchor == null ? bx : bx + bw - 2;
        g.fill(stripe, by, stripe + 2, by + bh, HudStyle.argb(alpha, HudStyle.CYAN));
        int ty = by + 4;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, bx + 6, ty, HudStyle.argb(alpha, HudStyle.WHITE), false);
            ty += 10;
        }
    }

    // ================================================================ De muis

    private static void click(GuiGraphics g, Font font, SceneRenderer renderer, HowToCue.Click click, float time) {
        float progress = ScenePlayer.progress(time, click.tick(), click.duration());
        if (progress < 0.0f) {
            return;
        }
        Vec3 at = ScenePlayer.along(click.path(), click.path().size() > 1 ? progress : 0.0f);
        float[] p = renderer.project(at);
        float fade = ScenePlayer.fade(time, click.tick(), click.duration());
        int alpha = Math.max(8, Math.round(fade * 255));
        // De knop is ingedrukt: bij een klik even (van 25% tot 65%), bij vasthouden de hele tijd.
        boolean pressed = click.hold() || (progress > 0.25f && progress < 0.65f);
        int cx = Math.round(p[0]);
        int cy = Math.round(p[1]);
        // Hoekjes rond de plek, die even samentrekken als je klikt.
        int r = pressed ? 4 : 6;
        HudStyle.brackets(g, cx - r, cy - r, cx + r + 1, cy + r + 1, 3, HudStyle.argb(alpha, pressed ? HudStyle.CYAN : HudStyle.WHITE));
        // Een kaartje rechtsonder: de muis, en wat je vasthoudt.
        int bx = cx + 9;
        int by = cy + 7;
        boolean item = !click.item().isEmpty();
        String sneak = click.sneak() ? "Sneak +" : "";
        int bw = 17 + (item ? 18 : 0) + (sneak.isEmpty() ? 0 : font.width(sneak) + 4);
        g.fill(bx, by, bx + bw, by + 20, HudStyle.argb(Math.round(fade * 220), PANEL));
        g.renderOutline(bx, by, bw, 20, HudStyle.argb(Math.round(fade * 220), EDGE));
        int mx = bx + 4;
        if (!sneak.isEmpty()) {
            g.drawString(font, sneak, mx, by + 6, HudStyle.argb(alpha, HudStyle.ORANGE), false);
            mx += font.width(sneak) + 4;
        }
        mouse(g, mx, by + 3, click.button(), pressed, alpha);
        if (item) {
            g.renderItem(click.item(), mx + 13, by + 2);
        }
    }

    /** Een muisje van 9 bij 14 pixels, met de ingedrukte knop in cyaan. */
    private static void mouse(GuiGraphics g, int x, int y, HowToCue.Button button, boolean pressed, int alpha) {
        int edge = HudStyle.argb(alpha, HudStyle.WHITE);
        int body = HudStyle.argb(alpha, 0x1C242C);
        g.fill(x + 1, y, x + 8, y + 14, edge);
        g.fill(x, y + 1, x + 9, y + 13, edge);
        g.fill(x + 1, y + 1, x + 8, y + 13, body);
        // De twee knoppen bovenaan, met een streepje ertussen en eronder.
        g.fill(x + 4, y + 1, x + 5, y + 6, edge);
        g.fill(x + 1, y + 6, x + 8, y + 7, edge);
        if (pressed) {
            int lit = HudStyle.argb(alpha, HudStyle.CYAN);
            if (button == HowToCue.Button.LEFT) {
                g.fill(x + 1, y + 1, x + 4, y + 6, lit);
            } else {
                g.fill(x + 5, y + 1, x + 8, y + 6, lit);
            }
        }
    }

    // ================================================================ Een balkje

    private static void meter(GuiGraphics g, Font font, SceneRenderer renderer, HowToCue.Meter meter, float time) {
        float fade = ScenePlayer.fade(time, meter.tick(), meter.duration());
        if (fade <= 0.0f) {
            return;
        }
        float progress = Math.max(0.0f, ScenePlayer.progress(time, meter.tick(), meter.duration()));
        // Vult in de eerste 70% van de tijd, en blijft dan even staan.
        float eased = Math.min(1.0f, progress / 0.7f);
        eased = 1.0f - (1.0f - eased) * (1.0f - eased);
        float value = meter.from() + (meter.to() - meter.from()) * eased;
        float[] p = renderer.project(meter.anchor());
        int alpha = Math.max(8, Math.round(fade * 255));
        String label = meter.label().getString();
        String percent = Math.round(value * meter.max()) + meter.unit();
        int bw = Math.max(64, font.width(label) + font.width(percent) + 16);
        int bx = Math.round(p[0]) - bw / 2;
        int by = Math.round(p[1]) - 24;
        g.fill(bx, by, bx + bw, by + 22, HudStyle.argb(Math.round(fade * 225), PANEL));
        g.renderOutline(bx, by, bw, 22, HudStyle.argb(Math.round(fade * 225), EDGE));
        g.drawString(font, label, bx + 5, by + 3, HudStyle.argb(alpha, meter.color()), false);
        g.drawString(font, percent, bx + bw - 5 - font.width(percent), by + 3, HudStyle.argb(alpha, HudStyle.WHITE), false);
        g.fill(bx + 5, by + 14, bx + bw - 5, by + 18, HudStyle.argb(alpha, 0x0A0E12));
        int fill = Math.round((bw - 10) * value);
        if (fill > 0) {
            g.fillGradient(bx + 5, by + 14, bx + 5 + fill, by + 18, HudStyle.argb(alpha, 0xFFE07F), HudStyle.argb(alpha, 0xB56B12));
        }
    }

    // ================================================================ Een plaatje van een echt scherm

    private static void picture(GuiGraphics g, Font font, HowToCue.Picture picture, float time, int x, int y, int w, int h) {
        float fade = ScenePlayer.fade(time, picture.tick(), picture.duration());
        float in = Math.min(1.0f, (time - picture.tick()) / 10.0f);
        float slide = (1.0f - (1.0f - (1.0f - in) * (1.0f - in))) * w * 0.6f;
        g.pose().pushPose();
        // Boven de voorwerpjes van de klikken en de tekstballonnen uit.
        g.pose().translate(0, 0, 500);
        g.fill(x, y, x + w, y + h, HudStyle.argb(Math.round(fade * 170), 0x04070A));
        // Zo groot mogelijk in de rechterkant, met de verhouding van het echte scherm.
        float ratio = picture.width() / (float) picture.height();
        int ph = Math.min(h - 16, Math.round(w * 0.6f / ratio));
        int pw = Math.round(ph * ratio);
        int px = x + w - pw - 8 + Math.round(slide);
        int py = y + (h - ph) / 2;
        int alpha = Math.max(8, Math.round(fade * 255));
        if (exists(picture.texture())) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(1.0f, 1.0f, 1.0f, fade);
            g.blit(picture.texture(), px, py, pw, ph, 0.0f, 0.0f, 1, 1, 1, 1);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            g.fill(px, py, px + pw, py + ph, HudStyle.argb(Math.round(fade * 240), PANEL));
            HudStyle.wrappedCentered(g, font, Component.literal("Picture of the screen is missing (gradlew runVisualCheck -Ponly=howtopics)"),
                    px + pw / 2, py + ph / 2 - 10, pw - 16, HudStyle.argb(alpha, HudStyle.ORANGE));
        }
        g.renderOutline(px - 1, py - 1, pw + 2, ph + 2, HudStyle.argb(alpha, HudStyle.CYAN));
        // De pijltjes: links de uitleg onder elkaar, met een lijntje naar de plek op het plaatje. Een voor een erbij.
        int labelW = Math.max(60, px - x - 26);
        List<List<FormattedCharSequence>> labels = new ArrayList<>();
        int total = 0;
        for (HowToCue.Arrow arrow : picture.arrows()) {
            List<FormattedCharSequence> lines = font.split(styled(arrow.label().getString()), labelW - 10);
            labels.add(lines);
            total += lines.size() * 10 + 8 + 6;
        }
        int ly = y + Math.max(6, (h - total) / 2);
        for (int i = 0; i < picture.arrows().size(); i++) {
            HowToCue.Arrow arrow = picture.arrows().get(i);
            List<FormattedCharSequence> lines = labels.get(i);
            float shown = ScenePlayer.fade(time, picture.tick() + 12 + i * 12, picture.duration() - 12 - i * 12);
            int bh = lines.size() * 10 + 8;
            if (shown > 0.0f) {
                int a = Math.max(8, Math.round(shown * fade * 255));
                int bw = 10;
                for (FormattedCharSequence line : lines) {
                    bw = Math.max(bw, font.width(line) + 10);
                }
                int bx = x + 8;
                g.fill(bx, ly, bx + bw, ly + bh, HudStyle.argb(Math.round(shown * fade * 235), PANEL));
                g.renderOutline(bx, ly, bw, bh, HudStyle.argb(Math.round(shown * fade * 235), EDGE));
                int ty = ly + 4;
                for (FormattedCharSequence line : lines) {
                    g.drawString(font, line, bx + 5, ty, HudStyle.argb(a, HudStyle.WHITE), false);
                    ty += 10;
                }
                float tx = px + arrow.x() * pw;
                float ty2 = py + arrow.y() * ph;
                line(g, bx + bw, ly + bh / 2.0f, tx, ty2, HudStyle.argb(a, HudStyle.CYAN));
                dot(g, tx, ty2, HudStyle.argb(a, HudStyle.CYAN));
            }
            ly += bh + 6;
        }
        g.pose().popPose();
    }

    private static boolean exists(ResourceLocation texture) {
        return PICTURE_EXISTS.computeIfAbsent(texture,
                key -> Minecraft.getInstance().getResourceManager().getResource(key).isPresent());
    }

    // ================================================================ Lijntjes en stippen

    /** Een lijntje van één pixel dik van ({@code x0}, {@code y0}) naar ({@code x1}, {@code y1}). */
    static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int colour) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5f) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0);
        g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        g.fill(0, 0, Math.round(length), 1, colour);
        g.pose().popPose();
    }

    /** Een stipje van 3 bij 3 met een donker randje. */
    static void dot(GuiGraphics g, float x, float y, int colour) {
        int cx = Math.round(x);
        int cy = Math.round(y);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, HudStyle.argb((colour >>> 24) * 3 / 4, 0x04070A));
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, colour);
    }
}
