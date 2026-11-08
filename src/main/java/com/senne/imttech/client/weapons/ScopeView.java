package com.senne.imttech.client.weapons;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De kijker van de sniper in derde persoon (8 november, jouw opmerking: "als je nu met een sniper scopet in F5 dan zie
 * je jezelf door de scope"). In F5 hangt de camera achter je hoofd (of ervoor): door de kijker zag je dus je eigen
 * hoofd. Nu kijk je, zolang de kijker op je scherm staat, door je eigen ogen zoals in eerste persoon, en gaat hij weg,
 * dan sta je meteen weer in F5. We wisselen vlak voor elk beeld, precies wanneer de kijker verschijnt of weggaat
 * (GunControl.scoped): zo dekt de kijker de sprong van de camera af.
 * <p>
 * Druk je F5 terwijl je door de kijker kijkt, dan blijf je erdoor kijken en kies je de stand waarin je straks
 * terugkomt. Minecraft bewaart de stand van de camera niet in options.txt: ook als het spel stopt terwijl je richt,
 * blijft er niets verkeerd staan.
 */
public final class ScopeView {
    private ScopeView() {}

    /** De stand van de camera van vóór de kijker (F5 van achteren of van voren), of null als wij niets veranderden. */
    private static @Nullable CameraType before;

    /** Vlak voor elk beeld: door de kijker kijk je door je eigen ogen; zonder kijker terug naar je eigen stand. */
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // Alleen als jij de camera bent (niet als je door een drone kijkt).
        boolean scoped = mc.player != null && mc.getCameraEntity() == mc.player && GunControl.scoped(partialTick);
        CameraType now = mc.options.getCameraType();
        if (scoped) {
            if (!now.isFirstPerson()) {
                // Je kijkt door de kijker terwijl de camera op F5 staat: die stand onthouden we. Hadden wij de camera al
                // op eerste persoon gezet, dan drukte je F5 tijdens het richten: dat kiest de volgende stand voor straks,
                // net zoals F5 zelf de standen rondgaat.
                before = before == null ? now : before.cycle();
                set(mc, CameraType.FIRST_PERSON);
            }
        } else if (before != null) {
            // De kijker is weg. Heeft iets anders de camera intussen al verzet, dan laten we die stand staan.
            if (now.isFirstPerson()) {
                set(mc, before);
            }
            before = null;
        }
    }

    /** De camera verzetten, zoals Minecraft zelf doet bij F5 (dan rekent hij opnieuw uit welke stukken wereld je ziet). */
    private static void set(Minecraft mc, CameraType type) {
        mc.options.setCameraType(type);
        mc.levelRenderer.needsUpdate();
    }
}
