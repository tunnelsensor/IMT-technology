package com.senne.starktech.client.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.starktech.StarkTech;
import com.senne.starktech.drone.StarkDroneEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Tekent de Stark-drones.
 */
public class DroneRenderer extends MobRenderer<StarkDroneEntity, DroneRenderState, DroneModel> {
    private static final Identifier TEXTURE = texture("stark_drone");

    public DroneRenderer(EntityRendererProvider.Context context) {
        super(context, new DroneModel(context.bakeLayer(DroneModel.LAYER)), 0.25f);
        addLayer(new LightsLayer(this));
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(StarkTech.MODID, "textures/entity/drone/" + name + ".png");
    }

    @Override
    public Identifier getTextureLocation(DroneRenderState state) {
        return TEXTURE;
    }

    @Override
    public DroneRenderState createRenderState() {
        return new DroneRenderState();
    }

    @Override
    public void extractRenderState(StarkDroneEntity entity, DroneRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.mode = entity.getMode();
    }

    /**
     * De lampjes van de drone. Ze gloeien ook in het donker ("eyes"-rendering, zoals de
     * ogen van een enderman). Rood voor aanvalsdrones, blauw voor beschermende drones,
     * wit voor de verkenningsdrone.
     */
    private static class LightsLayer extends RenderLayer<DroneRenderState, DroneModel> {
        private static final RenderType ATTACK_LIGHTS = RenderTypes.eyes(texture("stark_drone_lights_attack"));
        private static final RenderType GUARD_LIGHTS = RenderTypes.eyes(texture("stark_drone_lights_guard"));
        private static final RenderType SCOUT_LIGHTS = RenderTypes.eyes(texture("stark_drone_lights_scout"));

        LightsLayer(RenderLayerParent<DroneRenderState, DroneModel> renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DroneRenderState state, float yRot, float xRot) {
            RenderType lights = switch (state.mode) {
                case ATTACK -> ATTACK_LIGHTS;
                case GUARD -> GUARD_LIGHTS;
                // De spion is onzichtbaar; zie je hem toch (bv. met een detector), dan wit.
                case SCOUT, SPY -> SCOUT_LIGHTS;
                // Transportdrones: blauw, net als de beschermers.
                case TRANSPORT -> GUARD_LIGHTS;
            };
            collector.order(1).submitModel(getParentModel(), state, poseStack, lights, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
    }
}
