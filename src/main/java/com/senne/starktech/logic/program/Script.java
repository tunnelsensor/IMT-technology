package com.senne.starktech.logic.program;

import java.util.ArrayList;
import java.util.List;

/**
 * Een diagram als boom, zoals de code hem leest (zie ScriptParser) en schrijft (ScriptPrinter):
 * een lijst van "when"-blokken en eigen blokken ("def"), elk met de regels eronder, in de volgorde
 * waarin ze in de code staan.
 * <pre>
 * def open_lab():                    Function("open_lab")
 *     accept()                       Action(ACCEPT)
 *     pulse("Vault door", 3)         Action(PULSE, "Vault door", 3)
 *
 * when keycard("Lab door"):          Handler(KEYCARD, "Lab door")
 *     if card.level &gt;= 3:            If("card.level &gt;= 3", yes, no)
 *         open_lab()                 Action(CALL, "open_lab")
 *     else:
 *         deny()                     Action(DENY)
 * </pre>
 * Elk onderdeel onthoudt {@code ref}: in de code het regelnummer, in de blokjes het nummer van het
 * blokje. Zo kan een foutmelding zeggen waar het misging.
 */
public record Script(List<Top> items) {
    /** Iets aan de linkerkant van de code: een when of een def, met de regels eronder. */
    public sealed interface Top {
        List<Stmt> body();

        int ref();
    }

    /** Eén "when ...:" met de regels eronder. */
    public record Handler(Op trigger, String text, double number, List<Stmt> body, int ref) implements Top {}

    /** Eén "def naam():" (je eigen blok) met de regels eronder. */
    public record Function(String name, List<Stmt> body, int ref) implements Top {}

    /**
     * {@code count = 0} helemaal links (hoofdstuk 20): een naam voor het hele programma. {@code value}
     * is de waarde als nette code. Er staan geen regels onder.
     */
    public record Global(String name, String value, int ref) implements Top {
        @Override
        public List<Stmt> body() {
            return List.of();
        }
    }

    /** Een regel onder een when of def: een actie, een if, of een naam een waarde geven. */
    public sealed interface Stmt {
        int ref();
    }

    /** {@code count = count + 1} (hoofdstuk 20): een naam een waarde geven. {@code value} is nette code. */
    public record Assign(String name, String value, int ref) implements Stmt {}

    /**
     * Een actie, zoals {@code pulse("Vault door", 3)}. {@code text} is leeg en {@code number} 0 als hij
     * ze niet heeft. Een eigen blok gebruiken ({@code open_lab()}) is een actie CALL met de naam als tekst.
     */
    public record Action(Op op, String text, double number, int ref) implements Stmt {}

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
            }
        }
        return count;
    }
}
