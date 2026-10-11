package com.senne.starktech.client;

import com.senne.starktech.arsenal.ArsenalDrone;
import com.senne.starktech.arsenal.EdithArsenal;
import com.senne.starktech.client.arsenal.OrbitView;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.network.ArsenalActionPayload;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.satellite.SatelliteUpgrades;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het arsenaal-scherm: je drones in hun baan om de aarde, in 3D.
 *
 * Links de ruimte: de aarde, de Stark-satelliet en elke drone die klaar hangt, als echt model
 * met zijn schade eraan. Sleep met de muis om rond te kijken, scroll om in te zoomen, klik op
 * een drone om hem te kiezen. Rechts de lijst van al je drones; klik op een regel om hem te
 * kiezen. De gekozen drone draait groot in het vakje rechtsonder in de ruimte.
 *
 * Met RECALL laat je een drone uit zijn baan naast je landen. Repareren doe je daarna bij de
 * drone zelf: rechtsklik erop met ijzer in je rugzak. Met LAUNCH (of shift-rechtsklik op de
 * drone) stuur je hem weer omhoog. Een drone die ergens aan het werk is, kun je niet aanraken;
 * daarvoor is "stand down".
 *
 * Openen: G-menu > tabblad Satellite & Arsenal > Arsenal.
 */
public class ArsenalScreen extends Screen {
    /** Breedte van de lijst rechts. */
    private static final int LIST_W = 176;
    private static final int ROW_H = 16;
    private static final int HEADER_H = 20;
    private static final int FOOTER_H = 28;
    /** Het vakje met de gekozen drone, rechtsonder in de ruimte. */
    private static final int CLOSE_UP = 64;

    private final OrbitView view = new OrbitView();
    private final List<ArsenalDrone> rows = new ArrayList<>();
    private EdithArsenal shown = EdithArsenal.EMPTY;
    private @Nullable UUID selected;
    private int page;
    private int rowsPerPage;

    // Het paneel en de ruimte erin (uitgerekend in init).
    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int viewX0;
    private int viewY0;
    private int viewX1;
    private int viewY1;
    private int listX;

    /** Aan het slepen in de ruimte (om rond te kijken)? */
    private boolean dragging;

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
        panelW = Math.min(width - 12, 470);
        panelH = Math.min(height - 12, 260);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        viewX0 = left + 6;
        viewY0 = top + HEADER_H;
        viewX1 = left + panelW - LIST_W - 12;
        viewY1 = top + panelH - FOOTER_H;
        listX = left + panelW - LIST_W - 6;
        rowsPerPage = Math.max(3, (viewY1 - viewY0) / ROW_H);

        shown = data();
        if (selected == null || shown.find(selected) == null) {
            // Standaard de drone die er het slechtst aan toe is: die wil je meestal bekijken.
            selected = shown.drones().stream()
                    .min(java.util.Comparator.comparingDouble(ArsenalDrone::healthFraction))
                    .map(ArsenalDrone::id).orElse(null);
        }
        rows.clear();
        List<ArsenalDrone> all = shown.drones();
        int pages = Math.max(1, (all.size() + rowsPerPage - 1) / rowsPerPage);
        page = Mth.clamp(page, 0, pages - 1);

        int y = viewY0;
        for (int i = page * rowsPerPage; i < Math.min(all.size(), (page + 1) * rowsPerPage); i++) {
            ArsenalDrone drone = all.get(i);
            rows.add(drone);
            addButton(drone, y);
            y += ROW_H;
        }

        int bottom = top + panelH - FOOTER_H + 5;
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                    .bounds(listX, bottom, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                    .bounds(listX + 24, bottom, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + panelW - 70, bottom, 64, 20).build());
    }

    /** De knop van één drone. Welke je krijgt, hangt af van waar hij is. */
    private void addButton(ArsenalDrone drone, int y) {
        int x = listX + LIST_W - 46;
        switch (drone.state()) {
            case ORBIT -> addRenderableWidget(Button.builder(Component.translatable("screen.starktech.arsenal.recall"),
                            button -> send(ArsenalActionPayload.Op.RECALL, drone))
                    .bounds(x, y + 1, 46, 14).build());
            // Aan de grond: repareren doe je bij de drone zelf (rechtsklik), hier kun je hem
            // alleen terugsturen.
            case GROUNDED -> addRenderableWidget(Button.builder(
                            Component.translatable("screen.starktech.arsenal.launch"),
                            button -> send(ArsenalActionPayload.Op.LAUNCH, drone))
                    .bounds(x, y + 1, 46, 14).build());
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

    // ---------------------------------------------------------------- Muis

    private boolean inView(double x, double y) {
        return x >= viewX0 && x < viewX1 && y >= viewY0 && y < viewY1;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) {
            return true; // een knop
        }
        if (inView(x, y)) {
            UUID picked = view.pick(x, y);
            if (picked != null) {
                selected = picked;
            } else {
                dragging = true;
            }
            return true;
        }
        // Een regel in de lijst aanklikken kiest die drone.
        if (x >= listX && x < listX + LIST_W && y >= viewY0) {
            int index = (int) ((y - viewY0) / ROW_H);
            if (index >= 0 && index < rows.size()) {
                selected = rows.get(index).id();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging) {
            view.yaw += (float) dx * 0.8f;
            view.pitch = Mth.clamp(view.pitch + (float) dy * 0.6f, -80, 80);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        dragging = false;
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inView(x, y)) {
            view.zoom = Mth.clamp(view.zoom * (scrollY > 0 ? 1.12f : 1 / 1.12f), 0.6f, 2.4f);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // ---------------------------------------------------------------- Tekenen

    /**
     * Het paneel hoort bij de achtergrond. Minecraft tekent in render() eerst een wazige
     * achtergrond; wat daarvóór getekend was, werd dus mee vervaagd en verduisterd.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + panelW + 4, top + panelH, HudStyle.argb(215, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + panelW + 4, top + panelH, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        float seconds = (Util.getMillis() % 3_600_000L) / 1000.0f;
        List<ArsenalDrone> orbit = shown.drones().stream().filter(d -> d.state() == ArsenalDrone.State.ORBIT).toList();
        LocalPlayer player = Minecraft.getInstance().player;
        SatelliteUpgrades satellite = player == null ? SatelliteUpgrades.START : player.getData(ModAttachments.SATELLITE);
        view.render(g, viewX0, viewY0, viewX1, viewY1, orbit, selected, satellite, seconds);
        HudStyle.brackets(g, viewX0, viewY0, viewX1, viewY1, 6, HudStyle.argb(160, HudStyle.CYAN));

        super.render(g, mouseX, mouseY, a);

        g.drawString(font, title, left + 6, top + 5, HudStyle.argb(255, HudStyle.CYAN), true);
        // Rechtsboven: hoeveel er klaar hangen en hoeveel er onderweg zijn.
        Component summary = Component.translatable("screen.starktech.arsenal.summary",
                shown.availableCount(), shown.size(), shown.deployedCount());
        g.drawString(font, summary, left + panelW - 6 - font.width(summary), top + 5,
                HudStyle.argb(180, HudStyle.WHITE), true);

        if (shown.drones().isEmpty()) {
            HudStyle.wrappedCentered(g, font, Component.translatable("screen.starktech.arsenal.empty"),
                    (viewX0 + viewX1) / 2, viewY0 + 10, viewX1 - viewX0 - 16, HudStyle.argb(200, HudStyle.WHITE));
            HudStyle.wrappedCentered(g, font, Component.translatable("screen.starktech.arsenal.hint"),
                    listX + LIST_W / 2, viewY0 + 10, LIST_W - 8, HudStyle.argb(150, HudStyle.WHITE));
            return;
        }

        int y = viewY0;
        int number = page * rowsPerPage;
        for (ArsenalDrone drone : rows) {
            number++;
            drawRow(g, drone, number, y, mouseX, mouseY);
            y += ROW_H;
        }
        drawSelected(g, seconds);
        // Onderaan: hoe je rondkijkt, of (als er een drone aan de grond staat) hoe je repareert.
        boolean anyGrounded = shown.drones().stream().anyMatch(d -> d.state() == ArsenalDrone.State.GROUNDED);
        Component hint = Component.translatable(anyGrounded ? "screen.starktech.arsenal.repair_hint"
                : "screen.starktech.arsenal.controls");
        // Klein (75%) en desnoods over twee regels, zodat de zin niet halverwege ophoudt.
        List<FormattedCharSequence> lines = font.split(hint, (int) ((listX - viewX0 - 8) / 0.75f));
        g.pose().pushPose();
        g.pose().translate(viewX0 + 2, viewY1 + (lines.size() > 1 ? 7 : 11), 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(font, lines.get(i), 0, i * 10, HudStyle.argb(160, HudStyle.WHITE), false);
        }
        g.pose().popPose();
    }

    /** Eén regel: nummer, levensbalk en waar hij is. De gekozen drone licht op. */
    private void drawRow(GuiGraphics g, ArsenalDrone drone, int number, int y, int mouseX, int mouseY) {
        boolean chosen = drone.id().equals(selected);
        boolean hover = mouseX >= listX && mouseX < listX + LIST_W - 48 && mouseY >= y && mouseY < y + ROW_H;
        if (chosen || hover) {
            g.fill(listX, y, listX + LIST_W - 48, y + ROW_H, HudStyle.argb(chosen ? 70 : 35, HudStyle.CYAN));
        }
        int x = listX + 3;
        g.drawString(font, "#" + number, x, y + 4, HudStyle.argb(210, HudStyle.WHITE), true);

        float fraction = drone.healthFraction();
        int color = healthColour(fraction);
        HudStyle.bar(g, x + 22, y + 6, 36, 4, fraction, color);

        int stateColor = switch (drone.state()) {
            case ORBIT -> HudStyle.CYAN;
            case DEPLOYED -> HudStyle.ORANGE;
            case GROUNDED -> HudStyle.WHITE;
        };
        String where = font.plainSubstrByWidth(Component.translatable(drone.whereKey()).getString(), 62);
        g.drawString(font, where, x + 62, y + 4, HudStyle.argb(210, stateColor), true);
    }

    /** Het vakje met de gekozen drone: groot in 3D, met zijn leven eronder. */
    private void drawSelected(GuiGraphics g, float seconds) {
        ArsenalDrone drone = selected == null ? null : shown.find(selected);
        if (drone == null) {
            return;
        }
        int x1 = viewX1 - 4;
        int y1 = viewY1 - 4;
        int x0 = x1 - CLOSE_UP;
        int y0 = y1 - CLOSE_UP - 12;
        g.fill(x0, y0, x1, y1, HudStyle.argb(170, 0x06101A));
        HudStyle.brackets(g, x0, y0, x1, y1, 5, HudStyle.argb(220, HudStyle.CYAN));
        view.renderCloseUp(g, x0 + 2, y0 + 2, x1 - 2, y1 - 14, drone, seconds);
        float fraction = drone.healthFraction();
        String health = Math.round(drone.health()) + "/" + (int) StarkConfig.Drones.HEALTH;
        HudStyle.smallText(g, font, health, (x0 + x1) / 2, y1 - 10, HudStyle.argb(230, healthColour(fraction)));
    }

    private static int healthColour(float fraction) {
        return fraction >= 1.0f ? HudStyle.GREEN : fraction < 0.35f ? HudStyle.RED : HudStyle.ORANGE;
    }
}
