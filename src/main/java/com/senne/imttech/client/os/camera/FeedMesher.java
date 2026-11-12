package com.senne.imttech.client.os.camera;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.senne.imttech.ImtTech;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Bouwt van de stukjes wereld uit FeedWorld de "modellen" die de grafische kaart tekent (hoofdstuk 94): per stukje van
 * 16 x 16 x 16 blokken en per soort (vast, uitgesneden zoals bladeren, doorzichtig zoals glas en water) een buffer met
 * alle zijkanten van de blokken. Precies zoals Minecraft het zelf doet (SectionCompiler), met dezelfde blokmodellen, het
 * licht en de schaduw in de hoeken, maar uit onze eigen wereld ({@link Region}): zo hangt het camerabeeld niet af van
 * wat je spel voor jou getekend heeft (en dus ook niet van Sodium).
 *
 * Bouwen kost tijd (een paar honderdste milliseconde per stukje, soms meer). Daarom bouwen we per frame maar zoveel als
 * er in {@link #BUDGET_NANOS} past, het dichtstbijzijnde eerst; een beeld vult zich zo in een paar frames.
 */
public final class FeedMesher {
    private FeedMesher() {}

    /** Zoveel tijd (nanoseconden) per frame mogen we samen aan bouwen besteden. */
    static final long BUDGET_NANOS = 4_000_000L;

    /** Het model van één stukje wereld: per soort een buffer op de grafische kaart (leeg: niets te tekenen). */
    static final class Mesh {
        final Map<RenderType, VertexBuffer> buffers = new Reference2ObjectArrayMap<>();

        void close() {
            for (VertexBuffer buffer : buffers.values()) {
                buffer.close();
            }
            buffers.clear();
        }
    }

    private static final Long2ObjectOpenHashMap<Mesh> MESHES = new Long2ObjectOpenHashMap<>();
    /** Stukjes die (opnieuw) gebouwd moeten worden. */
    private static final LongOpenHashSet DIRTY = new LongOpenHashSet();
    private static @Nullable SectionBufferBuilderPack pack;
    /** Tot wanneer (System.nanoTime) er dit frame nog gebouwd mag worden. */
    private static long deadline;

    /** Een nieuw frame: er mag weer {@link #BUDGET_NANOS} gebouwd worden. */
    static void beginFrame() {
        deadline = System.nanoTime() + BUDGET_NANOS;
    }

    /** Dit stukje is nieuw of veranderd: opnieuw bouwen (zijn oude model blijft tot het nieuwe klaar is). */
    static void dirty(long key) {
        DIRTY.add(key);
    }

    /** Het model van dit stukje, of null als het nog niet gebouwd is. */
    static @Nullable Mesh mesh(long key) {
        return MESHES.get(key);
    }

    /**
     * Bouwt wat van deze stukjes (al op volgorde, het belangrijkste eerst) nog niet of niet meer klopt, zolang er tijd is.
     * {@code eye}: waar de camera is (doorzichtige dingen sorteren we van ver naar dichtbij, vanaf daar).
     */
    static void build(List<Long> keys, Vec3 eye) {
        for (long key : keys) {
            if (System.nanoTime() > deadline) {
                return;
            }
            if (MESHES.containsKey(key) && !DIRTY.contains(key)) {
                continue;
            }
            FeedWorld.Section section = FeedWorld.section(key);
            if (section == null) {
                continue;
            }
            Mesh mesh = new Mesh();
            try {
                compile(section, mesh, eye);
            } catch (RuntimeException e) {
                ImtTech.LOGGER.warn("[cameras] could not build a piece of a camera picture", e);
                mesh.close();
                // Wat er half in de buffers bleef staan, mag niet in het volgende stukje terechtkomen: nieuwe buffers.
                if (pack != null) {
                    pack.close();
                    pack = null;
                }
            }
            Mesh old = MESHES.put(key, mesh);
            if (old != null) {
                old.close();
            }
            DIRTY.remove(key);
        }
    }

    /** Dit stukje is weg (de server zei: vergeten). */
    static void release(long key) {
        Mesh mesh = MESHES.remove(key);
        if (mesh != null) {
            mesh.close();
        }
        DIRTY.remove(key);
    }

    /** Alles opruimen (uit de wereld). */
    static void clear() {
        for (Mesh mesh : MESHES.values()) {
            mesh.close();
        }
        MESHES.clear();
        DIRTY.clear();
        if (pack != null) {
            pack.close();
            pack = null;
        }
    }

    /** Hoeveel stukjes er gebouwd klaarstaan (voor het logboek en de tests). */
    static int size() {
        return MESHES.size();
    }

    /** Is er nog iets te bouwen (een nieuw of veranderd stukje), en is er dit frame nog tijd voor? */
    static boolean hasWork() {
        return System.nanoTime() < deadline && (!DIRTY.isEmpty() || MESHES.size() < FeedWorld.size());
    }

    // ================================================================ Bouwen

    private static final Region REGION = new Region();

    /**
     * Zoals SectionCompiler van Minecraft: elk blok van het stukje, met zijn vloeistof (water, lava) en zijn model, in de
     * buffer van zijn soort. Daarna gaat elke buffer naar de grafische kaart.
     */
    private static void compile(FeedWorld.Section section, Mesh mesh, Vec3 eye) {
        if (section.blocks == null) {
            return; // alleen lucht
        }
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        if (pack == null) {
            pack = new SectionBufferBuilderPack();
        }
        Map<RenderType, BufferBuilder> builders = new Reference2ObjectArrayMap<>();
        PoseStack pose = new PoseStack();
        RandomSource random = RandomSource.create();
        int ox = SectionPos.x(section.key) << 4;
        int oy = SectionPos.y(section.key) << 4;
        int oz = SectionPos.z(section.key) << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        ModelBlockRenderer.enableCaching();
        try {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.state(x, y, z);
                        if (state.isAir()) {
                            continue;
                        }
                        pos.set(ox + x, oy + y, oz + z);
                        try {
                            FluidState fluid = state.getFluidState();
                            if (!fluid.isEmpty()) {
                                RenderType type = ItemBlockRenderTypes.getRenderLayer(fluid);
                                dispatcher.renderLiquid(pos, REGION, builder(builders, type), state, fluid);
                            }
                            if (state.getRenderShape() == RenderShape.MODEL) {
                                BakedModel model = dispatcher.getBlockModel(state);
                                ModelData data = model.getModelData(REGION, pos, state, ModelData.EMPTY);
                                random.setSeed(state.getSeed(pos));
                                for (RenderType type : model.getRenderTypes(state, random, data)) {
                                    pose.pushPose();
                                    pose.translate(x, y, z);
                                    dispatcher.renderBatched(state, pos, REGION, pose, builder(builders, type), true, random, data, type);
                                    pose.popPose();
                                }
                            }
                        } catch (RuntimeException e) {
                            // Eén blok (van een andere mod) dat zich zo niet laat tekenen: dat ene blok slaan we over.
                        }
                    }
                }
            }
        } finally {
            ModelBlockRenderer.clearCache();
        }
        // Eerst elke buffer afmaken (wat niet afgemaakt wordt, zou in het volgende stukje terechtkomen), dan naar de kaart.
        Map<RenderType, MeshData> built = new Reference2ObjectArrayMap<>();
        for (Map.Entry<RenderType, BufferBuilder> entry : builders.entrySet()) {
            MeshData data = entry.getValue().build();
            if (data != null) {
                built.put(entry.getKey(), data);
            }
        }
        for (Map.Entry<RenderType, MeshData> entry : built.entrySet()) {
            MeshData data = entry.getValue();
            if (entry.getKey() == RenderType.translucent()) {
                // Doorzichtig (water, glas): van ver naar dichtbij, gezien vanaf de camera (die staat stil).
                data.sortQuads(pack.buffer(RenderType.translucent()),
                        VertexSorting.byDistance((float) (eye.x - ox), (float) (eye.y - oy), (float) (eye.z - oz)));
            }
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(data); // dit sluit data ook
            VertexBuffer.unbind();
            mesh.buffers.put(entry.getKey(), buffer);
        }
    }

    /** De buffer van deze soort (vast, uitgesneden, doorzichtig...), of een nieuwe. */
    private static BufferBuilder builder(Map<RenderType, BufferBuilder> builders, RenderType type) {
        BufferBuilder builder = builders.get(type);
        if (builder == null) {
            builder = new BufferBuilder(pack.buffer(type), VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            builders.put(type, builder);
        }
        return builder;
    }

    // ================================================================ Onze eigen wereld, voor de blokmodellen

    /**
     * De wereld zoals de blokmodellen hem zien tijdens het bouwen: de blokken, het licht en de biomen uit FeedWorld (wat de
     * server stuurde), niet uit je eigen wereld. Zo werkt het ook voor een camera ver buiten wat je spel kent.
     */
    static final class Region implements BlockAndTintGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            return FeedWorld.blockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return FeedWorld.blockEntities(SectionPos.asLong(pos)).get(pos);
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? 1.0f : mc.level.getShade(direction, shade);
        }

        @Override
        public float getShade(float normalX, float normalY, float normalZ, boolean shade) {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? 1.0f : mc.level.getShade(normalX, normalY, normalZ, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            // Alleen voor wie er toch naar vraagt; het licht zelf komt uit getBrightness hieronder.
            return Minecraft.getInstance().level.getLightEngine();
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return FeedWorld.brightness(layer, pos);
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return Math.max(FeedWorld.brightness(LightLayer.SKY, pos) - amount, FeedWorld.brightness(LightLayer.BLOCK, pos));
        }

        /** De kleur van gras, bladeren en water, uit het bioom op die plek (zonder het mengen van Minecraft). */
        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            Holder<Biome> biome = FeedWorld.biome(pos);
            Minecraft mc = Minecraft.getInstance();
            if (biome == null) {
                return mc.level == null ? -1 : mc.level.getBlockTint(pos, resolver);
            }
            return resolver.getColor(biome.value(), pos.getX(), pos.getZ());
        }

        @Override
        public int getHeight() {
            return Minecraft.getInstance().level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return Minecraft.getInstance().level.getMinBuildHeight();
        }
    }
}
