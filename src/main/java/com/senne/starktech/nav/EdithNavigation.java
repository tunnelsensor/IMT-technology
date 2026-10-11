package com.senne.starktech.nav;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.WaypointActionPayload;
import com.senne.starktech.registry.ModAttachments;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Navigatie aan de serverkant: waypoints opslaan, verwijderen en ernaartoe navigeren.
 * De waypoints zelf worden bij de speler bewaard (ModAttachments.WAYPOINTS) en
 * automatisch naar zijn eigen spel gestuurd.
 */
public final class EdithNavigation {
    private EdithNavigation() {}

    public static void handleWaypointAction(ServerPlayer player, WaypointActionPayload payload) {
        if (EdithServer.authorizedGlasses(player) == null) {
            return;
        }
        EdithWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        switch (payload.op()) {
            case ADD -> {
                if (data.waypoints().size() >= StarkConfig.Navigation.MAX_WAYPOINTS) {
                    EdithServer.send(player, Component.translatable("edith.starktech.msg.waypoints_full", StarkConfig.Navigation.MAX_WAYPOINTS),
                            EdithMessagePayload.Level.WARNING);
                    return;
                }
                String name = cleanName(payload.name(), data.waypoints().size() + 1);
                Waypoint waypoint = new Waypoint(name, player.level().dimension(), player.blockPosition(), validColor(payload.color()));
                data = data.with(waypoint);
                EdithServer.send(player, Component.translatable("edith.starktech.msg.waypoint_saved", name), EdithMessagePayload.Level.INFO);
            }
            case REMOVE -> {
                if (payload.index() < 0 || payload.index() >= data.waypoints().size()) {
                    return;
                }
                String name = data.waypoints().get(payload.index()).name();
                data = data.without(payload.index());
                EdithServer.send(player, Component.translatable("edith.starktech.msg.waypoint_removed", name), EdithMessagePayload.Level.INFO);
            }
            case RENAME -> {
                if (payload.index() < 0 || payload.index() >= data.waypoints().size()) {
                    return;
                }
                Waypoint old = data.waypoints().get(payload.index());
                String name = cleanName(payload.name(), payload.index() + 1);
                data = data.replace(payload.index(),
                        new Waypoint(name, old.dimension(), old.pos(), validColor(payload.color())));
                EdithServer.send(player, Component.translatable("edith.starktech.msg.waypoint_renamed", name),
                        EdithMessagePayload.Level.INFO);
            }
            case NAVIGATE -> {
                int index = payload.index();
                if (index == EdithWaypoints.DEATH && player.getLastDeathLocation().isEmpty()) {
                    return;
                }
                data = data.withActive(index);
                Component target = targetName(data, index);
                EdithServer.send(player, target == null
                        ? Component.translatable("edith.starktech.msg.navigation_stopped")
                        : Component.translatable("edith.starktech.msg.navigating", target), EdithMessagePayload.Level.INFO);
            }
        }
        player.setData(ModAttachments.WAYPOINTS, data);
    }

    /** Eén keer per seconde: ben je bij je bestemming aangekomen? */
    public static void tick(ServerPlayer player) {
        EdithWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        if (data.active() == EdithWaypoints.NONE) {
            return;
        }
        Optional<GlobalPos> target = targetPos(player, data);
        if (target.isEmpty()) {
            player.setData(ModAttachments.WAYPOINTS, data.withActive(EdithWaypoints.NONE));
            return;
        }
        GlobalPos pos = target.get();
        double max = StarkConfig.Navigation.ARRIVAL_DISTANCE;
        if (pos.dimension() == player.level().dimension() && Vec3.atCenterOf(pos.pos()).distanceToSqr(player.position()) < max * max) {
            Component name = targetName(data, data.active());
            player.setData(ModAttachments.WAYPOINTS, data.withActive(EdithWaypoints.NONE));
            EdithServer.send(player, Component.translatable("edith.starktech.msg.arrived", name == null ? Component.empty() : name),
                    EdithMessagePayload.Level.BOOT);
        }
    }

    /** Waar ligt de huidige bestemming? */
    public static Optional<GlobalPos> targetPos(ServerPlayer player, EdithWaypoints data) {
        if (data.active() == EdithWaypoints.DEATH) {
            return player.getLastDeathLocation();
        }
        Waypoint waypoint = data.activeWaypoint();
        return waypoint == null ? Optional.empty() : Optional.of(GlobalPos.of(waypoint.dimension(), waypoint.pos()));
    }

    private static Component targetName(EdithWaypoints data, int index) {
        if (index == EdithWaypoints.DEATH) {
            return Component.translatable("edith.starktech.nav.death");
        }
        return index >= 0 && index < data.waypoints().size() ? Component.literal(data.waypoints().get(index).name()) : null;
    }

    /**
     * De naam van een wereld ("Overworld", "Nether", "End" of de eigen naam van een mod-wereld).
     * Staat hier, zodat zowel de server (rapporten) als je eigen spel (schermen) hem kan gebruiken.
     */
    public static Component dimensionName(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) {
            return Component.translatable("edith.starktech.dimension.overworld");
        }
        if (dimension == Level.NETHER) {
            return Component.translatable("edith.starktech.dimension.nether");
        }
        if (dimension == Level.END) {
            return Component.translatable("edith.starktech.dimension.end");
        }
        return Component.literal(dimension.location().getPath());
    }

    /** Maakt een naam netjes: geen opmaakcodes of rare tekens, niet te lang, nooit leeg. */
    private static String cleanName(String name, int number) {
        // "§c" enz. zijn opmaakcodes (kleuren); die halen we er in hun geheel uit.
        String clean = name.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", "").trim();
        if (clean.length() > StarkConfig.Navigation.MAX_WAYPOINT_NAME) {
            clean = clean.substring(0, StarkConfig.Navigation.MAX_WAYPOINT_NAME).trim();
        }
        return clean.isEmpty() ? "Waypoint " + number : clean;
    }

    private static int validColor(int color) {
        for (int allowed : Waypoint.COLORS) {
            if (allowed == color) {
                return color;
            }
        }
        return Waypoint.COLORS[0];
    }
}
