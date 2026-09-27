package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.holo.HoloMode;
import com.senne.starktech.holo.HoloSettings;
import com.senne.starktech.holo.HoloTableBlock;
import com.senne.starktech.holo.HoloTableBlockEntity;
import com.senne.starktech.network.HoloActionPayload;
import com.senne.starktech.shield.ShieldColor;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het instelscherm van de holotafel.
 *
 * Alles wat je hier kiest gaat meteen naar de tafel, zodat je het hologram achter het scherm
 * ziet veranderen terwijl je klikt. Er is dus geen "opslaan"-knop.
 *
 * Openen: rechtsklik op de tafel met een lege hand (een bril is niet nodig).
 *
 * Elke regel heeft links een naam en rechts de knoppen. Alles is zo uitgerekend dat het
 * past op het kleinste scherm dat Minecraft toestaat (240 beeldpunten hoog): eerder liepen de
 * uitleg, "Take off" en "Done" over elkaar heen.
 */
public class HoloTableScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 222;
    /** Hoogte van één regel met een knop erin. */
    private static final int ROW = 24;
    private static final int MARGIN = 8;
    /** Waar de knoppen beginnen (de naam van de regel staat links daarvan). */
    private static final int VALUE_X = 96;
    private static final int VALUE_W = PANEL_W - VALUE_X - MARGIN;
    /** De knop "Take off" staat rechts in zijn regel. */
    private static final int CLEAR_W = 60;
    private static final int DONE_W = 80;
    /** Een kleurknopje en de ruimte ertussen. */
    private static final int SWATCH_W = 24;
    private static final int SWATCH_GAP = 4;

    private final BlockPos pos;
    private int left;
    private int top;

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
        if (mc.level == null || !mc.level.getBlockState(pos).hasProperty(HoloTableBlock.COLOR)) {
            return ShieldColor.CYAN;
        }
        return mc.level.getBlockState(pos).getValue(HoloTableBlock.COLOR);
    }

    // ---------------------------------------------------------------- Waar alles staat

    /** De y van regel nummer {@code row} (0 = "Showing"). */
    private int rowY(int row) {
        return top + 22 + row * ROW;
    }

    /** De standen staan in twee rijen (MODES_PER_ROW per rij), dus de volgende regel is 2. */
    private static final int ROW_MODE = 0;
    private static final int MODES_PER_ROW = 3;
    private static final int ROW_COLOUR = 2;
    private static final int ROW_RANGE = 3;
    private static final int ROW_SIZE = 4;
    private static final int ROW_SPIN = 5;
    private static final int ROW_TABLE = 6;

    /** Waar de uitleg en de knop "Done" staan, onderaan. */
    private int footerY() {
        return top + PANEL_H - 28;
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
        top = Math.max(6, (height - PANEL_H) / 2);
        int valueX = left + VALUE_X;

        // Wat hij laat zien: één knop per stand, de huidige staat uit. Vijf standen passen niet
        // naast elkaar (de namen vielen eraf), dus twee rijen.
        int modeW = (VALUE_W - (MODES_PER_ROW - 1) * 4) / MODES_PER_ROW;
        for (HoloMode mode : HoloMode.values()) {
            int column = mode.ordinal() % MODES_PER_ROW;
            int row = ROW_MODE + mode.ordinal() / MODES_PER_ROW;
            Button button = Button.builder(mode.label(), b -> send(HoloActionPayload.Op.MODE, mode.ordinal()))
                    .bounds(valueX + column * (modeW + 4), rowY(row), modeW, 20).build();
            button.active = table.mode() != mode;
            if (mode.needsGlasses() && !EdithClientState.authorized) {
                button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("screen.starktech.holo.needs_glasses")));
            }
            addRenderableWidget(button);
        }

        // De kleuren: een rij knopjes met een gekleurd vlakje erin (zie render).
        int x = valueX;
        for (ShieldColor option : ShieldColor.values()) {
            Button button = Button.builder(Component.empty(),
                            b -> send(HoloActionPayload.Op.COLOUR, option.ordinal()))
                    .bounds(x, rowY(ROW_COLOUR), SWATCH_W, 20).build();
            button.active = colour() != option;
            addRenderableWidget(button);
            x += SWATCH_W + SWATCH_GAP;
        }

        addRenderableWidget(Button.builder(HoloSettings.rangeLabel(table.range()),
                        b -> send(HoloActionPayload.Op.RANGE, next(table.range(), HoloSettings.RANGES.length)))
                .bounds(valueX, rowY(ROW_RANGE), 90, 20).build());

        addRenderableWidget(Button.builder(HoloSettings.scaleLabel(table.scale()),
                        b -> send(HoloActionPayload.Op.SCALE, next(table.scale(), HoloSettings.SCALES.length)))
                .bounds(valueX, rowY(ROW_SIZE), 90, 20).build());

        addRenderableWidget(Button.builder(onOff(table.spin()),
                        b -> send(HoloActionPayload.Op.SPIN, table.spin() ? 0 : 1))
                .bounds(valueX, rowY(ROW_SPIN), 90, 20).build());

        Button clear = Button.builder(Component.translatable("screen.starktech.holo.clear"),
                        b -> send(HoloActionPayload.Op.CLEAR_BLUEPRINT, 0))
                .bounds(left + PANEL_W - MARGIN - CLEAR_W, rowY(ROW_TABLE), CLEAR_W, 20).build();
        clear.active = !table.blueprint().isEmpty();
        addRenderableWidget(clear);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - MARGIN - DONE_W, footerY(), DONE_W, 20).build());
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
        if (table == null) {
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

    /**
     * Het paneel hoort bij de achtergrond. Minecraft tekent in render() eerst een wazige
     * achtergrond; wat daarvóór getekend was, werd dus mee vervaagd en verduisterd. En juist
     * hier wil je het hologram achter het scherm scherp zien veranderen.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(205, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8,
                HudStyle.argb(220, colour().rgb()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        HoloTableBlockEntity table = table();
        if (table == null) {
            return;
        }
        super.render(g, mouseX, mouseY, a);

        g.drawString(font, title, left + MARGIN, top + 7, HudStyle.argb(255, HudStyle.CYAN), true);

        label(g, "screen.starktech.holo.shows", ROW_MODE);
        label(g, "screen.starktech.holo.colour", ROW_COLOUR);
        label(g, "screen.starktech.holo.range", ROW_RANGE);
        label(g, "screen.starktech.holo.size", ROW_SIZE);
        label(g, "screen.starktech.holo.spin", ROW_SPIN);
        label(g, "screen.starktech.holo.on_table", ROW_TABLE);

        // Het gekleurde vlakje in elke kleurknop; de gekozen kleur krijgt een wit randje.
        int x = left + VALUE_X;
        int y = rowY(ROW_COLOUR);
        ShieldColor current = colour();
        for (ShieldColor option : ShieldColor.values()) {
            if (option == current) {
                g.fill(x + 4, y + 4, x + SWATCH_W - 4, y + 16, HudStyle.argb(255, HudStyle.WHITE));
            }
            g.fill(x + 6, y + 6, x + SWATCH_W - 6, y + 14, HudStyle.argb(255, option.rgb()));
            x += SWATCH_W + SWATCH_GAP;
        }

        // Wat er nu op tafel ligt: het voorwerp zelf en zijn naam (ingekort als hij te lang is).
        ItemStack blueprint = table.blueprint();
        int itemX = left + VALUE_X;
        int rowY = rowY(ROW_TABLE);
        int textX = itemX;
        if (!blueprint.isEmpty()) {
            g.renderItem(blueprint, itemX, rowY + 2);
            textX += 20;
        }
        int textRoom = left + PANEL_W - MARGIN - CLEAR_W - 6 - textX;
        Component what = blueprint.isEmpty()
                ? Component.translatable("screen.starktech.holo.nothing")
                : blueprint.getHoverName();
        String shown = font.plainSubstrByWidth(what.getString(), textRoom);
        if (shown.length() < what.getString().length()) {
            shown = font.plainSubstrByWidth(what.getString(), textRoom - font.width("...")) + "...";
        }
        g.drawString(font, shown, textX, rowY + 6, HudStyle.argb(210, HudStyle.WHITE), true);

        // De uitleg onderaan, over zoveel regels als nodig, links van "Done".
        int hintWidth = PANEL_W - MARGIN * 2 - DONE_W - 8;
        // Een stand van E.D.I.T.H. zonder bril op? Dan zegt de uitleg dat (in het rood).
        boolean locked = table.mode().needsGlasses() && !EdithClientState.authorized;
        List<FormattedCharSequence> hint = font.split(Component.translatable(locked
                ? "screen.starktech.holo.needs_glasses" : "screen.starktech.holo.hint"), hintWidth);
        int hintY = footerY() + 10 - hint.size() * 5;
        for (FormattedCharSequence line : hint) {
            g.drawString(font, line, left + MARGIN, hintY,
                    locked ? HudStyle.argb(230, HudStyle.RED) : HudStyle.argb(150, HudStyle.WHITE), false);
            hintY += 10;
        }
    }

    private void label(GuiGraphics g, String key, int row) {
        g.drawString(font, Component.translatable(key), left + MARGIN, rowY(row) + 6,
                HudStyle.argb(190, HudStyle.WHITE), true);
    }
}
