package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Bericht van server naar spelers in de buurt: "hier ging een EMP-granaat af" (hoofdstuk 35). Je spel tekent dan een
 * bol van blauw licht die snel groter wordt, met bliksemflitsen erin (zie client/weapons/EmpEffects).
 */
public record EmpBlastPayload(Vec3 center, float radius) implements CustomPacketPayload {
    public static final Type<EmpBlastPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "emp_blast"));

    public static final StreamCodec<ByteBuf, EmpBlastPayload> STREAM_CODEC = StreamCodec.composite(
            ModCodecs.VEC3, EmpBlastPayload::center,
            ByteBufCodecs.FLOAT, EmpBlastPayload::radius,
            EmpBlastPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
