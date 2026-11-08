package com.senne.imttech.howto;

import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Eén "How it works"-animatie (hoofdstuk 63): een draaiboek van cues op een tijdlijn, met hoofdstukjes (waar je met de
 * knoppen naartoe springt), een grondplaat van {@code sizeX} x {@code sizeZ} blokken, en hoe de camera begint.
 * Maak er een met {@link HowToSceneBuilder}.
 *
 * @param id       uniek, bv. "power_battery"; ook het begin van de sleutels van de teksten
 * @param height   hoe hoog het wereldje wordt (het hoogste blok + 1), zodat de camera niet verspringt
 * @param length   hoe lang de animatie duurt, in ticks
 * @param cues     alle cues, op volgorde van hun tick
 * @param chapters de hoofdstukjes, op volgorde (het eerste begint op tick 0)
 */
public record HowToScene(String id, Component title, int sizeX, int sizeZ, int height, float yaw, float pitch,
                         int length, List<HowToCue> cues, List<Chapter> chapters) {

    /** Een hoofdstukje: vanaf welke tick, en hoe het heet. */
    public record Chapter(int tick, Component title) {}

    /** Het hoofdstukje waarin tick {@code tick} valt (-1 als er geen is). */
    public int chapterIndexAt(int tick) {
        int found = -1;
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).tick() <= tick) {
                found = i;
            }
        }
        return found;
    }
}
