package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 38): er loopt iets over de kabels, om naar te kijken (zie lan/CableTraffic en
 * client/CableTraffic).
 * <ul>
 *   <li>{@code power = false}: een signaal, het heldere vakje dat de kabel volgt van het begin van {@code path} tot het
 *       eind (een lezer naar zijn kern, of de kern naar een apparaat);</li>
 *   <li>{@code power = true}: stroom, de gele streepjes die {@code ticks} lang van de leverancier (het begin) naar het
 *       apparaat (het eind) lopen.</li>
 * </ul>
 * {@code path} zijn de plekken langs de weg, elke plek naast de vorige.
 */
public record CableTrafficPayload(boolean power, List<BlockPos> path, int ticks) implements CustomPacketPayload {
    /**
     * Zo veel plekken mag een weg in één bericht hooguit hebben. Een langere weg (hoofdstuk 124: een lange kabel) knipt
     * lan/CableTraffic in stukken per speler; dit getal blijft zo, zodat een speler met een oudere versie het bericht nog
     * kan lezen.
     */
    public static final int MAX_PATH = 600;

    public static final Type<CableTrafficPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "cable_traffic"));

    public static final StreamCodec<ByteBuf, CableTrafficPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CableTrafficPayload::power,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PATH)), CableTrafficPayload::path,
            ByteBufCodecs.VAR_INT, CableTrafficPayload::ticks,
            CableTrafficPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
