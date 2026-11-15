package com.senne.imttech.logic.program;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * De soorten die je bij een naam of een parameter kunt schrijven (hoofdstuk 43, jouw wens: "variabelen: string, int,
 * bool"). Bij een naam zoals in Python met een dubbele punt: {@code lockdown: bool = false}, {@code count: int = 0}. Bij
 * een parameter sinds hoofdstuk 114 zoals in C# (jouw wens): het soort-woord vóór de naam, {@code def open_door(string door):}.
 * <ul>
 *   <li><b>int</b>: een heel getal. Krijgt hij een getal met een komma, dan wordt het naar beneden afgerond
 *       (7 / 2 wordt 3), zoals in C++.</li>
 *   <li><b>float</b>: een getal met een komma.</li>
 *   <li><b>string</b>: tekst.</li>
 *   <li><b>bool</b>: waar of onwaar.</li>
 * </ul>
 * Zonder soort-woord raadt de taal de soort uit de waarde, zoals vroeger.
 */
public enum VarType {
    INT("int", Expr.Type.NUMBER, "a whole number (7 / 2 becomes 3)"),
    FLOAT("float", Expr.Type.NUMBER, "a number with a decimal point"),
    STRING("string", Expr.Type.TEXT, "text, between quotes"),
    BOOL("bool", Expr.Type.BOOL, "true or false");

    private final String word;
    private final Expr.Type type;
    private final String help;

    VarType(String word, Expr.Type type, String help) {
        this.word = word;
        this.type = type;
        this.help = help;
    }

    /** Het woord in de code: int, float, string, bool. */
    public String word() {
        return word;
    }

    /** De soort zoals de uitdrukkingen hem kennen (een int en een float zijn allebei een getal). */
    public Expr.Type exprType() {
        return type;
    }

    public String help() {
        return help;
    }

    /** De soort met dit woord, of null. */
    public static @Nullable VarType byWord(@Nullable String word) {
        if (word == null) {
            return null;
        }
        for (VarType type : values()) {
            if (type.word.equals(word)) {
                return type;
            }
        }
        return null;
    }

    /** De soort die bij deze waarde past (voor een naam zonder soort-woord): een heel getal is een int. */
    public static VarType of(Object value) {
        if (value instanceof String) {
            return STRING;
        }
        if (value instanceof Boolean) {
            return BOOL;
        }
        double number = value instanceof Double d ? d : 0;
        return number == Math.rint(number) ? INT : FLOAT;
    }

    /** Het soort-woord dat bij deze soort uitdrukking hoort (een getal is een float: die mag alles). */
    public static VarType of(Expr.Type type) {
        return switch (type) {
            case TEXT -> STRING;
            case BOOL -> BOOL;
            default -> FLOAT;
        };
    }

    /** Alle woorden, voor Ctrl+Spatie en de foutmeldingen: "int, float, string or bool". */
    public static String words() {
        List<String> words = new java.util.ArrayList<>();
        for (VarType type : values()) {
            words.add(type.word);
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " or " + words.getLast();
    }

    /** Past deze waarde bij deze soort? Een int wil een heel getal. */
    public boolean accepts(Expr.Type valueType, @Nullable Double literal) {
        if (valueType == Expr.Type.ANY) {
            return true;
        }
        if (valueType != type) {
            return false;
        }
        return this != INT || literal == null || literal == Math.rint(literal);
    }

    /** De waarde zoals deze soort hem bewaart: een int rondt naar beneden af (naar nul toe, zoals in C++). */
    public Object coerce(Object value) {
        if (this == INT && value instanceof Double d) {
            return (double) (long) d.doubleValue();
        }
        return value;
    }

    /** De lege waarde als code, voor de foutmeldingen: 0, 0.0, "", false. */
    public String emptyCode() {
        return switch (this) {
            case INT -> "0";
            case FLOAT -> "0.0";
            case STRING -> "\"\"";
            case BOOL -> "false";
        };
    }

    /** De lege waarde van deze soort: 0, "", false. */
    public Object empty() {
        return switch (this) {
            case STRING -> "";
            case BOOL -> false;
            default -> 0.0;
        };
    }
}
