package com.senne.imttech.client.howto;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.senne.imttech.client.CableRenderer;
import com.senne.imttech.howto.HowToCue;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Tekent het wereldje van een "How it works"-animatie in een vak van het scherm (hoofdstuk 63): de blokken met de echte
 * modellen (zoals in de wereld, met schaduw in de hoekjes), de kabels en de randjes. Onthoudt hoe het wereldje op het
 * scherm staat, zodat de tekstballonnen weten waar ze naartoe moeten wijzen ({@link #project}) en je een blok kunt
 * aanwijzen ({@link #pick}).
 *
 * Het wereldje staat in de 3D-ruimte van het scherm: x naar rechts, y omhoog, z naar je toe. Eerst draaien we het (van
 * boven: {@code pitch}, om de verticale as: {@code yaw}), dan schalen we het zodat het in het vak past.
 */
final class SceneRenderer {
    private static final RandomSource RANDOM = RandomSource.create();
    /** Waar het wereldje in de diepte van het scherm staat: boven de achtergrond (0), onder niets (we wissen de diepte daarna). */
    private static final float DEPTH = 400.0f;

    /** Van het wereldje naar het scherm, zoals bij de laatste keer tekenen. */
    private final Matrix4f matrix = new Matrix4f();
    private final SceneLife life = new SceneLife();
    private boolean drawn;

    /**
     * Tekent het wereldje in het vak ({@code x}, {@code y}, {@code w}, {@code h}). {@code hovered}: het blok onder de muis
     * (krijgt een wit randje), of null.
     */
    void render(GuiGraphics g, ScenePlayer player, float partialTick, int x, int y, int w, int h, float yaw, float pitch,
                float zoom, @Nullable BlockPos hovered) {
        Minecraft mc = Minecraft.getInstance();
        HowToScene scene = player.scene;
        player.update();
        float time = player.time(partialTick);
        g.flush();
        PoseStack pose = g.pose();
        pose.pushPose();
        float scale = fit(scene, w, h) * zoom;
        pose.translate(x + w / 2.0f, y + h / 2.0f, DEPTH);
        // Een keer y omdraaien: op het scherm loopt y naar beneden, in het wereldje naar boven.
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-scene.sizeX() / 2.0f, -scene.height() / 2.0f, -scene.sizeZ() / 2.0f);
        matrix.set(pose.last().pose());
        drawn = true;

        g.enableScissor(x, y, x + w, y + h);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // Eerst alles wat niet doorzichtig is (blokken, het platform, de kabels), dan glas en dergelijke: anders verdwijnt
        // wat achter het glas zit.
        for (RenderType layer : RenderType.chunkBufferLayers()) {
            if (!translucent(layer)) {
                renderLayer(pose, buffers, player, time, layer);
            }
        }
        renderCracks(pose, buffers, player, time);
        renderPlate(pose, buffers, player);
        renderCables(pose, buffers, player, time);
        // Wezens, voorwerpen die zweven, de bewegende delen van blokken (het geschut van een torentje, de koepel) en schoten.
        life.renderActors(pose, buffers, player, time, partialTick);
        life.renderItems(pose, buffers, player, time);
        life.renderBlockEntities(pose, buffers, player, time, partialTick);
        life.renderBeams(pose, buffers, player, time);
        buffers.endBatch();
        // Rook en bollen van licht vóór glas: glas schrijft diepte, en dan verdween de rook achter een glazen dak. Zo komt
        // het glas er gewoon overheen (rook en licht zelf schrijven geen diepte).
        life.renderSmoke(pose, buffers, player, time);
        life.renderPulses(pose, buffers, player, time);
        life.renderFlames(pose, buffers, player, time);
        for (RenderType layer : RenderType.chunkBufferLayers()) {
            if (translucent(layer)) {
                renderLayer(pose, buffers, player, time, layer);
            }
        }
        renderOutlines(pose, buffers, player, time, hovered);
        buffers.endBatch();
        g.disableScissor();
        pose.popPose();
        // Alles hierna (tekstballonnen, knoppen, tooltips) komt over het wereldje heen.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /** Hoeveel pixels per blok, zodat het hele wereldje (zoals de camera begint) in het vak past. */
    static float fit(HowToScene scene, int w, int h) {
        Matrix4f rotation = new Matrix4f().rotateX((float) Math.toRadians(scene.pitch())).rotateY((float) Math.toRadians(scene.yaw()));
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            Vector4f v = new Vector4f(
                    ((corner & 1) == 0 ? -0.5f : 0.5f) * scene.sizeX(),
                    ((corner & 2) == 0 ? -0.5f : 0.5f) * scene.height(),
                    ((corner & 4) == 0 ? -0.5f : 0.5f) * scene.sizeZ(), 1.0f);
            rotation.transform(v);
            minX = Math.min(minX, v.x());
            maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y());
            maxY = Math.max(maxY, v.y());
        }
        return Math.min(w * 0.92f / (maxX - minX), h * 0.9f / (maxY - minY));
    }

    // ================================================================ Blokken

    /**
     * Barsten (hoofdstuk 63, de wapens): over een blok de laag die Minecraft tekent als je eraan hakt, zoals de barsten van
     * een frag in de echte wereld (zie breach/Cracks). De laatste Crack-cue per blok tot nu toe telt.
     */
    private static void renderCracks(PoseStack pose, MultiBufferSource.BufferSource buffers, ScenePlayer player, float time) {
        Map<BlockPos, Integer> stages = new HashMap<>();
        for (HowToCue cue : player.scene.cues()) {
            if (cue.tick() > time) {
                break;
            }
            if (cue instanceof HowToCue.Crack crack) {
                if (crack.stage() < 0) {
                    stages.remove(crack.pos());
                } else {
                    stages.put(crack.pos(), Math.min(9, crack.stage()));
                }
            }
        }
        if (stages.isEmpty()) {
            return;
        }
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (Map.Entry<BlockPos, Integer> entry : stages.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = player.world.blocks().get(pos);
            if (state == null || state.getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            pose.pushPose();
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            VertexConsumer cracks = new SheetedDecalTextureGenerator(
                    buffers.getBuffer(ModelBakery.DESTROY_TYPES.get(entry.getValue())), pose.last(), 1.0f);
            dispatcher.renderBreakingTexture(state, pos, player.world, pose, cracks, ModelData.EMPTY);
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static boolean translucent(RenderType layer) {
        return layer == RenderType.translucent() || layer == RenderType.tripwire();
    }

    /** Alle blokken van één laag (vast, uitgeknipt, doorzichtig...), zodat alles van één soort in één keer getekend wordt. */
    private static void renderLayer(PoseStack pose, MultiBufferSource.BufferSource buffers, ScenePlayer player, float time,
                                    RenderType layer) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        boolean any = false;
        for (Map.Entry<BlockPos, BlockState> entry : player.world.blocks().entrySet()) {
            any |= block(dispatcher, pose, buffers, player.world, entry.getKey(), entry.getValue(), layer, 0.0f, true);
        }
        // Vallende blokken staan nog niet in het wereldje: alle kanten tekenen (ze raken hun buren nog niet).
        for (ScenePlayer.Falling falling : player.falling) {
            any |= block(dispatcher, pose, buffers, player.world, falling.pos(), falling.state(), layer, falling.offset(time), false);
        }
        if (any) {
            buffers.endBatch(layer);
        }
    }

    private static boolean block(BlockRenderDispatcher dispatcher, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                 SceneWorld world, BlockPos pos, BlockState state, RenderType layer, float lift, boolean cull) {
        // De grondplaat tekenen we zelf, als het platform van I.M.T (zie renderPlate).
        if (state.getRenderShape() != RenderShape.MODEL || isPlate(pos, state)) {
            return false;
        }
        BakedModel model = dispatcher.getBlockModel(state);
        ModelData data = model.getModelData(world, pos, state, ModelData.EMPTY);
        RANDOM.setSeed(state.getSeed(pos));
        if (!model.getRenderTypes(state, RANDOM, data).contains(layer)) {
            return false;
        }
        pose.pushPose();
        pose.translate(pos.getX(), pos.getY() + lift, pos.getZ());
        dispatcher.renderBatched(state, pos, world, pose, buffers.getBuffer(layer), cull, RANDOM, data, layer);
        pose.popPose();
        return true;
    }

    // ================================================================ Het platform

    /*
     * Het platform onder elke animatie (jouw opmerking van 8 november: "het lijkt net iets te hard op Create", dus geen
     * dambord): een dunne, donkere plaat in de kleuren van de HUD, met een fijn cyaan raster op de naden (zo tel je nog
     * steeds de blokken) en een oplichtende cyaan rand, alsof het een hologram is dat in het donker zweeft.
     */
    private static final int FLOOR = 0x26333E;
    private static final int FLOOR_SIDE = 0x141C24;
    private static final int FLOOR_BOTTOM = 0x0B1116;
    private static final int GRID = 0x325463;
    private static final int RIM = 0x3FD8FF;
    private static final int RIM_LOW = 0x1D6F86;
    /** De plaat is dun: van y 0,8 tot 1 (het blok eronder is er wel, maar dat zie je niet). */
    private static final double PLATE_BOTTOM = 0.8;

    private static boolean isPlate(BlockPos pos, BlockState state) {
        return pos.getY() == 0 && state == HowToSceneBuilder.PLATE;
    }

    private static boolean isPlate(SceneWorld world, int x, int z) {
        BlockPos pos = new BlockPos(x, 0, z);
        return isPlate(pos, world.getBlockState(pos));
    }

    private static void renderPlate(PoseStack pose, MultiBufferSource buffers, ScenePlayer player) {
        Matrix4f m = pose.last().pose();
        int light = LightTexture.FULL_BRIGHT;
        SceneWorld world = player.world;
        for (Map.Entry<BlockPos, BlockState> entry : world.blocks().entrySet()) {
            BlockPos pos = entry.getKey();
            if (!isPlate(pos, entry.getValue())) {
                continue;
            }
            int x = pos.getX();
            int z = pos.getZ();
            CableRenderer.drawBox(m, buffers, new Vec3(x, PLATE_BOTTOM, z), new Vec3(x + 1, 1.0, z + 1), FLOOR, FLOOR_SIDE,
                    FLOOR_BOTTOM, light);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                boolean neighbour = isPlate(world, x + side.getStepX(), z + side.getStepZ());
                // Een naad tussen twee tegels tekenen we één keer (vanaf de tegel ten noorden of ten westen ervan).
                if (neighbour && (side == Direction.NORTH || side == Direction.WEST)) {
                    continue;
                }
                Vec3[] edge = edge(x, z, side);
                if (neighbour) {
                    CableRenderer.drawBar(m, buffers, edge[0].add(0, 1.0, 0), edge[1].add(0, 1.0, 0), 0.008, GRID, light);
                } else {
                    // De rand: fel bovenaan, zwak onderaan.
                    CableRenderer.drawBar(m, buffers, edge[0].add(0, 1.0, 0), edge[1].add(0, 1.0, 0), 0.032, RIM, light);
                    CableRenderer.drawBar(m, buffers, edge[0].add(0, PLATE_BOTTOM, 0), edge[1].add(0, PLATE_BOTTOM, 0), 0.014,
                            RIM_LOW, light);
                }
            }
        }
    }

    /** De twee hoeken (op hoogte 0) van de kant {@code side} van tegel ({@code x}, {@code z}). */
    private static Vec3[] edge(int x, int z, Direction side) {
        return switch (side) {
            case NORTH -> new Vec3[] {new Vec3(x, 0, z), new Vec3(x + 1, 0, z)};
            case SOUTH -> new Vec3[] {new Vec3(x, 0, z + 1), new Vec3(x + 1, 0, z + 1)};
            case WEST -> new Vec3[] {new Vec3(x, 0, z), new Vec3(x, 0, z + 1)};
            default -> new Vec3[] {new Vec3(x + 1, 0, z), new Vec3(x + 1, 0, z + 1)};
        };
    }

    // ================================================================ Kabels en randjes

    private static void renderCables(PoseStack pose, MultiBufferSource buffers, ScenePlayer player, float time) {
        for (HowToCue cue : player.scene.cues()) {
            if (cue.tick() > time) {
                break;
            }
            if (cue instanceof HowToCue.Cable cable) {
                float grown = cable.grow() <= 0 ? 1.0f : Math.min(1.0f, (time - cable.tick()) / cable.grow());
                List<Vec3> points = ScenePlayer.partial(cable.points(), grown);
                CableRenderer.drawPath(pose.last().pose(), buffers, cable.kind(), points, LightTexture.FULL_BRIGHT);
            } else if (cue instanceof HowToCue.Signal signal) {
                // Een signaal: een helder blokje dat over de kabel loopt (net iets dikker dan de kabel).
                float progress = ScenePlayer.progress(time, signal.tick(), signal.duration());
                if (progress >= 0.0f) {
                    Vec3 at = ScenePlayer.along(signal.path(), progress);
                    CableRenderer.drawBar(pose.last().pose(), buffers, at, at, 0.075, signal.color(), LightTexture.FULL_BRIGHT);
                }
            }
        }
    }

    private static void renderOutlines(PoseStack pose, MultiBufferSource buffers, ScenePlayer player, float time,
                                       @Nullable BlockPos hovered) {
        for (HowToCue cue : player.scene.cues()) {
            if (cue.tick() > time) {
                break;
            }
            if (cue instanceof HowToCue.Outline outline && ScenePlayer.fade(time, outline.tick(), outline.duration()) > 0.0f) {
                // Een beetje kloppen, zodat je het ziet.
                double pulse = 0.022 + 0.008 * Math.sin((time - outline.tick()) * 0.35);
                edges(pose, buffers, outline.box().inflate(0.02), pulse, outline.color());
            }
        }
        if (hovered != null) {
            edges(pose, buffers, new AABB(hovered).inflate(0.015), 0.012, 0xFFFFFF);
        }
    }

    /** De twaalf ribben van een doos als dunne staafjes. */
    private static void edges(PoseStack pose, MultiBufferSource buffers, AABB box, double half, int rgb) {
        Matrix4f m = pose.last().pose();
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        int light = LightTexture.FULL_BRIGHT;
        for (double y : ys) {
            for (double z : zs) {
                CableRenderer.drawBar(m, buffers, new Vec3(box.minX, y, z), new Vec3(box.maxX, y, z), half, rgb, light);
            }
            for (double x : xs) {
                CableRenderer.drawBar(m, buffers, new Vec3(x, y, box.minZ), new Vec3(x, y, box.maxZ), half, rgb, light);
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                CableRenderer.drawBar(m, buffers, new Vec3(x, box.minY, z), new Vec3(x, box.maxY, z), half, rgb, light);
            }
        }
    }

    // ================================================================ Van het wereldje naar het scherm en terug

    /** Waar een punt van het wereldje op het scherm staat (x, y), zoals bij de laatste keer tekenen. */
    float[] project(Vec3 point) {
        Vector4f v = new Vector4f((float) point.x, (float) point.y, (float) point.z, 1.0f);
        matrix.transform(v);
        return new float[] {v.x(), v.y()};
    }

    /** Het blok onder de muis (niet de grondplaat), of null. */
    @Nullable BlockPos pick(ScenePlayer player, double mouseX, double mouseY) {
        if (!drawn) {
            return null;
        }
        Matrix4f inverse = new Matrix4f(matrix).invert();
        Vector4f centre = new Vector4f(player.scene.sizeX() / 2.0f, player.scene.height() / 2.0f, player.scene.sizeZ() / 2.0f, 1.0f);
        matrix.transform(centre);
        Vector4f near = new Vector4f((float) mouseX, (float) mouseY, centre.z() + 3000.0f, 1.0f);
        Vector4f far = new Vector4f((float) mouseX, (float) mouseY, centre.z() - 3000.0f, 1.0f);
        inverse.transform(near);
        inverse.transform(far);
        Vec3 from = new Vec3(near.x(), near.y(), near.z());
        Vec3 to = new Vec3(far.x(), far.y(), far.z());
        BlockHitResult hit = player.world.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getBlockPos().getY() <= 0) {
            return null;
        }
        return hit.getBlockPos();
    }
}
