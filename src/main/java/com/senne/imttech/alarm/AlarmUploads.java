package com.senne.imttech.alarm;

import com.senne.imttech.alarm.audio.Adpcm;
import com.senne.imttech.alarm.audio.AlarmAudio;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.AlarmSoundDataPayload;
import com.senne.imttech.network.AlarmSoundRequestPayload;
import com.senne.imttech.network.AlarmUploadPayload;
import com.senne.imttech.network.AlarmUploadReplyPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.security.BaseTrust;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Geluiden uploaden naar een Alarm Speaker en ze naar de spelers sturen (hoofdstuk 126), aan de kant van de server.
 *
 * <b>Uploaden.</b> Je spel leest je bestand zelf in en maakt het klein (AlarmAudio, Adpcm); de server krijgt alleen dat
 * kleine geluid, in stukjes (AlarmUploadPayload). Het eerste stukje begint een upload, als je de speaker mag instellen
 * (je staat erbij, en hij is van jou of je bent vertrouwd in de basis), als uploaden aan staat ({@code [alarms]
 * uploadsAllowed}), als je niet net nog iets uploadde, en als het geluid niet langer is dan {@code maxSoundSeconds}. Daarna
 * moet elk stukje precies op zijn beurt komen en precies even groot zijn als verwacht; anders stopt de upload. Na het
 * laatste stukje bewaart AlarmSounds het geluid, kijkt de server nog eens of je de speaker nog mag instellen, en zet het
 * erop. Eén upload tegelijk per speler; wie uitlogt, of tien seconden niets meer stuurt, is zijn halve upload kwijt.
 *
 * <b>Ophalen.</b> Je spel vraagt een geluid zodra je in de buurt komt van een speaker die het heeft
 * (AlarmSoundRequestPayload). De server stuurt het alleen als er echt zo'n speaker in de buurt staat, en hooguit een paar
 * keer per tien seconden per speler: zo kan niemand de server leeg trekken.
 */
public final class AlarmUploads {
    private AlarmUploads() {}

    /** Zoveel ticks mag een upload stilliggen voordat hij weg is (tien seconden). */
    private static final int IDLE_TICKS = 200;
    /** Hooguit zoveel keer per {@link #REQUEST_WINDOW} ticks mag een speler een geluid vragen. */
    private static final int MAX_REQUESTS = 6;
    private static final int REQUEST_WINDOW = 200;
    /** Hetzelfde geluid vraagt een speler hooguit één keer per zoveel ticks (zijn spel onthoudt het daarna zelf). */
    private static final int SAME_SOUND_TICKS = 600;
    /**
     * Zoveel ticks tussen twee pogingen om een upload te beginnen (ook als die meteen mislukt): zo kan niemand de server
     * met eerste stukjes bestoken. (Gevonden door de nalezing.)
     */
    private static final int START_GAP = 20;

    /** Een upload die bezig is. */
    private static final class Upload {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        final int uploadId;
        final int chunks;
        final int samples;
        final String fileName;
        final byte[] data;
        int received;
        int next;
        long lastTick;

        Upload(ResourceKey<Level> dimension, BlockPos pos, int uploadId, int chunks, int samples, String fileName, int bytes, long now) {
            this.dimension = dimension;
            this.pos = pos.immutable();
            this.uploadId = uploadId;
            this.chunks = chunks;
            this.samples = samples;
            this.fileName = fileName;
            this.data = new byte[bytes];
            this.lastTick = now;
        }
    }

    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    /** Vanaf welke speltijd een speler weer mag uploaden. Blijft bij uitloggen (anders omzeil je het met opnieuw inloggen). */
    private static final Map<UUID, Long> NEXT_UPLOAD = new HashMap<>();
    /** Vanaf welke speltijd een speler weer een upload mag proberen te beginnen (zie START_GAP). */
    private static final Map<UUID, Long> NEXT_START = new HashMap<>();
    /** Wanneer een speler de laatste keren een geluid vroeg, en welk geluid wanneer. */
    private static final Map<UUID, Deque<Long>> REQUESTS = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> ASKED = new HashMap<>();

    // ================================================================ Uploaden

    /** Zoveel stukjes worden zoveel bytes (voor je spel en voor de server hetzelfde). */
    public static int chunksFor(int bytes) {
        return Math.max(1, (bytes + AlarmUploadPayload.CHUNK_BYTES - 1) / AlarmUploadPayload.CHUNK_BYTES);
    }

    /** De stukjes van een geluid, zoals je spel ze verstuurt. Ook voor de tests. */
    public static List<AlarmUploadPayload> payloads(BlockPos pos, int uploadId, int samples, String fileName, byte[] adpcm) {
        int chunks = chunksFor(adpcm.length);
        String name = AlarmSpeakerBlockEntity.clean(fileName, AlarmSpeakerBlockEntity.MAX_SOUND_NAME);
        List<AlarmUploadPayload> list = new ArrayList<>(chunks);
        for (int i = 0; i < chunks; i++) {
            int from = i * AlarmUploadPayload.CHUNK_BYTES;
            byte[] part = Arrays.copyOfRange(adpcm, from, Math.min(adpcm.length, from + AlarmUploadPayload.CHUNK_BYTES));
            list.add(new AlarmUploadPayload(pos.immutable(), uploadId, i, chunks, samples, name, part));
        }
        return list;
    }

    /** Een stukje van een upload kwam binnen (op de server-thread). */
    public static void handleUpload(ServerPlayer player, AlarmUploadPayload payload) {
        UUID id = player.getUUID();
        long now = player.serverLevel().getGameTime();
        if (payload.chunk() == 0) {
            if (!start(player, payload, now)) {
                return;
            }
        }
        Upload upload = UPLOADS.get(id);
        if (upload == null || upload.uploadId != payload.uploadId()) {
            return; // een stukje van een upload die al gestopt is: negeren
        }
        int expected = Math.min(AlarmUploadPayload.CHUNK_BYTES, upload.data.length - upload.received);
        if (!upload.pos.equals(payload.pos()) || upload.dimension != player.level().dimension() || payload.chunk() != upload.next
                || payload.chunks() != upload.chunks || payload.samples() != upload.samples || payload.data().length != expected) {
            UPLOADS.remove(id);
            reply(player, payload.pos(), payload.uploadId(), false, "alarm.imttech.upload.broken", "");
            return;
        }
        System.arraycopy(payload.data(), 0, upload.data, upload.received, expected);
        upload.received += expected;
        upload.next++;
        upload.lastTick = now;
        if (upload.next < upload.chunks) {
            return;
        }
        UPLOADS.remove(id);
        NEXT_UPLOAD.put(id, now + Math.round(Math.max(0f, ImtConfig.Alarms.UPLOAD_COOLDOWN_SECONDS) * 20f));
        finish(player, upload);
    }

    /** Het eerste stukje: mag deze upload beginnen? Zo ja, dan staat hij klaar in UPLOADS. */
    private static boolean start(ServerPlayer player, AlarmUploadPayload payload, long now) {
        UUID id = player.getUUID();
        Long nextStart = NEXT_START.get(id);
        if (nextStart != null && now < nextStart && now >= nextStart - START_GAP) {
            // Net nog een poging: niets vastleggen, alleen zeggen dat hij even moet wachten (een klein berichtje).
            reply(player, payload.pos(), payload.uploadId(), false, "alarm.imttech.upload.wait",
                    Long.toString(Math.max(1, (nextStart - now + 19) / 20)));
            return false;
        }
        NEXT_START.put(id, now + START_GAP);
        UPLOADS.remove(id); // een nieuwe upload vervangt een oude
        if (!ImtConfig.Alarms.UPLOADS_ALLOWED) {
            reply(player, payload.pos(), payload.uploadId(), false, "alarm.imttech.upload.off", "");
            return false;
        }
        AlarmSpeakerBlockEntity speaker = AlarmSpeakers.editable(player, payload.pos());
        if (speaker == null) {
            reply(player, payload.pos(), payload.uploadId(), false, "alarm.imttech.upload.not_allowed", "");
            return false;
        }
        Long allowedAt = NEXT_UPLOAD.get(id);
        if (allowedAt != null && now < allowedAt && now >= allowedAt - 72_000L) {
            long seconds = Math.max(1, (allowedAt - now + 19) / 20);
            reply(player, payload.pos(), payload.uploadId(), false, "alarm.imttech.upload.wait", Long.toString(seconds));
            return false;
        }
        int max = AlarmSounds.maxSamples();
        if (payload.samples() <= 0 || payload.samples() > max) {
            reply(player, payload.pos(), payload.uploadId(), false, "alarm.imttech.upload.too_long",
                    Integer.toString(Math.round(max / (float) AlarmAudio.RATE)));
            return false;
        }
        int bytes = Adpcm.encodedLength(payload.samples());
        // Eerst nakijken dat het eerste stukje klopt, pas dan het geheugen voor het hele geluid vastleggen.
        if (payload.chunks() != chunksFor(bytes) || payload.data().length != Math.min(AlarmUploadPayload.CHUNK_BYTES, bytes)) {
            reply(player, payload.pos(), payload.uploadId(), false, "alarm.imttech.upload.broken", "");
            return false;
        }
        String name = AlarmSpeakerBlockEntity.clean(payload.fileName(), AlarmSpeakerBlockEntity.MAX_SOUND_NAME);
        UPLOADS.put(id, new Upload(player.level().dimension(), payload.pos(), payload.uploadId(), payload.chunks(), payload.samples(),
                name.isEmpty() ? "sound" : name, bytes, now));
        return true;
    }

    /** Alles is binnen: bewaren, nog eens kijken of het mag, en op de speaker zetten. */
    private static void finish(ServerPlayer player, Upload upload) {
        MinecraftServer server = player.server;
        AlarmSounds.store(server, upload.samples, upload.data, soundId -> {
            if (soundId == null) {
                reply(player, upload.pos, upload.uploadId, false, "alarm.imttech.upload.store_failed", "");
                return;
            }
            ServerLevel level = server.getLevel(upload.dimension);
            if (level == null || !level.isLoaded(upload.pos) || !(level.getBlockEntity(upload.pos) instanceof AlarmSpeakerBlockEntity speaker)
                    || !BaseTrust.wouldAllow(level, speaker, player.getUUID(), player.getGameProfile().getName())) {
                reply(player, upload.pos, upload.uploadId, false, "alarm.imttech.upload.not_allowed", "");
                return;
            }
            speaker.setSound(soundId, upload.fileName, upload.samples / (float) AlarmAudio.RATE);
            AlarmSpeakers.log(level, upload.pos, player.getGameProfile().getName() + " uploaded the sound \"" + upload.fileName
                    + "\" to the alarm speaker \"" + speaker.name() + "\"");
            reply(player, upload.pos, upload.uploadId, true, "alarm.imttech.upload.done", upload.fileName);
            AlarmSpeakers.refresh(player, speaker);
        });
    }

    private static void reply(ServerPlayer player, BlockPos pos, int uploadId, boolean ok, String message, String argument) {
        ModNetwork.sendTo(player, new AlarmUploadReplyPayload(pos.immutable(), uploadId, ok, message, argument));
    }

    // ================================================================ Ophalen

    /** Een speler vraagt een geluid (op de server-thread). */
    public static void handleRequest(ServerPlayer player, AlarmSoundRequestPayload payload) {
        String soundId = payload.id();
        if (!AlarmSounds.validId(soundId)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (!allowRequest(player.getUUID(), soundId, now)) {
            // Te veel gevraagd: "even niet". Zijn spel vraagt het straks opnieuw (en wacht intussen stil).
            ModNetwork.sendTo(player, new AlarmSoundDataPayload(soundId, 0, AlarmSoundDataPayload.BUSY, 0, new byte[0]));
            return;
        }
        if (!AlarmSpeakers.usedNear(player.serverLevel(), player.position(), soundId)) {
            ModNetwork.sendTo(player, new AlarmSoundDataPayload(soundId, 0, 0, 0, new byte[0])); // niets voor jou: de sirene
            return;
        }
        AlarmSounds.load(player.server, soundId, stored -> {
            if (player.hasDisconnected()) {
                return;
            }
            if (stored == null) {
                ModNetwork.sendTo(player, new AlarmSoundDataPayload(soundId, 0, 0, 0, new byte[0]));
                return;
            }
            for (AlarmSoundDataPayload part : dataPayloads(stored)) {
                ModNetwork.sendTo(player, part);
            }
            AlarmSounds.touch(player.server, soundId); // in gebruik: als laatste opruimen
        });
    }

    /** De stukjes waarin de server een geluid verstuurt. Ook voor de tests. */
    public static List<AlarmSoundDataPayload> dataPayloads(AlarmSounds.Stored stored) {
        byte[] data = stored.adpcm();
        int chunks = Math.max(1, (data.length + AlarmSoundDataPayload.CHUNK_BYTES - 1) / AlarmSoundDataPayload.CHUNK_BYTES);
        List<AlarmSoundDataPayload> list = new ArrayList<>(chunks);
        for (int i = 0; i < chunks; i++) {
            int from = i * AlarmSoundDataPayload.CHUNK_BYTES;
            byte[] part = Arrays.copyOfRange(data, from, Math.min(data.length, from + AlarmSoundDataPayload.CHUNK_BYTES));
            list.add(new AlarmSoundDataPayload(stored.id(), i, chunks, stored.samples(), part));
        }
        return list;
    }

    /** Mag deze speler nu (nog) een geluid vragen? Hooguit een paar keer per tien seconden, en niet steeds hetzelfde. */
    public static boolean allowRequest(UUID player, String soundId, long now) {
        Map<String, Long> asked = ASKED.computeIfAbsent(player, key -> new HashMap<>());
        asked.values().removeIf(time -> now - time > SAME_SOUND_TICKS || time > now);
        if (asked.containsKey(soundId)) {
            return false;
        }
        Deque<Long> times = REQUESTS.computeIfAbsent(player, key -> new ArrayDeque<>());
        while (!times.isEmpty() && (now - times.peekFirst() > REQUEST_WINDOW || times.peekFirst() > now)) {
            times.pollFirst();
        }
        if (times.size() >= MAX_REQUESTS) {
            return false;
        }
        times.addLast(now);
        asked.put(soundId, now);
        return true;
    }

    // ================================================================ Opruimen

    /** Elke tick: uploads die te lang stilliggen, zijn weg. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (UPLOADS.isEmpty() || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, Upload>> it = UPLOADS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Upload> entry = it.next();
            ServerLevel level = event.getServer().getLevel(entry.getValue().dimension);
            long now = level == null ? 0 : level.getGameTime();
            if (level == null || now - entry.getValue().lastTick > IDLE_TICKS || now < entry.getValue().lastTick) {
                it.remove();
            }
        }
    }

    /** Wie uitlogt, is zijn halve upload kwijt (de wachttijd blijft). */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        UPLOADS.remove(id);
        REQUESTS.remove(id);
        ASKED.remove(id);
    }

    public static void clear() {
        UPLOADS.clear();
        NEXT_UPLOAD.clear();
        NEXT_START.clear();
        REQUESTS.clear();
        ASKED.clear();
    }

    /** Is er voor deze speler een upload bezig? (Voor de tests.) */
    public static boolean uploading(UUID player) {
        return UPLOADS.containsKey(player);
    }
}
