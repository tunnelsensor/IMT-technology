package com.senne.imttech.client.arsenal;

import com.senne.imttech.satellite.SatelliteUpgrades;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * De Biebia's Industries-satelliet: het ruimtestation waar je drones op wachten, en waar de satelliet-aanval
 * vandaan komt. Een romp met een reactor voorop, twee zonnepanelen, een schotel met antenne
 * bovenop en een motor achteraan.
 *
 * Naarmate je hem upgradet, komen er onderdelen bij (zie {@link #showUpgrades}):
 * - precisie-straal: een kanon onder de romp, met focusringen en op het laatst een gloeiende punt;
 * - bombardement: raketkokers, eerst op de romp en later onder de zonnepanelen;
 * - schuivende laser: een brede lens achter het kanon, met vinnen en een tweede lens;
 * - opladen: langere zonnepanelen;
 * - kracht: condensatoren op de romp en een grote reactor voorop;
 * - richten: een tweede antenne, een grotere schotel en een sensor onder de neus.
 *
 * Maten in pixels, net als bij DroneModel (16 pixels = 1 blok). De texture maakt
 * tools/generate_textures.py (gui/arsenal/satellite.png, 128 x 64).
 */
public final class SatelliteModel {
    private SatelliteModel() {}

    /** Zo breed is de satelliet zonder langere zonnepanelen (in pixels). */
    public static final float BASE_WIDTH = 52;
    /** En zo breed met de langste zonnepanelen. */
    public static final float MAX_WIDTH = 96;

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition core = root.addOrReplaceChild("core",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4, -4, -5, 8, 8, 10)       // romp
                        .texOffs(40, 0).addBox(-2, -2, -6, 4, 4, 1)       // reactor voorop
                        .texOffs(36, 32).addBox(-2, -2, 5, 4, 4, 3)       // motor achteraan
                        .texOffs(40, 8).addBox(-8, -1, -1, 4, 2, 2)       // steun links
                        .texOffs(40, 8).addBox(4, -1, -1, 4, 2, 2),       // steun rechts
                PartPose.ZERO);

        // ---- Zonnepanelen (opladen): elk verlengstuk hangt aan het vorige.
        PartDefinition left = core.addOrReplaceChild("panel_left",
                CubeListBuilder.create().texOffs(0, 20).addBox(-18, -0.5f, -4, 18, 1, 8),
                PartPose.offset(-8, 0, 0));
        PartDefinition right = core.addOrReplaceChild("panel_right",
                CubeListBuilder.create().texOffs(0, 20).addBox(0, -0.5f, -4, 18, 1, 8),
                PartPose.offset(8, 0, 0));
        PartDefinition leftExt = left.addOrReplaceChild("ext",
                CubeListBuilder.create().texOffs(64, 8).addBox(-11, -0.5f, -4, 10, 1, 8)
                        .texOffs(40, 8).addBox(-1.5f, -0.5f, -0.5f, 2, 1, 1),   // scharnier
                PartPose.offset(-18, 0, 0));
        leftExt.addOrReplaceChild("ext",
                CubeListBuilder.create().texOffs(64, 8).addBox(-11, -0.5f, -4, 10, 1, 8)
                        .texOffs(40, 8).addBox(-1.5f, -0.5f, -0.5f, 2, 1, 1),
                PartPose.offset(-11, 0, 0));
        PartDefinition rightExt = right.addOrReplaceChild("ext",
                CubeListBuilder.create().texOffs(64, 8).addBox(1, -0.5f, -4, 10, 1, 8)
                        .texOffs(40, 8).addBox(-0.5f, -0.5f, -0.5f, 2, 1, 1),
                PartPose.offset(18, 0, 0));
        rightExt.addOrReplaceChild("ext",
                CubeListBuilder.create().texOffs(64, 8).addBox(1, -0.5f, -4, 10, 1, 8)
                        .texOffs(40, 8).addBox(-0.5f, -0.5f, -0.5f, 2, 1, 1),
                PartPose.offset(11, 0, 0));

        // ---- De schotel bovenop, iets schuin naar de aarde, met een antenne (richten).
        PartDefinition dish = core.addOrReplaceChild("dish", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0, -5, 1, -0.5f, 0, 0));
        dish.addOrReplaceChild("small",
                CubeListBuilder.create().texOffs(0, 32).addBox(-3.5f, -0.5f, -3.5f, 7, 1, 7), PartPose.ZERO);
        dish.addOrReplaceChild("big",
                CubeListBuilder.create().texOffs(64, 20).addBox(-5, -0.5f, -5, 10, 1, 10), PartPose.ZERO);
        dish.addOrReplaceChild("antenna",
                CubeListBuilder.create().texOffs(30, 32).addBox(-0.5f, -5, -0.5f, 1, 5, 1), PartPose.ZERO);
        core.addOrReplaceChild("antenna_2",
                CubeListBuilder.create().texOffs(104, 20).addBox(-0.5f, -6, -0.5f, 1, 6, 1),
                PartPose.offset(2.5f, -4, 3.5f));
        core.addOrReplaceChild("sensor",
                CubeListBuilder.create().texOffs(108, 20).addBox(-1.5f, 0, -1.5f, 3, 2, 3),
                PartPose.offset(0, 4, -4));

        // ---- Het kanon onder de romp (precisie-straal), wijst naar de aarde.
        PartDefinition cannon = core.addOrReplaceChild("cannon",
                CubeListBuilder.create()
                        .texOffs(80, 0).addBox(-1.5f, 0, -1.5f, 3, 3, 3)   // huis
                        .texOffs(92, 0).addBox(-1, 3, -1, 2, 5, 2),        // loop
                PartPose.offset(0, 4, 0));
        cannon.addOrReplaceChild("ring_1",
                CubeListBuilder.create().texOffs(100, 0).addBox(-1.5f, 4, -1.5f, 3, 1, 3), PartPose.ZERO);
        cannon.addOrReplaceChild("ring_2",
                CubeListBuilder.create().texOffs(100, 0).addBox(-1.5f, 6, -1.5f, 3, 1, 3), PartPose.ZERO);
        cannon.addOrReplaceChild("tip",
                CubeListBuilder.create().texOffs(112, 0).addBox(-0.5f, 8, -0.5f, 1, 1, 1), PartPose.ZERO);

        // ---- Raketkokers (bombardement), telkens een paar erbij.
        core.addOrReplaceChild("pods_1", pods(5, 2.5f), PartPose.ZERO);
        core.addOrReplaceChild("pods_2", pods(5, -2.5f), PartPose.ZERO);
        core.addOrReplaceChild("pods_3", pods(12, 1.5f), PartPose.ZERO);
        core.addOrReplaceChild("pods_4", pods(19, 1.5f), PartPose.ZERO);

        // ---- De brede lens van de schuivende laser, achter het kanon.
        PartDefinition sweep = core.addOrReplaceChild("sweep",
                CubeListBuilder.create().texOffs(64, 32).addBox(-3, 4, 2, 6, 2, 3), PartPose.ZERO);
        sweep.addOrReplaceChild("fins",
                CubeListBuilder.create()
                        .texOffs(84, 32).addBox(3, 3, 1.5f, 1, 3, 4)
                        .texOffs(84, 32).addBox(-4, 3, 1.5f, 1, 3, 4),
                PartPose.ZERO);
        sweep.addOrReplaceChild("lens_2",
                CubeListBuilder.create().texOffs(110, 32).addBox(-2, 6, 2.5f, 4, 1, 2), PartPose.ZERO);

        // ---- Kracht: condensatoren langs de romp en een grote reactor voorop.
        core.addOrReplaceChild("coils_top",
                CubeListBuilder.create()
                        .texOffs(100, 8).addBox(3.5f, -5, -4, 2, 2, 8)
                        .texOffs(100, 8).addBox(-5.5f, -5, -4, 2, 2, 8),
                PartPose.ZERO);
        core.addOrReplaceChild("coils_bottom",
                CubeListBuilder.create()
                        .texOffs(100, 8).addBox(3.5f, 3, -4, 2, 2, 8)
                        .texOffs(100, 8).addBox(-5.5f, 3, -4, 2, 2, 8),
                PartPose.ZERO);
        core.addOrReplaceChild("reactor_big",
                CubeListBuilder.create().texOffs(96, 32).addBox(-3, -3, -7, 6, 6, 1), PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 64);
    }

    /** Twee raketkokers, links en rechts, {@code out} pixels uit het midden en {@code y} hoog. */
    private static CubeListBuilder pods(float out, float y) {
        return CubeListBuilder.create()
                .texOffs(64, 0).addBox(out - 1, y - 1, -3, 2, 2, 6)
                .texOffs(64, 0).addBox(-out - 1, y - 1, -3, 2, 2, 6);
    }

    /** Het gebakken model: klaar om te tekenen. */
    public static ModelPart bake() {
        return createLayer().bakeRoot();
    }

    /**
     * Zet de onderdelen aan of uit bij deze upgrades. Roep dit aan vlak voor het tekenen; het
     * model wordt gedeeld, dus wat je hier zet geldt tot de volgende keer.
     */
    public static void showUpgrades(ModelPart root, SatelliteUpgrades up) {
        ModelPart core = root.getChild("core");

        int recharge = up.recharge();
        core.getChild("panel_left").getChild("ext").visible = recharge >= 3;
        core.getChild("panel_right").getChild("ext").visible = recharge >= 3;
        core.getChild("panel_left").getChild("ext").getChild("ext").visible = recharge >= 7;
        core.getChild("panel_right").getChild("ext").getChild("ext").visible = recharge >= 7;

        int targeting = up.targeting();
        ModelPart dish = core.getChild("dish");
        dish.getChild("small").visible = targeting < 5;
        dish.getChild("big").visible = targeting >= 5;
        core.getChild("antenna_2").visible = targeting >= 2;
        core.getChild("sensor").visible = targeting >= 8;

        int precision = up.precision();
        ModelPart cannon = core.getChild("cannon");
        cannon.visible = precision >= 1;
        cannon.getChild("ring_1").visible = precision >= 4;
        cannon.getChild("ring_2").visible = precision >= 7;
        cannon.getChild("tip").visible = precision >= 10;

        int bombardment = up.bombardment();
        core.getChild("pods_1").visible = bombardment >= 1;
        core.getChild("pods_2").visible = bombardment >= 4;
        core.getChild("pods_3").visible = bombardment >= 7;
        core.getChild("pods_4").visible = bombardment >= 10;

        int sweep = up.sweep();
        ModelPart lens = core.getChild("sweep");
        lens.visible = sweep >= 1;
        lens.getChild("fins").visible = sweep >= 5;
        lens.getChild("lens_2").visible = sweep >= 9;

        int power = up.power();
        core.getChild("coils_top").visible = power >= 2;
        core.getChild("reactor_big").visible = power >= 5;
        core.getChild("coils_bottom").visible = power >= 8;
    }
}
