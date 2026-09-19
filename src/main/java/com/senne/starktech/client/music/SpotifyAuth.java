package com.senne.starktech.client.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.senne.starktech.StarkTech;
import com.senne.starktech.client.EdithClientConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * Het inloggen bij Spotify.
 *
 * Hoe dit werkt (en waarom het veilig is):
 * - Jij maakt zelf, gratis, een "app" aan in het Spotify-dashboard. Daar krijg je een Client ID.
 *   Dat is geen wachtwoord: het is gewoon een nummer dat zegt welke app het is.
 * - Wij gebruiken "PKCE": de mod maakt een geheime code, stuurt alleen een versleutelde versie
 *   naar Spotify, en jij logt in op de site van Spotify zelf. De mod ziet je wachtwoord nooit.
 * - Spotify stuurt je daarna terug naar http://127.0.0.1:8899/starktech, een klein webserver-tje
 *   dat alleen op jouw eigen pc draait en één seconde open staat.
 * - We bewaren daarna alleen een "refresh token" in config/starktech-spotify.json. Met dat token
 *   kan E.D.I.T.H. muziek starten; het gaat nooit naar iemand anders dan Spotify.
 *
 * Alles gebeurt op een aparte thread, zodat je spel niet vastloopt.
 */
public final class SpotifyAuth {
    private SpotifyAuth() {}

    /** Waar Spotify je na het inloggen naartoe stuurt. Zet dit ook in je Spotify-app! */
    public static final String REDIRECT_URI = "http://127.0.0.1:8899/starktech";
    private static final int CALLBACK_PORT = 8899;
    /** Wat de mod mag doen met je account: kijken wat er speelt en de muziek besturen. */
    private static final String SCOPES = "user-read-playback-state user-modify-playback-state";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static @Nullable String accessToken;
    private static long accessTokenExpiresAt;
    private static @Nullable String refreshToken;
    private static boolean loaded;

    /** Het bestandje met je refresh token. */
    private static Path tokenFile() {
        return FMLPaths.CONFIGDIR.get().resolve("starktech-spotify.json");
    }

    /** Is Spotify al gekoppeld? */
    public static synchronized boolean isLinked() {
        load();
        return refreshToken != null && !refreshToken.isBlank();
    }

    /** Heb je al een Client ID ingevuld? (Zonder dat kan het koppelen niet beginnen.) */
    public static boolean hasClientId() {
        return !clientId().isBlank();
    }

    private static String clientId() {
        return EdithClientConfig.SPEC.isLoaded() ? EdithClientConfig.SPOTIFY_CLIENT_ID.get().trim() : "";
    }

    /** Vergeet de koppeling (bv. als je een ander account wil gebruiken). */
    public static synchronized void unlink() {
        refreshToken = null;
        accessToken = null;
        try {
            Files.deleteIfExists(tokenFile());
        } catch (IOException e) {
            StarkTech.LOGGER.warn("E.D.I.T.H.: kon {} niet verwijderen", tokenFile(), e);
        }
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = tokenFile();
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("refresh_token")) {
                refreshToken = json.get("refresh_token").getAsString();
            }
        } catch (Exception e) {
            StarkTech.LOGGER.warn("E.D.I.T.H.: kan {} niet lezen", file, e);
        }
    }

    private static synchronized void saveRefreshToken(String token) {
        refreshToken = token;
        JsonObject json = new JsonObject();
        json.addProperty("refresh_token", token);
        try {
            Files.createDirectories(tokenFile().getParent());
            Files.writeString(tokenFile(), json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            StarkTech.LOGGER.warn("E.D.I.T.H.: kan het Spotify-token niet opslaan", e);
        }
    }

    // ================================================================ Koppelen (één keer)

    /**
     * Start het koppelen: opent je browser bij Spotify en wacht op het antwoord.
     *
     * @param done wordt aangeroepen met null als het lukte, of met een foutmelding
     */
    public static void link(Consumer<@Nullable String> done) {
        String id = clientId();
        if (id.isBlank()) {
            done.accept("no_client_id");
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                String verifier = randomString(64);
                String challenge = base64Url(sha256(verifier));
                String url = "https://accounts.spotify.com/authorize"
                        + "?client_id=" + encode(id)
                        + "&response_type=code"
                        + "&redirect_uri=" + encode(REDIRECT_URI)
                        + "&code_challenge_method=S256"
                        + "&code_challenge=" + challenge
                        + "&scope=" + encode(SCOPES);
                Util.getPlatform().openUri(url);
                String code = waitForCode();
                if (code == null) {
                    done.accept("no_code");
                    return;
                }
                exchangeCode(id, code, verifier, done);
            } catch (Exception e) {
                StarkTech.LOGGER.warn("E.D.I.T.H.: koppelen met Spotify mislukt", e);
                done.accept(e.getClass().getSimpleName());
            }
        }, "E.D.I.T.H. Spotify link");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Een piepklein webservertje: het wacht tot Spotify je browser terugstuurt naar
     * 127.0.0.1:8899 en haalt de code uit die ene regel.
     */
    private static @Nullable String waitForCode() throws IOException {
        try (ServerSocket server = new ServerSocket(CALLBACK_PORT, 1, java.net.InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(180_000); // drie minuten om in te loggen
            try (Socket socket = server.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String requestLine = in.readLine();
                String code = null;
                if (requestLine != null && requestLine.contains("code=")) {
                    String query = requestLine.substring(requestLine.indexOf("code=") + 5);
                    int end = query.indexOf('&');
                    if (end < 0) {
                        end = query.indexOf(' ');
                    }
                    code = end > 0 ? query.substring(0, end) : query;
                }
                // Een vriendelijk berichtje in de browser.
                String page = code != null
                        ? "<h2>E.D.I.T.H. is gekoppeld aan Spotify.</h2><p>Je kunt dit tabblad sluiten.</p>"
                        : "<h2>Er ging iets mis.</h2><p>Probeer het opnieuw in het spel.</p>";
                byte[] body = ("<html><body style='font-family:sans-serif'>" + page + "</body></html>")
                        .getBytes(StandardCharsets.UTF_8);
                OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                        + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(body);
                out.flush();
                return code;
            }
        }
    }

    /** De code omruilen voor tokens. */
    private static void exchangeCode(String clientId, String code, String verifier, Consumer<@Nullable String> done)
            throws Exception {
        String body = "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(REDIRECT_URI)
                + "&client_id=" + encode(clientId)
                + "&code_verifier=" + encode(verifier);
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            StarkTech.LOGGER.warn("E.D.I.T.H.: Spotify gaf {} terug: {}", response.statusCode(), response.body());
            done.accept("http_" + response.statusCode());
            return;
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        setAccessToken(json);
        if (json.has("refresh_token")) {
            saveRefreshToken(json.get("refresh_token").getAsString());
        }
        done.accept(null);
    }

    // ================================================================ Token vernieuwen

    /**
     * Geeft een geldig access token terug (en vernieuwt het als het bijna verlopen is).
     * Let op: dit mag NIET op de hoofd-thread gebeuren.
     */
    public static synchronized @Nullable String accessToken() {
        load();
        if (accessToken != null && System.currentTimeMillis() < accessTokenExpiresAt - 30_000) {
            return accessToken;
        }
        if (refreshToken == null || clientId().isBlank()) {
            return null;
        }
        try {
            String body = "grant_type=refresh_token"
                    + "&refresh_token=" + encode(refreshToken)
                    + "&client_id=" + encode(clientId());
            HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://accounts.spotify.com/api/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                StarkTech.LOGGER.warn("E.D.I.T.H.: Spotify-token vernieuwen mislukte ({})", response.statusCode());
                return null;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            setAccessToken(json);
            if (json.has("refresh_token")) {
                saveRefreshToken(json.get("refresh_token").getAsString());
            }
            return accessToken;
        } catch (Exception e) {
            StarkTech.LOGGER.warn("E.D.I.T.H.: Spotify-token vernieuwen mislukte", e);
            return null;
        }
    }

    private static void setAccessToken(JsonObject json) {
        accessToken = json.get("access_token").getAsString();
        long seconds = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600;
        accessTokenExpiresAt = System.currentTimeMillis() + seconds * 1000;
    }

    static HttpClient http() {
        return HTTP;
    }

    // ================================================================ Kleine hulpjes

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static String randomString(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return base64Url(bytes).substring(0, length);
    }

    private static byte[] sha256(String text) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
