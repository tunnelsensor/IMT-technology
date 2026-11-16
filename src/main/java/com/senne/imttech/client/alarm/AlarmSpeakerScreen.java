package com.senne.imttech.client.alarm;

import com.senne.imttech.alarm.AlarmSpeakerBlock;
import com.senne.imttech.alarm.AlarmSpeakerBlockEntity;
import com.senne.imttech.alarm.AlarmSpeakers;
import com.senne.imttech.alarm.audio.AlarmAudio;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.AlarmActionPayload;
import com.senne.imttech.network.AlarmOpenPayload;
import com.senne.imttech.network.AlarmSavePayload;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
 * Het scherm van een Alarm Speaker (hoofdstuk 126), dat je opent met sneak + rechtsklik met een lege hand (als je hem mag
 * instellen: zie AlarmSpeakers.open).
 *
 * <pre>
 *  ALARM SPEAKER                                    ● ONLINE
 *  Name    [Alarm 1                              ]
 *  Sound   siren.mp4 (12.4 s)
 *  [Choose file] [Folder] [Preview] [Siren]
 *  Or drag a sound file onto this window...  /  Uploading 64 % [====    ]
 *  [            Volume: 80 %                     ]
 *  [            Heard up to 32 blocks            ]
 *  [            On alarm: 30 s                   ]
 *  [Test] [Stop] [Use on all]
 *  on("Alarm 1")                        [Save] [Close]
 * </pre>
 *
 * Een bestand kiezen (Choose file, of slepen op het venster) leest het meteen in, in je eigen spel (AlarmFiles); pas Save
 * stuurt het naar de server (AlarmUploadClient), samen met de naam en de instellingen. Het scherm blijft open tot de
 * server "gelukt" zegt. Preview laat alleen jou het geluid horen.
 */
public final class AlarmSpeakerScreen extends Screen {
    static final int PANEL_W = 300;
    static final int PANEL_H = 226;
    private static final int MARGIN = 10;
    private static final int VALUE_X = 62;
    private static final int ROW_W = PANEL_W - 2 * MARGIN;
    /** Zo lang mag Preview hooguit klinken (ticks). */
    private static final int PREVIEW_TICKS = 120;

    private final BlockPos pos;
    private AlarmOpenPayload data;
    private int left;
    private int top;

    private @Nullable EditBox nameBox;
    private String typedName;
    private int volumePercent;
    private int range;
    private int alarmSeconds;

    private @Nullable short[] pendingSamples;
    private String pendingName = "";
    /** Hoeveel bestanden we nu inlezen, en welk het laatst gekozen is (alleen dat telt). */
    private int reading;
    private int readSeq;
    /** Knoppen die elke tick bijgewerkt worden (het venster, en de echte stand van de speaker). */
    private @Nullable Button chooseButton;
    private @Nullable Button testButton;
    private @Nullable Button stopButton;
    private boolean closeAfterUpload;
    /** De zin onder de knoppen (wat er gebeurt, of wat er mis is), en zijn kleur. */
    private Component note = Component.empty();
    private int noteColour = HudStyle.WHITE;
    private @Nullable AlarmPreviewSound preview;

    AlarmSpeakerScreen(AlarmOpenPayload data) {
        super(Component.translatable("screen.imttech.alarm_speaker"));
        this.pos = data.pos();
        this.data = data;
        this.typedName = data.name();
        this.volumePercent = Math.round(data.volume() * 100f);
        this.range = data.range();
        this.alarmSeconds = data.alarmSeconds();
    }

    BlockPos pos() {
        return pos;
    }

    /** Nieuwe gegevens van de server (na een knop, een upload, of Save). Wat je aan het typen bent, blijft staan. */
    void update(AlarmOpenPayload payload) {
        this.data = payload;
        rebuildWidgets();
    }

    /** De server zegt hoe de upload afliep. Gelukt en je drukte op Save: dan gaat het scherm dicht. */
    void uploadFinished(boolean ok, Component text) {
        note = text;
        noteColour = ok ? HudStyle.GREEN : HudStyle.ORANGE;
        if (ok) {
            pendingSamples = null;
            pendingName = "";
            if (closeAfterUpload) {
                // Het scherm gaat dicht: dan zie je het "gelukt" boven je hotbar.
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.displayClientMessage(text.copy().withStyle(net.minecraft.ChatFormatting.GREEN), true);
                }
                onClose();
                return;
            }
        }
        closeAfterUpload = false;
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
        boolean busy = busy();
        boolean uploads = data.uploadsAllowed();

        String keep = nameBox == null ? typedName : nameBox.getValue();
        nameBox = new EditBox(font, left + VALUE_X, top + 20, PANEL_W - VALUE_X - MARGIN, 18, Component.translatable("screen.imttech.alarm_speaker.name"));
        nameBox.setMaxLength(AlarmSpeakerBlockEntity.MAX_NAME);
        nameBox.setFilter(text -> text.indexOf('"') < 0 && text.indexOf('\\') < 0);
        nameBox.setValue(keep);
        nameBox.setResponder(text -> typedName = text);
        addRenderableWidget(nameBox);

        int x = left + MARGIN;
        int y = top + 58;
        Button choose = addRenderableWidget(Button.builder(Component.translatable("screen.imttech.alarm_speaker.choose"), b -> choose())
                .bounds(x, y, 74, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.imttech.alarm_speaker.choose.tooltip", data.maxSeconds())))
                .build());
        choose.active = uploads && !busy && !AlarmFiles.dialogOpen();
        chooseButton = choose;
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.alarm_speaker.folder"), b -> AlarmFiles.openFolder())
                .bounds(x + 78, y, 52, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.imttech.alarm_speaker.folder.tooltip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.alarm_speaker.preview"), b -> preview())
                .bounds(x + 134, y, 60, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.imttech.alarm_speaker.preview.tooltip")))
                .build());
        Button siren = addRenderableWidget(Button.builder(Component.translatable("screen.imttech.alarm_speaker.use_siren"), b -> useSiren())
                .bounds(x + 198, y, 82, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.imttech.alarm_speaker.use_siren.tooltip")))
                .build());
        siren.active = !busy && (pendingSamples != null || data.soundState() != AlarmOpenPayload.DEFAULT_SOUND);

        int maxRange = Math.max(AlarmSpeakerBlockEntity.MIN_RANGE, Math.round(ImtConfig.Alarms.MAX_RANGE));
        addRenderableWidget(new ValueSlider(x, top + 106, ROW_W, 2, 20, Math.max(2, Math.round(volumePercent / 5f)),
                step -> Component.translatable("screen.imttech.alarm_speaker.volume", step * 5), step -> volumePercent = step * 5));
        addRenderableWidget(new ValueSlider(x, top + 130, ROW_W, AlarmSpeakerBlockEntity.MIN_RANGE, maxRange, Mth.clamp(range,
                AlarmSpeakerBlockEntity.MIN_RANGE, maxRange), value -> Component.translatable("screen.imttech.alarm_speaker.range", value),
                value -> range = value));
        addRenderableWidget(Button.builder(lengthLabel(), b -> {
                    alarmSeconds = nextLength(alarmSeconds);
                    b.setMessage(lengthLabel());
                })
                .bounds(x, top + 154, ROW_W, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.imttech.alarm_speaker.length.tooltip")))
                .build());

        Button test = addRenderableWidget(Button.builder(Component.translatable("screen.imttech.alarm_speaker.test"),
                        b -> action(AlarmActionPayload.TEST))
                .bounds(x, top + 178, 64, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.imttech.alarm_speaker.test.tooltip", AlarmSpeakerBlockEntity.TEST_SECONDS)))
                .build());
        test.active = liveOnline();
        testButton = test;
        Button stop = addRenderableWidget(Button.builder(Component.translatable("screen.imttech.alarm_speaker.stop"),
                        b -> action(AlarmActionPayload.STOP))
                .bounds(x + 68, top + 178, 64, 20)
                .build());
        stop.active = liveSounding();
        stopButton = stop;
        Button all = addRenderableWidget(Button.builder(Component.translatable("screen.imttech.alarm_speaker.apply_all"),
                        b -> action(AlarmActionPayload.APPLY_ALL))
                .bounds(x + 136, top + 178, ROW_W - 136, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.imttech.alarm_speaker.apply_all.tooltip")))
                .build());
        all.active = !busy && data.soundState() == AlarmOpenPayload.CUSTOM_SOUND;

        Button save = addRenderableWidget(Button.builder(Component.translatable("screen.imttech.alarm_speaker.save"), b -> save())
                .bounds(left + PANEL_W - MARGIN - 124, top + PANEL_H - 24, 60, 20)
                .build());
        save.active = !busy;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - MARGIN - 60, top + PANEL_H - 24, 60, 20)
                .build());
    }

    private Component lengthLabel() {
        if (alarmSeconds <= 0) {
            return Component.translatable("screen.imttech.alarm_speaker.length.until_off");
        }
        String time = alarmSeconds % 60 == 0 ? (alarmSeconds / 60) + " min" : alarmSeconds + " s";
        return Component.translatable("screen.imttech.alarm_speaker.length", time);
    }

    /** De volgende keuze voor "hoe lang" (10 s, 30 s, 1 min, 2 min, 5 min, tot je hem uitzet), binnen het maximum van de server. */
    private static int nextLength(int current) {
        int[] choices = AlarmSpeakerBlockEntity.ALARM_CHOICES;
        int index = -1;
        for (int i = 0; i < choices.length; i++) {
            if (choices[i] == current) {
                index = i;
            }
        }
        for (int step = 1; step <= choices.length; step++) {
            int next = choices[(index + step + choices.length) % choices.length];
            if (next == 0 || next <= Math.round(ImtConfig.Alarms.MAX_ALARM_SECONDS)) {
                return next;
            }
        }
        return 0;
    }

    // ================================================================ Knoppen

    private void choose() {
        boolean opened = AlarmFiles.browse(file -> {
            if (minecraft == null || minecraft.screen != this) {
                return;
            }
            if (file != null) {
                readFile(file);
            } else if (note.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents key
                    && key.getKey().equals("screen.imttech.alarm_speaker.dialog_open")) {
                setNote(Component.empty(), HudStyle.WHITE); // geannuleerd: de uitleg over het venster mag weg
            }
        });
        // Het venster kan in volledig scherm achter Minecraft opengaan: zeg dat het er is, en dat slepen ook kan.
        setNote(Component.translatable("screen.imttech.alarm_speaker.dialog_open"), HudStyle.CYAN);
        if (opened && chooseButton != null) {
            chooseButton.active = false;
        }
    }

    /** Lezen we een bestand in, of loopt er een upload van deze speaker? Dan even geen nieuw bestand of Save. */
    private boolean busy() {
        return AlarmUploadClient.busyFor(pos) || reading > 0;
    }

    /** Een bestand dat je op het venster van Minecraft sleept (uit je Verkenner). */
    @Override
    public void onFilesDrop(List<Path> files) {
        if (!data.uploadsAllowed() || busy()) {
            return;
        }
        for (Path file : files) {
            if (AlarmFiles.looksLikeSound(file)) {
                readFile(file);
                return;
            }
        }
        setNote(Component.translatable("screen.imttech.alarm_speaker.not_sound"), HudStyle.ORANGE);
    }

    private void readFile(Path file) {
        if (!data.uploadsAllowed()) {
            setNote(Component.translatable("screen.imttech.alarm_speaker.uploads_off"), HudStyle.ORANGE);
            return;
        }
        reading++;
        int mine = ++readSeq;
        String name = file.getFileName() == null ? "sound" : file.getFileName().toString();
        setNote(Component.translatable("screen.imttech.alarm_speaker.reading", name), HudStyle.CYAN);
        rebuildWidgets();
        AlarmFiles.read(file, data.maxSeconds(), result -> {
            reading--;
            if (mine != readSeq) {
                return; // intussen koos je een ander bestand: dat telt
            }
            if (result.ok()) {
                pendingSamples = result.samples();
                pendingName = result.fileName();
                setNote(Component.translatable("screen.imttech.alarm_speaker.ready", pendingName, seconds(pendingSamples.length)), HudStyle.GREEN);
            } else if (result.error() != null) {
                setNote(Component.translatable("alarm.imttech.audio." + result.error().key, result.error().args), HudStyle.ORANGE);
            }
            if (minecraft != null && minecraft.screen == this) {
                rebuildWidgets();
            }
        });
    }

    /** Preview: het gekozen bestand, of het geluid dat er nu op staat (alleen jij hoort het). */
    private void preview() {
        stopPreview();
        short[] clip = pendingSamples;
        if (clip == null && data.soundState() == AlarmOpenPayload.CUSTOM_SOUND && minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity speaker) {
            clip = AlarmSoundCache.get(speaker.soundId());
            if (clip == null) {
                AlarmSoundCache.request(speaker.soundId());
                setNote(Component.translatable("screen.imttech.alarm_speaker.fetching"), HudStyle.CYAN);
                return;
            }
        }
        int ticks = clip == null ? 80 : Math.min(PREVIEW_TICKS, clip.length * 20 / AlarmAudio.RATE + 4);
        preview = new AlarmPreviewSound(clip, volumePercent / 100f, ticks);
        Minecraft.getInstance().getSoundManager().play(preview);
    }

    private void stopPreview() {
        if (preview != null) {
            Minecraft.getInstance().getSoundManager().stop(preview);
            preview = null;
        }
    }

    /** Weer de ingebouwde sirene: een gekozen bestand vergeten, en anders de server vragen het eigen geluid eraf te halen. */
    private void useSiren() {
        if (pendingSamples != null) {
            pendingSamples = null;
            pendingName = "";
            setNote(Component.empty(), HudStyle.WHITE);
            rebuildWidgets();
            return;
        }
        action(AlarmActionPayload.DEFAULT_SOUND);
    }

    private void action(int action) {
        PacketDistributor.sendToServer(new AlarmActionPayload(pos, action));
    }

    /** Save: naam en instellingen naar de server, en het gekozen bestand uploaden (dan blijft het scherm open tot het klaar is). */
    private void save() {
        String name = nameBox == null ? typedName : nameBox.getValue();
        PacketDistributor.sendToServer(new AlarmSavePayload(pos, name, volumePercent / 100f, range, alarmSeconds));
        if (pendingSamples != null) {
            closeAfterUpload = true;
            AlarmUploadClient.start(pos, pendingName, pendingSamples);
            setNote(AlarmUploadClient.message(), HudStyle.CYAN);
            rebuildWidgets();
        } else {
            onClose();
        }
    }

    private void setNote(Component text, int colour) {
        note = text;
        noteColour = colour;
    }

    private static String seconds(int samples) {
        return String.format(Locale.ROOT, "%.1f", samples / (float) AlarmAudio.RATE);
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

        int label = HudStyle.argb(190, HudStyle.WHITE);
        g.drawString(font, Component.translatable("screen.imttech.alarm_speaker.name"), left + MARGIN, top + 25, label, true);
        g.drawString(font, Component.translatable("screen.imttech.alarm_speaker.sound"), left + MARGIN, top + 45, label, true);
        g.drawString(font, fit(soundText(), PANEL_W - VALUE_X - MARGIN), left + VALUE_X, top + 45, soundColour(), false);

        // De regel onder de knoppen: de upload (met een balk), of wat er gebeurt, of hoe slepen werkt.
        int noteY = top + 84;
        if (AlarmUploadClient.busyFor(pos)) {
            Component sending = AlarmUploadClient.message();
            g.drawString(font, sending, left + MARGIN, noteY, HudStyle.argb(255, HudStyle.CYAN), false);
            HudStyle.bar(g, left + MARGIN, noteY + 11, ROW_W, 4, AlarmUploadClient.progress(), HudStyle.CYAN);
        } else {
            Component text = note.getString().isEmpty() ? hint() : note;
            int colour = note.getString().isEmpty() ? HudStyle.argb(150, 0x9FC9D6) : HudStyle.argb(255, noteColour);
            int lines = 0;
            for (FormattedCharSequence line : font.split(text, ROW_W)) {
                if (lines++ >= 2) {
                    break;
                }
                g.drawString(font, line, left + MARGIN, noteY + (lines - 1) * 10, colour, false);
            }
        }

        // Onderaan: hoe hij in een diagram heet.
        String name = nameBox == null ? typedName : nameBox.getValue();
        Component code = Component.literal("on").withColor(HudStyle.CYAN)
                .append(Component.literal("(").withColor(0xE8EEF2))
                .append(Component.literal("\"" + (name.isEmpty() ? data.name() : name) + "\"").withColor(0x6BE88B))
                .append(Component.literal(")").withColor(0xE8EEF2));
        int room = PANEL_W - 2 * MARGIN - 128;
        g.drawString(font, font.width(code) > room ? Component.literal(fit(code.getString(), room)).withColor(0x6BE88B) : code,
                left + MARGIN, top + PANEL_H - 18, HudStyle.argb(255, HudStyle.WHITE), false);
    }

    private Component hint() {
        return data.uploadsAllowed()
                ? Component.translatable("screen.imttech.alarm_speaker.drop", data.maxSeconds())
                : Component.translatable("screen.imttech.alarm_speaker.uploads_off");
    }

    private String soundText() {
        if (pendingSamples != null) {
            return Component.translatable("screen.imttech.alarm_speaker.pending", pendingName, seconds(pendingSamples.length)).getString();
        }
        return switch (data.soundState()) {
            case AlarmOpenPayload.CUSTOM_SOUND -> Component.translatable("screen.imttech.alarm_speaker.custom", data.soundName(),
                    String.format(Locale.ROOT, "%.1f", data.soundSeconds())).getString();
            case AlarmOpenPayload.MISSING_SOUND -> Component.translatable("screen.imttech.alarm_speaker.missing", data.soundName()).getString();
            default -> Component.translatable("screen.imttech.alarm_speaker.siren").getString();
        };
    }

    private int soundColour() {
        if (pendingSamples != null) {
            return HudStyle.argb(255, HudStyle.GREEN);
        }
        return data.soundState() == AlarmOpenPayload.MISSING_SOUND ? HudStyle.argb(255, HudStyle.ORANGE) : HudStyle.argb(255, HudStyle.WHITE);
    }

    /**
     * De status rechtsboven volgt de echte stand van de speaker (de blokstand reist vanzelf mee naar je spel), niet die van
     * toen het scherm openging: begint hij te loeien, of valt de stroom weg, dan zie je dat meteen. (Gevonden door de
     * nalezing.) Waarom hij offline is (geen stroom, geen netwerk, EMP), weet alleen de server: dat komt mee als het scherm
     * opent of bijgewerkt wordt; ging hij pas later uit, dan staat er OFFLINE.
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
        // Wat buiten het scherm verandert: het venster ging dicht, de speaker begon te loeien of viel uit.
        if (chooseButton != null) {
            chooseButton.active = data.uploadsAllowed() && !busy() && !AlarmFiles.dialogOpen();
        }
        if (testButton != null) {
            testButton.active = liveOnline();
        }
        if (stopButton != null) {
            stopButton.active = liveSounding();
        }
    }

    /**
     * Staat de speaker er nog, en sta je er nog bij? Even ver als de server toelaat (AlarmSpeakers.REACH): verder weg zou
     * Save niets meer doen. (Gevonden door de nalezing: hier stond eerst 8 blokken.)
     */
    private boolean stillThere() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.level.getBlockState(pos).getBlock() instanceof AlarmSpeakerBlock
                && mc.player.distanceToSqr(pos.getCenter()) <= AlarmSpeakers.REACH * AlarmSpeakers.REACH;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && nameBox != null && nameBox.isFocused() && !busy()) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        stopPreview();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================ Voor de rondleiding (zonder het venster van je computer)

    /** Doe alsof je dit bestand koos (de rondleiding heeft geen venster). */
    public void tourPending(String fileName, short[] samples) {
        pendingSamples = samples;
        pendingName = fileName;
        setNote(Component.translatable("screen.imttech.alarm_speaker.ready", fileName, seconds(samples.length)), HudStyle.GREEN);
        rebuildWidgets();
    }

    /** Druk op Save. */
    public void tourSave() {
        save();
    }

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
