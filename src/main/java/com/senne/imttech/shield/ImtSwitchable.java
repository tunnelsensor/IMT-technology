package com.senne.imttech.shield;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Een blok dat I.M.T aan en uit kan zetten (zie ImtRedstone).
 *
 * Het relais doet dat door zijn redstone-signaal om te zetten; de laserzender en de
 * schildkoepel doen er zelf nog iets bij (de wand maken, de koepel starten). Daarom
 * vraagt I.M.T het blok netjes zelf: "ga aan" of "ga uit".
 */
public interface ImtSwitchable {
    /** Zet dit blok aan of uit. */
    void setSwitched(ServerLevel level, BlockPos pos, BlockState state, boolean on);

    /** Staat het blok nu aan? */
    boolean isSwitchedOn(BlockState state);
}
