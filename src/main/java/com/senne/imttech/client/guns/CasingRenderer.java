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
    /**
     * Zo groot is de huls in de wereld. Sinds hoofdstuk 74 is het model op ware grootte naast het pistool (1,5 pixel
     * lang, 19 mm); 0,85 houdt hem op de grond even groot als vroeger, zodat je hem nog vindt om op te rapen.
     */
    private static final float SCALE = 0.85f;

    /** De lengte van de 9mm-huls in het model (pixels): daar is {@link #SCALE} op afgesteld. */
    private static final float LENGTH_9MM = 1.5f;
    private static final java.util.Map<com.senne.imttech.guns.Caliber, Float> SCALES =
            new java.util.EnumMap<>(com.senne.imttech.guns.Caliber.class);

    /**
     * Hoofdstuk 103: zo groot is de huls van dit kaliber in de wereld. Een 9mm-huls is hier ruim vier keer zo groot als
     * een echte (anders vind je hem niet terug). Een huls van een geweer is op die schaal een staaf van 16 cm, dus die
     * krimpt mee met de wortel van zijn lengte: een 7.62-huls is zo nog duidelijk langer dan een 9mm-huls, maar geen
     * knuppel. De lengte meten we aan het model zelf.
     */
    private static float scale(com.senne.imttech.guns.Caliber caliber) {
        return SCALES.computeIfAbsent(caliber, key -> {
            net.minecraft.world.phys.AABB box = FirearmRig.measure(FirearmModels.casing(key));
            float length = (float) Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
            return SCALE * (float) Math.sqrt(LENGTH_9MM / Math.max(LENGTH_9MM, length));
        });
    }

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
        float scale = scale(casing.caliber());
        pose.scale(scale, scale, scale);
        pose.translate(-0.5, -0.47, -0.5);
        // Hoofdstuk 103: elk kaliber zijn eigen huls (die van de AK-47 is langer en heeft een flessenhals).
        FirearmModels.render(pose, buffers, FirearmModels.casing(casing.caliber()), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(casing, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CasingEntity casing) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
