package com.senne.imttech.client.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.ImtTech;
import com.senne.imttech.drone.DroneDamage;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Roetvlekken en krassen over een beschadigde drone, zoals de barsten op een ijzergolem.
 * Drie plaatjes, van "wat krassen" tot "zwartgeblakerd" (tools/generate_textures.py maakt ze).
 * Voor je eigen drones en de raiddrones: dezelfde machine, dus dezelfde schade.
 */
public class DamageLayer<T extends LivingEntity> extends RenderLayer<T, DroneModel<T>> {
    private static final ResourceLocation LOW = texture("drone_damage_low");
    private static final ResourceLocation MEDIUM = texture("drone_damage_medium");
    private static final ResourceLocation HIGH = texture("drone_damage_high");

    public DamageLayer(RenderLayerParent<T, DroneModel<T>> renderer) {
        super(renderer);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/entity/drone/" + name + ".png");
    }

    /** Het roetplaatje bij deze schade, of null als de drone nog heel genoeg is. */
    public static @Nullable ResourceLocation overlay(float damage) {
        return switch (DroneDamage.stage(damage)) {
            case FINE -> null;
            case SCRATCHED -> LOW;
            case BROKEN -> MEDIUM;
            case WRECKED -> HIGH;
        };
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T drone, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (drone.isInvisible()) {
            return;
        }
        ResourceLocation texture = overlay(DroneDamage.of(drone));
        if (texture != null) {
            renderColoredCutoutModel(getParentModel(), texture, poseStack, buffers, light, drone, 0xFFFFFFFF);
        }
    }
}
