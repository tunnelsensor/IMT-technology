package com.senne.starktech.client.suit;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.senne.starktech.client.SuitFlight;
import com.senne.starktech.network.ThrusterPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * De effecten van het harnas, zelf getekend (geen Minecraft-deeltjes):
 * - het REPULSORSCHOT: een felle kern met een gloed eromheen, drukgolfringen bij de hand, een
 *   lichtbol die naar het doel schiet, en bij de inslag een flits met een uitdijende ring;
 * - de STRAALVLAMMEN onder de laarzen als je vliegt: ze zitten vast aan je voeten (dus geen
 *   spoor achter je aan) en worden langer en feller naarmate je harder gaat.
 *
 * Alles is "licht dat opgeteld wordt" (zie GLOW): het gloeit en je ziet erdoorheen.
 */
public final class SuitEffects {
    private SuitEffects() {}

    /**
     * Onze manier van tekenen: gekleurd licht dat bij wat erachter zit wordt OPGETELD (zoals de
     * bliksem van Minecraft), maar zonder de dieptebuffer te beschrijven. Zo drukken lagen licht
     * elkaar niet weg: een witte kern met een blauwe gloed erover blijft allebei zichtbaar. Muren
     * houden het wel tegen. Beide kanten van een vlak zijn zichtbaar.
     */
    static final RenderType GLOW = RenderType.create("starktech_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /**
     * Hetzelfde licht, maar bovenop alles, ook als er iets voor staat. Voor de handpalm in je
     * eigen beeld: je kijkt tegen de achterkant van je hand, en het licht straalt eromheen.
     */
    static final RenderType GLOW_ON_TOP = RenderType.create("starktech_glow_on_top",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    // ================================================================ Repulsorschot

    /** Hoe lang een schot te zien is (ticks). */
    private static final float SHOT_TICKS = 10;

    private record Shot(Vec3 from, Vec3 to, long start) {}

    private static final List<Shot> SHOTS = new ArrayList<>();

    /** Wanneer (speltijd) een speler voor het laatst vuurde: dan gloeit zijn hand even fel. */
    private static final Map<Integer, Long> FIRED = new HashMap<>();

    /**
     * Een repulsorschot van {@code from} (bij de hand) naar {@code to} (waar het inslaat).
     * {@code eye} zijn de ogen van de schutter: zo weten we wiens hand er moet oplichten.
     */
    public static void addShot(Vec3 eye, Vec3 from, Vec3 to) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        SHOTS.add(new Shot(from, to, now));
        Player shooter = mc.level.getNearestPlayer(eye.x, eye.y - 1.62, eye.z, 2.0, false);
        if (shooter != null) {
            FIRED.put(shooter.getId(), now);
        }
    }

    /** Hoe lang geleden deze speler vuurde (ticks), of een groot getal. */
    public static float sinceFired(Entity player, float partialTick) {
        Long at = FIRED.get(player.getId());
        if (at == null || player.level() == null) {
            return 1000;
        }
        return player.level().getGameTime() - at + partialTick;
    }

    // ================================================================ Straalvlammen

    private record Thrust(float power, long seen) {}

    /** Andere spelers die vliegen (volgens de server), met hoe hard. */
    private static final Map<Integer, Thrust> THRUSTING = new HashMap<>();

    public static void onThruster(ThrusterPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            THRUSTING.put(payload.entityId(), new Thrust(payload.power(), mc.level.getGameTime()));
        }
    }

    public static void clear() {
        SHOTS.clear();
        FIRED.clear();
        THRUSTING.clear();
    }

    // ================================================================ Tekenen

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float time = mc.level.getGameTime() + partialTick;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer glow = buffers.getBuffer(GLOW);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();

        // De vlammen: van jezelf (als je vliegt) en van anderen (volgens de server).
        if (SuitFlight.isEngaged() && !mc.player.onGround()) {
            float power = Mth.clamp(SuitFlight.thrust() * 0.6f + (SuitFlight.isBoosting() ? 0.4f : 0), 0, 1);
            flames(m, glow, mc.player, power, partialTick, time, cam);
        }
        for (Iterator<Map.Entry<Integer, Thrust>> it = THRUSTING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Thrust> entry = it.next();
            if (time - entry.getValue().seen() > 6) {
                it.remove();
                continue;
            }
            if (entry.getKey() == mc.player.getId()) {
                continue; // jezelf tekenen we al hierboven
            }
            Entity flyer = mc.level.getEntity(entry.getKey());
            if (flyer instanceof Player player) {
                flames(m, glow, player, entry.getValue().power(), partialTick, time, cam);
            }
        }

        // De repulsorschoten.
        for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext(); ) {
            Shot shot = it.next();
            float age = (time - shot.start()) / SHOT_TICKS;
            if (age >= 1 || age < 0) {
                if (age >= 1) {
                    it.remove();
                }
                continue;
            }
            shot(m, glow, shot, age, cam);
        }
        pose.popPose();
        buffers.endBatch(GLOW);
    }

    /**
     * Eén schot, {@code age} van 0 (net gevuurd) tot 1 (weg). De straal is meteen helemaal
     * zichtbaar en wordt dunner; een lichtbol schiet er in drie ticks overheen naar het doel.
     */
    private static void shot(Matrix4f m, VertexConsumer out, Shot shot, float age, Vec3 cam) {
        Vec3 from = shot.from();
        Vec3 to = shot.to();
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 0.01) {
            return;
        }
        dir = dir.scale(1 / length);
        float fade = 1 - age;

        // De straal: een witte kern met twee lagen blauw licht eromheen, naar de camera gedraaid.
        ribbon(m, out, from, to, cam, 0.13f * fade, 0xF2FCFF, fade);
        ribbon(m, out, from, to, cam, 0.34f * fade, 0x7FDBFF, 0.5f * fade);
        ribbon(m, out, from, to, cam, 0.75f * fade * fade, 0x3FA8FF, 0.2f * fade);

        // De lichtbol die naar het doel schiet (de eerste drie ticks).
        float travel = Mth.clamp(age * SHOT_TICKS / 3, 0, 1);
        if (travel < 1) {
            Vec3 ball = from.add(dir.scale(length * travel));
            billboard(m, out, ball, cam, 0.45f, 0xE8FBFF, 0.9f);
            billboard(m, out, ball, cam, 0.9f, 0x5FC8FF, 0.35f);
        }

        // Drukgolfringen bij de hand: drie ringen die om de beurt uitdijen.
        for (int i = 0; i < 3; i++) {
            float ringAge = Mth.clamp(age * 1.6f - i * 0.18f, 0, 1);
            if (ringAge <= 0 || ringAge >= 1) {
                continue;
            }
            Vec3 centre = from.add(dir.scale(0.2 + i * 0.3 + ringAge * 0.5));
            ring(m, out, centre, dir, 0.06f + ringAge * (0.18f + i * 0.06f), 0.025f, 0x9FE8FF, 0.7f * (1 - ringAge));
        }

        // De inslag: een flits die snel dooft, en een ring die groter wordt.
        float impact = Mth.clamp((age * SHOT_TICKS - 2) / (SHOT_TICKS - 2), 0, 1);
        if (age * SHOT_TICKS >= 2) {
            billboard(m, out, to, cam, 1.4f * (1 - impact), 0xFFFFFF, 0.9f * (1 - impact));
            billboard(m, out, to, cam, 2.2f * (1 - impact * 0.5f), 0x3FD8FF, 0.3f * (1 - impact));
            Vec3 facing = cam.subtract(to).normalize();
            ring(m, out, to, facing, 0.3f + impact * 1.6f, 0.08f, 0x9FE8FF, 0.8f * (1 - impact));
        }
    }

    /**
     * De straalvlammen onder de laarzen van deze speler. Ze hangen recht naar beneden vanaf de
     * zolen, een witte kern met blauw eromheen, en flakkeren een beetje. {@code power} (0..1)
     * maakt ze langer en feller.
     */
    private static void flames(Matrix4f m, VertexConsumer out, Player player, float power, float partialTick,
                               float time, Vec3 cam) {
        Vec3 feet = player.getPosition(partialTick);
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(Mth.cos(bodyYaw), 0, Mth.sin(bodyYaw));
        for (int side = -1; side <= 1; side += 2) {
            Vec3 sole = feet.add(right.scale(0.12 * side)).add(0, 0.02, 0);
            float flicker = 0.9f + 0.1f * Mth.sin(time * 2.3f + side * 1.7f) + 0.06f * Mth.sin(time * 5.1f + side);
            float length = (0.55f + 0.9f * power) * flicker;
            // Buiten naar binnen: breed en blauw, dan smaller en lichter, dan een witte kern.
            flame(m, out, sole, cam, 0.3f + 0.08f * power, length, 0x2F7BFF, 0.5f + 0.25f * power);
            flame(m, out, sole, cam, 0.19f, length * 0.72f, 0x5FD0FF, 0.75f + 0.2f * power);
            flame(m, out, sole, cam, 0.1f, length * 0.42f, 0xF2FCFF, 1f);
            // Een gloeiend schijfje tegen de zool, en een zachte gloed eromheen.
            disc(m, out, sole.add(0, -0.01, 0), 0.11f, 0xBFF4FF, 0.9f);
            billboard(m, out, sole.add(0, -0.08, 0), cam, 0.45f + 0.2f * power, 0x3F9BFF, 0.3f);
        }
    }

    // ================================================================ Bouwstenen

    /** Een lichtband van a naar b, altijd met de brede kant naar de camera. */
    private static void ribbon(Matrix4f m, VertexConsumer out, Vec3 a, Vec3 b, Vec3 cam, float width,
                               int rgb, float alpha) {
        Vec3 dir = b.subtract(a);
        Vec3 toCam = cam.subtract(a.add(b).scale(0.5));
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        side = side.normalize().scale(width / 2);
        quad(m, out, a.add(side), a.subtract(side), b.subtract(side), b.add(side), rgb, alpha);
    }

    /** Een vlam: een driehoek van licht die vanaf {@code top} recht naar beneden hangt. */
    private static void flame(Matrix4f m, VertexConsumer out, Vec3 top, Vec3 cam, float width, float length,
                              int rgb, float alpha) {
        Vec3 down = new Vec3(0, -length, 0);
        Vec3 side = down.cross(cam.subtract(top));
        if (side.lengthSqr() < 1.0E-8) {
            side = new Vec3(1, 0, 0);
        }
        side = side.normalize().scale(width / 2);
        Vec3 tip = top.add(down);
        // Onderaan wordt hij smal (bijna een punt) en doorzichtig.
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        Vec3 tipSide = side.scale(0.15);
        vertex(m, out, top.add(side), r, g, b, a);
        vertex(m, out, top.subtract(side), r, g, b, a);
        vertex(m, out, tip.subtract(tipSide), r, g, b, 0);
        vertex(m, out, tip.add(tipSide), r, g, b, 0);
    }

    /** Een rond vlekje licht dat altijd naar de camera kijkt; {@code size} is de doorsnede. */
    private static void billboard(Matrix4f m, VertexConsumer out, Vec3 centre, Vec3 cam, float size,
                                  int rgb, float alpha) {
        Vec3 toCam = cam.subtract(centre).normalize();
        Vec3 up = Math.abs(toCam.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = up.cross(toCam).normalize();
        Vec3 realUp = toCam.cross(right).normalize();
        float radius = size / 2;
        int steps = 16;
        for (int i = 0; i < steps; i++) {
            double t0 = i * Math.PI * 2 / steps;
            double t1 = (i + 1) * Math.PI * 2 / steps;
            Vec3 p0 = centre.add(right.scale(Math.cos(t0) * radius)).add(realUp.scale(Math.sin(t0) * radius));
            Vec3 p1 = centre.add(right.scale(Math.cos(t1) * radius)).add(realUp.scale(Math.sin(t1) * radius));
            quad(m, out, centre, centre, p1, p0, rgb, alpha);
        }
    }

    /** Een ring van licht om {@code centre}, loodrecht op {@code axis}. */
    static void ring(Matrix4f m, VertexConsumer out, Vec3 centre, Vec3 axis, float radius, float thickness,
                             int rgb, float alpha) {
        Vec3 a = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = axis.cross(a).normalize();
        Vec3 v = axis.cross(u).normalize();
        int steps = 24;
        for (int i = 0; i < steps; i++) {
            double t0 = i * Math.PI * 2 / steps;
            double t1 = (i + 1) * Math.PI * 2 / steps;
            Vec3 d0 = u.scale(Math.cos(t0)).add(v.scale(Math.sin(t0)));
            Vec3 d1 = u.scale(Math.cos(t1)).add(v.scale(Math.sin(t1)));
            quad(m, out, centre.add(d0.scale(radius)), centre.add(d1.scale(radius)),
                    centre.add(d1.scale(radius + thickness)), centre.add(d0.scale(radius + thickness)), rgb, alpha);
        }
    }

    /** Een plat, liggend schijfje licht. */
    static void disc(Matrix4f m, VertexConsumer out, Vec3 centre, float radius, int rgb, float alpha) {
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            double t0 = i * Math.PI * 2 / steps;
            double t1 = (i + 1) * Math.PI * 2 / steps;
            Vec3 p0 = centre.add(Math.cos(t0) * radius, 0, Math.sin(t0) * radius);
            Vec3 p1 = centre.add(Math.cos(t1) * radius, 0, Math.sin(t1) * radius);
            quad(m, out, centre, centre, p1, p0, rgb, alpha);
        }
    }

    /** Een vierhoek (GLOW tekent beide kanten). */
    static void quad(Matrix4f m, VertexConsumer out, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int bl = rgb & 0xFF;
        int al = Mth.clamp((int) (alpha * 255), 0, 255);
        vertex(m, out, a, r, g, bl, al);
        vertex(m, out, b, r, g, bl, al);
        vertex(m, out, c, r, g, bl, al);
        vertex(m, out, d, r, g, bl, al);
    }

    private static void vertex(Matrix4f m, VertexConsumer out, Vec3 p, int r, int g, int b, int a) {
        out.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }
}
