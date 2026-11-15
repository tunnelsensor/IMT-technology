package com.senne.imttech.logic.program;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Een diagram als boom, zoals de code hem leest (zie ScriptParser) en schrijft (ScriptPrinter):
 * een lijst van "when"-blokken en eigen blokken ("def"), elk met de regels eronder, in de volgorde
 * waarin ze in de code staan.
 * <pre>
 * def open_lab(string door):        Function("open_lab", [string door])
 *     accept()                       Action(ACCEPT)
 *     pulse(door, 3)                 Action(PULSE, textExpr "door", 3)
 *
 * when keycard("Lab door"):          Handler(KEYCARD, "Lab door")
 *     if card.in_group("lab"):        If("card.in_group(\"lab\")", yes, no)
 *         open_lab("Vault door")     Action(CALL, "open_lab", expr "\"Vault door\"")
 *     else:
 *         deny()                     Action(DENY)
 * </pre>
 * Elk onderdeel onthoudt {@code ref}: in de code het regelnummer, in de blokjes het nummer van het
 * blokje. Zo kan een foutmelding zeggen waar het misging.
 *
 * Hoofdstuk 43: {@code global} is waar als de eerste regel van de code {@code global} is: dan is dit een
 * <b>global script</b>, met alleen defs en gedeelde namen erin, die elk ander diagram van dezelfde kern kan
 * gebruiken (zie {@link Library}).
 */
public record Script(List<Top> items, boolean global) {
    /** Een gewoon diagram (geen global script). */
    public Script(List<Top> items) {
        this(items, false);
    }

    /** Iets aan de linkerkant van de code: een when of een def, met de regels eronder. */
    public sealed interface Top {
        List<Stmt> body();

        int ref();
    }

    /** Eén "when ...:" met de regels eronder. */
    public record Handler(Op trigger, String text, double number, List<Stmt> body, int ref) implements Top {}

    /**
     * Een parameter van een def (hoofdstuk 43): {@code string door} (sinds hoofdstuk 114 zoals in C#, daarvoor
     * {@code door: string}). {@code type} is het soort-woord (int, float, string, bool), of leeg als er geen staat (dan
     * mag elke waarde).
     */
    public record Param(String name, String type) {
        /** De soort, of null als er geen soort-woord staat. */
        public @Nullable VarType varType() {
            return VarType.byWord(type);
        }

        /** De soort zoals de uitdrukkingen hem kennen (ANY zonder soort-woord). */
        public Expr.Type exprType() {
            VarType varType = varType();
            return varType == null ? Expr.Type.ANY : varType.exprType();
        }

        /** Zoals in de code: {@code string door} of {@code door}. */
        public String code() {
            return type.isEmpty() ? name : type + " " + name;
        }
    }

    /** Eén "def naam(parameters):" (je eigen blok) met de regels eronder. */
    public record Function(String name, List<Param> params, List<Stmt> body, int ref) implements Top {
        /** Een def zonder parameters (zoals voor hoofdstuk 43). */
        public Function(String name, List<Stmt> body, int ref) {
            this(name, List.of(), body, ref);
        }

        /** De parameters zoals in de code: {@code string door, int times}. */
        public String paramsCode() {
            StringBuilder code = new StringBuilder();
            for (Param param : params) {
                code.append(code.isEmpty() ? "" : ", ").append(param.code());
            }
            return code.toString();
        }
    }

    /**
     * {@code count = 0} helemaal links (hoofdstuk 20): een naam voor het hele programma. {@code value}
     * is de waarde als nette code. {@code type} (hoofdstuk 43) is het soort-woord ({@code count: int = 0}), of leeg.
     * In een global script is zo'n naam een <b>gedeelde naam</b>: elk diagram van de kern kan hem lezen en veranderen.
     * Er staan geen regels onder.
     */
    public record Global(String name, String type, String value, int ref) implements Top {
        /** Zonder soort-woord. */
        public Global(String name, String value, int ref) {
            this(name, "", value, ref);
        }

        @Override
        public List<Stmt> body() {
            return List.of();
        }
    }

    /** Een regel onder een when of def: een actie, een if, of een naam een waarde geven. */
    public sealed interface Stmt {
        int ref();
    }

    /**
     * {@code count = count + 1} (hoofdstuk 20): een naam een waarde geven. {@code value} is nette code; {@code type}
     * (hoofdstuk 43) het soort-woord ({@code count: int = 0}), of leeg.
     */
    public record Assign(String name, String type, String value, int ref) implements Stmt {
        /** Zonder soort-woord. */
        public Assign(String name, String value, int ref) {
            this(name, "", value, ref);
        }
    }

    /**
     * Een lus (hoofdstuk 20): {@code for i in range(3):} of {@code while count < 3:}, met de stappen erin. Bij een
     * for is {@code var} de naam die telt ({@code i}) en {@code expr} het bereik als nette code ({@code 3} of
     * {@code 1, 4}); bij een while is {@code var} leeg en {@code expr} de voorwaarde.
     */
    public record Loop(Op op, String var, String expr, List<Stmt> body, int ref) implements Stmt {}

    /**
     * Een actie, zoals {@code pulse("Vault door", 3)}. {@code text} is leeg en {@code number} 0 als hij
     * ze niet heeft. Een eigen blok gebruiken ({@code open_lab()}) is een actie CALL met de naam als tekst, en
     * (hoofdstuk 43) de waarden voor zijn parameters als nette code in {@code expr}.
     * {@code expr} (hoofdstuk 20) is een berekening als nette code, als een bericht of het aantal
     * seconden geen gewone tekst of gewoon getal is: {@code say("Count: " + count)}, {@code wait(delay)}.
     * {@code textExpr} (hoofdstuk 43) is de naam van het apparaat of de robot als code, als die geen gewone tekst is:
     * {@code pulse(door, 3)} met {@code door} een parameter.
     */
    public record Action(Op op, String text, double number, String expr, String textExpr, int ref) implements Stmt {
        /** Een actie zonder berekening (zoals voor hoofdstuk 20). */
        public Action(Op op, String text, double number, int ref) {
            this(op, text, number, "", "", ref);
        }

        /** Een actie zonder berekend apparaat (zoals voor hoofdstuk 43). */
        public Action(Op op, String text, double number, String expr, int ref) {
            this(op, text, number, expr, "", ref);
        }
    }

    /** Een if met wat er bij "yes" en bij "no" (else) gebeurt. {@code condition} is de voorwaarde als nette tekst. */
    public record If(String condition, List<Stmt> yes, List<Stmt> no, int ref) implements Stmt {}

    /** Alle when-blokken, in de volgorde van de code. */
    public List<Handler> handlers() {
        List<Handler> handlers = new ArrayList<>();
        for (Top item : items) {
            if (item instanceof Handler handler) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    /** Alle namen helemaal links ({@code count = 0}), in de volgorde van de code. */
    public List<Global> globals() {
        List<Global> globals = new ArrayList<>();
        for (Top item : items) {
            if (item instanceof Global global) {
                globals.add(global);
            }
        }
        return globals;
    }

    /** Alle eigen blokken (defs), in de volgorde van de code. */
    public List<Function> functions() {
        List<Function> functions = new ArrayList<>();
        for (Top item : items) {
            if (item instanceof Function function) {
                functions.add(function);
            }
        }
        return functions;
    }

    /** Hoeveel blokjes dit diagram kost (elke when, def, if en actie is er één): zo vol raakt je SSD. */
    public int blocks() {
        int count = 0;
        for (Top item : items) {
            count += 1 + blocks(item.body());
        }
        return count;
    }

    private static int blocks(List<Stmt> statements) {
        int count = 0;
        for (Stmt statement : statements) {
            count++;
            if (statement instanceof If branch) {
                count += blocks(branch.yes()) + blocks(branch.no());
            } else if (statement instanceof Loop loop) {
                count += blocks(loop.body());
            }
        }
        return count;
    }
}
