package com.senne.imttech.client;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.lan.RouterBlock;
import com.senne.imttech.network.RouterActionPayload;
import com.senne.imttech.network.RouterOpenPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het scherm van een Router (hoofdstuk 112, jouw wens: "een router: hier kun je elk ding draadloos tot op 32 blokken op
 * connecten ... elke router kan max 10 devices").
 *
 * <pre>
 *  ROUTER  Router 1                                  ● online
 *  7 / 10 linked · reaches 32 blocks        Security Core found
 *  LINKED
 *  ●  Chest Sensor                          12 m    [Unlink]
 *  ●  Relay  "Front door"                    5 m    [Unlink]
 *  IN RANGE
 *  ○  Security Camera                       20 m    [ Link ]
 *  ○  Relay                    on Router 2  31 m    [ Link ]
 *                                                    [Done]
 * </pre>
 * Bovenaan staat wat eraan hangt, daaronder wat er binnen bereik staat en er (nog) niet aan hangt, de dichtste eerst.
 * Klik op een regel en er loopt in de wereld een rij lichtjes van dat apparaat naar de router: zo zie je welk het is. De
 * lijst komt van de server en wordt elke seconde ververst, en na elke knop meteen. Dit scherm krijgt alleen wie de router
 * mag instellen (wie hem neerzette, of wie vertrouwd is in die basis); onder IN RANGE staat alleen wat je zelf mag
 * verbinden.
 */
public class RouterScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 226;
    private static final int ROW = 16;
    /** Zoveel regels passen er in de lijst. */
    private static final int ROWS = 9;
    private static final int LIST_TOP = 42;
    private static final int GREY = 0x8A9BA0;
    /** Wit-blauw, zoals een netwerkkabel. */
    private static final int ACCENT = 0xCFE3FF;

    private final BlockPos pos;
    private RouterOpenPayload data;
    private int left;
    private int top;
    private int scroll;
    private int refreshIn = 20;
    private final ScrollBar bar = new ScrollBar();

    /** Eén regel van de lijst: een kopje (entry == null) of een apparaat. */
    private record Line(@Nullable Component header, @Nullable RouterOpenPayload.Entry entry, boolean linked) {}

    public RouterScreen(RouterOpenPayload data) {
        super(Component.translatable("screen.imttech.router"));
        this.pos = data.pos();
        this.data = data;
    }

    /** De plek van de router van dit scherm. */
    public BlockPos pos() {
        return pos;
    }

    /** Nieuwe gegevens van de server (na een knop, of de verversing van elke seconde). */
    public void update(RouterOpenPayload fresh) {
        data = fresh;
        scroll = Math.min(scroll, maxScroll());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private @Nullable BlockState state() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.getBlockState(pos);
    }

    /** De regels van de lijst: het kopje LINKED met wat eraan hangt, en het kopje IN RANGE met wat er in de buurt staat. */
    private List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Component.translatable("screen.imttech.router.linked", data.linked().size(), data.max()), null, true));
        for (RouterOpenPayload.Entry entry : data.linked()) {
            lines.add(new Line(null, entry, true));
        }
        if (data.linked().isEmpty()) {
            lines.add(new Line(Component.translatable("screen.imttech.router.none_linked"), null, false));
        }
        lines.add(new Line(Component.translatable("screen.imttech.router.nearby", data.range()), null, true));
        for (RouterOpenPayload.Entry entry : data.nearby()) {
            lines.add(new Line(null, entry, false));
        }
        if (data.nearby().isEmpty()) {
            lines.add(new Line(Component.translatable("screen.imttech.router.none_nearby"), null, false));
        }
        return lines;
    }

    private int maxScroll() {
        return Math.max(0, lines().size() - ROWS);
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - PANEL_H) / 2);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - 70, top + PANEL_H - 26, 60, 20).build());
    }

    /** De router weg? Dan het scherm ook. En elke seconde de lijst opnieuw vragen. */
    @Override
    public void tick() {
        BlockState state = state();
        if (state == null || !(state.getBlock() instanceof RouterBlock)) {
            onClose();
            return;
        }
        if (--refreshIn <= 0) {
            refreshIn = 20;
            PacketDistributor.sendToServer(new RouterActionPayload(pos, RouterActionPayload.REFRESH, pos));
        }
    }

    // ================================================================ Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudStyle.dim(g, width, height);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, HudStyle.argb(225, 0x10161C));
        HudStyle.brackets(g, left, top, left + PANEL_W, top + PANEL_H, 8, HudStyle.argb(220, ACCENT));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        BlockState state = state();
        // Heeft hij stroom? Dat lezen we uit het blok zelf: zo klopt het lampje meteen, ook tussen twee verversingen.
        boolean online = state != null && state.getBlock() instanceof RouterBlock ? state.getValue(RouterBlock.ONLINE) : data.online();
        g.drawString(font, title, left + 10, top + 10, HudStyle.argb(255, ACCENT), true);
        g.drawString(font, fit(data.name(), 120), left + 16 + font.width(title), top + 10, HudStyle.argb(220, HudStyle.WHITE), false);
        Component power = Component.translatable(online ? "screen.imttech.switch.online" : "screen.imttech.switch.no_power");
        int powerX = left + PANEL_W - 12 - font.width(power);
        g.fill(powerX - 10, top + 11, powerX - 4, top + 17, HudStyle.argb(255, online ? HudStyle.GREEN : HudStyle.RED));
        g.drawString(font, power, powerX, top + 10, HudStyle.argb(230, online ? HudStyle.GREEN : HudStyle.RED), false);
        // De tweede regel: links hoe vol en hoe ver, rechts of er een kern in zijn netwerk zit.
        Component core = Component.translatable(data.core() ? "screen.imttech.router.core" : "screen.imttech.router.no_core");
        small(g, core.getString(), left + PANEL_W - 12 - Math.round(font.width(core) * 0.75f), top + 27,
                HudStyle.argb(210, data.core() ? HudStyle.GREEN : GREY));
        small(g, Component.translatable("screen.imttech.router.summary", data.linked().size(), data.max(), data.range()).getString(),
                left + 10, top + 27, HudStyle.argb(200, GREY));

        List<Line> lines = lines();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - ROWS)));
        int listTop = top + LIST_TOP;
        g.fill(left + 8, listTop - 2, left + PANEL_W - 8, listTop + ROWS * ROW + 2, HudStyle.argb(120, 0x070B0F));
        for (int i = 0; i < ROWS && scroll + i < lines.size(); i++) {
            Line line = lines.get(scroll + i);
            int y = listTop + i * ROW;
            if (line.entry() == null) {
                drawHeader(g, line, y);
            } else {
                drawEntry(g, line, y, mouseX, mouseY);
            }
        }
        bar.set(left + PANEL_W - 14, listTop, ROWS * ROW, lines.size(), ROWS, scroll);
        bar.render(g, mouseX, mouseY);
        // Onderaan: een korte uitleg.
        small(g, Component.translatable("screen.imttech.router.hint").getString(), left + 10, top + PANEL_H - 19,
                HudStyle.argb(170, GREY));
    }

    /** Een kopje ("LINKED 7 / 10"), of de grijze regel "nothing here yet". */
    private void drawHeader(GuiGraphics g, Line line, int y) {
        if (line.linked()) {
            g.drawString(font, line.header(), left + 12, y + 4, HudStyle.argb(235, ACCENT), false);
        } else {
            small(g, line.header().getString(), left + 26, y + 5, HudStyle.argb(150, GREY));
        }
    }

    /** Eén apparaat: een bolletje, wat het is en hoe het heet, hoe ver, zijn stand, en de knop. */
    private void drawEntry(GuiGraphics g, Line line, int y, int mouseX, int mouseY) {
        RouterOpenPayload.Entry entry = line.entry();
        boolean hover = mouseX >= left + 8 && mouseX < left + PANEL_W - 16 && mouseY >= y && mouseY < y + ROW;
        if (hover) {
            g.fill(left + 8, y, left + PANEL_W - 16, y + ROW, HudStyle.argb(40, ACCENT));
        }
        boolean trouble = entry.status() == RouterOpenPayload.FAR || entry.status() == RouterOpenPayload.OUT_OF_RANGE;
        int dot = !line.linked() ? GREY : trouble ? HudStyle.ORANGE : HudStyle.GREEN;
        g.fill(left + 13, y + 5, left + 19, y + 11, HudStyle.argb(255, dot));
        if (!line.linked()) {
            g.fill(left + 14, y + 6, left + 18, y + 10, HudStyle.argb(255, 0x10161C)); // een open rondje: hangt er niet aan
        }
        // Rechts de knop, links daarvan de afstand, links daarvan de stand; wat overblijft is voor de naam.
        int buttonX = left + PANEL_W - 16 - 50;
        String distance = entry.distance() + " m";
        int distanceX = buttonX - 8 - font.width(distance);
        g.drawString(font, distance, distanceX, y + 4, HudStyle.argb(200, GREY), false);
        String status = statusText(entry.status());
        int statusW = status.isEmpty() ? 0 : Math.round(font.width(status) * 0.75f) + 6;
        if (!status.isEmpty()) {
            small(g, status, distanceX - statusW, y + 5, HudStyle.argb(210, trouble ? HudStyle.ORANGE : GREY));
        }
        int room = distanceX - statusW - (left + 26) - 4;
        String block = entry.block().isEmpty() ? Component.translatable("screen.imttech.router.unknown").getString()
                : Component.translatable(entry.block()).getString();
        String label = entry.name().isEmpty() || entry.name().equalsIgnoreCase(block) ? block : block + "  \"" + entry.name() + "\"";
        g.drawString(font, fit(label, room), left + 26, y + 4, HudStyle.argb(240, HudStyle.WHITE), false);
        boolean over = mouseX >= buttonX && mouseX < buttonX + 46 && mouseY >= y + 1 && mouseY < y + ROW - 1;
        int colour = line.linked() ? HudStyle.ORANGE : HudStyle.GREEN;
        g.fill(buttonX, y + 1, buttonX + 46, y + ROW - 1, HudStyle.argb(over ? 110 : 50, colour));
        g.renderOutline(buttonX, y + 1, 46, ROW - 2, HudStyle.argb(over ? 255 : 170, colour));
        Component text = Component.translatable(line.linked() ? "screen.imttech.router.unlink" : "screen.imttech.router.link");
        g.drawString(font, text, buttonX + 23 - font.width(text) / 2, y + 4, HudStyle.argb(255, HudStyle.WHITE), false);
    }

    /** De stand van een apparaat in een paar woorden ("" = niets bijzonders). */
    private static String statusText(int status) {
        String key = switch (status) {
            case RouterOpenPayload.FAR -> "screen.imttech.router.status.far";
            case RouterOpenPayload.OUT_OF_RANGE -> "screen.imttech.router.status.out_of_range";
            case RouterOpenPayload.OTHER_ROUTER -> "screen.imttech.router.status.other_router";
            case RouterOpenPayload.WIRED -> "screen.imttech.router.status.wired";
            default -> "";
        };
        return key.isEmpty() ? "" : Component.translatable(key).getString();
    }

    /** Tekst die niet breder wordt dan {@code room} pixels (anders met drie puntjes), zodat er niets over elkaar loopt. */
    private String fit(String text, int room) {
        if (room <= 0) {
            return "";
        }
        return font.width(text) <= room ? text : font.plainSubstrByWidth(text, Math.max(0, room - font.width("..."))) + "...";
    }

    /** Kleine tekst (75%), linksboven op x, y. */
    private void small(GuiGraphics g, String text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    // ================================================================ Klikken en scrollen

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int dragged = bar.mouseClicked(mouseX, mouseY);
        if (dragged >= 0) {
            scroll = dragged;
            return true;
        }
        int listTop = top + LIST_TOP;
        if (button == 0 && mouseX >= left + 8 && mouseX < left + PANEL_W - 16 && mouseY >= listTop && mouseY < listTop + ROWS * ROW) {
            List<Line> lines = lines();
            int index = scroll + (int) ((mouseY - listTop) / ROW);
            if (index >= 0 && index < lines.size() && lines.get(index).entry() != null) {
                Line line = lines.get(index);
                int buttonX = left + PANEL_W - 16 - 50;
                boolean onButton = mouseX >= buttonX && mouseX < buttonX + 46;
                int op = !onButton ? RouterActionPayload.PING : line.linked() ? RouterActionPayload.UNLINK : RouterActionPayload.LINK;
                PacketDistributor.sendToServer(new RouterActionPayload(pos, op, line.entry().pos()));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, onButton ? 1.0f : 1.4f));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int dragged = bar.mouseDragged(mouseY);
        if (dragged >= 0) {
            scroll = dragged;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        bar.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
        return true;
    }
}
