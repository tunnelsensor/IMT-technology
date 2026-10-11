package com.senne.starktech.station;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.energy.EnergyCarry;
import com.senne.starktech.energy.EnergySource;
import com.senne.starktech.energy.StarkGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * De binnenkant van een lader: een zonnepaneel of een warmtegenerator.
 *
 * Allebei werken ze hetzelfde: ze maken energie en stoppen die in de bril die je erin legt.
 * Het verschil zit in wáár die energie vandaan komt, en dat regelt {@link #produce}.
 *
 * Wat ze opwekken kan ook het stroomnet op: apparaten die via Stark-kabels (of er direct
 * tegenaan) verbonden zijn, halen het hier weg (zie EnergySource en StarkGrid).
 *
 * Twee slots: 0 is de bril, 1 is brandstof (alleen de warmtegenerator doet daar iets mee).
 */
public abstract class ChargerBlockEntity extends BaseContainerBlockEntity implements EnergySource {
    public static final int SLOT_GLASSES = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_COUNT = 2;

    /** Nummers voor de gegevens die naar het scherm gestuurd worden. */
    public static final int DATA_ENERGY = 0;
    /**
     * Hoeveel het blok op dit moment oplevert, in honderdsten per tick (0.3 per tick wordt 30), zodat
     * het scherm ook kommagetallen kan tonen.
     */
    public static final int DATA_RATE = 1;
    /**
     * Hoeveel dit blok in totaal heeft opgewekt, in twee helften.
     *
     * Een scherm-getal past maar in 16 bits (tot 32767), en een totaal loopt veel hoger op.
     * Daarom sturen we het in twee stukken en plakt het menu ze weer aan elkaar.
     */
    public static final int DATA_TOTAL_LOW = 2;
    public static final int DATA_TOTAL_HIGH = 3;
    /**
     * Het hele stroomnet waar dit blok aan hangt, voor het overzicht naast het scherm. Elk getal in twee
     * helften (net als het totaal): wat alle generatoren samen maken, wat het net gebruikt, wat apparaten
     * tekortkomen (die drie in honderdsten per tick), wat erin zit en wat erin past.
     */
    public static final int DATA_GRID_MADE = 4;
    public static final int DATA_GRID_USED = 6;
    public static final int DATA_GRID_MISSED = 8;
    public static final int DATA_GRID_STORED = 10;
    public static final int DATA_GRID_CAPACITY = 12;
    /** Hoeveel generatoren er op het net hangen; 0 = we zijn nog aan het meten. */
    public static final int DATA_GRID_SOURCES = 14;
    public static final int DATA_COUNT = 15;

    /**
     * Zo lang (ticks) meten we voor het overzicht van het net: 6 seconden. Dan tellen ook apparaten die
     * maar om de één of twee seconden betalen (serverkast, kern, camera) eerlijk mee.
     */
    public static final int GRID_WINDOW = 120;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    /**
     * Hoeveel stroom erin zit. Een kommagetal, zodat ook 0.3 per tick opwekken werkt: het net haalt er
     * alleen hele stukken uit (zie {@link #energy()}), de rest groeit vanzelf aan.
     */
    private double energy;
    /** Wat we deze tick opwekten; puur om in het scherm te laten zien. */
    private double rate;
    /** Alles wat dit blok ooit heeft opgewekt (voor het tekstje bij de balk). */
    private double totalProduced;
    /** Het stukje van STATION_CHARGE_RATE dat nog geen hele stroom is (zie EnergyCarry). */
    private final EnergyCarry charge = new EnergyCarry();

    // Meten voor het overzicht van het net: wat er dit venster bij kwam, eruit ging en tekort was...
    private double windowMade;
    private double windowUsed;
    private double windowMissed;
    private int windowTicks;
    // ...en het gemiddelde per tick van het vorige venster.
    private double madePerTick;
    private double usedPerTick;
    private double missedPerTick;
    private boolean measured;
    /** Het hele net (zie DATA_GRID_...): maakt, gebruikt, tekort (honderdsten per tick), opgeslagen, past. */
    private final int[] grid = new int[5];
    private int gridSources;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> (int) energy;
                case DATA_RATE -> (int) Math.min(0xFFFF, Math.round(rate * 100));
                case DATA_TOTAL_LOW -> total() & 0xFFFF;
                case DATA_TOTAL_HIGH -> (total() >>> 16) & 0xFFFF;
                case DATA_GRID_SOURCES -> gridSources;
                default -> index >= DATA_GRID_MADE && index < DATA_GRID_SOURCES
                        ? (index % 2 == 0 ? grid[(index - DATA_GRID_MADE) / 2] & 0xFFFF
                                : (grid[(index - DATA_GRID_MADE) / 2] >>> 16) & 0xFFFF)
                        : 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_ENERGY -> energy = value;
                case DATA_RATE -> rate = value;
                case DATA_TOTAL_LOW -> totalProduced = (total() & ~0xFFFF) | (value & 0xFFFF);
                case DATA_TOTAL_HIGH -> totalProduced = (total() & 0xFFFF) | ((value & 0xFFFF) << 16);
                case DATA_GRID_SOURCES -> gridSources = value;
                default -> {
                    if (index >= DATA_GRID_MADE && index < DATA_GRID_SOURCES) {
                        int slot = (index - DATA_GRID_MADE) / 2;
                        grid[slot] = index % 2 == 0 ? (grid[slot] & ~0xFFFF) | (value & 0xFFFF)
                                : (grid[slot] & 0xFFFF) | ((value & 0xFFFF) << 16);
                    }
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    protected ChargerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Hoeveel energie dit blok deze tick opwekt.
     * Het zonnepaneel kijkt naar de zon, de warmtegenerator naar zijn brandstof en de hitte.
     */
    protected abstract double produce(ServerLevel level, BlockPos pos);

    /** Hoeveel dit blok kan opslaan. */
    public abstract int capacity();

    /** Hoeveel hele stroom erin zit (wat het net eruit kan halen). */
    @Override
    public int energy() {
        return (int) energy;
    }

    /** Het stroomnet haalt hier stroom weg (zie StarkGrid). */
    @Override
    public int drain(int max) {
        int taken = Math.min(max, energy());
        if (taken > 0) {
            energy -= taken;
            windowUsed += taken;
            setChanged();
        }
        return taken;
    }

    @Override
    public void missed(double amount) {
        windowMissed += amount;
    }

    @Override
    public double madePerTick() {
        return madePerTick;
    }

    @Override
    public double usedPerTick() {
        return usedPerTick;
    }

    @Override
    public double missedPerTick() {
        return missedPerTick;
    }

    @Override
    public boolean measured() {
        return measured;
    }

    /** Het hele net (voor de tests): wat alle generatoren samen per tick maken... */
    public float gridMadePerTick() {
        return grid[0] / 100.0f;
    }

    /** ...wat het net per tick gebruikt... */
    public float gridUsedPerTick() {
        return grid[1] / 100.0f;
    }

    /** ...en wat apparaten per tick tekortkomen. */
    public float gridMissedPerTick() {
        return grid[2] / 100.0f;
    }

    /** Stroom erbij zetten zonder dat hij die zelf opwekt (voor de tests). */
    public void fill(int amount) {
        energy = Math.min(capacity(), energy + amount);
        setChanged();
    }

    /** Alles wat dit blok ooit heeft opgewekt. */
    public int totalProduced() {
        return total();
    }

    /** Het totaal als heel getal (bij een paar miljard houdt het op; dat haalt niemand). */
    private int total() {
        return (int) Math.min(Integer.MAX_VALUE, totalProduced);
    }

    // ---------------------------------------------------------------- Elke tick (alleen server)

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        double produced = Math.max(0, produce(level, pos));
        double made = Math.max(0, Math.min(produced, capacity() - energy));
        if (made > 0) {
            energy += made;
            totalProduced += made;
        }
        rate = made;
        boolean changed = made > 0;
        changed |= chargeGlasses();
        if (changed) {
            setChanged();
        }
        measure(level, pos, produced);
    }

    /**
     * Voor het overzicht van het net: optellen wat hij kon maken (ook als hij vol zit), en na elk venster
     * het gemiddelde per tick bewaren. Elke seconde tellen we alle generatoren op het net bij elkaar op.
     */
    private void measure(ServerLevel level, BlockPos pos, double produced) {
        windowMade += produced;
        if (++windowTicks >= GRID_WINDOW) {
            madePerTick = windowMade / windowTicks;
            usedPerTick = windowUsed / windowTicks;
            missedPerTick = windowMissed / windowTicks;
            windowMade = 0;
            windowUsed = 0;
            windowMissed = 0;
            windowTicks = 0;
            measured = true;
        }
        if (measured && Math.floorMod(level.getGameTime() + pos.hashCode(), 20) == 0) {
            double made = madePerTick;
            double used = usedPerTick;
            double missed = missedPerTick;
            long stored = energy();
            long room = capacity();
            int count = 1;
            for (EnergySource other : StarkGrid.sources(level, pos)) {
                if (other == this) {
                    continue;
                }
                made += other.madePerTick();
                used += other.usedPerTick();
                missed += other.missedPerTick();
                stored += other.energy();
                room += other.capacity();
                count++;
            }
            grid[0] = hundredths(made);
            grid[1] = hundredths(used);
            grid[2] = hundredths(missed);
            grid[3] = (int) Math.min(Integer.MAX_VALUE, stored);
            grid[4] = (int) Math.min(Integer.MAX_VALUE, room);
            gridSources = count;
        }
    }

    private static int hundredths(double perTick) {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(perTick * 100));
    }

    /** Stuurt energie uit de buffer naar de bril in het slot. */
    private boolean chargeGlasses() {
        ItemStack glasses = items.get(SLOT_GLASSES);
        if (energy <= 0 || !EdithData.isGlasses(glasses)) {
            return false;
        }
        int give = Math.min(charge.take(Math.min(StarkConfig.Power.STATION_CHARGE_RATE, energy)), energy());
        int added = EdithData.addEnergy(glasses, give);
        energy -= added;
        windowUsed += added; // ook de bril in het vakje gebruikt stroom
        return added > 0;
    }

    // ---------------------------------------------------------------- Opslag

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> newItems) {
        items = newItems;
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        energy = tag.getDouble("Energy"); // ook goed voor een oude wereld, waar het nog een heel getal was
        totalProduced = tag.getDouble("TotalProduced");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putDouble("Energy", energy);
        tag.putDouble("TotalProduced", totalProduced);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new ChargerMenu(containerId, inventory, this, data, usesFuel());
    }

    /** Heeft dit blok iets aan brandstof? (Het zonnepaneel niet.) */
    public abstract boolean usesFuel();

    @Override
    protected abstract Component getDefaultName();
}
