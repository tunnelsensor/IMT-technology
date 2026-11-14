package com.senne.imttech.car;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Wat er in een RC Car-item zit (hoofdstuk 108, jouw wens: "je kunt deze rc car ook oppakken en in je inventory
 * zetten"): alles gaat mee, zodat je hem ergens anders neerzet en hij precies dezelfde auto is.
 *
 * @param name   zijn naam ("" = geen: dan heet hij "RC Car")
 * @param source de code van zijn programma (een nieuwe auto heeft er geen: die schrijf je zelf, zie CarProgram)
 * @param link   zijn vaste nummer, waaraan een RC Controller hem herkent (leeg bij een nieuwe auto: hij krijgt er een
 *               als je hem neerzet)
 * @param cell   hoe vol zijn energiecel is, van 0 tot 100; {@link #NO_CELL} = hij heeft er geen
 * @param trunk  wat er in zijn kofferbak ligt
 * @param drill  zijn boorkop (een nieuwe auto heeft de ijzeren, zie DrillHead)
 */
public record RcCarData(String name, String source, Optional<UUID> link, float cell, ItemContainerContents trunk, DrillHead drill) {
    /** Geen energiecel. */
    public static final float NO_CELL = -1.0f;

    /** Een nieuwe auto: geen naam, geen programma, geen cel, een lege kofferbak, de ijzeren boorkop. */
    public static final RcCarData DEFAULT =
            new RcCarData("", CarProgram.EMPTY, Optional.empty(), NO_CELL, ItemContainerContents.EMPTY, DrillHead.IRON);

    /** Heeft hij een energiecel? */
    public boolean hasCell() {
        return cell >= 0;
    }

    /** Hoeveel voorwerpen er in zijn kofferbak liggen. */
    public int items() {
        return trunk.stream().mapToInt(stack -> stack.getCount()).sum();
    }

    public static final Codec<RcCarData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(RcCarData::name),
            Codec.STRING.optionalFieldOf("source", CarProgram.EMPTY).forGetter(RcCarData::source),
            UUIDUtil.CODEC.optionalFieldOf("link").forGetter(RcCarData::link),
            Codec.FLOAT.optionalFieldOf("cell", NO_CELL).forGetter(RcCarData::cell),
            ItemContainerContents.CODEC.optionalFieldOf("trunk", ItemContainerContents.EMPTY).forGetter(RcCarData::trunk),
            // "lenient": een kop die we niet (meer) kennen is de ijzeren kop, en de rest van de auto blijft heel.
            DrillHead.CODEC.lenientOptionalFieldOf("drill", DrillHead.IRON).forGetter(RcCarData::drill)
    ).apply(instance, RcCarData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RcCarData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RcCarData::name,
            ByteBufCodecs.STRING_UTF8, RcCarData::source,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional), RcCarData::link,
            ByteBufCodecs.FLOAT, RcCarData::cell,
            ItemContainerContents.STREAM_CODEC, RcCarData::trunk,
            DrillHead.STREAM_CODEC, RcCarData::drill,
            RcCarData::new);
}
