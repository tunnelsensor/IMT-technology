package com.senne.starktech.logic.program;

import java.util.List;

/**
 * Schrijft een {@link Script} als nette code (4 spaties per inspringing, een lege regel tussen de
 * when-blokken en defs). Een lege tak wordt {@code pass}; een else met alleen een if erin wordt
 * {@code elif}. Zo zie je in de code precies wat de blokjes doen.
 */
public final class ScriptPrinter {
    private ScriptPrinter() {}

    private static final String INDENT = "    ";

    public static String print(Script script) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < script.items().size(); i++) {
            Script.Top item = script.items().get(i);
            // Een lege regel tussen de blokken, maar namen bovenaan (count = 0) staan onder elkaar.
            if (i > 0 && !(item instanceof Script.Global && script.items().get(i - 1) instanceof Script.Global)) {
                out.append('\n');
            }
            if (item instanceof Script.Global global) {
                out.append(global.name()).append(" = ").append(global.value()).append('\n');
                continue;
            }
            if (item instanceof Script.Function function) {
                out.append("def ").append(function.name()).append("():\n");
            } else if (item instanceof Script.Handler handler) {
                out.append("when ").append(call(handler.trigger(), handler.text(), handler.number())).append(":\n");
            }
            block(out, item.body(), 1);
        }
        return out.toString();
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
                out.append(INDENT.repeat(depth)).append(call(action.op(), action.text(), action.number(), action.expr()))
                        .append('\n');
            } else if (statement instanceof Script.Assign assign) {
                out.append(INDENT.repeat(depth)).append(assign.name()).append(" = ").append(assign.value()).append('\n');
            } else if (statement instanceof Script.Loop loop) {
                out.append(INDENT.repeat(depth)).append(loopLine(loop.op(), loop.expr())).append('\n');
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
        if (expr.isEmpty()) {
            return call(op, text, number);
        }
        StringBuilder call = new StringBuilder(op.code()).append('(');
        if (!Checks.exprIsText(op) && op.textArg() != Op.Arg.NONE && op.textArg() != Op.Arg.VALUES) {
            call.append(Expr.quote(text)).append(", "); // eerst het apparaat, dan de berekende seconden
        }
        // Bij goto(x, y, z) (hoofdstuk 23) is expr het hele rijtje waarden.
        return call.append(expr).append(')').toString();
    }

    /** De eerste regel van een lus: {@code forever:}, {@code repeat(3):} of {@code while count < 3:}. */
    public static String loopLine(Op op, String expr) {
        return switch (op) {
            case FOREVER -> "forever:";
            case REPEAT -> "repeat(" + expr + "):";
            default -> "while " + expr + ":";
        };
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
