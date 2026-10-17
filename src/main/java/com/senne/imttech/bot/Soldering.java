package com.senne.imttech.bot;

import com.senne.imttech.config.ImtConfig;
import net.minecraft.util.RandomSource;

/**
 * Het soldeerspelletje, aan de kant van de server (hoofdstuk 22). Je spel laat de hittebalk zien en vertelt
 * hoe lang je de muis vasthield; hier beslissen we wat er gebeurt (jouw antwoord: "met een klein spel waardoor
 * je een shortcut kunt maken"):
 * <ul>
 *   <li>te kort (onder {@code solderCold} van de balk): het soldeer pakt niet, de module blijft bij jou
 *       ({@link Joint#COLD});</li>
 *   <li>in het groene vak: een goede verbinding ({@link Joint#GOOD}), met een heel kleine kans toch een brug;</li>
 *   <li>te lang (boven {@code solderHot}): de module zit erop, maar meestal is het soldeer naar de pin ernaast
 *       gelopen: een soldeerbrug ({@link Joint#BRIDGED}). Dat zegt het spel je niet: je ziet het alleen als je
 *       goed kijkt (of met Inspect).</li>
 * </ul>
 * Zolang de robot uit staat, kan een brug geen kwaad: lossolderen en opnieuw. Zet je hem aan met een brug aan
 * een module, dan krijg je kortsluiting (zie GuardRobotEntity.shortCircuit).
 */
public final class Soldering {
    private Soldering() {}

    /** Hoe de verbinding werd. */
    public enum Joint { COLD, GOOD, BRIDGED }

    /** Wat er gebeurde: de verbinding, het nieuwe bord, en (bij een brug) met welke pin. */
    public record Result(Joint joint, BotBoard board, int bridgedWith) {}

    /** Hoe ver de hittebalk vol is na zoveel ticks vasthouden (0 tot 1, en meer als je te lang houdt). */
    public static double heat(int holdTicks) {
        return holdTicks / Math.max(1.0, ImtConfig.Robots.SOLDER_FULL_TICKS);
    }

    /** Soldeer {@code module} op pin {@code pin} (die leeg moet zijn), na zoveel ticks vasthouden. */
    public static Result solder(BotBoard board, int pin, BotModule module, int holdTicks, RandomSource random) {
        double heat = heat(holdTicks);
        if (heat < ImtConfig.Robots.SOLDER_COLD) {
            return new Result(Joint.COLD, board, 0);
        }
        BotBoard soldered = board.with(pin, module.id());
        double chance = heat <= ImtConfig.Robots.SOLDER_HOT ? ImtConfig.Robots.BRIDGE_CHANCE_GOOD
                : ImtConfig.Robots.BRIDGE_CHANCE_HOT;
        if (random.nextDouble() >= chance) {
            return new Result(Joint.GOOD, soldered, 0);
        }
        // Het soldeer loopt naar een buurpin (aan de rand van het bord is er maar één).
        int neighbour = pin == 1 ? 2 : pin == BotBoard.PINS ? BotBoard.PINS - 1 : random.nextBoolean() ? pin - 1 : pin + 1;
        return new Result(Joint.BRIDGED, soldered.withBridge(Math.min(pin, neighbour)), neighbour);
    }
}
