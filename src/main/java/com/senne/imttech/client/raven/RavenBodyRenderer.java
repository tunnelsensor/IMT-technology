package com.senne.imttech.client.raven;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.ImtTech;
import com.senne.imttech.raven.RavenBodyEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Het lichaam van een gevallen Raven-soldaat (hoofdstuk 60, stap 5): hetzelfde mensmodel en pak als de soldaat, maar
 * plat op zijn rug op de grond, zoals een robot die de Ion Blaster vloerde (GuardRobotRenderer). Het model wordt zo
 * gedraaid dat het midden van het lichaam op de plek van het wezen ligt (de hitbox is een platte doos).
 */
public class RavenBodyRenderer extends LivingEntityRenderer<RavenBodyEntity, HumanoidModel<RavenBodyEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/entity/raven_soldier.png");

    public RavenBodyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(RavenSoldierRenderer.LAYER)), 0.0f);
    }

    @Override
    public ResourceLocation getTextureLocation(RavenBodyEntity body) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(RavenBodyEntity body, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(body, pose, bob, yBodyRot, partialTick, scale);
        // Op zijn rug: iets boven de grond, een kwartslag achterover, en het midden van het lichaam op de plek van het wezen.
        pose.translate(0.0, 0.3, 0.0);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
        pose.translate(0.0, -0.9, 0.0);
    }

    @Override
    protected boolean shouldShowName(RavenBodyEntity body) {
        return false;
    }
}
