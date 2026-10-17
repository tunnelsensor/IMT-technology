package com.senne.imttech.client;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.nav.ImtNavigation;
import com.senne.imttech.nav.ImtWaypoints;
import com.senne.imttech.nav.Waypoint;
import com.senne.imttech.network.WaypointActionPayload;
import com.senne.imttech.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Het navigatiescherm: plekken opslaan, verwijderen en ernaartoe navigeren.
 * Openen met M (of via het G-menu).
 */
public class NavigationScreen extends Screen {
    private static final int PANEL_W = 320;
    private static final int ROW_H = 22;
    private static final int PER_PAGE = 6;

    private EditBox nameBox;
    private String pendingName = "";
    private int colorIndex;
    private int page;
    private ImtWaypoints shownData = ImtWaypoints.EMPTY;
    private int left;
    private int top;

    /** Een regel in de lijst: een waypoint (index >= 0) of de sterfplek (DEATH). */
    private record Row(int index, int y) {}

    private final List<Row> rows = new ArrayList<>();

    public NavigationScreen() {
        super(Component.translatable("screen.imttech.nav"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    private static ImtWaypoints data() {
        LocalPlayer player = player();
        return player == null ? ImtWaypoints.EMPTY : player.getData(ModAttachments.WAYPOINTS);
    }

    // ---------------------------------------------------------------- Opbouw

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(8, (height - 240) / 2);
        shownData = data();
        rows.clear();

        // Bovenaan: naam + kleur + "hier opslaan"
        nameBox = new EditBox(font, left + 8, top + 24, 170, 18, Component.translatable("screen.imttech.nav.name"));
        nameBox.setMaxLength(Math.round(ImtConfig.Navigation.MAX_WAYPOINT_NAME));
        nameBox.setValue(pendingName);
        nameBox.setResponder(value -> pendingName = value);
        nameBox.setHint(Component.translatable("screen.imttech.nav.name_hint"));
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.empty(), button -> colorIndex = (colorIndex + 1) % Waypoint.COLORS.length)
                .bounds(left + 182, top + 24, 18, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.nav.save_here"), button -> save())
                .bounds(left + 204, top + 24, 108, 18).build());

        int y = top + 52;
        // De laatste sterfplek (als die er is)
        if (player() != null && player().getLastDeathLocation().isPresent()) {
            addRowButtons(ImtWaypoints.DEATH, y, false);
            rows.add(new Row(ImtWaypoints.DEATH, y));
            y += ROW_H;
        }
        // De opgeslagen waypoints, per pagina
        List<Waypoint> waypoints = shownData.waypoints();
        int pages = Math.max(1, (waypoints.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        for (int i = page * PER_PAGE; i < Math.min(waypoints.size(), (page + 1) * PER_PAGE); i++) {
            addRowButtons(i, y, true);
            rows.add(new Row(i, y));
            y += ROW_H;
        }

        int bottom = top + 52 + (PER_PAGE + 1) * ROW_H + 4;
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1)).bounds(left + 8, bottom, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1)).bounds(left + 32, bottom, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_W - 88, bottom, 80, 20).build());
        setInitialFocus(nameBox);
    }

    private void addRowButtons(int index, int y, boolean deletable) {
        boolean active = shownData.active() == index;
        Component label = Component.translatable(active ? "screen.imttech.nav.stop" : "screen.imttech.nav.navigate");
        int navigateTo = active ? ImtWaypoints.NONE : index;
        // De sterfplek kun je niet bewerken of weggooien: die zet het spel zelf.
        addRenderableWidget(Button.builder(label, button -> send(WaypointActionPayload.Op.NAVIGATE, navigateTo))
                .bounds(left + PANEL_W - (deletable ? 134 : 88), y, 80, 18).build());
        if (deletable) {
            addRenderableWidget(Button.builder(Component.literal("✎"),
                            button -> minecraft.setScreen(new WaypointEditScreen(index)))
                    .bounds(left + PANEL_W - 52, y, 20, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("screen.imttech.nav.edit")))
                    .build());
            addRenderableWidget(Button.builder(Component.literal("X"), button -> send(WaypointActionPayload.Op.REMOVE, index))
                    .bounds(left + PANEL_W - 28, y, 20, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("screen.imttech.nav.remove")))
                    .build());
        }
    }

    private void changePage(int delta) {
        page = Math.max(0, page + delta);
        rebuildWidgets();
    }

    private void save() {
        PacketDistributor.sendToServer(new WaypointActionPayload(WaypointActionPayload.Op.ADD, 0, pendingName, Waypoint.COLORS[colorIndex]));
        pendingName = "";
        nameBox.setValue("");
    }

    private void send(WaypointActionPayload.Op op, int index) {
        PacketDistributor.sendToServer(new WaypointActionPayload(op, index, "", 0));
    }

    /** Veranderd er iets aan de waypoints (antwoord van de server)? Dan het scherm opnieuw opbouwen. */
    @Override
    public void tick() {
        super.tick();
        if (!ImtClientState.authorized) {
            onClose();
            return;
        }
        if (data() != shownData) {
            rebuildWidgets();
        }
    }

    // ---------------------------------------------------------------- Tekenen

    /**
     * Het paneel hoort bij de achtergrond. Minecraft tekent in render() eerst een wazige
     * achtergrond; wat daarvóór getekend was, werd dus mee vervaagd en verduisterd.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + 52 + (PER_PAGE + 1) * ROW_H + 30, HudStyle.argb(200, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + 52 + (PER_PAGE + 1) * ROW_H + 30, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);

        g.drawString(font, title, left + 8, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);
        String count = shownData.waypoints().size() + "/" + Math.round(ImtConfig.Navigation.MAX_WAYPOINTS);
        g.drawString(font, count, left + PANEL_W - 8 - font.width(count), top + 8, HudStyle.argb(180, HudStyle.WHITE), true);
        // Kleurvakje op de kleurknop
        g.fill(left + 186, top + 28, left + 196, top + 38, HudStyle.argb(255, Waypoint.COLORS[colorIndex]));

        LocalPlayer player = player();
        if (player == null) {
            return;
        }
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.imttech.nav.empty"), left + PANEL_W / 2, top + 60,
                    HudStyle.argb(160, HudStyle.WHITE));
        }
        for (Row row : rows) {
            boolean active = shownData.active() == row.index();
            if (active) {
                g.fill(left + 4, row.y() - 2, left + PANEL_W - 4, row.y() + ROW_H - 4, HudStyle.argb(60, HudStyle.CYAN));
            }
            int color;
            Component name;
            ResourceKey<Level> dimension;
            BlockPos pos;
            if (row.index() == ImtWaypoints.DEATH) {
                Optional<GlobalPos> death = player.getLastDeathLocation();
                if (death.isEmpty()) {
                    continue;
                }
                color = HudStyle.RED;
                name = Component.translatable("imt.imttech.nav.death");
                dimension = death.get().dimension();
                pos = death.get().pos();
            } else {
                Waypoint waypoint = shownData.waypoints().get(row.index());
                color = waypoint.color();
                name = Component.literal(waypoint.name());
                dimension = waypoint.dimension();
                pos = waypoint.pos();
            }
            g.fill(left + 8, row.y() + 4, left + 16, row.y() + 12, HudStyle.argb(255, color));
            g.drawString(font, name, left + 22, row.y() + 1, HudStyle.argb(255, HudStyle.WHITE), true);
            g.drawString(font, locationText(player, dimension, pos), left + 22, row.y() + 10, HudStyle.argb(170, 0x9FC9D6), false);
        }
    }

    /** "123 m" in dezelfde wereld, anders de naam van de andere wereld. */
    static Component locationText(LocalPlayer player, ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension == player.level().dimension()) {
            int distance = (int) Math.round(Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(pos))));
            return Component.translatable("screen.imttech.nav.distance", distance, pos.getX(), pos.getY(), pos.getZ());
        }
        return Component.translatable("screen.imttech.nav.other_dimension", dimensionName(dimension));
    }

    public static Component dimensionName(ResourceKey<Level> dimension) {
        return ImtNavigation.dimensionName(dimension);
    }
}
