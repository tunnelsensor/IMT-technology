package com.senne.imttech.client.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.guns.CasingEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Quaternionf;

/**
 * Een lege huls (hoofdstuk 52): het messing buisje, tollend door de lucht en daarna op zijn kant op de grond.
 */
public class CasingRenderer extends EntityRenderer<CasingEntity> {
    /** Zo groot is de huls in de wereld (het model is 2,5 pixel lang: een echte huls is 19 mm). */
    private static final float SCALE = 0.5f;

    public CasingRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public void render(CasingEntity casing, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float spin = Mth.lerp(partialTick, casing.spinO, casing.spin);
        pose.translate(0, 0.03f, 0);
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(spin * 0.4f)).rotateZ((float) Math.toRadians(90))
                .rotateX((float) Math.toRadians(casing.onGround() ? 0 : spin)));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-0.5, -0.47, -0.5);
        FirearmModels.render(pose, buffers, FirearmModels.location("casing"), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(casing, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CasingEntity casing) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
