package com.senne.starktech.redstone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Eén plek die E.D.I.T.H. kan aansturen: een E.D.I.T.H.-relais dat je ergens hebt neergezet.
 *
 * Je geeft het een naam ("voordeur", "lichten keuken") en een groep ("huis", "val"). Zo kun je
 * er één aanzetten, of een hele groep tegelijk.
 *
 * @param name      de naam die jij eraan gaf
 * @param group     de groep waarin het zit (lege naam = groep "general")
 * @param dimension in welke wereld het staat
 * @param pos       waar het staat
 * @param channel   welke schakelaar van het blok (een kant van een stroomkast), of -1 = het
 *                  hele blok (een relais, torentje, koepel)
 */
public record Relay(String name, String group, ResourceKey<Level> dimension, BlockPos pos, int channel) {
    /** De groep als je zelf niets invult. (Engels: je ziet hem in het spel.) */
    public static final String DEFAULT_GROUP = "general";
    /** Zo heette de standaardgroep vroeger; relais uit oude werelden komen zo in "general". */
    private static final String OLD_DEFAULT_GROUP = "algemeen";

    public Relay {
        if (OLD_DEFAULT_GROUP.equals(group)) {
            group = DEFAULT_GROUP;
        }
    }

    /** Een relais voor het hele blok (geen apart kanaal). */
    public Relay(String name, String group, ResourceKey<Level> dimension, BlockPos pos) {
        this(name, group, dimension, pos, -1);
    }

    public static final Codec<Relay> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(Relay::name),
            Codec.STRING.optionalFieldOf("group", DEFAULT_GROUP).forGetter(Relay::group),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Relay::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Relay::pos),
            Codec.INT.optionalFieldOf("channel", -1).forGetter(Relay::channel)
    ).apply(instance, Relay::new));

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

    public Relay withGroup(String newGroup) {
        return new Relay(name, newGroup, dimension, pos, channel);
    }
}
