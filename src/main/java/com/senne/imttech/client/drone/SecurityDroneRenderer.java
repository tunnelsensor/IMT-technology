package com.senne.imttech.client.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.ImtTech;
import com.senne.imttech.drone.DroneDamage;
import com.senne.imttech.security.SecurityDroneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Tekent een beveiligingsdrone: hetzelfde model als de andere drones, in I.M.T-wit met een rode
 * streep. Zijn lampjes zijn rood zolang de toren van niemand is (hij valt je aan), en blauw als
 * de toren van iemand is.
 */
public class SecurityDroneRenderer extends MobRenderer<SecurityDroneEntity, DroneModel<SecurityDroneEntity>> {
    private static final ResourceLocation TEXTURE = texture("security_drone");
    private static final RenderType ALARM = RenderType.eyes(texture("security_drone_lights_alarm"));
    private static final RenderType CALM = RenderType.eyes(texture("drone_lights_guard"));

    public SecurityDroneRenderer(EntityRendererProvider.Context context) {
        super(context, new DroneModel<>(context.bakeLayer(DroneModel.LAYER)), 0.3f);
        addLayer(new DamageLayer<>(this));
        addLayer(new LightsLayer(this));
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/entity/drone/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(SecurityDroneEntity drone) {
        return TEXTURE;
    }

    /** De lampjes gloeien ook in het donker. */
    private static class LightsLayer extends RenderLayer<SecurityDroneEntity, DroneModel<SecurityDroneEntity>> {
        LightsLayer(RenderLayerParent<SecurityDroneEntity, DroneModel<SecurityDroneEntity>> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, SecurityDroneEntity drone,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            // Geen stroom (de kern staat uit): de lampjes zijn uit.
            if (drone.isOffline() || DroneDamage.lightsFlickerOff(DroneDamage.of(drone), ageInTicks, drone.getUUID().hashCode())) {
                return;
            }
            getParentModel().renderToBuffer(poseStack, buffers.getBuffer(drone.isClaimed() ? CALM : ALARM), light,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
    }
}
