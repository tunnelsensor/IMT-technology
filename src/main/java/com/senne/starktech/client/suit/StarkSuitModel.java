package com.senne.starktech.client.suit;

import com.senne.starktech.StarkTech;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.suit.RocketPackItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Het 3D-model van het Stark-harnas.
 *
 * Een gewoon harnas in Minecraft is een doosje om elk lichaamsdeel, net iets groter dan het
 * lichaamsdeel zelf. Dit model is dat ook, maar met extra doosjes erop die meebewegen:
 * - borst: een reactor die uitsteekt, twee borstplaten, drie buikplaten, een rugplaat met
 *   twee straalopeningen;
 * - armen: schouderplaten, onderarmbeschermers, en een repulsor in de handpalm;
 * - laarzen: een dikkere schaal, een gouden neus en een zool met een straalbuis;
 * - broek: kniestukken, dijplaten aan de buitenkant en een gesp aan de riem;
 * - met een raketpakket: twee raketbuizen op de rug, die alleen zichtbaar zijn als de
 *   borstplaat er een heeft (zie registerExtensions).
 *
 * Er zijn twee modellen, net als in Minecraft: het BUITENSTE voor borst en laarzen, en het
 * BINNENSTE (iets strakker) voor de broek, zodat die niet door de borstplaat heen steekt.
 *
 * Het plaatje is 128x64: links boven het gewone harnas, de rest voor de extra stukken. Die
 * worden gemaakt door tools/generate_textures.py; de plekken (texOffs) moeten daar precies mee
 * kloppen. Maten in pixels: een blok is 16 pixels.
 */
public final class StarkSuitModel {
    private StarkSuitModel() {}

    public static final ModelLayerLocation OUTER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "stark_suit"), "outer");
    public static final ModelLayerLocation INNER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "stark_suit"), "inner");

    private static HumanoidModel<LivingEntity> outer;
    private static HumanoidModel<LivingEntity> inner;

    /** Het model voor dit stuk harnas (de broek krijgt het binnenste). */
    public static HumanoidModel<LivingEntity> forSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.LEGS ? inner() : outer();
    }

    public static HumanoidModel<LivingEntity> outer() {
        if (outer == null) {
            outer = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(OUTER));
        }
        return outer;
    }

    private static HumanoidModel<LivingEntity> inner() {
        if (inner == null) {
            inner = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(INNER));
        }
        return inner;
    }

    /**
     * Vertel Minecraft dat de drie stukken harnas dit model gebruiken in plaats van het gewone
     * harnasmodel. (Minecraft neemt zelf de houding van je lichaam over.)
     */
    public static void registerExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                                                          HumanoidModel<?> original) {
                HumanoidModel<LivingEntity> model = forSlot(slot);
                if (slot == EquipmentSlot.CHEST) {
                    // Hetzelfde model tekent elke borstplaat: per keer aan- of uitzetten.
                    boolean pods = RocketPackItem.installedOn(stack);
                    model.body.getChild("rocket_pod_right").visible = pods;
                    model.body.getChild("rocket_pod_left").visible = pods;
                }
                return model;
            }
        }, ModItems.STARK_CHESTPLATE.get(), ModItems.STARK_LEGGINGS.get(), ModItems.STARK_BOOTS.get());
    }

    // ================================================================ Borst en laarzen

    public static LayerDefinition createOuter() {
        // Het gewone buitenste harnas: 1 pixel om je heen (de benen 0.9).
        MeshDefinition mesh = HumanoidArmorModel.createBodyLayer(new CubeDeformation(1.0f));
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.getChild("body");
        // De reactor steekt een pixel voor de borstplaat uit, precies op het blauwe rondje.
        body.addOrReplaceChild("reactor", CubeListBuilder.create().texOffs(64, 0)
                .addBox(-1.5f, 1.5f, -4f, 3, 3, 1), PartPose.ZERO);
        body.addOrReplaceChild("chest_right", CubeListBuilder.create().texOffs(72, 0)
                .addBox(-4f, 0.5f, -3.5f, 3, 4, 1), PartPose.ZERO);
        body.addOrReplaceChild("chest_left", CubeListBuilder.create().texOffs(72, 0).mirror()
                .addBox(1f, 0.5f, -3.5f, 3, 4, 1), PartPose.ZERO);
        for (int i = 0; i < 3; i++) {
            body.addOrReplaceChild("abs_" + i, CubeListBuilder.create().texOffs(80, 0)
                    .addBox(-3f, 6.3f + i * 1.6f, -3.4f, 6, 1, 1), PartPose.ZERO);
        }
        body.addOrReplaceChild("back_plate", CubeListBuilder.create().texOffs(96, 0)
                .addBox(-3f, 1f, 2.5f, 6, 7, 1), PartPose.ZERO);
        body.addOrReplaceChild("vent_right", CubeListBuilder.create().texOffs(112, 0)
                .addBox(-2.5f, 8f, 2.6f, 2, 2, 1), PartPose.ZERO);
        body.addOrReplaceChild("vent_left", CubeListBuilder.create().texOffs(112, 0).mirror()
                .addBox(0.5f, 8f, 2.6f, 2, 2, 1), PartPose.ZERO);
        // Raketbuizen op de rug (raketpakket): schuin naar achteren en een beetje naar buiten.
        body.addOrReplaceChild("rocket_pod_right", CubeListBuilder.create().texOffs(96, 48)
                .addBox(-1.5f, -5f, -1.5f, 3, 5, 3), PartPose.offsetAndRotation(-3.2f, 2.5f, 5f, -0.25f, 0, 0.2f));
        body.addOrReplaceChild("rocket_pod_left", CubeListBuilder.create().texOffs(96, 48).mirror()
                .addBox(-1.5f, -5f, -1.5f, 3, 5, 3), PartPose.offsetAndRotation(3.2f, 2.5f, 5f, -0.25f, 0, -0.2f));

        // Armen: het midden van de rechterarm ligt 1 pixel naar buiten (x = -1), van de linker
        // 1 pixel de andere kant op (x = +1).
        arm(root.getChild("right_arm"), -1, false);
        arm(root.getChild("left_arm"), 1, true);
        boot(root.getChild("right_leg"), false);
        boot(root.getChild("left_leg"), true);
        return LayerDefinition.create(mesh, 128, 64);
    }

    private static void arm(PartDefinition arm, float centreX, boolean mirror) {
        // Het schouderstuk kantelt een beetje naar buiten, zoals een schouderplaat.
        arm.addOrReplaceChild("pauldron", mirrored(CubeListBuilder.create().texOffs(64, 16), mirror)
                .addBox(centreX - 3.5f, -3.8f, -3.5f, 7, 3, 7), PartPose.rotation(0, 0, centreX * 0.12f));
        arm.addOrReplaceChild("gauntlet", mirrored(CubeListBuilder.create().texOffs(92, 16), mirror)
                .addBox(centreX - 3.5f, 6.5f, -3.5f, 7, 3, 7), PartPose.ZERO);
        arm.addOrReplaceChild("repulsor", mirrored(CubeListBuilder.create().texOffs(64, 28), mirror)
                .addBox(centreX - 1.5f, 10.5f, -1.5f, 3, 1, 3), PartPose.ZERO);
    }

    private static void boot(PartDefinition leg, boolean mirror) {
        leg.addOrReplaceChild("boot_shell", mirrored(CubeListBuilder.create().texOffs(64, 36), mirror)
                .addBox(-3.5f, 7.5f, -3.5f, 7, 4, 7), PartPose.ZERO);
        leg.addOrReplaceChild("boot_toe", mirrored(CubeListBuilder.create().texOffs(92, 36), mirror)
                .addBox(-3f, 10f, -4f, 6, 3, 1), PartPose.ZERO);
        leg.addOrReplaceChild("boot_sole", mirrored(CubeListBuilder.create().texOffs(64, 48), mirror)
                .addBox(-3.5f, 12f, -3.5f, 7, 1, 7), PartPose.ZERO);
    }

    // ================================================================ Broek

    public static LayerDefinition createInner() {
        // Het gewone binnenste harnas: een halve pixel om je heen (de benen 0.4).
        MeshDefinition mesh = HumanoidArmorModel.createBodyLayer(new CubeDeformation(0.5f));
        PartDefinition root = mesh.getRoot();
        root.getChild("body").addOrReplaceChild("buckle", CubeListBuilder.create().texOffs(88, 0)
                .addBox(-1.5f, 9.5f, -3.2f, 3, 2, 1), PartPose.ZERO);
        leg(root.getChild("right_leg"), -1, false);
        leg(root.getChild("left_leg"), 1, true);
        return LayerDefinition.create(mesh, 128, 64);
    }

    /** {@code outside}: -1 voor het rechterbeen (buiten is -x), +1 voor het linker. */
    private static void leg(PartDefinition leg, int outside, boolean mirror) {
        leg.addOrReplaceChild("knee", mirrored(CubeListBuilder.create().texOffs(64, 0), mirror)
                .addBox(-2f, 4f, -3.2f, 4, 3, 1), PartPose.ZERO);
        float x = outside < 0 ? -3.2f : 2.2f;
        leg.addOrReplaceChild("thigh", mirrored(CubeListBuilder.create().texOffs(76, 0), mirror)
                .addBox(x, 0.5f, -2f, 1, 4, 4), PartPose.ZERO);
    }

    private static CubeListBuilder mirrored(CubeListBuilder builder, boolean mirror) {
        return mirror ? builder.mirror() : builder;
    }
}
