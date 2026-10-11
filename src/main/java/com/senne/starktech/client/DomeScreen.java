package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.network.DomeActionPayload;
import com.senne.starktech.shield.DomeGeneratorBlockEntity;
import com.senne.starktech.shield.DomeSettings;
import com.senne.starktech.shield.ShieldColor;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het instelscherm van de schildkoepel.
 *
 * Bovenaan zie je hoe het met de koepel gaat (staat hij, en hoeveel schildkracht heeft hij), dan
 * de knoppen: aan/uit, kleur, bereik en sterkte. Onderaan wat dat aan stroom kost en hoeveel
 * zonnepanelen of warmtegeneratoren er aan zijn net hangen. Alles wat je kiest, gaat meteen naar
 * de koepel: je ziet hem achter het scherm veranderen.
 *
 * Openen: rechtsklik op de koepelgenerator (alleen de eigenaar).
 */
public class DomeScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 206;
    private static final int ROW = 24;
    private static final int MARGIN = 8;
    private static final int VALUE_X = 96;
    private static final int SWATCH_W = 24;
    private static final int SWATCH_GAP = 4;
    private static final int GREY = 0x8A9BA0;

    private static final int ROW_STATUS = 0;
    private static final int ROW_POWER = 1;
    private static final int ROW_COLOUR = 2;
    private static final int ROW_RADIUS = 3;
    private static final int ROW_STRENGTH = 4;

    private final BlockPos pos;
    private int left;
    private int top;
    /** Wat we de laatste keer lieten zien: verandert het, dan bouwen we de knoppen opnieuw op. */
    private String shown = "";

    public DomeScreen(BlockPos pos) {
        super(Component.translatable("screen.starktech.dome"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private @Nullable DomeGeneratorBlockEntity dome() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof DomeGeneratorBlockEntity dome ? dome : null;
    }

    private int rowY(int row) {
        return top + 22 + row * ROW;
    }

    private void send(DomeActionPayload.Op op, int value) {
        PacketDistributor.sendToServer(new DomeActionPayload(pos, op, value));
    }

    /** Een sleutel van alles wat de knoppen bepaalt. */
    private static String key(DomeGeneratorBlockEntity dome) {
        return dome.enabled() + "/" + dome.colour() + "/" + dome.radiusIndex() + "/" + dome.strength();
    }

    // ---------------------------------------------------------------- Opbouw

    @Override
    protected void init() {
        DomeGeneratorBlockEntity dome = dome();
        if (dome == null) {
            onClose();
            return;
        }
        shown = key(dome);
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - PANEL_H) / 2);
        int valueX = left + VALUE_X;

        addRenderableWidget(Button.builder(Component.translatable(dome.enabled() ? "screen.starktech.on" : "screen.starktech.off"),
                        b -> send(DomeActionPayload.Op.ENABLE, dome.enabled() ? 0 : 1))
                .bounds(valueX, rowY(ROW_POWER), 90, 20).build());

        int x = valueX;
        for (ShieldColor option : ShieldColor.values()) {
            Button button = Button.builder(Component.empty(), b -> send(DomeActionPayload.Op.COLOUR, option.ordinal()))
                    .bounds(x, rowY(ROW_COLOUR), SWATCH_W, 20).build();
            button.active = dome.colour() != option;
            addRenderableWidget(button);
            x += SWATCH_W + SWATCH_GAP;
        }

        // Bereik en sterkte: min, de waarde (getekend in render), plus.
        stepper(ROW_RADIUS, dome.radiusIndex() > 0, dome.radiusIndex() < StarkConfig.Dome.RADII.length - 1,
                () -> send(DomeActionPayload.Op.RADIUS, dome.radiusIndex() - 1),
                () -> send(DomeActionPayload.Op.RADIUS, dome.radiusIndex() + 1));
        stepper(ROW_STRENGTH, dome.strength() > StarkConfig.Dome.MIN_STRENGTH, dome.strength() < StarkConfig.Dome.MAX_STRENGTH,
                () -> send(DomeActionPayload.Op.STRENGTH, dome.strength() - 1),
                () -> send(DomeActionPayload.Op.STRENGTH, dome.strength() + 1));

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - MARGIN - 80, top + PANEL_H - 28, 80, 20).build());
        // Boven Done: stroom per tick, seconde, minuut of uur.
        addRenderableWidget(PowerUnit.button(left + PANEL_W - MARGIN - 80, top + PANEL_H - 52, 80, 20));
    }

    private void stepper(int row, boolean canDown, boolean canUp, Runnable down, Runnable up) {
        int valueX = left + VALUE_X;
        Button minus = Button.builder(Component.literal("-"), b -> down.run()).bounds(valueX, rowY(row), 20, 20).build();
        minus.active = canDown;
        addRenderableWidget(minus);
        Button plus = Button.builder(Component.literal("+"), b -> up.run()).bounds(valueX + 110, rowY(row), 20, 20).build();
        plus.active = canUp;
        addRenderableWidget(plus);
    }

    /** Verandert er iets (antwoord van de server)? Dan de knoppen opnieuw opbouwen. */
    @Override
    public void tick() {
        super.tick();
        DomeGeneratorBlockEntity dome = dome();
        if (dome == null) {
            onClose();
        } else if (!key(dome).equals(shown)) {
            rebuildWidgets();
        }
    }

    // ---------------------------------------------------------------- Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(215, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);
        DomeGeneratorBlockEntity dome = dome();
        if (dome == null) {
            return;
        }
        g.drawString(font, title, left + MARGIN, top + 4, HudStyle.argb(255, HudStyle.CYAN), true);

        label(g, "screen.starktech.dome.status", ROW_STATUS);
        label(g, "screen.starktech.dome.power", ROW_POWER);
        label(g, "screen.starktech.dome.colour", ROW_COLOUR);
        label(g, "screen.starktech.dome.radius", ROW_RADIUS);
        label(g, "screen.starktech.dome.strength", ROW_STRENGTH);

        // Status: wat hij doet, en een balk met zijn schildkracht.
        int valueX = left + VALUE_X;
        int statusColour = switch (dome.status()) {
            case UP -> HudStyle.GREEN;
            case OFF -> GREY;
            case NO_POWER, DEPLETED -> HudStyle.RED;
        };
        Component status = Component.translatable("screen.starktech.dome.status." + dome.status().name().toLowerCase());
        g.drawString(font, status, valueX, rowY(ROW_STATUS) + 6, HudStyle.argb(255, statusColour), true);
        int barX = valueX + 100;
        int barW = left + PANEL_W - MARGIN - barX;
        float shield = dome.maxShield() > 0 ? Math.min(1, (float) dome.shield() / dome.maxShield()) : 0;
        g.fill(barX, rowY(ROW_STATUS) + 6, barX + barW, rowY(ROW_STATUS) + 13, HudStyle.argb(90, 0x000000));
        g.fill(barX, rowY(ROW_STATUS) + 6, barX + Math.round(barW * shield), rowY(ROW_STATUS) + 13,
                HudStyle.argb(230, shield < 0.3f ? HudStyle.RED : dome.colour().rgb()));
        HudStyle.smallText(g, font, dome.shield() + " / " + dome.maxShield(), barX + barW / 2, rowY(ROW_STATUS) + 16,
                HudStyle.argb(200, HudStyle.WHITE));

        // Kleurvlakjes in de kleurknoppen.
        int x = valueX;
        for (ShieldColor option : ShieldColor.values()) {
            int y = rowY(ROW_COLOUR);
            g.fill(x + 4, y + 4, x + SWATCH_W - 4, y + 16, HudStyle.argb(255, option.rgb()));
            if (option == dome.colour()) {
                HudStyle.brackets(g, x - 1, y - 1, x + SWATCH_W + 1, y + 21, 4, HudStyle.argb(255, HudStyle.WHITE));
            }
            x += SWATCH_W + SWATCH_GAP;
        }

        // Bereik en sterkte tussen de min- en plusknop.
        g.drawCenteredString(font, DomeSettings.radiusLabel(dome.radiusIndex()), valueX + 65, rowY(ROW_RADIUS) + 6,
                HudStyle.argb(255, HudStyle.WHITE));
        for (int i = 0; i < StarkConfig.Dome.MAX_STRENGTH; i++) {
            int px = valueX + 28 + i * 16;
            boolean filled = i < dome.strength();
            g.fill(px, rowY(ROW_STRENGTH) + 5, px + 12, rowY(ROW_STRENGTH) + 15,
                    HudStyle.argb(filled ? 240 : 50, filled ? dome.colour().rgb() : HudStyle.WHITE));
        }

        // Wat het kost, en of er stroom op zit.
        int infoY = rowY(ROW_STRENGTH) + ROW + 2;
        Component upkeep = PowerUnit.current().rate(DomeSettings.upkeep(dome.radiusIndex(), dome.strength()));
        Component cost = Component.translatable("screen.starktech.dome.cost", upkeep, dome.sources());
        g.drawString(font, cost, left + MARGIN, infoY, HudStyle.argb(220, HudStyle.WHITE), false);
        Component hint = dome.sources() == 0
                ? Component.translatable("screen.starktech.dome.no_sources")
                : Component.translatable("screen.starktech.dome.hint");
        List<FormattedCharSequence> lines = font.split(hint, PANEL_W - 2 * MARGIN - 90);
        // Tot vier regels: links van de knop Done is daar ruimte voor.
        for (int i = 0; i < Math.min(4, lines.size()); i++) {
            g.drawString(font, lines.get(i), left + MARGIN, infoY + 12 + i * 10,
                    HudStyle.argb(dome.sources() == 0 ? 255 : 170, dome.sources() == 0 ? HudStyle.RED : GREY), false);
        }
    }

    private void label(GuiGraphics g, String key, int row) {
        g.drawString(font, Component.translatable(key), left + MARGIN, rowY(row) + 6, HudStyle.argb(220, GREY), false);
    }
}
