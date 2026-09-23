package com.senne.starktech.client.arsenal;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * De Stark-satelliet: het ruimtestation waar je drones op wachten, en waar de satelliet-aanval
 * vandaan komt. Een romp met een reactor voorop, twee zonnepanelen, een schotel met antenne
 * bovenop en een motor achteraan.
 *
 * Maten in pixels, net als bij DroneModel (16 pixels = 1 blok). De texture maakt
 * tools/generate_textures.py (gui/arsenal/satellite.png).
 */
public final class SatelliteModel {
    private SatelliteModel() {}

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
        // De zonnepanelen hangen apart, zodat ze naar de zon kunnen draaien.
        core.addOrReplaceChild("panel_left",
                CubeListBuilder.create().texOffs(0, 20).addBox(-18, -0.5f, -4, 18, 1, 8),
                PartPose.offset(-8, 0, 0));
        core.addOrReplaceChild("panel_right",
                CubeListBuilder.create().texOffs(0, 20).addBox(0, -0.5f, -4, 18, 1, 8),
                PartPose.offset(8, 0, 0));
        // De schotel bovenop, iets schuin naar de aarde, met een antenne in het midden.
        PartDefinition dish = core.addOrReplaceChild("dish",
                CubeListBuilder.create().texOffs(0, 32).addBox(-3.5f, -0.5f, -3.5f, 7, 1, 7),
                PartPose.offsetAndRotation(0, -5, 1, -0.5f, 0, 0));
        dish.addOrReplaceChild("antenna",
                CubeListBuilder.create().texOffs(30, 32).addBox(-0.5f, -5, -0.5f, 1, 5, 1),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Het gebakken model: klaar om te tekenen. */
    public static ModelPart bake() {
        return createLayer().bakeRoot();
    }
}
