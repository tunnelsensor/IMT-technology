package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Een hack met de Bypass Kit (hoofdstuk 12), beide kanten op. {@code pos} is de lezer of het paneel,
 * {@code op} wat er gebeurt, {@code args} de gegevens die erbij horen.
 * <ul>
 *   <li>server naar speler: {@code reader} (open het stroomcircuit: naam, het getal voor het raster),
 *       {@code result} (hoe het afliep), {@code close} (het scherm moet dicht: je liep weg);</li>
 *   <li>speler naar server: {@code level} (dit niveau gekozen, de klok loopt), {@code solve} (zo staan
 *       de stukjes: kijk na), {@code fail} (ALARM of de tijd is om), {@code cancel} (Esc).</li>
 * </ul>
 * De server controleert altijd zelf of het klopt.
 */
public record BypassPayload(BlockPos pos, String op, CompoundTag args) implements CustomPacketPayload {
    /** Langer dan dit is een opdrachtnaam nooit. */
    public static final int MAX_OP = 16;

    public static final Type<BypassPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "bypass"));

    public static final StreamCodec<ByteBuf, BypassPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BypassPayload::pos,
            ByteBufCodecs.stringUtf8(MAX_OP), BypassPayload::op,
            ByteBufCodecs.COMPOUND_TAG, BypassPayload::args,
            BypassPayload::new);

    /** Een bericht zonder gegevens. */
    public static BypassPayload of(BlockPos pos, String op) {
        return new BypassPayload(pos, op, new CompoundTag());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
