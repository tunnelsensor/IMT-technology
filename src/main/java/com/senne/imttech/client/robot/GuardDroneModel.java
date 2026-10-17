package com.senne.imttech.client.robot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.ImtTech;
import com.senne.imttech.bot.BotModule;
import com.senne.imttech.robot.GuardDroneEntity;
import com.senne.imttech.robot.RobotState;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Het 3D-model van een security drone (hoofdstuk 25): een platte romp met een dakje, twee gekruiste armen met vier
 * rotors, en twee landingsski's eronder. Wat er op zijn printplaat gesoldeerd is, zie je ook (net als bij een
 * robot): een radarkoepel (Scan), een cyaan lensstrook vooraan (Face Recognition), een rood sensoroogje (Distance),
 * een antenne (Pathfinding), een laserloop rechts onder zijn buik (Laser), een geweerloop links (Gun), twee
 * zonnevleugels (Solar Panel) en een camera onder zijn neus (Camera).
 *
 * Maten zijn in pixels (16 pixels = 1 blok). Minecraft-modellen staan "op hun kop": y = 24 is de grond en kleinere
 * y is hoger; de neus wijst naar -z. De textuur (64 x 64) maakt tools/generate_textures.py (guard_drone_textures).
 */
public class GuardDroneModel extends EntityModel<GuardDroneEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "guard_drone"), "main");

    /** Afstand van het midden tot een rotor (8 pixels arm, schuin: 8 / wortel 2). */
    private static final float ROTOR_OFFSET = 5.66f;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart[] rotors = new ModelPart[4];
    private final Map<BotModule, ModelPart> modules = new EnumMap<>(BotModule.class);

    public GuardDroneModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        for (int i = 0; i < 4; i++) {
            rotors[i] = body.getChild("rotor_" + i);
        }
        for (BotModule module : BotModule.values()) {
            if (body.hasChild("mod_" + module.id())) {
                modules.put(module, body.getChild("mod_" + module.id()));
            }
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Romp: 8 x 4 x 8 pixels, met een dakje van 6 x 1 x 6 erop.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4, -2, -4, 8, 4, 8)
                        .texOffs(0, 12).addBox(-3, -3, -3, 6, 1, 6),
                PartPose.offset(0, 19, 0));

        // Twee gekruiste armen, 16 pixels lang.
        body.addOrReplaceChild("arm_a",
                CubeListBuilder.create().texOffs(0, 20).addBox(-8, -0.5f, -0.5f, 16, 1, 1),
                PartPose.rotation(0, (float) Math.PI / 4, 0));
        body.addOrReplaceChild("arm_b",
                CubeListBuilder.create().texOffs(0, 22).addBox(-8, -0.5f, -0.5f, 16, 1, 1),
                PartPose.rotation(0, -(float) Math.PI / 4, 0));

        // Vier rotors aan de uiteinden: een naaf met twee gekruiste bladen.
        float[][] corners = {
                {ROTOR_OFFSET, ROTOR_OFFSET}, {-ROTOR_OFFSET, ROTOR_OFFSET},
                {-ROTOR_OFFSET, -ROTOR_OFFSET}, {ROTOR_OFFSET, -ROTOR_OFFSET}};
        for (int i = 0; i < 4; i++) {
            body.addOrReplaceChild("rotor_" + i,
                    CubeListBuilder.create()
                            .texOffs(36, 20).addBox(-1, -1, -1, 2, 2, 2)           // naaf
                            .texOffs(36, 24).addBox(-3.5f, -1.5f, -0.5f, 7, 1, 1)  // blad 1
                            .texOffs(36, 26).addBox(-0.5f, -1.5f, -3.5f, 1, 1, 7), // blad 2
                    PartPose.offset(corners[i][0], -1, corners[i][1]));
        }

        // Twee landingsski's onder de romp.
        body.addOrReplaceChild("skids",
                CubeListBuilder.create()
                        .texOffs(0, 26).addBox(-3, 2, -3.5f, 1, 2, 7)
                        .texOffs(0, 26).addBox(2, 2, -3.5f, 1, 2, 7),
                PartPose.ZERO);

        // De modules (alleen te zien als ze op zijn printplaat zitten).
        body.addOrReplaceChild("mod_" + BotModule.SCAN.id(),
                CubeListBuilder.create().texOffs(40, 36).addBox(-1.5f, -5, 0.5f, 3, 2, 3), PartPose.ZERO);
        body.addOrReplaceChild("mod_" + BotModule.FACE_RECOGNITION.id(),
                CubeListBuilder.create().texOffs(0, 36).addBox(-3, -1, -4.5f, 6, 1, 1), PartPose.ZERO);
        body.addOrReplaceChild("mod_" + BotModule.DISTANCE.id(),
                CubeListBuilder.create().texOffs(16, 36).addBox(-1, 0.5f, -5, 2, 1, 1), PartPose.ZERO);
        body.addOrReplaceChild("mod_" + BotModule.PATHFINDING.id(),
                CubeListBuilder.create().texOffs(24, 36).addBox(1.5f, -7, 2, 1, 4, 1), PartPose.ZERO);
        body.addOrReplaceChild("mod_" + BotModule.LASER.id(),
                CubeListBuilder.create().texOffs(0, 40).addBox(-3, 2, -6, 1, 1, 6), PartPose.ZERO);
        body.addOrReplaceChild("mod_" + BotModule.GUN.id(),
                CubeListBuilder.create().texOffs(16, 40).addBox(1, 2, -6, 2, 1, 6), PartPose.ZERO);
        body.addOrReplaceChild("mod_" + BotModule.SOLAR_PANEL.id(),
                CubeListBuilder.create()
                        .texOffs(0, 48).addBox(4, -2, -3, 4, 1, 6)
                        .texOffs(20, 48).addBox(-8, -2, -3, 4, 1, 6),
                PartPose.ZERO);
        body.addOrReplaceChild("mod_" + BotModule.CAMERA.id(),
                CubeListBuilder.create().texOffs(32, 40).addBox(-1, 2, -4, 2, 2, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * Elke frame: zweven, rotors draaien (aan, of hij vliegt), en welke modules er te zien zijn. Het model wordt
     * gedeeld door alle drones, dus we beginnen elke keer vanaf de uitgangshouding.
     */
    @Override
    public void setupAnim(GuardDroneEntity drone, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        RobotState state = drone.state();
        boolean moving = drone.getX() != drone.xo || drone.getY() != drone.yo || drone.getZ() != drone.zo;
        boolean spinning = state == RobotState.ACTIVE || state == RobotState.STANDBY && moving;
        if (spinning) {
            // Een beetje op en neer zweven, en de rotors snel rond (om en om de andere kant op).
            body.y = 19 + Mth.sin(ageInTicks * 0.15f) * 0.4f;
            for (int i = 0; i < rotors.length; i++) {
                rotors[i].yRot = ageInTicks * 1.3f * (i % 2 == 0 ? 1 : -1) + i;
            }
        } else {
            for (int i = 0; i < rotors.length; i++) {
                rotors[i].yRot = i * 0.4f; // stil, elk een beetje anders
            }
        }
        int soldered = drone.board().modules();
        for (Map.Entry<BotModule, ModelPart> entry : modules.entrySet()) {
            entry.getValue().visible = (soldered & 1 << entry.getKey().ordinal()) != 0;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
        root.render(poseStack, buffer, light, overlay, color);
    }
}
