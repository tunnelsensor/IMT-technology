package com.senne.imttech.energy;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * De brug naar Forge Energy (hoofdstuk 46, jouw keuze: "herwerk alle stroom zodat het FE wordt, zoals in de andere
 * mods"). Sinds 4 november rekent alles in FE: een zonnepaneel maakt 24 FE per tick (zoals het zonnepaneel van Mekanism),
 * de bril houdt 100.000 FE, en elke kabel of machine van een andere mod (Create Crafts &amp; Additions, Mekanism...) kan
 * met onze blokken praten via de energie-capability van NeoForge:
 * <ul>
 *   <li>een <b>leverancier</b> (zonnepaneel, warmtegenerator, de batterij) laat zich leegtrekken ({@code extractEnergy})
 *       en duwt zelf elke tick FE in wat ertegen staat en het aanneemt (tot {@link ImtConfig.Power#FE_PUSH_RATE}); de
 *       batterij en het landingsplatform nemen ook FE aan ({@code receiveEnergy});</li>
 *   <li>elk ander blok van ons dat stroom gebruikt heeft een klein <b>buffertje</b> ({@link ImtConfig.Power#FE_DEVICE_BUFFER})
 *       dat een vreemde kabel kan vullen; het stroomnet (PowerGrid) gebruikt dat eerst, en dan pas de generatoren;</li>
 *   <li>en een vreemde leverancier naast een van onze blokken of kabels telt mee als bron van ons net (zie
 *       PowerGrid.look).</li>
 * </ul>
 * Onze eigen opslagen hebben het etiket {@link Own}: zo duwt een generator niet in het buffertje van zijn buurman, en telt
 * een buffertje niet als bron.
 */
public final class FeBridge {
    private FeBridge() {}

    /** Een energie-opslag van deze mod (geen vreemde). */
    public interface Own {}

    // ================================================================ Registreren

    /** Elk blok van ons met een blokentiteit krijgt de energie-capability: als leverancier, als opslag of met een buffertje. */
    public static void register(RegisterCapabilitiesEvent event) {
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            if (key == null || !key.getNamespace().equals(ImtTech.MODID)) {
                continue;
            }
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (entity, side) -> storageFor(entity));
        }
    }

    /** De opslag die bij deze blokentiteit hoort, of null als het blok niets met stroom doet. */
    private static @Nullable IEnergyStorage storageFor(BlockEntity entity) {
        if (entity instanceof EnergySource source) {
            return new SourceStorage(source, entity instanceof Receiving);
        }
        if (entity instanceof Receiving receiving) {
            return new ReceivingStorage(receiving);
        }
        if (entity instanceof PowerConnectable || entity.getBlockState().getBlock() instanceof PowerConnectable) {
            return new BufferStorage(entity);
        }
        return null;
    }

    /** Een blok van ons dat FE van een andere mod in zijn eigen opslag neemt (de batterij, het landingsplatform). */
    public interface Receiving {
        /** Neemt tot {@code amount} FE aan en geeft terug hoeveel het echt was. */
        int receiveFe(int amount, boolean simulate);

        int storedFe();

        int capacityFe();
    }

    // ================================================================ Leveranciers

    /** Een zonnepaneel, warmtegenerator of batterij, gezien door een andere mod: leeg te trekken, en soms ook te vullen. */
    private record SourceStorage(EnergySource source, boolean receives) implements IEnergyStorage, Own {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return receives && source instanceof Receiving receiving ? receiving.receiveFe(toReceive, simulate) : 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            int amount = Math.max(0, Math.min(toExtract, source.energy()));
            if (simulate || amount == 0) {
                return amount;
            }
            return source.drain(amount);
        }

        @Override
        public int getEnergyStored() {
            return source.energy();
        }

        @Override
        public int getMaxEnergyStored() {
            return source.capacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return receives;
        }
    }

    /** Een opslag van ons die alleen aanneemt (het landingsplatform). */
    private record ReceivingStorage(Receiving receiving) implements IEnergyStorage, Own {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return receiving.receiveFe(toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return receiving.storedFe();
        }

        @Override
        public int getMaxEnergyStored() {
            return receiving.capacityFe();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    // ================================================================ Het buffertje van een apparaat

    /** Per apparaat (wereld en plek) wat een vreemde kabel erin duwde. Niet bewaard: hooguit een paar seconden stroom. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Integer>> BUFFERS = new HashMap<>();

    /** Zoveel FE zit er in het buffertje van dit apparaat. */
    public static int buffered(Level level, BlockPos device) {
        Map<BlockPos, Integer> perLevel = BUFFERS.get(level.dimension());
        return perLevel == null ? 0 : perLevel.getOrDefault(device, 0);
    }

    /** Haalt tot {@code amount} FE uit het buffertje; geeft terug hoeveel het was. */
    public static int drainBuffer(Level level, BlockPos device, int amount) {
        Map<BlockPos, Integer> perLevel = BUFFERS.get(level.dimension());
        if (perLevel == null || amount <= 0) {
            return 0;
        }
        int stored = perLevel.getOrDefault(device, 0);
        int taken = Math.min(stored, amount);
        if (taken > 0) {
            if (stored - taken > 0) {
                perLevel.put(device.immutable(), stored - taken);
            } else {
                perLevel.remove(device);
            }
        }
        return taken;
    }

    private static int fillBuffer(Level level, BlockPos device, int amount, boolean simulate) {
        int max = Math.max(0, Math.round(ImtConfig.Power.FE_DEVICE_BUFFER));
        Map<BlockPos, Integer> perLevel = BUFFERS.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        int stored = perLevel.getOrDefault(device, 0);
        int added = Math.max(0, Math.min(amount, max - stored));
        if (!simulate && added > 0) {
            perLevel.put(device.immutable(), stored + added);
        }
        return added;
    }

    /** Het buffertje van een apparaat van ons, gezien door een vreemde kabel: alleen vullen. */
    private record BufferStorage(BlockEntity entity) implements IEnergyStorage, Own {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            Level level = entity.getLevel();
            return level == null ? 0 : fillBuffer(level, entity.getBlockPos(), toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            Level level = entity.getLevel();
            return level == null ? 0 : buffered(level, entity.getBlockPos());
        }

        @Override
        public int getMaxEnergyStored() {
            return Math.max(0, Math.round(ImtConfig.Power.FE_DEVICE_BUFFER));
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    // ================================================================ Vreemde leveranciers en afnemers

    /** De energie-opslag van een andere mod op deze plek (gezien vanaf {@code side}), of null (ook voor onze eigen opslagen). */
    public static @Nullable IEnergyStorage foreign(Level level, BlockPos pos, @Nullable Direction side) {
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        return storage == null || storage instanceof Own ? null : storage;
    }

    /** Een vreemde leverancier (een Create-accumulator, een Mekanism-energiekubus...) als bron van ons net. */
    public static @Nullable EnergySource foreignSource(Level level, BlockPos pos, @Nullable Direction side) {
        IEnergyStorage storage = foreign(level, pos, side);
        return storage != null && storage.canExtract() ? new ForeignSource(storage) : null;
    }

    private record ForeignSource(IEnergyStorage storage) implements EnergySource {
        @Override
        public int energy() {
            return storage.getEnergyStored();
        }

        @Override
        public int capacity() {
            return storage.getMaxEnergyStored();
        }

        @Override
        public int drain(int max) {
            return storage.extractEnergy(max, false);
        }
    }

    /**
     * Duwt FE uit een leverancier van ons in alles van een andere mod dat ertegen staat en het aanneemt (een kabel, een
     * machine), tot {@link ImtConfig.Power#FE_PUSH_RATE} per tick in totaal. Geeft terug hoeveel er wegging.
     */
    public static int push(Level level, BlockPos pos, EnergySource source) {
        int budget = Math.min(source.energy(), Math.max(0, Math.round(ImtConfig.Power.FE_PUSH_RATE)));
        int pushed = 0;
        for (Direction direction : Direction.values()) {
            if (budget - pushed <= 0) {
                break;
            }
            IEnergyStorage target = foreign(level, pos.relative(direction), direction.getOpposite());
            if (target == null || !target.canReceive()) {
                continue;
            }
            int accepted = target.receiveEnergy(budget - pushed, true);
            if (accepted <= 0) {
                continue;
            }
            int taken = source.drain(accepted);
            if (taken > 0) {
                pushed += target.receiveEnergy(taken, false);
            }
        }
        return pushed;
    }

    /** Bij het sluiten van de wereld: de buffertjes vergeten. */
    public static void clear() {
        BUFFERS.clear();
    }

    /** Voor de tests: een vreemde opslag op deze plek gevonden? */
    public static boolean hasForeign(Level level, GlobalPos pos) {
        return level.dimension().equals(pos.dimension()) && foreign(level, pos.pos(), null) != null;
    }
}
