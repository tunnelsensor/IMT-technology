package com.senne.imttech.client.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Het "oor" van I.M.T: zet wat je zei (geluid) om in tekst, met het AI-model Qwen3-ASR.
 * Het model zegt er zelf bij in welke taal je praatte; I.M.T doet alleen iets met Engels
 * (zie EnglishOnly).
 */
final class ImtEar {
    private ImtEar() {}

    static final LlamaServer SERVER = new LlamaServer("ear", () -> List.of(
            "-m", VoicePack.EAR.dir().resolve("model.gguf").toString(),
            "--mmproj", VoicePack.EAR.dir().resolve("audio.gguf").toString()));

    /** Geluid (16000 metingen per seconde, 16 bit, mono) omzetten in tekst (en de taal). */
    static EnglishOnly.Heard transcribe(byte[] pcm) throws Exception {
        SERVER.start();
        JsonObject audio = new JsonObject();
        audio.addProperty("data", Base64.getEncoder().encodeToString(wav(pcm)));
        audio.addProperty("format", "wav");
        JsonObject part = new JsonObject();
        part.addProperty("type", "input_audio");
        part.add("input_audio", audio);
        JsonArray content = new JsonArray();
        content.add(part);
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.add("content", content);
        JsonArray messages = new JsonArray();
        messages.add(message);
        JsonObject body = new JsonObject();
        body.add("messages", messages);
        body.addProperty("temperature", 0.0);
        body.addProperty("max_tokens", 120);

        String reply = SERVER.post("/v1/chat/completions", body.toString(), Duration.ofSeconds(30));
        String text = JsonParser.parseString(reply).getAsJsonObject()
                .getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
        return EnglishOnly.parse(text);
    }

    /** Maakt van losse geluidsmetingen een echt WAV-bestand (met de 44 bytes "kop" ervoor). */
    private static byte[] wav(byte[] pcm) {
        int rate = 16000;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36 + pcm.length).put("WAVE".getBytes());
        header.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        header.put("data".getBytes()).putInt(pcm.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
        out.writeBytes(header.array());
        out.writeBytes(pcm);
        return out.toByteArray();
    }
}
