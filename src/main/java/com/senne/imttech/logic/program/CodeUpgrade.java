package com.senne.imttech.logic.program;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zet bewaarde code van vóór een verandering in de taal om, zodat een oude wereld blijft werken.
 *
 * Hoofdstuk 114 (15 november, jouw wens: "zoals in C#"): de parameters van een def schrijf je nu met het soort-woord
 * vóór de naam en zonder dubbele punt: {@code def open_door(string door, float secs):}. Tot dan was het zoals in Python:
 * {@code def open_door(door: string, secs: float):}. Code die nog zo bewaard is (in een diagram, een robot, een RC Car,
 * een Training End), draaien we bij het laden om. Al de rest blijft staan zoals jij hem schreef: je commentaar, je lege
 * regels, en ook {@code count: int = 0} (een naam met een soort is niet veranderd).
 */
public final class CodeUpgrade {
    private CodeUpgrade() {}

    /** {@code door: string} (een oude parameter): de naam, dan een dubbele punt, dan een soort-woord. */
    private static final Pattern OLD_PARAM = Pattern.compile(
            "(?<![A-Za-z0-9_])([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*(int|float|string|bool)(?![A-Za-z0-9_])");
    /**
     * Een regel met een def: alles tot en met de {@code (}, wat tussen de haakjes staat, en de rest (de {@code ):} en
     * eventueel commentaar). DOTALL zodat een {@code \r} aan het einde (Windows) bij de rest hoort.
     */
    private static final Pattern DEF_LINE = Pattern.compile(
            "^(\\s*def\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\()([^)]*)(\\).*)$", Pattern.DOTALL);

    /**
     * De parameters van één def, zoals ze tussen de haakjes staan (of in het vak Parameters van een DEF-blokje):
     * {@code door: string, times} wordt {@code string door, times}. Wat al goed staat, blijft hetzelfde.
     */
    public static String params(String text) {
        if (text.indexOf(':') < 0) {
            return text; // de snelle weg: zonder dubbele punt staat er niets ouds
        }
        return OLD_PARAM.matcher(text).replaceAll("$2 $1");
    }

    /**
     * Elke def-regel in deze code met zijn parameters omgedraaid ({@link #params}). Geeft dezelfde tekst terug als er
     * niets te veranderen was.
     */
    public static String source(String source) {
        if (source.indexOf(':') < 0 || !source.contains("def")) {
            return source;
        }
        String[] lines = source.split("\n", -1);
        boolean changed = false;
        for (int i = 0; i < lines.length; i++) {
            Matcher def = DEF_LINE.matcher(lines[i]);
            if (def.matches()) {
                String params = params(def.group(2));
                if (!params.equals(def.group(2))) {
                    lines[i] = def.group(1) + params + def.group(3);
                    changed = true;
                }
            }
        }
        return changed ? String.join("\n", lines) : source;
    }
}
