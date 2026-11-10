package com.senne.imttech.client.howto;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.senne.imttech.ImtTech;
import com.senne.imttech.client.CableRenderer;
import com.senne.imttech.client.suit.SuitEffects;
import com.senne.imttech.cuffs.Handcuffs;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.howto.HowToCue;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.turret.TurretBlock;
import com.senne.imttech.turret.TurretBlockEntity;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

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
    /** Welke uitrusting elke speler nu draagt (zie HowToCue.Actor#worn). */
    private final Map<String, List<ItemStack>> worn = new HashMap<>();
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
        // De tijd eerst: een granaat die geladen wordt, telt zijn lont vanaf nu (zijn lampje knippert steeds sneller).
        entity.tickCount = (int) time;
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
            // Ook zijn snelheid (en die houdt hij als hij stilstaat): een sticky granaat die tegen een muur vloog, ligt dan
            // met zijn kussen tegen de muur (zie GrenadeRenderer).
            if (ahead.distanceToSqr(at) > 1.0E-8) {
                entity.setDeltaMovement(ahead.subtract(at).normalize().scale(0.5));
            }
        }
        entity.setPos(at.x, at.y, at.z);
        entity.setYRot(yaw);
        entity.setXRot(0.0f);
        if (entity instanceof com.senne.imttech.suit.MiniRocketEntity && actor.path().size() > 1) {
            // Een raketje wijst met zijn neus in de vliegrichting, ook omhoog en omlaag (zoals MiniRocketEntity#faceMotion).
            Vec3 from = ScenePlayer.along(actor.path(), Math.max(0.0f, progress - 0.02f));
            Vec3 to = ScenePlayer.along(actor.path(), Math.min(1.0f, progress + 0.02f));
            Vec3 motion = to.subtract(from);
            if (motion.lengthSqr() > 1.0E-8) {
                entity.setYRot((float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG));
                entity.setXRot((float) (Mth.atan2(motion.y, motion.horizontalDistance()) * Mth.RAD_TO_DEG));
            }
        }
        // Ook de "vorige" plek: anders denkt een drone dat hij vliegt (en draaien zijn rotors terwijl hij uit staat).
        entity.setOldPosAndRot();
        pose(entity, actor);
        if (entity instanceof Player person && worn.get(actor.key()) != actor.worn()) {
            wear(person, actor.worn());
            worn.put(actor.key(), actor.worn());
        }
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

    /**
     * Hoe een wezen erbij staat (hoofdstuk 63, de beveiliging): {@code "Crouch"} in zijn gegevens = sluipen, {@code "Cuffed"}
     * = geboeid (de handen op de rug, zoals het spel een geboeide speler of Raven-soldaat tekent: zie CuffRenderer).
     */
    private static void pose(Entity entity, HowToCue.Actor actor) {
        boolean crouch = actor.data().getBoolean("Crouch");
        if (entity.isShiftKeyDown() != crouch) {
            entity.setShiftKeyDown(crouch);
            entity.setPose(crouch ? Pose.CROUCHING : Pose.STANDING);
        }
        boolean cuffed = actor.data().getBoolean("Cuffed");
        if (Handcuffs.isCuffed(entity) != cuffed) {
            if (cuffed) {
                entity.setData(ModAttachments.CUFFS, new Handcuffs.State(true, Optional.empty()));
            } else {
                entity.removeData(ModAttachments.CUFFS);
            }
        }
    }

    /**
     * Wat een speler aan uitrusting draagt (de holster, de pouches, de twee slings en sinds hoofdstuk 72 een rugzak, zie
     * HowToCue.Actor#worn): dan tekent
     * het spel ze op zijn lijf. Met Curios in de modpack lukt dat bij een speler in de animatie niet (hij heeft geen
     * Curios-vakjes); dan zie je ze gewoon niet.
     */
    private static void wear(Player person, List<ItemStack> worn) {
        try {
            GearItem.Kind[] kinds = GearItem.Kind.values();
            for (int i = 0; i < kinds.length; i++) {
                Gear.set(person, kinds[i], i < worn.size() ? worn.get(i).copy() : ItemStack.EMPTY);
            }
            for (int i = 0; i < Gear.SLINGS; i++) {
                int index = kinds.length + i;
                Gear.setSling(person, i, index < worn.size() ? worn.get(index).copy() : ItemStack.EMPTY);
            }
            // Hoofdstuk 72: als zesde de rugzak op zijn rug.
            Gear.setBack(person, Gear.BACK < worn.size() ? worn.get(Gear.BACK).copy() : ItemStack.EMPTY);
        } catch (RuntimeException e) {
            ImtTech.LOGGER.warn("[how it works] de uitrusting van een speler lukt niet", e);
        }
    }

    // ================================================================ Voorwerpen, licht en rook

    /** Voorwerpen die zweven en ronddraaien (de onderdelen van een pistool, een patroon): ze springen even op als ze komen. */
    void renderItems(PoseStack pose, MultiBufferSource buffers, ScenePlayer player, float time) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Lighting.setupForEntityInInventory();
        try {
            for (HowToCue cue : player.scene.cues()) {
                if (cue.tick() > time) {
                    break;
                }
                if (cue instanceof HowToCue.Item item && time < item.tick() + item.duration()) {
                    float in = Mth.clamp((time - item.tick()) / 6.0f, 0.0f, 1.0f);
                    float out = Mth.clamp((item.tick() + item.duration() - time) / 6.0f, 0.0f, 1.0f);
                    float size = item.scale() * Math.min(in, out);
                    if (size <= 0.01f) {
                        continue;
                    }
                    pose.pushPose();
                    pose.translate(item.pos().x, item.pos().y + Mth.sin(time * 0.12f + item.tick()) * 0.04f, item.pos().z);
                    pose.mulPose(Axis.YP.rotationDegrees(time * 3.0f + item.tick() * 7.0f));
                    pose.scale(size, size, size);
                    mc.getItemRenderer().renderStatic(item.stack(), ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
                    pose.popPose();
                }
            }
        } finally {
            Lighting.setupFor3DItems();
        }
    }

    /**
     * Bollen van licht (een EMP, een flits, een ontploffing): in {@link #PULSE_GROW} ticks groot (eerst snel, dan trager),
     * daarna vagen ze weg. Licht dat opgeteld wordt, zoals de echte EMP (zie EmpEffects): je ziet erdoorheen.
     */
    void renderPulses(PoseStack pose, MultiBufferSource.BufferSource buffers, ScenePlayer player, float time) {
        VertexConsumer out = null;
        for (HowToCue cue : player.scene.cues()) {
            if (cue.tick() > time) {
                break;
            }
            if (cue instanceof HowToCue.Pulse pulse && time < pulse.tick() + pulse.duration()) {
                if (out == null) {
                    out = buffers.getBuffer(SuitEffects.GLOW);
                }
                float age = time - pulse.tick();
                float grow = Mth.clamp(age / PULSE_GROW, 0.0f, 1.0f);
                grow = 1 - (1 - grow) * (1 - grow);
                float radius = 0.2f + (pulse.radius() - 0.2f) * grow;
                float fade = 1 - Mth.clamp((age - PULSE_GROW) / Math.max(1.0f, pulse.duration() - PULSE_GROW), 0.0f, 1.0f);
                Matrix4f m = pose.last().pose();
                sphere(m, out, pulse.center(), radius, pulse.color(), 0.3f * fade);
                sphere(m, out, pulse.center(), radius * 0.96f, 0xF2FCFF, 0.12f * fade);
            }
        }
        if (out != null) {
            buffers.endBatch(SuitEffects.GLOW);
        }
    }

    private static final float PULSE_GROW = 6.0f;

    /**
     * De straalvlammen van het harnas (hoofdstuk 63, de rest van de onderwerpen): onder de laarzen van een speler met
     * {@code "Thrust"} in zijn gegevens (0 tot 1), dezelfde blauwe vlammen als in de wereld (zie SuitEffects). Ze staan met
     * hun brede kant naar de kijker: dat is in het wereldje de richting van het scherm naar jou toe.
     */
    void renderFlames(PoseStack pose, MultiBufferSource.BufferSource buffers, ScenePlayer player, float time) {
        Map<String, HowToCue.Actor> active = new java.util.LinkedHashMap<>();
        for (HowToCue cue : player.scene.cues()) {
            if (cue.tick() > time) {
                break;
            }
            if (cue instanceof HowToCue.Actor actor && time < actor.tick() + actor.duration()) {
                active.put(actor.key(), actor);
            }
        }
        Matrix4f m = pose.last().pose();
        org.joml.Vector3f toViewer = new Matrix4f(m).invert().transformDirection(new org.joml.Vector3f(0, 0, 1)).normalize();
        VertexConsumer out = null;
        for (HowToCue.Actor actor : active.values()) {
            float power = actor.data().getFloat("Thrust");
            if (actor.type() != null || power <= 0) {
                continue;
            }
            float progress = Mth.clamp((time - actor.tick()) / Math.max(1, actor.move()), 0.0f, 1.0f);
            Vec3 feet = ScenePlayer.along(actor.path(), progress);
            if (out == null) {
                out = buffers.getBuffer(SuitEffects.GLOW);
            }
            Vec3 cam = feet.add(toViewer.x() * 100.0, toViewer.y() * 100.0, toViewer.z() * 100.0);
            SuitEffects.sceneFlames(m, out, feet, actor.yaw(), power, time, cam);
        }
        if (out != null) {
            buffers.endBatch(SuitEffects.GLOW);
        }
    }

    /** Een bol van vierhoekjes (lengte- en breedtegraden), zoals de bol van een EMP. */
    private static void sphere(Matrix4f m, VertexConsumer out, Vec3 c, float r, int rgb, float alpha) {
        int rings = 12;
        int segments = 24;
        for (int i = 0; i < rings; i++) {
            double a0 = Math.PI * i / rings - Math.PI / 2;
            double a1 = Math.PI * (i + 1) / rings - Math.PI / 2;
            for (int j = 0; j < segments; j++) {
                double b0 = Math.PI * 2 * j / segments;
                double b1 = Math.PI * 2 * (j + 1) / segments;
                SuitEffects.quad(m, out, point(c, r, a0, b0), point(c, r, a0, b1), point(c, r, a1, b1), point(c, r, a1, b0),
                        rgb, alpha);
            }
        }
    }

    private static Vec3 point(Vec3 c, float r, double lat, double lon) {
        double cos = Math.cos(lat);
        return c.add(Math.cos(lon) * cos * r, Math.sin(lat) * r, Math.sin(lon) * cos * r);
    }

    /** Half doorzichtige rook die achter blokken verdwijnt (wel diepte testen, niet schrijven: de rook is geen muur). */
    private static final RenderType SMOKE = RenderType.create("imttech_howto_smoke", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 8192, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private static final float SMOKE_GROW = 30.0f;
    private static final float SMOKE_FADE = 40.0f;

    /**
     * Rookwolken: de stukjes lucht vullen zich, de dichtstbijzijnde eerst, en tegen het eind trekt de rook op. We tekenen
     * alleen de buitenkant van de wolk (een kant van een stukje rook waar geen rook naast zit), met elk stukje een tikje
     * anders van dikte: zo is het een wolk en geen raster van doosjes.
     */
    void renderSmoke(PoseStack pose, MultiBufferSource.BufferSource buffers, ScenePlayer player, float time) {
        boolean any = false;
        for (HowToCue cue : player.scene.cues()) {
            if (cue.tick() > time) {
                break;
            }
            if (!(cue instanceof HowToCue.Smoke smoke) || time >= smoke.tick() + smoke.duration() || smoke.cells().isEmpty()) {
                continue;
            }
            float age = time - smoke.tick();
            int shown = Math.max(1, Math.round(smoke.cells().size() * Mth.clamp(age / SMOKE_GROW, 0.0f, 1.0f)));
            float fade = Mth.clamp((smoke.tick() + smoke.duration() - time) / SMOKE_FADE, 0.0f, 1.0f);
            Set<BlockPos> cells = new HashSet<>(smoke.cells().subList(0, Math.min(shown, smoke.cells().size())));
            VertexConsumer out = buffers.getBuffer(SMOKE);
            Matrix4f m = pose.last().pose();
            for (BlockPos cell : cells) {
                int hash = cell.getX() * 7 + cell.getY() * 13 + cell.getZ() * 5;
                float density = 0.46f + 0.12f * ((hash & 3) / 3.0f);
                // Elk stukje een beetje anders van grootte: dan is de rand van de wolk hobbelig, geen strakke doos.
                float puff = 0.12f * (((hash >> 2) & 3) / 3.0f) - 0.04f;
                int alpha = Math.round(255 * density * fade);
                for (Direction side : Direction.values()) {
                    if (!cells.contains(cell.relative(side))) {
                        smokeFace(m, out, cell, side, smoke.color(), alpha, puff);
                    }
                }
            }
            any = true;
        }
        if (any) {
            buffers.endBatch(SMOKE);
        }
    }

    /**
     * Eén kant van een stukje rook: {@code puff} groter of kleiner dan het blok (de hobbels van de wolk), en altijd een heel
     * klein beetje binnen het blok als er een muur naast staat (dan flikkert hij niet).
     */
    private static void smokeFace(Matrix4f m, VertexConsumer out, BlockPos cell, Direction side, int rgb, int alpha, float puff) {
        float in = 0.02f - puff;
        float x0 = cell.getX() + in;
        float y0 = cell.getY() + in;
        float z0 = cell.getZ() + in;
        float x1 = cell.getX() + 1 - in;
        float y1 = cell.getY() + 1 - in;
        float z1 = cell.getZ() + 1 - in;
        // De bovenkant iets lichter, de onderkant iets donkerder: dan zie je de vorm van de wolk.
        float shade = side == Direction.UP ? 1.0f : side == Direction.DOWN ? 0.7f : side.getAxis() == Direction.Axis.X ? 0.85f : 0.92f;
        int r = Math.round((rgb >> 16 & 0xFF) * shade);
        int g = Math.round((rgb >> 8 & 0xFF) * shade);
        int b = Math.round((rgb & 0xFF) * shade);
        float[][] corners = switch (side) {
            case UP -> new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case DOWN -> new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
            case NORTH -> new float[][] {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}};
            case SOUTH -> new float[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            case WEST -> new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
            case EAST -> new float[][] {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}};
        };
        for (float[] c : corners) {
            out.addVertex(m, c[0], c[1], c[2]).setColor(r, g, b, alpha);
        }
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
