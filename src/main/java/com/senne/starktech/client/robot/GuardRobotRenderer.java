package com.senne.starktech.client.robot;

import com.senne.starktech.StarkTech;
import com.senne.starktech.robot.GuardRobotEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Tekent een guard robot (hoofdstuk 21): het model met de gekozen stijl van elk onderdeel (zie RobotModel)
 * en daarover zijn lichtjes, die ook in het donker gloeien (het rode oog van de Commando, het blauw van de
 * Trainer).
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
