package com.senne.imttech.client.suit;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * Een nieuwe armhouding voor spelers: de arm recht vooruit, waar je naartoe kijkt, zoals Iron Man
 * die zijn laser cannon richt. Na een schot schiet de arm even omhoog (terugslag).
 *
 * Minecraft kent maar een vaste lijst armhoudingen (een "enum"). NeoForge laat mods er één
 * toevoegen: dat staat in resources/META-INF/enumextensions.json, en die wijst naar het veld
 * POSE hieronder. Deze klasse is zo klein mogelijk, want hij wordt al geladen terwijl Minecraft
 * die lijst opbouwt.
 */
public final class LaserCannonArmPose {
    private LaserCannonArmPose() {}

    public static final EnumProxy<HumanoidModel.ArmPose> POSE = new EnumProxy<>(HumanoidModel.ArmPose.class,
            false, (IArmPoseTransformer) LaserCannonArmPose::apply);

    private static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        // Terugslag: vlak na een schot wipt de arm omhoog en zakt dan terug.
        float since = SuitEffects.sinceFired(entity, 0);
        float kick = since < 6 ? (1 - since / 6) * (1 - since / 6) : 0;
        // Recht vooruit (een kwartslag), mee met je hoofd, iets naar binnen gericht.
        part.xRot = -Mth.HALF_PI + model.head.xRot - kick * 0.5f;
        part.yRot = model.head.yRot - 0.12f * side;
        part.zRot = 0;
    }
}
