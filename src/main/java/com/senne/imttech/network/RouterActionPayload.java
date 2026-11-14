package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server (hoofdstuk 112): een knop in het scherm van een Router.
 *
 * @param pos    waar de router staat
 * @param op     wat: {@link #REFRESH}, {@link #LINK}, {@link #UNLINK} of {@link #PING}
 * @param target het apparaat waar het over gaat (bij REFRESH telt het niet: dan de plek van de router zelf)
 */
public record RouterActionPayload(BlockPos pos, int op, BlockPos target) implements CustomPacketPayload {
    /** Stuur me opnieuw wat het scherm toont (het scherm vraagt dit om de seconde). */
    public static final int REFRESH = 0;
    /** Verbind de router met dit apparaat. */
    public static final int LINK = 1;
    /** Maak dit apparaat los van de router. */
    public static final int UNLINK = 2;
    /** Laat vonkjes van dit apparaat naar de router lopen, zodat je ziet welk het is. */
    public static final int PING = 3;

    public static final Type<RouterActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "router_action"));

    public static final StreamCodec<ByteBuf, RouterActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RouterActionPayload::pos,
            ByteBufCodecs.VAR_INT, RouterActionPayload::op,
            BlockPos.STREAM_CODEC, RouterActionPayload::target,
            RouterActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
