package com.senne.imttech.client.os.camera;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
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
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

/**
 * Tekent de wereld zoals een bewakingscamera hem ziet, in een klein plaatje (een TextureTarget).
 *
 * Sinds hoofdstuk 94 tekenen we uit onze eigen kopie van de wereld rond de camera's: de stukjes die de server stuurt
 * (FeedWorld), waarvan FeedMesher de modellen bouwt. Vroeger tekenden we de stukken wereld die je spel al voor jou had
 * klaargezet (de "view area" van Minecraft); daardoor gaf een camera verder weg dan je kijkafstand "OUT OF RANGE", en met
 * Sodium (dat het tekenen van de wereld overneemt) elke camera. Nu:
 * <ol>
 *   <li>de stukjes wereld binnen {@code distance} blokken van de lens die in beeld vallen, het dichtstbijzijnde eerst (wat
 *       nog geen model heeft, bouwt FeedMesher; een nieuw beeld vult zich zo in een paar frames);</li>
 *   <li>de blokken (vast, uitgesneden zoals bladeren, en als laatste doorzichtig zoals glas);</li>
 *   <li>de wezens en spelers (ook jij: de camera ziet je): die je spel zelf kent, en anders hun "pop" (FeedPuppets), en
 *       blokken met een eigen model, zoals kisten en de koppen van torentjes.</li>
 * </ol>
 * Geen lucht, wolken, regen of deeltjes: in de verte vervaagt alles naar de kleur van de lucht. (De rook van een
 * smokebom tekenen we wel, als dichte blokjes waar de rook hangt: zie client/weapons/SmokeView.)
 *
 * Alles wat we aan de instellingen van het tekenen veranderen, zetten we daarna terug, zodat het
 * gewone beeld er niets van merkt (zie {@link #render}).
 */
final class FeedRenderer {
    private FeedRenderer() {}

    /** Een camera die we zelf neerzetten (de gewone camera van Minecraft volgt altijd een wezen). */
    private static final class FeedCamera extends Camera {
        void place(Vec3 eye, float yaw, float pitch) {
            setRotation(yaw, pitch);
            setPosition(eye);
        }
    }

    private static final FeedCamera CAMERA = new FeedCamera();

    /**
     * Kan dit beeld al getekend worden? Pas als je spel het stukje wereld van de lens zelf heeft (van de server); daarvoor
     * staat er "CONNECTING..." op het scherm.
     */
    static boolean ready(Vec3 eye) {
        return FeedWorld.has(SectionPos.asLong(BlockPos.containing(eye)));
    }

    /**
     * Bouwt alvast de modellen voor dit beeld (zoveel als er dit frame nog tijd is), ook als het nu niet getekend wordt: zo
     * vult een nieuw beeld zich sneller.
     */
    static void prepare(Vec3 eye, float yaw, float pitch, int distance, float aspect) {
        Frustum frustum = frustum(eye, yaw, pitch, distance, aspect);
        FeedMesher.build(visibleSections(eye, frustum, distance), eye);
    }

    /** Wat de camera ziet: een piramide vanaf de lens (70 graden hoog, zo breed als het beeld), tot net voorbij de mist. */
    private static Frustum frustum(Vec3 eye, float yaw, float pitch, int distance, float aspect) {
        CAMERA.place(eye, yaw, pitch);
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70.0), aspect, 0.05f, distance + 16f);
        Matrix4f view = new Matrix4f().rotation(CAMERA.rotation().conjugate(new Quaternionf()));
        Frustum frustum = new Frustum(view, projection);
        frustum.prepare(eye.x, eye.y, eye.z);
        return frustum;
    }

    /**
     * Tekent één beeld. {@code eye} is de lens, {@code yaw}/{@code pitch} de richting (zoals bij een
     * speler), {@code distance} hoe ver de camera kijkt. {@code skip} is het nummer van een wezen dat we niet
     * tekenen (de robot waar de camera op zit), of -1.
     */
    static void render(TextureTarget target, Vec3 eye, float yaw, float pitch, float partialTick, int distance, int skip) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
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

            List<Long> sections = visibleSections(eye, frustum, distance);
            FeedMesher.build(sections, eye);

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
            // De rook van een smokebom (hoofdstuk 35): deeltjes tekenen we hier niet, dus dichte blokjes.
            com.senne.imttech.client.weapons.SmokeView.renderInFeed(eye, pose, buffers);
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

    // ================================================================ Welke stukjes

    /**
     * De stukjes wereld binnen {@code distance} blokken van de lens (hun midden binnen {@code distance} + 14: dan valt geen
     * hoek weg) die in beeld vallen, het dichtstbijzijnde eerst. Alleen wat je spel van de server heeft (FeedWorld).
     */
    private static List<Long> visibleSections(Vec3 eye, Frustum frustum, int distance) {
        List<Long> found = new ArrayList<>();
        FeedWorld.around(eye, distance + 14.0, found);
        found.removeIf(key -> !frustum.isVisible(box(key)));
        found.sort(Comparator.comparingDouble(key -> {
            double ox = (SectionPos.x(key) << 4) + 8 - eye.x;
            double oy = (SectionPos.y(key) << 4) + 8 - eye.y;
            double oz = (SectionPos.z(key) << 4) + 8 - eye.z;
            return ox * ox + oy * oy + oz * oz;
        }));
        return found;
    }

    /** Het kader van één stukje wereld. */
    private static AABB box(long key) {
        double x = SectionPos.x(key) << 4;
        double y = SectionPos.y(key) << 4;
        double z = SectionPos.z(key) << 4;
        return new AABB(x, y, z, x + 16, y + 16, z + 16);
    }

    // ================================================================ Tekenen

    /** Eén laag blokken, zoals LevelRenderer.renderSectionLayer: elk stukje heeft er een eigen buffer voor (FeedMesher). */
    private static void renderLayer(RenderType type, List<Long> sections, boolean farFirst, Vec3 eye, Matrix4f view,
                                    Matrix4f projection) {
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
            long key = sections.get(farFirst ? sections.size() - 1 - i : i);
            FeedMesher.Mesh mesh = FeedMesher.mesh(key);
            VertexBuffer buffer = mesh == null ? null : mesh.buffers.get(type);
            if (buffer == null) {
                continue;
            }
            if (offset != null) {
                offset.set((float) ((SectionPos.x(key) << 4) - eye.x), (float) ((SectionPos.y(key) << 4) - eye.y),
                        (float) ((SectionPos.z(key) << 4) - eye.z));
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

    /**
     * De wezens binnen bereik die in beeld vallen (ook jijzelf: de camera ziet je), behalve {@code skip}: eerst die je spel
     * zelf kent, dan de poppen van wie te ver weg is om zelf te kennen (FeedPuppets), met het licht uit FeedWorld.
     */
    private static void renderEntities(Minecraft mc, ClientLevel level, Vec3 eye, Frustum frustum, int distance,
                                       float partialTick, PoseStack pose, MultiBufferSource buffers, int skip) {
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.prepare(level, CAMERA, null);
        double reach = (double) distance * distance;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.getId() == skip || entity.distanceToSqr(eye) > reach || entity == mc.player && mc.player.isSpectator()
                    || !inView(entity, frustum, eye)) {
                continue;
            }
            renderEntity(dispatcher, entity, eye, partialTick, pose, buffers, dispatcher.getPackedLightCoords(entity, partialTick));
        }
        for (Entity puppet : FeedPuppets.near(eye, distance)) {
            if (puppet.getId() == skip || level.getEntity(puppet.getId()) != null || !inView(puppet, frustum, eye)) {
                continue;
            }
            int light = FeedWorld.packedLight(BlockPos.containing(puppet.getLightProbePosition(partialTick)));
            renderEntity(dispatcher, puppet, eye, partialTick, pose, buffers, light);
        }
    }

    /**
     * Valt dit wezen in het beeld van de camera? Zoals Minecraft zelf (EntityRenderer.shouldRender), maar zonder te vragen
     * wat jij ziet: Sodium slaat daar wezens over in stukken wereld die het voor jou niet tekent (achter je, of ver weg),
     * en die ziet de camera juist wel.
     */
    private static boolean inView(Entity entity, Frustum frustum, Vec3 eye) {
        if (!entity.shouldRender(eye.x, eye.y, eye.z)) {
            return false;
        }
        if (entity.noCulling) {
            return true;
        }
        AABB box = entity.getBoundingBoxForCulling().inflate(0.5);
        if (box.hasNaN() || box.getSize() == 0.0) {
            box = new AABB(entity.getX() - 2.0, entity.getY() - 2.0, entity.getZ() - 2.0,
                    entity.getX() + 2.0, entity.getY() + 2.0, entity.getZ() + 2.0);
        }
        return frustum.isVisible(box);
    }

    private static void renderEntity(EntityRenderDispatcher dispatcher, Entity entity, Vec3 eye, float partialTick, PoseStack pose,
                                     MultiBufferSource buffers, int light) {
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        float yRot = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        try {
            dispatcher.render(entity, x - eye.x, y - eye.y, z - eye.z, yRot, partialTick, pose, buffers, light);
        } catch (RuntimeException e) {
            // een wezen (van een andere mod) dat zich zo niet laat tekenen: dat ene slaan we over
        }
    }

    /**
     * Blokken met een eigen model (kisten, de koppen van torentjes) in de stukjes in beeld. Kent je spel dat blok zelf
     * (het staat bij jou in de buurt), dan tekenen we dat (het beweegt: een kist die opengaat); anders onze kopie. Een blok
     * dat zich ook buiten beeld tekent (een schermmuur) slaan we over: zo tekent een beeld nooit zichzelf.
     */
    private static void renderBlockEntities(Minecraft mc, ClientLevel level, List<Long> sections, Vec3 eye, Frustum frustum,
                                            float partialTick, PoseStack pose, MultiBufferSource buffers) {
        BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();
        dispatcher.prepare(level, CAMERA, mc.hitResult);
        for (long key : sections) {
            for (BlockEntity copy : FeedWorld.blockEntities(key).values()) {
                BlockPos pos = copy.getBlockPos();
                BlockEntity real = level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
                BlockEntity entity = real != null && real.getType() == copy.getType() ? real : copy;
                BlockEntityRenderer<BlockEntity> renderer = dispatcher.getRenderer(entity);
                if (renderer == null || renderer.shouldRenderOffScreen(entity) || !renderer.shouldRender(entity, eye)
                        || !net.neoforged.neoforge.client.ClientHooks.isBlockEntityRendererVisible(dispatcher, entity, frustum)) {
                    continue;
                }
                int light = entity == real ? LevelRenderer.getLightColor(level, pos) : FeedWorld.packedLight(pos);
                pose.pushPose();
                pose.translate(pos.getX() - eye.x, pos.getY() - eye.y, pos.getZ() - eye.z);
                try {
                    renderer.render(entity, partialTick, pose, buffers, light, OverlayTexture.NO_OVERLAY);
                } catch (RuntimeException e) {
                    // dit ene blok niet
                }
                pose.popPose();
            }
        }
    }
}
