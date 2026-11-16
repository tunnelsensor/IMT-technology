package com.senne.imttech.client.alarm;

import com.senne.imttech.alarm.AlarmSounds;
import com.senne.imttech.alarm.audio.Adpcm;
import com.senne.imttech.alarm.audio.AlarmAudio;
import com.senne.imttech.network.AlarmSoundDataPayload;
import com.senne.imttech.network.AlarmSoundRequestPayload;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * De alarmgeluiden die je spel van de server haalde (hoofdstuk 126), zodat het ze niet steeds opnieuw hoeft te vragen.
 *
 * Een speaker zegt alleen de naam van zijn geluid (de vingerafdruk, zie AlarmSounds); kom je in de buurt van een speaker
 * met een geluid dat je nog niet hebt, dan vraagt je spel het één keer (AlarmSoundRequestPayload). Het komt in stukjes
 * terug (AlarmSoundDataPayload); als alles er is, kijken we na of het klopt (de vingerafdruk moet gelijk zijn: zo kan
 * niemand je een ander geluid onder dezelfde naam laten horen) en pakken we het uit.
 *
 * <ul>
 *   <li>Je spel vraagt hooguit {@link #MAX_REQUESTS} geluiden per tien seconden (de server stuurt er per speler zes): zo
 *       vraagt het nooit meer dan de server wil sturen. (Gevonden door de nalezing.)</li>
 *   <li>Zegt de server "even niet" (te veel gevraagd), dan vragen we het vijf seconden later opnieuw; zolang wachten we
 *       stil.</li>
 *   <li>Heeft de server het niet (meer), of komt het niet binnen 15 seconden, dan laat de speaker de ingebouwde sirene
 *       horen; na een halve minuut mag je spel het opnieuw vragen.</li>
 *   <li>We houden hooguit {@link #KEEP} uitgepakte geluiden bij (een geluid van 30 seconden is 1,3 MB): het langst niet
 *       gebruikte gaat eruit. Geluiden van speakers in de buurt worden elke halve seconde "gebruikt" (zie keep).</li>
 * </ul>
 */
final class AlarmSoundCache {
    private AlarmSoundCache() {}

    private static final int KEEP = 12;
    /** Zo lang (ticks) wachten we op een geluid voordat we het opgeven. */
    private static final int TIMEOUT_TICKS = 300;
    /** Zo lang (ticks) vragen we een geluid dat mislukte niet opnieuw (de server wil het hooguit eens per 30 s sturen). */
    private static final int RETRY_TICKS = 640;
    /** Zei de server "even niet": zo lang (ticks) wachten voor we het opnieuw vragen. */
    private static final int BUSY_RETRY_TICKS = 100;
    /** Hooguit zoveel vragen per {@link #REQUEST_WINDOW} ticks (net onder wat de server per speler stuurt). */
    private static final int MAX_REQUESTS = 5;
    private static final int REQUEST_WINDOW = 220;
    /** Meer stukjes dan dit is nooit een geldig geluid (120 seconden past in 11 stukjes). */
    private static final int MAX_CHUNKS = 16;

    private static final Map<String, short[]> SOUNDS = new LinkedHashMap<>(KEEP, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, short[]> eldest) {
            return size() > KEEP;
        }
    };
    private static final Map<String, Download> DOWNLOADS = new HashMap<>();
    /** Geluiden die mislukten (of die de server niet heeft): tot welke tick we ze niet opnieuw vragen. */
    private static final Map<String, Long> FAILED = new HashMap<>();
    /** Geluiden waarvoor de server "even niet" zei: vanaf welke tick we het opnieuw vragen (intussen stil wachten). */
    private static final Map<String, Long> RETRY = new HashMap<>();
    /** Wanneer we de laatste vragen stuurden. */
    private static final Deque<Long> REQUESTED = new ArrayDeque<>();
    /** Een eigen klok in ticks (de speltijd kan verspringen met /time). */
    private static long clock;

    /** Een geluid dat binnenkomt. */
    private static final class Download {
        final long started;
        int chunks = -1;
        int samples;
        byte[][] parts;
        int received;

        Download(long started) {
            this.started = started;
        }
    }

    /** Het geluid met deze naam, of null als we het (nog) niet hebben. Vraagt het niet aan: dat doet {@link #request}. */
    static @Nullable short[] get(String id) {
        return SOUNDS.get(id);
    }

    /** Lukte het niet om dit geluid te krijgen (de server heeft het niet)? Dan speelt de speaker de sirene. */
    static boolean failed(String id) {
        Long until = FAILED.get(id);
        return until != null && clock < until;
    }

    /** Wachten we nog op dit geluid (of op een tweede poging)? */
    static boolean pending(String id) {
        return DOWNLOADS.containsKey(id) || RETRY.containsKey(id);
    }

    /**
     * Een speaker in de buurt heeft dit geluid: houd het vers (zodat het niet als eerste uit de cache gaat), of vraag het
     * aan als we het nog niet hebben.
     */
    static void keep(String id) {
        if (SOUNDS.get(id) == null) {
            request(id);
        }
    }

    /**
     * Vraag dit geluid aan de server, als we het nog niet hebben, er niet al op wachten, het niet net mislukte, en we niet
     * al te veel vroegen. Geeft true als er echt een vraag vertrok.
     */
    static boolean request(String id) {
        if (!AlarmSounds.validId(id) || SOUNDS.containsKey(id) || DOWNLOADS.containsKey(id) || failed(id)) {
            return false;
        }
        Long retryAt = RETRY.get(id);
        if (retryAt != null && clock < retryAt) {
            return false;
        }
        while (!REQUESTED.isEmpty() && clock - REQUESTED.peekFirst() > REQUEST_WINDOW) {
            REQUESTED.pollFirst();
        }
        if (REQUESTED.size() >= MAX_REQUESTS) {
            return false; // straks weer
        }
        REQUESTED.addLast(clock);
        RETRY.remove(id);
        DOWNLOADS.put(id, new Download(clock));
        PacketDistributor.sendToServer(new AlarmSoundRequestPayload(id));
        return true;
    }

    /** Een geluid dat we zelf hebben (net geüpload): meteen bewaren, dan hoeven we het niet op te halen. */
    static void put(String id, short[] samples) {
        SOUNDS.put(id, samples);
        FAILED.remove(id);
        RETRY.remove(id);
        DOWNLOADS.remove(id);
    }

    /** Een stukje van een geluid kwam binnen. Wat we niet vroegen, of wat niet klopt, gooien we weg. */
    static void onData(AlarmSoundDataPayload payload) {
        Download download = DOWNLOADS.get(payload.id());
        if (download == null) {
            return;
        }
        if (payload.chunks() == AlarmSoundDataPayload.BUSY) {
            DOWNLOADS.remove(payload.id()); // de server zei "even niet": straks opnieuw (intussen stil wachten)
            RETRY.put(payload.id(), clock + BUSY_RETRY_TICKS);
            return;
        }
        if (payload.chunks() == 0) {
            fail(payload.id()); // de server heeft het niet: de sirene
            return;
        }
        int maxSamples = AlarmAudio.RATE * AlarmSounds.HARD_MAX_SECONDS;
        if (payload.chunks() > MAX_CHUNKS || payload.chunk() < 0 || payload.chunk() >= payload.chunks()
                || payload.samples() <= 0 || payload.samples() > maxSamples
                || download.chunks >= 0 && (download.chunks != payload.chunks() || download.samples != payload.samples())) {
            fail(payload.id());
            return;
        }
        if (download.chunks < 0) {
            download.chunks = payload.chunks();
            download.samples = payload.samples();
            download.parts = new byte[payload.chunks()][];
        }
        if (download.parts[payload.chunk()] == null) {
            download.parts[payload.chunk()] = payload.data();
            download.received++;
        }
        if (download.received < download.chunks) {
            return;
        }
        DOWNLOADS.remove(payload.id());
        int total = 0;
        for (byte[] part : download.parts) {
            total += part.length;
        }
        if (total != Adpcm.encodedLength(download.samples)) {
            fail(payload.id());
            return;
        }
        byte[] data = new byte[total];
        int at = 0;
        for (byte[] part : download.parts) {
            System.arraycopy(part, 0, data, at, part.length);
            at += part.length;
        }
        if (!AlarmSounds.idOf(download.samples, data).equals(payload.id())) {
            fail(payload.id()); // klopt niet bij zijn naam
            return;
        }
        SOUNDS.put(payload.id(), Adpcm.decode(data, download.samples));
    }

    private static void fail(String id) {
        DOWNLOADS.remove(id);
        FAILED.put(id, clock + RETRY_TICKS);
    }

    /** Elke tick: wat te lang duurt, geven we op. */
    static void tick() {
        clock++;
        if (!DOWNLOADS.isEmpty()) {
            DOWNLOADS.entrySet().removeIf(entry -> {
                if (clock - entry.getValue().started > TIMEOUT_TICKS) {
                    FAILED.put(entry.getKey(), clock + RETRY_TICKS);
                    return true;
                }
                return false;
            });
        }
        if (clock % 200 == 0) {
            FAILED.values().removeIf(until -> until <= clock);
            RETRY.values().removeIf(at -> at + TIMEOUT_TICKS <= clock);
        }
    }

    static void clear() {
        SOUNDS.clear();
        DOWNLOADS.clear();
        FAILED.clear();
        RETRY.clear();
        REQUESTED.clear();
    }
}
