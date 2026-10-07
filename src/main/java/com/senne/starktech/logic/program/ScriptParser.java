package com.senne.starktech.logic.program;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Leest de code van een diagram (jouw keuze: "zoals Python") en maakt er een {@link Script} van.
 * <pre>
 * when keycard("Lab door"):
 *     if card.valid and card.level &gt;= 3:
 *         accept()
 *         pulse("Vault door", 3)
 *     elif card.level &gt;= 1:
 *         say("Someone with a low card tried the lab")
 *     else:
 *         deny()
 *         alarm("Wrong card at the lab")
 * </pre>
 * Net als in Python bepaalt het INSPRINGEN wat waarbij hoort: alles wat verder naar rechts staat dan
 * een regel met een dubbele punt, hoort bij die regel. Een tab telt als 4 spaties. Een lege regel en
 * commentaar (vanaf #) tellen niet mee.
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

    private ScriptParser(String source) {
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
                lines.add(new Line(i + 1, indent, tokens));
            }
        }
    }

    /** Leest de code. Gooit een LogicException (met regelnummer) bij de eerste fout. */
    public static Script parse(String source) {
        return new ScriptParser(source).script();
    }

    // ================================================================ when ...:

    private Script script() {
        List<Script.Handler> handlers = new ArrayList<>();
        while (index < lines.size()) {
            Line line = lines.get(index);
            if (line.indent() != 0) {
                throw new LogicException(line.number(), "This line is indented, but there is no when above it");
            }
            Lexer.Token first = line.first();
            if (!first.is("when")) {
                Op op = first.type() == Lexer.Type.NAME ? Op.byCode(first.text()) : null;
                if (op != null && !op.isTrigger() && op != Op.IF) {
                    throw new LogicException(line.number(), first.text()
                            + "() must be inside a when: indent it with 4 spaces under a line like when keycard(\"Lab door\"):");
                }
                throw new LogicException(line.number(), "Every diagram starts with when, like: when keycard(\"Lab door\"):");
            }
            Call call = call(line, 1);
            Op trigger = Op.byCode(call.name());
            if (trigger == null || !trigger.isTrigger()) {
                String hint = trigger == null ? unknown(call.name())
                        : call.name() + " is not a trigger. A when starts with one of: keycard, codepanel, turned_on, turned_off, every";
                throw new LogicException(line.number(), hint);
            }
            expectColon(line, call.end());
            check(line, trigger, call);
            index++;
            List<Script.Stmt> body = block(line, trigger);
            handlers.add(new Script.Handler(trigger, call.text() == null ? "" : call.text(),
                    call.number() == null ? 0 : call.number(), body, line.number()));
        }
        return new Script(handlers);
    }

    // ================================================================ Een ingesprongen blok

    /**
     * De regels onder {@code owner} (een regel met een dubbele punt): ze springen allemaal even ver
     * in, verder dan {@code owner}.
     */
    private List<Script.Stmt> block(Line owner, Op trigger) {
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

    /** Eén regel in een blok: een if (met zijn eigen blokken), pass, of een actie. */
    private @Nullable Script.Stmt statement(Line line, Op trigger) {
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
        if (first.is("pass")) {
            if (line.tokens().get(1).type() != Lexer.Type.END) {
                throw new LogicException(line.number(), "Nothing may come after pass");
            }
            index++;
            return null;
        }
        Call call = call(line, 0);
        if (line.tokens().get(call.end()).type() != Lexer.Type.END) {
            Lexer.Token extra = line.tokens().get(call.end());
            throw new LogicException(line.number(), extra.is(":")
                    ? call.name() + "() is an action: it does not end with a :" : "I did not expect " + extra + " after " + call.name() + "()");
        }
        Op op = Op.byCode(call.name());
        if (op == null) {
            throw new LogicException(line.number(), unknown(call.name()));
        }
        if (op.isTrigger()) {
            throw new LogicException(line.number(), call.name() + "(...) starts a diagram: write it at the left edge as when "
                    + call.name() + "(...):");
        }
        check(line, op, call);
        String problem = Checks.contextProblem(op, trigger);
        if (problem != null) {
            throw new LogicException(line.number(), problem);
        }
        index++;
        double number = call.number() != null ? call.number() : op.numberArg() == Op.Arg.OPTIONAL_SECONDS ? op.defaultSeconds() : 0;
        return new Script.Action(op, call.text() == null ? "" : call.text(), number, line.number());
    }

    /** if (of elif) voorwaarde: blok, en dan eventueel elif/else met hun blok. */
    private Script.If ifStatement(Line line, Op trigger) {
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

    // ================================================================ naam(...)

    /** Een aanroep zoals {@code pulse("Vault door", 3)}: de naam, de tekst en het getal (of null), en waar hij ophoudt. */
    private record Call(String name, @Nullable String text, @Nullable Double number, int args, int end) {}

    private Call call(Line line, int start) {
        List<Lexer.Token> tokens = line.tokens();
        Lexer.Token name = tokens.get(start);
        if (name.type() != Lexer.Type.NAME) {
            throw new LogicException(line.number(), "I expected the name of an action here, not " + name);
        }
        if (!tokens.get(start + 1).is("(")) {
            Op op = Op.byCode(name.text());
            throw new LogicException(line.number(), op != null
                    ? name.text() + " needs brackets, like " + name.text() + "(" + (op.textArg() != Op.Arg.NONE ? "\"...\"" : "") + ")"
                    : unknown(name.text()));
        }
        int i = start + 2;
        String text = null;
        Double number = null;
        int args = 0;
        if (!tokens.get(i).is(")")) {
            while (true) {
                Lexer.Token arg = tokens.get(i);
                if (arg.type() == Lexer.Type.STRING && text == null && number == null) {
                    text = arg.text();
                } else if (arg.type() == Lexer.Type.NUMBER && number == null) {
                    number = arg.number();
                } else if (arg.type() == Lexer.Type.END) {
                    throw new LogicException(line.number(), name.text() + "( is missing its closing )");
                } else if (arg.type() == Lexer.Type.NAME) {
                    throw new LogicException(line.number(), "Put names between quotes: " + name.text() + "(\"" + arg.text() + "\")");
                } else {
                    throw new LogicException(line.number(), "I did not expect " + arg + " between the brackets of " + name.text());
                }
                args++;
                i++;
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
        return new Call(name.text(), text, number, args, i + 1);
    }

    /** Kloppen de naam en het getal voor dit blokje? */
    private static void check(Line line, Op op, Call call) {
        String problem = Checks.argsProblem(op, call.text(), call.number());
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

    private static String unknown(String name) {
        String guess = Checks.suggest(name);
        return "I don't know \"" + name + "\"" + (guess != null ? ". Did you mean " + guess + "?" : "");
    }
}
