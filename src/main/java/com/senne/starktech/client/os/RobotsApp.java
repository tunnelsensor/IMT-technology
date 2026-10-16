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
 * De app Robots van de Stark OS-desktop (hoofdstuk 21): de guard robots (en sinds hoofdstuk 25 de security drones,
 * met "drone" achter hun naam) in het gebied van deze kern, elk met
 * zijn naam, waar hij staat, hoe het met hem gaat, zijn batterij en of hij een dock heeft. Per robot een knop
 * Switch on/off en Rename (typ de naam, Enter bewaart, Esc laat het zoals het was), en (hoofdstuk 23) welk
 * programma hij draait, met de knop Program om hem een robotprogramma van deze server te sturen.
 *
 * <pre>
 *  Guard 1   12 64 -40                      [Rename] [Switch off]
 *    active  battery 86%  dock
 *    program Patrol  (older than on the server)       [Program]
 *  Door guard   20 64 -38                   [Rename] [Switch on]
 *    standby  battery 100%  dock
 *    no program                                       [Program]
 * </pre>
 * Een robot in een stuk wereld dat niet geladen is, staat er grijs bij ("far away"): die kun je van hier
 * niet aan- of uitzetten, maar een programma wacht op hem tot hij terug is. De lijst komt van de server (zie
 * robot/Robots.snapshot, uit BotNetwork).
 */
final class RobotsApp {
    private static final int ROW_H = 31;
    /** Zo hoog is één keuze in de lijst van programma's. */
    private static final int OPTION_H = 12;

    private final StarkOsScreen screen;
    private int scroll;
    /** Welke robot je een andere naam aan het geven bent (zijn nummer), of null. */
    private @Nullable String renaming;
    /** Typ je een squad (hoofdstuk 26) in plaats van een naam? */
    private boolean squadMode;
    private @Nullable EditBox nameBox;
    /** Voor welke robot je een programma kiest (zijn nummer), of null. */
    private @Nullable String choosing;

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

    private String squadLabel(CompoundTag robot) {
        return "Squad";
    }

    /** De robotprogramma's op deze server (of de droneprogramma's, voor een drone), en "" = geen programma. */
    private List<String> programOptions() {
        CompoundTag robot = robot(choosing);
        boolean drone = robot != null && robot.getBoolean("drone");
        List<String> options = new ArrayList<>();
        for (Tag tag : screen.data().getList(drone ? "dronePrograms" : "robotPrograms", Tag.TAG_STRING)) {
            options.add(tag.getAsString());
        }
        options.add("");
        return options;
    }

    private @Nullable CompoundTag robot(@Nullable String id) {
        for (CompoundTag robot : robots()) {
            if (robot.getString("id").equals(id)) {
                return robot;
            }
        }
        return null;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my, float partialTick) {
        OsDraw.field(g, a.x(), a.y(), a.x() + a.w(), a.y() + a.h(), false);
        List<CompoundTag> robots = robots();
        if (renaming != null && robots.stream().noneMatch(robot -> robot.getString("id").equals(renaming))) {
            renaming = null; // de robot is weg
        }
        if (choosing != null && robot(choosing) == null) {
            choosing = null;
        }
        if (robots.isEmpty()) {
            g.drawString(font, "No guard robots or drones in this network's area yet.", a.x() + 5, a.y() + 5, HudStyle.argb(255, OsDraw.GREY), false);
            g.drawString(font, "Build a robot in a Robot Assembler, or put a Security Drone", a.x() + 5, a.y() + 15,
                    HudStyle.argb(255, OsDraw.GREY), false);
            g.drawString(font, "on a Drone Dock here.", a.x() + 5, a.y() + 25, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        scroll = Math.max(0, Math.min(scroll, robots.size() - 1));
        int y = a.y() + 3;
        for (int i = scroll; i < robots.size() && y + ROW_H <= a.y() + a.h(); i++) {
            renderRobot(g, font, a, y, robots.get(i), choosing == null ? mx : -1, my, partialTick);
            y += ROW_H;
        }
        if (choosing != null) {
            renderChooser(g, font, a, mx, my);
        }
    }

    /** Waar de lijst met programma's staat: midden in de app, zo hoog als nodig. */
    private int[] chooserBox(StarkOsScreen.Area a) {
        int w = Math.min(a.w() - 20, 200);
        int h = 30 + programOptions().size() * OPTION_H + OsDraw.BUTTON_H;
        return new int[] {a.x() + (a.w() - w) / 2, a.y() + 8, w, Math.min(h, a.h() - 16)};
    }

    /** De keuze na de knop Program: de robotprogramma's van deze server, "no program", en Cancel. */
    private void renderChooser(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my) {
        CompoundTag robot = robot(choosing);
        int[] box = chooserBox(a);
        int x = box[0];
        int y = box[1];
        g.fill(a.x(), a.y(), a.x() + a.w(), a.y() + a.h(), HudStyle.argb(190, 0x05080B)); // de lijst erachter wat donkerder
        g.fill(x, y, x + box[2], y + box[3], HudStyle.argb(250, 0x0B141B));
        OsDraw.field(g, x, y, x + box[2], y + box[3], true);
        g.drawString(font, OsDraw.fitLeft(font, "Program for " + (robot == null ? "?" : robot.getString("n")) + ":", box[2] - 8),
                x + 4, y + 4, HudStyle.argb(255, HudStyle.ORANGE), false);
        int oy = y + 16;
        List<String> options = programOptions();
        if (options.size() == 1) {
            boolean drone = robot != null && robot.getBoolean("drone");
            g.drawString(font, OsDraw.fitLeft(font, (drone ? "No drone programs" : "No robot programs") + " yet: make one in Logic (+)",
                    box[2] - 8), x + 4, oy, HudStyle.argb(255, OsDraw.GREY), false);
            oy += OPTION_H;
        }
        String current = robot == null ? "" : robot.getString("prog");
        for (String option : options) {
            boolean hover = OsDraw.inside(mx, my, x + 2, oy - 1, box[2] - 4, OPTION_H);
            if (hover) {
                g.fill(x + 2, oy - 2, x + box[2] - 2, oy + OPTION_H - 2, HudStyle.argb(255, 0x16303F));
            }
            String label = (option.equals(current) ? "> " : "  ") + (option.isEmpty() ? "(no program)" : option);
            g.drawString(font, OsDraw.fitLeft(font, label, box[2] - 8), x + 4, oy,
                    HudStyle.argb(255, option.isEmpty() ? OsDraw.GREY : 0x9ED8FF), false);
            oy += OPTION_H;
        }
        OsDraw.button(g, font, x + box[2] - 4 - OsDraw.buttonWidth(font, "Cancel"), y + box[3] - OsDraw.BUTTON_H - 3, "Cancel", true,
                HudStyle.CYAN);
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
            g.drawString(font, squadMode ? "Squad (like Wave 1; empty = none): Enter = save   Esc = cancel"
                    : "Enter = save   Esc = cancel", a.x() + 10, y + 11, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        String name = robot.getString("n");
        g.drawString(font, name, a.x() + 6, y + 1, HudStyle.argb(255, loaded ? HudStyle.WHITE : OsDraw.GREY), false);
        int wx = a.x() + 10 + font.width(name);
        if (robot.getBoolean("drone")) {
            // Een security drone (hoofdstuk 25): dat staat achter zijn naam.
            g.drawString(font, "drone", wx, y + 1, HudStyle.argb(255, 0x8FE3FF), false);
            wx += font.width("drone ") + 2;
        }
        String where = robot.getInt("x") + " " + robot.getInt("y") + " " + robot.getInt("z");
        g.drawString(font, where, wx, y + 1, HudStyle.argb(255, OsDraw.GREY), false);
        String state = loaded ? robot.getString("st") : "far away";
        int colour = !loaded ? OsDraw.GREY : switch (state) {
            case "active" -> HudStyle.GREEN;
            case "standby" -> HudStyle.ORANGE;
            default -> HudStyle.RED;
        };
        String details = "  battery " + robot.getInt("bat") + "%" + (robot.getBoolean("dock") ? "  dock" : "  no dock")
                + (robot.getString("sq").isEmpty() ? "" : "  squad " + robot.getString("sq"));
        g.drawString(font, state, a.x() + 10, y + 10, HudStyle.argb(255, colour), false);
        g.drawString(font, details, a.x() + 10 + font.width(state), y + 10, HudStyle.argb(255, OsDraw.GREY), false);
        // Zijn programma (hoofdstuk 23), en de knop om hem een ander te sturen.
        String program = robot.getString("prog");
        int programRight = a.x() + a.w() - 4 - OsDraw.buttonWidth(font, "Program");
        OsDraw.button(g, font, programRight, y + 17, "Program", true, 0x9ED8FF);
        if (loaded) {
            // De squad (hoofdstuk 26): "Wave 1".
            programRight -= OsDraw.buttonWidth(font, squadLabel(robot)) + 4;
            OsDraw.button(g, font, programRight, y + 17, squadLabel(robot), true, 0xF1E87C);
        }
        int px = a.x() + 10;
        String line = program.isEmpty() ? "no program" : "program " + program;
        g.drawString(font, line, px, y + 19, HudStyle.argb(255, program.isEmpty() ? OsDraw.GREY : 0x9ED8FF), false);
        px += font.width(line);
        String note = robot.contains("wait") ? "  waiting: " + ("-".equals(robot.getString("wait")) ? "no program" : robot.getString("wait"))
                : robot.getBoolean("old") ? "  (older than on the server)"
                : loaded && !robot.getBoolean("link") ? "  no connection" : "";
        if (!note.isEmpty()) {
            int noteColour = loaded && !robot.getBoolean("link") && !robot.contains("wait") && !robot.getBoolean("old")
                    ? HudStyle.RED : HudStyle.ORANGE;
            g.drawString(font, OsDraw.fitLeft(font, note, Math.max(0, programRight - px - 4)), px, y + 19,
                    HudStyle.argb(255, noteColour), false);
        }
    }

    // ================================================================ Muis

    void mouseClicked(Font font, StarkOsScreen.Area a, int mx, int my) {
        if (choosing != null) {
            clickChooser(font, a, mx, my);
            return;
        }
        List<CompoundTag> robots = robots();
        int y = a.y() + 3;
        for (int i = scroll; i < robots.size() && y + ROW_H <= a.y() + a.h(); i++) {
            CompoundTag robot = robots.get(i);
            int programW = OsDraw.buttonWidth(font, "Program");
            if (OsDraw.inside(mx, my, a.x() + a.w() - 4 - programW, y + 17, programW, OsDraw.BUTTON_H)) {
                choosing = robot.getString("id");
                renaming = null;
                return;
            }
            int squadW = OsDraw.buttonWidth(font, squadLabel(robot));
            if (robot.getBoolean("loaded")
                    && OsDraw.inside(mx, my, a.x() + a.w() - 4 - programW - 4 - squadW, y + 17, squadW, OsDraw.BUTTON_H)) {
                startSquad(robot);
                return;
            }
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

    /** Een klik in de lijst met programma's: dat programma naar de robot, of Cancel (of ernaast: ook dicht). */
    private void clickChooser(Font font, StarkOsScreen.Area a, int mx, int my) {
        int[] box = chooserBox(a);
        int oy = box[1] + 16 + (programOptions().size() == 1 ? OPTION_H : 0);
        for (String option : programOptions()) {
            if (OsDraw.inside(mx, my, box[0] + 2, oy - 1, box[2] - 4, OPTION_H)) {
                CompoundTag args = new CompoundTag();
                args.putString("id", choosing);
                args.putString("program", option);
                screen.send("robot_program", args);
                choosing = null;
                return;
            }
            oy += OPTION_H;
        }
        choosing = null; // Cancel, of ernaast geklikt
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
    }

    /** Alleen voor de screenshot-rondleiding: de lijst met programma's voor de eerste robot. */
    void tourChooseProgram() {
        List<CompoundTag> robots = robots();
        choosing = robots.isEmpty() ? null : robots.getFirst().getString("id");
    }

    // ================================================================ Een andere naam

    /** De squad van deze robot typen (hoofdstuk 26). */
    private void startSquad(CompoundTag robot) {
        startRename(robot);
        squadMode = true;
        nameBox().setValue(robot.getString("sq"));
        nameBox().moveCursorToEnd(false);
        nameBox().setHighlightPos(0);
    }

    private void startRename(CompoundTag robot) {
        squadMode = false;
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
        if (squadMode) {
            args.putString("squad", nameBox().getValue());
            screen.send("robot_squad", args);
            return;
        }
        args.putString("name", nameBox().getValue());
        screen.send("robot_rename", args);
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (choosing != null && keyCode == 256) {
            choosing = null; // esc
            return true;
        }
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

    /** Een ander venster kwam bovenop, of dit venster ging dicht: stop met typen (en met kiezen). */
    void blur() {
        renaming = null;
        choosing = null;
    }
}
