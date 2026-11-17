package com.senne.imttech.security.os;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * De mappen en bestanden op een beveiligingssysteem (jouw wens, 28 september: je eigen
 * mappenstructuur, notities, het beveiligingsbestand verstoppen en valstrikken zetten).
 *
 * Een boom: elke map ({@link Node} met {@code dir = true}) heeft kinderen, een bestand heeft tekst.
 * Alles wordt op de kern bewaard (NBT). Paden zijn altijd "absoluut": {@code /etc/security.conf}.
 *
 * Twee vlaggen op een bestand:
 * <ul>
 *   <li>{@code system}: een bestand van het systeem zelf (security.conf). Hernoemen en verplaatsen
 *       mag (verstoppen!), verwijderen of de inhoud veranderen niet.</li>
 *   <li>{@code trap}: een valstrik. Leest een hacker hem, dan gaat het alarm af (zie de shell).</li>
 * </ul>
 *
 * Een derde soort bestand (hoofdstuk 136): een <b>geluid</b> in de map {@link #SOUNDS}. Dat is geen tekst maar een
 * verwijzing naar een geüpload geluid (de vingerafdruk uit alarm/AlarmSounds en hoeveel monsters het heeft); het geluid
 * zelf staat in de map van de wereld. Een diagram speelt het met {@code playsound("naam", "Speaker 1")}. Je kunt het
 * hernoemen, verplaatsen en weggooien, maar niet als tekst veranderen; zijn {@link Node#content()} is een korte
 * beschrijving (voor de terminal van een hacker).
 *
 * Er zijn grenzen (MAX_NODES, MAX_FILE_LENGTH), zodat een wereld-save niet ontploft.
 *
 * Alle methodes die iets veranderen geven {@code null} terug als het lukte, en anders een
 * Engelse foutmelding (die zie je in de terminal of het Files-venster).
 */
public final class VirtualFs {
    /** Zoveel mappen en bestanden samen mag een systeem hebben. */
    public static final int MAX_NODES = 512;
    /** Zoveel tekens mag één bestand hebben. */
    public static final int MAX_FILE_LENGTH = 4096;
    /** Zo lang mag een naam zijn. */
    public static final int MAX_NAME = 32;
    /** Zo diep mogen mappen in elkaar zitten. */
    public static final int MAX_DEPTH = 12;
    /** De map met de geluiden van de kern (hoofdstuk 136): hier zoekt playsound(...). */
    public static final String SOUNDS = "/sounds";

    /** Eén map of bestand. */
    public static final class Node {
        private String name;
        private final boolean dir;
        private boolean system;
        private boolean trap;
        private String content = "";
        /** Hoofdstuk 136: bij een geluidsbestand de vingerafdruk van het geluid (anders ""), en hoeveel monsters het heeft. */
        private String soundId = "";
        private int soundSamples;
        /** De kinderen van een map, in de volgorde waarin ze erbij kwamen. */
        private final Map<String, Node> children = new LinkedHashMap<>();

        private Node(String name, boolean dir) {
            this.name = name;
            this.dir = dir;
        }

        public String name() {
            return name;
        }

        public boolean isDir() {
            return dir;
        }

        public boolean isSystem() {
            return system;
        }

        public boolean isTrap() {
            return trap;
        }

        public String content() {
            return content;
        }

        public List<Node> children() {
            return List.copyOf(children.values());
        }

        /** Is dit een geluidsbestand (hoofdstuk 136)? */
        public boolean isSound() {
            return !dir && !soundId.isEmpty();
        }

        /** De vingerafdruk van het geluid (zie alarm/AlarmSounds), of "" als dit geen geluid is. */
        public String soundId() {
            return soundId;
        }

        /** Hoeveel monsters het geluid heeft (22 050 per seconde). */
        public int soundSamples() {
            return soundSamples;
        }

        /** Zit er (ergens diep) een systeembestand in deze map? Dan mag hij niet weg. */
        private boolean holdsSystemFile() {
            if (system) {
                return true;
            }
            for (Node child : children.values()) {
                if (child.holdsSystemFile()) {
                    return true;
                }
            }
            return false;
        }

        private int count() {
            int count = 1;
            for (Node child : children.values()) {
                count += child.count();
            }
            return count;
        }
    }

    private final Node root = new Node("", true);
    /**
     * Gaat één omhoog bij elke verandering. Niet bewaard: alleen zodat de desktop weet of hij de
     * mappenlijst opnieuw moet sturen (zie SecurityOs).
     */
    private int revision;

    /** Het versienummer van de mappen (verandert bij elke wijziging). */
    public int revision() {
        return revision;
    }

    // ================================================================ Paden

    /**
     * Maakt van wat je typt een volledig pad. {@code cwd} is de map waar je nu staat;
     * {@code ..} gaat een map omhoog, {@code .} blijft staan.
     */
    public static String resolve(String cwd, String path) {
        String full = path.startsWith("/") ? path : (cwd.equals("/") ? "" : cwd) + "/" + path;
        List<String> parts = new ArrayList<>();
        for (String part : full.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.removeLast();
                }
            } else {
                parts.add(part);
            }
        }
        return "/" + String.join("/", parts);
    }

    /** De map waar dit pad in staat ({@code /etc/a.txt} geeft {@code /etc}). */
    public static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    /** De naam aan het eind van een pad ({@code /etc/a.txt} geeft {@code a.txt}). */
    public static String nameOf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Is dit een toegestane naam? Letters, cijfers, punt, streepje en liggend streepje. */
    public static boolean validName(String name) {
        return !name.isEmpty() && name.length() <= MAX_NAME && !name.equals(".") && !name.equals("..")
                && name.chars().allMatch(c -> Character.isLetterOrDigit(c) && c < 128 || c == '.' || c == '-' || c == '_');
    }

    // ================================================================ Opvragen

    /** Het bestand of de map op dit pad, of null. */
    public @Nullable Node get(String path) {
        Node node = root;
        for (String part : path.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!node.dir) {
                return null;
            }
            node = node.children.get(part);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    public boolean isDir(String path) {
        Node node = get(path);
        return node != null && node.dir;
    }

    public boolean isFile(String path) {
        Node node = get(path);
        return node != null && !node.dir;
    }

    /** Wat er in een map zit, of een lege lijst. */
    public List<Node> list(String path) {
        Node node = get(path);
        return node == null || !node.dir ? List.of() : node.children();
    }

    /** Alle bestanden (geen mappen), met hun volledige pad, van boven naar beneden. */
    public Map<String, Node> allFiles() {
        Map<String, Node> files = new LinkedHashMap<>();
        collect(root, "", files);
        return files;
    }

    private static void collect(Node dir, String prefix, Map<String, Node> files) {
        for (Node child : dir.children.values()) {
            String path = prefix + "/" + child.name;
            if (child.dir) {
                collect(child, path, files);
            } else {
                files.put(path, child);
            }
        }
    }

    /** Alle mappen én bestanden met hun volledige pad, van boven naar beneden (een map vóór wat erin zit). */
    public Map<String, Node> walk() {
        Map<String, Node> nodes = new LinkedHashMap<>();
        walk(root, "", nodes);
        return nodes;
    }

    private static void walk(Node dir, String prefix, Map<String, Node> nodes) {
        for (Node child : dir.children.values()) {
            String path = prefix + "/" + child.name;
            nodes.put(path, child);
            if (child.dir) {
                walk(child, path, nodes);
            }
        }
    }

    /** De paden van alle bestanden met deze naam (hoofdletters maken niet uit). */
    public List<String> findByName(String name) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, Node> file : allFiles().entrySet()) {
            if (file.getValue().name.equalsIgnoreCase(name)) {
                found.add(file.getKey());
            }
        }
        return found;
    }

    /** Het pad van het systeembestand security.conf (waar de eigenaar het ook verstopte), of null. */
    public @Nullable String systemFile() {
        for (Map.Entry<String, Node> file : allFiles().entrySet()) {
            if (file.getValue().system) {
                return file.getKey();
            }
        }
        return null;
    }

    public int count() {
        return root.count() - 1;
    }

    // ================================================================ Veranderen

    /** Een nieuwe (lege) map. */
    public @Nullable String mkdir(String path) {
        String error = checkNew(path);
        if (error != null) {
            return error;
        }
        get(parentOf(path)).children.put(nameOf(path), new Node(nameOf(path), true));
        revision++;
        return null;
    }

    /**
     * Schrijft een bestand (nieuw, of de inhoud van een bestaand bestand vervangen). Een
     * systeembestand kun je niet overschrijven.
     */
    public @Nullable String write(String path, String content) {
        if (content.length() > MAX_FILE_LENGTH) {
            return "file too big (max " + MAX_FILE_LENGTH + " characters)";
        }
        Node existing = get(path);
        if (existing != null) {
            if (existing.dir) {
                return nameOf(path) + " is a directory";
            }
            if (existing.system) {
                return nameOf(path) + " is a system file: it can't be changed";
            }
            if (existing.isSound()) {
                return nameOf(path) + " is a sound: upload a new one to change it";
            }
            existing.content = content;
            revision++;
            return null;
        }
        String error = checkNew(path);
        if (error != null) {
            return error;
        }
        Node file = new Node(nameOf(path), false);
        file.content = content;
        get(parentOf(path)).children.put(file.name, file);
        revision++;
        return null;
    }

    /**
     * Verplaatst en/of hernoemt (zoals {@code mv}). Is {@code to} een bestaande map, dan gaat het
     * erin met dezelfde naam; anders krijgt het de nieuwe naam op die plek.
     */
    public @Nullable String move(String from, String to) {
        Node node = get(from);
        if (node == null || from.equals("/")) {
            return nameOf(from) + ": no such file or directory";
        }
        Node target = get(to);
        String destination = target != null && target.dir ? (to.equals("/") ? "" : to) + "/" + node.name : to;
        if (destination.equals(from)) {
            return null;
        }
        // Een map kan niet in zichzelf.
        if (node.dir && (destination + "/").startsWith(from + "/")) {
            return "can't move a directory into itself";
        }
        Node newParent = get(parentOf(destination));
        String newName = nameOf(destination);
        if (newParent == null || !newParent.dir) {
            return parentOf(destination) + ": no such directory";
        }
        if (!validName(newName)) {
            return "'" + newName + "' is not a valid name (letters, digits, . - _)";
        }
        if (newParent.children.containsKey(newName)) {
            return newName + " already exists there";
        }
        if (depth(parentOf(destination)) + height(node) > MAX_DEPTH) {
            return "too many folders inside each other";
        }
        get(parentOf(from)).children.remove(node.name);
        node.name = newName;
        newParent.children.put(newName, node);
        revision++;
        return null;
    }

    /** Verwijdert een bestand of map (met alles erin). Systeembestanden blijven. */
    public @Nullable String delete(String path) {
        Node node = get(path);
        if (node == null || path.equals("/")) {
            return nameOf(path) + ": no such file or directory";
        }
        if (node.holdsSystemFile()) {
            return node.dir ? nameOf(path) + " holds a system file: it can't be deleted (you can move it)"
                    : nameOf(path) + " is a system file: it can't be deleted (you can move or rename it)";
        }
        get(parentOf(path)).children.remove(node.name);
        revision++;
        return null;
    }

    /**
     * Hoofdstuk 136: zet een geluid in deze map (meestal {@link #SOUNDS}), onder een vrije naam: bestaat {@code name} al,
     * dan {@code name_2}, {@code name_3}... Maakt de map als hij er nog niet is. Geeft het pad, of null als er geen plaats
     * meer is (de schijf is vol of de naam kan niet).
     *
     * @param seconds hoe lang het geluid duurt (voor de beschrijving)
     */
    public @Nullable String putSound(String folder, String name, String soundId, int samples, float seconds) {
        Node dir = get(folder);
        if (dir == null) {
            if (count() >= MAX_NODES) {
                return null;
            }
            mkdirs(folder);
            dir = get(folder);
        }
        if (dir == null || !dir.dir || !validName(name)) {
            return null;
        }
        String free = name;
        for (int n = 2; dir.children.containsKey(free); n++) {
            String suffix = "_" + n;
            free = (name.length() + suffix.length() > MAX_NAME ? name.substring(0, MAX_NAME - suffix.length()) : name) + suffix;
        }
        String path = (folder.equals("/") ? "" : folder) + "/" + free;
        if (checkNew(path) != null) {
            return null;
        }
        Node file = new Node(free, false);
        file.soundId = soundId;
        file.soundSamples = Math.max(0, samples);
        file.content = soundDescription(seconds);
        dir.children.put(free, file);
        revision++;
        return path;
    }

    /** Wat de terminal van een hacker toont bij {@code cat} op een geluid. */
    private static String soundDescription(float seconds) {
        return String.format(java.util.Locale.ROOT, "(sound, %.1f seconds)", seconds);
    }

    /**
     * Hoofdstuk 136: het geluid met deze naam in {@link #SOUNDS}: {@code "siren"}, of met een extensie erachter
     * ({@code "siren.mp4"} vindt ook {@code siren}). Hoofdletters maken niet uit. Null als er zo geen geluid is.
     */
    public @Nullable Node sound(String name) {
        Node dir = get(SOUNDS);
        if (dir == null || !dir.dir || name.isBlank()) {
            return null;
        }
        String wanted = name.strip();
        if (wanted.startsWith(SOUNDS + "/")) {
            wanted = wanted.substring(SOUNDS.length() + 1);
        }
        for (String candidate : new String[] {wanted, wanted.contains(".") ? wanted.substring(0, wanted.lastIndexOf('.')) : wanted}) {
            for (Node child : dir.children.values()) {
                if (child.isSound() && child.name.equalsIgnoreCase(candidate)) {
                    return child;
                }
            }
        }
        return null;
    }

    /** De vingerafdrukken van alle geluiden op dit systeem (ook buiten /sounds), voor het opruimen in alarm/AlarmSounds. */
    public java.util.Set<String> soundIds() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Node node : allFiles().values()) {
            if (node.isSound()) {
                ids.add(node.soundId);
            }
        }
        return ids;
    }

    /** Maakt van een bestand een valstrik (of weer een gewoon bestand). */
    public @Nullable String setTrap(String path, boolean trap) {
        Node node = get(path);
        if (node == null || node.dir) {
            return nameOf(path) + ": no such file";
        }
        if (node.system) {
            return nameOf(path) + " is a system file: it can't be a trap";
        }
        node.trap = trap;
        revision++;
        return null;
    }

    /** Alleen bij het maken van een nieuw systeem: een systeembestand neerzetten. */
    public void putSystemFile(String path, String content) {
        mkdirs(parentOf(path));
        Node file = new Node(nameOf(path), false);
        file.system = true;
        file.content = content;
        get(parentOf(path)).children.put(file.name, file);
        revision++;
    }

    /** Alle mappen op dit pad maken (bij het maken van een nieuw systeem). */
    public void mkdirs(String path) {
        Node node = root;
        for (String part : path.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            node = node.children.computeIfAbsent(part, name -> new Node(name, true));
        }
        revision++;
    }

    /** Kan hier iets nieuws komen? (De map bestaat, de naam mag, het is nog niet vol.) */
    private @Nullable String checkNew(String path) {
        if (path.equals("/")) {
            return "that already exists";
        }
        Node parent = get(parentOf(path));
        String name = nameOf(path);
        if (parent == null || !parent.dir) {
            return parentOf(path) + ": no such directory";
        }
        if (!validName(name)) {
            return "'" + name + "' is not a valid name (letters, digits, . - _)";
        }
        if (parent.children.containsKey(name)) {
            return name + " already exists";
        }
        if (count() >= MAX_NODES) {
            return "the disk is full (max " + MAX_NODES + " files and folders)";
        }
        if (depth(parentOf(path)) >= MAX_DEPTH) {
            return "too many folders inside each other";
        }
        return null;
    }

    private static int depth(String path) {
        return path.equals("/") ? 0 : path.split("/").length - 1;
    }

    private static int height(Node node) {
        int height = 1;
        for (Node child : node.children.values()) {
            height = Math.max(height, 1 + height(child));
        }
        return height;
    }

    // ================================================================ Opslaan

    public CompoundTag save() {
        return saveNode(root);
    }

    private static CompoundTag saveNode(Node node) {
        CompoundTag tag = new CompoundTag();
        tag.putString("N", node.name);
        if (node.dir) {
            tag.putBoolean("D", true);
            ListTag children = new ListTag();
            for (Node child : node.children.values()) {
                children.add(saveNode(child));
            }
            tag.put("K", children);
        } else {
            tag.putString("C", node.content);
            if (node.system) {
                tag.putBoolean("S", true);
            }
            if (!node.soundId.isEmpty()) {
                tag.putString("A", node.soundId); // hoofdstuk 136: een geluid
                tag.putInt("L", node.soundSamples);
            }
            if (node.trap) {
                tag.putBoolean("T", true);
            }
        }
        return tag;
    }

    public static VirtualFs load(CompoundTag tag) {
        VirtualFs fs = new VirtualFs();
        loadChildren(fs.root, tag, 0);
        return fs;
    }

    private static void loadChildren(Node dir, CompoundTag tag, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        for (Tag childTag : tag.getList("K", Tag.TAG_COMPOUND)) {
            CompoundTag child = (CompoundTag) childTag;
            String name = child.getString("N");
            if (!validName(name) || dir.children.containsKey(name)) {
                continue;
            }
            Node node = new Node(name, child.getBoolean("D"));
            if (node.dir) {
                loadChildren(node, child, depth + 1);
            } else {
                node.content = child.getString("C");
                node.system = child.getBoolean("S");
                node.trap = child.getBoolean("T");
                String sound = child.getString("A");
                if (com.senne.imttech.alarm.AlarmSounds.validId(sound) && !node.system) {
                    node.soundId = sound;
                    node.soundSamples = Math.max(0, child.getInt("L"));
                }
            }
            dir.children.put(name, node);
        }
    }
}
