package com.senne.starktech.redstone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Alle E.D.I.T.H.-relais die één speler heeft aangemeld, met hun naam en tags, en welke tag in welke
 * andere tag zit.
 *
 * <h2>Tags in tags (13 oktober)</h2>
 * Jouw voorbeeld: "deur1 en deur2 zitten in security room, en die zit in base". De relais hebben de
 * tag "security room"; in {@link #tagParents} staat dat "security room" in "base" zit. Schakel je
 * "base", dan gaan deur1 en deur2 mee. Een tag mag in meerdere tags zitten, maar nooit (via een
 * omweg) in zichzelf: zie {@link #wouldLoop}.
 *
 * Wordt bij de speler opgeslagen (zie ModAttachments) en naar zijn eigen spel gestuurd, zodat
 * het scherm en de HUD de lijst kennen.
 *
 * Dit object verandert nooit: bij een wijziging maken we een nieuw object. Zo weet NeoForge
 * dat het opnieuw naar de speler gestuurd moet worden.
 *
 * @param relays     de relais, in de volgorde waarin je ze aanmeldde
 * @param tagParents per tag: in welke tags hij zit. Een tag die je in het tag-scherm aanmaakte maar
 *                   (nog) nergens in zit, staat hier met een lege lijst.
 */
public record EdithRelays(List<Relay> relays, Map<String, List<String>> tagParents) {
    public static final EdithRelays EMPTY = new EdithRelays(List.of(), Map.of());

    public static final MapCodec<EdithRelays> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Relay.CODEC.listOf().optionalFieldOf("relays", List.of()).forGetter(EdithRelays::relays),
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("tag_parents", Map.of())
                    .forGetter(EdithRelays::tagParents)
    ).apply(instance, EdithRelays::new));

    public static final StreamCodec<ByteBuf, EdithRelays> STREAM_CODEC = StreamCodec.composite(
            Relay.STREAM_CODEC.apply(ByteBufCodecs.list(256)), EdithRelays::relays,
            ByteBufCodecs.<ByteBuf, String, List<String>, Map<String, List<String>>>map(LinkedHashMap::new,
                    ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), 1024),
            EdithRelays::tagParents,
            EdithRelays::new);

    public EdithRelays {
        relays = List.copyOf(relays);
        Map<String, List<String>> copy = new LinkedHashMap<>();
        tagParents.forEach((tag, parents) -> copy.put(tag, List.copyOf(parents)));
        tagParents = Collections.unmodifiableMap(copy);
    }

    public EdithRelays(List<Relay> relays) {
        this(relays, Map.of());
    }

    // ================================================================ Relais

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

    /** Het nummer van dit kanaal (een kant van een stroomkast) op deze plek, of -1. */
    public int indexAt(ResourceKey<Level> dimension, BlockPos pos, int channel) {
        for (int i = 0; i < relays.size(); i++) {
            Relay relay = relays.get(i);
            if (relay.pos().equals(pos) && relay.dimension() == dimension && relay.channel() == channel) {
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
        return new EdithRelays(list, tagParents);
    }

    public EdithRelays without(int index) {
        if (index < 0 || index >= relays.size()) {
            return this;
        }
        List<Relay> list = new ArrayList<>(relays);
        list.remove(index);
        return new EdithRelays(list, tagParents);
    }

    /** Vervangt het relais op plek index (bv. na het wijzigen van naam of tags). */
    public EdithRelays replace(int index, Relay relay) {
        if (index < 0 || index >= relays.size()) {
            return this;
        }
        List<Relay> list = new ArrayList<>(relays);
        list.set(index, relay);
        return new EdithRelays(list, tagParents);
    }

    // ================================================================ Tags: welke zijn er?

    /**
     * Alle tags die je gebruikt: op een relais, in een andere tag, of zelf aangemaakt. Op alfabet,
     * elke tag één keer (hoofdletters maken niet uit; we tonen de schrijfwijze die het eerst voorkomt).
     */
    public List<String> tags() {
        Map<String, String> found = new LinkedHashMap<>();
        for (Relay relay : relays) {
            for (String tag : relay.tags()) {
                found.putIfAbsent(key(tag), tag);
            }
        }
        tagParents.forEach((tag, parents) -> {
            found.putIfAbsent(key(tag), tag);
            for (String parent : parents) {
                found.putIfAbsent(key(parent), parent);
            }
        });
        List<String> sorted = new ArrayList<>(found.values());
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    /** Bestaat deze tag (ergens)? */
    public boolean hasTag(String tag) {
        return tags().stream().anyMatch(known -> known.equalsIgnoreCase(tag));
    }

    /** De tags waar deze tag direct in zit. */
    public List<String> parentsOf(String tag) {
        for (Map.Entry<String, List<String>> entry : tagParents.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(tag)) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    /** De tags die direct in deze tag zitten, op alfabet. */
    public List<String> childrenOf(String tag) {
        List<String> children = new ArrayList<>();
        for (String other : tags()) {
            if (parentsOf(other).stream().anyMatch(parent -> parent.equalsIgnoreCase(tag))) {
                children.add(other);
            }
        }
        return children;
    }

    /** De tags die nergens in zitten: de bovenkant van de boom ("base"). */
    public List<String> topTags() {
        List<String> top = new ArrayList<>();
        for (String tag : tags()) {
            if (parentsOf(tag).isEmpty()) {
                top.add(tag);
            }
        }
        return top;
    }

    /**
     * Zit {@code tag} (direct, of via tags ertussen) in {@code outer}? "security room" zit in "base", en
     * een tag zit ook "in" zichzelf.
     */
    public boolean isInside(String tag, String outer) {
        Deque<String> todo = new ArrayDeque<>();
        List<String> seen = new ArrayList<>();
        todo.add(tag);
        while (!todo.isEmpty()) {
            String next = todo.poll();
            if (next.equalsIgnoreCase(outer)) {
                return true;
            }
            if (seen.stream().anyMatch(done -> done.equalsIgnoreCase(next))) {
                continue; // al bekeken (zou niet moeten kunnen, maar zo loopt het nooit vast)
            }
            seen.add(next);
            todo.addAll(parentsOf(next));
        }
        return false;
    }

    /**
     * Zou "{@code tag} zit in {@code parent}" een rondje maken? Dat is zo als {@code parent} al (via een
     * omweg) in {@code tag} zit, of als het dezelfde tag is: "base" in "security room" kan niet als
     * "security room" al in "base" zit.
     */
    public boolean wouldLoop(String tag, String parent) {
        return isInside(parent, tag);
    }

    // ================================================================ Tags: veranderen

    /** Een nieuwe (lege) tag, zoals "base" die je in het tag-scherm aanmaakt. */
    public EdithRelays withTag(String tag) {
        if (hasTag(tag)) {
            return this;
        }
        Map<String, List<String>> map = new LinkedHashMap<>(tagParents);
        map.put(tag, List.of());
        return new EdithRelays(relays, map);
    }

    /** "{@code tag} zit in {@code parent}". Doet niets als dat een rondje zou maken (zie {@link #wouldLoop}). */
    public EdithRelays withParent(String tag, String parent) {
        if (wouldLoop(tag, parent) || parentsOf(tag).stream().anyMatch(own -> own.equalsIgnoreCase(parent))) {
            return this;
        }
        Map<String, List<String>> map = new LinkedHashMap<>(tagParents);
        String stored = storedKey(tag);
        List<String> parents = new ArrayList<>(parentsOf(tag));
        parents.add(spelling(parent));
        map.remove(stored);
        map.put(stored == null ? spelling(tag) : stored, parents);
        return new EdithRelays(relays, map);
    }

    /** "{@code tag} zit niet meer in {@code parent}". */
    public EdithRelays withoutParent(String tag, String parent) {
        String stored = storedKey(tag);
        if (stored == null) {
            return this;
        }
        Map<String, List<String>> map = new LinkedHashMap<>(tagParents);
        List<String> parents = new ArrayList<>(map.get(stored));
        if (!parents.removeIf(own -> own.equalsIgnoreCase(parent))) {
            return this;
        }
        map.put(stored, parents);
        return new EdithRelays(relays, map);
    }

    /**
     * Gooit een tag helemaal weg: van alle relais, en uit alle tags. De relais zelf blijven; tags die
     * erin zaten, zitten er gewoon niet meer in.
     */
    public EdithRelays withoutTag(String tag) {
        List<Relay> list = new ArrayList<>();
        for (Relay relay : relays) {
            list.add(relay.hasTag(tag) ? relay.withoutTag(tag) : relay);
        }
        Map<String, List<String>> map = new LinkedHashMap<>();
        tagParents.forEach((own, parents) -> {
            if (!own.equalsIgnoreCase(tag)) {
                List<String> left = new ArrayList<>(parents);
                left.removeIf(parent -> parent.equalsIgnoreCase(tag));
                map.put(own, left);
            }
        });
        return new EdithRelays(list, map);
    }

    // ================================================================ Zoeken

    /** Alle relais met deze tag: zelf, of via een tag die erin zit. Hoofdletters maken niet uit. */
    public List<Relay> inTag(String tag) {
        String wanted = tag.strip();
        List<Relay> found = new ArrayList<>();
        for (Relay relay : relays) {
            for (String own : relay.tags()) {
                if (isInside(own, wanted)) {
                    found.add(relay);
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Precies deze naam of tag, zonder te raden: alle relais met die naam, en anders alles in die tag.
     * Voor de relay emitter: die moet nooit per ongeluk "keukenlicht" schakelen als je "licht" typte.
     */
    public List<Relay> named(String nameOrTag) {
        String wanted = nameOrTag.strip();
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<Relay> found = new ArrayList<>();
        for (Relay relay : relays) {
            if (relay.name().equalsIgnoreCase(wanted)) {
                found.add(relay);
            }
        }
        return found.isEmpty() ? inTag(wanted) : found;
    }

    /**
     * Zoekt relais op naam OF op tag. Zegt de speler "zet de lichten aan", dan kan "lichten"
     * zowel de naam van één relais als een hele tag zijn (met alles wat erin zit).
     */
    public List<Relay> find(String nameOrTag) {
        String wanted = nameOrTag.toLowerCase(Locale.ROOT).strip();
        if (wanted.isEmpty()) {
            return List.of();
        }
        // 1. Precies dezelfde naam.
        for (Relay relay : relays) {
            if (relay.name().toLowerCase(Locale.ROOT).equals(wanted)) {
                return List.of(relay);
            }
        }
        // 2. Een hele tag (met de tags die erin zitten).
        List<Relay> tagged = inTag(wanted);
        if (!tagged.isEmpty()) {
            return tagged;
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

    // ================================================================ Hulpjes

    private static String key(String tag) {
        return tag.toLowerCase(Locale.ROOT);
    }

    /** Hoe de tag in {@link #tagParents} staat (met zijn eigen hoofdletters), of null. */
    private @Nullable String storedKey(String tag) {
        for (String own : tagParents.keySet()) {
            if (own.equalsIgnoreCase(tag)) {
                return own;
            }
        }
        return null;
    }

    /** De schrijfwijze die we al kennen voor deze tag, of zoals hij nu getypt is. */
    private String spelling(String tag) {
        for (String known : tags()) {
            if (known.equalsIgnoreCase(tag)) {
                return known;
            }
        }
        return tag;
    }
}
