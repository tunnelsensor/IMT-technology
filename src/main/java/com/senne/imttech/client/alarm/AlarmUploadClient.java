package com.senne.imttech.client.alarm;

import com.senne.imttech.alarm.AlarmSounds;
import com.senne.imttech.alarm.AlarmUploads;
import com.senne.imttech.alarm.audio.Adpcm;
import com.senne.imttech.network.AlarmUploadPayload;
import com.senne.imttech.network.AlarmUploadReplyPayload;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Een geluid naar de server sturen (hoofdstuk 126; sinds hoofdstuk 136 naar de map sounds in Files van een kern), in je
 * spel.
 *
 * Files geeft het ingelezen geluid ({@link #start}, met de plek van de kern); wij maken het klein (Adpcm) en sturen het in
 * stukjes van 24 kB, een paar per tick (AlarmUploadPayload). Daarna wachten we op het antwoord van de server
 * (AlarmUploadReplyPayload): gelukt (dan staat het in /sounds), of waarom niet. Het antwoord zie je boven je hotbar.
 *
 * Het geluid dat je net uploadde, onthoudt je spel meteen (AlarmSoundCache): je hoeft het niet terug op te halen.
 */
public final class AlarmUploadClient {
    private AlarmUploadClient() {}

    /** Zoveel stukjes per tick (24 kB elk). */
    private static final int CHUNKS_PER_TICK = 4;
    /** Zo lang (ticks) wachten we op het antwoord van de server. */
    private static final int REPLY_TIMEOUT = 600;

    public enum State { IDLE, SENDING, WAITING, DONE, FAILED }

    private static State state = State.IDLE;
    private static BlockPos pos = BlockPos.ZERO;
    private static int uploadId;
    private static List<AlarmUploadPayload> chunks = List.of();
    private static int sent;
    private static int waited;
    private static Component message = Component.empty();
    /** Wanneer (onze klok, ticks) de laatste upload klaar was of mislukte, zodat Files de uitslag nog even toont. */
    private static long finishedAt = -10_000;
    private static long clock;
    private static int nextId = 1;

    /** Begin een upload naar Files van de kern op deze plek. Een upload die nog bezig was, stopt. */
    public static void start(BlockPos core, String fileName, short[] samples) {
        byte[] adpcm = Adpcm.encode(samples);
        // Wat je zelf hoort, is wat de anderen horen: het kleine geluid, weer uitgepakt.
        AlarmSoundCache.put(AlarmSounds.idOf(samples.length, adpcm), Adpcm.decode(adpcm, samples.length));
        pos = core.immutable();
        uploadId = nextId++;
        chunks = AlarmUploads.payloads(pos, uploadId, samples.length, fileName, adpcm);
        sent = 0;
        waited = 0;
        state = State.SENDING;
        message = Component.translatable("alarm.imttech.upload.sending", 0);
    }

    static void tick() {
        clock++;
        if (state == State.SENDING) {
            for (int i = 0; i < CHUNKS_PER_TICK && sent < chunks.size(); i++) {
                PacketDistributor.sendToServer(chunks.get(sent++));
            }
            message = Component.translatable("alarm.imttech.upload.sending", Math.round(progress() * 100f));
            if (sent >= chunks.size()) {
                state = State.WAITING;
                waited = 0;
            }
        } else if (state == State.WAITING && ++waited > REPLY_TIMEOUT) {
            finish(false, Component.translatable("alarm.imttech.upload.no_answer"));
        }
    }

    /** Het antwoord van de server. */
    static void onReply(AlarmUploadReplyPayload reply) {
        if (reply.uploadId() != uploadId || !reply.pos().equals(pos) || state == State.IDLE) {
            return;
        }
        finish(reply.ok(), Component.translatable(reply.message(), reply.argument()));
    }

    private static void finish(boolean ok, Component text) {
        state = ok ? State.DONE : State.FAILED;
        message = text;
        finishedAt = clock;
        chunks = List.of();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(text.copy().withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        }
    }

    /** Hoe ver de upload is (0 tot 1). */
    public static float progress() {
        if (state == State.WAITING || state == State.DONE) {
            return 1f;
        }
        return chunks.isEmpty() ? 0f : sent / (float) chunks.size();
    }

    public static State state() {
        return state;
    }

    /** Gaat er nu een upload naar (Files van) de kern op deze plek? */
    public static boolean busyFor(BlockPos core) {
        return (state == State.SENDING || state == State.WAITING) && pos.equals(core);
    }

    public static Component message() {
        return message;
    }

    /**
     * Hoofdstuk 136: de uitslag van de laatste upload naar deze kern (gelukt, of waarom niet), als die minder dan acht
     * seconden geleden kwam; anders null. Files toont hem onderaan: boven je hotbar zie je hem niet, want het bureaublad
     * ligt erover (gevonden door de nalezing).
     */
    public static @org.jetbrains.annotations.Nullable Component recentResult(BlockPos core) {
        return (state == State.DONE || state == State.FAILED) && pos.equals(core) && clock - finishedAt < 160 ? message : null;
    }

    /** Was de laatste upload (waarvan {@link #recentResult} iets zegt) gelukt? */
    public static boolean lastOk() {
        return state == State.DONE;
    }

    static void clear() {
        state = State.IDLE;
        chunks = List.of();
        sent = 0;
        message = Component.empty();
    }
}
