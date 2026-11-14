package com.senne.imttech.armor;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van jouw spel naar de server (hoofdstuk 109): de tekst op je rug en zijn kleur (een kleurstof, 0 = wit), voor het
 * vest dat je in het scherm open hebt. De server knipt hem zelf op maat (VestData.clean).
 */
public record VestTextPayload(String text, int colour) implements CustomPacketPayload {
    public static final Type<VestTextPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "vest_text"));

    public static final StreamCodec<ByteBuf, VestTextPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), VestTextPayload::text,
            ByteBufCodecs.VAR_INT, VestTextPayload::colour,
            VestTextPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
