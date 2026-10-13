package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.network.RelayActionPayload;
import com.senne.starktech.redstone.EdithRelays;
import com.senne.starktech.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het tag-scherm (13 oktober): tags in tags zetten.
 *
 * Jouw voorbeeld: "deur1 en deur2 zitten in security room en die zit in base". Zo doe je dat:
 * <ol>
 *   <li>Geef deur1 en deur2 de tag "security room" (rechtsklik op het relais, of "rename").</li>
 *   <li>Maak hier links onderaan de tag "base" aan.</li>
 *   <li>Klik links op "security room", en rechts op "base". Klaar: "security room" zit nu in "base".</li>
 * </ol>
 * Links zie je al je tags als een boom (een tag die in twee tags zit, staat er twee keer). Rechts zie je
 * alle andere tags; een vinkje betekent "de gekozen tag zit hierin". Een tag die een rondje zou maken
 * ("base" in "security room", terwijl "security room" al in "base" zit) kun je niet aanklikken.
 *
 * Openen: de knop "Tags" onderaan het redstone-scherm.
 */
public class RelayTagsScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 236;
    private static final int ROW_H = 18;
    /** Zoveel tags links op één bladzijde. */
    private static final int TREE_ROWS = 8;
    /** Rechts: twee kolommen van zoveel rijen. */
    private static final int GRID_ROWS = 6;
    private static final int LEFT_W = 156;
    private static final int INDENT = 8;
    private static final int MAX_DEPTH = 8;

    /** Een regel van de boom links: de tag, en hoe diep hij zit. */
    private record TreeRow(String tag, int depth) {}

    private EdithRelays shown = EdithRelays.EMPTY;
    private @Nullable String selected;
    /** Een tag die je net aanmaakte: de server moet hem nog bevestigen, maar we kiezen hem alvast. */
    private @Nullable String pending;
    private int treePage;
    private int gridPage;
    private EditBox newTagBox;
    private String newTagText = "";
    private int left;
    private int top;

    public RelayTagsScreen() {
        super(Component.translatable("screen.starktech.relay_tags"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static EdithRelays data() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? EdithRelays.EMPTY : player.getData(ModAttachments.RELAYS);
    }

    // ---------------------------------------------------------------- Opbouw

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(8, (height - PANEL_H) / 2);
        shown = data();
        if (pending != null && shown.hasTag(pending)) {
            pending = null; // de server heeft hem
        }
        if (selected != null && !shown.hasTag(selected) && !selected.equals(pending)) {
            selected = null; // net weggegooid
        }

        // Links: de boom van tags.
        List<TreeRow> tree = new ArrayList<>();
        for (String tag : shown.topTags()) {
            addTree(tree, tag, 0, new ArrayList<>());
        }
        int treePages = Math.max(1, (tree.size() + TREE_ROWS - 1) / TREE_ROWS);
        treePage = Math.min(treePage, treePages - 1);
        int y = top + 26;
        for (int i = treePage * TREE_ROWS; i < Math.min(tree.size(), (treePage + 1) * TREE_ROWS); i++) {
            TreeRow row = tree.get(i);
            int x = left + 8 + Math.min(row.depth(), MAX_DEPTH) * INDENT;
            String tag = row.tag();
            Button button = Button.builder(Component.literal(font.plainSubstrByWidth(tag, LEFT_W - (x - left) - 8)), b -> select(tag))
                    .bounds(x, y, LEFT_W - (x - left), ROW_H - 2).build();
            button.active = !tag.equalsIgnoreCase(selected == null ? "" : selected);
            addRenderableWidget(button);
            y += ROW_H;
        }
        int underTree = top + 26 + TREE_ROWS * ROW_H + 2;
        if (treePages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { treePage = Math.max(0, treePage - 1); rebuildWidgets(); })
                    .bounds(left + 8, underTree, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { treePage++; rebuildWidgets(); })
                    .bounds(left + 30, underTree, 20, 18).build());
        }

        // Links onderaan: een nieuwe tag maken (zoals "base", waar nog geen relais direct in zit).
        newTagBox = new EditBox(font, left + 8, top + PANEL_H - 30, LEFT_W - 32, 18, Component.translatable("screen.starktech.relay_tags.new"));
        newTagBox.setMaxLength(Math.round(StarkConfig.Relays.MAX_NAME));
        newTagBox.setHint(Component.translatable("screen.starktech.relay_tags.new_hint"));
        newTagBox.setValue(newTagText);
        newTagBox.setResponder(text -> newTagText = text);
        addRenderableWidget(newTagBox);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> createTag())
                .bounds(left + LEFT_W - 20, top + PANEL_H - 30, 20, 18)
                .tooltip(Tooltip.create(Component.translatable("screen.starktech.relay_tags.create")))
                .build());

        // Rechts: in welke tags zit de gekozen tag?
        if (selected != null && shown.hasTag(selected)) {
            addParentGrid(selected);
            String tag = selected;
            addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relay_tags.delete"),
                            b -> send(RelayActionPayload.tag(RelayActionPayload.Op.TAG_DELETE, tag, "")))
                    .bounds(left + PANEL_W - 98, top + PANEL_H - 30, 90, 18)
                    .tooltip(Tooltip.create(Component.translatable("screen.starktech.relay_tags.delete.tip")))
                    .build());
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + LEFT_W + 14, top + PANEL_H - 30, 60, 18).build());
    }

    /** Een tag in de boom, met de tags die erin zitten eronder. */
    private void addTree(List<TreeRow> out, String tag, int depth, List<String> path) {
        if (depth > MAX_DEPTH || path.stream().anyMatch(seen -> seen.equalsIgnoreCase(tag))) {
            return;
        }
        out.add(new TreeRow(tag, depth));
        List<String> deeper = new ArrayList<>(path);
        deeper.add(tag);
        for (String child : shown.childrenOf(tag)) {
            addTree(out, child, depth + 1, deeper);
        }
    }

    /** Rechts: alle andere tags als knop. Vinkje = de gekozen tag zit erin. */
    private void addParentGrid(String tag) {
        List<String> others = new ArrayList<>();
        for (String other : shown.tags()) {
            if (!other.equalsIgnoreCase(tag)) {
                others.add(other);
            }
        }
        int perPage = GRID_ROWS * 2;
        int pages = Math.max(1, (others.size() + perPage - 1) / perPage);
        gridPage = Math.min(gridPage, pages - 1);
        int x0 = left + LEFT_W + 14;
        int colW = (PANEL_W - LEFT_W - 22) / 2;
        int y0 = top + 64;
        List<String> parents = shown.parentsOf(tag);
        for (int i = gridPage * perPage; i < Math.min(others.size(), (gridPage + 1) * perPage); i++) {
            String other = others.get(i);
            int slot = i - gridPage * perPage;
            int x = x0 + (slot % 2) * (colW + 4);
            int y = y0 + (slot / 2) * ROW_H;
            boolean isParent = parents.stream().anyMatch(parent -> parent.equalsIgnoreCase(other));
            boolean loop = !isParent && shown.wouldLoop(tag, other);
            String label = (isParent ? "✔ " : "") + other;
            Button button = Button.builder(Component.literal(font.plainSubstrByWidth(label, colW - 8)),
                            b -> send(RelayActionPayload.tag(isParent ? RelayActionPayload.Op.TAG_UNPARENT : RelayActionPayload.Op.TAG_PARENT,
                                    tag, other)))
                    .bounds(x, y, colW, ROW_H - 2).build();
            button.active = !loop;
            if (loop) {
                button.setTooltip(Tooltip.create(Component.translatable("screen.starktech.relay_tags.loop", other, tag)));
            }
            addRenderableWidget(button);
        }
        if (pages > 1) {
            int y = y0 + GRID_ROWS * ROW_H + 2;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { gridPage = Math.max(0, gridPage - 1); rebuildWidgets(); })
                    .bounds(x0, y, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { gridPage++; rebuildWidgets(); })
                    .bounds(x0 + 22, y, 20, 18).build());
        }
    }

    /** Voor de screenshot-rondleiding (VisualCheck): deze tag kiezen. */
    public void tourSelect(String tag) {
        select(tag);
    }

    private void select(String tag) {
        selected = tag;
        gridPage = 0;
        rebuildWidgets();
    }

    private void createTag() {
        String tag = newTagText.strip();
        if (tag.isEmpty()) {
            return;
        }
        send(RelayActionPayload.tag(RelayActionPayload.Op.TAG_CREATE, tag, ""));
        newTagText = "";
        selected = tag; // meteen kiezen: dan zie je hem rechts
        pending = tag;
        rebuildWidgets();
    }

    /** Enter in het vakje = aanmaken. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (newTagBox != null && newTagBox.isFocused() && (keyCode == 257 || keyCode == 335)) {
            createTag();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static void send(RelayActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** Verandert er iets aan je tags (antwoord van de server)? Dan het scherm opnieuw opbouwen. */
    @Override
    public void tick() {
        super.tick();
        if (!data().equals(shown)) {
            rebuildWidgets();
        }
    }

    /** Terug naar het redstone-scherm. */
    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new RelayListScreen());
    }

    // ---------------------------------------------------------------- Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(210, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8, HudStyle.argb(220, HudStyle.CYAN));
        // Een dun lijntje tussen links en rechts.
        g.fill(left + LEFT_W + 6, top + 24, left + LEFT_W + 7, top + PANEL_H - 36, HudStyle.argb(90, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);
        g.drawString(font, title, left + 8, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);

        int rightX = left + LEFT_W + 14;
        int rightW = PANEL_W - LEFT_W - 22;
        if (shown.tags().isEmpty()) {
            HudStyle.wrappedCentered(g, font, Component.translatable("screen.starktech.relay_tags.empty"),
                    left + LEFT_W / 2 + 4, top + 40, LEFT_W - 16, HudStyle.argb(150, HudStyle.WHITE));
        }
        if (selected == null) {
            HudStyle.wrappedCentered(g, font, Component.translatable("screen.starktech.relay_tags.pick"),
                    rightX + rightW / 2, top + 40, rightW - 8, HudStyle.argb(150, HudStyle.WHITE));
            return;
        }
        g.drawString(font, font.plainSubstrByWidth(selected, rightW), rightX, top + 26, HudStyle.argb(255, HudStyle.CYAN), true);
        String contains = Component.translatable("screen.starktech.relay_tags.contains", shown.inTag(selected).size()).getString();
        g.drawString(font, contains, rightX, top + 38, HudStyle.argb(160, 0x9FC9D6), false);
        g.drawString(font, Component.translatable("screen.starktech.relay_tags.inside", selected), rightX, top + 52,
                HudStyle.argb(200, HudStyle.WHITE), false);
        if (shown.tags().size() <= 1) {
            HudStyle.wrappedCentered(g, font, Component.translatable("screen.starktech.relay_tags.only_one"),
                    rightX + rightW / 2, top + 72, rightW - 8, HudStyle.argb(140, HudStyle.WHITE));
        }
    }
}
