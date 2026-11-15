package com.senne.imttech.client.os;

import com.senne.imttech.bot.BotNetwork;
import com.senne.imttech.client.hud.HudStyle;
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
 * De app Robots van de Security OS-desktop (hoofdstuk 21): de guard robots (en sinds hoofdstuk 25 de security drones)
 * in het gebied van deze kern.
 *
 * Sinds 19 oktober een tabel (jouw notitie: "de robots gui moet beter, zorg dat het ook meer in tabelvorm is en dat de
 * knoppen naast elkaar staan"): één regel per robot met lijntjes ertussen. Klik een robot aan; onderaan staat wat er
 * verder over hem te weten is, en zijn knoppen naast elkaar. (Alle knoppen per rij passen niet in het venster.)
 * <pre>
 *  NAME        TYPE   STATE     BATT  PROGRAM       SQUAD
 *  Guard 1     robot  active    86%   Patrol        Wave 1
 *  ─────────────────────────────────────────────────────────
 *  Drone 1     drone  standby   100%  Hover patrol
 *  ─────────────────────────────────────────────────────────
 *  Guard 4     robot  far away  40%
 *  ─────────────────────────────────────────────────────────
 *  Digger      car    ready     72%   Strip miner
 *
 *  Guard 1  at 12 64 -40  dock  (older than on the server)
 *  [Rename] [Switch off] [Squad] [Program]              [Delete]
 * </pre>
 * Switch on/off zet hem aan of uit; Rename en Squad (hoofdstuk 26) laten je onderaan typen (Enter bewaart, Esc laat het
 * zoals het was); Program (hoofdstuk 23) stuurt hem een robotprogramma van deze server. Een robot in een stuk wereld dat
 * niet geladen is, staat er grijs bij ("far away"): die kun je van hier niet aan- of uitzetten, maar een programma wacht
 * op hem tot hij terug is, en Delete (18 oktober) haalt hem uit de lijst (eerst "Sure?", nog een keer klikken). Bestaat
 * hij toch nog, dan komt hij vanzelf terug als hij weer geladen is. De lijst komt van de server (zie
 * robot/Robots.snapshot, uit BotNetwork).
 *
 * Sinds hoofdstuk 119 staan ook de RC Cars van deze basis erin (soort "car", na de robots; jouw keuze: een RC program
 * laad je op het scherm van de auto en hier). Voor een auto is er alleen de knop Program: die laadt een kopie van een RC
 * program van deze server op hem (zie car/RcCars.snapshot en car/RcCars.desktop). Alleen auto's die nu geladen zijn.
 */
final class RobotsApp {
    /** Zo hoog is één regel van de tabel. */
    private static final int ROW_H = 14;
    /** Zo hoog is één keuze in de lijst van programma's. */
    private static final int OPTION_H = 12;
    /** De kolommen, vanaf de linkerkant: naam, soort, toestand, batterij, programma, squad. */
    private static final int COL_NAME = 4;
    private static final int COL_TYPE = 96;
    private static final int COL_STATE = 134;
    private static final int COL_BATT = 192;
    private static final int COL_PROGRAM = 224;
    private static final int COL_SQUAD = 304;
    /** Onderaan: de regel met meer over de gekozen robot, en de knoppen. */
    private static final int FOOTER_H = 32;

    private final SecurityOsScreen screen;
    private int scroll;
    /** De robot die je aangeklikt hebt (zijn nummer); de knoppen onderaan zijn voor hem. Null = de eerste. */
    private @Nullable String selected;
    /** Welke robot je een andere naam aan het geven bent (zijn nummer), of null. */
    private @Nullable String renaming;
    /** Typ je een squad (hoofdstuk 26) in plaats van een naam? */
    private boolean squadMode;
    private @Nullable EditBox nameBox;
    /** Voor welke robot je een programma kiest (zijn nummer), of null. */
    private @Nullable String choosing;
    /** Delete vraagt eerst "Sure?": voor welke robot (zijn nummer), en tot wanneer die vraag geldt. */
    private @Nullable String deleting;
    private long deletingUntil;

    RobotsApp(SecurityOsScreen screen) {
        this.screen = screen;
    }

    /** De robots en drones, en daarna de RC Cars (hoofdstuk 119: met "car" erbij, zodat de rest weet wat ze zijn). */
    private List<CompoundTag> robots() {
        List<CompoundTag> list = new ArrayList<>();
        for (Tag element : screen.data().getList("robots", Tag.TAG_COMPOUND)) {
            list.add((CompoundTag) element);
        }
        for (Tag element : screen.data().getList("cars", Tag.TAG_COMPOUND)) {
            CompoundTag car = ((CompoundTag) element).copy();
            car.putBoolean("car", true);
            car.putBoolean("loaded", true); // de server stuurt alleen auto's die nu geladen zijn
            list.add(car);
        }
        return list;
    }

    private static boolean isCar(@Nullable CompoundTag row) {
        return row != null && row.getBoolean("car");
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
        return robot.getString("id").equals(renaming) && !squadMode ? "Save" : "Rename";
    }

    private String squadLabel(CompoundTag robot) {
        return robot.getString("id").equals(renaming) && squadMode ? "Save" : "Squad";
    }

    /** De knop Delete van een robot ver weg: na de eerste klik staat er "Sure?" (4 seconden lang). */
    private String deleteLabel(CompoundTag robot) {
        return robot.getString("id").equals(deleting) && net.minecraft.Util.getMillis() < deletingUntil ? "Sure?" : "Delete";
    }

    /**
     * De robotprogramma's op deze server (of de droneprogramma's, voor een drone), en "" = geen programma. Voor een RC Car
     * de RC programs, zonder "geen programma" (gevonden door de nalezing: dat wiste zonder vragen de code van een auto van
     * vóór hoofdstuk 119, en een auto zonder programma heb je nergens voor nodig).
     */
    private List<String> programOptions() {
        CompoundTag robot = robot(choosing);
        boolean drone = robot != null && robot.getBoolean("drone");
        List<String> options = new ArrayList<>();
        String list = isCar(robot) ? "carPrograms" : drone ? "dronePrograms" : "robotPrograms";
        for (Tag tag : screen.data().getList(list, Tag.TAG_STRING)) {
            options.add(tag.getAsString());
        }
        if (!isCar(robot)) {
            options.add("");
        }
        return options;
    }

    /** Staat er bovenaan de keuzelijst "nog geen programma's" (de lijst heeft alleen "geen programma", of niets)? */
    private boolean noPrograms(List<String> options) {
        return options.isEmpty() || options.size() == 1 && options.getFirst().isEmpty();
    }

    private @Nullable CompoundTag robot(@Nullable String id) {
        for (CompoundTag robot : robots()) {
            if (robot.getString("id").equals(id)) {
                return robot;
            }
        }
        return null;
    }

    /** De gekozen robot (bestaat hij niet meer, of koos je er nog geen, dan de eerste), of null als er geen zijn. */
    private @Nullable CompoundTag selectedRobot(List<CompoundTag> robots) {
        for (CompoundTag robot : robots) {
            if (robot.getString("id").equals(selected)) {
                return robot;
            }
        }
        return robots.isEmpty() ? null : robots.getFirst();
    }

    // ================================================================ Waar alles staat

    private static int rowsTop(SecurityOsScreen.Area a) {
        return a.y() + 14;
    }

    private static int rowsBottom(SecurityOsScreen.Area a) {
        return a.y() + a.h() - FOOTER_H;
    }

    private static int visibleRows(SecurityOsScreen.Area a) {
        return Math.max(1, (rowsBottom(a) - rowsTop(a)) / ROW_H);
    }

    /** De regel met meer over de gekozen robot (of het vak om te typen). */
    private static int detailY(SecurityOsScreen.Area a) {
        return a.y() + a.h() - FOOTER_H + 4;
    }

    /** De knoppen onderaan. */
    private static int buttonsY(SecurityOsScreen.Area a) {
        return a.y() + a.h() - OsDraw.BUTTON_H - 3;
    }

    /** Eén knop onderaan: de tekst, waar hij staat, en of hij nu kan. */
    private record Button(String action, String label, int x, int w, boolean enabled, int colour) {}

    /** De knoppen onderaan voor deze robot, van links naar rechts (Delete helemaal rechts). */
    private List<Button> buttons(Font font, SecurityOsScreen.Area a, CompoundTag robot) {
        boolean loaded = robot.getBoolean("loaded");
        String id = robot.getString("id");
        List<Button> buttons = new ArrayList<>();
        int x = a.x() + 4;
        if (isCar(robot)) {
            // Een RC Car (hoofdstuk 119): alleen een RC program laden. Hernoemen doe je met een naamplaatje, oppakken bij hem.
            buttons.add(new Button("program", "Program", x, OsDraw.buttonWidth(font, "Program"), true, 0x9ED8FF));
            return buttons;
        }
        String rename = renameLabel(robot);
        buttons.add(new Button("rename", rename, x, OsDraw.buttonWidth(font, rename), loaded,
                rename.equals("Save") ? HudStyle.GREEN : HudStyle.CYAN));
        x += OsDraw.buttonWidth(font, rename) + 4;
        String toggle = switchLabel(robot);
        buttons.add(new Button("switch", toggle, x, OsDraw.buttonWidth(font, toggle), loaded,
                robot.getBoolean("on") ? HudStyle.RED : HudStyle.GREEN));
        x += OsDraw.buttonWidth(font, toggle) + 4;
        String squad = squadLabel(robot);
        buttons.add(new Button("squad", squad, x, OsDraw.buttonWidth(font, squad), loaded,
                squad.equals("Save") ? HudStyle.GREEN : 0xF1E87C));
        x += OsDraw.buttonWidth(font, squad) + 4;
        buttons.add(new Button("program", "Program", x, OsDraw.buttonWidth(font, "Program"), true, 0x9ED8FF));
        String delete = deleteLabel(robot);
        int deleteW = OsDraw.buttonWidth(font, delete);
        // Delete alleen voor een robot ver weg (een robot die er is, haal je weg door hem op te pakken).
        buttons.add(new Button("delete", delete, a.x() + a.w() - 4 - deleteW, deleteW, !loaded && !id.isEmpty(), HudStyle.RED));
        return buttons;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
        OsDraw.field(g, a.x(), a.y(), a.x() + a.w(), a.y() + a.h(), false);
        List<CompoundTag> robots = robots();
        if (renaming != null && robots.stream().noneMatch(robot -> robot.getString("id").equals(renaming))) {
            renaming = null; // de robot is weg
        }
        if (choosing != null && robot(choosing) == null) {
            choosing = null;
        }
        if (robots.isEmpty()) {
            g.drawString(font, "No guard robots, drones or RC Cars in this network's area yet.", a.x() + 5, a.y() + 5,
                    HudStyle.argb(255, OsDraw.GREY), false);
            g.drawString(font, "Build a robot in a Robot Assembler, or put a Security Drone", a.x() + 5, a.y() + 15,
                    HudStyle.argb(255, OsDraw.GREY), false);
            g.drawString(font, "on a Drone Dock here.", a.x() + 5, a.y() + 25, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        CompoundTag chosen = selectedRobot(robots);
        int[] columns = {a.x() + COL_NAME, a.x() + COL_TYPE, a.x() + COL_STATE, a.x() + COL_BATT, a.x() + COL_PROGRAM,
                a.x() + COL_SQUAD};
        OsDraw.tableHeader(g, font, a.x() + 2, a.x() + a.w() - 2, a.y() + 3, columns,
                new String[] {"NAME", "TYPE", "STATE", "BATT", "PROGRAM", "SQUAD"});
        scroll = Math.max(0, Math.min(scroll, robots.size() - visibleRows(a)));
        int y = rowsTop(a);
        int hoverMx = choosing == null ? mx : -1;
        for (int i = scroll; i < robots.size() && y + ROW_H <= rowsBottom(a); i++) {
            CompoundTag robot = robots.get(i);
            boolean isChosen = robot == chosen;
            if (isChosen) {
                g.fill(a.x() + 2, y, a.x() + a.w() - 2, y + ROW_H - 1, HudStyle.argb(255, 0x16303F));
                g.fill(a.x() + 2, y, a.x() + 4, y + ROW_H - 1, HudStyle.argb(255, HudStyle.CYAN));
            } else if (OsDraw.inside(hoverMx, my, a.x() + 2, y, a.w() - 4, ROW_H - 1)) {
                g.fill(a.x() + 2, y, a.x() + a.w() - 2, y + ROW_H - 1, HudStyle.argb(255, 0x0F1E27));
            }
            renderRow(g, font, a, y, robot);
            OsDraw.rowLine(g, a.x() + 2, a.x() + a.w() - 2, y + ROW_H - 1);
            y += ROW_H;
        }
        if (scroll > 0 || scroll + visibleRows(a) < robots.size()) {
            String more = (scroll > 0 ? "▲ " : "") + (scroll + visibleRows(a) < robots.size() ? "▼" : "");
            g.drawString(font, more, a.x() + a.w() - 4 - font.width(more), a.y() + 3, HudStyle.argb(255, OsDraw.GREY), false);
        }
        if (chosen != null) {
            renderFooter(g, font, a, chosen, choosing == null ? mx : -1, my, partialTick);
        }
        if (choosing != null) {
            renderChooser(g, font, a, mx, my);
        }
    }

    /** Eén robot op één regel: naam, soort, toestand, batterij, programma en squad. */
    private void renderRow(GuiGraphics g, Font font, SecurityOsScreen.Area a, int y, CompoundTag robot) {
        if (isCar(robot)) {
            renderCarRow(g, font, a, y, robot);
            return;
        }
        boolean loaded = robot.getBoolean("loaded");
        int ty = y + 3;
        cell(g, font, robot.getString("n"), a.x() + COL_NAME, ty, COL_TYPE - COL_NAME - 4, loaded ? HudStyle.WHITE : OsDraw.GREY);
        boolean drone = robot.getBoolean("drone");
        cell(g, font, drone ? "drone" : "robot", a.x() + COL_TYPE, ty, COL_STATE - COL_TYPE - 4, drone ? 0x8FE3FF : 0xB9C7CC);
        String state = loaded ? robot.getString("st") : "far away";
        int colour = !loaded ? OsDraw.GREY : switch (state) {
            case "active" -> HudStyle.GREEN;
            case "standby" -> HudStyle.ORANGE;
            default -> HudStyle.RED;
        };
        cell(g, font, state, a.x() + COL_STATE, ty, COL_BATT - COL_STATE - 4, colour);
        int battery = robot.getInt("bat");
        cell(g, font, battery + "%", a.x() + COL_BATT, ty, COL_PROGRAM - COL_BATT - 4,
                battery < 20 ? HudStyle.RED : battery < 50 ? HudStyle.ORANGE : OsDraw.GREY);
        String program = robot.getString("prog");
        boolean warn = robot.getBoolean("old") || robot.contains("wait") || loaded && !robot.getBoolean("link");
        cell(g, font, program.isEmpty() ? "-" : program, a.x() + COL_PROGRAM, ty, COL_SQUAD - COL_PROGRAM - 4,
                program.isEmpty() ? OsDraw.GREY : warn ? HudStyle.ORANGE : 0x9ED8FF);
        String squad = robot.getString("sq");
        cell(g, font, squad.isEmpty() ? "-" : squad, a.x() + COL_SQUAD, ty, a.w() - COL_SQUAD - 4,
                squad.isEmpty() ? OsDraw.GREY : 0xF1E87C);
    }

    /**
     * Eén RC Car op één regel (hoofdstuk 119): naam, "car", wat hij doet, zijn batterij (een streepje zonder cel), zijn RC
     * program (oranje als de server er intussen een nieuwere versie van heeft) en geen squad.
     */
    private void renderCarRow(GuiGraphics g, Font font, SecurityOsScreen.Area a, int y, CompoundTag car) {
        int ty = y + 3;
        cell(g, font, car.getString("n"), a.x() + COL_NAME, ty, COL_TYPE - COL_NAME - 4, HudStyle.WHITE);
        cell(g, font, "car", a.x() + COL_TYPE, ty, COL_STATE - COL_TYPE - 4, LogicApp.CAR_COLOUR);
        String state = car.getString("st");
        int colour = switch (state) {
            case "running" -> HudStyle.GREEN;
            case "ready" -> HudStyle.CYAN;
            default -> HudStyle.RED; // no cell, cell empty, jammed
        };
        cell(g, font, state, a.x() + COL_STATE, ty, COL_BATT - COL_STATE - 4, colour);
        int battery = car.getInt("bat");
        cell(g, font, battery < 0 ? "-" : battery + "%", a.x() + COL_BATT, ty, COL_PROGRAM - COL_BATT - 4,
                battery < 0 ? OsDraw.GREY : battery < 20 ? HudStyle.RED : battery < 50 ? HudStyle.ORANGE : OsDraw.GREY);
        String program = car.getString("prog");
        cell(g, font, program.isEmpty() ? "-" : program, a.x() + COL_PROGRAM, ty, COL_SQUAD - COL_PROGRAM - 4,
                program.isEmpty() || program.startsWith("(") ? OsDraw.GREY
                        : car.getBoolean("old") || car.getBoolean("other") ? HudStyle.ORANGE : 0x9ED8FF);
        cell(g, font, "-", a.x() + COL_SQUAD, ty, a.w() - COL_SQUAD - 4, OsDraw.GREY);
    }

    /** Tekst in een vakje van de tabel, afgeknipt als hij te lang is. */
    private static void cell(GuiGraphics g, Font font, String text, int x, int y, int width, int rgb) {
        g.drawString(font, OsDraw.fitRight(font, text, width), x, y, HudStyle.argb(255, rgb), false);
    }

    /** Onderaan: meer over de gekozen robot (of het vak om te typen), en zijn knoppen naast elkaar. */
    private void renderFooter(GuiGraphics g, Font font, SecurityOsScreen.Area a, CompoundTag robot, int mx, int my,
                              float partialTick) {
        g.fill(a.x() + 2, rowsBottom(a), a.x() + a.w() - 2, rowsBottom(a) + 1, HudStyle.argb(255, OsDraw.DIM));
        int y = detailY(a);
        if (robot.getString("id").equals(renaming)) {
            String label = squadMode ? "Squad:" : "Name:";
            g.drawString(font, label, a.x() + 5, y + 1, HudStyle.argb(255, squadMode ? 0xF1E87C : HudStyle.CYAN), false);
            int boxX = a.x() + 9 + font.width(label);
            int boxRight = a.x() + a.w() / 2 + 40;
            OsDraw.field(g, boxX - 2, y - 1, boxRight, y + 10, true);
            EditBox box = nameBox();
            box.setX(boxX + 1);
            box.setY(y + 1);
            box.setWidth(boxRight - boxX - 4);
            box.render(g, mx, my, partialTick);
            String hint = squadMode ? "like Wave 1, empty = none; Enter = save, Esc = cancel" : "Enter = save, Esc = cancel";
            g.drawString(font, OsDraw.fitRight(font, hint, a.x() + a.w() - boxRight - 8), boxRight + 4, y + 1,
                    HudStyle.argb(255, OsDraw.GREY), false);
        } else if (isCar(robot)) {
            // Een RC Car (hoofdstuk 119): waar hij staat, of hij een arm heeft, en wat er met zijn programma is.
            String info = "at " + robot.getInt("x") + " " + robot.getInt("y") + " " + robot.getInt("z")
                    + (robot.getBoolean("arm") ? "   robot arm" : "   no robot arm");
            g.drawString(font, robot.getString("n"), a.x() + 5, y + 1, HudStyle.argb(255, HudStyle.WHITE), false);
            int x = a.x() + 11 + font.width(robot.getString("n"));
            g.drawString(font, info, x, y + 1, HudStyle.argb(255, OsDraw.GREY), false);
            x += font.width(info);
            String note = robot.getBoolean("old") ? "   (older than on the server: load it again)"
                    : robot.getBoolean("other") ? "   (from the server of another base)"
                    : robot.getString("prog").startsWith("(") ? "   (code from before RC programs)" : "";
            if (!note.isEmpty()) {
                g.drawString(font, OsDraw.fitRight(font, note, Math.max(0, a.x() + a.w() - x - 4)), x, y + 1,
                        HudStyle.argb(255, HudStyle.ORANGE), false);
            }
        } else {
            boolean loaded = robot.getBoolean("loaded");
            StringBuilder info = new StringBuilder();
            info.append("at ").append(robot.getInt("x")).append(' ').append(robot.getInt("y")).append(' ').append(robot.getInt("z"));
            info.append(robot.getBoolean("dock") ? "   dock" : "   no dock");
            g.drawString(font, robot.getString("n"), a.x() + 5, y + 1, HudStyle.argb(255, loaded ? HudStyle.WHITE : OsDraw.GREY), false);
            int x = a.x() + 11 + font.width(robot.getString("n"));
            g.drawString(font, info.toString(), x, y + 1, HudStyle.argb(255, OsDraw.GREY), false);
            x += font.width(info.toString());
            // Wat er met zijn programma is (hoofdstuk 23): wacht het op hem, is het ouder dan op de server, of praat hij
            // niet met de server?
            String note = robot.contains("wait") ? "   waiting: " + ("-".equals(robot.getString("wait")) ? "no program" : robot.getString("wait"))
                    : robot.getBoolean("old") ? "   (older than on the server)"
                    : loaded && !robot.getBoolean("link") ? "   no connection" : "";
            if (!note.isEmpty()) {
                int noteColour = loaded && !robot.getBoolean("link") && !robot.contains("wait") && !robot.getBoolean("old")
                        ? HudStyle.RED : HudStyle.ORANGE;
                g.drawString(font, OsDraw.fitRight(font, note, Math.max(0, a.x() + a.w() - x - 4)), x, y + 1,
                        HudStyle.argb(255, noteColour), false);
            }
        }
        for (Button button : buttons(font, a, robot)) {
            OsDraw.button(g, font, button.x(), buttonsY(a), button.label(), button.enabled(), button.colour());
        }
    }

    /** Waar de lijst met programma's staat: midden in de app, zo hoog als nodig. */
    private int[] chooserBox(SecurityOsScreen.Area a) {
        int w = Math.min(a.w() - 20, 200);
        List<String> options = programOptions();
        int h = 30 + (options.size() + (noPrograms(options) ? 1 : 0)) * OPTION_H + OsDraw.BUTTON_H;
        return new int[] {a.x() + (a.w() - w) / 2, a.y() + 8, w, Math.min(h, a.h() - 16)};
    }

    /** De keuze na de knop Program: de robotprogramma's van deze server, "no program", en Cancel. */
    private void renderChooser(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my) {
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
        if (noPrograms(options)) {
            boolean drone = robot != null && robot.getBoolean("drone");
            String none = isCar(robot) ? "No RC programs" : drone ? "No drone programs" : "No robot programs";
            g.drawString(font, OsDraw.fitLeft(font, none + " yet: make one in Logic (+)",
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

    // ================================================================ Muis

    void mouseClicked(Font font, SecurityOsScreen.Area a, int mx, int my) {
        if (choosing != null) {
            clickChooser(font, a, mx, my);
            return;
        }
        List<CompoundTag> robots = robots();
        CompoundTag chosen = selectedRobot(robots);
        // De knoppen onderaan (voor de gekozen robot).
        if (chosen != null) {
            for (Button button : buttons(font, a, chosen)) {
                if (OsDraw.inside(mx, my, button.x(), buttonsY(a), button.w(), OsDraw.BUTTON_H)) {
                    if (button.enabled()) {
                        press(button.action(), chosen);
                    }
                    return;
                }
            }
            if (chosen.getString("id").equals(renaming) && my >= detailY(a) - 1 && my < detailY(a) + 10) {
                nameBox().mouseClicked(mx, my, 0); // de cursor verplaatsen
                return;
            }
        }
        // Een regel van de tabel: die robot kiezen.
        int y = rowsTop(a);
        for (int i = scroll; i < robots.size() && y + ROW_H <= rowsBottom(a); i++) {
            if (OsDraw.inside(mx, my, a.x() + 2, y, a.w() - 4, ROW_H)) {
                String id = robots.get(i).getString("id");
                if (!id.equals(selected)) {
                    renaming = null;
                    deleting = null;
                }
                selected = id;
                return;
            }
            y += ROW_H;
        }
        renaming = null;
        deleting = null; // ergens anders geklikt: de vraag "Sure?" vervalt
    }

    /** Een knop onderaan, voor deze robot. */
    private void press(String action, CompoundTag robot) {
        String id = robot.getString("id");
        switch (action) {
            case "rename" -> {
                if (id.equals(renaming) && !squadMode) {
                    submitName();
                } else {
                    startRename(robot);
                }
            }
            case "squad" -> {
                if (id.equals(renaming) && squadMode) {
                    submitName();
                } else {
                    startSquad(robot);
                }
            }
            case "switch" -> {
                CompoundTag args = new CompoundTag();
                args.putString("id", id);
                args.putBoolean("on", !robot.getBoolean("on"));
                screen.send("robot_switch", args);
                renaming = null;
            }
            case "program" -> {
                choosing = id;
                renaming = null;
            }
            case "delete" -> {
                // De eerste klik vraagt "Sure?", de tweede haalt hem echt uit de lijst.
                if (deleteLabel(robot).equals("Sure?")) {
                    CompoundTag args = new CompoundTag();
                    args.putString("id", id);
                    screen.send("robot_delete", args);
                    deleting = null;
                } else {
                    deleting = id;
                    deletingUntil = net.minecraft.Util.getMillis() + 4000;
                }
                renaming = null;
            }
            default -> { }
        }
    }

    /** Een klik in de lijst met programma's: dat programma naar de robot, of Cancel (of ernaast: ook dicht). */
    private void clickChooser(Font font, SecurityOsScreen.Area a, int mx, int my) {
        int[] box = chooserBox(a);
        int oy = box[1] + 16 + (noPrograms(programOptions()) ? OPTION_H : 0);
        for (String option : programOptions()) {
            if (OsDraw.inside(mx, my, box[0] + 2, oy - 1, box[2] - 4, OPTION_H)) {
                CompoundTag args = new CompoundTag();
                args.putString("id", choosing);
                args.putString("program", option);
                // Hoofdstuk 119: voor een RC Car een eigen knop (de server zoekt hem bij de auto's, zie car/RcCars.desktop).
                screen.send(isCar(robot(choosing)) ? "robot_car_program" : "robot_program", args);
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
        scroll = 0;
        deleting = null;
        selected = robots.isEmpty() ? null : robots.getFirst().getString("id");
        choosing = selected;
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 119): de eerste RC Car gekozen en in beeld, eventueel met zijn keuzelijst. */
    void tourChooseCar(boolean chooser) {
        List<CompoundTag> robots = robots();
        deleting = null;
        for (int i = 0; i < robots.size(); i++) {
            if (isCar(robots.get(i))) {
                scroll = Math.max(0, i - 2);
                selected = robots.get(i).getString("id");
                choosing = chooser ? selected : null;
                return;
            }
        }
    }

    /** Alleen voor de screenshot-rondleiding: de eerste robot in een squad gekozen en in beeld (één robot erboven). */
    void tourSquad() {
        List<CompoundTag> robots = robots();
        for (int i = 0; i < robots.size(); i++) {
            if (!robots.get(i).getString("sq").isEmpty()) {
                scroll = Math.max(0, i - 1);
                selected = robots.get(i).getString("id");
                return;
            }
        }
    }

    /**
     * Alleen voor de screenshot-rondleiding: de eerste robot ver weg gekozen en in beeld (twee robots erboven), met
     * "Sure?" op zijn knop Delete.
     */
    void tourAskDelete() {
        List<CompoundTag> robots = robots();
        for (int i = 0; i < robots.size(); i++) {
            if (!robots.get(i).getBoolean("loaded")) {
                scroll = Math.max(0, i - 2);
                selected = robots.get(i).getString("id");
                deleting = selected;
                deletingUntil = net.minecraft.Util.getMillis() + 60_000;
                return;
            }
        }
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
        selected = renaming;
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
        deleting = null;
    }
}
