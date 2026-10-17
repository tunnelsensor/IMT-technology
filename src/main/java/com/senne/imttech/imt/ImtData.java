package com.senne.imttech.imt;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.satellite.SatelliteMode;
import com.senne.imttech.registry.ModItems;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Hulpfuncties om de gegevens van een I.M.T-bril te lezen en te veranderen.
 * Werkt zowel op de server als op de client.
 */
public final class ImtData {
    private ImtData() {}

    // ---------------------------------------------------------------- Bril zoeken

    public static boolean isGlasses(ItemStack stack) {
        return stack.is(ModItems.IMT_GLASSES.get());
    }

    /** De bril die de speler op zijn hoofd heeft, of een leeg item als hij er geen draagt. */
    public static ItemStack getWornGlasses(Player player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        return isGlasses(head) ? head : ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- Energie

    /** Hoeveel energie er in de bril zit, als heel getal (voor de HUD, de schermen en de laders). */
    public static int getEnergy(ItemStack stack) {
        return (int) getExactEnergy(stack);
    }

    /** Hoeveel energie er precies in de bril zit: een kommagetal, zodat ook 0.5 per seconde verbruiken werkt. */
    public static double getExactEnergy(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ENERGY.get(), 0.0);
    }

    public static int getMaxEnergy(ItemStack stack) {
        return (int) (ImtConfig.Glasses.BASE_CAPACITY
                + getUpgrades(stack).battery() * ImtConfig.Glasses.CAPACITY_PER_BATTERY_UPGRADE);
    }

    public static void setEnergy(ItemStack stack, double energy) {
        stack.set(ModDataComponents.ENERGY.get(), Mth.clamp(energy, 0, getMaxEnergy(stack)));
    }

    /**
     * Probeert energie te gebruiken (mag een kommagetal zijn, bv. 0.25). Geeft true terug als er genoeg
     * was (en haalt het er dan af).
     */
    public static boolean tryUseEnergy(ItemStack stack, double amount) {
        double energy = getExactEnergy(stack);
        if (energy < amount) {
            return false;
        }
        setEnergy(stack, energy - amount);
        return true;
    }

    /**
     * Voegt hele energie toe (uit een lader) en geeft terug hoeveel die lader kwijt is. Past er nog maar
     * een stukje bij (9999.5 van 10000), dan wordt de bril vol en kost dat de lader 1.
     */
    public static int addEnergy(ItemStack stack, int amount) {
        double energy = getExactEnergy(stack);
        double room = getMaxEnergy(stack) - energy;
        int added = (int) Math.min(amount, Math.ceil(room));
        if (added > 0) {
            setEnergy(stack, Math.min(getMaxEnergy(stack), energy + added));
        }
        return Math.max(added, 0);
    }

    public static float getEnergyFraction(ItemStack stack) {
        int max = getMaxEnergy(stack);
        return max <= 0 ? 0 : (float) (getExactEnergy(stack) / max);
    }

    // ---------------------------------------------------------------- Eigenaar

    public static @Nullable ImtOwner getOwner(ItemStack stack) {
        return stack.get(ModDataComponents.OWNER.get());
    }

    public static void setOwner(ItemStack stack, @Nullable ImtOwner owner) {
        stack.set(ModDataComponents.OWNER.get(), owner);
    }

    /** Mag deze speler de functies van de bril gebruiken? Alleen de eigenaar mag dat. */
    public static boolean isAuthorized(ItemStack stack, Player player) {
        ImtOwner owner = getOwner(stack);
        return owner != null && owner.id().equals(player.getUUID());
    }

    // ---------------------------------------------------------------- Functies aan/uit

    public static int getFeatureMask(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.FEATURES.get(), ImtFeature.defaultMask());
    }

    public static boolean isEnabled(ItemStack stack, ImtFeature feature) {
        return (getFeatureMask(stack) & feature.bit()) != 0;
    }

    /** Zet een functie om (aan wordt uit, uit wordt aan) en geeft de nieuwe stand terug. */
    public static boolean toggle(ItemStack stack, ImtFeature feature) {
        int mask = getFeatureMask(stack) ^ feature.bit();
        stack.set(ModDataComponents.FEATURES.get(), mask);
        return (mask & feature.bit()) != 0;
    }

    // ---------------------------------------------------------------- Upgrades

    public static ImtUpgrades getUpgrades(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.UPGRADES.get(), ImtUpgrades.NONE);
    }

    public static void setUpgrades(ItemStack stack, ImtUpgrades upgrades) {
        stack.set(ModDataComponents.UPGRADES.get(), upgrades);
    }

    public static int getOreScanRadius(ItemStack stack) {
        return Math.round(ImtConfig.Scanner.ORE_SCAN_RADIUS[Mth.clamp(getUpgrades(stack).scanner(), 0, ImtConfig.Scanner.ORE_SCAN_RADIUS.length - 1)]);
    }

    public static int getThreatRadius(ItemStack stack) {
        return Math.round(ImtConfig.Scanner.THREAT_RADIUS[Mth.clamp(getUpgrades(stack).scanner(), 0, ImtConfig.Scanner.THREAT_RADIUS.length - 1)]);
    }

    /**
     * Hoeveel drones er in een zwerm zitten (met upgrades). De getallen komen uit ImtConfig.Drones,
     * zodat de eigenaar van de wereld het maximum zelf kan instellen.
     */
    public static int getSwarmSize(ItemStack stack) {
        int size = Math.round(ImtConfig.Drones.SWARM_BASE) + getUpgrades(stack).drones() * Math.round(ImtConfig.Drones.SWARM_PER_UPGRADE);
        return Math.min(size, Math.round(ImtConfig.Drones.SWARM_MAX));
    }

    /** Hoeveel beschermende drones er rond de speler vliegen (met upgrades). */
    public static int getGuardCount(ItemStack stack) {
        int count = Math.round(ImtConfig.Drones.GUARD_BASE) + getUpgrades(stack).drones() * Math.round(ImtConfig.Drones.GUARDS_PER_UPGRADE);
        return Math.min(count, Math.round(ImtConfig.Drones.GUARD_MAX));
    }

    // ---------------------------------------------------------------- Satelliet-stand

    /** In welke stand de satelliet staat (precisie, bombardement of schuivende laser). */
    public static SatelliteMode getSatelliteMode(ItemStack stack) {
        return SatelliteMode.byId(stack.getOrDefault(ModDataComponents.SATELLITE_MODE.get(), 0));
    }

    public static void setSatelliteMode(ItemStack stack, SatelliteMode mode) {
        stack.set(ModDataComponents.SATELLITE_MODE.get(), mode.ordinal());
    }

    // ---------------------------------------------------------------- Formatie van de escorte

    /** In welke vorm de escortedrones om je heen vliegen. */
    public static com.senne.imttech.drone.DroneFormation getFormation(ItemStack stack) {
        return com.senne.imttech.drone.DroneFormation.byId(stack.getOrDefault(ModDataComponents.ESCORT_FORMATION.get(), 0));
    }

    public static void setFormation(ItemStack stack, com.senne.imttech.drone.DroneFormation formation) {
        stack.set(ModDataComponents.ESCORT_FORMATION.get(), formation.ordinal());
    }

    // ---------------------------------------------------------------- Samengevat

    /**
     * Werkt de bril op dit moment voor deze speler? Dat is zo als hij hem draagt,
     * de eigenaar is, en er nog energie in zit.
     */
    public static boolean isOperational(Player player) {
        ItemStack glasses = getWornGlasses(player);
        return !glasses.isEmpty() && isAuthorized(glasses, player) && getEnergy(glasses) > 0;
    }
}
