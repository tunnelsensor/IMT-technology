package com.senne.starktech.client.c4;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.starktech.network.C4AlertPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * De rode markeringen "C4!" boven C4 die iemand op jouw zone plakte (hoofdstuk 10, stap 7). De server
 * zet ze aan en weer uit (zie c4/C4Alarm); je ziet ze door muren heen, met hoe ver ze zijn. Een
 * markering die de server vergat uit te zetten, verdwijnt na een kwartier vanzelf.
 */
public final class C4Alerts {
    private C4Alerts() {}

    /** Zo lang blijft een markering hooguit staan (milliseconden). */
    private static final long MAX_AGE = 15 * 60 * 1000L;

    /** Waar de markeringen staan, en sinds wanneer. */
    private static final Map<GlobalPos, Long> ALERTS = new HashMap<>();

    public static void onPayload(C4AlertPayload payload) {
        if (payload.on()) {
            ALERTS.put(payload.pos(), Util.getMillis());
        } else {
            ALERTS.remove(payload.pos());
        }
    }

    public static void clear() {
        ALERTS.clear();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ALERTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        long now = Util.getMillis();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        // Knipperen: een halve seconde fel, een halve seconde zwakker.
        boolean bright = (now / 500) % 2 == 0;
        for (Iterator<Map.Entry<GlobalPos, Long>> it = ALERTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<GlobalPos, Long> entry = it.next();
            if (now - entry.getValue() > MAX_AGE) {
                it.remove();
                continue;
            }
            GlobalPos pos = entry.getKey();
            if (!pos.dimension().equals(mc.level.dimension())) {
                continue;
            }
            Vec3 at = pos.pos().getCenter().add(0, 0.7, 0);
            double distance = at.distanceTo(mc.player.position());
            if (distance > 160) {
                continue;
            }
            String text = "C4!  " + Math.round(distance) + " m";
            float size = 0.03f * (float) Math.max(1.0, at.distanceTo(cam) / 8.0);
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(camera.rotation());
            pose.scale(size, -size, size);
            Matrix4f m = pose.last().pose();
            float x = -mc.font.width(text) / 2.0f;
            int colour = bright ? 0xFFFF3A2A : 0xFFB02A20;
            mc.font.drawInBatch(text, x, 0, (colour & 0x00FFFFFF) | 0x90000000, false, m, buffers, Font.DisplayMode.SEE_THROUGH,
                    0x70000000, LightTexture.FULL_BRIGHT);
            mc.font.drawInBatch(text, x, 0, colour, false, m, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
        pose.popPose();
    }
}
