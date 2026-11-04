package com.senne.imttech.client.repair;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.breach.BlockReinforcerItem;
import com.senne.imttech.client.ImtClientState;
import com.senne.imttech.client.zone.ZoneBorderRenderer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Het randje om verstevigde blokken (hoofdstuk 44, jouw keuze: "alleen met de bril en het gereedschap in je hand").
 * De server stuurt elke seconde welke blokken in je buurt verstevigd zijn zolang je de Block Reinforcer vasthoudt
 * (zie Cracks.onServerTick); hier tekenen we er een dun staalblauw randje omheen, zoals de oranje doos van de Zone
 * Marker. Zonder bril, of met iets anders in je hand, zie je niets: het blok ziet er gewoon uit.
 */
public final class ReinforcedRenderer {
    private ReinforcedRenderer() {}

    /** Staalblauw, iets lichter dan de zone-rand. */
    private static final int RGB = 0x9FD3FF;
    /** Zo lang (ms) blijven de blokken staan zonder nieuw bericht (de server stuurt elke seconde). */
    private static final long STALE_MS = 2500;

    private static List<BlockPos> blocks = List.of();
    private static long receivedAt;

    public static void set(List<BlockPos> fresh) {
        blocks = fresh;
        receivedAt = System.currentTimeMillis();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || blocks.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ImtClientState.operational || !BlockReinforcerItem.holds(mc.player)
                || System.currentTimeMillis() - receivedAt > STALE_MS) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        VertexConsumer lines = buffers.getBuffer(ZoneBorderRenderer.lines());
        for (BlockPos pos : blocks) {
            if (pos.distToCenterSqr(cam) > (double) BlockReinforcerItem.SHOW_RADIUS * BlockReinforcerItem.SHOW_RADIUS) {
                continue;
            }
            ZoneBorderRenderer.outline(m, lines, pos.getX() + 0.01, pos.getY() + 0.01, pos.getZ() + 0.01,
                    pos.getX() + 0.99, pos.getY() + 0.99, pos.getZ() + 0.99, RGB, 0.75f);
        }
        buffers.endBatch(ZoneBorderRenderer.lines());
        pose.popPose();
    }
}
