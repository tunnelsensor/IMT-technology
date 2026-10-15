package com.senne.starktech.logic.program;

import java.util.ArrayList;
import java.util.List;

/**
 * Knipt één regel code in stukjes ("tokens"): woorden ({@code keycard}, {@code card.level}),
 * getallen ({@code 3}, {@code 0.5}), tekst tussen aanhalingstekens ({@code "Lab door"}) en tekens
 * ({@code ==}, {@code (}, {@code :}, {@code +}). Een {@code #} begint commentaar: de rest van de
 * regel telt niet.
 *
 * Klopt er iets niet (een vreemd teken, een tekst zonder sluitend aanhalingsteken), dan gooit hij
 * een {@link LogicException} met uitleg in begrijpelijk Engels.
 */
public final class Lexer {
    private Lexer() {}

    /** Soorten stukjes. */
    public enum Type { NAME, NUMBER, STRING, SYMBOL, END }

    /**
     * Eén stukje: soort, de tekst (bij STRING zonder aanhalingstekens), het getal (bij NUMBER), en
     * waar het op de regel begint (voor foutmeldingen).
     */
    public record Token(Type type, String text, double number, int column) {
        public boolean is(String symbolOrName) {
            return (type == Type.SYMBOL || type == Type.NAME) && text.equals(symbolOrName);
        }

        @Override
        public String toString() {
            return switch (type) {
                case STRING -> "\"" + text + "\"";
                case END -> "the end of the line";
                default -> text;
            };
        }
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
                    throw new LogicException("Use == to compare (one = is not allowed here)");
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
