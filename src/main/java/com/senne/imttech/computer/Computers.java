package com.senne.imttech.computer;

import com.senne.imttech.lan.DevicePower;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Alles rond de Computer en de Monitor (hoofdstuk 40) dat niet in één blok past: welke computer bij een monitor hoort,
 * wat rechtsklikken doet, en een vrije naam voor een nieuwe computer.
 */
public final class Computers {
    private Computers() {}

    /** De computer waar deze monitor tegenaan staat (de eerste die we vinden, aan welke kant ook), of null. */
    public static @Nullable BlockPos computerNextTo(BlockGetter level, BlockPos monitor) {
        for (Direction direction : Direction.values()) {
            BlockPos next = monitor.relative(direction);
            if (level.getBlockState(next).getBlock() instanceof ComputerBlock) {
                return next;
            }
        }
        return null;
    }

    /** Licht het scherm van een monitor op deze plek op? Ja als hij tegen een computer staat die werkt. */
    public static boolean lit(BlockGetter level, BlockPos monitor) {
        BlockPos computer = computerNextTo(level, monitor);
        if (computer == null) {
            return false;
        }
        BlockState state = level.getBlockState(computer);
        return state.getBlock() instanceof ComputerBlock && state.getValue(ComputerBlock.ON);
    }

    /**
     * Waarom werkt de computer op deze plek niet? Null als hij werkt. (Engels, voor de actiebalk.)
     */
    public static @Nullable Component problem(ServerLevel level, BlockPos computer) {
        if (!(level.getBlockEntity(computer) instanceof ComputerBlockEntity entity)) {
            return Component.translatable("computer.imttech.msg.no_computer");
        }
        SecurityCoreBlockEntity core = entity.core(level);
        if (core == null) {
            return Component.translatable("computer.imttech.msg.no_network");
        }
        if (!core.isOnline()) {
            return Component.translatable("computer.imttech.msg.core_offline", core.hostname());
        }
        if (com.senne.imttech.weapons.Emp.isOut(level, computer)) {
            return Component.translatable("computer.imttech.msg.emp");
        }
        if (!DevicePower.hasPower(level, computer)) {
            return Component.translatable("computer.imttech.msg.no_power");
        }
        return null;
    }

    /** Rechtsklik op de kast: zijn naam, en aan welke kern hij hangt (of waarom hij niet werkt). */
    public static void status(ServerPlayer player, BlockPos computer) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(computer) instanceof ComputerBlockEntity entity)) {
            return;
        }
        Component problem = problem(level, computer);
        if (problem != null) {
            player.displayClientMessage(Component.literal(entity.name() + ": ").append(problem).withStyle(ChatFormatting.RED), true);
            return;
        }
        SecurityCoreBlockEntity core = entity.core(level);
        player.displayClientMessage(Component.translatable("computer.imttech.msg.status", entity.name(),
                core == null ? "?" : core.hostname(), core == null ? "?" : core.ip()).withStyle(ChatFormatting.AQUA), true);
    }

    /** Rechtsklik op een monitor: de desktop van zijn computer (of waarom dat niet gaat). */
    public static void use(ServerPlayer player, BlockPos monitor) {
        ServerLevel level = player.serverLevel();
        BlockPos computer = computerNextTo(level, monitor);
        if (computer == null) {
            player.displayClientMessage(Component.translatable("computer.imttech.msg.no_computer")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        Component problem = problem(level, computer);
        if (problem != null) {
            player.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        player.displayClientMessage(Component.translatable("computer.imttech.msg.not_set_up")
                .withStyle(ChatFormatting.YELLOW), true);
    }

    /** Een computer is weg (afgebroken): wie hem open had, krijgt hem dicht. */
    public static void gone(ServerLevel level, BlockPos computer) {
    }

    /** Een naam die nog geen andere computer van deze kern heeft: "Computer 1", "Computer 2", ... */
    public static String freeName(ServerLevel level, BlockPos pos) {
        Set<String> used = new HashSet<>();
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
        if (core != null) {
            for (BlockPos other : SecurityDevices.scan(level, core).computers()) {
                if (!other.equals(pos) && level.getBlockEntity(other) instanceof ComputerBlockEntity entity) {
                    used.add(entity.name());
                }
            }
        }
        int number = 1;
        while (used.contains("Computer " + number)) {
            number++;
        }
        return "Computer " + number;
    }
}
