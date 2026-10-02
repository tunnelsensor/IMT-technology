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
 * Server naar speler: de hele toestand van een Stark OS-desktop, als één CompoundTag. De server
 * stuurt dit alleen naar wie echt ingelogd is, en ververst het terwijl het scherm openstaat. In de
 * tag zit de status (naam, IP, poort, wachtwoord ja/nee), het logboek, de apparaten, de toegang,
 * de mappen en de camera's. Zo hoef ik voor elk nieuw veld geen aparte codec te schrijven, en het
 * echte wachtwoord gaat er nooit in (alleen "heeft een wachtwoord": ja/nee).
 */
public record OsSnapshotPayload(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
    public static final Type<OsSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "os_snapshot"));

    public static final StreamCodec<ByteBuf, OsSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OsSnapshotPayload::pos,
            ByteBufCodecs.COMPOUND_TAG, OsSnapshotPayload::data,
            OsSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
