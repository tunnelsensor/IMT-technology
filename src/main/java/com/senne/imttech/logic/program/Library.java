package com.senne.imttech.logic.program;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Wat de <b>global scripts</b> van een kern aan de andere diagrammen geven (hoofdstuk 43, jouw wens: "global
 * functions: die kun je in al je scripts gebruiken" en "baseLockdown = true, dan kun je met een if kijken of een
 * keycardlezer mag werken").
 *
 * Een global script begint met de regel {@code global} en bevat alleen defs en namen van links. Elke def erin
 * ({@code def activate_alarm():}) kan elk diagram van dezelfde kern aanroepen; elke naam erin ({@code lockdown: bool =
 * false}) is een <b>gedeelde naam</b>: elk diagram leest en verandert dezelfde waarde, en de kern bewaart hem.
 *
 * De bibliotheek is wat de controle (ScriptChecker) en Ctrl+Spatie daarvan moeten weten: welke defs er zijn (met hun
 * parameters en hun stappen, want {@code accept()} in zo'n def werkt nog steeds alleen onder {@code when keycard}),
 * en welke gedeelde namen, met hun soort. Twee global scripts met dezelfde def of naam: de eerste in de lijst wint,
 * en de controle zegt het bij de tweede.
 */
public final class Library {
    public static final Library EMPTY = new Library(List.of());

    /** Eén global script: zijn naam (het diagram) en zijn inhoud. */
    public record Source(String script, Script contents) {}

    /** Een def uit een global script: het script waar hij in staat, en de def zelf. */
    public record Def(String script, Script.Function function) {}

    /** Een gedeelde naam: het script waar hij in staat, zijn soort, en de regel die hem zijn eerste waarde geeft. */
    public record Shared(String script, VarType type, Script.Global declaration) {}

    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, Shared> shared = new LinkedHashMap<>();
    private final List<String> scripts = new ArrayList<>();

    public Library(List<Source> sources) {
        for (Source source : sources) {
            if (!source.contents().global()) {
                continue;
            }
            scripts.add(source.script());
            for (Script.Function function : source.contents().functions()) {
                defs.putIfAbsent(function.name(), new Def(source.script(), function));
            }
            for (Script.Global global : source.contents().globals()) {
                shared.putIfAbsent(global.name(), new Shared(source.script(), typeOf(global), global));
            }
        }
    }

    /**
     * De bibliotheek van deze diagrammen: alle global scripts erin, behalve {@code except} (het diagram dat je nu
     * controleert: zijn eigen defs tellen niet als "van een ander script").
     */
    public static Library of(Collection<LogicProgram> programs, @Nullable String except) {
        List<Source> sources = new ArrayList<>();
        for (LogicProgram program : programs) {
            if (program.graph().global() && (except == null || !program.name().equalsIgnoreCase(except))) {
                sources.add(new Source(program.name(), GraphConverter.toScript(program.graph())));
            }
        }
        return sources.isEmpty() ? EMPTY : new Library(sources);
    }

    /** De soort van een naam van links: het soort-woord als het er staat, anders geraden uit de waarde. */
    public static VarType typeOf(Script.Global global) {
        VarType type = VarType.byWord(global.type());
        if (type != null) {
            return type;
        }
        try {
            Expr value = Expr.parseLenient(global.value(), false);
            if (value instanceof Expr.Num num) {
                return VarType.of((Object) num.value());
            }
            return VarType.of(Expr.typeOf(value));
        } catch (LogicException e) {
            return VarType.FLOAT;
        }
    }

    public boolean isEmpty() {
        return defs.isEmpty() && shared.isEmpty();
    }

    /** De namen van de global scripts, in de volgorde van de lijst. */
    public List<String> scripts() {
        return scripts;
    }

    /** Alle defs uit de global scripts, op naam. */
    public Map<String, Def> defs() {
        return defs;
    }

    public @Nullable Def def(String name) {
        return defs.get(name);
    }

    /** Alle gedeelde namen, op naam. */
    public Map<String, Shared> shared() {
        return shared;
    }

    public @Nullable Shared sharedName(String name) {
        return shared.get(name);
    }

    /** De gedeelde namen met hun soort, zoals de strenge controle ze wil (naam -> soort van de uitdrukking). */
    public Map<String, Expr.Type> sharedScope() {
        Map<String, Expr.Type> scope = new LinkedHashMap<>();
        shared.forEach((name, entry) -> scope.put(name, entry.type().exprType()));
        return scope;
    }

    /** De defs van de global scripts als functies, zoals de controle ze wil (naam -> def). */
    public Map<String, Script.Function> functions() {
        Map<String, Script.Function> functions = new LinkedHashMap<>();
        defs.forEach((name, def) -> functions.put(name, def.function()));
        return functions;
    }
}
