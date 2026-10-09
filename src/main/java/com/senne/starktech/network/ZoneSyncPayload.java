package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import com.senne.starktech.security.SecurityBalance;
import com.senne.starktech.security.zone.ZoneBox;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: de zones die jij mag zien (hoofdstuk 10). Dat zijn alleen de zones
 * waar je mag bouwen (jouw eigen zones en die van wie jou vertrouwt of in je team zit): anderen zien
 * de rand niet (jouw keuze, 8 oktober). Een lege lijst = geen zones (meer) in de buurt.
 */
public record ZoneSyncPayload(List<View> zones) implements CustomPacketPayload {
    public static final Type<ZoneSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "zone_sync"));

    /** Hooguit zoveel zones in één bericht (ruim: twee kernen staan minstens 2 x ZONE_REACH uit elkaar). */
    public static final int MAX_ZONES = 64;

    /**
     * Eén zone zoals jouw spel hem tekent: waar de kern staat, of de zone nu beschermt (zonder stroom
     * niet: dan tekent je spel hem rood), en zijn kubussen.
     */
    public record View(BlockPos core, boolean active, List<ZoneBox> boxes) {
        public static final StreamCodec<ByteBuf, View> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, View::core,
                ByteBufCodecs.BOOL, View::active,
                ZoneBox.STREAM_CODEC.apply(ByteBufCodecs.list(SecurityBalance.ZONE_MAX_BOXES)), View::boxes,
                View::new);
    }

    public static final StreamCodec<ByteBuf, ZoneSyncPayload> STREAM_CODEC = View.STREAM_CODEC
            .apply(ByteBufCodecs.list(MAX_ZONES))
            .map(ZoneSyncPayload::new, ZoneSyncPayload::zones);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
