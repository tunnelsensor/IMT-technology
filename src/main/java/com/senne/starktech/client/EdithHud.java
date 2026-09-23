package com.senne.starktech.client;

import com.senne.starktech.client.hud.BootOverlay;
import com.senne.starktech.client.hud.CompassWidget;
import com.senne.starktech.client.hud.DroneFeedWidget;
import com.senne.starktech.client.hud.FlightWidget;
import com.senne.starktech.client.hud.HackBubbleWidget;
import com.senne.starktech.client.hud.HudContext;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.client.hud.HudWidget;
import com.senne.starktech.client.hud.MarkedTargetsWidget;
import com.senne.starktech.client.hud.NavigationMarkerWidget;
import com.senne.starktech.client.hud.NoPowerWidget;
import com.senne.starktech.client.hud.NotificationWidget;
import com.senne.starktech.client.hud.OreMarkerWidget;
import com.senne.starktech.client.hud.PingWidget;
import com.senne.starktech.client.hud.RepulsorWidget;
import com.senne.starktech.client.hud.RelayLabelWidget;
import com.senne.starktech.client.hud.SatelliteStrikeWidget;
import com.senne.starktech.client.hud.SpyTargetWidget;
import com.senne.starktech.client.hud.StatusPanelWidget;
import com.senne.starktech.client.hud.TargetAnalysisWidget;
import com.senne.starktech.client.hud.ThreatMarkerWidget;
import com.senne.starktech.client.hud.ZoomWidget;
import com.senne.starktech.edith.EdithData;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * De HUD van E.D.I.T.H.: alles wat je "in de bril" ziet. Wordt elke frame getekend.
 *
 * Deze klasse tekent zelf bijna niets. Ze zet klaar wat elk onderdeel nodig heeft
 * (een HudContext) en laat daarna de onderdelen uit {@link #WIDGETS} hun ding doen,
 * op volgorde: wat onderaan de lijst staat, komt bovenop te liggen.
 *
 * Een nieuw onderdeel toevoegen? Maak een klasse die HudWidget is (zie de map hud/)
 * en zet hem in de lijst hieronder op de plek waar hij hoort.
 */
public class EdithHud implements LayeredDraw.Layer {

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
            new RelayLabelWidget(),
            new HackBubbleWidget(),
            new SatelliteStrikeWidget(),
            new CompassWidget(),
            new DroneFeedWidget(),
            new ZoomWidget(),
            new RepulsorWidget(),
            new FlightWidget(),
            new NoPowerWidget(),
            new StatusPanelWidget(),
            NOTIFICATIONS);

    private final WorldProjection projection = new WorldProjection();

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || !EdithClientState.wearing) {
            return;
        }
        ItemStack glasses = EdithData.getWornGlasses(player);
        if (glasses.isEmpty()) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        long now = Util.getMillis();

        // Een bril van iemand anders: alleen "toegang geweigerd" en de meldingen.
        if (!EdithClientState.authorized) {
            BootOverlay.accessDenied(g, mc.font, glasses, w, h, now);
            new NotificationWidget().render(context(mc, g, player, glasses, w, h, now, false, false));
            return;
        }

        // Net opgezet: eerst de opstart-animatie, de rest komt daarna.
        long bootAge = now - EdithClientState.bootStartMillis;
        if (bootAge < BootOverlay.BOOT_MILLIS) {
            BootOverlay.boot(g, mc.font, w, h, bootAge, EdithClientState.bootStartMillis, player, glasses);
            return;
        }

        projection.begin(w, h, deltaTracker.getGameTimeDeltaPartialTick(false));
        boolean powered = EdithData.getEnergy(glasses) > 0;
        // Is het ronde G-menu open? Dan verdwijnt alles wat plat op je scherm staat (het paneel
        // links, het kompas, meldingen...), zodat niets de cirkel in het midden in de weg zit.
        // Markeringen in de wereld (vijanden, ertsen, pings...) blijven wel zichtbaar.
        boolean menuOpen = mc.screen instanceof EdithMenuScreen;
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
