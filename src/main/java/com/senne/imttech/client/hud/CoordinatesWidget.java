package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.CYAN;
import static com.senne.imttech.client.hud.HudStyle.WHITE;
import static com.senne.imttech.client.hud.HudStyle.argb;

import com.senne.imttech.client.ImtClientConfig;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Je coördinaten rechtsboven: {@code X 0  Y -60  Z -4} (hoofdstuk 82). Links staan je batterij en wat er actief is
 * (StatusPanelWidget), rechts je coördinaten. Aan/uit via G > Navigation > Coordinates.
 *
 * Rechtsboven tekent Minecraft ook de icoontjes van je effecten (een drankje van snelheid, gif...). Zolang je de
 * coördinaten ziet, schuiven die icoontjes een stukje naar onder (zie {@link #onEffectsPre}), zodat de coördinaten
 * altijd op dezelfde plek blijven staan en er niets over elkaar valt. Zet je ze uit, dan staan de icoontjes weer
 * gewoon bovenaan.
 */
public final class CoordinatesWidget implements HudWidget {
    /** Even ver van de rand als het paneel links (zie StatusPanelWidget). */
    private static final int MARGIN = 10;
    /** Zoveel pixels schuiven de effect-icoontjes naar onder: net onder de coördinaten (en hun schaduw). */
    private static final int EFFECTS_DOWN = 21;

    /** Staan de coördinaten deze frame op je scherm? (De HUD van de bril wordt vóór de effecten getekend.) */
    private static boolean drawnThisFrame;
    /** Hebben we de effecten nu verschoven? Dan zetten we dat na de effecten weer terug. */
    private static boolean effectsShifted;

    @Override
    public boolean visible(HudContext c) {
        return c.powered && !c.menuOpen && ImtClientConfig.coordinates();
    }

    @Override
    public void render(HudContext c) {
        // De letters cyaan, de getallen wit. Gewoon het getal (zoals "Block" in F3), zonder punten of "k".
        Component text = Component.translatable("hud.imttech.coords", number(c.player.getBlockX()),
                number(c.player.getBlockY()), number(c.player.getBlockZ()));
        c.g.drawString(c.font, text, c.width - MARGIN - c.font.width(text), MARGIN, argb(255, CYAN), true);
        drawnThisFrame = true;
    }

    /** Eén getal van je coördinaten, in het wit. */
    private static Component number(int value) {
        return Component.literal(Integer.toString(value)).withColor(WHITE);
    }

    /**
     * Elke frame vóór de HUD van de bril (ImtHud): nog niets getekend. Elke frame tekent Minecraft met een nieuwe
     * GuiGraphics, dus een verschuiving die vorige frame niet teruggezet werd (een andere mod schrapte de effecten na
     * ons), telt nu niet meer.
     */
    public static void newFrame() {
        drawnThisFrame = false;
        effectsShifted = false;
    }

    /**
     * Net voor Minecraft de effect-icoontjes tekent: staan de coördinaten er, dan alles een stukje naar onder. Als
     * laatste (LOWEST, zie ImtTechClient): heeft een andere mod de icoontjes al geschrapt, dan komen we hier niet.
     */
    public static void onEffectsPre(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.EFFECTS) || !drawnThisFrame || effectsShifted) {
            return;
        }
        event.getGuiGraphics().pose().pushPose();
        event.getGuiGraphics().pose().translate(0, EFFECTS_DOWN, 0);
        effectsShifted = true;
    }

    /** Na de effect-icoontjes: de verschuiving weer weg. */
    public static void onEffectsPost(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.EFFECTS)) {
            return;
        }
        if (effectsShifted) {
            event.getGuiGraphics().pose().popPose();
            effectsShifted = false;
        }
        drawnThisFrame = false;
    }
}
