package com.senne.imttech.client.training;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.network.TrainingActionPayload;
import com.senne.imttech.training.Training;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het instelscherm van een Training Start-blok (hoofdstuk 26). Openen: sneak + rechtsklik met een lege hand, als jij
 * het neerzette of als je in deze basis vertrouwd bent (de server kijkt dat na, zie training/TrainingBlocks).
 *
 * <pre>
 *  Training Start
 *  Name   [Bootcamp            ]
 *  Kind   [Training      ]        your robots and drones attack you
 *  Shots  [Training shots]        geen schade: een leven eraf
 *  Lives  [-]  1  [+]
 *  Time   [-]  no limit  [+]
 *  Klik het blok (zonder sneak) om te beginnen: jij bent de trainee.
 *  [Save]                                  [Cancel]
 * </pre>
 * Kind (hoofdstuk 27): een gewone training, of een oefening waarin je hele basis jou als indringer behandelt: een
 * inbraak (hack de kern) of een uitbraak (haal het doel buiten je cel).
 */
public class TrainingStartScreen extends Screen {
    private static final int PANEL_W = 316;
    private static final int PANEL_H = 204;
    private static final int MARGIN = 8;
    private static final int VALUE_X = 58;
    /** De rijen (vanaf de bovenkant van het paneel; de tekst staat op deze hoogte, de knoppen 6 hoger). */
    private static final int ROW_NAME = 24;
    private static final int ROW_KIND = 54;
    private static final int ROW_SHOTS = 78;
    private static final int ROW_LIVES = 104;
    private static final int ROW_TIME = 130;
    /** De stappen van de tijdslimiet (seconden; 0 = geen limiet). */
    private static final int[] TIMES = {0, 30, 60, 90, 120, 180, 300, 600, 900, 1800, 3600};

    private final BlockPos pos;
    private String name;
    private Training.Mode mode;
    private Training.Kind kind;
    private int lives;
    private int seconds;
    private int left;
    private int top;
    private @Nullable EditBox nameBox;
    private @Nullable Button modeButton;
    private @Nullable Button kindButton;

    public TrainingStartScreen(BlockPos pos, Training.Settings settings) {
        super(Component.literal("Training Start"));
        this.pos = pos;
        this.name = settings.name();
        this.mode = settings.mode();
        this.kind = settings.kind();
        this.lives = settings.lives();
        this.seconds = settings.seconds();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - PANEL_H) / 2);
        nameBox = addRenderableWidget(new EditBox(font, left + VALUE_X, top + ROW_NAME, 150, 18, Component.literal("Name")));
        nameBox.setMaxLength(Training.Settings.MAX_NAME);
        nameBox.setFilter(value -> value.indexOf('"') < 0);
        nameBox.setValue(name);
        nameBox.setResponder(value -> name = value);
        // De soort (hoofdstuk 27): Training, dan Break-in drill, dan Break-out drill, en weer van voren af aan.
        kindButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            kind = Training.Kind.values()[(kind.ordinal() + 1) % Training.Kind.values().length];
            refresh();
        }).bounds(left + VALUE_X, top + ROW_KIND - 6, 100, 20).build());
        modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            mode = mode == Training.Mode.TRAINING_SHOTS ? Training.Mode.LETHAL : Training.Mode.TRAINING_SHOTS;
            refresh();
        }).bounds(left + VALUE_X, top + ROW_SHOTS - 6, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> lives = Math.max(1, lives - 1))
                .bounds(left + VALUE_X, top + ROW_LIVES - 6, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> lives = Math.min(Training.Settings.MAX_LIVES, lives + 1))
                .bounds(left + VALUE_X + 70, top + ROW_LIVES - 6, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> seconds = step(-1))
                .bounds(left + VALUE_X, top + ROW_TIME - 6, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> seconds = step(1))
                .bounds(left + VALUE_X + 70, top + ROW_TIME - 6, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(left + MARGIN, top + PANEL_H - 28, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(left + PANEL_W - MARGIN - 70, top + PANEL_H - 28, 70, 20).build());
        refresh();
    }

    /** De volgende (of vorige) stap van de tijdslimiet. */
    private int step(int direction) {
        int index = 0;
        for (int i = 0; i < TIMES.length; i++) {
            if (TIMES[i] <= seconds) {
                index = i;
            }
        }
        return TIMES[Math.max(0, Math.min(TIMES.length - 1, index + direction))];
    }

    private void refresh() {
        if (modeButton != null) {
            modeButton.setMessage(Component.literal(mode.title()));
        }
        if (kindButton != null) {
            kindButton.setMessage(Component.literal(kind.title()));
        }
    }

    private void save() {
        Training.Settings settings = new Training.Settings(name, mode, lives, seconds, kind).clean();
        PacketDistributor.sendToServer(new TrainingActionPayload(pos, settings.save()));
        onClose();
    }

    /** Enter in het naamvak = Save. */
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (nameBox != null && nameBox.isFocused() && (key == 257 || key == 335)) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(205, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);
        g.drawString(font, "Training Start", left + MARGIN, top + 6, HudStyle.argb(255, HudStyle.CYAN), true);
        label(g, "Name", top + ROW_NAME + 5);
        label(g, "Kind", top + ROW_KIND);
        String kindHint = switch (kind) {
            case TRAINING -> "your robots attack you";
            case BREAK_IN -> "you are the intruder";
            case BREAK_OUT -> "escape to the goal";
        };
        g.drawString(font, kindHint, left + VALUE_X + 106, top + ROW_KIND,
                HudStyle.argb(200, kind.drill() ? HudStyle.ORANGE : 0x9FC9D6), false);
        label(g, "Shots", top + ROW_SHOTS);
        g.drawString(font, mode == Training.Mode.TRAINING_SHOTS ? "no damage: a hit costs a life" : "real damage",
                left + VALUE_X + 106, top + ROW_SHOTS, HudStyle.argb(200, mode == Training.Mode.LETHAL ? HudStyle.RED : 0x9FC9D6), false);
        label(g, "Lives", top + ROW_LIVES);
        centred(g, String.valueOf(lives), left + VALUE_X + 45, top + ROW_LIVES);
        label(g, "Time", top + ROW_TIME);
        centred(g, seconds == 0 ? "no limit" : seconds / 60 + ":" + String.format("%02d", seconds % 60), left + VALUE_X + 45,
                top + ROW_TIME);
        String start = switch (kind) {
            case TRAINING -> "Click the block to start: you are the trainee.";
            case BREAK_IN -> "Click to start (survival): hack your own core.";
            case BREAK_OUT -> "Click to start (survival): escape to the goal.";
        };
        g.drawString(font, start, left + MARGIN, top + 150, HudStyle.argb(200, 0x9FC9D6), false);
        g.drawString(font, "Diagrams: when training_started(\"" + name + "\"):", left + MARGIN, top + 161,
                HudStyle.argb(170, 0x9FC9D6), false);
    }

    private void label(GuiGraphics g, String text, int y) {
        g.drawString(font, text, left + MARGIN, y, HudStyle.argb(190, HudStyle.WHITE), true);
    }

    private void centred(GuiGraphics g, String text, int x, int y) {
        g.drawString(font, text, x - font.width(text) / 2, y, HudStyle.argb(240, HudStyle.WHITE), false);
    }
}
