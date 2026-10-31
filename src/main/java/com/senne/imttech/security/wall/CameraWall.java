package com.senne.imttech.security.wall;

import com.senne.imttech.bot.BotEntity;
import com.senne.imttech.bot.BotLink;
import com.senne.imttech.bot.BotModule;
import com.senne.imttech.network.CameraWallOpenPayload;
import com.senne.imttech.network.CameraWallSavePayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.robot.Robots;
import com.senne.imttech.security.BaseTrust;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De schermmuur (19 oktober, jouw notitie: "ik wil fysieke cameraschermen, dit is een multiblock: 1x1 is een normaal scherm,
 * plaats je er 2 naast elkaar wordt het 2x1, 2 op elkaar en naast elkaar 2x2 en zo voort. Rechtsklik op de monitor en je
 * komt op een GUI van het hele scherm, hier kies je welke kamer waar en hoe groot staat").
 *
 * <h2>Eén scherm</h2>
 * Camera Monitors die dezelfde kant op kijken en tegen elkaar staan (naast of op elkaar), zijn samen één scherm, zolang het
 * een rechthoek is van hooguit {@link #MAX_WIDTH} x {@link #MAX_HEIGHT} ({@link Shape}). Dat rekenen we niet per blok vast
 * uit maar steeds opnieuw, als er ergens een monitor bij komt of weggaat ({@link #changed}).
 *
 * <h2>Wat erop staat</h2>
 * Het scherm is een rooster met één vak per monitor. Een camera staat op een rechthoek van vakken (een "tegel"); die tegel
 * bewaren we in de monitor van zijn linkerbovenhoek (zie CameraMonitorBlockEntity.Tile). Zet je er een rij monitoren bij of
 * haal je er een weg, dan blijft de rest dus gewoon staan waar hij stond.
 *
 * <h2>Wie mag wat</h2>
 * Kijken mag iedereen die ervoor staat. Instellen mag wie alle monitoren van dit scherm mag instellen (zie BaseTrust), en er
 * staan alleen camera's op van de kern waar het scherm aan hangt: zijn camerablokken, en zijn robots en drones met een
 * Camera-module.
 */
public final class CameraWall {
    private CameraWall() {}

    /** Hoe groot een scherm hooguit is (monitoren). */
    public static final int MAX_WIDTH = 8;
    public static final int MAX_HEIGHT = 6;
    /** Zo dichtbij moet je staan om een scherm in te stellen (blokken). Ruim: zo'n scherm is groot. */
    private static final double REACH = 16.0;

    /** Telt elke monitor die erbij komt of weggaat (ook in je eigen spel): dan rekent elk scherm zijn vorm opnieuw uit. */
    private static volatile int version;

    /** Er kwam een monitor bij of er ging er een weg. */
    public static void changed() {
        version++;
    }

    static int version() {
        return version;
    }

    /**
     * Hoe een scherm eruitziet: de monitor linksboven (gezien vanaf de voorkant), de kant waar het heen kijkt, en hoeveel
     * monitoren breed en hoog. {@code rectangle} = false: er ontbreken monitoren. {@code tooBig}: groter dan mag.
     */
    public record Shape(BlockPos anchor, Direction facing, int width, int height, boolean rectangle, boolean tooBig) {
        public boolean valid() {
            return rectangle && !tooBig;
        }

        /** Rechts, voor wie naar het scherm kijkt. */
        public Direction right() {
            return facing.getCounterClockWise();
        }

        /** De monitor in deze kolom en rij (vanaf linksboven). */
        public BlockPos at(int column, int row) {
            return anchor.relative(right(), column).below(row);
        }

        /** In welke kolom deze monitor staat. */
        public int columnOf(BlockPos pos) {
            Direction right = right();
            return (pos.getX() - anchor.getX()) * right.getStepX() + (pos.getZ() - anchor.getZ()) * right.getStepZ();
        }

        /** In welke rij deze monitor staat. */
        public int rowOf(BlockPos pos) {
            return anchor.getY() - pos.getY();
        }
    }

    /** Het scherm waar de monitor op deze plek bij hoort: we lopen langs alle monitoren die ermee verbonden zijn. */
    public static Shape of(BlockGetter level, BlockPos pos) {
        BlockState start = level.getBlockState(pos);
        Direction facing = start.getBlock() instanceof CameraMonitorBlock ? start.getValue(CameraMonitorBlock.FACING) : Direction.NORTH;
        Direction right = facing.getCounterClockWise();
        Direction[] sides = {right, right.getOpposite(), Direction.UP, Direction.DOWN};
        int limit = MAX_WIDTH * MAX_HEIGHT;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        seen.add(pos.immutable());
        todo.add(pos.immutable());
        int minColumn = 0;
        int maxColumn = 0;
        int minRow = 0;
        int maxRow = 0;
        while (!todo.isEmpty() && seen.size() <= limit) {
            BlockPos at = todo.poll();
            for (Direction side : sides) {
                BlockPos next = at.relative(side);
                if (seen.contains(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof CameraMonitorBlock && state.getValue(CameraMonitorBlock.FACING) == facing) {
                    seen.add(next);
                    todo.add(next);
                    int column = (next.getX() - pos.getX()) * right.getStepX() + (next.getZ() - pos.getZ()) * right.getStepZ();
                    int row = pos.getY() - next.getY();
                    minColumn = Math.min(minColumn, column);
                    maxColumn = Math.max(maxColumn, column);
                    minRow = Math.min(minRow, row);
                    maxRow = Math.max(maxRow, row);
                }
            }
        }
        int width = maxColumn - minColumn + 1;
        int height = maxRow - minRow + 1;
        boolean tooBig = seen.size() > limit || width > MAX_WIDTH || height > MAX_HEIGHT;
        BlockPos anchor = pos.relative(right, minColumn).below(minRow);
        return new Shape(anchor, facing, width, height, seen.size() == width * height, tooBig);
    }

    /** Alle monitoren van dit (geldige) scherm. */
    public static List<BlockPos> members(Shape shape) {
        List<BlockPos> members = new ArrayList<>();
        for (int row = 0; row < shape.height(); row++) {
            for (int column = 0; column < shape.width(); column++) {
                members.add(shape.at(column, row));
            }
        }
        return members;
    }

    // ================================================================ Het instelscherm openen

    /** Waarom dit scherm niet werkt omdat het niet goed staat, of null. */
    private static @Nullable Component shapeProblem(Shape shape) {
        return shape.tooBig() ? Component.translatable("wall.imttech.msg.too_big", MAX_WIDTH, MAX_HEIGHT)
                : !shape.rectangle() ? Component.translatable("wall.imttech.msg.not_rectangle") : null;
    }

    /** Rechtsklik op een monitor: mag deze speler het instellen, dan opent zijn spel het scherm met het hele rooster. */
    public static void open(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(pos) instanceof CameraMonitorBlockEntity entity)
                || player.distanceToSqr(pos.getCenter()) > REACH * REACH) {
            return;
        }
        Shape shape = of(level, pos);
        Component problem = shapeProblem(shape);
        if (problem == null && !mayEditAll(player, level, shape)) {
            String owner = BaseTrust.belongsTo(level, entity);
            problem = owner.isEmpty() ? Component.translatable("wall.imttech.msg.locked")
                    : Component.translatable("wall.imttech.msg.not_yours", owner);
        }
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
        if (problem == null && core == null) {
            problem = Component.translatable("wall.imttech.msg.no_network");
        }
        if (problem != null) {
            player.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        ModNetwork.sendTo(player, new CameraWallOpenPayload(shape.anchor(), shape.width(), shape.height(), snapshot(level, core, shape)));
    }

    /** Mag deze speler alle monitoren van dit scherm instellen? (Anders zette je gewoon je eigen monitor tegen die van een ander.) */
    private static boolean mayEditAll(ServerPlayer player, ServerLevel level, Shape shape) {
        for (BlockPos member : members(shape)) {
            if (level.getBlockEntity(member) instanceof CameraMonitorBlockEntity entity && !BaseTrust.mayEdit(player, entity)) {
                return false;
            }
        }
        return true;
    }

    /** Wat het instelscherm nodig heeft: de camera's van deze kern (zoals de app Cameras) en de tegels die er nu staan. */
    public static CompoundTag snapshot(ServerLevel level, SecurityCoreBlockEntity core, Shape shape) {
        ListTag cameras = SecurityDevices.cameras(level, core);
        cameras.addAll(Robots.cameras(level, core));
        ListTag tiles = new ListTag();
        for (BlockPos member : members(shape)) {
            if (level.getBlockEntity(member) instanceof CameraMonitorBlockEntity entity && entity.tile() != null) {
                CameraMonitorBlockEntity.Tile tile = entity.tile();
                CompoundTag tag = new CompoundTag();
                tag.putInt("c", shape.columnOf(member));
                tag.putInt("r", shape.rowOf(member));
                tag.putInt("w", tile.width());
                tag.putInt("h", tile.height());
                if (tile.camera() != null) {
                    tag.putLong("cam", tile.camera().asLong());
                } else if (tile.robot() != null) {
                    tag.putString("id", tile.robot().toString());
                }
                tiles.add(tag);
            }
        }
        CompoundTag data = new CompoundTag();
        data.put("cameras", cameras);
        data.put("tiles", tiles);
        return data;
    }

    // ================================================================ Bewaren

    /** Een nieuwe indeling uit het instelscherm: de server kijkt alles zelf na (mag het, past het, is het een camera van hier). */
    public static void handleSave(ServerPlayer player, CameraWallSavePayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos anchor = payload.pos();
        if (!level.isLoaded(anchor) || player.distanceToSqr(anchor.getCenter()) > REACH * REACH * 4) {
            return;
        }
        Shape shape = of(level, anchor);
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, anchor);
        if (!shape.valid() || !shape.anchor().equals(anchor) || core == null || !mayEditAll(player, level, shape)) {
            return;
        }
        setLayout(level, core, shape, payload.tiles().getList("tiles", Tag.TAG_COMPOUND));
    }

    /**
     * Zet deze indeling op het scherm: elke tegel in zijn linkerbovenhoek. Wat buiten het scherm valt, over een andere tegel
     * heen ligt, of geen camera van deze kern is, valt af.
     */
    public static void setLayout(ServerLevel level, SecurityCoreBlockEntity core, Shape shape, ListTag tiles) {
        boolean[][] used = new boolean[shape.width()][shape.height()];
        List<BlockPos> members = members(shape);
        for (BlockPos member : members) {
            if (level.getBlockEntity(member) instanceof CameraMonitorBlockEntity entity) {
                entity.setTile(null);
            }
        }
        for (Tag element : tiles) {
            CompoundTag tag = (CompoundTag) element;
            int column = tag.getInt("c");
            int row = tag.getInt("r");
            int width = tag.getInt("w");
            int height = tag.getInt("h");
            if (column < 0 || row < 0 || width < 1 || height < 1 || column + width > shape.width() || row + height > shape.height()
                    || !free(used, column, row, width, height)) {
                continue;
            }
            CameraMonitorBlockEntity.Tile tile = tile(level, core, tag, width, height);
            if (tile != null && level.getBlockEntity(shape.at(column, row)) instanceof CameraMonitorBlockEntity entity) {
                entity.setTile(tile);
                for (int x = column; x < column + width; x++) {
                    for (int y = row; y < row + height; y++) {
                        used[x][y] = true;
                    }
                }
            }
        }
    }

    private static boolean free(boolean[][] used, int column, int row, int width, int height) {
        for (int x = column; x < column + width; x++) {
            for (int y = row; y < row + height; y++) {
                if (used[x][y]) {
                    return false;
                }
            }
        }
        return true;
    }

    /** De tegel voor deze camera, als dat een camera van deze kern is; anders null. */
    private static @Nullable CameraMonitorBlockEntity.Tile tile(ServerLevel level, SecurityCoreBlockEntity core, CompoundTag tag,
                                                                int width, int height) {
        if (tag.contains("cam")) {
            BlockPos camera = BlockPos.of(tag.getLong("cam"));
            return SecurityDevices.scan(level, core).cameras().contains(camera)
                    ? new CameraMonitorBlockEntity.Tile(width, height, camera, null) : null;
        }
        UUID id;
        try {
            id = UUID.fromString(tag.getString("id"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return level.getEntity(id) instanceof BotEntity robot && robot.board().has(BotModule.CAMERA) && BotLink.core(robot) == core
                ? new CameraMonitorBlockEntity.Tile(width, height, null, id) : null;
    }
}
