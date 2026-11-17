package com.senne.imttech.sdcard;

import com.senne.imttech.logic.LogicServer;
import com.senne.imttech.logic.LogicStats;
import com.senne.imttech.logic.LogicStore;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.Library;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.LogicGraph;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.logic.program.ScriptPrinter;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.security.os.SystemDefaults;
import com.senne.imttech.security.os.VirtualFs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Wat je op de desktop met een SD-kaart doet (hoofdstuk 130). Jouw keuze: geen eigen app, maar in de apps die je al kent:
 * <ul>
 *   <li><b>Logic</b>: onder je programma's een lijstje "SD card" met wat er op de kaart staat. Een programma van je server
 *       zet je erop, <b>open</b> (de ander krijgt de code) of <b>op slot</b> (de ander kan het alleen gebruiken: jouw wens
 *       "als het opslot is kun je het enkel gebruiken zoals start stop bij een rc car... je kunt de code niet zien anders
 *       zou je die kunnen overnemen"). Een programma van de kaart installeer je op je server (Install).</li>
 *   <li><b>Files</b>: de kaart is de map {@code sd} (helemaal bovenaan). Paden op de kaart beginnen met {@link #PREFIX}
 *       ({@code sd:/notes/plan.txt}). Slepen tussen je systeem en de kaart kopieert (zoals tussen twee schijven).</li>
 * </ul>
 * De server doet alles: het scherm stuurt alleen namen en paden, nooit code (zo kan een programma op slot nooit via jouw
 * spel van of naar de kaart). Elke opdracht geeft een {@link Result}: een korte zin voor onderaan de desktop.
 */
public final class SdDesktop {
    private SdDesktop() {}

    /** Hoe een pad op de kaart begint in de app Files: {@code sd:/notes/plan.txt}. */
    public static final String PREFIX = "sd:";

    /** Hoe het afliep: een korte zin voor onderaan de desktop, en of het fout ging. Null-tekst = niets te melden. */
    public record Result(@Nullable String text, boolean error) {
        static Result ok(String text) {
            return new Result(text, false);
        }

        static Result fail(String text) {
            return new Result(text, true);
        }
    }

    /** De kaart in het blok van deze desktop: het blok, het voorwerp, de maat, en wat erop staat (null = nog leeg). */
    public record View(SdCardHolder holder, ItemStack stack, SdCardItem item, @Nullable SdCards.Card card) {}

    /** De kaart in dit blok, of null als er geen in zit. */
    public static @Nullable View view(ServerLevel level, @Nullable SdCardHolder holder) {
        if (holder == null) {
            return null;
        }
        ItemStack stack = holder.sdCard();
        if (!(stack.getItem() instanceof SdCardItem item)) {
            return null;
        }
        SdCardData data = SdCardItem.data(stack);
        return new View(holder, stack, item, data == null ? null : SdCards.get(level).card(data.id()));
    }

    // ================================================================ Naar het scherm

    /**
     * Een sleutel die verandert als het scherm de kaart opnieuw moet krijgen: een andere kaart, iets erop veranderd, of
     * geen kaart meer. Zo sturen we de lijst alleen als er iets veranderde (net als de mappen en de diagrammen).
     */
    public static String key(@Nullable View view) {
        if (view == null) {
            return "none";
        }
        String name = view.stack().has(DataComponents.CUSTOM_NAME) ? view.stack().getHoverName().getString() : "";
        return view.item().gigabytes() + "|" + (view.card() == null ? "blank" : view.card().id() + ":" + view.card().revision())
                + "|" + name;
    }

    /**
     * Wat het scherm van de kaart krijgt: of er een in zit, zijn naam (uit het aambeeld, anders leeg), de maat, wat erop
     * past, de programma's (naam, soort, op slot, van wie, hoeveel blokjes, global script) en de bestanden (met
     * {@link #PREFIX} voor het pad). Nooit code.
     */
    public static CompoundTag snapshot(@Nullable View view) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("in", view != null);
        if (view == null) {
            return tag;
        }
        tag.putString("name", view.stack().has(DataComponents.CUSTOM_NAME) ? view.stack().getHoverName().getString() : "");
        tag.putInt("gb", view.item().gigabytes());
        tag.putInt("maxP", view.item().maxPrograms());
        tag.putInt("maxF", view.item().maxFiles());
        ListTag programs = new ListTag();
        ListTag files = new ListTag();
        if (view.card() != null) {
            for (LogicProgram program : view.card().programs()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("n", program.name());
                entry.putString("k", program.kind().name());
                entry.putBoolean("l", program.locked());
                entry.putString("a", program.author());
                entry.putInt("b", program.graph().size());
                entry.putBoolean("g", program.graph().global());
                programs.add(entry);
            }
            for (Map.Entry<String, VirtualFs.Node> entry : view.card().files().walk().entrySet()) {
                CompoundTag item = new CompoundTag();
                item.putString("p", PREFIX + entry.getKey());
                if (entry.getValue().isDir()) {
                    item.putBoolean("d", true);
                } else {
                    item.putInt("n", entry.getValue().content().length());
                }
                files.add(item);
            }
        }
        tag.put("p", programs);
        tag.put("f", files);
        return tag;
    }

    // ================================================================ Programma's (de app Logic)

    /**
     * Een programma van je server op de kaart zetten ({@code sd_copy}). {@code locked}: op slot (wie het krijgt, kan het
     * alleen gebruiken). Staat er al een programma met die naam op de kaart, dan wordt dat vervangen (zo werk je je
     * "product" bij).
     */
    public static Result copyToCard(ServerPlayer player, SecurityCoreBlockEntity core, @Nullable View view, String name,
                                    boolean locked) {
        if (view == null) {
            return Result.fail("There is no SD card in here");
        }
        LogicProgram program = core.logic().program(name);
        if (program == null) {
            return Result.fail("There is no program called " + name);
        }
        if (program.locked()) {
            return Result.fail(program.name() + " is locked: it can't go on a card again");
        }
        if (locked && program.graph().global()) {
            return Result.fail("A global script always goes open: your other programs need its names");
        }
        SdCards.Card existing = view.card();
        boolean replaces = existing != null && existing.program(program.name()) != null;
        int count = existing == null ? 0 : existing.programs().size();
        if (!replaces && count >= view.item().maxPrograms()) {
            return Result.fail("The SD card is full: it holds " + view.item().maxPrograms() + " programs");
        }
        ServerLevel level = player.serverLevel();
        SdCards.Card card = writable(level, view);
        String who = player.getGameProfile().getName();
        // Een echte kopie (LogicProgram.load maakt alles netjes), aan, en eventueel op slot met jouw naam erbij.
        card.put(LogicProgram.load(program.save()).withEnabled(true).withLock(locked, who));
        changed(level, view, card);
        core.logEvent(EventLog.Type.DEVICE, who + " copied the program \"" + program.name() + "\" to an SD card"
                + (locked ? " (locked)" : ""));
        return Result.ok((replaces ? "Replaced " : "Copied ") + program.name() + " on the SD card"
                + (locked ? " (locked)" : ""));
    }

    /**
     * Een programma van de kaart op deze server zetten ({@code sd_install}). Dezelfde controles als Save in Logic (zie
     * SecurityOs.saveProgram): klopt het hier (de global scripts van deze server), draait de server, past het nog. Een
     * diagram staat daarna uit: zet het zelf aan als je klaar bent (mijn invulling: een programma van een ander schakelt
     * niet zomaar je basis). Bestaat de naam al, dan krijgt het een nummer erbij ("Mine car 2"). Op slot blijft op slot.
     */
    public static Result install(ServerPlayer player, SecurityCoreBlockEntity core, @Nullable View view, String name) {
        LogicProgram program = view == null || view.card() == null ? null : view.card().program(name);
        if (program == null) {
            return Result.fail("There is no program called " + name + " on the SD card");
        }
        ServerLevel level = player.serverLevel();
        LogicStore store = core.logic();
        String newName = freeName(store, program.name());
        ProgramKind kind = program.kind();
        LogicGraph graph = program.graph();
        String source = program.source();
        if (kind == ProgramKind.CAR && !source.isBlank()) {
            // Zoals bij Save (hoofdstuk 119): een auto krijgt de code letterlijk, dus de blokjes komen uit die code.
            if (source.length() > LogicProgram.MAX_SOURCE) {
                return Result.fail("Not installed: an RC program can be at most " + LogicProgram.MAX_SOURCE + " characters");
            }
            try {
                graph = GraphConverter.toGraph(ScriptParser.parse(source, ProgramKind.CAR), graph);
            } catch (LogicException e) {
                return Result.fail("Not installed: " + (program.locked() ? "the locked program does not work here" : e.describe()));
            }
        }
        Library library = kind == ProgramKind.CAR ? Library.EMPTY : Library.of(store.programs(), newName);
        var problems = graph.problems(kind, library);
        if (!problems.isEmpty()) {
            // Een programma op slot: de melding van de controle noemt namen uit zijn code (een def, een gedeelde naam), en
            // die zijn geheim (gevonden door de nalezing: met een eigen global script kon je zo namen raden). Dan zeggen we
            // alleen wat je kunt doen: de open global scripts van de kaart eerst installeren, of het de maker vragen.
            return Result.fail("Not installed: " + (program.locked() ? lockedProblem(store, view.card(), program)
                    : problems.getFirst().message()));
        }
        LogicServer.Stats stats = LogicServer.stats(level, core);
        if (!stats.online()) {
            return Result.fail("Not installed: " + stats.problem());
        }
        if (store.programs().size() >= LogicStore.MAX_PROGRAMS) {
            return Result.fail("Not installed: a server holds at most " + LogicStore.MAX_PROGRAMS + " programs");
        }
        int used = store.blocks() + graph.size();
        if (used > stats.capacity()) {
            return Result.fail("Not enough storage: this needs " + LogicStats.bytes(LogicStats.blocksMb(used))
                    + ", your SSDs hold " + LogicStats.bytes(stats.ssdMb()));
        }
        // Een diagram op de server staat uit (een global script draait niet, dat mag aan blijven); een robot-, drone- of RC
        // program zet je niet aan of uit op de server.
        boolean on = kind != ProgramKind.SERVER || graph.global();
        store.put(null, new LogicProgram(newName, on, graph, source.length() > LogicProgram.MAX_SOURCE ? "" : source, kind,
                program.locked(), program.author()));
        core.setChanged();
        com.senne.imttech.logic.LogicDevices.refresh(level, core, player);
        String who = player.getGameProfile().getName();
        core.logEvent(EventLog.Type.DEVICE, who + " installed the program \"" + newName + "\" from an SD card"
                + (program.locked() ? " (locked by " + program.author() + ")" : ""));
        StringBuilder text = new StringBuilder("Installed ").append(newName);
        if (!newName.equals(program.name())) {
            text.append(" (that name was taken)");
        }
        if (!on) {
            text.append(": it is off, switch it on when you are ready");
        }
        String unknown = com.senne.imttech.security.os.SecurityOs.unknownDevice(level, core, graph);
        if (unknown != null) {
            text.append(". No device called \"").append(unknown).append("\" yet");
        }
        return new Result(text.toString(), unknown != null);
    }

    /**
     * Waarom een programma op slot hier niet past, zonder iets uit zijn code te noemen: staan er open global scripts op de
     * kaart die deze server nog niet heeft, dan die (hun namen zijn niet geheim); anders "vraag het de maker".
     */
    private static String lockedProblem(LogicStore store, SdCards.Card card, LogicProgram program) {
        List<String> missing = new ArrayList<>();
        for (LogicProgram other : card.programs()) {
            if (other.graph().global() && !other.locked() && store.program(other.name()) == null) {
                missing.add(other.name());
            }
        }
        if (!missing.isEmpty()) {
            return "install the global script " + String.join(", ", missing) + " from the card first";
        }
        return "the locked program does not fit this server (ask "
                + (program.author().isBlank() ? "its maker" : program.author()) + ")";
    }

    /** Een vrije naam op deze server: de naam zelf, of met een nummer erachter ("Mine car 2"), nooit langer dan mag. */
    static String freeName(LogicStore store, String name) {
        if (store.program(name) == null) {
            return name;
        }
        for (int number = 2; number < 1000; number++) {
            String suffix = " " + number;
            String base = name.length() + suffix.length() > LogicProgram.MAX_NAME
                    ? name.substring(0, LogicProgram.MAX_NAME - suffix.length()).strip() : name;
            if (store.program(base + suffix) == null) {
                return base + suffix;
            }
        }
        return name;
    }

    /** Een programma van de kaart gooien ({@code sd_delete}). */
    public static Result deleteFromCard(ServerPlayer player, SecurityCoreBlockEntity core, @Nullable View view, String name) {
        SdCards.Card card = view == null ? null : view.card();
        LogicProgram program = card == null ? null : card.program(name);
        if (program == null) {
            return Result.fail("There is no program called " + name + " on the SD card");
        }
        card.remove(program.name());
        changed(player.serverLevel(), view, card);
        return Result.ok("Deleted " + program.name() + " from the SD card");
    }

    /**
     * De code van een programma op de kaart, om te bekijken voor je het installeert ({@code sd_view}), of null: niet op de
     * kaart, of op slot (dan nooit).
     */
    public static @Nullable String code(@Nullable View view, String name) {
        LogicProgram program = view == null || view.card() == null ? null : view.card().program(name);
        if (program == null || program.locked()) {
            return null;
        }
        return program.source().isBlank() ? ScriptPrinter.print(GraphConverter.toScript(program.graph())) : program.source();
    }

    // ================================================================ Bestanden (de app Files)

    /** Staat dit pad (van het scherm) op de kaart? */
    public static boolean onCard(String path) {
        return path.startsWith(PREFIX);
    }

    /** Het pad op de kaart zelf (zonder {@link #PREFIX}), altijd netjes gemaakt (absoluut, zonder {@code ..}). */
    public static String cardPath(String path) {
        return VirtualFs.resolve("/", onCard(path) ? path.substring(PREFIX.length()) : path);
    }

    /** Raakt deze opdracht van Files de kaart (een van zijn paden begint met {@link #PREFIX})? */
    public static boolean touchesCard(CompoundTag args) {
        return onCard(args.getString("path")) || onCard(args.getString("from")) || onCard(args.getString("to"));
    }

    /** Het bestand op de kaart met dit pad (met {@link #PREFIX}), of null. */
    public static @Nullable VirtualFs.Node cardFile(@Nullable View view, String path) {
        if (view == null || view.card() == null) {
            return null;
        }
        VirtualFs.Node node = view.card().files().get(cardPath(path));
        return node == null || node.isDir() ? null : node;
    }

    /**
     * Een opdracht van Files of Notes waarvan een pad op de kaart staat: een map, een nieuw bestand, bewaren, verplaatsen
     * of hernoemen, weggooien. Slepen van je systeem naar de kaart (of terug) kopieert ({@link #copy}). Een valstrik kan op
     * een kaart niet: die hoort bij een systeem. {@code owner}: is deze speler de eigenaar van de kern (zie copy).
     */
    public static Result fileOp(ServerPlayer player, SecurityCoreBlockEntity core, @Nullable View view, String op,
                                CompoundTag args, boolean owner) {
        if (view == null) {
            return Result.fail("There is no SD card in here");
        }
        ServerLevel level = player.serverLevel();
        switch (op) {
            case "fs_mkdir" -> {
                String path = cardPath(args.getString("path"));
                SdCards.Card card = view.card();
                String room = room(card, view.item(), 0, 1);
                if (room != null) {
                    return Result.fail(room);
                }
                card = writable(level, view);
                return done(level, view, card, card.files().mkdir(path), null);
            }
            case "fs_touch", "fs_save" -> {
                String path = cardPath(args.getString("path"));
                String text = op.equals("fs_save") ? args.getString("text") : "";
                SdCards.Card card = view.card();
                VirtualFs.Node existing = card == null ? null : card.files().get(path);
                if (existing != null && op.equals("fs_touch")) {
                    return Result.fail(VirtualFs.nameOf(path) + " already exists");
                }
                if (existing == null) {
                    String room = room(card, view.item(), 1, 0);
                    if (room != null) {
                        return Result.fail(room);
                    }
                }
                card = writable(level, view);
                return done(level, view, card, card.files().write(path, text),
                        op.equals("fs_save") ? "Saved " + VirtualFs.nameOf(path) + " on the SD card" : null);
            }
            case "fs_move" -> {
                String from = args.getString("from");
                String to = args.getString("to");
                if (onCard(from) != onCard(to)) {
                    return copy(player, core, view, from, to, owner);
                }
                SdCards.Card card = view.card();
                if (card == null) {
                    return Result.fail(VirtualFs.nameOf(cardPath(from)) + ": no such file or directory");
                }
                return done(level, view, card, card.files().move(cardPath(from), cardPath(to)), null);
            }
            case "fs_delete" -> {
                SdCards.Card card = view.card();
                String path = cardPath(args.getString("path"));
                if (card == null) {
                    return Result.fail(VirtualFs.nameOf(path) + ": no such file or directory");
                }
                return done(level, view, card, card.files().delete(path), null);
            }
            case "fs_trap" -> {
                return Result.fail("A file on an SD card can't be a trap");
            }
            default -> {
                return Result.fail("That does not work on an SD card");
            }
        }
    }

    /**
     * Kopieert een bestand of een map (met alles erin) tussen je systeem en de kaart ({@code fs_copy}, en slepen tussen de
     * twee). {@code to} is de map waar het in komt; leeg = de kaart zelf (van je systeem), of je eigen map in /home (van
     * de kaart). Twee dingen gaan nooit mee: security.conf (de sleutel van je systeem) en of iets een valstrik is. Wie
     * niet de eigenaar is en een valstrik kopieert, "leest" hem: die gaat af (zoals bij openen).
     */
    public static Result copy(ServerPlayer player, SecurityCoreBlockEntity core, @Nullable View view, String from, String to,
                              boolean owner) {
        if (view == null) {
            return Result.fail("There is no SD card in here");
        }
        ServerLevel level = player.serverLevel();
        boolean toCard = !onCard(from);
        VirtualFs source = toCard ? core.fs() : view.card() == null ? null : view.card().files();
        String fromPath = toCard ? VirtualFs.resolve("/", from) : cardPath(from);
        VirtualFs.Node node = source == null ? null : source.get(fromPath);
        if (node == null || fromPath.equals("/")) {
            return Result.fail(VirtualFs.nameOf(fromPath) + ": no such file or directory");
        }
        // Hoofdstuk 136: een geluid blijft op de kern (het geluid zelf staat bij deze wereld, niet op de kaart). Eerst zei het
        // "gekopieerd" zonder iets te doen (gevonden door de nalezing).
        if (toCard && node.isSound()) {
            return Result.fail(node.name() + " is a sound: sounds stay on this core");
        }
        // Wat er gekopieerd wordt (een map met alles erin), en wat niet mee mag.
        List<String> paths = new ArrayList<>();
        paths.add(fromPath);
        if (node.isDir()) {
            String prefix = fromPath + "/";
            for (String path : source.walk().keySet()) {
                if (path.startsWith(prefix)) {
                    paths.add(path);
                }
            }
        }
        if (toCard) {
            if (node.isSystem()) {
                return Result.fail(node.name() + " can't be copied: it is the key of your system");
            }
            if (!owner) {
                for (String path : paths) {
                    VirtualFs.Node each = source.get(path);
                    if (each != null && each.isTrap()) {
                        com.senne.imttech.security.os.SecurityOs.springTrap(player, core, path);
                        return new Result(null, true); // de desktop is dicht: niets meer te melden
                    }
                }
            }
        }
        // Waar het naartoe gaat.
        VirtualFs target = toCard ? null : core.fs();
        String dir;
        if (toCard) {
            dir = to.isBlank() ? "/" : cardPath(to);
        } else if (to.isBlank()) {
            String home = "/home/" + SystemDefaults.homeName(player.getGameProfile().getName());
            dir = core.fs().isDir(home) ? home : "/";
        } else {
            dir = VirtualFs.resolve("/", to);
        }
        // Wat er echt bij komt (zonder security.conf), om eerst te kijken of het past.
        int files = 0;
        int folders = 0;
        for (String path : paths) {
            VirtualFs.Node each = source.get(path);
            if (each == null || each.isSystem()) {
                continue;
            }
            if (each.isDir()) {
                folders++;
            } else {
                files++;
            }
        }
        if (toCard) {
            SdCards.Card card = view.card();
            if (card != null && !dir.equals("/") && !card.files().isDir(dir) || card == null && !dir.equals("/")) {
                return Result.fail(dir + ": no such folder on the SD card");
            }
            if (card != null && card.files().get((dir.equals("/") ? "" : dir) + "/" + node.name()) != null) {
                return Result.fail(node.name() + " is already on the SD card there");
            }
            String room = room(card, view.item(), files, folders);
            if (room != null) {
                return Result.fail(room);
            }
            card = writable(level, view);
            String error = copyTree(source, fromPath, card.files(), dir);
            changed(level, view, card);
            if (error != null) {
                return Result.fail(error);
            }
            core.logEvent(EventLog.Type.SYSTEM, player.getGameProfile().getName() + " copied " + fromPath + " to an SD card");
            return Result.ok("Copied " + node.name() + " to the SD card");
        }
        if (!target.isDir(dir)) {
            return Result.fail(dir + ": no such folder");
        }
        if (target.get((dir.equals("/") ? "" : dir) + "/" + node.name()) != null) {
            return Result.fail(node.name() + " already exists in " + dir);
        }
        if (target.count() + files + folders > VirtualFs.MAX_NODES) {
            return Result.fail("Your system is full (max " + VirtualFs.MAX_NODES + " files and folders)");
        }
        String error = copyTree(source, fromPath, target, dir);
        core.setChanged();
        if (error != null) {
            return Result.fail(error);
        }
        return Result.ok("Copied " + node.name() + " to " + dir);
    }

    /**
     * Kopieert {@code path} (een bestand, of een map met alles erin) van {@code from} naar map {@code dir} van {@code to}.
     * Alleen de tekst: nooit security.conf (die slaan we over) en nooit of iets een valstrik is. Geeft de eerste fout, of
     * null.
     */
    private static @Nullable String copyTree(VirtualFs from, String path, VirtualFs to, String dir) {
        VirtualFs.Node node = from.get(path);
        // Een geluid (hoofdstuk 136) blijft op de kern: het geluid zelf staat in de map van deze wereld, niet op de kaart.
        if (node == null || node.isSystem() || node.isSound()) {
            return null;
        }
        String target = (dir.equals("/") ? "" : dir) + "/" + node.name();
        if (!node.isDir()) {
            return to.write(target, node.content());
        }
        String error = to.mkdir(target);
        if (error != null) {
            return error;
        }
        for (VirtualFs.Node child : node.children()) {
            error = copyTree(from, (path.equals("/") ? "" : path) + "/" + child.name(), to, target);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    // ================================================================ Hulp

    /** Past er nog zoveel bij op de kaart? Null = ja, anders waarom niet. */
    private static @Nullable String room(@Nullable SdCards.Card card, SdCardItem item, int files, int folders) {
        int haveFiles = card == null ? 0 : card.fileCount();
        int haveFolders = card == null ? 0 : card.folderCount();
        if (haveFiles + files > item.maxFiles()) {
            return "The SD card is full: it holds " + item.maxFiles() + " files";
        }
        if (haveFolders + folders > item.maxFiles()) {
            return "The SD card holds at most " + item.maxFiles() + " folders";
        }
        return null;
    }

    /** Wat er op de kaart staat, om iets op te zetten: een lege kaart krijgt nu een nummer (en een plek op de server). */
    private static SdCards.Card writable(ServerLevel level, View view) {
        if (view.card() != null) {
            return view.card();
        }
        SdCards cards = SdCards.get(level);
        SdCardData data = SdCardItem.data(view.stack());
        SdCards.Card card = data == null ? null : cards.card(data.id());
        if (card == null) {
            card = cards.create();
            view.stack().set(ModDataComponents.SD_CARD.get(), card.summary());
            view.holder().sdCardChanged();
        }
        return card;
    }

    /**
     * Er veranderde iets op de kaart: bewaren, de samenvatting op het voorwerp bijwerken (voor de tooltip), en een kaart
     * waar niets meer op staat is weer een lege kaart (zonder nummer).
     */
    private static void changed(ServerLevel level, View view, SdCards.Card card) {
        SdCards cards = SdCards.get(level);
        cards.changed(card);
        if (card.empty()) {
            cards.forget(card.id());
            view.stack().remove(ModDataComponents.SD_CARD.get());
        } else {
            view.stack().set(ModDataComponents.SD_CARD.get(), card.summary());
        }
        view.holder().sdCardChanged();
    }

    /** Na een opdracht op de kaart: bewaren als het lukte, en de melding (of geen: het scherm toont het zelf). */
    private static Result done(ServerLevel level, View view, SdCards.Card card, @Nullable String error, @Nullable String ok) {
        changed(level, view, card);
        return error != null ? Result.fail(error) : new Result(ok, false);
    }
}
