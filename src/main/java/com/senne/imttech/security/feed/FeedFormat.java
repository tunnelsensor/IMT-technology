package com.senne.imttech.security.feed;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import org.jetbrains.annotations.Nullable;

/**
 * Hoe de wereld rond een camera over de lijn gaat (hoofdstuk 94): de server schrijft, je spel leest. Zie FeedServer
 * (wat er wanneer gestuurd wordt) en client/os/camera/FeedWorld (wat je spel ermee doet).
 *
 * <h2>Een sectie</h2>
 * Minecraft deelt de wereld op in stukjes van 16 x 16 x 16 blokken. Per stukje sturen we:
 * <ul>
 *   <li>waar het ligt (één getal, {@code SectionPos.asLong});</li>
 *   <li>de blokken en de biomen, precies zoals Minecraft ze zelf naar je spel stuurt ({@code LevelChunkSection.write}),
 *       behalve als het stukje alleen lucht is;</li>
 *   <li>het daglicht en het licht van lampen en fakkels: per blok een getal van 0 tot 15. Is het overal hetzelfde (de
 *       lucht boven de grond: overal 15), dan maar één getal;</li>
 *   <li>de blokken met een eigen model (kisten, de koppen van torentjes): hun soort en hun gegevens, zoals Minecraft
 *       ze voor je spel inpakt ({@code getUpdateTag}).</li>
 * </ul>
 * Alles samen pakken we in (zoals een zip, {@link #pack}): blokken en licht lijken veel op elkaar, dat scheelt veel.
 */
public final class FeedFormat {
    private FeedFormat() {}

    /** Verandert het formaat, dan dit getal ook (een oud spel leest dan niets verkeerds). */
    public static final int VERSION = 1;

    private static final int HAS_BLOCKS = 1;
    private static final int SKY_FULL = 2;
    private static final int BLOCK_FULL = 4;

    /** Eén sectie zoals je spel hem krijgt. {@code blocks} is null als het alleen lucht is. */
    public record Section(long key, @Nullable LevelChunkSection blocks, byte[] sky, byte[] block, List<BlockEntityData> blockEntities) {}

    /** Een blok met een eigen model: waar, welke soort (nummer in het register) en zijn gegevens. */
    public record BlockEntityData(BlockPos pos, int type, CompoundTag tag) {}

    /** Wat er in één bericht staat: stukjes om te vergeten, en nieuwe of veranderde stukjes. */
    public record World(long[] forget, List<Section> sections) {}

    // ================================================================ Schrijven (op de server)

    /**
     * Schrijft deze sectie zoals hij nu in de wereld is. De chunk moet geladen zijn ({@code chunk}). Geeft het aantal
     * bytes dat erbij kwam (ongeveer: voor het budget per tick).
     */
    public static int writeSection(FriendlyByteBuf buf, ServerLevel level, LevelChunk chunk, long key) {
        int start = buf.writerIndex();
        SectionPos pos = SectionPos.of(key);
        int index = chunk.getSectionIndexFromSectionY(pos.y());
        LevelChunkSection section = index >= 0 && index < chunk.getSectionsCount() ? chunk.getSection(index) : null;
        boolean blocks = section != null && !section.hasOnlyAir();
        byte[] sky = skyLight(level, pos);
        byte[] block = blockLight(level, pos);
        int skyUniform = uniform(sky);
        int blockUniform = uniform(block);
        int flags = (blocks ? HAS_BLOCKS : 0) | (skyUniform < 0 ? SKY_FULL : 0) | (blockUniform < 0 ? BLOCK_FULL : 0);
        buf.writeLong(key);
        buf.writeByte(flags);
        if (blocks) {
            section.write(buf);
        }
        writeLight(buf, sky, skyUniform);
        writeLight(buf, block, blockUniform);
        // De blokken met een eigen model in deze sectie.
        List<BlockEntity> entities = new ArrayList<>();
        if (blocks) {
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                if (SectionPos.blockToSectionCoord(entity.getBlockPos().getY()) == pos.y()) {
                    entities.add(entity);
                }
            }
        }
        buf.writeVarInt(entities.size());
        for (BlockEntity entity : entities) {
            buf.writeLong(entity.getBlockPos().asLong());
            buf.writeVarInt(BuiltInRegistries.BLOCK_ENTITY_TYPE.getId(entity.getType()));
            CompoundTag tag;
            try {
                tag = entity.getUpdateTag(level.registryAccess());
            } catch (RuntimeException e) {
                tag = new CompoundTag(); // een blok van een andere mod dat niet mee wil: dan zonder gegevens
            }
            buf.writeNbt(tag);
        }
        return buf.writerIndex() - start;
    }

    private static void writeLight(FriendlyByteBuf buf, byte[] light, int uniform) {
        if (uniform >= 0) {
            buf.writeByte(uniform);
        } else {
            buf.writeBytes(light);
        }
    }

    /** Is het licht overal hetzelfde? Dan dat getal (0-15), anders -1. */
    static int uniform(byte[] light) {
        byte first = light[0];
        if ((first & 15) != ((first >> 4) & 15)) {
            return -1;
        }
        for (byte b : light) {
            if (b != first) {
                return -1;
            }
        }
        return first & 15;
    }

    /**
     * Het daglicht in deze sectie, per blok (2048 bytes: twee blokken per byte, zoals Minecraft het bewaart). Heeft
     * Minecraft voor deze sectie zelf niets bewaard, dan komt het licht van boven: het onderste laagje van de eerste sectie
     * erboven die wel iets heeft, of 15 als er niets boven is (zo doet Minecraft het zelf ook, SkyLightSectionStorage). Dat
     * vragen we per kolom één keer.
     */
    static byte[] skyLight(ServerLevel level, SectionPos pos) {
        LayerLightEventListener sky = level.getLightEngine().getLayerListener(LightLayer.SKY);
        DataLayer data = sky.getDataLayerData(pos);
        if (data != null) {
            return data.copy().getData();
        }
        DataLayer column = new DataLayer();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int baseX = pos.minBlockX();
        int baseY = pos.minBlockY();
        int baseZ = pos.minBlockZ();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int value = sky.getLightValue(at.set(baseX + x, baseY, baseZ + z));
                for (int y = 0; y < 16; y++) {
                    column.set(x, y, z, value);
                }
            }
        }
        return column.getData();
    }

    /** Het licht van lampen en fakkels in deze sectie (niets bewaard = overal 0). */
    static byte[] blockLight(ServerLevel level, SectionPos pos) {
        DataLayer data = level.getLightEngine().getLayerListener(LightLayer.BLOCK).getDataLayerData(pos);
        return data != null ? data.copy().getData() : new byte[DataLayer.SIZE];
    }

    /**
     * Een getal dat verandert als het licht in deze sectie verandert (om te weten of we hem opnieuw moeten sturen). Kijkt
     * alleen naar wat Minecraft bewaard heeft: voor een sectie zonder daglicht ook naar de eerste erboven die wel iets heeft.
     */
    static int lightHash(ServerLevel level, SectionPos pos) {
        LayerLightEventListener sky = level.getLightEngine().getLayerListener(LightLayer.SKY);
        int hash = 17;
        DataLayer block = level.getLightEngine().getLayerListener(LightLayer.BLOCK).getDataLayerData(pos);
        hash = hash * 31 + (block == null ? 0 : java.util.Arrays.hashCode(block.getData()));
        SectionPos above = pos;
        for (int up = 0; up < 24; up++) {
            DataLayer data = sky.getDataLayerData(above);
            if (data != null) {
                return hash * 31 + up * 7 + java.util.Arrays.hashCode(data.getData());
            }
            above = SectionPos.of(above.x(), above.y() + 1, above.z());
            if (above.y() > level.getMaxSection()) {
                break;
            }
        }
        return hash * 31 + 15;
    }

    // ================================================================ Inpakken

    /** Pakt de bytes in (zoals een zip). */
    public static byte[] pack(ByteBuf buf) {
        byte[] raw = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), raw);
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
            byte[] chunk = new byte[16384];
            while (!deflater.finished()) {
                int n = deflater.deflate(chunk);
                out.write(chunk, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /** Pakt de bytes weer uit. Geeft null als het niet lukt (een kapot bericht). */
    public static @Nullable FriendlyByteBuf unpack(byte[] packed) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length * 4);
            byte[] chunk = new byte[16384];
            while (!inflater.finished()) {
                int n = inflater.inflate(chunk);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(chunk, 0, n);
            }
            return new FriendlyByteBuf(Unpooled.wrappedBuffer(out.toByteArray()));
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    // ================================================================ Lezen (in je spel)

    /** Leest een heel bericht (al uitgepakt). {@code biomes}: het biomenregister van je wereld. */
    public static World readWorld(FriendlyByteBuf buf, Registry<Biome> biomes) {
        int version = buf.readVarInt();
        if (version != VERSION) {
            return new World(new long[0], List.of());
        }
        int forgetCount = buf.readVarInt();
        long[] forget = new long[forgetCount];
        for (int i = 0; i < forgetCount; i++) {
            forget[i] = buf.readLong();
        }
        int count = buf.readVarInt();
        List<Section> sections = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            sections.add(readSection(buf, biomes));
        }
        return new World(forget, sections);
    }

    private static Section readSection(FriendlyByteBuf buf, Registry<Biome> biomes) {
        long key = buf.readLong();
        int flags = buf.readUnsignedByte();
        LevelChunkSection blocks = null;
        if ((flags & HAS_BLOCKS) != 0) {
            blocks = new LevelChunkSection(biomes);
            blocks.read(buf);
        }
        byte[] sky = readLight(buf, (flags & SKY_FULL) != 0);
        byte[] block = readLight(buf, (flags & BLOCK_FULL) != 0);
        int count = buf.readVarInt();
        List<BlockEntityData> entities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos pos = BlockPos.of(buf.readLong());
            int type = buf.readVarInt();
            CompoundTag tag = buf.readNbt();
            entities.add(new BlockEntityData(pos, type, tag == null ? new CompoundTag() : tag));
        }
        return new Section(key, blocks, sky, block, entities);
    }

    private static byte[] readLight(FriendlyByteBuf buf, boolean full) {
        byte[] light = new byte[DataLayer.SIZE];
        if (full) {
            buf.readBytes(light);
        } else {
            int value = buf.readUnsignedByte() & 15;
            java.util.Arrays.fill(light, (byte) (value | value << 4));
        }
        return light;
    }

    /** De soort van een blok met een eigen model, uit zijn nummer (of null als je spel hem niet kent). */
    public static @Nullable BlockEntityType<?> blockEntityType(int id) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.byId(id);
    }
}
