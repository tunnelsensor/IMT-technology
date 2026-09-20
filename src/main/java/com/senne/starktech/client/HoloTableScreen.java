package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.holo.HoloMode;
import com.senne.starktech.holo.HoloSettings;
import com.senne.starktech.holo.HoloTableBlock;
import com.senne.starktech.holo.HoloTableBlockEntity;
import com.senne.starktech.network.HoloActionPayload;
import com.senne.starktech.shield.ShieldColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het instelscherm van de holotafel.
 *
 * Alles wat je hier kiest gaat meteen naar de tafel, zodat je het hologram achter het scherm
 * ziet veranderen terwijl je klikt. Er is dus geen "opslaan"-knop.
 *
 * Openen: rechtsklik op de tafel met een lege hand (met de bril op).
 */
public class HoloTableScreen extends Screen {
    private static final int PANEL_W = 268;
    private static final int PANEL_H = 190;
    /** Hoogte van één regel met een knop erin. */
    private static final int ROW = 24;
    private static final int MARGIN = 8;
    /** Waar de knoppen van de instellingen beginnen (de tekst ervoor staat links). */
    private static final int VALUE_X = 96;
    private static final int VALUE_W = 84;

    private final BlockPos pos;
    private int left;
    private int top;
    /** De y van elke regel, uitgerekend in init() en gebruikt bij het tekenen. */
    private int modeY;
    private int colourY;
    private int rangeY;
    private int scaleY;
    private int spinY;
    private int clearY;

    public HoloTableScreen(BlockPos pos) {
        super(Component.translatable("screen.starktech.holo"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** De tafel waar dit scherm bij hoort, of null als hij weg is. */
    private @Nullable HoloTableBlockEntity table() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof HoloTableBlockEntity table ? table : null;
    }

    private ShieldColor colour() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? ShieldColor.CYAN : mc.level.getBlockState(pos).getValue(HoloTableBlock.COLOR);
    }

    // ---------------------------------------------------------------- Opbouw

    @Override
    protected void init() {
        HoloTableBlockEntity table = table();
        if (table == null) {
            onClose();
            return;
        }
        left = (width - PANEL_W) / 2;
        top = Math.max(10, (height - PANEL_H) / 2);

        modeY = top + 30;
        colourY = modeY + ROW + 12;
        rangeY = colourY + ROW + 12;
        scaleY = rangeY + ROW;
        spinY = scaleY + ROW;
        clearY = spinY + ROW;

        // Wat hij laat zien: één knop per stand, de huidige staat uit.
        int modeW = (PANEL_W - MARGIN * 2 - 8) / HoloMode.values().length;
        int x = left + MARGIN;
        for (HoloMode mode : HoloMode.values()) {
            Button button = Button.builder(mode.label(), b -> send(HoloActionPayload.Op.MODE, mode.ordinal()))
                    .bounds(x, modeY, modeW, 20).build();
            button.active = table.mode() != mode;
            addRenderableWidget(button);
            x += modeW + 4;
        }

        // De kleuren: een rij knopjes met een gekleurd vlakje erin (zie render).
        x = left + MARGIN;
        for (ShieldColor option : ShieldColor.values()) {
            Button button = Button.builder(Component.empty(),
                            b -> send(HoloActionPayload.Op.COLOUR, option.ordinal()))
                    .bounds(x, colourY, 24, 20).build();
            button.active = colour() != option;
            addRenderableWidget(button);
            x += 28;
        }

        addRenderableWidget(Button.builder(HoloSettings.rangeLabel(table.range()),
                        b -> send(HoloActionPayload.Op.RANGE, next(table.range(), HoloSettings.RANGES.length)))
                .bounds(left + VALUE_X, rangeY, VALUE_W, 20).build());

        addRenderableWidget(Button.builder(HoloSettings.scaleLabel(table.scale()),
                        b -> send(HoloActionPayload.Op.SCALE, next(table.scale(), HoloSettings.SCALES.length)))
                .bounds(left + VALUE_X, scaleY, VALUE_W, 20).build());

        addRenderableWidget(Button.builder(onOff(table.spin()),
                        b -> send(HoloActionPayload.Op.SPIN, table.spin() ? 0 : 1))
                .bounds(left + VALUE_X, spinY, VALUE_W, 20).build());

        Button clear = Button.builder(Component.translatable("screen.starktech.holo.clear"),
                        b -> send(HoloActionPayload.Op.CLEAR_BLUEPRINT, 0))
                .bounds(left + VALUE_X, clearY, VALUE_W, 20).build();
        clear.active = !table.blueprint().isEmpty();
        addRenderableWidget(clear);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - 88, top + PANEL_H - 28, 80, 20).build());
    }

    private static int next(int current, int count) {
        return (current + 1) % count;
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "screen.starktech.on" : "screen.starktech.off");
    }

    private void send(HoloActionPayload.Op op, int value) {
        PacketDistributor.sendToServer(new HoloActionPayload(pos, op, value));
    }

    // ---------------------------------------------------------------- Bijblijven

    private HoloMode shownMode;
    private ShieldColor shownColour;
    private int shownRange = -1;
    private int shownScale = -1;
    private boolean shownSpin;
    private boolean shownHasBlueprint;

    /** De tafel verandert (ons eigen bericht komt terug): dan het scherm opnieuw opbouwen. */
    @Override
    public void tick() {
        super.tick();
        HoloTableBlockEntity table = table();
        if (table == null || !EdithClientState.authorized) {
            onClose();
            return;
        }
        if (changed(table)) {
            rebuildWidgets();
        }
    }

    private boolean changed(HoloTableBlockEntity table) {
        boolean hasBlueprint = !table.blueprint().isEmpty();
        boolean different = table.mode() != shownMode || colour() != shownColour
                || table.range() != shownRange || table.scale() != shownScale
                || table.spin() != shownSpin || hasBlueprint != shownHasBlueprint;
        shownMode = table.mode();
        shownColour = colour();
        shownRange = table.range();
        shownScale = table.scale();
        shownSpin = table.spin();
        shownHasBlueprint = hasBlueprint;
        return different;
    }

    // ---------------------------------------------------------------- Tekenen

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        HoloTableBlockEntity table = table();
        if (table == null) {
            return;
        }
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(205, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8,
                HudStyle.argb(220, HudStyle.CYAN));
        super.render(g, mouseX, mouseY, a);

        g.drawString(font, title, left + MARGIN, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);
        label(g, "screen.starktech.holo.shows", modeY - 11);

        // Het gekleurde vlakje in elke kleurknop.
        int x = left + MARGIN;
        for (ShieldColor option : ShieldColor.values()) {
            g.fill(x + 6, colourY + 6, x + 18, colourY + 14, HudStyle.argb(255, option.rgb()));
            x += 28;
        }

        label(g, "screen.starktech.holo.range", rangeY + 6);
        label(g, "screen.starktech.holo.size", scaleY + 6);
        label(g, "screen.starktech.holo.spin", spinY + 6);
        label(g, "screen.starktech.holo.on_table", clearY + 6);

        // Wat er nu op tafel ligt: de naam, en het voorwerp zelf rechts.
        ItemStack blueprint = table.blueprint();
        Component what = blueprint.isEmpty()
                ? Component.translatable("screen.starktech.holo.nothing")
                : blueprint.getHoverName();
        g.drawString(font, what, left + VALUE_X + VALUE_W + 8, clearY + 6, HudStyle.argb(210, HudStyle.WHITE), true);
        if (!blueprint.isEmpty()) {
            g.renderItem(blueprint, left + PANEL_W - 24, clearY + 2);
        }

        g.drawString(font, Component.translatable("screen.starktech.holo.hint"), left + MARGIN,
                top + PANEL_H - 22, HudStyle.argb(140, HudStyle.WHITE), false);
    }

    private void label(GuiGraphics g, String key, int y) {
        g.drawString(font, Component.translatable(key), left + MARGIN, y, HudStyle.argb(190, HudStyle.WHITE), true);
    }
}
