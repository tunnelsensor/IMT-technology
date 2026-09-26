package com.senne.starktech.client.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.util.List;

/**
 * Het lokale brein: een Qwen3.5-model (zie BrainModel) in een eigen llama-server op je pc,
 * naast het oor. Gratis en zonder internet, maar minder slim dan Claude.
 *
 * Het antwoord moet het JSON-schema van BrainPrompt volgen: llama.cpp maakt daar een
 * "grammatica" van, zodat het model letterlijk niets anders kan schrijven. De eerste keer
 * duurt dat een tijdje (opwarmen); daarna ± 1 seconde per zin op de videokaart.
 */
final class LocalBrain {
    private LocalBrain() {}

    static final LlamaServer SERVER = new LlamaServer("brain", () -> List.of(
            "-m", BrainModel.current().pack().dir().resolve("model.gguf").toString()));

    /** Stelt de vraag en geeft het JSON-antwoord terug (op een achtergrond-thread). */
    static String ask(String system, List<EdithMemory.Turn> history, String message) throws Exception {
        SERVER.start();
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        for (EdithMemory.Turn turn : history) {
            messages.add(message("user", "PLAYER SAID: \"" + turn.said() + "\""));
            messages.add(message("assistant", turn.answer()));
        }
        messages.add(message("user", message));

        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        JsonObject schema = new JsonObject();
        schema.addProperty("name", "decision");
        schema.addProperty("strict", true);
        schema.add("schema", JsonParser.parseString(BrainPrompt.toJson(BrainPrompt.schema())));
        format.add("json_schema", schema);
        // Qwen3.5 kan eerst "nadenken" voor het antwoordt; voor een spraakcommando is dat te traag.
        JsonObject template = new JsonObject();
        template.addProperty("enable_thinking", false);

        JsonObject body = new JsonObject();
        body.add("messages", messages);
        body.addProperty("temperature", 0.2);
        body.addProperty("max_tokens", 400);
        body.add("response_format", format);
        body.add("chat_template_kwargs", template);

        String reply = SERVER.post("/v1/chat/completions", body.toString(), Duration.ofSeconds(90));
        return JsonParser.parseString(reply).getAsJsonObject()
                .getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }
}
