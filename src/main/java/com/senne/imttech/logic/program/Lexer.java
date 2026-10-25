package com.senne.imttech.logic.program;

import java.util.ArrayList;
import java.util.List;

/**
 * Knipt één regel code in stukjes ("tokens"): woorden ({@code keycard}, {@code card.valid}),
 * getallen ({@code 3}, {@code 0.5}), tekst tussen aanhalingstekens ({@code "Lab door"}) en tekens
 * ({@code ==}, {@code (}, {@code :}, {@code +}). Een {@code #} begint commentaar: de rest van de
 * regel telt niet. Sinds 19 oktober ook tekst met namen erin ({@code $"Round {i} of {count}"}, jouw
 * notitie: "zorg dat string interpolation ook werkt"): die blijft één stukje (TEMPLATE), en Expr leest
 * de code tussen de { en } erin.
 *
 * Klopt er iets niet (een vreemd teken, een tekst zonder sluitend aanhalingsteken), dan gooit hij
 * een {@link LogicException} met uitleg in begrijpelijk Engels.
 */
public final class Lexer {
    private Lexer() {}

    /** Soorten stukjes. TEMPLATE is tekst met namen erin: {@code $"Round {i}"}. */
    public enum Type { NAME, NUMBER, STRING, TEMPLATE, SYMBOL, END }

    /**
     * Eén stukje: soort, de tekst (bij STRING zonder aanhalingstekens; bij TEMPLATE alles tussen {@code $"} en
     * {@code "}, precies zoals het er staat), het getal (bij NUMBER), en waar het op de regel begint (voor
     * foutmeldingen).
     */
    public record Token(Type type, String text, double number, int column) {
        public boolean is(String symbolOrName) {
            return (type == Type.SYMBOL || type == Type.NAME) && text.equals(symbolOrName);
        }

        @Override
        public String toString() {
            return switch (type) {
                case STRING -> "\"" + text + "\"";
                case TEMPLATE -> "$\"" + text + "\"";
                case END -> "the end of the line";
                default -> text;
            };
        }
    }

    /**
     * Waar de { op plek {@code open} in de tekst van een TEMPLATE dichtgaat: de } die erbij hoort (tekst tussen
     * aanhalingstekens in de code telt niet mee). Gooit een fout als hij niet dichtgaat.
     */
    static int closingBrace(String raw, int open) {
        int depth = 0;
        char inner = 0;
        for (int i = open; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inner != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == inner) {
                    inner = 0;
                }
            } else if (c == '"' || c == '\'') {
                inner = c;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        throw new LogicException("A { in $\"...\" is missing its }");
    }

    /**
     * De stukjes code in de tekst van een TEMPLATE: bij {@code Round {i} of {count + 1}} zijn dat {@code i} en
     * {@code count + 1}. Voor wie de namen in een programma zoekt (LogicGraph). {@code {{} en {@code }}} zijn
     * gewone haakjes in de tekst, geen code.
     */
    public static java.util.List<String> templateCode(String raw) {
        java.util.List<String> code = new ArrayList<>();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '\\' || (c == '{' || c == '}') && i + 1 < raw.length() && raw.charAt(i + 1) == c) {
                i += 2;
            } else if (c == '{') {
                int end = closingBrace(raw, i);
                code.add(raw.substring(i + 1, end));
                i = end + 1;
            } else {
                i++;
            }
        }
        return code;
    }

    /** Knipt deze regel in stukjes; het laatste stukje is altijd END. */
    public static List<Token> tokens(String line) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t') {
                i++;
            } else if (c == '#') {
                break; // commentaar tot het einde van de regel
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_' || line.charAt(i) == '.')) {
                    i++;
                }
                String word = line.substring(start, i);
                if (word.endsWith(".")) {
                    throw new LogicException("\"" + word + "\" ends with a dot");
                }
                tokens.add(new Token(Type.NAME, word, 0, start));
            } else if (Character.isDigit(c) || c == '.' && i + 1 < n && Character.isDigit(line.charAt(i + 1))) {
                int start = i;
                while (i < n && (Character.isDigit(line.charAt(i)) || line.charAt(i) == '.')) {
                    i++;
                }
                String number = line.substring(start, i);
                if (i < n && Character.isLetter(line.charAt(i))) {
                    throw new LogicException("\"" + number + line.charAt(i) + "\": a name cannot start with a number");
                }
                try {
                    tokens.add(new Token(Type.NUMBER, number, Double.parseDouble(number), start));
                } catch (NumberFormatException e) {
                    throw new LogicException("\"" + number + "\" is not a number");
                }
            } else if (c == '$' && i + 1 < n && (line.charAt(i + 1) == '"' || line.charAt(i + 1) == '\'')) {
                // Tekst met namen erin: $"Round {i} of {count}". Hij gaat dicht bij het aanhalingsteken dat niet in
                // de code tussen { en } staat (daar mag ook tekst staan: {name + "!"}). Het uit elkaar halen in tekst
                // en code doet Expr.
                int start = i;
                char quote = line.charAt(i + 1);
                i += 2;
                int depth = 0;
                char inner = 0;
                boolean closed = false;
                int from = i;
                while (i < n) {
                    char d = line.charAt(i);
                    if (inner != 0) {
                        if (d == '\\') {
                            i++;
                        } else if (d == inner) {
                            inner = 0;
                        }
                    } else if (depth > 0) {
                        if (d == '"' || d == '\'') {
                            inner = d;
                        } else if (d == '{') {
                            depth++;
                        } else if (d == '}') {
                            depth--;
                        }
                    } else if (d == '\\') {
                        i++;
                    } else if (d == quote) {
                        closed = true;
                        break;
                    } else if (d == '{') {
                        if (i + 1 < n && line.charAt(i + 1) == '{') {
                            i++; // {{ is een gewone { in de tekst
                        } else {
                            depth = 1;
                        }
                    }
                    i++;
                }
                if (!closed) {
                    throw new LogicException(depth > 0 ? "A { in $\"...\" is missing its }"
                            : "A text is missing its closing quote " + quote);
                }
                tokens.add(new Token(Type.TEMPLATE, line.substring(from, Math.min(i, n)), 0, start));
                i++; // het sluitende aanhalingsteken
            } else if (c == '"' || c == '\'') {
                int start = i;
                char quote = c;
                StringBuilder text = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < n) {
                    char d = line.charAt(i);
                    if (d == '\\' && i + 1 < n) {
                        text.append(line.charAt(i + 1));
                        i += 2;
                    } else if (d == quote) {
                        closed = true;
                        i++;
                        break;
                    } else {
                        text.append(d);
                        i++;
                    }
                }
                if (!closed) {
                    throw new LogicException("A text is missing its closing quote " + quote);
                }
                tokens.add(new Token(Type.STRING, text.toString(), 0, start));
            } else {
                String two = i + 1 < n ? line.substring(i, i + 2) : "";
                if (two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=")) {
                    tokens.add(new Token(Type.SYMBOL, two, 0, i));
                    i += 2;
                } else if ("()<>,:+-*/".indexOf(c) >= 0) {
                    // Haakjes, vergelijken, komma, dubbele punt, en rekenen (+ - * /). Een minteken
                    // voor een getal (-20) is ook een los stukje: de voorwaarde maakt er -20 van.
                    tokens.add(new Token(Type.SYMBOL, String.valueOf(c), 0, i));
                    i++;
                } else if (c == '=') {
                    // Eén = geeft een naam een waarde (count = 0). In een if zegt de voorwaarde zelf
                    // "Use == to compare".
                    tokens.add(new Token(Type.SYMBOL, "=", 0, i));
                    i++;
                } else if (c == '&' || c == '|' || c == '!') {
                    throw new LogicException("Use the words and, or, not instead of " + c);
                } else {
                    throw new LogicException("The sign " + c + " is not allowed here");
                }
            }
        }
        tokens.add(new Token(Type.END, "", 0, n));
        return tokens;
    }
}
