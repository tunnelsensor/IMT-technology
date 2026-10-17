package com.senne.imttech.redstone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Alle I.M.T-relais die één speler heeft aangemeld, met hun naam en groep, en welke groep in welke
 * andere groep zit.
 *
 * <h2>Groepen in groepen (14 oktober)</h2>
 * Jouw voorbeeld:
 * <pre>
 *   base
 *     visitors
 *       cafe
 *       storage
 *     restricted
 *       security room
 *       power room
 * </pre>
 * Een relais zit in hooguit één groep ("security room"), en een groep in hooguit één andere groep
 * ({@link #groupParents}: "security room" zit in "restricted", die zit in "base"). Zo is het een echte boom.
 * Schakel je "base", dan gaat alles eronder mee. Een groep kan nooit (via een omweg) in zichzelf: "base"
 * in "temp" kan niet als "temp" al in "base" zit (zie {@link #wouldLoop}).
 *
 * Tot 14 oktober waren dit tags (een relais met meerdere tags, een tag in meerdere tags). Die zijn helemaal
 * weg ("tags zijn volledig weg"); bij het inladen wordt de eerste tag de groep.
 *
 * Wordt bij de speler opgeslagen (zie ModAttachments) en naar zijn eigen spel gestuurd, zodat de schermen
 * (ook de app Groups op de Security OS-desktop) en de HUD de lijst kennen.
 *
 * Dit object verandert nooit: bij een wijziging maken we een nieuw object. Zo weet NeoForge dat het opnieuw
 * naar de speler gestuurd moet worden.
 *
 * @param relays       de relais, in de volgorde waarin je ze aanmeldde
 * @param groupParents per groep: in welke groep hij zit ("" = bovenaan, in geen groep). Een groep die je
 *                     aanmaakte maar die nergens in zit, staat hier met "".
 */
public record ImtRelays(List<Relay> relays, Map<String, String> groupParents) {
    public static final ImtRelays EMPTY = new ImtRelays(List.of(), Map.of());

    /**
     * Opslaan als "group_parents". Een lijst uit een oudere wereld heeft nog "tag_parents" (13 oktober: per
     * tag een lijst tags erboven): de eerste daarvan wordt de groep erboven.
     */
    public static final MapCodec<ImtRelays> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Relay.CODEC.listOf().optionalFieldOf("relays", List.of()).forGetter(ImtRelays::relays),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("group_parents")
                    .forGetter(data -> Optional.of(data.groupParents())),
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("tag_parents")
                    .forGetter(data -> Optional.empty())
    ).apply(instance, ImtRelays::load));

    private static ImtRelays load(List<Relay> relays, Optional<Map<String, String>> parents,
                                    Optional<Map<String, List<String>>> oldTagParents) {
        Map<String, String> map = new LinkedHashMap<>();
        if (parents.isPresent()) {
            map.putAll(parents.get());
        } else {
            oldTagParents.ifPresent(old -> old.forEach((tag, above) -> map.put(tag, above.isEmpty() ? "" : above.getFirst())));
        }
        // Kwam er (uit oude tags) toch een rondje in, dan breken we het hier: de groep gaat naar bovenaan.
        ImtRelays data = new ImtRelays(relays, map);
        for (String group : new ArrayList<>(map.keySet())) {
            if (data.loopsAbove(group)) {
                map.put(group, "");
                data = new ImtRelays(relays, map);
            }
        }
        return data;
    }

    public static final StreamCodec<ByteBuf, ImtRelays> STREAM_CODEC = StreamCodec.composite(
            Relay.STREAM_CODEC.apply(ByteBufCodecs.list(256)), ImtRelays::relays,
            ByteBufCodecs.<ByteBuf, String, String, Map<String, String>>map(LinkedHashMap::new,
                    ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8, 1024),
            ImtRelays::groupParents,
            ImtRelays::new);

    public ImtRelays {
        relays = List.copyOf(relays);
        groupParents = Collections.unmodifiableMap(new LinkedHashMap<>(groupParents));
    }

    public ImtRelays(List<Relay> relays) {
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

    public ImtRelays with(Relay relay) {
        List<Relay> list = new ArrayList<>(relays);
        list.add(relay);
        return new ImtRelays(list, groupParents);
    }

    public ImtRelays without(int index) {
        if (index < 0 || index >= relays.size()) {
            return this;
        }
        List<Relay> list = new ArrayList<>(relays);
        list.remove(index);
        return new ImtRelays(list, groupParents);
    }

    /** Vervangt het relais op plek index (bv. na het wijzigen van naam of groep). */
    public ImtRelays replace(int index, Relay relay) {
        if (index < 0 || index >= relays.size()) {
            return this;
        }
        List<Relay> list = new ArrayList<>(relays);
        list.set(index, relay);
        return new ImtRelays(list, groupParents);
    }

    // ================================================================ Groepen: welke zijn er?

    /**
     * Alle groepen: waar een relais in zit, waar een groep in zit, of zelf aangemaakt. Op alfabet, elke groep
     * één keer (hoofdletters maken niet uit; we tonen de schrijfwijze die het eerst voorkomt).
     */
    public List<String> groups() {
        Map<String, String> found = new LinkedHashMap<>();
        for (Relay relay : relays) {
            if (relay.hasGroup()) {
                found.putIfAbsent(key(relay.group()), relay.group());
            }
        }
        groupParents.forEach((group, parent) -> {
            found.putIfAbsent(key(group), group);
            if (!parent.isEmpty()) {
                found.putIfAbsent(key(parent), parent);
            }
        });
        List<String> sorted = new ArrayList<>(found.values());
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    /** Bestaat deze groep (ergens)? */
    public boolean hasGroup(String group) {
        return !group.isBlank() && groups().stream().anyMatch(known -> known.equalsIgnoreCase(group.strip()));
    }

    /** De groep waar deze groep direct in zit, of "" (bovenaan). */
    public String parentOf(String group) {
        String stored = storedKey(group);
        return stored == null ? "" : groupParents.get(stored);
    }

    /** De groepen die direct in deze groep zitten, op alfabet. */
    public List<String> childrenOf(String group) {
        List<String> children = new ArrayList<>();
        for (String other : groups()) {
            if (parentOf(other).equalsIgnoreCase(group)) {
                children.add(other);
            }
        }
        return children;
    }

    /** De groepen die nergens in zitten: de bovenkant van de boom ("base"). */
    public List<String> topGroups() {
        List<String> top = new ArrayList<>();
        for (String group : groups()) {
            if (parentOf(group).isEmpty()) {
                top.add(group);
            }
        }
        return top;
    }

    /**
     * De weg van boven naar deze groep: ["base", "restricted", "security room"]. Leeg voor "" (geen groep).
     */
    public List<String> pathOf(String group) {
        List<String> path = new ArrayList<>();
        String next = group.strip();
        while (!next.isEmpty() && !containsIgnoreCase(path, next)) {
            path.addFirst(spelling(next));
            next = parentOf(next);
        }
        return path;
    }

    private static boolean containsIgnoreCase(List<String> list, String wanted) {
        for (String own : list) {
            if (own.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Zit {@code group} (direct, of via groepen ertussen) in {@code outer}? "security room" zit in "base", en
     * een groep zit ook "in" zichzelf. Een relais zonder groep ("") zit nergens in.
     */
    public boolean isInside(String group, String outer) {
        if (group.isEmpty() || outer.isEmpty()) {
            return false;
        }
        List<String> seen = new ArrayList<>();
        String next = group;
        while (!next.isEmpty()) {
            if (next.equalsIgnoreCase(outer)) {
                return true;
            }
            if (containsIgnoreCase(seen, next)) {
                return false; // een rondje (kan niet, maar zo loopt het nooit vast)
            }
            seen.add(next);
            next = parentOf(next);
        }
        return false;
    }

    /**
     * Zou "{@code group} zit in {@code parent}" een rondje maken? Dat is zo als {@code parent} al (via een
     * omweg) in {@code group} zit, of als het dezelfde groep is: "base" in "temp" kan niet als "temp" al in
     * "base" zit. Bovenaan ({@code parent} = "") kan altijd.
     */
    public boolean wouldLoop(String group, String parent) {
        return isInside(parent, group);
    }

    /** Komt deze groep, als je steeds een groep omhoog gaat, weer bij zichzelf uit? */
    private boolean loopsAbove(String group) {
        return isInside(parentOf(group), group);
    }

    // ================================================================ Groepen: veranderen

    /** Een nieuwe (lege) groep, in {@code parent} ("" = bovenaan). Bestaat hij al, dan gebeurt er niets. */
    public ImtRelays withGroup(String group, String parent) {
        if (hasGroup(group)) {
            return this;
        }
        Map<String, String> map = new LinkedHashMap<>(groupParents);
        map.put(group.strip(), hasGroup(parent) ? spelling(parent) : "");
        return new ImtRelays(relays, map);
    }

    /**
     * "{@code group} zit voortaan in {@code parent}" ("" = bovenaan, in geen groep). Een groep zit maar in één
     * groep, dus dit vervangt de oude plek. Doet niets als dat een rondje zou maken (zie {@link #wouldLoop}).
     */
    public ImtRelays withParent(String group, String parent) {
        if (!hasGroup(group) || !parent.isEmpty() && !hasGroup(parent) || wouldLoop(group, parent)) {
            return this;
        }
        Map<String, String> map = new LinkedHashMap<>(groupParents);
        String stored = storedKey(group);
        if (stored != null) {
            map.remove(stored);
        }
        map.put(stored == null ? spelling(group) : stored, parent.isEmpty() ? "" : spelling(parent));
        return new ImtRelays(relays, map);
    }

    /**
     * Geeft een groep een andere naam: de relais erin, de groepen erin en zijn eigen plek gaan mee. Doet niets
     * als er al een (andere) groep zo heet.
     */
    public ImtRelays renameGroup(String group, String newName) {
        String fresh = newName.strip();
        if (!hasGroup(group) || fresh.isEmpty() || hasGroup(fresh) && !fresh.equalsIgnoreCase(group)) {
            return this;
        }
        List<Relay> list = new ArrayList<>();
        for (Relay relay : relays) {
            list.add(relay.isIn(group) ? relay.withGroup(fresh) : relay);
        }
        Map<String, String> map = new LinkedHashMap<>();
        String parent = parentOf(group);
        boolean placed = false;
        for (Map.Entry<String, String> entry : groupParents.entrySet()) {
            boolean self = entry.getKey().equalsIgnoreCase(group);
            String above = entry.getValue().equalsIgnoreCase(group) ? fresh : entry.getValue();
            map.put(self ? fresh : entry.getKey(), above);
            placed |= self;
        }
        if (!placed) {
            map.put(fresh, parent); // een groep die alleen op relais stond, komt nu in de lijst
        }
        return new ImtRelays(list, map);
    }

    /**
     * Gooit een groep weg. Wat erin zat (relais en groepen) schuift één laag omhoog: in de groep erboven, of
     * bovenaan (relais: geen groep) als hij nergens in zat. Zo blijft "base" alles schakelen wat er eerst in
     * zat. De relais zelf blijven altijd.
     */
    public ImtRelays withoutGroup(String group) {
        String parent = parentOf(group);
        List<Relay> list = new ArrayList<>();
        for (Relay relay : relays) {
            list.add(relay.isIn(group) ? relay.withGroup(parent) : relay);
        }
        Map<String, String> map = new LinkedHashMap<>();
        groupParents.forEach((own, above) -> {
            if (!own.equalsIgnoreCase(group)) {
                map.put(own, above.equalsIgnoreCase(group) ? parent : above);
            }
        });
        return new ImtRelays(list, map);
    }

    // ================================================================ Zoeken

    /** Alle relais in deze groep: zelf, of via een groep die erin zit. Hoofdletters maken niet uit. */
    public List<Relay> inGroup(String group) {
        String wanted = group.strip();
        List<Relay> found = new ArrayList<>();
        for (Relay relay : relays) {
            if (isInside(relay.group(), wanted)) {
                found.add(relay);
            }
        }
        return found;
    }

    /**
     * Precies deze naam of groep, zonder te raden: alle relais met die naam, en anders alles in die groep.
     * Voor de relay emitter: die moet nooit per ongeluk "keukenlicht" schakelen als je "licht" typte.
     */
    public List<Relay> named(String nameOrGroup) {
        String wanted = nameOrGroup.strip();
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<Relay> found = new ArrayList<>();
        for (Relay relay : relays) {
            if (relay.name().equalsIgnoreCase(wanted)) {
                found.add(relay);
            }
        }
        return found.isEmpty() ? inGroup(wanted) : found;
    }

    /**
     * Zoekt relais op naam OF op groep. Zegt de speler "zet de lichten aan", dan kan "lichten" zowel de naam
     * van één relais als een hele groep zijn (met alles wat erin zit).
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
        // 2. Een hele groep (met de groepen die erin zitten).
        List<Relay> grouped = inGroup(wanted);
        if (!grouped.isEmpty()) {
            return grouped;
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

    private static String key(String group) {
        return group.toLowerCase(Locale.ROOT);
    }

    /** Hoe de groep in {@link #groupParents} staat (met zijn eigen hoofdletters), of null. */
    private @Nullable String storedKey(String group) {
        for (String own : groupParents.keySet()) {
            if (own.equalsIgnoreCase(group)) {
                return own;
            }
        }
        return null;
    }

    /** De schrijfwijze die we al kennen voor deze groep, of zoals hij nu getypt is. */
    public String spelling(String group) {
        for (String known : groups()) {
            if (known.equalsIgnoreCase(group.strip())) {
                return known;
            }
        }
        return group.strip();
    }
}
