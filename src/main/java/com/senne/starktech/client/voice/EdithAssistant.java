package com.senne.starktech.client.voice;

import com.senne.starktech.StarkTech;
import net.minecraft.client.Minecraft;

/**
 * Wat er gebeurt nadat je de spraaktoets loslaat:
 *
 * 1. Het oor (EdithEar) zet je stem om in tekst (± 0,1 seconde met een videokaart, ± 0,5 zonder).
 * 2. VoiceControl zoekt die tekst op in het woordenboek en voert het commando uit.
 */
public final class EdithAssistant {
    private EdithAssistant() {}

    /** In welke fase E.D.I.T.H. zit (voor de HUD). */
    public enum Stage {
        IDLE,
        /** Aan het opnemen. */
        LISTENING,
        /** Het oor start nog op (alleen de eerste keer). */
        STARTING,
        /** Het oor schrijft uit wat je zei. */
        HEARING,
        /** Het brein denkt na over wat je zei (zie EdithBrain). */
        THINKING
    }

    private static volatile Stage stage = Stage.IDLE;
    private static volatile boolean preloadStarted;

    public static Stage stage() {
        return stage == Stage.IDLE && EdithBrain.isThinking() ? Stage.THINKING : stage;
    }

    public static boolean isBusy() {
        return stage() != Stage.IDLE;
    }

    static void setStage(Stage newStage) {
        stage = newStage;
    }

    /** Start het oor alvast op de achtergrond, dan reageert E.D.I.T.H. meteen de eerste keer. */
    public static void preload() {
        if (preloadStarted) {
            return;
        }
        preloadStarted = true;
        Thread thread = new Thread(() -> {
            try {
                EdithEar.SERVER.start();
            } catch (Throwable t) {
                StarkTech.LOGGER.warn("E.D.I.T.H.: could not start speech recognition in advance", t);
            }
        }, "E.D.I.T.H. ear start");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stopt het oor (bv. als je spraakbesturing uitzet), zodat het geheugen vrijkomt. */
    public static void shutdown() {
        EdithEar.SERVER.stop();
        preloadStarted = false;
    }

    /** Het opgenomen geluid verwerken (op de luister-thread, niet op de hoofd-thread). */
    static void process(byte[] pcm) {
        try {
            if (pcm.length < 16000 * 2 * 4 / 10) { // korter dan 0,4 seconde: niets gezegd
                post(() -> VoiceControl.onHeardNothing());
                return;
            }
            if (!EdithEar.SERVER.isRunning()) {
                stage = Stage.STARTING;
                EdithEar.SERVER.start();
            }
            stage = Stage.HEARING;
            String text = EdithEar.transcribe(pcm);
            if (text.isBlank()) {
                post(() -> VoiceControl.onHeardNothing());
                return;
            }
            post(() -> VoiceControl.onHeard(text));
        } catch (Throwable t) {
            StarkTech.LOGGER.warn("E.D.I.T.H.: speech recognition failed", t);
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
