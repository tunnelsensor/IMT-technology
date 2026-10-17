package com.senne.imttech.client.voice;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.senne.imttech.client.ImtClientConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * Het brein in de cloud: Claude van Anthropic, via de officiële Java-SDK en jouw eigen
 * API-sleutel (in te vullen in G > Systemen > AI Brain).
 *
 * Per zin één vraag aan Claude (claude-opus-5-5, tenzij je in config/imttech-client.toml iets
 * anders kiest), met:
 * - "effort: low": een spraakcommando moet snel gaan, diep nadenken hoeft niet;
 * - "structured outputs": het antwoord volgt gegarandeerd het JSON-schema van BrainPrompt;
 * - "prompt caching": de lange instructies worden onthouden, dus volgende zinnen zijn sneller
 *   en goedkoper;
 * - "fallbacks: default": weigert Claude een vraag (zijn veiligheidsfilters kunnen soms te
 *   streng zijn), dan probeert de API het meteen met een ander Claude-model.
 *
 * Je sleutel staat alleen op je eigen pc (.minecraft/imttech/ai/claude-key.txt) en gaat
 * alleen naar Anthropic, nooit naar de server waarop je speelt.
 */
final class ClaudeBrain {
    private ClaudeBrain() {}

    /** Waarom het niet lukte, als vertaalsleutel voor wat I.M.T zegt. */
    static final class Failure extends Exception {
        final String reason;

        Failure(String reason, @Nullable Throwable cause) {
            super(reason, cause);
            this.reason = reason;
        }
    }

    private static @Nullable AnthropicClient client;
    private static @Nullable String clientKey;

    // ================================================================ De sleutel

    static Path keyFile() {
        return FMLPaths.GAMEDIR.get().resolve("imttech").resolve("ai").resolve("claude-key.txt");
    }

    /** De bewaarde API-sleutel, of een lege tekst. */
    static String key() {
        try {
            return Files.exists(keyFile()) ? Files.readString(keyFile(), StandardCharsets.UTF_8).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    static boolean hasKey() {
        return !key().isEmpty();
    }

    /** Bewaart de sleutel (leeg = wissen). */
    static void setKey(String key) throws IOException {
        String clean = key.trim();
        if (clean.isEmpty()) {
            Files.deleteIfExists(keyFile());
        } else {
            Files.createDirectories(keyFile().getParent());
            Files.writeString(keyFile(), clean, StandardCharsets.UTF_8);
        }
    }

    private static synchronized AnthropicClient client(String key) {
        if (client == null || !key.equals(clientKey)) {
            client = AnthropicOkHttpClient.builder()
                    .apiKey(key)
                    .timeout(Duration.ofSeconds(45))
                    .maxRetries(1)
                    .build();
            clientKey = key;
        }
        return client;
    }

    // ================================================================ De vraag

    /** Stelt de vraag en geeft het JSON-antwoord terug (op een achtergrond-thread). */
    static String ask(String system, List<ImtMemory.Turn> history, String message) throws Failure {
        String key = key();
        if (key.isEmpty()) {
            throw new Failure("no_key", null);
        }
        BetaJsonOutputFormat.Schema.Builder schema = BetaJsonOutputFormat.Schema.builder();
        for (Map.Entry<String, Object> entry : BrainPrompt.schema().entrySet()) {
            schema.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
        }
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(ImtClientConfig.CLAUDE_MODEL.get())
                .maxTokens(4096L)
                .system(system)
                .cacheControl(BetaCacheControlEphemeral.builder().build())
                .outputConfig(BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.LOW)
                        .format(BetaJsonOutputFormat.builder().schema(schema.build()).build())
                        .build())
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        for (ImtMemory.Turn turn : history) {
            params.addUserMessage("PLAYER SAID: \"" + turn.said() + "\"");
            params.addAssistantMessage(turn.answer());
        }
        params.addUserMessage(message);

        BetaMessage response;
        try {
            response = client(key).beta().messages().create(params.build());
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new Failure("bad_key", e);
        } catch (RateLimitException e) {
            throw new Failure("busy", e);
        } catch (AnthropicServiceException e) {
            throw new Failure("api_error", e);
        } catch (AnthropicIoException e) {
            throw new Failure("offline", e);
        }
        // Eerst de reden van stoppen bekijken, pas dan de inhoud.
        BetaStopReason stop = response.stopReason().orElse(null);
        if (BetaStopReason.REFUSAL.equals(stop)) {
            throw new Failure("refused", null);
        }
        if (BetaStopReason.MAX_TOKENS.equals(stop)) {
            throw new Failure("api_error", null);
        }
        StringBuilder text = new StringBuilder();
        for (BetaContentBlock block : response.content()) {
            block.text().ifPresent(textBlock -> text.append(textBlock.text()));
        }
        return text.toString();
    }
}
