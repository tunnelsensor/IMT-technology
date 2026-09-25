package com.senne.starktech.client.illusion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.starktech.illusion.IllusionScene;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Tekent de illusies van de cloakingdrones in JOUW spel.
 *
 * Alles wat je hier ziet is nep: de blokken en wezens bestaan alleen in beeld. Daarom tekenen
 * we ze zelf, en hoeven we niets aan de wereld te veranderen.
 *
 * De zwakke plek uit de film zit er ook in: waar de velden van twee verschillende spelers
 * elkaar overlappen, gaat het beeld flikkeren en zie je stukken van de echte wereld
 * doorschemeren. Je eigen illusies storen elkaar niet: zet je twee muren naast elkaar, dan
 * flikkerde vroeger alles (en leek het kapot).
 *
 * Richt je zelf een illusie (zie EdithIllusions), dan tekenen we hier ook de voorvertoning: het
 * beeld op de grond onder je drone, met een blauw kader eromheen en een lijn naar de drone.
 */
public final class IllusionRenderer {
    private IllusionRenderer() {}

    /** Eén illusie die nu in de buurt is; {@code anchor} is het punt op de grond waar hij staat. */
    public record Projection(int droneId, UUID owner, double radius, boolean jammed, BlockPos anchor, IllusionScene scene) {}

    /** Het veld van een illusie: waar het midden is, hoe groot, en van wie. */
    private record Field(Vec3 centre, double radius, UUID owner) {}

    /**
     * Eén nep-wezen van een leger dat loopt: waar het nu is en vorige tick (om er vloeiend
     * tussenin te tekenen), welke kant het op kijkt, en of het aan het slaan is.
     */
    private static final class Walker {
        Vec3 pos;
        Vec3 prev;
        float yaw;
        float prevYaw;
        /** Hoeveel het deze tick liep (voor de loopbeweging van de benen). */
        float moved;
        boolean attacking;

        Walker(Vec3 pos, float yaw) {
            this.pos = pos;
            this.prev = pos;
            this.yaw = yaw;
            this.prevYaw = yaw;
        }
    }

    /** Per leger (drone) de wezens die lopen, in dezelfde volgorde als in de scène. */
    private static final Map<Integer, List<Walker>> WALKERS = new HashMap<>();
    /** Hoe snel ze lopen (blokken per tick): ongeveer een zombie die op je af komt. */
    private static final double WALK_SPEED = 0.16;
    /** Zo dicht gaan ze om hun doelwit heen staan. */
    private static final double REACH = 1.3;
    /** Zo ver van het beeld zoeken ze iemand om aan te vallen, en verder dan LEASH gaan ze niet. */
    private static final double HUNT_RANGE = 24;
    private static final double LEASH = 30;
    /** Eén klap duurt zoveel ticks. */
    private static final int SWING_TICKS = 12;

    /** Alle illusies die jouw spel kent, per drone. */
    private static final Map<Integer, Projection> PROJECTIONS = new HashMap<>();
    /**
     * Nep-wezens die we hergebruiken, per soort én per plek in de rij.
     *
     * Elk nep-wezen heeft zijn eigen lichaam nodig: deelden ze er één, dan stonden ze allemaal
     * in precies dezelfde houding, keken ze dezelfde kant op en viel meteen op dat het nep is.
     */
    private static final Map<String, Entity> FAKE_ENTITIES = new HashMap<>();

    /** Bericht van de server: nieuwe (of gestopte) illusie. */
    public static void onServerMessage(int droneId, UUID owner, double radius, boolean jammed, BlockPos anchor,
                                       @Nullable IllusionScene scene) {
        if (scene == null) {
            PROJECTIONS.remove(droneId);
            if (droneId < 0) {
                PROJECTIONS.clear(); // "vergeet alles"
            }
            return;
        }
        PROJECTIONS.put(droneId, new Projection(droneId, owner, radius, jammed, anchor, scene));
    }

    /** De drone waarmee je nu een illusie richt, of -1. */
    private static int aimDroneId = -1;
    /** Wat je aan het richten bent (nog niet gedraaid naar je kijkrichting). */
    private static @Nullable IllusionScene aimScene;

    /** Bericht van de server: je richt nu met deze drone (of: klaar met richten, bij null). */
    public static void onAim(int droneId, @Nullable IllusionScene scene) {
        aimDroneId = scene == null ? -1 : droneId;
        aimScene = scene;
    }

    /** Ben je een illusie aan het richten? */
    public static boolean isAiming() {
        return aimScene != null && aimDroneId >= 0;
    }

    public static void clear() {
        PROJECTIONS.clear();
        FAKE_ENTITIES.clear();
        WALKERS.clear();
        aimDroneId = -1;
        aimScene = null;
    }

    /** Hoeveel illusies ken je nu (voor de HUD)? */
    public static int count() {
        return PROJECTIONS.size();
    }

    // ================================================================ Het nep-leger valt aan

    /**
     * Elke tick: de wezens van een nep-leger zoeken de dichtstbijzijnde speler die niet de
     * eigenaar is, lopen erheen, gaan in een kring om hem heen staan en slaan (zonder schade:
     * het is alleen beeld, in het spel van wie kijkt). Zo leid je iemand af. Is er niemand in de
     * buurt, dan lopen ze terug naar hun plek.
     */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        WALKERS.keySet().removeIf(id -> !PROJECTIONS.containsKey(id));
        for (Projection projection : PROJECTIONS.values()) {
            if (projection.scene().kind() != IllusionScene.Kind.ARMY || projection.jammed()) {
                continue;
            }
            List<Walker> walkers = WALKERS.computeIfAbsent(projection.droneId(), id -> startWalkers(mc, projection));
            tickWalkers(mc, projection, walkers);
        }
    }

    /** Waar een wezen hoort als het niets te doen heeft: zijn plek in de scène. */
    private static Vec3 home(Minecraft mc, IllusionScene.MobElement element, BlockPos anchor) {
        double x = anchor.getX() + 0.5 + element.x();
        double z = anchor.getZ() + 0.5 + element.z();
        return new Vec3(x, IllusionScene.feetY(mc.level, x, z, anchor.getY()) + element.y(), z);
    }

    private static List<Walker> startWalkers(Minecraft mc, Projection projection) {
        List<Walker> walkers = new ArrayList<>();
        for (IllusionScene.MobElement element : projection.scene().mobs()) {
            walkers.add(new Walker(home(mc, element, projection.anchor()), element.yaw()));
        }
        return walkers;
    }

    private static void tickWalkers(Minecraft mc, Projection projection, List<Walker> walkers) {
        List<IllusionScene.MobElement> mobs = projection.scene().mobs();
        Vec3 centre = Vec3.atBottomCenterOf(projection.anchor());
        for (int i = 0; i < walkers.size() && i < mobs.size(); i++) {
            Walker walker = walkers.get(i);
            IllusionScene.MobElement element = mobs.get(i);
            walker.prev = walker.pos;
            walker.prevYaw = walker.yaw;

            Player victim = victim(mc, walker.pos, centre, projection.owner());
            Vec3 goal;
            if (victim != null) {
                // Elk wezen zijn eigen plekje in een kring om het doelwit, anders lopen ze in elkaar.
                double angle = i * (Math.PI * 2 / walkers.size());
                goal = victim.position().add(Math.cos(angle) * REACH, 0, Math.sin(angle) * REACH);
            } else {
                goal = home(mc, element, projection.anchor());
            }
            double dx = goal.x - walker.pos.x;
            double dz = goal.z - walker.pos.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            walker.moved = 0;
            if (distance > 0.15) {
                double step = Math.min(WALK_SPEED, distance);
                double x = walker.pos.x + dx / distance * step;
                double z = walker.pos.z + dz / distance * step;
                // Niet verder dan de lijn: het blijft een beeld rond de drone.
                if (Math.hypot(x - centre.x, z - centre.z) <= LEASH) {
                    double y = IllusionScene.feetY(mc.level, x, z, (int) Math.floor(walker.pos.y)) + element.y();
                    walker.pos = new Vec3(x, y, z);
                    walker.moved = (float) step;
                }
            }
            // Draaien naar waar ze heen gaan (of naar hun doelwit), en thuis weer hun eigen kant op.
            Vec3 lookAt = victim != null ? victim.position() : goal;
            double lx = lookAt.x - walker.pos.x;
            double lz = lookAt.z - walker.pos.z;
            float wanted = victim == null && distance <= 0.15 ? element.yaw()
                    : (float) (Mth.atan2(lz, lx) * Mth.RAD_TO_DEG) - 90.0f;
            walker.yaw = Mth.rotLerp(0.3f, walker.yaw, wanted);

            boolean wasAttacking = walker.attacking;
            walker.attacking = victim != null && victim.position().distanceTo(walker.pos) <= REACH + 0.8;
            // De benen bewegen mee met hoeveel ze liepen (zoals Minecraft dat voor echte wezens doet).
            if (fakeEntity(mc, element, i + 1) instanceof LivingEntity living) {
                living.walkAnimation.update(Math.min(walker.moved * 4.0f, 1.0f), 0.4f);
            }
            // Bij elke klap een tik, maar dan zonder schade.
            if (walker.attacking && wasAttacking && (mc.level.getGameTime() + i * 5L) % SWING_TICKS == 0) {
                mc.level.playLocalSound(victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.HOSTILE, 0.4f, 0.9f + i * 0.03f, false);
            }
        }
    }

    /** De dichtstbijzijnde speler die niet de eigenaar is, niet te ver van het beeld. */
    private static @Nullable Player victim(Minecraft mc, Vec3 from, Vec3 centre, UUID owner) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : mc.level.players()) {
            if (player.getUUID().equals(owner) || player.isSpectator() || !player.isAlive()
                    || Math.hypot(player.getX() - centre.x, player.getZ() - centre.z) > HUNT_RANGE) {
                continue;
            }
            double distance = player.position().distanceToSqr(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    // ================================================================ Tekenen

    /**
     * Wordt elke frame aangeroepen, net nadat Minecraft de vaste blokken getekend heeft.
     * Wij tekenen daar onze nep-blokken en nep-wezens bij.
     */
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        // Meteen even de camera-matrices van dit frame bewaren: daarmee tekent de HUD zijn
        // beugels precies om een doelwit heen (zie WorldProjection).
        com.senne.starktech.client.WorldProjection.capture(event.getModelViewMatrix(), event.getProjectionMatrix());
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (PROJECTIONS.isEmpty() && !isAiming())) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();

        if (isAiming()) {
            renderPreview(mc, camera, poseStack, buffers, partialTick, now);
        }

        // Eerst alle velden (rond de drones) opzoeken, zodat we overlap kunnen zien.
        List<Field> fields = new ArrayList<>();
        for (Projection projection : PROJECTIONS.values()) {
            Entity drone = mc.level.getEntity(projection.droneId());
            if (drone != null) {
                fields.add(new Field(drone.position(), projection.radius(), projection.owner()));
            }
        }

        for (Projection projection : PROJECTIONS.values()) {
            Entity drone = mc.level.getEntity(projection.droneId());
            if (drone == null || projection.jammed()) {
                continue; // drone weg of platgelegd door een E.M.P.: geen beeld
            }
            renderBlocks(mc, projection.scene(), projection.anchor(), camera, poseStack, buffers, fields, now);
            renderMobs(mc, projection.scene(), projection.anchor(), camera, poseStack, buffers, partialTick, fields, now,
                    WALKERS.get(projection.droneId()));
        }
        buffers.endBatch();
    }

    /**
     * De voorvertoning tijdens het richten: het beeld op de grond waar je drone naar kijkt (de
     * muur dwars op je kijkrichting), met een knipperend blauw kader eromheen en een richtlijn
     * van de drone ernaartoe.
     */
    private static void renderPreview(Minecraft mc, Vec3 camera, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                      float partialTick, long now) {
        Entity drone = mc.level.getEntity(aimDroneId);
        if (drone == null || aimScene == null || mc.player == null) {
            return;
        }
        Vec3 dronePos = drone.getEyePosition(partialTick);
        BlockPos anchor = IllusionScene.aimAnchor(mc.level, dronePos, mc.player.getViewYRot(partialTick),
                mc.player.getViewXRot(partialTick));
        IllusionScene scene = aimScene.facing(mc.player.getYRot());
        renderBlocks(mc, scene, anchor, camera, poseStack, buffers, List.of(), now);
        renderMobs(mc, scene, anchor, camera, poseStack, buffers, partialTick, List.of(), now, null);

        // Het kader: om alle blokken heen, of om de kring van wezens.
        AABB box = previewBox(scene, anchor);
        float pulse = 0.55f + 0.45f * Mth.sin((now + partialTick) * 0.25f);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(poseStack, lines, box, 0.25f, 0.85f, 1.0f, pulse);
        // Een richtlijn van net onder de drone naar het midden van het beeld.
        PoseStack.Pose pose = poseStack.last();
        Vec3 from = dronePos.add(0, -0.4, 0);
        Vec3 ground = new Vec3(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
        Vec3 dir = ground.subtract(from).normalize();
        lines.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(0.25f, 0.85f, 1.0f, 0.8f).setNormal(pose, (float) dir.x, (float) dir.y, (float) dir.z);
        lines.addVertex(pose, (float) ground.x, (float) ground.y, (float) ground.z)
                .setColor(0.25f, 0.85f, 1.0f, 0.8f).setNormal(pose, (float) dir.x, (float) dir.y, (float) dir.z);
        poseStack.popPose();
    }

    /** Het kader om een voorvertoning: de blokken, of de wezens (twee blokken hoog). */
    private static AABB previewBox(IllusionScene scene, BlockPos anchor) {
        AABB box = null;
        for (IllusionScene.BlockElement element : scene.blocks()) {
            AABB block = new AABB(anchor.offset(element.offset()));
            box = box == null ? block : box.minmax(block);
        }
        for (IllusionScene.MobElement element : scene.mobs()) {
            double x = anchor.getX() + 0.5 + element.x();
            double z = anchor.getZ() + 0.5 + element.z();
            AABB mob = new AABB(x - 0.4, anchor.getY(), z - 0.4, x + 0.4, anchor.getY() + 2.0, z + 0.4);
            box = box == null ? mob : box.minmax(mob);
        }
        return box == null ? new AABB(anchor) : box.inflate(0.05);
    }

    /** De nep-blokken tekenen, ten opzichte van het ankerpunt. */
    private static void renderBlocks(Minecraft mc, IllusionScene scene, BlockPos anchor, Vec3 camera, PoseStack poseStack,
                                     MultiBufferSource buffers, List<Field> fields, long now) {
        for (IllusionScene.BlockElement element : scene.blocks()) {
            BlockPos pos = anchor.offset(element.offset());
            Vec3 world = Vec3.atLowerCornerOf(pos);
            if (world.distanceToSqr(camera) > 128 * 128) {
                continue; // te ver om te zien
            }
            if (glitching(world, fields, now)) {
                continue; // in de overlapzone: dit stukje flikkert weg
            }
            poseStack.pushPose();
            poseStack.translate(world.x - camera.x, world.y - camera.y, world.z - camera.z);
            int light = LevelRenderer.getLightColor(mc.level, pos);
            mc.getBlockRenderer().renderSingleBlock(element.state(), poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }

    /**
     * De nep-wezens tekenen. Elk wezen staat op de grond onder zijn eigen plek, zodat ze op een
     * heuvel niet in de grond verdwijnen. Een leger dat loopt (zie onClientTick) tekenen we waar
     * het nu is, met loopbeweging en, bij het aanvallen, geheven armen en klappen.
     */
    private static void renderMobs(Minecraft mc, IllusionScene scene, BlockPos anchor, Vec3 camera, PoseStack poseStack,
                                   MultiBufferSource buffers, float partialTick, List<Field> fields, long now,
                                   @Nullable List<Walker> walkers) {
        int index = 0;
        for (IllusionScene.MobElement element : scene.mobs()) {
            index++;
            Walker walker = walkers != null && index - 1 < walkers.size() ? walkers.get(index - 1) : null;
            Vec3 world = walker != null ? walker.prev.lerp(walker.pos, partialTick) : home(mc, element, anchor);
            if (world.distanceToSqr(camera) > 96 * 96 || glitching(world, fields, now)) {
                continue;
            }
            Entity fake = fakeEntity(mc, element, index);
            if (fake == null) {
                continue;
            }
            // Ze wiegen zachtjes, elk in zijn eigen tempo, en hun hoofd volgt jou. Zo ziet een
            // nep-leger er levend uit in plaats van als een rij standbeelden.
            float sway = Mth.sin((now + index * 7) * 0.05f) * 6.0f;
            fake.setPos(world.x, world.y, world.z);
            fake.xOld = world.x;
            fake.yOld = world.y;
            fake.zOld = world.z;
            // Eigen tijd per wezen: dan ademen en knipperen ze niet allemaal tegelijk.
            fake.tickCount = (int) (now + index * 13L);
            boolean busy = walker != null && (walker.moved > 0 || walker.attacking);
            float bodyYaw = walker != null ? Mth.rotLerp(partialTick, walker.prevYaw, walker.yaw) + (busy ? 0 : sway)
                    : element.yaw() + sway;
            fake.setYRot(bodyYaw);
            fake.yRotO = bodyYaw;
            if (fake instanceof Mob mob) {
                // Een zombie die aanvalt, steekt zijn armen naar voren.
                mob.setAggressive(busy);
            }
            if (fake instanceof LivingEntity living) {
                living.yBodyRot = bodyYaw;
                living.yBodyRotO = bodyYaw;
                float headYaw = bodyYaw;
                float headPitch = 0.0f;
                if (mc.player != null && !busy) {
                    // Naar de speler kijken, maar niet verder dan een nek kan draaien.
                    Vec3 toPlayer = mc.player.getEyePosition().subtract(world.x, world.y + fake.getEyeHeight(), world.z);
                    float wanted = (float) (Mth.atan2(toPlayer.z, toPlayer.x) * Mth.RAD_TO_DEG) - 90.0f;
                    headYaw = bodyYaw + Mth.clamp(Mth.wrapDegrees(wanted - bodyYaw), -60.0f, 60.0f);
                    double flat = Math.sqrt(toPlayer.x * toPlayer.x + toPlayer.z * toPlayer.z);
                    headPitch = Mth.clamp((float) (-Mth.atan2(toPlayer.y, flat) * Mth.RAD_TO_DEG), -30.0f, 30.0f);
                }
                living.yHeadRot = headYaw;
                living.yHeadRotO = headYaw;
                living.setYHeadRot(headYaw);
                living.setXRot(headPitch);
                living.xRotO = headPitch;
                if (walker == null || walker.moved <= 0) {
                    // Stilstaan, maar niet bevroren: geen loopbeweging, wel de idle-animatie.
                    living.walkAnimation.setSpeed(0.0f);
                }
                // Aan het slaan: de armen gaan in een ronde van SWING_TICKS heen en weer.
                if (walker != null && walker.attacking) {
                    long t = now + (index - 1) * 5L;
                    living.oAttackAnim = Math.floorMod(t - 1, SWING_TICKS) / (float) SWING_TICKS;
                    living.attackAnim = Math.floorMod(t, SWING_TICKS) / (float) SWING_TICKS;
                } else {
                    living.oAttackAnim = 0;
                    living.attackAnim = 0;
                }
            }
            mc.getEntityRenderDispatcher().render(fake, world.x - camera.x, world.y - camera.y, world.z - camera.z,
                    fake.getYRot(), partialTick, poseStack, buffers, LevelRenderer.getLightColor(mc.level, BlockPos.containing(world)));
        }
    }

    /** Maakt (of hergebruikt) een nep-wezen voor deze plek in de rij. Het komt NIET in de wereld. */
    private static @Nullable Entity fakeEntity(Minecraft mc, IllusionScene.MobElement element, int index) {
        String key = element.type() + "#" + index;
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
     * Zit dit punt in de overlap van de velden van twee verschillende spelers? Dan "breekt" het
     * beeld daar: precies de zwakke plek uit de film. We laten het flikkeren, dus af en toe zie
     * je de echte wereld. Velden van dezelfde speler tellen samen als één.
     */
    private static boolean glitching(Vec3 point, List<Field> fields, long now) {
        Set<UUID> owners = new HashSet<>();
        for (Field field : fields) {
            if (field.centre().distanceTo(point) <= field.radius()) {
                owners.add(field.owner());
            }
        }
        if (owners.size() < 2) {
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
