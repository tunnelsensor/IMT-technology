package com.senne.starktech.client.suit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.senne.starktech.StarkTech;
import com.senne.starktech.client.DroneCamera;
import com.senne.starktech.client.RepulsorControl;
import com.senne.starktech.suit.StarkSuitItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * De repulsorhand.
 *
 * In je EIGEN BEELD: staat de repulsor aan (toets I) en heb je niets in je hand, dan gaat je
 * arm omhoog en wijst vooruit, met de handpalm naar voren. Die gloeit zacht blauw en klopt
 * een beetje. Vuur je, dan flitst hij fel op en slaat je arm even terug.
 *
 * Voor ANDEREN (en voor jezelf met F5): de arm staat vooruit gericht terwijl de repulsor aan
 * staat (bij anderen: kort na elk schot, want hun spel weet niet wanneer jij hem aanzet), en de
 * handpalm gloeit.
 */
public final class RepulsorHand {
    private RepulsorHand() {}

    /** Zo lang (ticks) duurt het optillen van je arm als je de repulsor aanzet. */
    private static final float RAISE_TICKS = 5;
    /** Zo lang (ticks) na een schot houden anderen hun arm nog vooruit. */
    private static final float AIM_AFTER_SHOT = 40;
    /** Zo lang (ticks) duurt de flits en terugslag van een schot. */
    private static final float RECOIL_TICKS = 6;
    /** Je arm in je eigen beeld iets kleiner dan echt, anders vult hij half het scherm. */
    private static final float ARM_SCALE = 0.85f;
    /** Waar de pols zit, gemeten langs de arm vanaf de schouder (in blokken; de arm is 10/16 lang). */
    private static final float WRIST = 9.5f / 16f;

    private static final ResourceLocation SUIT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/models/armor/stark_layer_1.png");

    /** Waren we de vorige keer al aan het richten? Zo zien we wanneer je arm omhoog moet. */
    private static boolean wasArmed;
    private static float armedAt;
    /** Het armstuk van het harnas (een gewoon armor-model), pas gemaakt als we het nodig hebben. */
    private static HumanoidModel<AbstractClientPlayer> suitModel;

    // ================================================================ Wie richt er?

    /** Staat de arm van deze speler vooruit? Alleen met een lege hand. */
    static boolean aiming(Player player, float partialTick) {
        if (!player.getMainHandItem().isEmpty() || player.isInvisible()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && RepulsorControl.isArmed()) {
            return true;
        }
        return SuitEffects.sinceFired(player, partialTick) < AIM_AFTER_SHOT;
    }

    /** Hoe fel de handpalm gloeit: 0 uit, 0.5 rustig aan, tot 1.5 vlak na een schot. */
    static float glow(Player player, float partialTick, float time) {
        if (!aiming(player, partialTick)) {
            return 0;
        }
        float pulse = 0.45f + 0.1f * Mth.sin(time * 0.35f);
        return pulse + recoil(player, partialTick);
    }

    /** 1 vlak na een schot, zakt in een paar ticks naar 0. */
    private static float recoil(Player player, float partialTick) {
        float since = SuitEffects.sinceFired(player, partialTick);
        if (since >= RECOIL_TICKS) {
            return 0;
        }
        float left = 1 - since / RECOIL_TICKS;
        return left * left;
    }

    // ================================================================ Derde persoon

    /**
     * Vlak voordat een speler getekend wordt: zet zijn arm in de richthouding. (Minecraft heeft
     * de armhouding op dat moment al gekozen, dus wat wij hier zetten blijft staan.)
     */
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!aiming(player, event.getPartialTick())) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        if (player.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = RepulsorArmPose.POSE.getValue();
        } else {
            model.leftArmPose = RepulsorArmPose.POSE.getValue();
        }
    }

    /** Voeg de gloeiende handpalm toe aan het tekenen van spelers (beide soorten skins). */
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new GlowLayer(renderer, skin == PlayerSkin.Model.SLIM));
            }
        }
    }

    /** Tekent het licht in de handpalm, vast aan het uiteinde van de arm. */
    private static final class GlowLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final boolean slim;

        GlowLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, boolean slim) {
            super(parent);
            this.slim = slim;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            float glow = glow(player, partialTick, ageInTicks);
            if (glow <= 0) {
                return;
            }
            HumanoidArm arm = player.getMainArm();
            ModelPart part = arm == HumanoidArm.RIGHT ? getParentModel().rightArm : getParentModel().leftArm;
            pose.pushPose();
            part.translateAndRotate(pose);
            boolean suit = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof StarkSuitItem;
            palm(pose, buffers.getBuffer(SuitEffects.GLOW), arm, slim, suit, glow);
            pose.popPose();
        }
    }

    // ================================================================ Eigen beeld

    /**
     * Minecraft gaat je hand tekenen. Richt je de repulsor met een lege hand, dan doen wij dat:
     * je arm omhoog en vooruit, met een gloeiende handpalm.
     */
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (event.getHand() != InteractionHand.MAIN_HAND || player == null) {
            return;
        }
        float partialTick = event.getPartialTick();
        float time = player.level().getGameTime() + partialTick;
        boolean armed = RepulsorControl.isArmed() && event.getItemStack().isEmpty()
                && !DroneCamera.isActive() && !player.isInvisible();
        if (armed && !wasArmed) {
            armedAt = time;
        }
        wasArmed = armed;
        if (!armed) {
            return;
        }
        event.setCanceled(true);

        HumanoidArm arm = player.getMainArm();
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float raise = Mth.clamp((time - armedAt) / RAISE_TICKS, 0, 1);
        raise = raise * raise * (3 - 2 * raise); // zacht beginnen en zacht stoppen
        float recoil = recoil(player, partialTick);

        // Waar je onderarm naar wijst (x rechts, y omhoog, -z vooruit): vooruit, iets naar binnen
        // en omhoog. Bij de terugslag wipt hij verder omhoog.
        Vector3f dir = new Vector3f(-0.2f * side, 0.16f + 0.3f * recoil, -1).normalize();
        // De pols rechtsonder in beeld; de schouder zit daar een onderarm achter.
        Vector3f wrist = new Vector3f(0.36f * side, -0.34f - (1 - raise) * 0.8f, -1.0f);
        wrist.add(0, 0.004f * Mth.sin(time * 0.12f), 0); // een beetje ademen
        Vector3f shoulder = new Vector3f(dir).mul(-(WRIST * ARM_SCALE + 0.08f * recoil)).add(wrist);

        // De stand van de arm: zijn lengte (y) langs "dir", zijn voorkant (-z) naar boven, net
        // als een echte arm die je naar voren optilt. Dan wijst de buitenkant (-x) vanzelf naar buiten.
        Vector3f down = new Vector3f(0, -1, 0).sub(new Vector3f(dir).mul(-dir.y)).normalize();
        Vector3f across = new Vector3f(dir).cross(down);
        Quaternionf turn = new Matrix3f(across.x, across.y, across.z, dir.x, dir.y, dir.z, down.x, down.y, down.z)
                .getNormalizedRotation(new Quaternionf());

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        boolean suit = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof StarkSuitItem;
        pose.pushPose();
        pose.translate(shoulder.x, shoulder.y, shoulder.z);
        pose.mulPose(turn);
        pose.scale(ARM_SCALE, ARM_SCALE, ARM_SCALE);
        // De onderarm: gewoon je arm.
        drawArm(mc, player, arm, suit, pose, buffers, light);
        // De hand: nog eens je arm, maar half zo lang, bij de pols omhoog geknikt (een kwartslag),
        // zodat de handpalm naar voren wijst. Bij een schot knikt hij nog wat verder door.
        pose.pushPose();
        pose.translate(0, WRIST, -1 / 16f);
        pose.mulPose(Axis.XP.rotationDegrees(-90 - 20 * recoil));
        pose.scale(1, 0.5f, 1);
        drawArm(mc, player, arm, suit, pose, buffers, light);
        pose.popPose();
        pose.popPose();

        // De gloed van de handpalm: je kijkt tegen de rug van je hand, dus tekenen we het licht
        // bovenop, naar je toe gedraaid, alsof het om je hand heen straalt.
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        float face = WRIST + (suit ? 3f : 2f) / 16f + 0.01f;
        Vector3f palm = new Vector3f(palmX(arm, slim), face, -3 / 16f).mul(ARM_SCALE);
        Vector3f centre = turn.transform(palm).add(shoulder);
        halo(pose, buffers.getBuffer(SuitEffects.GLOW_ON_TOP), centre, glow(player, partialTick, time));
    }

    /** Je arm met alles erop: huid, mouw van je skin, en de mouw van het harnas als je dat draagt. */
    private static void drawArm(Minecraft mc, LocalPlayer player, HumanoidArm arm, boolean suit, PoseStack pose,
                                MultiBufferSource buffers, int light) {
        boolean right = arm == HumanoidArm.RIGHT;
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ResourceLocation skin = player.getSkin().texture();
        drawPart(pose, right ? model.rightArm : model.leftArm, buffers.getBuffer(RenderType.entitySolid(skin)), light);
        if (player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE)) {
            drawPart(pose, right ? model.rightSleeve : model.leftSleeve,
                    buffers.getBuffer(RenderType.entityTranslucent(skin)), light);
        }
        if (suit) {
            HumanoidModel<AbstractClientPlayer> suitArm = suitModel(mc);
            drawPart(pose, right ? suitArm.rightArm : suitArm.leftArm,
                    buffers.getBuffer(RenderType.armorCutoutNoCull(SUIT_TEXTURE)), light);
        }
    }

    /** Het licht in je eigen handpalm, als schijven die naar de camera kijken. */
    private static void halo(PoseStack pose, VertexConsumer out, Vector3f tip, float glow) {
        float flash = Math.max(0, glow - 0.55f);
        pose.pushPose();
        pose.translate(tip.x, tip.y, tip.z);
        // Draai de y-as naar de camera (die staat hier in het nulpunt).
        Vector3f toCam = new Vector3f(tip).negate().normalize();
        pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, toCam.x, toCam.y, toCam.z));
        Matrix4f m = pose.last().pose();
        // Het licht wordt opgeteld bij wat eronder zit (je rode handschoen): veel blauw en groen
        // erbij, anders wordt het roze in plaats van blauwwit.
        SuitEffects.disc(m, out, Vec3.ZERO, 0.2f + 0.35f * flash, 0x2F9BFF, 0.25f * glow);
        SuitEffects.disc(m, out, Vec3.ZERO, 0.1f + 0.08f * flash, 0x4FD8FF, 0.7f * Math.min(1, glow));
        SuitEffects.disc(m, out, Vec3.ZERO, 0.045f + 0.03f * flash, 0xE8FCFF, 1f);
        SuitEffects.ring(m, out, Vec3.ZERO, new Vec3(0, 1, 0), 0.06f + 0.02f * flash, 0.012f, 0xBFF4FF,
                0.8f * Math.min(1, glow));
        pose.popPose();
    }

    /** Het midden van de arm ligt niet op de draaias: bij een gewone arm 1 pixel naar buiten. */
    private static float palmX(HumanoidArm arm, boolean slim) {
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        return -side * (slim ? 0.5f : 1f) / 16f;
    }

    /** Net voorbij het uiteinde van de arm; het harnas maakt hem een pixel langer. */
    private static float palmY(boolean suit) {
        return (suit ? 11f : 10f) / 16f + 0.01f;
    }

    /** Teken één stuk van een model, recht en zonder verschuiving (wij hebben het al neergezet). */
    private static void drawPart(PoseStack pose, ModelPart part, VertexConsumer out, int light) {
        PartPose saved = part.storePose();
        part.setPos(0, 0, 0);
        part.setRotation(0, 0, 0);
        part.render(pose, out, light, OverlayTexture.NO_OVERLAY);
        part.loadPose(saved);
    }

    private static HumanoidModel<AbstractClientPlayer> suitModel(Minecraft mc) {
        if (suitModel == null) {
            suitModel = new HumanoidModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        }
        return suitModel;
    }

    // ================================================================ De gloed

    /**
     * Het licht in de handpalm, getekend in de ruimte van de arm: de arm loopt langs +y naar de
     * hand, dus de handpalm is een schijf dwars op de y-as, net voorbij het uiteinde.
     */
    private static void palm(PoseStack pose, VertexConsumer out, HumanoidArm arm, boolean slim, boolean suit,
                             float glow) {
        float flash = Math.max(0, glow - 0.55f);
        Matrix4f m = pose.last().pose();
        Vec3 centre = new Vec3(palmX(arm, slim), palmY(suit), 0);
        SuitEffects.disc(m, out, centre, 0.06f, 0xF2FCFF, Math.min(1, 0.6f + glow * 0.4f));
        SuitEffects.disc(m, out, centre.add(0, 0.002, 0), 0.11f + 0.05f * flash, 0x9FE8FF, 0.55f * Math.min(1, glow));
        SuitEffects.disc(m, out, centre.add(0, 0.004, 0), 0.2f + 0.3f * flash, 0x3FA8FF, 0.22f * glow);
        // Een ring eromheen, zoals de rand van de repulsor.
        SuitEffects.ring(m, out, centre.add(0, 0.003, 0), new Vec3(0, 1, 0), 0.075f, 0.015f, 0xBFF4FF,
                0.7f * Math.min(1, glow));
    }

    public static void reset() {
        wasArmed = false;
        suitModel = null;
    }
}
