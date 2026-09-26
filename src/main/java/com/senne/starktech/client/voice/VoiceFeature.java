package com.senne.starktech.client.voice;

import com.senne.starktech.client.EdithClientConfig;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * De twee stem-functies van E.D.I.T.H. Je installeert en zet ze aan/uit in het G-menu
 * (tabblad Systemen).
 */
public enum VoiceFeature {
    /** E.D.I.T.H. spreekt haar meldingen uit (Piper, tekst naar spraak). */
    SPEECH("speech"),
    /** Jij praat tegen E.D.I.T.H.: het oor schrijft het uit, het woordenboek zegt wat ze doet. */
    COMMANDS("commands"),
    /** Het lokale brein: een taalmodel op je pc dat gewone taal begrijpt (zie EdithBrain). */
    BRAIN("brain");

    private final String id;

    VoiceFeature(String id) {
        this.id = id;
    }

    /** Welke onderdelen deze functie nodig heeft. Voor de stem hangt dat af van je taal. */
    public List<VoicePack> packs() {
        return switch (this) {
            case SPEECH -> List.of(VoicePack.PIPER, EdithSpeech.voicePack());
            case COMMANDS -> List.of(VoicePack.LLAMA, VoicePack.EAR);
            case BRAIN -> List.of(VoicePack.LLAMA, BrainModel.current().pack());
        };
    }

    public boolean isInstalled() {
        return packs().stream().allMatch(VoicePack::isInstalled);
    }

    /** Hoeveel er nog gedownload moet worden, bv. "86 MB" of "2.3 GB". */
    public String missingSize() {
        long bytes = packs().stream().filter(pack -> !pack.isInstalled()).mapToLong(VoicePack::size).sum();
        return bytes >= 1_000_000_000L
                ? String.format(Locale.ROOT, "%.1f GB", bytes / 1_000_000_000.0)
                : Math.max(1, Math.round(bytes / 1_000_000.0)) + " MB";
    }

    /** Staat de functie aan (in config/starktech-client.toml)? */
    public boolean isEnabled() {
        if (!EdithClientConfig.SPEC.isLoaded()) {
            return false;
        }
        return switch (this) {
            case SPEECH -> EdithClientConfig.VOICE.get();
            case COMMANDS -> EdithClientConfig.VOICE_COMMANDS.get();
            case BRAIN -> EdithClientConfig.BRAIN.get() == BrainMode.LOCAL;
        };
    }

    public void setEnabled(boolean enabled) {
        if (!EdithClientConfig.SPEC.isLoaded()) {
            return;
        }
        if (this == BRAIN) {
            EdithBrain.setMode(enabled ? BrainMode.LOCAL : BrainMode.DICTIONARY);
            return;
        }
        (this == SPEECH ? EdithClientConfig.VOICE : EdithClientConfig.VOICE_COMMANDS).set(enabled);
        EdithClientConfig.SPEC.save();
    }

    /** Naam in het G-menu, bv. "Stem". */
    public Component menuLabel() {
        return Component.translatable(switch (this) {
            case SPEECH -> "screen.starktech.menu.voice";
            case COMMANDS -> "screen.starktech.menu.voice_commands";
            case BRAIN -> "screen.starktech.menu.brain";
        });
    }

    /** Naam in een zin, bv. "Ik download [mijn stem]". */
    public Component spokenName() {
        return Component.translatable("edith.starktech.voice.name." + id);
    }

    /** Regel in de HUD tijdens het downloaden. */
    public Component downloadingLine(int percent) {
        return Component.translatable("hud.starktech.voice.downloading." + id, percent);
    }
}
