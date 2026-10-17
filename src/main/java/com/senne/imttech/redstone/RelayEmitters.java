package com.senne.imttech.redstone;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.EmitterOpenPayload;
import com.senne.imttech.network.EmitterSavePayload;
import com.senne.imttech.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Het scherm van de relay emitter, aan de kant van de server (13 oktober): het openen, en wat je erin
 * kiest op de emitter zetten. Het scherm zelf staat in client/RelayEmitterScreen.
 *
 * Alleen de eigenaar (wie hem neerzette) mag hem instellen: hij schakelt relais uit JOUW lijst. Anderen
 * zien wel van wie hij is en wat hij doet. Een emitter zonder eigenaar (bv. met een commando neergezet)
 * wordt van wie hem het eerst opent.
 */
public final class RelayEmitters {
    private RelayEmitters() {}

    /** Zo dichtbij moet je staan (blokken), net als bij een lamp of stroomkast. */
    private static final double REACH = 6.0;

    /** Rechtsklik op de emitter: staat de speler dichtbij genoeg, dan opent zijn spel het scherm. */
    public static void open(ServerPlayer player, BlockPos pos) {
        RelayEmitterBlockEntity emitter = emitter(player, pos);
        if (emitter == null) {
            return;
        }
        if (emitter.owner() == null) {
            emitter.setOwner(player);
        }
        boolean mine = player.getUUID().equals(emitter.owner());
        if (mine) {
            emitter.refresh(player);
        }
        ModNetwork.sendTo(player, new EmitterOpenPayload(pos, emitter.target(), emitter.mode().ordinal(),
                emitter.ownerName(), mine, emitter.targets().size()));
    }

    /** "Save" in het scherm. */
    public static void handleSave(ServerPlayer player, EmitterSavePayload payload) {
        RelayEmitterBlockEntity emitter = emitter(player, payload.pos());
        if (emitter == null) {
            return;
        }
        if (!player.getUUID().equals(emitter.owner())) {
            player.displayClientMessage(Component.translatable("imt.imttech.msg.emitter_not_yours", emitter.ownerName()), true);
            return;
        }
        String target = ImtRedstone.clean(payload.target(), Math.round(ImtConfig.Relays.MAX_NAME), "");
        emitter.configure(player, target, RelayEmitterBlockEntity.Mode.of(payload.mode()));
        Component message = target.isEmpty()
                ? Component.translatable("imt.imttech.msg.emitter_cleared")
                : emitter.targets().isEmpty()
                        ? Component.translatable("imt.imttech.msg.emitter_unknown", target)
                        : Component.translatable("imt.imttech.msg.emitter_set", target, emitter.targets().size(),
                                Component.translatable(emitter.mode().key()));
        player.displayClientMessage(message, true);
    }

    /** De emitter waar deze speler bij kan, of null (er staat daar geen emitter, of hij staat te ver weg). */
    private static @Nullable RelayEmitterBlockEntity emitter(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH
                || !(player.serverLevel().getBlockEntity(pos) instanceof RelayEmitterBlockEntity emitter)) {
            return null;
        }
        return emitter;
    }
}
