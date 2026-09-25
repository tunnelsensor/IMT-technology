package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Bericht van server naar spelers in de buurt: "teken hier een straal".
 * De spelers tekenen de straal zelf met deeltjes; zo hoeft de server maar één klein
 * bericht te sturen in plaats van honderden deeltjes.
 */
public record BeamEffectPayload(Vec3 from, Vec3 to, Kind kind) implements CustomPacketPayload {
    public static final Type<BeamEffectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "beam_effect"));

    public enum Kind {
        /** Laserschot van een drone. */
        DRONE_LASER,
        /** De satelliet richt: dunne rode straal en een ring op de grond. */
        SATELLITE_CHARGE,
        /** De satelliet vuurt: dikke, felle straal. */
        SATELLITE_FIRE,
        /** Precisie-straal: dun, wit en oogverblindend. */
        SATELLITE_PRECISION,
        /** De schuivende laser: een brede streep die over de grond kruipt. */
        SATELLITE_SWEEP,
        /** Fase 15: een repulsorstoot uit je handpalm. */
        REPULSOR,
        /** Het lasergeweer van een Hydra-soldaat: een rode straal. */
        HYDRA_LASER
    }

    public static final StreamCodec<ByteBuf, BeamEffectPayload> STREAM_CODEC = StreamCodec.composite(
            ModCodecs.VEC3, BeamEffectPayload::from,
            ModCodecs.VEC3, BeamEffectPayload::to,
            ByteBufCodecs.VAR_INT.map(i -> Kind.values()[Math.floorMod(i, Kind.values().length)], Kind::ordinal), BeamEffectPayload::kind,
            BeamEffectPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
