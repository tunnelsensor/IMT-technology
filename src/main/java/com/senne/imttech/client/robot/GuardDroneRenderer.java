package com.senne.imttech.client.robot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.ImtTech;
import com.senne.imttech.robot.GuardDroneEntity;
import com.senne.imttech.robot.RobotState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Tekent een security drone (hoofdstuk 25): het model met de modules die op zijn printplaat zitten (zie
 * GuardDroneModel), en daarover zijn lichtjes, die ook in het donker gloeien (cyaan, en rood bij de laser en de
 * afstandssensor). Staat hij uit of is zijn batterij leeg, dan zijn ze uit. Ligt hij neer (na de Ion Blaster), dan
 * ligt hij scheef op de grond.
 */
public class GuardDroneRenderer extends MobRenderer<GuardDroneEntity, GuardDroneModel> {
    private static final ResourceLocation TEXTURE = texture("guard_drone");
    private static final RenderType GLOW = RenderType.eyes(texture("guard_drone_glow"));

    public GuardDroneRenderer(EntityRendererProvider.Context context) {
        super(context, new GuardDroneModel(context.bakeLayer(GuardDroneModel.LAYER)), 0.4f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }

            /** Alleen als hij aan staat: uit of met een lege batterij zijn zijn lichtjes uit. */
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffers, int light, GuardDroneEntity drone,
                               float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                               float netHeadYaw, float headPitch) {
                if (drone.state() == RobotState.ACTIVE) {
                    super.render(poseStack, buffers, light, drone, limbSwing, limbSwingAmount, partialTick, ageInTicks,
                            netHeadYaw, headPitch);
                }
            }
        });
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/entity/robot/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(GuardDroneEntity drone) {
        return TEXTURE;
    }

    /** Neergelegd door de Ion Blaster: hij ligt scheef op de grond, op één ski. */
    @Override
    protected void setupRotations(GuardDroneEntity drone, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(drone, pose, bob, yBodyRot, partialTick, scale);
        if (drone.state() == RobotState.DOWN && drone.deathTime == 0) {
            pose.translate(0.0, 0.1, 0.0);
            pose.mulPose(Axis.ZP.rotationDegrees(28.0f));
        }
    }
}
