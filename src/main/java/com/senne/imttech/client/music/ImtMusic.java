package com.senne.imttech.client.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.senne.imttech.ImtTech;
import com.senne.imttech.client.ImtClientState;
import com.senne.imttech.network.ImtMessagePayload;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * "I.M.T, speel Thunderstruck van AC/DC."
 *
 * Deze klasse praat met Spotify: zoeken, starten, pauzeren, volgende, vorige en "wat speelt er".
 * Het loopt allemaal op een aparte thread, want internet is traag en je spel mag niet haperen.
 *
 * Wat je nodig hebt (één keer instellen, zie MusicScreen):
 * 1. Spotify Premium (anders mag een app de muziek niet starten).
 * 2. Een gratis "app" in het Spotify-dashboard, met http://127.0.0.1:8899/imttech als
 *    Redirect URI. Het Client ID daarvan vul je in het spel in.
 * 3. Spotify moet ergens open staan (pc, telefoon, speaker): Spotify start de muziek daar.
 */
public final class ImtMusic {
    private ImtMusic() {}

    private static final String API = "https://api.spotify.com/v1";
    /** Eén achtergrond-thread: verzoeken netjes na elkaar. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "I.M.T music");
        thread.setDaemon(true);
        return thread;
    });

    /** Wat er volgens Spotify nu speelt (voor de HUD), of leeg. */
    private static String nowPlaying = "";

    public static String nowPlaying() {
        return nowPlaying;
    }

    // ================================================================ Wat I.M.T kan doen

    /**
     * "speel X van Y": zoekt het nummer en start het.
     * Je kunt ook gewoon "speel wat rustige muziek" zeggen; dan zoekt Spotify zelf iets.
     */
    public static void play(String request) {
        String query = MusicQuery.build(request);
        if (query.isBlank()) {
            tell("imt.imttech.music.what", ImtMessagePayload.Level.WARNING);
            return;
        }
        run(token -> {
            JsonArray items = search(token, query);
            String plain = MusicQuery.plain(request);
            // Niets gevonden met track:/artist:? Dan was "van" misschien deel van de titel
            // ("man van staal"). Zoek het dan nog eens als gewone tekst.
            if (items != null && items.isEmpty() && !plain.equalsIgnoreCase(query) && !plain.isBlank()) {
                items = search(token, plain);
            }
            if (items == null) {
                tell("imt.imttech.music.error", ImtMessagePayload.Level.ALERT);
                return;
            }
            if (items.isEmpty()) {
                tellText(Component.translatable("imt.imttech.music.not_found", plain.isBlank() ? query : plain),
                        ImtMessagePayload.Level.WARNING);
                return;
            }
            JsonObject track = items.get(0).getAsJsonObject();
            String uri = track.get("uri").getAsString();
            String name = track.get("name").getAsString();
            String artist = track.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();

            JsonObject body = new JsonObject();
            JsonArray uris = new JsonArray();
            uris.add(uri);
            body.add("uris", uris);
            int status = put(token, API + "/me/player/play", body.toString());
            if (status == 404) {
                tell("imt.imttech.music.no_device", ImtMessagePayload.Level.WARNING);
            } else if (status == 403) {
                tell("imt.imttech.music.needs_premium", ImtMessagePayload.Level.WARNING);
            } else if (status / 100 == 2) {
                nowPlaying = name + " - " + artist;
                tellText(Component.translatable("imt.imttech.music.playing", name, artist),
                        ImtMessagePayload.Level.INFO);
            } else {
                tell("imt.imttech.music.error", ImtMessagePayload.Level.ALERT);
            }
        });
    }

    public static void pause() {
        run(token -> {
            int status = put(token, API + "/me/player/pause", null);
            if (status / 100 == 2) {
                tell("imt.imttech.music.paused", ImtMessagePayload.Level.INFO);
            } else if (status == 404) {
                tell("imt.imttech.music.no_device", ImtMessagePayload.Level.WARNING);
            }
        });
    }

    public static void resume() {
        run(token -> {
            int status = put(token, API + "/me/player/play", null);
            if (status / 100 == 2) {
                tell("imt.imttech.music.resumed", ImtMessagePayload.Level.INFO);
            } else if (status == 404) {
                tell("imt.imttech.music.no_device", ImtMessagePayload.Level.WARNING);
            }
        });
    }

    public static void next() {
        run(token -> {
            post(token, API + "/me/player/next");
            current(true);
        });
    }

    public static void previous() {
        run(token -> {
            post(token, API + "/me/player/previous");
            current(true);
        });
    }

    /** "Wat speelt er?" */
    public static void whatIsPlaying() {
        run(token -> current(true));
    }

    /** Haalt op wat er speelt; met tell = true zegt I.M.T het ook. */
    private static void current(boolean tell) {
        run(token -> {
            JsonObject json = get(token, API + "/me/player/currently-playing");
            if (json == null || !json.has("item") || json.get("item").isJsonNull()) {
                nowPlaying = "";
                if (tell) {
                    tell("imt.imttech.music.nothing", ImtMessagePayload.Level.INFO);
                }
                return;
            }
            JsonObject track = json.getAsJsonObject("item");
            String name = track.get("name").getAsString();
            String artist = track.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
            nowPlaying = name + " - " + artist;
            if (tell) {
                tellText(Component.translatable("imt.imttech.music.now", name, artist), ImtMessagePayload.Level.INFO);
            }
        });
    }

    // ================================================================ Praten met Spotify

    /** Doet iets met een geldig token, op de achtergrond-thread. */
    private static void run(TokenTask task) {
        if (!SpotifyAuth.isLinked()) {
            tell("imt.imttech.music.not_linked", ImtMessagePayload.Level.WARNING);
            return;
        }
        WORKER.submit(() -> {
            String token = SpotifyAuth.accessToken();
            if (token == null) {
                tell("imt.imttech.music.not_linked", ImtMessagePayload.Level.WARNING);
                return;
            }
            try {
                task.run(token);
            } catch (Exception e) {
                ImtTech.LOGGER.warn("I.M.T: Spotify request failed", e);
                tell("imt.imttech.music.error", ImtMessagePayload.Level.ALERT);
            }
        });
    }

    private interface TokenTask {
        void run(String token) throws Exception;
    }

    private static @Nullable JsonObject get(String token, String url) throws Exception {
        HttpResponse<String> response = SpotifyAuth.http().send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2 || response.body().isBlank()) {
            return null;
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static int put(String token, String url, @Nullable String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return SpotifyAuth.http().send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(publisher).build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private static int post(String token, String url) throws Exception {
        return SpotifyAuth.http().send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    // ================================================================ Hulpjes

    /** Zoekt nummers bij Spotify. Geeft null als er iets misging (dan is er geen antwoord). */
    private static @Nullable JsonArray search(String token, String query) throws Exception {
        String url = API + "/search?type=track&limit=1&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        JsonObject result = get(token, url);
        return result == null ? null : result.getAsJsonObject("tracks").getAsJsonArray("items");
    }

    private static void tell(String key, ImtMessagePayload.Level level) {
        tellText(Component.translatable(key), level);
    }

    /** Meldingen moeten van de hoofd-thread komen, dus zetten we ze netjes in de wachtrij. */
    private static void tellText(Component message, ImtMessagePayload.Level level) {
        Minecraft.getInstance().execute(() -> ImtClientState.addNotification(message, level));
    }
}
