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
import net.minecraft.util.Mth;

/**
 * Het 3D-model van een RC Car (hoofdstuk 108): een laag onderstel op vier wielen, een motorkap met twee koplampen,
 * een open laadbak achterop (zijn kofferbak), een antenne, en vooraan een boor in vier trapjes. De wielen draaien als
 * hij rijdt, de boor draait als hij boort.
 *
 * Met een RC Robot Arm (hoofdstuk 118; sinds hoofdstuk 113 heette dat gewoon "de arm") staat er achter de boor een gele
 * behuizing: zo zie je van ver dat hij een arm heeft. Staat de arm omhoog (arm_up()), dan schuift de boor een of twee
 * blokken omhoog langs een mast die uit die behuizing groeit. Boort hij het blok boven of onder zich weg (mine_up(),
 * mine_down()), dan kantelt de boor die kant op. Zonder arm zit de boor vast op zijn neus, zoals op een nieuwe auto.
 *
 * Maten zijn in pixels (16 pixels = 1 blok). Minecraft-modellen staan "op hun kop": y = 24 is de grond en kleinere y
 * is hoger; de neus wijst naar -z. De auto is 12 pixels breed (met wielen) en past dus in een gang van één blok. De
 * punt van zijn boor steekt een pixel in het blok voor hem: daar boort hij. De textuur (64 x 64) maakt
 * tools/generate_textures.py (rc_car_textures).
 */
public class RcCarModel extends EntityModel<RcCarEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "rc_car"), "main");

    /** Zo hoog (pixels) staat het midden van de boor boven de grond als de arm beneden is. */
    private static final float DRILL_HEIGHT = 3.5f;
    /** Zo ver (pixels) gaat de boor omhoog om het blok boven de auto te raken: zijn punt komt dan net bij dat blok. */
    private static final float UP_REACH = 6.5f;

    private final ModelPart root;
    private final ModelPart[] wheels = new ModelPart[4];
    /** Waar de boor op zit: dit deel gaat omhoog met de arm en kantelt naar boven of onder. */
    private final ModelPart drillMount;
    /** De boor zelf: die draait om zijn eigen lengte-as, ook als hij gekanteld is. */
    private final ModelPart drill;
    private final ModelPart mast;
    private final ModelPart armBase;

    public RcCarModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        for (int i = 0; i < 4; i++) {
            wheels[i] = body.getChild("wheel_" + i);
        }
        drillMount = body.getChild("drill_mount");
        drill = drillMount.getChild("drill");
        mast = body.getChild("mast");
        armBase = body.getChild("arm_base");
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

        // De boor: vier trapjes die naar voren smaller worden. Twee delen in elkaar: de houder (gaat omhoog met de arm en
        // kantelt), en daarin de boor (draait om zijn lengte-as). Zo blijft hij om zijn eigen as draaien als hij naar
        // boven of onder wijst.
        PartDefinition mount = body.addOrReplaceChild("drill_mount", CubeListBuilder.create(),
                PartPose.offset(0, -DRILL_HEIGHT, -4));
        mount.addOrReplaceChild("drill",
                CubeListBuilder.create()
                        .texOffs(14, 26).addBox(-2, -2, -2, 4, 4, 2)
                        .texOffs(28, 26).addBox(-1.5f, -1.5f, -4, 3, 3, 2)
                        .texOffs(40, 26).addBox(-1, -1, -5, 2, 2, 1)
                        .texOffs(50, 26).addBox(-0.5f, -0.5f, -6, 1, 1, 1),
                PartPose.ZERO);

        // De mast van de arm (hoofdstuk 113): een paal van één blok hoog achter de boor, die het model uitrekt tot waar
        // de boor staat (zie setupAnim). Zijn draaipunt zit onderaan, op de hoogte van de boor als de arm beneden is.
        body.addOrReplaceChild("mast",
                CubeListBuilder.create().texOffs(0, 40).addBox(-1, -16, -1, 2, 16, 2),
                PartPose.offset(0, -DRILL_HEIGHT, -3));

        // De behuizing van de RC Robot Arm (hoofdstuk 118): een geel blok achter de boor (4 breed, 6 hoog, 3 diep), waar
        // de mast uit groeit. Je ziet hem alleen als de auto een arm heeft.
        body.addOrReplaceChild("arm_base",
                CubeListBuilder.create().texOffs(12, 40).addBox(-2, -9, -4, 4, 6, 3),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * De wielen draaien met de afstand die hij reed (limbSwing loopt mee met zijn beweging), de boor draait zolang hij
     * boort. Met een arm: de boor op de hoogte van de arm, of gekanteld naar het blok boven of onder hem.
     */
    @Override
    public void setupAnim(RcCarEntity car, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        for (ModelPart wheel : wheels) {
            wheel.xRot = limbSwing * 2.0f;
        }
        drill.zRot = car.isDrilling() ? ageInTicks * 1.3f : 0.0f;
        float partial = ageInTicks - car.tickCount;
        // De arm (hoofdstuk 113): de boor schuift vloeiend omhoog (16 pixels per blok; kleinere y is hoger), en de mast
        // rekt mee. Kantelen (hoofdstuk 118): naar boven gaat de boor ook een stukje omhoog, tot zijn punt het blok boven
        // de auto raakt; naar onder blijft hij op zijn plek en wijst zijn punt in de grond.
        // Zonder arm (ook net na Arm off) staat de boor meteen op zijn neus: anders zakte hij nog even zonder mast terug
        // (gevonden door de review).
        boolean hasArm = car.hasRobotArm();
        float arm = hasArm ? Mth.lerp(partial, car.armShownO, car.armShown) : 0.0f;
        float tilt = hasArm ? Mth.lerp(partial, car.tiltShownO, car.tiltShown) : 0.0f;
        float lift = Mth.lerp(Math.abs(tilt), 16.0f * arm, tilt > 0 ? UP_REACH : 0.0f);
        drillMount.y = -DRILL_HEIGHT - lift;
        drillMount.xRot = -tilt * Mth.HALF_PI;
        armBase.visible = hasArm;
        mast.visible = hasArm && lift > 0.3f;
        mast.yScale = Math.max(0.001f, lift / 16.0f);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
