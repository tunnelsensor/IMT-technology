package com.senne.imttech.client.weapons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.client.suit.SuitEffects;
import com.senne.imttech.network.GunShotPayload;
import com.senne.imttech.weapons.BoltFlight;
import com.senne.imttech.weapons.GunType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * De laserbouten van de lasergeweren (hoofdstuk 35): bij elk schot (van jou of van een ander) een felle streep licht die
 * razendsnel van de loop naar waar het schot inslaat vliegt, wit in het midden en cyaan eromheen (jouw stijl: een
 * witte/cyaan gloed). De sniper trekt in één keer een volle straal die even blijft hangen. Waar het schot inslaat:
 * vonken, en bij een geraakt wezen meer.
 *
 * Voor je eigen schoten begint de bout bij de loop van het geweer in je beeld (zie GunRenderer.ownMuzzle); voor anderen
 * ongeveer bij hun rechterhand.
 */
public final class GunEffects {
    private GunEffects() {}

    /** Zo lang (ticks) blijft de straal van de sniper hangen. */
    private static final float BEAM_TICKS = 9;

    private record Bolt(Vec3 from, Vec3 to, float start, boolean beam) {}

    private static final List<Bolt> BOLTS = new ArrayList<>();

    /** Iemand schoot (zie GunShotPayload). */
    public static void shot(GunShotPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Entity shooter = level.getEntity(payload.shooter());
        GunType type = GunType.byId(payload.gun());
        Vec3 from = null;
        if (shooter == mc.player) {
            from = GunRenderer.ownMuzzle(mc, mc.getTimer().getGameTimeDeltaPartialTick(false));
        }
        if (from == null && shooter != null) {
            from = handOf(shooter);
            level.addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, 0, 0.01, 0);
        }
        if (from == null) {
            return;
        }
        float now = level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        RandomSource random = level.getRandom();
        for (int i = 0; i < payload.ends().size(); i++) {
            Vec3 end = payload.ends().get(i);
            BOLTS.add(new Bolt(from, end, now, type == GunType.SNIPER));
            boolean hit = (payload.hits() >> i & 1) != 0;
            int sparks = hit ? 10 : 5;
            for (int k = 0; k < sparks; k++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z,
                        (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.3);
            }
            if (!hit) {
                level.addParticle(ParticleTypes.SMOKE, end.x, end.y, end.z, 0, 0.02, 0);
            }
        }
    }

    /** Ongeveer waar de loop van een ander zit: een stukje voor, rechts van en onder zijn ogen. */
    private static Vec3 handOf(Entity shooter) {
        Vec3 look = shooter.getViewVector(1.0f);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 eye = shooter instanceof LivingEntity ? shooter.getEyePosition() : shooter.position();
        return eye.add(look.scale(0.9)).add(right.scale(0.28)).add(0, -0.22, 0);
    }

    public static void clear() {
        BOLTS.clear();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BOLTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BOLTS.clear();
            return;
        }
        float now = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(SuitEffects.GLOW);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
            Bolt bolt = it.next();
            float age = now - bolt.start();
            Vec3 path = bolt.to().subtract(bolt.from());
            double length = path.length();
            if (length < 1.0E-3) {
                it.remove();
                continue;
            }
            Vec3 dir = path.scale(1 / length);
            if (bolt.beam()) {
                if (age > BEAM_TICKS) {
                    it.remove();
                    continue;
                }
                float fade = 1 - Math.max(0, age) / BEAM_TICKS;
                EmpEffects.line(m, out, bolt.from(), bolt.to(), cam, 0.22f * fade, 0x3FA8FF, 0.45f * fade);
                EmpEffects.line(m, out, bolt.from(), bolt.to(), cam, 0.07f * fade, 0xF2FCFF, fade);
                continue;
            }
            BoltFlight flight = BoltFlight.at(age, length);
            if (flight.gone(length)) {
                it.remove();
                continue;
            }
            Vec3 a = bolt.from().add(dir.scale(flight.tail()));
            Vec3 b = bolt.from().add(dir.scale(flight.head()));
            EmpEffects.line(m, out, a, b, cam, 0.16f, 0x3FA8FF, 0.5f);
            EmpEffects.line(m, out, a, b, cam, 0.05f, 0xF2FCFF, 1.0f);
        }
        pose.popPose();
        buffers.endBatch(SuitEffects.GLOW);
    }
}
