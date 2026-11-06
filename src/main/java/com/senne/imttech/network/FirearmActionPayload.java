package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Een handeling met een pistool of magazijn (hoofdstuk 52), twee kanten op. Van jouw spel naar de server: "ik wil dit
 * doen" ({@code kind} = Firearms.ActionKind.ordinal(), {@code arg} = bijvoorbeeld welk magazijn uit de pouch;
 * {@code ticks} doet er dan niet toe). Van de server naar jouw spel: "het begint en duurt zoveel ticks" (dan loopt de
 * animatie even lang), of {@code ticks} = 0: "dat gaat niet" of "het is afgebroken" (de animatie stopt).
 */
public record FirearmActionPayload(int kind, int arg, int ticks) implements CustomPacketPayload {
    public static final Type<FirearmActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "firearm_action"));

    public static final StreamCodec<ByteBuf, FirearmActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FirearmActionPayload::kind,
            ByteBufCodecs.VAR_INT, FirearmActionPayload::arg,
            ByteBufCodecs.VAR_INT, FirearmActionPayload::ticks,
            FirearmActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
