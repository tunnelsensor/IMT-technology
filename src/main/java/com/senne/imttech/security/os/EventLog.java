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
 */
public final class EventLog {
    public static final int MAX_ENTRIES = 64;

    /** Soort regel: bepaalt het icoontje en de kleur in het Logs-venster. */
    public enum Type { ENTRY, SHOT, HACK, LOGIN_FAIL, LOGIN, TRAP, OWNER, DEVICE, SYSTEM, LOGIC, ALARM, HEAT, ZONE, TRAINING }

    /** Eén regel: wanneer (speltijd in ticks), wat voor soort, en de tekst (Engels). */
    public record Entry(long time, Type type, String text) {}

    private final Deque<Entry> entries = new ArrayDeque<>();

    public void add(long time, Type type, String text) {
        entries.addLast(new Entry(time, type, text));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
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
            log.add(tag.getLong("T"), type, tag.getString("X"));
        }
        return log;
    }
}
