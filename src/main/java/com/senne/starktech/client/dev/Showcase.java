package com.senne.starktech.client.dev;

import com.senne.starktech.StarkTech;
import com.senne.starktech.client.EdithClientState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * De showcase: het spel laat alle functies één voor één zien, gestuurd vanuit je terminal.
 *
 * Starten doe je met {@code python tools/showcase.py}. Dat script start het spel met
 * {@code gradlew runShowcase}, en dan gebeurt dit:
 * 1. het spel maakt een platte wereld en zet alles klaar (ShowcaseScene);
 * 2. het script maakt verbinding (ShowcaseLink) en krijgt de lijst met functies;
 * 3. het script vraagt "laat functie 1 zien": het spel ruimt op, zet je op de goede plek en
 *    speelt het draaiboek af (ShowcaseFeatures);
 * 4. als dat klaar is zegt het spel "klaar", en het script wacht (als je dat wilde) tot jij op
 *    Enter drukt voor de volgende. Typ je tijdens een functie "s" (overslaan), "v" (vorige)
 *    of een ander commando, dan stopt het spel die functie meteen (SKIP).
 *
 * In een normaal spel doet deze klasse niets: hij staat alleen aan met
 * {@code -Dstarktech.showcase=true}, en dat doet alleen die ene Gradle-taak.
 */
public final class Showcase {
    private Showcase() {}

    private static final boolean ENABLED = Boolean.getBoolean("starktech.showcase");
    private static final String WORLD = "Showcase";

    private enum Stage { START, LOADING, SETUP, READY }

    private static final ShowcaseLink LINK = new ShowcaseLink();
    private static Stage stage = Stage.START;
    private static int ticksInWorld;
    private static final DevScript SETUP = new DevScript();
    private static boolean announced;

    /** De functie die nu speelt, en zijn draaiboek. */
    private static int current = -1;
    private static @Nullable DevScript playing;

    /**
     * Testmodus (tools/showcase.py --test): halverwege elke functie een screenshot, zodat
     * iemand achteraf kan nakijken of alles goed ging.
     */
    private static boolean shots;
    private static int playedTicks;
    private static int shotAtTick = -1;

    /** Welke meldingen van E.D.I.T.H. al naar je terminal gestuurd zijn. */
    private static final Set<Long> FORWARDED = new HashSet<>();

    public static void registerIfEnabled() {
        if (!ENABLED) {
            return;
        }
        StarkTech.LOGGER.info("[showcase] showcase staat aan");
        LINK.start();
        NeoForge.EVENT_BUS.addListener(Showcase::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        switch (stage) {
            case START -> {
                if (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen) {
                    // Met geluid: je wilt de repulsor en de stuwraketten horen.
                    DevWorld.quietOptions(mc, false);
                    DevWorld.createFlatWorld(mc, WORLD);
                    stage = Stage.LOADING;
                }
            }
            case LOADING -> {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.screen == null) {
                    if (++ticksInWorld > 40) {
                        buildSetup();
                        stage = Stage.SETUP;
                    }
                }
            }
            case SETUP -> {
                if (SETUP.tick()) {
                    stage = Stage.READY;
                    StarkTech.LOGGER.info("[showcase] wereld staat klaar");
                }
            }
            case READY -> run(mc);
        }
        // Heeft het script de verbinding verbroken (jij stopte), dan stopt het spel ook.
        if (LINK.wasDisconnected() && stage == Stage.READY) {
            StarkTech.LOGGER.info("[showcase] script weg, spel sluit af");
            mc.stop();
        }
    }

    /** De wereld inrichten; de Stark Tower pas als de stukken wereld daar geladen zijn. */
    private static void buildSetup() {
        SETUP.command(0, "time set noon")
                .command(0, "weather clear")
                .onServer(0, ShowcaseScene::build)
                .onServer(0, player -> ShowcaseScene.reset(player, net.minecraft.world.level.GameType.CREATIVE))
                .step(100, ShowcaseScene::placeTower)
                .onServer(0, player -> DevWorld.placeCrashSite(player.serverLevel(), ShowcaseScene.CRASH_SITE))
                // Even wachten: de bril start op, en de toren moet er staan.
                .pause(60);
    }

    private static void run(Minecraft mc) {
        List<ShowcaseFeatures.Feature> features = ShowcaseFeatures.all();
        if (LINK.isConnected() && !announced) {
            for (int i = 0; i < features.size(); i++) {
                ShowcaseFeatures.Feature feature = features.get(i);
                LINK.send("FEATURE", i + 1, feature.title(), feature.watch());
            }
            LINK.send("READY", features.size());
            announced = true;
        }
        String message;
        while ((message = LINK.poll()) != null) {
            String[] parts = message.split("\t");
            switch (parts[0]) {
                case "SHOW" -> start(features, Integer.parseInt(parts[1].trim()) - 1);
                case "SKIP" -> skip(Integer.parseInt(parts[1].trim()) - 1);
                case "SHOTS" -> shots = true;
                case "QUIT" -> {
                    StarkTech.LOGGER.info("[showcase] klaar, spel sluit af");
                    mc.stop();
                    return;
                }
                default -> StarkTech.LOGGER.warn("[showcase] onbekend bericht: {}", message);
            }
        }
        if (playing != null) {
            if (shots && ++playedTicks == shotAtTick) {
                String name = String.format(java.util.Locale.ROOT, "showcase_%02d", current + 1);
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
            }
            if (playing.tick()) {
                LINK.send("DONE", current + 1);
                playing = null;
            }
        }
        forwardNotifications();
    }

    /** Begint aan een functie: opruimen, op de goede plek zetten, titel, draaiboek. */
    private static void start(List<ShowcaseFeatures.Feature> features, int index) {
        if (index < 0 || index >= features.size()) {
            LINK.send("DONE", index + 1);
            return;
        }
        ShowcaseFeatures.Feature feature = features.get(index);
        current = index;
        DevScript script = new DevScript();
        script.step(0, ShowcaseFeatures::clientReset)
                .onServer(0, player -> ShowcaseScene.reset(player, feature.mode()))
                .stand(5, feature.from().x, feature.from().y, feature.from().z, feature.yaw(), feature.pitch())
                .look(2, feature.yaw(), feature.pitch())
                .title(5, feature.title(), (index + 1) + " / " + features.size());
        feature.demo().accept(script);
        playing = script;
        playedTicks = 0;
        // Het screenshot komt op 60% van de tijd: dan is er meestal het meeste te zien.
        shotAtTick = (int) (script.totalTicks() * 0.6);
        LINK.send("START", index + 1);
    }

    /**
     * Stopt de functie die nu speelt, omdat je in de terminal een commando typte (overslaan,
     * vorige, opnieuw...). De rest van het draaiboek wordt niet meer gedaan. Losgelaten toetsen en een dicht scherm, zodat je niet
     * blijft doorvliegen; de wereld zelf ruimt de volgende functie op (ShowcaseScene.reset).
     */
    private static void skip(int index) {
        // Was de functie net vanzelf klaar (DONE is al verstuurd), dan valt er niets te stoppen.
        if (playing == null || index != current) {
            return;
        }
        playing = null;
        ShowcaseFeatures.clientReset();
        LINK.send("DONE", current + 1);
    }

    /** Wat E.D.I.T.H. zegt, gaat ook naar je terminal (handig voor waarschuwingen). */
    private static void forwardNotifications() {
        for (EdithClientState.Notification notification : EdithClientState.NOTIFICATIONS) {
            long key = notification.startMillis() * 31 + notification.text().getString().hashCode();
            if (FORWARDED.add(key)) {
                LINK.send("SAY", notification.level().name(), notification.text().getString());
            }
        }
        if (FORWARDED.size() > 500) {
            FORWARDED.clear();
        }
    }
}
