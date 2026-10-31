package com.senne.imttech.security.wall;

import com.senne.imttech.bot.BotEntity;
import com.senne.imttech.bot.BotLink;
import com.senne.imttech.bot.BotModule;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.security.BaseTrust;
import com.senne.imttech.security.CameraBlock;
import com.senne.imttech.security.CameraBlockEntity;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van een Camera Monitor (zie CameraMonitorBlock en CameraWall): wie hem neerzette, en de camera die hier
 * begint, als deze monitor de linkerbovenhoek van een tegel is.
 *
 * Op de server kijkt hij om de twee seconden of hij werkt: een goed scherm, aan een kern met stroom, geen EMP, en zijn eigen
 * stroom betaald (dat zet hij in het blok: {@link CameraMonitorBlock#ON}). Zijn tegel krijgt dan de naam en of er beeld is;
 * dat gaat naar je spel, en daar tekent CameraWallRenderer het beeld.
 */
public class CameraMonitorBlockEntity extends BlockEntity implements BaseTrust.Placed {
    /** Hoeveel blokken ver een speler nog telt als "kijkt mee" voor het read(cameraPin) van een robot op dit scherm. */
    private static final double WATCH_RANGE = 32.0;

    /**
     * Wat er vanaf deze monitor op het scherm staat: hoeveel monitoren breed en hoog, en de camera: een camerablok
     * ({@code camera}) of een robot of drone ({@code robot}: zijn nummer in de wereld).
     */
    public record Tile(int width, int height, @Nullable BlockPos camera, @Nullable UUID robot) {}

    private @Nullable UUID owner;
    private String ownerName = "";
    private @Nullable Tile tile;
    /** De naam bij het beeld, zoals de app Cameras hem toont. */
    private String label = "";
    /** Heeft de camera beeld (uit de server, om de twee seconden)? */
    private boolean live;
    /** Het nummer waaronder je spel de robot van deze tegel kent, of -1. */
    private int robotEntity = -1;

    private @Nullable CameraWall.Shape shape;
    private int shapeVersion = -1;

    public CameraMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA_MONITOR.get(), pos, state);
    }

    // ================================================================ Eigenaar

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    @Override
    public String ownerName() {
        return ownerName;
    }

    @Override
    public void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    // ================================================================ Het scherm en de tegel

    /** Het scherm waar deze monitor bij hoort (onthouden tot er ergens een monitor bij komt of weggaat). */
    public CameraWall.Shape shape() {
        if (shape == null || shapeVersion != CameraWall.version()) {
            shape = CameraWall.of(level, worldPosition);
            shapeVersion = CameraWall.version();
        }
        return shape;
    }

    public @Nullable Tile tile() {
        return tile;
    }

    public String label() {
        return label;
    }

    public boolean live() {
        return live;
    }

    public int robotEntity() {
        return robotEntity;
    }

    /** Een andere camera vanaf deze monitor (of geen). Hij kijkt meteen na of die beeld heeft. */
    public void setTile(@Nullable Tile tile) {
        this.tile = tile;
        label = "";
        live = false;
        robotEntity = -1;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            refreshTile(serverLevel, getBlockState().getValue(CameraMonitorBlock.ON));
            sync();
        }
    }

    /** Het nieuwe naar de spelers in de buurt sturen. */
    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ================================================================ Elke twee seconden (op de server)

    public void serverTick(ServerLevel level, BlockPos pos) {
        if ((level.getGameTime() + pos.hashCode()) % 40 == 0) {
            updateOn(level);
        }
    }

    /**
     * Werkt deze monitor? Het scherm moet goed staan (een rechthoek, niet te groot), aan een kern met stroom hangen, niet
     * plat liggen door een EMP-puls, en hij moet zijn eigen stroom kunnen betalen. Zonder stroom blijft hij zwart.
     */
    public void updateOn(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof CameraMonitorBlock)) {
            return;
        }
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, worldPosition);
        boolean on = shape().valid() && core != null && core.isOnline() && !com.senne.imttech.weapons.Emp.isOut(level, worldPosition)
                && PowerGrid.pay(level, worldPosition, ImtConfig.Power.CAMERA_MONITOR_UPKEEP);
        if (on != state.getValue(CameraMonitorBlock.ON)) {
            level.setBlock(worldPosition, state.setValue(CameraMonitorBlock.ON, on), Block.UPDATE_ALL);
        }
        if (tile != null && refreshTile(level, on)) {
            sync();
        }
    }

    /** Heeft de camera van deze tegel beeld, en hoe heet hij nu? Geeft true als er iets veranderde. */
    private boolean refreshTile(ServerLevel level, boolean on) {
        if (tile == null) {
            return false;
        }
        String newLabel = label;
        boolean newLive = false;
        int newRobot = -1;
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, worldPosition);
        if (core != null && tile.camera() != null) {
            List<BlockPos> cameras = SecurityDevices.scan(level, core).cameras();
            int index = cameras.indexOf(tile.camera());
            if (index >= 0 && level.getBlockEntity(tile.camera()) instanceof CameraBlockEntity camera) {
                newLabel = SecurityDevices.cameraName(camera, index + 1);
                newLive = on && level.getBlockState(tile.camera()).getValue(CameraBlock.ONLINE);
            }
        } else if (core != null && tile.robot() != null && level.getEntity(tile.robot()) instanceof BotEntity robot && robot.isAlive()
                && robot.board().has(BotModule.CAMERA) && BotLink.core(robot) == core) {
            newLabel = robot.displayName();
            newRobot = robot.getId();
            newLive = on && robot.brain().pins().cameraOn() && !robot.isDown();
            // Kijkt er iemand mee? Dan is zijn read(cameraPin) true, net als in de app Cameras.
            if (newLive && level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    WATCH_RANGE, false) != null) {
                robot.brain().pins().watch();
            }
        }
        boolean changed = !newLabel.equals(label) || newLive != live || newRobot != robotEntity;
        label = newLabel;
        live = newLive;
        robotEntity = newRobot;
        return changed;
    }

    // ================================================================ Erbij en weg

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        CameraWall.changed();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CameraWall.changed();
    }

    // ================================================================ Bewaren en doorsturen

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        if (tile != null) {
            CompoundTag saved = new CompoundTag();
            saved.putInt("Width", tile.width());
            saved.putInt("Height", tile.height());
            if (tile.camera() != null) {
                saved.putLong("Camera", tile.camera().asLong());
            }
            if (tile.robot() != null) {
                saved.putUUID("Robot", tile.robot());
            }
            saved.putString("Label", label);
            saved.putBoolean("Live", live);
            saved.putInt("RobotEntity", robotEntity);
            tag.put("Tile", saved);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        if (tag.contains("Tile")) {
            CompoundTag saved = tag.getCompound("Tile");
            tile = new Tile(Math.max(1, saved.getInt("Width")), Math.max(1, saved.getInt("Height")),
                    saved.contains("Camera") ? BlockPos.of(saved.getLong("Camera")) : null,
                    saved.hasUUID("Robot") ? saved.getUUID("Robot") : null);
            label = saved.getString("Label");
            live = saved.getBoolean("Live");
            robotEntity = saved.getInt("RobotEntity");
        } else {
            tile = null;
            label = "";
            live = false;
            robotEntity = -1;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
