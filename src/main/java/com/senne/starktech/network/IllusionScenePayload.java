package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import com.senne.starktech.illusion.IllusionScene;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "deze drone projecteert dit beeld".
 *
 * Een leeg beeld (scene = leeg) betekent: de illusie is voorbij, vergeet hem.
 *
 * @param droneId het nummer van de cloakingdrone
 * @param radius  hoe groot het veld is
 * @param jammed  true = de illusie ligt plat door een E.M.P.
 * @param anchor  het punt op de grond waar het beeld staat (zie IllusionScene)
 * @param scene   het beeld, of leeg om te stoppen
 */
public record IllusionScenePayload(int droneId, double radius, boolean jammed, BlockPos anchor,
                                   Optional<IllusionScene> scene) implements CustomPacketPayload {
    public static final Type<IllusionScenePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "illusion_scene"));

    /** "Deze illusie is voorbij." */
    public static IllusionScenePayload stop(int droneId) {
        return new IllusionScenePayload(droneId, 0, false, BlockPos.ZERO, Optional.empty());
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, IllusionScenePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, IllusionScenePayload::droneId,
            ByteBufCodecs.DOUBLE, IllusionScenePayload::radius,
            ByteBufCodecs.BOOL, IllusionScenePayload::jammed,
            BlockPos.STREAM_CODEC, IllusionScenePayload::anchor,
            ByteBufCodecs.optional(IllusionScene.STREAM_CODEC), IllusionScenePayload::scene,
            IllusionScenePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
