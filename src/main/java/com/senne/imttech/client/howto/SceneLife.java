package com.senne.imttech.client.howto;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.ImtTech;
import com.senne.imttech.client.CableRenderer;
import com.senne.imttech.howto.HowToCue;
import com.senne.imttech.turret.TurretBlock;
import com.senne.imttech.turret.TurretBlockEntity;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Wat er leeft en beweegt in het wereldje van een "How it works"-animatie (hoofdstuk 63, stap 7): wezens (een speler met
 * jouw skin en de bril op, een monster dat op een torentje afloopt), de bewegende delen van blokken die Minecraft met een
 * "block entity renderer" tekent (het geschut van een torentje, de schildkoepel, de beelden van een schermmuur), en
 * schoten. De wezens en block entities zijn echte objecten van het spel, maar ze staan nergens in de wereld: we tekenen
 * ze alleen.
 */
final class SceneLife {
    private final Map<String, Entity> actors = new HashMap<>();
    private final Map<String, Map<EquipmentSlot, ItemStack>> dressed = new HashMap<>();
    /** Welke gegevens elk wezen nu heeft (zie HowToCue.Actor#data). */
    private final Map<String, CompoundTag> loaded = new HashMap<>();
    private final Map<String, Integer> walkedTo = new HashMap<>();
    /** Waar de monsters nu staan (voor de torentjes, die erop mikken). */
    private final Map<String, Vec3> monsters = new HashMap<>();
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
    private final Map<BlockPos, CompoundTag> appliedData = new HashMap<>();
    /** Block entities waarvan het tekenen buiten de wereld niet lukt: die slaan we voortaan over. */
    private static final Set<Class<?>> BROKEN = new HashSet<>();

    // ================================================================ Wezens

    void renderActors(PoseStack pose, MultiBufferSource buffers, ScenePlayer player, float time, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        monsters.clear();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        boolean hideGui = mc.options.hideGui;
        Lighting.setupForEntityInInventory();
        dispatcher.setRenderShadow(false);
        // Geen naamkaartje boven de speler (dat tekent Minecraft alleen als de HUD aan staat).
        mc.options.hideGui = true;
        try {
            // Per wezen de laatste cue die nu loopt (een latere cue met dezelfde sleutel neemt het over).
            Map<String, HowToCue.Actor> active = new java.util.LinkedHashMap<>();
            for (HowToCue cue : player.scene.cues()) {
                if (cue.tick() > time) {
                    break;
                }
                if (cue instanceof HowToCue.Actor actor && time < actor.tick() + actor.duration()) {
                    active.put(actor.key(), actor);
                }
            }
            for (HowToCue.Actor actor : active.values()) {
                renderActor(mc, dispatcher, pose, buffers, actor, time, partialTick);
            }
        } finally {
            mc.options.hideGui = hideGui;
            dispatcher.setRenderShadow(true);
            Lighting.setupFor3DItems();
        }
    }

    private void renderActor(Minecraft mc, EntityRenderDispatcher dispatcher, PoseStack pose, MultiBufferSource buffers,
                             HowToCue.Actor actor, float time, float partialTick) {
        Entity entity = actors.computeIfAbsent(actor.key(), key -> create(mc, actor));
        if (entity == null) {
            return;
        }
        if (actor.type() != null && !actor.data().isEmpty() && !actor.data().equals(loaded.get(actor.key()))) {
            // Zijn gegevens (een robot met zijn onderdelen en printplaat, aan of neer), zoals het spel ze laadt. Daarna
            // zetten we hem hieronder gewoon op zijn plek.
            loaded.put(actor.key(), actor.data());
            try {
                entity.load(actor.data());
            } catch (RuntimeException e) {
                ImtTech.LOGGER.warn("[how it works] de gegevens van {} laden lukt niet", actor.key(), e);
            }
        }
        float progress = Mth.clamp((time - actor.tick()) / Math.max(1, actor.move()), 0.0f, 1.0f);
        Vec3 at = ScenePlayer.along(actor.path(), progress);
        boolean moving = actor.path().size() > 1 && progress < 1.0f;
        float yaw = actor.yaw();
        if (moving) {
            Vec3 ahead = ScenePlayer.along(actor.path(), Math.min(1.0f, progress + 0.02f));
            double dx = ahead.x - at.x;
            double dz = ahead.z - at.z;
            if (dx * dx + dz * dz > 1.0E-6) {
                yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            }
        }
        entity.setPos(at.x, at.y, at.z);
        entity.setYRot(yaw);
        entity.setXRot(0.0f);
        // Ook de "vorige" plek: anders denkt een drone dat hij vliegt (en draaien zijn rotors terwijl hij uit staat).
        entity.setOldPosAndRot();
        entity.tickCount = (int) time;
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
            living.yHeadRot = yaw;
            living.yHeadRotO = yaw;
            if (dressed.get(actor.key()) != actor.gear()) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    living.setItemSlot(slot, actor.gear().getOrDefault(slot, ItemStack.EMPTY).copy());
                }
                dressed.put(actor.key(), actor.gear());
            }
            walk(actor.key(), living, (int) time, moving);
        }
        if (entity instanceof Enemy) {
            monsters.put(actor.key(), at.add(0, entity.getBbHeight() * 0.6, 0));
        }
        dispatcher.render(entity, at.x, at.y, at.z, yaw, partialTick, pose, buffers, LightTexture.FULL_BRIGHT);
    }

    /**
     * Een nieuw wezen: een speler met jouw skin, een vreemde (met een gewone skin van Minecraft die bij zijn naam hoort:
     * zijn UUID staat niet in de spelerslijst, dus het spel kiest er een van de standaardskins bij), of een wezen van
     * {@code type}.
     */
    private static @Nullable Entity create(Minecraft mc, HowToCue.Actor actor) {
        if (actor.type() != null) {
            return actor.type().create(mc.level);
        }
        if (actor.data().contains("Profile")) {
            String name = actor.data().getString("Profile");
            UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            return new RemotePlayer(mc.level, new GameProfile(id, name));
        }
        return new RemotePlayer(mc.level, mc.player.getGameProfile());
    }

    /** De benen: per tick van de animatie een stapje verder zolang het wezen loopt (terugspoelen begint opnieuw). */
    private void walk(String key, LivingEntity living, int tick, boolean moving) {
        Integer last = walkedTo.get(key);
        if (last == null || tick < last || tick - last > 40) {
            last = tick;
        }
        while (last < tick) {
            living.walkAnimation.update(moving ? 0.9f : 0.0f, 0.4f);
            last++;
        }
        walkedTo.put(key, last);
    }

    // ================================================================ Bewegende delen van blokken

    void renderBlockEntities(PoseStack pose, MultiBufferSource buffers, ScenePlayer player, float time, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Set<BlockPos> present = new HashSet<>();
        for (Map.Entry<BlockPos, BlockState> entry : player.world.blocks().entrySet()) {
            BlockState state = entry.getValue();
            if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
                continue;
            }
            BlockPos pos = entry.getKey();
            present.add(pos);
            BlockEntity be = blockEntities.get(pos);
            if (be == null || be.getBlockState().getBlock() != state.getBlock()) {
                be = entityBlock.newBlockEntity(pos, state);
                if (be == null) {
                    continue;
                }
                be.setLevel(mc.level);
                blockEntities.put(pos, be);
                appliedData.remove(pos);
            }
            if (BROKEN.contains(be.getClass())) {
                continue;
            }
            be.setBlockState(state);
            BlockEntityRenderer<BlockEntity> renderer = mc.getBlockEntityRenderDispatcher().getRenderer(be);
            if (renderer == null) {
                continue;
            }
            applyData(mc, be, pos, player, time);
            if (be instanceof TurretBlockEntity turret) {
                aim(turret, pos, state, time);
            }
            pose.pushPose();
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            try {
                renderer.render(be, partialTick, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            } catch (RuntimeException e) {
                // Een renderer die de echte wereld nodig heeft: niet meer proberen (anders crasht het scherm elk frame).
                BROKEN.add(be.getClass());
                ImtTech.LOGGER.warn("[how it works] {} tekent niet buiten de wereld", be.getClass().getSimpleName(), e);
            }
            pose.popPose();
        }
        blockEntities.keySet().retainAll(present);
        appliedData.keySet().retainAll(present);
    }

    /** De gegevens van de laatste BlockData-cue voor dit blok (tot nu toe), in het block entity. */
    private void applyData(Minecraft mc, BlockEntity be, BlockPos pos, ScenePlayer player, float time) {
        HowToCue.BlockData latest = null;
        for (HowToCue cue : player.scene.cues()) {
            if (cue.tick() > time) {
                break;
            }
            if (cue instanceof HowToCue.BlockData data && data.pos().equals(pos)) {
                latest = data;
            }
        }
        if (latest == null) {
            return;
        }
        CompoundTag tag = latest.tag().copy();
        if (tag.getBoolean("ChangedAtScene")) {
            // De animatie van het blok (de koepel die opengaat) loopt mee met de tijd van de animatie.
            tag.putLong("ChangedAt", mc.level.getGameTime() - (long) (time - latest.tick()));
        } else if (tag.equals(appliedData.get(pos))) {
            return;
        }
        be.loadWithComponents(tag, mc.level.registryAccess());
        appliedData.put(pos, tag);
    }

    /** Een torentje mikt op het dichtstbijzijnde monster; zonder doelwit kijkt het rustig rond, zonder stroom hangt de loop. */
    private void aim(TurretBlockEntity turret, BlockPos pos, BlockState state, float time) {
        boolean active = (!state.hasProperty(TurretBlock.ONLINE) || state.getValue(TurretBlock.ONLINE))
                && (!state.hasProperty(TurretBlock.ENABLED) || state.getValue(TurretBlock.ENABLED));
        Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.3, 0);
        Vec3 target = null;
        for (Vec3 monster : monsters.values()) {
            if (target == null || monster.distanceToSqr(muzzle) < target.distanceToSqr(muzzle)) {
                target = monster;
            }
        }
        float yaw;
        float pitch;
        if (!active) {
            yaw = turret.yaw;
            pitch = -30.0f;
        } else if (target != null) {
            Vec3 aim = target.subtract(muzzle);
            yaw = (float) (Mth.atan2(aim.x, aim.z) * Mth.RAD_TO_DEG);
            pitch = (float) (Mth.atan2(aim.y, aim.horizontalDistance()) * Mth.RAD_TO_DEG);
        } else {
            yaw = time * 1.5f;
            pitch = 0.0f;
        }
        turret.yaw = yaw;
        turret.yawO = yaw;
        turret.pitch = pitch;
        turret.pitchO = pitch;
    }

    // ================================================================ Schoten

    void renderBeams(PoseStack pose, MultiBufferSource buffers, ScenePlayer player, float time) {
        for (HowToCue cue : player.scene.cues()) {
            if (cue.tick() > time) {
                break;
            }
            if (cue instanceof HowToCue.Beam beam && time < beam.tick() + beam.duration()) {
                CableRenderer.drawBeam(pose.last().pose(), buffers, beam.from(), beam.to(), 0.04, beam.color(),
                        LightTexture.FULL_BRIGHT);
            }
        }
    }

}
