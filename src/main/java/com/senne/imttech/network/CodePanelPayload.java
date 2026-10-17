package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Het codepaneel, beide kanten op:
 * <ul>
 *   <li>server naar speler: open de toetsen van het paneel op {@code pos}; {@code text} is de naam
 *       van het paneel (die staat bovenaan);</li>
 *   <li>speler naar server: deze code is ingetypt op het paneel op {@code pos}; {@code text} is de
 *       code (alleen cijfers; de server controleert dat).</li>
 * </ul>
 */
public record CodePanelPayload(BlockPos pos, String text) implements CustomPacketPayload {
    public static final Type<CodePanelPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "code_panel"));

    public static final StreamCodec<ByteBuf, CodePanelPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CodePanelPayload::pos,
            ByteBufCodecs.stringUtf8(64), CodePanelPayload::text,
            CodePanelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
