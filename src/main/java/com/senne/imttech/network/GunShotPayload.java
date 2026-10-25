package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Bericht van de server naar spelers in de buurt: "deze speler schoot met een lasergeweer" (hoofdstuk 35). {@code ends}
 * zijn de plekken waar de stralen eindigen (de shotgun heeft er meer), {@code hits} welke daarvan een wezen raakten (bit
 * 0 = de eerste). Elk spel tekent de laserbouten zelf vanaf de loop van de schutter (zie client/weapons/GunEffects).
 */
public record GunShotPayload(int shooter, int gun, List<Vec3> ends, int hits) implements CustomPacketPayload {
    public static final Type<GunShotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "gun_shot"));

    public static final StreamCodec<ByteBuf, GunShotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GunShotPayload::shooter,
            ByteBufCodecs.VAR_INT, GunShotPayload::gun,
            ModCodecs.VEC3.apply(ByteBufCodecs.list(64)), GunShotPayload::ends,
            ByteBufCodecs.VAR_INT, GunShotPayload::hits,
            GunShotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
