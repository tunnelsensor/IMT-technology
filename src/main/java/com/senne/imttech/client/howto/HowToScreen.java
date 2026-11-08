package com.senne.imttech.client.howto;

import com.mojang.blaze3d.platform.InputConstants;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.howto.HowToCue;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToTopic;
import com.senne.imttech.howto.HowToTopics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Het scherm van "How it works" (hoofdstuk 63, zoals Ponder van Create): bovenaan het onderwerp, in het midden het
 * wereldje met de animatie, onderaan de tijdlijn in hoofdstukjes met de knoppen opnieuw, vorige, pauze en volgende.
 * Slepen draait het wereldje, scrollen zoomt, rechtsklik zet de camera terug. Wijs je een blok aan, dan zie je hoe het
 * heet. Heeft een item meer animaties (of hoort het bij meer onderwerpen), dan blader je met de pijltjes bovenaan.
 *
 * Het scherm komt als een laag boven je inventaris (of de kist, of het overzicht): Esc brengt je daar terug, met alles
 * nog zoals het was.
 */
public final class HowToScreen extends Screen {
    private static final int PANEL = 0x0E1216;
    private static final int EDGE = 0x465460;
    private static final int TOP = 21;
    private static final int BOTTOM = 28;

    /** Eén animatie, met het onderwerp waar hij bij hoort. */
    private record Entry(HowToTopic topic, HowToScene scene) {}

    /** Een knopje met een plaatje, dat we zelf tekenen en aanklikken. */
    private record IconButton(int x, int y, int size, String[] icon, Component tooltip, Runnable action) {
        boolean over(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
        }
    }

    private final List<Entry> entries;
    private final ItemStack icon;
    private final SceneRenderer renderer = new SceneRenderer();
    private final List<IconButton> buttons = new ArrayList<>();
    private int index;
    private ScenePlayer player;
    private float yaw;
    private float pitch;
    private float zoom = 1.0f;
    private boolean rotating;
    private boolean scrubbing;
    private long lastTickMillis = Util.getMillis();
    private int ticksOpen;
    private @Nullable BlockPos hovered;

    private HowToScreen(List<Entry> entries, int start, ItemStack icon) {
        super(Component.translatable("screen.imttech.howto.title"));
        this.entries = entries;
        this.icon = icon;
        select(start);
    }

    // ================================================================ Openen

    /** Opent de animaties van dit item (alle onderwerpen waar het bij hoort). Geeft false als het er geen heeft. */
    public static boolean open(ItemStack stack) {
        List<Entry> entries = new ArrayList<>();
        for (HowToTopic topic : HowToTopics.readyFor(stack.getItem())) {
            for (HowToScene scene : topic.buildScenes()) {
                entries.add(new Entry(topic, scene));
            }
        }
        if (entries.isEmpty()) {
            return false;
        }
        show(new HowToScreen(entries, 0, stack.copyWithCount(1)));
        return true;
    }

    /** Opent de animaties van één onderwerp (uit het overzicht). */
    public static void open(HowToTopic topic) {
        List<Entry> entries = new ArrayList<>();
        for (HowToScene scene : topic.buildScenes()) {
            entries.add(new Entry(topic, scene));
        }
        if (entries.isEmpty()) {
            return;
        }
        List<Item> items = topic.items().get();
        show(new HowToScreen(entries, 0, items.isEmpty() ? ItemStack.EMPTY : new ItemStack(items.getFirst())));
    }

    /** Als laag boven het scherm dat open staat (Esc gaat daarnaar terug), of gewoon open als er niets open staat. */
    static void show(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(screen);
        } else {
            mc.pushGuiLayer(screen);
        }
    }

    private void select(int which) {
        index = Math.floorMod(which, entries.size());
        HowToScene scene = entries.get(index).scene();
        player = new ScenePlayer(scene);
        yaw = scene.yaw();
        pitch = scene.pitch();
        zoom = 1.0f;
        hovered = null;
        lastTickMillis = Util.getMillis();
    }

    // ================================================================ Indeling

    private int viewX() {
        return 4;
    }

    private int viewY() {
        return TOP;
    }

    private int viewW() {
        return width - 8;
    }

    private int viewH() {
        return height - TOP - BOTTOM;
    }

    private int barX0() {
        return 84;
    }

    private int barX1() {
        return width - 8;
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        int by = height - 23;
        buttons.add(new IconButton(6, by, 16, Icons.REPLAY, Component.translatable("screen.imttech.howto.replay"), this::replay));
        buttons.add(new IconButton(24, by, 16, Icons.PREVIOUS, Component.translatable("screen.imttech.howto.previous"),
                this::previousChapter));
        buttons.add(new IconButton(42, by, 16, Icons.PLAY, Component.translatable("screen.imttech.howto.pause"), this::togglePause));
        buttons.add(new IconButton(60, by, 16, Icons.NEXT, Component.translatable("screen.imttech.howto.next"), this::nextChapter));
        buttons.add(new IconButton(width - 20, 3, 15, Icons.CLOSE, Component.translatable("screen.imttech.howto.close"), this::onClose));
        buttons.add(new IconButton(width - 37, 3, 15, Icons.OVERVIEW, Component.translatable("screen.imttech.howto.overview"),
                () -> show(new HowToIndexScreen())));
        if (entries.size() > 1) {
            buttons.add(new IconButton(width - 54, 3, 15, Icons.RIGHT, Component.translatable("screen.imttech.howto.next_scene"),
                    () -> select(index + 1)));
            buttons.add(new IconButton(width - 95, 3, 15, Icons.LEFT, Component.translatable("screen.imttech.howto.previous_scene"),
                    () -> select(index - 1)));
        }
    }

    // ================================================================ Afspelen

    @Override
    public void tick() {
        super.tick();
        ticksOpen++;
        if (!scrubbing) {
            player.advance();
        }
        lastTickMillis = Util.getMillis();
    }

    private void replay() {
        player.seek(0);
        player.setPaused(false);
    }

    private void togglePause() {
        if (player.finished()) {
            replay();
            return;
        }
        player.setPaused(!player.paused());
    }

    private void previousChapter() {
        HowToScene scene = player.scene;
        int current = Math.max(0, scene.chapterIndexAt(player.tick()));
        // Net begonnen aan dit hoofdstukje? Dan naar het vorige; anders naar het begin van dit.
        int target = player.tick() - scene.chapters().get(current).tick() < 20 ? current - 1 : current;
        player.seek(target < 0 ? 0 : scene.chapters().get(target).tick());
    }

    private void nextChapter() {
        HowToScene scene = player.scene;
        int next = scene.chapterIndexAt(player.tick()) + 1;
        player.seek(next < scene.chapters().size() ? scene.chapters().get(next).tick() : scene.length());
    }

    @Override
    public boolean isPauseScreen() {
        // In je eigen wereld staat het spel stil terwijl je kijkt (op een server kan dat niet).
        return true;
    }

    // ================================================================ Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Geen wazige wereld erachter, maar een donker vlak: het wereldje van de animatie moet goed te zien zijn.
        g.fill(0, 0, width, height, HudStyle.argb(255, 0x05080B));
        g.fill(viewX(), viewY(), viewX() + viewW(), viewY() + viewH(), HudStyle.argb(255, 0x0A1016));
        g.renderOutline(viewX(), viewY(), viewW(), viewH(), HudStyle.argb(255, EDGE));
        g.fill(0, height - BOTTOM + 2, width, height, HudStyle.argb(240, PANEL));
        g.fill(0, height - BOTTOM + 2, width, height - BOTTOM + 3, HudStyle.argb(255, EDGE));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        float partial = player.paused() ? 0.0f : Mth.clamp((Util.getMillis() - lastTickMillis) / 50.0f, 0.0f, 1.0f);
        float time = player.time(partial);
        int vx = viewX();
        int vy = viewY();
        int vw = viewW();
        int vh = viewH();
        boolean overView = mouseX >= vx && mouseX < vx + vw && mouseY >= vy && mouseY < vy + vh;
        hovered = overView && !rotating && !pictureShown(time) ? renderer.pick(player, mouseX, mouseY) : null;

        renderer.render(g, player, partial, vx, vy, vw, vh, yaw, pitch, zoom, hovered);
        SceneOverlays.render(g, font, renderer, player, time, vx, vy, vw, vh);

        // Bovenaan: het item, het onderwerp en de animatie.
        Entry entry = entries.get(index);
        if (!icon.isEmpty()) {
            g.renderItem(icon, 4, 2);
        }
        String topic = entry.topic().title().getString().toUpperCase(Locale.ROOT);
        int titleX = icon.isEmpty() ? 6 : 24;
        g.drawString(font, topic, titleX, 7, HudStyle.argb(255, HudStyle.CYAN), false);
        int sceneX = titleX + font.width(topic) + 8;
        int rightLimit = entries.size() > 1 ? width - 98 : width - 40;
        g.drawString(font, fit(entry.scene().title().getString(), rightLimit - sceneX), sceneX, 7,
                HudStyle.argb(220, HudStyle.WHITE), false);
        if (entries.size() > 1) {
            String count = (index + 1) + "/" + entries.size();
            g.drawString(font, count, width - 67 - font.width(count) / 2, 7, HudStyle.argb(220, HudStyle.WHITE), false);
        }

        // In het begin even uitleggen hoe je het wereldje draait.
        if (ticksOpen < 140) {
            float a = Math.min(1.0f, (140 - ticksOpen) / 30.0f);
            String hint = Component.translatable("screen.imttech.howto.hint").getString();
            g.drawString(font, hint, vx + vw - font.width(hint) - 6, vy + vh - 12, HudStyle.argb(Math.max(8, Math.round(a * 150)), HudStyle.WHITE), false);
        }

        renderTimeline(g, mouseX, mouseY, time);
        for (IconButton button : buttons) {
            drawButton(g, button, mouseX, mouseY);
        }

        // Tooltips (Minecraft tekent ze na alles): van een knop, of de naam van het blok onder de muis.
        for (IconButton button : buttons) {
            if (button.over(mouseX, mouseY)) {
                setTooltipForNextRenderPass(tooltipOf(button));
                return;
            }
        }
        if (hovered != null) {
            setTooltipForNextRenderPass(player.world.getBlockState(hovered).getBlock().getName());
        }
    }

    private Component tooltipOf(IconButton button) {
        if (button.icon() == Icons.PLAY) {
            return Component.translatable(player.finished() ? "screen.imttech.howto.replay"
                    : player.paused() ? "screen.imttech.howto.play" : "screen.imttech.howto.pause");
        }
        return button.tooltip();
    }

    private void drawButton(GuiGraphics g, IconButton button, int mouseX, int mouseY) {
        boolean over = button.over(mouseX, mouseY);
        String[] icon = button.icon();
        if (icon == Icons.PLAY && !player.paused() && !player.finished()) {
            icon = Icons.PAUSE;
        }
        // Afgelopen? Dan klopt de knop opnieuw, zodat je hem vindt.
        boolean pulse = player.finished() && button.icon() == Icons.REPLAY && (ticksOpen / 10) % 2 == 0;
        g.fill(button.x(), button.y(), button.x() + button.size(), button.y() + button.size(),
                HudStyle.argb(over ? 255 : 220, over ? 0x1A2733 : PANEL));
        g.renderOutline(button.x(), button.y(), button.size(), button.size(),
                HudStyle.argb(255, over || pulse ? HudStyle.CYAN : EDGE));
        int iw = icon[0].length();
        int ih = icon.length;
        int ix = button.x() + (button.size() - iw) / 2;
        int iy = button.y() + (button.size() - ih) / 2;
        int colour = HudStyle.argb(255, over ? HudStyle.CYAN : HudStyle.WHITE);
        for (int row = 0; row < ih; row++) {
            for (int col = 0; col < iw; col++) {
                if (icon[row].charAt(col) == '#') {
                    g.fill(ix + col, iy + row, ix + col + 1, iy + row + 1, colour);
                }
            }
        }
    }

    /** De tijdlijn onderaan: het hoofdstukje, de tijd, en een balk met een streepje per hoofdstukje. */
    private void renderTimeline(GuiGraphics g, int mouseX, int mouseY, float time) {
        HowToScene scene = player.scene;
        int x0 = barX0();
        int x1 = barX1();
        int barY = height - 10;
        int chapter = Math.max(0, scene.chapterIndexAt(player.tick()));
        String title = (chapter + 1) + "/" + scene.chapters().size() + "  " + scene.chapters().get(chapter).title().getString();
        String clock = clock(player.tick()) + " / " + clock(scene.length());
        g.drawString(font, fit(title, x1 - x0 - font.width(clock) - 10), x0, height - 23, HudStyle.argb(230, HudStyle.WHITE), false);
        g.drawString(font, clock, x1 - font.width(clock), height - 23, HudStyle.argb(160, HudStyle.WHITE), false);
        g.fill(x0, barY, x1, barY + 4, HudStyle.argb(255, 0x0A0E12));
        g.renderOutline(x0 - 1, barY - 1, x1 - x0 + 2, 6, HudStyle.argb(255, EDGE));
        int filled = Math.round((x1 - x0) * Math.min(1.0f, time / scene.length()));
        g.fill(x0, barY, x0 + filled, barY + 4, HudStyle.argb(255, HudStyle.CYAN));
        // Een streepje bij elk hoofdstukje (niet bij het eerste); onder de muis zie je hoe het heet.
        for (int i = 1; i < scene.chapters().size(); i++) {
            int cx = x0 + Math.round((x1 - x0) * scene.chapters().get(i).tick() / (float) scene.length());
            g.fill(cx, barY - 2, cx + 1, barY + 6, HudStyle.argb(255, HudStyle.WHITE));
        }
        if (mouseY >= barY - 4 && mouseY <= barY + 8 && mouseX >= x0 && mouseX <= x1) {
            int at = Math.round((mouseX - x0) / (float) (x1 - x0) * scene.length());
            int over = Math.max(0, scene.chapterIndexAt(at));
            setTooltipForNextRenderPass(scene.chapters().get(over).title());
        }
    }

    private static String clock(int ticks) {
        int seconds = ticks / 20;
        return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    private String fit(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String out = text;
        while (!out.isEmpty() && font.width(out + "...") > width) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "...";
    }

    private boolean pictureShown(float time) {
        for (HowToCue cue : player.scene.cues()) {
            if (cue.tick() > time) {
                break;
            }
            if (cue instanceof HowToCue.Picture picture && ScenePlayer.fade(time, picture.tick(), picture.duration()) > 0.0f) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ Muis en toetsen

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inView = mouseX >= viewX() && mouseX < viewX() + viewW() && mouseY >= viewY() && mouseY < viewY() + viewH();
        if (button == 0) {
            for (IconButton icon : List.copyOf(buttons)) {
                if (icon.over(mouseX, mouseY)) {
                    icon.action().run();
                    return true;
                }
            }
            if (overBar(mouseX, mouseY)) {
                scrubbing = true;
                scrub(mouseX);
                return true;
            }
            if (inView) {
                rotating = true;
                return true;
            }
        } else if (button == 1 && inView) {
            // Rechtsklik: de camera terug zoals hij begon.
            yaw = player.scene.yaw();
            pitch = player.scene.pitch();
            zoom = 1.0f;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean overBar(double mouseX, double mouseY) {
        int barY = height - 10;
        return mouseY >= barY - 4 && mouseY <= barY + 8 && mouseX >= barX0() - 2 && mouseX <= barX1() + 2;
    }

    private void scrub(double mouseX) {
        float fraction = Mth.clamp((float) (mouseX - barX0()) / (barX1() - barX0()), 0.0f, 1.0f);
        player.seek(Math.round(fraction * player.scene.length()));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrubbing) {
            scrub(mouseX);
            return true;
        }
        if (rotating) {
            yaw += (float) dragX * 0.8f;
            pitch = Mth.clamp(pitch + (float) dragY * 0.6f, -15.0f, 90.0f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        rotating = false;
        scrubbing = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        zoom = Mth.clamp(zoom * (float) Math.pow(1.12, scrollY), 0.5f, 3.0f);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case InputConstants.KEY_SPACE -> {
                togglePause();
                return true;
            }
            case InputConstants.KEY_LEFT -> {
                previousChapter();
                return true;
            }
            case InputConstants.KEY_RIGHT -> {
                nextChapter();
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    // ================================================================ Alleen voor de rondleiding

    /** Springt naar hoofdstukje {@code chapter} plus {@code offset} ticks en zet de animatie stil (voor een screenshot). */
    public void devShow(int chapter, int offset) {
        HowToScene scene = player.scene;
        int start = chapter < 0 ? scene.length() : scene.chapters().get(Math.min(chapter, scene.chapters().size() - 1)).tick();
        player.seek(start + offset);
        player.setPaused(true);
    }

    /** Zet de camera ergens anders (alsof je gesleept en gescrold hebt). */
    public void devCamera(float yaw, float pitch, float zoom) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.zoom = zoom;
    }

    /** Waar een punt van het wereldje nu op het scherm staat. */
    public float[] devProject(net.minecraft.world.phys.Vec3 point) {
        return renderer.project(point);
    }

    // ================================================================ De plaatjes van de knoppen

    /** Kleine pixelplaatjes voor de knoppen ('#' = een pixel). */
    static final class Icons {
        private Icons() {}

        static final String[] PLAY = {
                "#.....",
                "###...",
                "#####.",
                "######",
                "#####.",
                "###...",
                "#....."};
        static final String[] PAUSE = {
                "##..##",
                "##..##",
                "##..##",
                "##..##",
                "##..##",
                "##..##",
                "##..##"};
        /** Vorig hoofdstukje: twee driehoekjes naar links. */
        static final String[] PREVIOUS = {
                "...#..#",
                "..##.##",
                ".######",
                "#######",
                ".######",
                "..##.##",
                "...#..#"};
        /** Volgend hoofdstukje: twee driehoekjes naar rechts. */
        static final String[] NEXT = {
                "#..#...",
                "##.##..",
                "######.",
                "#######",
                "######.",
                "##.##..",
                "#..#..."};
        /** Opnieuw (terug naar het begin): een streep met een driehoekje naar links. */
        static final String[] REPLAY = {
                "##....#",
                "##...##",
                "##..###",
                "##.####",
                "##..###",
                "##...##",
                "##....#"};
        static final String[] CLOSE = {
                "#.....#",
                ".#...#.",
                "..#.#..",
                "...#...",
                "..#.#..",
                ".#...#.",
                "#.....#"};
        static final String[] OVERVIEW = {
                "##.##.##",
                "##.##.##",
                "........",
                "##.##.##",
                "##.##.##",
                "........",
                "##.##.##",
                "##.##.##"};
        static final String[] LEFT = {
                "...#",
                "..##",
                ".###",
                "####",
                ".###",
                "..##",
                "...#"};
        static final String[] RIGHT = {
                "#...",
                "##..",
                "###.",
                "####",
                "###.",
                "##..",
                "#..."};
    }
}
