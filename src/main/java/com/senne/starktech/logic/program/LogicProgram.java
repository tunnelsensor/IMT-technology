package com.senne.starktech.logic.program;

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
 */
public record LogicProgram(String name, boolean enabled, LogicGraph graph, String source) {
    /** Zo lang mag de naam van een diagram hooguit zijn. */
    public static final int MAX_NAME = 24;
    /** Zo lang mag de bewaarde code hooguit zijn (tekens). */
    public static final int MAX_SOURCE = 8000;

    public LogicProgram withEnabled(boolean on) {
        return new LogicProgram(name, on, graph, source);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putBoolean("On", enabled);
        tag.put("Graph", graph.save());
        if (!source.isEmpty()) {
            tag.putString("Source", source);
        }
        return tag;
    }

    public static LogicProgram load(CompoundTag tag) {
        String source = tag.getString("Source");
        return new LogicProgram(tag.getString("Name"), !tag.contains("On") || tag.getBoolean("On"),
                LogicGraph.load(tag.getCompound("Graph")), source.length() > MAX_SOURCE ? "" : source);
    }
}
