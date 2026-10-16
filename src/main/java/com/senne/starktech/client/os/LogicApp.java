package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.logic.program.GraphConverter;
import com.senne.starktech.logic.program.LogicException;
import com.senne.starktech.logic.program.LogicGraph;
import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.logic.program.Op;
import com.senne.starktech.logic.program.ProgramKind;
import com.senne.starktech.logic.program.Script;
import com.senne.starktech.logic.program.ScriptParser;
import com.senne.starktech.logic.program.ScriptPrinter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * De app Logic op de Stark OS-desktop (jouw wens, 7 oktober): hier programmeer je de redstone van
 * je basis. Links je diagrammen (en hoe vol je SSD's zijn), rechts de code van het diagram dat open
 * is.
 * <pre>
 *  DIAGRAMS  [+]   | Name [Lab______] [Check] [Save] [On] [Delete]      [Help]
 *  * Lab           |  1 when keycard("Lab door"):
 *    Lights        |  2     if card.valid and card.level &gt;= 3:
 *                  |  3         accept()
 *  SSD 14/48       |  ...
 *  running 1       | No problems: 9 blocks
 * </pre>
 * "Check" laat zien of de code klopt (en springt naar de regel met de fout); "Save" stuurt hem naar
 * de server, die alles nog eens controleert. "Help" toont alles wat je kunt gebruiken, met de namen
 * van jouw apparaten: klik erop en het komt in je code. Ctrl+Spatie in de code toont wat je op die
 * plek kunt typen (zie CodeCompletion).
 *
 * De server bewaart blokjes (zie LogicGraph); de code is een andere manier om ze te laten zien (zie
 * GraphConverter). Je commentaar (#) blijft bewaard zolang de code bij de blokjes past.
 */
final class LogicApp {
    private static final int SIDE = 92;
    private static final int ROW = 12;
    private static final int TOOLBAR = OsDraw.BUTTON_H + 4;
    private static final int STATUS = 11;
    private static final int HELP_W = 152;

    /** Eén diagram zoals de server het stuurde. */
    private record Entry(String name, boolean on, LogicGraph graph, String source, ProgramKind kind) {}

    /** Code of blokjes: twee manieren om hetzelfde diagram te bewerken. */
    private enum Mode { CODE, BLOCKS }

    private final StarkOsScreen screen;
    private final CodeEditor editor;
    private final BlockCanvas canvas;
    private Mode mode = Mode.CODE;
    /** De blokjes zoals je ze nu bewerkt (de werkversie). */
    private @Nullable LogicGraph blockGraph;
    /** De code toen je naar de blokjes wisselde: is hij nog hetzelfde, dan houden de blokjes alles (ook losse). */
    private String codeAtSwitch = "";
    /** Veranderde je iets in de blokjes sinds het openen of bewaren? */
    private boolean blocksEdited;
    /** Wat de blokjes-editor zegt over het blokje onder de muis (alleen dit beeld). */
    private String hoverHint = "";
    private final List<Entry> entries = new ArrayList<>();
    private int revision = -1;

    /** Het diagram dat open is: zijn naam op de server (null = nieuw, nog niet bewaard). */
    private @Nullable String current;
    /** Wat voor programma er open is (hoofdstuk 23): een diagram op de server, of een robotprogramma. */
    private ProgramKind kind = ProgramKind.SERVER;
    /** Na een klik op +: kiezen wat voor programma je maakt (twee knoppen in de zijbalk). */
    private boolean choosingKind;
    private boolean open;
    private String loadedCode = "";
    private String loadedName = "";
    /** De blokjes zoals ze op de server staan (zodat ze bij opslaan hun plek houden). */
    private @Nullable LogicGraph baseGraph;
    /** Na Save: dit diagram openen zodra de server het terugstuurt. */
    private @Nullable String pendingSelect;

    private String status = "";
    private int statusColour = OsDraw.GREY;
    /** Een klik die nog bevestigd moet worden (Delete, of weg van een diagram met wijzigingen). */
    private @Nullable String confirm;
    private long confirmUntil;

    private @Nullable EditBox nameBox;
    private boolean helpOpen;
    private int helpScroll;
    private int sideScroll;
    /** Bij Help: welke lezer of welk paneel je een andere naam geeft (of null). */
    private @Nullable CompoundTag renaming;
    private @Nullable EditBox renameBox;

    LogicApp(StarkOsScreen screen, Font font) {
        this.screen = screen;
        this.editor = new CodeEditor(font);
        // Ctrl+Spatie: de namen van je apparaten, lezers en panelen komen van de server.
        this.editor.setCompletionData(this::completionData);
        this.canvas = new BlockCanvas(this::info, () -> blocksEdited = true, text -> hoverHint = text);
    }

    private CompoundTag info() {
        return screen.data().getCompound("logicInfo");
    }

    private CompoundTag server() {
        return screen.data().getCompound("server");
    }

    private EditBox nameBox() {
        if (nameBox == null) {
            nameBox = new EditBox(Minecraft.getInstance().font, 0, 0, 80, 10, Component.literal("Name"));
            nameBox.setBordered(false);
            nameBox.setMaxLength(LogicProgram.MAX_NAME);
            nameBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
        }
        return nameBox;
    }

    private EditBox renameBox() {
        if (renameBox == null) {
            renameBox = new EditBox(Minecraft.getInstance().font, 0, 0, 80, 10, Component.literal("Name"));
            renameBox.setBordered(false);
            renameBox.setMaxLength(24);
            renameBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
        }
        return renameBox;
    }

    // ================================================================ Van de server

    /** Nieuwe diagrammen van de server (alleen als er iets veranderde). */
    void update(CompoundTag logic) {
        revision = logic.getInt("rev");
        entries.clear();
        for (Tag element : logic.getList("programs", Tag.TAG_COMPOUND)) {
            LogicProgram program = LogicProgram.load((CompoundTag) element);
            entries.add(new Entry(program.name(), program.enabled(), program.graph(), program.source(), program.kind()));
        }
        if (pendingSelect != null && find(pendingSelect) != null) {
            openEntry(find(pendingSelect), true);
            pendingSelect = null;
            return;
        }
        if (open && current != null) {
            Entry entry = find(current);
            if (entry == null) {
                // Weg (verwijderd): open het eerste andere diagram, tenzij je hier nog aan werkt.
                if (!dirty()) {
                    open = false;
                    current = null;
                }
            } else if (!dirty()) {
                openEntry(entry, true);
            }
        }
        if (!open && !entries.isEmpty()) {
            openEntry(entries.getFirst(), false);
        }
    }

    private @Nullable Entry find(String name) {
        for (Entry entry : entries) {
            if (entry.name().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    /** Opent dit diagram in de editor. {@code keepCursor}: is de code hetzelfde, laat de editor dan met rust. */
    private void openEntry(Entry entry, boolean keepCursor) {
        current = entry.name();
        open = true;
        setKind(entry.kind());
        baseGraph = entry.graph();
        blockGraph = entry.graph().copy();
        blocksEdited = false;
        if (mode == Mode.BLOCKS) {
            if (keepCursor) {
                canvas.replaceGraph(blockGraph);
            } else {
                canvas.setGraph(blockGraph);
            }
        }
        String code = codeFor(entry);
        if (!keepCursor || !editor.text().equals(code)) {
            editor.setText(code);
        }
        loadedCode = code;
        codeAtSwitch = code;
        loadedName = entry.name();
        nameBox().setValue(entry.name());
        confirm = null;
        check(false);
        int loose = entry.graph().size() - entry.graph().reachable().size();
        if (loose > 0) {
            show(loose + (loose == 1 ? " loose block is" : " loose blocks are") + " not in the code (Save removes them)", HudStyle.ORANGE);
        }
    }

    /** De code van een diagram: zoals jij hem schreef als die nog bij de blokjes past, anders uit de blokjes. */
    static String codeFor(LogicGraph graph, String source) {
        return codeFor(graph, source, ProgramKind.SERVER);
    }

    static String codeFor(LogicGraph graph, String source, ProgramKind kind) {
        String printed = ScriptPrinter.print(GraphConverter.toScript(graph));
        if (!source.isEmpty()) {
            try {
                if (ScriptPrinter.print(ScriptParser.parse(source, kind)).equals(printed)) {
                    return source;
                }
            } catch (LogicException e) {
                // De bewaarde code klopt niet meer: dan de code uit de blokjes.
            }
        }
        return printed;
    }

    private static String codeFor(Entry entry) {
        return codeFor(entry.graph(), entry.source(), entry.kind());
    }

    /** Een ander soort programma open: het palet en Ctrl+Spatie gaan mee. */
    private void setKind(ProgramKind kind) {
        this.kind = kind;
        canvas.setKind(kind);
    }

    /** Zijn er wijzigingen die nog niet bewaard zijn? */
    boolean dirty() {
        return open && (current == null || blocksEdited || !editor.text().equals(loadedCode)
                || !nameBox().getValue().strip().equals(loadedName));
    }

    // ================================================================ Code <-> blokjes

    /** Wisselen tussen code en blokjes. Code met een fout kan nog niet naar blokjes. */
    private void switchMode() {
        if (mode == Mode.CODE) {
            Script script = check(true);
            if (script == null) {
                show("Fix the error first: " + status, HudStyle.RED);
                return;
            }
            // Is de code niet veranderd sinds de vorige keer blokjes, dan blijven die blokjes zoals ze waren.
            if (blockGraph == null || !editor.text().equals(codeAtSwitch)) {
                blockGraph = GraphConverter.toGraph(script, blockGraph != null ? blockGraph : baseGraph);
                if (!editor.text().equals(codeAtSwitch)) {
                    blocksEdited = true;
                }
            }
            codeAtSwitch = editor.text();
            canvas.setGraph(blockGraph);
            mode = Mode.BLOCKS;
            checkBlocks(false);
        } else {
            if (blocksEdited && blockGraph != null) {
                String code = ScriptPrinter.print(GraphConverter.toScript(blockGraph));
                if (!code.equals(editor.text())) {
                    editor.setText(code);
                }
            }
            codeAtSwitch = editor.text();
            canvas.blur();
            mode = Mode.CODE;
            check(false);
            int loose = blockGraph == null ? 0 : blockGraph.size() - blockGraph.reachable().size();
            if (loose > 0) {
                show(loose + (loose == 1 ? " loose block is" : " loose blocks are") + " not in the code", HudStyle.ORANGE);
            }
        }
    }

    /** Check in de blokjes: het eerste blokje met een fout wordt gekozen (rood). */
    private boolean checkBlocks(boolean select) {
        if (blockGraph == null) {
            return false;
        }
        var problems = blockGraph.problems(kind);
        if (!problems.isEmpty()) {
            if (select) {
                canvas.select(problems.getFirst().node());
            }
            show(problems.getFirst().message(), HudStyle.RED);
            return false;
        }
        int loose = blockGraph.size() - blockGraph.reachable().size();
        int handlers = (int) blockGraph.nodes().stream().filter(node -> node.op.isTrigger()).count();
        if (handlers == 0) {
            show(blockGraph.size() == 0 ? "Start with a WHEN block (on the right)"
                    : "Add a WHEN block: a DEF does nothing until a when uses it", HudStyle.ORANGE);
        } else if (loose > 0) {
            show("No problems, but " + loose + (loose == 1 ? " block is" : " blocks are")
                    + " loose (not connected to a when or def)", HudStyle.ORANGE);
        } else {
            show("No problems: " + blockGraph.size() + (blockGraph.size() == 1 ? " block" : " blocks"), HudStyle.GREEN);
        }
        return true;
    }

    // ================================================================ Knoppen

    /** Controleert de code. {@code jump}: spring naar de regel met de fout. Geeft het Script, of null bij een fout. */
    private @Nullable Script check(boolean jump) {
        try {
            Script script = ScriptParser.parse(editor.text(), kind);
            editor.setErrorLine(0);
            int blocks = script.blocks();
            if (script.items().isEmpty()) {
                show("Empty: start with a line like when keycard(\"Reader 1\"):", HudStyle.ORANGE);
            } else if (script.handlers().isEmpty()) {
                show("No problems, but no when uses your defs yet (a def does nothing on its own)", HudStyle.ORANGE);
            } else {
                show("No problems: " + blocks + (blocks == 1 ? " block" : " blocks"), HudStyle.GREEN);
            }
            return script;
        } catch (LogicException e) {
            editor.setErrorLine(e.line());
            show(e.describe(), HudStyle.RED);
            if (jump && e.line() > 0) {
                editor.goToLine(e.line());
            }
            return null;
        }
    }

    /** Bewaren: de code wordt blokjes, en die gaan naar de server. */
    void save() {
        if (!open) {
            return;
        }
        LogicGraph graph;
        String source;
        if (mode == Mode.BLOCKS) {
            if (!checkBlocks(true) || blockGraph == null) {
                return;
            }
            graph = blockGraph.copy();
            source = ScriptPrinter.print(GraphConverter.toScript(graph));
        } else {
            Script script = check(true);
            if (script == null) {
                return;
            }
            // Code niet veranderd sinds de blokjes? Dan de blokjes zelf (met hun plek, ook losse).
            graph = blockGraph != null && editor.text().equals(codeAtSwitch) && blocksEdited
                    ? blockGraph.copy() : GraphConverter.toGraph(script, blockGraph != null ? blockGraph : baseGraph);
            source = editor.text();
        }
        String name = nameBox().getValue().strip();
        if (name.isEmpty()) {
            show("Give the diagram a name first", HudStyle.RED);
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putString("old", current == null ? "" : current);
        args.putString("name", name);
        args.put("graph", graph.save());
        args.putString("source", source);
        args.putString("kind", kind.name());
        screen.send("logic_save", args);
        pendingSelect = name;
    }

    /** Bewaren bij het sluiten van de desktop, maar alleen als de code klopt. */
    void saveIfValid() {
        if (!dirty()) {
            return;
        }
        if (mode == Mode.BLOCKS) {
            if (blockGraph != null && blockGraph.problems(kind).isEmpty()) {
                save();
            }
            return;
        }
        try {
            ScriptParser.parse(editor.text(), kind);
            save();
        } catch (LogicException e) {
            // Kapotte code bewaren we niet vanzelf.
        }
    }

    /**
     * Een nieuw programma van deze soort: een diagram op de server, of (hoofdstuk 23) een robotprogramma. Het
     * krijgt een vrije naam ("Diagram 2", "Robot 1") en een begin dat al klopt.
     */
    private void newDiagram(ProgramKind newKind) {
        choosingKind = false;
        if (!confirmed("new", "Unsaved changes: click New again to throw them away")) {
            return;
        }
        String base = newKind == ProgramKind.ROBOT ? "Robot " : "Diagram ";
        int number = 1;
        while (find(base + number) != null) {
            number++;
        }
        current = null;
        open = true;
        baseGraph = null;
        setKind(newKind);
        nameBox().setValue(base + number);
        loadedName = "";
        String reader = firstInput(false);
        String code = newKind == ProgramKind.ROBOT
                ? "# A robot program: it runs on the robot itself. Press Help to see what a robot can do.\nwhen every(10):\n    pass\n"
                : reader != null
                ? "when keycard(\"" + reader + "\"):\n    if card.valid and card.level >= 1:\n        accept()\n    else:\n        deny()\n"
                : "# Start with a when. Press Help to see everything you can use.\nwhen every(10):\n    pass\n";
        editor.setText(code);
        loadedCode = "";
        codeAtSwitch = code;
        blockGraph = GraphConverter.toGraph(ScriptParser.parse(code, newKind), null);
        blocksEdited = false;
        if (mode == Mode.BLOCKS) {
            canvas.setGraph(blockGraph);
            checkBlocks(false);
        } else {
            editor.setFocused(true);
            check(false);
        }
    }

    private void toggleOn() {
        Entry entry = current == null ? null : find(current);
        if (entry == null) {
            show("Save the diagram first", HudStyle.ORANGE);
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putString("name", entry.name());
        args.putBoolean("on", !entry.on());
        screen.send("logic_enable", args);
    }

    private void delete() {
        if (current == null) {
            open = false; // nooit bewaard: gewoon weg
            if (!entries.isEmpty()) {
                openEntry(entries.getFirst(), false);
            }
            return;
        }
        if (!confirmed("delete", "Click Delete again to delete " + current)) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putString("name", current);
        screen.send("logic_delete", args);
        open = false;
        current = null;
    }

    /** Bij iets dat je niet ongedaan kunt maken: de eerste klik vraagt het, de tweede doet het. */
    private boolean confirmed(String what, String question) {
        boolean needed = what.equals("delete") || dirty();
        if (!needed || what.equals(confirm) && Util.getMillis() < confirmUntil) {
            confirm = null;
            return true;
        }
        confirm = what;
        confirmUntil = Util.getMillis() + 4000;
        show(question, HudStyle.ORANGE);
        return false;
    }

    private void show(String text, int colour) {
        status = text;
        statusColour = colour;
    }

    /** Wat Ctrl+Spatie aan namen kan voorstellen: je apparaten, je lezers en je panelen (en voor welke soort programma). */
    private CodeCompletion.Data completionData() {
        List<String> outputs = new ArrayList<>();
        for (Tag tag : info().getList("outputs", Tag.TAG_STRING)) {
            outputs.add(tag.getAsString());
        }
        List<String> readers = new ArrayList<>();
        List<String> panels = new ArrayList<>();
        for (Tag element : info().getList("inputs", Tag.TAG_COMPOUND)) {
            CompoundTag input = (CompoundTag) element;
            (input.getBoolean("p") ? panels : readers).add(input.getString("n"));
        }
        return new CodeCompletion.Data(outputs, readers, panels, kind);
    }

    private @Nullable String firstInput(boolean panel) {
        for (Tag element : info().getList("inputs", Tag.TAG_COMPOUND)) {
            CompoundTag input = (CompoundTag) element;
            if (input.getBoolean("p") == panel) {
                return input.getString("n");
            }
        }
        return null;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my, float partialTick) {
        renderSidebar(g, font, a, mx, my);
        int x0 = a.x() + SIDE + 6;
        int width = a.x() + a.w() - x0;
        if (!open) {
            g.drawString(font, "No diagram open.", x0, a.y() + 4, HudStyle.argb(255, HudStyle.WHITE), false);
            g.drawString(font, "Press + to make one.", x0, a.y() + 16, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        // De knoppenbalk.
        int bx = x0;
        g.drawString(font, "Name", bx, a.y() + 2, HudStyle.argb(255, HudStyle.WHITE), false);
        bx += font.width("Name") + 4;
        EditBox box = nameBox();
        int nameW = nameWidth(font, a);
        OsDraw.field(g, bx, a.y(), bx + nameW, a.y() + OsDraw.BUTTON_H, box.isFocused());
        box.setX(bx + 3);
        box.setY(a.y() + 2);
        box.setWidth(nameW - 6);
        box.render(g, mx, my, partialTick);
        bx += nameW + 4;
        for (String label : toolbar()) {
            boolean enabled = !label.equals("Save") || dirty();
            int accent = label.equals("Delete") ? HudStyle.RED : label.equals("Off") ? HudStyle.ORANGE
                    : label.equals("On") ? HudStyle.GREEN : label.equals("Blocks") || label.equals("Code") ? 0xC09CFF : HudStyle.CYAN;
            bx += OsDraw.button(g, font, bx, a.y(), label, enabled, accent) + 3;
        }
        int top = a.y() + TOOLBAR;
        hoverHint = "";
        if (mode == Mode.BLOCKS) {
            canvas.render(g, font, x0, top, width, a.h() - TOOLBAR - STATUS, mx, my, partialTick);
        } else {
            int helpX = a.x() + a.w() - OsDraw.buttonWidth(font, "Help");
            if (helpOpen) {
                g.fill(helpX, a.y(), helpX + OsDraw.buttonWidth(font, "Help"), a.y() + OsDraw.BUTTON_H, HudStyle.argb(255, 0x4A3416));
            }
            OsDraw.button(g, font, helpX, a.y(), "Help", true, HudStyle.ORANGE);
            // De editor.
            int editorW = helpOpen ? width - HELP_W - 4 : width;
            editor.setBounds(x0, top, editorW, a.h() - TOOLBAR - STATUS);
            editor.render(g, mx, my);
            if (helpOpen) {
                renderHelp(g, font, x0 + editorW + 4, top, HELP_W, a.h() - TOOLBAR - STATUS, mx, my, partialTick);
            }
        }
        // De statusregel (in de blokjes: wat er onder de muis is, als er iets is).
        String text = !hoverHint.isEmpty() ? hoverHint : (dirty() ? "* " : "") + status;
        int colour = !hoverHint.isEmpty() ? 0xB8C8D0 : statusColour;
        g.drawString(font, OsDraw.fitLeft(font, text, width), x0, a.y() + a.h() - 9, HudStyle.argb(255, colour), false);
    }

    /** Hoe breed het naamvak is: 84 pixels, of smaller als de knoppen (en Help) anders niet passen. */
    private int nameWidth(Font font, StarkOsScreen.Area a) {
        int x0 = a.x() + SIDE + 6;
        int room = a.x() + a.w() - x0 - font.width("Name") - 8;
        if (mode == Mode.CODE) {
            room -= OsDraw.buttonWidth(font, "Help") + 4;
        }
        for (String label : toolbar()) {
            room -= OsDraw.buttonWidth(font, label) + 3;
        }
        return Math.max(40, Math.min(84, room));
    }

    /**
     * De knoppen na het naamvak. "On" of "Off" zegt wat er gebeurt als je klikt (en staat er pas als
     * het diagram bewaard is).
     */
    private List<String> toolbar() {
        Entry entry = current == null ? null : find(current);
        String view = mode == Mode.CODE ? "Blocks" : "Code";
        if (entry == null || entry.kind() != ProgramKind.SERVER) {
            // Een robotprogramma zet je niet aan of uit op de server: de robot draait het (app Robots).
            return List.of(view, "Check", "Save", "Delete");
        }
        return List.of(view, "Check", "Save", entry.on() ? "Off" : "On", "Delete");
    }

    private void renderSidebar(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my) {
        int x = a.x();
        g.drawString(font, "DIAGRAMS", x, a.y() + 2, HudStyle.argb(255, HudStyle.CYAN), false);
        OsDraw.button(g, font, x + SIDE - OsDraw.buttonWidth(font, "+"), a.y(), "+", true, HudStyle.GREEN);
        int top = a.y() + TOOLBAR;
        int bottom = a.y() + a.h() - 34;
        OsDraw.field(g, x, top, x + SIDE, bottom, false);
        if (choosingKind) {
            // Na +: wat voor programma? (Hoofdstuk 23.)
            g.drawString(font, "New:", x + 4, top + 4, HudStyle.argb(255, HudStyle.WHITE), false);
            OsDraw.button(g, font, x + 4, top + 15, "Server diagram", true, HudStyle.CYAN);
            OsDraw.button(g, font, x + 4, top + 15 + OsDraw.BUTTON_H + 3, "Robot program", true, 0xF1E87C);
            OsDraw.button(g, font, x + 4, top + 15 + 2 * (OsDraw.BUTTON_H + 3), "Cancel", true, OsDraw.GREY);
            return;
        }
        int y = top + 3;
        List<Entry> shown = new ArrayList<>(entries);
        sideScroll = Math.max(0, Math.min(sideScroll, Math.max(0, shown.size() - 1)));
        for (int i = sideScroll; i < shown.size() && y + ROW <= bottom; i++) {
            Entry entry = shown.get(i);
            boolean selected = open && current != null && current.equalsIgnoreCase(entry.name());
            if (selected) {
                g.fill(x + 2, y - 2, x + SIDE - 2, y + ROW - 3, HudStyle.argb(255, 0x16303F));
            }
            g.fill(x + 4, y + 2, x + 7, y + 5, HudStyle.argb(255, entry.on() ? HudStyle.GREEN : 0x4A5A62));
            String name = (selected && dirty() ? "*" : "") + entry.name();
            boolean robot = entry.kind() == ProgramKind.ROBOT;
            g.drawString(font, OsDraw.fitLeft(font, name, SIDE - (robot ? 24 : 14)), x + 10, y,
                    HudStyle.argb(255, entry.on() || robot ? HudStyle.WHITE : OsDraw.GREY), false);
            if (robot) {
                // Een robotprogramma: een geel R'tje rechts (het draait op een robot, niet op de server).
                g.drawString(font, "R", x + SIDE - 10, y, HudStyle.argb(255, 0xF1E87C), false);
            }
            y += ROW;
        }
        if (open && current == null && y + ROW <= bottom) {
            g.fill(x + 2, y - 2, x + SIDE - 2, y + ROW - 3, HudStyle.argb(255, 0x16303F));
            g.drawString(font, OsDraw.fitLeft(font, "*" + nameBox().getValue(), SIDE - 14), x + 10, y,
                    HudStyle.argb(255, HudStyle.ORANGE), false);
        }
        if (entries.isEmpty() && !open) {
            g.drawString(font, "(none yet)", x + 5, top + 3, HudStyle.argb(255, OsDraw.GREY), false);
        }
        // Onderaan: hoe vol de SSD's zijn, en wat er bezig is.
        CompoundTag info = info();
        CompoundTag server = server();
        int used = info.getInt("used");
        int cap = server.getInt("cap");
        int sy = bottom + 3;
        g.drawString(font, "SSD " + used + "/" + cap, x, sy, HudStyle.argb(255, used > cap ? HudStyle.RED : HudStyle.WHITE), false);
        int barW = SIDE;
        g.fill(x, sy + 10, x + barW, sy + 12, HudStyle.argb(255, 0x1A2A33));
        if (cap > 0) {
            g.fill(x, sy + 10, x + Math.min(barW, barW * used / cap), sy + 12,
                    HudStyle.argb(255, used > cap ? HudStyle.RED : HudStyle.CYAN));
        }
        String state = !server.getBoolean("online") ? "Server offline"
                : "Running " + info.getInt("running") + (info.getInt("queued") > 0 ? ", waiting " + info.getInt("queued") : "");
        g.drawString(font, OsDraw.fitLeft(font, state, SIDE), x, sy + 16,
                HudStyle.argb(255, server.getBoolean("online") ? OsDraw.GREY : HudStyle.RED), false);
    }

    // ================================================================ Help

    /** Eén regel in het Help-paneel: een kopje, of iets dat je kunt invoegen (met uitleg). */
    private record HelpLine(String label, @Nullable String insert, String tip, int colour, @Nullable CompoundTag input) {}

    private List<HelpLine> helpLines() {
        List<HelpLine> lines = new ArrayList<>();
        String reader = firstInput(false);
        String panel = firstInput(true);
        List<String> outputs = new ArrayList<>();
        for (Tag tag : info().getList("outputs", Tag.TAG_STRING)) {
            outputs.add(tag.getAsString());
        }
        String device = outputs.isEmpty() ? "Lamp" : outputs.getFirst();
        boolean server = kind == ProgramKind.SERVER;
        lines.add(header("START WITH (when)"));
        if (server) {
        lines.add(item("keycard(...)", "when keycard(\"" + (reader == null ? "Reader 1" : reader) + "\"):\n    if card.valid:\n        accept()\n    else:\n        deny()\n", Op.KEYCARD));
        lines.add(item("codepanel(...)", "when codepanel(\"" + (panel == null ? "Panel 1" : panel) + "\"):\n    if code == \"1234\":\n        accept()\n    else:\n        deny()\n", Op.CODEPANEL));
        lines.add(item("turned_on(...)", "when turned_on(\"" + device + "\"):\n    pass\n", Op.TURNED_ON));
        lines.add(item("turned_off(...)", "when turned_off(\"" + device + "\"):\n    pass\n", Op.TURNED_OFF));
        }
        lines.add(item("every(10)", "when every(10):\n    pass\n", Op.EVERY));
        lines.add(header("DO (actions)"));
        lines.add(item("on / off / toggle", "on(\"" + device + "\")", Op.ON));
        lines.add(item("pulse(..., 3)", "pulse(\"" + device + "\", 3)", Op.PULSE));
        lines.add(item("wait(2)", "wait(2)", Op.WAIT));
        if (server) {
            lines.add(item("accept() / deny()", "accept()", Op.ACCEPT));
        }
        lines.add(item("say(...)", "say(\"Someone is at the door\")", Op.SAY));
        lines.add(item("alarm(...)", "alarm(\"Intruder!\")", Op.ALARM));
        lines.add(item("log(...)", "log(\"Door opened\")", Op.LOG));
        lines.add(header("CHECK (if)"));
        if (server) {
        lines.add(new HelpLine("if card.level >= 3:", "if card.valid and card.level >= 3:\n    pass", "card.level: 1-5; card.valid: written by this network and not blocked", HudStyle.WHITE, null));
        lines.add(new HelpLine("if code == \"1234\":", "if code == \"1234\":\n    pass", "the code typed on a code panel (between quotes)", HudStyle.WHITE, null));
        lines.add(new HelpLine("if player.trusted:", "if player.trusted:\n    pass", "the owner, their team, and trusted players (Access)", HudStyle.WHITE, null));
        }
        lines.add(new HelpLine("if is_on(...):", "if is_on(\"" + device + "\"):\n    pass", "is that device switched on right now?", HudStyle.WHITE, null));
        lines.add(new HelpLine("if is_night:", "if is_night:\n    pass", server ? "also: is_day, player.owner, player.name, card.number, card.name"
                : "also: is_day", HudStyle.WHITE, null));
        lines.add(new HelpLine(server ? "if card.level * 2 - 1 >= 5:" : "if count * 2 - 1 >= 5:",
                server ? "if card.level * 2 - 1 >= 5:\n    pass" : "if count * 2 - 1 >= 5:\n    pass",
                "calculate with + - * / and brackets (* and / go first); + also glues text: \"Level \" + card.level",
                HudStyle.WHITE, null));
        lines.add(new HelpLine("else: / elif ...:", "else:\n    pass", "what happens when the if is not true", HudStyle.WHITE, null));
        lines.add(header("NAMES (variables)"));
        lines.add(new HelpLine("count = 0", "count = 0\n",
                "At the left edge: a name for the whole program. It keeps its value between runs.", 0xF1E87C, null));
        lines.add(new HelpLine("count = count + 1", "count = count + 1",
                "Inside a when or def: give a name a (new) value. A def has its own names.", 0xF1E87C, null));
        lines.add(new HelpLine("if count >= 3:", "if count >= 3:\n    pass",
                "Use your name like card.level. A number stays a number, text stays text.", HudStyle.WHITE, null));
        lines.add(new HelpLine("say(\"Count: \" + count)", "say(\"Count: \" + count)",
                "A message (say, log, alarm) or the seconds (wait, pulse) may be calculated: wait(delay * 2)",
                0x9ED8FF, null));
        lines.add(header("LOOPS (again and again)"));
        lines.add(new HelpLine("repeat(3):", "repeat(3):\n    pulse(\"" + device + "\", 0.5)\n    wait(1)",
                "Do the steps inside 3 times. The number may be a name or a sum: repeat(count + 1):", 0xFF79C6, null));
        lines.add(new HelpLine("while count < 3:", "while count < 3:\n    count = count + 1",
                "As long as the check is true. Change something inside, or it never stops.", 0xFF79C6, null));
        lines.add(new HelpLine("forever:", "forever:\n    wait(1)",
                "Again and again. Put a wait inside: a loop without waiting keeps your CPU busy.", 0xFF79C6, null));
        lines.add(new HelpLine("break", "break", Op.BREAK.help(), 0xFF79C6, null));
        lines.add(header("YOUR OWN BLOCKS (def)"));
        lines.add(new HelpLine("def open_door():", "def open_door():\n    accept()\n    pulse(\"" + device + "\", 3)\n",
                "Write steps once (at the left edge), then use them in many whens with open_door()", 0xC9A0FF, null));
        java.util.regex.Matcher defs = java.util.regex.Pattern.compile("(?m)^def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
                .matcher(editor.text());
        while (defs.find()) {
            lines.add(new HelpLine(defs.group(1) + "()", defs.group(1) + "()", "one of your own blocks: does all its steps here",
                    0xC9A0FF, null));
        }
        lines.add(header("YOUR DEVICES"));
        if (outputs.isEmpty()) {
            lines.add(new HelpLine("(none: name relays with your glasses)", null,
                    "Place a relay, laser or turret here and give it a name with E.D.I.T.H.", OsDraw.GREY, null));
        }
        for (String name : outputs) {
            lines.add(new HelpLine(name, quoted(name), "a relay, laser, dome, turret or cabinet switch", 0x9ED8FF, null));
        }
        if (!server) {
            return lines; // lezers en panelen horen bij de server
        }
        lines.add(header("READERS AND PANELS"));
        for (Tag element : info().getList("inputs", Tag.TAG_COMPOUND)) {
            CompoundTag input = (CompoundTag) element;
            lines.add(new HelpLine((input.getBoolean("p") ? "[panel] " : "[reader] ") + input.getString("n"),
                    quoted(input.getString("n")), "click R to rename it", HudStyle.CYAN, input));
        }
        if (info().getList("inputs", Tag.TAG_COMPOUND).isEmpty()) {
            lines.add(new HelpLine("(none in this network yet)", null, "Place a Keycard Reader or Code Panel near the core", OsDraw.GREY, null));
        }
        return lines;
    }

    private static String quoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static HelpLine header(String text) {
        return new HelpLine(text, null, "", HudStyle.ORANGE, null);
    }

    private static HelpLine item(String label, String insert, Op op) {
        return new HelpLine(label, insert, op.help(), op.isTrigger() ? HudStyle.CYAN : 0x9ED8FF, null);
    }

    private void renderHelp(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my, float partialTick) {
        OsDraw.field(g, x, y, x + w, y + h, false);
        List<HelpLine> lines = helpLines();
        int tipH = 20;
        int listBottom = y + h - tipH - (renaming != null ? 14 : 0);
        helpScroll = Math.max(0, Math.min(helpScroll, Math.max(0, lines.size() - 3)));
        String tip = "Click a line to put it in your code. Or press Ctrl+Space in the code.";
        int ly = y + 3;
        g.enableScissor(x + 1, y + 1, x + w - 1, listBottom);
        for (int i = helpScroll; i < lines.size() && ly < listBottom; i++) {
            HelpLine line = lines.get(i);
            boolean hover = line.insert() != null && mx >= x && mx < x + w && my >= ly - 1 && my < ly + 9;
            if (hover) {
                g.fill(x + 1, ly - 1, x + w - 1, ly + 9, HudStyle.argb(255, 0x16303F));
                tip = line.tip();
            }
            g.drawString(font, OsDraw.fitLeft(font, line.label(), w - (line.input() != null ? 16 : 6)), x + 4, ly,
                    HudStyle.argb(255, line.colour()), false);
            if (line.input() != null) {
                g.drawString(font, "R", x + w - 9, ly, HudStyle.argb(255, HudStyle.ORANGE), false);
            }
            ly += 10;
        }
        g.disableScissor();
        // Onderaan: de uitleg van wat onder de muis is (over twee regels).
        List<net.minecraft.util.FormattedCharSequence> tipLines = font.split(Component.literal(tip), w - 6);
        for (int i = 0; i < Math.min(2, tipLines.size()); i++) {
            g.drawString(font, tipLines.get(i), x + 3, y + h - tipH + 1 + i * 9, HudStyle.argb(255, OsDraw.GREY), false);
        }
        if (renaming != null) {
            int ry = y + h - tipH - 13;
            EditBox box = renameBox();
            OsDraw.field(g, x + 2, ry, x + w - 30, ry + OsDraw.BUTTON_H, true);
            box.setX(x + 5);
            box.setY(ry + 2);
            box.setWidth(w - 38);
            box.render(g, mx, my, partialTick);
            OsDraw.button(g, font, x + w - 26, ry, "OK", true, HudStyle.GREEN);
        }
    }

    // ================================================================ Muis

    void mouseClicked(Font font, StarkOsScreen.Area a, int mx, int my, int button) {
        // De zijbalk: + en de diagrammen.
        if (OsDraw.inside(mx, my, a.x() + SIDE - OsDraw.buttonWidth(font, "+"), a.y(), OsDraw.buttonWidth(font, "+"), OsDraw.BUTTON_H)) {
            choosingKind = !choosingKind;
            return;
        }
        int top = a.y() + TOOLBAR;
        if (choosingKind) {
            int bx = a.x() + 4;
            if (OsDraw.inside(mx, my, bx, top + 15, OsDraw.buttonWidth(font, "Server diagram"), OsDraw.BUTTON_H)) {
                newDiagram(ProgramKind.SERVER);
            } else if (OsDraw.inside(mx, my, bx, top + 15 + OsDraw.BUTTON_H + 3, OsDraw.buttonWidth(font, "Robot program"), OsDraw.BUTTON_H)) {
                newDiagram(ProgramKind.ROBOT);
            } else if (OsDraw.inside(mx, my, bx, top + 15 + 2 * (OsDraw.BUTTON_H + 3), OsDraw.buttonWidth(font, "Cancel"), OsDraw.BUTTON_H)) {
                choosingKind = false;
            }
            if (mx >= a.x() && mx < a.x() + SIDE) {
                return;
            }
        }
        if (mx >= a.x() && mx < a.x() + SIDE && my >= top) {
            int index = sideScroll + (my - top - 1) / ROW;
            if (index >= 0 && index < entries.size()) {
                Entry entry = entries.get(index);
                if (current == null || !current.equalsIgnoreCase(entry.name())) {
                    if (confirmed("switch:" + entry.name(), "Unsaved changes: click " + entry.name() + " again to throw them away")) {
                        openEntry(entry, false);
                    }
                }
            }
            return;
        }
        if (!open) {
            return;
        }
        // De knoppenbalk.
        int x0 = a.x() + SIDE + 6;
        int bx = x0 + font.width("Name") + 4;
        EditBox box = nameBox();
        int nameW = nameWidth(font, a);
        boolean inName = OsDraw.inside(mx, my, bx, a.y(), nameW, OsDraw.BUTTON_H);
        box.setFocused(inName);
        if (inName) {
            editor.setFocused(false);
            box.mouseClicked(mx, my, 0);
            return;
        }
        bx += nameW + 4;
        for (String label : toolbar()) {
            int w = OsDraw.buttonWidth(font, label);
            if (OsDraw.inside(mx, my, bx, a.y(), w, OsDraw.BUTTON_H)) {
                switch (label) {
                    case "Blocks", "Code" -> switchMode();
                    case "Check" -> {
                        if (mode == Mode.BLOCKS) {
                            checkBlocks(true);
                        } else {
                            check(true);
                        }
                    }
                    case "Save" -> save();
                    case "On", "Off" -> toggleOn();
                    case "Delete" -> delete();
                    default -> { }
                }
                return;
            }
            bx += w + 3;
        }
        if (mode == Mode.BLOCKS) {
            canvas.mouseClicked(font, mx, my, button);
            return;
        }
        int helpW = OsDraw.buttonWidth(font, "Help");
        if (OsDraw.inside(mx, my, a.x() + a.w() - helpW, a.y(), helpW, OsDraw.BUTTON_H)) {
            helpOpen = !helpOpen;
            renaming = null;
            return;
        }
        // Het Help-paneel.
        int width = a.x() + a.w() - x0;
        if (helpOpen && mx >= x0 + width - HELP_W) {
            clickHelp(font, x0 + width - HELP_W, a.y() + TOOLBAR, HELP_W, a.h() - TOOLBAR - STATUS, mx, my);
            return;
        }
        editor.mouseClicked(mx, my, button);
    }

    private void clickHelp(Font font, int x, int y, int w, int h, int mx, int my) {
        int tipH = 20;
        if (renaming != null) {
            int ry = y + h - tipH - 13;
            EditBox box = renameBox();
            if (OsDraw.inside(mx, my, x + w - 26, ry, 24, OsDraw.BUTTON_H)) {
                submitRename();
                return;
            }
            if (OsDraw.inside(mx, my, x + 2, ry, w - 32, OsDraw.BUTTON_H)) {
                box.setFocused(true);
                box.mouseClicked(mx, my, 0);
                return;
            }
        }
        List<HelpLine> lines = helpLines();
        int index = helpScroll + (my - y - 2) / 10;
        if (index < 0 || index >= lines.size()) {
            return;
        }
        HelpLine line = lines.get(index);
        if (line.input() != null && mx >= x + w - 12) {
            renaming = line.input();
            renameBox().setValue(line.input().getString("n"));
            renameBox().setFocused(true);
            editor.setFocused(false);
            return;
        }
        if (line.insert() != null) {
            editor.setFocused(true);
            editor.insert(line.insert());
        }
    }

    private void submitRename() {
        if (renaming == null) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putInt("x", renaming.getInt("x"));
        args.putInt("y", renaming.getInt("y"));
        args.putInt("z", renaming.getInt("z"));
        args.putString("name", renameBox().getValue());
        screen.send("input_rename", args);
        renaming = null;
    }

    boolean mouseDragged(int mx, int my) {
        return mode == Mode.BLOCKS ? canvas.mouseDragged(mx, my) : editor.mouseDragged(mx, my);
    }

    void mouseReleased(int mx, int my) {
        if (mode == Mode.BLOCKS) {
            canvas.mouseReleased(mx, my);
        } else {
            editor.mouseReleased();
        }
    }

    void mouseScrolled(StarkOsScreen.Area a, int mx, int my, double scrollY) {
        if (mx < a.x() + SIDE) {
            sideScroll = Math.max(0, sideScroll - (int) Math.signum(scrollY));
        } else if (mode == Mode.BLOCKS) {
            canvas.mouseScrolled(mx, my, scrollY, Screen.hasShiftDown(), Screen.hasControlDown());
        } else if (helpOpen && mx >= a.x() + a.w() - HELP_W) {
            helpScroll = Math.max(0, helpScroll - (int) Math.signum(scrollY) * 2);
        } else {
            editor.mouseScrolled(scrollY);
        }
    }

    // ================================================================ Toetsen

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown() && keyCode == 83) { // Ctrl+S
            save();
            return true;
        }
        if (renaming != null && renameBox().isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                submitRename();
            } else if (keyCode == 256) {
                renaming = null;
            } else {
                renameBox().keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        EditBox box = nameBox();
        if (box.isFocused()) {
            if (keyCode == 257 || keyCode == 335 || keyCode == 256) {
                box.setFocused(false);
            } else {
                box.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        if (mode == Mode.BLOCKS) {
            return canvas.keyPressed(keyCode, scanCode, modifiers);
        }
        boolean handled = editor.keyPressed(keyCode, scanCode, modifiers);
        if (handled && editor.focused()) {
            editor.setErrorLine(0); // je bent aan het veranderen: de rode regel weg tot de volgende Check
        }
        return handled;
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (renaming != null && renameBox().isFocused()) {
            renameBox().charTyped(codePoint, modifiers);
            return true;
        }
        if (nameBox().isFocused()) {
            nameBox().charTyped(codePoint, modifiers);
            return true;
        }
        return mode == Mode.BLOCKS ? canvas.charTyped(codePoint, modifiers) : editor.charTyped(codePoint, modifiers);
    }

    void blur() {
        editor.setFocused(false);
        canvas.blur();
        nameBox().setFocused(false);
        if (renameBox != null) {
            renameBox.setFocused(false);
        }
    }

    // ================================================================ Voor de screenshot-rondleiding

    /** Naar de blokjes (of terug naar de code), en eventueel een blokje kiezen (de eerste van deze soort). */
    void tourBlocks(@Nullable Op select) {
        if (mode == Mode.CODE) {
            switchMode();
        }
        if (select != null && blockGraph != null) {
            blockGraph.nodes().stream().filter(node -> node.op == select).findFirst()
                    .ifPresent(node -> canvas.select(node.id));
        }
    }

    /**
     * Ctrl+Spatie op de regel met deze tekst, met de cursor vlak na {@code after} (of aan het einde van
     * de regel als hij {@code after} niet vindt).
     */
    void tourComplete(String lineText, String after) {
        if (mode == Mode.BLOCKS) {
            switchMode(); // Ctrl+Spatie hoort bij de code
        }
        editor.setFocused(true);
        editor.tourCursor(lineText, after);
        editor.openCompletion();
    }

    /** De knop Save, zoals jij hem indrukt. */
    void tourSave() {
        save();
    }

    /** Alleen voor de screenshot-rondleiding: na + de keuze tussen een diagram op de server en een robotprogramma. */
    void tourChooseKind() {
        choosingKind = true;
    }

    /** Alleen voor de screenshot-rondleiding: een nieuw programma van deze soort, met Help open. */
    void tourNew(ProgramKind newKind) {
        open = false; // geen "wijzigingen weggooien?"
        newDiagram(newKind);
        if (mode == Mode.BLOCKS) {
            switchMode();
        }
        helpOpen = true;
    }

    /** Een nieuw diagram met deze code en naam, en meteen Check. */
    void tourCode(String name, String code, boolean help) {
        current = null;
        open = true;
        baseGraph = null;
        nameBox().setValue(name);
        editor.setText(code);
        loadedCode = "";
        helpOpen = help;
        check(true);
    }
}
