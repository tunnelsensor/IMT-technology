package com.senne.starktech.client;

import com.senne.starktech.arsenal.ArsenalBalance;
import com.senne.starktech.arsenal.ArsenalDrone;
import com.senne.starktech.arsenal.EdithArsenal;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.network.ArsenalActionPayload;
import com.senne.starktech.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Het arsenaal-scherm: al je drones, met hoe heel ze nog zijn en waar ze uithangen.
 *
 * Met RECALL laat je een drone uit zijn baan naast je landen. Repareren doe je daarna bij de
 * drone zelf: rechtsklik erop met ijzer in je rugzak. Met LAUNCH (of shift-rechtsklik op de
 * drone) stuur je hem weer omhoog. Een drone die ergens aan het werk is, kun je niet aanraken;
 * daarvoor is "stand down".
 *
 * Openen: G-menu > tabblad Drones > Arsenaal.
 */
public class ArsenalScreen extends Screen {
    private static final int PANEL_W = 320;
    private static final int ROW_H = 20;
    private static final int ROWS_PER_PAGE = 9;
    /** Kleur van de balk van een drone die er slecht aan toe is. */
    private static final int HURT = HudStyle.ORANGE;

    private final List<ArsenalDrone> rows = new ArrayList<>();
    private EdithArsenal shown = EdithArsenal.EMPTY;
    private int page;
    private int left;
    private int top;

    public ArsenalScreen() {
        super(Component.translatable("screen.starktech.arsenal"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static EdithArsenal data() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? EdithArsenal.EMPTY : player.getData(ModAttachments.ARSENAL);
    }

    // ---------------------------------------------------------------- Opbouw

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(8, (height - (ROWS_PER_PAGE + 2) * ROW_H - 60) / 2);
        shown = data();
        rows.clear();

        List<ArsenalDrone> all = shown.drones();
        int pages = Math.max(1, (all.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.min(page, pages - 1);

        int y = top + 28;
        for (int i = page * ROWS_PER_PAGE; i < Math.min(all.size(), (page + 1) * ROWS_PER_PAGE); i++) {
            ArsenalDrone drone = all.get(i);
            rows.add(drone);
            addButtons(drone, y);
            y += ROW_H;
        }

        int bottom = top + 28 + ROWS_PER_PAGE * ROW_H + 6;
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                    .bounds(left + 8, bottom, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                    .bounds(left + 32, bottom, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_W - 88, bottom, 80, 20).build());
    }

    /** De knoppen van één drone. Welke je krijgt, hangt af van waar hij is. */
    private void addButtons(ArsenalDrone drone, int y) {
        int x = left + PANEL_W - 152;
        switch (drone.state()) {
            case ORBIT -> addRenderableWidget(Button.builder(Component.translatable("screen.starktech.arsenal.recall"),
                            button -> send(ArsenalActionPayload.Op.RECALL, drone))
                    .bounds(x + 76, y, 68, 18).build());
            // Aan de grond: repareren doe je bij de drone zelf (rechtsklik), hier kun je hem
            // alleen terugsturen.
            case GROUNDED -> addRenderableWidget(Button.builder(
                            Component.translatable("screen.starktech.arsenal.launch"),
                            button -> send(ArsenalActionPayload.Op.LAUNCH, drone))
                    .bounds(x + 76, y, 68, 18).build());
            // Onderweg: daar valt niets aan te doen tot hij terug is.
            case DEPLOYED -> { }
        }
    }

    private void changePage(int delta) {
        page = Math.max(0, page + delta);
        rebuildWidgets();
    }

    private void send(ArsenalActionPayload.Op op, ArsenalDrone drone) {
        PacketDistributor.sendToServer(new ArsenalActionPayload(op, drone.id()));
    }

    /** Verandert er iets aan je arsenaal (antwoord van de server)? Dan het scherm opnieuw opbouwen. */
    @Override
    public void tick() {
        super.tick();
        if (!EdithClientState.authorized) {
            onClose();
            return;
        }
        if (!data().equals(shown)) {
            rebuildWidgets();
        }
    }

    // ---------------------------------------------------------------- Tekenen

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        int panelH = 28 + ROWS_PER_PAGE * ROW_H + 32;
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + panelH, HudStyle.argb(205, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + panelH, 8, HudStyle.argb(220, HudStyle.CYAN));
        super.render(g, mouseX, mouseY, a);

        g.drawString(font, title, left + 8, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);
        // Rechtsboven: hoeveel er klaar hangen en hoeveel er onderweg zijn.
        Component summary = Component.translatable("screen.starktech.arsenal.summary",
                shown.availableCount(), shown.size(), shown.deployedCount());
        g.drawString(font, summary, left + PANEL_W - 8 - font.width(summary), top + 8,
                HudStyle.argb(180, HudStyle.WHITE), true);

        if (shown.drones().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.starktech.arsenal.empty"),
                    left + PANEL_W / 2, top + 40, HudStyle.argb(170, HudStyle.WHITE));
            g.drawCenteredString(font, Component.translatable("screen.starktech.arsenal.hint"),
                    left + PANEL_W / 2, top + 54, HudStyle.argb(140, HudStyle.WHITE));
            return;
        }

        int y = top + 28;
        int number = page * ROWS_PER_PAGE;
        boolean anyGrounded = false;
        for (ArsenalDrone drone : rows) {
            number++;
            drawRow(g, drone, number, y);
            anyGrounded |= drone.state() == ArsenalDrone.State.GROUNDED;
            y += ROW_H;
        }
        if (anyGrounded) {
            g.drawCenteredString(font, Component.translatable("screen.starktech.arsenal.repair_hint"),
                    left + PANEL_W / 2, top + 28 + ROWS_PER_PAGE * ROW_H + 12, HudStyle.argb(150, HudStyle.WHITE));
        }
    }

    /** Eén regel: nummer, levensbalk, hoeveel punten, en waar hij is. */
    private void drawRow(GuiGraphics g, ArsenalDrone drone, int number, int y) {
        int x = left + 8;
        g.drawString(font, "#" + number, x, y + 5, HudStyle.argb(210, HudStyle.WHITE), true);

        float fraction = drone.healthFraction();
        int color = fraction >= 1.0f ? HudStyle.GREEN : fraction < 0.35f ? HudStyle.RED : HURT;
        HudStyle.bar(g, x + 26, y + 7, 56, 4, fraction, color);
        String health = Math.round(drone.health()) + "/" + (int) ArsenalBalance.DRONE_MAX_HEALTH;
        g.drawString(font, health, x + 88, y + 5, HudStyle.argb(200, color), true);

        Component where = Component.translatable(drone.state().translationKey());
        int stateColor = switch (drone.state()) {
            case ORBIT -> HudStyle.CYAN;
            case DEPLOYED -> HudStyle.ORANGE;
            case GROUNDED -> HudStyle.WHITE;
        };
        g.drawString(font, where, x + 128, y + 5, HudStyle.argb(210, stateColor), true);
    }
}
