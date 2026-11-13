package com.senne.imttech.client.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.guns.Attachment;
import com.senne.imttech.guns.AttachmentSlot;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * De kijkers op een vuurwapen in je spel (hoofdstuk 103, stap 5): waar ze staan, waar je oog is als je erdoor richt, en
 * hoe ze getekend worden. De maten zijn die van tools/attachment_models.py (pixels van het model; het nulpunt van een
 * kijker is het midden van de onderkant van zijn voet, daar waar hij op het wapen staat: {@link FirearmRig#sightMount}).
 *
 * <b>De Red Dot Sight</b>: een open raampje met een rood stipje dat zelf licht geeft. Je oog komt recht achter het
 * stipje; je ziet je wapen gewoon nog (jouw keuze: "je moet altijd je wapen nog zien bij de red dot, zoals TACZ").
 *
 * <b>De Sniper Scope</b>: je oog komt vlak achter het oculair. Jouw keuze: "bij de sniperscope kijk je erin, maar je ziet
 * je 3D-model nog een beetje": geen zwart beeld. Daarom zoomt de hele wereld in (zie FirearmControl) en tekenen we de
 * kijker gewoon als model: je ziet de rand van het oculair om het midden van je beeld, met het dradenkruis erin, en
 * daaronder nog een stuk van je wapen. Eén probleem: recht achter het oculair liggen de rest van de kijker en de loop,
 * en die zou je dwars door het glas zien. Daarom tekenen we in de opening van het oculair eerst een onzichtbaar schijfje
 * dat alleen de <i>diepte</i> invult ({@link RenderType#waterMask}, zoals Minecraft het water uit een boot houdt): alles
 * van het wapen wat daarachter ligt wordt dan niet meer getekend, en de wereld (die er al stond) blijft staan.
 */
public final class Optics {
    private Optics() {}

    /** De Red Dot Sight: waar het stipje zweeft, vanaf de montage. */
    private static final Vector3f RED_DOT_EYE = new Vector3f(0, 1.46f, -1.2f);
    /** De Sniper Scope: de as van de buis boven de montage, en hoe ver het achterste vlak van het oculair achter de montage ligt. */
    private static final float SCOPE_AXIS = 2.75f;
    private static final float SCOPE_REAR = 16.29f;
    /** De straal van de opening van het oculair, en uit hoeveel vlakjes de buis rondom bestaat (ROUND in het script). */
    private static final float SCOPE_HOLE = 1.55f;
    private static final int SCOPE_ROUND = 24;

    /** Vanaf hier (0 = heup, 1 = helemaal gericht) kijk je door de kijker: het glas wordt doorzichtig, het dradenkruis komt. */
    public static final float THROUGH = 0.7f;

    /**
     * Waar een kijker op een wapen staat (hoofdstuk 103, stap 7: ook op een lasergeweer): het punt waar zijn voet staat
     * (met een verhoging erbij), hoe hoog die verhoging is, of hij op het bewegende deel staat (de slede van een
     * pistool), en hoe groot hij getekend wordt (de Sniper Scope op de kleinere Laser Sniper: driekwart).
     */
    public record Seat(Vector3f mount, float riser, boolean onSlide, float scale) {}

    /** De plek van een kijker op dit vuurwapen. */
    public static Seat seat(FirearmRig rig) {
        return new Seat(mount(rig), rig.sightRiser, rig.sightOnSlide, 1.0f);
    }

    /** Het punt dat voor je oog komt als je door deze kijker richt, voor een kijker op deze plek. */
    public static Vector3f eye(Seat seat, Attachment optic) {
        Vector3f offset = optic == Attachment.SNIPER_SCOPE ? new Vector3f(0, SCOPE_AXIS, SCOPE_REAR) : new Vector3f(RED_DOT_EYE);
        return new Vector3f(seat.mount()).add(offset.mul(seat.scale()));
    }

    /** De kijker op dit wapen, of null. */
    public static @Nullable Attachment of(ItemStack gun) {
        return FirearmItem.attachment(gun, AttachmentSlot.SIGHT);
    }

    /** Waar de voet van een kijker op dit wapen staat (model-pixels), met de verhoging erbij. */
    public static Vector3f mount(FirearmRig rig) {
        return new Vector3f(rig.sightMount).add(0, rig.sightRiser, 0);
    }

    /** Het punt op het wapen dat voor je oog komt als je door deze kijker richt (model-pixels). */
    public static Vector3f eye(FirearmRig rig, Attachment optic) {
        return eye(seat(rig), optic);
    }

    /**
     * Zo ver voor je oog zit dat punt als je richt (blokken). Hoofdstuk 105: de lange wapens werden groter getekend (0,34
     * werd {@link FirearmRig#SCALE}); deze afstanden groeiden even hard mee, zodat het beeld door de red dot en door de
     * kijker precies blijft zoals het was. De Glock werd kleiner (0,44 werd 0,37) en staat een tikje verder van je oog,
     * zoals zijn ijzeren vizier: hij is gericht een vijfde kleiner in beeld dan vroeger.
     */
    public static float eyeDistance(FirearmType type, Attachment optic) {
        return switch (optic) {
            case SNIPER_SCOPE -> 0.0784f;
            // Een pistool houd je op armlengte; een lang wapen tegen je schouder.
            default -> type.longGun ? 0.163f : 0.252f;
        };
    }

    /**
     * Tekent de kijker op het wapen. {@code pose} staat in de pixels van het wapen (zoals in FirearmRenderer); {@code back}:
     * zo ver staat de slede nu naar achteren (een kijker op de slede van een pistool gaat mee); {@code aim}: hoe ver je
     * gericht bent (0 tot 1), of -1 als dit niet je eigen beeld is (dan is het glas er gewoon).
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, FirearmRig rig, Attachment optic, int light, float back,
                              float aim) {
        render(pose, buffers, seat(rig), optic, light, back, aim);
    }

    /** Hetzelfde voor een kijker op deze plek (een vuurwapen of een lasergeweer). */
    public static void render(PoseStack pose, MultiBufferSource buffers, Seat seat, Attachment optic, int light, float back,
                              float aim) {
        Vector3f mount = seat.mount();
        pose.pushPose();
        pose.translate(mount.x, mount.y, mount.z + (seat.onSlide() ? back : 0));
        if (seat.riser() > 0) {
            // De verhoging: een blokje van 1 pixel hoog, uitgerekt tot onder de voet.
            pose.pushPose();
            pose.translate(0, -seat.riser(), 0);
            pose.scale(16, 16 * seat.riser(), 16);
            FirearmModels.render(pose, buffers, FirearmModels.location("attachment/sight_riser"), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.scale(seat.scale(), seat.scale(), seat.scale());
        if (optic == Attachment.SNIPER_SCOPE && aim >= THROUGH) {
            mask(pose, buffers);
        }
        pose.scale(16, 16, 16);
        FirearmModels.render(pose, buffers, FirearmModels.attachment(optic), light, OverlayTexture.NO_OVERLAY);
        if (optic == Attachment.SNIPER_SCOPE) {
            String extra = aim >= THROUGH ? "attachment/sniper_scope_reticle" : "attachment/sniper_scope_lens";
            FirearmModels.render(pose, buffers, FirearmModels.location(extra), light, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
    }

    /**
     * Het onzichtbare schijfje in de opening van het oculair (zie de uitleg bovenaan): het vult alleen de diepte in, en
     * wordt meteen getekend, voor de rest van het wapen aan de beurt komt.
     */
    private static void mask(PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer out = buffers.getBuffer(RenderType.waterMask());
        Matrix4f matrix = pose.last().pose();
        float z = SCOPE_REAR - 0.3f;
        float r = SCOPE_HOLE - 0.01f;
        int steps = SCOPE_ROUND;
        for (int i = 0; i < steps; i += 2) {
            // Een taartpunt van twee stappen als vierhoek: het midden en drie punten op de rand (naar je oog gekeerd).
            out.addVertex(matrix, 0, SCOPE_AXIS, z);
            for (int k = 0; k <= 2; k++) {
                double a = 2 * Math.PI * (i + k) / steps;
                out.addVertex(matrix, (float) (r * Math.cos(a)), SCOPE_AXIS + (float) (r * Math.sin(a)), z);
            }
        }
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(RenderType.waterMask());
        }
    }
}
