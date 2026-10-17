package com.senne.imttech.client.voice;

import com.senne.imttech.ImtTech;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * I.M.T leert van jouw manier van praten: begrijpt het brein een zin die nog niet in het
 * woordenboek stond, dan zet ze die zin erbij, bij een commando "Learned: ..." met dezelfde
 * acties. Staat het brein uit of is het niet bereikbaar, dan begrijpt ze jouw zin toch.
 *
 * Wat ze niet leert: een "ja" op haar vraag (dat hangt van de vraag af), zinnen van één woord,
 * lange zinnen (te toevallig), en acties met een waarde (plek, naam, liedje: die verschillen
 * elke keer). In G > Systemen > Commando's zie je wat ze leerde, en kun je het weghalen.
 */
final class VoiceLearning {
    private VoiceLearning() {}

    static final String PREFIX = "Learned: ";
    private static final int MAX_WORDS = 8;

    static void learn(String heard, List<BrainDecision.Call> calls) {
        String phrase = normalize(heard);
        int words = phrase.isEmpty() ? 0 : phrase.split(" ").length;
        if (calls.isEmpty() || words < 2 || words > MAX_WORDS
                || calls.stream().anyMatch(call -> call.action().needsValue() || call.action().id().startsWith("say_"))) {
            return;
        }
        // Begrijpt het woordenboek deze zin al? Dan hoeft ze niets bij te leren.
        if (!VoiceCommandStore.match(heard).isEmpty()) {
            return;
        }
        String name = PREFIX + calls.stream().map(call -> call.action().id()).collect(Collectors.joining(", "));
        VoiceDictionary dictionary = VoiceCommandStore.dictionary().copy();
        VoiceDictionary.Command command = dictionary.commands.stream()
                .filter(existing -> existing.name.equals(name))
                .findFirst()
                .orElse(null);
        if (command == null) {
            command = new VoiceDictionary.Command(name);
            for (BrainDecision.Call call : calls) {
                command.actions.add(new VoiceDictionary.ActionRef(call.action(), ""));
            }
            dictionary.commands.add(command);
        }
        if (command.phrases.contains(phrase)) {
            return;
        }
        command.phrases.add(phrase);
        try {
            VoiceCommandStore.save(dictionary);
            ImtTech.LOGGER.info("I.M.T: learned \"{}\" -> {}", phrase, name);
        } catch (IOException e) {
            ImtTech.LOGGER.warn("I.M.T: could not save the learned phrase", e);
        }
    }

    /** Kleine letters, geen leestekens, enkele spaties. */
    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
