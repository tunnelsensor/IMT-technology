package com.senne.imttech.client.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.senne.imttech.ImtTech;
import com.senne.imttech.client.ImtClientConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
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
import java.util.concurrent.atomic.AtomicReference;
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
 * - Spotify stuurt je daarna terug naar http://127.0.0.1:8899/imttech, een klein webserver-tje
 *   dat alleen op jouw eigen pc draait en dat we sluiten zodra je antwoord binnen is.
 * - We bewaren daarna alleen een "refresh token" in config/imttech-spotify.json. Met dat token
 *   kan I.M.T muziek starten; het gaat nooit naar iemand anders dan Spotify.
 *
 * Alles gebeurt op een aparte thread, zodat je spel niet vastloopt.
 */
public final class SpotifyAuth {
    private SpotifyAuth() {}

    /** Waar Spotify je na het inloggen naartoe stuurt. Zet dit ook in je Spotify-app! */
    public static final String REDIRECT_URI = "http://127.0.0.1:8899/imttech";
    /**
     * Het adres en de poort halen we uit REDIRECT_URI zelf, zodat ze nooit uit elkaar lopen.
     *
     * Dat luistert nauw: je pc heeft twee "eigen" adressen, 127.0.0.1 (IPv4) en ::1 (IPv6).
     * Dat zijn aparte deuren. Spotify stuurt je browser naar 127.0.0.1, dus daar moeten we
     * wachten. Luisterden we op ::1, dan klopt je browser op een deur waar niemand zit en
     * krijg je "kan geen verbinding maken met 127.0.0.1:8899".
     */
    private static final URI REDIRECT = URI.create(REDIRECT_URI);
    private static final int CALLBACK_PORT = REDIRECT.getPort();
    /** Zo lang mag je erover doen om in te loggen. */
    private static final int LINK_TIMEOUT_MS = 180_000;
    /** De luisteraar van een koppelpoging die nog loopt, zodat een tweede klik hem opruimt. */
    private static final AtomicReference<ServerSocket> PENDING = new AtomicReference<>();
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
        return FMLPaths.CONFIGDIR.get().resolve("imttech-spotify.json");
    }

    /** Is Spotify al gekoppeld? */
    public static synchronized boolean isLinked() {
        load();
        return refreshToken != null && !refreshToken.isBlank();
    }

    /** De poort waarop we op Spotify wachten (voor de foutmelding als hij bezet is). */
    public static int callbackPort() {
        return CALLBACK_PORT;
    }

    /** Heb je al een Client ID ingevuld? (Zonder dat kan het koppelen niet beginnen.) */
    public static boolean hasClientId() {
        return !clientId().isBlank();
    }

    private static String clientId() {
        return ImtClientConfig.SPEC.isLoaded() ? ImtClientConfig.SPOTIFY_CLIENT_ID.get().trim() : "";
    }

    /** Vergeet de koppeling (bv. als je een ander account wil gebruiken). */
    public static synchronized void unlink() {
        refreshToken = null;
        accessToken = null;
        try {
            Files.deleteIfExists(tokenFile());
        } catch (IOException e) {
            ImtTech.LOGGER.warn("I.M.T: could not delete {}", tokenFile(), e);
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
            ImtTech.LOGGER.warn("I.M.T: cannot read {}", file, e);
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
            ImtTech.LOGGER.warn("I.M.T: cannot save the Spotify token", e);
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
        // Stond er nog een oude poging te wachten? Die laten we los, anders is de poort bezet.
        closePending();
        Thread thread = new Thread(() -> {
            ServerSocket server;
            try {
                // Eerst luisteren, dan pas de browser openen: ben je al ingelogd bij Spotify,
                // dan is je browser sneller terug dan wij de deur open kunnen zetten.
                server = openCallbackServer();
            } catch (IOException e) {
                ImtTech.LOGGER.warn("I.M.T: cannot listen on {} (port in use?)", REDIRECT_URI, e);
                done.accept("port_in_use");
                return;
            }
            PENDING.set(server);
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
                Callback callback = waitForCallback(server);
                if (callback == null) {
                    done.accept("timeout");
                    return;
                }
                if (callback.error() != null) {
                    done.accept(callback.error());
                    return;
                }
                exchangeCode(id, callback.code(), verifier, done);
            } catch (Exception e) {
                // Is er intussen een nieuwe poging gestart? Dan hoort deze melding daar niet meer bij.
                if (PENDING.get() == server) {
                    ImtTech.LOGGER.warn("I.M.T: linking with Spotify failed", e);
                    done.accept(e.getClass().getSimpleName());
                }
            } finally {
                PENDING.compareAndSet(server, null);
                close(server);
            }
        }, "I.M.T Spotify link");
        thread.setDaemon(true);
        thread.start();
    }

    /** Breekt een koppelpoging af die nog op je browser staat te wachten. */
    private static void closePending() {
        close(PENDING.getAndSet(null));
    }

    private static void close(@Nullable ServerSocket server) {
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
                // Dichtdoen mag best mislukken; we openen toch een nieuwe.
            }
        }
    }

    /** Wat je browser terugbracht: een code, of een fout van Spotify (bv. je klikte op "Cancel"). */
    private record Callback(@Nullable String code, @Nullable String error) {}

    /**
     * Zet het piepkleine webservertje klaar, op precies het adres uit REDIRECT_URI.
     * Het staat alleen open op jouw eigen pc; niemand anders kan erbij.
     */
    private static ServerSocket openCallbackServer() throws IOException {
        ServerSocket server = new ServerSocket();
        // Zonder dit blijft de poort na een afgebroken poging nog even "bezet" van Windows.
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(InetAddress.getByName(REDIRECT.getHost()), CALLBACK_PORT), 5);
        return server;
    }

    /**
     * Wacht tot Spotify je browser terugstuurt en haalt de code uit die ene regel.
     *
     * We blijven luisteren tot er echt een antwoord van Spotify langskomt. Browsers leggen
     * namelijk soms alvast een lege verbinding aan, of vragen een favicon op; stopten we na
     * de eerste de beste verbinding, dan misten we het echte antwoord.
     *
     * @return de code of de fout, of null als je er te lang over deed
     */
    private static @Nullable Callback waitForCallback(ServerSocket server) throws IOException {
        long deadline = System.currentTimeMillis() + LINK_TIMEOUT_MS;
        while (true) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                return null;
            }
            server.setSoTimeout((int) left);
            try (Socket socket = server.accept();
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String requestLine = in.readLine();
                if (requestLine == null) {
                    continue; // een lege verbinding: de browser warmde alleen even op
                }
                String code = queryValue(requestLine, "code");
                String error = queryValue(requestLine, "error");
                if (code == null && error == null) {
                    continue; // iets anders dan het antwoord van Spotify (bv. /favicon.ico)
                }
                // Een vriendelijk berichtje in de browser.
                respond(socket, code != null
                        ? "<h2>I.M.T is connected to Spotify.</h2><p>You can close this tab.</p>"
                        : "<h2>Something went wrong.</h2><p>Please try again from inside the game.</p>");
                return new Callback(code, error);
            } catch (SocketTimeoutException e) {
                return null;
            }
        }
    }

    /** Haalt één waarde uit de vraagregel van je browser: "GET /imttech?code=... HTTP/1.1". */
    private static @Nullable String queryValue(String requestLine, String key) {
        int start = requestLine.indexOf('?');
        if (start < 0) {
            return null;
        }
        int space = requestLine.indexOf(' ', start);
        String query = space < 0 ? requestLine.substring(start + 1) : requestLine.substring(start + 1, space);
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            if (equals > 0 && part.substring(0, equals).equals(key)) {
                return URLDecoder.decode(part.substring(equals + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void respond(Socket socket, String page) throws IOException {
        byte[] body = ("<html><body style='font-family:sans-serif'>" + page + "</body></html>")
                .getBytes(StandardCharsets.UTF_8);
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                + body.length + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
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
            ImtTech.LOGGER.warn("I.M.T: Spotify gaf {} terug: {}", response.statusCode(), response.body());
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
                ImtTech.LOGGER.warn("I.M.T: Spotify-token vernieuwen mislukte ({})", response.statusCode());
                return null;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            setAccessToken(json);
            if (json.has("refresh_token")) {
                saveRefreshToken(json.get("refresh_token").getAsString());
            }
            return accessToken;
        } catch (Exception e) {
            ImtTech.LOGGER.warn("I.M.T: Spotify-token vernieuwen mislukte", e);
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
