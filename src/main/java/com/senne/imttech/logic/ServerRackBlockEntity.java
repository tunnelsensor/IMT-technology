package com.senne.imttech.logic;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * De "binnenkant" van een serverrack: zijn zes vakjes (CPU, GPU, RAM, SSD, fan), en of hij meedraait.
 *
 * Elke twee seconden kijkt hij:
 * <ul>
 *   <li>staat hij in het gebied van een beveiligingskern? (de server hangt aan je Security OS);</li>
 *   <li>hoort hij bij de server van die kern? Racks die elkaar raken zijn samen één server; staan
 *       er losse groepjes, dan telt alleen de grootste groep (zie LogicServer);</li>
 *   <li>heeft hij stroom? Dan betaalt hij RACK_UPKEEP.</li>
 * </ul>
 * Is alles in orde, dan gaan zijn lampjes knipperen (ServerRackBlock.ONLINE) en tellen zijn
 * onderdelen mee.
 *
 * Elk rack heeft zijn eigen temperatuur (jouw wens, 8 oktober: "elke server rack maakt zijn eigen
 * temperatuur"): de onderdelen erin maken warmte, en alleen de fans in DIT rack koelen die weg. Zonder
 * fan raakt de warmte er niet uit, dus elk rack met iets erin heeft minstens één fan nodig. Het
 * rekenen doet Datacenter (om de twee seconden); het rack onthoudt alleen hoe warm het is.
 *
 * Wordt een rack veel te heet, dan kan het in brand vliegen: vuur erop, rook, en na BURN_TICKS is het
 * weg, met alles wat er dan nog in zit. Tot die tijd kun je het openen en je onderdelen eruit halen.
 */
public class ServerRackBlockEntity extends BlockEntity implements MenuProvider {
    /** Waarom hij niet meedraait (voor het scherm). */
    public enum Status {
        ONLINE, NO_NETWORK, NOT_CONNECTED, NO_POWER
    }

    private final SimpleContainer parts = new SimpleContainer(ImtConfig.Logic.RACK_SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof ServerPartItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(ServerRackBlockEntity.this, player);
        }
    };

    private Status status = Status.NO_NETWORK;
    /** Tot wanneer (speltijd) dit rack in brand staat; 0 = het brandt niet. */
    private long burnUntil;
    /** Hoe warm dit rack is (graden), en hoe lang (ticks) het al aan één stuk boven FIRE_TEMPERATURE is. */
    private double temperature = ImtConfig.Logic.AMBIENT_TEMPERATURE;
    private int fireTicks;
    /**
     * De laatste meting (niet bewaard, na het laden meteen opnieuw): warmte nu, warmte als alle CPU's
     * en GPU's zo hard werken als ze kunnen, en koeling (de fans in dit rack). Alles in watt.
     */
    private double heat;
    private double fullHeat;
    private double cooling;

    /** Wat het scherm te zien krijgt; zie ServerRackMenu. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index == ServerRackMenu.DATA_STATUS) {
                return status.ordinal();
            }
            if (!(level instanceof ServerLevel serverLevel)) {
                return 0;
            }
            if (index == ServerRackMenu.DATA_BURN) {
                return burnSecondsLeft(serverLevel.getGameTime());
            }
            if (index == ServerRackMenu.DATA_TEMP) {
                return tenths(temperature); // ook zonder netwerk: dan koelt het af
            }
            SecurityCoreBlockEntity core = SecurityCores.coreAt(serverLevel, worldPosition);
            if (core == null) {
                return 0;
            }
            LogicServer.Stats stats = LogicServer.stats(serverLevel, core);
            return switch (index) {
                case ServerRackMenu.DATA_RACKS -> stats.racks();
                case ServerRackMenu.DATA_CPU -> stats.cpus();
                case ServerRackMenu.DATA_GPU -> stats.gpus();
                case ServerRackMenu.DATA_FAN -> stats.fans();
                case ServerRackMenu.DATA_RAM -> stats.ram();
                case ServerRackMenu.DATA_SSD -> stats.ssd();
                case ServerRackMenu.DATA_CORE_ONLINE -> stats.coreOnline() ? 1 : 0;
                // De warmte en koeling van DIT rack, en de temperatuur van het heetste rack van de server
                // (dat bepaalt hoe snel de hele server werkt).
                case ServerRackMenu.DATA_HEAT -> tenths(heat);
                case ServerRackMenu.DATA_FULL_HEAT -> tenths(fullHeat);
                case ServerRackMenu.DATA_COOLING -> tenths(cooling);
                case ServerRackMenu.DATA_SERVER_TEMP -> tenths(core.datacenter().temperature());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Alleen de server rekent; het scherm leest alleen.
        }

        @Override
        public int getCount() {
            return ServerRackMenu.DATA_COUNT;
        }
    };

    /** 72.44 wordt 724 (het scherm krijgt alleen hele getallen, en niet groter dan 32767). */
    private static int tenths(double value) {
        return (int) Math.max(-32000, Math.min(32000, Math.round(value * 10)));
    }

    public ServerRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVER_RACK.get(), pos, state);
        parts.addListener(container -> {
            setChanged();
            LogicServer.forgetAll(); // de server telt opnieuw
        });
    }

    public SimpleContainer parts() {
        return parts;
    }

    public Status status() {
        return status;
    }

    /** Hoeveel onderdelen van deze soort in dit rack zitten. */
    public int count(ServerPartItem.Kind kind) {
        int count = 0;
        for (int i = 0; i < parts.getContainerSize(); i++) {
            if (parts.getItem(i).getItem() instanceof ServerPartItem part && part.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    // ================================================================ Elke twee seconden

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (burnUntil > 0) {
            if (level.getGameTime() >= burnUntil) {
                burnDown(level);
                return;
            }
            burning(level, pos);
        }
        if ((level.getGameTime() + pos.hashCode()) % 40 == 0) {
            update(level, pos, state);
        }
    }

    /** Hoort hij bij een server, heeft hij stroom? Zet dat in het blok (en betaal de stroom). */
    public void update(ServerLevel level, BlockPos pos, BlockState state) {
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        Status now;
        if (core == null) {
            now = Status.NO_NETWORK;
        } else if (!LogicServer.inMainCluster(level, core, pos)) {
            now = Status.NOT_CONNECTED;
        } else if (!PowerGrid.pay(level, pos, ImtConfig.Power.SERVER_RACK_UPKEEP)) {
            now = Status.NO_POWER;
        } else {
            now = Status.ONLINE;
        }
        if (now != status) {
            status = now;
            setChanged();
            LogicServer.forgetAll();
        }
        if (now == Status.NO_NETWORK || now == Status.NOT_CONNECTED) {
            // Hoort het bij geen server, dan rekent geen enkele kern voor dit rack: niets draait, het koelt af.
            heatStep(0, 0, 0);
        }
        boolean online = now == Status.ONLINE;
        if (online != state.getValue(ServerRackBlock.ONLINE)) {
            level.setBlock(pos, state.setValue(ServerRackBlock.ONLINE, online), Block.UPDATE_CLIENTS);
        }
    }

    // ================================================================ Temperatuur (8 oktober)

    public double temperature() {
        return temperature;
    }

    /** Voor de tests en de showcase: meteen deze temperatuur. */
    public void setTemperature(double temperature) {
        this.temperature = temperature;
        setChanged();
    }

    /** Warmte (watt) die dit rack nu maakt. */
    public double heat() {
        return heat;
    }

    /** Warmte (watt) als alle CPU's en GPU's in dit rack zo hard werken als ze kunnen. */
    public double fullHeat() {
        return fullHeat;
    }

    /** Koeling (watt): de fans in dit rack. */
    public double cooling() {
        return cooling;
    }

    /** Waar de temperatuur van dit rack naartoe gaat (oneindig: zonder fan wordt het steeds warmer). */
    public double target() {
        return Datacenter.target(heat, cooling);
    }

    /** Hoeveel fans er nog in dit rack moeten (zie Datacenter.fansNeeded). */
    public int fansNeeded() {
        return Datacenter.fansNeeded(fullHeat, cooling);
    }

    /** Eén stap van twee seconden met deze warmte en koeling (zie Datacenter.step). */
    void heatStep(double heat, double fullHeat, double cooling) {
        this.heat = heat;
        this.fullHeat = fullHeat;
        this.cooling = cooling;
        double before = temperature;
        temperature = Datacenter.step(temperature, heat, cooling);
        if (Math.abs(temperature - before) > 0.05) {
            setChanged();
        }
    }

    /** Hoe lang (ticks) het al aan één stuk boven FIRE_TEMPERATURE is (zie Datacenter). */
    int fireTicks() {
        return fireTicks;
    }

    void setFireTicks(int ticks) {
        if (ticks != fireTicks) {
            fireTicks = ticks;
            setChanged();
        }
    }

    // ================================================================ Brand (8 oktober)

    public boolean isBurning() {
        return burnUntil > 0;
    }

    /** Hoeveel seconden het nog brandt voor het weg is (0 = het brandt niet). */
    public int burnSecondsLeft(long now) {
        return burnUntil <= 0 ? 0 : (int) Math.max(1, (burnUntil - now + 19) / 20);
    }

    /** Het rack vat vlam (dat beslist Datacenter): vuur erop, en om {@code until} is het weg. */
    public void ignite(ServerLevel level, long until) {
        burnUntil = until;
        setChanged();
        lightFires(level, worldPosition);
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.2f, 0.7f);
    }

    /** Terwijl het brandt: vlammen en rook, en het vuur erop blijft branden. */
    private void burning(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        if (now % 4 == 0) {
            level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 4, 0.35, 0.4, 0.35, 0.01);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0.02);
        }
        if (now % 20 == 0) {
            level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0f, 0.8f + level.random.nextFloat() * 0.3f);
        }
        if (now % 40 == 0) {
            lightFires(level, pos);
        }
    }

    /** Vuur boven op het rack en ernaast (alleen waar lucht is en vuur kan blijven staan). */
    private static void lightFires(ServerLevel level, BlockPos pos) {
        for (Direction direction : new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos spot = pos.relative(direction);
            if (level.getBlockState(spot).isAir() && BaseFireBlock.canBePlacedAt(level, spot, direction)) {
                level.setBlockAndUpdate(spot, BaseFireBlock.getState(level, spot));
            }
        }
    }

    /**
     * Afgebrand: alles wat er nog in zit is weg (zat er een SSD in, dan zijn alle gegevens van de
     * server weg, zie Datacenter), en het rack zelf ook; op zijn plek brandt nog wat vuur.
     */
    public void burnDown(ServerLevel level) {
        BlockPos pos = worldPosition;
        boolean ssd = count(ServerPartItem.Kind.SSD) > 0;
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        parts.clearContent(); // verbrand: er valt niets uit
        burnUntil = 0;
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.4, 0.5, 0.4, 0.05);
        level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.8f, 1.3f);
        level.removeBlock(pos, false);
        if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
            level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
        }
        if (core != null) {
            core.datacenter().rackBurned(level, core, ssd);
        }
    }

    // ================================================================ Het scherm

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.imttech.server_rack");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ServerRackMenu(containerId, inventory, parts, data);
    }

    // ================================================================ Opslaan

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NonNullList<ItemStack> items = NonNullList.withSize(parts.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, parts.getItem(i));
        }
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putString("Status", status.name());
        if (burnUntil > 0) {
            tag.putLong("BurnUntil", burnUntil);
        }
        tag.putDouble("Temp", temperature);
        if (fireTicks > 0) {
            tag.putInt("FireTicks", fireTicks);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> items = NonNullList.withSize(parts.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int i = 0; i < items.size(); i++) {
            parts.setItem(i, items.get(i));
        }
        try {
            status = Status.valueOf(tag.getString("Status"));
        } catch (IllegalArgumentException e) {
            status = Status.NO_NETWORK;
        }
        burnUntil = tag.getLong("BurnUntil");
        // Oude racks (van voor 8 oktober) hebben nog geen eigen temperatuur: die beginnen koel.
        temperature = tag.contains("Temp") ? tag.getDouble("Temp") : ImtConfig.Logic.AMBIENT_TEMPERATURE;
        fireTicks = Math.max(0, tag.getInt("FireTicks"));
    }
}
