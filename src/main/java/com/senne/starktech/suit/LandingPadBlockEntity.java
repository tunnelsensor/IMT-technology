package com.senne.starktech.suit;

import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * De "binnenkant" van een landingsplatform: van wie hij is, en wat hij doet voor wie erop staat.
 *
 * - Van wie? Van wie hem neerzet. Het platform op het dak van een Stark Tower (neergezet door
 *   de toren zelf) is van wie de toren gehackt heeft.
 * - Hij zet zichzelf op de lijst van zijn eigenaar (zie LandingPads): zo wijst de HUD je
 *   tijdens het vliegen de weg ernaartoe.
 * - Sta je erop (in een vierkant van 5 x 5 eromheen), dan laadt hij je bril op en repareert hij
 *   je harnas, met stroom van het net. Land je er met het harnas, dan zegt E.D.I.T.H. "welkom thuis".
 */
public class LandingPadBlockEntity extends BlockEntity {
    /** Zo ver opzij (in blokken, vanaf het midden) telt nog als "op het platform". */
    public static final double ZONE = 2.5;
    private static final int CHECK_TICKS = 20;
    /** Na een welkom zegt ze het zo lang (ticks) niet nog eens. */
    private static final int WELCOME_PAUSE = 200;
    private static final Map<UUID, Long> LAST_WELCOME = new HashMap<>();

    /** Wie hem neerzette (null bij het platform van de toren: dan de eigenaar van de toren). */
    private @Nullable UUID owner;
    /** Op wiens lijst hij nu staat (om hem eraf te halen als de eigenaar verandert). */
    private @Nullable UUID listedFor;

    public LandingPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LANDING_PAD.get(), pos, state);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** Van wie dit platform nu is: wie hem neerzette, of anders wie de toren eromheen gehackt heeft. */
    public @Nullable UUID ownerId(ServerLevel level) {
        if (owner != null) {
            return owner;
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, worldPosition);
        return core == null ? null : core.ownerId();
    }

    // ================================================================ Elke seconde (server)

    public void serverTick(ServerLevel level, BlockPos pos) {
        if ((level.getGameTime() + pos.asLong()) % CHECK_TICKS != 0) {
            return;
        }
        UUID ownerId = ownerId(level);
        list(level, ownerId);
        if (ownerId == null) {
            return;
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, zone())) {
            if (ownerId.equals(player.getUUID()) && !player.isSpectator()) {
                service(level, pos, player);
            }
        }
    }

    /** Het vierkant boven het platform waar je "erop" staat. */
    private AABB zone() {
        return new AABB(worldPosition.getX() + 0.5 - ZONE, worldPosition.getY() + 1, worldPosition.getZ() + 0.5 - ZONE,
                worldPosition.getX() + 0.5 + ZONE, worldPosition.getY() + 3, worldPosition.getZ() + 0.5 + ZONE);
    }

    /** Zorgt dat dit platform (alleen) op de lijst van zijn eigenaar staat. */
    private void list(ServerLevel level, @Nullable UUID ownerId) {
        GlobalPos here = GlobalPos.of(level.dimension(), worldPosition);
        if (listedFor != null && !listedFor.equals(ownerId)) {
            ServerPlayer old = level.getServer().getPlayerList().getPlayer(listedFor);
            if (old != null) {
                LandingPads.remove(old, here);
            }
            listedFor = null;
            setChanged();
        }
        ServerPlayer player = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        if (player != null) {
            LandingPads.add(player, here);
            if (!ownerId.equals(listedFor)) {
                listedFor = ownerId;
                setChanged();
            }
        }
    }

    /** Bril opladen en harnas repareren, zolang het net stroom heeft. */
    private static void service(ServerLevel level, BlockPos pos, ServerPlayer player) {
        boolean worked = false;
        ItemStack glasses = EdithData.getWornGlasses(player);
        if (!glasses.isEmpty() && EdithData.isAuthorized(glasses, player)) {
            int missing = EdithData.getMaxEnergy(glasses) - EdithData.getEnergy(glasses);
            int got = StarkGrid.draw(level, pos, Math.min(missing, SuitBalance.PAD_CHARGE_PER_SECOND));
            if (got > 0) {
                EdithData.addEnergy(glasses, got);
                worked = true;
            }
        }
        for (ItemStack piece : player.getInventory().armor) {
            if (!(piece.getItem() instanceof StarkSuitItem) || !piece.isDamaged()) {
                continue;
            }
            int repair = Math.min(piece.getDamageValue(), SuitBalance.PAD_REPAIR_PER_SECOND);
            int cost = repair * SuitBalance.PAD_REPAIR_ENERGY;
            if (StarkGrid.available(level, pos) >= cost && StarkGrid.draw(level, pos, cost) >= cost) {
                piece.setDamageValue(piece.getDamageValue() - repair);
                worked = true;
            }
        }
        if (worked) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.1, player.getZ(),
                    6, 0.4, 0.05, 0.4, 0.05);
        }
    }

    // ================================================================ Landen

    /**
     * De stuwraketten staan nog aan en je raakt de grond: sta je op een van je eigen platforms,
     * dan zegt E.D.I.T.H. "welkom thuis". (Aangeroepen door EdithSuit.)
     */
    public static void touchdown(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_WELCOME.get(player.getUUID());
        if (last != null && now - last < WELCOME_PAUSE) {
            return;
        }
        LandingPadBlockEntity pad = padUnder(player);
        if (pad == null) {
            return;
        }
        LAST_WELCOME.put(player.getUUID(), now);
        EdithServer.send(player, Component.translatable("suit.starktech.msg.welcome_home"), EdithMessagePayload.Level.INFO);
    }

    /** Het platform waar deze speler op staat (en dat van hem is), of null. */
    public static @Nullable LandingPadBlockEntity padUnder(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos feet = player.blockPosition();
        int reach = (int) Math.ceil(ZONE);
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-reach, -2, -reach), feet.offset(reach, -1, reach))) {
            if (level.getBlockState(pos).is(ModBlocks.LANDING_PAD.get())
                    && level.getBlockEntity(pos) instanceof LandingPadBlockEntity pad
                    && pad.zone().contains(player.position())
                    && player.getUUID().equals(pad.ownerId(level))) {
                return pad;
            }
        }
        return null;
    }

    /** Afgebroken: van de lijst van zijn eigenaar af. */
    public void onBroken() {
        if (level instanceof ServerLevel serverLevel && listedFor != null) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(listedFor);
            if (player != null) {
                LandingPads.remove(player, GlobalPos.of(serverLevel.dimension(), worldPosition));
            }
        }
    }

    public static void forget(UUID player) {
        LAST_WELCOME.remove(player);
    }

    public static void clear() {
        LAST_WELCOME.clear();
    }

    // ================================================================ Opslaan

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        if (listedFor != null) {
            tag.putUUID("ListedFor", listedFor);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        listedFor = tag.hasUUID("ListedFor") ? tag.getUUID("ListedFor") : null;
    }
}
