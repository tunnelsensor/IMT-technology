package com.senne.imttech.battery;

import com.senne.imttech.network.BatteryStatsPayload;
import com.senne.imttech.network.ModNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Hulpjes voor de Power Battery (hoofdstuk 47): de poorten waarschuwen als er een blok bijkomt of weggaat, het scherm
 * openen vanaf eender welk blok van de batterij, en de stats voor dat scherm bij elkaar zoeken.
 */
public final class Batteries {
    private Batteries() {}

    /** Een batterijblok kwam erbij of ging weg: elke poort van die batterij kijkt opnieuw of de doos klopt. */
    public static void changed(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        for (BlockPos port : BatteryStructure.portsAround(level, pos)) {
            if (level.getBlockEntity(port) instanceof BatteryPortBlockEntity entity) {
                entity.invalidate();
            }
        }
    }

    /** De poort die bij dit blok hoort (de baas als de doos klopt), of null als er geen poort aan vastzit. */
    public static @Nullable BatteryPortBlockEntity portFor(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BatteryPortBlockEntity own) {
            return own.master();
        }
        for (BlockPos port : BatteryStructure.portsAround(level, pos)) {
            if (level.getBlockEntity(port) instanceof BatteryPortBlockEntity entity) {
                return entity.master();
            }
        }
        return null;
    }

    /** Rechtsklik op een blok van de batterij: het scherm openen (of zeggen dat er een poort ontbreekt). */
    public static void open(Level level, BlockPos pos, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BatteryPortBlockEntity port = portFor(level, pos);
        if (port == null) {
            serverPlayer.displayClientMessage(Component.translatable("battery.imttech.no_port").withStyle(ChatFormatting.RED), true);
            return;
        }
        ModNetwork.sendTo(serverPlayer, stats(serverLevel, port, true, serverPlayer));
    }

    /** Een scherm vraagt om nieuwe cijfers (elke seconde): sturen als de poort er nog is. */
    public static void refresh(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || player.distanceToSqr(pos.getCenter()) > 64 * 64) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof BatteryPortBlockEntity port) {
            ModNetwork.sendTo(player, stats(level, port.master(), false, player));
        }
    }

    /** Alles voor het scherm van {@code viewer}, uit de baas van de batterij. */
    public static BatteryStatsPayload stats(ServerLevel level, BatteryPortBlockEntity port, boolean open, ServerPlayer viewer) {
        BatteryStructure.Structure structure = port.structure();
        List<BatteryStatsPayload.Entry> generators = new ArrayList<>();
        for (BatteryPortBlockEntity.Use use : port.generators()) {
            generators.add(new BatteryStatsPayload.Entry(use.name(), use.count(), use.perTick()));
        }
        List<BatteryStatsPayload.Entry> devices = new ArrayList<>();
        for (BatteryPortBlockEntity.Use use : port.devices()) {
            devices.add(new BatteryStatsPayload.Entry(use.name(), use.count(), use.perTick()));
        }
        // Geen cellen: erbij hoeveel één cel houdt, zoals deze speler grote getallen wil zien (hoofdstuk 66).
        String problem = BatteryStructure.NO_CELLS.equals(port.problem())
                ? port.problem() + ": each cell stores " + com.senne.imttech.config.Numbers.amount(viewer,
                        com.senne.imttech.config.ImtConfig.Power.BATTERY_CELL_CAPACITY) + " FE"
                : port.problem();
        return new BatteryStatsPayload(port.getBlockPos(), open, structure != null, problem,
                structure == null ? "" : structure.size(), structure == null ? 0 : structure.cells(),
                port.stored(), port.capacityStored(), port.gridMade(), port.gridUsed(), port.gridMissed(),
                generators, devices, port.historyMade(), port.historyUsed());
    }
}
