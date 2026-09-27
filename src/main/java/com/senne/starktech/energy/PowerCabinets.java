package com.senne.starktech.energy;

import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.CabinetActionPayload;
import com.senne.starktech.network.CabinetOpenPayload;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.redstone.EdithRedstone;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wat je met een stroomkast doet (zie PowerCabinetBlock): het deurtje openen, een schakelaar
 * omzetten, en de schakelaars aanmelden bij E.D.I.T.H. zodat je ze in het redstone-tabblad ziet.
 */
public final class PowerCabinets {
    private PowerCabinets() {}

    /** Zo dichtbij moet je staan om het deurtje te openen en te schakelen (blokken). */
    private static final double REACH = 6.0;

    /** De naam van een kant, gezien vanaf het deurtje: voor, achter, links, rechts, boven, onder. */
    public static String sideKey(BlockState state, Direction side) {
        Direction front = state.getValue(PowerCabinetBlock.FACING);
        String relative;
        if (side == front) {
            relative = "front";
        } else if (side == front.getOpposite()) {
            relative = "back";
        } else if (side == front.getClockWise()) {
            relative = "left";
        } else if (side == front.getCounterClockWise()) {
            relative = "right";
        } else {
            relative = side == Direction.UP ? "top" : "bottom";
        }
        return "screen.starktech.cabinet.side." + relative;
    }

    /** Rechtsklik op de kast: staat de speler dichtbij genoeg, dan opent zijn spel het scherm. */
    public static void open(ServerPlayer player, BlockPos pos) {
        if (!(player.serverLevel().getBlockEntity(pos) instanceof PowerCabinetBlockEntity cabinet)
                || player.distanceToSqr(pos.getCenter()) > REACH * REACH) {
            return;
        }
        UUID owner = cabinet.ownerId(player.serverLevel());
        ModNetwork.sendTo(player, new CabinetOpenPayload(pos, player.getUUID().equals(owner)));
        player.serverLevel().playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    /** Een bericht van het scherm: een schakelaar omzetten, of de kast bij E.D.I.T.H. aanmelden. */
    public static void handleAction(ServerPlayer player, CabinetActionPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PowerCabinetBlock) || player.distanceToSqr(pos.getCenter()) > REACH * REACH) {
            return;
        }
        switch (payload.op()) {
            case TOGGLE -> {
                Direction side = Direction.from3DDataValue(payload.side());
                setSwitch(level, pos, side, !PowerCabinetBlock.isOn(state, side));
            }
            case LINK -> link(player, level, pos, state);
        }
    }

    /** Zet de schakelaar van één kant om (ook E.D.I.T.H. doet het zo, zie EdithRedstone). */
    public static void setSwitch(ServerLevel level, BlockPos pos, Direction side, boolean on) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PowerCabinetBlock) || PowerCabinetBlock.isOn(state, side) == on) {
            return;
        }
        level.setBlock(pos, state.setValue(PowerCabinetBlock.SWITCHES.get(side), on), Block.UPDATE_ALL);
        StarkGrid.changed();
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, on ? 0.8f : 0.6f);
        if (on) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5 + side.getStepX() * 0.55,
                    pos.getY() + 0.5 + side.getStepY() * 0.55, pos.getZ() + 0.5 + side.getStepZ() * 0.55,
                    6, 0.1, 0.1, 0.1, 0.1);
        }
    }

    /**
     * Meld de zes schakelaars aan bij E.D.I.T.H. (in het redstone-tabblad, in een eigen groep).
     * Alleen de eigenaar mag dat: op afstand schakelen is voor hem.
     */
    private static void link(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof PowerCabinetBlockEntity cabinet)
                || !player.getUUID().equals(cabinet.ownerId(level))) {
            EdithServer.send(player, Component.translatable("screen.starktech.cabinet.not_owner"), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (EdithServer.authorizedGlasses(player) == null) {
            EdithServer.send(player, Component.translatable("edith.starktech.msg.relay_needs_edith"), EdithMessagePayload.Level.WARNING);
            return;
        }
        String group = EdithRedstone.nextCabinetGroup(player);
        for (Direction side : Direction.values()) {
            String name = group + " " + Component.translatable(sideKey(state, side)).getString().toLowerCase(java.util.Locale.ROOT);
            EdithRedstone.registerChannel(player, pos, side.get3DDataValue(), name, group);
        }
        EdithServer.send(player, Component.translatable("screen.starktech.cabinet.linked", group), EdithMessagePayload.Level.INFO);
    }
}
