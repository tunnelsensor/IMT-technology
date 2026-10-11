package com.senne.starktech.arsenal;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Het schouwspel bij het lanceren: de drone die je net gebouwd hebt schiet voor je ogen de
 * lucht in. In het spel is dat alles wat er gebeurt - dat hij "in een baan" hangt is een
 * afspraak, geen echte plek in de wereld.
 */
public final class ArsenalLaunch {
    private ArsenalLaunch() {}

    /** Laat een drone vlak voor de speler opstijgen en de lucht uit vliegen. */
    public static void spawn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        StarkDroneEntity drone = ModEntities.STARK_DRONE.get().create(level);
        if (drone == null) {
            return;
        }
        drone.setup(player, StarkDroneEntity.Mode.LAUNCH, 0, Math.round(StarkConfig.Drones.LAUNCH_TICKS));
        // Twee blokken voor je, op ooghoogte: je ziet hem wegschieten in plaats van uit je hoofd.
        double x = player.getX() + player.getViewVector(1.0f).x * 2.0;
        double z = player.getZ() + player.getViewVector(1.0f).z * 2.0;
        drone.moveTo(x, player.getEyeY(), z, player.getYRot(), 0.0f);
        level.addFreshEntity(drone);
    }
}
