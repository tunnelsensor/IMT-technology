package com.senne.imttech.client.voice;

import com.senne.imttech.client.ImtClientConfig;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * De twee stem-functies van I.M.T. Je installeert en zet ze aan/uit in het G-menu
 * (tabblad Systemen).
 */
public enum VoiceFeature {
    /** I.M.T spreekt haar meldingen uit (Piper, tekst naar spraak). */
    SPEECH("speech"),
    /** Jij praat tegen I.M.T: het oor schrijft het uit, het woordenboek zegt wat ze doet. */
    COMMANDS("commands"),
    /** Het lokale brein: een taalmodel op je pc dat gewone taal begrijpt (zie ImtBrain). */
    BRAIN("brain");

    private final String id;

    VoiceFeature(String id) {
        this.id = id;
    }

    /** Welke onderdelen deze functie nodig heeft. Voor de stem hangt dat af van je taal. */
    public List<VoicePack> packs() {
        return switch (this) {
            case SPEECH -> List.of(VoicePack.PIPER, ImtSpeech.voicePack());
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

    /** Staat de functie aan (in config/imttech-client.toml)? */
    public boolean isEnabled() {
        if (!ImtClientConfig.SPEC.isLoaded()) {
            return false;
        }
        return switch (this) {
            case SPEECH -> ImtClientConfig.VOICE.get();
            case COMMANDS -> ImtClientConfig.VOICE_COMMANDS.get();
            case BRAIN -> ImtClientConfig.BRAIN.get() == BrainMode.LOCAL;
        };
    }

    public void setEnabled(boolean enabled) {
        if (!ImtClientConfig.SPEC.isLoaded()) {
            return;
        }
        if (this == BRAIN) {
            ImtBrain.setMode(enabled ? BrainMode.LOCAL : BrainMode.DICTIONARY);
            return;
        }
        (this == SPEECH ? ImtClientConfig.VOICE : ImtClientConfig.VOICE_COMMANDS).set(enabled);
        ImtClientConfig.SPEC.save();
    }

    /** Naam in het G-menu, bv. "Stem". */
    public Component menuLabel() {
        return Component.translatable(switch (this) {
            case SPEECH -> "screen.imttech.menu.voice";
            case COMMANDS -> "screen.imttech.menu.voice_commands";
            case BRAIN -> "screen.imttech.menu.brain";
        });
    }

    /** Naam in een zin, bv. "Ik download [mijn stem]". */
    public Component spokenName() {
        return Component.translatable("imt.imttech.voice.name." + id);
    }

    /** Regel in de HUD tijdens het downloaden. */
    public Component downloadingLine(int percent) {
        return Component.translatable("hud.imttech.voice.downloading." + id, percent);
    }
}
