package com.senne.imttech.client.drone;

import com.senne.imttech.ImtTech;
import com.senne.imttech.drone.DroneDamage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * Het 3D-model van een drone: een quadcopter met een romp, twee gekruiste armen,
 * vier draaiende rotors en een camera aan de voorkant.
 *
 * Maten zijn in pixels (16 pixels = 1 blok). Minecraft-modellen staan "op hun kop":
 * y = 24 is de grond en kleinere y is hoger.
 *
 * Een beschadigde drone ziet er ook zo uit (zie {@link #setDamage}): een gebogen rotor, dan
 * een rotor die ontbreekt en een hangende camera, en op het laatst nog maar twee rotors en een
 * romp die wiebelt. De roetvlekken en de rook komen van DroneRenderer en DroneDamage.
 */
public class DroneModel<T extends Entity> extends EntityModel<T> {
    // Het model weet niet welke drone het tekent: je eigen drones en de vijandige raiddrones
    // zijn dezelfde machine en gebruiken hetzelfde model, alleen met andere texturen.
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "drone"), "main");

    /** Afstand van het midden tot een rotor (6 pixels arm, schuin: 6 / wortel 2). */
    private static final float ROTOR_OFFSET = 4.24f;

    /** De hele drone (alles hangt aan dit deel). */
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart camera;
    private final ModelPart armB;
    private final ModelPart[] rotors = new ModelPart[4];

    /** Hoe kapot de drone die we nu tekenen is: 0 = heel, 1 = op. */
    private float damage;
    /** Een getal dat per drone anders is: zo gaat niet bij elke drone dezelfde rotor stuk. */
    private int seed;

    public DroneModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.camera = body.getChild("camera");
        this.armB = body.getChild("arm_b");
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

    /**
     * Hoe kapot de volgende drone is die we tekenen. Voor drones in de wereld doet setupAnim
     * dit zelf (uit hun leven); het arsenaal-scherm roept het aan voor drones in hun baan.
     *
     * @param damage 0 = heel, 1 = op
     * @param seed   een getal dat per drone anders is (bijvoorbeeld uit zijn UUID)
     */
    public void setDamage(float damage, int seed) {
        this.damage = Mth.clamp(damage, 0.0f, 1.0f);
        this.seed = seed;
    }

    /**
     * Wordt elke frame aangeroepen om de drone te laten bewegen.
     * "ageInTicks" loopt op met de tijd; daarmee laten we hem zweven en de rotors draaien.
     */
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        if (entity instanceof LivingEntity living) {
            setDamage(DroneDamage.of(living), entity.getUUID().hashCode());
        } else {
            setDamage(0, 0);
        }
        pose(ageInTicks);
    }

    /**
     * Zet alle onderdelen goed voor dit moment: zweven, rotors draaien, en de schade.
     *
     * Het model wordt gedeeld door alle drones, dus we beginnen elke keer vanaf de
     * uitgangshouding. Anders hield de volgende drone de gebroken rotor van de vorige.
     */
    public void pose(float ageInTicks) {
        root.getAllParts().forEach(part -> {
            part.resetPose();
            part.visible = true;
        });

        // Een beetje op en neer zweven; een kapotte drone schokt er wat bij.
        body.y = 20 + Mth.sin(ageInTicks * 0.15f) * 0.5f + Mth.sin(ageInTicks * 1.7f) * 0.35f * damage;
        // Rotors draaien snel, om en om de andere kant op. Hoe kapotter, hoe trager.
        float speed = 1.3f * (1.0f - 0.3f * damage);
        for (int i = 0; i < rotors.length; i++) {
            float direction = i % 2 == 0 ? 1 : -1;
            rotors[i].yRot = ageInTicks * speed * direction + i;
        }

        int first = Math.floorMod(seed, 4);      // de rotor die het eerst stukgaat
        int bent = (first + 1) % 4;              // die buigt al eerder krom
        int second = (first + 2) % 4;            // en die gaat als tweede
        DroneDamage.Stage stage = DroneDamage.stage(damage);
        if (stage.atLeast(DroneDamage.Stage.SCRATCHED)) {
            // Een kromme rotor die slingert.
            rotors[bent].xRot = 0.35f + Mth.sin(ageInTicks * 0.8f) * 0.1f;
            rotors[bent].zRot = -0.2f;
        }
        if (stage.atLeast(DroneDamage.Stage.BROKEN)) {
            // Een rotor weg, een arm verbogen, de camera hangt aan zijn draadjes.
            rotors[first].visible = false;
            armB.zRot = 0.18f;
            camera.xRot = 0.5f + Mth.sin(ageInTicks * 0.5f) * 0.1f;
        }
        if (stage.atLeast(DroneDamage.Stage.WRECKED)) {
            // Nog maar twee rotors: de romp kan zich niet recht houden.
            rotors[second].visible = false;
            body.zRot = Mth.sin(ageInTicks * 0.9f) * 0.12f;
            body.xRot = 0.1f + Mth.sin(ageInTicks * 0.6f) * 0.06f;
            camera.xRot = 0.9f;
        }
    }

    /** Tekent het model (in 1.21.1 doet het model dat zelf). */
    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
                               com.mojang.blaze3d.vertex.VertexConsumer buffer, int light, int overlay, int color) {
        root.render(poseStack, buffer, light, overlay, color);
    }
}
