package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar de spelers in de buurt: "deze speler vliegt met zijn harnas, zo hard".
 * Hun spel tekent dan de straalvlammen onder zijn laarzen (zie SuitEffects). De server stuurt het
 * om de paar ticks zolang hij vliegt; komt er even niets, dan gaan de vlammen uit.
 *
 * @param entityId de vliegende speler
 * @param power    hoe hard de stuwraketten werken, 0 (stilhangen) tot 1 (naverbrander)
 */
public record ThrusterPayload(int entityId, float power) implements CustomPacketPayload {
    public static final Type<ThrusterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "thruster"));

    public static final StreamCodec<ByteBuf, ThrusterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ThrusterPayload::entityId,
            ByteBufCodecs.FLOAT, ThrusterPayload::power,
            ThrusterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
