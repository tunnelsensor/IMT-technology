package com.senne.imttech.client.weapons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.GrenadeEntity;
import com.senne.imttech.weapons.RollingGrenade;
import com.senne.imttech.weapons.StickyGrenade;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Tekent de granaten (hoofdstuk 35) met hun 3D-modellen (zie WeaponModels).
 *
 * Een bol (EMP, smoke, flash) draait mee met het rollen (RollingGrenade.spin). De sticky ligt met zijn kleefkussen plat
 * tegen wat hij raakte, met de bolle kant naar buiten; in de lucht vliegt hij met het kussen naar voren. Het lampje van
 * een EMP-granaat (het deel "light") tekenen we alleen als het aan is: zo knippert het. Een smokebom krijgt een band in
 * de kleur van zijn rook.
 */
public class GrenadeRenderer extends EntityRenderer<GrenadeEntity> {
    public GrenadeRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.12f;
    }

    @Override
    public void render(GrenadeEntity grenade, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        pose.pushPose();
        String name;
        ItemStack tint = ItemStack.EMPTY;
        if (grenade instanceof RollingGrenade ball) {
            name = switch (ball.kind()) {
                case EMP -> "emp_grenade";
                case FRAG -> "frag_grenade";
                case SMOKE -> "smoke_grenade";
                case FLASH -> "flashbang";
            };
            if (ball.kind() == RollingGrenade.Kind.SMOKE) {
                tint = new ItemStack(ModItems.SMOKE_GRENADE.get());
                if (ball.smokeColor() != null) {
                    tint.set(ModDataComponents.SMOKE_COLOR.get(), ball.smokeColor());
                }
            }
            pose.translate(0, RollingGrenade.RADIUS, 0);
            pose.mulPose(new Quaternionf(ball.spinO).slerp(ball.spin, partialTick));
            pose.translate(-0.5, -0.5, -0.5);
        } else {
            name = grenade instanceof StickyGrenade sticky && sticky.isFrag() ? "sticky_frag_grenade" : "sticky_emp_grenade";
            Vec3 up = grenade instanceof StickyGrenade sticky && sticky.isStuck() ? sticky.normal()
                    : grenade.getDeltaMovement().lengthSqr() > 1.0E-4 ? grenade.getDeltaMovement().normalize().reverse()
                    : new Vec3(0, 1, 0);
            pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, (float) up.x, (float) up.y, (float) up.z));
            // Het kussen ligt in het model op 5 pixels hoogte, in het midden (8, 8).
            pose.translate(-0.5, -5 / 16.0, -0.5);
        }
        WeaponModels.render(pose, buffers, name, "body", light, OverlayTexture.NO_OVERLAY, tint);
        if (grenade.lightOn()) {
            WeaponModels.render(pose, buffers, name, "light", light, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
        super.render(grenade, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(GrenadeEntity grenade) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
