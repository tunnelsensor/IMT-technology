package com.senne.starktech.light;

import com.senne.starktech.network.LampActionPayload;
import com.senne.starktech.network.LampOpenPayload;
import com.senne.starktech.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Het instelscherm van de Arc Lamp, aan de kant van de server (10 oktober: "zorg dat je de brightness kunt
 * instellen"): het scherm openen, en wat je erin verandert op de lamp zetten. Het scherm zelf staat in
 * client/light/ArcLampScreen.
 *
 * Iedereen die bij de lamp staat, mag hem instellen (net als een hendel of de stroomkast).
 */
public final class ArcLamps {
    private ArcLamps() {}

    /** Zo dichtbij moet je staan (blokken), net als bij de stroomkast. */
    private static final double REACH = 6.0;

    /** Rechtsklik op de lamp: staat de speler dichtbij genoeg, dan opent zijn spel het scherm. */
    public static void open(ServerPlayer player, BlockPos pos) {
        if (lamp(player, pos) != null) {
            ModNetwork.sendTo(player, new LampOpenPayload(pos));
        }
    }

    /** Een schuifje uit het scherm. */
    public static void handleAction(ServerPlayer player, LampActionPayload payload) {
        ArcLampBlockEntity lamp = lamp(player, payload.pos());
        if (lamp == null) {
            return;
        }
        switch (payload.op()) {
            case BRIGHTNESS -> lamp.setBrightness(payload.value());
        }
    }

    /** De lamp waar deze speler bij kan, of null (er staat daar geen lamp, of hij staat te ver weg). */
    private static @Nullable ArcLampBlockEntity lamp(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH
                || !(player.serverLevel().getBlockEntity(pos) instanceof ArcLampBlockEntity lamp)) {
            return null;
        }
        return lamp;
    }
}
