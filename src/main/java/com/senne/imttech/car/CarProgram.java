package com.senne.imttech.car;

import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.LogicGraph;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.Op;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.ScriptParser;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Het programma van een RC Car (hoofdstuk 108, jouw wens: "robots programmeren zoals in Education Edition, zodat die
 * kunnen helpen bij het uitmijnen en bouwen van je base").
 *
 * Het is dezelfde taal als die van je server, je robots en je drones, maar een eigen soort
 * ({@link ProgramKind#CAR}) met eigen woorden: rijden ({@code forward(3)}, {@code back(1)}, {@code turn_left()},
 * {@code turn_right()}), breken ({@code mine()}, {@code mine_up()}, {@code mine_down()}), plaatsen ({@code place()},
 * {@code place_up()}, {@code place_down()}), oprapen en lossen ({@code collect()}, {@code unload()}), en wat de auto
 * weet ({@code car.battery}, {@code car.blocked}, {@code block()}...). Sinds hoofdstuk 113 ook zijn arm
 * ({@code arm_up()}, {@code arm_down()}), kisten ({@code take("iron_ingot", 8)}, {@code put()}, {@code check()},
 * {@code if item("iron_ingot") in chest():}) en de bestellingen van een Storage Interface
 * ({@code when ordered("fetch"):}, {@code order.item}, {@code order.left}).
 *
 * Een programma bestaat uit twee soorten stukken:
 * <ul>
 *   <li>{@code when started:} is het hoofdprogramma: dat loopt als je op Start drukt;</li>
 *   <li>elke {@code def naam():} zonder parameters is een <b>functie</b>: een knop op het scherm van de auto en op de RC
 *       Controller (jouw wens: "functions die je via een controller kunt aansturen"). Een def mét parameters
 *       ({@code def lane(length: int):}) is een hulpje dat andere stukken gebruiken.</li>
 * </ul>
 * De auto bewaart alleen de code; bij het laden maken we er opnieuw blokjes van ({@link #compile}).
 */
public final class CarProgram {
    private CarProgram() {}

    /**
     * Waar een nieuwe RC Car mee begint: niets. Jouw wens: "ik wil dat je de rc car vanaf 0 moet programmeren; deze
     * functions kun je wel zelf schrijven, maar er mag geen voorbestaande code zijn". Zijn scherm zegt dan "No functions
     * yet", en de editor is leeg (met een paar grijze regels uitleg, die geen code zijn).
     */
    public static final String EMPTY = "";

    /**
     * Twee voorbeelden, alleen om te tonen in How it works (jouw wens: "in de how it works kun je dit wel als voorbeeld
     * geven") en om mee te testen; geen auto krijgt ze vanzelf. Het zijn jouw eigen voorbeelden: "herhaal: breek voor
     * jou, stap naar voor, breek boven jou... dit maakt dan een lange strip mine", en "een functie die een kamer van 5x3x5
     * uithakt".
     */
    public static final String EXAMPLE_STRIP_MINE = """
            def strip_mine():
                for i in range(20):
                    mine()
                    forward(1)
                    mine_up()
            """;

    /**
     * Een kamer van 5 breed, 3 hoog en 5 diep. De auto begint vóór de kamer (in de deuropening): de kamer komt voor hem
     * en rechts van hem, en hij eindigt weer waar hij begon. Sinds hoofdstuk 113 breekt hij de drie blokken hoog met zijn
     * arm ({@code dig}: breken, arm omhoog, breken, arm omhoog, breken, arm weer omlaag), want {@code mine_up(2)} bestaat
     * niet meer.
     */
    public static final String EXAMPLE_ROOM = """
            def dig():
                mine()
                arm_up()
                mine()
                arm_up()
                mine()
                arm_down(2)

            def room():
                dig()
                forward(1)
                for row in range(5):
                    for step in range(4):
                        dig()
                        forward(1)
                    back(4)
                    if row < 4:
                        turn_right()
                        dig()
                        forward(1)
                        turn_left()
                turn_left()
                forward(4)
                turn_right()
                back(1)
            """;

    /**
     * Het voorbeeld van hoofdstuk 113, ook alleen voor How it works en de tests: een auto die voor een Storage Interface
     * werkt. Hij staat thuis met zijn neus naar de interface; rechts van hem loopt een gang, met links van die gang een
     * rij kisten van drie hoog (hier vier stapels: {@code chests = 4}). Bij een bestelling rijdt hij de gang in, kijkt bij
     * elke stapel met zijn arm in de drie kisten, neemt wat hij nodig heeft, rijdt achteruit terug en legt het in de
     * interface. Opbergen gaat andersom: hij pakt het bij de interface en legt het in een kist waar dat item al ligt; weet
     * de interface nergens zo'n kist ({@code order.known}), dan in de eerste lege kist. Wat nergens past, houdt hij in
     * zijn kofferbak.
     */
    public static final String EXAMPLE_STORAGE = """
            chests = 4

            when ordered("fetch"):
                turn_right()
                steps = 0
                while steps < chests and order.left > 0:
                    forward(1)
                    steps = steps + 1
                    turn_left()
                    look()
                    turn_right()
                go_home(steps)
                if car.items > 0:
                    put()

            when ordered("store"):
                take(order.item, order.count)
                turn_right()
                steps = 0
                while steps < chests and trunk.count(order.item) > 0:
                    forward(1)
                    steps = steps + 1
                    turn_left()
                    stow()
                    turn_right()
                go_home(steps)
                if car.items > 0:
                    put()

            def go_home(steps: int):
                if steps > 0:
                    back(steps)
                turn_left()

            def look():
                grab()
                arm_up()
                grab()
                arm_up()
                grab()
                arm_down(2)

            def grab():
                if item(order.item) in chest():
                    take(order.item, order.left)

            def stow():
                drop()
                arm_up()
                drop()
                arm_up()
                drop()
                arm_down(2)

            def drop():
                if trunk.count(order.item) > 0:
                    if item(order.item) in chest() or (not order.known and chest.items == 0):
                        put(order.item)
            """;

    /**
     * Maakt van de code een programma. Klopt de code niet, dan krijg je een {@link LogicException} met het regelnummer
     * en uitleg in het Engels (dezelfde controle als de knop Check in de editor).
     */
    public static LogicProgram compile(String source) {
        // parse() doet de strenge controle (ScriptChecker) zelf al, en gooit bij de eerste fout.
        return new LogicProgram("RC Car", true, GraphConverter.toGraph(ScriptParser.parse(source, ProgramKind.CAR), null),
                source, ProgramKind.CAR);
    }

    /** Zoals {@link #compile}, maar geeft null als de code niet klopt (een oude auto na een update van de taal). */
    public static @Nullable LogicProgram compileOrNull(String source) {
        try {
            return compile(source);
        } catch (LogicException e) {
            return null;
        }
    }

    /** Wat er mis is met deze code ("Line 3: ..."), of null als hij klopt. */
    public static @Nullable String problem(String source) {
        try {
            compile(source);
            return null;
        } catch (LogicException e) {
            return e.describe();
        }
    }

    /**
     * De functies van dit programma: de namen van de defs zonder parameters, in de volgorde van de code. Dat zijn de
     * knoppen op de RC Controller.
     */
    public static List<String> functions(@Nullable LogicProgram program) {
        List<String> names = new ArrayList<>();
        if (program == null) {
            return names;
        }
        List<LogicGraph.Node> nodes = new ArrayList<>(program.graph().nodes());
        nodes.sort(java.util.Comparator.comparingInt(node -> node.id));
        for (LogicGraph.Node node : nodes) {
            if (node.op == Op.DEF && node.expr.isBlank() && !node.shared) {
                names.add(node.text);
            }
        }
        return names;
    }

    /** Heeft dit programma een hoofdprogramma ({@code when started:})? Dan heeft de knop Start iets te doen. */
    public static boolean hasMain(@Nullable LogicProgram program) {
        return program != null && program.graph().nodes().stream().anyMatch(node -> node.op == Op.STARTED);
    }
}
