package com.senne.starktech.logic;

import com.senne.starktech.logic.program.LogicProgram;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Wat de server van een beveiligingskern onthoudt:
 * <ul>
 *   <li>de keycards die hij schreef (en welke geblokkeerd zijn);</li>
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

    /** Een geschreven kaart: zijn nummer, naam, niveau, of hij geblokkeerd is, en wie hem schreef. */
    public record Card(int number, String label, int level, boolean blocked, String writtenBy) {
        Card withBlocked(boolean blocked) {
            return new Card(number, label, level, blocked, writtenBy);
        }
    }

    /** Een apparaat dat een diagram kan schakelen: een blok, en welke schakelaar ervan (-1 = het hele blok). */
    public record Target(BlockPos pos, int channel) {}

    private final TreeMap<Integer, Card> cards = new TreeMap<>();
    private int nextCard = FIRST_CARD;
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

    /** Een nieuwe kaart in de lijst (met het volgende nummer), of null als de lijst vol is. */
    public @Nullable Card issue(String label, int level, String writtenBy) {
        if (cards.size() >= MAX_CARDS) {
            return null;
        }
        Card card = new Card(nextCard++, label, Math.max(1, Math.min(KeycardData.MAX_LEVEL, level)), false, writtenBy);
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
     * staan, niet geblokkeerd zijn, en hetzelfde niveau hebben als in de lijst.
     */
    public boolean isValid(String networkIp, @Nullable KeycardData data) {
        if (data == null || !data.ip().equals(networkIp)) {
            return false;
        }
        Card card = cards.get(data.number());
        return card != null && !card.blocked() && card.level() == data.level();
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
     * diagrammen en alle keycards. Het telefoonboek van apparaten blijft (dat komt toch weer uit
     * je redstone-lijst), en kaartnummers komen nooit terug, ook nu niet.
     */
    public void wipe() {
        programs.clear();
        cards.clear();
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
            entry.putInt("V", card.level());
            entry.putBoolean("B", card.blocked());
            entry.putString("W", card.writtenBy());
            list.add(entry);
        }
        tag.put("Cards", list);
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
        for (Tag element : tag.getList("Cards", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            Card card = new Card(entry.getInt("N"), entry.getString("L"), entry.getInt("V"), entry.getBoolean("B"),
                    entry.getString("W"));
            store.cards.put(card.number(), card);
            store.nextCard = Math.max(store.nextCard, card.number() + 1);
        }
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
}
