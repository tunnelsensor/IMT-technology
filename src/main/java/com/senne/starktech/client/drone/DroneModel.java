package com.senne.starktech.client.drone;

import com.senne.starktech.StarkTech;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Het 3D-model van een Stark-drone: een quadcopter met een romp, twee gekruiste armen,
 * vier draaiende rotors en een camera aan de voorkant.
 *
 * Maten zijn in pixels (16 pixels = 1 blok). Minecraft-modellen staan "op hun kop":
 * y = 24 is de grond en kleinere y is hoger.
 */
public class DroneModel extends EntityModel<DroneRenderState> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(StarkTech.MODID, "stark_drone"), "main");

    /** Afstand van het midden tot een rotor (6 pixels arm, schuin: 6 / wortel 2). */
    private static final float ROTOR_OFFSET = 4.24f;

    private final ModelPart body;
    private final ModelPart[] rotors = new ModelPart[4];

    public DroneModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        for (int i = 0; i < 4; i++) {
            rotors[i] = body.getChild("rotor_" + i);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Romp: 6 x 3 x 6 pixels
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3, -1.5f, -3, 6, 3, 6),
                PartPose.offset(0, 20, 0));

        // Camera/laser aan de voorkant onderaan
        body.addOrReplaceChild("camera",
                CubeListBuilder.create().texOffs(0, 10).addBox(-1, 1.5f, -3.5f, 2, 2, 2),
                PartPose.ZERO);

        // Twee gekruiste armen
        body.addOrReplaceChild("arm_a",
                CubeListBuilder.create().texOffs(0, 16).addBox(-6, -0.5f, -0.5f, 12, 1, 1),
                PartPose.rotation(0, (float) Math.PI / 4, 0));
        body.addOrReplaceChild("arm_b",
                CubeListBuilder.create().texOffs(0, 19).addBox(-6, -0.5f, -0.5f, 12, 1, 1),
                PartPose.rotation(0, -(float) Math.PI / 4, 0));

        // Vier rotors aan de uiteinden: een naaf met twee gekruiste bladen
        float[][] corners = {
                {ROTOR_OFFSET, ROTOR_OFFSET}, {-ROTOR_OFFSET, ROTOR_OFFSET},
                {-ROTOR_OFFSET, -ROTOR_OFFSET}, {ROTOR_OFFSET, -ROTOR_OFFSET}};
        for (int i = 0; i < 4; i++) {
            body.addOrReplaceChild("rotor_" + i,
                    CubeListBuilder.create()
                            .texOffs(28, 11).addBox(-1, -1, -1, 2, 2, 2)          // naaf
                            .texOffs(28, 0).addBox(-3, -1.5f, -0.5f, 6, 1, 1)     // blad 1
                            .texOffs(28, 3).addBox(-0.5f, -1.5f, -3, 1, 1, 6),    // blad 2
                    PartPose.offset(corners[i][0], -1, corners[i][1]));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(DroneRenderState state) {
        super.setupAnim(state);
        // Een beetje op en neer zweven.
        body.y = 20 + Mth.sin(state.ageInTicks * 0.15f) * 0.5f;
        // Rotors draaien snel, om en om de andere kant op.
        for (int i = 0; i < rotors.length; i++) {
            float direction = i % 2 == 0 ? 1 : -1;
            rotors[i].yRot = state.ageInTicks * 1.3f * direction + i;
        }
    }
}
