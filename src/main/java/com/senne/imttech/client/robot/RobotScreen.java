package com.senne.imttech.client.robot;

import com.senne.imttech.bot.BotEntity;
import com.senne.imttech.bot.BotNetwork;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.network.RobotActionPayload;
import com.senne.imttech.robot.GuardDroneEntity;
import com.senne.imttech.robot.GuardRobotEntity;
import com.senne.imttech.robot.RobotParts;
import com.senne.imttech.robot.RobotSlot;
import com.senne.imttech.robot.RobotState;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het scherm van een guard robot (hoofdstuk 21). Openen: sneak + rechtsklik met een lege hand, als jij hem
 * neerzette of als je vertrouwd bent in de basis waar hij staat (de server kijkt dat na, zie robot/Robots).
 *
 * <pre>
 *  Guard 1                                   ACTIVE
 *  Owner: Senne
 *  Head   Commando        Body   Super Battle
 *  Arms   Trainer         Legs   Battle
 *  Battery [##########--------]  56%
 *  Dock    12, 64, -40
 *  Name    [Guard 1          ] [Rename]
 *  [Switch off] [Pick up] [Board]           [Done]
 * </pre>
 * Wat er nu is (naam, toestand, batterij) leest het scherm gewoon van de robot: dat kent jouw spel al, dus je
 * ziet het veranderen zodra de server iets gedaan heeft. Een security drone (hoofdstuk 25) heeft hetzelfde scherm,
 * zonder onderdelen (een drone ziet er altijd hetzelfde uit).
 */
public class RobotScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 176;
    private static final int MARGIN = 8;
    private static final int VALUE_X = 58;

    private final int entityId;
    private final String owner;
    private final @Nullable BlockPos home;
    private int left;
    private int top;
    private @Nullable EditBox nameBox;
    private @Nullable Button switchButton;

    public RobotScreen(int entityId, String owner, @Nullable BlockPos home) {
        super(Component.literal("Guard Robot"));
        this.entityId = entityId;
        this.owner = owner;
        this.home = home;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** De robot (of drone) zoals jouw spel hem kent, of null als hij weg is. */
    private @Nullable BotEntity robot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(entityId) instanceof BotEntity robot && robot.isAlive()) {
            return robot;
        }
        return null;
    }

    private int footerY() {
        return top + PANEL_H - 28;
    }

    @Override
    protected void init() {
        BotEntity robot = robot();
        if (robot == null) {
            onClose();
            return;
        }
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - PANEL_H) / 2);
        nameBox = addRenderableWidget(new EditBox(font, left + VALUE_X, top + 108, 120, 18, Component.literal("Name")));
        nameBox.setMaxLength(BotNetwork.MAX_NAME);
        nameBox.setValue(robot.hasCustomName() ? robot.getCustomName().getString() : "");
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> {
            if (nameBox != null) {
                send(RobotActionPayload.Op.RENAME, nameBox.getValue());
            }
        }).bounds(left + VALUE_X + 126, top + 107, PANEL_W - VALUE_X - 126 - MARGIN, 20).build());
        switchButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            BotEntity now = robot();
            if (now != null) {
                send(now.isActive() ? RobotActionPayload.Op.TURN_OFF : RobotActionPayload.Op.TURN_ON, "");
            }
        }).bounds(left + MARGIN, footerY(), 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Pick up"), b -> {
            send(RobotActionPayload.Op.PICK_UP, "");
            onClose();
        }).bounds(left + MARGIN + 86, footerY(), 66, 20).build());
        // Het bord: modules solderen (hoofdstuk 22).
        addRenderableWidget(Button.builder(Component.literal("Board"), b -> minecraft.setScreen(new BoardScreen(entityId, this)))
                .bounds(left + MARGIN + 158, footerY(), 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - MARGIN - 60, footerY(), 60, 20).build());
        refresh(robot);
    }

    private void send(RobotActionPayload.Op op, String text) {
        PacketDistributor.sendToServer(new RobotActionPayload(entityId, op, text));
    }

    private void refresh(BotEntity robot) {
        if (switchButton != null) {
            switchButton.setMessage(Component.literal(robot.isActive() ? "Switch off" : "Switch on"));
        }
    }

    /** Elke tick: de knop bijwerken, en dicht als de robot weg is (opgepakt of kapot). */
    @Override
    public void tick() {
        super.tick();
        BotEntity robot = robot();
        if (robot == null) {
            onClose();
            return;
        }
        refresh(robot);
    }

    /** Enter in het naamvak = Rename. */
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (nameBox != null && nameBox.isFocused() && (key == 257 || key == 335)) {
            send(RobotActionPayload.Op.RENAME, nameBox.getValue());
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // ---------------------------------------------------------------- Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(205, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        BotEntity robot = robot();
        if (robot == null) {
            return;
        }
        super.render(g, mouseX, mouseY, a);
        boolean drone = robot instanceof GuardDroneEntity;
        String name = robot.hasCustomName() ? robot.getCustomName().getString() : drone ? "Security Drone" : "Guard Robot";
        g.drawString(font, name, left + MARGIN, top + 7, HudStyle.argb(255, HudStyle.CYAN), true);
        RobotState state = robot.state();
        String stateText = state.title().toUpperCase(Locale.ROOT);
        int stateColour = state == RobotState.ACTIVE ? HudStyle.GREEN : state == RobotState.STANDBY ? HudStyle.ORANGE : HudStyle.RED;
        g.drawString(font, stateText, left + PANEL_W - MARGIN - font.width(stateText), top + 7, HudStyle.argb(255, stateColour), true);
        g.drawString(font, owner.isEmpty() ? "No owner yet" : "Owner: " + owner, left + MARGIN, top + 20,
                HudStyle.argb(150, 0x9FC9D6), false);
        // Met een Gun-module (hoofdstuk 24): hoeveel kogels hij nog heeft.
        if (robot.board().has(com.senne.imttech.bot.BotModule.GUN)) {
            String ammo = "Ammo " + robot.ammo() + "/" + BotEntity.maxAmmo();
            g.drawString(font, ammo, left + PANEL_W - MARGIN - font.width(ammo), top + 20,
                    HudStyle.argb(220, robot.ammo() == 0 ? HudStyle.RED : HudStyle.ORANGE), false);
        }

        // De onderdelen, twee per regel (een drone heeft er geen: die vliegt).
        if (robot instanceof GuardRobotEntity guard) {
            RobotParts parts = guard.parts();
            for (RobotSlot slot : RobotSlot.values()) {
                int x = left + MARGIN + (slot.ordinal() % 2) * (PANEL_W / 2);
                int y = top + 36 + (slot.ordinal() / 2) * 12;
                g.drawString(font, slot.title(), x, y, HudStyle.argb(170, HudStyle.ORANGE), false);
                g.drawString(font, parts.get(slot).title(), x + 34, y, HudStyle.argb(230, HudStyle.WHITE), false);
            }
        } else {
            g.drawString(font, "Security Drone", left + MARGIN, top + 36, HudStyle.argb(170, HudStyle.ORANGE), false);
            g.drawString(font, "it flies: up(n) and down(n) in its program", left + MARGIN, top + 48, HudStyle.argb(200, 0x9FC9D6), false);
        }

        // De batterij: een balk, groen, oranje onder 30%, rood onder 10%.
        label(g, "Battery", top + 66);
        float fraction = robot.batteryFraction();
        int barX = left + VALUE_X;
        int barW = PANEL_W - VALUE_X - MARGIN - 34;
        g.fill(barX, top + 66, barX + barW, top + 74, HudStyle.argb(255, 0x1E2128));
        int colour = fraction < 0.1f ? HudStyle.RED : fraction < 0.3f ? HudStyle.ORANGE : HudStyle.GREEN;
        g.fill(barX, top + 66, barX + Math.round(barW * fraction), top + 74, HudStyle.argb(255, colour));
        g.renderOutline(barX - 1, top + 65, barW + 2, 10, HudStyle.argb(180, 0x465460));
        String percent = Math.round(fraction * 100) + "%";
        g.drawString(font, percent, left + PANEL_W - MARGIN - font.width(percent), top + 66, HudStyle.argb(230, HudStyle.WHITE), false);

        // Zijn dock.
        label(g, "Dock", top + 86);
        String dock = home == null ? drone ? "None (put it on a Drone Dock)" : "None (put it on a Robot Dock)"
                : home.getX() + ", " + home.getY() + ", " + home.getZ();
        g.drawString(font, dock, left + VALUE_X, top + 86, HudStyle.argb(home == null ? 170 : 230, HudStyle.WHITE), false);

        label(g, "Name", top + 113);
    }

    private void label(GuiGraphics g, String text, int y) {
        g.drawString(font, text, left + MARGIN, y, HudStyle.argb(190, HudStyle.WHITE), true);
    }
}
