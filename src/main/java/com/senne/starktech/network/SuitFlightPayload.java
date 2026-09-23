package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * De stuwraketten van het harnas gaan aan of uit. Dit bericht gaat twee kanten op:
 * - van jouw spel naar de server: "ik zet ze aan" (dubbel op spatie) of "ik ben geland"
 * - van de server naar jouw spel: "ze gaan uit", bijvoorbeeld omdat je energie op is
 *
 * @param engaged staan de stuwraketten aan?
 */
public record SuitFlightPayload(boolean engaged) implements CustomPacketPayload {
    public static final Type<SuitFlightPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "suit_flight"));

    public static final StreamCodec<ByteBuf, SuitFlightPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(SuitFlightPayload::new, SuitFlightPayload::engaged);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
