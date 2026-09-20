package com.senne.starktech.client.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.starktech.StarkTech;
import com.senne.starktech.drone.StarkDroneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Tekent de Stark-drones.
 */
public class DroneRenderer extends MobRenderer<StarkDroneEntity, DroneModel> {
    private static final ResourceLocation TEXTURE = texture("stark_drone");

    public DroneRenderer(EntityRendererProvider.Context context) {
        super(context, new DroneModel(context.bakeLayer(DroneModel.LAYER)), 0.25f);
        addLayer(new LightsLayer(this));
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/entity/drone/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(StarkDroneEntity drone) {
        return TEXTURE;
    }

    /**
     * De lampjes van de drone. Ze gloeien ook in het donker ("eyes"-rendering, zoals de
     * ogen van een enderman). Rood voor aanvalsdrones, blauw voor beschermende en
     * transportdrones, wit voor de verkenner en de spion.
     */
    private static class LightsLayer extends RenderLayer<StarkDroneEntity, DroneModel> {
        private static final RenderType ATTACK_LIGHTS = RenderType.eyes(texture("stark_drone_lights_attack"));
        private static final RenderType GUARD_LIGHTS = RenderType.eyes(texture("stark_drone_lights_guard"));
        private static final RenderType SCOUT_LIGHTS = RenderType.eyes(texture("stark_drone_lights_scout"));

        LightsLayer(RenderLayerParent<StarkDroneEntity, DroneModel> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, StarkDroneEntity drone,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            RenderType lights = switch (drone.getMode()) {
                case ATTACK -> ATTACK_LIGHTS;
                case GUARD, TRANSPORT -> GUARD_LIGHTS;
                // De spion en de cloakingdrone zijn onzichtbaar; zie je ze toch, dan wit.
                // Een drone op weg naar zijn baan krijgt hetzelfde witte licht.
                case SCOUT, SPY, ILLUSION, LAUNCH -> SCOUT_LIGHTS;
            };
            getParentModel().renderToBuffer(poseStack, buffers.getBuffer(lights), light,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
    }
}
