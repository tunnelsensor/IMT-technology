package com.senne.imttech.logic.program;

import net.minecraft.nbt.CompoundTag;

/**
 * Eén diagram op de server: een naam ("Lab", "Lights"), of hij aan staat, zijn blokjes, en de code
 * zoals jij hem schreef ({@code source}). In de app Logic zie je ze als een lijstje links; elk diagram
 * kan meerdere "when"-blokken hebben.
 *
 * De server voert de BLOKJES uit. De code bewaren we erbij zodat je commentaar (#) en je eigen
 * opmaak niet verdwijnen; de app laat hem alleen zien als hij nog precies bij de blokjes past (heb je
 * daarna in de blokjes iets veranderd, dan schrijft de app de code opnieuw uit de blokjes).
 *
 * Dit object verandert niet: opslaan maakt een nieuw diagram. Zo kan een diagram dat nog bezig is
 * (aan het wachten met wait()) rustig afmaken met de blokjes waarmee het begon.
 *
 * Hoofdstuk 23: elk programma heeft een soort ({@link ProgramKind}). Een robotprogramma staat ook op de
 * server (het telt mee voor je SSD's), maar de server voert het niet uit: dat doet de robot.
 */
public record LogicProgram(String name, boolean enabled, LogicGraph graph, String source, ProgramKind kind) {
    /** Zo lang mag de naam van een diagram hooguit zijn. */
    public static final int MAX_NAME = 24;
    /** Zo lang mag de bewaarde code hooguit zijn (tekens). */
    public static final int MAX_SOURCE = 8000;

    /** Een diagram op de server (zoals vóór hoofdstuk 23). */
    public LogicProgram(String name, boolean enabled, LogicGraph graph, String source) {
        this(name, enabled, graph, source, ProgramKind.SERVER);
    }

    public LogicProgram withEnabled(boolean on) {
        return new LogicProgram(name, on, graph, source, kind);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putBoolean("On", enabled);
        tag.put("Graph", graph.save());
        if (!source.isEmpty()) {
            tag.putString("Source", source);
        }
        if (kind != ProgramKind.SERVER) {
            tag.putString("Kind", kind.name());
        }
        return tag;
    }

    public static LogicProgram load(CompoundTag tag) {
        String source = tag.getString("Source");
        LogicGraph graph = LogicGraph.load(tag.getCompound("Graph"));
        if (graph.renameReservedDefs()) {
            // Een oud diagram met een def die nu een woord van de taal is (repeat...): die kreeg een andere
            // naam, dus de bewaarde code past niet meer. We schrijven hem opnieuw uit de blokjes.
            source = ScriptPrinter.print(GraphConverter.toScript(graph));
        }
        return new LogicProgram(tag.getString("Name"), !tag.contains("On") || tag.getBoolean("On"),
                graph, source.length() > MAX_SOURCE ? "" : source, ProgramKind.byName(tag.getString("Kind")));
    }
}
