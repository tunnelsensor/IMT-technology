package com.senne.imttech.client;

import com.senne.imttech.client.hud.BootOverlay;
import com.senne.imttech.client.hud.CompassWidget;
import com.senne.imttech.client.hud.DroneFeedWidget;
import com.senne.imttech.client.hud.FlightWidget;
import com.senne.imttech.client.hud.HackBubbleWidget;
import com.senne.imttech.client.hud.HudContext;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.client.hud.HudWidget;
import com.senne.imttech.client.hud.MarkedTargetsWidget;
import com.senne.imttech.client.hud.NavigationMarkerWidget;
import com.senne.imttech.client.hud.NoPowerWidget;
import com.senne.imttech.client.hud.NotificationWidget;
import com.senne.imttech.client.hud.OreMarkerWidget;
import com.senne.imttech.client.hud.PingWidget;
import com.senne.imttech.client.hud.RepulsorWidget;
import com.senne.imttech.client.hud.SatelliteStrikeWidget;
import com.senne.imttech.client.hud.SpyTargetWidget;
import com.senne.imttech.client.hud.StatusPanelWidget;
import com.senne.imttech.client.hud.TargetAnalysisWidget;
import com.senne.imttech.client.hud.ThreatMarkerWidget;
import com.senne.imttech.client.hud.ZoomWidget;
import com.senne.imttech.imt.ImtData;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * De HUD van I.M.T: alles wat je "in de bril" ziet. Wordt elke frame getekend.
 *
 * Deze klasse tekent zelf bijna niets. Ze zet klaar wat elk onderdeel nodig heeft
 * (een HudContext) en laat daarna de onderdelen uit {@link #WIDGETS} hun ding doen,
 * op volgorde: wat onderaan de lijst staat, komt bovenop te liggen.
 *
 * Een nieuw onderdeel toevoegen? Maak een klasse die HudWidget is (zie de map hud/)
 * en zet hem in de lijst hieronder op de plek waar hij hoort.
 */
public class ImtHud implements LayeredDraw.Layer {

    /**
     * Alle onderdelen, in tekenvolgorde.
     *
     * Eerst alles wat in de wereld staat (markeringen op vijanden, ertsen, pings...), dan
     * wat plat op je scherm ligt. De meldingen staan expres achter het statuspaneel: ze
     * schuiven opzij als dat paneel breed is, en daarvoor moet het paneel eerst getekend zijn.
     */
    /** Apart, omdat de meldingen ook bij "toegang geweigerd" nog getekend worden. */
    private static final NotificationWidget NOTIFICATIONS = new NotificationWidget();

    private static final List<HudWidget> WIDGETS = List.of(
            new ThreatMarkerWidget(),
            new MarkedTargetsWidget(),
            new TargetAnalysisWidget(),
            new OreMarkerWidget(),
            new NavigationMarkerWidget(),
            new PingWidget(),
            new SpyTargetWidget(),
            new HackBubbleWidget(),
            new SatelliteStrikeWidget(),
            new CompassWidget(),
            new DroneFeedWidget(),
            new ZoomWidget(),
            new RepulsorWidget(),
            new FlightWidget(),
            new com.senne.imttech.client.hud.LandingPadWidget(),
            new NoPowerWidget(),
            new StatusPanelWidget(),
            NOTIFICATIONS);

    private final WorldProjection projection = new WorldProjection();

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || !ImtClientState.wearing) {
            return;
        }
        ItemStack glasses = ImtData.getWornGlasses(player);
        if (glasses.isEmpty()) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        long now = Util.getMillis();

        // Een bril van iemand anders: alleen "toegang geweigerd" en de meldingen.
        if (!ImtClientState.authorized) {
            BootOverlay.accessDenied(g, mc.font, glasses, w, h, now);
            new NotificationWidget().render(context(mc, g, player, glasses, w, h, now, false, false));
            return;
        }

        // Net opgezet: eerst de opstart-animatie, de rest komt daarna (en zolang die nog wacht
        // op het einde van het laadscherm, nog niets).
        if (ImtClientState.bootPending) {
            return;
        }
        long bootAge = now - ImtClientState.bootStartMillis;
        if (bootAge < BootOverlay.BOOT_MILLIS) {
            BootOverlay.boot(g, mc.font, w, h, bootAge, ImtClientState.bootStartMillis, player, glasses);
            return;
        }

        projection.begin(w, h, deltaTracker.getGameTimeDeltaPartialTick(false));
        boolean powered = ImtData.getEnergy(glasses) > 0;
        // Is het ronde G-menu open? Dan verdwijnt alles wat plat op je scherm staat (het paneel
        // links, het kompas, meldingen...), zodat niets de cirkel in het midden in de weg zit.
        // Markeringen in de wereld (vijanden, ertsen, pings...) blijven wel zichtbaar.
        boolean menuOpen = mc.screen instanceof ImtMenuScreen;
        HudContext c = context(mc, g, player, glasses, w, h, now, powered, menuOpen);

        BootOverlay.frame(g, w, h, powered ? HudStyle.CYAN : HudStyle.RED, 110);
        for (HudWidget widget : WIDGETS) {
            if (widget.visible(c)) {
                widget.render(c);
            }
        }
    }

    private HudContext context(Minecraft mc, GuiGraphics g, Player player, ItemStack glasses,
                               int w, int h, long now, boolean powered, boolean menuOpen) {
        return new HudContext(mc, g, mc.font, player, glasses, w, h, now, powered, menuOpen, projection);
    }
}
