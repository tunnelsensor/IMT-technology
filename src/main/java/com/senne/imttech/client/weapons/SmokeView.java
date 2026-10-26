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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * De rook van de smokebom in je spel (hoofdstuk 35). Zolang een granaat rookt, blaast hij elke tick nieuwe plukken
 * rook (SmokeCloudParticle) in de plekken die de wolk nu vult: jij kijkt er dus niet doorheen. Waar de rook kan komen,
 * rekent je spel zelf uit met dezelfde blokken als de server (Smoke.Volume): hij gaat niet door muren, dichte deuren of
 * glas (jouw wens). Bewakingscamera's tekenen geen deeltjes; daarom tekenen we in hun beeld de wolk als dichte grijze
 * blokjes (jouw keuze: "een camera die in de rook kijkt ziet grijs", zie {@link #renderInFeed}).
 */
public final class SmokeView {
    private SmokeView() {}

    /** Gewone rook: lichtgrijs. */
    public static final int GREY = 0xB8B8B8;

    /**
     * Een wolk zoals je spel hem kent: waar de rook kan komen, hoe groot de wolk nu is, welke kleur, en wanneer we hem
     * het laatst zagen.
     */
    private record Cloud(Smoke.Volume volume, double radius, int rgb, long seen) {}

    /** De wolken die nu roken (per granaat). */
    private static final Map<Integer, Cloud> CLOUDS = new HashMap<>();

    /** De kleur van rook van deze kleurstof (of grijs). */
    public static int rgb(@Nullable DyeColor color) {
        return color == null ? GREY : color.getTextureDiffuseColor() & 0xFFFFFF;
    }

    /** Een rokende granaat, elke tick in je spel: nieuwe plukken rook, en de wolk onthouden. */
    public static void puff(RollingGrenade grenade, int age) {
        if (!(grenade.level() instanceof ClientLevel level)) {
            return;
        }
        int duration = RollingGrenade.smokeTicks();
        double radius = Smoke.radius(age, duration);
        int rgb = rgb(grenade.smokeColor());
        Cloud before = CLOUDS.get(grenade.getId());
        // De eerste keer: uitrekenen waar de rook kan komen (vanaf hetzelfde midden als op de server).
        Smoke.Volume volume = before != null ? before.volume()
                : Smoke.Volume.fill(level, grenade.position().add(0, 0.5, 0));
        CLOUDS.put(grenade.getId(), new Cloud(volume, radius, rgb, level.getGameTime()));
        int cells = volume.count(radius);
        if (radius <= 0.2 || cells == 0) {
            return;
        }
        SmokeCloudOptions smoke = new SmokeCloudOptions(rgb);
        RandomSource random = level.getRandom();
        // Een straal rook uit de granaat zelf...
        level.addParticle(smoke, grenade.getX(), grenade.getY() + 0.2, grenade.getZ(),
                (random.nextDouble() - 0.5) * 0.08, 0.06 + random.nextDouble() * 0.04, (random.nextDouble() - 0.5) * 0.08);
        // ...en plukken overal in de plekken die de wolk nu vult. Aan het eind, als hij wegtrekt, steeds minder.
        boolean fading = age > duration - Smoke.FADE_TICKS - 40;
        int count = fading ? (random.nextInt(3) == 0 ? 1 : 0) : age < Smoke.GROW_TICKS ? 5 : 3;
        for (int i = 0; i < count; i++) {
            long cell = volume.cell(random.nextInt(cells));
            // Ergens in die cel, niet tegen de rand (zo steekt een pluk nooit door de muur ernaast).
            double x = BlockPos.getX(cell) + 0.5 + (random.nextDouble() - 0.5) * 0.7;
            double y = BlockPos.getY(cell) + 0.5 + (random.nextDouble() - 0.5) * 0.7;
            double z = BlockPos.getZ(cell) + 0.5 + (random.nextDouble() - 0.5) * 0.7;
            level.addParticle(smoke, x, y, z, (random.nextDouble() - 0.5) * 0.02, 0.002, (random.nextDouble() - 0.5) * 0.02);
        }
    }

    /**
     * Hoeveel ruimte een rookpluk op deze plek heeft tot het dichtstbijzijnde blok (zie Smoke.Volume), of -1 als hier
     * geen wolk kan komen (de pluk dreef eruit, bijvoorbeeld tegen een plafond).
     */
    public static float room(double x, double y, double z) {
        long cell = BlockPos.asLong(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        float best = -1;
        for (Cloud cloud : CLOUDS.values()) {
            best = Math.max(best, cloud.volume().room(cell));
        }
        return best;
    }

    /** Uit de wereld: alles vergeten. */
    public static void clear() {
        CLOUDS.clear();
    }

    // ================================================================ In het beeld van een camera

    /** De vier hoeken van elke kant van een blokje (zie {@link #renderInFeed}), in de volgorde van Direction. */
    private static final int[][] FACES = {
            {0, 1, 5, 4}, // onder
            {2, 3, 7, 6}, // boven
            {0, 1, 3, 2}, // noord
            {4, 5, 7, 6}, // zuid
            {0, 4, 6, 2}, // west
            {1, 5, 7, 3}, // oost
    };
    /** Zo ver (blokken) ligt de rook binnen zijn cel, zodat hij niet gaat flikkeren tegen een muur. */
    private static final float INSET = 0.01f;

    /**
     * Tekent in het beeld van een bewakingscamera (zie os/camera/FeedRenderer) elke wolk als dichte blokjes in zijn kleur:
     * de buitenkant van alle cellen met rook. Van buiten zie je een grijze wolk (die niet door muren gaat), en zit de lens
     * erin, dan is het hele beeld grijs. {@code eye} is de lens; {@code pose} staat al zo dat (0, 0, 0) de lens is.
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
                cells(m, out, cloud, eye);
            }
        }
    }

    /** De kanten van de cellen met rook die tegen een cel zonder rook aan liggen; van binnen en van buiten te zien. */
    private static void cells(Matrix4f m, VertexConsumer out, Cloud cloud, Vec3 eye) {
        int red = (cloud.rgb() >> 16) & 0xFF;
        int green = (cloud.rgb() >> 8) & 0xFF;
        int blue = cloud.rgb() & 0xFF;
        int alpha = 235;
        Smoke.Volume volume = cloud.volume();
        int count = volume.count(cloud.radius());
        float[][] corner = new float[8][3];
        for (int i = 0; i < count; i++) {
            long cell = volume.cell(i);
            float x = (float) (BlockPos.getX(cell) - eye.x);
            float y = (float) (BlockPos.getY(cell) - eye.y);
            float z = (float) (BlockPos.getZ(cell) - eye.z);
            for (int c = 0; c < 8; c++) {
                corner[c][0] = x + ((c & 1) != 0 ? 1 - INSET : INSET);
                corner[c][1] = y + ((c & 2) != 0 ? 1 - INSET : INSET);
                corner[c][2] = z + ((c & 4) != 0 ? 1 - INSET : INSET);
            }
            for (Direction side : Direction.values()) {
                if (volume.has(BlockPos.offset(cell, side), cloud.radius())) {
                    continue; // daar zit ook rook: geen kant ertussen
                }
                int[] face = FACES[side.ordinal()];
                // Twee keer: één keer met de klok mee en één keer tegen de klok in, zodat je hem van beide kanten ziet.
                for (int k = 0; k < 4; k++) {
                    float[] p = corner[face[k]];
                    out.addVertex(m, p[0], p[1], p[2]).setColor(red, green, blue, alpha);
                }
                for (int k = 3; k >= 0; k--) {
                    float[] p = corner[face[k]];
                    out.addVertex(m, p[0], p[1], p[2]).setColor(red, green, blue, alpha);
                }
            }
        }
    }
}
