package com.senne.starktech.logic.program;

import net.minecraft.nbt.CompoundTag;

/**
 * Eén diagram op de server: een naam ("Lab", "Lights"), of hij aan staat, en zijn blokjes. In de
 * app Logic zie je ze als een lijstje links; elk diagram kan meerdere "when"-blokken hebben.
 *
 * Dit object verandert niet: opslaan maakt een nieuw diagram. Zo kan een diagram dat nog bezig is
 * (aan het wachten met wait()) rustig afmaken met de blokjes waarmee het begon.
 */
public record LogicProgram(String name, boolean enabled, LogicGraph graph) {
    /** Zo lang mag de naam van een diagram hooguit zijn. */
    public static final int MAX_NAME = 24;

    public LogicProgram withEnabled(boolean on) {
        return new LogicProgram(name, on, graph);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putBoolean("On", enabled);
        tag.put("Graph", graph.save());
        return tag;
    }

    public static LogicProgram load(CompoundTag tag) {
        return new LogicProgram(tag.getString("Name"), !tag.contains("On") || tag.getBoolean("On"),
                LogicGraph.load(tag.getCompound("Graph")));
    }
}
