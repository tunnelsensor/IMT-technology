package com.senne.imttech.client.weapons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.weapons.EmpGrenadeEntity;
import com.senne.imttech.weapons.RollingEmpGrenade;
import com.senne.imttech.weapons.StickyEmpGrenade;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Tekent de twee EMP-granaten (hoofdstuk 35) met hun 3D-modellen (zie WeaponModels).
 *
 * De bol draait mee met het rollen (RollingEmpGrenade.spin). De sticky ligt met zijn kleefkussen plat tegen wat hij
 * raakte, met de bolle kant naar buiten; in de lucht vliegt hij met het kussen naar voren. Het lampje (het deel "light")
 * tekenen we alleen als het aan is: zo knippert het.
 */
public class EmpGrenadeRenderer extends EntityRenderer<EmpGrenadeEntity> {
    public EmpGrenadeRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.12f;
    }

    @Override
    public void render(EmpGrenadeEntity grenade, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        pose.pushPose();
        String name;
        if (grenade instanceof RollingEmpGrenade ball) {
            name = "emp_grenade";
            pose.translate(0, RollingEmpGrenade.RADIUS, 0);
            pose.mulPose(new Quaternionf(ball.spinO).slerp(ball.spin, partialTick));
            pose.translate(-0.5, -0.5, -0.5);
        } else {
            name = "sticky_emp_grenade";
            Vec3 up = grenade instanceof StickyEmpGrenade sticky && sticky.isStuck() ? sticky.normal()
                    : grenade.getDeltaMovement().lengthSqr() > 1.0E-4 ? grenade.getDeltaMovement().normalize().reverse()
                    : new Vec3(0, 1, 0);
            pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, (float) up.x, (float) up.y, (float) up.z));
            // Het kussen ligt in het model op 5 pixels hoogte, in het midden (8, 8).
            pose.translate(-0.5, -5 / 16.0, -0.5);
        }
        WeaponModels.render(pose, buffers, name, "body", light, OverlayTexture.NO_OVERLAY);
        if (grenade.lightOn()) {
            WeaponModels.render(pose, buffers, name, "light", light, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
        super.render(grenade, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(EmpGrenadeEntity grenade) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
