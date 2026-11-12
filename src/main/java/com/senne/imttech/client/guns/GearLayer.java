package com.senne.imttech.client.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.client.weapons.LaserGunModels;
import com.senne.imttech.client.weapons.WeaponModels;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.LaserGunItem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Wat je draagt, op je lijf (hoofdstuk 52, stap 2): de holster op je rechterheup (met het pistool erin, de loop naar
 * beneden), de granaatpouch op je linkerheup en de magazijnpouch achter op je rechterheup. Voor anderen, en voor jezelf
 * met F5.
 *
 * Sinds hoofdstuk 54 (jouw notitie: "je ziet niet of er iets in zit") zie je ook wat erin zit: de granaten steken met
 * hun bovenkant uit de vier open vakjes van de granaatpouch, de magazijnen uit de drie vakjes van de magazijnpouch.
 * Waar die vakjes zitten, staat in tools/generate_gun_models.py (GRENADE_POCKETS en MAG_POCKETS), in pixels van het
 * model van de pouch.
 *
 * Sinds hoofdstuk 55: in de holster kan ook het laserpistool zitten, in de magazijnpouch staan ook energiecellen, en
 * op je <b>rug</b> en voor je <b>borst</b> hangen de geweren uit je sling-vakjes (schuin, met hun sling eraan) met een
 * leren band over je schouder.
 *
 * Sinds hoofdstuk 100: je <b>keycard</b> aan een koordje om je nek, op je borst (zichtbaar, ook voor een zakkenroller).
 */
public final class GearLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Het midden van elk vakje van de granaatpouch (model-pixels). */
    private static final float[][] GRENADE_POCKETS = {{6.4f, 9.7f, 6.7f}, {9.6f, 9.7f, 6.7f}, {6.4f, 9.7f, 9.3f}, {9.6f, 9.7f, 9.3f}};
    /** Het midden van de voet van elk vakje van de magazijnpouch (model-pixels): twee rijen van drie (hoofdstuk 58). */
    private static final float[][] MAG_POCKETS = {
            {5.9f, 6.6f, 6.7f}, {8.0f, 6.6f, 6.7f}, {10.1f, 6.6f, 6.7f},
            {5.9f, 6.6f, 9.5f}, {8.0f, 6.6f, 9.5f}, {10.1f, 6.6f, 9.5f}};
    /** Zo groot is een granaat in de pouch (de bol van 6 pixels wordt er een van ruim 2,5), een magazijn, en een cel. */
    private static final float GRENADE_SCALE = 0.44f;
    private static final float MAG_SCALE = 0.62f;
    private static final float CELL_SCALE = 0.5f;
    /** Zo groot hangt een geweer op je rug of voor je borst (1 = 16 pixels van het model per blok). */
    private static final float SLING_SCALE = 0.5f;

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
        ItemStack back = Gear.sling(player, 0);
        ItemStack chest = Gear.sling(player, 1);
        ItemStack neckCard = Gear.neck(player);
        if (holster.isEmpty() && grenades.isEmpty() && magazines.isEmpty() && back.isEmpty() && chest.isEmpty()
                && neckCard.isEmpty()) {
            return;
        }
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        if (player.isCrouching()) {
            pose.translate(0, 0.05, 0);
        }
        if (!holster.isEmpty()) {
            renderHolster(pose, buffers, light, holster);
        }
        if (!grenades.isEmpty()) {
            renderGrenadePouch(pose, buffers, light, player, grenades);
        }
        if (!magazines.isEmpty()) {
            renderMagPouch(pose, buffers, light, player, magazines);
        }
        if (!back.isEmpty()) {
            // Hoofdstuk 72: met een rugzak hangt het geweer over de rugzak heen (jouw keuze).
            renderSlung(pose, buffers, light, back, true, com.senne.imttech.client.backpack.BackpackLayer.slingShift(player));
        }
        if (!chest.isEmpty()) {
            renderSlung(pose, buffers, light, chest, false, 0);
        }
        if (!neckCard.isEmpty()) {
            renderNeckCard(pose, buffers, light, player, neckCard);
        }
        pose.popPose();
    }

    /**
     * Hoofdstuk 100: de keycard aan je nek. Twee dunne koordjes van je nek schuin naar beneden, en de kaart plat op je borst
     * (de voorkant van je lijf is -z in de ruimte van het model, en +y is omlaag).
     */
    private static void renderNeckCard(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                       ItemStack card) {
        for (float side : new float[]{-1, 1}) {
            pose.pushPose();
            pose.translate(0.1 * side, -0.01, -0.135);
            pose.mulPose(Axis.ZP.rotationDegrees(-20 * side));
            pose.scale(0.45f, 0.3f, 0.5f);
            pose.translate(-0.5, 0, 0);
            WeaponModels.render(pose, buffers, WeaponModels.location("sling_strap"), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(0, 0.33, -0.15);
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.scale(0.3f, 0.3f, 0.3f);
        Minecraft.getInstance().getItemRenderer().renderStatic(card, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, player.level(), player.getId());
        pose.popPose();
    }

    /** De holster op je rechterheup, met het pistool erin: de loop naar beneden, de greep boven de holster uit. */
    private static void renderHolster(PoseStack pose, MultiBufferSource buffers, int light, ItemStack holster) {
        pose.pushPose();
        // Rechterheup (in de ruimte van het model is rechts -x, en +y omlaag).
        pose.translate(-0.34, 0.6, 0.0);
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.mulPose(Axis.YP.rotationDegrees(90));
        pose.scale(0.9f, 0.9f, 0.9f);
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
        } else if (LaserGunItem.typeOf(gun) != null) {
            // Het laserpistool (hoofdstuk 55): net zo in de holster, uit zijn onderdelen.
            pose.pushPose();
            pose.translate(0, 0.14, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.scale(0.55f, 0.55f, 0.55f);
            pose.translate(-0.5, -0.4, -0.45);
            LaserGunModels.render(pose, buffers, gun, light, false, null);
            pose.popPose();
        }
        pose.translate(-0.5, -0.5, -0.5);
        FirearmModels.render(pose, buffers, FirearmModels.location("holster"), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** De granaatpouch op je linkerheup, met de granaten in hun vakjes (de bovenkant steekt boven de rand uit). */
    private static void renderGrenadePouch(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                           ItemStack grenades) {
        pose.pushPose();
        pose.translate(0.33, 0.6, 0.0);
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.mulPose(Axis.YP.rotationDegrees(-90));
        pose.scale(0.8f, 0.8f, 0.8f);
        pose.translate(-0.5, -0.5, -0.5);
        FirearmModels.render(pose, buffers, FirearmModels.location("grenade_pouch"), light, OverlayTexture.NO_OVERLAY);
        List<ItemStack> inside = GearItem.contents(grenades);
        for (int i = 0; i < inside.size() && i < GRENADE_POCKETS.length; i++) {
            ItemStack grenade = inside.get(i);
            if (grenade.isEmpty()) {
                continue;
            }
            float[] at = GRENADE_POCKETS[i];
            pose.pushPose();
            pose.translate(at[0] / 16f, at[1] / 16f, at[2] / 16f);
            pose.mulPose(Axis.YP.rotationDegrees(35 + i * 50));
            pose.scale(GRENADE_SCALE, GRENADE_SCALE, GRENADE_SCALE);
            Minecraft.getInstance().getItemRenderer().renderStatic(grenade, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, player.level(), 0);
            pose.popPose();
        }
        pose.popPose();
    }

    /** De magazijnpouch achter op je rechterheup, met de magazijnen (en sinds hoofdstuk 55 de cellen) rechtop in hun vakjes. */
    private static void renderMagPouch(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                       ItemStack magazines) {
        pose.pushPose();
        pose.translate(-0.12, 0.6, 0.3);
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.scale(0.7f, 0.7f, 0.7f);
        pose.translate(-0.5, -0.5, -0.5);
        FirearmModels.render(pose, buffers, FirearmModels.location("mag_pouch"), light, OverlayTexture.NO_OVERLAY);
        List<ItemStack> inside = GearItem.contents(magazines);
        for (int i = 0; i < inside.size() && i < MAG_POCKETS.length; i++) {
            ItemStack stack = inside.get(i);
            float[] at = MAG_POCKETS[i];
            FirearmType type = MagazineItem.typeOf(stack);
            if (type != null) {
                pose.pushPose();
                pose.translate(at[0] / 16f, at[1] / 16f, at[2] / 16f);
                // Hoofdstuk 103: een lang magazijn (de MP5) iets kleiner, zodat het niet ver boven de pouch uitsteekt.
                float magScale = MAG_SCALE * FirearmRig.magazineFit(type);
                pose.scale(magScale / 16f, magScale / 16f, magScale / 16f);
                // Het magazijn staat in zijn model zoals in het wapen (bij de Glock schuin); hier rechtop, met het midden van
                // zijn bodemplaat op de voet van het vakje (FirearmRig.magazineUpright, hoofdstuk 74). Leeg = leeg getekend.
                pose.mulPose(FirearmRig.magazineUpright(type));
                pose.scale(16, 16, 16);
                FirearmModels.render(pose, buffers, FirearmModels.magazine(stack), light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            } else if (stack.is(ModItems.ENERGY_CELL.get())) {
                // Een energiecel: rechtop in het vakje, de gouden kap boven de rand uit.
                pose.pushPose();
                pose.translate(at[0] / 16f, at[1] / 16f, at[2] / 16f);
                pose.scale(CELL_SCALE, CELL_SCALE, CELL_SCALE);
                pose.translate(-0.5, -0.12, -0.5);
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                        pose, buffers, player.level(), 0);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /**
     * Een geweer uit een sling-vakje (hoofdstuk 55): schuin over je rug (de loop linksboven, de kolf rechtsonder) of
     * voor je borst (gespiegeld), met zijn sling eraan, en een leren band over je schouder, voor en achter.
     */
    private static void renderSlung(PoseStack pose, MultiBufferSource buffers, int light, ItemStack gun, boolean back, float shift) {
        float side = back ? 1 : -1;
        // De band over je schouder: van de ene schouder schuin naar de andere heup, aan beide kanten van je lijf.
        for (float z : new float[]{-0.215f, 0.215f}) {
            pose.pushPose();
            pose.translate(0.2 * side, -0.02, z);
            pose.mulPose(Axis.ZP.rotationDegrees(30 * side));
            pose.translate(-0.5, 0, z > 0 ? -0.5f / 16 : -0.0f);
            WeaponModels.render(pose, buffers, WeaponModels.location("sling_strap"), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        // Het geweer: plat tegen je rug (of borst), schuin.
        pose.pushPose();
        pose.translate(0, 0.36, 0.265 * side + shift);
        pose.mulPose(Axis.ZP.rotationDegrees(180 - 40 * side));
        pose.mulPose(Axis.YP.rotationDegrees(90 * side));
        FirearmType firearm = FirearmItem.typeOf(gun);
        if (firearm != null) {
            // Hoofdstuk 103: een lang vuurwapen (de MP5...) uit zijn onderdelen, om zijn eigen midden, op zijn eigen grootte.
            FirearmRig rig = FirearmRig.of(firearm);
            pose.scale(rig.slingScale, rig.slingScale, rig.slingScale);
            pose.translate(-rig.slingPivot.x / 16f, -rig.slingPivot.y / 16f, -rig.slingPivot.z / 16f);
            FirearmModels.renderParts(pose, buffers, gun, light, true, null);
            pose.popPose();
            return;
        }
        pose.scale(SLING_SCALE, SLING_SCALE, SLING_SCALE);
        pose.translate(-0.5, -0.62, -0.32);
        LaserGunModels.render(pose, buffers, gun, light, true, null);
        pose.popPose();
    }
}
