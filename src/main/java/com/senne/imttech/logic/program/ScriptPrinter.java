package com.senne.imttech.logic.program;

import java.util.List;

/**
 * Schrijft een {@link Script} als nette code (4 spaties per inspringing, een lege regel tussen de
 * when-blokken en defs). Een lege tak wordt {@code pass}; een else met alleen een if erin wordt
 * {@code elif}. Zo zie je in de code precies wat de blokjes doen.
 *
 * Hoofdstuk 43: een global script begint met de regel {@code global}; een naam met een soort-woord wordt
 * {@code count: int = 0}; een def met parameters {@code def open_door(string door):} (sinds hoofdstuk 114 zoals in C#); een aanroep met waarden
 * {@code open_door("Lab door")}; en een apparaat als code {@code pulse(door, 3)}.
 */
public final class ScriptPrinter {
    private ScriptPrinter() {}

    private static final String INDENT = "    ";

    /**
     * Zoals {@link #print(Script)}, maar elke when met zijn uitgerekende naam, ook als hij een berekende naam heeft
     * (hoofdstuk 135): om te vergelijken of je code nog bij de blokjes past. Een diagram dat bewaard werd voor de blokjes
     * de code van hun when-naam bijhielden (hoofdstuk 135 voor de nalezing), past dan nog bij je code met de f-tekst.
     */
    public static String printWithNames(Script script) {
        List<Script.Top> items = new java.util.ArrayList<>();
        for (Script.Top item : script.items()) {
            items.add(item instanceof Script.Handler handler ? new Script.Handler(handler.trigger(), handler.text(),
                    handler.number(), handler.body(), handler.ref(), "") : item);
        }
        return print(new Script(items, script.global()));
    }

    public static String print(Script script) {
        StringBuilder out = new StringBuilder();
        if (script.global()) {
            out.append("global\n");
        }
        for (int i = 0; i < script.items().size(); i++) {
            Script.Top item = script.items().get(i);
            // Een lege regel tussen de blokken, maar namen bovenaan (count = 0) staan onder elkaar.
            boolean afterGlobalWord = i == 0 && script.global();
            if ((i > 0 || afterGlobalWord) && !(item instanceof Script.Global && i > 0 && script.items().get(i - 1) instanceof Script.Global)) {
                out.append('\n');
            }
            if (item instanceof Script.Global global) {
                out.append(global.name()).append(typeSuffix(global.type())).append(" = ").append(global.value()).append('\n');
                continue;
            }
            if (item instanceof Script.Function function) {
                out.append("def ").append(function.name()).append('(').append(function.paramsCode()).append("):\n");
            } else if (item instanceof Script.Handler handler) {
                // Een berekende naam (hoofdstuk 135) komt terug als code: when keycard($"{room} door"):
                out.append("when ").append(handler.textExpr().isEmpty() ? call(handler.trigger(), handler.text(), handler.number())
                        : handler.trigger().code() + "(" + handler.textExpr() + ")").append(":\n");
            }
            block(out, item.body(), 1);
        }
        return out.toString();
    }

    /** {@code : int} achter een naam, of niets als er geen soort-woord is. */
    private static String typeSuffix(String type) {
        return type.isEmpty() ? "" : ": " + type;
    }

    private static void block(StringBuilder out, List<Script.Stmt> statements, int depth) {
        if (statements.isEmpty()) {
            out.append(INDENT.repeat(depth)).append("pass\n");
            return;
        }
        for (Script.Stmt statement : statements) {
            if (statement instanceof Script.If branch) {
                ifStatement(out, branch, depth, "if");
            } else if (statement instanceof Script.Action action) {
                out.append(INDENT.repeat(depth))
                        .append(call(action.op(), action.text(), action.number(), action.expr(), action.textExpr()))
                        .append('\n');
            } else if (statement instanceof Script.Assign assign) {
                out.append(INDENT.repeat(depth)).append(assign.name()).append(typeSuffix(assign.type())).append(" = ")
                        .append(assign.value()).append('\n');
            } else if (statement instanceof Script.Loop loop) {
                out.append(INDENT.repeat(depth)).append(loopLine(loop.op(), loop.var(), loop.expr())).append('\n');
                block(out, loop.body(), depth + 1);
            }
        }
    }

    private static void ifStatement(StringBuilder out, Script.If branch, int depth, String keyword) {
        out.append(INDENT.repeat(depth)).append(keyword).append(' ').append(branch.condition()).append(":\n");
        block(out, branch.yes(), depth + 1);
        if (branch.no().size() == 1 && branch.no().getFirst() instanceof Script.If inner) {
            ifStatement(out, inner, depth, "elif");
        } else if (!branch.no().isEmpty()) {
            out.append(INDENT.repeat(depth)).append("else:\n");
            block(out, branch.no(), depth + 1);
        }
    }

    /**
     * Een aanroep met een berekening ({@code expr}, hoofdstuk 20): {@code say("Count: " + count)},
     * {@code wait(delay * 2)}, {@code pulse("Lamp", delay)}. Zonder berekening zoals {@link #call(Op, String, double)}.
     */
    public static String call(Op op, String text, double number, String expr) {
        return call(op, text, number, expr, "");
    }

    /**
     * Zoals hierboven, en (hoofdstuk 43) met het apparaat als code ({@code textExpr}: {@code pulse(door, 3)}), de
     * parameters van een def ({@code expr} bij DEF: {@code def open_door(string door)}) of de waarden van een aanroep
     * ({@code expr} bij CALL: {@code open_door("Lab door")}).
     */
    public static String call(Op op, String text, double number, String expr, String textExpr) {
        if (op == Op.CALL) {
            return text + "(" + expr + ")";
        }
        if (op == Op.DEF) {
            return "def " + text + "(" + expr + ")";
        }
        boolean codeName = !textExpr.isEmpty() && (op.textArg() == Op.Arg.DEVICE || op.textArg() == Op.Arg.ROBOT);
        if (expr.isEmpty() && !codeName) {
            return call(op, text, number);
        }
        StringBuilder call = new StringBuilder(op.code()).append('(');
        if (op.textArg() != Op.Arg.NONE && op.textArg() != Op.Arg.VALUES && !Checks.exprIsText(op)) {
            call.append(codeName ? textExpr : Expr.quote(text)); // eerst het apparaat, dan de (berekende) seconden
            if (!expr.isEmpty()) {
                call.append(", ");
            }
        }
        if (!expr.isEmpty()) {
            // Bij goto(x, y, z) (hoofdstuk 23) is expr het hele rijtje waarden; bij pulse de berekende seconden.
            call.append(expr);
        } else if (codeName && (op.numberArg() == Op.Arg.NUMBER
                || op.numberArg() == Op.Arg.OPTIONAL_NUMBER && number != op.defaultNumber())) {
            call.append(", ").append(Expr.number(number));
        }
        return call.append(')').toString();
    }

    /** De eerste regel van een lus: {@code for i in range(3):} of {@code while count < 3:}. */
    public static String loopLine(Op op, String var, String expr) {
        return op == Op.FOR ? "for " + var + " in range(" + expr + "):" : "while " + expr + ":";
    }

    /**
     * Een aanroep: {@code keycard("Lab door")}, {@code wait(2)}, {@code pulse("Gate", 0.5)},
     * {@code accept()}, een eigen blok: {@code open_lab()}, en een trigger zonder haakjes: {@code started}.
     */
    public static String call(Op op, String text, double number) {
        if (op == Op.CALL) {
            return text + "()";
        }
        if (op == Op.BREAK) {
            return "break";
        }
        if (op == Op.DEF) {
            return "def " + text + "()";
        }
        if (op.withoutBrackets()) {
            return op.code(); // when started:
        }
        StringBuilder call = new StringBuilder(op.code()).append('(');
        if (op.textArg() != Op.Arg.NONE && op.textArg() != Op.Arg.VALUES) {
            call.append(Expr.quote(text));
        }
        boolean showNumber = op.numberArg() == Op.Arg.NUMBER
                || op.numberArg() == Op.Arg.OPTIONAL_NUMBER && number != op.defaultNumber();
        if (showNumber) {
            if (op.textArg() != Op.Arg.NONE) {
                call.append(", ");
            }
            call.append(Expr.number(number));
        }
        return call.append(')').toString();
    }
}
