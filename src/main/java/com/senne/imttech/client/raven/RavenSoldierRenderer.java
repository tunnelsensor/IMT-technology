package com.senne.imttech.client.raven;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

import com.senne.imttech.ImtTech;
import com.senne.imttech.raven.RavenSoldierEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Tekent een Raven-soldaat: het gewone mensmodel met zijn eigen pak (zie raven_soldier() in
 * tools/generate_textures.py) en zijn lasergeweer in de hand.
 */
public class RavenSoldierRenderer extends HumanoidMobRenderer<RavenSoldierEntity, RavenSoldierRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "raven_soldier"), "main");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/entity/raven_soldier.png");

    public RavenSoldierRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(LAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(RavenSoldierEntity soldier) {
        return TEXTURE;
    }

    /** Hoofdstuk 60: boven een bewaker die iets in de gaten heeft staat een "?" (achterdochtig, zoekend) of een "!" (hij heeft je). */
    @Override
    public void render(RavenSoldierEntity soldier, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(soldier, yaw, partialTick, pose, buffers, light);
        AlertMarks.render(entityRenderDispatcher, getFont(), soldier, soldier.alertState(), pose, buffers, light);
    }

    /** Het gewone mensmodel (zoals een zombie), 64 x 64. */
    public static LayerDefinition createLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f), 64, 64);
    }

    /**
     * Het model: in de aanval legt hij zijn geweer aan met twee handen (de houding van een
     * kruisboogschutter), anders houdt hij het gewoon vast.
     */
    public static class Model extends HumanoidModel<RavenSoldierEntity> {
        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void prepareMobModel(RavenSoldierEntity soldier, float limbSwing, float limbSwingAmount, float partialTick) {
            if (com.senne.imttech.cuffs.Handcuffs.isCuffed(soldier)) {
                // Geboeid (hoofdstuk 54): de handen op de rug.
                rightArmPose = com.senne.imttech.client.cuffs.CuffArmPoses.CUFFED.getValue();
                leftArmPose = com.senne.imttech.client.cuffs.CuffArmPoses.CUFFED.getValue();
                super.prepareMobModel(soldier, limbSwing, limbSwingAmount, partialTick);
                return;
            }
            if (soldier.alertState() == com.senne.imttech.raven.RavenAlert.CALLING) {
                // Hoofdstuk 60: hij roept versterking, de radio aan zijn oor (linkerhand), het geweer losjes in zijn rechter.
                rightArmPose = ArmPose.ITEM;
                leftArmPose = ArmPose.SPYGLASS;
                super.prepareMobModel(soldier, limbSwing, limbSwingAmount, partialTick);
                return;
            }
            rightArmPose = soldier.isAggressive() ? ArmPose.CROSSBOW_HOLD : ArmPose.ITEM;
            leftArmPose = ArmPose.EMPTY;
            super.prepareMobModel(soldier, limbSwing, limbSwingAmount, partialTick);
        }
    }
}
