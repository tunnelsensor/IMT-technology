package com.senne.starktech.client.holo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.senne.starktech.arsenal.ArsenalDrone;
import com.senne.starktech.arsenal.EdithArsenal;
import com.senne.starktech.holo.HoloMode;
import com.senne.starktech.holo.HoloTableBlock;
import com.senne.starktech.holo.HoloTableBlockEntity;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.registry.ModAttachments;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Tekent het hologram boven de holotafel.
 *
 * Alles is opgebouwd uit lijntjes (RenderType.lines), want dat is precies hoe een hologram
 * eruit hoort te zien: doorzichtig, lichtgevend en zonder textuur. De hele projectie draait
 * langzaam rond, zodat je hem van alle kanten kunt bekijken zonder zelf te lopen.
 *
 * Drie standen, zie {@link HoloMode}:
 * - KAART: een reliëfkaart van de omgeving, met je waypoints, spelers en vijanden erop
 * - BOUWTEKENING: het recept van het voorwerp dat je op de tafel legde
 * - ARSENAAL: je drones die om een bolletje cirkelen, met hun schade in de kleur
 */
public class HoloTableRenderer implements BlockEntityRenderer<HoloTableBlockEntity> {
    /** Hoe groot het hologram is (in blokken, van rand tot rand). */
    private static final float SIZE = 1.6f;
    /** Hoe hoog boven de tafel het hologram zweeft. */
    private static final float LIFT = 1.05f;
    /** Tot hoe ver je het hologram nog getekend krijgt (in blokken). */
    private static final int VIEW_DISTANCE = 24;

    private final ItemRenderer items;
    private final HoloMapData map = new HoloMapData();

    public HoloTableRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE * 16;
    }

    @Override
    public void render(HoloTableBlockEntity table, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = table.getLevel();
        if (level == null) {
            return;
        }
        int rgb = table.getBlockState().getValue(HoloTableBlock.COLOR).rgb();
        float time = (level.getGameTime() + partialTick);

        poseStack.pushPose();
        poseStack.translate(0.5, LIFT, 0.5);
        // Heel licht op en neer zweven, en (als dat aan staat) langzaam ronddraaien.
        poseStack.translate(0, Mth.sin(time / 26.0f) * 0.02f, 0);
        if (table.spin()) {
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.4f));
        }
        poseStack.scale(table.scaleFactor(), table.scaleFactor(), table.scaleFactor());

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        baseRing(poseStack, lines, rgb, time);

        switch (table.mode()) {
            case MAP -> renderMap(table, level, poseStack, lines, rgb, time);
            case BLUEPRINT -> renderBlueprint(table, level, poseStack, buffers, lines, rgb, light);
            case ARSENAL -> renderArsenal(poseStack, lines, rgb, time);
        }
        poseStack.popPose();
    }

    // ---------------------------------------------------------------- Kaart

    /**
     * De reliëfkaart: per vakje een staafje dat zo hoog is als de grond daar.
     *
     * De hoogtes rekenen we niet elke frame opnieuw uit - dat zijn duizenden opzoekingen - maar
     * hooguit een paar keer per seconde, en alleen als er iets veranderd is (zie HoloMapData).
     */
    private void renderMap(HoloTableBlockEntity table, Level level, PoseStack poseStack,
                           VertexConsumer lines, int rgb, float time) {
        HoloMapData.Snapshot snapshot = map.get(level, table.getBlockPos(), table.rangeBlocks());
        int cells = HoloMapData.CELLS;
        float step = SIZE / cells;
        float half = SIZE / 2.0f;

        for (int cx = 0; cx < cells; cx++) {
            for (int cz = 0; cz < cells; cz++) {
                float height = snapshot.relativeHeight(cx, cz);
                if (Float.isNaN(height)) {
                    continue; // niets geladen daar
                }
                float x = -half + cx * step;
                float z = -half + cz * step;
                // Hoe hoger de grond, hoe feller het staafje.
                float shade = 0.35f + 0.65f * Mth.clamp((height + 0.5f) / 1.0f, 0.0f, 1.0f);
                int alpha = (int) (110 + 90 * shade);
                vline(poseStack, lines, x, 0, z, height * 0.5f, rgb, alpha, shade);
            }
        }

        // De tafel zelf als een klein baken in het midden.
        marker(poseStack, lines, 0, 0, 0, 0.16f, 0xFFFFFF, 255);

        // Wat er leeft in de buurt, en waar je naartoe wil.
        renderWaypoints(table, level, poseStack, lines, snapshot);
        renderEntities(table, level, poseStack, lines, snapshot, time);
    }

    /** Je waypoints als paaltjes in hun eigen kleur, zodat je ze uit elkaar houdt. */
    private void renderWaypoints(HoloTableBlockEntity table, Level level, PoseStack poseStack,
                                 VertexConsumer lines, HoloMapData.Snapshot snapshot) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        EdithWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        for (Waypoint waypoint : data.waypoints()) {
            if (waypoint.dimension() != level.dimension()) {
                continue;
            }
            float[] spot = snapshot.project(waypoint.pos().getX() + 0.5, waypoint.pos().getZ() + 0.5, SIZE);
            if (spot == null) {
                continue;
            }
            vline(poseStack, lines, spot[0], 0, spot[1], 0.34f, waypoint.color(), 235, 1.0f);
            marker(poseStack, lines, spot[0], 0.34f, spot[1], 0.05f, waypoint.color(), 255);
        }
    }

    /** Spelers in het wit, vijanden in het rood: een levende kaart. */
    private void renderEntities(HoloTableBlockEntity table, Level level, PoseStack poseStack,
                                VertexConsumer lines, HoloMapData.Snapshot snapshot, float time) {
        AABB box = new AABB(table.getBlockPos()).inflate(snapshot.radius());
        // Een klein pulsje, zodat de stipjes opvallen tussen de staafjes.
        float pulse = 0.05f + 0.015f * Mth.sin(time / 6.0f);
        for (Entity entity : level.getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive())) {
            float[] spot = snapshot.project(entity.getX(), entity.getZ(), SIZE);
            if (spot == null) {
                continue;
            }
            int colour = entity instanceof Player ? 0xFFFFFF : entity instanceof Enemy ? 0xFF4545 : 0x5CFF9D;
            float height = snapshot.relativeHeightAt(entity.getX(), entity.getZ());
            float y = Float.isNaN(height) ? 0.0f : height * 0.5f;
            marker(poseStack, lines, spot[0], y + 0.04f, spot[1], pulse, colour, 255);
        }
    }

    // ---------------------------------------------------------------- Bouwtekening

    /** Het recept van het voorwerp op tafel: het rooster met de onderdelen, en het resultaat erboven. */
    private void renderBlueprint(HoloTableBlockEntity table, Level level, PoseStack poseStack,
                                 MultiBufferSource buffers, VertexConsumer lines, int rgb, int light) {
        ItemStack wanted = table.blueprint();
        if (wanted.isEmpty()) {
            // Niets op tafel: een leeg rooster, als uitnodiging.
            grid(poseStack, lines, rgb, 110);
            return;
        }
        HoloRecipe recipe = HoloRecipe.of(level, wanted);
        grid(poseStack, lines, rgb, 170);

        float cell = SIZE / 4.0f;
        if (recipe != null) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    ItemStack ingredient = recipe.at(col, row);
                    if (!ingredient.isEmpty()) {
                        float x = (col - 1) * cell;
                        float z = (row - 1) * cell;
                        item(poseStack, buffers, ingredient, x, 0.02f, z, 0.42f, light);
                    }
                }
            }
        }
        // Het resultaat zweeft erboven, groter.
        item(poseStack, buffers, wanted, 0, 0.55f, 0, 0.7f, light);
        if (recipe == null) {
            // Geen recept bekend: alleen het voorwerp, met een kruis eronder.
            cross(poseStack, lines, 0.22f, 0xFF4545, 220);
        }
    }

    // ---------------------------------------------------------------- Arsenaal

    /** Je drones cirkelen om een bolletje; de kleur zegt hoe heel ze zijn. */
    private void renderArsenal(PoseStack poseStack, VertexConsumer lines, int rgb, float time) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        EdithArsenal arsenal = player.getData(ModAttachments.ARSENAL);
        // Het "planeetje" in het midden: drie ringen.
        float radius = SIZE / 4.0f;
        ring(poseStack, lines, radius, 0, rgb, 150);
        ringTilted(poseStack, lines, radius, rgb, 110, 90);
        ringTilted(poseStack, lines, radius, rgb, 110, 0);

        List<ArsenalDrone> drones = arsenal.drones();
        if (drones.isEmpty()) {
            return;
        }
        // Meer dan dit tekenen we niet: anders is het één wolk en zie je niets meer.
        int shown = Math.min(drones.size(), 48);
        for (int i = 0; i < shown; i++) {
            ArsenalDrone drone = drones.get(i);
            // Elke drone een eigen baan: een andere hoek, hoogte en snelheid.
            float angle = (float) (i * (Math.PI * 2 / shown)) + time / (60.0f + (i % 5) * 12.0f);
            float orbit = radius + 0.16f + (i % 3) * 0.07f;
            float x = Mth.cos(angle) * orbit;
            float z = Mth.sin(angle) * orbit;
            float y = Mth.sin(angle * 2 + i) * 0.12f;
            int colour = healthColour(drone.healthFraction());
            // Een drone die onderweg is knippert, zodat je ziet dat hij niet klaar hangt.
            int alpha = drone.state() == ArsenalDrone.State.DEPLOYED
                    ? (int) (120 + 110 * Mth.sin(time / 5.0f + i)) : 255;
            marker(poseStack, lines, x, y, z, 0.05f, colour, Mth.clamp(alpha, 40, 255));
        }
    }

    /** Groen als hij heel is, oranje als hij een tik heeft, rood als hij bijna op is. */
    private static int healthColour(float fraction) {
        if (fraction >= 0.999f) {
            return 0x5CFF9D;
        }
        return fraction < 0.35f ? 0xFF4545 : 0xFFA93F;
    }

    // ================================================================ Tekenhulpjes

    /** De ring op de tafel waar de projectie uit lijkt te komen. */
    private void baseRing(PoseStack poseStack, VertexConsumer lines, int rgb, float time) {
        ring(poseStack, lines, SIZE / 2.0f + 0.05f, -0.12f, rgb, 90);
        // Een straaltje van de tafel naar het hologram.
        int alpha = (int) (60 + 40 * Mth.sin(time / 8.0f));
        vline(poseStack, lines, 0, -LIFT + 0.62f, 0, LIFT - 0.7f, rgb, alpha, 1.0f);
    }

    /** Een cirkel van lijnstukjes op hoogte y. */
    private void ring(PoseStack poseStack, VertexConsumer lines, float radius, float y, int rgb, int alpha) {
        int steps = 48;
        for (int i = 0; i < steps; i++) {
            double a0 = i * (Math.PI * 2 / steps);
            double a1 = (i + 1) * (Math.PI * 2 / steps);
            line(poseStack, lines,
                    (float) (Math.cos(a0) * radius), y, (float) (Math.sin(a0) * radius),
                    (float) (Math.cos(a1) * radius), y, (float) (Math.sin(a1) * radius),
                    rgb, alpha);
        }
    }

    /** Een cirkel die rechtop staat (voor het bolletje in de arsenaal-stand). */
    private void ringTilted(PoseStack poseStack, VertexConsumer lines, float radius, int rgb, int alpha, float yaw) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(90));
        ring(poseStack, lines, radius, 0, rgb, alpha);
        poseStack.popPose();
    }

    /** Het 3x3-rooster van een recept. */
    private void grid(PoseStack poseStack, VertexConsumer lines, int rgb, int alpha) {
        float cell = SIZE / 4.0f;
        float half = cell * 1.5f;
        for (int i = 0; i <= 3; i++) {
            float at = -half + i * cell;
            line(poseStack, lines, at, 0, -half, at, 0, half, rgb, alpha);
            line(poseStack, lines, -half, 0, at, half, 0, at, rgb, alpha);
        }
    }

    /** Een staafje omhoog vanaf (x, y, z). */
    private void vline(PoseStack poseStack, VertexConsumer lines, float x, float y, float z, float height,
                       int rgb, int alpha, float shade) {
        line(poseStack, lines, x, y, z, x, y + height, z, shade(rgb, shade), alpha);
    }

    /** Een klein kruisje in drie richtingen: een puntje dat je van elke kant ziet. */
    private void marker(PoseStack poseStack, VertexConsumer lines, float x, float y, float z, float size,
                        int rgb, int alpha) {
        line(poseStack, lines, x - size, y, z, x + size, y, z, rgb, alpha);
        line(poseStack, lines, x, y - size, z, x, y + size, z, rgb, alpha);
        line(poseStack, lines, x, y, z - size, x, y, z + size, rgb, alpha);
    }

    /** Een kruis: "hier klopt iets niet". */
    private void cross(PoseStack poseStack, VertexConsumer lines, float size, int rgb, int alpha) {
        line(poseStack, lines, -size, 0, -size, size, 0, size, rgb, alpha);
        line(poseStack, lines, -size, 0, size, size, 0, -size, rgb, alpha);
    }

    /** Eén lijnstuk. */
    private void line(PoseStack poseStack, VertexConsumer lines, float x0, float y0, float z0,
                      float x1, float y1, float z1, int rgb, int alpha) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        // De normaal wijst langs de lijn; dat verwacht RenderType.lines().
        float nx = x1 - x0;
        float ny = y1 - y0;
        float nz = z1 - z0;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-5f) {
            return;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        lines.addVertex(matrix, x0, y0, z0).setColor(r, g, b, alpha).setNormal(pose, nx, ny, nz);
        lines.addVertex(matrix, x1, y1, z1).setColor(r, g, b, alpha).setNormal(pose, nx, ny, nz);
    }

    /** Een voorwerp als zwevend model. */
    private void item(PoseStack poseStack, MultiBufferSource buffers, ItemStack stack,
                      float x, float y, float z, float scale, int light) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(scale, scale, scale);
        // Tegendraaien, zodat de voorwerpen rechtop blijven staan terwijl de kaart draait.
        items.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                poseStack, buffers, Minecraft.getInstance().level, 0);
        poseStack.popPose();
    }

    /** Dezelfde kleur, maar donkerder (0 = zwart, 1 = onveranderd). */
    private static int shade(int rgb, float factor) {
        int r = (int) (((rgb >> 16) & 0xFF) * factor);
        int g = (int) (((rgb >> 8) & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    /** Alleen tekenen als je in de buurt bent; een hologram van 100 blokken ver zie je toch niet. */
    @Override
    public boolean shouldRender(HoloTableBlockEntity table, Vec3 cameraPos) {
        return cameraPos.closerThan(Vec3.atCenterOf(table.getBlockPos()), VIEW_DISTANCE);
    }

}
