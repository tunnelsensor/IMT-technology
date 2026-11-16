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
 * <p>
 * Hoofdstuk 130 (de SD-kaart): een programma kan <b>op slot</b> zijn ({@code locked}). Dat gebeurt als iemand het op slot
 * op een SD-kaart zet om het door te geven of te verkopen (jouw wens: "als het opslot is kun je het enkel gebruiken zoals
 * start stop bij een rc car... je kunt de code niet zien anders zou je die kunnen overnemen"). Wie het installeert, kan
 * het laten draaien, aan- en uitzetten, op een robot of RC Car laden en weggooien, maar nooit de code zien of veranderen,
 * en het ook niet weer op een kaart zetten. {@code author} is wie het op slot zette ("" bij een open programma). De
 * blokjes en de code staan wel op de server (die moet het kunnen uitvoeren), maar gaan nooit naar iemands spel: zie
 * {@link #saveForClient()}.
 */
public record LogicProgram(String name, boolean enabled, LogicGraph graph, String source, ProgramKind kind, boolean locked,
                           String author) {
    /** Zo lang mag de naam van een diagram hooguit zijn. */
    public static final int MAX_NAME = 24;
    /** Zo lang mag de bewaarde code hooguit zijn (tekens). */
    public static final int MAX_SOURCE = 8000;
    /** Zo lang mag de naam van wie een programma op slot zette hooguit zijn (een Minecraft-naam is hooguit 16). */
    public static final int MAX_AUTHOR = 32;

    public LogicProgram {
        author = author == null ? "" : author.length() > MAX_AUTHOR ? author.substring(0, MAX_AUTHOR) : author;
    }

    /** Een diagram op de server (zoals vóór hoofdstuk 23). */
    public LogicProgram(String name, boolean enabled, LogicGraph graph, String source) {
        this(name, enabled, graph, source, ProgramKind.SERVER);
    }

    /** Een open programma (zoals vóór hoofdstuk 130: niet op slot). */
    public LogicProgram(String name, boolean enabled, LogicGraph graph, String source, ProgramKind kind) {
        this(name, enabled, graph, source, kind, false, "");
    }

    public LogicProgram withEnabled(boolean on) {
        return new LogicProgram(name, on, graph, source, kind, locked, author);
    }

    /** Hetzelfde programma met een andere naam (hoofdstuk 130: installeren van een SD-kaart als die naam al bestaat). */
    public LogicProgram withName(String newName) {
        return new LogicProgram(newName, enabled, graph, source, kind, locked, author);
    }

    /** Hetzelfde programma, op slot (of weer open), met wie het op slot zette. */
    public LogicProgram withLock(boolean lock, String by) {
        return new LogicProgram(name, enabled, graph, source, kind, lock, lock ? by : "");
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
        if (locked) {
            tag.putBoolean("Locked", true);
            tag.putString("Author", author);
        }
        return tag;
    }

    /**
     * Wat het spel van een speler van dit programma krijgt (hoofdstuk 130). Een open programma: alles ({@link #save()}). Een
     * programma op slot: alleen zijn naam, aan of uit, zijn soort, dat het op slot is en van wie, en hoeveel blokjes het
     * heeft (voor wat het op je SSD's inneemt), maar <b>geen blokjes en geen code</b>: uit de blokjes kun je de code weer
     * maken, dus die zijn net zo geheim. In je spel wordt het zo een leeg programma met {@code locked} = true.
     */
    public CompoundTag saveForClient() {
        if (!locked) {
            return save();
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putBoolean("On", enabled);
        tag.put("Graph", new CompoundTag());
        if (kind != ProgramKind.SERVER) {
            tag.putString("Kind", kind.name());
        }
        tag.putBoolean("Locked", true);
        tag.putString("Author", author);
        tag.putInt("Blocks", graph.size());
        return tag;
    }

    /**
     * Een opgeslagen programma over het netwerk (in het item van een robot of drone): een programma op slot gaat zonder
     * blokjes en code (zie {@link #redact}), een open programma zoals het is. Op de schijf blijft alles staan.
     */
    public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, CompoundTag> NETWORK_TAG =
            net.minecraft.network.codec.ByteBufCodecs.COMPOUND_TAG.map(tag -> tag, LogicProgram::redact);

    /** Is dit een (opgeslagen) programma op slot? Zonder het helemaal in te lezen (voor de netwerkcodecs van robots). */
    public static boolean isLocked(CompoundTag tag) {
        return tag.getBoolean("Locked");
    }

    /** Wat er van een opgeslagen programma op slot over het netwerk mag (zie {@link #saveForClient()}), zonder het in te lezen. */
    public static CompoundTag redact(CompoundTag tag) {
        if (!isLocked(tag)) {
            return tag;
        }
        CompoundTag safe = new CompoundTag();
        safe.putString("Name", tag.getString("Name"));
        safe.putBoolean("On", !tag.contains("On") || tag.getBoolean("On"));
        safe.put("Graph", new CompoundTag());
        if (tag.contains("Kind")) {
            safe.putString("Kind", tag.getString("Kind"));
        }
        safe.putBoolean("Locked", true);
        safe.putString("Author", tag.getString("Author"));
        // De verwijzing naar het hele programma op de server (logic/LockedPrograms): een vingerafdruk, geen code.
        if (tag.contains("Ref")) {
            safe.putString("Ref", tag.getString("Ref"));
        }
        return safe;
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
                graph, source.length() > MAX_SOURCE ? "" : source, kind, tag.getBoolean("Locked"),
                tag.getBoolean("Locked") ? tag.getString("Author") : "");
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
            // Zonder de controle (hoofdstuk 115): ook code met een fout van een latere regel houdt zo zijn commentaar.
            return ScriptPrinter.print(ScriptParser.parseUnchecked(converted, kind)).equals(printed) ? converted : null;
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
