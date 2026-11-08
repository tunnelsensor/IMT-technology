package com.senne.imttech.client.howto;

import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.howto.HowToTopic;
import com.senne.imttech.howto.HowToTopics;
import com.senne.imttech.registry.ModCreativeTabs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Het overzicht van "How it works" (hoofdstuk 63, jouw antwoord 1: een knopje in je inventaris dat alle onderwerpen
 * laat zien, per creative-tabblad). Elk onderwerp is een tegel met zijn item; wijs je er een aan, dan staat bovenaan hoe
 * het heet, en een klik opent de animatie. Alleen onderwerpen die al klaar zijn staan erin.
 */
public final class HowToIndexScreen extends Screen {
    private static final int PANEL = 0x0E1216;
    private static final int EDGE = 0x465460;
    private static final int TILE = 24;
    private static final int GAP = 3;

    /** Een tegel: welk onderwerp, en waar hij staat (zonder het scrollen). */
    private record Tile(HowToTopic topic, ItemStack icon, int x, int y) {}

    private final Map<String, List<HowToTopic>> byTab = new LinkedHashMap<>();
    private final List<Tile> tiles = new ArrayList<>();
    private final List<int[]> headers = new ArrayList<>();
    private final List<Component> headerNames = new ArrayList<>();
    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int contentH;
    private double scroll;

    public HowToIndexScreen() {
        super(Component.translatable("screen.imttech.howto.index"));
        for (var tab : ModCreativeTabs.TABS.getEntries()) {
            byTab.put(tab.getId().getPath(), new ArrayList<>());
        }
        for (HowToTopic topic : HowToTopics.ALL) {
            if (topic.ready()) {
                byTab.computeIfAbsent(topic.tab(), key -> new ArrayList<>()).add(topic);
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        panelW = Math.min(width - 16, 340);
        panelH = height - 16;
        left = (width - panelW) / 2;
        top = 8;
        tiles.clear();
        headers.clear();
        headerNames.clear();
        int perRow = Math.max(1, (panelW - 16 + GAP) / (TILE + GAP));
        int y = 0;
        for (Map.Entry<String, List<HowToTopic>> tab : byTab.entrySet()) {
            if (tab.getValue().isEmpty()) {
                continue;
            }
            headers.add(new int[] {y});
            headerNames.add(Component.translatable("itemGroup.imttech." + tab.getKey()));
            y += 12;
            int column = 0;
            for (HowToTopic topic : tab.getValue()) {
                List<Item> items = topic.items().get();
                ItemStack icon = items.isEmpty() ? ItemStack.EMPTY : new ItemStack(items.getFirst());
                tiles.add(new Tile(topic, icon, column * (TILE + GAP), y));
                if (++column == perRow) {
                    column = 0;
                    y += TILE + GAP;
                }
            }
            if (column > 0) {
                y += TILE + GAP;
            }
            y += 4;
        }
        contentH = y;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - listH()));
    }

    /** Waar de lijst begint (onder de kop en de uitleg) en hoe hoog hij is. */
    private int listTop() {
        return top + 44;
    }

    private int listH() {
        return panelH - 50;
    }

    private @Nullable Tile tileAt(double mouseX, double mouseY) {
        if (mouseY < listTop() || mouseY >= listTop() + listH()) {
            return null;
        }
        for (Tile tile : tiles) {
            int x = left + 8 + tile.x();
            int y = listTop() + tile.y() - (int) scroll;
            if (mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE) {
                return tile;
            }
        }
        return null;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, HudStyle.argb(225, 0x05080B));
        g.fill(left, top, left + panelW, top + panelH, HudStyle.argb(255, PANEL));
        g.renderOutline(left, top, panelW, panelH, HudStyle.argb(255, EDGE));
        HudStyle.brackets(g, left, top, left + panelW, top + panelH, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, "HOW IT WORKS", left + 8, top + 8, HudStyle.argb(255, HudStyle.CYAN), false);
        Tile hovered = tileAt(mouseX, mouseY);
        // De regel onder de kop: wat je aanwijst, of hoe het werkt.
        Component info = hovered != null
                ? Component.translatable("screen.imttech.howto.index.topic", hovered.topic().title())
                : Component.translatable("screen.imttech.howto.index.hint", ImtKeys.HOW_IT_WORKS.getTranslatedKeyMessage());
        HudStyle.wrappedCentered(g, font, SceneOverlays.styled(info.getString()), left + panelW / 2, top + 22, panelW - 16,
                HudStyle.argb(hovered != null ? 255 : 190, HudStyle.WHITE));
        if (tiles.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.imttech.howto.index.empty"), left + panelW / 2,
                    listTop() + 20, HudStyle.argb(170, HudStyle.WHITE));
            return;
        }
        g.enableScissor(left + 2, listTop(), left + panelW - 2, listTop() + listH());
        for (int i = 0; i < headers.size(); i++) {
            int y = listTop() + headers.get(i)[0] - (int) scroll;
            g.drawString(font, headerNames.get(i), left + 8, y + 2, HudStyle.argb(255, HudStyle.ORANGE), false);
        }
        for (Tile tile : tiles) {
            int x = left + 8 + tile.x();
            int y = listTop() + tile.y() - (int) scroll;
            boolean over = tile == hovered;
            g.fill(x, y, x + TILE, y + TILE, HudStyle.argb(255, over ? 0x1A2733 : 0x0A1016));
            g.renderOutline(x, y, TILE, TILE, HudStyle.argb(255, over ? HudStyle.CYAN : EDGE));
            g.renderItem(tile.icon(), x + 4, y + 4);
        }
        g.disableScissor();
        // Een schuifbalkje als het niet allemaal past.
        if (contentH > listH()) {
            int trackX = left + panelW - 5;
            int thumbH = Math.max(12, listH() * listH() / contentH);
            int thumbY = listTop() + (int) ((listH() - thumbH) * (scroll / (contentH - listH())));
            g.fill(trackX, listTop(), trackX + 2, listTop() + listH(), HudStyle.argb(255, 0x0A0E12));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, HudStyle.argb(255, HudStyle.CYAN));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Tile tile = button == 0 ? tileAt(mouseX, mouseY) : null;
        if (tile != null) {
            HowToScreen.open(tile.topic());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - scrollY * 16, 0, Math.max(0, contentH - listH()));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Alleen voor de rondleiding: het midden van tegel {@code index} op het scherm (of null als die er niet is). */
    public int @Nullable [] devTileCenter(int index) {
        if (index < 0 || index >= tiles.size()) {
            return null;
        }
        Tile tile = tiles.get(index);
        return new int[] {left + 8 + tile.x() + TILE / 2, listTop() + tile.y() - (int) scroll + TILE / 2};
    }
}
