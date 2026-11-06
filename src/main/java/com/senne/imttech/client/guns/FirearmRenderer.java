package com.senne.imttech.client.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.client.suit.LaserCannonHand;
import com.senne.imttech.client.suit.SuitEffects;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.suit.TechSuitItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Het pistool in je eigen beeld (hoofdstuk 52), zoals GunRenderer voor de lasergeweren: uit zijn losse onderdelen
 * (elk in zijn materiaal), met je twee handen eraan. Wat er beweegt:
 * <ul>
 *   <li><b>schieten</b>: de slede schiet terug en veert naar voren, de trekker gaat in, een gele vlam uit de loop;
 *       na het laatste schot (met een leeg magazijn) blijft de slede open staan;</li>
 *   <li><b>storingen</b>: bij een klemzittende huls staat de slede half open met de huls erin; een tap-rack is een klap
 *       op het magazijn en de slede naar achteren en weer naar voren;</li>
 *   <li><b>herladen</b>: het magazijn valt uit de greep (R), een nieuw magazijn gaat erin (rechtsklik met het magazijn:
 *       het pistool komt in beeld, je linkerhand duwt het erin), de slede gaat naar voren;</li>
 *   <li><b>een patroon in de kamer</b> (sneak + R): de slede naar achteren, de patroon erin, de slede naar voren;</li>
 *   <li><b>een magazijn vullen</b> (R met het magazijn in je hand): je duim duwt er een patroon in;</li>
 *   <li>richten (de korrel en de keep voor je oog), bekijken (H), sprinten (het pistool zakt).</li>
 * </ul>
 */
public final class FirearmRenderer {
    private FirearmRenderer() {}

    private static final float ARM_SCALE = 0.78f;
    private static final float WRIST = 9.0f / 16f;
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(0.6f, -0.85f, 0.35f);
    private static final Vector3f LEFT_SHOULDER = new Vector3f(-0.6f, -0.9f, 0.25f);

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        FirearmType type = FirearmControl.held();
        boolean magazine = FirearmControl.heldMagazine();
        if (player == null || (type == null && !magazine) || player.isInvisible()) {
            return;
        }
        if (event.getHand() == InteractionHand.OFF_HAND) {
            event.setCanceled(true);
            return;
        }
        ItemStack stack = event.getItemStack();
        float pt = event.getPartialTick();
        Firearms.ActionKind action = FirearmControl.action();
        if (magazine) {
            if (MagazineItem.typeOf(stack) == null) {
                return;
            }
            event.setCanceled(true);
            if (action == Firearms.ActionKind.INSERT_MAG) {
                // Het pistool uit je werkbalk komt in beeld en het magazijn gaat erin.
                ItemStack gun = player.getInventory().getItem(FirearmControl.actionArg());
                FirearmType gunType = FirearmItem.typeOf(gun);
                if (gunType != null) {
                    renderGun(mc, player, event, gun, gunType, pt, true);
                    return;
                }
            }
            renderMagazine(mc, player, event, stack, pt);
            return;
        }
        if (FirearmItem.typeOf(stack) != type) {
            return;
        }
        event.setCanceled(true);
        renderGun(mc, player, event, stack, type, pt, false);
    }

    // ================================================================ Het pistool

    private static void renderGun(Minecraft mc, LocalPlayer player, RenderHandEvent event, ItemStack stack, FirearmType type,
                                  float pt, boolean inserting) {
        FirearmRig rig = FirearmRig.of(type);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float time = FirearmControl.time(pt);
        float aim = inserting ? 0 : FirearmControl.aim(pt);
        float inspect = FirearmControl.inspect(pt);
        float since = FirearmControl.sinceShot(pt);
        Firearms.ActionKind action = FirearmControl.action();
        float progress = FirearmControl.action(pt);
        GunParts parts = FirearmItem.parts(stack);

        pose.pushPose();
        Vector3f pivot = lerp(rig.grip, rig.sight, aim);
        Vector3f place = lerp(rig.hip, new Vector3f(0, 0, -rig.sightDistance), aim);
        float rotX = 0;
        float rotY = 5 * (1 - aim);
        float rotZ = -3 * (1 - aim);
        float breathe = 1 - 0.7f * aim;
        place.y += 0.004f * Mth.sin(time * 0.08f) * breathe;
        rotX += 0.4f * Mth.sin(time * 0.06f) * breathe;
        // Pakken: van onder je beeld omhoog (bij een magazijn erin: van rechtsonder, alsof je het pistool erbij pakt).
        float draw = ease(inserting ? Mth.clamp(progress * 3, 0, 1) : FirearmControl.drawn(pt));
        place.y -= (1 - draw) * 0.45f;
        rotX -= (1 - draw) * 35;
        if (action == Firearms.ActionKind.DRAW) {
            // Uit de holster: van je heup (rechtsonder, de loop omlaag) omhoog in je hand.
            float d = ease(progress);
            place.add(0.12f * (1 - d), -0.35f * (1 - d), 0.1f * (1 - d));
            rotX -= 70 * (1 - d);
            rotZ -= 25 * (1 - d);
        }
        float sprint = FirearmControl.sprint(pt);
        place.add(-0.03f * sprint, -0.035f * sprint, 0.02f * sprint);
        rotX -= 12 * sprint;
        rotY += 22 * sprint;
        rotZ += 10 * sprint;
        float kick = recoil(since);
        place.z += rig.kickBack * kick * (1 - 0.4f * aim);
        rotX += rig.kickUp * kick * (1 - 0.5f * aim);

        // De handelingen: het pistool kantelt zodat je ziet wat je doet.
        float tilt = 0;
        if (action == Firearms.ActionKind.EJECT_MAG || action == Firearms.ActionKind.INSERT_MAG
                || action == Firearms.ActionKind.RELOAD_POUCH) {
            tilt = ease(Mth.clamp(progress / 0.15f, 0, 1)) * ease(Mth.clamp((1 - progress) / 0.15f, 0, 1));
        } else if (action == Firearms.ActionKind.TAP_RACK) {
            tilt = ease(Mth.clamp(progress / 0.12f, 0, 1)) * ease(Mth.clamp((1 - progress) / 0.2f, 0, 1));
            // De klap op het magazijn: het pistool wipt even omhoog.
            place.y -= 0.02f * bump(progress, 0.25f, 0.08f);
        } else if (action == Firearms.ActionKind.CHAMBER_ROUND || action == Firearms.ActionKind.SLIDE_RELEASE) {
            tilt = 0.6f * ease(Mth.clamp(progress / 0.2f, 0, 1)) * ease(Mth.clamp((1 - progress) / 0.2f, 0, 1));
        }
        rotZ -= 30 * tilt;
        rotX += 12 * tilt;
        rotY += 10 * tilt;
        place.add(-0.06f * tilt, 0.07f * tilt, 0.03f * tilt);
        if (inspect >= 0) {
            float side = ease(Mth.clamp(inspect / 0.15f, 0, 1)) * ease(Mth.clamp((1 - inspect) / 0.15f, 0, 1));
            float top = ease(Mth.clamp((inspect - 0.48f) / 0.14f, 0, 1)) * ease(Mth.clamp((0.88f - inspect) / 0.12f, 0, 1));
            rotY -= 48 * side - 18 * top;
            rotZ -= 14 * side;
            rotX += 32 * top;
            place.add(-0.09f * side, 0.06f * side + 0.03f * top, 0.06f * side);
            rotZ += 3 * Mth.sin(inspect * 14) * side * (1 - top);
        }
        float s = rig.scale / 16f;
        Matrix4f gun = new Matrix4f()
                .translate(place)
                .rotateY((float) Math.toRadians(rotY))
                .rotateX((float) Math.toRadians(rotX))
                .rotateZ((float) Math.toRadians(rotZ))
                .scale(s)
                .translate(-pivot.x, -pivot.y, -pivot.z);
        pose.mulPose(gun);

        // ---- De onderdelen.
        part(pose, buffers, type, parts, GunPart.FRAME, light);
        float trigger = Math.max(since < 2 ? 1 : 0, FirearmControl.sinceClick(pt) < 2 ? 1 : 0);
        pose.pushPose();
        pose.translate(0, 0, 0.4f * trigger);
        part(pose, buffers, type, parts, GunPart.TRIGGER, light);
        pose.popPose();
        float slideBack = slideBack(rig, stack, since, action, progress);
        pose.pushPose();
        pose.translate(0, 0, slideBack);
        part(pose, buffers, type, parts, GunPart.SLIDE, light);
        pose.popPose();
        part(pose, buffers, type, parts, GunPart.BARREL, light);
        // Een huls die klem zit, in de uitwerpopening.
        int jam = FirearmItem.jam(stack);
        boolean casingStuck = jam == FirearmItem.JAM_STOVEPIPE && (action != Firearms.ActionKind.TAP_RACK || progress < 0.62f);
        if (casingStuck) {
            pose.pushPose();
            // Het model van de huls staat rond (8, 7.65, 8): eerst naar de opening, dan kantelen, dan het midden erop.
            pose.translate(rig.port.x + 0.4f, rig.port.y + 0.5f, rig.port.z);
            pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-40)));
            pose.translate(-8, -7.65f, -8);
            pose.scale(16, 16, 16);
            FirearmModels.render(pose, buffers, FirearmModels.location("casing"), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        // Het magazijn: erin, eruit vallend, of erin komend.
        float magOut = magazineOffset(action, progress);
        boolean hasMag = FirearmItem.hasMagazine(stack) || action == Firearms.ActionKind.INSERT_MAG
                || (action == Firearms.ActionKind.RELOAD_POUCH && progress > 0.45f);
        if (action == Firearms.ActionKind.EJECT_MAG && progress > 0.9f) {
            hasMag = false;
        }
        if (hasMag && magOut < 40) {
            pose.pushPose();
            Vector3f axis = rig.magazineAxis;
            pose.translate(axis.x * magOut, axis.y * magOut, axis.z * magOut);
            pose.scale(16, 16, 16);
            FirearmModels.render(pose, buffers, FirearmModels.magazine(type), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        // Een patroon die met de hand in de kamer gaat.
        if (action == Firearms.ActionKind.CHAMBER_ROUND && progress > 0.3f && progress < 0.72f) {
            float t = ease((progress - 0.3f) / 0.4f);
            pose.pushPose();
            Vector3f from = new Vector3f(rig.port.x + 3.5f, rig.port.y - 6.5f, rig.port.z + 1.5f);
            Vector3f to = new Vector3f(rig.port.x - 1.2f, rig.port.y - 7.3f, rig.port.z - 7.5f);
            Vector3f at = lerp(from, to, t);
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(-90)));
            pose.translate(-8, -8.3f, -8);
            pose.scale(16, 16, 16);
            FirearmModels.render(pose, buffers, FirearmModels.location("round"), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        if (since < 1.8f && since >= 0) {
            flash(pose, buffers.getBuffer(SuitEffects.GLOW), rig.muzzle, 1 - since / 1.8f);
        }
        pose.popPose();

        // ---- Je armen.
        boolean suit = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof TechSuitItem;
        Vector3f right = gun.transformPosition(new Vector3f(rig.grip));
        Vector3f left = gun.transformPosition(leftHand(rig, action, progress, slideBack, magOut, hasMag));
        HumanoidArm main = player.getMainArm();
        arm(mc, player, main, suit, right, main == HumanoidArm.RIGHT ? RIGHT_SHOULDER : LEFT_SHOULDER, pose, buffers, light);
        HumanoidArm other = main.getOpposite();
        arm(mc, player, other, suit, left, other == HumanoidArm.RIGHT ? RIGHT_SHOULDER : LEFT_SHOULDER, pose, buffers, light);
    }

    /** Eén onderdeel tekenen, in zijn materiaal (als het erin zit). */
    private static void part(PoseStack pose, MultiBufferSource buffers, FirearmType type, GunParts parts, GunPart part, int light) {
        PartState state = parts.get(part);
        if (state == null) {
            return;
        }
        pose.pushPose();
        pose.scale(16, 16, 16);
        FirearmModels.render(pose, buffers, FirearmModels.part(type, part, state.material()), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Hoe ver (pixels) de slede nu naar achteren staat. */
    private static float slideBack(FirearmRig rig, ItemStack stack, float since, @Nullable Firearms.ActionKind action, float t) {
        float back = 0;
        if (since >= 0 && since < rig.cycleTicks + 1) {
            back = since < 1 ? since : 1 - (since - 1) / rig.cycleTicks;
        }
        back *= rig.cycle;
        if (FirearmItem.slideLocked(stack)) {
            back = rig.cycle;
        } else if (FirearmItem.jam(stack) == FirearmItem.JAM_STOVEPIPE) {
            back = rig.cycle * 0.55f;
        }
        if (action == Firearms.ActionKind.SLIDE_RELEASE) {
            back = rig.cycle * (1 - ease(Mth.clamp((t - 0.35f) / 0.3f, 0, 1)));
        } else if (action == Firearms.ActionKind.TAP_RACK) {
            // De klap (tot 0,35), dan de slede naar achteren (0,4-0,6) en los (0,62-0,8).
            float rack = t < 0.4f ? Math.max(back / rig.cycle, 0) : t < 0.6f ? Mth.lerp(ease((t - 0.4f) / 0.2f), back / rig.cycle, 1.1f)
                    : t < 0.8f ? 1.1f * (1 - ease((t - 0.62f) / 0.18f)) : 0;
            back = Math.max(0, rack) * rig.cycle;
        } else if (action == Firearms.ActionKind.CHAMBER_ROUND) {
            float rack = t < 0.3f ? ease(t / 0.3f) * 1.1f : t < 0.72f ? 1.1f : 1.1f * (1 - ease((t - 0.72f) / 0.2f));
            back = rack * rig.cycle;
        } else if (action == Firearms.ActionKind.RELOAD_POUCH && t > 0.8f) {
            back = rig.cycle * (1 - ease((t - 0.8f) / 0.2f)) * (FirearmItem.chambered(stack) ? 0 : 1);
        }
        return back;
    }

    /** Hoe ver (pixels) het magazijn uit de greep staat tijdens een handeling. */
    private static float magazineOffset(@Nullable Firearms.ActionKind action, float t) {
        if (action == Firearms.ActionKind.EJECT_MAG) {
            return t < 0.3f ? 0 : t < 0.6f ? 5 * ease((t - 0.3f) / 0.3f) : 5 + 60 * (t - 0.6f) * (t - 0.6f);
        }
        if (action == Firearms.ActionKind.INSERT_MAG) {
            return t < 0.25f ? 16 : t < 0.85f ? 16 - 16 * ease((t - 0.25f) / 0.6f) : 0;
        }
        if (action == Firearms.ActionKind.RELOAD_POUCH) {
            if (t < 0.12f) {
                return 0;
            }
            if (t < 0.3f) {
                return 4 * ease((t - 0.12f) / 0.18f);
            }
            if (t < 0.42f) {
                return 4 + 60 * (t - 0.3f) * (t - 0.3f);
            }
            if (t < 0.45f) {
                return 99;
            }
            if (t < 0.75f) {
                return 16 - 16 * ease((t - 0.45f) / 0.3f);
            }
            return 0;
        }
        return 0;
    }

    /** Waar je linkerhand is (model-pixels): de steun, of bij het magazijn, de slede, de uitwerpopening. */
    private static Vector3f leftHand(FirearmRig rig, @Nullable Firearms.ActionKind action, float t, float slideBack, float magOut,
                                     boolean hasMag) {
        Vector3f rest = new Vector3f(rig.support);
        Vector3f axis = rig.magazineAxis;
        Vector3f atMag = new Vector3f(rig.magazine).add(new Vector3f(axis).mul(Math.min(magOut, 18) + 3.5f)).add(-1.5f, 0, 0.5f);
        Vector3f atSlide = new Vector3f(rig.port.x - 1.0f, rig.port.y + 1.2f, rig.port.z + 8.5f + slideBack);
        if (action == null) {
            return rest;
        }
        switch (action) {
            case EJECT_MAG -> {
                // Naar de knop (bij de greep), en als het magazijn valt weer terug.
                Vector3f button = new Vector3f(rig.grip.x - 1.8f, rig.grip.y + 1.6f, rig.grip.z + 0.8f);
                if (t < 0.25f) return lerp(rest, button, ease(t / 0.25f));
                if (t < 0.6f) return button;
                return lerp(button, rest, ease((t - 0.6f) / 0.4f));
            }
            case INSERT_MAG, RELOAD_POUCH -> {
                if (action == Firearms.ActionKind.RELOAD_POUCH && t < 0.45f) {
                    Vector3f button = new Vector3f(rig.grip.x - 1.8f, rig.grip.y + 1.6f, rig.grip.z + 0.8f);
                    return t < 0.12f ? lerp(rest, button, ease(t / 0.12f)) : button;
                }
                if (t > 0.85f) return lerp(atMag, rest, ease((t - 0.85f) / 0.15f));
                return magOut < 40 ? atMag : rest;
            }
            case SLIDE_RELEASE -> {
                Vector3f stop = new Vector3f(rig.port.x - 2.6f, rig.port.y - 1.4f, rig.port.z + 6.0f);
                if (t < 0.3f) return lerp(rest, stop, ease(t / 0.3f));
                if (t < 0.7f) return stop;
                return lerp(stop, rest, ease((t - 0.7f) / 0.3f));
            }
            case TAP_RACK -> {
                Vector3f base = new Vector3f(rig.magazine).add(new Vector3f(axis).mul(7.5f)).add(0, 0, 0.5f);
                if (t < 0.15f) return lerp(rest, base, ease(t / 0.15f));
                if (t < 0.35f) return base;
                if (t < 0.45f) return lerp(base, atSlide, ease((t - 0.35f) / 0.1f));
                if (t < 0.8f) return atSlide;
                return lerp(atSlide, rest, ease((t - 0.8f) / 0.2f));
            }
            case CHAMBER_ROUND -> {
                Vector3f port = new Vector3f(rig.port.x + 3.2f, rig.port.y - 1.0f, rig.port.z + 2.5f);
                if (t < 0.3f) return lerp(rest, atSlide, ease(t / 0.3f));
                if (t < 0.4f) return lerp(atSlide, port, ease((t - 0.3f) / 0.1f));
                if (t < 0.7f) return port;
                if (t < 0.8f) return lerp(port, atSlide, ease((t - 0.7f) / 0.1f));
                return lerp(atSlide, rest, ease((t - 0.8f) / 0.2f));
            }
            default -> {
                return rest;
            }
        }
    }

    // ================================================================ Een magazijn in je hand

    /** Het magazijn in je linkerhand; met R duwt je rechterhand er een patroon in. */
    private static void renderMagazine(Minecraft mc, LocalPlayer player, RenderHandEvent event, ItemStack stack, float pt) {
        FirearmType type = MagazineItem.typeOf(stack);
        if (type == null) {
            return;
        }
        FirearmRig rig = FirearmRig.of(type);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float time = FirearmControl.time(pt);
        float draw = ease(FirearmControl.drawn(pt));
        float sprint = FirearmControl.sprint(pt);
        boolean loading = FirearmControl.action() == Firearms.ActionKind.LOAD_ROUND;
        float t = loading ? FirearmControl.action(pt) : -1;

        pose.pushPose();
        Vector3f place = new Vector3f(-0.06f, -0.26f - (1 - draw) * 0.4f - 0.05f * sprint, -0.58f);
        place.y += 0.004f * Mth.sin(time * 0.08f);
        float rotX = -12 - (1 - draw) * 30 - 8 * sprint;
        float rotY = 30;
        float rotZ = 10;
        float s = rig.scale / 16f;
        // Het magazijn staat in het model schuin (zoals in de greep): hier recht overeind, om zijn eigen as.
        // Het magazijn staat in het model schuin in de greep (om (8, 9.3, 11)): hier recht overeind, met zijn midden op
        // {@code place}.
        Matrix4f mag = new Matrix4f()
                .translate(place)
                .rotateY((float) Math.toRadians(rotY))
                .rotateX((float) Math.toRadians(rotX))
                .rotateZ((float) Math.toRadians(rotZ))
                .scale(s)
                .translate(-8, -6.0f, -12.0f)
                .translate(8, 9.3f, 11.0f)
                .rotateX((float) Math.toRadians(-22.5))
                .translate(-8, -9.3f, -11.0f);
        pose.mulPose(mag);
        pose.pushPose();
        pose.scale(16, 16, 16);
        FirearmModels.render(pose, buffers, FirearmModels.magazine(type), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        // De patroon die erin gaat: van rechtsboven naar de bovenkant van het magazijn.
        Vector3f top = new Vector3f(8, 9.2f, 11.9f);
        if (loading) {
            float k = ease(Mth.clamp(t / 0.75f, 0, 1));
            Vector3f from = new Vector3f(14, 14, 10);
            Vector3f at = lerp(from, top, k);
            if (t < 0.85f) {
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(90)).rotateX((float) Math.toRadians(90)));
                pose.translate(-8, -8, -8);
                pose.scale(16, 16, 16);
                FirearmModels.render(pose, buffers, FirearmModels.location("round"), light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        pose.popPose();

        boolean suit = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof TechSuitItem;
        Vector3f leftAt = mag.transformPosition(new Vector3f(8.6f, 4.2f, 12.2f));
        Vector3f rightRest = mag.transformPosition(new Vector3f(16, 12, 8));
        Vector3f rightAt = rightRest;
        if (loading) {
            float k = ease(Mth.clamp(t / 0.75f, 0, 1));
            rightAt = mag.transformPosition(lerp(new Vector3f(16, 12, 8), new Vector3f(9.8f, 11.0f, 11.6f), k));
        }
        HumanoidArm main = player.getMainArm();
        HumanoidArm other = main.getOpposite();
        arm(mc, player, other, suit, leftAt, other == HumanoidArm.RIGHT ? RIGHT_SHOULDER : LEFT_SHOULDER, pose, buffers, light);
        arm(mc, player, main, suit, rightAt, main == HumanoidArm.RIGHT ? RIGHT_SHOULDER : LEFT_SHOULDER, pose, buffers, light);
    }

    // ================================================================ Tekenen

    private static float recoil(float since) {
        if (since < 0) {
            return 0;
        }
        return since < 1 ? since : (float) Math.exp(-(since - 1) * 0.6);
    }

    /** De vlam uit de loop: geel-wit in het midden, oranje naar buiten. */
    private static void flash(PoseStack pose, VertexConsumer out, Vector3f muzzle, float strength) {
        Matrix4f m = pose.last().pose();
        Vec3 c = new Vec3(muzzle.x, muzzle.y, muzzle.z - 1.0f);
        SuitEffects.disc(m, out, c, new Vec3(0, 0, 1), 3.0f * strength, 0xFF7A20, 0.45f * strength);
        SuitEffects.disc(m, out, c, new Vec3(0, 0, 1), 1.5f * strength, 0xFFD070, 0.9f * strength);
        SuitEffects.disc(m, out, c.add(0, 0, -0.4), new Vec3(0, 0, 1), 0.7f, 0xFFFFF0, strength);
        for (int i = 0; i < 5; i++) {
            float a = i * Mth.TWO_PI / 5 + 0.3f;
            Vec3 tip = c.add(Mth.cos(a) * 3.8f * strength, Mth.sin(a) * 3.8f * strength, -1.2f);
            SuitEffects.quad(m, out, c, c.add(Mth.cos(a + 0.3f) * 0.5f, Mth.sin(a + 0.3f) * 0.5f, 0), tip,
                    c.add(Mth.cos(a - 0.3f) * 0.5f, Mth.sin(a - 0.3f) * 0.5f, 0), 0xFFB040, 0.7f * strength);
        }
    }

    private static void arm(Minecraft mc, LocalPlayer player, HumanoidArm side, boolean suit, Vector3f hand,
                            Vector3f shoulderGuess, PoseStack pose, MultiBufferSource buffers, int light) {
        Vector3f dir = new Vector3f(hand).sub(shoulderGuess).normalize();
        Vector3f shoulder = new Vector3f(hand).sub(new Vector3f(dir).mul(WRIST * ARM_SCALE));
        float sign = side == HumanoidArm.RIGHT ? 1 : -1;
        Vector3f inward = new Vector3f(-sign, 0.25f, 0).sub(new Vector3f(dir).mul(dir.dot(-sign, 0.25f, 0))).normalize();
        Vector3f across = new Vector3f(dir).cross(inward);
        Quaternionf turn = new Matrix3f(across.x, across.y, across.z, dir.x, dir.y, dir.z, inward.x, inward.y, inward.z)
                .getNormalizedRotation(new Quaternionf());
        pose.pushPose();
        pose.translate(shoulder.x, shoulder.y, shoulder.z);
        pose.mulPose(turn);
        pose.scale(ARM_SCALE, ARM_SCALE, ARM_SCALE);
        LaserCannonHand.drawArm(mc, player, side, suit, false, pose, buffers, light);
        pose.popPose();
    }

    private static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, t);
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float bump(float t, float at, float width) {
        float d = Math.abs(t - at) / width;
        return d >= 1 ? 0 : 1 - d * d;
    }
}
