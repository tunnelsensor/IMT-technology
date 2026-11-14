package com.senne.imttech.client.os;

import com.senne.imttech.car.CarProgram;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.logic.program.Expr;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.Op;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.network.RcCarActionPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * De code-editor van een RC Car (hoofdstuk 108, jouw wens: "robots programmeren zoals in Education Edition"). Dezelfde
 * editor als in de app Logic (kleuren, Ctrl+Spatie, ongedaan maken), maar voor het programma van één auto, en zonder
 * server: je opent hem op de auto zelf (sneak + rechtsklik, Program) of met de RC Controller.
 *
 * <pre>
 *  RC Car program                                  each def is a button
 *  ┌──────────────────────────────────────────┐ ┌───────────────────┐
 *  │ def strip_mine():                        │ │ DRIVE             │
 *  │     for i in range(20):                  │ │ forward(1)        │
 *  │         mine()                           │ │ back(1)           │
 *  │         forward(1)                       │ │ turn_left()       │
 *  │         mine_up()                        │ │ MINE              │
 *  │                                          │ │ mine()            │
 *  └──────────────────────────────────────────┘ └───────────────────┘
 *  No problems: 2 functions                     [Check] [Save] [Cancel]
 * </pre>
 * Rechts staat alles wat de auto kent, per soort: klik op een regel en hij staat in je code (onderaan lees je wat hij
 * doet). Het zijn losse woorden, geen kant-en-klare programma's (jouw wens: "ik wil dat je de rc car vanaf 0 moet
 * programmeren; er mag geen voorbestaande code zijn"): voorbeelden staan in How it works. Een nieuwe auto heeft een
 * lege editor; die zegt dan in grijze letters hoe je begint (dat is geen code). Save stuurt de code naar de server; die kijkt hem nog eens na en bewaart hem alleen als hij klopt. Esc sluit
 * zonder te bewaren (heb je iets veranderd, dan vraagt hij het eerst nog eens).
 */
public class RcCarCodeScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int HELP_W = 132;
    private static final int BUTTON_W = 48;
    /** Onder het paneel: vier regels uitleg over wat onder de muis is. */
    private static final int TIP_H = 40;

    /** Eén regel in het paneel rechts: een kopje, of iets dat je kunt invoegen (met uitleg). */
    private record HelpLine(String label, @Nullable String insert, String tip, int colour) {}

    private final int entityId;
    private final String owner;
    private final boolean remote;
    private final String original;
    private final CodeEditor editor;
    private final List<HelpLine> help = helpLines();
    private int helpScroll;
    private String status = "";
    private boolean statusError;
    /** De regel onderaan is alleen een mededeling ("No function yet"): grijsblauw, niet groen of rood. */
    private boolean statusNote;
    /** Esc één keer gedrukt terwijl er iets veranderd is: de tweede keer sluit hij echt. */
    private boolean closeAsked;
    /** Alleen voor de screenshot-rondleiding: deze regel van het paneel rechts doet alsof de muis erop staat (-1 = niet). */
    private int tourHover = -1;
    private int left;
    private int top;
    private int panelW;
    private int panelH;

    public RcCarCodeScreen(int entityId, String owner, boolean remote, String source) {
        super(Component.literal("RC Car program"));
        this.entityId = entityId;
        this.owner = owner;
        this.remote = remote;
        this.original = source.replace("\r", "");
        this.editor = new CodeEditor(Minecraft.getInstance().font);
        editor.setCompletionData(() -> new CodeCompletion.Data(List.of(), List.of(), List.of(), ProgramKind.CAR));
        editor.setText(original);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 20, 560);
        panelH = Math.min(height - 20, 320);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        editor.setBounds(left + MARGIN, top + 22, panelW - 3 * MARGIN - HELP_W, panelH - 22 - 32);
        editor.setFocused(true);
        int y = top + panelH - 26;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> back())
                .bounds(left + panelW - MARGIN - BUTTON_W, y, BUTTON_W, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(left + panelW - MARGIN - 2 * BUTTON_W - 4, y, BUTTON_W, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Check"), b -> check())
                .bounds(left + panelW - MARGIN - 3 * BUTTON_W - 8, y, BUTTON_W, 20).build());
        check();
    }

    /** Klopt de code? Dan "No problems" en hoeveel functies hij heeft, anders de fout met zijn regel (die kleurt rood). */
    private boolean check() {
        try {
            LogicProgram program = CarProgram.compile(editor.text());
            int functions = CarProgram.functions(program).size();
            editor.setErrorLine(0);
            boolean main = CarProgram.hasMain(program);
            // Nog niets om te starten (een lege editor, of alleen uitleg): dat is geen fout, maar zeg het wel.
            statusNote = functions == 0 && !main;
            status = statusNote ? "No function yet: write def name():"
                    : "No problems: " + functions + (functions == 1 ? " function" : " functions") + (main ? " + Start" : "");
            statusError = false;
            return true;
        } catch (LogicException e) {
            editor.setErrorLine(e.line());
            status = e.describe();
            statusError = true;
            statusNote = false;
            return false;
        }
    }

    private void save() {
        if (!check()) {
            return; // eerst de fout oplossen
        }
        PacketDistributor.sendToServer(new RcCarActionPayload(entityId, RcCarActionPayload.Op.SAVE, editor.text()));
        back();
    }

    /** Terug naar het scherm van de auto. */
    private void back() {
        minecraft.setScreen(new com.senne.imttech.client.car.RcCarScreen(entityId, owner, remote));
    }

    private boolean changed() {
        return !editor.text().equals(original);
    }

    // ---------------------------------------------------------------- Het paneel rechts

    private int helpX() {
        return left + panelW - MARGIN - HELP_W;
    }

    private int helpY() {
        return top + 22;
    }

    private int helpH() {
        return panelH - 22 - 32;
    }

    private static HelpLine header(String text) {
        return new HelpLine(text, null, "", HudStyle.ORANGE);
    }

    private static HelpLine op(Op op) {
        String code = op.example().replace("|", "");
        return new HelpLine(code, code, op.help(), 0x9ED8FF);
    }

    /** Alles wat een RC Car kent, per soort. */
    private static List<HelpLine> helpLines() {
        List<HelpLine> lines = new ArrayList<>();
        lines.add(header("START"));
        lines.add(new HelpLine("def name():", "def name():\n    ",
                "A function: a button on the car's screen and on the RC Controller. Give it your own name", CodeEditor.FUNCTION));
        lines.add(new HelpLine("when started:", "when started:\n    ",
                "The main program: it runs when you press Start", HudStyle.CYAN));
        lines.add(header("DRIVE"));
        lines.add(op(Op.FORWARD));
        lines.add(op(Op.BACK));
        lines.add(op(Op.TURN_LEFT));
        lines.add(op(Op.TURN_RIGHT));
        lines.add(header("MINE"));
        lines.add(op(Op.MINE));
        lines.add(op(Op.MINE_UP));
        lines.add(op(Op.MINE_DOWN));
        lines.add(header("BUILD"));
        lines.add(new HelpLine("place()", "place()", Op.PLACE.help(), 0x9ED8FF));
        lines.add(new HelpLine("place_up()", "place_up()", Op.PLACE_UP.help(), 0x9ED8FF));
        lines.add(new HelpLine("place_down()", "place_down()", Op.PLACE_DOWN.help(), 0x9ED8FF));
        lines.add(header("TRUNK"));
        lines.add(op(Op.COLLECT));
        lines.add(op(Op.UNLOAD));
        lines.add(header("REPEAT AND CHECK"));
        lines.add(new HelpLine("for i in range(3):", "for i in range(3):\n    ", Op.FOR.help() + ". i is 0, 1, 2", 0xFF79C6));
        lines.add(new HelpLine("while ...:", "while car.battery > 10:\n    ", Op.WHILE.help(), 0xFF79C6));
        lines.add(new HelpLine("if ...:", "if car.blocked:\n    ", Op.IF.help(), 0xFF79C6));
        lines.add(new HelpLine("else:", "else:\n    ", "What happens when the if above is not true", 0xFF79C6));
        lines.add(new HelpLine("break", "break", Op.BREAK.help(), 0xFF79C6));
        lines.add(header("THE CAR KNOWS"));
        for (Expr.Function function : Expr.Function.values()) {
            if (function.allowedIn(ProgramKind.CAR)) {
                lines.add(new HelpLine(function.code() + "()", function.code() + "()", function.help(), 0xF1E87C));
            }
        }
        for (Expr.Variable variable : Expr.Variable.values()) {
            if (variable.allowedIn(ProgramKind.CAR)) {
                lines.add(new HelpLine(variable.code(), variable.code(), variable.help(), 0xF1E87C));
            }
        }
        lines.add(header("OTHER"));
        lines.add(op(Op.WAIT));
        lines.add(new HelpLine("say(\"...\")", "say(\"Done\")", "The car tells you something in the chat", 0x9ED8FF));
        lines.add(op(Op.STOP));
        lines.add(new HelpLine("count = 0", "count = 0", Op.SET.help(), 0xF1E87C));
        return lines;
    }

    /** De regel van het paneel onder de muis, of -1. */
    private int helpLineAt(int mx, int my) {
        int x = helpX();
        int y = helpY();
        if (mx < x || mx >= x + HELP_W || my < y + 2 || my >= y + helpH() - TIP_H) {
            return -1;
        }
        int index = helpScroll + (my - y - 2) / 10;
        return index >= 0 && index < help.size() ? index : -1;
    }

    private void renderHelp(GuiGraphics g, int mx, int my) {
        int x = helpX();
        int y = helpY();
        int h = helpH();
        OsDraw.field(g, x, y, x + HELP_W, y + h, false);
        int rows = Math.max(1, (h - TIP_H - 4) / 10);
        helpScroll = Math.max(0, Math.min(helpScroll, Math.max(0, help.size() - rows)));
        int hover = tourHover >= 0 ? tourHover : helpLineAt(mx, my);
        String tip = "Click a line to put it in your code.";
        int ly = y + 3;
        g.enableScissor(x + 1, y + 1, x + HELP_W - 1, y + h - TIP_H);
        for (int i = helpScroll; i < help.size() && i < helpScroll + rows; i++) {
            HelpLine line = help.get(i);
            if (i == hover && line.insert() != null) {
                g.fill(x + 1, ly - 1, x + HELP_W - 1, ly + 9, HudStyle.argb(255, 0x16303F));
                tip = line.tip();
            }
            g.drawString(font, OsDraw.fitRight(font, line.label(), HELP_W - 8), x + (line.insert() == null ? 4 : 8), ly,
                    HudStyle.argb(255, line.colour()), false);
            ly += 10;
        }
        g.disableScissor();
        // Meer boven of onder? Een pijltje in de hoek.
        if (helpScroll > 0) {
            g.drawString(font, "▲", x + HELP_W - 9, y + 3, HudStyle.argb(255, OsDraw.GREY), false);
        }
        if (helpScroll + rows < help.size()) {
            g.drawString(font, "▼", x + HELP_W - 9, y + h - TIP_H - 10, HudStyle.argb(255, OsDraw.GREY), false);
        }
        // Onderaan: de uitleg van wat onder de muis is (over twee regels).
        List<net.minecraft.util.FormattedCharSequence> tipLines = font.split(Component.literal(tip), HELP_W - 6);
        for (int i = 0; i < Math.min(4, tipLines.size()); i++) {
            g.drawString(font, tipLines.get(i), x + 3, y + h - TIP_H + 2 + i * 9, HudStyle.argb(255, OsDraw.GREY), false);
        }
    }

    /** Wat een lege editor in grijze letters zegt (geen code: het verdwijnt zodra je iets typt). */
    private static final String[] EMPTY_HINT = {
            "Your RC Car has no program yet:",
            "you write it yourself. For example:",
            "",
            "def my_tunnel():",
            "    mine()",
            "    forward(1)",
            "",
            "def makes a function: a button on the",
            "car and on the RC Controller. The steps",
            "under it start with four spaces (Tab).",
            "",
            "Ctrl+Space, or the list on the right,",
            "shows everything the car knows.",
            "More examples: hold [%s] on the RC Car",
            "in your inventory (How it works).",
    };

    /**
     * De uitleg in een lege editor, vanaf de tweede regel (op de eerste staat je cursor), zo breed als de editor. In het
     * kleinste venster (240 hoog) passen de vijftien regels precies; regels die niet meer passen vallen weg.
     */
    private void renderEmptyHint(GuiGraphics g) {
        // Waar de code begint: na de regelnummers (zie CodeEditor.render).
        int x = left + MARGIN + font.width("99") + 12;
        int y = top + 22 + 2 + CodeEditor.LINE_H;
        int width = left + panelW - 2 * MARGIN - HELP_W - 6 - x;
        int bottom = top + 22 + panelH - 22 - 32 - 4;
        String key = com.senne.imttech.client.ImtKeys.HOW_IT_WORKS.getTranslatedKeyMessage().getString();
        for (String line : EMPTY_HINT) {
            if (y + 9 > bottom) {
                break;
            }
            g.drawString(font, OsDraw.fitRight(font, line.replace("%s", key), width), x, y, HudStyle.argb(255, 0x56707C), false);
            y += CodeEditor.LINE_H;
        }
    }

    // ---------------------------------------------------------------- Alleen voor de screenshot-rondleiding

    /** Een nieuwe regel onder mine_up() van strip_mine, en daar Ctrl+Spatie: het lijstje met wat de auto kent. */
    public void tourCompletion() {
        editor.tourCursor("mine_up()", "mine_up()");
        editor.insert("\n");
        editor.openCompletion();
    }

    /** Doet alsof de muis op deze regel van het paneel rechts staat (de uitleg onderaan hoort erbij). */
    public void tourHelp(int line) {
        if (editor.popupOpen()) {
            editor.keyPressed(256, 0, 0); // Esc: het lijstje van Ctrl+Spatie dicht
        }
        tourHover = line;
    }

    // ---------------------------------------------------------------- Toetsen en muis naar de editor

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256) { // Esc
            if (editor.popupOpen()) {
                return editor.keyPressed(key, scan, modifiers);
            }
            if (changed() && !closeAsked) {
                closeAsked = true;
                status = "Not saved yet: press Esc again to close without saving";
                statusError = true;
                statusNote = false;
                return true;
            }
            back();
            return true;
        }
        closeAsked = false;
        if (editor.keyPressed(key, scan, modifiers)) {
            editor.setErrorLine(0);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        closeAsked = false;
        return editor.charTyped(c, modifiers) || super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int line = helpLineAt((int) mx, (int) my);
        if (line >= 0) {
            String insert = help.get(line).insert();
            if (insert != null) {
                editor.insert(insert);
                editor.setFocused(true);
                editor.setErrorLine(0);
            }
            return true;
        }
        return editor.mouseClicked((int) mx, (int) my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return editor.mouseDragged((int) mx, (int) my) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        editor.mouseReleased();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= helpX() && mx < helpX() + HELP_W && my >= helpY() && my < helpY() + helpH()) {
            helpScroll -= (int) Math.signum(scrollY) * 3;
        } else {
            editor.mouseScrolled(scrollY);
        }
        return true;
    }

    // ---------------------------------------------------------------- Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + panelW + 4, top + panelH + 4, HudStyle.argb(245, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + panelW + 4, top + panelH + 4, 8, HudStyle.argb(220, HudStyle.ORANGE));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);
        String title = "RC Car program";
        g.drawString(font, title, left + MARGIN, top + 7, HudStyle.argb(255, HudStyle.ORANGE), true);
        String hint = "Each def is a button on the car and the RC Controller";
        int room = panelW - 2 * MARGIN - font.width(title) - 10;
        String shown = OsDraw.fitRight(font, hint, room);
        g.drawString(font, shown, left + panelW - MARGIN - font.width(shown), top + 7, HudStyle.argb(180, 0x9FC9D6), false);
        editor.render(g, mouseX, mouseY);
        if (editor.text().isEmpty()) {
            renderEmptyHint(g);
        }
        renderHelp(g, mouseX, mouseY);
        g.drawString(font, OsDraw.fitRight(font, status, panelW - 2 * MARGIN - 3 * BUTTON_W - 14), left + MARGIN, top + panelH - 20,
                HudStyle.argb(255, statusError ? HudStyle.RED : statusNote ? 0x9FC9D6 : HudStyle.GREEN), false);
    }
}
