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
 * Speler naar server: "doe iets op de Stark OS-desktop van de kern op {@code pos}". {@code op} is
 * de opdracht ({@code passwd}, {@code trust}, {@code toggle}, {@code mkdir}, {@code close}...),
 * {@code args} de gegevens die erbij horen (bijvoorbeeld het nieuwe wachtwoord of welk apparaat).
 * De server controleert altijd zelf of je ingelogd bent en of het mag.
 */
public record OsActionPayload(BlockPos pos, String op, CompoundTag args) implements CustomPacketPayload {
    /** Langer dan dit is een opdrachtnaam nooit. */
    public static final int MAX_OP = 32;

    public static final Type<OsActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "os_action"));

    public static final StreamCodec<ByteBuf, OsActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OsActionPayload::pos,
            ByteBufCodecs.stringUtf8(MAX_OP), OsActionPayload::op,
            ByteBufCodecs.COMPOUND_TAG, OsActionPayload::args,
            OsActionPayload::new);

    public static OsActionPayload of(BlockPos pos, String op, CompoundTag args) {
        return new OsActionPayload(pos, op, args);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
