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
        // Hoofdstuk 114: parameters van vóór die tijd (def open(door: string):) worden def open(string door): (de blokjes
        // zet LogicGraph.load net zo om, dus de code past er nog bij en je commentaar blijft staan).
        String source = CodeUpgrade.source(tag.getString("Source"));
        ProgramKind kind = ProgramKind.byName(tag.getString("Kind"));
        LogicGraph graph = LogicGraph.load(tag.getCompound("Graph"));
        boolean renamed = graph.renameReservedDefs();
        if (renamed || graph.convertedLoops()) {
            // Een oud diagram met een def die nu een woord van de taal is (repeat...), of (16 oktober) met een
            // repeat(3) of forever, die nu een for en een while true zijn: de bewaarde code past niet meer. Een lus
            // zetten we in de code zelf om (dan blijft je commentaar staan); lukt dat niet precies, of kreeg een def
            // een andere naam, dan schrijven we de code opnieuw uit de blokjes (je commentaar valt dan weg).
            String printed = ScriptPrinter.print(GraphConverter.toScript(graph));
            String converted = renamed ? null : convertLoops(source, graph, kind, printed);
            source = converted != null ? converted : printed;
        }
        return new LogicProgram(tag.getString("Name"), !tag.contains("On") || tag.getBoolean("On"),
                graph, source.length() > MAX_SOURCE ? "" : source, kind);
    }

    /** {@code repeat(3):}, met wat ervoor staat (inspringen) en erna (commentaar). */
    private static final java.util.regex.Pattern REPEAT =
            java.util.regex.Pattern.compile("^(\\s*)repeat\\s*\\((.*)\\)\\s*:(\\s*#.*)?$");
    /** {@code forever:}, met wat ervoor en erna staat. */
    private static final java.util.regex.Pattern FOREVER = java.util.regex.Pattern.compile("^(\\s*)forever\\s*:(\\s*#.*)?$");

    /**
     * Zet de lussen in de bewaarde code om zoals de blokjes omgezet zijn (16 oktober): de n-de {@code repeat(3):} wordt
     * {@code for <naam> in range(3):} met de naam die de n-de for in de blokjes kreeg, en {@code forever:} wordt
     * {@code while true:}. Al de rest (je commentaar, lege regels) blijft. Geeft null als de code daarna niet precies
     * bij de blokjes past ({@code printed}): dan gebruiken we die.
     */
    private static @org.jetbrains.annotations.Nullable String convertLoops(String source, LogicGraph graph, ProgramKind kind,
                                                                         String printed) {
        if (source.isBlank()) {
            return null;
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Script.Top item : GraphConverter.toScript(graph).items()) {
            forNames(item.body(), names);
        }
        StringBuilder out = new StringBuilder();
        int next = 0;
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            java.util.regex.Matcher repeat = REPEAT.matcher(line);
            java.util.regex.Matcher forever = FOREVER.matcher(line);
            if (repeat.matches()) {
                if (next >= names.size()) {
                    return null;
                }
                line = repeat.group(1) + "for " + names.get(next++) + " in range(" + repeat.group(2).strip() + "):"
                        + (repeat.group(3) == null ? "" : repeat.group(3));
            } else if (forever.matches()) {
                line = forever.group(1) + "while true:" + (forever.group(2) == null ? "" : forever.group(2));
            }
            out.append(line).append(i < lines.length - 1 ? "\n" : "");
        }
        String converted = out.toString();
        try {
            return ScriptPrinter.print(ScriptParser.parse(converted, kind)).equals(printed) ? converted : null;
        } catch (LogicException e) {
            return null;
        }
    }

    /** De namen van de for-lussen in de volgorde van de code (ook in de takken van een if en in lussen). */
    private static void forNames(java.util.List<Script.Stmt> statements, java.util.List<String> out) {
        for (Script.Stmt statement : statements) {
            if (statement instanceof Script.Loop loop) {
                if (loop.op() == Op.FOR) {
                    out.add(loop.var());
                }
                forNames(loop.body(), out);
            } else if (statement instanceof Script.If branch) {
                forNames(branch.yes(), out);
                forNames(branch.no(), out);
            }
        }
    }
}
