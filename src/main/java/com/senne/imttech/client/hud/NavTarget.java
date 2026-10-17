package com.senne.imttech.client.hud;

import com.senne.imttech.client.NavigationScreen;
import com.senne.imttech.nav.ImtWaypoints;
import com.senne.imttech.nav.Waypoint;
import com.senne.imttech.registry.ModAttachments;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De bestemming waar je nu naartoe navigeert, klaar om te tekenen.
 *
 * Drie onderdelen van de HUD hebben hem nodig (het paneel links, het kompas en het ruitje in
 * de wereld), dus rekenen we hem één keer uit op een plek die ze alle drie kunnen vinden.
 */
public record NavTarget(Component name, Vec3 pos, int color, boolean sameDimension, Component dimensionName) {

    /** De huidige bestemming, of null als je nergens naartoe navigeert. */
    public static @Nullable NavTarget of(Player player) {
        ImtWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        if (data.active() == ImtWaypoints.DEATH) {
            Optional<GlobalPos> death = player.getLastDeathLocation();
            if (death.isEmpty()) {
                return null;
            }
            return new NavTarget(Component.translatable("imt.imttech.nav.death"), Vec3.atCenterOf(death.get().pos()),
                    HudStyle.RED, death.get().dimension() == player.level().dimension(),
                    NavigationScreen.dimensionName(death.get().dimension()));
        }
        Waypoint waypoint = data.activeWaypoint();
        if (waypoint == null) {
            return null;
        }
        return new NavTarget(Component.literal(waypoint.name()), Vec3.atCenterOf(waypoint.pos()), waypoint.color(),
                waypoint.dimension() == player.level().dimension(), NavigationScreen.dimensionName(waypoint.dimension()));
    }
}
