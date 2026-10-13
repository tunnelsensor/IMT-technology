package com.senne.starktech.client.light;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.light.ArcLampBlock;
import com.senne.starktech.light.ArcLampBlockEntity;
import com.senne.starktech.network.LampActionPayload;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het instelscherm van een Arc Lamp (10 oktober: "zorg dat je de brightness kunt instellen"). Openen:
 * rechtsklik op de lamp.
 *
 * <b>Brightness</b>: zijn lichtniveau, van 1 tot 100 (of wat de config toestaat). Tot 15 is het een gewone
 * lamp; daarboven reikt het licht verder: tot "niveau min 15" blokken vol licht, en op "niveau" blokken is
 * het donker. Zachter gebruikt ook minder stroom. (Tot 13 oktober was er ook een schuifje "Glow" voor de
 * gloed om de lamp; die gloed is weg.)
 *
 * Het schuifje heeft een min- en een plusknop ernaast (voor één stapje). Wat je kiest gaat meteen naar de
 * lamp, dus je ziet het licht veranderen terwijl je schuift; er is geen "opslaan". Een ander licht dan 15
 * of minder meet de lamp opnieuw zodra je even stopt met schuiven (een halve seconde).
 */
public class ArcLampScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 146;
    private static final int MARGIN = 8;
    /** Waar de schuifjes beginnen (de naam van de regel staat links daarvan). */
    private static final int VALUE_X = 84;
    private static final int STEP_W = 20;
    private static final int DONE_W = 80;
    /** Hoeveel een klik op min of plus verschuift. */
    private static final int BRIGHTNESS_STEP = 1;

    private final BlockPos pos;
    private int left;
    private int top;
    private @Nullable ValueSlider brightness;

    public ArcLampScreen(BlockPos pos) {
        super(Component.translatable("screen.starktech.lamp"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** De lamp waar dit scherm bij hoort, of null als hij weg is. */
    private @Nullable ArcLampBlockEntity lamp() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof ArcLampBlockEntity lamp ? lamp : null;
    }

    private boolean lit() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockState(pos).hasProperty(ArcLampBlock.LIT)
                && mc.level.getBlockState(pos).getValue(ArcLampBlock.LIT);
    }

    // ---------------------------------------------------------------- Waar alles staat

    private int brightnessY() {
        return top + 24;
    }

    private int footerY() {
        return top + PANEL_H - 28;
    }

    // ---------------------------------------------------------------- Opbouw

    @Override
    protected void init() {
        ArcLampBlockEntity lamp = lamp();
        if (lamp == null) {
            onClose();
            return;
        }
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - PANEL_H) / 2);
        int valueX = left + VALUE_X;
        int sliderW = PANEL_W - VALUE_X - MARGIN - 2 * (STEP_W + 4);

        brightness = new ValueSlider(valueX + STEP_W + 4, brightnessY(), sliderW, 1, Math.round(StarkConfig.Lights.ARC_LAMP_MAX_LEVEL),
                lamp.brightness(), value -> Component.translatable("screen.starktech.lamp.level_value", value),
                value -> send(LampActionPayload.Op.BRIGHTNESS, value));
        addStepButtons(brightness, brightnessY(), BRIGHTNESS_STEP);
        addRenderableWidget(brightness);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - MARGIN - DONE_W, footerY(), DONE_W, 20).build());
        // Links van Done: stroom per tick, seconde, minuut of uur.
        addRenderableWidget(com.senne.starktech.client.PowerUnit.button(
                left + PANEL_W - MARGIN - DONE_W - 4 - 50, footerY(), 50, 20));
    }

    /** Een min-knop links en een plus-knop rechts van een schuifje. */
    private void addStepButtons(ValueSlider slider, int y, int step) {
        addRenderableWidget(Button.builder(Component.literal("-"), b -> slider.set(slider.current() - step))
                .bounds(left + VALUE_X, y, STEP_W, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> slider.set(slider.current() + step))
                .bounds(left + PANEL_W - MARGIN - STEP_W, y, STEP_W, 20).build());
    }

    private void send(LampActionPayload.Op op, int value) {
        PacketDistributor.sendToServer(new LampActionPayload(pos, op, value));
    }

    /** De lamp is weg (afgebroken): dan het scherm dicht. */
    @Override
    public void tick() {
        super.tick();
        if (lamp() == null) {
            onClose();
        }
    }

    // ---------------------------------------------------------------- Voor de showcase en de screenshots

    /** Zet het lichtniveau, alsof je het schuifje daarheen schoof. */
    public void tourBrightness(int value) {
        if (brightness != null) {
            brightness.set(value);
        }
    }

    // ---------------------------------------------------------------- Tekenen

    /** Een scherp paneel met een lichte verduistering (zoals de holotafel), zodat je de lamp erachter ziet. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(205, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        if (brightness == null) {
            return;
        }
        super.render(g, mouseX, mouseY, a);
        g.drawString(font, title, left + MARGIN, top + 7, HudStyle.argb(255, HudStyle.CYAN), true);
        // Rechtsboven: brandt hij, of heeft hij geen stroom?
        Component status = Component.translatable(lit() ? "screen.starktech.lamp.on" : "screen.starktech.lamp.no_power");
        g.drawString(font, status, left + PANEL_W - MARGIN - font.width(status), top + 7,
                HudStyle.argb(255, lit() ? HudStyle.GREEN : HudStyle.RED), true);

        label(g, "screen.starktech.lamp.brightness", brightnessY());
        int level = brightness.current();
        int reach = Math.max(0, level - 15);
        Component range = reach > 0
                ? Component.translatable("screen.starktech.lamp.reach", reach, level)
                : Component.translatable("screen.starktech.lamp.reach_small", level);
        Component use = com.senne.starktech.client.PowerUnit.current().rate(ArcLampBlockEntity.powerPerTick(level));
        int y = hint(g, range, brightnessY() + 25);
        y = hint(g, Component.translatable("screen.starktech.lamp.power", use), y);
        // 13 oktober: het licht gaat in rechte lijnen, dus elke kamer heeft zijn eigen lamp nodig.
        hint(g, Component.translatable("screen.starktech.lamp.rooms"), y + 5);
    }

    private void label(GuiGraphics g, String key, int y) {
        g.drawString(font, Component.translatable(key), left + MARGIN, y + 6, HudStyle.argb(190, HudStyle.WHITE), true);
    }

    /** Uitleg onder een schuifje, zo nodig over meer regels (van 11 hoog). Geeft de y onder de laatste regel. */
    private int hint(GuiGraphics g, Component text, int y) {
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, PANEL_W - VALUE_X - MARGIN)) {
            g.drawString(font, line, left + VALUE_X, y, HudStyle.argb(150, HudStyle.WHITE), false);
            y += 11;
        }
        return y;
    }

    /**
     * Een schuifje voor een heel getal van {@code min} tot {@code max}. Minecraft geeft een schuifje een
     * waarde van 0 tot 1; wij rekenen die om naar het getal, zetten het in de tekst, en geven een nieuw
     * getal meteen door.
     */
    private static final class ValueSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final IntFunction<Component> label;
        private final IntConsumer onChange;
        private int current;

        ValueSlider(int x, int y, int width, int min, int max, int start, IntFunction<Component> label, IntConsumer onChange) {
            super(x, y, width, 20, Component.empty(), max > min ? (Mth.clamp(start, min, max) - min) / (double) (max - min) : 0.0);
            this.min = min;
            this.max = max;
            this.label = label;
            this.onChange = onChange;
            this.current = Mth.clamp(start, min, max);
            updateMessage();
        }

        int current() {
            return current;
        }

        /** Naar dit getal (van de min- of plusknop). */
        void set(int target) {
            int clamped = Mth.clamp(target, min, max);
            value = max > min ? (clamped - min) / (double) (max - min) : 0.0;
            applyValue();
            updateMessage();
        }

        private int fromValue() {
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(label.apply(fromValue()));
        }

        @Override
        protected void applyValue() {
            int now = fromValue();
            if (now != current) {
                current = now;
                onChange.accept(now);
            }
        }
    }
}
