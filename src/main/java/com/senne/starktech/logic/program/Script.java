package com.senne.starktech.logic.program;

import java.util.List;

/**
 * Een diagram als boom, zoals de code hem leest (zie ScriptParser) en schrijft (ScriptPrinter):
 * een lijst van "when"-blokken, elk met de regels eronder.
 * <pre>
 * when keycard("Lab door"):          Handler(KEYCARD, "Lab door")
 *     if card.level &gt;= 3:            If("card.level &gt;= 3", yes, no)
 *         accept()                   Action(ACCEPT)
 *     else:
 *         deny()                     Action(DENY)
 * </pre>
 * Elk onderdeel onthoudt {@code ref}: in de code het regelnummer, in de blokjes het nummer van het
 * blokje. Zo kan een foutmelding zeggen waar het misging.
 */
public record Script(List<Handler> handlers) {
    /** Eén "when ...:" met de regels eronder. */
    public record Handler(Op trigger, String text, double number, List<Stmt> body, int ref) {}

    /** Een regel onder een when: een actie of een if. */
    public sealed interface Stmt {
        int ref();
    }

    /** Een actie, zoals {@code pulse("Vault door", 3)}. {@code text} is leeg en {@code number} 0 als hij ze niet heeft. */
    public record Action(Op op, String text, double number, int ref) implements Stmt {}

    /** Een if met wat er bij "yes" en bij "no" (else) gebeurt. {@code condition} is de voorwaarde als nette tekst. */
    public record If(String condition, List<Stmt> yes, List<Stmt> no, int ref) implements Stmt {}

    /** Hoeveel blokjes dit diagram kost (elke when, if en actie is er één): zo vol raakt je SSD. */
    public int blocks() {
        int count = 0;
        for (Handler handler : handlers) {
            count += 1 + blocks(handler.body());
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
