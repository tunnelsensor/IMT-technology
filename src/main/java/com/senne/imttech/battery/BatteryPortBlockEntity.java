package com.senne.imttech.battery;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.EnergySource;
import com.senne.imttech.energy.FeBridge;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Het brein van een Power Battery (hoofdstuk 47, jouw keuze: "zoals Mekanism", en "dit is vanaf dan de enige plek waar
 * je de stats van het stroomnet ziet"). Elke poort heeft er een; de <b>laagste poort</b> van een gevormde batterij is de
 * baas: die bewaart de stroom en meet het net. De andere poorten geven alles aan hem door, zodat een kabel aan eender
 * welke poort bij dezelfde stroom komt.
 * <ul>
 *   <li><b>Laden</b>: elke tick haalt de batterij wat generatoren op het net over hebben (boven de reserve die ze voor
 *       het net achterhouden, zie STATION_GRID_RESERVE), tot {@link ImtConfig.Power#BATTERY_CHARGE_RATE} per tick.</li>
 *   <li><b>Leveren</b>: hij is een leverancier zoals een generator (EnergySource): apparaten halen er stroom uit, en
 *       omdat hij meestal het meeste heeft, als eerste.</li>
 *   <li><b>Forge Energy</b>: een vreemde kabel vult hem (Receiving) of trekt hem leeg, en hij duwt zelf in vreemde
 *       machines ertegen (FeBridge.push).</li>
 *   <li><b>Meten</b>: elke seconde wat het hele net maakt, gebruikt en tekortkomt, elke 5 seconden een punt voor de
 *       grafiek van de laatste 5 minuten, en de lijst van generatoren en apparaten (zie {@link Batteries#stats}).</li>
 * </ul>
 * Valt de doos uit elkaar, dan blijft de stroom in de baas bewaard tot hij weer klopt (hooguit wat er dan in past).
 */
public class BatteryPortBlockEntity extends BlockEntity implements EnergySource, FeBridge.Receiving {
    /** Zoveel punten heeft de grafiek: elke 5 seconden één, dus 5 minuten. */
    public static final int HISTORY = 60;
    private static final int SAMPLE_TICKS = 100;
    private static final int MEASURE_TICKS = 20;
    private static final int CHECK_TICKS = 100;

    /** Eén soort apparaat (of generator) op het net: de naam, hoeveel ervan, en wat ze samen per tick doen. */
    public record Use(String name, int count, float perTick) {}

    private long energy;
    private BatteryStructure.@Nullable Structure structure;
    private String problem = "Not checked yet";
    private boolean dirty = true;
    private long nextCheck;

    // Meten (alleen de baas).
    private double drainedWindow;
    private double chargedWindow;
    private double foreignWindow;
    private double missedWindow;
    private int windowTicks;
    private float drainedPerTick;
    private float chargedPerTick;
    private float foreignPerTick;
    private float missedPerTick;
    private float gridMade;
    private float gridUsed;
    private float gridMissed;
    private int generatorCount;
    private List<Use> generators = List.of();
    private List<Use> devices = List.of();
    private final int[] historyMade = new int[HISTORY];
    private final int[] historyUsed = new int[HISTORY];
    private int historyAt;
    private int historyCount;
    private long nextSample;
    private long nextMeasure;

    public BatteryPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY_PORT.get(), pos, state);
    }

    // ================================================================ De vorm

    /** De doos moet opnieuw bekeken worden (een blok erbij of eraf). */
    public void invalidate() {
        dirty = true;
    }

    public BatteryStructure.@Nullable Structure structure() {
        return structure;
    }

    public boolean formed() {
        return structure != null;
    }

    /** Waarom het geen batterij is (leeg als hij klopt). */
    public String problem() {
        return formed() ? "" : problem;
    }

    /** De baas van deze batterij: de laagste poort (of deze poort zelf als er geen doos is). */
    public BatteryPortBlockEntity master() {
        if (structure != null && level != null && !structure.master().equals(worldPosition)
                && level.getBlockEntity(structure.master()) instanceof BatteryPortBlockEntity other && other.structure != null) {
            return other;
        }
        return this;
    }

    public boolean isMaster() {
        return master() == this;
    }

    public void revalidate(ServerLevel level) {
        BatteryStructure.Result result = BatteryStructure.find(level, worldPosition);
        boolean was = structure != null;
        structure = result.structure();
        problem = result.problem();
        dirty = false;
        if (structure != null) {
            // De baas bewaart de stroom: een poort die baas af is, geeft zijn stroom aan de nieuwe baas.
            BatteryPortBlockEntity master = master();
            if (master != this && energy > 0) {
                master.energy = Math.min(master.capacityLong(), master.energy + energy);
                energy = 0;
                master.setChanged();
            }
            energy = Math.min(energy, capacityLong());
        }
        if (was != (structure != null)) {
            PowerGrid.changed();
            setChanged();
        }
    }

    // ================================================================ Stroom

    private long capacityLong() {
        return structure == null ? 0 : structure.capacity();
    }

    /** Wat er in zit (de baas weet het). */
    public long stored() {
        return master().formed() ? master().energy : 0;
    }

    public long capacityStored() {
        return master().capacityLong();
    }

    @Override
    public int energy() {
        return (int) Math.min(Integer.MAX_VALUE, stored());
    }

    @Override
    public int capacity() {
        return (int) Math.min(Integer.MAX_VALUE, capacityStored());
    }

    @Override
    public int drain(int max) {
        BatteryPortBlockEntity master = master();
        if (!master.formed() || max <= 0) {
            return 0;
        }
        int taken = (int) Math.min(max, master.energy);
        if (taken > 0) {
            master.energy -= taken;
            master.drainedWindow += taken;
            master.setChanged();
        }
        return taken;
    }

    @Override
    public void missed(double amount) {
        master().missedWindow += amount;
    }

    @Override
    public double usedPerTick() {
        return master().drainedPerTick;
    }

    @Override
    public double missedPerTick() {
        return master().missedPerTick;
    }

    @Override
    public boolean measured() {
        return true;
    }

    /** Een vreemde kabel vult de batterij (Forge Energy). */
    @Override
    public int receiveFe(int amount, boolean simulate) {
        BatteryPortBlockEntity master = master();
        if (!master.formed()) {
            return 0;
        }
        int added = (int) Math.max(0, Math.min(amount, master.capacityLong() - master.energy));
        if (!simulate && added > 0) {
            master.energy += added;
            master.foreignWindow += added;
            master.setChanged();
        }
        return added;
    }

    @Override
    public int storedFe() {
        return energy();
    }

    @Override
    public int capacityFe() {
        return capacity();
    }

    /** Voor de showcase en de tests: zet er stroom in (tot wat erin past). */
    public void fill(long amount) {
        BatteryPortBlockEntity master = master();
        master.energy = Math.max(0, Math.min(master.capacityLong(), master.energy + amount));
        master.setChanged();
    }

    // ================================================================ Elke tick

    public void serverTick(ServerLevel level) {
        long now = level.getGameTime();
        if (dirty || now >= nextCheck) {
            revalidate(level);
            nextCheck = now + CHECK_TICKS;
        }
        if (!isMaster() || structure == null) {
            return;
        }
        List<EnergySource> sources = sources(level);
        charge(sources);
        for (BlockPos port : structure.ports()) {
            FeBridge.push(level, port, this);
        }
        windowTicks++;
        if (now >= nextMeasure) {
            measure(sources);
            nextMeasure = now + MEASURE_TICKS;
        }
        if (now >= nextSample) {
            sample(level, sources);
            nextSample = now + SAMPLE_TICKS;
        }
    }

    /** Alle leveranciers op het net van deze batterij (via al zijn poorten), zonder de batterij zelf, elk één keer. */
    private List<EnergySource> sources(ServerLevel level) {
        Set<EnergySource> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        List<EnergySource> result = new ArrayList<>();
        if (structure == null) {
            return result;
        }
        for (BlockPos port : structure.ports()) {
            for (EnergySource source : PowerGrid.sources(level, port)) {
                if (!(source instanceof BatteryPortBlockEntity) && seen.add(source)) {
                    result.add(source);
                }
            }
        }
        return result;
    }

    /** Laden met wat de generatoren over hebben (boven hun reserve), tot BATTERY_CHARGE_RATE per tick. */
    private void charge(List<EnergySource> sources) {
        long room = capacityLong() - energy;
        if (room <= 0) {
            return;
        }
        int budget = (int) Math.min(room, Math.max(0, Math.round(ImtConfig.Power.BATTERY_CHARGE_RATE)));
        for (EnergySource source : sources) {
            if (budget <= 0) {
                break;
            }
            long spare = source.energy() - (long) (source.capacity() * ImtConfig.Power.STATION_GRID_RESERVE);
            int take = (int) Math.min(spare, budget);
            if (take <= 0) {
                continue;
            }
            int got = source.drain(take);
            if (got > 0) {
                energy += got;
                chargedWindow += got;
                budget -= got;
                setChanged();
            }
        }
    }

    /** Elke seconde: wat het net maakt, gebruikt en tekortkomt (de generatoren meten zelf over 6 seconden). */
    private void measure(List<EnergySource> sources) {
        int ticks = Math.max(1, windowTicks);
        drainedPerTick = (float) (drainedWindow / ticks);
        chargedPerTick = (float) (chargedWindow / ticks);
        foreignPerTick = (float) (foreignWindow / ticks);
        missedPerTick = (float) (missedWindow / ticks);
        drainedWindow = 0;
        chargedWindow = 0;
        foreignWindow = 0;
        missedWindow = 0;
        windowTicks = 0;
        double made = foreignPerTick;
        double used = 0;
        double missed = missedPerTick;
        for (EnergySource source : sources) {
            made += source.madePerTick();
            used += source.usedPerTick();
            missed += source.missedPerTick();
        }
        // Wat de generatoren aan de batterij gaven is geen verbruik van een apparaat; wat apparaten uit de batterij
        // haalden wel.
        gridMade = (float) made;
        gridUsed = (float) Math.max(0, used - chargedPerTick + drainedPerTick);
        gridMissed = (float) missed;
        generatorCount = sources.size();
    }

    /** Elke 5 seconden: een punt voor de grafiek, en de lijsten van generatoren en apparaten. */
    private void sample(ServerLevel level, List<EnergySource> sources) {
        historyMade[historyAt] = hundredths(gridMade);
        historyUsed[historyAt] = hundredths(gridUsed);
        historyAt = (historyAt + 1) % HISTORY;
        historyCount = Math.min(HISTORY, historyCount + 1);
        // Generatoren per soort.
        Map<String, int[]> kinds = new LinkedHashMap<>();
        Map<String, Double> kindRates = new LinkedHashMap<>();
        for (EnergySource source : sources) {
            String name = source instanceof BlockEntity entity ? entity.getBlockState().getBlock().getName().getString() : "Another mod";
            kinds.computeIfAbsent(name, key -> new int[1])[0]++;
            kindRates.merge(name, source.madePerTick(), Double::sum);
        }
        List<Use> generatorList = new ArrayList<>();
        kinds.forEach((name, count) -> generatorList.add(new Use(name, count[0], kindRates.get(name).floatValue())));
        generators = generatorList;
        // Apparaten die de laatste tijd stroom haalden, en aan een poort van deze batterij hangen.
        Map<String, int[]> deviceCounts = new LinkedHashMap<>();
        Map<String, Double> deviceRates = new LinkedHashMap<>();
        for (PowerGrid.DeviceUse use : PowerGrid.recentDevices(level)) {
            if (!connected(level, use.device())) {
                continue;
            }
            String name = level.getBlockState(use.device()).getBlock().getName().getString();
            deviceCounts.computeIfAbsent(name, key -> new int[1])[0]++;
            deviceRates.merge(name, (double) use.perTick(), Double::sum);
        }
        List<Use> deviceList = new ArrayList<>();
        deviceCounts.forEach((name, count) -> deviceList.add(new Use(name, count[0], deviceRates.get(name).floatValue())));
        deviceList.sort((a, b) -> Float.compare(b.perTick(), a.perTick()));
        devices = deviceList.size() > 12 ? deviceList.subList(0, 12) : deviceList;
    }

    /** Hangt dit apparaat (via kabels) aan een poort van deze batterij? */
    private boolean connected(ServerLevel level, BlockPos device) {
        if (structure == null) {
            return false;
        }
        for (BlockPos source : PowerGrid.sourcePositionsOf(level, device)) {
            if (structure.ports().contains(source)) {
                return true;
            }
        }
        return false;
    }

    private static int hundredths(double perTick) {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(perTick * 100));
    }

    /**
     * Alleen voor de showcase en de screenshots (niet in het spel): vult de grafiek met vijf minuten nagemaakte cijfers,
     * een golf rond wat het net nu maakt en gebruikt (of vaste getallen als er nog niets gemeten is), zodat je niet vijf
     * minuten hoeft te wachten om te zien hoe de grafiek eruitziet.
     */
    public void tourHistory() {
        BatteryPortBlockEntity master = master();
        double made = master.gridMade > 0 ? master.gridMade : 124;
        double used = master.gridUsed > 0 ? master.gridUsed : 25;
        for (int i = 0; i < HISTORY; i++) {
            master.historyMade[i] = hundredths(made * (1.0 + 0.25 * Math.sin(i / 6.0)));
            master.historyUsed[i] = hundredths(used * (1.0 + 0.6 * Math.sin(i / 4.0 + 1.0)));
        }
        master.historyAt = 0;
        master.historyCount = HISTORY;
    }

    // ================================================================ Voor het scherm

    public float gridMade() {
        return master().gridMade;
    }

    public float gridUsed() {
        return master().gridUsed;
    }

    public float gridMissed() {
        return master().gridMissed;
    }

    public int generatorCount() {
        return master().generatorCount;
    }

    public List<Use> generators() {
        return master().generators;
    }

    public List<Use> devices() {
        return master().devices;
    }

    /** De grafiek van de baas, oud naar nieuw: wat het net maakte (honderdsten per tick). */
    public int[] historyMade() {
        return master().history(master().historyMade);
    }

    public int[] historyUsed() {
        return master().history(master().historyUsed);
    }

    private int[] history(int[] ring) {
        int[] out = new int[historyCount];
        for (int i = 0; i < historyCount; i++) {
            out[i] = ring[(historyAt - historyCount + i + HISTORY) % HISTORY];
        }
        return out;
    }

    // ================================================================ Opslaan

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Energy", energy);
        tag.putInt("EnergyUnits", 2);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, tag.getLong("Energy"));
        dirty = true;
    }
}
