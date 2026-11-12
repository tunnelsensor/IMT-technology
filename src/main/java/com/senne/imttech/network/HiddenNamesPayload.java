package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 99): van welke spelers jij de naam niet ziet, omdat ze in een beschermde zone
 * staan waar jij niet mag bouwen (zie security/zone/ZoneNames). De nummers zijn hun entity-id's. Een lege lijst = je ziet
 * iedereen weer.
 */
public record HiddenNamesPayload(List<Integer> players) implements CustomPacketPayload {
    public static final Type<HiddenNamesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "hidden_names"));

    /** Hooguit zoveel spelers in één bericht (ruim genoeg voor een server). */
    public static final int MAX = 512;

    public static final StreamCodec<ByteBuf, HiddenNamesPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT
            .apply(ByteBufCodecs.list(MAX))
            .map(HiddenNamesPayload::new, HiddenNamesPayload::players);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
