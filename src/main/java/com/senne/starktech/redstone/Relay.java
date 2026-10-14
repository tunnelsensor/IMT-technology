package com.senne.starktech.redstone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
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
 * Je geeft het een naam ("voordeur", "lichten keuken") en zet het eventueel in een groep ("security
 * room"). Zo kun je er één aanzetten, of een hele groep tegelijk. Een groep kan zelf weer in een andere
 * groep zitten ("security room" zit in "restricted", die zit in "base"): dat onthoudt EdithRelays, niet
 * het relais.
 *
 * @param name      de naam die jij eraan gaf
 * @param group     de groep waarin het zit ("" = geen groep)
 * @param dimension in welke wereld het staat
 * @param pos       waar het staat
 * @param channel   welke schakelaar van het blok (een kant van een stroomkast), of -1 = het
 *                  hele blok (een relais, torentje, koepel)
 */
public record Relay(String name, String group, ResourceKey<Level> dimension, BlockPos pos, int channel) {
    /**
     * Vóór 13 oktober had een relais één groep (opgeslagen als "group"), en "general" (eerder "algemeen")
     * betekende "geen groep". Die worden bij het inladen geen groep.
     */
    private static final List<String> OLD_DEFAULT_GROUPS = List.of("general", "algemeen");

    /** Een relais voor het hele blok (geen apart kanaal). */
    public Relay(String name, String group, ResourceKey<Level> dimension, BlockPos pos) {
        this(name, group, dimension, pos, -1);
    }

    /**
     * Opslaan als "in_group" (altijd, ook als het leeg is). Een relais uit een oudere wereld heeft nog:
     * <ul>
     *   <li>"tags" (13 oktober, een lijst): de eerste tag wordt zijn groep (14 oktober: "tags zijn volledig
     *       weg");</li>
     *   <li>"group" (van vóór 13 oktober): die groep, behalve de oude standaardgroep (die betekende "geen
     *       groep").</li>
     * </ul>
     */
    public static final Codec<Relay> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(Relay::name),
            Codec.STRING.optionalFieldOf("in_group").forGetter(relay -> Optional.of(relay.group())),
            Codec.STRING.listOf().optionalFieldOf("tags").forGetter(relay -> Optional.empty()),
            Codec.STRING.optionalFieldOf("group").forGetter(relay -> Optional.empty()),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Relay::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Relay::pos),
            Codec.INT.optionalFieldOf("channel", -1).forGetter(Relay::channel)
    ).apply(instance, Relay::load));

    private static Relay load(String name, Optional<String> group, Optional<List<String>> oldTags, Optional<String> oldGroup,
                              ResourceKey<Level> dimension, BlockPos pos, int channel) {
        String own = group.orElseGet(() -> oldTags
                .map(tags -> tags.isEmpty() ? "" : tags.getFirst())
                .orElseGet(() -> oldGroup
                        .filter(old -> !OLD_DEFAULT_GROUPS.contains(old.toLowerCase(Locale.ROOT)))
                        .orElse("")));
        return new Relay(name, own.strip(), dimension, pos, channel);
    }

    public static final StreamCodec<ByteBuf, Relay> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Relay::name,
            ByteBufCodecs.STRING_UTF8, Relay::group,
            ResourceKey.streamCodec(Registries.DIMENSION), Relay::dimension,
            BlockPos.STREAM_CODEC, Relay::pos,
            ByteBufCodecs.VAR_INT.map(i -> i - 1, i -> i + 1), Relay::channel,
            Relay::new);

    public Relay withName(String newName) {
        return new Relay(newName, group, dimension, pos, channel);
    }

    /** In een andere groep ("" = geen groep). */
    public Relay withGroup(String newGroup) {
        return new Relay(name, newGroup, dimension, pos, channel);
    }

    /** Zit dit relais zelf in deze groep (niet via een groep eronder)? Hoofdletters maken niet uit. */
    public boolean isIn(String otherGroup) {
        return group.equalsIgnoreCase(otherGroup);
    }

    public boolean hasGroup() {
        return !group.isEmpty();
    }
}
