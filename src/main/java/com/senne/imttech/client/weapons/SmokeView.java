package com.senne.imttech.client.weapons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.weapons.RollingGrenade;
import com.senne.imttech.weapons.Smoke;
import com.senne.imttech.weapons.SmokeCloudOptions;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * De rook van de smokebom in je spel (hoofdstuk 35). Zolang een granaat rookt, blaast hij elke tick nieuwe plukken
 * rook (SmokeCloudParticle) in een bol die groeit tot de grootte van de wolk op de server (Smoke.radius): jij kijkt er
 * dus niet doorheen. Bewakingscamera's tekenen geen deeltjes; daarom tekenen we in hun beeld de wolk als een dikke grijze
 * bol (jouw keuze: "een camera die in de rook kijkt ziet grijs", zie {@link #renderInFeed}).
 */
public final class SmokeView {
    private SmokeView() {}

    /** Gewone rook: lichtgrijs. */
    public static final int GREY = 0xB8B8B8;

    /** Een wolk zoals je spel hem kent: waar, hoe groot nu, welke kleur, en wanneer we hem het laatst zagen. */
    private record Cloud(Vec3 center, double radius, int rgb, long seen) {}

    /** De wolken die nu roken (per granaat), voor het camerabeeld. */
    private static final Map<Integer, Cloud> CLOUDS = new HashMap<>();

    /** De kleur van rook van deze kleurstof (of grijs). */
    public static int rgb(@Nullable DyeColor color) {
        return color == null ? GREY : color.getTextureDiffuseColor() & 0xFFFFFF;
    }

    /** Een rokende granaat, elke tick in je spel: nieuwe plukken rook, en de wolk onthouden voor de camera's. */
    public static void puff(RollingGrenade grenade, int age) {
        if (!(grenade.level() instanceof ClientLevel level)) {
            return;
        }
        int duration = RollingGrenade.smokeTicks();
        double radius = Smoke.radius(age, duration);
        int rgb = rgb(grenade.smokeColor());
        Vec3 center = grenade.position().add(0, 0.5, 0);
        CLOUDS.put(grenade.getId(), new Cloud(center, radius, rgb, level.getGameTime()));
        if (radius <= 0.2) {
            return;
        }
        SmokeCloudOptions smoke = new SmokeCloudOptions(rgb);
        RandomSource random = level.getRandom();
        // Een straal rook uit de granaat zelf...
        level.addParticle(smoke, grenade.getX(), grenade.getY() + 0.2, grenade.getZ(),
                (random.nextDouble() - 0.5) * 0.08, 0.06 + random.nextDouble() * 0.04, (random.nextDouble() - 0.5) * 0.08);
        // ...en plukken overal in de wolk. Aan het eind, als hij wegtrekt, steeds minder.
        boolean fading = age > duration - Smoke.FADE_TICKS - 40;
        int count = fading ? (random.nextInt(3) == 0 ? 1 : 0) : age < Smoke.GROW_TICKS ? 5 : 3;
        for (int i = 0; i < count; i++) {
            Vec3 at = randomIn(random, radius * 0.85);
            double y = center.y + Math.abs(at.y) * 0.75 - 0.3; // rook ligt vooral laag, en zakt niet onder de vloer
            level.addParticle(smoke, center.x + at.x, y, center.z + at.z,
                    (random.nextDouble() - 0.5) * 0.02, 0.002, (random.nextDouble() - 0.5) * 0.02);
        }
    }

    /** Een willekeurig punt in een bol met deze straal (rond 0, 0, 0). */
    private static Vec3 randomIn(RandomSource random, double radius) {
        while (true) {
            double x = random.nextDouble() * 2 - 1;
            double y = random.nextDouble() * 2 - 1;
            double z = random.nextDouble() * 2 - 1;
            if (x * x + y * y + z * z <= 1) {
                return new Vec3(x * radius, y * radius, z * radius);
            }
        }
    }

    /** Uit de wereld: alles vergeten. */
    public static void clear() {
        CLOUDS.clear();
    }

    // ================================================================ In het beeld van een camera

    /**
     * Tekent in het beeld van een bewakingscamera (zie os/camera/FeedRenderer) elke wolk als een dichte bol in zijn
     * kleur: van buiten zie je een grijze bol, en zit de lens erin, dan is het hele beeld grijs. {@code eye} is de lens;
     * {@code pose} staat al zo dat (0, 0, 0) de lens is.
     */
    public static void renderInFeed(Vec3 eye, PoseStack pose, MultiBufferSource buffers) {
        ClientLevel level = Minecraft.getInstance().level;
        if (CLOUDS.isEmpty() || level == null) {
            return;
        }
        long now = level.getGameTime();
        VertexConsumer out = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m = pose.last().pose();
        for (Iterator<Map.Entry<Integer, Cloud>> it = CLOUDS.entrySet().iterator(); it.hasNext(); ) {
            Cloud cloud = it.next().getValue();
            if (now - cloud.seen() > 5) {
                it.remove(); // de granaat rookt niet meer (of is weg)
                continue;
            }
            if (cloud.radius() > 0.3) {
                sphere(m, out, cloud.center().subtract(eye), (float) cloud.radius(), cloud.rgb());
            }
        }
    }

    /** Een bol van vierhoekjes, van binnen en van buiten te zien, bijna niet doorzichtig. */
    private static void sphere(Matrix4f m, VertexConsumer out, Vec3 c, float r, int rgb) {
        int rings = 10;
        int segments = 16;
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int alpha = 235;
        for (int i = 0; i < rings; i++) {
            double a0 = Math.PI * i / rings - Math.PI / 2;
            double a1 = Math.PI * (i + 1) / rings - Math.PI / 2;
            for (int j = 0; j < segments; j++) {
                double b0 = Math.PI * 2 * j / segments;
                double b1 = Math.PI * 2 * (j + 1) / segments;
                Vec3[] quad = {point(c, r, a0, b0), point(c, r, a0, b1), point(c, r, a1, b1), point(c, r, a1, b0)};
                // Twee keer: één keer met de klok mee en één keer tegen de klok in, zodat je hem van beide kanten ziet.
                for (int k = 0; k < 4; k++) {
                    out.addVertex(m, (float) quad[k].x, (float) quad[k].y, (float) quad[k].z).setColor(red, green, blue, alpha);
                }
                for (int k = 3; k >= 0; k--) {
                    out.addVertex(m, (float) quad[k].x, (float) quad[k].y, (float) quad[k].z).setColor(red, green, blue, alpha);
                }
            }
        }
    }

    private static Vec3 point(Vec3 c, float r, double lat, double lon) {
        double cos = Math.cos(lat);
        return c.add(Math.cos(lon) * cos * r, Math.sin(lat) * r, Math.sin(lon) * cos * r);
    }
}
