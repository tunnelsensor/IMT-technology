package com.senne.starktech.redstone;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Alle E.D.I.T.H.-relais die één speler heeft aangemeld, met hun naam en groep.
 *
 * Wordt bij de speler opgeslagen (zie ModAttachments) en naar zijn eigen spel gestuurd, zodat
 * het scherm en de HUD de lijst kennen.
 *
 * Dit object verandert nooit: bij een wijziging maken we een nieuw object. Zo weet NeoForge
 * dat het opnieuw naar de speler gestuurd moet worden.
 */
public record EdithRelays(List<Relay> relays) {
    public static final EdithRelays EMPTY = new EdithRelays(List.of());

    public static final MapCodec<EdithRelays> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Relay.CODEC.listOf().optionalFieldOf("relays", List.of()).forGetter(EdithRelays::relays)
    ).apply(instance, EdithRelays::new));

    public static final StreamCodec<ByteBuf, EdithRelays> STREAM_CODEC = StreamCodec.composite(
            Relay.STREAM_CODEC.apply(ByteBufCodecs.list(256)), EdithRelays::relays,
            EdithRelays::new);

    public EdithRelays {
        relays = List.copyOf(relays);
    }

    /** Het relais op deze plek, of null. */
    public @Nullable Relay at(ResourceKey<Level> dimension, BlockPos pos) {
        for (Relay relay : relays) {
            if (relay.pos().equals(pos) && relay.dimension() == dimension) {
                return relay;
            }
        }
        return null;
    }

    /** Het nummer van het relais op deze plek in de lijst, of -1. */
    public int indexAt(ResourceKey<Level> dimension, BlockPos pos) {
        for (int i = 0; i < relays.size(); i++) {
            Relay relay = relays.get(i);
            if (relay.pos().equals(pos) && relay.dimension() == dimension) {
                return i;
            }
        }
        return -1;
    }

    public @Nullable Relay get(int index) {
        return index >= 0 && index < relays.size() ? relays.get(index) : null;
    }

    public EdithRelays with(Relay relay) {
        List<Relay> list = new ArrayList<>(relays);
        list.add(relay);
        return new EdithRelays(list);
    }

    public EdithRelays without(int index) {
        if (index < 0 || index >= relays.size()) {
            return this;
        }
        List<Relay> list = new ArrayList<>(relays);
        list.remove(index);
        return new EdithRelays(list);
    }

    /** Vervangt het relais op plek index (bv. na het wijzigen van naam of groep). */
    public EdithRelays replace(int index, Relay relay) {
        if (index < 0 || index >= relays.size()) {
            return this;
        }
        List<Relay> list = new ArrayList<>(relays);
        list.set(index, relay);
        return new EdithRelays(list);
    }

    /** Alle groepsnamen die je gebruikt, op volgorde van het eerste relais in die groep. */
    public Set<String> groups() {
        Set<String> groups = new LinkedHashSet<>();
        for (Relay relay : relays) {
            groups.add(relay.group());
        }
        return groups;
    }

    /** Alle relais in een groep (niet gevoelig voor grote/kleine letters). */
    public List<Relay> inGroup(String group) {
        String wanted = group.toLowerCase(Locale.ROOT).strip();
        List<Relay> found = new ArrayList<>();
        for (Relay relay : relays) {
            if (relay.group().toLowerCase(Locale.ROOT).equals(wanted)) {
                found.add(relay);
            }
        }
        return found;
    }

    /**
     * Zoekt relais op naam OF op groep. Zegt de speler "zet de lichten aan", dan kan "lichten"
     * zowel de naam van één relais als de naam van een hele groep zijn.
     */
    public List<Relay> find(String nameOrGroup) {
        String wanted = nameOrGroup.toLowerCase(Locale.ROOT).strip();
        if (wanted.isEmpty()) {
            return List.of();
        }
        // 1. Precies dezelfde naam.
        for (Relay relay : relays) {
            if (relay.name().toLowerCase(Locale.ROOT).equals(wanted)) {
                return List.of(relay);
            }
        }
        // 2. Een hele groep.
        List<Relay> group = inGroup(wanted);
        if (!group.isEmpty()) {
            return group;
        }
        // 3. Namen die het woord bevatten ("keukenlicht" bij "licht").
        List<Relay> partial = new ArrayList<>();
        for (Relay relay : relays) {
            String name = relay.name().toLowerCase(Locale.ROOT);
            if (name.contains(wanted) || wanted.contains(name)) {
                partial.add(relay);
            }
        }
        return partial;
    }
}
