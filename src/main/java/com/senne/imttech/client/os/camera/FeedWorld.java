package com.senne.imttech.client.os.camera;

import com.senne.imttech.ImtTech;
import com.senne.imttech.network.CameraFeedPayload;
import com.senne.imttech.network.CameraWorldPayload;
import com.senne.imttech.security.feed.FeedEntities;
import com.senne.imttech.security.feed.FeedFormat;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De wereld rond de camera's die je bekijkt, zoals de server hem stuurt (hoofdstuk 94, zie security/feed/FeedServer). Je spel
 * tekent de camerabeelden hieruit, los van de wereld die het voor jou tekent: zo hebben ook camera's ver weg beeld, en werkt
 * het ook met Sodium (dat het tekenen van de wereld helemaal overneemt).
 * <ul>
 *   <li>{@link #section}: een stukje van 16 x 16 x 16 blokken: de blokken, het licht en de blokken met een eigen model
 *       (die maken we hier, zonder ze in je wereld te zetten: ze doen niets, ze worden alleen getekend).</li>
 *   <li>{@link #camera} en {@link #robot}: wat de server over een camera zei (mag je hem zien, waar kijkt hij).</li>
 *   <li>De wezens rond de camera's: zie FeedPuppets.</li>
 * </ul>
 * Komt er een stukje binnen (of verandert het), dan moet FeedMesher het opnieuw bouwen, en ook de zes stukjes ernaast (de
 * randen van hun blokken en hun schaduw hangen ervan af).
 */
public final class FeedWorld {
    private FeedWorld() {}

    /** Eén stukje wereld. {@code blocks} is null als het alleen lucht is. */
    public static final class Section {
        final long key;
        final @Nullable LevelChunkSection blocks;
        final byte[] sky;
        final byte[] block;
        final Map<BlockPos, BlockEntity> blockEntities;

        Section(long key, @Nullable LevelChunkSection blocks, byte[] sky, byte[] block, Map<BlockPos, BlockEntity> blockEntities) {
            this.key = key;
            this.blocks = blocks;
            this.sky = sky;
            this.block = block;
            this.blockEntities = blockEntities;
        }

        BlockState state(int x, int y, int z) {
            return blocks == null ? Blocks.AIR.defaultBlockState() : blocks.getBlockState(x & 15, y & 15, z & 15);
        }

        int light(LightLayer layer, int x, int y, int z) {
            byte[] data = layer == LightLayer.SKY ? sky : block;
            int index = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
            return (data[index >> 1] >> ((index & 1) << 2)) & 15;
        }

        @Nullable Holder<Biome> biome(int x, int y, int z) {
            return blocks == null ? null : blocks.getNoiseBiome((x & 15) >> 2, (y & 15) >> 2, (z & 15) >> 2);
        }
    }

    /** Wat de server over een camera of robot zei, en wanneer (ms). */
    public record State(byte status, @Nullable Vec3 eye, float yaw, float pitch, long time) {}

    private static final Long2ObjectOpenHashMap<Section> SECTIONS = new Long2ObjectOpenHashMap<>();
    private static final Map<Long, State> CAMERAS = new HashMap<>();
    private static final Map<Integer, State> ROBOTS = new HashMap<>();

    // ================================================================ Wat er binnenkomt

    /** Stukjes wereld van de server: vergeten wat weg mag, en de nieuwe of veranderde erin zetten. */
    public static void onWorld(CameraWorldPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        FriendlyByteBuf buf = FeedFormat.unpack(payload.data());
        if (buf == null) {
            return;
        }
        FeedFormat.World world;
        try {
            world = FeedFormat.readWorld(buf, mc.level.registryAccess().registryOrThrow(Registries.BIOME));
        } catch (RuntimeException e) {
            ImtTech.LOGGER.warn("[cameras] could not read a camera world message", e);
            return;
        } finally {
            buf.release();
        }
        for (long key : world.forget()) {
            if (SECTIONS.remove(key) != null) {
                FeedMesher.release(key);
                dirtyAround(key);
            }
        }
        for (FeedFormat.Section data : world.sections()) {
            SECTIONS.put(data.key(), new Section(data.key(), data.blocks(), data.sky(), data.block(), blockEntities(data)));
            FeedMesher.dirty(data.key());
            dirtyAround(data.key());
        }
    }

    /** De zes buren van een stukje moeten ook opnieuw gebouwd worden (hun randen en schaduw hangen ervan af). */
    private static void dirtyAround(long key) {
        for (Direction direction : Direction.values()) {
            long next = SectionPos.offset(key, direction);
            if (SECTIONS.containsKey(next)) {
                FeedMesher.dirty(next);
            }
        }
    }

    /**
     * De blokken met een eigen model in dit stukje, gemaakt zoals je spel ze ook maakt als het een stuk wereld krijgt
     * (handleUpdateTag), maar niet in je wereld gezet: ze tikken niet en doen niets. Alleen wat je spel ook echt tekent
     * (met een eigen tekenaar), houden we.
     */
    private static Map<BlockPos, BlockEntity> blockEntities(FeedFormat.Section data) {
        if (data.blocks() == null || data.blockEntities().isEmpty()) {
            return Map.of();
        }
        Minecraft mc = Minecraft.getInstance();
        Map<BlockPos, BlockEntity> entities = new HashMap<>();
        for (FeedFormat.BlockEntityData entity : data.blockEntities()) {
            BlockEntityType<?> type = FeedFormat.blockEntityType(entity.type());
            BlockPos pos = entity.pos();
            BlockState state = data.blocks().getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
            if (type == null || !type.isValid(state)) {
                continue;
            }
            try {
                BlockEntity made = type.create(pos, state);
                if (made == null) {
                    continue;
                }
                made.setLevel(mc.level);
                made.handleUpdateTag(entity.tag(), mc.level.registryAccess());
                if (mc.getBlockEntityRenderDispatcher().getRenderer(made) != null) {
                    entities.put(pos, made);
                }
            } catch (RuntimeException e) {
                // een blok van een andere mod dat zo niet gemaakt wil worden: dan zonder
            }
        }
        return entities;
    }

    /** Wat de server zegt over de camera's, en wie er rondloopt. */
    public static void onFeed(CameraFeedPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()), mc.level.registryAccess());
        FeedEntities.Feed feed;
        try {
            feed = FeedEntities.read(buf);
        } catch (RuntimeException e) {
            ImtTech.LOGGER.warn("[cameras] could not read a camera feed message", e);
            return;
        } finally {
            buf.release();
        }
        long now = net.minecraft.Util.getMillis();
        for (FeedEntities.SourceState source : feed.sources()) {
            State state = new State(source.status(), source.eye(), source.yaw(), source.pitch(), now);
            if (source.robot()) {
                ROBOTS.put((int) source.id(), state);
            } else {
                CAMERAS.put(source.id(), state);
            }
        }
        FeedPuppets.update(feed.entities());
    }

    // ================================================================ Opvragen

    public static @Nullable Section section(long key) {
        return SECTIONS.get(key);
    }

    /** Heeft je spel dit stukje al van de server? */
    public static boolean has(long key) {
        return SECTIONS.containsKey(key);
    }

    /** Wat de server het laatst over deze camera zei, of null. */
    public static @Nullable State camera(BlockPos pos) {
        return CAMERAS.get(pos.asLong());
    }

    /** Wat de server het laatst over deze robot zei, of null. */
    public static @Nullable State robot(int entityId) {
        return ROBOTS.get(entityId);
    }

    /** Het blok op deze plek, uit wat de server stuurde (lucht als je spel dat stukje niet heeft). */
    public static BlockState blockState(BlockPos pos) {
        Section section = SECTIONS.get(SectionPos.asLong(pos));
        return section == null ? Blocks.AIR.defaultBlockState() : section.state(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Het licht op deze plek (0-15), uit wat de server stuurde. Een stukje dat je spel niet heeft: daglicht 15, verder
     * donker (zo ver kijkt een camera toch niet: daar is het mist).
     */
    public static int brightness(LightLayer layer, BlockPos pos) {
        Section section = SECTIONS.get(SectionPos.asLong(pos));
        if (section == null) {
            return layer == LightLayer.SKY ? 15 : 0;
        }
        return section.light(layer, pos.getX(), pos.getY(), pos.getZ());
    }

    /** Het licht op deze plek zoals een tekenaar het wil hebben (daglicht en lamplicht samen in één getal). */
    public static int packedLight(BlockPos pos) {
        return net.minecraft.client.renderer.LightTexture.pack(brightness(LightLayer.BLOCK, pos), brightness(LightLayer.SKY, pos));
    }

    /** Het bioom op deze plek (voor de kleur van gras en bladeren), of null. */
    public static @Nullable Holder<Biome> biome(BlockPos pos) {
        Section section = SECTIONS.get(SectionPos.asLong(pos));
        return section == null ? null : section.biome(pos.getX(), pos.getY(), pos.getZ());
    }

    /** De blokken met een eigen model in dit stukje (leeg als er geen zijn). */
    public static Map<BlockPos, BlockEntity> blockEntities(long key) {
        Section section = SECTIONS.get(key);
        return section == null ? Map.of() : section.blockEntities;
    }

    /** Hoeveel stukjes wereld je spel nu van de server heeft (voor de tests en het logboek). */
    public static int size() {
        return SECTIONS.size();
    }

    // ================================================================ Opruimen

    /** Uit de wereld (of naar een andere dimensie): alles vergeten. */
    public static void clear() {
        SECTIONS.clear();
        CAMERAS.clear();
        ROBOTS.clear();
        FeedMesher.clear();
        FeedPuppets.clear();
    }

    /** Wat al lang niet meer van de server kwam (de server stuurde niets meer: je kijkt niet meer), vergeten. */
    static void forgetOldStates(long now) {
        CAMERAS.values().removeIf(state -> now - state.time() > 10_000);
        ROBOTS.values().removeIf(state -> now - state.time() > 10_000);
    }

    /** Alle stukjes die je spel heeft binnen dit bereik van deze plek (het midden binnen {@code reach} blokken). */
    static void around(Vec3 eye, double reach, List<Long> into) {
        double reach2 = reach * reach;
        int radius = (int) Math.ceil(reach / 16.0);
        int cx = SectionPos.posToSectionCoord(eye.x);
        int cy = SectionPos.posToSectionCoord(eye.y);
        int cz = SectionPos.posToSectionCoord(eye.z);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int sx = cx + dx;
                    int sy = cy + dy;
                    int sz = cz + dz;
                    long key = SectionPos.asLong(sx, sy, sz);
                    if (!SECTIONS.containsKey(key)) {
                        continue;
                    }
                    double ox = (sx << 4) + 8 - eye.x;
                    double oy = (sy << 4) + 8 - eye.y;
                    double oz = (sz << 4) + 8 - eye.z;
                    if (ox * ox + oy * oy + oz * oz <= reach2) {
                        into.add(key);
                    }
                }
            }
        }
    }
}
