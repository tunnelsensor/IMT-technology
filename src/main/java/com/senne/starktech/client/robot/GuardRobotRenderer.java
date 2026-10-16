package com.senne.starktech.client.robot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.starktech.StarkTech;
import com.senne.starktech.robot.GuardRobotEntity;
import com.senne.starktech.robot.RobotState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Tekent een guard robot (hoofdstuk 21): het model met de gekozen stijl van elk onderdeel (zie RobotModel)
 * en daarover zijn lichtjes, die ook in het donker gloeien (het rode oog van de Commando, het blauw van de
 * Trainer). Staat hij uit of is zijn batterij leeg, dan zijn ze uit.
 */
public class GuardRobotRenderer extends MobRenderer<GuardRobotEntity, RobotModel> {
    private static final ResourceLocation TEXTURE = texture("guard_robot");
    private static final RenderType GLOW = RenderType.eyes(texture("guard_robot_glow"));

    public GuardRobotRenderer(EntityRendererProvider.Context context) {
        super(context, new RobotModel(context.bakeLayer(RobotModel.LAYER)), 0.5f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }

            /** Alleen als hij aan staat: uit of met een lege batterij zijn zijn lichtjes uit. */
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffers, int light, GuardRobotEntity robot,
                               float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                               float netHeadYaw, float headPitch) {
                if (robot.state() == RobotState.ACTIVE) {
                    super.render(poseStack, buffers, light, robot, limbSwing, limbSwingAmount, partialTick, ageInTicks,
                            netHeadYaw, headPitch);
                }
            }
        });
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/entity/robot/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(GuardRobotEntity robot) {
        return TEXTURE;
    }
}
