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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
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
    /**
     * Een kaart per tafel. Er is maar één renderer voor alle holotafels samen, dus met één
     * gedeelde kaart gooiden twee tafels elkaars kaart elke frame weg en rekenden ze hem
     * steeds opnieuw uit.
     */
    private final Map<BlockPos, HoloMapData> maps = new HashMap<>();
    /**
     * Hoe hoog het reliëf wordt: een hoogteverschil van "1" op de kaart is zoveel blokken.
     * Omlaag minder dan omhoog, want onder het hologram staat de tafel zelf.
     */
    private static final float RELIEF_UP = 0.55f;
    private static final float RELIEF_DOWN = 0.18f;
    /** De bodem van het "blok landschap": de randen van de kaart lopen tot hier naar beneden. */
    private static final float MAP_FLOOR = -0.2f;

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
            case BLUEPRINT -> renderBlueprint(table, level, poseStack, buffers, lines, rgb);
            case ARSENAL -> renderArsenal(poseStack, lines, rgb, time);
        }
        poseStack.popPose();
    }

    // ---------------------------------------------------------------- Kaart

    /**
     * De reliëfkaart: een draadmodel van het landschap rond de tafel.
     *
     * Elk vakje is een punt op de hoogte van de grond daar, verbonden met zijn buren. Zo zie je
     * ook vlak land (als een net) en niet alleen de bergen; hoe hoger de grond, hoe feller de lijn.
     * Water tekenen we zwakker, zodat je rivieren en meren herkent.
     *
     * De randen lopen naar beneden tot een bodem, met een kader eromheen: zo ziet het eruit als
     * een uitgesneden blok landschap dat boven de tafel zweeft, en niet als een plat vel.
     *
     * De hoogtes rekenen we niet elke frame opnieuw uit - dat zijn duizenden opzoekingen - maar
     * hooguit eens per paar seconden (zie HoloMapData).
     */
    private void renderMap(HoloTableBlockEntity table, Level level, PoseStack poseStack,
                           VertexConsumer lines, int rgb, float time) {
        HoloMapData.Snapshot snapshot = mapFor(table).get(level, table.getBlockPos(), table.rangeBlocks());
        int cells = HoloMapData.CELLS;
        for (int cx = 0; cx < cells; cx++) {
            for (int cz = 0; cz < cells; cz++) {
                float height = snapshot.relativeHeight(cx, cz);
                if (Float.isNaN(height)) {
                    continue; // niets geladen daar
                }
                boolean water = snapshot.isWater(cx, cz);
                float x = cellCentre(cx);
                float z = cellCentre(cz);
                float y = relief(height);
                float right = snapshot.relativeHeight(cx + 1, cz);
                if (!Float.isNaN(right)) {
                    meshLine(poseStack, lines, x, y, z, cellCentre(cx + 1), relief(right), z, rgb,
                            (height + right) / 2, water && snapshot.isWater(cx + 1, cz));
                }
                float below = snapshot.relativeHeight(cx, cz + 1);
                if (!Float.isNaN(below)) {
                    meshLine(poseStack, lines, x, y, z, x, relief(below), cellCentre(cz + 1), rgb,
                            (height + below) / 2, water && snapshot.isWater(cx, cz + 1));
                }
                // De rand: een lijn van het landschap naar de bodem.
                if (cx == 0 || cz == 0 || cx == cells - 1 || cz == cells - 1) {
                    line(poseStack, lines, x, y, z, x, MAP_FLOOR, z, shade(rgb, 0.7f), 110);
                }
            }
        }
        frame(poseStack, lines, rgb);

        // De tafel zelf als een klein baken in het midden.
        marker(poseStack, lines, 0, groundAt(snapshot, table.getBlockPos().getX() + 0.5,
                table.getBlockPos().getZ() + 0.5) + 0.03f, 0, 0.06f, 0xFFFFFF, 255);

        // Wat er leeft in de buurt, en waar je naartoe wil.
        renderWaypoints(table, level, poseStack, lines, snapshot);
        renderEntities(table, level, poseStack, lines, snapshot, time);
    }

    /**
     * Het kader om de bodem van de kaart, met een pijltje dat naar het noorden wijst. Het
     * hologram draait rond, dus zonder dat pijltje weet je niet welke kant noord is.
     */
    private void frame(PoseStack poseStack, VertexConsumer lines, int rgb) {
        float half = SIZE / 2.0f;
        float y = MAP_FLOOR;
        line(poseStack, lines, -half, y, -half, half, y, -half, rgb, 170);
        line(poseStack, lines, half, y, -half, half, y, half, rgb, 170);
        line(poseStack, lines, half, y, half, -half, y, half, rgb, 170);
        line(poseStack, lines, -half, y, half, -half, y, -half, rgb, 170);
        // Noord is -z in Minecraft.
        float tip = -half - 0.16f;
        float back = -half - 0.05f;
        line(poseStack, lines, -0.06f, y, back, 0, y, tip, 0xFFFFFF, 230);
        line(poseStack, lines, 0.06f, y, back, 0, y, tip, 0xFFFFFF, 230);
        line(poseStack, lines, -0.06f, y, back, 0.06f, y, back, 0xFFFFFF, 230);
    }

    /** Van een hoogte op de kaart (-1 tot 1) naar een hoogte in het hologram. */
    private static float relief(float height) {
        return height >= 0 ? height * RELIEF_UP : height * RELIEF_DOWN;
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
            // Een paaltje dat uit het landschap omhoog steekt, met een kruisje erop.
            float ground = groundAt(snapshot, waypoint.pos().getX() + 0.5, waypoint.pos().getZ() + 0.5);
            vline(poseStack, lines, spot[0], ground, spot[1], 0.34f, waypoint.color(), 235, 1.0f);
            marker(poseStack, lines, spot[0], ground + 0.34f, spot[1], 0.05f, waypoint.color(), 255);
        }
    }

    /** Spelers in het wit, vijanden in het rood: een levende kaart. */
    private void renderEntities(HoloTableBlockEntity table, Level level, PoseStack poseStack,
                                VertexConsumer lines, HoloMapData.Snapshot snapshot, float time) {
        AABB box = new AABB(table.getBlockPos()).inflate(snapshot.radius());
        // Een klein pulsje, zodat de stipjes opvallen tussen de lijnen van het landschap.
        float pulse = 0.035f + 0.01f * Mth.sin(time / 6.0f);
        for (Entity entity : level.getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive())) {
            float[] spot = snapshot.project(entity.getX(), entity.getZ(), SIZE);
            if (spot == null) {
                continue;
            }
            int colour = entity instanceof Player ? 0xFFFFFF : entity instanceof Enemy ? 0xFF4545 : 0x5CFF9D;
            float y = groundAt(snapshot, entity.getX(), entity.getZ());
            // Een steeltje vanaf de grond, met het stipje erop: zo zie je waar hij staat.
            line(poseStack, lines, spot[0], y, spot[1], spot[0], y + 0.08f, spot[1], colour, 160);
            marker(poseStack, lines, spot[0], y + 0.08f, spot[1], pulse, colour, 255);
        }
    }

    // ---------------------------------------------------------------- Bouwtekening

    /** Het recept van het voorwerp op tafel: het rooster met de onderdelen, en het resultaat erboven. */
    private void renderBlueprint(HoloTableBlockEntity table, Level level, PoseStack poseStack,
                                 MultiBufferSource buffers, VertexConsumer lines, int rgb) {
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
                        // Een beetje boven het rooster, anders zakt het voorwerp er half doorheen.
                        item(poseStack, buffers, ingredient, x, 0.12f, z, 0.42f);
                    }
                }
            }
        }
        // Het resultaat zweeft erboven, groter.
        item(poseStack, buffers, wanted, 0, 0.62f, 0, 0.7f);
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

    /**
     * Een voorwerp als zwevend model. Het draait mee met het hologram, als op een draaischijf.
     *
     * Het licht staat op vol: een hologram geeft zelf licht. Met het licht van de plek waren de
     * voorwerpen 's nachts en binnenshuis bijna zwart.
     */
    private void item(PoseStack poseStack, MultiBufferSource buffers, ItemStack stack,
                      float x, float y, float z, float scale) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(scale, scale, scale);
        items.renderStatic(stack, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                poseStack, buffers, Minecraft.getInstance().level, 0);
        poseStack.popPose();
    }

    /** De kaart van deze tafel (en ruim de kaarten op als er heel veel tafels voorbij zijn gekomen). */
    private HoloMapData mapFor(HoloTableBlockEntity table) {
        if (maps.size() > 32) {
            maps.clear();
        }
        return maps.computeIfAbsent(table.getBlockPos().immutable(), pos -> new HoloMapData());
    }

    /** Waar vakje nummer {@code cell} in het hologram ligt (het midden van het vakje). */
    private static float cellCentre(int cell) {
        return ((cell + 0.5f) / HoloMapData.CELLS - 0.5f) * SIZE;
    }

    /** Hoe hoog het landschap in het hologram is op deze plek in de wereld. */
    private static float groundAt(HoloMapData.Snapshot snapshot, double worldX, double worldZ) {
        float height = snapshot.relativeHeightAt(worldX, worldZ);
        return Float.isNaN(height) ? 0.0f : relief(height);
    }

    /**
     * Eén lijntje van het landschap; hoe hoger, hoe feller. Vlak land is doorschijnend, zodat
     * het net niet dichtslibt tot een plaat en de heuvels eruit springen.
     */
    private void meshLine(PoseStack poseStack, VertexConsumer lines, float x0, float y0, float z0,
                          float x1, float y1, float z1, int rgb, float height, boolean water) {
        if (water) {
            line(poseStack, lines, x0, y0, z0, x1, y1, z1, shade(rgb, 0.55f), 55);
            return;
        }
        float up = Mth.clamp(height * 2.5f, 0.0f, 1.0f);
        float brightness = 0.6f + 0.4f * up;
        int alpha = (int) (85 + 170 * up);
        line(poseStack, lines, x0, y0, z0, x1, y1, z1, shade(rgb, brightness), alpha);
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

    /**
     * Het stuk wereld waarin het hologram kan zitten.
     *
     * Minecraft tekent een blok alleen als dit vak in beeld is, en standaard is dat alleen het
     * blok zelf. Het hologram zweeft erboven en steekt aan de zijkanten uit, dus keek je omhoog
     * naar het hologram met de tafel net buiten beeld, dan verdween het hele hologram.
     */
    @Override
    public AABB getRenderBoundingBox(HoloTableBlockEntity table) {
        return new AABB(table.getBlockPos()).inflate(1.6, 0.0, 1.6).expandTowards(0.0, 3.0, 0.0);
    }

}
