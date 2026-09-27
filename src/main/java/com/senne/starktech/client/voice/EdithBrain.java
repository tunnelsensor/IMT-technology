package com.senne.starktech.client.voice;

import com.senne.starktech.StarkTech;
import com.senne.starktech.client.EdithClientConfig;
import com.senne.starktech.client.EdithClientEvents;
import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.registry.ModSounds;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Het brein van E.D.I.T.H.: begrijpt gewone taal ("stuur de drones naar die zombie", "hoeveel
 * energie heb ik nog?") en beslist wat ze doet en zegt.
 *
 * Wat er gebeurt als je iets zegt (en het brein staat op LOCAL of CLAUDE):
 * 1. Op de hoofd-thread: de toestand van jou en je bril opschrijven (BrainPrompt.status).
 * 2. Op een eigen achtergrond-thread: de vraag stellen aan het lokale model of aan Claude. Dat
 *    duurt ± 1 seconde; het spel loopt gewoon door (in de HUD staat "THINKING...").
 * 3. Terug op de hoofd-thread: het antwoord uitvoeren (BrainDecision): zeggen, doen, onthouden.
 *
 * Lukt het denken niet (geen internet, geen sleutel, het model start niet), dan zoekt ze je zin
 * op in het woordenboek, zoals zonder brein. Het woordenboek is de snelle reserve.
 */
public final class EdithBrain {
    private EdithBrain() {}

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "E.D.I.T.H. brain");
        thread.setDaemon(true);
        return thread;
    });

    /** Zo vaak (ms) zegt ze hooguit dat het brein niet lukte; daarna valt ze stil terug. */
    private static final long FAILURE_NOTICE_MILLIS = 60_000;

    private static volatile boolean thinking;
    private static volatile boolean warmedUp;
    private static long lastFailureNotice;

    public static BrainMode mode() {
        return EdithClientConfig.SPEC.isLoaded() ? EdithClientConfig.BRAIN.get() : BrainMode.DICTIONARY;
    }

    public static void setMode(BrainMode mode) {
        if (!EdithClientConfig.SPEC.isLoaded()) {
            return;
        }
        EdithClientConfig.BRAIN.set(mode);
        EdithClientConfig.SPEC.save();
        EdithMemory.clearConversation();
        if (mode != BrainMode.LOCAL) {
            stopLocal(); // geheugen van de videokaart vrijmaken
        }
    }

    /** Kan het gekozen brein nu denken? (Het lokale model geïnstalleerd, of een sleutel voor Claude.) */
    public static boolean ready(BrainMode mode) {
        return switch (mode) {
            case DICTIONARY -> false;
            case LOCAL -> VoiceInstaller.state(VoiceFeature.BRAIN) == VoiceInstaller.State.INSTALLED;
            case CLAUDE -> ClaudeBrain.hasKey();
        };
    }

    public static boolean isThinking() {
        return thinking;
    }

    // ================================================================ Kiezen (het scherm AI Brain)

    /**
     * Kies een brein. Het lokale model wordt eerst gedownload als dat nog moet (de mod kiest
     * de maat bij je pc); voor Claude moet er eerst een sleutel zijn.
     */
    public static void choose(BrainMode mode) {
        switch (mode) {
            case DICTIONARY -> {
                setMode(BrainMode.DICTIONARY);
                modeSet(mode);
            }
            case CLAUDE -> {
                if (!ClaudeBrain.hasKey()) {
                    VoiceControl.tell(Component.translatable("edith.starktech.brain.needs_key"), EdithMessagePayload.Level.WARNING);
                    return;
                }
                setMode(BrainMode.CLAUDE);
                modeSet(mode);
            }
            case LOCAL -> chooseLocal();
        }
    }

    private static void chooseLocal() {
        if (VoiceInstaller.state(VoiceFeature.COMMANDS) != VoiceInstaller.State.INSTALLED) {
            VoiceControl.tell(Component.translatable("edith.starktech.brain.needs_ear"), EdithMessagePayload.Level.WARNING);
            return;
        }
        switch (VoiceInstaller.state(VoiceFeature.BRAIN)) {
            case UNSUPPORTED -> VoiceControl.tell(Component.translatable("edith.starktech.voice.unsupported"),
                    EdithMessagePayload.Level.WARNING);
            case INSTALLED -> {
                setMode(BrainMode.LOCAL);
                modeSet(BrainMode.LOCAL);
            }
            case INSTALLING -> VoiceControl.tell(Component.translatable("edith.starktech.voice.installing",
                    VoiceFeature.BRAIN.spokenName(), VoiceInstaller.percent(VoiceFeature.BRAIN)), EdithMessagePayload.Level.INFO);
            case NOT_INSTALLED, FAILED -> WORKER.execute(() -> {
                // Eerst kijken wat je pc aankan (dat start llama.cpp even), dan pas downloaden.
                BrainModel model = BrainModel.decide();
                post(() -> {
                    VoiceControl.tell(Component.translatable("edith.starktech.brain.install_started", model.size(),
                            VoiceFeature.BRAIN.missingSize()), EdithMessagePayload.Level.INFO);
                    VoiceInstaller.install(VoiceFeature.BRAIN, error -> {
                        if (error != null) {
                            VoiceControl.tell(Component.translatable("edith.starktech.voice.install_failed",
                                    VoiceFeature.BRAIN.spokenName(), error), EdithMessagePayload.Level.ALERT);
                            return;
                        }
                        setMode(BrainMode.LOCAL);
                        VoiceControl.tell(Component.translatable("edith.starktech.brain.install_done", model.size()),
                                EdithMessagePayload.Level.BOOT);
                    });
                });
            });
        }
    }

    private static void modeSet(BrainMode mode) {
        VoiceControl.tell(Component.translatable("edith.starktech.brain.mode_set", Component.translatable(mode.translationKey())),
                EdithMessagePayload.Level.INFO);
    }

    public static boolean hasClaudeKey() {
        return ClaudeBrain.hasKey();
    }

    /** De sleutel, maar alleen het begin en het eind (om te zien welke het is). */
    public static String maskedClaudeKey() {
        String key = ClaudeBrain.key();
        return key.length() <= 14 ? "*".repeat(key.length()) : key.substring(0, 7) + "..." + key.substring(key.length() - 4);
    }

    /** Bewaart de sleutel (leeg = wissen). Zonder sleutel kan Claude niet: dan terug naar het woordenboek. */
    public static void saveClaudeKey(String key) throws java.io.IOException {
        ClaudeBrain.setKey(key);
        if (key.isBlank() && mode() == BrainMode.CLAUDE) {
            setMode(BrainMode.DICTIONARY);
        }
    }

    // ================================================================ Elke tick

    static void tick(Minecraft mc) {
        // Het lokale model alvast opstarten (en opwarmen) als je de bril draagt: de eerste
        // vraag duurt anders een halve minuut.
        if (EdithClientState.wearing && mode() == BrainMode.LOCAL && ready(BrainMode.LOCAL) && !warmedUp) {
            warmedUp = true;
            WORKER.execute(() -> {
                try {
                    LocalBrain.ask(BrainPrompt.system(), List.of(), BrainPrompt.turn("STATUS\n(warming up)", List.of(), "hello"));
                } catch (Throwable t) {
                    StarkTech.LOGGER.warn("E.D.I.T.H.: could not warm up the local brain", t);
                }
            });
        }
        EdithInitiative.tick(mc);
    }

    // ================================================================ Wat je zei

    /**
     * Wat het oor hoorde (hoofd-thread). Geeft false terug als het brein het niet overneemt
     * (woordenboek gekozen, of het brein is nog niet klaar): dan doet het woordenboek het.
     */
    static boolean handle(String heard) {
        BrainMode mode = mode();
        if (!ready(mode) || thinking) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        String status = BrainPrompt.status(mc);
        String message = BrainPrompt.turn(status, EdithMemory.facts(), heard);
        List<EdithMemory.Turn> history = EdithMemory.recentTurns();
        // Was haar vorige beurt een vraag ("bedoelde je ...?")? Dan is dit het antwoord daarop.
        boolean confirming = !history.isEmpty() && wasQuestion(history.getLast());
        thinking = true;
        WORKER.execute(() -> {
            try {
                long start = System.currentTimeMillis();
                String json = ask(mode, history, message);
                BrainDecision decision = BrainDecision.parse(json);
                StarkTech.LOGGER.info("E.D.I.T.H. brain ({}, {} ms): \"{}\" -> {}", mode,
                        System.currentTimeMillis() - start, heard, json.replaceAll("\\s+", " "));
                post(() -> apply(heard, decision, confirming));
            } catch (ClaudeBrain.Failure failure) {
                StarkTech.LOGGER.warn("E.D.I.T.H.: Claude could not answer ({})", failure.reason, failure.getCause());
                post(() -> fallback(heard, "edith.starktech.brain.fail." + failure.reason));
            } catch (Throwable t) {
                StarkTech.LOGGER.warn("E.D.I.T.H.: the {} brain could not answer", mode, t);
                post(() -> fallback(heard, mode == BrainMode.CLAUDE ? "edith.starktech.brain.fail.api_error"
                        : "edith.starktech.brain.fail.local"));
            } finally {
                thinking = false;
            }
        });
        return true;
    }

    private static boolean wasQuestion(EdithMemory.Turn turn) {
        try {
            return BrainDecision.parse(turn.answer()).unsure();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String ask(BrainMode mode, List<EdithMemory.Turn> history, String message) throws Exception {
        return mode == BrainMode.CLAUDE
                ? ClaudeBrain.ask(BrainPrompt.system(), history, message)
                : LocalBrain.ask(BrainPrompt.system(), history, message);
    }

    /** Het antwoord uitvoeren: onthouden, zeggen, doen (hoofd-thread). */
    private static void apply(String heard, BrainDecision decision, boolean confirming) {
        if (!EdithClientState.authorized) {
            return; // bril afgezet terwijl ze nadacht
        }
        EdithMemory.addTurn(heard, decision.json());
        if (!decision.forget().isBlank()) {
            EdithMemory.forget(decision.forget());
        }
        if (!decision.remember().isBlank()) {
            EdithMemory.remember(decision.remember());
        }
        if (!decision.reply().isBlank()) {
            VoiceControl.tell(Component.literal(decision.reply()), EdithMessagePayload.Level.INFO);
            // Haar eigen antwoord zegt al wat er gebeurt: de gewone meldingen van de acties
            // tonen we wel, maar spreken we niet ook nog uit.
            EdithSpeech.holdInfoMessages(2500);
        } else if (decision.actions().isEmpty()) {
            EdithClientEvents.playUi(ModSounds.EDITH_BLIP.get(), 1.0f);
        }
        boolean quiet = decision.actions().size() > 1 || !decision.reply().isBlank();
        for (BrainDecision.Call call : decision.actions()) {
            VoiceControl.execute(call.action(), call.value(), quiet);
        }
        if (!decision.unsure() && !confirming) {
            VoiceLearning.learn(heard, decision.actions());
        }
    }

    /** Het brein lukte niet: zeggen waarom (niet te vaak), en dan het woordenboek. */
    private static void fallback(String heard, String reasonKey) {
        long now = System.currentTimeMillis();
        if (now - lastFailureNotice > FAILURE_NOTICE_MILLIS) {
            lastFailureNotice = now;
            VoiceControl.tell(Component.translatable(reasonKey), EdithMessagePayload.Level.WARNING);
        }
        VoiceControl.runDictionary(heard);
    }

    // ================================================================ Uit zichzelf

    /**
     * Iets dat ze uit zichzelf mag zeggen (EdithInitiative): een korte opmerking, nooit een
     * actie. Zegt het brein niets, dan blijft ze stil.
     */
    static void remark(String event) {
        BrainMode mode = mode();
        if (!ready(mode) || thinking || EdithAssistant.isBusy()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String message = BrainPrompt.event(BrainPrompt.status(mc), EdithMemory.facts(), event);
        thinking = true;
        WORKER.execute(() -> {
            try {
                BrainDecision decision = BrainDecision.parse(ask(mode, List.of(), message));
                StarkTech.LOGGER.info("E.D.I.T.H. brain ({}), on her own about \"{}\": \"{}\"", mode, event, decision.reply());
                if (!decision.reply().isBlank()) {
                    post(() -> {
                        if (EdithClientState.authorized) {
                            VoiceControl.tell(Component.literal(decision.reply()), EdithMessagePayload.Level.INFO);
                        }
                    });
                }
            } catch (Throwable t) {
                StarkTech.LOGGER.debug("E.D.I.T.H.: no remark ({})", t.toString());
            } finally {
                thinking = false;
            }
        });
    }

    // ================================================================ Opruimen

    static void stopLocal() {
        LocalBrain.SERVER.stop();
        warmedUp = false;
    }

    private static void post(Runnable task) {
        Minecraft.getInstance().execute(task);
    }
}
