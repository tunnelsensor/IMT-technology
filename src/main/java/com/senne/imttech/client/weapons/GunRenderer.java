package com.senne.imttech.client.weapons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.client.suit.LaserCannonHand;
import com.senne.imttech.client.suit.SuitEffects;
import com.senne.imttech.suit.TechSuitItem;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.LaserPart;
import com.senne.imttech.weapons.LaserParts;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Het lasergeweer in je eigen beeld (hoofdstuk 35, jouw wens: "goede models/animaties zoals bij TACZ"). In plaats van
 * het gewone item in je hand tekenen we het geweer uit zijn vier onderdelen (WeaponModels), met je twee armen eraan:
 * je rechterhand aan de greep, je linkerhand onder de loop. Elk onderdeel kan apart bewegen:
 * <ul>
 *   <li><b>pakken</b>: het geweer komt van onder je beeld omhoog;</li>
 *   <li><b>schieten</b>: het geweer springt terug en omhoog, de trekker gaat in, het bewegende deel schiet terug (de
 *       slede van het pistool, de spanhendel van het geweer; de grendel van de sniper en de pomp van de shotgun gaan
 *       daarna heen en weer), en er is een flits aan de loop;</li>
 *   <li><b>richten</b>: het vizier schuift recht voor je oog (de sniper laat dan zijn kijker over je scherm zien, zie
 *       GunHud);</li>
 *   <li><b>herladen</b>: het geweer kantelt, de cel schuift eruit en valt, je linkerhand haalt een nieuwe, klikt hem erin,
 *       en het lichtbalkje laadt op;</li>
 *   <li><b>bekijken</b> (H): je draait het geweer om het van opzij en van boven te bekijken;</li>
 *   <li><b>sprinten</b>: het geweer zakt naar beneden.</li>
 * </ul>
 * Alle maten van het geweer zelf staan in GunRig; hoe ver elke animatie is, weet GunControl.
 */
public final class GunRenderer {
    private GunRenderer() {}

    /** Je armen in beeld iets kleiner dan echt (net als bij de laser cannon). */
    private static final float ARM_SCALE = 0.78f;
    /** Waar de hand zit, gemeten langs de arm vanaf de schouder (blokken; een arm is 10/16 lang). */
    private static final float WRIST = 9.0f / 16f;
    /** Waar je schouders ongeveer zitten (rechts en links, onder je beeld). */
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(0.6f, -0.85f, 0.35f);
    private static final Vector3f LEFT_SHOULDER = new Vector3f(-0.6f, -0.9f, 0.25f);

    /** Waar de loop vorige frame in beeld zat (in "scherm-eenheden"), voor de laserbouten van je eigen schoten. */
    private static float muzzleX;
    private static float muzzleY = -0.3f;
    private static boolean muzzleKnown;

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GunType type = GunControl.held();
        if (player == null || type == null) {
            muzzleKnown = false;
            return;
        }
        // Met een geweer vast heb je je andere hand nodig: wat daarin zit, tekenen we niet.
        if (event.getHand() == InteractionHand.OFF_HAND) {
            event.setCanceled(true);
            return;
        }
        ItemStack stack = event.getItemStack();
        if (LaserGunItem.typeOf(stack) != type || player.isInvisible()) {
            return;
        }
        event.setCanceled(true);
        float pt = event.getPartialTick();
        GunRig rig = GunRig.of(type);
        float aim = GunControl.aim(pt);
        // De sniper: helemaal gericht zie je alleen nog de kijker (die tekent GunHud).
        if (GunControl.scoped(pt)) {
            muzzleX = 0;
            muzzleY = 0;
            muzzleKnown = true;
            return;
        }
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float time = GunControl.time(pt);
        float reload = GunControl.reload(pt);
        // Alleen de cel eruit (hoofdstuk 57): een eigen, korter verloop; "swap" is de hele wissel (eruit en een nieuwe erin).
        boolean eject = reload >= 0 && GunControl.ejectOnly();
        float swap = eject ? -1 : reload;
        float inspect = GunControl.inspect(pt);
        float since = GunControl.sinceShot(pt);

        pose.pushPose();
        // ---- Waar het geweer hangt: vanuit de heup (de greep rechtsonder) of gericht (het vizier recht voor je oog).
        Vector3f pivot = lerp(rig.grip, rig.sight, aim);
        Vector3f place = lerp(rig.hip, new Vector3f(0, 0, -rig.sightDistance), aim);
        float rotX = 0;
        float rotY = 4 * (1 - aim);
        float rotZ = -2 * (1 - aim);
        // Ademen (heel zacht, minder als je richt).
        float breathe = 1 - 0.7f * aim;
        place.y += 0.004f * Mth.sin(time * 0.08f) * breathe;
        rotX += 0.4f * Mth.sin(time * 0.06f) * breathe;
        // Pakken: van onder je beeld omhoog.
        float draw = ease(GunControl.draw(pt));
        place.y -= (1 - draw) * 0.45f;
        rotX -= (1 - draw) * 35;
        // Sprinten: het geweer zakt en draait opzij.
        float sprint = GunControl.sprint(pt);
        place.add(-0.03f * sprint, -0.035f * sprint, 0.02f * sprint);
        rotX -= 12 * sprint;
        rotY += 22 * sprint;
        rotZ += 10 * sprint;
        // Schieten: terug en omhoog, dan snel weer stil.
        float kick = recoil(since);
        place.z += rig.kickBack * kick * (1 - 0.4f * aim);
        rotX += rig.kickUp * kick * (1 - 0.5f * aim);
        // Herladen: kantelen (en weer terug). Bij alleen de cel eruit: kantelen, de cel valt weg, en terug.
        if (reload >= 0) {
            float tilt = eject ? ease(Mth.clamp(reload / 0.15f, 0, 1)) * ease(Mth.clamp((1 - reload) / 0.2f, 0, 1))
                    : ease(Mth.clamp(reload / 0.12f, 0, 1)) * ease(Mth.clamp((1 - reload) / 0.14f, 0, 1));
            // Met de klok mee kantelen: dan wijst de onderkant (de cel) naar het midden van je beeld.
            rotZ -= 32 * tilt;
            rotX += 14 * tilt;
            rotY += 8 * tilt;
            place.add(-0.07f * tilt, 0.08f * tilt, 0.03f * tilt);
            // De klik als de nieuwe cel erin gaat: een tikje.
            float slap = bump(swap, 0.57f, 0.05f);
            place.y -= 0.012f * slap;
        }
        // Bekijken: eerst de zijkant, dan de bovenkant, en terug.
        if (inspect >= 0) {
            float side = ease(Mth.clamp(inspect / 0.15f, 0, 1)) * ease(Mth.clamp((1 - inspect) / 0.15f, 0, 1));
            float top = ease(Mth.clamp((inspect - 0.48f) / 0.14f, 0, 1)) * ease(Mth.clamp((0.88f - inspect) / 0.12f, 0, 1));
            rotY -= 48 * side - 18 * top;
            rotZ -= 14 * side;
            rotX += 32 * top;
            place.add(-0.09f * side, 0.06f * side + 0.03f * top, 0.06f * side);
            rotZ += 3 * Mth.sin(inspect * 14) * side * (1 - top);
        }
        // De stand van het geweer als één matrix in de ruimte van je hand (x rechts, y omhoog, -z vooruit). Die
        // gebruiken we ook voor je armen: hun schouders staan in dezelfde ruimte.
        float s = rig.scale / 16f;
        Matrix4f gun = new Matrix4f()
                .translate(place)
                .rotateY((float) Math.toRadians(rotY))
                .rotateX((float) Math.toRadians(rotX))
                .rotateZ((float) Math.toRadians(rotZ))
                .scale(s)
                .translate(-pivot.x, -pivot.y, -pivot.z);
        pose.mulPose(gun);

        // ---- De onderdelen (hoofdstuk 55): wat erin zit, elk in zijn kwaliteit; wat ontbreekt zie je niet.
        String name = type.id;
        LaserParts parts = LaserGunItem.parts(stack);
        for (LaserPart p : LaserPart.wearing()) {
            PartState state = parts.get(p);
            if (state != null && p != LaserPart.TRIGGER) {
                model(pose, buffers, WeaponModels.part(type, p, state.material()), light);
            }
        }
        if (parts.hasCore()) {
            part(pose, buffers, name, "core", light);
        }
        // De trekker: twee ticks ingedrukt na een schot (of een lege klik).
        float trigger = Math.max(since < 2 ? 1 : 0, GunControl.sinceEmpty(pt) < 2 ? 1 : 0);
        PartState triggerState = parts.get(LaserPart.TRIGGER);
        if (triggerState != null) {
            pose.pushPose();
            pose.translate(0, 0, 0.45f * trigger);
            model(pose, buffers, WeaponModels.part(type, LaserPart.TRIGGER, triggerState.material()), light);
            pose.popPose();
        }
        // Het bewegende deel (de slede, de spanhendel, de grendel, de pomp): van elk onderdeel dat er een heeft.
        float moving = movingOffset(type, rig, since, swap);
        pose.pushPose();
        if (type == GunType.SNIPER) {
            // De grendel gaat eerst omhoog en dan naar achteren.
            pose.translate(0, 0.6f * Math.min(1, moving * 2), moving * rig.cycle);
        } else {
            pose.translate(0, 0, moving * rig.cycle);
        }
        for (LaserPart p : LaserPart.wearing()) {
            PartState state = parts.get(p);
            if (state != null && LaserRig.moves(type, p)) {
                model(pose, buffers, WeaponModels.moving(type, p, state.material()), light);
            }
        }
        if (rig.displayMoves) {
            display(pose, buffers, rig, stack, swap);
        }
        pose.popPose();
        if (!rig.displayMoves) {
            display(pose, buffers, rig, stack, swap);
        }
        // De cel (bij herladen: eruit, weg, en een nieuwe erin; bij alleen eruit: eruit en weg).
        float cellOut = eject ? ejectOffset(reload) : cellOffset(swap);
        boolean hasCell = eject || LaserGunItem.cell(stack) >= 0 || swap >= 0;
        if (hasCell && cellOut < 30) {
            pose.pushPose();
            Vector3f down = cellAxis(type);
            pose.translate(down.x * cellOut, down.y * cellOut, down.z * cellOut);
            part(pose, buffers, name, "cell", light);
            pose.popPose();
        }
        // De flits aan de loop.
        if (since < 2.2f) {
            flash(pose, buffers.getBuffer(SuitEffects.GLOW), rig.muzzle, 1 - since / 2.2f);
        }
        pose.popPose();

        // ---- Waar de loop in beeld zit (voor de laserbout van dit schot), in de eenheden van het scherm.
        Vector3f muzzle = gun.transformPosition(new Vector3f(rig.muzzle));
        if (muzzle.z < -0.05f) {
            float tan = (float) Math.tan(Math.toRadians(70) / 2);
            float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
            muzzleX = muzzle.x / (-muzzle.z * tan * aspect);
            muzzleY = muzzle.y / (-muzzle.z * tan);
            muzzleKnown = true;
        }

        // ---- Je armen.
        boolean suit = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof TechSuitItem;
        Vector3f right = gun.transformPosition(new Vector3f(rig.grip));
        Vector3f left = gun.transformPosition(eject ? ejectHand(type, rig, reload, moving, cellOut)
                : leftHand(type, rig, swap, moving, cellOut));
        HumanoidArm main = player.getMainArm();
        arm(mc, player, main, suit, right, main == HumanoidArm.RIGHT ? RIGHT_SHOULDER : LEFT_SHOULDER, pose, buffers, light);
        if (left != null) {
            HumanoidArm other = main.getOpposite();
            arm(mc, player, other, suit, left, other == HumanoidArm.RIGHT ? RIGHT_SHOULDER : LEFT_SHOULDER, pose, buffers, light);
        }
    }

    /**
     * Waar de laserbout van je eigen schot begint: het punt in de wereld dat op je scherm precies op de loop van je
     * geweer valt, een halve meter voor je. (Je geweer wordt met een andere lens getekend dan de wereld; zo klopt het
     * toch.) Null als we het niet weten (geen geweer, of in derde persoon).
     */
    public static Vec3 ownMuzzle(Minecraft mc, float partialTick) {
        if (!muzzleKnown || !mc.options.getCameraType().isFirstPerson()) {
            return null;
        }
        var camera = mc.gameRenderer.getMainCamera();
        double fov = GunControl.worldFov();
        float tan = (float) Math.tan(Math.toRadians(fov) / 2);
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        float distance = 0.6f;
        Vector3f offset = new Vector3f(look)
                .add(new Vector3f(left).mul(-muzzleX * tan * aspect))
                .add(new Vector3f(up).mul(muzzleY * tan))
                .mul(distance);
        return camera.getPosition().add(offset.x, offset.y, offset.z);
    }

    // ================================================================ De animaties

    /** De terugslag na een schot: in één tick naar 1, dan wegzakken. */
    private static float recoil(float since) {
        if (since < 0) {
            return 0;
        }
        return since < 1 ? since : (float) Math.exp(-(since - 1) * 0.55);
    }

    /**
     * Hoe ver het bewegende deel nu terug staat (0 tot 1): de slede van het pistool en de spanhendel van het geweer
     * schieten meteen terug en veren weer naar voren; de grendel van de sniper en de pomp van de shotgun gaan pas na een
     * paar ticks heen en weer. Bij herladen gaat het deel ook een keer heen en weer (het geweer laadt op).
     */
    private static float movingOffset(GunType type, GunRig rig, float since, float reload) {
        float value = 0;
        if (since >= 0) {
            if (type == GunType.SNIPER || type == GunType.SHOTGUN) {
                float t = (since - 4) / rig.cycleTicks;
                if (t > 0 && t < 1) {
                    value = Mth.sin(t * Mth.PI);
                }
            } else if (since < rig.cycleTicks + 1) {
                value = since < 1 ? since : 1 - (since - 1) / rig.cycleTicks;
            }
        }
        if (reload >= 0) {
            float t = (reload - 0.68f) / 0.14f;
            if (t > 0 && t < 1) {
                value = Math.max(value, Mth.sin(t * Mth.PI));
            }
        }
        return Mth.clamp(value, 0, 1);
    }

    /**
     * Hoe ver de cel onder het geweer uit staat (pixels van het model) tijdens het herladen: eruit schuiven, wegvallen,
     * een nieuwe van onder aanvoeren en erin klikken. Meer dan 30 = uit beeld (niet tekenen).
     */
    private static float cellOffset(float reload) {
        if (reload < 0) {
            return 0;
        }
        if (reload < 0.12f) {
            return 0;
        }
        if (reload < 0.28f) {
            return 4 * ease((reload - 0.12f) / 0.16f);
        }
        if (reload < 0.38f) {
            float t = (reload - 0.28f) / 0.10f;
            return 4 + 40 * t * t; // valt weg
        }
        if (reload < 0.42f) {
            return 99;
        }
        if (reload < 0.54f) {
            return 16 - 13 * ease((reload - 0.42f) / 0.12f);
        }
        if (reload < 0.58f) {
            return 3 - 3 * ease((reload - 0.54f) / 0.04f);
        }
        return 0;
    }

    /**
     * Hetzelfde voor alleen de cel eruit (hoofdstuk 57): eruit schuiven en wegvallen, en er komt geen nieuwe. Meer dan
     * 30 = uit beeld.
     */
    private static float ejectOffset(float eject) {
        if (eject < 0.15f) {
            return 0;
        }
        if (eject < 0.4f) {
            return 4 * ease((eject - 0.15f) / 0.25f);
        }
        if (eject < 0.6f) {
            float t = (eject - 0.4f) / 0.2f;
            return 4 + 40 * t * t; // valt weg
        }
        return 99;
    }

    /** Je linkerhand bij alleen de cel eruit: naar de cel, hem eruit trekken en laten vallen, en terug. */
    private static Vector3f ejectHand(GunType type, GunRig rig, float eject, float moving, float cellOut) {
        Vector3f rest = new Vector3f(rig.support);
        if (type == GunType.SHOTGUN) {
            rest.z += moving * rig.cycle;
        }
        Vector3f axis = cellAxis(type);
        Vector3f atCell = new Vector3f(rig.cell).add(new Vector3f(axis).mul(Math.min(cellOut, 18) + 1.5f));
        Vector3f below = new Vector3f(rig.cell).add(new Vector3f(axis).mul(24)).add(-3, 0, 4);
        if (eject < 0.1f) {
            return rest;
        }
        if (eject < 0.2f) {
            return lerp(rest, atCell, ease((eject - 0.1f) / 0.1f));
        }
        if (eject < 0.45f) {
            return atCell;
        }
        if (eject < 0.6f) {
            return lerp(atCell, below, ease((eject - 0.45f) / 0.15f));
        }
        if (eject < 0.85f) {
            return lerp(below, rest, ease((eject - 0.6f) / 0.25f));
        }
        return rest;
    }

    /** De richting waarin de cel uit het geweer schuift (bij het pistool schuin, langs de greep). */
    private static Vector3f cellAxis(GunType type) {
        if (type == GunType.PISTOL) {
            float a = (float) Math.toRadians(22.5);
            return new Vector3f(0, -Mth.cos(a), Mth.sin(a));
        }
        return new Vector3f(0, -1, 0);
    }

    /**
     * Waar je linkerhand is (in de maten van het model): onder de loop, of bij de shotgun op de pomp (die beweegt mee).
     * Bij herladen gaat hij naar de cel, haalt beneden een nieuwe en duwt die erin; bij het pistool houdt hij je
     * rechterhand vast.
     */
    private static Vector3f leftHand(GunType type, GunRig rig, float reload, float moving, float cellOut) {
        Vector3f rest = new Vector3f(rig.support);
        if (type == GunType.SHOTGUN) {
            rest.z += moving * rig.cycle;
        }
        if (reload < 0) {
            return rest;
        }
        Vector3f axis = cellAxis(type);
        Vector3f atCell = new Vector3f(rig.cell).add(new Vector3f(axis).mul(Math.min(cellOut, 18) + 1.5f));
        Vector3f below = new Vector3f(rig.cell).add(new Vector3f(axis).mul(24)).add(-3, 0, 4);
        if (reload < 0.08f) {
            return rest;
        }
        if (reload < 0.16f) {
            return lerp(rest, atCell, ease((reload - 0.08f) / 0.08f));
        }
        if (reload < 0.30f) {
            return atCell;
        }
        if (reload < 0.42f) {
            return lerp(atCell, below, ease((reload - 0.30f) / 0.12f));
        }
        if (reload < 0.58f) {
            return cellOut < 30 ? atCell : below;
        }
        if (reload < 0.70f) {
            return lerp(atCell, rest, ease((reload - 0.58f) / 0.12f));
        }
        return rest;
    }

    // ================================================================ Tekenen

    /**
     * Eén onderdeel tekenen. Wij rekenen in pixels van het model (16 = één blok), maar de modellen die Minecraft inlaadt
     * zijn al in blokken: daarom hier even 16 keer groter.
     */
    private static void part(PoseStack pose, MultiBufferSource buffers, String name, String part, int light) {
        pose.pushPose();
        pose.scale(16, 16, 16);
        WeaponModels.render(pose, buffers, name, part, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Hetzelfde voor een model op zijn volledige naam (een onderdeel in zijn kwaliteit, hoofdstuk 55). */
    private static void model(PoseStack pose, MultiBufferSource buffers,
                              net.minecraft.client.resources.model.ModelResourceLocation location, int light) {
        pose.pushPose();
        pose.scale(16, 16, 16);
        WeaponModels.render(pose, buffers, location, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Het lichtbalkje: tien vakjes langs de zijkant, zoveel aan als de cel vol is (bij herladen lopen ze vol). */
    private static void display(PoseStack pose, MultiBufferSource buffers, GunRig rig, ItemStack stack, float reload) {
        float charge = Math.max(0, LaserGunItem.cell(stack)) / EnergyCellItem.FULL;
        if (reload >= 0) {
            charge = reload < 0.6f ? 0 : Mth.clamp((reload - 0.6f) / 0.25f, 0, 1);
        }
        VertexConsumer out = buffers.getBuffer(SuitEffects.GLOW);
        Matrix4f m = pose.last().pose();
        int segments = 10;
        float step = rig.displayLength / segments;
        float x = rig.display.x;
        float y = rig.display.y;
        float h = 0.45f;
        for (int i = 0; i < segments; i++) {
            float z0 = rig.display.z - i * step;
            float z1 = z0 - step * 0.7f;
            boolean on = charge * segments > i + 0.01f;
            int rgb = on ? (charge < 0.2f ? 0xFF5040 : 0x6FE8FF) : 0x0A2A33;
            float alpha = on ? 0.95f : 0.5f;
            SuitEffects.quad(m, out, new Vec3(x, y, z0), new Vec3(x, y + h, z0), new Vec3(x, y + h, z1), new Vec3(x, y, z1), rgb, alpha);
        }
    }

    /** De mondingsvlam: een felle ster van licht voor de loop. */
    private static void flash(PoseStack pose, VertexConsumer out, Vector3f muzzle, float strength) {
        Matrix4f m = pose.last().pose();
        Vec3 c = new Vec3(muzzle.x, muzzle.y, muzzle.z - 1.2f);
        SuitEffects.disc(m, out, c, new Vec3(0, 0, 1), 3.2f * strength, 0x3FA8FF, 0.5f * strength);
        SuitEffects.disc(m, out, c, new Vec3(0, 0, 1), 1.6f * strength, 0xBFF4FF, 0.9f * strength);
        SuitEffects.disc(m, out, c.add(0, 0, -0.5), new Vec3(0, 0, 1), 0.8f, 0xFFFFFF, strength);
        for (int i = 0; i < 4; i++) {
            float a = i * Mth.HALF_PI + 0.4f;
            Vec3 tip = c.add(Mth.cos(a) * 4.5f * strength, Mth.sin(a) * 4.5f * strength, -1.5f);
            SuitEffects.quad(m, out, c, c.add(Mth.cos(a + 0.3f) * 0.6f, Mth.sin(a + 0.3f) * 0.6f, 0), tip,
                    c.add(Mth.cos(a - 0.3f) * 0.6f, Mth.sin(a - 0.3f) * 0.6f, 0), 0x8FE6FF, 0.7f * strength);
        }
    }

    /**
     * Eén arm, van een schouder onder je beeld naar {@code hand}. De arm wijst van de schouder naar de hand, met de
     * handpalm naar binnen (naar het geweer), zoals je een greep vasthoudt.
     */
    private static void arm(Minecraft mc, LocalPlayer player, HumanoidArm side, boolean suit, Vector3f hand,
                            Vector3f shoulderGuess, PoseStack pose, MultiBufferSource buffers, int light) {
        Vector3f dir = new Vector3f(hand).sub(shoulderGuess).normalize();
        Vector3f shoulder = new Vector3f(hand).sub(new Vector3f(dir).mul(WRIST * ARM_SCALE));
        float sign = side == HumanoidArm.RIGHT ? 1 : -1;
        // "Naar binnen": voor de rechterarm naar links, voor de linkerarm naar rechts, dwars op de arm.
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

    // ================================================================ Hulpjes

    private static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, t);
    }

    /** Zacht beginnen en zacht stoppen (0 tot 1). */
    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Een kort bultje rond {@code at} (breedte {@code width}): 0, dan 1, dan weer 0. */
    private static float bump(float t, float at, float width) {
        float d = Math.abs(t - at) / width;
        return d >= 1 ? 0 : 1 - d * d;
    }
}
