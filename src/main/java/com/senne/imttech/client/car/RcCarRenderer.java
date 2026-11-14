package com.senne.imttech.client.car;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.ImtTech;
import com.senne.imttech.car.DrillHead;
import com.senne.imttech.car.RcCarEntity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Tekent een RC Car (hoofdstuk 108): het model (zie RcCarModel), en daarover zijn lichtjes, die ook in het donker
 * gloeien: twee koplampen en een lampje boven op zijn antenne. Zonder energiecel, of met een lege, zijn ze uit: zo zie
 * je van ver of hij nog stroom heeft. Zijn boor heeft de kleur van zijn boorkop (ijzer, steel, titanium of netherite):
 * elke kop is een eigen textuur (zie tools/generate_textures.py, rc_car_textures).
 */
public class RcCarRenderer extends MobRenderer<RcCarEntity, RcCarModel> {
    /** De textuur per boorkop: rc_car.png is de ijzeren kop, de andere heten rc_car_steel.png enzovoort. */
    private static final Map<DrillHead, ResourceLocation> TEXTURES = textures();
    private static final RenderType GLOW = RenderType.eyes(texture("rc_car_glow"));

    public RcCarRenderer(EntityRendererProvider.Context context) {
        super(context, new RcCarModel(context.bakeLayer(RcCarModel.LAYER)), 0.35f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }

            /** Alleen met stroom: zonder cel (of met een lege) zijn zijn lichtjes uit. */
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffers, int light, RcCarEntity car,
                               float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                               float netHeadYaw, float headPitch) {
                if (car.batteryFraction() > 0) {
                    super.render(poseStack, buffers, light, car, limbSwing, limbSwingAmount, partialTick, ageInTicks,
                            netHeadYaw, headPitch);
                }
            }
        });
    }

    private static Map<DrillHead, ResourceLocation> textures() {
        Map<DrillHead, ResourceLocation> map = new EnumMap<>(DrillHead.class);
        for (DrillHead head : DrillHead.values()) {
            map.put(head, texture(head == DrillHead.IRON ? "rc_car" : "rc_car_" + head.getSerializedName()));
        }
        return map;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(RcCarEntity car) {
        return TEXTURES.get(car.drill());
    }
}
