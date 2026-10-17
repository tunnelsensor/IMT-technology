package com.senne.imttech.client.os.camera;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

/**
 * Tekent de wereld zoals een bewakingscamera hem ziet, in een klein plaatje (een TextureTarget).
 *
 * Minecraft tekent de wereld in "secties" (stukjes van 16x16x16 blokken) die je spel al heeft
 * klaargezet. Wij tekenen hier dezelfde secties nog een keer, maar vanuit de camera:
 * <ol>
 *   <li>de secties binnen {@code distance} blokken van de lens die in beeld vallen. Met een EIGEN
 *       lijst: Minecraft tekent alleen wat jij kunt zien, maar een camera op een andere verdieping
 *       ziet zijn eigen kamer (secties die nog nooit gebouwd zijn, laten we hier bouwen);</li>
 *   <li>de blokken (vast, uitgesneden zoals bladeren, en als laatste doorzichtig zoals glas);</li>
 *   <li>de wezens en spelers (ook jij: de camera ziet je), en blokken met een eigen model, zoals
 *       kisten en de koppen van torentjes.</li>
 * </ol>
 * Geen lucht, wolken, regen of deeltjes: in de verte vervaagt alles naar de kleur van de lucht.
 *
 * Alles wat we aan de instellingen van het tekenen veranderen, zetten we daarna terug, zodat het
 * gewone beeld er niets van merkt (zie {@link #render}).
 */
final class FeedRenderer {
    private FeedRenderer() {}

    /** Zoveel secties laten we hooguit per beeld nieuw bouwen (het bouwen zelf gebeurt naast het spel). */
    private static final int MAX_COMPILES = 6;

    /** Een camera die we zelf neerzetten (de gewone camera van Minecraft volgt altijd een wezen). */
    private static final class FeedCamera extends Camera {
        void place(Vec3 eye, float yaw, float pitch) {
            setRotation(yaw, pitch);
            setPosition(eye);
        }
    }

    private static final FeedCamera CAMERA = new FeedCamera();

    /**
     * Tekent één beeld. {@code eye} is de lens, {@code yaw}/{@code pitch} de richting (zoals bij een
     * speler), {@code distance} hoe ver de camera kijkt. {@code skip} is het nummer van een wezen dat we niet
     * tekenen (de robot waar de camera op zit), of -1.
     */
    static void render(TextureTarget target, Vec3 eye, float yaw, float pitch, float partialTick, int distance, int skip) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LevelRenderer levelRenderer = mc.levelRenderer;
        ViewArea viewArea = levelRenderer.viewArea; // via de access transformer (META-INF/accesstransformer.cfg)
        if (level == null || viewArea == null) {
            return;
        }
        CAMERA.place(eye, yaw, pitch);
        float aspect = (float) target.width / target.height;
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70.0), aspect, 0.05f, distance + 16f);
        Matrix4f view = new Matrix4f().rotation(CAMERA.rotation().conjugate(new Quaternionf()));
        Frustum frustum = new Frustum(view, projection);
        frustum.prepare(eye.x, eye.y, eye.z);

        // De achtergrond en de mist: de kleur van de lucht.
        Vec3 sky = level.getSkyColor(eye, partialTick);
        float red = (float) sky.x;
        float green = (float) sky.y;
        float blue = (float) sky.z;
        target.setClearColor(red, green, blue, 1.0f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        modelView.mul(view);
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setShaderFogStart(distance * 0.6f);
            RenderSystem.setShaderFogEnd(distance);
            RenderSystem.setShaderFogColor(red, green, blue);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);

            List<SectionRenderDispatcher.RenderSection> sections = visibleSections(level, viewArea, eye, frustum, distance);
            buildMissing(level, levelRenderer.getSectionRenderDispatcher(), sections);

            // De blokken: eerst vast, dan uitgesneden (bladeren, gras), zoals Minecraft zelf.
            renderLayer(RenderType.solid(), sections, false, eye, view, projection);
            var atlas = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
            atlas.setBlurMipmap(false, mc.options.mipmapLevels().get() > 0);
            renderLayer(RenderType.cutoutMipped(), sections, false, eye, view, projection);
            atlas.restoreLastBlurMipmap();
            renderLayer(RenderType.cutout(), sections, false, eye, view, projection);

            // Wezens en blokken met een eigen model.
            if (level.effects().constantAmbientLight()) {
                Lighting.setupNetherLevel();
            } else {
                Lighting.setupLevel();
            }
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            PoseStack pose = new PoseStack();
            renderEntities(mc, level, eye, frustum, distance, partialTick, pose, buffers, skip);
            renderBlockEntities(mc, level, sections, eye, frustum, partialTick, pose, buffers);
            buffers.endLastBatch();
            buffers.endBatch();
            // Met "Fabulous!"-graphics schakelt doorzichtig spul naar een ander tekenvel: terug naar het onze.
            target.bindWrite(true);

            // Als laatste het doorzichtige (water, glas), van ver naar dichtbij. Met "Fabulous!" niet:
            // dat tekent Minecraft op een apart vel dat alleen bij het gewone beeld hoort.
            if (!Minecraft.useShaderTransparency()) {
                renderLayer(RenderType.translucent(), sections, true, eye, view, projection);
            }
        } finally {
            // Alles terug zoals het was, voor het gewone beeld.
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            mc.getMainRenderTarget().bindWrite(true);
            FogRenderer.setupNoFog();
            Camera main = mc.gameRenderer.getMainCamera();
            mc.getEntityRenderDispatcher().prepare(level, main, mc.crosshairPickEntity);
            mc.getBlockEntityRenderDispatcher().prepare(level, main, mc.hitResult);
        }
    }

    // ================================================================ Welke secties

    /**
     * De secties binnen {@code distance} blokken van de lens die in beeld vallen, de dichtstbijzijnde
     * eerst. We zoeken ze op in de "view area" van je spel, net zoals Minecraft zelf (zie
     * ViewArea.getRenderSectionAt): die is een raster dat met je meeschuift.
     */
    private static List<SectionRenderDispatcher.RenderSection> visibleSections(ClientLevel level, ViewArea viewArea,
                                                                                Vec3 eye, Frustum frustum, int distance) {
        SectionRenderDispatcher.RenderSection[] all = viewArea.sections;
        int sizeX = viewArea.getViewDistance() * 2 + 1;
        int sizeY = level.getSectionsCount();
        int radius = (distance >> 4) + 1;
        int cx = SectionPos.posToSectionCoord(eye.x);
        int cy = SectionPos.posToSectionCoord(eye.y);
        int cz = SectionPos.posToSectionCoord(eye.z);
        double reach = (distance + 14.0) * (distance + 14.0);
        List<SectionRenderDispatcher.RenderSection> found = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int sx = cx + dx;
                    int sy = cy + dy;
                    int sz = cz + dz;
                    int iy = sy - level.getMinSection();
                    if (iy < 0 || iy >= sizeY) {
                        continue;
                    }
                    int index = (Math.floorMod(sz, sizeX) * sizeY + iy) * sizeX + Math.floorMod(sx, sizeX);
                    if (index < 0 || index >= all.length) {
                        continue;
                    }
                    SectionRenderDispatcher.RenderSection section = all[index];
                    BlockPos origin = section.getOrigin();
                    // Klopt de plek niet, dan ligt dit stuk buiten wat je spel klaar heeft staan.
                    if (origin.getX() != sx << 4 || origin.getY() != sy << 4 || origin.getZ() != sz << 4) {
                        continue;
                    }
                    double ox = origin.getX() + 8 - eye.x;
                    double oy = origin.getY() + 8 - eye.y;
                    double oz = origin.getZ() + 8 - eye.z;
                    if (ox * ox + oy * oy + oz * oz > reach || !frustum.isVisible(section.getBoundingBox())) {
                        continue;
                    }
                    found.add(section);
                }
            }
        }
        found.sort(Comparator.comparingDouble(section -> {
            BlockPos origin = section.getOrigin();
            double ox = origin.getX() + 8 - eye.x;
            double oy = origin.getY() + 8 - eye.y;
            double oz = origin.getZ() + 8 - eye.z;
            return ox * ox + oy * oy + oz * oz;
        }));
        return found;
    }

    /**
     * Secties die nog nooit gebouwd zijn (of veranderd zijn), laten we bouwen, net zoals Minecraft
     * dat doet voor wat jij ziet. Het bouwen gebeurt naast het spel; een volgend beeld heeft ze dan.
     */
    private static void buildMissing(ClientLevel level, SectionRenderDispatcher dispatcher,
                                     List<SectionRenderDispatcher.RenderSection> sections) {
        RenderRegionCache cache = null;
        int scheduled = 0;
        for (SectionRenderDispatcher.RenderSection section : sections) {
            if (!section.isDirty() || !section.hasAllNeighbors()
                    || !level.getLightEngine().lightOnInSection(SectionPos.of(section.getOrigin()))) {
                continue;
            }
            if (cache == null) {
                cache = new RenderRegionCache();
            }
            section.rebuildSectionAsync(dispatcher, cache);
            section.setNotDirty();
            if (++scheduled >= MAX_COMPILES) {
                return;
            }
        }
    }

    // ================================================================ Tekenen

    /** Eén laag blokken, zoals LevelRenderer.renderSectionLayer: elke sectie heeft er een eigen buffer voor. */
    private static void renderLayer(RenderType type, List<SectionRenderDispatcher.RenderSection> sections, boolean farFirst,
                                    Vec3 eye, Matrix4f view, Matrix4f projection) {
        type.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            type.clearRenderState();
            return;
        }
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, projection, Minecraft.getInstance().getWindow());
        shader.apply();
        Uniform offset = shader.CHUNK_OFFSET;
        for (int i = 0; i < sections.size(); i++) {
            SectionRenderDispatcher.RenderSection section = sections.get(farFirst ? sections.size() - 1 - i : i);
            if (section.getCompiled().isEmpty(type)) {
                continue;
            }
            VertexBuffer buffer = section.getBuffer(type);
            BlockPos origin = section.getOrigin();
            if (offset != null) {
                offset.set((float) (origin.getX() - eye.x), (float) (origin.getY() - eye.y), (float) (origin.getZ() - eye.z));
                offset.upload();
            }
            buffer.bind();
            buffer.draw();
        }
        if (offset != null) {
            offset.set(0.0f, 0.0f, 0.0f);
        }
        shader.clear();
        VertexBuffer.unbind();
        type.clearRenderState();
    }

    /** De wezens binnen bereik die in beeld vallen (ook jijzelf: de camera ziet je), behalve {@code skip}. */
    private static void renderEntities(Minecraft mc, ClientLevel level, Vec3 eye, Frustum frustum, int distance,
                                       float partialTick, PoseStack pose, MultiBufferSource buffers, int skip) {
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.prepare(level, CAMERA, null);
        double reach = (double) distance * distance;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.getId() == skip || entity.distanceToSqr(eye) > reach || entity == mc.player && mc.player.isSpectator()
                    || !dispatcher.shouldRender(entity, frustum, eye.x, eye.y, eye.z)) {
                continue;
            }
            double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
            double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
            double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
            float yRot = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            dispatcher.render(entity, x - eye.x, y - eye.y, z - eye.z, yRot, partialTick, pose, buffers,
                    dispatcher.getPackedLightCoords(entity, partialTick));
        }
    }

    /** Blokken met een eigen model (kisten, borden, de koppen van torentjes) in de secties in beeld. */
    private static void renderBlockEntities(Minecraft mc, ClientLevel level, List<SectionRenderDispatcher.RenderSection> sections,
                                            Vec3 eye, Frustum frustum, float partialTick, PoseStack pose,
                                            MultiBufferSource buffers) {
        BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();
        dispatcher.prepare(level, CAMERA, mc.hitResult);
        for (SectionRenderDispatcher.RenderSection section : sections) {
            for (BlockEntity blockEntity : section.getCompiled().getRenderableBlockEntities()) {
                if (!net.neoforged.neoforge.client.ClientHooks.isBlockEntityRendererVisible(dispatcher, blockEntity, frustum)) {
                    continue;
                }
                BlockPos pos = blockEntity.getBlockPos();
                pose.pushPose();
                pose.translate(pos.getX() - eye.x, pos.getY() - eye.y, pos.getZ() - eye.z);
                dispatcher.render(blockEntity, partialTick, pose, buffers);
                pose.popPose();
            }
        }
    }

    /**
     * Kan je spel dit stuk wereld nu tekenen? (De plek ligt in wat je spel klaar heeft staan.) Zo
     * niet, dan is het beeld "out of range" (camera's van ver: hoofdstuk 7, stap 10).
     */
    static boolean inRange(Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        @Nullable ViewArea viewArea = mc.levelRenderer.viewArea;
        if (level == null || viewArea == null || !level.isLoaded(BlockPos.containing(eye))) {
            return false;
        }
        int sizeX = viewArea.getViewDistance() * 2 + 1;
        int sizeY = level.getSectionsCount();
        int sx = SectionPos.posToSectionCoord(eye.x);
        int sy = SectionPos.posToSectionCoord(eye.y);
        int sz = SectionPos.posToSectionCoord(eye.z);
        int iy = sy - level.getMinSection();
        if (iy < 0 || iy >= sizeY) {
            return false;
        }
        int index = (Math.floorMod(sz, sizeX) * sizeY + iy) * sizeX + Math.floorMod(sx, sizeX);
        if (index < 0 || index >= viewArea.sections.length) {
            return false;
        }
        BlockPos origin = viewArea.sections[index].getOrigin();
        return origin.getX() == sx << 4 && origin.getY() == sy << 4 && origin.getZ() == sz << 4;
    }
}
