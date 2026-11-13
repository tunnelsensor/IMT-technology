package com.senne.imttech.arsenal;

import com.senne.imttech.config.ImtConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Waar je drones uit hun baan je kunnen bereiken (hoofdstuk 106, jouw wens van 13 november: "drones kun je niet in
 * nether of end gebruiken tenzij je met een bepaalde drone, vb escort drones, naar daar gaat; dan blijft het aantal
 * drones dat je meenam voor elke andere drone actie beschikbaar").
 *
 * Je arsenaal hangt in een baan om de planeet: de gewone wereld. De Nether en het End zijn andere werelden, en daar
 * hangt niets boven. Een drone komt er alleen door een portaal, met jou mee:
 * <ul>
 *   <li>de drones die om je heen vliegen als je door het portaal stapt (je escorte en je zwerm) gaan mee;</li>
 *   <li>is zo'n drone daar klaar, dan vliegt hij niet naar zijn baan (die is er niet) maar wacht hij bij jou: in je
 *       arsenaal staat hij dan op "With you" ({@link ArsenalDrone.State#STANDBY});</li>
 *   <li>elke drone-actie (zwerm, losse drone, escorte, verkenner, spion, vervoer, vracht, illusie) neemt daar een drone
 *       die bij je wacht, in plaats van een uit je baan;</li>
 *   <li>ga je terug naar de gewone wereld, dan gaan ze mee en hangen ze weer in hun baan.</li>
 * </ul>
 * Deze klasse werkt op de server en in je eigen spel (het arsenaalscherm), want ze kijkt alleen naar welke wereld het is.
 */
public final class Orbit {
    private Orbit() {}

    /** Hangt er boven deze wereld een baan waar je drones uit kunnen komen? Niet boven de Nether en het End. */
    public static boolean reachable(Level level) {
        if (!ImtConfig.Drones.NO_ORBIT_IN_NETHER_AND_END) {
            return true; // de oude regel (config): je hele arsenaal, overal
        }
        ResourceKey<Level> dimension = level.dimension();
        return dimension != Level.NETHER && dimension != Level.END;
    }
}
