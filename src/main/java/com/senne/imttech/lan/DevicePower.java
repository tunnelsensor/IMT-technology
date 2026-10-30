package com.senne.imttech.lan;

import com.senne.imttech.energy.PowerGrid;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Stroom voor de kleine beveiligingsapparaten op het netwerk (hoofdstuk 38, jouw wens: "deze dingen hebben allemaal stroom
 * nodig, dus de keycardreader heeft een netwerkkabel én een powerkabel"): de keycardlezer, het codepaneel, de
 * netwerkknop en de netwerkhendel. (De switch betaalt zijn stroom zelf, zie NetworkSwitchBlockEntity.)
 *
 * Ze hebben geen eigen klok: de kern waar ze met een netwerkkabel aan hangen, betaalt om de twee seconden hun stroom (zie
 * SecurityCoreBlockEntity.powerTick), van hun eigen stroomnet (een stroomkabel ernaast, of straks een PoE-kabel). Gebruik
 * je ze, dan kijken we of er genoeg stroom op hun net staat.
 */
public final class DevicePower {
    private DevicePower() {}

    /** Een blok dat zo'n klein apparaat is: hoeveel stroom het elke twee seconden gebruikt (uit [power]). */
    public interface Powered {
        float upkeep();
    }

    /**
     * Heeft het apparaat op deze plek stroom? Er moet genoeg op zijn net staan voor twee seconden. Een apparaat dat (volgens
     * de config) niets gebruikt, heeft altijd stroom. Met {@code [network] required = false} (het oude gedrag, voor een oude
     * wereld) hebben lezers en panelen geen stroom nodig, zoals vroeger.
     */
    public static boolean hasPower(Level level, BlockPos pos) {
        if (!LanGrid.required() || !(level.getBlockState(pos).getBlock() instanceof Powered powered) || powered.upkeep() <= 0) {
            return true;
        }
        return PowerGrid.available(level, pos) >= Math.max(1.0, Math.ceil(powered.upkeep()));
    }

    /**
     * Heeft dit apparaat geen stroom? Dan hoort de speler (als die er is) een droog klikje en "No power", en geeft dit
     * true: de aanroeper doet dan verder niets.
     */
    public static boolean dead(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (hasPower(level, pos)) {
            return false;
        }
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.4f, 1.6f);
        if (player != null) {
            player.displayClientMessage(Component.translatable("logic.imttech.input.no_power").withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    /** Om de twee seconden: de stroom van deze apparaten betalen (ieder van zijn eigen net). Niet in het oude gedrag. */
    public static void payUpkeep(ServerLevel level, Iterable<BlockPos> devices) {
        if (!LanGrid.required()) {
            return;
        }
        for (BlockPos pos : devices) {
            if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof Powered powered && powered.upkeep() > 0) {
                PowerGrid.pay(level, pos, powered.upkeep());
            }
        }
    }
}
