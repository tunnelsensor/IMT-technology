package com.senne.starktech.client.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.starktech.StarkTech;
import com.senne.starktech.arsenal.DroneDockBlock;
import com.senne.starktech.arsenal.DroneDockBlockEntity;
import com.senne.starktech.drone.DroneDamage;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Tekent de drone die op een drone-dock staat: het echte 3D-model, net boven het platform, met
 * langzaam draaiende rotors en zijn schade (roet, een kapotte rotor).
 *
 * - Net geland: hij zakt de laatste drie blokken naar beneden.
 * - Net vertrokken: hij schiet omhoog en is weg.
 * - Op pad: een doorschijnend blauw hologram houdt zijn plek vrij.
 */
public class DroneDockRenderer implements BlockEntityRenderer<DroneDockBlockEntity> {
    private static final ResourceLocation DRONE = texture("stark_drone");
    private static final ResourceLocation LIGHTS = texture("stark_drone_lights_guard");
    /** Zoveel ticks duurt landen en opstijgen. */
    private static final int LAND_TICKS = 20;
    private static final int LIFT_TICKS = 14;
    /** Zo hoog boven het platform zweeft een drone die er staat (het platform is 4 pixels hoog). */
    private static final float DOCK_TOP = 4 / 16.0f;
    private static final float HOVER = 0.12f;

    private final DroneModel<Entity> model;

    public DroneDockRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new DroneModel<>(context.bakeLayer(DroneModel.LAYER));
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/entity/drone/" + name + ".png");
    }

    @Override
    public void render(DroneDockBlockEntity dock, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (!dock.hasDrone() || dock.getLevel() == null) {
            return;
        }
        float now = dock.getLevel().getGameTime() + partialTick;
        float sinceMove = now - dock.movedAt();
        float damage = 1.0f - dock.health();
        int seed = dock.droneSeed();
        int light = LevelRenderer.getLightColor(dock.getLevel(), dock.getBlockPos().above());

        float lift;
        float rotorSpeed;
        boolean ghost = false;
        float ghostAlpha = 1.0f;
        if (dock.present()) {
            // Staat er: zweeft rustig. Net geland? Dan zakt hij nog de laatste blokken.
            float u = Mth.clamp(sinceMove / LAND_TICKS, 0, 1);
            lift = 3.0f * (1 - u) * (1 - u);
            rotorSpeed = u < 1 ? 1.0f : 0.12f;
        } else if (sinceMove < LIFT_TICKS) {
            // Net vertrokken: schiet omhoog.
            float u = sinceMove / LIFT_TICKS;
            lift = 5.0f * u * u;
            rotorSpeed = 1.0f;
        } else {
            // Op pad: een hologram dat langzaam draait en na het vertrek opkomt.
            ghost = true;
            ghostAlpha = Mth.clamp((sinceMove - LIFT_TICKS) / 30.0f, 0, 1);
            lift = 0;
            rotorSpeed = 0.05f;
        }

        pose.pushPose();
        pose.translate(0.5, DOCK_TOP + HOVER + lift, 0.5);
        float facing = dock.getBlockState().hasProperty(DroneDockBlock.FACING)
                ? dock.getBlockState().getValue(DroneDockBlock.FACING).toYRot() : 0;
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing + (ghost ? now * 1.5f : 0)));
        // Zoals een entity: het model staat op zijn kop, en zijn "grond" ligt 24 pixels lager.
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.5, 0);
        model.setDamage(ghost ? 0 : damage, seed);
        model.pose((now + Math.floorMod(seed, 100)) * rotorSpeed);

        if (ghost) {
            int alpha = (int) (90 * ghostAlpha);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(DRONE)), light,
                    OverlayTexture.NO_OVERLAY, alpha << 24 | 0x50C8FF);
            pose.popPose();
            return;
        }
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(DRONE)), light,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        ResourceLocation soot = DamageLayer.overlay(damage);
        if (soot != null) {
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(soot)), light,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
        if (!DroneDamage.lightsFlickerOff(damage, now, seed)) {
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(LIGHTS)), light,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
        pose.popPose();
    }

    /** Een opstijgende drone gaat tot vijf blokken boven de dock: dan moet hij ook daar nog getekend worden. */
    @Override
    public AABB getRenderBoundingBox(DroneDockBlockEntity dock) {
        return new AABB(dock.getBlockPos()).expandTowards(0, 6, 0);
    }
}
