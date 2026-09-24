package com.senne.starktech.client.shield;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.starktech.StarkTech;
import com.senne.starktech.shield.DomeGeneratorBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Tekent de schildkoepel.
 *
 * Gaat de koepel op, dan gebeurt dat in drie stappen (tijden in ticks, 20 = 1 seconde):
 * <pre>
 *  0 - 14   een felle straal schiet uit de generator omhoog, tot de top van de koepel
 * 10 - 22   bovenin vouwt hij open: een schijf die breder wordt
 * 18 - 50   de koepel zakt vanuit de top naar beneden, tot hij op de grond staat
 * </pre>
 * Daarna staat hij er: doorzichtig, in zijn kleur, met een zeshoekjespatroon dat langzaam over
 * de koepel glijdt. Heeft hij weinig schildkracht over, dan flakkert hij. Valt hij weg, dan
 * trekt hij zich snel terug naar de top en verdwijnt.
 *
 * We tekenen met "energySwirl" (net als het schild om een opgeladen creeper): licht dat opgeteld
 * wordt bij wat erachter zit, dus hij gloeit en je ziet er toch doorheen. Van binnen zie je hem
 * ook.
 */
public class DomeRenderer implements BlockEntityRenderer<DomeGeneratorBlockEntity> {
    private static final ResourceLocation FIELD =
            ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/misc/dome_field.png");
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    private static final float BEAM_TICKS = 14;
    private static final float OPEN_START = 10;
    private static final float OPEN_TICKS = 12;
    private static final float DROP_START = 18;
    private static final float DROP_TICKS = 32;
    private static final float COLLAPSE_TICKS = 16;

    /** Hoeveel ringen (van de top naar beneden) en stukken (rondom) de koepel heeft. */
    private static final int RINGS = 24;
    private static final int SEGMENTS = 48;

    public DomeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(DomeGeneratorBlockEntity dome) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    /** De koepel is veel groter dan het blok: Minecraft moet hem ook tekenen als het blok zelf net buiten beeld is. */
    @Override
    public AABB getRenderBoundingBox(DomeGeneratorBlockEntity dome) {
        return new AABB(dome.getBlockPos()).inflate(dome.radius() + 1);
    }

    @Override
    public void render(DomeGeneratorBlockEntity dome, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = dome.getLevel();
        if (level == null) {
            return;
        }
        float time = level.getGameTime() + partialTick;
        float age = time - dome.changedAt();
        float radius = dome.radius();
        int rgb = dome.colour().rgb();

        float beam;     // hoe hoog de straal is (0..1 van de straal van de koepel)
        float open;     // hoe breed de schijf bovenin is (0..1)
        float drop;     // tot hoe ver de koepel naar beneden reikt (0 = alleen de top, 1 = tot de grond)
        float bright;   // hoe fel de koepel is
        if (dome.isUp()) {
            beam = Mth.clamp(age / BEAM_TICKS, 0, 1);
            open = ease(Mth.clamp((age - OPEN_START) / OPEN_TICKS, 0, 1));
            drop = ease(Mth.clamp((age - DROP_START) / DROP_TICKS, 0, 1));
            bright = 1.0f;
        } else {
            float collapse = Mth.clamp(age / COLLAPSE_TICKS, 0, 1);
            if (collapse >= 1) {
                return;
            }
            beam = 0;
            open = 1 - collapse;
            drop = 1 - ease(collapse);
            bright = 1 - collapse;
        }

        // Weinig schildkracht over? Dan flakkert hij (en helemaal onderaan hapert hij).
        float health = dome.maxShield() > 0 ? (float) dome.shield() / dome.maxShield() : 1;
        if (dome.isUp() && health < 0.3f) {
            float flicker = 0.55f + 0.45f * Mth.sin(time * 1.7f) * Mth.sin(time * 0.61f);
            bright *= health < 0.12f && Math.floorMod((long) time, 7) == 0 ? 0.15f : Math.max(0.35f, flicker);
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        // Het patroon glijdt langzaam over de koepel.
        VertexConsumer field = buffers.getBuffer(RenderType.energySwirl(FIELD, time * 0.004f, time * 0.002f));
        Matrix4f matrix = poseStack.last().pose();

        if (open > 0 && drop <= 0.02f) {
            // De schijf bovenin, net voor hij naar beneden zakt.
            cap(matrix, field, radius, open * 0.45f, rgb, 0.9f * bright);
        }
        if (drop > 0) {
            dome(matrix, field, radius, drop, rgb, 0.42f * bright);
        }

        // Het felle licht (de straal en de rand die naar beneden zakt): effen kleur, opgeteld.
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        if (beam > 0 && drop < 1) {
            beam(matrix, glow, radius * beam, rgb, bright * (1 - drop));
        }
        if (dome.isUp() && drop > 0 && drop < 1) {
            // De onderrand van de zakkende koepel is feller: daar "bouwt" hij.
            edge(matrix, glow, radius, drop, rgb, bright);
        }
        poseStack.popPose();
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    /**
     * De straal uit de generator, van de projector tot {@code height}: een bijna witte kern met
     * twee steeds zwakkere gloeilagen eromheen, elk als twee gekruiste banen.
     */
    private static void beam(Matrix4f m, VertexConsumer out, float height, int rgb, float alpha) {
        int core = mix(rgb, 0xFFFFFF, 0.6f);
        glowCross(m, out, 0.22f, height, core, 0.95f * alpha);
        glowCross(m, out, 0.6f, height, rgb, 0.4f * alpha);
        glowCross(m, out, 1.3f, height, rgb, 0.14f * alpha);
    }

    private static void glowCross(Matrix4f m, VertexConsumer out, float width, float height, int rgb, float alpha) {
        float w = width / 2;
        float y0 = 0.35f;
        glowQuad(m, out, -w, y0, 0, w, y0, 0, w, height, 0, -w, height, 0, rgb, alpha);
        glowQuad(m, out, 0, y0, -w, 0, y0, w, 0, height, w, 0, height, -w, rgb, alpha);
    }

    /** Twee kleuren mengen: {@code t} = 0 is helemaal a, 1 is helemaal b. */
    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    /** Een felle vierhoek van effen licht, van twee kanten te zien. */
    private static void glowQuad(Matrix4f m, VertexConsumer out,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, float x3, float y3, float z3, int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        out.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
        out.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        out.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        out.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        out.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        out.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        out.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        out.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
    }

    /**
     * De top van de koepel als platte schijf die opengaat, op de hoogte van de koepeltop.
     * {@code part} is hoe breed hij is (deel van de straal).
     */
    private static void cap(Matrix4f m, VertexConsumer out, float radius, float part, int rgb, float alpha) {
        float y = radius;
        float r = radius * part;
        for (int j = 0; j < SEGMENTS; j++) {
            double a0 = j * (Math.PI * 2 / SEGMENTS);
            double a1 = (j + 1) * (Math.PI * 2 / SEGMENTS);
            float x0 = (float) Math.cos(a0) * r;
            float z0 = (float) Math.sin(a0) * r;
            float x1 = (float) Math.cos(a1) * r;
            float z1 = (float) Math.sin(a1) * r;
            quad(m, out, 0, y, 0, 0, y, 0, x1, y, z1, x0, y, z0, rgb, alpha,
                    j / (float) SEGMENTS * 8, 0, (j + 1) / (float) SEGMENTS * 8, part * 4);
        }
    }

    /**
     * De koepel zelf: een halve bol van ringen, van de top naar beneden. {@code drop} zegt tot waar
     * hij al reikt (1 = tot aan de grond).
     */
    private static void dome(Matrix4f m, VertexConsumer out, float radius, float drop, int rgb, float alpha) {
        int rings = Math.max(1, Math.round(RINGS * drop));
        float maxTheta = (float) (Math.PI / 2 * drop);
        for (int i = 0; i < rings; i++) {
            float t0 = maxTheta * i / rings;
            float t1 = maxTheta * (i + 1) / rings;
            for (int j = 0; j < SEGMENTS; j++) {
                float p0 = (float) (j * Math.PI * 2 / SEGMENTS);
                float p1 = (float) ((j + 1) * Math.PI * 2 / SEGMENTS);
                // u rondom (8 keer het patroon), v van boven naar beneden (6 keer).
                float u0 = j / (float) SEGMENTS * 8;
                float u1 = (j + 1) / (float) SEGMENTS * 8;
                float v0 = t0 / (float) (Math.PI / 2) * 6;
                float v1 = t1 / (float) (Math.PI / 2) * 6;
                quad(m, out,
                        px(radius, t0, p0), py(radius, t0), pz(radius, t0, p0),
                        px(radius, t0, p1), py(radius, t0), pz(radius, t0, p1),
                        px(radius, t1, p1), py(radius, t1), pz(radius, t1, p1),
                        px(radius, t1, p0), py(radius, t1), pz(radius, t1, p0),
                        rgb, alpha, u0, v0, u1, v1);
            }
        }
    }

    /** Een felle band op de onderrand van de koepel terwijl hij naar beneden zakt. */
    private static void edge(Matrix4f m, VertexConsumer out, float radius, float drop, int rgb, float alpha) {
        float t1 = (float) (Math.PI / 2 * drop);
        float t0 = Math.max(0, t1 - 0.05f);
        int colour = mix(rgb, 0xFFFFFF, 0.4f);
        for (int j = 0; j < SEGMENTS; j++) {
            float p0 = (float) (j * Math.PI * 2 / SEGMENTS);
            float p1 = (float) ((j + 1) * Math.PI * 2 / SEGMENTS);
            glowQuad(m, out,
                    px(radius, t0, p0), py(radius, t0), pz(radius, t0, p0),
                    px(radius, t0, p1), py(radius, t0), pz(radius, t0, p1),
                    px(radius, t1, p1), py(radius, t1), pz(radius, t1, p1),
                    px(radius, t1, p0), py(radius, t1), pz(radius, t1, p0),
                    colour, 0.7f * alpha);
        }
    }

    // Een punt op de bol: theta van de top (0) naar de evenaar (pi/2), phi rondom.
    private static float px(float r, float theta, float phi) {
        return (float) (Math.sin(theta) * Math.cos(phi)) * r;
    }

    private static float py(float r, float theta) {
        return (float) Math.cos(theta) * r;
    }

    private static float pz(float r, float theta, float phi) {
        return (float) (Math.sin(theta) * Math.sin(phi)) * r;
    }

    /**
     * Een vierhoek, met kleur, helderheid en een stukje van het patroon. Het licht wordt
     * opgeteld bij wat erachter zit: hoe fel hij is, zit dus in de kleur zelf (donkerder = zwakker).
     */
    private static void quad(Matrix4f m, VertexConsumer out,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             int rgb, float alpha, float u0, float v0, float u1, float v1) {
        float k = Mth.clamp(alpha, 0, 1);
        int a = 255;
        int r = (int) (((rgb >> 16) & 0xFF) * k);
        int g = (int) (((rgb >> 8) & 0xFF) * k);
        int b = (int) ((rgb & 0xFF) * k);
        vertex(m, out, x0, y0, z0, r, g, b, a, u0, v0);
        vertex(m, out, x1, y1, z1, r, g, b, a, u1, v0);
        vertex(m, out, x2, y2, z2, r, g, b, a, u1, v1);
        vertex(m, out, x3, y3, z3, r, g, b, a, u0, v1);
    }

    private static void vertex(Matrix4f m, VertexConsumer out, float x, float y, float z,
                               int r, int g, int b, int a, float u, float v) {
        out.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LIGHT).setNormal(0, 1, 0);
    }
}
