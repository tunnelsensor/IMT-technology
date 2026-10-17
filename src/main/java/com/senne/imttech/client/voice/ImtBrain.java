package com.senne.imttech.client.voice;

import com.senne.imttech.ImtTech;
import com.senne.imttech.client.ImtClientConfig;
import com.senne.imttech.client.ImtClientEvents;
import com.senne.imttech.client.ImtClientState;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.registry.ModSounds;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Het brein van I.M.T: begrijpt gewone taal ("stuur de drones naar die zombie", "hoeveel
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
public final class ImtBrain {
    private ImtBrain() {}

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "I.M.T brain");
        thread.setDaemon(true);
        return thread;
    });

    /** Zo vaak (ms) zegt ze hooguit dat het brein niet lukte; daarna valt ze stil terug. */
    private static final long FAILURE_NOTICE_MILLIS = 60_000;

    private static volatile boolean thinking;
    private static volatile boolean warmedUp;
    private static long lastFailureNotice;

    public static BrainMode mode() {
        return ImtClientConfig.SPEC.isLoaded() ? ImtClientConfig.BRAIN.get() : BrainMode.DICTIONARY;
    }

    public static void setMode(BrainMode mode) {
        if (!ImtClientConfig.SPEC.isLoaded()) {
            return;
        }
        ImtClientConfig.BRAIN.set(mode);
        ImtClientConfig.SPEC.save();
        ImtMemory.clearConversation();
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

    // ================================================================ Het datacenter (8 oktober)

    /**
     * Heeft het brein een datacenter om op te draaien? Zonder werkt er geen enkel brein, ook het
     * woordenboek niet (in creative hoeft het niet: dan stuurt de server vertraging 0).
     */
    public static boolean hasDatacenter() {
        return ImtClientState.brainDelayTicks >= 0;
    }

    /** Hoe lang (ms) het datacenter erbij zet zolang het nog niet sterk is (0 = sterk genoeg). */
    public static long datacenterDelayMillis() {
        return Math.max(0, ImtClientState.brainDelayTicks) * 50L;
    }

    /** Zeg tegen de server dat ze nadenkt (dan werkt je datacenter hard), of dat ze klaar is. */
    private static void tellServer(boolean busy) {
        ImtClientEvents.sendAction(busy ? com.senne.imttech.imt.ImtAction.BRAIN_THINKING
                : com.senne.imttech.imt.ImtAction.BRAIN_DONE);
    }

    /**
     * Het woordenboek, maar pas na de vertraging van je datacenter (hoofd-thread). Geeft false als
     * er geen vertraging is (dan doet de beller het meteen) of ze nog met iets anders bezig is.
     */
    static boolean dictionaryLater(String heard) {
        long delay = datacenterDelayMillis();
        if (delay <= 0 || thinking) {
            return false;
        }
        thinking = true;
        tellServer(true);
        WORKER.execute(() -> {
            sleep(delay);
            post(() -> {
                thinking = false;
                tellServer(false);
                if (ImtClientState.authorized) {
                    VoiceControl.runDictionary(heard);
                }
            });
        });
        return true;
    }

    /** Even wachten op deze thread (de achtergrond-thread van het brein, nooit de hoofd-thread). */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
                    VoiceControl.tell(Component.translatable("imt.imttech.brain.needs_key"), ImtMessagePayload.Level.WARNING);
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
            VoiceControl.tell(Component.translatable("imt.imttech.brain.needs_ear"), ImtMessagePayload.Level.WARNING);
            return;
        }
        switch (VoiceInstaller.state(VoiceFeature.BRAIN)) {
            case UNSUPPORTED -> VoiceControl.tell(Component.translatable("imt.imttech.voice.unsupported"),
                    ImtMessagePayload.Level.WARNING);
            case INSTALLED -> {
                setMode(BrainMode.LOCAL);
                modeSet(BrainMode.LOCAL);
            }
            case INSTALLING -> VoiceControl.tell(Component.translatable("imt.imttech.voice.installing",
                    VoiceFeature.BRAIN.spokenName(), VoiceInstaller.percent(VoiceFeature.BRAIN)), ImtMessagePayload.Level.INFO);
            case NOT_INSTALLED, FAILED -> WORKER.execute(() -> {
                // Eerst kijken wat je pc aankan (dat start llama.cpp even), dan pas downloaden.
                BrainModel model = BrainModel.decide();
                post(() -> {
                    VoiceControl.tell(Component.translatable("imt.imttech.brain.install_started", model.size(),
                            VoiceFeature.BRAIN.missingSize()), ImtMessagePayload.Level.INFO);
                    VoiceInstaller.install(VoiceFeature.BRAIN, error -> {
                        if (error != null) {
                            VoiceControl.tell(Component.translatable("imt.imttech.voice.install_failed",
                                    VoiceFeature.BRAIN.spokenName(), error), ImtMessagePayload.Level.ALERT);
                            return;
                        }
                        setMode(BrainMode.LOCAL);
                        VoiceControl.tell(Component.translatable("imt.imttech.brain.install_done", model.size()),
                                ImtMessagePayload.Level.BOOT);
                    });
                });
            });
        }
    }

    private static void modeSet(BrainMode mode) {
        VoiceControl.tell(Component.translatable("imt.imttech.brain.mode_set", Component.translatable(mode.translationKey())),
                ImtMessagePayload.Level.INFO);
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
        if (ImtClientState.wearing && mode() == BrainMode.LOCAL && ready(BrainMode.LOCAL) && !warmedUp) {
            warmedUp = true;
            WORKER.execute(() -> {
                try {
                    LocalBrain.ask(BrainPrompt.system(), List.of(), BrainPrompt.turn("STATUS\n(warming up)", List.of(), "hello"));
                } catch (Throwable t) {
                    ImtTech.LOGGER.warn("I.M.T: could not warm up the local brain", t);
                }
            });
        }
        ImtInitiative.tick(mc);
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
        String message = BrainPrompt.turn(status, ImtMemory.facts(), heard);
        List<ImtMemory.Turn> history = ImtMemory.recentTurns();
        // Was haar vorige beurt een vraag ("bedoelde je ...?")? Dan is dit het antwoord daarop.
        boolean confirming = !history.isEmpty() && wasQuestion(history.getLast());
        // De vertraging van je datacenter komt bovenop de tijd van het model (8 oktober).
        long extra = datacenterDelayMillis();
        thinking = true;
        tellServer(true);
        WORKER.execute(() -> {
            try {
                long start = System.currentTimeMillis();
                String json = ask(mode, history, message);
                BrainDecision decision = BrainDecision.parse(json);
                ImtTech.LOGGER.info("I.M.T brain ({}, {} ms + {} ms datacenter): \"{}\" -> {}", mode,
                        System.currentTimeMillis() - start, extra, heard, json.replaceAll("\\s+", " "));
                sleep(extra);
                post(() -> apply(heard, decision, confirming));
            } catch (ClaudeBrain.Failure failure) {
                ImtTech.LOGGER.warn("I.M.T: Claude could not answer ({})", failure.reason, failure.getCause());
                post(() -> fallback(heard, "imt.imttech.brain.fail." + failure.reason));
            } catch (Throwable t) {
                ImtTech.LOGGER.warn("I.M.T: the {} brain could not answer", mode, t);
                post(() -> fallback(heard, mode == BrainMode.CLAUDE ? "imt.imttech.brain.fail.api_error"
                        : "imt.imttech.brain.fail.local"));
            } finally {
                thinking = false;
                post(() -> tellServer(false));
            }
        });
        return true;
    }

    private static boolean wasQuestion(ImtMemory.Turn turn) {
        try {
            return BrainDecision.parse(turn.answer()).unsure();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String ask(BrainMode mode, List<ImtMemory.Turn> history, String message) throws Exception {
        return mode == BrainMode.CLAUDE
                ? ClaudeBrain.ask(BrainPrompt.system(), history, message)
                : LocalBrain.ask(BrainPrompt.system(), history, message);
    }

    /** Het antwoord uitvoeren: onthouden, zeggen, doen (hoofd-thread). */
    private static void apply(String heard, BrainDecision decision, boolean confirming) {
        if (!ImtClientState.authorized) {
            return; // bril afgezet terwijl ze nadacht
        }
        ImtMemory.addTurn(heard, decision.json());
        if (!decision.forget().isBlank()) {
            ImtMemory.forget(decision.forget());
        }
        if (!decision.remember().isBlank()) {
            ImtMemory.remember(decision.remember());
        }
        if (!decision.reply().isBlank()) {
            VoiceControl.tell(Component.literal(decision.reply()), ImtMessagePayload.Level.INFO);
            // Haar eigen antwoord zegt al wat er gebeurt: de gewone meldingen van de acties
            // tonen we wel, maar spreken we niet ook nog uit.
            ImtSpeech.holdInfoMessages(2500);
        } else if (decision.actions().isEmpty()) {
            ImtClientEvents.playUi(ModSounds.IMT_BLIP.get(), 1.0f);
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
            VoiceControl.tell(Component.translatable(reasonKey), ImtMessagePayload.Level.WARNING);
        }
        VoiceControl.runDictionary(heard);
    }

    // ================================================================ Uit zichzelf

    /**
     * Iets dat ze uit zichzelf mag zeggen (ImtInitiative): een korte opmerking, nooit een
     * actie. Zegt het brein niets, dan blijft ze stil.
     */
    static void remark(String event) {
        BrainMode mode = mode();
        if (!ready(mode) || thinking || ImtAssistant.isBusy() || !hasDatacenter()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String message = BrainPrompt.event(BrainPrompt.status(mc), ImtMemory.facts(), event);
        thinking = true;
        WORKER.execute(() -> {
            try {
                BrainDecision decision = BrainDecision.parse(ask(mode, List.of(), message));
                ImtTech.LOGGER.info("I.M.T brain ({}), on her own about \"{}\": \"{}\"", mode, event, decision.reply());
                if (!decision.reply().isBlank()) {
                    post(() -> {
                        if (ImtClientState.authorized) {
                            VoiceControl.tell(Component.literal(decision.reply()), ImtMessagePayload.Level.INFO);
                        }
                    });
                }
            } catch (Throwable t) {
                ImtTech.LOGGER.debug("I.M.T: no remark ({})", t.toString());
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
