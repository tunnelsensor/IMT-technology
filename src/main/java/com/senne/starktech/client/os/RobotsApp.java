package com.senne.starktech.client.os;

import com.senne.starktech.bot.BotNetwork;
import com.senne.starktech.client.hud.HudStyle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * De app Robots van de Stark OS-desktop (hoofdstuk 21): de guard robots in het gebied van deze kern, elk met
 * zijn naam, waar hij staat, hoe het met hem gaat, zijn batterij en of hij een dock heeft. Per robot een knop
 * Switch on/off en Rename (typ de naam, Enter bewaart, Esc laat het zoals het was).
 *
 * <pre>
 *  Guard 1   12 64 -40                      [Rename] [Switch off]
 *    active  battery 86%  dock
 *  Door guard   20 64 -38                   [Rename] [Switch on]
 *    standby  battery 100%  dock
 * </pre>
 * Een robot in een stuk wereld dat niet geladen is, staat er grijs bij ("far away"): die kun je van hier
 * niet bedienen. De lijst komt van de server (zie robot/Robots.snapshot, uit BotNetwork).
 */
final class RobotsApp {
    private static final int ROW_H = 21;

    private final StarkOsScreen screen;
    private int scroll;
    /** Welke robot je een andere naam aan het geven bent (zijn nummer), of null. */
    private @Nullable String renaming;
    private @Nullable EditBox nameBox;

    RobotsApp(StarkOsScreen screen) {
        this.screen = screen;
    }

    private List<CompoundTag> robots() {
        List<CompoundTag> list = new ArrayList<>();
        for (Tag element : screen.data().getList("robots", Tag.TAG_COMPOUND)) {
            list.add((CompoundTag) element);
        }
        return list;
    }

    private EditBox nameBox() {
        if (nameBox == null) {
            nameBox = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 10, Component.literal("Robot name"));
            nameBox.setBordered(false);
            nameBox.setMaxLength(BotNetwork.MAX_NAME);
            nameBox.setFilter(value -> value.indexOf('"') < 0 && value.indexOf('§') < 0);
            nameBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
        }
        return nameBox;
    }

    private static String switchLabel(CompoundTag robot) {
        return robot.getBoolean("on") ? "Switch off" : "Switch on";
    }

    private String renameLabel(CompoundTag robot) {
        return robot.getString("id").equals(renaming) ? "Save" : "Rename";
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my, float partialTick) {
        OsDraw.field(g, a.x(), a.y(), a.x() + a.w(), a.y() + a.h(), false);
        List<CompoundTag> robots = robots();
        if (renaming != null && robots.stream().noneMatch(robot -> robot.getString("id").equals(renaming))) {
            renaming = null; // de robot is weg
        }
        if (robots.isEmpty()) {
            g.drawString(font, "No guard robots in this network's area yet.", a.x() + 5, a.y() + 5, HudStyle.argb(255, OsDraw.GREY), false);
            g.drawString(font, "Build one in a Robot Assembler and put it here.", a.x() + 5, a.y() + 15, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        scroll = Math.max(0, Math.min(scroll, robots.size() - 1));
        int y = a.y() + 3;
        for (int i = scroll; i < robots.size() && y + ROW_H <= a.y() + a.h(); i++) {
            renderRobot(g, font, a, y, robots.get(i), mx, my, partialTick);
            y += ROW_H;
        }
    }

    private void renderRobot(GuiGraphics g, Font font, StarkOsScreen.Area a, int y, CompoundTag robot,
                             int mx, int my, float partialTick) {
        boolean loaded = robot.getBoolean("loaded");
        // Rechts de knoppen (alleen als de desktop hem kan bereiken).
        int right = a.x() + a.w() - 4;
        if (loaded) {
            String toggle = switchLabel(robot);
            right -= OsDraw.buttonWidth(font, toggle);
            OsDraw.button(g, font, right, y + 2, toggle, true, robot.getBoolean("on") ? HudStyle.RED : HudStyle.GREEN);
            String rename = renameLabel(robot);
            right -= OsDraw.buttonWidth(font, rename) + 4;
            OsDraw.button(g, font, right, y + 2, rename, true, robot.getString("id").equals(renaming) ? HudStyle.GREEN : HudStyle.CYAN);
        }
        if (robot.getString("id").equals(renaming)) {
            OsDraw.field(g, a.x() + 4, y - 1, right - 6, y + 10, true);
            EditBox box = nameBox();
            box.setX(a.x() + 7);
            box.setY(y + 1);
            box.setWidth(right - a.x() - 14);
            box.render(g, mx, my, partialTick);
            g.drawString(font, "Enter = save   Esc = cancel", a.x() + 10, y + 11, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        String name = robot.getString("n");
        g.drawString(font, name, a.x() + 6, y + 1, HudStyle.argb(255, loaded ? HudStyle.WHITE : OsDraw.GREY), false);
        String where = robot.getInt("x") + " " + robot.getInt("y") + " " + robot.getInt("z");
        g.drawString(font, where, a.x() + 10 + font.width(name), y + 1, HudStyle.argb(255, OsDraw.GREY), false);
        String state = loaded ? robot.getString("st") : "far away";
        int colour = !loaded ? OsDraw.GREY : switch (state) {
            case "active" -> HudStyle.GREEN;
            case "standby" -> HudStyle.ORANGE;
            default -> HudStyle.RED;
        };
        String details = "  battery " + robot.getInt("bat") + "%" + (robot.getBoolean("dock") ? "  dock" : "  no dock");
        g.drawString(font, state, a.x() + 10, y + 10, HudStyle.argb(255, colour), false);
        g.drawString(font, details, a.x() + 10 + font.width(state), y + 10, HudStyle.argb(255, OsDraw.GREY), false);
    }

    // ================================================================ Muis

    void mouseClicked(Font font, StarkOsScreen.Area a, int mx, int my) {
        List<CompoundTag> robots = robots();
        int y = a.y() + 3;
        for (int i = scroll; i < robots.size() && y + ROW_H <= a.y() + a.h(); i++) {
            CompoundTag robot = robots.get(i);
            if (robot.getBoolean("loaded")) {
                int right = a.x() + a.w() - 4;
                int toggleW = OsDraw.buttonWidth(font, switchLabel(robot));
                right -= toggleW;
                if (OsDraw.inside(mx, my, right, y + 2, toggleW, OsDraw.BUTTON_H)) {
                    CompoundTag args = new CompoundTag();
                    args.putString("id", robot.getString("id"));
                    args.putBoolean("on", !robot.getBoolean("on"));
                    screen.send("robot_switch", args);
                    renaming = null;
                    return;
                }
                int renameW = OsDraw.buttonWidth(font, renameLabel(robot));
                right -= renameW + 4;
                if (OsDraw.inside(mx, my, right, y + 2, renameW, OsDraw.BUTTON_H)) {
                    if (robot.getString("id").equals(renaming)) {
                        submitName();
                    } else {
                        startRename(robot);
                    }
                    return;
                }
                if (robot.getString("id").equals(renaming) && OsDraw.inside(mx, my, a.x() + 4, y - 1, right - a.x() - 10, 11)) {
                    nameBox().mouseClicked(mx, my, 0);
                    return;
                }
            }
            y += ROW_H;
        }
        renaming = null;
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
    }

    // ================================================================ Een andere naam

    private void startRename(CompoundTag robot) {
        EditBox box = nameBox();
        box.setValue(robot.getString("n"));
        box.moveCursorToEnd(false);
        box.setHighlightPos(0);
        box.setFocused(true);
        renaming = robot.getString("id");
    }

    private void submitName() {
        String id = renaming;
        renaming = null;
        if (id == null) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putString("id", id);
        args.putString("name", nameBox().getValue());
        screen.send("robot_rename", args);
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renaming == null) {
            return false;
        }
        switch (keyCode) {
            case 257, 335 -> submitName(); // enter
            case 256 -> renaming = null; // esc
            default -> nameBox().keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (renaming == null) {
            return false;
        }
        nameBox().charTyped(codePoint, modifiers);
        return true;
    }

    /** Een ander venster kwam bovenop, of dit venster ging dicht: stop met typen. */
    void blur() {
        renaming = null;
    }
}
