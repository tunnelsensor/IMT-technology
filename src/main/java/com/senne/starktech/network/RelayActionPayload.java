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
 * @param op      wat er moet gebeuren: naam geven, weghalen of schakelen
 * @param relayOp bij SWITCH: aan, uit, omschakelen of een korte puls
 * @param index   welk relais (-1 = zoek op naam of groep, zie name)
 * @param name    de nieuwe naam (bij RENAME) of de naam/groep om te zoeken (bij index -1)
 * @param group   de nieuwe groep (bij RENAME)
 */
public record RelayActionPayload(Op op, EdithRedstone.Op relayOp, int index, String name, String group)
        implements CustomPacketPayload {
    public static final Type<RelayActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "relay_action"));

    public enum Op {
        /** Naam en groep instellen. */
        RENAME,
        /** Uit de lijst halen (het blok blijft staan). */
        REMOVE,
        /** Aan/uit/omschakelen/puls. */
        SWITCH
    }

    /** Snelle manier om een schakel-bericht te maken voor één relais. */
    public static RelayActionPayload switchOne(int index, EdithRedstone.Op relayOp) {
        return new RelayActionPayload(Op.SWITCH, relayOp, index, "", "");
    }

    /** Snelle manier om te schakelen op naam of groep (bv. vanuit een spraakcommando). */
    public static RelayActionPayload switchNamed(String nameOrGroup, EdithRedstone.Op relayOp) {
        return new RelayActionPayload(Op.SWITCH, relayOp, -1, nameOrGroup, "");
    }

    public static final StreamCodec<ByteBuf, RelayActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal),
            RelayActionPayload::op,
            ByteBufCodecs.VAR_INT.map(i -> EdithRedstone.Op.values()[Math.floorMod(i, EdithRedstone.Op.values().length)],
                    EdithRedstone.Op::ordinal), RelayActionPayload::relayOp,
            ByteBufCodecs.VAR_INT.map(i -> i - 1, i -> i + 1), RelayActionPayload::index,
            ByteBufCodecs.stringUtf8(64), RelayActionPayload::name,
            ByteBufCodecs.stringUtf8(64), RelayActionPayload::group,
            RelayActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
