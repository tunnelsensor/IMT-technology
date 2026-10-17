package com.senne.imttech.client.suit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.ImtTech;
import com.senne.imttech.suit.MiniRocketEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Tekent een mini-raket: een dun buisje met een rode neus en vier vinnetjes achteraan.
 *
 * Het model ligt langs de Z-as met de neus naar voren (+Z). We draaien het eerst omhoog of
 * omlaag (pitch) en dan opzij (yaw), zodat de neus precies in de vliegrichting wijst.
 */
public class MiniRocketRenderer extends EntityRenderer<MiniRocketEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "mini_rocket"), "main");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/entity/mini_rocket.png");

    private final ModelPart model;

    public MiniRocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = context.bakeLayer(LAYER);
    }

    /** Maten in pixels (16 = een blok): het buisje is 2 dik en 8 lang. Het plaatje is 32x16. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1f, -1f, -4f, 2, 2, 8), PartPose.ZERO);
        root.addOrReplaceChild("fins_v", CubeListBuilder.create().texOffs(20, 0)
                .addBox(-0.5f, -3f, -4f, 1, 6, 2), PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(26, 0)
                .addBox(-0.5f, -0.5f, 4f, 1, 1, 2), PartPose.ZERO);
        root.addOrReplaceChild("fins_h", CubeListBuilder.create().texOffs(0, 10)
                .addBox(-3f, -0.5f, -4f, 6, 1, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public void render(MiniRocketEntity rocket, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.translate(0, rocket.getBbHeight() / 2, 0);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, rocket.yRotO, rocket.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, rocket.xRotO, rocket.getXRot())));
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), packedLight,
                OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(rocket, entityYaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MiniRocketEntity rocket) {
        return TEXTURE;
    }
}
