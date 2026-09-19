package com.senne.starktech.client.drone;

import com.senne.starktech.drone.StarkDroneEntity;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Wat de renderer over een drone moet weten om hem te tekenen.
 */
public class DroneRenderState extends LivingEntityRenderState {
    /** Aanval (rode lampjes), bescherming (blauwe lampjes) of verkenner (witte lampjes). */
    public StarkDroneEntity.Mode mode = StarkDroneEntity.Mode.ATTACK;
}
