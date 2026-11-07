package com.senne.imttech.client.raven;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.raven.RavenAlert;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

/**
 * Het "?" en het "!" boven het hoofd van een bewaker (hoofdstuk 60, jouw antwoord 2: "boven zijn hoofd een ? en dan
 * een !", zoals in Hitman en Metal Gear). Een achterdochtige of zoekende bewaker krijgt een geel vraagteken, een die je
 * heeft (of versterking roept) een rood uitroepteken. Het teken kijkt altijd naar je toe, zoals een naamplaatje, en is
 * ook van een eind te zien. Een Raven-soldaat tekent het (RavenSoldierRenderer), en sinds stap 6 ook een robot of drone.
 */
public final class AlertMarks {
    private AlertMarks() {}

    private static final int YELLOW = 0xFFF0C040;
    private static final int RED = 0xFFFF4545;
    private static final int SHADOW = 0x66000000;

    /** Tekent het teken voor deze toestand boven {@code entity} (niets als hij rustig is). */
    public static void render(EntityRenderDispatcher dispatcher, Font font, Entity entity, RavenAlert alert,
                              PoseStack pose, MultiBufferSource buffers, int packedLight) {
        if (alert == null || !alert.alerted()) {
            return;
        }
        if (entity.distanceToSqr(dispatcher.camera.getPosition()) > 64 * 64) {
            return;
        }
        boolean danger = alert == RavenAlert.COMBAT || alert == RavenAlert.CALLING;
        String mark = danger ? "!" : "?";
        int colour = danger ? RED : YELLOW;
        pose.pushPose();
        pose.translate(0.0, entity.getBbHeight() + 0.75, 0.0);
        pose.mulPose(dispatcher.cameraOrientation());
        // Twee keer zo groot als een naamplaatje, en op zijn kop zoals alle tekst in de wereld.
        pose.scale(-0.05f, -0.05f, 0.05f);
        Matrix4f matrix = pose.last().pose();
        float x = -font.width(mark) / 2.0f;
        // Een lichte pulse zodat het opvalt: het teken wiebelt een beetje omhoog en omlaag.
        float bob = (float) Math.sin((entity.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) * 0.3) * 0.6f;
        font.drawInBatch(mark, x, -4 + bob, colour, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, SHADOW, packedLight);
        font.drawInBatch(mark, x, -4 + bob, colour, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, packedLight);
        pose.popPose();
    }
}
