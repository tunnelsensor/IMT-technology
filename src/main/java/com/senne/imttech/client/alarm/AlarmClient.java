package com.senne.imttech.client.alarm;

import com.senne.imttech.alarm.audio.AlarmAudio;
import com.senne.imttech.network.AlarmOpenPayload;
import com.senne.imttech.network.AlarmSoundDataPayload;
import com.senne.imttech.network.AlarmUploadReplyPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * De Speaker in je spel (hoofdstuk 126, sinds hoofdstuk 136 "Speaker"): hier komen de berichten van de server binnen, en
 * hier begint elke tick.
 *
 * <ul>
 *   <li>{@link AlarmSpeakerScreen}: het scherm (sneak + rechtsklik op een speaker die je mag instellen).</li>
 *   <li>{@link AlarmFiles}: een bestand kiezen (het venster van je computer, of slepen) en inlezen, naast het spel.</li>
 *   <li>{@link AlarmUploadClient}: het geluid in stukjes naar de server sturen (naar /sounds in Files van een kern).</li>
 *   <li>{@link AlarmSoundCache}: geluiden van anderen ophalen en onthouden.</li>
 *   <li>{@link AlarmSpeakerSounds}: speakers in de buurt laten klinken (geluiden en zinnen).</li>
 *   <li>Een geluid uit Files beluisteren: {@link #onPreview}.</li>
 * </ul>
 */
public final class AlarmClient {
    private AlarmClient() {}

    /** Bij het opstarten van je spel: ogg-bestanden lezen we met Minecraft's eigen lezer (die bestaat alleen in je spel). */
    public static void init() {
        AlarmAudio.setOggDecoder(new OggDecoder());
    }

    /** Het scherm openen, of bijwerken als het al open staat (na een knop of een upload). Nooit opnieuw openen. */
    public static void onOpen(AlarmOpenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AlarmSpeakerScreen screen && screen.pos().equals(payload.pos())) {
            screen.update(payload);
        } else if (minecraft.screen == null && payload.open()) {
            minecraft.setScreen(new AlarmSpeakerScreen(payload));
        }
    }

    public static void onUploadReply(AlarmUploadReplyPayload payload) {
        AlarmUploadClient.onReply(payload);
    }

    public static void onSoundData(AlarmSoundDataPayload payload) {
        AlarmSoundCache.onData(payload);
    }

    /** Het geluid dat je in Files beluistert (hoofdstuk 136), en hoe lang we er nog op wachten (ticks). */
    private static @org.jetbrains.annotations.Nullable String previewId;
    private static int previewWait;
    private static @org.jetbrains.annotations.Nullable AlarmPreviewSound preview;

    /** Je dubbelklikte in Files op een geluid: het komt eraan (in stukjes); zodra het er is, hoor jij het (alleen jij). */
    public static void onPreview(com.senne.imttech.network.AlarmPreviewPayload payload) {
        AlarmSoundCache.expect(payload.id());
        previewId = payload.id();
        previewWait = 300;
    }

    private static void tickPreview() {
        if (previewId == null) {
            return;
        }
        short[] clip = AlarmSoundCache.get(previewId);
        if (clip == null) {
            if (--previewWait <= 0 || AlarmSoundCache.failed(previewId)) {
                previewId = null;
            }
            return;
        }
        previewId = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (preview != null) {
            minecraft.getSoundManager().stop(preview);
        }
        preview = new AlarmPreviewSound(clip, 1f, clip.length * 20 / com.senne.imttech.alarm.audio.AlarmAudio.RATE + 4);
        minecraft.getSoundManager().play(preview);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        AlarmUploadClient.tick();
        AlarmSoundCache.tick();
        AlarmSpeakerSounds.tick();
        tickPreview();
    }

    /** Klinkt er nu een speaker op deze plek in je spel? (Voor de rondleiding: die mag de server niet lezen.) */
    public static boolean playingAt(net.minecraft.core.BlockPos pos) {
        return AlarmSpeakerSounds.playingAt(pos);
    }

    /** Uitloggen of een andere wereld: alles stil en vergeten (een andere server heeft andere geluiden). */
    public static void clear() {
        AlarmSpeakerSounds.clear();
        AlarmUploadClient.clear();
        previewId = null;
        AlarmSoundCache.clear();
    }
}
