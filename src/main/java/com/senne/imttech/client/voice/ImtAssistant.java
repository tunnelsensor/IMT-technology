package com.senne.imttech.client.voice;

import com.senne.imttech.ImtTech;
import net.minecraft.client.Minecraft;

/**
 * Wat er gebeurt nadat je de spraaktoets loslaat:
 *
 * 1. Het oor (ImtEar) zet je stem om in tekst (± 0,1 seconde met een videokaart, ± 0,5 zonder).
 * 2. VoiceControl zoekt die tekst op in het woordenboek en voert het commando uit.
 */
public final class ImtAssistant {
    private ImtAssistant() {}

    /** In welke fase I.M.T zit (voor de HUD). */
    public enum Stage {
        IDLE,
        /** Aan het opnemen. */
        LISTENING,
        /** Het oor start nog op (alleen de eerste keer). */
        STARTING,
        /** Het oor schrijft uit wat je zei. */
        HEARING,
        /** Het brein denkt na over wat je zei (zie ImtBrain). */
        THINKING
    }

    private static volatile Stage stage = Stage.IDLE;
    private static volatile boolean preloadStarted;

    public static Stage stage() {
        return stage == Stage.IDLE && ImtBrain.isThinking() ? Stage.THINKING : stage;
    }

    public static boolean isBusy() {
        return stage() != Stage.IDLE;
    }

    static void setStage(Stage newStage) {
        stage = newStage;
    }

    /** Start het oor alvast op de achtergrond, dan reageert I.M.T meteen de eerste keer. */
    public static void preload() {
        if (preloadStarted) {
            return;
        }
        preloadStarted = true;
        Thread thread = new Thread(() -> {
            try {
                ImtEar.SERVER.start();
            } catch (Throwable t) {
                ImtTech.LOGGER.warn("I.M.T: could not start speech recognition in advance", t);
            }
        }, "I.M.T ear start");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stopt het oor (bv. als je spraakbesturing uitzet), zodat het geheugen vrijkomt. */
    public static void shutdown() {
        ImtEar.SERVER.stop();
        preloadStarted = false;
    }

    /** Het opgenomen geluid verwerken (op de luister-thread, niet op de hoofd-thread). */
    static void process(byte[] pcm) {
        try {
            if (pcm.length < 16000 * 2 * 4 / 10) { // korter dan 0,4 seconde: niets gezegd
                post(() -> VoiceControl.onHeardNothing());
                return;
            }
            if (!ImtEar.SERVER.isRunning()) {
                stage = Stage.STARTING;
                ImtEar.SERVER.start();
            }
            stage = Stage.HEARING;
            EnglishOnly.Heard heard = ImtEar.transcribe(pcm);
            if (heard.text().isBlank()) {
                post(() -> VoiceControl.onHeardNothing());
                return;
            }
            // Alleen Engels: een andere taal voeren we niet uit.
            if (!EnglishOnly.accepted(heard)) {
                ImtTech.LOGGER.info("I.M.T: heard {} instead of English: \"{}\"", heard.language(), heard.text());
                post(() -> VoiceControl.onNotEnglish());
                return;
            }
            post(() -> VoiceControl.onHeard(heard.text()));
        } catch (Throwable t) {
            ImtTech.LOGGER.warn("I.M.T: speech recognition failed", t);
            String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            post(() -> VoiceControl.onError(reason));
        } finally {
            stage = Stage.IDLE;
        }
    }

    /** De microfoon werkte niet. */
    static void failed(String reason) {
        stage = Stage.IDLE;
        post(() -> VoiceControl.onError(reason));
    }

    private static void post(Runnable task) {
        Minecraft.getInstance().execute(task);
    }
}
