package com.senne.imttech.client.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.client.suit.SuitEffects;
import com.senne.imttech.guns.BulletEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Een kogel in de lucht (hoofdstuk 52): hij vliegt zo snel dat je hem alleen als een streepje ziet, een "tracer": een
 * dun geel-wit streepje van waar hij net was naar waar hij nu is, altijd naar je toe gedraaid.
 */
public class BulletRenderer extends EntityRenderer<BulletEntity> {
    public BulletRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public void render(BulletEntity bullet, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 velocity = bullet.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 cam = entityRenderDispatcher.camera.getPosition();
        Vec3 at = bullet.getPosition(partialTick);
        Vec3 dir = velocity.normalize();
        double length = Math.min(0.9, velocity.length() * 0.06);
        Vec3 tail = at.subtract(dir.scale(length));
        pose.pushPose();
        // We tekenen in de ruimte van de wereld rond de kogel: de pose staat al op zijn plek.
        Matrix4f m = pose.last().pose();
        VertexConsumer out = buffers.getBuffer(SuitEffects.GLOW);
        Vec3 a = tail.subtract(at);
        Vec3 b = Vec3.ZERO;
        line(m, out, a, b, cam.subtract(at), 0.05f, 0xFFB850, 0.55f);
        line(m, out, a.scale(0.5), b, cam.subtract(at), 0.02f, 0xFFF4D0, 0.95f);
        pose.popPose();
    }

    /** Een streep tussen twee punten die naar de camera gedraaid is (zoals de laserbouten). */
    static void line(Matrix4f m, VertexConsumer out, Vec3 a, Vec3 b, Vec3 cam, float width, int rgb, float alpha) {
        Vec3 dir = b.subtract(a);
        Vec3 side = dir.cross(a.subtract(cam));
        if (side.lengthSqr() < 1.0E-8) {
            side = dir.cross(new Vec3(0, 1, 0));
        }
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        side = side.normalize().scale(width);
        SuitEffects.quad(m, out, a.add(side), b.add(side), b.subtract(side), a.subtract(side), rgb, alpha);
        SuitEffects.quad(m, out, a.subtract(side), b.subtract(side), b.add(side), a.add(side), rgb, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(BulletEntity bullet) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
