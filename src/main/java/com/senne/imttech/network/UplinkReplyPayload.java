package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server naar speler (hoofdstuk 122): het antwoord op een UplinkPayload. {@code done} = het lukte (verbonden of
 * losgekoppeld): het scherm Datacenter gaat dicht, en I.M.T zegt het zelf. Anders staat in {@code message} waarom niet
 * (fout wachtwoord, geen toegang...), in het rood in het scherm.
 */
public record UplinkReplyPayload(boolean done, String message) implements CustomPacketPayload {
    public static final Type<UplinkReplyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "glasses_uplink_reply"));

    public static final StreamCodec<ByteBuf, UplinkReplyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, UplinkReplyPayload::done,
            ByteBufCodecs.stringUtf8(256), UplinkReplyPayload::message,
            UplinkReplyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
