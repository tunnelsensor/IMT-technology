package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van jouw spel naar de server: een skillpunt uitgeven (hoofdstuk 52, stap 3) op deze skill
 * ({@code skill} = GunSkill.ordinal()) in deze scope ("global", of de naam van het pistool, "glock_19").
 */
public record SkillLearnPayload(String scope, int skill) implements CustomPacketPayload {
    public static final Type<SkillLearnPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "skill_learn"));

    public static final StreamCodec<ByteBuf, SkillLearnPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SkillLearnPayload::scope,
            ByteBufCodecs.VAR_INT, SkillLearnPayload::skill,
            SkillLearnPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
