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
 * @param op      wat er moet gebeuren: naam geven, weghalen, schakelen, omkeren, of iets met tags
 * @param relayOp bij SWITCH: aan, uit, omschakelen of een korte puls
 * @param index   welk relais (-1 = zoek op naam of tag, -2 = precies deze tag; zie name)
 * @param name    de nieuwe naam (bij RENAME), de naam/tag om te zoeken (bij index -1), of de tag
 *                waar het om gaat (bij de TAG_-dingen)
 * @param text    de nieuwe tags, met komma's ertussen (bij RENAME), of de tag erboven (bij TAG_PARENT
 *                en TAG_UNPARENT)
 */
public record RelayActionPayload(Op op, EdithRedstone.Op relayOp, int index, String name, String text)
        implements CustomPacketPayload {
    public static final Type<RelayActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "relay_action"));

    public enum Op {
        /** Naam en tags instellen. */
        RENAME,
        /** Uit de lijst halen (het blok blijft staan). */
        REMOVE,
        /** Aan/uit/omschakelen/puls. */
        SWITCH,
        /** Het relais omkeren: redstone als het UIT staat (of weer gewoon). */
        INVERT,
        /** Een nieuwe, lege tag aanmaken (name). */
        TAG_CREATE,
        /** Tag name zit voortaan in tag text. */
        TAG_PARENT,
        /** Tag name zit niet meer in tag text. */
        TAG_UNPARENT,
        /** Tag name helemaal weggooien. */
        TAG_DELETE
    }

    /** Snelle manier om een schakel-bericht te maken voor één relais. */
    public static RelayActionPayload switchOne(int index, EdithRedstone.Op relayOp) {
        return new RelayActionPayload(Op.SWITCH, relayOp, index, "", "");
    }

    /** Snelle manier om te schakelen op naam of tag (bv. vanuit een spraakcommando). */
    public static RelayActionPayload switchNamed(String nameOrTag, EdithRedstone.Op relayOp) {
        return new RelayActionPayload(Op.SWITCH, relayOp, -1, nameOrTag, "");
    }

    /** Alles in deze tag schakelen (ook als er een relais is dat zo heet). Voor de knoppen bij een tag. */
    public static RelayActionPayload switchTag(String tag, EdithRedstone.Op relayOp) {
        return new RelayActionPayload(Op.SWITCH, relayOp, TAG_ONLY, tag, "");
    }

    /** Index die zegt: name is een tag, geen naam. */
    public static final int TAG_ONLY = -2;

    /** Iets met een tag: aanmaken, in een andere tag zetten (of eruit halen), weggooien. */
    public static RelayActionPayload tag(Op op, String tag, String parent) {
        return new RelayActionPayload(op, EdithRedstone.Op.TOGGLE, -1, tag, parent);
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
