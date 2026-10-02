package com.senne.starktech.security;

import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De "binnenkant" van een bewakingscamera: zijn naam, en hoever hij van zijn gewone richting is
 * gedraaid (pan: links/rechts) en gekanteld (tilt: omhoog/omlaag). Dat stel je in op de desktop,
 * hooguit {@link #MAX_TURN} graden elke kant op.
 *
 * Op de server kijkt hij elke twee seconden of hij stroom heeft en of de camera's van zijn netwerk
 * aan staan, en betaalt hij een beetje stroom (SecurityBalance.CAMERA_UPKEEP). Dat zet hij in het
 * blok (CameraBlock.ONLINE), zodat jouw spel weet of er beeld is.
 *
 * Naam, pan en tilt gaan ook naar je spel: daar tekent de bewakingsmuur het beeld vanaf de lens
 * (zie {@link #eye}) in de richting {@link #yaw()}/{@link #pitch()}.
 */
public class CameraBlockEntity extends BlockEntity {
    /** Zoveel graden mag je hem hooguit draaien of kantelen (elke kant op). */
    public static final float MAX_TURN = 60f;

    private String name = "";
    /** Graden naar rechts (+) of links (-) van waar hij gewoon heen kijkt. */
    private float pan;
    /** Graden omlaag (+) of omhoog (-) van zijn gewone kanteling. */
    private float tilt;

    public CameraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SECURITY_CAMERA.get(), pos, state);
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name.length() > 32 ? name.substring(0, 32) : name;
        changed();
    }

    public float pan() {
        return pan;
    }

    public float tilt() {
        return tilt;
    }

    /** Een stukje draaien en/of kantelen (vanaf de desktop), binnen de grenzen. */
    public void aim(float dPan, float dTilt) {
        pan = Mth.clamp(pan + dPan, -MAX_TURN, MAX_TURN);
        tilt = Mth.clamp(tilt + dTilt, -MAX_TURN, MAX_TURN);
        changed();
    }

    /** Bewaren, en de nieuwe stand naar de spelers in de buurt sturen. */
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ================================================================ Waar hij heen kijkt

    /** De richting waar hij zonder draaien heen kijkt (graden, zoals de draaiing van een speler). */
    public static float baseYaw(BlockState state) {
        return state.getValue(CameraBlock.FACING).toYRot();
    }

    /**
     * Hoeveel hij zonder kantelen omlaag kijkt: aan de muur net zoveel als het model gekanteld is
     * (22,5 graden), aan het plafond iets meer, op de vloer recht vooruit.
     */
    public static float basePitch(BlockState state) {
        return switch (state.getValue(CameraBlock.FACE)) {
            case WALL -> 22.5f;
            case CEILING -> 30f;
            case FLOOR -> 0f;
        };
    }

    public float yaw() {
        return baseYaw(getBlockState()) + pan;
    }

    public float pitch() {
        return Mth.clamp(basePitch(getBlockState()) + tilt, -80f, 85f);
    }

    /**
     * Waar de lens zit (het "oog" van de camera) in de wereld: net voor de lens, in de richting waar
     * hij kijkt. Uitgerekend uit het model (tools/generate_block_models.py, security_camera): de
     * voorkant van de gekantelde lens ligt zo'n 0,28 blok voor het midden van het blok.
     */
    public static Vec3 eye(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(CameraBlock.FACING);
        AttachFace face = state.getValue(CameraBlock.FACE);
        double up = switch (face) {
            case WALL -> -0.03;
            case CEILING -> -0.04;
            case FLOOR -> -0.06;
        };
        return Vec3.atCenterOf(pos).add(facing.getStepX() * 0.34, up, facing.getStepZ() * 0.34);
    }

    // ================================================================ Stroom (op de server)

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if ((level.getGameTime() + pos.hashCode()) % 40 == 0) {
            updateOnline(level, pos, state);
        }
    }

    /** Heeft hij stroom, en staan de camera's van zijn netwerk aan? Zet dat in het blok (en betaal de stroom). */
    public void updateOnline(ServerLevel level, BlockPos pos, BlockState state) {
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        boolean allowed = core == null || core.camerasEnabled();
        boolean online = allowed && StarkGrid.available(level, pos) >= SecurityBalance.CAMERA_UPKEEP;
        if (online) {
            StarkGrid.draw(level, pos, SecurityBalance.CAMERA_UPKEEP);
        }
        if (online != state.getValue(CameraBlock.ONLINE)) {
            level.setBlock(pos, state.setValue(CameraBlock.ONLINE, online), Block.UPDATE_CLIENTS);
        }
    }

    // ================================================================ Opslaan en naar je spel

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Name", name);
        tag.putFloat("Pan", pan);
        tag.putFloat("Tilt", tilt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = tag.getString("Name");
        pan = Mth.clamp(tag.getFloat("Pan"), -MAX_TURN, MAX_TURN);
        tilt = Mth.clamp(tag.getFloat("Tilt"), -MAX_TURN, MAX_TURN);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
