package com.senne.imttech.suit;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.energy.EnergyCarry;
import com.senne.imttech.energy.EnergySource;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
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
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * De "binnenkant" van een landingsplatform: van wie hij is, en wat hij doet voor wie erop staat.
 *
 * - Van wie? Van wie hem neerzet. Het platform op het dak van een Bibie's Industries Tower (neergezet door
 *   de toren zelf) is van wie de toren gehackt heeft.
 * - Hij zet zichzelf op de lijst van zijn eigenaar (zie LandingPads): zo wijst de HUD je
 *   tijdens het vliegen de weg ernaartoe.
 * - Sta je erop (in een vierkant van 5 x 5 eromheen), dan laadt hij je bril op en repareert hij
 *   je harnas. Land je er met het harnas, dan zegt I.M.T "welkom thuis".
 * - Hij heeft een eigen BATTERIJ (rechtsklik = zijn scherm). Die vult hij langzaam met wat het
 *   net over heeft, en daaruit laadt hij je bril veel sneller op dan rechtstreeks uit het net. In
 *   het scherm zit een vakje voor Battery Upgrades: elke maakt de batterij groter.
 */
public class LandingPadBlockEntity extends BlockEntity implements MenuProvider {
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

    /** Hoeveel stroom er in de eigen batterij zit. */
    private int energy;
    /** Het vakje voor batterijcellen (Battery Upgrades), hooguit MAX_CELLS. */
    public static final int MAX_CELLS = 4;
    private final SimpleContainer cells = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.is(ModItems.BATTERY_UPGRADE.get());
        }

        @Override
        public int getMaxStackSize() {
            return MAX_CELLS;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(LandingPadBlockEntity.this, player);
        }
    };
    /** Wat er de vorige seconde gebeurde (voor het scherm): uit het net erin, en naar jou toe. */
    private int filledLastSecond;
    private int chargedLastSecond;

    /** Wat het scherm te zien krijgt; zie LandingPadMenu (getallen boven 32767 in twee helften). */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case LandingPadMenu.DATA_ENERGY_LOW -> energy & 0xFFFF;
                case LandingPadMenu.DATA_ENERGY_HIGH -> (energy >>> 16) & 0xFFFF;
                case LandingPadMenu.DATA_CAPACITY_LOW -> capacity() & 0xFFFF;
                case LandingPadMenu.DATA_CAPACITY_HIGH -> (capacity() >>> 16) & 0xFFFF;
                case LandingPadMenu.DATA_GRID -> level == null ? 0 : Math.min(0x7FFF, PowerGrid.available(level, worldPosition) / 10);
                case LandingPadMenu.DATA_FILLED -> filledLastSecond;
                case LandingPadMenu.DATA_CHARGED -> chargedLastSecond;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Alleen de server rekent; het scherm leest alleen.
        }

        @Override
        public int getCount() {
            return LandingPadMenu.DATA_COUNT;
        }
    };

    /** De stukjes van de snelheden in de config die nog geen hele stroom zijn (zie EnergyCarry). */
    private final EnergyCarry fillCarry = new EnergyCarry();
    private final EnergyCarry batteryCarry = new EnergyCarry();
    private final EnergyCarry gridCarry = new EnergyCarry();

    public LandingPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LANDING_PAD.get(), pos, state);
        cells.addListener(container -> setChanged());
    }

    /** Hoeveel er in de eigen batterij past: een basis, plus een stuk per batterijcel. */
    public int capacity() {
        int count = Math.min(MAX_CELLS, cells.getItem(0).getCount());
        return (int) (ImtConfig.Power.PAD_BATTERY_BASE + count * ImtConfig.Power.PAD_BATTERY_PER_CELL);
    }

    public int energy() {
        return energy;
    }

    /** Het vakje met batterijcellen (voor het scherm, en om ze te laten vallen bij afbreken). */
    public SimpleContainer cells() {
        return cells;
    }

    // ---------------------------------------------------------------- Het scherm

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.imttech.landing_pad");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new LandingPadMenu(containerId, inventory, cells, data);
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
        // Past er minder in dan er zit (een batterijcel eruit gehaald)? Dan loopt het over.
        energy = Math.min(energy, capacity());
        filledLastSecond = fillFromGrid(level, pos);
        chargedLastSecond = 0;
        UUID ownerId = ownerId(level);
        list(level, ownerId);
        if (ownerId == null) {
            return;
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, zone())) {
            if (ownerId.equals(player.getUUID()) && !player.isSpectator()) {
                chargedLastSecond += service(level, pos, player);
            }
        }
        if (filledLastSecond > 0 || chargedLastSecond > 0) {
            setChanged();
        }
    }

    /**
     * De eigen batterij bijvullen uit het net, maar alleen met wat over is: net als het
     * laadstation houden de generatoren de helft achter (STATION_GRID_RESERVE), zodat torentjes,
     * laserpoorten en de koepel op hetzelfde net altijd voorgaan.
     */
    private int fillFromGrid(ServerLevel level, BlockPos pos) {
        int room = capacity() - energy;
        if (room <= 0) {
            return 0;
        }
        long stored = 0;
        long gridCapacity = 0;
        for (EnergySource source : PowerGrid.sources(level, pos)) {
            stored += source.energy();
            gridCapacity += source.capacity();
        }
        long spare = stored - (long) (gridCapacity * ImtConfig.Power.STATION_GRID_RESERVE);
        int wanted = (int) Math.min(Math.min(spare, room), fillCarry.take(ImtConfig.Power.PAD_BATTERY_FILL));
        if (wanted <= 0) {
            return 0;
        }
        int got = PowerGrid.draw(level, pos, wanted);
        energy += got;
        return got;
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

    /**
     * Bril opladen en harnas repareren. Eerst uit de eigen batterij (snel), en wat dan nog
     * ontbreekt rechtstreeks uit het net (langzamer). Geeft terug hoeveel de bril erbij kreeg.
     */
    private int service(ServerLevel level, BlockPos pos, ServerPlayer player) {
        boolean worked = false;
        int charged = 0;
        ItemStack glasses = ImtData.getWornGlasses(player);
        if (!glasses.isEmpty() && ImtData.isAuthorized(glasses, player)) {
            int missing = ImtData.getMaxEnergy(glasses) - ImtData.getEnergy(glasses);
            int fromBattery = Math.min(Math.min(missing, energy), batteryCarry.take(ImtConfig.Power.PAD_BATTERY_CHARGE));
            energy -= fromBattery;
            int fromGrid = PowerGrid.draw(level, pos,
                    Math.min(missing - fromBattery, gridCarry.take(ImtConfig.Power.PAD_CHARGE_PER_SECOND)));
            charged = fromBattery + fromGrid;
            if (charged > 0) {
                ImtData.addEnergy(glasses, charged);
                worked = true;
            }
        }
        for (ItemStack piece : player.getInventory().armor) {
            if (!(piece.getItem() instanceof TechSuitItem) || !piece.isDamaged()) {
                continue;
            }
            int repair = Math.min(piece.getDamageValue(), Math.round(ImtConfig.Power.PAD_REPAIR_PER_SECOND));
            if (PowerGrid.pay(level, pos, repair * ImtConfig.Power.PAD_REPAIR_ENERGY)) {
                piece.setDamageValue(piece.getDamageValue() - repair);
                worked = true;
            }
        }
        if (worked) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.1, player.getZ(),
                    6, 0.4, 0.05, 0.4, 0.05);
        }
        return charged;
    }

    // ================================================================ Landen

    /**
     * De stuwraketten staan nog aan en je raakt de grond: sta je op een van je eigen platforms,
     * dan zegt I.M.T "welkom thuis". (Aangeroepen door ImtSuit.)
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
        ImtServer.send(player, Component.translatable("suit.imttech.msg.welcome_home"), ImtMessagePayload.Level.INFO);
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
        tag.putInt("Energy", energy);
        if (!cells.getItem(0).isEmpty()) {
            tag.put("Cells", cells.getItem(0).save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        listedFor = tag.hasUUID("ListedFor") ? tag.getUUID("ListedFor") : null;
        energy = tag.getInt("Energy");
        cells.setItem(0, tag.contains("Cells") ? ItemStack.parseOptional(registries, tag.getCompound("Cells")) : ItemStack.EMPTY);
    }
}
