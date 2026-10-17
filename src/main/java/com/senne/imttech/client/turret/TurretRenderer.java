package com.senne.imttech.client.turret;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.senne.imttech.ImtTech;
import com.senne.imttech.turret.TurretBlockEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Tekent de draaiende kop van een torentje (de voet is een gewoon blokmodel).
 *
 * De kop bestaat uit twee delen:
 * - de draaischijf: die draait alleen opzij (yaw);
 * - het geschut (kop, loop en raketbuis): dat draait opzij én kantelt omhoog/omlaag (pitch).
 *
 * Hoe de kop staat, rekent TurretBlockEntity.clientTick elke tick uit; hier tekenen we hem
 * vloeiend tussen de vorige en de huidige stand in.
 *
 * Het model ligt met de loop langs +Z. Maten in pixels (16 = een blok); het plaatje is 64x32
 * en wordt gemaakt door tools/generate_textures.py.
 */
public class TurretRenderer implements BlockEntityRenderer<TurretBlockEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "turret"), "main");
    private static final ResourceLocation ROCKET_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/entity/rocket_turret.png");
    private static final ResourceLocation RAVEN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/entity/raven_turret.png");
    private static final ResourceLocation SECURITY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/entity/security_turret.png");
    /** Hoe hoog het draaipunt van de kop zit (in pixels boven de onderkant van het blok). */
    private static final float PIVOT_Y = 11;

    private final ModelPart swivel;
    private final ModelPart gun;

    public TurretRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        this.swivel = root.getChild("swivel");
        this.gun = root.getChild("gun");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Draaischijf: ligt net op de voet (die is 7 pixels hoog).
        root.addOrReplaceChild("swivel", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3f, -4f, -3f, 6, 1, 6), PartPose.ZERO);
        PartDefinition gun = root.addOrReplaceChild("gun", CubeListBuilder.create().texOffs(0, 7)
                .addBox(-4f, -3f, -4f, 8, 6, 8), PartPose.ZERO);
        gun.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(32, 0)
                .addBox(-1f, -1f, 4f, 2, 2, 7), PartPose.ZERO);
        gun.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(50, 0)
                .addBox(-1.5f, -1.5f, 10f, 3, 3, 1), PartPose.ZERO);
        // De raketbuis aan de rechterkant.
        gun.addOrReplaceChild("pod", CubeListBuilder.create().texOffs(32, 9)
                .addBox(-7f, -2f, -2f, 3, 4, 5), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void render(TurretBlockEntity turret, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        ResourceLocation texture = switch (turret.kind()) {
            case ROCKET -> ROCKET_TEXTURE;
            case RAVEN -> RAVEN_TEXTURE;
            case SECURITY -> SECURITY_TEXTURE;
        };
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        pose.pushPose();
        pose.translate(0.5, PIVOT_Y / 16.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, turret.yawO, turret.yaw)));
        swivel.render(pose, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, turret.pitchO, turret.pitch)));
        gun.render(pose, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
