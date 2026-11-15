package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Speler naar server (hoofdstuk 122): "verbind mijn I.M.T-bril met het datacenter van de kern met dit IP, met dit
 * wachtwoord" (het scherm Datacenter in het G-menu). Een leeg IP betekent: loskoppelen. De server kijkt het na zoals bij
 * de laptop, en of je op die kern de eigenaar bent of vertrouwd (zie logic/GlassesUplink); hij antwoordt met een
 * UplinkReplyPayload.
 */
public record UplinkPayload(String ip, String password) implements CustomPacketPayload {
    public static final Type<UplinkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "glasses_uplink"));

    public static final StreamCodec<ByteBuf, UplinkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), UplinkPayload::ip,
            ByteBufCodecs.stringUtf8(32), UplinkPayload::password,
            UplinkPayload::new);

    /** Loskoppelen: de bril hangt dan aan geen enkele kern meer. */
    public static UplinkPayload disconnect() {
        return new UplinkPayload("", "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
