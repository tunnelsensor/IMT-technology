package com.senne.imttech.security;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * hooguit {@link #MAX_TURN} graden elke kant op. De naam geef je in een aambeeld of op de desktop
 * (de knop Rename bij het grote beeld); breek je hem af, dan houdt het item die naam.
 *
 * Op de server kijkt hij elke twee seconden of hij stroom heeft en of de camera's van zijn netwerk
 * aan staan, en betaalt hij een beetje stroom (ImtConfig.Power.CAMERA_UPKEEP). Dat zet hij in het
 * blok (CameraBlock.ONLINE), zodat jouw spel weet of er beeld is.
 *
 * Naam, pan en tilt gaan ook naar je spel: daar tekent de bewakingsmuur het beeld vanaf de lens
 * (zie {@link #eye}) in de richting {@link #yaw()}/{@link #pitch()}.
 *
 * Sinds hoofdstuk 93 onthoudt hij ook wie hem neerzette ({@link BaseTrust.Placed}): wie dat is, of wie vertrouwd is in de
 * basis, geeft hem met sneak + rechtsklik een andere naam (zie CameraNames), net als bij een keycardlezer.
 */
public class CameraBlockEntity extends BlockEntity implements BaseTrust.Placed, com.senne.imttech.lan.DevicePower.Idle {
    /** Zoveel graden mag je hem hooguit draaien of kantelen (elke kant op). */
    public static final float MAX_TURN = 60f;
    /** Zo lang mag zijn naam hooguit zijn (tekens). */
    public static final int MAX_NAME = 32;

    private String name = "";
    /** Wie hem neerzette (hoofdstuk 93), of null: van vóór 12 november, of uit een gebouw (de toren). */
    private @Nullable java.util.UUID owner;
    private String ownerName = "";
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

    /** Een nieuwe naam (netjes gemaakt, zie {@link #cleanName}). Leeg = weer "Camera 3" op de desktop. */
    public void setName(String name) {
        this.name = cleanName(name);
        changed();
    }

    /**
     * Een naam zoals we hem bewaren: zonder onzichtbare tekens en zonder het teken waarmee
     * Minecraft tekst kleurt, zonder spaties voor en achter, en hooguit {@link #MAX_NAME} tekens.
     */
    public static String cleanName(String name) {
        StringBuilder clean = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c >= ' ' && c != 127 && c != '§') {
                clean.append(c);
            }
        }
        String result = clean.toString().strip();
        return result.length() > MAX_NAME ? result.substring(0, MAX_NAME).strip() : result;
    }

    // ================================================================ Wie hem neerzette (hoofdstuk 93)

    @Override
    public @Nullable java.util.UUID owner() {
        return owner;
    }

    @Override
    public String ownerName() {
        return ownerName;
    }

    @Override
    public void setOwner(net.minecraft.server.level.ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
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

    /**
     * Heeft hij stroom, staan de camera's van zijn netwerk aan, en is hij niet uitgeschakeld door een EMP-granaat
     * (hoofdstuk 35)? Zet dat in het blok (en betaal de stroom). Sinds hoofdstuk 38 moet hij ook met een netwerkkabel
     * aan de desktop van een kern hangen (behalve met {@code [network] required = false}): zonder kabel geen beeld.
     */
    public void updateOnline(ServerLevel level, BlockPos pos, BlockState state) {
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
        boolean allowed = core == null ? !com.senne.imttech.lan.LanGrid.required() : core.camerasEnabled();
        boolean online = state.getValue(CameraBlock.ENABLED) && allowed && !com.senne.imttech.weapons.Emp.isOut(level, pos)
                && PowerGrid.pay(level, pos, ImtConfig.Power.CAMERA_UPKEEP);
        if (online != state.getValue(CameraBlock.ONLINE)) {
            // Hoofdstuk 124: zonder vormupdates naar de buren (UPDATE_KNOWN_SHAPE). ONLINE verandert geen enkele buur, en aan
            // de rand van een stuk wereld dat niet draait, zou de server het stuk ernaast daarvoor meteen moeten laden.
            level.setBlock(pos, state.setValue(CameraBlock.ONLINE, online), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /**
     * Hoofdstuk 124: draait zijn stuk wereld niet (aan het eind van een lange kabel, dat NetworkKeeper alleen geladen
     * houdt), dan kijkt zijn kern om de twee seconden of hij aan kan (zie DevicePower.refreshIdle). Alleen aan, nooit uit:
     * daar ziet de kern zijn eigen stroom niet altijd (zie DevicePower.Idle). Uit gaat hij in zijn eigen tick, of als je
     * hem uitzet.
     */
    @Override
    public void refreshIdle(ServerLevel level) {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof CameraBlock && !state.getValue(CameraBlock.ONLINE)) {
            updateOnline(level, worldPosition, state);
        }
    }

    // ================================================================ Opslaan en naar je spel

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Name", name);
        tag.putFloat("Pan", pan);
        tag.putFloat("Tilt", tilt);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = tag.getString("Name");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        pan = Mth.clamp(tag.getFloat("Pan"), -MAX_TURN, MAX_TURN);
        tilt = Mth.clamp(tag.getFloat("Tilt"), -MAX_TURN, MAX_TURN);
    }

    /**
     * Breek je hem af, dan krijgt het item zijn naam mee (zie de loot table: copy_components). Zet
     * je het item weer neer, dan geeft CameraBlock.setPlacedBy de naam terug.
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!name.isEmpty()) {
            components.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
    }

    /** De naam zit al in het item (als CUSTOM_NAME), dus niet nog eens in zijn blokgegevens. */
    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Name");
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
