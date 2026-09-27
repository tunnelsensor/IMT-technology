package com.senne.starktech.client.satellite;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.starktech.StarkTech;
import com.senne.starktech.satellite.OrbitalMissileEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Tekent de raket van de satelliet: een lange witte romp met een rode neus, vier zwarte vinnen,
 * een straalpijp en een flakkerende vlam die in het donker gloeit.
 *
 * Net als de mini-raket ligt het model langs de Z-as met de neus naar voren (+Z); we draaien het
 * zo dat de neus in de vliegrichting wijst. Hij wordt anderhalf keer zo groot getekend, zodat je
 * hem ook hoog in de lucht nog ziet vallen.
 */
public class OrbitalMissileRenderer extends EntityRenderer<OrbitalMissileEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "orbital_missile"), "main");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/entity/orbital_missile.png");

    private final ModelPart body;
    private final ModelPart flame;

    public OrbitalMissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        ModelPart root = context.bakeLayer(LAYER);
        this.body = root.getChild("body");
        this.flame = root.getChild("flame");
    }

    /** Maten in pixels (16 = een blok): de romp is 4 dik en 24 lang. Het plaatje is 64x64. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2f, -2f, -12f, 4, 4, 24), PartPose.ZERO);
        body.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(0, 28)
                .addBox(-1.5f, -1.5f, 12f, 3, 3, 3), PartPose.ZERO);
        body.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(12, 28)
                .addBox(-0.5f, -0.5f, 15f, 1, 1, 2), PartPose.ZERO);
        body.addOrReplaceChild("fins_v", CubeListBuilder.create().texOffs(20, 28)
                .addBox(-0.5f, -5f, -12f, 1, 10, 5), PartPose.ZERO);
        body.addOrReplaceChild("fins_h", CubeListBuilder.create().texOffs(32, 28)
                .addBox(-5f, -0.5f, -12f, 10, 1, 5), PartPose.ZERO);
        body.addOrReplaceChild("nozzle", CubeListBuilder.create().texOffs(0, 44)
                .addBox(-1.5f, -1.5f, -14f, 3, 3, 2), PartPose.ZERO);
        root.addOrReplaceChild("flame", CubeListBuilder.create().texOffs(12, 44)
                .addBox(-1.5f, -1.5f, -8f, 3, 3, 8), PartPose.offset(0, 0, -14f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void render(OrbitalMissileEntity missile, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.translate(0, missile.getBbHeight() / 2, 0);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, missile.yRotO, missile.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, missile.xRotO, missile.getXRot())));
        pose.scale(1.5f, 1.5f, 1.5f);
        body.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
        // De vlam: altijd fel (ook 's nachts), en hij flakkert in lengte.
        float flicker = 0.8f + 0.35f * Mth.sin((missile.tickCount + partialTick) * 2.3f)
                + 0.15f * Mth.sin((missile.tickCount + partialTick) * 5.1f);
        flame.zScale = flicker;
        flame.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(missile, entityYaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(OrbitalMissileEntity missile) {
        return TEXTURE;
    }
}
