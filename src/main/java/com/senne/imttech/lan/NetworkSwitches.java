package com.senne.imttech.lan;

import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.network.SwitchActionPayload;
import com.senne.imttech.network.SwitchOpenPayload;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wat je met een Network Switch doet (zie NetworkSwitchBlock): het deurtje openen, een poort omzetten (ook vanaf de
 * desktop, zie SecurityOs), en de naam van een nieuwe switch kiezen.
 */
public final class NetworkSwitches {
    private NetworkSwitches() {}

    /** Zo dichtbij moet je staan om het deurtje te openen en te schakelen (blokken), net als bij de stroomkast. */
    private static final double REACH = 6.0;

    /** Rechtsklik op de switch: staat de speler dichtbij genoeg, dan opent zijn spel het scherm. */
    public static void open(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH || !player.serverLevel().isLoaded(pos)
                || !(player.serverLevel().getBlockEntity(pos) instanceof NetworkSwitchBlockEntity entity)) {
            return;
        }
        ModNetwork.sendTo(player, new SwitchOpenPayload(pos, entity.name()));
        player.serverLevel().playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5f, 1.4f);
    }

    /**
     * Een knop uit het scherm: de poort aan één kant omzetten (wie ervoor staat, mag dat). Eerst de afstand, dan pas de
     * wereld: de plek komt uit een bericht van het spel van de speler, en een blok lezen in een stuk wereld dat niet
     * geladen is, laat de server dat stuk laden (gevonden bij hoofdstuk 113; dezelfde fout zat in het scherm van de
     * Router, hoofdstuk 112).
     */
    public static void handleAction(ServerPlayer player, SwitchActionPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof NetworkSwitchBlock)) {
            return;
        }
        Direction side = Direction.from3DDataValue(payload.side());
        setPort(level, pos, side, !NetworkSwitchBlock.isOn(state, side));
    }

    /** Zet de poort aan één kant aan of uit (vanaf het deurtje of de desktop). Het netwerk loopt daarna anders. */
    public static void setPort(ServerLevel level, BlockPos pos, Direction side, boolean on) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof NetworkSwitchBlock) || NetworkSwitchBlock.isOn(state, side) == on) {
            return;
        }
        level.setBlock(pos, state.setValue(NetworkSwitchBlock.PORTS.get(side), on), Block.UPDATE_ALL);
        PowerGrid.changed(); // ook het netwerk (en PoE-stroom gaat ook door de poorten)
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, on ? 1.3f : 1.1f);
        if (on) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5 + side.getStepX() * 0.55,
                    pos.getY() + 0.5 + side.getStepY() * 0.55, pos.getZ() + 0.5 + side.getStepZ() * 0.55, 4, 0.1, 0.1, 0.1, 0.05);
        }
    }

    /** "Switch 1", "Switch 2", ...: het eerste nummer dat op het netwerk van deze plek nog niet gebruikt is. */
    static String freeName(ServerLevel level, BlockPos pos) {
        Set<String> used = new HashSet<>();
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
        if (core != null) {
            for (BlockPos other : SecurityDevices.scan(level, core).switches()) {
                if (!other.equals(pos) && level.getBlockEntity(other) instanceof NetworkSwitchBlockEntity entity) {
                    used.add(entity.name());
                }
            }
        }
        int number = 1;
        while (used.contains("Switch " + number)) {
            number++;
        }
        return "Switch " + number;
    }
}
