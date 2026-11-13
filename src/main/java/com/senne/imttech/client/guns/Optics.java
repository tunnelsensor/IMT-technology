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
        Vector3f mount = mount(rig);
        return switch (optic) {
            case RED_DOT -> mount.add(RED_DOT_EYE);
            case SNIPER_SCOPE -> mount.add(0, SCOPE_AXIS, SCOPE_REAR);
        };
    }

    /** Zo ver voor je oog zit dat punt als je richt (blokken). */
    public static float eyeDistance(FirearmType type, Attachment optic) {
        return switch (optic) {
            // Een pistool houd je op armlengte; een lang wapen tegen je schouder.
            case RED_DOT -> type.longGun ? 0.15f : 0.24f;
            case SNIPER_SCOPE -> 0.072f;
        };
    }

    /**
     * Tekent de kijker op het wapen. {@code pose} staat in de pixels van het wapen (zoals in FirearmRenderer); {@code back}:
     * zo ver staat de slede nu naar achteren (een kijker op de slede van een pistool gaat mee); {@code aim}: hoe ver je
     * gericht bent (0 tot 1), of -1 als dit niet je eigen beeld is (dan is het glas er gewoon).
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, FirearmRig rig, Attachment optic, int light, float back,
                              float aim) {
        Vector3f mount = mount(rig);
        pose.pushPose();
        pose.translate(mount.x, mount.y, mount.z + (rig.sightOnSlide ? back : 0));
        if (rig.sightRiser > 0) {
            // De verhoging: een blokje van 1 pixel hoog, uitgerekt tot onder de voet.
            pose.pushPose();
            pose.translate(0, -rig.sightRiser, 0);
            pose.scale(16, 16 * rig.sightRiser, 16);
            FirearmModels.render(pose, buffers, FirearmModels.location("attachment/sight_riser"), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
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
