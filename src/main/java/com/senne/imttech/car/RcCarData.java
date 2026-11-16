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
 * @param name     zijn naam ("" = geen: dan heet hij "RC Car")
 * @param source   de code van zijn programma (een nieuwe auto heeft er geen; sinds hoofdstuk 119 een kopie van een RC
 *                 program van je server)
 * @param link     zijn vaste nummer, waaraan een RC Controller hem herkent (leeg bij een nieuwe auto: hij krijgt er een
 *                 als je hem neerzet)
 * @param cell     hoe vol zijn energiecel is, van 0 tot 100; {@link #NO_CELL} = hij heeft er geen
 * @param trunk    wat er in zijn kofferbak ligt
 * @param drill    zijn boorkop (een nieuwe auto heeft de ijzeren, zie DrillHead)
 * @param robotArm heeft hij een RC Robot Arm (hoofdstuk 118)? Een nieuwe auto niet.
 * @param program  de naam van het RC program van de server waar zijn code een kopie van is (hoofdstuk 119); "" = geen
 * @param programServer van welke server dat RC program kwam (het IP van die Security Core); "" = onbekend
 *                 (een nieuwe auto, of een auto met code die je vóór hoofdstuk 119 op de auto zelf schreef)
 * @param programRef hoofdstuk 130: is dat RC program op slot (van een SD-kaart), dan staat hier de vingerafdruk van zijn
 *                 code op de server (zie logic/LockedPrograms) en is {@code source} leeg. Zo staat de code nooit op het
 *                 item: niet in je inventaris, niet op een holotafel, niet in een kist. "" = een open programma.
 */
public record RcCarData(String name, String source, Optional<UUID> link, float cell, ItemContainerContents trunk, DrillHead drill,
                        boolean robotArm, String program, String programServer, String programRef) {
    /** Een auto met een open programma (zoals vóór hoofdstuk 130). */
    public RcCarData(String name, String source, Optional<UUID> link, float cell, ItemContainerContents trunk, DrillHead drill,
                     boolean robotArm, String program, String programServer) {
        this(name, source, link, cell, trunk, drill, robotArm, program, programServer, "");
    }

    /** Is zijn RC program op slot (hoofdstuk 130)? */
    public boolean programLocked() {
        return !programRef.isEmpty();
    }

    /** Geen energiecel. */
    public static final float NO_CELL = -1.0f;

    /** Een nieuwe auto: geen naam, geen programma, geen cel, een lege kofferbak, de ijzeren boorkop, geen arm. */
    public static final RcCarData DEFAULT =
            new RcCarData("", CarProgram.EMPTY, Optional.empty(), NO_CELL, ItemContainerContents.EMPTY, DrillHead.IRON, false, "", "",
                    "");

    /** Heeft hij een energiecel? */
    public boolean hasCell() {
        return cell >= 0;
    }

    /** Hoeveel voorwerpen er in zijn kofferbak liggen. */
    public int items() {
        return trunk.stream().mapToInt(stack -> stack.getCount()).sum();
    }

    /** Dezelfde auto zonder vast nummer (het item dat in creative in je hand blijft, zie RcCarItem). */
    public RcCarData withoutLink() {
        return new RcCarData(name, source, Optional.empty(), cell, trunk, drill, robotArm, program, programServer, programRef);
    }

    public static final Codec<RcCarData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(RcCarData::name),
            Codec.STRING.optionalFieldOf("source", CarProgram.EMPTY).forGetter(RcCarData::source),
            UUIDUtil.CODEC.optionalFieldOf("link").forGetter(RcCarData::link),
            Codec.FLOAT.optionalFieldOf("cell", NO_CELL).forGetter(RcCarData::cell),
            ItemContainerContents.CODEC.optionalFieldOf("trunk", ItemContainerContents.EMPTY).forGetter(RcCarData::trunk),
            // "lenient": een kop die we niet (meer) kennen is de ijzeren kop, en de rest van de auto blijft heel.
            DrillHead.CODEC.lenientOptionalFieldOf("drill", DrillHead.IRON).forGetter(RcCarData::drill),
            // Hoofdstuk 118: een auto die je oppakte vóór er een losse arm was, had een arm. Die houdt hij (jouw regel van
            // hoofdstuk 106: wat er al is, blijft). Daarom is "geen veld" hier waar; een auto zonder arm bewaart false.
            Codec.BOOL.optionalFieldOf("robot_arm", true).forGetter(RcCarData::robotArm),
            Codec.STRING.optionalFieldOf("program", "").forGetter(RcCarData::program),
            Codec.STRING.optionalFieldOf("program_server", "").forGetter(RcCarData::programServer),
            Codec.STRING.optionalFieldOf("program_ref", "").forGetter(RcCarData::programRef)
    ).apply(instance, RcCarData::new));

    /**
     * Over het netwerk (in je spel: de tooltip). Met de hand geschreven, want StreamCodec.composite kent er hooguit zes.
     * Hoofdstuk 130: van een programma op slot staat de code niet op het item (alleen zijn naam en de vingerafdruk), dus
     * hij gaat ook nooit over het netwerk.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, RcCarData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, data.name());
                ByteBufCodecs.STRING_UTF8.encode(buf, data.programLocked() ? "" : data.source());  // ook als iemand er toch code in zette
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional).encode(buf, data.link());
                buf.writeFloat(data.cell());
                ItemContainerContents.STREAM_CODEC.encode(buf, data.trunk());
                DrillHead.STREAM_CODEC.encode(buf, data.drill());
                buf.writeBoolean(data.robotArm());
                ByteBufCodecs.STRING_UTF8.encode(buf, data.program());
                ByteBufCodecs.STRING_UTF8.encode(buf, data.programServer());
                ByteBufCodecs.STRING_UTF8.encode(buf, data.programRef());
            },
            buf -> new RcCarData(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional).decode(buf), buf.readFloat(),
                    ItemContainerContents.STREAM_CODEC.decode(buf), DrillHead.STREAM_CODEC.decode(buf), buf.readBoolean(),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf)));
}
