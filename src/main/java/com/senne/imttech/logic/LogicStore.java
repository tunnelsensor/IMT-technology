package com.senne.imttech.logic;

import com.senne.imttech.logic.program.LogicProgram;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Wat de server van een beveiligingskern onthoudt:
 * <ul>
 *   <li>de keycards die hij schreef (en welke geblokkeerd zijn), en hun groepen (25 oktober);</li>
 *   <li>je redstone-diagrammen (LogicProgram);</li>
 *   <li>het "telefoonboek" van apparaten: welke naam bij welk relais, laserzender, koepel of
 *       torentje in het gebied hoort. Die namen geef je met je bril (je redstone-lijst); de server
 *       neemt ze over, zodat je diagrammen ook werken als jij offline bent (zie LogicDevices).</li>
 * </ul>
 * Het staat in de kern zelf (SecurityCoreBlockEntity), niet in de racks: breek je een rack af, dan
 * ben je je diagrammen niet kwijt. Neemt iemand je kern over, dan krijgt hij dit allemaal mee (net
 * als je bestanden).
 */
public final class LogicStore {
    /** Zoveel kaarten kan één netwerk hooguit schrijven. */
    public static final int MAX_CARDS = 200;
    /** Zoveel diagrammen kan één server hooguit hebben. */
    public static final int MAX_PROGRAMS = 16;
    /** Het eerste kaartnummer. */
    private static final int FIRST_CARD = 1001;
    /** Zoveel keycard-groepen kan één netwerk hooguit hebben (25 oktober). */
    public static final int MAX_GROUPS = 32;
    /** Zo lang mag de naam van een groep hooguit zijn. */
    public static final int MAX_GROUP_NAME = 24;

    /**
     * Een geschreven kaart: zijn nummer, naam, kleur (1-8), of hij geblokkeerd is, wie hem schreef, en in welke
     * groepen hij zelf zit (25 oktober: een kaart kan in meer dan één groep zitten). Zit "security" in "staff", dan
     * staat bij een kaart van "security" alleen "security": dat hij ook in "staff" zit, rekent {@link #inGroup} uit.
     */
    public record Card(int number, String label, int colour, boolean blocked, String writtenBy, List<String> groups) {
        public Card {
            groups = List.copyOf(groups);
        }

        Card withBlocked(boolean blocked) {
            return new Card(number, label, colour, blocked, writtenBy, groups);
        }

        Card withGroups(List<String> groups) {
            List<String> sorted = new ArrayList<>(groups);
            sorted.sort(String.CASE_INSENSITIVE_ORDER);
            return new Card(number, label, colour, blocked, writtenBy, sorted);
        }

        /** Zit hij zelf in deze groep (hoofdletters maken niet uit)? */
        public boolean isIn(String group) {
            return groups.stream().anyMatch(own -> own.equalsIgnoreCase(group.strip()));
        }
    }

    /** Een apparaat dat een diagram kan schakelen: een blok, en welke schakelaar ervan (-1 = het hele blok). */
    public record Target(BlockPos pos, int channel) {}

    private final TreeMap<Integer, Card> cards = new TreeMap<>();
    private int nextCard = FIRST_CARD;
    /**
     * De keycard-groepen: per groep in welke groep hij zit ("" = bovenaan). Een groep zit in hooguit één andere groep,
     * dus het is een boom, net als je relaisgroepen. Hoofdletters maken niet uit; de schrijfwijze van de eigenaar blijft.
     */
    private final TreeMap<String, String> groupParents = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final List<LogicProgram> programs = new ArrayList<>();
    /** Telt elke verandering aan de diagrammen (zo weet de desktop wanneer hij ze opnieuw moet krijgen). */
    private int revision;
    /** Naam (hoofdletters maken niet uit) -> de apparaten met die naam. */
    private final TreeMap<String, List<Target>> devices = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    // ================================================================ Keycards

    /** Alle kaarten, op nummer. */
    public List<Card> cards() {
        return new ArrayList<>(cards.values());
    }

    public @Nullable Card card(int number) {
        return cards.get(number);
    }

    /**
     * Een nieuwe kaart in de lijst (met het volgende nummer), in groep {@code group} ("" = in geen groep), of null als
     * de lijst vol is. Een groep die niet bestaat, telt als geen groep.
     */
    public @Nullable Card issue(String label, int colour, String writtenBy, String group) {
        if (cards.size() >= MAX_CARDS) {
            return null;
        }
        String own = spelling(group);
        Card card = new Card(nextCard++, label, KeycardData.clampColour(colour), false, writtenBy,
                own == null ? List.of() : List.of(own));
        cards.put(card.number(), card);
        return card;
    }

    /** Een kaart blokkeren (of weer toelaten). False als die kaart niet bestaat. */
    public boolean setBlocked(int number, boolean blocked) {
        Card card = cards.get(number);
        if (card == null) {
            return false;
        }
        cards.put(number, card.withBlocked(blocked));
        return true;
    }

    /**
     * Een kaart uit de lijst halen (dan is hij nergens meer geldig). Zijn nummer komt nooit meer terug
     * ({@code nextCard} gaat alleen omhoog), dus een oude kaart met dit nummer wordt nooit meer geldig.
     */
    public boolean forget(int number) {
        return cards.remove(number) != null;
    }

    /** Alle geblokkeerde kaarten uit de lijst halen. Geeft terug hoeveel het er waren. */
    public int forgetBlocked() {
        int before = cards.size();
        cards.values().removeIf(Card::blocked);
        return before - cards.size();
    }

    /**
     * Is deze kaart hier geldig? Hij moet door DIT netwerk geschreven zijn (zijn IP), nog in de lijst
     * staan en niet geblokkeerd zijn.
     */
    public boolean isValid(String networkIp, @Nullable KeycardData data) {
        if (data == null || !data.ip().equals(networkIp)) {
            return false;
        }
        Card card = cards.get(data.number());
        return card != null && !card.blocked();
    }

    // ================================================================ Keycard-groepen (25 oktober)

    /** Alle groepen, op alfabet. */
    public List<String> groups() {
        return new ArrayList<>(groupParents.keySet());
    }

    public boolean hasGroup(String group) {
        return spelling(group) != null;
    }

    /** Hoe deze groep bewaard is ("Security" als je "security" typt), of null als hij er niet is. */
    public @Nullable String spelling(String group) {
        String wanted = group.strip();
        if (wanted.isEmpty()) {
            return null;
        }
        String stored = groupParents.ceilingKey(wanted);
        return stored != null && stored.equalsIgnoreCase(wanted) ? stored : null;
    }

    /** De groep waar deze groep direct in zit, of "" (bovenaan, of als hij niet bestaat). */
    public String parentOf(String group) {
        String parent = group.isBlank() ? null : groupParents.get(group.strip());
        return parent == null ? "" : parent;
    }

    /** De groepen die direct in deze groep zitten ("" = de groepen bovenaan), op alfabet. */
    public List<String> childrenOf(String group) {
        List<String> children = new ArrayList<>();
        groupParents.forEach((own, parent) -> {
            if (parent.equalsIgnoreCase(group.strip())) {
                children.add(own);
            }
        });
        return children;
    }

    /**
     * Zit groep {@code group} in groep {@code outer}? Dat is zo als het dezelfde groep is, of als hij erin zit via
     * groepen in groepen ("night shift" zit in "security", die zit in "staff": dan zit "night shift" ook in "staff").
     */
    public boolean isInside(String group, String outer) {
        String wanted = outer.strip();
        String at = group.strip();
        // Hooguit zoveel stappen omhoog als er groepen zijn: zo stopt het altijd, ook bij een fout in de opslag.
        for (int steps = 0; !at.isEmpty() && steps <= groupParents.size(); steps++) {
            if (at.equalsIgnoreCase(wanted)) {
                return true;
            }
            at = parentOf(at);
        }
        return false;
    }

    /** Zit een van deze groepen in groep {@code outer} (zie {@link #isInside})? */
    public boolean isInsideAny(List<String> groups, String outer) {
        return groups.stream().anyMatch(group -> isInside(group, outer));
    }

    /**
     * Zit kaart {@code number} in deze groep: zelf, of via een groep die erin zit? Of de kaart geldig is, kijkt dit
     * niet na (dat doet {@link #isValid}).
     */
    public boolean inGroup(int number, String group) {
        Card card = cards.get(number);
        return card != null && isInsideAny(card.groups(), group);
    }

    /** Mag dit de naam van een groep zijn? Niet leeg, hooguit 24 tekens, en geen aanhalingstekens of \ (die horen bij je code). */
    public static boolean validGroupName(String name) {
        String clean = name.strip();
        return !clean.isEmpty() && clean.length() <= MAX_GROUP_NAME && clean.chars().noneMatch(c -> c == '"' || c == '\'' || c == '\\');
    }

    /** Zou "{@code group} zit in {@code parent}" een rondje maken? Dat is zo als {@code parent} al in {@code group} zit. */
    public boolean wouldLoop(String group, String parent) {
        return !parent.isBlank() && isInside(parent, group);
    }

    /**
     * Een nieuwe groep in groep {@code parent} ("" = bovenaan). False als de naam niet mag, de groep al bestaat, er al
     * {@link #MAX_GROUPS} zijn, of {@code parent} niet bestaat.
     */
    public boolean addGroup(String name, String parent) {
        String clean = name.strip();
        String above = parent.isBlank() ? "" : spelling(parent);
        if (!validGroupName(clean) || hasGroup(clean) || groupParents.size() >= MAX_GROUPS || above == null) {
            return false;
        }
        groupParents.put(clean, above);
        return true;
    }

    /** Groep {@code group} zit voortaan in {@code parent} ("" = bovenaan). False bij een rondje, of als een van beide er niet is. */
    public boolean moveGroup(String group, String parent) {
        String own = spelling(group);
        String above = parent.isBlank() ? "" : spelling(parent);
        if (own == null || above == null || wouldLoop(own, above)) {
            return false;
        }
        groupParents.put(own, above);
        return true;
    }

    /**
     * Een andere naam voor een groep: de groepen erin en de kaarten erin gaan mee. False als de naam niet mag of er al
     * een andere groep zo heet. (Alleen andere hoofdletters mag wel: "security" wordt "Security".)
     */
    public boolean renameGroup(String group, String newName) {
        String own = spelling(group);
        String fresh = newName.strip();
        if (own == null || !validGroupName(fresh) || hasGroup(fresh) && !fresh.equalsIgnoreCase(own)) {
            return false;
        }
        String parent = groupParents.remove(own);
        for (Map.Entry<String, String> entry : groupParents.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(own)) {
                entry.setValue(fresh);
            }
        }
        groupParents.put(fresh, parent);
        for (Card card : new ArrayList<>(cards.values())) {
            if (card.isIn(own)) {
                List<String> groups = new ArrayList<>();
                for (String other : card.groups()) {
                    groups.add(other.equalsIgnoreCase(own) ? fresh : other);
                }
                cards.put(card.number(), card.withGroups(groups));
            }
        }
        return true;
    }

    /**
     * Gooit een groep weg. Wat erin zat, schuift één laag omhoog, net als bij je relais: de groepen erin komen in de
     * groep erboven, en een kaart die erin zat, zit voortaan in de groep erboven (stond de groep bovenaan, dan in geen
     * van beide). Zo geeft {@code card.in_group(...)} voor de groep erboven nog hetzelfde antwoord.
     */
    public boolean removeGroup(String group) {
        String own = spelling(group);
        if (own == null) {
            return false;
        }
        String parent = groupParents.remove(own);
        for (Map.Entry<String, String> entry : groupParents.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(own)) {
                entry.setValue(parent);
            }
        }
        for (Card card : new ArrayList<>(cards.values())) {
            if (card.isIn(own)) {
                List<String> groups = new ArrayList<>();
                for (String other : card.groups()) {
                    if (!other.equalsIgnoreCase(own)) {
                        groups.add(other);
                    }
                }
                if (!parent.isEmpty() && groups.stream().noneMatch(other -> other.equalsIgnoreCase(parent))) {
                    groups.add(parent);
                }
                cards.put(card.number(), card.withGroups(groups));
            }
        }
        return true;
    }

    /** Kaart {@code number} ook in deze groep (zijn andere groepen blijven). False als een van beide er niet is, of hij er al in zit. */
    public boolean addToGroup(int number, String group) {
        Card card = cards.get(number);
        String own = spelling(group);
        if (card == null || own == null || card.isIn(own)) {
            return false;
        }
        List<String> groups = new ArrayList<>(card.groups());
        groups.add(own);
        cards.put(number, card.withGroups(groups));
        return true;
    }

    /** Kaart {@code number} uit deze groep (zijn andere groepen blijven). False als hij er niet in zat. */
    public boolean removeFromGroup(int number, String group) {
        Card card = cards.get(number);
        if (card == null || !card.isIn(group)) {
            return false;
        }
        List<String> groups = new ArrayList<>();
        for (String other : card.groups()) {
            if (!other.equalsIgnoreCase(group.strip())) {
                groups.add(other);
            }
        }
        cards.put(number, card.withGroups(groups));
        return true;
    }

    // ================================================================ Diagrammen

    /** Alle diagrammen, in de volgorde van de lijst (de bovenste krijgen als eerste een plek op de SSD's). */
    public List<LogicProgram> programs() {
        return Collections.unmodifiableList(programs);
    }

    /** Het diagram met deze naam (hoofdletters maken niet uit), of null. */
    public @Nullable LogicProgram program(String name) {
        for (LogicProgram program : programs) {
            if (program.name().equalsIgnoreCase(name)) {
                return program;
            }
        }
        return null;
    }

    /**
     * Bewaart een diagram. Heette het eerst anders ({@code oldName}), dan komt het op dezelfde plek in
     * de lijst; een nieuw diagram komt onderaan.
     */
    public void put(@Nullable String oldName, LogicProgram program) {
        int at = -1;
        for (int i = 0; i < programs.size(); i++) {
            String name = programs.get(i).name();
            if (oldName != null && name.equalsIgnoreCase(oldName) || name.equalsIgnoreCase(program.name())) {
                at = i;
                break;
            }
        }
        if (at >= 0) {
            programs.set(at, program);
            // Een tweede diagram met de oude of nieuwe naam (kan bij hernoemen) gaat weg.
            for (int i = programs.size() - 1; i > at; i--) {
                String name = programs.get(i).name();
                if (name.equalsIgnoreCase(program.name()) || oldName != null && name.equalsIgnoreCase(oldName)) {
                    programs.remove(i);
                }
            }
        } else {
            programs.add(program);
        }
        revision++;
    }

    public boolean remove(String name) {
        boolean removed = programs.removeIf(program -> program.name().equalsIgnoreCase(name));
        if (removed) {
            revision++;
        }
        return removed;
    }

    public int revision() {
        return revision;
    }

    /**
     * Alles op de SSD's is weg (8 oktober: een SSD verbrandde of ging kapot door de hitte): alle
     * diagrammen en alle keycards, met hun groepen. Het telefoonboek van apparaten blijft (dat komt
     * toch weer uit je redstone-lijst), en kaartnummers komen nooit terug, ook nu niet.
     */
    public void wipe() {
        programs.clear();
        cards.clear();
        groupParents.clear();
        revision++;
    }

    /**
     * Zet een backup terug: de diagrammen van {@code saved} vervangen de huidige. De keycards (met
     * hun groepen) alleen als {@code withCards} (de backup is van dit netwerk). Kaartnummers gaan
     * nooit terug: het volgende nummer is het hoogste van de twee.
     */
    public void restoreFrom(LogicStore saved, boolean withCards) {
        programs.clear();
        for (LogicProgram program : saved.programs) {
            if (programs.size() < MAX_PROGRAMS) {
                programs.add(program);
            }
        }
        if (withCards) {
            cards.clear();
            cards.putAll(saved.cards);
            groupParents.clear();
            groupParents.putAll(saved.groupParents);
        }
        nextCard = Math.max(nextCard, saved.nextCard);
        revision++;
    }

    /** Hoeveel blokjes alle diagrammen samen gebruiken (zo vol zijn de SSD's). */
    public int blocks() {
        int total = 0;
        for (LogicProgram program : programs) {
            total += program.graph().size();
        }
        return total;
    }

    // ================================================================ Apparaten

    /** De apparaten met deze naam (hoofdletters maken niet uit); leeg als er geen is. */
    public List<Target> devices(String name) {
        List<Target> found = devices.get(name);
        return found == null ? List.of() : found;
    }

    /** Alle namen van apparaten, op alfabet. */
    public List<String> deviceNames() {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, List<Target>> entry : devices.entrySet()) {
            names.add(entry.getKey());
        }
        return names;
    }

    /** De namen van het apparaat op deze plek (meestal één). */
    public List<String> namesAt(BlockPos pos, int channel) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, List<Target>> entry : devices.entrySet()) {
            for (Target target : entry.getValue()) {
                if (target.pos().equals(pos) && target.channel() == channel) {
                    names.add(entry.getKey());
                    break;
                }
            }
        }
        return names;
    }

    /** Het nieuwe telefoonboek (van LogicDevices.refresh). */
    public boolean setDevices(Map<String, List<Target>> fresh) {
        TreeMap<String, List<Target>> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<Target>> entry : fresh.entrySet()) {
            sorted.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        // Hetzelfde als wat we al hadden (ook dezelfde hoofdletters)? Dan verandert er niets.
        if (new ArrayList<>(sorted.entrySet()).equals(new ArrayList<>(devices.entrySet()))) {
            return false;
        }
        devices.clear();
        devices.putAll(sorted);
        return true;
    }

    // ================================================================ Opslaan

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("NextCard", nextCard);
        ListTag list = new ListTag();
        for (Card card : cards.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("N", card.number());
            entry.putString("L", card.label());
            entry.putInt("C", card.colour());
            entry.putBoolean("B", card.blocked());
            entry.putString("W", card.writtenBy());
            ListTag groups = new ListTag();
            for (String group : card.groups()) {
                groups.add(StringTag.valueOf(group));
            }
            entry.put("G", groups);
            list.add(entry);
        }
        tag.put("Cards", list);
        // De groepen (25 oktober). Ook als er geen zijn: zo weet load() dat dit geen kern van voor de groepen is.
        ListTag groupList = new ListTag();
        groupParents.forEach((group, parent) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("N", group);
            entry.putString("P", parent);
            groupList.add(entry);
        });
        tag.put("Groups", groupList);
        ListTag programList = new ListTag();
        for (LogicProgram program : programs) {
            programList.add(program.save());
        }
        tag.put("Programs", programList);
        ListTag deviceList = new ListTag();
        for (Map.Entry<String, List<Target>> entry : devices.entrySet()) {
            for (Target target : entry.getValue()) {
                CompoundTag device = new CompoundTag();
                device.putString("N", entry.getKey());
                device.putLong("P", target.pos().asLong());
                device.putInt("C", target.channel());
                deviceList.add(device);
            }
        }
        tag.put("Devices", deviceList);
        return tag;
    }

    public static LogicStore load(CompoundTag tag) {
        LogicStore store = new LogicStore();
        store.nextCard = Math.max(FIRST_CARD, tag.getInt("NextCard"));
        for (Tag element : tag.getList("Groups", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            String group = entry.getString("N").strip();
            if (validGroupName(group) && !store.hasGroup(group) && store.groupParents.size() < MAX_GROUPS) {
                store.groupParents.put(group, entry.getString("P").strip());
            }
        }
        store.repairGroups();
        // Van voor 25 oktober: toen had een kaart een niveau ("V") in plaats van een kleur en groepen.
        boolean beforeGroups = !tag.contains("Groups");
        Map<Integer, Integer> levels = new TreeMap<>();
        for (Tag element : tag.getList("Cards", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            List<String> groups = new ArrayList<>();
            for (Tag group : entry.getList("G", Tag.TAG_STRING)) {
                String own = store.spelling(group.getAsString());
                if (own != null && groups.stream().noneMatch(own::equalsIgnoreCase)) {
                    groups.add(own);
                }
            }
            int colour = entry.contains("C") ? entry.getInt("C") : entry.getInt("V");
            Card card = new Card(entry.getInt("N"), entry.getString("L"), KeycardData.clampColour(colour), entry.getBoolean("B"),
                    entry.getString("W"), groups);
            store.cards.put(card.number(), card);
            store.nextCard = Math.max(store.nextCard, card.number() + 1);
            if (beforeGroups && entry.contains("V")) {
                levels.put(card.number(), Math.max(1, Math.min(5, entry.getInt("V"))));
            }
        }
        store.groupsFromLevels(levels);
        for (Tag element : tag.getList("Programs", Tag.TAG_COMPOUND)) {
            if (store.programs.size() < MAX_PROGRAMS) {
                store.programs.add(LogicProgram.load((CompoundTag) element));
            }
        }
        for (Tag element : tag.getList("Devices", Tag.TAG_COMPOUND)) {
            CompoundTag device = (CompoundTag) element;
            store.devices.computeIfAbsent(device.getString("N"), key -> new ArrayList<>())
                    .add(new Target(BlockPos.of(device.getLong("P")), device.getInt("C")));
        }
        return store;
    }

    /**
     * Na het laden: elke groep zit in een groep die bestaat (anders bovenaan, met de schrijfwijze van die groep), en
     * er zijn geen rondjes (die kunnen alleen door een fout in de opslag komen; dan komt die groep bovenaan).
     */
    private void repairGroups() {
        for (Map.Entry<String, String> entry : groupParents.entrySet()) {
            String parent = spelling(entry.getValue());
            entry.setValue(parent == null || parent.equalsIgnoreCase(entry.getKey()) ? "" : parent);
        }
        for (String group : groups()) {
            String at = parentOf(group);
            for (int steps = 0; !at.isEmpty(); steps++) {
                if (at.equalsIgnoreCase(group) || steps > groupParents.size()) {
                    groupParents.put(group, "");
                    break;
                }
                at = parentOf(at);
            }
        }
    }

    /**
     * Een kern van voor 25 oktober: toen had elke kaart een niveau (1-5) in plaats van groepen. Elke kaart komt in de
     * groep "level N" van zijn niveau, en die groepen zitten in elkaar ("level 5" in "level 4" in ... "level 1").
     * Zo is {@code card.in_group("level 3")} precies wat {@code card.level >= 3} was. Er komen groepen tot en met het
     * hoogste niveau dat er was (alleen kaarten van niveau 2: "level 1" en "level 2").
     */
    private void groupsFromLevels(Map<Integer, Integer> levels) {
        if (levels.isEmpty()) {
            return;
        }
        int highest = levels.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        String below = "";
        for (int level = 1; level <= highest; level++) {
            String group = "level " + level;
            groupParents.put(group, below);
            below = group;
        }
        levels.forEach((number, level) -> {
            Card card = cards.get(number);
            if (card != null) {
                cards.put(number, card.withGroups(List.of("level " + level)));
            }
        });
    }
}
