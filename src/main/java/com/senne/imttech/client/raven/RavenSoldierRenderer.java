package com.senne.imttech.client.raven;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.ImtTech;
import com.senne.imttech.raven.RavenSoldierEntity;
import net.minecraft.util.Mth;
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
 * tools/generate_textures.py) en zijn lasergeweer in de hand. Sinds 8 november ook de sluipaanval: hij worstelt, wordt
 * slap, zakt door zijn knieën en valt achterover (TakedownPose).
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

    /**
     * Een sluipaanval (8 november): zijn hele lijf trilt en leunt achterover terwijl hij worstelt, zakt dan tot hij zit en
     * valt achterover (om zijn heupen). De armen, benen en het hoofd doet TakedownPose.
     */
    @Override
    protected void setupRotations(RavenSoldierEntity soldier, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(soldier, pose, bob, yBodyRot, partialTick, scale);
        if (!soldier.isBeingTakenDown()) {
            return;
        }
        float age = soldier.tickCount + partialTick;
        TakedownPose.Phases p = TakedownPose.phases(soldier.takedownProgress(age));
        pose.translate(0.0, -TakedownPose.SINK * p.sink(), 0.0);
        float back = TakedownPose.LEAN_DEGREES * p.struggle() + TakedownPose.FALL_DEGREES * p.fall();
        float shudder = Mth.sin(age * 3.3f) * 3.0f * p.struggle();
        pose.translate(0.0, TakedownPose.HIP, 0.0);
        pose.mulPose(Axis.XP.rotationDegrees(-back));
        pose.mulPose(Axis.ZP.rotationDegrees(shudder));
        pose.translate(0.0, -TakedownPose.HIP, 0.0);
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
            if (soldier.isBeingTakenDown()) {
                // 8 november: iemand heeft hem vast; de houding komt uit TakedownPose (zie setupAnim).
                rightArmPose = ArmPose.EMPTY;
                leftArmPose = ArmPose.EMPTY;
                super.prepareMobModel(soldier, limbSwing, limbSwingAmount, partialTick);
                return;
            }
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

        @Override
        public void setupAnim(RavenSoldierEntity soldier, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
            super.setupAnim(soldier, limbSwing, limbSwingAmount, age, netHeadYaw, headPitch);
            if (soldier.isBeingTakenDown()) {
                TakedownPose.apply(this, soldier.takedownProgress(age), age);
            }
        }
    }
}
