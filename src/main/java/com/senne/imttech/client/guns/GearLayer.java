package com.senne.imttech.client.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.PartState;
import java.util.List;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Wat je draagt, op je lijf (hoofdstuk 52, stap 2): de holster op je rechterheup (met het pistool erin, de loop naar
 * beneden), de granaatpouch op je linkerheup en de magazijnpouch achter op je rechterheup. Voor anderen, en voor jezelf
 * met F5.
 */
public final class GearLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public GearLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new GearLayer(renderer));
            }
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        ItemStack holster = Gear.get(player, GearItem.Kind.HOLSTER);
        ItemStack grenades = Gear.get(player, GearItem.Kind.GRENADE_POUCH);
        ItemStack magazines = Gear.get(player, GearItem.Kind.MAG_POUCH);
        if (holster.isEmpty() && grenades.isEmpty() && magazines.isEmpty()) {
            return;
        }
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        if (player.isCrouching()) {
            pose.translate(0, 0.05, 0);
        }
        if (!holster.isEmpty()) {
            pose.pushPose();
            // Rechterheup (in de ruimte van het model is rechts -x, en +y omlaag).
            pose.translate(-0.34, 0.6, 0.0);
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.mulPose(Axis.YP.rotationDegrees(90));
            pose.scale(0.9f, 0.9f, 0.9f);
            // Het pistool erin: de loop naar beneden, de greep boven de holster uit.
            List<ItemStack> inside = GearItem.contents(holster);
            ItemStack gun = inside.isEmpty() ? ItemStack.EMPTY : inside.getFirst();
            FirearmType type = FirearmItem.typeOf(gun);
            if (type != null) {
                pose.pushPose();
                pose.translate(0, 0.14, 0);
                pose.mulPose(Axis.XP.rotationDegrees(-90));
                pose.scale(0.55f, 0.55f, 0.55f);
                pose.translate(-0.5, -0.45, -0.47);
                GunParts parts = FirearmItem.parts(gun);
                for (GunPart part : new GunPart[]{GunPart.FRAME, GunPart.SLIDE, GunPart.TRIGGER}) {
                    PartState state = parts.get(part);
                    if (state != null) {
                        FirearmModels.render(pose, buffers, FirearmModels.part(type, part, state.material()), light, OverlayTexture.NO_OVERLAY);
                    }
                }
                if (FirearmItem.hasMagazine(gun)) {
                    FirearmModels.render(pose, buffers, FirearmModels.magazine(type), light, OverlayTexture.NO_OVERLAY);
                }
                pose.popPose();
            }
            pose.translate(-0.5, -0.5, -0.5);
            FirearmModels.render(pose, buffers, FirearmModels.location("holster"), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        if (!grenades.isEmpty()) {
            pose.pushPose();
            pose.translate(0.33, 0.6, 0.0);
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.mulPose(Axis.YP.rotationDegrees(-90));
            pose.scale(0.8f, 0.8f, 0.8f);
            pose.translate(-0.5, -0.5, -0.5);
            FirearmModels.render(pose, buffers, FirearmModels.location("grenade_pouch"), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        if (!magazines.isEmpty()) {
            pose.pushPose();
            pose.translate(-0.12, 0.6, 0.3);
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.scale(0.7f, 0.7f, 0.7f);
            pose.translate(-0.5, -0.5, -0.5);
            FirearmModels.render(pose, buffers, FirearmModels.location("mag_pouch"), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }
}
