package com.senne.imttech.client.zone;

import java.util.List;
import java.util.Set;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

/**
 * Hoofdstuk 99: de spelers van wie jouw spel de naam niet tekent, omdat ze in een beschermde zone staan waar jij niet mag
 * bouwen. Dat rekent de server uit (security/zone/ZoneNames) en stuurt het als entity-id's; jouw spel weet zelf niet van
 * wie een zone is.
 */
public final class HiddenNames {
    private HiddenNames() {}

    private static Set<Integer> hidden = Set.of();

    public static void set(List<Integer> players) {
        hidden = Set.copyOf(players);
    }

    /** Uitgelogd: niets meer onthouden (op een andere server zijn het andere nummers). */
    public static void clear() {
        hidden = Set.of();
    }

    /** Minecraft gaat een naam boven een hoofd tekenen: van een speler in een zone die niet van jou is, niet. */
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof Player player && hidden.contains(player.getId())) {
            event.setCanRender(TriState.FALSE);
        }
    }
}
