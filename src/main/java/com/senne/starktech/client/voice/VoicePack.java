package com.senne.starktech.client.voice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * De onderdelen die E.D.I.T.H. downloadt voor haar stem en voor spraakbesturing. Ze zijn te
 * groot om in de mod zelf te stoppen (samen ± 1,1 GB), dus ze worden pas gedownload als je daar
 * zelf om vraagt in het G-menu (tabblad Systemen).
 *
 * Elk bestand heeft een vaste SHA-256-"vingerafdruk". Klopt die niet (beschadigd of vervalst
 * bestand), dan gooit de installer het weg en wordt het niet gebruikt. De vingerafdrukken zijn
 * gecontroleerd met die van HuggingFace (stemmen en spraakherkenning) en GitHub (llama.cpp).
 *
 * Voorlopig alleen voor Windows (64-bit): daar is alles getest.
 */
public enum VoicePack {
    /** Piper: het programma dat tekst omzet in spraak. */
    PIPER(zip("https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip",
            22_477_236L, "f3c58906402b24f3a96d92145f58acba6d86c9b5db896d207f78dc80811efcea")),
    /** De Engelse stem van E.D.I.T.H.: "Lessac". */
    VOICE_EN(
            file("https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/lessac/medium/en_US-lessac-medium.onnx",
                    "voice.onnx", 63_201_294L, "5efe09e69902187827af646e1a6e9d269dee769f9877d17b16b1b46eeaaf019f"),
            file("https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json",
                    "voice.onnx.json", 4_885L, "efe19c417bed055f2d69908248c6ba650fa135bc868b0e6abb3da181dab690a0")),
    /**
     * llama.cpp: het programma dat de spraakherkenning laat draaien (op je videokaart via Vulkan,
     * of anders op de processor).
     */
    LLAMA(flatZip("https://github.com/ggml-org/llama.cpp/releases/download/b11047/llama-b11047-bin-win-vulkan-x64.zip",
            31_837_591L, "fa7c949c4b75871b9719ac3f401c30813aec860f8fba9c06dfb3b98318ac2155")),
    /**
     * Het "oor": Qwen3-ASR zet je stem om in tekst. Het herkent 30 talen, maar E.D.I.T.H. doet
     * alleen iets met Engels (zie EnglishOnly).
     */
    EAR(
            file("https://huggingface.co/ggml-org/Qwen3-ASR-0.6B-GGUF/resolve/928ab958557df9aa2ef1c93e0e83c7ad0933fae2/Qwen3-ASR-0.6B-Q8_0.gguf",
                    "model.gguf", 804_749_248L, "bca259818b50ca7c4c05e9bdb35a5dc04fa039653a6d6f3f0f331f96f6aa1971"),
            file("https://huggingface.co/ggml-org/Qwen3-ASR-0.6B-GGUF/resolve/928ab958557df9aa2ef1c93e0e83c7ad0933fae2/mmproj-Qwen3-ASR-0.6B-Q8_0.gguf",
                    "audio.gguf", 214_392_480L, "41a342b5e4c514e968cb756de6cd1b7be39eff43c44c57a2ef5fc6522e36603d")),
    /*
     * Het lokale brein (zie BrainModel): Qwen3.5 in drie maten, telkens Q4_K_M (ingepakt tot een
     * kwart van de grootte, met weinig verlies). De vingerafdrukken komen van HuggingFace.
     */
    BRAIN_SMALL(file("https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/f6d5376be1edb4d416d56da11e5397a961aca8ae/Qwen3.5-2B-Q4_K_M.gguf", "model.gguf", 1_280_835_840L, "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223")),
    BRAIN_MEDIUM(file("https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/e87f176479d0855a907a41277aca2f8ee7a09523/Qwen3.5-4B-Q4_K_M.gguf", "model.gguf", 2_740_937_888L, "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4")),
    BRAIN_LARGE(file("https://huggingface.co/unsloth/Qwen3.5-9B-GGUF/resolve/3885219b6810b007914f3a7950a8d1b469d598a5/Qwen3.5-9B-Q4_K_M.gguf", "model.gguf", 5_680_522_464L, "03b74727a860a56338e042c4420bb3f04b2fec5734175f4cb9fa853daf52b7e8"));


    /** Wat er met een download gebeurt. */
    public enum Kind {
        /** Gewoon bewaren onder de gegeven naam. */
        FILE,
        /** Zip uitpakken; de bovenste map in de zip (bv. "piper/") laten we weg. */
        ZIP_IN_FOLDER,
        /** Zip uitpakken zoals hij is (de bestanden zitten niet in een map). */
        FLAT_ZIP
    }

    /**
     * Eén bestand om te downloaden.
     *
     * @param fileName onder welke naam het bewaard wordt (alleen bij Kind.FILE)
     */
    public record Download(String url, Kind kind, @Nullable String fileName, long size, String sha256) {}

    /** Dit bestandje wordt pas geschreven als alles gedownload, gecontroleerd en uitgepakt is. */
    private static final String MARKER = "installed.txt";

    private final List<Download> downloads;

    VoicePack(Download... downloads) {
        this.downloads = List.of(downloads);
    }

    private static Download zip(String url, long size, String sha256) {
        return new Download(url, Kind.ZIP_IN_FOLDER, null, size, sha256);
    }

    private static Download flatZip(String url, long size, String sha256) {
        return new Download(url, Kind.FLAT_ZIP, null, size, sha256);
    }

    private static Download file(String url, String fileName, long size, String sha256) {
        return new Download(url, Kind.FILE, fileName, size, sha256);
    }

    public List<Download> downloads() {
        return downloads;
    }

    /** Totale downloadgrootte in bytes. */
    public long size() {
        return downloads.stream().mapToLong(Download::size).sum();
    }

    /** De map waar dit onderdeel komt, bv. .minecraft/starktech/voice/piper. */
    public Path dir() {
        return VoiceInstaller.root().resolve(name().toLowerCase(Locale.ROOT));
    }

    /** Onthouden of het geïnstalleerd is, zodat we niet elke frame op de harde schijf moeten kijken. */
    private volatile @Nullable Boolean installed;

    public boolean isInstalled() {
        Boolean known = installed;
        if (known == null) {
            known = Files.isRegularFile(marker());
            installed = known;
        }
        return known;
    }

    /** Na (her)installeren: opnieuw op de harde schijf kijken. */
    void forgetInstalledState() {
        installed = null;
    }

    Path marker() {
        return dir().resolve(MARKER);
    }
}
