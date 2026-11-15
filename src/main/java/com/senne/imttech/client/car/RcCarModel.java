package com.senne.imttech.client.car;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.ImtTech;
import com.senne.imttech.car.RcCarEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Het 3D-model van een RC Car (hoofdstuk 108): een laag onderstel op vier wielen, een motorkap met twee koplampen,
 * een open laadbak achterop (zijn kofferbak), een antenne, en vooraan een boor in vier trapjes. De wielen draaien als
 * hij rijdt, de boor draait als hij boort. Sinds hoofdstuk 113 zit de boor op een arm: staat die omhoog (arm_up()), dan
 * schuift de boor een of twee blokken omhoog langs een mast die meegroeit.
 *
 * Maten zijn in pixels (16 pixels = 1 blok). Minecraft-modellen staan "op hun kop": y = 24 is de grond en kleinere y
 * is hoger; de neus wijst naar -z. De auto is 12 pixels breed (met wielen) en past dus in een gang van één blok. De
 * punt van zijn boor steekt een pixel in het blok voor hem: daar boort hij. De textuur (64 x 64) maakt
 * tools/generate_textures.py (rc_car_textures).
 */
public class RcCarModel extends EntityModel<RcCarEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "rc_car"), "main");

    private final ModelPart root;
    private final ModelPart[] wheels = new ModelPart[4];
    private final ModelPart drill;
    private final ModelPart mast;

    public RcCarModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        for (int i = 0; i < 4; i++) {
            wheels[i] = body.getChild("wheel_" + i);
        }
        drill = body.getChild("drill");
        mast = body.getChild("mast");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Het onderstel (8 x 3 x 10, twee pixels boven de grond), de laadbak achterop, de motorkap, de antenne en de
        // twee koplampen. Het draaipunt van dit deel ligt op de grond, in het midden.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4, -5, -4, 8, 3, 10)
                        .texOffs(0, 14).addBox(-3, -8, 0, 6, 3, 6)
                        .texOffs(26, 14).addBox(-3, -6, -4, 6, 1, 4)
                        .texOffs(48, 14).addBox(2, -13, 4, 1, 5, 1)
                        .texOffs(48, 22).addBox(-4, -5, -5, 2, 1, 1)
                        .texOffs(48, 22).addBox(2, -5, -5, 2, 1, 1),
                PartPose.offset(0, 24, 0));

        // Vier wielen (2 breed, 4 hoog en lang), die om hun as draaien.
        float[][] axles = {{-5, -2}, {5, -2}, {-5, 4}, {5, 4}};
        for (int i = 0; i < 4; i++) {
            body.addOrReplaceChild("wheel_" + i,
                    CubeListBuilder.create().texOffs(0, 26).addBox(-1, -2, -2, 2, 4, 4),
                    PartPose.offset(axles[i][0], -2, axles[i][1]));
        }

        // De boor: vier trapjes die naar voren smaller worden, draaiend om de lengte-as.
        body.addOrReplaceChild("drill",
                CubeListBuilder.create()
                        .texOffs(14, 26).addBox(-2, -2, -2, 4, 4, 2)
                        .texOffs(28, 26).addBox(-1.5f, -1.5f, -4, 3, 3, 2)
                        .texOffs(40, 26).addBox(-1, -1, -5, 2, 2, 1)
                        .texOffs(50, 26).addBox(-0.5f, -0.5f, -6, 1, 1, 1),
                PartPose.offset(0, -3.5f, -4));

        // De mast van de arm (hoofdstuk 113): een paal van één blok hoog achter de boor, die het model uitrekt tot waar
        // de boor staat (zie setupAnim). Zijn draaipunt zit onderaan, op de hoogte van de boor als de arm beneden is.
        body.addOrReplaceChild("mast",
                CubeListBuilder.create().texOffs(0, 40).addBox(-1, -16, -1, 2, 16, 2),
                PartPose.offset(0, -3.5f, -3));

        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * De wielen draaien met de afstand die hij reed (limbSwing loopt mee met zijn beweging), de boor draait zolang hij
     * boort.
     */
    @Override
    public void setupAnim(RcCarEntity car, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        for (ModelPart wheel : wheels) {
            wheel.xRot = limbSwing * 2.0f;
        }
        drill.zRot = car.isDrilling() ? ageInTicks * 1.3f : 0.0f;
        // De arm (hoofdstuk 113): de boor schuift vloeiend omhoog (16 pixels per blok; kleinere y is hoger), en de mast
        // rekt mee. Met de arm beneden is de mast er niet.
        float arm = net.minecraft.util.Mth.lerp(ageInTicks - car.tickCount, car.armShownO, car.armShown);
        drill.y = -3.5f - 16.0f * arm;
        mast.visible = arm > 0.02f;
        mast.yScale = Math.max(0.001f, arm);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
