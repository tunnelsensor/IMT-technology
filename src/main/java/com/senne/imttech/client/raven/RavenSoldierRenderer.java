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
        addLayer(new PackLayer(this));
    }

    /** Hoofdstuk 72, stap 5: zijn rugzak op zijn rug (soms een antracietgrijze Assault Pack), zoals bij een speler. */
    private static final class PackLayer extends net.minecraft.client.renderer.entity.layers.RenderLayer<RavenSoldierEntity, Model> {
        PackLayer(net.minecraft.client.renderer.entity.RenderLayerParent<RavenSoldierEntity, Model> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, RavenSoldierEntity soldier,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            net.minecraft.world.item.ItemStack pack = soldier.pack();
            if (pack.isEmpty() || soldier.isInvisible()) {
                return;
            }
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            com.senne.imttech.client.backpack.BackpackLayer.renderOnBack(pose, buffers, light, pack, soldier.level(), false);
            pose.popPose();
        }
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
     * kruisboogschutter), anders houdt hij het gewoon vast. Een lang vuurwapen (hoofdstuk 103, stap 8: de MP5, de AK-47,
     * de AWP en de Remington 870) draagt hij in rust met twee handen voor zijn lijf, de loop schuin naar de grond: aan
     * één hangende arm stak de loop van de AWP in de vloer.
     */
    public static class Model extends HumanoidModel<RavenSoldierEntity> {
        /**
         * Hoe ver zijn armen in rust lager hangen dan aangelegd, met een lang vuurwapen (radialen: ruim 40 graden). Een lang
         * wapen houdt hij vlakker, zodat zijn loop de grond niet raakt (zie lowReadyAngle).
         */
        private static final float LOW_READY = 0.75f;
        /**
         * Hoe ver de loop in rust onder zijn hand mag zakken (blokken). Zo laag hield hij de Remington 870 vroeger, en dat
         * ging net; de AWP (die vroeger op 0,5 radialen stond) kwam op 0,68.
         */
        private static final float MAX_MUZZLE_DROP = 0.76f;
        /** Draagt hij nu een lang vuurwapen in rust, en hoe laag? (per soldaat opnieuw bepaald in prepareMobModel) */
        private boolean lowReady;
        private float lowReadyAngle = LOW_READY;

        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void prepareMobModel(RavenSoldierEntity soldier, float limbSwing, float limbSwingAmount, float partialTick) {
            lowReady = false;
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
            com.senne.imttech.guns.FirearmType firearm = com.senne.imttech.guns.FirearmItem.typeOf(soldier.getMainHandItem());
            lowReady = !soldier.isAggressive() && firearm != null && firearm.longGun;
            lowReadyAngle = firearm == null ? LOW_READY : lowReadyAngle(firearm);
            super.prepareMobModel(soldier, limbSwing, limbSwingAmount, partialTick);
        }

        /**
         * Hoofdstuk 105: hoe laag hij dit wapen in rust houdt. Sinds elk vuurwapen op dezelfde maat getekend wordt
         * (FirearmRig.SCALE) is een lang wapen langer dan vroeger, de AWP een kwart. Zijn loop mag niet verder dan
         * {@link #MAX_MUZZLE_DROP} onder zijn hand zakken: hoe langer de loop voor zijn hand, hoe vlakker hij het houdt (de
         * MP5 en de AK-47 gewoon op 0,75, de 870 op ongeveer 0,64, de AWP op ongeveer 0,45).
         */
        static float lowReadyAngle(com.senne.imttech.guns.FirearmType type) {
            com.senne.imttech.client.guns.FirearmRig rig = com.senne.imttech.client.guns.FirearmRig.of(type);
            float reach = (rig.grip.z - rig.muzzle.z) / 16f * com.senne.imttech.client.guns.FirearmRig.SCALE;
            if (reach <= MAX_MUZZLE_DROP) {
                return LOW_READY;
            }
            return Math.min(LOW_READY, (float) Math.asin(MAX_MUZZLE_DROP / reach));
        }

        @Override
        public void setupAnim(RavenSoldierEntity soldier, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
            super.setupAnim(soldier, limbSwing, limbSwingAmount, age, netHeadYaw, headPitch);
            if (soldier.isBeingTakenDown()) {
                TakedownPose.apply(this, soldier.takedownProgress(age), age);
            } else if (lowReady) {
                // Dezelfde greep als aangelegd (de kruisbooghouding), maar lager, en los van waar hij kijkt: kijkt een
                // bewaker op zijn route om zich heen, dan zwaait zijn geweer niet mee.
                rightArm.yRot = -0.3f;
                leftArm.yRot = 0.6f;
                rightArm.xRot = -Mth.HALF_PI + 0.1f + lowReadyAngle;
                leftArm.xRot = -1.5f + lowReadyAngle;
                rightArm.zRot = 0.0f;
                leftArm.zRot = 0.0f;
            }
        }
    }
}
