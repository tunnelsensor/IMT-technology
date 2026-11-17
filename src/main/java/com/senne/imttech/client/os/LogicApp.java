package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.logic.program.Expr;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.LogicGraph;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.Op;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.Script;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.logic.program.ScriptPrinter;
import com.senne.imttech.logic.program.ScriptTemplates;
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
 * De app Logic op de Security OS-desktop (jouw wens, 7 oktober): hier programmeer je de redstone van
 * je basis. Links je diagrammen (en hoe vol je SSD's zijn), rechts de code van het diagram dat open
 * is.
 * <pre>
 *  DIAGRAMS  [+]   | Name [Lab______] [Check] [Save] [On] [Delete]      [Help]
 *  * Lab           |  1 when keycard("Lab door"):
 *    Lights        |  2     if card.in_group("lab"):
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
 * <p>
 * Een nieuw programma begint leeg (hoofdstuk 125, jouw wens: "zorg dat al de standaard scripts die in een script komen
 * als je een nieuw script maakt weg zijn"). Code die je vaak gebruikt, bewaar je met <b>Templates</b> (links onder je
 * diagrammen) op je pc, en in een leeg programma klik je hem aan om hem in te laden (zie ScriptTemplates).
 * <p>
 * Hoofdstuk 130 (de SD-kaart, jouw keuze: "in Logic en Files zelf"): zit er een SD-kaart in de kern of computer, dan staat
 * onder je diagrammen een lijstje <b>SD</b> met wat er op de kaart staat. Klik op de kop: een pagina om programma's van je
 * server op de kaart te zetten, open of <b>op slot</b>. Klik op een programma van de kaart: installeren op je server, of
 * weggooien (een open programma kun je eerst lezen). Een programma op slot op je server opent niet in de editor: je ziet
 * alleen dat het op slot is, en je kunt het aan- en uitzetten en weggooien (jouw wens: "enkel gebruiken").
 */
final class LogicApp {
    private static final int SIDE = 92;
    private static final int ROW = 12;
    private static final int TOOLBAR = OsDraw.BUTTON_H + 4;
    private static final int STATUS = 11;
    private static final int HELP_W = 152;

    /** Eén diagram zoals de server het stuurde. */
    private record Entry(String name, boolean on, LogicGraph graph, String source, ProgramKind kind, boolean locked,
                         String author, int blocks) {}

    /** Hoofdstuk 130: een programma op de SD-kaart (naam en zo; de code komt alleen als je hem wilt lezen, en nooit op slot). */
    private record CardProgram(String name, ProgramKind kind, boolean locked, String author, int blocks, boolean global) {}

    /** Hoofdstuk 130: wat er rechts van de lijst staat: de editor, de pagina van de SD-kaart, of een programma op de kaart. */
    private enum Page { EDITOR, CARD, CARD_PROGRAM }

    /** Eén regel in de lijst links: een diagram, het nieuwe (nog niet bewaarde) programma, de kop SD, of een kaartprogramma. */
    private record SideRow(@Nullable Entry entry, @Nullable CardProgram card, boolean header, boolean fresh) {}

    /** Code of blokjes: twee manieren om hetzelfde diagram te bewerken. */
    private enum Mode { CODE, BLOCKS }

    private final SecurityOsScreen screen;
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

    /** Rechts het paneel Templates in plaats van Help (hoofdstuk 125)? Nooit allebei. */
    private boolean templatesOpen;
    private int templateScroll;
    /** In het paneel Templates: de naam waaronder je de code bewaart. */
    private @Nullable EditBox templateNameBox;
    /** Je templates (hoofdstuk 125): het bestand op je pc, gelezen zodra de app ze voor het eerst nodig heeft. */
    private @Nullable ScriptTemplates templates;

    /** Een template in de hint van een leeg programma: waar hij staat, zodat een klik hem laadt. */
    private record HintRow(ScriptTemplates.Template template, int x, int y, int w) {}

    /** De templates in de hint, zoals ze het laatst getekend werden (leeg als er geen hint staat). */
    private final List<HintRow> hintRows = new ArrayList<>();

    // ---------------------------------------------------------------- De SD-kaart (hoofdstuk 130)
    /** Zit er een kaart in (in de kern, of in de computer van deze desktop)? */
    private boolean cardIn;
    /** Zijn naam uit een aambeeld ("" = geen: dan heet hij "SD card"). */
    private String cardName = "";
    private int cardGb;
    private int cardMaxPrograms;
    private int cardMaxFiles;
    private int cardFiles;
    private final List<CardProgram> cardPrograms = new ArrayList<>();
    private Page page = Page.EDITOR;
    /** Het programma op de kaart dat je aanklikte (Page.CARD_PROGRAM). */
    private @Nullable String cardSelected;
    private int cardScroll;
    /** Welk programma op de kaart al één keer op Delete from card kreeg, en tot wanneer de tweede klik telt. */
    private @Nullable String cardConfirm;
    private long cardConfirmUntil;
    /** De code van een open programma op de kaart (alleen lezen), en van welk programma die is (null = nog niet gekregen). */
    private final CodeEditor cardPreview;
    private @Nullable String previewFor;

    LogicApp(SecurityOsScreen screen, Font font) {
        this.screen = screen;
        this.editor = new CodeEditor(font);
        // Ctrl+Spatie: de namen van je apparaten, lezers en panelen komen van de server.
        this.editor.setCompletionData(this::completionData);
        this.canvas = new BlockCanvas(this::info, () -> blocksEdited = true, text -> hoverHint = text);
        this.cardPreview = new CodeEditor(font);
        cardPreview.setReadOnly(true);
        cardPreview.setCompletionData(() -> new CodeCompletion.Data(List.of(), List.of(), List.of(), ProgramKind.SERVER));
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

    private EditBox templateNameBox() {
        if (templateNameBox == null) {
            templateNameBox = new EditBox(Minecraft.getInstance().font, 0, 0, 80, 10, Component.literal("Template name"));
            templateNameBox.setBordered(false);
            templateNameBox.setMaxLength(ScriptTemplates.MAX_NAME);
            templateNameBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
        }
        return templateNameBox;
    }

    /** Je templates (hoofdstuk 125): {@code imttech/script_templates.json} in je Minecraft-map, voor elke wereld en server. */
    private ScriptTemplates templates() {
        if (templates == null) {
            templates = new ScriptTemplates(Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("imttech").resolve("script_templates.json"));
        }
        return templates;
    }

    // ================================================================ Van de server

    /** Nieuwe diagrammen van de server (alleen als er iets veranderde). */
    void update(CompoundTag logic) {
        revision = logic.getInt("rev");
        entries.clear();
        for (Tag element : logic.getList("programs", Tag.TAG_COMPOUND)) {
            LogicProgram program = LogicProgram.load((CompoundTag) element);
            // Hoofdstuk 130: van een programma op slot krijgt je spel geen blokjes of code, alleen hoeveel blokjes het heeft.
            int blocks = program.locked() ? ((CompoundTag) element).getInt("Blocks") : program.graph().size();
            entries.add(new Entry(program.name(), program.enabled(), program.graph(), program.source(), program.kind(),
                    program.locked(), program.author(), blocks));
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

    // ================================================================ De SD-kaart (hoofdstuk 130)

    /** Wat er op de SD-kaart staat (de server stuurt het alleen als er iets veranderde, of als de kaart eruit ging). */
    void updateCard(CompoundTag sd) {
        cardIn = sd.getBoolean("in");
        cardName = sd.getString("name");
        cardGb = sd.getInt("gb");
        cardMaxPrograms = sd.getInt("maxP");
        cardMaxFiles = sd.getInt("maxF");
        cardPrograms.clear();
        for (Tag element : sd.getList("p", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            cardPrograms.add(new CardProgram(entry.getString("n"), ProgramKind.byName(entry.getString("k")), entry.getBoolean("l"),
                    entry.getString("a"), entry.getInt("b"), entry.getBoolean("g")));
        }
        cardFiles = (int) sd.getList("f", Tag.TAG_COMPOUND).stream().filter(tag -> !((CompoundTag) tag).getBoolean("d")).count();
        if (!cardIn) {
            page = Page.EDITOR;
            cardSelected = null;
        } else if (page == Page.CARD_PROGRAM && (cardSelected == null || cardProgram(cardSelected) == null)) {
            page = Page.CARD; // weggegooid van de kaart: terug naar de kaart
            cardSelected = null;
        }
    }

    /** De code van een open programma op de kaart, waar je om vroeg (sd_view). */
    void updateCardCode(CompoundTag code) {
        if (page == Page.CARD_PROGRAM && code.getString("n").equalsIgnoreCase(cardSelected == null ? "" : cardSelected)) {
            cardPreview.setText(code.getString("c"));
            previewFor = cardSelected;
        }
    }

    private @Nullable CardProgram cardProgram(String name) {
        for (CardProgram program : cardPrograms) {
            if (program.name().equalsIgnoreCase(name)) {
                return program;
            }
        }
        return null;
    }

    /** Hoe de kaart heet: zijn naam uit een aambeeld, anders "SD card". */
    private String cardTitle() {
        return cardName.isBlank() ? "SD card" : cardName;
    }

    /** Is het programma dat open staat een programma op slot (van een SD-kaart)? */
    private boolean currentLocked() {
        Entry entry = current == null ? null : find(current);
        return entry != null && entry.locked();
    }

    /**
     * Geen tekstvak meer actief (het naamvak, het naamvak van Templates, het hernoemvak van Help). Op de pagina's van de
     * kaart en bij een programma op slot staan die niet op het scherm: typen mag dan nergens heen gaan (gevonden door de
     * nalezing: anders hernoemde je ongezien het programma dat open stond, en bewaarde het sluiten van de desktop dat).
     */
    private void clearTextFocus() {
        renaming = null;
        nameBox().setFocused(false);
        if (renameBox != null) {
            renameBox.setFocused(false);
        }
        if (templateNameBox != null) {
            templateNameBox.setFocused(false);
        }
        editor.setFocused(false);
    }

    /** Naar de pagina van de kaart (de kop SD in de lijst). De editor blijft zoals hij was. */
    private void openCardPage() {
        page = Page.CARD;
        cardSelected = null;
        cardScroll = 0;
        choosingKind = false;
        clearTextFocus();
    }

    /** Een programma op de kaart aanklikken: zijn pagina, en (als het open is) vragen we de code om te lezen. */
    private void openCardProgram(CardProgram program) {
        page = Page.CARD_PROGRAM;
        cardSelected = program.name();
        choosingKind = false;
        clearTextFocus();
        previewFor = null;
        cardPreview.setText("");
        if (!program.locked()) {
            CompoundTag args = new CompoundTag();
            args.putString("name", program.name());
            screen.send("sd_view", args);
        }
    }

    /**
     * Een programma op slot opent niet in de editor (je spel heeft zijn code ook niet): de editor wordt leeg en
     * onaanraakbaar, en rechts staat dat het op slot is. Aanzetten, uitzetten en weggooien kan wel.
     */
    private void openLocked(Entry entry) {
        current = entry.name();
        open = true;
        setKind(entry.kind());
        baseGraph = entry.graph();
        blockGraph = entry.graph().copy();
        blocksEdited = false;
        editor.setText("");
        editor.setErrorLine(0);
        loadedCode = "";
        codeAtSwitch = "";
        loadedName = entry.name();
        nameBox().setValue(entry.name());
        clearTextFocus();
        helpOpen = false;
        templatesOpen = false;
        confirm = null;
        show("Locked: you can use it, not read it", OsDraw.GREY);
    }

    /** Opent dit diagram in de editor. {@code keepCursor}: is de code hetzelfde, laat de editor dan met rust. */
    private void openEntry(Entry entry, boolean keepCursor) {
        if (entry.locked()) {
            openLocked(entry); // hoofdstuk 130
            return;
        }
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
        if (templateNameBox != null && !keepCursor) {
            templateNameBox.setValue(entry.name()); // bewaar je dit als template, dan heet het standaard zoals het diagram
        }
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
        return codeFor(graph, source, kind, com.senne.imttech.logic.program.Library.EMPTY);
    }

    /**
     * {@code library}: de defs uit je global scripts (hoofdstuk 43). Zonder die las een programma dat er een gebruikt niet,
     * en dan zag je na het openen (en na Save) de code uit de blokjes: zonder je commentaar, en met $"..." waar jij
     * f"..." schreef. Sinds hoofdstuk 131 lezen we je code met dezelfde global scripts als Check.
     */
    static String codeFor(LogicGraph graph, String source, ProgramKind kind, com.senne.imttech.logic.program.Library library) {
        String printed = ScriptPrinter.print(GraphConverter.toScript(graph));
        if (!source.isEmpty()) {
            try {
                // Zonder de controle (hoofdstuk 115): een oud diagram met een fout van een latere regel (player.trusted na
                // een keycard) past nog bij zijn blokjes. Dan zie je je eigen code met je commentaar, en de fout eronder.
                // Met de uitgerekende when-namen vergeleken (hoofdstuk 135): een when met een f-tekst past zo ook bij
                // blokjes die alleen zijn uitgerekende naam kennen.
                if (ScriptPrinter.printWithNames(ScriptParser.parseUnchecked(source, kind, library))
                        .equals(ScriptPrinter.printWithNames(GraphConverter.toScript(graph)))) {
                    return source;
                }
            } catch (LogicException e) {
                // De bewaarde code klopt niet meer: dan de code uit de blokjes.
            }
        }
        return printed;
    }

    private String codeFor(Entry entry) {
        return codeFor(entry.graph(), entry.source(), entry.kind(), library()); // current en kind staan al op dit diagram
    }

    /** Een ander soort programma open: het palet en Ctrl+Spatie gaan mee. */
    private void setKind(ProgramKind kind) {
        this.kind = kind;
        canvas.setKind(kind);
    }

    /**
     * De defs en gedeelde namen van je global scripts (hoofdstuk 43), behalve van het diagram dat open staat: die
     * mag dit diagram gebruiken. De controle hier (Check) en op de server (Save) kijken er allebei mee.
     */
    private com.senne.imttech.logic.program.Library library() {
        if (kind == ProgramKind.CAR) {
            // Een RC program (hoofdstuk 119) draait op de auto, die alleen zijn eigen kopie heeft: geen global scripts.
            return com.senne.imttech.logic.program.Library.EMPTY;
        }
        List<LogicProgram> programs = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.graph().global()) {
                programs.add(new LogicProgram(entry.name(), entry.on(), entry.graph(), entry.source(), entry.kind()));
            }
        }
        return com.senne.imttech.logic.program.Library.of(programs, current);
    }

    /** Zijn er wijzigingen die nog niet bewaard zijn? */
    boolean dirty() {
        if (!open) {
            return false;
        }
        if (currentLocked()) {
            // Hoofdstuk 130: een programma op slot verander je niet (ook niet vanzelf bij het sluiten van de desktop).
            return false;
        }
        if (current == null) {
            // Een nieuw programma waar nog niets in staat (hoofdstuk 125: het begint leeg) is geen wijziging: weggaan gooit
            // niets weg, en bij het sluiten van de desktop bewaren we geen leeg programma op je server. We kijken naar wat
            // Save echt zou bewaren (de nalezing: een Enter en een wissel naar de blokjes telde al als wijziging): in de
            // blokjes de blokjes; in de code de code, of losse blokjes die van de blokjes overbleven.
            if (mode == Mode.BLOCKS) {
                return blockGraph != null && blockGraph.size() > 0;
            }
            return !ScriptTemplates.blank(editor.text())
                    || blocksEdited && editor.text().equals(codeAtSwitch) && blockGraph != null && blockGraph.size() > 0;
        }
        return blocksEdited || !editor.text().equals(loadedCode) || !nameBox().getValue().strip().equals(loadedName);
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
        var problems = blockGraph.problems(kind, library());
        if (!problems.isEmpty()) {
            if (select) {
                canvas.select(problems.getFirst().node());
            }
            show(problems.getFirst().message(), HudStyle.RED);
            return false;
        }
        int loose = blockGraph.size() - blockGraph.reachable().size();
        int handlers = (int) blockGraph.nodes().stream().filter(node -> node.op.isTrigger()).count();
        int defs = (int) blockGraph.nodes().stream().filter(node -> node.op == Op.DEF).count();
        if (blockGraph.global()) {
            show("Global script: " + blockGraph.size() + (blockGraph.size() == 1 ? " block" : " blocks")
                    + " (DEF blocks and names at start, for all your diagrams)", HudStyle.GREEN);
        } else if (kind == ProgramKind.CAR && handlers == 0 && defs == 0) {
            // Hoofdstuk 119 (gevonden door de nalezing): in een RC program is een DEF een knop op de auto, geen WHEN nodig.
            show(blockGraph.size() == 0 ? "Start with a DEF block (a button on the car)"
                    : "Add a DEF block: every def is a button on the car", HudStyle.ORANGE);
        } else if (handlers == 0 && kind != ProgramKind.CAR) {
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
            Script script = ScriptParser.parse(editor.text(), kind, library());
            editor.setErrorLine(0);
            int blocks = script.blocks();
            if (script.global()) {
                // Een global script (hoofdstuk 43): defs en gedeelde namen voor al je diagrammen.
                int defs = script.functions().size();
                int shared = script.globals().size();
                show(blocks == 0 ? "Empty global script: add defs and shared names"
                        : "Global script: " + defs + (defs == 1 ? " def" : " defs") + " and " + shared
                        + (shared == 1 ? " shared name" : " shared names") + " for all your diagrams (" + blocks
                        + (blocks == 1 ? " block)" : " blocks)"), HudStyle.GREEN);
            } else if (script.items().isEmpty()) {
                // Hoofdstuk 119: een RC program begint met een def (een knop op de auto), een robot of drone met when started.
                show(kind == ProgramKind.CAR ? "Empty: start with def strip_mine(): (a button on the car)"
                        : kind == ProgramKind.ROBOT || kind == ProgramKind.DRONE ? "Empty: start with the line when started:"
                        : "Empty: start with a line like when keycard(\"Reader 1\"):", HudStyle.ORANGE);
            } else if (kind == ProgramKind.CAR && script.handlers().isEmpty() && script.functions().isEmpty()) {
                show("No problems, but no def yet: every def is a button on the car", HudStyle.ORANGE);
            } else if (script.handlers().isEmpty() && kind != ProgramKind.CAR) {
                // In een RC program is een def zonder when gewoon een knop op de auto: daar hoort deze waarschuwing niet.
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
        if (currentLocked()) {
            show("This program is locked: you can only use it", HudStyle.ORANGE);
            return;
        }
        if (current == null && !dirty()) {
            // Een nieuw programma zonder code bewaren we niet (hoofdstuk 125, de nalezing): Save is dan grijs, maar een klik
            // of Ctrl+S zette anders toch een leeg programma op je server.
            show("Nothing to save yet: write some code first", HudStyle.ORANGE);
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
            ScriptParser.parse(editor.text(), kind, library());
            save();
        } catch (LogicException e) {
            // Kapotte code bewaren we niet vanzelf.
        }
    }

    /**
     * Een nieuw programma van deze soort: een diagram op de server, of (hoofdstuk 23) een robotprogramma, of
     * (hoofdstuk 25) een droneprogramma, of (hoofdstuk 119) een RC program. Het krijgt een vrije naam ("Diagram 2",
     * "Robot 1", "Flight 1", "RC 1") en is <b>leeg</b> (hoofdstuk 125, jouw wens: "zorg dat al de standaard scripts die in
     * een script komen als je een nieuw script maakt weg zijn"). In het lege programma staan je templates van deze soort:
     * klik er een aan en hij staat erin.
     */
    private void newDiagram(ProgramKind newKind) {
        choosingKind = false;
        page = Page.EDITOR;
        if (!confirmed("new", "Unsaved changes: click New again to throw them away")) {
            return;
        }
        // Een droneprogramma heet "Flight 1": "Drone 1" is al de naam van je eerste drone. Een RC program (hoofdstuk 119):
        // "RC 1".
        String base = newKind == ProgramKind.ROBOT ? "Robot " : newKind == ProgramKind.DRONE ? "Flight "
                : newKind == ProgramKind.CAR ? "RC " : "Diagram ";
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
        // Leeg (hoofdstuk 125): geen voorbeeldcode en geen uitleg in commentaar meer. Wat je hier kunt typen, staat in de
        // grijze hint in de editor (die wordt niet bewaard), in Help en onder Ctrl+Spatie.
        startEmpty(newKind, "");
    }

    /**
     * Het begin van elk nieuw programma: deze code (leeg, of de regel global), niets om ongedaan te maken, en de blokjes
     * erbij. Zijn we in de blokjes, dan blijven we daar.
     */
    private void startEmpty(ProgramKind newKind, String code) {
        editor.setText(code);
        editor.cursorToEnd();
        editor.setErrorLine(0);
        loadedCode = "";
        codeAtSwitch = code;
        blockGraph = GraphConverter.toGraph(ScriptParser.parse(code, newKind), null);
        blocksEdited = false;
        templateScroll = 0;
        // Typen gaat naar de code (de nalezing: stond het naamvak van Templates nog aan, dan kwam je code daarin).
        nameBox().setFocused(false);
        if (renameBox != null) {
            renameBox.setFocused(false);
        }
        if (templateNameBox != null) {
            templateNameBox.setValue("");
            templateNameBox.setFocused(false);
        }
        if (mode == Mode.BLOCKS) {
            canvas.setGraph(blockGraph);
            checkBlocks(false);
        } else {
            editor.setFocused(true);
            check(false);
        }
    }

    /**
     * Een nieuw global script (hoofdstuk 43): een diagram op de server dat met {@code global} begint. Het heet "Global 1".
     * Sinds hoofdstuk 125 staat er alleen die eerste regel in (zonder {@code global} is het geen global script), geen
     * voorbeeldnaam en geen voorbeeld-def meer; de cursor staat eronder.
     */
    private void newGlobalScript() {
        choosingKind = false;
        page = Page.EDITOR;
        if (!confirmed("new", "Unsaved changes: click New again to throw them away")) {
            return;
        }
        int number = 1;
        while (find("Global " + number) != null) {
            number++;
        }
        current = null;
        open = true;
        baseGraph = null;
        setKind(ProgramKind.SERVER);
        nameBox().setValue("Global " + number);
        loadedName = "";
        startEmpty(ProgramKind.SERVER, "global\n\n");
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
        return ask(what, question, what.equals("delete") || dirty());
    }

    /** Hetzelfde, maar je zegt zelf of het gevraagd moet worden ({@code needed}; de templates van hoofdstuk 125). */
    private boolean ask(String what, String question, boolean needed) {
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

    /**
     * Wat Ctrl+Spatie aan namen kan voorstellen: je apparaten, je lezers, je panelen, je robots en je keycard-groepen (en
     * voor welke soort programma).
     */
    private CodeCompletion.Data completionData() {
        List<String> outputs = new ArrayList<>();
        for (Tag tag : info().getList("outputs", Tag.TAG_STRING)) {
            outputs.add(tag.getAsString());
        }
        // De lezers, panelen, knoppen en hendels, per trigger die ze starten (keycard, codepanel, button, lever).
        List<String> readers = new ArrayList<>();
        List<String> panels = new ArrayList<>();
        List<String> buttons = new ArrayList<>();
        List<String> levers = new ArrayList<>();
        List<String> consoles = new ArrayList<>();
        for (Tag element : info().getList("inputs", Tag.TAG_COMPOUND)) {
            CompoundTag input = (CompoundTag) element;
            switch (input.getString("t")) {
                case "codepanel" -> panels.add(input.getString("n"));
                case "button" -> buttons.add(input.getString("n"));
                case "lever" -> levers.add(input.getString("n"));
                case "request" -> consoles.add(input.getString("n"));
                default -> readers.add(input.getString("n"));
            }
        }
        List<String> robots = new ArrayList<>();
        for (Tag element : info().getList("robots", Tag.TAG_COMPOUND)) {
            robots.add(((CompoundTag) element).getString("n"));
        }
        // De gedeelde namen en de defs uit je global scripts (hoofdstuk 43).
        com.senne.imttech.logic.program.Library library = library();
        List<String> shared = new ArrayList<>(library.shared().keySet());
        java.util.Map<String, String> globalDefs = new java.util.LinkedHashMap<>();
        library.defs().forEach((name, def) -> globalDefs.put(name, def.function().paramsCode()));
        return new CodeCompletion.Data(outputs, readers, panels, kind, robots, cardGroups(), buttons, levers, shared, globalDefs, consoles);
    }

    /** De keycard-groepen van dit netwerk (25 oktober), op alfabet. */
    private List<String> cardGroups() {
        List<String> groups = new ArrayList<>();
        for (Tag element : screen.data().getList("cardGroups", Tag.TAG_COMPOUND)) {
            groups.add(((CompoundTag) element).getString("n"));
        }
        return groups;
    }

    /** De naam van het eerste apparaat dat deze trigger start (een lezer bij keycard, ...), of null. */
    private @Nullable String firstInput(Op trigger) {
        for (Tag element : info().getList("inputs", Tag.TAG_COMPOUND)) {
            CompoundTag input = (CompoundTag) element;
            if (input.getString("t").equals(trigger.code())) {
                return input.getString("n");
            }
        }
        return null;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
        renderSidebar(g, font, a, mx, my);
        int x0 = a.x() + SIDE + 6;
        int width = a.x() + a.w() - x0;
        // Hoofdstuk 130: de pagina van de SD-kaart, of van een programma erop (de editor blijft daaronder zoals hij was).
        if (page == Page.CARD && cardIn) {
            renderCardPage(g, font, a, x0, width, mx, my);
            return;
        }
        if (page == Page.CARD_PROGRAM && cardIn && cardSelected != null && cardProgram(cardSelected) != null) {
            renderCardProgramPage(g, font, a, x0, width, mx, my, cardProgram(cardSelected));
            return;
        }
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
        boolean locked = currentLocked();
        OsDraw.field(g, bx, a.y(), bx + nameW, a.y() + OsDraw.BUTTON_H, box.isFocused() && !locked);
        if (locked) {
            // Hoofdstuk 130: de naam van een programma op slot staat vast (je kunt hem niet bewaren).
            g.drawString(font, OsDraw.fitRight(font, current, nameW - 6), bx + 3, a.y() + 2, HudStyle.argb(255, OsDraw.GREY), false);
        } else {
            box.setX(bx + 3);
            box.setY(a.y() + 2);
            box.setWidth(nameW - 6);
            box.render(g, mx, my, partialTick);
        }
        bx += nameW + 4;
        for (String label : toolbar()) {
            boolean enabled = !label.equals("Save") || dirty();
            int accent = label.equals("Delete") ? HudStyle.RED : label.equals("Off") ? HudStyle.ORANGE
                    : label.equals("On") ? HudStyle.GREEN : label.equals("Blocks") || label.equals("Code") ? 0xC09CFF : HudStyle.CYAN;
            bx += OsDraw.button(g, font, bx, a.y(), label, enabled, accent) + 3;
        }
        int top = a.y() + TOOLBAR;
        hoverHint = "";
        hintRows.clear();
        // Rechts: Help (bij de code).
        int rx = a.x() + a.w();
        for (String label : panelButtons().reversed()) {
            rx -= OsDraw.buttonWidth(font, label);
            panelButton(g, font, rx, a.y(), label, helpOpen, HudStyle.ORANGE);
            rx -= 3;
        }
        if (locked) {
            renderLockedPage(g, font, x0, top, width, a.h() - TOOLBAR - STATUS);
        } else if (mode == Mode.BLOCKS) {
            canvas.render(g, font, x0, top, width, a.h() - TOOLBAR - STATUS, mx, my, partialTick);
        } else {
            // De editor, met rechts Help of Templates als dat open is.
            boolean panel = helpOpen || templatesOpen;
            int editorW = panel ? width - HELP_W - 4 : width;
            int editorH = a.h() - TOOLBAR - STATUS;
            editor.setBounds(x0, top, editorW, editorH);
            editor.render(g, mx, my);
            renderEmptyHint(g, font, x0, top, editorW, editorH, mx, my);
            if (helpOpen) {
                renderHelp(g, font, x0 + editorW + 4, top, HELP_W, editorH, mx, my, partialTick);
            } else if (templatesOpen) {
                renderTemplates(g, font, x0 + editorW + 4, top, HELP_W, editorH, mx, my, partialTick);
            }
        }
        // De statusregel (in de blokjes: wat er onder de muis is, als er iets is).
        String text = !hoverHint.isEmpty() ? hoverHint : (dirty() ? "* " : "") + status;
        int colour = !hoverHint.isEmpty() ? 0xB8C8D0 : statusColour;
        g.drawString(font, OsDraw.fitLeft(font, text, width), x0, a.y() + a.h() - 9, HudStyle.argb(255, colour), false);
    }

    /** Hoe breed het naamvak is: 84 pixels, of smaller als de knoppen (en Help) anders niet passen. */
    private int nameWidth(Font font, SecurityOsScreen.Area a) {
        int x0 = a.x() + SIDE + 6;
        int room = a.x() + a.w() - x0 - font.width("Name") - 8;
        for (String label : panelButtons()) {
            room -= OsDraw.buttonWidth(font, label) + 3;
        }
        room -= 1;
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
        if (entry != null && entry.locked()) {
            // Hoofdstuk 130: op slot = alleen gebruiken: aan of uit (een diagram op de server) en weggooien.
            return entry.kind() == ProgramKind.SERVER && !entry.graph().global() ? List.of(entry.on() ? "Off" : "On", "Delete")
                    : List.of("Delete");
        }
        String view = mode == Mode.CODE ? "Blocks" : "Code";
        if (entry == null || entry.kind() != ProgramKind.SERVER) {
            // Een robotprogramma zet je niet aan of uit op de server: de robot draait het (app Robots).
            return List.of(view, "Check", "Save", "Delete");
        }
        return List.of(view, "Check", "Save", entry.on() ? "Off" : "On", "Delete");
    }

    /**
     * De knoppen helemaal rechts in de knoppenbalk: Help (alleen bij de code). De knop Templates (hoofdstuk 125) staat
     * links onder je diagrammen: in de knoppenbalk is op een klein scherm geen plek meer (1280 x 720: dan schoof hij over
     * Delete). Help en Templates openen allebei rechts een paneel; nooit twee tegelijk.
     */
    private List<String> panelButtons() {
        return mode == Mode.CODE && !currentLocked() ? List.of("Help") : List.of();
    }

    /** Waar de knop Templates staat: onderaan in de lijst met diagrammen (alleen als er een programma open is). */
    private static int templatesButtonY(SecurityOsScreen.Area a) {
        return a.y() + a.h() - 34 - OsDraw.BUTTON_H - 3;
    }

    /** Een knop die het paneel rechts opent (Help, Templates): met een streepje eronder als dat paneel open is. */
    private static void panelButton(GuiGraphics g, Font font, int x, int y, String label, boolean open, int accent) {
        int w = OsDraw.button(g, font, x, y, label, true, accent);
        if (open) {
            g.fill(x + 3, y + OsDraw.BUTTON_H - 2, x + w - 3, y + OsDraw.BUTTON_H - 1, HudStyle.argb(255, accent));
        }
    }

    private void renderSidebar(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my) {
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
            OsDraw.button(g, font, x + 4, top + 15 + 2 * (OsDraw.BUTTON_H + 3), "Drone program", true, DRONE_COLOUR);
            // Een RC program (hoofdstuk 119): het programma van een RC Car.
            OsDraw.button(g, font, x + 4, top + 15 + 3 * (OsDraw.BUTTON_H + 3), "RC program", true, CAR_COLOUR);
            // Een global script (hoofdstuk 43): defs en gedeelde namen voor al je diagrammen.
            OsDraw.button(g, font, x + 4, top + 15 + 4 * (OsDraw.BUTTON_H + 3), "Global script", true, HudStyle.ORANGE);
            OsDraw.button(g, font, x + 4, top + 15 + 5 * (OsDraw.BUTTON_H + 3), "Cancel", true, OsDraw.GREY);
            return;
        }
        // Onderaan in de lijst: de knop Templates (hoofdstuk 125), als er een programma open is (niet bij een programma op
        // slot, en niet op de pagina's van de SD-kaart: daar is geen code).
        int listBottom = bottom;
        if (templatesButtonShown()) {
            int ty = templatesButtonY(a);
            panelButton(g, font, x + 3, ty, "Templates", templatesOpen && mode == Mode.CODE, TEMPLATE_COLOUR);
            listBottom = ty - 2;
        }
        int y = top + 3;
        List<SideRow> rows = sideRows();
        sideScroll = Math.max(0, Math.min(sideScroll, Math.max(0, rows.size() - 1)));
        for (int i = sideScroll; i < rows.size() && y + ROW <= listBottom; i++) {
            renderSideRow(g, font, x, y, rows.get(i));
            y += ROW;
        }
        if (entries.isEmpty() && !open && !cardIn) {
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

    /** Staat de knop Templates onderaan de lijst? Alleen bij code die je kunt veranderen. */
    private boolean templatesButtonShown() {
        return open && page == Page.EDITOR && !currentLocked();
    }

    /**
     * Wat er in de lijst links staat: je diagrammen, het nieuwe programma dat nog niet bewaard is, en (hoofdstuk 130) als er
     * een SD-kaart in zit de kop SD met de programma's op de kaart.
     */
    private List<SideRow> sideRows() {
        List<SideRow> rows = new ArrayList<>();
        for (Entry entry : entries) {
            rows.add(new SideRow(entry, null, false, false));
        }
        if (open && current == null) {
            rows.add(new SideRow(null, null, false, true));
        }
        if (cardIn) {
            rows.add(new SideRow(null, null, true, false));
            for (CardProgram program : cardPrograms) {
                rows.add(new SideRow(null, program, false, false));
            }
        }
        return rows;
    }

    /** Een slotje (hoofdstuk 130): een programma op slot. 5 breed en 7 hoog. */
    private static void lock(GuiGraphics g, int x, int y, int colour) {
        int c = HudStyle.argb(255, colour);
        g.fill(x + 1, y, x + 4, y + 1, c);
        g.fill(x, y + 1, x + 1, y + 3, c);
        g.fill(x + 4, y + 1, x + 5, y + 3, c);
        g.fill(x, y + 3, x + 5, y + 7, c);
    }

    /** De letter van de soort rechts in de lijst (R, D, C of G), of "" voor een gewoon diagram. */
    private static String kindLetter(ProgramKind kind, boolean global) {
        return switch (kind) {
            case CAR -> "C";
            case DRONE -> "D";
            case ROBOT -> "R";
            default -> global ? "G" : "";
        };
    }

    private static int kindLetterColour(ProgramKind kind) {
        return switch (kind) {
            case CAR -> CAR_COLOUR;
            case DRONE -> DRONE_COLOUR;
            case ROBOT -> 0xF1E87C;
            default -> HudStyle.ORANGE;
        };
    }

    private void renderSideRow(GuiGraphics g, Font font, int x, int y, SideRow row) {
        if (row.header()) {
            // De kop van de SD-kaart: een kaartje, "SD" en zijn naam.
            boolean selected = page == Page.CARD;
            g.fill(x + 2, y - 3, x + SIDE - 2, y - 2, HudStyle.argb(255, OsDraw.DIM));
            if (selected) {
                g.fill(x + 2, y - 2, x + SIDE - 2, y + ROW - 3, HudStyle.argb(255, 0x16303F));
            }
            g.fill(x + 4, y, x + 9, y + 7, HudStyle.argb(255, 0x2A5BD7));
            g.fill(x + 4, y, x + 6, y + 2, HudStyle.argb(255, OsDraw.FIELD));
            g.drawString(font, OsDraw.fitRight(font, cardName.isBlank() ? "SD card" : "SD " + cardName, SIDE - 16), x + 12, y,
                    HudStyle.argb(255, HudStyle.CYAN), false);
            return;
        }
        if (row.fresh()) {
            if (page == Page.EDITOR) {
                g.fill(x + 2, y - 2, x + SIDE - 2, y + ROW - 3, HudStyle.argb(255, 0x16303F));
            }
            g.drawString(font, OsDraw.fitLeft(font, "*" + nameBox().getValue(), SIDE - 14), x + 10, y,
                    HudStyle.argb(255, HudStyle.ORANGE), false);
            return;
        }
        if (row.card() != null) {
            // Een programma op de kaart: ingesprongen, zonder aan/uit-bolletje.
            CardProgram program = row.card();
            boolean selected = page == Page.CARD_PROGRAM && program.name().equalsIgnoreCase(cardSelected == null ? "" : cardSelected);
            if (selected) {
                g.fill(x + 2, y - 2, x + SIDE - 2, y + ROW - 3, HudStyle.argb(255, 0x16303F));
            }
            String letter = kindLetter(program.kind(), program.global());
            int room = SIDE - 12 - (letter.isEmpty() ? 4 : 12) - (program.locked() ? 8 : 0);
            g.drawString(font, OsDraw.fitLeft(font, program.name(), room), x + 10, y, HudStyle.argb(255, 0xB8D8E8), false);
            if (!letter.isEmpty()) {
                g.drawString(font, letter, x + SIDE - 10, y, HudStyle.argb(255, kindLetterColour(program.kind())), false);
            }
            if (program.locked()) {
                lock(g, x + SIDE - (letter.isEmpty() ? 10 : 18), y, 0xE8C25A);
            }
            return;
        }
        Entry entry = row.entry();
        boolean selected = page == Page.EDITOR && open && current != null && current.equalsIgnoreCase(entry.name());
        if (selected) {
            g.fill(x + 2, y - 2, x + SIDE - 2, y + ROW - 3, HudStyle.argb(255, 0x16303F));
        }
        g.fill(x + 4, y + 2, x + 7, y + 5, HudStyle.argb(255, entry.on() ? HudStyle.GREEN : 0x4A5A62));
        String name = (selected && dirty() ? "*" : "") + entry.name();
        boolean car = entry.kind() == ProgramKind.CAR;
        boolean bot = entry.kind().onBot() || car;
        boolean global = entry.graph().global();
        // Een robotprogramma: een geel R'tje rechts (het draait op een robot, niet op de server); een droneprogramma
        // (hoofdstuk 25): een lichtblauwe D; een RC program (hoofdstuk 119): een groene C; een global script (hoofdstuk 43):
        // een oranje G. Hoofdstuk 130: een programma op slot krijgt er een slotje bij.
        String letter = kindLetter(entry.kind(), global);
        int room = SIDE - (letter.isEmpty() ? 14 : 24) - (entry.locked() ? 8 : 0);
        g.drawString(font, OsDraw.fitLeft(font, name, room), x + 10, y,
                HudStyle.argb(255, entry.on() || bot ? HudStyle.WHITE : OsDraw.GREY), false);
        if (!letter.isEmpty()) {
            g.drawString(font, letter, x + SIDE - 10, y, HudStyle.argb(255, kindLetterColour(entry.kind())), false);
        }
        if (entry.locked()) {
            lock(g, x + SIDE - (letter.isEmpty() ? 10 : 18), y, 0xE8C25A);
        }
    }

    /**
     * Een klik op een regel in de lijst links: een diagram opent in de editor (met de vraag of je wijzigingen weg mogen),
     * de kop SD opent de pagina van de kaart, een programma op de kaart zijn pagina. De pagina's van de kaart laten de editor
     * zoals hij was: klik je daarna je diagram weer aan, dan staat alles er nog (ook wat je nog niet bewaarde).
     */
    private void clickSideRow(SideRow row) {
        if (row.header()) {
            openCardPage();
            return;
        }
        if (row.card() != null) {
            openCardProgram(row.card());
            return;
        }
        if (row.fresh()) {
            page = Page.EDITOR; // het nieuwe programma dat je aan het schrijven bent
            return;
        }
        Entry entry = row.entry();
        if (current != null && current.equalsIgnoreCase(entry.name())) {
            page = Page.EDITOR;
            return;
        }
        if (confirmed("switch:" + entry.name(), "Unsaved changes: click " + entry.name() + " again to throw them away")) {
            page = Page.EDITOR;
            openEntry(entry, false);
        }
    }

    // ================================================================ De pagina's van de SD-kaart (hoofdstuk 130)

    /** Een rij knoppen die je kunt aanklikken: waar ze staan en wat ze doen (getekend en geklikt met dezelfde plekken). */
    private record CardHit(int x, int y, int w, int h, Runnable action) {}

    private final List<CardHit> cardHits = new ArrayList<>();
    private static final int CARD_ROW = 13;

    private int cardButton(GuiGraphics g, Font font, int x, int y, String label, boolean enabled, int accent, Runnable action) {
        int w = OsDraw.button(g, font, x, y, label, enabled, accent);
        if (enabled) {
            cardHits.add(new CardHit(x, y, w, OsDraw.BUTTON_H, action));
        }
        return w;
    }

    /** Hoe een soort heet: "Server diagram", "Robot program", ... en "Global script". */
    private static String kindTitle(ProgramKind kind, boolean global) {
        return global ? "Global script" : kind.title();
    }

    private static int kindTitleColour(ProgramKind kind, boolean global) {
        return kind == ProgramKind.SERVER && !global ? HudStyle.CYAN : kindLetterColour(kind);
    }

    /**
     * De pagina van de kaart:
     * <pre>
     *  SD CARD  Mijnbot  32 GB                 3/16 programs  2/32 files
     *  Copy a program from this server onto the card:
     *  Lights        Server diagram          [Copy] [Copy locked]
     *  Mine car      RC program              [Copy] [Copy locked]
     *  Locked: they can use it (start, stop, load it on a car), never read it.
     * </pre>
     */
    private void renderCardPage(GuiGraphics g, Font font, SecurityOsScreen.Area a, int x0, int width, int mx, int my) {
        cardHits.clear();
        int y0 = a.y();
        int right = x0 + width;
        String used = cardPrograms.size() + "/" + cardMaxPrograms + " programs  " + cardFiles + "/" + cardMaxFiles + " files";
        g.drawString(font, "SD CARD", x0, y0 + 2, HudStyle.argb(255, HudStyle.CYAN), false);
        int nx = x0 + font.width("SD CARD") + 6;
        String title = (cardName.isBlank() ? "" : cardName + "  ") + cardGb + " GB";
        g.drawString(font, OsDraw.fitRight(font, title, right - font.width(used) - 8 - nx), nx, y0 + 2,
                HudStyle.argb(255, HudStyle.WHITE), false);
        g.drawString(font, used, right - font.width(used), y0 + 2, HudStyle.argb(255, OsDraw.GREY), false);
        g.fill(x0, y0 + 12, right, y0 + 13, HudStyle.argb(255, OsDraw.DIM));
        boolean mayUse = screen.mayUse(SecurityOsScreen.App.LOGIC);
        g.drawString(font, "Copy a program from this server onto the card:", x0, y0 + 17, HudStyle.argb(255, HudStyle.WHITE), false);
        int top = y0 + 30;
        int bottom = a.y() + a.h() - 34;
        int rows = Math.max(1, (bottom - top) / CARD_ROW);
        cardScroll = Math.max(0, Math.min(cardScroll, Math.max(0, entries.size() - rows)));
        int lockedW = OsDraw.buttonWidth(font, "Copy locked");
        int copyW = OsDraw.buttonWidth(font, "Copy");
        int kindX = x0 + Math.min(110, (width - lockedW - copyW - 10) / 2);
        if (entries.isEmpty()) {
            g.drawString(font, "(no programs on this server yet)", x0 + 4, top + 2, HudStyle.argb(255, OsDraw.GREY), false);
        }
        for (int i = cardScroll; i < entries.size() && i - cardScroll < rows; i++) {
            Entry entry = entries.get(i);
            int ry = top + (i - cardScroll) * CARD_ROW;
            boolean global = entry.graph().global();
            g.drawString(font, OsDraw.fitRight(font, entry.name(), kindX - x0 - 6), x0 + 4, ry + 2,
                    HudStyle.argb(255, HudStyle.WHITE), false);
            g.drawString(font, OsDraw.fitRight(font, kindTitle(entry.kind(), global), right - lockedW - copyW - 10 - kindX), kindX, ry + 2,
                    HudStyle.argb(255, kindTitleColour(entry.kind(), global)), false);
            if (entry.locked()) {
                // Een programma op slot (van een kaart) zet je nooit weer op een kaart.
                String text = "locked";
                lock(g, right - font.width(text) - 9, ry + 2, 0xE8C25A);
                g.drawString(font, text, right - font.width(text), ry + 2, HudStyle.argb(255, OsDraw.GREY), false);
                continue;
            }
            String name = entry.name();
            cardButton(g, font, right - lockedW - copyW - 3, ry, "Copy", mayUse, HudStyle.CYAN, () -> copyToCard(name, false));
            // Een global script gaat altijd open op een kaart (je andere programma's hebben zijn namen nodig).
            cardButton(g, font, right - lockedW, ry, "Copy locked", mayUse && !global, 0xE8C25A, () -> copyToCard(name, true));
        }
        int ty = a.y() + a.h() - 30;
        g.drawString(font, OsDraw.fitRight(font, "Locked: they can use it, never read or change it.", width), x0, ty,
                HudStyle.argb(255, OsDraw.GREY), false);
        g.drawString(font, OsDraw.fitRight(font, "Files: the card is the folder sd. Sneak + empty hand: out.", width), x0, ty + 10,
                HudStyle.argb(255, OsDraw.GREY), false);
    }

    /**
     * De pagina van een programma op de kaart: wat het is, Install en Delete, en (open) zijn code om te lezen of (op
     * slot) dat je de code nooit ziet.
     */
    private void renderCardProgramPage(GuiGraphics g, Font font, SecurityOsScreen.Area a, int x0, int width, int mx, int my,
                                       CardProgram program) {
        cardHits.clear();
        int y0 = a.y();
        int right = x0 + width;
        g.drawString(font, OsDraw.fitRight(font, program.name(), width / 2), x0, y0 + 2, HudStyle.argb(255, HudStyle.WHITE), false);
        int kx = x0 + Math.min(font.width(program.name()), width / 2) + 6;
        g.drawString(font, OsDraw.fitRight(font, kindTitle(program.kind(), program.global()) + " on " + cardTitle(), right - kx),
                kx, y0 + 2, HudStyle.argb(255, kindTitleColour(program.kind(), program.global())), false);
        g.fill(x0, y0 + 12, right, y0 + 13, HudStyle.argb(255, OsDraw.DIM));
        String about = program.locked()
                ? "Locked by " + (program.author().isBlank() ? "its maker" : program.author())
                        + ": install it to use it. Nobody can read or change its code."
                : "Open: install it, then you can read and change it. Its code is below.";
        List<String> lines = OsDraw.wrap(font, about, width, 2);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x0, y0 + 17 + i * 10,
                    HudStyle.argb(255, program.locked() ? 0xE8C25A : OsDraw.GREY), false);
        }
        boolean mayUse = screen.mayUse(SecurityOsScreen.App.LOGIC);
        int by = y0 + 40;
        String name = program.name();
        int bx = x0;
        bx += cardButton(g, font, bx, by, "Install on this server", mayUse, HudStyle.GREEN, () -> installFromCard(name)) + 4;
        boolean asking = name.equalsIgnoreCase(cardConfirm) && Util.getMillis() < cardConfirmUntil;
        cardButton(g, font, bx, by, asking ? "Sure? Click again" : "Delete from card", mayUse, HudStyle.RED, () -> deleteFromCard(name));
        int top = by + OsDraw.BUTTON_H + 5;
        int h = a.y() + a.h() - STATUS - top;
        if (program.locked()) {
            OsDraw.field(g, x0, top, right, top + h, false);
            lock(g, x0 + 8, top + 8, 0xE8C25A);
            List<String> text = new ArrayList<>(OsDraw.wrap(font, "The code of a locked program never leaves the server.",
                    width - 26, 2));
            text.add("Size: " + program.blocks() + (program.blocks() == 1 ? " block" : " blocks"));
            for (int i = 0; i < text.size(); i++) {
                g.drawString(font, text.get(i), x0 + 20, top + 8 + i * 10, HudStyle.argb(255, OsDraw.GREY), false);
            }
        } else {
            cardPreview.setBounds(x0, top, width, h);
            cardPreview.render(g, mx, my);
            if (previewFor == null) {
                g.drawString(font, "Loading the code...", x0 + 6, top + 6, HudStyle.argb(255, OsDraw.GREY), false);
            }
        }
    }

    /** Een programma op slot op je server: geen code, alleen wat het is en wat je ermee kunt. */
    private void renderLockedPage(GuiGraphics g, Font font, int x0, int top, int width, int h) {
        Entry entry = current == null ? null : find(current);
        if (entry == null) {
            return;
        }
        OsDraw.field(g, x0, top, x0 + width, top + h, false);
        lock(g, x0 + 8, top + 8, 0xE8C25A);
        g.drawString(font, "Locked: " + kindTitle(entry.kind(), entry.graph().global()), x0 + 20,
                top + 8, HudStyle.argb(255, 0xE8C25A), false);
        String by = entry.author().isBlank() ? "someone" : entry.author();
        String use = entry.kind() == ProgramKind.SERVER
                ? "From an SD card, locked by " + by + ". You can switch it on and off, or delete it."
                : "From an SD card, locked by " + by + ". Load it onto a " + (entry.kind() == ProgramKind.CAR ? "car" : "robot")
                        + " (app Robots), or delete it.";
        List<String> lines = new ArrayList<>(OsDraw.wrap(font, use, width - 26, 3));
        lines.addAll(OsDraw.wrap(font, "Nobody can see or change its code: it only runs.", width - 26, 2));
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x0 + 20, top + 22 + i * 10, HudStyle.argb(255, OsDraw.GREY), false);
        }
    }

    private void copyToCard(String name, boolean locked) {
        CompoundTag args = new CompoundTag();
        args.putString("name", name);
        args.putBoolean("locked", locked);
        screen.send("sd_copy", args);
    }

    private void installFromCard(String name) {
        CompoundTag args = new CompoundTag();
        args.putString("name", name);
        screen.send("sd_install", args);
    }

    /** Weggooien van de kaart vraagt het eerst: de knop wordt "Sure? Click again" (4 seconden). */
    private void deleteFromCard(String name) {
        if (!name.equalsIgnoreCase(cardConfirm) || Util.getMillis() >= cardConfirmUntil) {
            cardConfirm = name;
            cardConfirmUntil = Util.getMillis() + 4000;
            return;
        }
        cardConfirm = null;
        CompoundTag args = new CompoundTag();
        args.putString("name", name);
        screen.send("sd_delete", args);
    }

    // ================================================================ Help

    /** Eén regel in het Help-paneel: een kopje, of iets dat je kunt invoegen (met uitleg). */
    private record HelpLine(String label, @Nullable String insert, String tip, int colour, @Nullable CompoundTag input) {}

    private List<HelpLine> helpLines() {
        if (kind == ProgramKind.CAR) {
            return carHelpLines(); // hoofdstuk 119: wat een RC Car kent
        }
        List<HelpLine> lines = new ArrayList<>();
        String reader = firstInput(Op.KEYCARD);
        String panel = firstInput(Op.CODEPANEL);
        String button = firstInput(Op.BUTTON);
        String lever = firstInput(Op.LEVER);
        String console = firstInput(Op.REQUEST);
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
        lines.add(item("button(...)", "when button(\"" + (button == null ? "Button 1" : button) + "\"):\n    say(player.name + \" pressed the button\")\n", Op.BUTTON));
        lines.add(item("lever(...)", "when lever(\"" + (lever == null ? "Lever 1" : lever) + "\"):\n    if lever.on:\n        on(\"" + device + "\")\n    else:\n        off(\"" + device + "\")\n", Op.LEVER));
        lines.add(item("request(...)", "when request(\"" + (console == null ? "Console 1" : console) + "\"):\n    if request.accepted:\n        pulse(\"" + device + "\", 5)\n    else:\n        say(player.name + \" was denied\")\n", Op.REQUEST));
        lines.add(item("turned_on(...)", "when turned_on(\"" + device + "\"):\n    pass\n", Op.TURNED_ON));
        lines.add(item("turned_off(...)", "when turned_off(\"" + device + "\"):\n    pass\n", Op.TURNED_OFF));
        lines.add(item("message(...)", "when message(\"intruder\"):\n    alarm(\"Intruder seen by \" + sender.name)\n", Op.MESSAGE));
        } else {
            lines.add(item("started", kind == ProgramKind.DRONE
                    ? "when started:\n    up(2)\n    while true:\n        forward(3)\n        turn_right()\n"
                    : "when started:\n    while true:\n        forward(3)\n        turn_right()\n", Op.STARTED));
            lines.add(item("ordered(...)", "when ordered(\"attack\"):\n    goto(order.x, order.y, order.z)\n", Op.ORDERED));
            // Hoofdstuk 60: de zintuigen van een bewaker.
            lines.add(item("heard", "when heard:\n    goto(sound.x, sound.y, sound.z)\n", Op.HEARD));
            lines.add(item("spotted", "when spotted:\n    send(\"contact\")\n    aim(target.x, target.y, target.z)\n    write(laserPin, true)\n", Op.SPOTTED));
            lines.add(item("body_seen", "when body_seen:\n    send(\"man down\")\n", Op.BODY_SEEN));
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
        if (server) {
            // Je robots een opdracht geven (hoofdstuk 23).
            lines.add(header("ROBOTS"));
            lines.add(item("order(..., \"attack\")", "order(\"Guard 1\", \"attack\", sender.x, sender.y, sender.z)", Op.ORDER));
            lines.add(item("activate(...)", "activate(\"Guard 1\")", Op.ACTIVATE));
            lines.add(item("deactivate(...)", "deactivate(\"Guard 1\")", Op.DEACTIVATE));
            // Squads (hoofdstuk 26): een naam mag een squad zijn.
            lines.add(item("squad_down(...)", "when squad_down(\"Wave 1\"):\n    activate(\"Wave 2\")\n", Op.SQUAD_DOWN));
            lines.add(new HelpLine("if active(\"Wave 1\") == 0:", "if active(\"Wave 1\") == 0:\n    pass",
                    Expr.Function.ACTIVE.help(), HudStyle.WHITE, null));
            // Trainingen (hoofdstuk 26).
            lines.add(header("TRAINING"));
            lines.add(item("training_started(...)", "when training_started(\"Bootcamp\"):\n    say(\"Good luck, \" + trainee)\n",
                    Op.TRAINING_STARTED));
            lines.add(item("training_hit(...)", "when training_hit(\"Bootcamp\"):\n    say(trainee + \" has \" + lives + \" lives left\")\n",
                    Op.TRAINING_HIT));
            lines.add(item("training_ended(...)", "when training_ended(\"Bootcamp\"):\n    if passed:\n        say(\"Well done, \" + trainee)\n",
                    Op.TRAINING_ENDED));
            lines.add(new HelpLine("trainee, lives, hits, passed", "trainee",
                    "who trains, lives left (Lethal: 1 until they die), how often hit, and (when ended) if they passed",
                    HudStyle.WHITE, null));
        } else {
            // Wat een robot (of een drone, hoofdstuk 25) zelf kan (hoofdstuk 23).
            String self = kind == ProgramKind.DRONE ? "drone" : "robot";
            lines.add(header("MOVE"));
            lines.add(item("forward(3) / back(1)", "forward(3)", Op.FORWARD));
            lines.add(item("turn_left() / turn_right()", "turn_right()", Op.TURN_RIGHT));
            if (kind == ProgramKind.DRONE) {
                lines.add(item("up(2) / down(1)", "up(2)", Op.UP));
            }
            lines.add(item("goto(x, y, z)", "goto(" + self + ".x + 5, " + self + ".y, " + self + ".z)", Op.GOTO));
            lines.add(item("charge() / charge(80)", "if " + self + ".battery < 10:\n    charge()", Op.CHARGE));
            lines.add(item("stop()", "stop()", Op.STOP));
            lines.add(header("PINS AND THE SERVER"));
            lines.add(new HelpLine("scanPin = pin(1)", "scanPin = pin(1)\n", Expr.Function.PIN.help(), 0xF1E87C, null));
            lines.add(new HelpLine("if read(scanPin):", "if read(scanPin):\n    pass", Expr.Function.READ.help(), HudStyle.WHITE, null));
            lines.add(item("write(laserPin, true)", "write(laserPin, true)", Op.WRITE));
            lines.add(item("aim(x, y, z) / aim()", "aim(" + self + ".x, " + self + ".y, " + self + ".z - 5)", Op.AIM));
            lines.add(item("send(...)", "send(\"intruder\")", Op.SEND));
            lines.add(new HelpLine("if " + self + ".battery < 10:", "if " + self + ".battery < 10:\n    charge()",
                    kind == ProgramKind.DRONE
                            ? "also: drone.x, drone.y, drone.z, drone.height, drone.facing, drone.name, drone.blocked, drone.connected, drone.alert (0-100)"
                            : "also: robot.x, robot.y, robot.z, robot.facing, robot.name, robot.blocked, robot.connected, robot.alert (0-100)",
                    HudStyle.WHITE, null));
            // Wat elke module zegt (read) en doet (write) (hoofdstuk 24).
            lines.add(header("MODULES (read / write)"));
            lines.add(new HelpLine("Scan: if read(scanPin):", "if read(scanPin):\n    alarm(\"Intruder near \" + " + self + ".name)",
                    "true if it sees a player (not through walls; not in creative); write(scanPin, false) = off: it sees nobody",
                    HudStyle.WHITE, null));
            lines.add(new HelpLine("Distance: read(distancePin)", "if read(distancePin) < 3:\n    turn_right()",
                    "blocks to what stands in front of it (99 = nothing, or switched off)", HudStyle.WHITE, null));
            lines.add(new HelpLine("Face: write(facePin, ...)", "write(facePin, \"strangers\")",
                    "who it looks for: \"everyone\", \"strangers\", \"trusted\", \"trainee\" or a name; read(facePin) = who it sees; false = off",
                    0x9ED8FF, null));
            lines.add(new HelpLine("Laser: write(laserPin, true)", "write(laserPin, true)",
                    "on: fires whenever ready, at aim(x, y, z) or whom the scan sees (no scan: straight ahead), until false; read = ready to fire",
                    0x9ED8FF, null));
            lines.add(new HelpLine("Gun: write(gunPin, true)", "write(gunPin, true)",
                    "the same, with bullets (Robot Ammo: right-click the " + self + " to load); read = bullets left", 0x9ED8FF, null));
            lines.add(new HelpLine("Solar: read(solarPin)", "if read(solarPin) > 50:\n    pass",
                    "how much sun it catches, 0-100 (it charges by itself); write(solarPin, false) = off: it stops charging",
                    HudStyle.WHITE, null));
            lines.add(new HelpLine("Camera: read(cameraPin)", "if read(cameraPin):\n    say(\"Someone is watching\")",
                    "true while someone looks through its eyes (Cameras app); write(cameraPin, false) = off", HudStyle.WHITE, null));
            lines.add(new HelpLine("Path: read(pathPin)", "goto(10, 64, -20)",
                    "lets goto(x, y, z) work; read = true while it is on its way; write(pathPin, false) = off: goto is blocked",
                    HudStyle.WHITE, null));
        }
        lines.add(header("CHECK (if)"));
        if (server) {
        String group = cardGroups().isEmpty() ? "security" : cardGroups().getFirst();
        lines.add(new HelpLine("if card.in_group(...):", "if card.in_group(\"" + group + "\"):\n    pass",
                "a valid card in that keycard group, or in a group inside it (Keycards app)", HudStyle.WHITE, null));
        lines.add(new HelpLine("if card.valid:", "if card.valid:\n    pass", "written by this network and not blocked", HudStyle.WHITE, null));
        lines.add(new HelpLine("if code == \"1234\":", "if code == \"1234\":\n    pass", "the code typed on a code panel (between quotes)", HudStyle.WHITE, null));
        // De tip onderaan heeft twee regels (hoofdstuk 115: kort, zodat "after a keycard" er nog op past).
        lines.add(new HelpLine("if player.trusted:", "if player.trusted:\n    pass", "owner, team, trusted; never after a keycard",
                HudStyle.WHITE, null));
        }
        // Hoofdstuk 131: de naam mag ook berekend zijn. De uitleg past op de twee regels onder het paneel.
        lines.add(new HelpLine("if is_on(...):", "if is_on(\"" + device + "\"):\n    pass",
                "is that device on right now? Also: is_on(f\"gate {i}\")", HudStyle.WHITE, null));
        lines.add(new HelpLine("if is_night:", "if is_night:\n    pass", server ? "also: is_day, player.owner, player.name, card.number, card.name"
                : "also: is_day", HudStyle.WHITE, null));
        lines.add(new HelpLine("if count * 2 - 1 >= 5:", "if count * 2 - 1 >= 5:\n    pass",
                "calculate with + - * / and brackets (* and / go first); + also glues text: \"Count \" + count",
                HudStyle.WHITE, null));
        lines.add(new HelpLine("else: / elif ...:", "else:\n    pass", "what happens when the if is not true", HudStyle.WHITE, null));
        lines.add(header("NAMES (variables)"));
        lines.add(new HelpLine("count = 0", "count = 0\n",
                "At the left edge: a name for the whole program. It keeps its value between runs.", 0xF1E87C, null));
        lines.add(new HelpLine("count: int = 0", "count: int = 0\n",
                "A name with a type: int (whole number), float (decimal), string (text) or bool (true/false). It keeps that type;"
                        + " an int rounds down (7 / 2 = 3).", 0xF1E87C, null));
        lines.add(new HelpLine("count = count + 1", "count = count + 1",
                "Inside a when or def: give a name a (new) value. A def has its own names.", 0xF1E87C, null));
        lines.add(new HelpLine("if count >= 3:", "if count >= 3:\n    pass",
                "Use your name like is_night or card.number. A number stays a number, text stays text.", HudStyle.WHITE, null));
        lines.add(new HelpLine("say(\"Count: \" + count)", "say(\"Count: \" + count)",
                "A message (say, log, alarm) or the seconds (wait, pulse) may be calculated: wait(delay * 2)",
                0x9ED8FF, null));
        // 19 oktober: tekst met namen erin (jouw notitie: $"dit is de {aantal} test").
        // Hoofdstuk 135 (jouw wens: "zorg dat een f string bij alles werkt niet enkel bij for loop"): de f staat voorop, en
        // de uitleg zegt dat het overal werkt waar tekst mag. Elke uitleg past op de twee regels onder het paneel (146 px).
        lines.add(new HelpLine("say(f\"This is test {count}\")", "say(f\"This is test {count}\")",
                "Fills in the { }: a name or a sum. Anywhere text goes.", 0x6BE88B, null));
        // 16 november (hoofdstuk 131): een rijtje apparaten met dezelfde naam en een ander getal. Het label (116 px) en de
        // uitleg (twee regels) passen in het paneel van 152 px; een klik zet de hele lus in je code.
        lines.add(new HelpLine("on(f\"gate {i}\") in a loop", "for i in range(3):\n    on(f\"gate {i}\")",
                "Switches on gate 0, 1 and 2. on(\"gate \" + i) works too.", 0x6BE88B, null));
        if (server) {
            // Ook in een when-regel, met een naam van bovenaan (zijn beginwaarde): de when moet zijn apparaat al kennen.
            lines.add(new HelpLine("when keycard(f\"{room}\"):", "room = \"Lab\"\n\nwhen keycard(f\"{room} door\"):\n    accept()\n",
                    "A when uses names from the top: room = \"Lab\" above it.", 0x6BE88B, null));
        }
        lines.add(header("LOOPS (again and again)"));
        lines.add(new HelpLine("for i in range(3):", "for i in range(3):\n    pulse(\"" + device + "\", 0.5)\n    wait(1)",
                "Do the steps inside 3 times; i is 0, then 1, then 2. The range may be a name or a sum: range(count + 1)",
                0xFF79C6, null));
        lines.add(new HelpLine("for i in range(1, 4):", "for i in range(1, 4):\n    say(\"Round \" + i)",
                "Count from 1 up to 4 (without 4): i is 1, 2, 3. Use i like any name: say(\"Round \" + i)", 0xFF79C6, null));
        lines.add(new HelpLine("while count < 3:", "while count < 3:\n    count = count + 1",
                "As long as the check is true. Change something inside, or it never stops.", 0xFF79C6, null));
        lines.add(new HelpLine("while true:", "while true:\n    wait(1)",
                "Again and again, until break. Put a wait inside: a loop without waiting keeps your CPU busy.", 0xFF79C6, null));
        lines.add(new HelpLine("break", "break", Op.BREAK.help(), 0xFF79C6, null));
        robotHelp(lines, server);
        lines.add(header("YOUR OWN BLOCKS (def)"));
        lines.add(new HelpLine("def open_door():", "def open_door():\n    accept()\n    pulse(\"" + device + "\", 3)\n",
                "Write steps once (at the left edge), then use them in many whens with open_door()", 0xC9A0FF, null));
        lines.add(new HelpLine("def open_door(string door):", "def open_door(string door):\n    accept()\n    log(\"Opened \" + door)\n    pulse(door, 3)\n",
                "A def with values it needs (parameters, with a type): use it with open_door(\"" + device + "\"). Inside, door is a name.",
                0xC9A0FF, null));
        java.util.regex.Matcher defs = java.util.regex.Pattern.compile("(?m)^def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)")
                .matcher(editor.text());
        while (defs.find()) {
            boolean takes = !defs.group(2).isBlank();
            lines.add(new HelpLine(defs.group(1) + "(" + defs.group(2).strip() + ")", defs.group(1) + "()",
                    "one of your own blocks: does all its steps here" + (takes ? " (needs: " + defs.group(2).strip() + ")" : ""),
                    0xC9A0FF, null));
        }
        // Global scripts (hoofdstuk 43): defs en gedeelde namen voor al je diagrammen, met hun waarde van nu.
        lines.add(header("GLOBAL SCRIPTS (shared)"));
        lines.add(new HelpLine("global", "global\n\n", "The first line of a global script: it holds only defs and shared names,"
                + " and every diagram of this core can use them (+ > Global script)", 0xFF79C6, null));
        lines.add(new HelpLine("lockdown: bool = false", "lockdown: bool = false\n",
                "In a global script: a shared name. Every diagram reads and changes the same value, and the core keeps it.",
                0xF1E87C, null));
        lines.add(new HelpLine("if lockdown:", "if lockdown:\n    deny()", "In any diagram: use a shared name like your own names",
                HudStyle.WHITE, null));
        com.senne.imttech.logic.program.Library library = library();
        for (var entry : library.defs().entrySet()) {
            String params = entry.getValue().function().paramsCode();
            lines.add(new HelpLine(entry.getKey() + "(" + params + ")", entry.getKey() + "()",
                    "a def from global script " + entry.getValue().script() + (params.isBlank() ? "" : " (needs: " + params + ")"),
                    0xC9A0FF, null));
        }
        for (Tag element : info().getList("shared", Tag.TAG_COMPOUND)) {
            CompoundTag sharedName = (CompoundTag) element;
            lines.add(new HelpLine(sharedName.getString("n") + " = " + sharedName.getString("v"), sharedName.getString("n"),
                    "a shared name (" + sharedName.getString("t") + "), its value right now: " + sharedName.getString("v"), 0xF1E87C, null));
        }
        if (library.isEmpty() && info().getList("shared", Tag.TAG_COMPOUND).isEmpty()) {
            lines.add(new HelpLine("(no global script yet)", null, "Press + and choose Global script", OsDraw.GREY, null));
        }
        lines.add(header("YOUR DEVICES"));
        if (outputs.isEmpty()) {
            lines.add(new HelpLine("(none: name relays with your glasses)", null,
                    "Place a relay, laser or turret here and give it a name with I.M.T.", OsDraw.GREY, null));
        }
        for (String name : outputs) {
            lines.add(new HelpLine(name, quoted(name), "a relay, laser, dome, turret or cabinet switch", 0x9ED8FF, null));
        }
        if (!server) {
            return lines; // lezers, panelen, knoppen en hendels horen bij de server
        }
        lines.add(header("READERS, PANELS, BUTTONS, LEVERS"));
        for (Tag element : info().getList("inputs", Tag.TAG_COMPOUND)) {
            CompoundTag input = (CompoundTag) element;
            String tag = switch (input.getString("t")) {
                case "codepanel" -> "[panel] ";
                case "button" -> "[button] ";
                case "lever" -> "[lever] ";
                default -> "[reader] ";
            };
            lines.add(new HelpLine(tag + input.getString("n"), quoted(input.getString("n")), "click R to rename it",
                    HudStyle.CYAN, input));
        }
        if (info().getList("inputs", Tag.TAG_COMPOUND).isEmpty()) {
            lines.add(new HelpLine("(none in this network yet)", null,
                    "Connect a reader, panel, button or lever to the core with a Network Cable", OsDraw.GREY, null));
        }
        return lines;
    }

    /** Een robot die een vierkant loopt, en laadt als zijn batterij bijna leeg is (voorbeeld in Help). */
    private static final String PATROL_EXAMPLE = """
            when started:
                while true:
                    if robot.battery < 20:
                        charge()
                    forward(4)
                    turn_right()
            """;

    /** Een robot die komt als de server roept (voorbeeld in Help). */
    private static final String COME_EXAMPLE = """
            when ordered("come"):
                goto(order.x, order.y, order.z)
                send("arrived")
            """;

    /** Een drone die een vierkant vliegt, en laadt als zijn batterij bijna leeg is (voorbeeld in Help, hoofdstuk 25). */
    private static final String DRONE_PATROL_EXAMPLE = """
            when started:
                up(2)
                while true:
                    if drone.battery < 20:
                        charge()
                        up(2)
                    forward(4)
                    turn_right()
            """;

    /** De kleur van een droneprogramma (de D in de lijst, de knop na +). */
    private static final int DRONE_COLOUR = 0x8FE3FF;
    /** De kleur van een RC program (hoofdstuk 119): groen, zoals de knop Start van de auto. */
    static final int CAR_COLOUR = 0x6BE88B;

    /** Een serverdiagram dat een robot roept (voorbeeld in Help). */
    private static final String CALL_EXAMPLE = """
            when message("help"):
                order("Guard 1", "come", sender.x, sender.y, sender.z)
            """;

    /**
     * Je robots in de Help (hoofdstuk 23). In een robotprogramma: wat er op hun pinnen zit (klik = de pinnen met een
     * naam in je code, zoals {@code scanPin = pin(1)}) en voorbeeldprogramma's. In een serverdiagram: hun namen (voor
     * order, activate en deactivate) en een voorbeeld. Een droneprogramma (hoofdstuk 25) toont je drones, een
     * serverdiagram je robots en je drones.
     */
    private void robotHelp(List<HelpLine> lines, boolean server) {
        boolean drones = kind == ProgramKind.DRONE;
        lines.add(header(server ? "YOUR ROBOTS AND DRONES" : drones ? "YOUR DRONES" : "YOUR ROBOTS"));
        List<CompoundTag> robots = new ArrayList<>();
        for (Tag element : info().getList("robots", Tag.TAG_COMPOUND)) {
            CompoundTag robot = (CompoundTag) element;
            if (server || robot.getBoolean("drone") == drones) {
                robots.add(robot);
            }
        }
        if (robots.isEmpty()) {
            lines.add(new HelpLine("(none in this network's area)", null, drones ? "Craft a Security Drone and put it on a Drone Dock here"
                    : "Build one in a Robot Assembler and put it here", OsDraw.GREY, null));
        }
        if (server) {
            // Je squads (hoofdstuk 26): één naam voor een hele groep.
            java.util.Map<String, List<String>> squads = new java.util.LinkedHashMap<>();
            for (CompoundTag robot : robots) {
                if (!robot.getString("sq").isEmpty()) {
                    squads.computeIfAbsent(robot.getString("sq"), key -> new ArrayList<>()).add(robot.getString("n"));
                }
            }
            for (java.util.Map.Entry<String, List<String>> squad : squads.entrySet()) {
                lines.add(new HelpLine("[squad] " + squad.getKey(), quoted(squad.getKey()),
                        "a squad: " + String.join(", ", squad.getValue()) + " (activate, deactivate, order, squad_down)",
                        0xF1E87C, null));
            }
        }
        for (CompoundTag robot : robots) {
            String name = robot.getString("n");
            if (server) {
                lines.add(new HelpLine(name, quoted(name), (robot.getBoolean("drone") ? "a security drone" : "a guard robot")
                        + ": use its name in order, activate and deactivate", 0x9ED8FF, null));
                continue;
            }
            if (!robot.contains("pins")) {
                lines.add(new HelpLine(name + " (far away)", null, "too far away to see its circuit board", OsDraw.GREY, null));
                continue;
            }
            StringBuilder modules = new StringBuilder();
            StringBuilder code = new StringBuilder();
            int count = 0;
            java.util.Set<String> used = new java.util.HashSet<>();
            net.minecraft.nbt.ListTag pins = robot.getList("pins", Tag.TAG_STRING);
            for (int pin = 1; pin <= pins.size(); pin++) {
                com.senne.imttech.bot.BotModule module = com.senne.imttech.bot.BotModule.byId(pins.getString(pin - 1));
                if (module == null || module == com.senne.imttech.bot.BotModule.BURNT) {
                    continue;
                }
                count++;
                modules.append(modules.isEmpty() ? "" : ", ").append(pin).append(' ').append(module.title().toLowerCase(java.util.Locale.ROOT));
                String pinName = module.pinName();
                for (int number = 2; !used.add(pinName); number++) {
                    pinName = module.pinName() + number;
                }
                code.append(pinName).append(" = pin(").append(pin).append(")\n");
            }
            // Past het niet op de regel, dan alleen hoeveel; de pinnen staan in de uitleg eronder.
            String label = name + ": " + (count == 0 ? "no modules" : modules);
            if (label.length() > 26) {
                label = name + ": " + count + (count == 1 ? " module" : " modules");
            }
            lines.add(new HelpLine(label, code.isEmpty() ? null : code.toString(),
                    code.isEmpty() ? "solder modules onto its circuit board first" : "pins " + modules + ". Click to put them in your code",
                    0x9ED8FF, null));
        }
        lines.add(header("EXAMPLES"));
        if (server) {
            lines.add(new HelpLine("Call a robot", CALL_EXAMPLE, "a robot says send(\"help\"): Guard 1 comes to it", 0xC9A0FF, null));
        } else {
            lines.add(drones ? new HelpLine("Patrol a square", DRONE_PATROL_EXAMPLE, "flies a square, and charges when its battery is low", 0xC9A0FF, null)
                    : new HelpLine("Patrol a square", PATROL_EXAMPLE, "walks a square, and charges when its battery is low", 0xC9A0FF, null));
            lines.add(new HelpLine("Come when called", COME_EXAMPLE, "goes where the server says (needs the Pathfinding module)",
                    0xC9A0FF, null));
        }
    }

    private static String quoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static HelpLine header(String text) {
        return new HelpLine(text, null, "", HudStyle.ORANGE, null);
    }

    /**
     * Wat een RC Car kent (hoofdstuk 119: zijn programma schrijf je voortaan hier, als RC program), per soort. Het zijn losse
     * woorden, geen kant-en-klare programma's (jouw wens bij de RC Car: "er mag geen voorbestaande code zijn"); voorbeelden
     * staan in How it works.
     */
    private List<HelpLine> carHelpLines() {
        List<HelpLine> lines = new ArrayList<>();
        lines.add(header("START WITH"));
        lines.add(new HelpLine("def name():", "def name():\n    ",
                "A function: a button on the car's screen and on the RC Controller. Give it your own name", 0xC9A0FF, null));
        lines.add(new HelpLine("when started:", "when started:\n    ", "The main program: it runs when you press Start on the car",
                HudStyle.CYAN, null));
        lines.add(new HelpLine("when ordered(\"fetch\"):", "when ordered(\"fetch\"):\n    ",
                "Someone ordered an item at the Storage Interface of this car: order.item, order.count, order.left", HudStyle.CYAN, null));
        lines.add(new HelpLine("when ordered(\"store\"):", "when ordered(\"store\"):\n    ",
                "Someone put items into the Storage Interface to store them", HudStyle.CYAN, null));
        lines.add(header("DRIVE"));
        lines.add(item("forward(3) / back(1)", "forward(1)", Op.FORWARD));
        lines.add(item("turn_left() / turn_right()", "turn_right()", Op.TURN_RIGHT));
        lines.add(header("MINE"));
        lines.add(item("mine()", "mine()", Op.MINE));
        lines.add(item("mine_up()", "mine_up()", Op.MINE_UP));
        lines.add(item("mine_down()", "mine_down()", Op.MINE_DOWN));
        lines.add(header("ARM (needs an RC Robot Arm)"));
        lines.add(item("arm_up() / arm_up(2)", "arm_up()", Op.ARM_UP));
        lines.add(item("arm_down() / arm_down(2)", "arm_down()", Op.ARM_DOWN));
        lines.add(header("BUILD"));
        lines.add(item("place() / place(\"...\")", "place()", Op.PLACE));
        lines.add(item("place_up()", "place_up()", Op.PLACE_UP));
        lines.add(item("place_down()", "place_down()", Op.PLACE_DOWN));
        lines.add(header("TRUNK AND CHESTS"));
        lines.add(item("collect()", "collect()", Op.COLLECT));
        lines.add(item("unload()", "unload()", Op.UNLOAD));
        lines.add(item("take(\"...\", 8)", "take(\"iron_ingot\", 8)", Op.TAKE));
        lines.add(item("put(\"...\", 8)", "put(\"iron_ingot\", 8)", Op.PUT));
        lines.add(item("check()", "check()", Op.CHECK));
        lines.add(new HelpLine("if ... in chest():", "if item(\"iron_ingot\") in chest():\n    ",
                "Is that item in the chest in front of the car (at the height of its arm)? in trunk() looks in its own trunk",
                HudStyle.WHITE, null));
        lines.add(header("THE CAR KNOWS"));
        for (Expr.Function function : Expr.Function.values()) {
            if (function.allowedIn(ProgramKind.CAR)) {
                String call = function.code() + (function.takesItem() ? "(\"iron_ingot\")" : "()");
                lines.add(new HelpLine(function.code() + (function.takesItem() ? "(\"...\")" : "()"), call, function.help(),
                        0xF1E87C, null));
            }
        }
        for (Expr.Variable variable : Expr.Variable.values()) {
            if (variable.allowedIn(ProgramKind.CAR)) {
                lines.add(new HelpLine(variable.code(), variable.code(), variable.help(), 0xF1E87C, null));
            }
        }
        lines.add(header("REPEAT AND CHECK"));
        lines.add(new HelpLine("for i in range(3):", "for i in range(3):\n    ", Op.FOR.help() + ". i is 0, 1, 2", 0xFF79C6, null));
        lines.add(new HelpLine("while ...:", "while car.battery > 10:\n    ", Op.WHILE.help(), 0xFF79C6, null));
        lines.add(new HelpLine("if ...:", "if car.blocked:\n    ", Op.IF.help(), 0xFF79C6, null));
        lines.add(new HelpLine("else: / elif ...:", "else:\n    ", "What happens when the if is not true", 0xFF79C6, null));
        lines.add(new HelpLine("break", "break", Op.BREAK.help(), 0xFF79C6, null));
        lines.add(header("OTHER"));
        lines.add(item("wait(2)", "wait(2)", Op.WAIT));
        lines.add(new HelpLine("say(\"...\")", "say(\"Done\")", "The car tells you something in the chat", 0x9ED8FF, null));
        lines.add(item("stop()", "stop()", Op.STOP));
        lines.add(new HelpLine("count = 0", "count = 0\n",
                "At the left edge: a name for the whole program. It keeps its value between jobs.", 0xF1E87C, null));
        lines.add(new HelpLine("def lane(int length):", "def lane(int length):\n    for i in range(length):\n        mine()\n        forward(1)\n",
                "A def with values it needs (parameters): a helper for your other steps, not a button. Use it with lane(5)",
                0xC9A0FF, null));
        lines.add(header("YOUR OWN BLOCKS (def)"));
        java.util.regex.Matcher defs = java.util.regex.Pattern.compile("(?m)^def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)")
                .matcher(editor.text());
        boolean any = false;
        while (defs.find()) {
            any = true;
            boolean takes = !defs.group(2).isBlank();
            lines.add(new HelpLine(defs.group(1) + "(" + defs.group(2).strip() + ")", defs.group(1) + "()",
                    takes ? "one of your helpers (needs: " + defs.group(2).strip() + ")" : "one of your functions: a button on the car",
                    0xC9A0FF, null));
        }
        if (!any) {
            lines.add(new HelpLine("(none yet)", null, "Write def name(): at the left edge, with the steps under it", OsDraw.GREY, null));
        }
        return lines;
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

    // ================================================================ Templates (hoofdstuk 125)

    /** De kleur van de knop en het paneel Templates: zeegroen, anders dan Help (oranje) en de knoppen links (cyaan). */
    private static final int TEMPLATE_COLOUR = 0x7FD4C1;
    /** Zo hoog is een regel in de lijst met templates. */
    private static final int TEMPLATE_ROW = 10;
    private static final String SAVE_TEMPLATE = "Save as template";

    /** De kleur van een template van deze soort: zoals de letter in de lijst links (R geel, D lichtblauw, C groen). */
    private static int kindColour(ProgramKind kind) {
        return switch (kind) {
            case ROBOT -> 0xF1E87C;
            case DRONE -> DRONE_COLOUR;
            case CAR -> CAR_COLOUR;
            default -> HudStyle.WHITE;
        };
    }

    /** Het kopje van het paneel: je ziet de templates van de soort programma die open is. */
    private String templateTitle() {
        return switch (kind) {
            case ROBOT -> "ROBOT TEMPLATES";
            case DRONE -> "DRONE TEMPLATES";
            case CAR -> "RC TEMPLATES";
            default -> "SERVER TEMPLATES";
        };
    }

    /** Waarmee we een vraag ("klik nog eens") aan één template koppelen. */
    private static String key(ScriptTemplates.Template template) {
        return template.kind().name() + ":" + template.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Hoeveel rijen er in de lijst van het paneel passen (zoveel tekent renderTemplates: elke rij 9 hoog, 10 uit elkaar). */
    private static int templateRows(int top, int bottom) {
        return Math.max(1, (bottom - top + 1) / TEMPLATE_ROW);
    }

    private static String lines(ScriptTemplates.Template template) {
        return template.lines() + (template.lines() == 1 ? " line" : " lines");
    }

    /** De knop Templates: het paneel open of dicht. In de blokjes gaan we eerst naar de code (een template is code). */
    private void toggleTemplates() {
        if (templatesOpen && mode == Mode.CODE) {
            templatesOpen = false;
            templateNameBox().setFocused(false);
            return;
        }
        if (mode == Mode.BLOCKS) {
            switchMode();
        }
        templatesOpen = true;
        helpOpen = false;
        renaming = null;
        templateScroll = 0;
        if (templateNameBox().getValue().isBlank() && current != null) {
            templateNameBox().setValue(current);
        }
    }

    /**
     * Het paneel Templates, rechts naast de code (waar ook Help staat):
     * <pre>
     *  ROBOT TEMPLATES
     *  [Patrol_____________]
     *  [Save as template]
     *  ---------------------
     *  Come when called   x
     *  Patrol             x
     *  (uitleg van wat onder de muis is)
     * </pre>
     */
    private void renderTemplates(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my, float partialTick) {
        OsDraw.field(g, x, y, x + w, y + h, false);
        g.drawString(font, templateTitle(), x + 4, y + 3, HudStyle.argb(255, TEMPLATE_COLOUR), false);
        // Het naamvak, met een grijs woordje erin zolang het leeg is.
        int fy = y + 14;
        EditBox box = templateNameBox();
        OsDraw.field(g, x + 3, fy, x + w - 3, fy + OsDraw.BUTTON_H, box.isFocused());
        box.setX(x + 6);
        box.setY(fy + 2);
        box.setWidth(w - 12);
        box.render(g, mx, my, partialTick);
        if (box.getValue().isEmpty() && !box.isFocused()) {
            g.drawString(font, "template name", x + 6, fy + 2, HudStyle.argb(255, 0x46606C), false);
        }
        int sy = fy + OsDraw.BUTTON_H + 3;
        boolean blank = ScriptTemplates.blank(editor.text());
        OsDraw.button(g, font, x + 3, sy, SAVE_TEMPLATE, !blank, TEMPLATE_COLOUR);
        int tipH = 20;
        int top = sy + OsDraw.BUTTON_H + 6;
        int bottom = y + h - tipH;
        g.fill(x + 3, top - 4, x + w - 3, top - 3, HudStyle.argb(255, OsDraw.DIM));
        List<ScriptTemplates.Template> list = templates().of(kind);
        // De uitleg onderaan heeft twee korte regels (het paneel is smal): lange zinnen zouden afgeknipt worden.
        String tip = list.isEmpty() ? "Kept on this PC, for every world."
                : blank ? "Click a template to load it. x deletes it." : "Click a template to replace your code. x deletes it.";
        if (OsDraw.inside(mx, my, x + 3, sy, OsDraw.buttonWidth(font, SAVE_TEMPLATE), OsDraw.BUTTON_H)) {
            tip = blank ? "Write some code first." : "Keep the code on the left as a template.";
        }
        if (list.isEmpty()) {
            // Nog geen templates: in de lijst zelf wat je moet doen (daar is plek voor meer regels).
            int ey = top;
            for (String line : OsDraw.wrap(font, "No " + kind.noun() + " templates yet. Write code on the left, type a name above,"
                    + " then press " + SAVE_TEMPLATE + ".", w - 8, Math.max(1, (bottom - top) / TEMPLATE_ROW))) {
                g.drawString(font, line, x + 4, ey, HudStyle.argb(255, OsDraw.GREY), false);
                ey += TEMPLATE_ROW;
            }
        }
        int rows = templateRows(top, bottom);
        templateScroll = Math.max(0, Math.min(templateScroll, Math.max(0, list.size() - rows)));
        int ly = top;
        g.enableScissor(x + 1, top - 1, x + w - 1, bottom);
        for (int i = templateScroll; i < list.size() && ly + 9 <= bottom; i++) {
            ScriptTemplates.Template template = list.get(i);
            boolean hover = mx >= x + 1 && mx < x + w - 1 && my >= ly - 1 && my < ly + 9;
            boolean onDelete = hover && mx >= x + w - 12;
            boolean asking = ("tdel:" + key(template)).equals(confirm) && Util.getMillis() < confirmUntil;
            if (hover) {
                g.fill(x + 1, ly - 1, x + w - 1, ly + 9, HudStyle.argb(255, 0x16303F));
                tip = onDelete ? "Delete " + template.name() + " (click x twice)"
                        : blank ? "Load " + template.name() + " (" + lines(template) + ")" : "Replace your code with " + template.name();
            }
            int room = w - 20 - (template.global() ? 10 : 0);
            g.drawString(font, OsDraw.fitRight(font, template.name(), room), x + 4, ly,
                    HudStyle.argb(255, kindColour(template.kind())), false);
            if (template.global()) {
                g.drawString(font, "G", x + w - 21, ly, HudStyle.argb(255, HudStyle.ORANGE), false);
            }
            g.drawString(font, "x", x + w - 9, ly, HudStyle.argb(255, asking || onDelete ? HudStyle.RED : OsDraw.GREY), false);
            ly += TEMPLATE_ROW;
        }
        g.disableScissor();
        // Onderaan: de uitleg van wat onder de muis is (over twee regels, zoals bij Help).
        List<String> tipLines = OsDraw.wrap(font, tip, w - 6, 2);
        for (int i = 0; i < tipLines.size(); i++) {
            g.drawString(font, tipLines.get(i), x + 3, y + h - tipH + 1 + i * 9, HudStyle.argb(255, OsDraw.GREY), false);
        }
    }

    private void clickTemplates(Font font, int x, int y, int w, int h, int mx, int my) {
        int fy = y + 14;
        if (OsDraw.inside(mx, my, x + 3, fy, w - 6, OsDraw.BUTTON_H)) {
            editor.setFocused(false);
            templateNameBox().setFocused(true);
            templateNameBox().mouseClicked(mx, my, 0);
            return;
        }
        int sy = fy + OsDraw.BUTTON_H + 3;
        if (OsDraw.inside(mx, my, x + 3, sy, OsDraw.buttonWidth(font, SAVE_TEMPLATE), OsDraw.BUTTON_H)) {
            saveTemplate();
            return;
        }
        int top = sy + OsDraw.BUTTON_H + 6;
        if (my < top - 1 || my >= y + h - 20) {
            return;
        }
        // Alleen een rij die getekend is (de nalezing: onder de laatste zichtbare rij laadde een klik de volgende).
        int row = (my - top + 1) / TEMPLATE_ROW;
        if (row >= templateRows(top, y + h - 20)) {
            return;
        }
        List<ScriptTemplates.Template> list = templates().of(kind);
        int index = templateScroll + row;
        if (index < 0 || index >= list.size()) {
            return;
        }
        if (mx >= x + w - 12) {
            deleteTemplate(list.get(index));
        } else {
            loadTemplate(list.get(index));
        }
    }

    /** Save as template (of Enter in het naamvak): de code links bewaren onder die naam, voor deze soort programma. */
    private void saveTemplate() {
        if (!open) {
            return;
        }
        String name = templateNameBox().getValue().strip();
        if (name.isEmpty()) {
            editor.setFocused(false);
            templateNameBox().setFocused(true);
            show("Type a name for the template first", HudStyle.ORANGE);
            return;
        }
        String code = editor.text();
        if (ScriptTemplates.blank(code)) {
            show("Nothing to save yet: write some code first", HudStyle.ORANGE);
            return;
        }
        ScriptTemplates.Template old = templates().find(kind, name);
        if (old != null && !ask("tsave:" + key(old), "That name exists: click again to replace the template", true)) {
            return;
        }
        ScriptTemplates.Result result = templates().save(name, kind, code);
        ScriptTemplates.Template saved = templates().find(kind, name);
        String shown = saved == null ? name : saved.name();
        switch (result) {
            case SAVED -> show("Saved template " + shown + " on this PC", HudStyle.GREEN);
            case REPLACED -> show("Replaced template " + shown, HudStyle.GREEN);
            case TOO_LONG -> show("Too long for a template: at most " + ScriptTemplates.MAX_CODE + " characters", HudStyle.RED);
            case FULL -> show("You have " + ScriptTemplates.MAX_TEMPLATES + " templates: delete one first (x)", HudStyle.RED);
            case NOT_WRITTEN -> show("Could not write the template file (not saved)", HudStyle.RED);
            default -> show("Not saved: a template needs a name and some code", HudStyle.ORANGE);
        }
        if (result == ScriptTemplates.Result.SAVED || result == ScriptTemplates.Result.REPLACED) {
            templateNameBox().setFocused(false);
        }
    }

    /**
     * Een template inladen. In een leeg programma (of een nieuw global script met alleen de regel global) meteen; staat er
     * al code, dan vraagt de eerste klik het, en vervangt de tweede je code (Ctrl+Z geeft hem terug).
     */
    private void loadTemplate(ScriptTemplates.Template template) {
        if (!open) {
            return;
        }
        if (mode == Mode.BLOCKS) {
            switchMode();
        }
        if (!ask("tload:" + key(template), "Click " + template.name() + " again to replace your code",
                !ScriptTemplates.blank(editor.text()))) {
            return;
        }
        editor.replaceAll(template.code());
        editor.setFocused(true);
        editor.setErrorLine(0);
        templateNameBox().setFocused(false);
        Script script = check(false);
        if (script != null) {
            int blocks = script.blocks();
            show("Loaded template " + template.name() + " (" + blocks + (blocks == 1 ? " block)" : " blocks)"), HudStyle.GREEN);
        }
    }

    private void deleteTemplate(ScriptTemplates.Template template) {
        if (!ask("tdel:" + key(template), "Click x again to delete " + template.name(), true)) {
            return;
        }
        if (templates().delete(template.kind(), template.name())) {
            show("Deleted template " + template.name(), HudStyle.ORANGE);
        } else {
            show("Could not delete " + template.name() + " (template file)", HudStyle.RED);
        }
    }

    /**
     * In een leeg programma: grijze uitleg onder de cursor, en je templates van deze soort om aan te klikken. Het staat
     * niet in je code: het verdwijnt zodra je iets typt, en wordt nooit bewaard. Klik je ernaast, dan typ je gewoon.
     */
    private void renderEmptyHint(GuiGraphics g, Font font, int ex, int ey, int ew, int eh, int mx, int my) {
        // Niet terwijl het lijstje van Ctrl+Spatie open is (de nalezing: dat lijstje ligt over de hint, en een klik erop
        // laadde dan een template in plaats van je keuze).
        if (!ScriptTemplates.blank(editor.text()) || editor.popupOpen()) {
            return;
        }
        int left = editor.textLeft();
        int width = ex + ew - 8 - left;
        int bottom = ey + eh - 2;
        int ly = editor.lineTop(editor.lineCount()) + CodeEditor.LINE_H / 2;
        if (width < 40 || ly < ey + 2 || ly + CodeEditor.LINE_H > bottom) {
            return;
        }
        int grey = HudStyle.argb(255, OsDraw.GREY);
        String intro = ScriptTemplates.startsGlobal(editor.text())
                ? "A global script: write defs and shared names (like lockdown: bool = false) for all your diagrams."
                : kind == ProgramKind.CAR ? "Empty. Write def name(): for a button on the car. Help shows what a car can do."
                : "Empty. Type your code here, or click Help to see what you can use.";
        g.enableScissor(ex + 1, ey + 1, ex + ew - 1, ey + eh - 1);
        for (String line : OsDraw.wrap(font, intro, width, 3)) {
            g.drawString(font, line, left, ly, grey, false);
            ly += CodeEditor.LINE_H;
        }
        ly += CodeEditor.LINE_H / 2;
        List<ScriptTemplates.Template> list = templates().of(kind);
        if (list.isEmpty()) {
            for (String line : OsDraw.wrap(font, "No " + kind.noun() + " templates yet. Code you use often: keep it with"
                    + " Templates (under your diagrams), then click it here in a new " + kind.noun() + ".", width, 4)) {
                g.drawString(font, line, left, ly, grey, false);
                ly += CodeEditor.LINE_H;
            }
        } else if (ly + 2 * CodeEditor.LINE_H <= bottom) {
            g.drawString(font, OsDraw.fitRight(font, "Your templates (click one to load it):", width), left, ly, grey, false);
            ly += CodeEditor.LINE_H;
            int room = (bottom - ly) / CodeEditor.LINE_H;
            int shown = list.size() <= room ? list.size() : Math.max(0, room - 1);
            for (int i = 0; i < shown; i++) {
                ScriptTemplates.Template template = list.get(i);
                if (OsDraw.inside(mx, my, left - 2, ly - 1, width + 4, CodeEditor.LINE_H)) {
                    g.fill(left - 2, ly - 1, left + width + 2, ly + CodeEditor.LINE_H - 1, HudStyle.argb(255, 0x16303F));
                    hoverHint = "Load template " + template.name() + " (" + lines(template) + ")";
                }
                String label = "> " + template.name() + (template.global() ? "  (global)" : "");
                g.drawString(font, OsDraw.fitRight(font, label, width), left, ly, HudStyle.argb(255, kindColour(kind)), false);
                hintRows.add(new HintRow(template, left - 2, ly - 1, width + 4));
                ly += CodeEditor.LINE_H;
            }
            if (shown < list.size() && room > 0) {
                g.drawString(font, OsDraw.fitRight(font, "+ " + (list.size() - shown) + " more under Templates (bottom left)", width),
                        left, ly, grey, false);
            }
        }
        g.disableScissor();
    }

    // ================================================================ Muis

    void mouseClicked(Font font, SecurityOsScreen.Area a, int mx, int my, int button) {
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
            } else if (OsDraw.inside(mx, my, bx, top + 15 + 2 * (OsDraw.BUTTON_H + 3), OsDraw.buttonWidth(font, "Drone program"), OsDraw.BUTTON_H)) {
                newDiagram(ProgramKind.DRONE);
            } else if (OsDraw.inside(mx, my, bx, top + 15 + 3 * (OsDraw.BUTTON_H + 3), OsDraw.buttonWidth(font, "RC program"), OsDraw.BUTTON_H)) {
                newDiagram(ProgramKind.CAR);
            } else if (OsDraw.inside(mx, my, bx, top + 15 + 4 * (OsDraw.BUTTON_H + 3), OsDraw.buttonWidth(font, "Global script"), OsDraw.BUTTON_H)) {
                newGlobalScript();
            } else if (OsDraw.inside(mx, my, bx, top + 15 + 5 * (OsDraw.BUTTON_H + 3), OsDraw.buttonWidth(font, "Cancel"), OsDraw.BUTTON_H)) {
                choosingKind = false;
            }
            if (mx >= a.x() && mx < a.x() + SIDE) {
                return;
            }
        }
        // Onderaan in de lijst: de knop Templates (hoofdstuk 125).
        if (!choosingKind && templatesButtonShown()
                && OsDraw.inside(mx, my, a.x() + 3, templatesButtonY(a), OsDraw.buttonWidth(font, "Templates"), OsDraw.BUTTON_H)) {
            toggleTemplates();
            return;
        }
        if (mx >= a.x() && mx < a.x() + SIDE && my >= top) {
            // Alleen een rij die getekend is (de nalezing: naast en boven de knop Templates opende een klik een diagram dat
            // daar niet stond). renderSidebar tekent een rij als hij helemaal boven listBottom past.
            int listBottom = templatesButtonShown() ? templatesButtonY(a) - 2 : a.y() + a.h() - 34;
            int row = (my - top - 1) / ROW;
            int index = sideScroll + row;
            List<SideRow> rows = sideRows();
            if (my > top && top + 3 + (row + 1) * ROW <= listBottom && index >= 0 && index < rows.size()) {
                clickSideRow(rows.get(index));
            }
            return;
        }
        // Hoofdstuk 130: de pagina's van de SD-kaart (hun knoppen).
        if (page != Page.EDITOR && cardIn) {
            for (CardHit hit : List.copyOf(cardHits)) {
                if (OsDraw.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.action().run();
                    return;
                }
            }
            if (page == Page.CARD_PROGRAM) {
                cardPreview.mouseClicked(mx, my, button);
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
        boolean inName = OsDraw.inside(mx, my, bx, a.y(), nameW, OsDraw.BUTTON_H) && !currentLocked();
        box.setFocused(inName);
        if (templateNameBox != null) {
            templateNameBox.setFocused(false); // weer aan als je in het naamvak van Templates klikt (clickTemplates)
        }
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
        if (currentLocked()) {
            return; // hoofdstuk 130: geen code om in te klikken
        }
        if (mode == Mode.BLOCKS) {
            canvas.mouseClicked(font, mx, my, button);
            return;
        }
        int helpW = OsDraw.buttonWidth(font, "Help");
        if (OsDraw.inside(mx, my, a.x() + a.w() - helpW, a.y(), helpW, OsDraw.BUTTON_H)) {
            helpOpen = !helpOpen;
            templatesOpen = false; // Help en Templates delen het paneel rechts (hoofdstuk 125)
            renaming = null;
            return;
        }
        // Het paneel rechts: Help of Templates.
        int width = a.x() + a.w() - x0;
        if (helpOpen && mx >= x0 + width - HELP_W) {
            clickHelp(font, x0 + width - HELP_W, a.y() + TOOLBAR, HELP_W, a.h() - TOOLBAR - STATUS, mx, my);
            return;
        }
        if (templatesOpen && mx >= x0 + width - HELP_W) {
            clickTemplates(font, x0 + width - HELP_W, a.y() + TOOLBAR, HELP_W, a.h() - TOOLBAR - STATUS, mx, my);
            return;
        }
        // In een leeg programma: een template uit de hint aanklikken laadt hem (hoofdstuk 125). Is het lijstje van
        // Ctrl+Spatie open, dan krijgt dat de klik.
        if (!editor.popupOpen()) {
            for (HintRow row : hintRows) {
                if (OsDraw.inside(mx, my, row.x(), row.y(), row.w(), CodeEditor.LINE_H)) {
                    loadTemplate(row.template());
                    return;
                }
            }
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
        if (page == Page.CARD_PROGRAM && cardIn) {
            return cardPreview.mouseDragged(mx, my);
        }
        if (page != Page.EDITOR && cardIn || currentLocked()) {
            return false;
        }
        return mode == Mode.BLOCKS ? canvas.mouseDragged(mx, my) : editor.mouseDragged(mx, my);
    }

    void mouseReleased(int mx, int my) {
        if (page == Page.CARD_PROGRAM && cardIn) {
            cardPreview.mouseReleased();
            return;
        }
        if (mode == Mode.BLOCKS) {
            canvas.mouseReleased(mx, my);
        } else {
            editor.mouseReleased();
        }
    }

    void mouseScrolled(SecurityOsScreen.Area a, int mx, int my, double scrollY) {
        if (mx < a.x() + SIDE) {
            sideScroll = Math.max(0, sideScroll - (int) Math.signum(scrollY));
        } else if (page == Page.CARD && cardIn) {
            cardScroll = Math.max(0, cardScroll - (int) Math.signum(scrollY));
        } else if (page == Page.CARD_PROGRAM && cardIn) {
            cardPreview.mouseScrolled(scrollY);
        } else if (currentLocked()) {
            return;
        } else if (mode == Mode.BLOCKS) {
            canvas.mouseScrolled(mx, my, scrollY, Screen.hasShiftDown(), Screen.hasControlDown());
        } else if (helpOpen && mx >= a.x() + a.w() - HELP_W) {
            helpScroll = Math.max(0, helpScroll - (int) Math.signum(scrollY) * 2);
        } else if (templatesOpen && mx >= a.x() + a.w() - HELP_W) {
            templateScroll = Math.max(0, templateScroll - (int) Math.signum(scrollY));
        } else {
            editor.mouseScrolled(scrollY);
        }
    }

    // ================================================================ Toetsen

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (page == Page.CARD_PROGRAM && cardIn) {
            // Hoofdstuk 130: de code van een open programma op de kaart: alleen lezen (Ctrl+A en Ctrl+C werken).
            return cardPreview.keyPressed(keyCode, scanCode, modifiers);
        }
        if (page != Page.EDITOR && cardIn) {
            return false;
        }
        if (currentLocked()) {
            return false; // hoofdstuk 130: bij een programma op slot staat er geen tekstvak en geen code
        }
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
        if (templateNameBox != null && templateNameBox.isFocused()) {
            // Het naamvak van Templates (hoofdstuk 125): Enter bewaart de code als template, Esc laat het.
            if (keyCode == 257 || keyCode == 335) {
                saveTemplate();
            } else if (keyCode == 256) {
                templateNameBox.setFocused(false);
            } else {
                templateNameBox.keyPressed(keyCode, scanCode, modifiers);
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
        if (currentLocked()) {
            return false; // hoofdstuk 130: geen code om in te typen
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
        if (page != Page.EDITOR && cardIn || currentLocked()) {
            return false; // hoofdstuk 130: op de pagina's van de kaart en bij een programma op slot typ je niets
        }
        if (renaming != null && renameBox().isFocused()) {
            renameBox().charTyped(codePoint, modifiers);
            return true;
        }
        if (nameBox().isFocused()) {
            nameBox().charTyped(codePoint, modifiers);
            return true;
        }
        if (templateNameBox != null && templateNameBox.isFocused()) {
            templateNameBox.charTyped(codePoint, modifiers);
            return true;
        }
        return mode == Mode.BLOCKS ? canvas.charTyped(codePoint, modifiers) : editor.charTyped(codePoint, modifiers);
    }

    void blur() {
        editor.setFocused(false);
        cardPreview.setFocused(false);
        canvas.blur();
        nameBox().setFocused(false);
        if (renameBox != null) {
            renameBox.setFocused(false);
        }
        if (templateNameBox != null) {
            templateNameBox.setFocused(false);
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

    /** Alleen voor de screenshot-rondleiding: na + de keuze tussen een diagram op de server, een robot- en een droneprogramma. */
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
        templatesOpen = false;
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 125): een nieuw (leeg) programma, zonder Help of Templates. */
    void tourNewEmpty(ProgramKind newKind) {
        open = false;
        newDiagram(newKind);
        if (mode == Mode.BLOCKS) {
            switchMode();
        }
        helpOpen = false;
        templatesOpen = false;
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 125): + > Global script, zonder Help of Templates. */
    void tourNewGlobal() {
        open = false;
        newGlobalScript();
        if (mode == Mode.BLOCKS) {
            switchMode();
        }
        helpOpen = false;
        templatesOpen = false;
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 125): het paneel Templates open of dicht, en deze naam erin. */
    void tourTemplates(boolean show, @Nullable String name) {
        if (show != (templatesOpen && mode == Mode.CODE)) {
            toggleTemplates();
        }
        if (show) {
            helpOpen = false;
        }
        if (name != null) {
            templateNameBox().setValue(name);
        }
    }

    /** Alleen voor de screenshot-rondleiding: de knop Save as template. */
    void tourSaveTemplate() {
        saveTemplate();
    }

    /** Alleen voor de screenshot-rondleiding: de template met deze naam aanklikken (zoals in de hint of het paneel). */
    void tourLoadTemplate(String name) {
        ScriptTemplates.Template template = templates().find(kind, name);
        if (template != null) {
            loadTemplate(template);
        }
    }

    /** Alleen voor de screenshot-rondleiding: een template van de rondleiding weer weg, zonder te vragen. */
    void tourForgetTemplate(ProgramKind templateKind, String name) {
        templates().delete(templateKind, name);
    }

    /** Alleen voor de screenshot-rondleiding: scroll de Help tot het kopje met deze naam bovenaan staat. */
    void tourHelpAt(String header) {
        List<HelpLine> lines = helpLines();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).label().equals(header)) {
                helpScroll = i;
                return;
            }
        }
    }

    /** Alleen voor de screenshot-rondleiding: open het bewaarde programma met deze naam (in de code, zonder Help). */
    void tourOpen(String name) {
        Entry entry = find(name);
        if (entry != null) {
            if (mode == Mode.BLOCKS) {
                switchMode();
            }
            helpOpen = false;
            openEntry(entry, false);
        }
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 130): de pagina van de SD-kaart. */
    void tourCard() {
        openCardPage();
    }

    /** Alleen voor de screenshot-rondleiding: een programma op de SD-kaart (een open programma vraagt zijn code). */
    void tourCardProgram(String name) {
        CardProgram program = cardProgram(name);
        if (program != null) {
            openCardProgram(program);
        }
    }

    /** Alleen voor de screenshot-rondleiding: terug naar de editor (bij het programma dat open stond). */
    void tourEditor() {
        page = Page.EDITOR;
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
        if (help) {
            templatesOpen = false;
        }
        check(true);
    }
}
