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
 * Het groepen-scherm (14 oktober): groepen maken, in elkaar zetten, hernoemen en weggooien.
 *
 * Jouw voorbeeld:
 * <pre>
 *   base
 *     visitors
 *       cafe
 *       storage
 *     restricted
 *       security room
 *       power room
 * </pre>
 * Zo maak je dat: kies links "base" en typ links onderaan "visitors" (+): een nieuwe groep komt in de groep
 * die je gekozen hebt. Wil je een groep verhuizen, kies hem dan links en klik rechts in welke groep hij
 * hoort (of "(top level)"). Een groep zit in hooguit één groep. Wat een rondje zou maken ("base" in "temp"
 * terwijl "temp" al in "base" zit) staat grijs.
 *
 * Openen: de knop "Groups" onderaan het redstone-scherm, of de knop "Group" in het scherm van een relais.
 * In dat laatste geval kies je hier ook de groep van dat relais (de knop "Choose", of "(No group)").
 */
public class RelayGroupsScreen extends Screen {
    private static final int PANEL_W = 380;
    private static final int PANEL_H = 236;
    private static final int ROW_H = 18;
    /** Zoveel groepen links op één bladzijde. */
    private static final int TREE_ROWS = 8;
    /** Rechts: twee kolommen van zoveel rijen. */
    private static final int GRID_ROWS = 5;
    private static final int LEFT_W = 160;
    private static final int INDENT = 8;
    private static final int MAX_DEPTH = 8;

    /** Een regel van de boom links: de groep ("" = "(No group)"), en hoe diep hij zit. */
    private record TreeRow(String group, int depth) {}

    /** Het scherm van het relais waar we een groep voor kiezen, of null (dan beheer je alleen je groepen). */
    private final @Nullable RelayEditScreen picker;
    private EdithRelays shown = EdithRelays.EMPTY;
    /** De gekozen groep links. "" = "(No group)" (alleen als je voor een relais kiest), null = niets. */
    private @Nullable String selected;
    /** Een groep die je net aanmaakte: de server moet hem nog bevestigen, maar we kiezen hem alvast. */
    private @Nullable String pending;
    private int treePage;
    private int gridPage;
    private EditBox newGroupBox;
    private String newGroupText = "";
    private EditBox renameBox;
    private String renameText = "";
    private int left;
    private int top;

    /** Je groepen beheren (vanuit het redstone-scherm). */
    public RelayGroupsScreen() {
        this(null, null);
    }

    /** Een groep kiezen voor een relais (vanuit zijn scherm); {@code current} is waar het nu in zit. */
    public RelayGroupsScreen(@Nullable RelayEditScreen picker, @Nullable String current) {
        super(Component.translatable("screen.starktech.relay_groups"));
        this.picker = picker;
        this.selected = current;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static EdithRelays data() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? EdithRelays.EMPTY : player.getData(ModAttachments.RELAYS);
    }

    private boolean picking() {
        return picker != null;
    }

    private boolean groupSelected() {
        return selected != null && !selected.isEmpty() && shown.hasGroup(selected);
    }

    // ---------------------------------------------------------------- Opbouw

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(8, (height - PANEL_H) / 2);
        shown = data();
        if (pending != null && shown.hasGroup(pending)) {
            pending = null; // de server heeft hem
        }
        if (selected != null && !selected.isEmpty() && !shown.hasGroup(selected) && !selected.equalsIgnoreCase(pending)) {
            selected = null; // net weggegooid (of hernoemd)
        }
        if (selected == null && picking()) {
            selected = "";
        }

        // Links: de boom van groepen (bij het kiezen voor een relais eerst "(No group)").
        List<TreeRow> tree = new ArrayList<>();
        if (picking()) {
            tree.add(new TreeRow("", 0));
        }
        for (String group : shown.topGroups()) {
            addTree(tree, group, 0);
        }
        int treePages = Math.max(1, (tree.size() + TREE_ROWS - 1) / TREE_ROWS);
        treePage = Math.min(treePage, treePages - 1);
        int y = top + 26;
        for (int i = treePage * TREE_ROWS; i < Math.min(tree.size(), (treePage + 1) * TREE_ROWS); i++) {
            TreeRow row = tree.get(i);
            int x = left + 8 + Math.min(row.depth(), MAX_DEPTH) * INDENT;
            String group = row.group();
            String label = group.isEmpty() ? Component.translatable("screen.starktech.relay_groups.none").getString() : group;
            Button button = Button.builder(Component.literal(font.plainSubstrByWidth(label, LEFT_W - (x - left) - 8)), b -> select(group))
                    .bounds(x, y, LEFT_W - (x - left), ROW_H - 2).build();
            button.active = selected == null || !group.equalsIgnoreCase(selected);
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

        // Links onderaan: een nieuwe groep, in de groep die je gekozen hebt (of bovenaan).
        newGroupBox = new EditBox(font, left + 8, top + PANEL_H - 30, LEFT_W - 32, 18, Component.translatable("screen.starktech.relay_groups.new"));
        newGroupBox.setMaxLength(Math.round(StarkConfig.Relays.MAX_NAME));
        // De uitleg in het lege vakje, ingekort zodat hij niet onder de knop "+" doorloopt.
        Component hint = groupSelected()
                ? Component.translatable("screen.starktech.relay_groups.new_hint_in", shown.spelling(selected))
                : Component.translatable("screen.starktech.relay_groups.new_hint");
        newGroupBox.setHint(Component.literal(font.plainSubstrByWidth(hint.getString(), LEFT_W - 32 - 10)));
        newGroupBox.setValue(newGroupText);
        newGroupBox.setResponder(text -> newGroupText = text);
        addRenderableWidget(newGroupBox);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> createGroup())
                .bounds(left + LEFT_W - 20, top + PANEL_H - 30, 20, 18)
                .tooltip(Tooltip.create(Component.translatable("screen.starktech.relay_groups.create")))
                .build());

        int rightX = left + LEFT_W + 14;
        int rightW = PANEL_W - LEFT_W - 22;
        // Rechts: in welke groep zit de gekozen groep, een andere naam, en weggooien.
        if (groupSelected()) {
            String group = selected;
            addParentGrid(group);
            renameBox = new EditBox(font, rightX, top + PANEL_H - 54, rightW - 62, 18, Component.translatable("screen.starktech.relay_groups.rename"));
            renameBox.setMaxLength(Math.round(StarkConfig.Relays.MAX_NAME));
            renameBox.setHint(Component.translatable("screen.starktech.relay_groups.rename_hint"));
            renameBox.setValue(renameText);
            renameBox.setResponder(text -> renameText = text);
            addRenderableWidget(renameBox);
            addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relay_groups.rename"), b -> renameGroup())
                    .bounds(rightX + rightW - 58, top + PANEL_H - 54, 58, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relay_groups.delete"),
                            b -> send(RelayActionPayload.group(RelayActionPayload.Op.GROUP_DELETE, group, "")))
                    .bounds(rightX + rightW - 84, top + PANEL_H - 30, 84, 18)
                    .tooltip(Tooltip.create(Component.translatable("screen.starktech.relay_groups.delete.tip")))
                    .build());
        }

        // Onderaan rechts: kiezen (voor een relais) en terug.
        int bx = rightX;
        if (picking() && selected != null && (selected.isEmpty() || groupSelected() || selected.equalsIgnoreCase(pending))) {
            String choice = selected;
            addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relay_groups.choose"), b -> choose(choice))
                    .bounds(bx, top + PANEL_H - 30, 56, 18).build());
            bx += 60;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(bx, top + PANEL_H - 30, 40, 18).build());
    }

    /** Een groep in de boom, met de groepen die erin zitten eronder. */
    private void addTree(List<TreeRow> out, String group, int depth) {
        if (depth > MAX_DEPTH) {
            return; // een rondje kan niet, maar zo loopt het nooit vast
        }
        out.add(new TreeRow(group, depth));
        for (String child : shown.childrenOf(group)) {
            addTree(out, child, depth + 1);
        }
    }

    /**
     * Rechts: "(top level)" en alle andere groepen als knop. Vinkje = de gekozen groep zit daar nu in. Een
     * groep die een rondje zou maken (hij zit zelf al in de gekozen groep) staat grijs.
     */
    private void addParentGrid(String group) {
        List<String> choices = new ArrayList<>();
        choices.add(""); // (top level)
        for (String other : shown.groups()) {
            if (!other.equalsIgnoreCase(group)) {
                choices.add(other);
            }
        }
        int perPage = GRID_ROWS * 2;
        int pages = Math.max(1, (choices.size() + perPage - 1) / perPage);
        gridPage = Math.min(gridPage, pages - 1);
        int x0 = left + LEFT_W + 14;
        int colW = (PANEL_W - LEFT_W - 22 - 4) / 2;
        int y0 = top + 64;
        String parent = shown.parentOf(group);
        for (int i = gridPage * perPage; i < Math.min(choices.size(), (gridPage + 1) * perPage); i++) {
            String other = choices.get(i);
            int slot = i - gridPage * perPage;
            int x = x0 + (slot % 2) * (colW + 4);
            int y = y0 + (slot / 2) * ROW_H;
            boolean current = parent.equalsIgnoreCase(other);
            boolean loop = shown.wouldLoop(group, other);
            String name = other.isEmpty() ? Component.translatable("screen.starktech.relay_groups.top").getString() : other;
            Button button = Button.builder(Component.literal(font.plainSubstrByWidth((current ? "✔ " : "") + name, colW - 8)),
                            b -> send(RelayActionPayload.group(RelayActionPayload.Op.GROUP_PARENT, group, other)))
                    .bounds(x, y, colW, ROW_H - 2).build();
            button.active = !current && !loop;
            if (loop) {
                button.setTooltip(Tooltip.create(Component.translatable("screen.starktech.relay_groups.loop", other, group)));
            }
            addRenderableWidget(button);
        }
        if (pages > 1) {
            int y = y0 + GRID_ROWS * ROW_H;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { gridPage = Math.max(0, gridPage - 1); rebuildWidgets(); })
                    .bounds(x0, y, 20, 16).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { gridPage++; rebuildWidgets(); })
                    .bounds(x0 + 22, y, 20, 16).build());
        }
    }

    /** Voor de screenshot-rondleiding (VisualCheck): deze groep kiezen. */
    public void tourSelect(String group) {
        select(group);
    }

    private void select(String group) {
        selected = group;
        gridPage = 0;
        renameText = "";
        rebuildWidgets();
    }

    /** Een nieuwe groep, in de groep die je gekozen hebt (of bovenaan). */
    private void createGroup() {
        String group = newGroupText.strip();
        if (group.isEmpty()) {
            return;
        }
        send(RelayActionPayload.group(RelayActionPayload.Op.GROUP_CREATE, group, groupSelected() ? selected : ""));
        newGroupText = "";
        selected = group; // meteen kiezen: dan zie je hem rechts
        pending = group;
        renameText = "";
        rebuildWidgets();
    }

    private void renameGroup() {
        if (!groupSelected() || renameText.isBlank()) {
            return;
        }
        String fresh = renameText.strip();
        send(RelayActionPayload.group(RelayActionPayload.Op.GROUP_RENAME, selected, fresh));
        selected = fresh;
        pending = fresh;
        renameText = "";
        rebuildWidgets();
    }

    /** Terug naar het scherm van het relais, met deze groep gekozen ("" = geen groep). */
    private void choose(String group) {
        if (picker != null) {
            picker.chooseGroup(group);
            Minecraft.getInstance().setScreen(picker);
        }
    }

    /** Enter in een vakje = aanmaken of hernoemen. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean enter = keyCode == 257 || keyCode == 335;
        if (enter && newGroupBox != null && newGroupBox.isFocused()) {
            createGroup();
            return true;
        }
        if (enter && renameBox != null && renameBox.isFocused() && groupSelected()) {
            renameGroup();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static void send(RelayActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** Verandert er iets aan je groepen (antwoord van de server)? Dan het scherm opnieuw opbouwen. */
    @Override
    public void tick() {
        super.tick();
        if (!data().equals(shown)) {
            rebuildWidgets();
        }
    }

    /** Terug naar het scherm van het relais, of naar het redstone-scherm. */
    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(picker != null ? picker : new RelayListScreen());
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
        Component heading = picker != null
                ? Component.translatable("screen.starktech.relay_groups.pick_for", picker.relayName())
                : title;
        g.drawString(font, font.plainSubstrByWidth(heading.getString(), PANEL_W - 16), left + 8, top + 8,
                HudStyle.argb(255, HudStyle.CYAN), true);

        int rightX = left + LEFT_W + 14;
        int rightW = PANEL_W - LEFT_W - 22;
        if (shown.groups().isEmpty()) {
            HudStyle.wrappedCentered(g, font, Component.translatable("screen.starktech.relay_groups.empty"),
                    left + LEFT_W / 2 + 4, top + (picking() ? 52 : 40), LEFT_W - 16, HudStyle.argb(150, HudStyle.WHITE));
        }
        if (selected != null && selected.isEmpty() && picker != null) {
            HudStyle.wrappedCentered(g, font, Component.translatable("screen.starktech.relay_groups.none_info", picker.relayName()),
                    rightX + rightW / 2, top + 40, rightW - 8, HudStyle.argb(170, HudStyle.WHITE));
            return;
        }
        if (!groupSelected()) {
            HudStyle.wrappedCentered(g, font, Component.translatable("screen.starktech.relay_groups.pick"),
                    rightX + rightW / 2, top + 40, rightW - 8, HudStyle.argb(150, HudStyle.WHITE));
            return;
        }
        // De weg erheen ("base › restricted › security room"), en hoeveel apparaten de groep schakelt.
        String path = String.join(" › ", shown.pathOf(selected));
        g.drawString(font, font.plainSubstrByWidth(path, rightW), rightX, top + 26, HudStyle.argb(255, HudStyle.CYAN), true);
        String contains = Component.translatable("screen.starktech.relay_groups.contains", shown.inGroup(selected).size()).getString();
        g.drawString(font, contains, rightX, top + 38, HudStyle.argb(160, 0x9FC9D6), false);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.starktech.relay_groups.inside",
                        shown.spelling(selected)).getString(), rightW), rightX, top + 52, HudStyle.argb(200, HudStyle.WHITE), false);
    }
}
