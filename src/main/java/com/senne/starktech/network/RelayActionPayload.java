package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import com.senne.starktech.redstone.EdithRedstone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "doe iets met mijn redstone-relais".
 *
 * @param op      wat er moet gebeuren: naam geven, weghalen, schakelen, omkeren, of iets met groepen
 * @param relayOp bij SWITCH: aan, uit, omschakelen of een korte puls
 * @param index   welk relais (-1 = zoek op naam of groep, -2 = precies deze groep; zie name)
 * @param name    de nieuwe naam (bij RENAME), de naam/groep om te zoeken (bij index -1), of de groep
 *                waar het om gaat (bij de GROUP_-dingen)
 * @param text    de groep van het relais (bij RENAME, "" = geen groep), de groep erboven (bij GROUP_CREATE
 *                en GROUP_PARENT, "" = bovenaan), of de nieuwe naam van de groep (bij GROUP_RENAME)
 */
public record RelayActionPayload(Op op, EdithRedstone.Op relayOp, int index, String name, String text)
        implements CustomPacketPayload {
    public static final Type<RelayActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "relay_action"));

    public enum Op {
        /** Naam en groep instellen. */
        RENAME,
        /** Uit de lijst halen (het blok blijft staan). */
        REMOVE,
        /** Aan/uit/omschakelen/puls. */
        SWITCH,
        /** Het relais omkeren: redstone als het UIT staat (of weer gewoon). */
        INVERT,
        /** Een nieuwe, lege groep name aanmaken, in groep text ("" = bovenaan). */
        GROUP_CREATE,
        /** Groep name zit voortaan in groep text ("" = bovenaan, in geen groep). */
        GROUP_PARENT,
        /** Groep name heet voortaan text. */
        GROUP_RENAME,
        /** Groep name weggooien (wat erin zat, schuift een laag omhoog). */
        GROUP_DELETE
    }

    /** Snelle manier om een schakel-bericht te maken voor één relais. */
    public static RelayActionPayload switchOne(int index, EdithRedstone.Op relayOp) {
        return new RelayActionPayload(Op.SWITCH, relayOp, index, "", "");
    }

    /** Snelle manier om te schakelen op naam of groep (bv. vanuit een spraakcommando). */
    public static RelayActionPayload switchNamed(String nameOrGroup, EdithRedstone.Op relayOp) {
        return new RelayActionPayload(Op.SWITCH, relayOp, -1, nameOrGroup, "");
    }

    /** Alles in deze groep schakelen (ook als er een relais is dat zo heet). Voor de knoppen bij een groep. */
    public static RelayActionPayload switchGroup(String group, EdithRedstone.Op relayOp) {
        return new RelayActionPayload(Op.SWITCH, relayOp, GROUP_ONLY, group, "");
    }

    /** Index die zegt: name is een groep, geen naam. */
    public static final int GROUP_ONLY = -2;

    /** Naam en groep van één relais bewaren ({@code group} "" = geen groep). */
    public static RelayActionPayload rename(int index, String name, String group) {
        return new RelayActionPayload(Op.RENAME, EdithRedstone.Op.TOGGLE, index, name, group);
    }

    /**
     * Iets met een groep: aanmaken (text = de groep erboven), in een andere groep zetten (text = die groep,
     * "" = bovenaan), een andere naam geven (text = de nieuwe naam), weggooien.
     */
    public static RelayActionPayload group(Op op, String group, String text) {
        return new RelayActionPayload(op, EdithRedstone.Op.TOGGLE, -1, group, text);
    }

    public static final StreamCodec<ByteBuf, RelayActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal),
            RelayActionPayload::op,
            ByteBufCodecs.VAR_INT.map(i -> EdithRedstone.Op.values()[Math.floorMod(i, EdithRedstone.Op.values().length)],
                    EdithRedstone.Op::ordinal), RelayActionPayload::relayOp,
            ByteBufCodecs.VAR_INT.map(i -> i - 1, i -> i + 1), RelayActionPayload::index,
            ByteBufCodecs.stringUtf8(64), RelayActionPayload::name,
            ByteBufCodecs.stringUtf8(1024), RelayActionPayload::text,
            RelayActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
