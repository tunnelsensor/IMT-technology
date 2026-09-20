package com.senne.starktech.client.hud;

import com.senne.starktech.client.WorldProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Alles wat elk onderdeel van de HUD dit frame nodig heeft, in één pakketje.
 *
 * Er wordt er één per frame gemaakt (zie EdithHud), en die gaat langs alle onderdelen.
 * Zo hoeft geen enkel onderdeel zelf op te zoeken hoe groot je scherm is, hoe laat het is
 * of waar de camera hangt.
 */
public final class HudContext {
    public final Minecraft mc;
    public final GuiGraphics g;
    public final Font font;
    public final Player player;
    /** De bril die je op hebt (nooit leeg zolang de HUD tekent). */
    public final ItemStack glasses;
    public final int width;
    public final int height;
    /** De tijd van nu in milliseconden; alle animaties rekenen hiermee. */
    public final long now;
    /** Is er nog energie? Zo niet, dan tekenen we alleen het lege frame. */
    public final boolean powered;
    /** Staat het ronde G-menu open? Dan blijft alles wat plat op je scherm staat weg. */
    public final boolean menuOpen;
    /** Rekent een plek in de wereld om naar een plek op je scherm. */
    public final WorldProjection projection;

    /**
     * Hoe ver het paneel links doorloopt. Het statuspaneel schrijft dit, en de meldingen
     * lezen het zodat ze er netjes naast komen te staan in plaats van eroverheen.
     */
    public int panelRight;

    public HudContext(Minecraft mc, GuiGraphics g, Font font, Player player, ItemStack glasses,
                      int width, int height, long now, boolean powered, boolean menuOpen,
                      WorldProjection projection) {
        this.mc = mc;
        this.g = g;
        this.font = font;
        this.player = player;
        this.glasses = glasses;
        this.width = width;
        this.height = height;
        this.now = now;
        this.powered = powered;
        this.menuOpen = menuOpen;
        this.projection = projection;
    }

    /** Waar de camera is (je ogen, of je verkenningsdrone). */
    public Vec3 cameraPosition() {
        return mc.gameRenderer.getMainCamera().getPosition();
    }

    /** Onthoudt dat het paneel links tot hier doorloopt. */
    public void claimPanelWidth(int right) {
        panelRight = Math.max(panelRight, right);
    }
}
