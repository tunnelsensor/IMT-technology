package com.senne.imttech.client.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/**
 * Wat het brein (lokaal model of Claude) besloot na jouw zin. Beide geven precies dit
 * JSON-object terug (zie BrainPrompt.schema()):
 *
 * <pre>
 * {"reply": "Op weg naar Thuis.",
 *  "actions": [{"action": "navigate_to", "value": "Thuis"}],
 *  "remember": "", "forget": "", "unsure": false}
 * </pre>
 *
 * @param reply    wat ze hardop zegt (mag leeg zijn)
 * @param actions  wat de bril doet, in deze volgorde
 * @param remember een blijvend feitje om te onthouden, of leeg
 * @param forget   een onthouden feitje om te vergeten, of leeg
 * @param unsure   ze weet niet zeker wat je bedoelde: dan vraagt ze het ("bedoelde je ...?")
 * @param json     het antwoord zoals het binnenkwam (voor het gesprek, zie ImtMemory)
 */
public record BrainDecision(String reply, List<Call> actions, String remember, String forget, boolean unsure, String json) {

    /** Eén actie met zijn waarde (plek, naam, liedje, bericht; anders leeg). */
    public record Call(VoiceAction action, String value) {}

    /**
     * Leest het antwoord van het brein. Acties die niet bestaan laten we weg (een taalmodel
     * verzint er soms een); is het geen geldig JSON-object, dan een IllegalArgumentException.
     */
    public static BrainDecision parse(String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("brain answer is not JSON: " + json, e);
        }
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("brain answer is not a JSON object: " + json);
        }
        JsonObject object = root.getAsJsonObject();
        List<Call> calls = new ArrayList<>();
        if (object.has("actions") && object.get("actions").isJsonArray()) {
            JsonArray array = object.getAsJsonArray("actions");
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                VoiceAction action = VoiceAction.byId(text(element.getAsJsonObject(), "action"));
                if (action != null) {
                    calls.add(new Call(action, action.needsValue() ? text(element.getAsJsonObject(), "value") : ""));
                }
            }
        }
        boolean unsure = object.has("unsure") && object.get("unsure").isJsonPrimitive() && object.get("unsure").getAsBoolean();
        return new BrainDecision(text(object, "reply"), unsure ? List.of() : List.copyOf(calls),
                text(object, "remember"), text(object, "forget"), unsure, json.trim());
    }

    private static String text(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString().trim() : "";
    }
}
