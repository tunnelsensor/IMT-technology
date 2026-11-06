package com.senne.imttech.client.shield;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.ImtTech;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtFeature;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;

/**
 * Het persoonlijke schild (G > Combat > Rayshield) te zien als een blauwe bol om de speler heen (hoofdstuk 54, jouw
 * notitie: "kun je de rayshield visueel maken zodat je ziet dat er een blauwe sphere rond is"). Dezelfde energielaag
 * als de koepel (het patroon glijdt er langzaam over), zacht kloppend, en zwakker als de bril bijna leeg is. Je ziet
 * hem bij anderen en bij jezelf in derde persoon (F5); in je eigen beeld niet, anders keek je er steeds doorheen.
 */
public final class PersonalShieldLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation FIELD = ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/misc/dome_field.png");
    private static final int RINGS = 12;
    private static final int SEGMENTS = 24;
    private static final int COLOUR = 0x3FD8FF;
    private static final float RADIUS = 1.05f;

    public PersonalShieldLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new PersonalShieldLayer(renderer));
            }
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack glasses = ImtData.getWornGlasses(player);
        if (glasses.isEmpty() || !ImtData.isEnabled(glasses, ImtFeature.SHIELD) || ImtData.getEnergy(glasses) <= 0
                || player.isInvisible() || com.senne.imttech.weapons.Emp.isJammed(player)) {
            return;
        }
        float time = player.tickCount + partialTick;
        float energy = ImtData.getEnergyFraction(glasses);
        // Zacht kloppen; bijna leeg flakkert hij.
        float alpha = 0.16f + 0.04f * Mth.sin(time * 0.12f);
        if (energy < 0.15f) {
            alpha *= 0.5f + 0.5f * Mth.abs(Mth.sin(time * 0.9f));
        }
        pose.pushPose();
        // Het model staat op zijn kop (y omlaag) met zijn nulpunt 1,5 blok boven de voeten: het midden van het lijf ligt
        // 0,95 blok boven de voeten, dus hier op y = 1,5 - 0,95.
        pose.translate(0, 1.501f - (player.isCrouching() ? 0.8f : 0.95f), 0);
        float radius = player.isCrouching() ? RADIUS * 0.9f : RADIUS;
        VertexConsumer out = buffers.getBuffer(RenderType.energySwirl(FIELD, time * 0.004f, time * 0.002f));
        sphere(pose.last().pose(), out, radius, COLOUR, alpha);
        pose.popPose();
    }

    /** Een bol van ringen (zoals de koepel), het patroon acht keer rondom en zes keer van boven naar beneden. */
    private static void sphere(Matrix4f m, VertexConsumer out, float radius, int rgb, float alpha) {
        float k = Mth.clamp(alpha, 0, 1);
        int r = (int) (((rgb >> 16) & 0xFF) * k);
        int g = (int) (((rgb >> 8) & 0xFF) * k);
        int b = (int) ((rgb & 0xFF) * k);
        for (int i = 0; i < RINGS; i++) {
            float t0 = (float) (Math.PI * i / RINGS);
            float t1 = (float) (Math.PI * (i + 1) / RINGS);
            for (int j = 0; j < SEGMENTS; j++) {
                float p0 = (float) (j * Math.PI * 2 / SEGMENTS);
                float p1 = (float) ((j + 1) * Math.PI * 2 / SEGMENTS);
                float u0 = j / (float) SEGMENTS * 8;
                float u1 = (j + 1) / (float) SEGMENTS * 8;
                float v0 = i / (float) RINGS * 6;
                float v1 = (i + 1) / (float) RINGS * 6;
                vertex(m, out, radius, t0, p0, r, g, b, u0, v0);
                vertex(m, out, radius, t0, p1, r, g, b, u1, v0);
                vertex(m, out, radius, t1, p1, r, g, b, u1, v1);
                vertex(m, out, radius, t1, p0, r, g, b, u0, v1);
            }
        }
    }

    private static void vertex(Matrix4f m, VertexConsumer out, float radius, float theta, float phi, int r, int g, int b,
                               float u, float v) {
        float x = (float) (Math.sin(theta) * Math.cos(phi)) * radius;
        float y = (float) Math.cos(theta) * radius;
        float z = (float) (Math.sin(theta) * Math.sin(phi)) * radius;
        out.addVertex(m, x, y, z).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(x / radius, y / radius, z / radius);
    }
}
