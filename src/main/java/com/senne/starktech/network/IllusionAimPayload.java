package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import com.senne.starktech.illusion.IllusionScene;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar de eigenaar: "je richt nu een illusie met deze drone; laat dit beeld
 * als voorvertoning zien op de grond onder de drone". Een leeg beeld betekent: klaar met richten
 * (neergezet of geannuleerd).
 *
 * @param droneId het nummer van de drone waarmee je richt
 * @param scene   het beeld (nog niet gedraaid naar je kijkrichting), of leeg om te stoppen
 */
public record IllusionAimPayload(int droneId, Optional<IllusionScene> scene) implements CustomPacketPayload {
    public static final Type<IllusionAimPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "illusion_aim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IllusionAimPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, IllusionAimPayload::droneId,
            ByteBufCodecs.optional(IllusionScene.STREAM_CODEC), IllusionAimPayload::scene,
            IllusionAimPayload::new);

    public static final IllusionAimPayload DONE = new IllusionAimPayload(-1, Optional.empty());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
