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
 * Het vuurwapen in je eigen beeld (hoofdstuk 52), zoals GunRenderer voor de lasergeweren: uit zijn losse onderdelen
 * (elk in zijn materiaal), met je twee handen eraan. Wat er beweegt:
 * <ul>
 *   <li><b>schieten</b>: de slede schiet terug en veert naar voren, de trekker gaat in, een gele vlam uit de loop;
 *       na het laatste schot (met een leeg magazijn) blijft de slede van een pistool open staan;</li>
 *   <li><b>storingen</b>: bij een klemzittende huls staat de slede half open met de huls erin; een tap-rack is een klap
 *       op het magazijn en de slede naar achteren en weer naar voren;</li>
 *   <li><b>herladen</b>: het magazijn valt uit het wapen (R), een nieuw magazijn gaat erin (rechtsklik met het magazijn:
 *       het wapen komt in beeld, je linkerhand duwt het erin), de slede gaat naar voren;</li>
 *   <li><b>een patroon in de kamer</b> (sneak + R): de slede naar achteren, de patroon erin, de slede naar voren;</li>
 *   <li><b>een magazijn vullen</b> (R met het magazijn in je hand): je duim duwt er een patroon in;</li>
 *   <li>richten (de korrel en de keep voor je oog), bekijken (H), sprinten (het wapen zakt).</li>
 * </ul>
 * Hoofdstuk 103: een <b>lang wapen</b> (de MP5...) houd je met je linkerhand vooraan vast. Doorladen doet die hand aan
 * de hendel van het wapen (FirearmRig.handle: de spanhendel van de MP5 links vooraan): naar achteren trekken en loslaten.
 * De grendel binnenin schuift mee; de hendel zelf beweegt bij een schot niet.
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
                // Het wapen uit je werkbalk komt in beeld en het magazijn gaat erin.
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

    // ================================================================ Het wapen

    private static void renderGun(Minecraft mc, LocalPlayer player, RenderHandEvent event, ItemStack stack, FirearmType type,
                                  float pt, boolean inserting) {
        FirearmRig rig = FirearmRig.of(type);
        boolean lng = type.longGun;
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
        // Pakken: van onder je beeld omhoog (bij een magazijn erin: van rechtsonder, alsof je het wapen erbij pakt).
        float draw = ease(inserting ? Mth.clamp(progress * 3, 0, 1) : action == Firearms.ActionKind.DRAW ? 1 : FirearmControl.drawn(pt));
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

        // De handelingen: het wapen kantelt zodat je ziet wat je doet (een lang wapen minder: je houdt het met twee handen).
        float tilt = 0;
        if (action == Firearms.ActionKind.EJECT_MAG || action == Firearms.ActionKind.INSERT_MAG
                || action == Firearms.ActionKind.RELOAD_POUCH) {
            tilt = ease(Mth.clamp(progress / 0.15f, 0, 1)) * ease(Mth.clamp((1 - progress) / 0.15f, 0, 1)) * (lng ? 0.45f : 1);
        } else if (action == Firearms.ActionKind.TAP_RACK) {
            tilt = ease(Mth.clamp(progress / 0.12f, 0, 1)) * ease(Mth.clamp((1 - progress) / 0.2f, 0, 1)) * (lng ? 0.4f : 1);
            // De klap op het magazijn: het wapen wipt even omhoog.
            place.y -= 0.02f * bump(progress, 0.25f, 0.08f);
        } else if (action == Firearms.ActionKind.CHAMBER_ROUND) {
            tilt = 0.6f * ease(Mth.clamp(progress / 0.2f, 0, 1)) * ease(Mth.clamp((1 - progress) / 0.2f, 0, 1));
        } else if (action == Firearms.ActionKind.SLIDE_RELEASE || action == Firearms.ActionKind.CYCLE) {
            tilt = (lng ? 0.15f : 0.6f) * ease(Mth.clamp(progress / 0.2f, 0, 1)) * ease(Mth.clamp((1 - progress) / 0.2f, 0, 1));
        } else if (lng && action == Firearms.ActionKind.UNLOAD_CHAMBER) {
            // Een lang wapen kantelt even, zodat je de patroon uit de uitwerpopening ziet springen.
            tilt = 0.42f * ease(Mth.clamp(progress / 0.2f, 0, 1)) * ease(Mth.clamp((1 - progress) / 0.2f, 0, 1));
        }
        rotZ -= 30 * tilt;
        rotX += 12 * tilt;
        rotY += 10 * tilt;
        place.add(-0.06f * tilt, 0.07f * tilt, 0.03f * tilt);
        // Hoofdstuk 103, stap 2: zit de hendel rechts (de AK-47), dan laadt je rechterhand door (zie rightHandRack). Het
        // wapen rolt daarbij wat naar links: zijn rechterkant komt naar boven, zodat je de spanhendel ziet bewegen.
        boolean rightRack = lng && rig.handleRight();
        if (rightRack) {
            float roll = rollForHandle(action, progress);
            rotZ += 20 * roll;
            rotY -= 5 * roll;
            place.add(-0.03f * roll, 0.02f * roll, 0);
        }
        if (inspect >= 0) {
            float side = ease(Mth.clamp(inspect / 0.15f, 0, 1)) * ease(Mth.clamp((1 - inspect) / 0.15f, 0, 1));
            float top = ease(Mth.clamp((inspect - 0.48f) / 0.14f, 0, 1)) * ease(Mth.clamp((0.88f - inspect) / 0.12f, 0, 1));
            // Hoofdstuk 74: verder gedraaid, zodat je de rechterkant ziet (de uitwerpopening, MADE IN AUSTRIA), zoals op
            // een foto van een Glock; en naar links geschoven, zodat de loop in beeld blijft. Een lang wapen draait
            // minder ver (anders zwaait de loop uit beeld) en gaat meer naar links.
            float turn = lng ? 0.62f : 1;
            rotY -= (68 * side - 26 * top) * turn;
            rotZ -= 10 * side;
            rotX += 32 * top * turn;
            place.add(-(lng ? 0.24f : 0.15f) * side, 0.07f * side + 0.03f * top, 0.03f * side);
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
        pose.translate(0, 0, (lng ? 0.22f : 0.4f) * trigger);
        part(pose, buffers, type, parts, GunPart.TRIGGER, light);
        pose.popPose();
        // De slede (een pistool) of de grendel (een lang wapen), en hoe ver de hendel naar achteren staat.
        float slideBack = slideBack(rig, stack, since, action, progress, lng);
        float handleBack = lng ? rack(action, progress) * rig.handleTravel : slideBack;
        if (lng) {
            slideBack = Math.max(slideBack, rack(action, progress) * rig.cycle);
        }
        pose.pushPose();
        pose.translate(0, 0, slideBack);
        part(pose, buffers, type, parts, GunPart.SLIDE, light);
        pose.popPose();
        PartState slideState = parts.get(GunPart.SLIDE);
        if (slideState != null) {
            for (String extra : FirearmModels.extras(type, GunPart.SLIDE)) {
                pose.pushPose();
                pose.translate(0, 0, handleBack);
                pose.scale(16, 16, 16);
                FirearmModels.render(pose, buffers, FirearmModels.extra(type, extra, slideState.material()), light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
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
            FirearmModels.render(pose, buffers, FirearmModels.casing(type.caliber), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        // Het magazijn: erin, eruit vallend, of erin komend.
        float magOut = magazineOffset(action, progress);
        boolean hasMag = !type.tube() && (FirearmItem.hasMagazine(stack) || action == Firearms.ActionKind.INSERT_MAG
                || (action == Firearms.ActionKind.RELOAD_POUCH && progress > 0.45f));
        if (action == Firearms.ActionKind.EJECT_MAG && progress > 0.9f) {
            hasMag = false;
        }
        if (hasMag && magOut < 40) {
            pose.pushPose();
            Vector3f axis = rig.magazineAxis;
            pose.translate(axis.x * magOut, axis.y * magOut, axis.z * magOut);
            pose.scale(16, 16, 16);
            // Hoofdstuk 74: een leeg magazijn dat eruit valt is ook echt leeg (de opvolger bovenin, geen patroon). Een
            // magazijn dat erin gaat komt uit je hand of je pouch en is vol getekend.
            boolean empty = FirearmItem.hasMagazine(stack) && FirearmItem.roundsInMagazine(stack) == 0
                    && action != Firearms.ActionKind.INSERT_MAG && action != Firearms.ActionKind.RELOAD_POUCH;
            FirearmModels.render(pose, buffers, FirearmModels.magazine(type, empty), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        // Een patroon die met de hand in de kamer gaat: van rechtsonder naast het wapen door de uitwerpopening de kamer
        // in (hoofdstuk 74: het pad hoort bij het nieuwe model; de patroon is nu op ware grootte).
        if (action == Firearms.ActionKind.CHAMBER_ROUND && progress > 0.3f && progress < 0.72f) {
            float t = ease((progress - 0.3f) / 0.4f);
            pose.pushPose();
            Vector3f from = new Vector3f(rig.port.x + 3.0f, rig.port.y - 2.2f, rig.port.z + 1.2f);
            Vector3f to = new Vector3f(8.0f, lng ? rig.port.y - 0.4f : rig.muzzle.y, rig.port.z - 0.4f);
            Vector3f at = lerp(from, to, t);
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(-90)));
            pose.translate(-8, -8.3f, -8);
            pose.scale(16, 16, 16);
            FirearmModels.render(pose, buffers, FirearmModels.round(type.caliber), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        // De patroon die bij een unload uit de kamer springt (hoofdstuk 54): een boogje naar rechts.
        if (action == Firearms.ActionKind.UNLOAD_CHAMBER && progress > 0.3f && progress < 0.6f) {
            float t = (progress - 0.3f) / 0.3f;
            pose.pushPose();
            pose.translate(rig.port.x + 0.4f + 6.5f * t, rig.port.y + 0.5f + 5f * t - 8f * t * t, rig.port.z + 2.5f * t);
            pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-40 - 120 * t)));
            pose.translate(-8, -8.3f, -8);
            pose.scale(16, 16, 16);
            FirearmModels.render(pose, buffers, FirearmModels.round(type.caliber), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        if (since < 1.8f && since >= 0) {
            flash(pose, buffers.getBuffer(SuitEffects.GLOW), rig.muzzle, 1 - since / 1.8f);
        }
        pose.popPose();

        // ---- Je armen.
        boolean suit = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof TechSuitItem;
        Vector3f right = gun.transformPosition(rightRack ? rightHandRack(rig, action, progress, handleBack) : new Vector3f(rig.grip));
        Vector3f left = gun.transformPosition(lng ? leftHandLong(rig, action, progress, handleBack, magOut)
                : leftHand(rig, action, progress, slideBack, magOut, hasMag));
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

    /** Hoe ver (pixels) de slede (of, bij een lang wapen, de grendel) nu naar achteren staat. */
    private static float slideBack(FirearmRig rig, ItemStack stack, float since, @Nullable Firearms.ActionKind action, float t,
                                   boolean lng) {
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
        if (lng) {
            return back; // de handelingen van een lang wapen: zie rack
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
        } else if (action == Firearms.ActionKind.UNLOAD_CHAMBER) {
            // De slede naar achteren trekken (de patroon springt eruit), even vasthouden, loslaten.
            float rack = t < 0.3f ? ease(t / 0.3f) * 1.1f : t < 0.6f ? 1.1f : 1.1f * (1 - ease((t - 0.6f) / 0.25f));
            back = rack * rig.cycle;
        } else if (action == Firearms.ActionKind.RELOAD_POUCH && t > 0.8f) {
            back = rig.cycle * (1 - ease((t - 0.8f) / 0.2f)) * (FirearmItem.chambered(stack) ? 0 : 1);
        }
        return back;
    }

    /**
     * Hoe ver een wapen met de hendel rechts (de AK-47) naar links gerold is tijdens een handeling (0 tot 1): op tijd voor
     * je hand bij de hendel is, en terug als de hendel los is.
     */
    private static float rollForHandle(@Nullable Firearms.ActionKind action, float t) {
        if (action == null) {
            return 0;
        }
        return switch (action) {
            case SLIDE_RELEASE, CYCLE -> ease(Mth.clamp(t / 0.18f, 0, 1)) * ease(Mth.clamp((1 - t) / 0.25f, 0, 1));
            case TAP_RACK -> ease(Mth.clamp((t - 0.33f) / 0.12f, 0, 1)) * ease(Mth.clamp((1 - t) / 0.18f, 0, 1));
            case CHAMBER_ROUND -> ease(Mth.clamp(t / 0.14f, 0, 1)) * ease(Mth.clamp((1 - t) / 0.12f, 0, 1));
            case UNLOAD_CHAMBER -> ease(Mth.clamp(t / 0.14f, 0, 1)) * ease(Mth.clamp((1 - t) / 0.22f, 0, 1));
            default -> 0;
        };
    }

    /**
     * Hoofdstuk 103: hoe ver de hendel van een lang wapen (de spanhendel, de grendel, de pomp) naar achteren staat
     * tijdens een handeling, van 0 (voor) tot 1 (helemaal achter). Je linkerhand is er dan al (zie leftHandLong).
     */
    private static float rack(@Nullable Firearms.ActionKind action, float t) {
        if (action == null) {
            return 0;
        }
        return switch (action) {
            // Naar achteren trekken (0,2-0,5), even vasthouden, en loslaten: hij klapt naar voren.
            case SLIDE_RELEASE, CYCLE -> t < 0.2f ? 0 : t < 0.5f ? ease((t - 0.2f) / 0.3f) : t < 0.58f ? 1 : 1 - ease((t - 0.58f) / 0.1f);
            // Eerst de klap op het magazijn (tot 0,35), dan de hendel.
            case TAP_RACK -> t < 0.45f ? 0 : t < 0.62f ? ease((t - 0.45f) / 0.17f) : t < 0.66f ? 1 : 1 - ease((t - 0.66f) / 0.12f);
            // Naar achteren en vasthouden terwijl de patroon erin gaat, dan los.
            case CHAMBER_ROUND -> t < 0.15f ? 0 : t < 0.32f ? ease((t - 0.15f) / 0.17f) : t < 0.76f ? 1 : 1 - ease((t - 0.76f) / 0.1f);
            case UNLOAD_CHAMBER -> t < 0.15f ? 0 : t < 0.35f ? ease((t - 0.15f) / 0.2f) : t < 0.6f ? 1 : 1 - ease((t - 0.6f) / 0.15f);
            default -> 0;
        };
    }

    /** Hoe ver (pixels) het magazijn uit het wapen staat tijdens een handeling. */
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
        // Achteraan op de slede, bij de groeven (5 pixels achter de uitwerpopening).
        Vector3f atSlide = new Vector3f(rig.port.x - 1.0f, rig.port.y + 1.0f, rig.port.z + 5.0f + slideBack);
        if (action == null) {
            return rest;
        }
        switch (action) {
            case EJECT_MAG -> {
                // Je duim drukt de knop in; je linkerhand gaat onder de greep en volgt het magazijn dat eruit glijdt
                // (hoofdstuk 74: vroeger ging hij naar de knop en stak je arm dan voor het pistool).
                if (t < 0.25f) return lerp(rest, atMag, ease(t / 0.25f));
                if (t < 0.6f) return atMag;
                return lerp(atMag, rest, ease((t - 0.6f) / 0.4f));
            }
            case INSERT_MAG, RELOAD_POUCH -> {
                if (action == Firearms.ActionKind.RELOAD_POUCH && t < 0.45f) {
                    return t < 0.12f ? lerp(rest, atMag, ease(t / 0.12f)) : atMag;
                }
                if (t > 0.85f) return lerp(atMag, rest, ease((t - 0.85f) / 0.15f));
                return magOut < 40 ? atMag : rest;
            }
            case SLIDE_RELEASE -> {
                Vector3f stop = new Vector3f(rig.port.x - 2.6f, rig.port.y - 1.4f, rig.port.z + 2.0f); // de slide stop links boven de trekker
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
            case UNLOAD_CHAMBER -> {
                if (t < 0.3f) return lerp(rest, atSlide, ease(t / 0.3f));
                if (t < 0.6f) return atSlide;
                return lerp(atSlide, rest, ease((t - 0.6f) / 0.4f));
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

    /**
     * Hoofdstuk 103: waar je linkerhand is bij een lang wapen (model-pixels): onder de handbeschermer, of bij het
     * magazijn, de hendel (die hij naar achteren trekt, zie rack) of boven de uitwerpopening.
     */
    private static Vector3f leftHandLong(FirearmRig rig, @Nullable Firearms.ActionKind action, float t, float handleBack, float magOut) {
        Vector3f rest = new Vector3f(rig.support);
        if (action == null) {
            return rest;
        }
        Vector3f axis = rig.magazineAxis;
        Vector3f atMag = new Vector3f(rig.magazine).add(new Vector3f(axis).mul(Math.min(magOut, 18) + 5.5f)).add(-1.0f, 0, 0.3f);
        Vector3f atHandle = new Vector3f(rig.handle).add(-0.3f, -0.2f, handleBack);
        // Hoofdstuk 103, stap 2: zit de hendel rechts (de AK-47), dan doet je rechterhand het doorladen (rightHandRack) en
        // blijft je linkerhand het wapen vooraan vasthouden.
        boolean rightRack = rig.handleRight();
        switch (action) {
            case EJECT_MAG -> {
                if (t < 0.25f) return lerp(rest, atMag, ease(t / 0.25f));
                if (t < 0.6f) return atMag;
                return lerp(atMag, rest, ease((t - 0.6f) / 0.4f));
            }
            case INSERT_MAG, RELOAD_POUCH -> {
                if (action == Firearms.ActionKind.RELOAD_POUCH && t < 0.45f) {
                    return t < 0.12f ? lerp(rest, atMag, ease(t / 0.12f)) : atMag;
                }
                if (t > 0.85f) return lerp(atMag, rest, ease((t - 0.85f) / 0.15f));
                return magOut < 40 ? atMag : rest;
            }
            case SLIDE_RELEASE, CYCLE -> {
                if (rightRack) return rest;
                // Naar de hendel (tot 0,2), trekken en loslaten (de hand blijft even achter), en terug naar voren.
                if (t < 0.2f) return lerp(rest, atHandle, ease(t / 0.2f));
                if (t < 0.58f) return atHandle;
                Vector3f released = new Vector3f(rig.handle).add(-0.3f, -0.2f, rig.handleTravel);
                if (t < 0.72f) return released;
                return lerp(released, rest, ease((t - 0.72f) / 0.28f));
            }
            case TAP_RACK -> {
                // Een klap onder tegen het magazijn, dan de hendel.
                Vector3f base = new Vector3f(rig.magazineFoot).add(-0.6f, -1.2f, 0.4f);
                if (t < 0.15f) return lerp(rest, base, ease(t / 0.15f));
                if (t < 0.35f) return base;
                if (rightRack) return lerp(base, rest, ease(Mth.clamp((t - 0.35f) / 0.15f, 0, 1)));
                if (t < 0.45f) return lerp(base, atHandle, ease((t - 0.35f) / 0.1f));
                if (t < 0.66f) return atHandle;
                Vector3f released = new Vector3f(rig.handle).add(-0.3f, -0.2f, rig.handleTravel);
                if (t < 0.8f) return released;
                return lerp(released, rest, ease((t - 0.8f) / 0.2f));
            }
            case UNLOAD_CHAMBER -> {
                if (rightRack) return rest;
                if (t < 0.15f) return lerp(rest, atHandle, ease(t / 0.15f));
                if (t < 0.75f) return atHandle;
                return lerp(atHandle, rest, ease((t - 0.75f) / 0.25f));
            }
            case CHAMBER_ROUND -> {
                // De hendel naar achteren, de patroon van boven in de uitwerpopening, en de hendel los.
                Vector3f port = new Vector3f(rig.port.x + 1.6f, rig.port.y + 1.4f, rig.port.z + 0.6f);
                if (rightRack) {
                    return rest; // je rechterhand trekt de hendel naar achteren en duwt de patroon in de kamer
                }
                if (t < 0.15f) return lerp(rest, atHandle, ease(t / 0.15f));
                if (t < 0.32f) return atHandle;
                if (t < 0.42f) return lerp(atHandle, port, ease((t - 0.32f) / 0.1f));
                if (t < 0.66f) return port;
                if (t < 0.76f) return lerp(port, atHandle, ease((t - 0.66f) / 0.1f));
                if (t < 0.88f) return atHandle;
                return lerp(atHandle, rest, ease((t - 0.88f) / 0.12f));
            }
            default -> {
                return rest;
            }
        }
    }

    /**
     * Hoofdstuk 103, stap 2: waar je rechterhand is bij een wapen met de hendel rechts (de AK-47). Ze ligt op de greep, en
     * bij het doorladen laat ze die even los: naar de spanhendel (vlak boven de greep), naar achteren trekken, loslaten en
     * terug. Dezelfde tijden als rack: de hand is er voor de hendel beweegt en blijft er tot hij los is.
     */
    private static Vector3f rightHandRack(FirearmRig rig, @Nullable Firearms.ActionKind action, float t, float handleBack) {
        Vector3f grip = new Vector3f(rig.grip);
        if (action == null) {
            return grip;
        }
        // De vingers om de knop gehaakt: de hand zit rechts onder de hendel, achter het wapen. Zo zie je de hendel
        // bewegen en blijft de kap vrij (een hand is in je beeld veel breder dan de behuizing).
        Vector3f atHandle = new Vector3f(rig.handle).add(1.9f, -3.0f, 0.6f + handleBack);
        Vector3f released = new Vector3f(rig.handle).add(1.9f, -3.0f, 0.6f + rig.handleTravel);
        switch (action) {
            case SLIDE_RELEASE, CYCLE -> {
                if (t < 0.2f) return lerp(grip, atHandle, ease(t / 0.2f));
                if (t < 0.58f) return atHandle;
                if (t < 0.72f) return released;
                return lerp(released, grip, ease((t - 0.72f) / 0.28f));
            }
            case TAP_RACK -> {
                if (t < 0.35f) return grip;
                if (t < 0.45f) return lerp(grip, atHandle, ease((t - 0.35f) / 0.1f));
                if (t < 0.66f) return atHandle;
                if (t < 0.8f) return released;
                return lerp(released, grip, ease((t - 0.8f) / 0.2f));
            }
            case UNLOAD_CHAMBER -> {
                if (t < 0.15f) return lerp(grip, atHandle, ease(t / 0.15f));
                if (t < 0.75f) return atHandle;
                return lerp(atHandle, grip, ease((t - 0.75f) / 0.25f));
            }
            case CHAMBER_ROUND -> {
                if (t < 0.15f) return lerp(grip, atHandle, ease(t / 0.15f));
                if (t < 0.86f) return atHandle;
                return lerp(atHandle, grip, ease((t - 0.86f) / 0.14f));
            }
            default -> {
                return grip;
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
        // Een unload (U, hoofdstuk 54) is dezelfde beweging achterstevoren: de patroon komt eruit.
        boolean unloading = FirearmControl.action() == Firearms.ActionKind.UNLOAD_ROUND;
        float t = loading || unloading ? FirearmControl.action(pt) : -1;

        pose.pushPose();
        Vector3f place = new Vector3f(0.02f, -0.2f - (1 - draw) * 0.4f - 0.05f * sprint, -0.44f);
        place.y += 0.004f * Mth.sin(time * 0.08f);
        float rotX = -12 - (1 - draw) * 30 - 8 * sprint;
        float rotY = 30;
        float rotZ = 10;
        // Hoofdstuk 103: een lang magazijn (de MP5) wordt kleiner getekend, zodat het even groot in je hand ligt als dat van
        // de Glock; {@code k} is hoeveel groter een pixel van de Glock is: zo ver staan je handen en de patroon ervan af.
        float fit = FirearmRig.magazineFit(type);
        float s = rig.scale / 16f * fit;
        float k = 0.44f / (rig.scale * fit);
        float height = FirearmRig.magazineHeight(type);
        // Het magazijn staat in het model zoals in het wapen (bij de Glock schuin): hier recht overeind
        // (FirearmRig.magazineUpright), met zijn midden op {@code place}.
        Matrix4f mag = new Matrix4f()
                .translate(place)
                .rotateY((float) Math.toRadians(rotY))
                .rotateX((float) Math.toRadians(rotX))
                .rotateZ((float) Math.toRadians(rotZ))
                .scale(s)
                .translate(0, -height / 2, 0)
                .mul(FirearmRig.magazineUpright(type));
        pose.mulPose(mag);
        pose.pushPose();
        pose.scale(16, 16, 16);
        FirearmModels.render(pose, buffers, FirearmModels.magazine(stack), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        // De patroon die erin gaat: van rechtsboven naar de bovenkant van het magazijn.
        Vector3f top = new Vector3f(rig.magazineTop);
        Vector3f foot = new Vector3f(rig.magazineFoot);
        if (loading || unloading) {
            float q = ease(Mth.clamp(t / 0.75f, 0, 1));
            Vector3f from = new Vector3f(top).add(4.87f * k, 3.27f * k, -0.7f * k);
            Vector3f at = loading ? lerp(from, top, q) : lerp(top, from, q);
            if (loading ? t < 0.85f : t > 0.08f) {
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                // Liggend, de punt naar voren (-z), zoals de patronen in het magazijn liggen.
                pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(-90)));
                pose.translate(-8, -8.3f, -8);
                pose.scale(16, 16, 16);
                FirearmModels.render(pose, buffers, FirearmModels.round(type.caliber), light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        pose.popPose();

        boolean suit = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof TechSuitItem;
        // Je linkerhand om het onderste stuk van het magazijn, je rechterhand ernaast (of met je duim op de patroon).
        Vector3f hold = new Vector3f(top).sub(foot).mul(0.28f).add(foot).add(0.56f * k, -0.09f * k, 0.13f * k);
        Vector3f away = new Vector3f(foot).add(11.0f * k, -0.3f * k, -1.9f * k);
        Vector3f atTop = new Vector3f(top).add(2.47f * k, 0.97f * k, 0.2f * k); // rechts naast de bovenkant: je duim duwt
        Vector3f leftAt = mag.transformPosition(hold);
        Vector3f rightAt = mag.transformPosition(new Vector3f(away));
        if (loading || unloading) {
            float q = ease(Mth.clamp(t / 0.75f, 0, 1));
            rightAt = mag.transformPosition(loading ? lerp(away, atTop, q) : lerp(atTop, away, q));
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
