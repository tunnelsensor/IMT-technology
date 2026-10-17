package com.senne.imttech.nav;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.network.WaypointActionPayload;
import com.senne.imttech.registry.ModAttachments;
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
public final class ImtNavigation {
    private ImtNavigation() {}

    public static void handleWaypointAction(ServerPlayer player, WaypointActionPayload payload) {
        if (ImtServer.authorizedGlasses(player) == null) {
            return;
        }
        ImtWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        switch (payload.op()) {
            case ADD -> {
                if (data.waypoints().size() >= Math.round(ImtConfig.Navigation.MAX_WAYPOINTS)) {
                    ImtServer.send(player, Component.translatable("imt.imttech.msg.waypoints_full", Math.round(ImtConfig.Navigation.MAX_WAYPOINTS)),
                            ImtMessagePayload.Level.WARNING);
                    return;
                }
                String name = cleanName(payload.name(), data.waypoints().size() + 1);
                Waypoint waypoint = new Waypoint(name, player.level().dimension(), player.blockPosition(), validColor(payload.color()));
                data = data.with(waypoint);
                ImtServer.send(player, Component.translatable("imt.imttech.msg.waypoint_saved", name), ImtMessagePayload.Level.INFO);
            }
            case REMOVE -> {
                if (payload.index() < 0 || payload.index() >= data.waypoints().size()) {
                    return;
                }
                String name = data.waypoints().get(payload.index()).name();
                data = data.without(payload.index());
                ImtServer.send(player, Component.translatable("imt.imttech.msg.waypoint_removed", name), ImtMessagePayload.Level.INFO);
            }
            case RENAME -> {
                if (payload.index() < 0 || payload.index() >= data.waypoints().size()) {
                    return;
                }
                Waypoint old = data.waypoints().get(payload.index());
                String name = cleanName(payload.name(), payload.index() + 1);
                data = data.replace(payload.index(),
                        new Waypoint(name, old.dimension(), old.pos(), validColor(payload.color())));
                ImtServer.send(player, Component.translatable("imt.imttech.msg.waypoint_renamed", name),
                        ImtMessagePayload.Level.INFO);
            }
            case NAVIGATE -> {
                int index = payload.index();
                if (index == ImtWaypoints.DEATH && player.getLastDeathLocation().isEmpty()) {
                    return;
                }
                data = data.withActive(index);
                Component target = targetName(data, index);
                ImtServer.send(player, target == null
                        ? Component.translatable("imt.imttech.msg.navigation_stopped")
                        : Component.translatable("imt.imttech.msg.navigating", target), ImtMessagePayload.Level.INFO);
            }
        }
        player.setData(ModAttachments.WAYPOINTS, data);
    }

    /** Eén keer per seconde: ben je bij je bestemming aangekomen? */
    public static void tick(ServerPlayer player) {
        ImtWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        if (data.active() == ImtWaypoints.NONE) {
            return;
        }
        Optional<GlobalPos> target = targetPos(player, data);
        if (target.isEmpty()) {
            player.setData(ModAttachments.WAYPOINTS, data.withActive(ImtWaypoints.NONE));
            return;
        }
        GlobalPos pos = target.get();
        double max = ImtConfig.Navigation.ARRIVAL_DISTANCE;
        if (pos.dimension() == player.level().dimension() && Vec3.atCenterOf(pos.pos()).distanceToSqr(player.position()) < max * max) {
            Component name = targetName(data, data.active());
            player.setData(ModAttachments.WAYPOINTS, data.withActive(ImtWaypoints.NONE));
            ImtServer.send(player, Component.translatable("imt.imttech.msg.arrived", name == null ? Component.empty() : name),
                    ImtMessagePayload.Level.BOOT);
        }
    }

    /** Waar ligt de huidige bestemming? */
    public static Optional<GlobalPos> targetPos(ServerPlayer player, ImtWaypoints data) {
        if (data.active() == ImtWaypoints.DEATH) {
            return player.getLastDeathLocation();
        }
        Waypoint waypoint = data.activeWaypoint();
        return waypoint == null ? Optional.empty() : Optional.of(GlobalPos.of(waypoint.dimension(), waypoint.pos()));
    }

    private static Component targetName(ImtWaypoints data, int index) {
        if (index == ImtWaypoints.DEATH) {
            return Component.translatable("imt.imttech.nav.death");
        }
        return index >= 0 && index < data.waypoints().size() ? Component.literal(data.waypoints().get(index).name()) : null;
    }

    /**
     * De naam van een wereld ("Overworld", "Nether", "End" of de eigen naam van een mod-wereld).
     * Staat hier, zodat zowel de server (rapporten) als je eigen spel (schermen) hem kan gebruiken.
     */
    public static Component dimensionName(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) {
            return Component.translatable("imt.imttech.dimension.overworld");
        }
        if (dimension == Level.NETHER) {
            return Component.translatable("imt.imttech.dimension.nether");
        }
        if (dimension == Level.END) {
            return Component.translatable("imt.imttech.dimension.end");
        }
        return Component.literal(dimension.location().getPath());
    }

    /** Maakt een naam netjes: geen opmaakcodes of rare tekens, niet te lang, nooit leeg. */
    private static String cleanName(String name, int number) {
        // "§c" enz. zijn opmaakcodes (kleuren); die halen we er in hun geheel uit.
        String clean = name.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", "").trim();
        if (clean.length() > Math.round(ImtConfig.Navigation.MAX_WAYPOINT_NAME)) {
            clean = clean.substring(0, Math.round(ImtConfig.Navigation.MAX_WAYPOINT_NAME)).trim();
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
