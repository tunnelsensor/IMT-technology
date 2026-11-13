package com.senne.imttech.client.weapons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.LaserPart;
import com.senne.imttech.weapons.LaserParts;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Een lasergeweer tekenen uit zijn onderdelen (hoofdstuk 55), stilstaand: wat erin zit op zijn plek, in zijn eigen
 * kwaliteit, de kern als hij erin zit, de cel als er een in zit, en (als je wilt) de sling. Voor de Laser Workbench, de
 * holster op je heup en de sling op je rug (zie GearLayer); in je eigen beeld tekent GunRenderer de onderdelen zelf,
 * met hun animaties. De hoek (0, 0, 0) van het model staat waar {@code pose} nu staat, en 1 blok = 16 pixels van het
 * model (zoals WeaponModels.render).
 */
public final class LaserGunModels {
    private LaserGunModels() {}

    /** Alle onderdelen van dit geweer op hun plek, behalve {@code skip} (het onderdeel dat je net van de tafel sleept). */
    public static void render(PoseStack pose, MultiBufferSource buffers, ItemStack gun, int light, boolean withSling,
                              @Nullable LaserPart skip) {
        render(pose, buffers, gun, light, withSling, skip, null);
    }

    /**
     * Hetzelfde, zonder het attachment op de plek {@code skipSlot} (dat sleep je net weg op de Laser Workbench).
     * Hoofdstuk 103, stap 7: zit er een kijker op, dan tekenen we het eigen vizier van het geweer (het onderdeel Optics)
     * niet: de kijker staat op zijn plek.
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, ItemStack gun, int light, boolean withSling,
                              @Nullable LaserPart skip, @Nullable com.senne.imttech.guns.AttachmentSlot skipSlot) {
        GunType type = LaserGunItem.typeOf(gun);
        if (type == null) {
            return;
        }
        com.senne.imttech.guns.Attachment optic = com.senne.imttech.guns.FirearmItem.sight(gun);
        com.senne.imttech.guns.Attachment under = com.senne.imttech.guns.FirearmItem.attachment(gun,
                com.senne.imttech.guns.AttachmentSlot.UNDERBARREL);
        GunRig rig = GunRig.of(type);
        if (optic != null && skipSlot != com.senne.imttech.guns.AttachmentSlot.SIGHT) {
            pose.pushPose();
            pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
            com.senne.imttech.client.guns.Optics.render(pose, buffers, rig.seat(optic), optic, light, 0, -1);
            pose.popPose();
        }
        if (under != null && rig.underMount != null && skipSlot != com.senne.imttech.guns.AttachmentSlot.UNDERBARREL) {
            pose.pushPose();
            pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
            com.senne.imttech.client.guns.AddOns.renderUnder(pose, buffers, rig.underMount, under, light);
            pose.popPose();
        }
        LaserParts parts = LaserGunItem.parts(gun);
        for (LaserPart part : LaserPart.wearing()) {
            PartState state = parts.get(part);
            if (state == null || part == skip || (part == LaserPart.OPTICS && optic != null)) {
                continue;
            }
            WeaponModels.render(pose, buffers, WeaponModels.part(type, part, state.material()), light, OverlayTexture.NO_OVERLAY);
            if (LaserRig.moves(type, part)) {
                WeaponModels.render(pose, buffers, WeaponModels.moving(type, part, state.material()), light, OverlayTexture.NO_OVERLAY);
            }
        }
        if (parts.hasCore() && skip != LaserPart.CORE) {
            WeaponModels.render(pose, buffers, type.id, "core", light, OverlayTexture.NO_OVERLAY);
        }
        if (withSling && parts.hasSling() && skip != LaserPart.SLING) {
            WeaponModels.render(pose, buffers, type.id, "sling", light, OverlayTexture.NO_OVERLAY);
        }
        if (LaserGunItem.cell(gun) >= 0) {
            WeaponModels.render(pose, buffers, type.id, "cell", light, OverlayTexture.NO_OVERLAY);
        }
    }

    /** Eén onderdeel (stilstaand deel plus bewegend deel) in deze kwaliteit, bijvoorbeeld om een los item te tonen. */
    public static void renderPart(PoseStack pose, MultiBufferSource buffers, GunType type, LaserPart part, PartState state, int light) {
        switch (part) {
            case CORE -> WeaponModels.render(pose, buffers, type.id, "core", light, OverlayTexture.NO_OVERLAY);
            case SLING -> WeaponModels.render(pose, buffers, type.id, "sling", light, OverlayTexture.NO_OVERLAY);
            default -> {
                WeaponModels.render(pose, buffers, WeaponModels.part(type, part, state.material()), light, OverlayTexture.NO_OVERLAY);
                if (LaserRig.moves(type, part)) {
                    WeaponModels.render(pose, buffers, WeaponModels.moving(type, part, state.material()), light, OverlayTexture.NO_OVERLAY);
                }
            }
        }
    }
}
