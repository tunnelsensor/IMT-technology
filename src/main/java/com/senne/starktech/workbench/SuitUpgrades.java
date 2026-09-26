package com.senne.starktech.workbench;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.starktech.registry.ModDataComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Welke upgrades er in één stuk harnas zitten (ingebouwd aan de harnas-werkbank).
 *
 * @param thrusters stuwraket-upgrades (alleen in de laarzen): sneller en zuiniger vliegen
 * @param plating   bepantsering (elk stuk): het harnas houdt meer schade tegen
 * @param capacitor repulsor-condensatoren (alleen in de borstplaat): een sterkere repulsor
 */
public record SuitUpgrades(int thrusters, int plating, int capacitor) {
    public static final SuitUpgrades NONE = new SuitUpgrades(0, 0, 0);

    public static final Codec<SuitUpgrades> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("thrusters", 0).forGetter(SuitUpgrades::thrusters),
            Codec.INT.optionalFieldOf("plating", 0).forGetter(SuitUpgrades::plating),
            Codec.INT.optionalFieldOf("capacitor", 0).forGetter(SuitUpgrades::capacitor)
    ).apply(instance, SuitUpgrades::new));

    public static final StreamCodec<ByteBuf, SuitUpgrades> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SuitUpgrades::thrusters,
            ByteBufCodecs.VAR_INT, SuitUpgrades::plating,
            ByteBufCodecs.VAR_INT, SuitUpgrades::capacitor,
            SuitUpgrades::new);

    /** De upgrades in dit stuk harnas (geen = NONE). */
    public static SuitUpgrades of(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SUIT_UPGRADES.get(), NONE);
    }

    public static void set(ItemStack stack, SuitUpgrades upgrades) {
        stack.set(ModDataComponents.SUIT_UPGRADES.get(), upgrades);
    }

    public boolean isEmpty() {
        return thrusters == 0 && plating == 0 && capacitor == 0;
    }

    /** Hoeveel stuwraket-upgrades er in de laarzen van deze speler zitten. */
    public static int thrustersOf(Player player) {
        return of(player.getItemBySlot(EquipmentSlot.FEET)).thrusters();
    }

    /** Hoeveel repulsor-condensatoren er in de borstplaat van deze speler zitten. */
    public static int capacitorsOf(Player player) {
        return of(player.getItemBySlot(EquipmentSlot.CHEST)).capacitor();
    }

    /** Alle bepantsering in alle gedragen stukken samen. */
    public static int platingOf(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().armor) {
            total += of(stack).plating();
        }
        return total;
    }
}
