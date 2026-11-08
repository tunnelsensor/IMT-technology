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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * De "binnenkant" van een serverrack: zijn acht vakjes (zie RackBay), zijn naam, wie hem neerzette, en of hij meedraait.
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
 * weg, met alles wat er dan nog in zit. Tot die tijd kun je je onderdelen eruit klikken.
 *
 * Hoofdstuk 64: wat er in de vakjes zit, staat ook in de blokstaat (bay_0 tot bay_7), zodat je het op de voorkant ziet;
 * na elke verandering werkt {@link #refresh} die bij. Een rack van voor 8 november had zes vakjes waar alles in mocht: bij
 * het laden gaat elk onderdeel naar een vakje waar het mag, en wat niet meer past valt eruit ({@link #dropOverflow}).
 */
public class ServerRackBlockEntity extends BlockEntity implements com.senne.imttech.security.BaseTrust.Placed {
    /** Waarom hij niet meedraait (voor de app Monitor). */
    public enum Status {
        ONLINE, NO_NETWORK, NOT_CONNECTED, NO_POWER
    }

    /** Hoe de vakjes liggen: 2 = de acht vakjes van hoofdstuk 64 (een rack zonder dit getal had er zes). */
    private static final int LAYOUT = 2;

    private final SimpleContainer parts = new SimpleContainer(ImtConfig.Logic.RACK_SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof ServerPartItem part && RackBay.accepts(slot, part.kind());
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

    /** De naam die je het rack gaf (sneak + rechtsklik), zoals hij in de app Monitor staat; leeg = nog geen naam. */
    private String name = "";
    /** Wie hem neerzette (hoofdstuk 64, voor de naam: zie security/BaseTrust), of null. */
    private @Nullable UUID owner;
    private String ownerName = "";
    /** Onderdelen van een oud rack (zes vakjes) die niet meer in een vakje passen: die vallen eruit bij de eerste tick. */
    private final List<ItemStack> overflow = new ArrayList<>();
    /** Moeten de vakjes nog nagekeken en de blokstaat bijgewerkt worden (na een verandering)? */
    private boolean dirtyBays = true;

    public ServerRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVER_RACK.get(), pos, state);
        parts.addListener(container -> {
            setChanged();
            LogicServer.forgetAll(); // de server telt opnieuw
            dirtyBays = true; // de voorkant bijwerken (bij de volgende tick, of meteen via refresh)
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

    /**
     * Zet een onderdeel in het eerste lege vakje waar het mag (voor de showcase, de rondleiding en de tests); geeft false als
     * er geen plek meer is. Eén stuk, ook als je een stapel geeft.
     */
    public boolean insert(ItemStack stack) {
        if (!(stack.getItem() instanceof ServerPartItem part)) {
            return false;
        }
        for (int slot = 0; slot < parts.getContainerSize(); slot++) {
            if (parts.getItem(slot).isEmpty() && RackBay.accepts(slot, part.kind())) {
                parts.setItem(slot, stack.copyWithCount(1));
                return true;
            }
        }
        return false;
    }

    /**
     * Na een verandering: een onderdeel dat in een verkeerd vakje zit (een oud rack, of code die het er zo in zette) gaat
     * naar een vakje waar het mag, of eruit; en de blokstaat laat zien wat er in elk vakje zit.
     */
    public void refresh() {
        dirtyBays = false;
        List<ItemStack> misplaced = new ArrayList<>();
        for (int slot = 0; slot < parts.getContainerSize(); slot++) {
            ItemStack stack = parts.getItem(slot);
            if (!stack.isEmpty() && !(stack.getItem() instanceof ServerPartItem part && RackBay.accepts(slot, part.kind()))) {
                misplaced.add(parts.removeItemNoUpdate(slot));
            }
        }
        for (ItemStack stack : misplaced) {
            if (!insert(stack)) {
                overflow.add(stack);
            }
        }
        if (!misplaced.isEmpty()) {
            setChanged();
            LogicServer.forgetAll();
        }
        dirtyBays = false; // insert hierboven zette het weer aan
        if (level != null && !level.isClientSide) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.getBlock() instanceof ServerRackBlock) {
                BlockState shown = state;
                for (int slot = 0; slot < parts.getContainerSize(); slot++) {
                    ItemStack stack = parts.getItem(slot);
                    RackBay bay = stack.getItem() instanceof ServerPartItem part ? RackBay.of(part.kind()) : RackBay.NONE;
                    shown = shown.setValue(ServerRackBlock.BAYS.get(slot), bay);
                }
                if (shown != state) {
                    level.setBlock(worldPosition, shown, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** De onderdelen die niet meer in een vakje passen (van een oud rack), naast het rack laten vallen. */
    public void dropOverflow(Level level) {
        if (overflow.isEmpty()) {
            return;
        }
        for (ItemStack stack : overflow) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack);
        }
        overflow.clear();
        setChanged();
    }

    // ================================================================ Naam en eigenaar (hoofdstuk 64)

    /** De naam die je het rack gaf, of "" (dan staat hij in de app Monitor met zijn plek). */
    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = InputDeviceBlockEntity.cleanName(name);
        setChanged();
    }

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

    // ================================================================ Elke twee seconden

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (dirtyBays) {
            refresh();
            state = level.getBlockState(pos);
        }
        if (!overflow.isEmpty()) {
            dropOverflow(level);
        }
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

    /**
     * Hoort hij bij een server, heeft hij stroom? Zet dat in het blok (en betaal de stroom). Sinds hoofdstuk 38 hoort hij
     * bij de kern aan het andere eind van zijn netwerkkabel (racks die tegen elkaar staan, delen die kabel).
     */
    public void update(ServerLevel level, BlockPos pos, BlockState state) {
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
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
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
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

    // ================================================================ Opslaan

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NonNullList<ItemStack> items = NonNullList.withSize(parts.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, parts.getItem(i));
        }
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Layout", LAYOUT);
        if (!overflow.isEmpty()) {
            net.minecraft.nbt.ListTag extra = new net.minecraft.nbt.ListTag();
            for (ItemStack stack : overflow) {
                extra.add(stack.save(registries));
            }
            tag.put("Overflow", extra);
        }
        if (!name.isEmpty()) {
            tag.putString("Name", name);
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
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
        // Een oud rack (zes vakjes, alles mocht overal): zijn vakjes 0 tot 5 komen gewoon binnen, en refresh zet bij de
        // eerste tick elk onderdeel in een vakje waar het mag (wat niet meer past, valt eruit).
        NonNullList<ItemStack> items = NonNullList.withSize(parts.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int i = 0; i < items.size(); i++) {
            parts.setItem(i, items.get(i));
        }
        overflow.clear();
        for (net.minecraft.nbt.Tag element : tag.getList("Overflow", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, element).ifPresent(overflow::add);
        }
        dirtyBays = true;
        name = tag.getString("Name");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
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
