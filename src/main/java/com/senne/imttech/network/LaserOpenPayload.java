package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "open het instelscherm van deze laserzender" (13 oktober).
 *
 * De server beslist of je het mag openen (jij zette hem neer, of je bent vertrouwd in de zone; zie
 * shield/LaserEmitters), en jouw spel opent het dan pas. De kleur, het omkeren en liggend/staand leest het
 * scherm gewoon uit het blok (dat kent jouw spel al). Een laserbrug (hoofdstuk 34) opent zo ook zijn scherm (zie
 * shield/LaserBridges).
 *
 * @param pos   welke zender
 * @param owner de naam van wie hem neerzette (leeg als niemand: een oude zender of een uit een gebouw)
 * @param name  (hoofdstuk 39) de naam van de poort in jouw I.M.T-lijst, of leeg als hij er niet in staat
 */
public record LaserOpenPayload(BlockPos pos, String owner, String name) implements CustomPacketPayload {
    public static final Type<LaserOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "laser_open"));

    public static final StreamCodec<ByteBuf, LaserOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LaserOpenPayload::pos,
            ByteBufCodecs.stringUtf8(64), LaserOpenPayload::owner,
            ByteBufCodecs.stringUtf8(64), LaserOpenPayload::name,
            LaserOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
