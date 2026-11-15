package com.senne.imttech.logic.program;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Leest de code van een diagram (jouw keuze: "zoals Python") en maakt er een {@link Script} van.
 * <pre>
 * when keycard("Lab door"):
 *     if card.in_group("lab"):
 *         accept()
 *         pulse("Vault door", 3)
 *     elif card.valid:
 *         say("Someone with a low card tried the lab")
 *     else:
 *         deny()
 *         alarm("Wrong card at the lab")
 * </pre>
 * Net als in Python bepaalt het INSPRINGEN wat waarbij hoort: alles wat verder naar rechts staat dan
 * een regel met een dubbele punt, hoort bij die regel. Een tab telt als 4 spaties. Een lege regel en
 * commentaar (vanaf #) tellen niet mee.
 *
 * Je eigen blokken (functies) maak je met {@code def naam():} aan de linkerkant, met de stappen
 * eronder, en je gebruikt ze in een when (of een andere def) met {@code naam()}. Een def mag ook
 * onder de when staan die hem gebruikt: we zoeken eerst alle namen op.
 *
 * Bij de eerste fout stopt hij met een {@link LogicException}: het regelnummer en uitleg in
 * begrijpelijk Engels ("Line 3: on needs the name of a device between quotes...").
 */
public final class ScriptParser {
    /** Eén regel code: zijn nummer (vanaf 1), hoever hij inspringt, en zijn stukjes. */
    private record Line(int number, int indent, List<Lexer.Token> tokens) {
        Lexer.Token first() {
            return tokens.getFirst();
        }
    }

    private final List<Line> lines = new ArrayList<>();
    private int index;
    /** De namen van alle defs in deze code (eerst opgezocht, zodat je ze overal kunt gebruiken). */
    private final java.util.Set<String> functionNames = new java.util.LinkedHashSet<>();
    /** Wat voor programma (hoofdstuk 23): een woord dat hier niet bestaat, zeggen we meteen. */
    private final ProgramKind kind;
    /** De defs en gedeelde namen van de global scripts (hoofdstuk 43): die mag je hier ook gebruiken. */
    private final Library library;
    /** Begint de code met de regel {@code global}? Dan is dit een global script (hoofdstuk 43). */
    private boolean global;

    private ScriptParser(String source, ProgramKind kind, Library library) {
        this.kind = kind;
        this.library = library;
        String[] raw = source.replace("\r", "").split("\n", -1);
        for (int i = 0; i < raw.length; i++) {
            String text = raw[i].replace("\t", "    ");
            int indent = 0;
            while (indent < text.length() && text.charAt(indent) == ' ') {
                indent++;
            }
            List<Lexer.Token> tokens;
            try {
                tokens = Lexer.tokens(text);
            } catch (LogicException e) {
                throw e.atLine(i + 1);
            }
            if (tokens.getFirst().type() != Lexer.Type.END) {
                // "global" als eerste regel code (hoofdstuk 43): een global script. Verderop is het een fout (zie script()).
                if (lines.isEmpty() && !global && indent == 0 && tokens.getFirst().is("global")
                        && tokens.get(1).type() == Lexer.Type.END) {
                    global = true;
                    continue;
                }
                lines.add(new Line(i + 1, indent, tokens));
                if (indent == 0 && tokens.getFirst().is("def") && tokens.get(1).type() == Lexer.Type.NAME) {
                    functionNames.add(tokens.get(1).text());
                }
            }
        }
    }

    /** Leest de code van een diagram op de server. Gooit een LogicException (met regelnummer) bij de eerste fout. */
    public static Script parse(String source) {
        return parse(source, ProgramKind.SERVER);
    }

    /** Leest de code van deze soort programma (hoofdstuk 23: een robotprogramma kent andere woorden). */
    public static Script parse(String source, ProgramKind kind) {
        return parse(source, kind, Library.EMPTY);
    }

    /**
     * Leest de code met de defs en gedeelde namen van de global scripts erbij ({@code library}, hoofdstuk 43): die mag
     * de code gebruiken alsof ze van hemzelf zijn.
     */
    public static Script parse(String source, ProgramKind kind, Library library) {
        Script script = new ScriptParser(source, kind, library).script();
        // Dan wat over meerdere regels gaat (defs): zelfde controles als bij de blokjes.
        List<ScriptChecker.Problem> problems = ScriptChecker.check(script, kind, library);
        if (!problems.isEmpty()) {
            throw new LogicException(problems.getFirst().ref(), problems.getFirst().message());
        }
        return script;
    }

    /** Is dit de naam van een def: een eigen def, of een uit een global script? */
    private boolean isDef(String name) {
        return functionNames.contains(name) || library.def(name) != null;
    }

    // ================================================================ when ...:

    private Script script() {
        List<Script.Top> items = new ArrayList<>();
        while (index < lines.size()) {
            Line line = lines.get(index);
            if (line.indent() != 0) {
                throw new LogicException(line.number(), "This line is indented, but there is no when or def above it");
            }
            Lexer.Token first = line.first();
            if (first.is("def")) {
                items.add(function(line));
                continue;
            }
            if (first.is("global")) {
                throw new LogicException(line.number(), line.tokens().get(1).type() == Lexer.Type.END
                        ? "global only works as the very first line of the diagram (above everything else)"
                        : "global stands alone on the first line: it makes this a global script with only defs and shared names");
            }
            if (isAssignment(line)) {
                // count = 0 helemaal links: een naam voor het hele programma. Met een soort-woord (hoofdstuk 43):
                // count: int = 0.
                String[] parts = assignment(line);
                items.add(new Script.Global(parts[0], parts[1], parts[2], line.number()));
                continue;
            }
            if (!first.is("when")) {
                Op op = first.type() == Lexer.Type.NAME ? Op.byCode(first.text()) : null;
                boolean own = first.type() == Lexer.Type.NAME && isDef(first.text());
                if (own || op != null && !op.isTrigger() && op != Op.IF) {
                    boolean keyword = op != null && (op.kind() == Op.Kind.LOOP || op == Op.BREAK);
                    throw new LogicException(line.number(), first.text() + (keyword ? "" : "()")
                            + " must be inside a when: indent it with 4 spaces under a line like " + exampleWhen());
                }
                if (first.type() == Lexer.Type.NAME && line.tokens().get(1).is(":") && line.tokens().get(2).type() == Lexer.Type.NAME
                        && VarType.byWord(line.tokens().get(2).text()) == null) {
                    throw new LogicException(line.number(), "I don't know the type \"" + line.tokens().get(2).text() + "\": use "
                            + VarType.words() + ", like " + first.text() + ": int = 0");
                }
                throw new LogicException(line.number(), "Every " + kind.noun() + " starts with when (or def), like: " + exampleWhen()
                        + " (a name with a value, like count = 0, may also stand at the left edge)");
            }
            if (global) {
                throw new LogicException(line.number(), "A global script holds only defs and shared names (like lockdown: bool = false):"
                        + " put this when in another diagram");
            }
            // when started: (hoofdstuk 23) heeft geen haakjes: er hoort niets bij.
            Call call = line.tokens().get(1).type() == Lexer.Type.NAME && !line.tokens().get(2).is("(")
                    ? new Call(line.tokens().get(1).text(), List.of(), 2) : call(line, 1);
            Op trigger = Op.byCode(call.name());
            if (trigger == null || !trigger.isTrigger()) {
                String hint = functionNames.contains(call.name())
                        ? call.name() + " is your own block, not a trigger. A when starts with one of: " + triggers()
                        : trigger == null ? unknown(call.name())
                        : call.name() + " is not a trigger. A when starts with one of: " + triggers();
                throw new LogicException(line.number(), hint);
            }
            if (!trigger.allowedIn(kind)) {
                throw new LogicException(line.number(), ScriptChecker.wrongKind(ScriptChecker.triggerWord(trigger), kind, trigger::allowedIn));
            }
            expectColon(line, call.end());
            Values values = values(line, trigger, call);
            check(line, trigger, values);
            if (trigger == Op.ORDERED && kind == ProgramKind.CAR && !"fetch".equals(values.text()) && !"store".equals(values.text())) {
                // Hoofdstuk 113: de opdrachten van een auto komen van zijn Storage Interface, en dat zijn er twee.
                throw new LogicException(line.number(), "An RC car gets its orders from a Storage Interface: write when ordered(\"fetch\"): "
                        + "(someone ordered an item) or when ordered(\"store\"): (someone put items in to store)");
            }
            index++;
            List<Script.Stmt> body = block(line, trigger);
            items.add(new Script.Handler(trigger, values.text() == null ? "" : values.text(),
                    values.number() == null ? 0 : values.number(), body, line.number()));
        }
        return new Script(items, global);
    }

    // ================================================================ def naam(parameters):

    /**
     * Een eigen blok: {@code def naam():} met de stappen eronder. Sinds hoofdstuk 43 met parameters tussen de haakjes:
     * {@code def open_door(string door, int times):} (het soort-woord mag weg: dan past elke waarde). Tot hoofdstuk 114
     * stond het soort-woord erachter, met een dubbele punt ({@code door: string}); zulke oude code zet
     * {@link CodeUpgrade} bij het laden om.
     */
    private Script.Function function(Line line) {
        List<Lexer.Token> tokens = line.tokens();
        Lexer.Token name = tokens.get(1);
        if (name.type() != Lexer.Type.NAME) {
            throw new LogicException(line.number(), "def needs a name, like: def open_door():");
        }
        String problem = Checks.nameProblem(name.text(), kind);
        if (problem != null) {
            throw new LogicException(line.number(), problem);
        }
        if (!tokens.get(2).is("(")) {
            throw new LogicException(line.number(), "A def needs brackets after its name, like: def " + name.text() + "():");
        }
        // De parameters: tot de ) die erbij hoort.
        int close = 3;
        while (tokens.get(close).type() != Lexer.Type.END && !tokens.get(close).is(")")) {
            close++;
        }
        if (tokens.get(close).type() == Lexer.Type.END) {
            throw new LogicException(line.number(), "def " + name.text() + "( is missing its closing )");
        }
        // Sinds hoofdstuk 114 zoals in C#: def open_door(string door, int times): (zelfde lezer als het DEF-blokje).
        List<Script.Param> params;
        try {
            params = Checks.parseParams(tokens, 3, close, name.text());
        } catch (LogicException e) {
            throw e.atLine(line.number());
        }
        problem = Checks.paramsProblem(params, functionNames, kind);
        if (problem != null) {
            throw new LogicException(line.number(), problem);
        }
        expectColon(line, close + 1);
        index++;
        // In een def weten we nog niet welke when hem gebruikt (null): dat controleert ScriptChecker.
        List<Script.Stmt> body = block(line, null);
        return new Script.Function(name.text(), params, body, line.number());
    }

    // ================================================================ Een ingesprongen blok

    /**
     * De regels onder {@code owner} (een regel met een dubbele punt): ze springen allemaal even ver
     * in, verder dan {@code owner}.
     */
    private List<Script.Stmt> block(Line owner, @Nullable Op trigger) {
        if (index >= lines.size() || lines.get(index).indent() <= owner.indent()) {
            throw new LogicException(owner.number(), "After a line with : there must be lines that are indented"
                    + " (4 spaces more). Write pass if nothing should happen.");
        }
        int indent = lines.get(index).indent();
        List<Script.Stmt> statements = new ArrayList<>();
        while (index < lines.size() && lines.get(index).indent() > owner.indent()) {
            Line line = lines.get(index);
            if (line.indent() != indent) {
                throw new LogicException(line.number(), line.indent() > indent
                        ? "This line is indented more than the line above it"
                        : "This line is indented less than the lines above it, but more than the line with the :");
            }
            Script.Stmt statement = statement(line, trigger);
            if (statement != null) {
                statements.add(statement);
            }
        }
        return statements;
    }

    /** Eén regel in een blok: een if (met zijn eigen blokken), pass, een actie, of een eigen blok. */
    private @Nullable Script.Stmt statement(Line line, @Nullable Op trigger) {
        Lexer.Token first = line.first();
        if (first.is("if")) {
            return ifStatement(line, trigger);
        }
        if (first.is("elif")) {
            throw new LogicException(line.number(), "This elif has no if above it (at the same indent)");
        }
        if (first.is("else")) {
            throw new LogicException(line.number(), "This else has no if above it (at the same indent)");
        }
        if (first.is("when")) {
            throw new LogicException(line.number(), "A when must not be indented: start it at the left edge");
        }
        if (first.is("def")) {
            throw new LogicException(line.number(), "A def must not be indented: start it at the left edge, not inside a when");
        }
        if (first.is("global")) {
            throw new LogicException(line.number(), "global only works as the very first line of the diagram: it makes the whole"
                    + " diagram a global script (shared names are written there, like lockdown: bool = false)");
        }
        if (first.is("pass")) {
            if (line.tokens().get(1).type() != Lexer.Type.END) {
                throw new LogicException(line.number(), "Nothing may come after pass");
            }
            index++;
            return null;
        }
        if (first.is("for") || first.is("while")) {
            return loop(line, trigger);
        }
        // Sinds 16 oktober weg (jouw notitie: "een for en while is voldoende"): zeg hoe het nu heet.
        if (first.is("repeat") && !functionNames.contains("repeat")) {
            throw new LogicException(line.number(), "repeat is now a for loop: write for i in range(3): (i counts 0, 1, 2)");
        }
        if (first.is("forever") && !functionNames.contains("forever")) {
            throw new LogicException(line.number(), "forever is now written while true: (it goes on until break)");
        }
        if (first.is("break")) {
            // Of hij in een lus staat, kijkt ScriptChecker na (die ziet ook de blokjes).
            if (line.tokens().get(1).type() != Lexer.Type.END) {
                throw new LogicException(line.number(), "Nothing may come after break");
            }
            index++;
            return new Script.Action(Op.BREAK, "", 0, line.number());
        }
        if (isAssignment(line)) {
            String[] parts = assignment(line);
            return new Script.Assign(parts[0], parts[1], parts[2], line.number());
        }
        if (first.type() == Lexer.Type.NAME && line.tokens().get(1).is(":") && line.tokens().get(2).type() == Lexer.Type.NAME
                && VarType.byWord(line.tokens().get(2).text()) == null && Op.byCode(first.text()) == null) {
            throw new LogicException(line.number(), "I don't know the type \"" + line.tokens().get(2).text() + "\": use "
                    + VarType.words() + ", like " + first.text() + ": int = 0");
        }
        Call call = call(line, 0);
        if (line.tokens().get(call.end()).type() != Lexer.Type.END) {
            Lexer.Token extra = line.tokens().get(call.end());
            throw new LogicException(line.number(), extra.is(":")
                    ? call.name() + "() is an action: it does not end with a :" : "I did not expect " + extra + " after " + call.name() + "()");
        }
        if (isDef(call.name())) {
            // Een van je eigen blokken (of een def uit een global script, hoofdstuk 43), met de waarden voor zijn
            // parameters als code: open_door("Lab door", 3). Of het er genoeg zijn, kijkt ScriptChecker na.
            index++;
            return new Script.Action(Op.CALL, call.name(), 0, Expr.printList(call.args()), line.number());
        }
        Op op = Op.byCode(call.name());
        if (op == Op.CALL || op == Op.DEF) {
            throw new LogicException(line.number(), "Write the name of your def with brackets, like open_door()");
        }
        if (op != null && op.kind() == Op.Kind.SET) {
            throw new LogicException(line.number(), "Give a name a value with =, like count = 0");
        }
        if (op == null && Expr.Function.byName(call.name()) != null) {
            throw new LogicException(line.number(), call.name() + "(...) gives a value: use it in an if or after =, like "
                    + Expr.Function.byName(call.name()).example());
        }
        if (op == null) {
            throw new LogicException(line.number(), unknown(call.name()));
        }
        if (op.isTrigger()) {
            throw new LogicException(line.number(), call.name() + "(...) starts a diagram: write it at the left edge as when "
                    + call.name() + "(...):");
        }
        if (!op.allowedIn(kind)) {
            throw new LogicException(line.number(), ScriptChecker.wrongKind(op.code() + "()", kind, op::allowedIn));
        }
        if ((op == Op.MINE || op == Op.MINE_UP || op == Op.MINE_DOWN) && !call.args().isEmpty()) {
            // Tot hoofdstuk 113 bestonden mine(2), mine_up(2) en mine_down(2): nu reikt de boor hoger met de arm. Naar
            // beneden kan de arm niet: daar herhaal je mine_down() (na elke keer zakt de auto in het gat).
            throw new LogicException(line.number(), op == Op.MINE_DOWN
                    ? "mine_down() breaks one block now, without a number. To dig deeper, repeat it: for i in range(2): mine_down()"
                    : op.code() + "() breaks one block now, without a number. To break higher, raise the arm: arm_up() and then mine()");
        }
        if (op.textArg() == Op.Arg.VALUES) {
            // goto(x, y, z), write(pin, true), order("Guard 1", "attack"): een rijtje waarden, als code bewaard.
            String problem = Checks.valuesProblem(op, call.args());
            if (problem != null) {
                throw new LogicException(line.number(), problem);
            }
            index++;
            return new Script.Action(op, "", 0, Expr.printList(call.args()), line.number());
        }
        Values values = values(line, op, call);
        check(line, op, values);
        String problem = Checks.contextProblem(op, trigger);
        if (problem != null) {
            throw new LogicException(line.number(), problem);
        }
        index++;
        double number = values.number() != null ? values.number()
                : op.numberArg() == Op.Arg.OPTIONAL_NUMBER ? op.defaultNumber() : 0;
        return new Script.Action(op, values.text() == null ? "" : values.text(), number, values.expr(), values.textExpr(),
                line.number());
    }

    /** if (of elif) voorwaarde: blok, en dan eventueel elif/else met hun blok. */
    private Script.If ifStatement(Line line, @Nullable Op trigger) {
        int[] end = new int[1];
        Expr condition;
        try {
            condition = Expr.parse(line.tokens(), 1, trigger, end);
        } catch (LogicException e) {
            throw e.atLine(line.number());
        }
        expectColon(line, end[0]);
        index++;
        List<Script.Stmt> yes = block(line, trigger);
        List<Script.Stmt> no = new ArrayList<>();
        if (index < lines.size() && lines.get(index).indent() == line.indent()) {
            Line next = lines.get(index);
            if (next.first().is("elif")) {
                no.add(ifStatement(next, trigger));
            } else if (next.first().is("else")) {
                expectColon(next, 1);
                index++;
                no = block(next, trigger);
            }
        }
        return new Script.If(Expr.print(condition), yes, no, line.number());
    }

    // ================================================================ Lussen

    /**
     * Een lus (hoofdstuk 20) met de stappen eronder: {@code for i in range(3):} (16 oktober, zoals in Python; het bereik
     * mag een naam of een som zijn, en {@code range(1, 4)} telt 1, 2, 3) of {@code while count < 3:} (ook
     * {@code while true:}, tot een break).
     */
    private Script.Loop loop(Line line, @Nullable Op trigger) {
        List<Lexer.Token> tokens = line.tokens();
        if (line.first().is("for")) {
            return forLoop(line, trigger);
        }
        if (tokens.get(1).type() == Lexer.Type.END || tokens.get(1).is(":")) {
            throw new LogicException(line.number(), "while needs a check, like: while count < 3: (or while true:)");
        }
        int[] end = new int[1];
        String expr;
        try {
            expr = Expr.print(Expr.parse(tokens, 1, trigger, end));
        } catch (LogicException e) {
            throw e.atLine(line.number());
        }
        expectColon(line, end[0]);
        index++;
        return new Script.Loop(Op.WHILE, "", expr, block(line, trigger), line.number());
    }

    /**
     * {@code for i in range(3):} of {@code for i in range(1, 4):}. De naam telt mee: hij is eerst het begin, en elke
     * ronde één meer, tot vlak voor het einde (zoals in Python). Of de naam al een andere soort waarde had, kijkt
     * ScriptChecker na.
     */
    private Script.Loop forLoop(Line line, @Nullable Op trigger) {
        List<Lexer.Token> tokens = line.tokens();
        String example = "like for i in range(3):";
        if (tokens.get(1).type() != Lexer.Type.NAME || tokens.get(1).is("in")) {
            throw new LogicException(line.number(), "for needs a name that counts, " + example);
        }
        String name = tokens.get(1).text();
        String problem = Checks.variableProblem(name, functionNames, kind);
        if (problem != null) {
            throw new LogicException(line.number(), problem);
        }
        if (!tokens.get(2).is("in")) {
            throw new LogicException(line.number(), "After the name comes in, like for " + name + " in range(3):");
        }
        if (!tokens.get(3).is("range")) {
            throw new LogicException(line.number(), "A for loop counts with range, like for " + name + " in range(3):");
        }
        if (!tokens.get(4).is("(") || tokens.get(5).is(")")) {
            throw new LogicException(line.number(), "range needs a number between brackets, like for " + name + " in range(3):");
        }
        List<Expr> bounds = new ArrayList<>();
        int i = 5;
        while (true) {
            int[] end = new int[1];
            try {
                bounds.add(Expr.parseValue(tokens, i, end));
            } catch (LogicException e) {
                throw e.atLine(line.number());
            }
            i = end[0];
            if (tokens.get(i).is(",")) {
                i++;
                continue;
            }
            if (!tokens.get(i).is(")")) {
                throw new LogicException(line.number(), tokens.get(i).type() == Lexer.Type.END ? "range( is missing its closing )"
                        : "Separate the numbers of range with a comma, like range(1, 4)");
            }
            break;
        }
        problem = Checks.rangeProblem(bounds);
        if (problem != null) {
            throw new LogicException(line.number(), problem);
        }
        expectColon(line, i + 1);
        index++;
        return new Script.Loop(Op.FOR, name, Expr.printList(bounds), block(line, trigger), line.number());
    }

    // ================================================================ naam = waarde

    /**
     * Is dit een regel als {@code count = count + 1}: een naam, dan één =? Of (hoofdstuk 43) met een soort-woord:
     * {@code count: int = 0}.
     */
    private static boolean isAssignment(Line line) {
        List<Lexer.Token> tokens = line.tokens();
        if (line.first().type() != Lexer.Type.NAME) {
            return false;
        }
        if (tokens.get(1).is("=")) {
            return true;
        }
        return tokens.get(1).is(":") && tokens.get(2).type() == Lexer.Type.NAME && VarType.byWord(tokens.get(2).text()) != null
                && (tokens.get(3).is("=") || tokens.get(3).type() == Lexer.Type.END);
    }

    /**
     * Leest {@code naam = waarde} (hoofdstuk 20) of {@code naam: soort = waarde} (hoofdstuk 43). Geeft {naam, soort-woord
     * (of leeg), waarde als nette code}. Of de naam al een waarde had en of de soort klopt, controleert ScriptChecker
     * (die kent het hele diagram).
     */
    private String[] assignment(Line line) {
        String name = line.first().text();
        String problem = Checks.variableProblem(name, functionNames, kind);
        if (problem != null) {
            throw new LogicException(line.number(), problem);
        }
        String type = "";
        int at = 2;
        if (line.tokens().get(1).is(":")) {
            type = line.tokens().get(2).text();
            if (line.tokens().get(3).type() == Lexer.Type.END) {
                throw new LogicException(line.number(), "Give " + name + " a value after the type, like " + name + ": " + type
                        + " = " + VarType.byWord(type).emptyCode());
            }
            at = 4;
        }
        if (line.tokens().get(at).type() == Lexer.Type.END) {
            throw new LogicException(line.number(), "Give " + name + " a value after the =, like " + name + " = 0");
        }
        int[] end = new int[1];
        Expr value;
        try {
            value = Expr.parseValue(line.tokens(), at, end);
        } catch (LogicException e) {
            throw e.atLine(line.number());
        }
        Lexer.Token extra = line.tokens().get(end[0]);
        if (extra.type() != Lexer.Type.END) {
            throw new LogicException(line.number(), extra.is(":") ? "A name with a value does not end with a :"
                    : "I did not expect " + extra + " here");
        }
        index++;
        return new String[] {name, type, Expr.print(value)};
    }

    // ================================================================ naam(...)

    /**
     * Een aanroep zoals {@code pulse("Vault door", 3)}: de naam, de waarden tussen de haakjes (elk een
     * uitdrukking, sinds hoofdstuk 20), en waar hij ophoudt.
     */
    private record Call(String name, List<Expr> args, int end) {}

    /**
     * De waarden van een aanroep, verdeeld over de vakjes van het blokje: de tekst (een apparaat of
     * bericht), het getal (seconden), en een berekening ({@code expr}) als het bericht of het getal geen
     * gewone tekst of gewoon getal is.
     */
    private record Values(@Nullable String text, @Nullable Double number, String expr, String textExpr) {}

    /**
     * Verdeelt de waarden van een aanroep over de vakjes van {@code op}. Een apparaat is meestal gewone tekst
     * tussen aanhalingstekens (zo kan de editor de namen voorstellen en controleren); sinds hoofdstuk 43 mag het
     * ook een naam of berekening zijn ({@code pulse(door, 3)} met {@code door} een parameter: dat komt in
     * {@code textExpr}). Een bericht (say, log, alarm) en het aantal seconden (wait, pulse) mogen een berekening
     * zijn. Een trigger neemt alleen gewone tekst en gewone getallen.
     */
    private static Values values(Line line, Op op, Call call) {
        List<Expr> args = call.args();
        boolean hasText = op.textArg() != Op.Arg.NONE;
        boolean hasNumber = op.numberArg() != Op.Arg.NONE;
        if (args.size() > (hasText ? 1 : 0) + (hasNumber ? 1 : 0)) {
            if (!hasText && !hasNumber) {
                throw new LogicException(line.number(), op.code() + " does not take "
                        + (args.getFirst() instanceof Expr.Str ? "a name" : "a number"));
            }
            throw new LogicException(line.number(), op.code() + "(...) has too much between the brackets, like "
                    + op.example().replace("|", "..."));
        }
        String text = null;
        Double number = null;
        String expr = "";
        String textExpr = "";
        int index = 0;
        if (hasText && index < args.size()) {
            Expr arg = args.get(index++);
            if (arg instanceof Expr.Str str) {
                text = str.value();
            } else if (Checks.exprIsText(op) && !op.isTrigger()) {
                expr = Expr.print(arg); // een berekend bericht: say("Count: " + count)
            } else if (op.isTrigger()) {
                throw new LogicException(line.number(), arg instanceof Expr.Ref ref
                        ? "Put names between quotes: " + op.code() + "(\"" + ref.name() + "\")"
                        : op.code() + " needs a name between quotes, like " + op.code() + "(\"Lab door\")");
            } else if ((op.textArg() == Op.Arg.DEVICE || op.textArg() == Op.Arg.ROBOT)
                    && (Expr.typeOf(arg) == Expr.Type.TEXT || Expr.typeOf(arg) == Expr.Type.ANY)) {
                // Een naam of berekening als apparaat (hoofdstuk 43): pulse(door, 3). Of die naam een waarde heeft,
                // kijkt ScriptChecker na (met een tip over aanhalingstekens als het een typfout lijkt).
                textExpr = Expr.print(arg);
            } else if (arg instanceof Expr.Ref ref) {
                throw new LogicException(line.number(), "Put names between quotes: " + op.code() + "(\"" + ref.name() + "\")");
            } else {
                String problem = Checks.argsProblem(op, null, null);
                throw new LogicException(line.number(), problem != null ? problem
                        : op.code() + " needs a name between quotes, like " + op.code() + "(\"Lab door\")");
            }
        }
        if (hasNumber && index < args.size()) {
            Expr arg = args.get(index);
            if (arg instanceof Expr.Num num) {
                number = num.value();
            } else if (op.isTrigger()) {
                throw new LogicException(line.number(), op.code() + " needs a plain number of " + op.unit() + ", like "
                        + op.code() + "(" + op.exampleNumber() + ")");
            } else {
                Expr.Type type = Expr.typeOf(arg);
                if (type == Expr.Type.TEXT || type == Expr.Type.BOOL) {
                    throw new LogicException(line.number(), op.code() + " needs a number of " + op.unit() + ", like "
                            + op.code() + "(" + (hasText ? "\"Lab door\", " : "") + op.exampleNumber() + ")");
                }
                expr = Expr.print(arg); // berekende seconden: wait(delay * 2)
            }
        }
        return new Values(text, number, expr, textExpr);
    }

    private Call call(Line line, int start) {
        List<Lexer.Token> tokens = line.tokens();
        Lexer.Token name = tokens.get(start);
        if (name.type() != Lexer.Type.NAME) {
            throw new LogicException(line.number(), "I expected the name of an action here, not " + name);
        }
        if (!tokens.get(start + 1).is("(")) {
            if (functionNames.contains(name.text())) {
                throw new LogicException(line.number(), name.text() + " needs brackets: " + name.text() + "()");
            }
            Op op = Op.byCode(name.text());
            throw new LogicException(line.number(), op != null
                    ? name.text() + " needs brackets, like " + op.example().replace("|", "...")
                    : unknown(name.text()));
        }
        int i = start + 2;
        List<Expr> args = new ArrayList<>();
        if (!tokens.get(i).is(")")) {
            while (true) {
                if (tokens.get(i).type() == Lexer.Type.END) {
                    throw new LogicException(line.number(), name.text() + "( is missing its closing )");
                }
                // Elke waarde is een uitdrukking: "Lab door", 3, delay * 2, "Count: " + count.
                int[] end = new int[1];
                try {
                    args.add(Expr.parseValue(tokens, i, end));
                } catch (LogicException e) {
                    throw e.atLine(line.number());
                }
                i = end[0];
                if (tokens.get(i).is(",")) {
                    i++;
                    continue;
                }
                if (!tokens.get(i).is(")")) {
                    throw new LogicException(line.number(), tokens.get(i).type() == Lexer.Type.END
                            ? name.text() + "( is missing its closing )" : "Separate the things between brackets with a comma");
                }
                break;
            }
        }
        return new Call(name.text(), args, i + 1);
    }

    /** Kloppen de naam en het getal voor dit blokje? */
    private static void check(Line line, Op op, Values values) {
        String problem = Checks.argsProblem(op, values.text(), values.number(), values.expr(), values.textExpr());
        if (problem != null) {
            throw new LogicException(line.number(), problem);
        }
    }

    /** Na een when of if moet een dubbele punt komen, en dan niets meer. */
    private static void expectColon(Line line, int at) {
        Lexer.Token token = line.tokens().get(at);
        if (!token.is(":")) {
            throw new LogicException(line.number(), token.type() == Lexer.Type.END
                    ? "This line must end with a :" : "I did not expect " + token + " here");
        }
        if (line.tokens().get(at + 1).type() != Lexer.Type.END) {
            throw new LogicException(line.number(), "Nothing may come after the : (put it on the next line, indented)");
        }
    }

    /** Hoe een when in deze soort programma begint, voor de foutmeldingen. */
    private String exampleWhen() {
        return kind.onBot() || kind == ProgramKind.CAR ? "when started:"
                : kind == ProgramKind.TRAINING_END ? "when trainee_hit:" : "when keycard(\"Lab door\"):";
    }

    /** De triggers die in deze soort programma bestaan: "keycard, codepanel, turned_on, turned_off, every, message". */
    private String triggers() {
        List<String> names = new ArrayList<>();
        for (Op op : Op.values()) {
            if (op.isTrigger() && op.allowedIn(kind)) {
                names.add(op.code());
            }
        }
        return String.join(", ", names);
    }

    private String unknown(String name) {
        String guess = Checks.suggest(name, functionNames);
        return "I don't know \"" + name + "\"" + (guess != null ? ". Did you mean " + guess + "?" : "");
    }
}
