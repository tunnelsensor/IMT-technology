package com.senne.imttech.client.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.guns.Attachment;
import com.senne.imttech.guns.AttachmentSlot;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * De attachments die geen kijker zijn (hoofdstuk 103, stap 6): de demper op de loop, de greep of de tweepoot onder de
 * handbeschermer, en de verlengbuis van de Remington 870. Waar ze op elk wapen zitten staat in {@link FirearmRig}
 * ({@code muzzleSeat}, {@code underMount}, {@code tubeMount}); hun maten zijn die van tools/attachment_models.py. De
 * kijkers doet {@link Optics}.
 */
public final class AddOns {
    private AddOns() {}

    /** De scharnieren van de tweepoot (links en rechts van het midden, en onder de montage), zie BIPOD_HINGE in het script. */
    private static final float BIPOD_HINGE_X = 0.95f;
    private static final float BIPOD_HINGE_Y = -0.75f;
    /** Zo ver staan de poten uitgeklapt opzij (graden). */
    private static final float BIPOD_SPLAY = 18;

    /** Waar het achterste vlak van een demper op dit wapen zit: op de as van de loop, een stukje over de monding. */
    public static Vector3f muzzleMount(FirearmRig rig) {
        return new Vector3f(rig.muzzle).add(0, 0, rig.muzzleSeat);
    }

    /**
     * Tekent wat er op de loop, onder de handbeschermer en op de buis van dit wapen zit. {@code pose} staat in de pixels
     * van het wapen (zoals in FirearmRenderer). {@code bipod}: hoe ver de tweepoot uitgeklapt is (0 = de poten naar
     * voren langs de loop, 1 = schuin omlaag). {@code skip}: een plek die je net wegsleept (op de Gunsmith Table).
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, FirearmRig rig, ItemStack gun, int light, float bipod,
                              @Nullable AttachmentSlot skip) {
        Attachment muzzle = skip == AttachmentSlot.MUZZLE ? null : FirearmItem.attachment(gun, AttachmentSlot.MUZZLE);
        if (muzzle != null) {
            at(pose, buffers, muzzleMount(rig), muzzle.id, light);
        }
        Attachment under = skip == AttachmentSlot.UNDERBARREL ? null : FirearmItem.attachment(gun, AttachmentSlot.UNDERBARREL);
        if (under != null && rig.underMount != null) {
            at(pose, buffers, rig.underMount, under.id, light);
            if (under == Attachment.BIPOD) {
                float fold = 1 - bipod;
                for (int side = -1; side <= 1; side += 2) {
                    pose.pushPose();
                    pose.translate(rig.underMount.x + side * BIPOD_HINGE_X, rig.underMount.y + BIPOD_HINGE_Y, rig.underMount.z);
                    // Ingeklapt wijst een poot naar voren (langs de loop); uitgeklapt omlaag en een eindje opzij.
                    pose.mulPose(Axis.XP.rotationDegrees(90 * fold));
                    pose.mulPose(Axis.ZP.rotationDegrees(side * BIPOD_SPLAY * bipod));
                    pose.scale(16, 16, 16);
                    FirearmModels.render(pose, buffers, FirearmModels.location("attachment/bipod_leg"), light, OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
            }
        }
        Attachment tube = skip == AttachmentSlot.TUBE ? null : FirearmItem.attachment(gun, AttachmentSlot.TUBE);
        if (tube != null && rig.tubeMount != null) {
            at(pose, buffers, rig.tubeMount, tube.id, light);
        }
    }

    /** Eén model met zijn nulpunt op dit punt van het wapen. */
    private static void at(PoseStack pose, MultiBufferSource buffers, Vector3f mount, String model, int light) {
        pose.pushPose();
        pose.translate(mount.x, mount.y, mount.z);
        pose.scale(16, 16, 16);
        FirearmModels.render(pose, buffers, FirearmModels.location("attachment/" + model), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /**
     * Waar je linkerhand een lang wapen vasthoudt: onder de handbeschermer ({@link FirearmRig#support}), of om de greep
     * als er een op zit.
     */
    public static Vector3f support(FirearmRig rig, ItemStack gun) {
        Attachment under = FirearmItem.attachment(gun, AttachmentSlot.UNDERBARREL);
        if (under == null || rig.underMount == null) {
            return new Vector3f(rig.support);
        }
        return switch (under) {
            case VERTICAL_GRIP -> new Vector3f(rig.underMount).add(0, -3.2f, 0.2f);
            case ANGLED_GRIP -> new Vector3f(rig.underMount).add(0, -1.9f, 1.0f);
            default -> new Vector3f(rig.support);
        };
    }

    // ================================================================ De Gunsmith Table

    /**
     * Het vak van deze plek op het wapen (model-pixels): om wat erop zit, of (leeg) om wat er meestal op komt.
     * {@code on}: wat er nu zit, of null.
     */
    public static AABB bounds(FirearmType type, AttachmentSlot slot, @Nullable Attachment on) {
        FirearmRig rig = FirearmRig.of(type);
        switch (slot) {
            case MUZZLE -> {
                Vector3f m = muzzleMount(rig);
                Attachment can = on != null ? on : type.caliber == com.senne.imttech.guns.Caliber.MM9 ? Attachment.SUPPRESSOR_9MM
                        : type.tube() ? Attachment.SUPPRESSOR_SHOTGUN : Attachment.SUPPRESSOR_RIFLE;
                if (can == Attachment.SUPPRESSOR_SHOTGUN) {
                    return new AABB(m.x - 1.45, m.y - 2.7, m.z - 14.0, m.x + 1.45, m.y + 1.0, m.z + 0.25);
                }
                double length = can == Attachment.SUPPRESSOR_RIFLE ? 15.5 : 11.5;
                double radius = can == Attachment.SUPPRESSOR_RIFLE ? 1.5 : 1.2;
                return new AABB(m.x - radius, m.y - radius, m.z - length, m.x + radius, m.y + radius, m.z);
            }
            case UNDERBARREL -> {
                Vector3f u = rig.underMount == null ? new Vector3f(rig.support) : rig.underMount;
                Attachment thing = on != null ? on : Attachment.BIPOD.fits(type) ? Attachment.BIPOD : Attachment.VERTICAL_GRIP;
                return switch (thing) {
                    // De tweepoot ingeklapt: de poten naar voren.
                    case BIPOD -> new AABB(u.x - 1.3, u.y - 1.3, u.z - 15.2, u.x + 1.3, u.y + 0.25, u.z + 1.4);
                    case ANGLED_GRIP -> new AABB(u.x - 0.8, u.y - 3.05, u.z - 3.5, u.x + 0.8, u.y, u.z + 3.1);
                    default -> new AABB(u.x - 0.98, u.y - 6.1, u.z - 1.35, u.x + 0.98, u.y, u.z + 1.35);
                };
            }
            case TUBE -> {
                Vector3f t = rig.tubeMount == null ? new Vector3f(rig.muzzle) : rig.tubeMount;
                return new AABB(t.x - 1.1, t.y - 1.1, t.z - 11.0, t.x + 1.1, t.y + 1.1, t.z + 0.9);
            }
            default -> {
                // De kijker: een vak van de grootte van een red dot, op zijn montage.
                Vector3f mount = Optics.mount(rig);
                return new AABB(mount.x - 1.2, mount.y, mount.z - 1.9, mount.x + 1.2, mount.y + 2.5, mount.z + 1.9);
            }
        }
    }
}
