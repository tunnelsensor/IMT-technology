package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "open het scherm om dit relais een naam en groep te geven".
 *
 * @param index   welk relais uit je eigen lijst (zie ImtRelays)
 * @param network hoofdstuk 112: {@link #NO_NEED} (het heeft geen netwerk nodig), {@link #ONLINE} (het hangt aan een
 *                netwerk) of {@link #OFFLINE} (het heeft er een nodig en heeft er geen)
 */
public record RelayEditPayload(int index, int network) implements CustomPacketPayload {
    /** Het scherm weet het niet (geopend vanuit je lijst, niet bij het relais zelf): dan toont het niets. */
    public static final int UNKNOWN = 0;
    public static final int NO_NEED = 1;
    public static final int ONLINE = 2;
    public static final int OFFLINE = 3;

    public static final Type<RelayEditPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "relay_edit"));

    public static final StreamCodec<ByteBuf, RelayEditPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RelayEditPayload::index,
            ByteBufCodecs.VAR_INT, RelayEditPayload::network,
            RelayEditPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
