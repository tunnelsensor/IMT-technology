package com.senne.starktech.client.illusion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.starktech.illusion.IllusionScene;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Tekent de illusies van de cloakingdrones in JOUW spel.
 *
 * Alles wat je hier ziet is nep: de blokken en wezens bestaan alleen in beeld. Daarom tekenen
 * we ze zelf, en hoeven we niets aan de wereld te veranderen.
 *
 * De zwakke plek uit de film zit er ook in: waar twee velden elkaar overlappen, gaat het beeld
 * flikkeren en zie je stukken van de echte wereld doorschemeren.
 */
public final class IllusionRenderer {
    private IllusionRenderer() {}

    /** Eén illusie die nu in de buurt is. */
    public record Projection(int droneId, double radius, boolean jammed, IllusionScene scene) {}

    /** Alle illusies die jouw spel kent, per drone. */
    private static final Map<Integer, Projection> PROJECTIONS = new HashMap<>();
    /** Nep-wezens die we hergebruiken (per soort), zodat we niet elke frame nieuwe maken. */
    private static final Map<String, Entity> FAKE_ENTITIES = new HashMap<>();

    /** Bericht van de server: nieuwe (of gestopte) illusie. */
    public static void onServerMessage(int droneId, double radius, boolean jammed, @Nullable IllusionScene scene) {
        if (scene == null) {
            PROJECTIONS.remove(droneId);
            if (droneId < 0) {
                PROJECTIONS.clear(); // "vergeet alles"
            }
            return;
        }
        PROJECTIONS.put(droneId, new Projection(droneId, radius, jammed, scene));
    }

    public static void clear() {
        PROJECTIONS.clear();
        FAKE_ENTITIES.clear();
    }

    /** Hoeveel illusies ken je nu (voor de HUD)? */
    public static int count() {
        return PROJECTIONS.size();
    }

    // ================================================================ Tekenen

    /**
     * Wordt elke frame aangeroepen, net nadat Minecraft de vaste blokken getekend heeft.
     * Wij tekenen daar onze nep-blokken en nep-wezens bij.
     */
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || PROJECTIONS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();

        // Eerst alle middens (de drones) opzoeken, zodat we overlap kunnen zien.
        List<Vec3> centers = new ArrayList<>();
        List<Double> radii = new ArrayList<>();
        for (Projection projection : PROJECTIONS.values()) {
            Entity drone = mc.level.getEntity(projection.droneId());
            if (drone != null) {
                centers.add(drone.position());
                radii.add(projection.radius());
            }
        }

        for (Projection projection : PROJECTIONS.values()) {
            Entity drone = mc.level.getEntity(projection.droneId());
            if (drone == null || projection.jammed()) {
                continue; // drone weg of platgelegd door een E.M.P.: geen beeld
            }
            Vec3 center = drone.position();
            renderBlocks(mc, projection, center, camera, poseStack, buffers, centers, radii, now);
            renderMobs(mc, projection, center, camera, poseStack, buffers, partialTick, centers, radii, now);
        }
        buffers.endBatch();
    }

    /** De nep-blokken tekenen. */
    private static void renderBlocks(Minecraft mc, Projection projection, Vec3 center, Vec3 camera, PoseStack poseStack,
                                     MultiBufferSource buffers, List<Vec3> centers, List<Double> radii, long now) {
        BlockPos droneBlock = BlockPos.containing(center);
        for (IllusionScene.BlockElement element : projection.scene().blocks()) {
            BlockPos pos = droneBlock.offset(element.offset());
            Vec3 world = Vec3.atLowerCornerOf(pos);
            if (world.distanceToSqr(camera) > 128 * 128) {
                continue; // te ver om te zien
            }
            if (glitching(world, centers, radii, now)) {
                continue; // in de overlapzone: dit stukje flikkert weg
            }
            poseStack.pushPose();
            poseStack.translate(world.x - camera.x, world.y - camera.y, world.z - camera.z);
            int light = LevelRenderer.getLightColor(mc.level, pos);
            mc.getBlockRenderer().renderSingleBlock(element.state(), poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }

    /** De nep-wezens tekenen (ze staan er alleen; ze doen niets). */
    private static void renderMobs(Minecraft mc, Projection projection, Vec3 center, Vec3 camera, PoseStack poseStack,
                                   MultiBufferSource buffers, float partialTick, List<Vec3> centers, List<Double> radii,
                                   long now) {
        int index = 0;
        for (IllusionScene.MobElement element : projection.scene().mobs()) {
            index++;
            Vec3 world = center.add(element.x(), element.y(), element.z());
            if (world.distanceToSqr(camera) > 96 * 96 || glitching(world, centers, radii, now)) {
                continue;
            }
            Entity fake = fakeEntity(mc, element);
            if (fake == null) {
                continue;
            }
            // Een beetje leven in de brouwerij: ze wiegen zachtjes heen en weer.
            float sway = Mth.sin((now + index * 7) * 0.05f) * 6.0f;
            fake.setPos(world.x, world.y, world.z);
            fake.setYRot(element.yaw() + sway);
            fake.yRotO = fake.getYRot();
            if (fake instanceof LivingEntity living) {
                living.yBodyRot = fake.getYRot();
                living.yHeadRot = fake.getYRot();
                living.setYHeadRot(fake.getYRot());
            }
            mc.getEntityRenderDispatcher().render(fake, world.x - camera.x, world.y - camera.y, world.z - camera.z,
                    fake.getYRot(), partialTick, poseStack, buffers, LevelRenderer.getLightColor(mc.level, BlockPos.containing(world)));
        }
    }

    /** Maakt (of hergebruikt) een nep-wezen van deze soort. Het komt NIET in de wereld. */
    private static @Nullable Entity fakeEntity(Minecraft mc, IllusionScene.MobElement element) {
        String key = element.type().toString();
        Entity fake = FAKE_ENTITIES.get(key);
        if (fake == null) {
            fake = element.type().create(mc.level);
            if (fake == null) {
                return null;
            }
            FAKE_ENTITIES.put(key, fake);
        }
        return fake;
    }

    /**
     * Zit dit punt in de overlap van twee velden? Dan "breekt" het beeld daar: precies de
     * zwakke plek uit de film. We laten het flikkeren, dus af en toe zie je de echte wereld.
     */
    private static boolean glitching(Vec3 point, List<Vec3> centers, List<Double> radii, long now) {
        int fields = 0;
        for (int i = 0; i < centers.size(); i++) {
            if (centers.get(i).distanceTo(point) <= radii.get(i)) {
                fields++;
            }
        }
        if (fields < 2) {
            return false;
        }
        // Flikkeren: op basis van de tijd en de plek, zodat het rommelig oogt maar niet trilt.
        long seed = now / 3 + Math.round(point.x) * 31L + Math.round(point.y) * 17L + Math.round(point.z) * 7L;
        return Math.floorMod(seed, 5) < 2;
    }

    // ================================================================ Verbergen (cloak)

    /**
     * Zit een wezen in het veld van een CLOAK-illusie? Dan tekenen we het niet.
     * Zo kun je jezelf (of je hele basis) laten verdwijnen.
     */
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (PROJECTIONS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || event.getEntity() == mc.player) {
            return; // jezelf blijf je zien (anders raak je het spoor kwijt)
        }
        for (Projection projection : PROJECTIONS.values()) {
            if (projection.jammed() || projection.scene().kind() != IllusionScene.Kind.CLOAK) {
                continue;
            }
            Entity drone = mc.level.getEntity(projection.droneId());
            if (drone != null && drone.position().distanceTo(event.getEntity().position()) <= projection.radius()) {
                event.setCanceled(true);
                return;
            }
        }
    }
}
