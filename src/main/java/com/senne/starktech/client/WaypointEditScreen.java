package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.network.WaypointActionPayload;
import com.senne.starktech.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Eén waypoint bewerken: een andere naam of een andere kleur.
 *
 * Je komt hier via het potloodje in het navigatiescherm. De plek zelf verandert niet - die
 * blijft waar je hem opsloeg. Weg ermee kan hier ook.
 */
public class WaypointEditScreen extends Screen {
    private static final int PANEL_W = 260;

    private final int index;
    private EditBox nameBox;
    private int color;
    private int left;
    private int top;

    public WaypointEditScreen(int index) {
        super(Component.translatable("screen.starktech.waypoint_edit"));
        this.index = index;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static EdithWaypoints data() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? EdithWaypoints.EMPTY : player.getData(ModAttachments.WAYPOINTS);
    }

    private @Nullable Waypoint waypoint() {
        EdithWaypoints data = data();
        return index >= 0 && index < data.waypoints().size() ? data.waypoints().get(index) : null;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(8, height / 2 - 55);
        Waypoint waypoint = waypoint();
        if (waypoint == null) {
            onClose();
            return;
        }
        color = waypoint.color();

        nameBox = new EditBox(font, left + 10, top + 34, PANEL_W - 44, 18,
                Component.translatable("screen.starktech.waypoint_edit.name"));
        nameBox.setMaxLength(Math.round(StarkConfig.Navigation.MAX_WAYPOINT_NAME));
        nameBox.setValue(waypoint.name());
        addRenderableWidget(nameBox);

        // Het gekleurde vierkantje ernaast: klikken bladert door de kleuren.
        addRenderableWidget(Button.builder(Component.empty(), button -> nextColor())
                .bounds(left + PANEL_W - 30, top + 34, 20, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.waypoint_edit.save"), button -> save())
                .bounds(left + 10, top + 62, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.waypoint_edit.remove"), button -> remove())
                .bounds(left + 116, top + 62, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + PANEL_W - 68, top + 62, 58, 20).build());
        setInitialFocus(nameBox);
    }

    private void nextColor() {
        int at = 0;
        for (int i = 0; i < Waypoint.COLORS.length; i++) {
            if (Waypoint.COLORS[i] == color) {
                at = i;
                break;
            }
        }
        color = Waypoint.COLORS[(at + 1) % Waypoint.COLORS.length];
    }

    private void save() {
        PacketDistributor.sendToServer(
                new WaypointActionPayload(WaypointActionPayload.Op.RENAME, index, nameBox.getValue(), color));
        back();
    }

    private void remove() {
        PacketDistributor.sendToServer(
                new WaypointActionPayload(WaypointActionPayload.Op.REMOVE, index, "", color));
        back();
    }

    /** Terug naar de lijst, want daar kwam je vandaan. */
    private void back() {
        Minecraft.getInstance().setScreen(new NavigationScreen());
    }

    /**
     * Het paneel hoort bij de achtergrond. Minecraft tekent in render() eerst een wazige
     * achtergrond; wat daarvóór getekend was, werd dus mee vervaagd en verduisterd.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        int panelH = 94;
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + panelH, HudStyle.argb(210, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + panelH, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);

        g.drawString(font, title, left + 10, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);
        Waypoint waypoint = waypoint();
        if (waypoint != null) {
            String where = waypoint.pos().getX() + " " + waypoint.pos().getY() + " " + waypoint.pos().getZ();
            g.drawString(font, where, left + PANEL_W - 10 - font.width(where), top + 8, HudStyle.argb(160, HudStyle.WHITE), true);
        }
        g.drawString(font, Component.translatable("screen.starktech.waypoint_edit.name"), left + 10, top + 24,
                HudStyle.argb(200, HudStyle.WHITE), false);
        // De gekozen kleur in het knopje laten zien.
        g.fill(left + PANEL_W - 26, top + 38, left + PANEL_W - 14, top + 50, HudStyle.argb(255, color));
    }
}
