package com.senne.imttech.client.cuffs;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * Twee armhoudingen voor de handboeien (hoofdstuk 54, jouw notitie): wie geboeid is heeft zijn handen op zijn rug (met
 * de boeien ertussen, zie CuffRenderer), en wie iemand vasthoudt heeft allebei zijn handen vooruit, aan die boeien.
 * Net als LaserCannonArmPose: Minecraft kent een vaste lijst armhoudingen, NeoForge laat ons er twee bij zetten via
 * resources/META-INF/enumextensions.json.
 */
public final class CuffArmPoses {
    private CuffArmPoses() {}

    public static final EnumProxy<HumanoidModel.ArmPose> CUFFED = new EnumProxy<>(HumanoidModel.ArmPose.class,
            false, (IArmPoseTransformer) CuffArmPoses::cuffed);
    public static final EnumProxy<HumanoidModel.ArmPose> HOLDING = new EnumProxy<>(HumanoidModel.ArmPose.class,
            true, (IArmPoseTransformer) CuffArmPoses::holding);

    /** De handen op de rug, bij elkaar. */
    private static void cuffed(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        part.xRot = 0.55f;
        part.yRot = 0.55f * side; // naar het midden (de arm hangt naar achteren: dan draait een positieve yRot naar binnen)
        part.zRot = 0.08f * side;
    }

    /** Allebei de handen vooruit, iets omlaag, bij elkaar: aan de boeien van wie voor je staat. */
    private static void holding(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        part.xRot = -1.05f;
        part.yRot = -0.3f * side;
        part.zRot = 0;
    }
}
