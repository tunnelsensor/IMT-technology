package com.senne.imttech.security.os;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Het logboek van een beveiligingssysteem: wie binnenkwam, wie beschoten werd, wie probeerde te
 * hacken, foute wachtwoorden, valstrikken, een nieuwe eigenaar. Je leest het in het Logs-venster
 * van de desktop.
 *
 * Alleen de laatste MAX_ENTRIES regels blijven bewaard (de oudste valt eraf).
 * <p>
 * Hoofdstuk 138: een regel kan <b>gemarkeerd</b> zijn ({@code flag}: waarom), door een serverdiagram met
 * {@code when logged:} en {@code flag("...")}. In Logs is hij dan rood, met de reden ervoor.
 */
public final class EventLog {
    public static final int MAX_ENTRIES = 64;

    /** Soort regel: bepaalt het icoontje en de kleur in het Logs-venster. */
    public enum Type { ENTRY, SHOT, HACK, LOGIN_FAIL, LOGIN, TRAP, OWNER, DEVICE, SYSTEM, LOGIC, ALARM, HEAT, ZONE, TRAINING }

    /** Eén regel: wanneer (speltijd in ticks), wat voor soort, de tekst (Engels), en (hoofdstuk 138) waarom gemarkeerd ("" = niet). */
    public record Entry(long time, Type type, String text, String flag) {
        public Entry(long time, Type type, String text) {
            this(time, type, text, "");
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();

    public void add(long time, Type type, String text) {
        append(new Entry(time, type, text));
    }

    /** Voegt een regel toe en geeft hem terug (hoofdstuk 138: zo kan when logged: precies deze regel markeren). */
    public Entry append(Entry entry) {
        entries.addLast(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
        return entry;
    }

    /**
     * Markeert deze regel (dezelfde, niet een die er alleen op lijkt). Geeft false als hij er niet meer is (intussen van het
     * logboek gevallen) of al gemarkeerd was.
     */
    public boolean flag(Entry entry, String reason) {
        List<Entry> all = new ArrayList<>(entries);
        int at = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i) == entry) {
                at = i;
            }
        }
        // Werd het logboek intussen opnieuw geladen (de wereld rond de kern ging even uit), dan is het dezelfde regel als
        // hij er nog precies zo staat (tijd, soort en tekst), en nog niet gemarkeerd (de nalezing).
        for (int i = all.size() - 1; at < 0 && i >= 0; i--) {
            Entry other = all.get(i);
            if (other.time() == entry.time() && other.type() == entry.type() && other.text().equals(entry.text())
                    && other.flag().isEmpty()) {
                at = i;
            }
        }
        for (int i = 0; i < all.size(); i++) {
            if (i == at) {
                entry = all.get(i);
                if (!entry.flag().isEmpty()) {
                    return false;
                }
                all.set(i, new Entry(entry.time(), entry.type(), entry.text(), reason.isBlank() ? "suspicious" : reason));
                entries.clear();
                entries.addAll(all);
                return true;
            }
        }
        return false;
    }

    /** Alle regels, de oudste eerst. */
    public List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    public void clear() {
        entries.clear();
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("T", entry.time());
            tag.putString("K", entry.type().name());
            tag.putString("X", entry.text());
            if (!entry.flag().isEmpty()) {
                tag.putString("F", entry.flag());
            }
            list.add(tag);
        }
        return list;
    }

    public static EventLog load(ListTag list) {
        EventLog log = new EventLog();
        for (Tag element : list) {
            CompoundTag tag = (CompoundTag) element;
            Type type;
            try {
                type = Type.valueOf(tag.getString("K"));
            } catch (IllegalArgumentException e) {
                type = Type.SYSTEM;
            }
            log.append(new Entry(tag.getLong("T"), type, tag.getString("X"), tag.getString("F")));
        }
        return log;
    }
}
