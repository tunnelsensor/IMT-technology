package com.senne.starktech.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * Kleine hulpjes om gegevens over het netwerk te sturen die Minecraft 1.21.1 zelf niet
 * kant-en-klaar heeft.
 */
public final class ModCodecs {
    private ModCodecs() {}

    /** Een plek in de wereld (x, y, z) als drie kommagetallen. */
    public static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x,
            ByteBufCodecs.DOUBLE, Vec3::y,
            ByteBufCodecs.DOUBLE, Vec3::z,
            Vec3::new);
}
