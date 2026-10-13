package com.senne.starktech.redstone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Eén plek die E.D.I.T.H. kan aansturen: een E.D.I.T.H.-relais dat je ergens hebt neergezet.
 *
 * Je geeft het een naam ("voordeur", "lichten keuken") en tags ("security room", "doors"). Zo kun je
 * er één aanzetten, of alles met een tag tegelijk. Een tag kan zelf weer in een andere tag zitten
 * ("security room" zit in "base"): dat onthoudt EdithRelays, niet het relais.
 *
 * @param name      de naam die jij eraan gaf
 * @param tags      de tags waarin het zit (leeg = geen tag)
 * @param dimension in welke wereld het staat
 * @param pos       waar het staat
 * @param channel   welke schakelaar van het blok (een kant van een stroomkast), of -1 = het
 *                  hele blok (een relais, torentje, koepel)
 */
public record Relay(String name, List<String> tags, ResourceKey<Level> dimension, BlockPos pos, int channel) {
    /**
     * Vóór 13 oktober had een relais één groep, en "general" (eerder "algemeen") betekende "geen groep".
     * Die worden bij het inladen geen tag.
     */
    private static final List<String> OLD_DEFAULT_GROUPS = List.of("general", "algemeen");

    public Relay {
        tags = List.copyOf(tags);
    }

    /** Een relais voor het hele blok (geen apart kanaal). */
    public Relay(String name, List<String> tags, ResourceKey<Level> dimension, BlockPos pos) {
        this(name, tags, dimension, pos, -1);
    }

    /**
     * Opslaan als "tags". Een relais uit een oude wereld heeft nog een "group": die wordt zijn eerste tag
     * (behalve de oude standaardgroep, die betekende "geen groep").
     */
    public static final Codec<Relay> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(Relay::name),
            Codec.STRING.listOf().optionalFieldOf("tags").forGetter(relay -> Optional.of(relay.tags())),
            Codec.STRING.optionalFieldOf("group").forGetter(relay -> Optional.empty()),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Relay::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Relay::pos),
            Codec.INT.optionalFieldOf("channel", -1).forGetter(Relay::channel)
    ).apply(instance, Relay::load));

    private static Relay load(String name, Optional<List<String>> tags, Optional<String> oldGroup,
                              ResourceKey<Level> dimension, BlockPos pos, int channel) {
        List<String> list = tags.orElseGet(() -> oldGroup
                .filter(group -> !group.isBlank() && !OLD_DEFAULT_GROUPS.contains(group.toLowerCase(Locale.ROOT)))
                .map(List::of)
                .orElse(List.of()));
        return new Relay(name, list, dimension, pos, channel);
    }

    public static final StreamCodec<ByteBuf, Relay> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Relay::name,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), Relay::tags,
            ResourceKey.streamCodec(Registries.DIMENSION), Relay::dimension,
            BlockPos.STREAM_CODEC, Relay::pos,
            ByteBufCodecs.VAR_INT.map(i -> i - 1, i -> i + 1), Relay::channel,
            Relay::new);

    public Relay withName(String newName) {
        return new Relay(newName, tags, dimension, pos, channel);
    }

    public Relay withTags(List<String> newTags) {
        return new Relay(name, newTags, dimension, pos, channel);
    }

    /** Heeft dit relais deze tag zelf (niet via een tag erboven)? Hoofdletters maken niet uit. */
    public boolean hasTag(String tag) {
        for (String own : tags) {
            if (own.equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    /** Zonder deze tag (bij het weggooien van een tag). */
    public Relay withoutTag(String tag) {
        List<String> left = new ArrayList<>(tags);
        left.removeIf(own -> own.equalsIgnoreCase(tag));
        return withTags(left);
    }

    /** De tags als één regel tekst: "security room, doors" (zo typ je ze ook in het scherm). */
    public String tagText() {
        return String.join(", ", tags);
    }
}
