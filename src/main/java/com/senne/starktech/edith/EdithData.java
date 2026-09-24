package com.senne.starktech.edith;

import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.satellite.SatelliteMode;
import com.senne.starktech.registry.ModItems;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Hulpfuncties om de gegevens van een E.D.I.T.H.-bril te lezen en te veranderen.
 * Werkt zowel op de server als op de client.
 */
public final class EdithData {
    private EdithData() {}

    // ---------------------------------------------------------------- Bril zoeken

    public static boolean isGlasses(ItemStack stack) {
        return stack.is(ModItems.EDITH_GLASSES.get());
    }

    /** De bril die de speler op zijn hoofd heeft, of een leeg item als hij er geen draagt. */
    public static ItemStack getWornGlasses(Player player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        return isGlasses(head) ? head : ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- Energie

    public static int getEnergy(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ENERGY.get(), 0);
    }

    public static int getMaxEnergy(ItemStack stack) {
        return EdithBalance.BASE_CAPACITY + getUpgrades(stack).battery() * EdithBalance.CAPACITY_PER_BATTERY_UPGRADE;
    }

    public static void setEnergy(ItemStack stack, int energy) {
        stack.set(ModDataComponents.ENERGY.get(), Mth.clamp(energy, 0, getMaxEnergy(stack)));
    }

    /** Probeert energie te gebruiken. Geeft true terug als er genoeg was (en haalt het er dan af). */
    public static boolean tryUseEnergy(ItemStack stack, int amount) {
        int energy = getEnergy(stack);
        if (energy < amount) {
            return false;
        }
        setEnergy(stack, energy - amount);
        return true;
    }

    /** Voegt energie toe en geeft terug hoeveel er echt bij kon. */
    public static int addEnergy(ItemStack stack, int amount) {
        int energy = getEnergy(stack);
        int added = Math.min(amount, getMaxEnergy(stack) - energy);
        if (added > 0) {
            setEnergy(stack, energy + added);
        }
        return Math.max(added, 0);
    }

    public static float getEnergyFraction(ItemStack stack) {
        int max = getMaxEnergy(stack);
        return max <= 0 ? 0 : (float) getEnergy(stack) / max;
    }

    // ---------------------------------------------------------------- Eigenaar

    public static @Nullable EdithOwner getOwner(ItemStack stack) {
        return stack.get(ModDataComponents.OWNER.get());
    }

    public static void setOwner(ItemStack stack, @Nullable EdithOwner owner) {
        stack.set(ModDataComponents.OWNER.get(), owner);
    }

    /** Mag deze speler de functies van de bril gebruiken? Alleen de eigenaar mag dat. */
    public static boolean isAuthorized(ItemStack stack, Player player) {
        EdithOwner owner = getOwner(stack);
        return owner != null && owner.id().equals(player.getUUID());
    }

    // ---------------------------------------------------------------- Functies aan/uit

    public static int getFeatureMask(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.FEATURES.get(), EdithFeature.defaultMask());
    }

    public static boolean isEnabled(ItemStack stack, EdithFeature feature) {
        return (getFeatureMask(stack) & feature.bit()) != 0;
    }

    /** Zet een functie om (aan wordt uit, uit wordt aan) en geeft de nieuwe stand terug. */
    public static boolean toggle(ItemStack stack, EdithFeature feature) {
        int mask = getFeatureMask(stack) ^ feature.bit();
        stack.set(ModDataComponents.FEATURES.get(), mask);
        return (mask & feature.bit()) != 0;
    }

    // ---------------------------------------------------------------- Upgrades

    public static EdithUpgrades getUpgrades(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.UPGRADES.get(), EdithUpgrades.NONE);
    }

    public static void setUpgrades(ItemStack stack, EdithUpgrades upgrades) {
        stack.set(ModDataComponents.UPGRADES.get(), upgrades);
    }

    public static int getOreScanRadius(ItemStack stack) {
        return EdithBalance.ORE_SCAN_RADIUS[Mth.clamp(getUpgrades(stack).scanner(), 0, EdithBalance.ORE_SCAN_RADIUS.length - 1)];
    }

    public static int getThreatRadius(ItemStack stack) {
        return EdithBalance.THREAT_RADIUS[Mth.clamp(getUpgrades(stack).scanner(), 0, EdithBalance.THREAT_RADIUS.length - 1)];
    }

    /**
     * Hoeveel drones er in een zwerm zitten (met upgrades). De getallen komen uit EdithConfig,
     * zodat de eigenaar van de wereld het maximum zelf kan instellen.
     */
    public static int getSwarmSize(ItemStack stack) {
        int size = EdithConfig.swarmBase() + getUpgrades(stack).drones() * EdithConfig.swarmPerUpgrade();
        return Math.min(size, EdithConfig.swarmMax());
    }

    /** Hoeveel beschermende drones er rond de speler vliegen (met upgrades). */
    public static int getGuardCount(ItemStack stack) {
        int count = EdithConfig.guardBase() + getUpgrades(stack).drones() * EdithConfig.guardsPerUpgrade();
        return Math.min(count, EdithConfig.guardMax());
    }

    // ---------------------------------------------------------------- Satelliet-stand

    /** In welke stand de satelliet staat (precisie, bombardement of schuivende laser). */
    public static SatelliteMode getSatelliteMode(ItemStack stack) {
        return SatelliteMode.byId(stack.getOrDefault(ModDataComponents.SATELLITE_MODE.get(), 0));
    }

    public static void setSatelliteMode(ItemStack stack, SatelliteMode mode) {
        stack.set(ModDataComponents.SATELLITE_MODE.get(), mode.ordinal());
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
