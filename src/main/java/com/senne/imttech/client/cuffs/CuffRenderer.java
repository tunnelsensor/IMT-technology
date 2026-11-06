package com.senne.imttech.client.cuffs;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.client.zone.ZoneBorderRenderer;
import com.senne.imttech.cuffs.Handcuffs;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * De handboeien in beeld (hoofdstuk 48; sinds hoofdstuk 54 zonder lange lijn): om de polsen van wie geboeid is, achter
 * zijn rug, een donker bandje (zijn handen zitten daar, zie CuffArmPoses), en van de handen van wie hem vasthoudt (vlak
 * achter hem, de handen vooruit) een kort stukje ketting naar die boeien. Wie geboeid is en door wie, komt mee met het
 * wezen (de attachment CUFFS). Wie iemand vasthoudt, onthouden we hier per frame: zo weet de tekenaar van spelers dat
 * zijn handen vooruit moeten.
 */
public final class CuffRenderer {
    private CuffRenderer() {}

    /** De spelers die op dit moment iemand vasthouden (het vorige frame; genoeg voor de armen). */
    private static final java.util.Set<UUID> HOLDERS = new java.util.HashSet<>();

    /** Houdt deze speler iemand aan de boeien vast? */
    public static boolean isHolding(UUID player) {
        return HOLDERS.contains(player);
    }

    /** De armen van spelers (hoofdstuk 54): geboeid = handen op de rug; iemand vasthouden = handen vooruit aan de boeien. */
    public static void onRenderPlayer(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        net.minecraft.client.model.PlayerModel<?> model = event.getRenderer().getModel();
        if (Handcuffs.isCuffed(player)) {
            model.rightArmPose = CuffArmPoses.CUFFED.getValue();
            model.leftArmPose = CuffArmPoses.CUFFED.getValue();
        } else if (isHolding(player.getUUID())) {
            model.rightArmPose = CuffArmPoses.HOLDING.getValue();
            model.leftArmPose = CuffArmPoses.HOLDING.getValue();
        }
    }

    /** Touwkleur (beige) en de boeien zelf (donker staal). */
    private static final int ROPE = 0xD2BC8E;
    private static final int CUFF = 0x3A4048;
    /** Hoeveel stukjes het touw heeft. */
    private static final int SEGMENTS = 12;

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean any = false;
        VertexConsumer out = null;
        Matrix4f m = null;
        HOLDERS.clear();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !Handcuffs.isCuffed(living) || living.distanceToSqr(cam) > 64 * 64) {
                continue;
            }
            if (!any) {
                any = true;
                pose.pushPose();
                pose.translate(-cam.x, -cam.y, -cam.z);
                m = pose.last().pose();
                out = buffers.getBuffer(ZoneBorderRenderer.lines());
            }
            Vec3 at = living.getPosition(partial);
            // Het bandje om zijn polsen: een dun donker balkje op heuphoogte, achter zijn rug.
            double hip = at.y + living.getBbHeight() * 0.42;
            float yaw = living instanceof Player ? living.yBodyRotO + (living.yBodyRot - living.yBodyRotO) * partial : living.getYRot();
            Vec3 back = new Vec3(Mth.sin(yaw * Mth.DEG_TO_RAD), 0, -Mth.cos(yaw * Mth.DEG_TO_RAD)).scale(living.getBbWidth() * 0.35);
            Vec3 side = new Vec3(-back.z, 0, back.x).normalize().scale(0.16);
            Vec3 wrists = new Vec3(at.x, hip, at.z).add(back);
            segment(m, out, wrists.subtract(side), wrists.add(side), 0.035, CUFF, 0.95f);
            UUID holderId = Handcuffs.holderOf(living);
            Player holder = holderId == null ? null : mc.level.getPlayerByUUID(holderId);
            if (holder == null || holder == living) {
                continue;
            }
            HOLDERS.add(holderId);
            // Het stukje ketting: van de handen van wie hem vasthoudt (vooruit, op borsthoogte) naar zijn polsen.
            Vec3 look = holder.getViewVector(partial);
            Vec3 hand = holder.getEyePosition(partial).add(new Vec3(look.x, 0, look.z).normalize().scale(0.55)).subtract(0, 0.6, 0);
            double distance = hand.distanceTo(wrists);
            double sag = Math.min(0.25, 0.1 * distance);
            Vec3 previous = hand;
            for (int i = 1; i <= SEGMENTS; i++) {
                double t = (double) i / SEGMENTS;
                Vec3 point = hand.lerp(wrists, t).subtract(0, sag * 4 * t * (1 - t), 0);
                segment(m, out, previous, point, 0.02, ROPE, 0.9f);
                previous = point;
            }
        }
        if (any) {
            buffers.endBatch(ZoneBorderRenderer.lines());
            pose.popPose();
        }
    }

    /** Een stukje touw van a naar b: twee kruislings staande vlakjes (van alle kanten te zien), {@code width} dik. */
    private static void segment(Matrix4f m, VertexConsumer out, Vec3 a, Vec3 b, double width, int rgb, float alpha) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0e-8) {
            return;
        }
        Vec3 axis = Math.abs(d.normalize().y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = d.cross(axis).normalize().scale(width);
        Vec3 v = d.cross(u).normalize().scale(width);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int bl = rgb & 0xFF;
        int al = Mth.clamp((int) (alpha * 255), 0, 255);
        quad(m, out, a.add(u), b.add(u), b.subtract(u), a.subtract(u), r, g, bl, al);
        quad(m, out, a.add(v), b.add(v), b.subtract(v), a.subtract(v), r, g, bl, al);
    }

    private static void quad(Matrix4f m, VertexConsumer out, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int r, int g, int b, int a) {
        for (Vec3 p : new Vec3[] {p0, p1, p2, p3}) {
            out.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
        }
    }
}
