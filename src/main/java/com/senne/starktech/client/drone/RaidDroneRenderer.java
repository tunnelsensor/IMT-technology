package com.senne.starktech.client.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.starktech.StarkTech;
import com.senne.starktech.drone.DroneDamage;
import com.senne.starktech.raid.RaidDroneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Tekent de vijandige raiddrones.
 *
 * Hetzelfde model als je eigen drones (het is dezelfde soort machine), maar met een donkere
 * romp en fel oranje lampjes, zodat je in een gevecht meteen ziet welke er niet van jou is.
 */
public class RaidDroneRenderer extends MobRenderer<RaidDroneEntity, DroneModel<RaidDroneEntity>> {
    private static final ResourceLocation TEXTURE = texture("raid_drone");
    private static final RenderType LIGHTS = RenderType.eyes(texture("raid_drone_lights"));

    public RaidDroneRenderer(EntityRendererProvider.Context context) {
        super(context, new DroneModel<>(context.bakeLayer(DroneModel.LAYER)), 0.3f);
        addLayer(new DamageLayer<>(this));
        addLayer(new LightsLayer(this));
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/entity/drone/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(RaidDroneEntity drone) {
        return TEXTURE;
    }

    /** De lampjes gloeien ook in het donker, zodat je ze aan zien komen. */
    private static class LightsLayer extends RenderLayer<RaidDroneEntity, DroneModel<RaidDroneEntity>> {
        LightsLayer(RenderLayerParent<RaidDroneEntity, DroneModel<RaidDroneEntity>> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, RaidDroneEntity drone,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (DroneDamage.lightsFlickerOff(DroneDamage.of(drone), ageInTicks, drone.getUUID().hashCode())) {
                return;
            }
            getParentModel().renderToBuffer(poseStack, buffers.getBuffer(LIGHTS), light,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
    }
}
