package com.senne.starktech.client.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.senne.starktech.StarkTech;
import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.network.EdithMessagePayload;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * "E.D.I.T.H., speel Thunderstruck van AC/DC."
 *
 * Deze klasse praat met Spotify: zoeken, starten, pauzeren, volgende, vorige en "wat speelt er".
 * Het loopt allemaal op een aparte thread, want internet is traag en je spel mag niet haperen.
 *
 * Wat je nodig hebt (één keer instellen, zie MusicScreen):
 * 1. Spotify Premium (anders mag een app de muziek niet starten).
 * 2. Een gratis "app" in het Spotify-dashboard, met http://127.0.0.1:8899/starktech als
 *    Redirect URI. Het Client ID daarvan vul je in het spel in.
 * 3. Spotify moet ergens open staan (pc, telefoon, speaker): Spotify start de muziek daar.
 */
public final class EdithMusic {
    private EdithMusic() {}

    private static final String API = "https://api.spotify.com/v1";
    /** Eén achtergrond-thread: verzoeken netjes na elkaar. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "E.D.I.T.H. music");
        thread.setDaemon(true);
        return thread;
    });

    /** Wat er volgens Spotify nu speelt (voor de HUD), of leeg. */
    private static String nowPlaying = "";

    public static String nowPlaying() {
        return nowPlaying;
    }

    // ================================================================ Wat E.D.I.T.H. kan doen

    /**
     * "speel X van Y": zoekt het nummer en start het.
     * Je kunt ook gewoon "speel wat rustige muziek" zeggen; dan zoekt Spotify zelf iets.
     */
    public static void play(String request) {
        String query = cleanRequest(request);
        if (query.isBlank()) {
            tell("edith.starktech.music.what", EdithMessagePayload.Level.WARNING);
            return;
        }
        run(token -> {
            String url = API + "/search?type=track&limit=1&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            JsonObject result = get(token, url);
            if (result == null) {
                tell("edith.starktech.music.error", EdithMessagePayload.Level.ALERT);
                return;
            }
            JsonArray items = result.getAsJsonObject("tracks").getAsJsonArray("items");
            if (items.isEmpty()) {
                tellText(Component.translatable("edith.starktech.music.not_found", query), EdithMessagePayload.Level.WARNING);
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
                tell("edith.starktech.music.no_device", EdithMessagePayload.Level.WARNING);
            } else if (status == 403) {
                tell("edith.starktech.music.needs_premium", EdithMessagePayload.Level.WARNING);
            } else if (status / 100 == 2) {
                nowPlaying = name + " - " + artist;
                tellText(Component.translatable("edith.starktech.music.playing", name, artist),
                        EdithMessagePayload.Level.INFO);
            } else {
                tell("edith.starktech.music.error", EdithMessagePayload.Level.ALERT);
            }
        });
    }

    public static void pause() {
        run(token -> {
            int status = put(token, API + "/me/player/pause", null);
            if (status / 100 == 2) {
                tell("edith.starktech.music.paused", EdithMessagePayload.Level.INFO);
            } else if (status == 404) {
                tell("edith.starktech.music.no_device", EdithMessagePayload.Level.WARNING);
            }
        });
    }

    public static void resume() {
        run(token -> {
            int status = put(token, API + "/me/player/play", null);
            if (status / 100 == 2) {
                tell("edith.starktech.music.resumed", EdithMessagePayload.Level.INFO);
            } else if (status == 404) {
                tell("edith.starktech.music.no_device", EdithMessagePayload.Level.WARNING);
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

    /** Haalt op wat er speelt; met tell = true zegt E.D.I.T.H. het ook. */
    private static void current(boolean tell) {
        run(token -> {
            JsonObject json = get(token, API + "/me/player/currently-playing");
            if (json == null || !json.has("item") || json.get("item").isJsonNull()) {
                nowPlaying = "";
                if (tell) {
                    tell("edith.starktech.music.nothing", EdithMessagePayload.Level.INFO);
                }
                return;
            }
            JsonObject track = json.getAsJsonObject("item");
            String name = track.get("name").getAsString();
            String artist = track.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
            nowPlaying = name + " - " + artist;
            if (tell) {
                tellText(Component.translatable("edith.starktech.music.now", name, artist), EdithMessagePayload.Level.INFO);
            }
        });
    }

    // ================================================================ Praten met Spotify

    /** Doet iets met een geldig token, op de achtergrond-thread. */
    private static void run(TokenTask task) {
        if (!SpotifyAuth.isLinked()) {
            tell("edith.starktech.music.not_linked", EdithMessagePayload.Level.WARNING);
            return;
        }
        WORKER.submit(() -> {
            String token = SpotifyAuth.accessToken();
            if (token == null) {
                tell("edith.starktech.music.not_linked", EdithMessagePayload.Level.WARNING);
                return;
            }
            try {
                task.run(token);
            } catch (Exception e) {
                StarkTech.LOGGER.warn("E.D.I.T.H.: Spotify-verzoek mislukt", e);
                tell("edith.starktech.music.error", EdithMessagePayload.Level.ALERT);
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

    /**
     * Maakt van wat je zei een zoekopdracht: "thunderstruck van acdc" wordt
     * "thunderstruck acdc". Spotify vindt daar zelf het juiste nummer bij.
     */
    private static String cleanRequest(String request) {
        String text = request.strip();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String by : new String[] {" van ", " by ", " from "}) {
            int index = lower.indexOf(by);
            if (index > 0) {
                return text.substring(0, index) + " " + text.substring(index + by.length());
            }
        }
        return text;
    }

    private static void tell(String key, EdithMessagePayload.Level level) {
        tellText(Component.translatable(key), level);
    }

    /** Meldingen moeten van de hoofd-thread komen, dus zetten we ze netjes in de wachtrij. */
    private static void tellText(Component message, EdithMessagePayload.Level level) {
        Minecraft.getInstance().execute(() -> EdithClientState.addNotification(message, level));
    }
}
