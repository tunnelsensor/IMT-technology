package com.senne.imttech.client.alarm;

import com.senne.imttech.alarm.AlarmSpeakerBlock;
import com.senne.imttech.alarm.AlarmSpeakerBlockEntity;
import com.senne.imttech.alarm.AlarmSpeakers;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.AlarmActionPayload;
import com.senne.imttech.network.AlarmOpenPayload;
import com.senne.imttech.network.AlarmSavePayload;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het scherm van een Speaker (hoofdstuk 126; sinds hoofdstuk 136 zonder eigen geluid), dat je opent met sneak + rechtsklik
 * met een lege hand (als je hem mag instellen: zie AlarmSpeakers.open).
 *
 * <pre>
 *  SPEAKER                                          ● ONLINE
 *  Name    [Speaker 1                            ]
 *  [            Volume: 80 %                     ]
 *  [            Heard up to 32 blocks            ]
 *  [Test]
 *  Sounds are in Files > sounds of your security core...
 *  playsound("siren", "Speaker 1")
 *                                         [Save] [Done]
 * </pre>
 *
 * Wat hij speelt, kies je niet hier maar in een diagram (jouw wens van 17 november): {@code playsound("siren", "Speaker 1")}
 * speelt een geluid uit de map sounds in Files van de kern, {@code playtext("Alarm armed", "Speaker 1")} zegt een zin met de
 * stem van I.M.T. Test laat hem een paar seconden de ingebouwde sirene spelen, zodat je hoort hoe ver hij reikt.
 */
public final class AlarmSpeakerScreen extends Screen {
    static final int PANEL_W = 300;
    static final int PANEL_H = 186;
    private static final int MARGIN = 10;
    private static final int VALUE_X = 62;
    private static final int ROW_W = PANEL_W - 2 * MARGIN;

    private final BlockPos pos;
    private AlarmOpenPayload data;
    private int left;
    private int top;

    private @Nullable EditBox nameBox;
    private String typedName;
    private int volumePercent;
    private int range;
    private @Nullable Button testButton;

    AlarmSpeakerScreen(AlarmOpenPayload data) {
        super(Component.translatable("screen.imttech.alarm_speaker"));
        this.pos = data.pos();
        this.data = data;
        this.typedName = data.name();
        this.volumePercent = Math.round(data.volume() * 100f);
        this.range = data.range();
    }

    BlockPos pos() {
        return pos;
    }

    /** Nieuwe gegevens van de server (na een knop of Save). Wat je aan het typen bent, blijft staan. */
    void update(AlarmOpenPayload payload) {
        this.data = payload;
        rebuildWidgets();
    }

    // ================================================================ Opbouw

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - PANEL_H) / 2);
        if (!stillThere()) {
            onClose();
            return;
        }
        String keep = nameBox == null ? typedName : nameBox.getValue();
        nameBox = new EditBox(font, left + VALUE_X, top + 20, PANEL_W - VALUE_X - MARGIN, 18, Component.translatable("screen.imttech.alarm_speaker.name"));
        nameBox.setMaxLength(AlarmSpeakerBlockEntity.MAX_NAME);
        nameBox.setFilter(text -> text.indexOf('"') < 0 && text.indexOf('\\') < 0);
        nameBox.setValue(keep);
        nameBox.setResponder(text -> typedName = text);
        addRenderableWidget(nameBox);

        int x = left + MARGIN;
        int maxRange = Math.max(AlarmSpeakerBlockEntity.MIN_RANGE, Math.round(ImtConfig.Alarms.MAX_RANGE));
        addRenderableWidget(new ValueSlider(x, top + 46, ROW_W, 2, 20, Math.max(2, Math.round(volumePercent / 5f)),
                step -> Component.translatable("screen.imttech.alarm_speaker.volume", step * 5), step -> volumePercent = step * 5));
        addRenderableWidget(new ValueSlider(x, top + 70, ROW_W, AlarmSpeakerBlockEntity.MIN_RANGE, maxRange, Mth.clamp(range,
                AlarmSpeakerBlockEntity.MIN_RANGE, maxRange), value -> Component.translatable("screen.imttech.alarm_speaker.range", value),
                value -> range = value));
        Button test = addRenderableWidget(Button.builder(Component.translatable("screen.imttech.alarm_speaker.test"),
                        b -> PacketDistributor.sendToServer(new AlarmActionPayload(pos, AlarmActionPayload.TEST)))
                .bounds(x, top + 94, 64, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.imttech.alarm_speaker.test.tooltip", AlarmSpeakerBlockEntity.TEST_SECONDS)))
                .build());
        test.active = liveOnline();
        testButton = test;

        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.alarm_speaker.save"), b -> save())
                .bounds(left + PANEL_W - MARGIN - 124, top + PANEL_H - 24, 60, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - MARGIN - 60, top + PANEL_H - 24, 60, 20)
                .build());
    }

    // ================================================================ Knoppen

    /** Save: naam, volume en bereik naar de server; daarna dicht. */
    private void save() {
        String name = nameBox == null ? typedName : nameBox.getValue();
        PacketDistributor.sendToServer(new AlarmSavePayload(pos, name, volumePercent / 100f, range));
        onClose();
    }

    // ================================================================ Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(205, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left + MARGIN, top + 7, HudStyle.argb(255, HudStyle.CYAN), true);
        Component status = statusText();
        g.drawString(font, status, left + PANEL_W - MARGIN - font.width(status), top + 7, statusColour(), true);
        g.drawString(font, Component.translatable("screen.imttech.alarm_speaker.name"), left + MARGIN, top + 25,
                HudStyle.argb(190, HudStyle.WHITE), true);

        // De uitleg: waar de geluiden staan, en hoe een diagram hem laat spelen (hooguit drie regels).
        int lines = 0;
        for (FormattedCharSequence line : font.split(Component.translatable("screen.imttech.alarm_speaker.hint"), ROW_W)) {
            if (lines++ >= 3) {
                break;
            }
            g.drawString(font, line, left + MARGIN, top + 120 + (lines - 1) * 10, HudStyle.argb(170, 0x9FC9D6), false);
        }

        // Onderaan: hoe hij in een diagram heet.
        String name = nameBox == null ? typedName : nameBox.getValue();
        String shown = name.isEmpty() ? data.name() : name;
        Component code = Component.literal("playsound").withColor(HudStyle.CYAN)
                .append(Component.literal("(").withColor(0xE8EEF2))
                .append(Component.literal("\"siren\"").withColor(0x6BE88B))
                .append(Component.literal(", ").withColor(0xE8EEF2))
                .append(Component.literal("\"" + shown + "\"").withColor(0x6BE88B))
                .append(Component.literal(")").withColor(0xE8EEF2));
        // Op een eigen regel boven Save en Done (gevonden door de nalezing: ernaast werd de naam afgekapt).
        g.drawString(font, font.width(code) > ROW_W ? Component.literal(fit(code.getString(), ROW_W)).withColor(0x6BE88B) : code,
                left + MARGIN, top + 145, HudStyle.argb(255, HudStyle.WHITE), false);
    }

    /**
     * De status rechtsboven volgt de echte stand van de speaker (de blokstand reist vanzelf mee naar je spel). Waarom hij
     * offline is (geen stroom, geen netwerk, EMP), weet alleen de server: dat komt mee als het scherm opent; ging hij pas later
     * uit, dan staat er OFFLINE.
     */
    private Component statusText() {
        if (liveSounding()) {
            return Component.translatable("screen.imttech.alarm_speaker.status.sounding");
        }
        if (liveOnline()) {
            return Component.translatable("screen.imttech.alarm_speaker.status.online");
        }
        return Component.translatable(switch (data.status()) {
            case AlarmOpenPayload.NO_POWER -> "screen.imttech.alarm_speaker.status.no_power";
            case AlarmOpenPayload.NO_NETWORK -> "screen.imttech.alarm_speaker.status.no_network";
            case AlarmOpenPayload.EMP -> "screen.imttech.alarm_speaker.status.emp";
            default -> "screen.imttech.alarm_speaker.status.offline";
        });
    }

    private int statusColour() {
        if (liveSounding()) {
            return HudStyle.argb(255, HudStyle.ORANGE);
        }
        return HudStyle.argb(255, liveOnline() ? HudStyle.GREEN : HudStyle.RED);
    }

    /** De blokstand van de speaker in je spel, of null als hij er niet (meer) staat. */
    private @Nullable BlockState liveState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        BlockState state = mc.level.getBlockState(pos);
        return state.getBlock() instanceof AlarmSpeakerBlock ? state : null;
    }

    private boolean liveSounding() {
        BlockState state = liveState();
        return state != null ? state.getValue(AlarmSpeakerBlock.SOUNDING) : data.sounding();
    }

    private boolean liveOnline() {
        BlockState state = liveState();
        return state != null ? state.getValue(AlarmSpeakerBlock.ONLINE) : data.status() == AlarmOpenPayload.ONLINE;
    }

    /** Een tekst die te breed is, korter maken met "..." erachter. */
    private String fit(String text, int room) {
        if (font.width(text) <= room) {
            return text;
        }
        String dots = "...";
        return font.plainSubstrByWidth(text, Math.max(0, room - font.width(dots))) + dots;
    }

    // ================================================================ Elke tick, toetsen, sluiten

    @Override
    public void tick() {
        super.tick();
        if (!stillThere()) {
            onClose();
            return;
        }
        if (testButton != null) {
            testButton.active = liveOnline();
        }
    }

    /** Staat de speaker er nog, en sta je er nog bij? Even ver als de server toelaat (AlarmSpeakers.REACH). */
    private boolean stillThere() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.level.getBlockState(pos).getBlock() instanceof AlarmSpeakerBlock
                && mc.player.distanceToSqr(pos.getCenter()) <= AlarmSpeakers.REACH * AlarmSpeakers.REACH;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && nameBox != null && nameBox.isFocused()) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================ Voor de rondleiding

    /** Haal de focus van het naamveld (anders staat er een knipperende streep in de foto). */
    public void tourUnfocus() {
        setFocused(null);
        if (nameBox != null) {
            nameBox.setFocused(false);
        }
    }

    // ================================================================ Schuifbalk

    /** Een schuifbalk met hele getallen (zoals die van de Arc Lamp). */
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
