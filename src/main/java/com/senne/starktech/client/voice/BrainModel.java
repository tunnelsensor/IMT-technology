package com.senne.starktech.client.voice;

import com.senne.starktech.StarkTech;
import com.senne.starktech.client.EdithClientConfig;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Welk lokaal taalmodel E.D.I.T.H. gebruikt: Qwen3.5 in drie maten. Groter = slimmer, maar het
 * heeft meer geheugen nodig en is trager.
 *
 * De mod kiest zelf, aan de hand van je pc (jouw keuze van 26 september):
 * - een videokaart met minstens 10 GB geheugen: LARGE (9B, 5,7 GB);
 * - een videokaart met minstens 6 GB: MEDIUM (4B, 2,7 GB);
 * - anders draait het op de processor, en dan is SMALL (2B, 1,3 GB) het snelst genoeg.
 *
 * Het oor (spraakherkenning) gebruikt de videokaart ook, daarom houden we wat ruimte over. De
 * keuze wordt één keer gemaakt en bewaard (brainModel in config/starktech-client.toml); daar
 * kun je hem ook zelf veranderen: auto, small, medium of large.
 */
public enum BrainModel {
    SMALL(VoicePack.BRAIN_SMALL, "2B"),
    MEDIUM(VoicePack.BRAIN_MEDIUM, "4B"),
    LARGE(VoicePack.BRAIN_LARGE, "9B");

    /** Zoveel videogeheugen (MiB) is nodig voor deze maat, naast het oor. */
    private static final int LARGE_VRAM = 10_000;
    private static final int MEDIUM_VRAM = 6_000;
    /** Een regel van llama-server --list-devices, bv. "Vulkan0: NVIDIA ... (8110 MiB, 7333 MiB free)". */
    private static final Pattern DEVICE = Pattern.compile("\\((\\d+) MiB");

    private final VoicePack pack;
    private final String size;

    BrainModel(VoicePack pack, String size) {
        this.pack = pack;
        this.size = size;
    }

    public VoicePack pack() {
        return pack;
    }

    /** "2B", "4B" of "9B" (miljard getallen in het model). */
    public String size() {
        return size;
    }

    /**
     * Het model dat nu geldt. Staat de keuze nog op "auto" (nog nooit gekozen), dan een snelle
     * gok op basis van je werkgeheugen; de echte keuze gebeurt in {@link #decide()}.
     */
    public static BrainModel current() {
        String saved = EdithClientConfig.SPEC.isLoaded() ? EdithClientConfig.BRAIN_MODEL.get() : "auto";
        for (BrainModel model : values()) {
            if (model.name().equalsIgnoreCase(saved)) {
                return model;
            }
        }
        return totalRamGb() >= 15 ? MEDIUM : SMALL; // een pc met "16 GB" meldt er net iets minder
    }

    /**
     * Kijkt naar je pc en legt de keuze vast (alleen als hij nog op "auto" staat). Duurt even
     * (start llama-server om de videokaart te vragen), dus niet op de hoofd-thread aanroepen.
     */
    public static BrainModel decide() {
        if (!EdithClientConfig.SPEC.isLoaded() || !EdithClientConfig.BRAIN_MODEL.get().equalsIgnoreCase("auto")) {
            return current();
        }
        int vram = largestGpuMib();
        BrainModel chosen = vram >= LARGE_VRAM ? LARGE : vram >= MEDIUM_VRAM ? MEDIUM : SMALL;
        StarkTech.LOGGER.info("E.D.I.T.H.: local brain {} chosen (video memory {} MiB, RAM {} GB)", chosen.size, vram, totalRamGb());
        EdithClientConfig.BRAIN_MODEL.set(chosen.name().toLowerCase(Locale.ROOT));
        EdithClientConfig.SPEC.save();
        return chosen;
    }

    /** Het grootste videogeheugen (MiB) dat llama.cpp ziet, of 0 (geen videokaart, of llama.cpp nog niet geïnstalleerd). */
    private static int largestGpuMib() {
        if (!VoicePack.LLAMA.isInstalled() || !EdithClientConfig.AI_GPU.get()) {
            return 0;
        }
        try {
            Process process = new ProcessBuilder(VoicePack.LLAMA.dir().resolve("llama-server.exe").toString(), "--list-devices")
                    .directory(VoicePack.LLAMA.dir().toFile())
                    .redirectErrorStream(true)
                    .start();
            int best = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = DEVICE.matcher(line);
                    if (matcher.find()) {
                        best = Math.max(best, Integer.parseInt(matcher.group(1)));
                    }
                }
            }
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            return best;
        } catch (Exception e) {
            StarkTech.LOGGER.warn("E.D.I.T.H.: could not ask llama.cpp about the video card", e);
            return 0;
        }
    }

    private static long totalRamGb() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            return os.getTotalMemorySize() / (1024L * 1024 * 1024);
        }
        return 8;
    }
}
