package com.senne.imttech.shield;

import com.senne.imttech.network.LaserActionPayload;
import com.senne.imttech.network.LaserOpenPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.security.BaseTrust;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Het instelscherm van de laserbrug, aan de kant van de server (hoofdstuk 34): het scherm openen, en wat je erin kiest
 * op de zenders zetten. Het scherm zelf is dat van de laserzender (client/shield/LaserEmitterScreen), zonder de regels
 * die bij een brug niet horen (staand of liggend, draden herstellen). Het gebruikt dezelfde berichten
 * (LaserOpenPayload, LaserActionPayload); staat er op die plek een brug, dan komt het bericht hier terecht.
 *
 * Wie het mag openen: dezelfde regel als bij de laserzender (security/BaseTrust): wie de brug neerzette, of wie in
 * deze basis vertrouwd is. Wat je kiest (de kleur, aan of uit, de naam), geldt voor de hele brug: alle brugzenders die
 * eraan vastzitten, behalve die van iemand anders.
 */
public final class LaserBridges {
    private LaserBridges() {}

    /** Zo dichtbij moet je staan (blokken), net als bij de laserzender. */
    private static final double REACH = 6.0;

    /** Sneak + rechtsklik op de zender: mag je hem instellen, dan opent jouw spel het scherm. */
    public static void open(ServerPlayer player, BlockPos pos) {
        LaserBridgeBlockEntity bridge = bridge(player, pos);
        if (bridge == null) {
            return;
        }
        if (!mayEdit(player, bridge)) {
            String owner = BaseTrust.belongsTo(player.serverLevel(), bridge);
            player.displayClientMessage(owner.isEmpty()
                    ? Component.translatable("imt.imttech.msg.laser_bridge_locked")
                    : Component.translatable("imt.imttech.msg.laser_bridge_not_yours", owner), true);
            return;
        }
        ModNetwork.sendTo(player, new LaserOpenPayload(pos, bridge.ownerName(),
                com.senne.imttech.redstone.DeviceNames.nameOf(player, LaserBridgeBlock.group(player.serverLevel(), pos))));
    }

    /** Save in het scherm (hoofdstuk 39): de brug krijgt deze naam in de I.M.T-lijst van de speler. */
    public static void handleRename(ServerPlayer player, com.senne.imttech.network.LaserRenamePayload payload) {
        LaserBridgeBlockEntity bridge = bridge(player, payload.pos());
        if (bridge == null || !mayEdit(player, bridge)) {
            return;
        }
        LaserEmitters.renamed(player, com.senne.imttech.redstone.DeviceNames.rename(player,
                LaserBridgeBlock.group(player.serverLevel(), payload.pos()), payload.pos(), payload.name(),
                "imt.imttech.laser_bridge.default_name"), payload.name());
    }

    /** Is dit een brugzender waar deze speler bij kan? Dan gaat een knop uit het scherm naar {@link #handleAction}. */
    public static boolean isBridge(ServerPlayer player, BlockPos pos) {
        return bridge(player, pos) != null;
    }

    /** Een knop uit het scherm: de kleur of aan/uit, voor de hele brug. */
    public static void handleAction(ServerPlayer player, LaserActionPayload payload) {
        LaserBridgeBlockEntity clicked = bridge(player, payload.pos());
        if (clicked == null || !mayEdit(player, clicked)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<BlockPos> bridge = new ArrayList<>();
        for (BlockPos member : LaserBridgeBlock.group(level, payload.pos())) {
            if (level.getBlockEntity(member) instanceof LaserBridgeBlockEntity other && mayEdit(player, other)) {
                bridge.add(member);
            }
        }
        switch (payload.op()) {
            case COLOR -> LaserBridgeBlock.applyColour(level, bridge,
                    ShieldColor.values()[Math.floorMod(payload.value(), ShieldColor.values().length)]);
            case SWITCH -> {
                if (!bridge.isEmpty()) {
                    com.senne.imttech.redstone.ImtRedstone.switchAt(level, bridge.getFirst(), -1,
                            payload.value() != 0 ? com.senne.imttech.redstone.ImtRedstone.Op.ON : com.senne.imttech.redstone.ImtRedstone.Op.OFF, 0);
                }
            }
            case TURN, REPAIR -> { } // een brug ligt altijd plat, en heeft geen draden om te herstellen
        }
    }

    /** Mag deze speler de brug instellen? (Zie de uitleg bovenaan.) */
    public static boolean mayEdit(ServerPlayer player, LaserBridgeBlockEntity bridge) {
        return BaseTrust.mayEdit(player, bridge);
    }

    /** De brugzender waar deze speler bij kan, of null (er staat daar geen brugzender, of hij staat te ver weg). */
    private static @Nullable LaserBridgeBlockEntity bridge(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH
                || !(player.serverLevel().getBlockEntity(pos) instanceof LaserBridgeBlockEntity bridge)) {
            return null;
        }
        return bridge;
    }
}
