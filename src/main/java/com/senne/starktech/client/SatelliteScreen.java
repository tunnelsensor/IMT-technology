package com.senne.starktech.client;

import com.senne.starktech.arsenal.EdithArsenal;
import com.senne.starktech.client.arsenal.OrbitView;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.network.SatelliteUpgradePayload;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.satellite.SatelliteStats;
import com.senne.starktech.satellite.SatelliteUpgrade;
import com.senne.starktech.satellite.SatelliteUpgrades;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het satellietscherm: je Stark-satelliet in 3D, wat hij kan, en per onderdeel hoe ver hij
 * opgewaardeerd is.
 *
 * Indeling:
 * - links de satelliet zelf (hij krijgt er onderdelen bij naarmate je upgradet) en daaronder
 *   een tabel met al zijn getallen, per schietstand;
 * - rechts twee secties: de drie schietstanden en de drie systemen. Per onderdeel de naam, tien
 *   blokjes voor het niveau en wat het volgende niveau doet; daarnaast een eigen vakje met wat
 *   het kost (twee rijen plaatjes), en helemaal rechts de knop;
 * - onderaan: "Done", en in creative een knop die alles in één keer op het hoogste niveau zet.
 *
 * Een niveau kost onderdelen uit je rugzak, en drones uit je arsenaal brengen ze naar boven.
 * Wat je nog mist, staat in het rood (en in de uitleg als je over de knop gaat).
 *
 * Openen: G-menu > Satellite & Arsenal > Satellite.
 */
public class SatelliteScreen extends Screen {
    private static final int HEADER_H = 18;
    private static final int FOOTER_H = 26;
    private static final int SECTION_H = 11;
    /** Hoe breed en hoog één kostenplaatje met zijn aantal is, en hoeveel er naast elkaar passen. */
    private static final int COST_W = 22;
    private static final int COST_H = 12;
    private static final int COST_COLUMNS = 4;
    /** Het kostenvakje: vier plaatjes breed (twee rijen), met een randje. */
    private static final int COST_BOX_W = COST_COLUMNS * COST_W + 4;
    /** Hoogte van één regel in de tabel met getallen (kleine tekst met wat lucht). */
    private static final int TABLE_ROW = 8;
    /** Waar de blokjes beginnen: net na de langste naam ("Sweeping laser"). */
    private static final int PIPS_AT = 82;

    private static final List<SatelliteUpgrade> MODES = List.of(
            SatelliteUpgrade.PRECISION, SatelliteUpgrade.BOMBARDMENT, SatelliteUpgrade.SWEEP);
    private static final List<SatelliteUpgrade> SYSTEMS = List.of(
            SatelliteUpgrade.RECHARGE, SatelliteUpgrade.POWER, SatelliteUpgrade.TARGETING);

    private final OrbitView view = new OrbitView();
    private SatelliteUpgrades shown = SatelliteUpgrades.START;
    /** Wat we net gekocht hebben en nog niet terugzagen van de server (de drones zijn onderweg). */
    private @Nullable SatelliteUpgrade sending;
    private long sendingSince;
    /** Het onderdeel dat net een niveau omhoog ging: dat licht even op. */
    private @Nullable SatelliteUpgrade flash;
    private long flashSince;

    private int left;
    private int top;
    /** Hoe breed de linkerkant is (satelliet en getallen); op een klein scherm wat smaller. */
    private int leftW;
    /** Hoe ver de tien blokjes uit elkaar staan: zo dat ze nog vóór de knop passen. */
    private int pipStep;
    private int panelW;
    private int panelH;
    private int rowsX;
    private int rowsW;
    private int rowH;
    /** Hoe breed de knoppen zijn (op een klein scherm wat smaller). */
    private int buttonW;
    /** Waar het kostenvakje begint, en hoe breed de tekst links ervan mag zijn. */
    private int costX;
    private int textW;
    /** Staat de creative-knop "alles op max" er (dan is er minder plek voor de uitleg onderaan)? */
    private boolean maxAllButton;
    /** Het vakje met de satelliet (uitgerekend bij het tekenen), om erin te slepen en te scrollen. */
    private int boxX0;
    private int boxY0;
    private int boxX1;
    private int boxY1;
    private boolean dragging;

    public SatelliteScreen() {
        super(Component.translatable("screen.starktech.satellite"));
        view.yaw = 0;
        view.pitch = 22;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static SatelliteUpgrades data() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? SatelliteUpgrades.START : player.getData(ModAttachments.SATELLITE);
    }

    private static int dronesReady() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0 : player.getData(ModAttachments.ARSENAL).availableCount();
    }

    // ---------------------------------------------------------------- Opbouw

    @Override
    protected void init() {
        panelW = Math.min(width - 12, 480);
        panelH = Math.min(height - 12, 270);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        leftW = Mth.clamp(Math.round(panelW * 0.30f), 118, 150);
        rowsX = left + leftW + 10;
        rowsW = left + panelW - 6 - rowsX;
        // Van rechts naar links: de knop, dan het kostenvakje, dan de naam, blokjes en uitleg.
        buttonW = rowsW >= 300 ? 58 : 48;
        costX = rowsX + rowsW - buttonW - 6 - COST_BOX_W;
        textW = costX - 6 - rowsX;
        // Tussen de blokjes en het kostenvakje moet "10/10" nog passen.
        int levelTextW = (int) Math.ceil(font.width("10/10") * 0.75f) + 4;
        pipStep = Mth.clamp((textW - PIPS_AT - levelTextW - 2) / SatelliteUpgrade.MAX_LEVEL, 3, 7);
        rowH = (panelH - HEADER_H - FOOTER_H - 2 * SECTION_H) / SatelliteUpgrade.values().length;
        shown = data();

        for (SatelliteUpgrade upgrade : SatelliteUpgrade.values()) {
            int y = rowY(upgrade);
            int level = shown.level(upgrade);
            Component label = level >= SatelliteUpgrade.MAX_LEVEL ? Component.translatable("screen.starktech.satellite.max")
                    : upgrade.isMode() && level == 0 ? Component.translatable("screen.starktech.satellite.unlock")
                    : Component.translatable("screen.starktech.satellite.upgrade");
            // De knop staat in het midden van de regel, los van de kosten ernaast.
            Button button = Button.builder(label, b -> buy(upgrade))
                    .bounds(rowsX + rowsW - buttonW, y + (rowH - 18) / 2, buttonW, 16).build();
            Component problem = problem(upgrade);
            button.active = problem == null;
            button.setTooltip(Tooltip.create(tooltip(upgrade, problem)));
            addRenderableWidget(button);
        }

        int footerY = top + panelH - FOOTER_H + 3;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelW - 70, footerY, 64, 20).build());
        // In creative: alles in één keer op het hoogste niveau (om de volle satelliet te proberen).
        LocalPlayer player = Minecraft.getInstance().player;
        maxAllButton = player != null && player.isCreative();
        if (maxAllButton) {
            Button all = Button.builder(Component.translatable("screen.starktech.satellite.max_all"), b -> {
                PacketDistributor.sendToServer(SatelliteUpgradePayload.everything());
                sending = null;
            }).bounds(left + panelW - 70 - 90, footerY, 86, 20).build();
            all.setTooltip(Tooltip.create(Component.translatable("screen.starktech.satellite.max_all.hint")));
            addRenderableWidget(all);
        }
    }

    /** Waar de regel van dit onderdeel begint: eerst de sectie "schietstanden", dan "systemen". */
    private int rowY(SatelliteUpgrade upgrade) {
        int body = top + HEADER_H;
        int index = MODES.indexOf(upgrade);
        if (index >= 0) {
            return body + SECTION_H + index * rowH;
        }
        return body + 2 * SECTION_H + (MODES.size() + SYSTEMS.indexOf(upgrade)) * rowH;
    }

    private void buy(SatelliteUpgrade upgrade) {
        PacketDistributor.sendToServer(SatelliteUpgradePayload.buy(upgrade));
        sending = upgrade;
        sendingSince = Util.getMillis();
        rebuildWidgets();
    }

    /**
     * Waarom deze knop niet kan, of null als hij kan. Dezelfde controles als op de server
     * (SatelliteWorkshop.whyNot); die beslist uiteindelijk, dit is alleen om het te laten zien.
     */
    private @Nullable Component problem(SatelliteUpgrade upgrade) {
        int next = shown.level(upgrade) + 1;
        if (next > SatelliteUpgrade.MAX_LEVEL) {
            return Component.translatable("satellite.starktech.msg.maxed");
        }
        if (sending != null) {
            return Component.translatable("satellite.starktech.msg.busy");
        }
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        for (ItemStack needed : upgrade.costFor(next)) {
            if (have(inventory, needed) < needed.getCount()) {
                return Component.translatable("satellite.starktech.msg.missing", needed.getCount(), needed.getHoverName());
            }
        }
        if (dronesReady() < SatelliteUpgrade.dronesFor(next)) {
            return Component.translatable("satellite.starktech.msg.drones", SatelliteUpgrade.dronesFor(next));
        }
        return null;
    }

    /** De uitleg bij de knop: wat het volgende niveau doet en kost, en wat er nog mist. */
    private Component tooltip(SatelliteUpgrade upgrade, @Nullable Component problem) {
        int level = shown.level(upgrade);
        MutableComponent text = Component.empty().append(upgrade.label().copy().withStyle(ChatFormatting.AQUA));
        text.append("\n").append(Component.translatable("screen.starktech.satellite.now", upgrade.effect(level)));
        if (level < SatelliteUpgrade.MAX_LEVEL) {
            text.append("\n").append(Component.translatable("screen.starktech.satellite.next", upgrade.effect(level + 1))
                    .withStyle(ChatFormatting.GREEN));
            text.append("\n").append(Component.translatable("screen.starktech.satellite.carried",
                    SatelliteUpgrade.dronesFor(level + 1)).withStyle(ChatFormatting.GRAY));
        }
        if (problem != null) {
            text.append("\n").append(problem.copy().withStyle(ChatFormatting.RED));
        }
        return text;
    }

    private static int have(Inventory inventory, ItemStack wanted) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(wanted.getItem())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Is er iets veranderd (een upgrade kwam binnen, je rugzak of je arsenaal)? Dan opnieuw opbouwen. */
    private EdithArsenal lastArsenal;
    private int lastInventoryHash;

    @Override
    public void tick() {
        super.tick();
        if (!EdithClientState.authorized) {
            onClose();
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        SatelliteUpgrades now = data();
        EdithArsenal arsenal = player.getData(ModAttachments.ARSENAL);
        int inventory = inventoryHash(player.getInventory());
        boolean changed = !now.equals(shown) || !arsenal.equals(lastArsenal) || inventory != lastInventoryHash;
        // Welk onderdeel ging net omhoog? Dat laten we even oplichten.
        for (SatelliteUpgrade upgrade : SatelliteUpgrade.values()) {
            if (now.level(upgrade) > shown.level(upgrade)) {
                flash = upgrade;
                flashSince = Util.getMillis();
            }
        }
        // De levering is binnen (of het duurde te lang: dan is er iets misgegaan).
        if (sending != null && (!now.equals(shown) || Util.getMillis() - sendingSince > 8000)) {
            sending = null;
            changed = true;
        }
        lastArsenal = arsenal;
        lastInventoryHash = inventory;
        if (changed) {
            rebuildWidgets();
        }
    }

    private static int inventoryHash(Inventory inventory) {
        int hash = 1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            hash = hash * 31 + stack.getItem().hashCode() * 7 + stack.getCount();
        }
        return hash;
    }

    // ---------------------------------------------------------------- Muis: de satelliet bekijken

    private boolean inBox(double x, double y) {
        return x >= boxX0 && x < boxX1 && y >= boxY0 && y < boxY1;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) {
            return true;
        }
        dragging = inBox(x, y);
        return dragging;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        dragging = false;
        return super.mouseReleased(x, y, button);
    }

    /** Slepen draait de satelliet rond. */
    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging) {
            view.yaw += (float) dx * 0.8f;
            view.pitch = Mth.clamp(view.pitch + (float) dy * 0.6f, -80, 80);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    /** Scrollen zoomt in, zodat je de onderdelen van dichtbij ziet. */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inBox(x, y)) {
            view.zoom = Mth.clamp(view.zoom * (scrollY > 0 ? 1.12f : 1 / 1.12f), 0.7f, 3.5f);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // ---------------------------------------------------------------- Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + panelW + 4, top + panelH, HudStyle.argb(215, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + panelW + 4, top + panelH, 8, HudStyle.argb(220, HudStyle.ORANGE));
        // Een dun lijntje tussen links (de satelliet) en rechts (de onderdelen).
        g.fill(rowsX - 6, top + HEADER_H, rowsX - 5, top + panelH - FOOTER_H, HudStyle.argb(60, HudStyle.ORANGE));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        float seconds = (Util.getMillis() % 3_600_000L) / 1000.0f;

        // Links onder: de tabel met alle getallen. Eerst uitrekenen hoe hoog die wordt; de
        // satelliet krijgt de rest.
        List<StatGroup> groups = stats();
        int statsH = tableHeight(groups);
        boxX0 = left + 2;
        boxY0 = top + HEADER_H;
        boxX1 = left + leftW;
        boxY1 = top + panelH - FOOTER_H - statsH - 4;
        g.fill(boxX0, boxY0, boxX1, boxY1, HudStyle.argb(235, 0x02050C));
        view.renderSatelliteCloseUp(g, boxX0, boxY0, boxX1, boxY1, shown, seconds);
        HudStyle.brackets(g, boxX0, boxY0, boxX1, boxY1, 6, HudStyle.argb(160, HudStyle.ORANGE));
        drawTable(g, groups, boxX0, boxX1, boxY1 + 3);

        super.render(g, mouseX, mouseY, a);

        g.drawString(font, title, left + 2, top + 4, HudStyle.argb(255, HudStyle.ORANGE), true);
        Component ready = Component.translatable("screen.starktech.satellite.drones_ready", dronesReady());
        g.drawString(font, ready, left + panelW - 6 - font.width(ready), top + 4, HudStyle.argb(180, HudStyle.WHITE), true);

        section(g, Component.translatable("screen.starktech.satellite.section.modes"), top + HEADER_H);
        section(g, Component.translatable("screen.starktech.satellite.section.systems"),
                rowY(SYSTEMS.getFirst()) - SECTION_H);
        for (SatelliteUpgrade upgrade : SatelliteUpgrade.values()) {
            drawRow(g, upgrade, rowY(upgrade));
        }

        // Onderaan links: hoe je de satelliet bekijkt en waar de onderdelen vandaan komen. Klein,
        // en desnoods over twee regels, zodat het naast de knoppen past.
        int hintRight = left + panelW - 70 - (maxAllButton ? 94 : 4);
        List<FormattedCharSequence> lines = font.split(Component.translatable("screen.starktech.satellite.hint"),
                (int) ((hintRight - left - 2) / 0.75f));
        g.pose().pushPose();
        g.pose().translate(left + 2, top + panelH - FOOTER_H + (lines.size() > 1 ? 6 : 10), 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(font, lines.get(i), 0, i * 10, HudStyle.argb(150, HudStyle.WHITE), true);
        }
        g.pose().popPose();
    }

    /** Een kopje boven een sectie, met een lijntje erachter. */
    private void section(GuiGraphics g, Component text, int y) {
        small(g, text.getString(), rowsX, y + 2, HudStyle.argb(230, HudStyle.ORANGE));
        int after = rowsX + (int) (font.width(text) * 0.75f) + 4;
        g.fill(after, y + 5, rowsX + rowsW, y + 6, HudStyle.argb(60, HudStyle.ORANGE));
    }

    /**
     * Eén onderdeel:
     * <pre>
     * naam .... [tien blokjes] 3/10   | kosten  kosten  kosten  kosten |  [knop]
     * Next: wat het volgende niveau doet | kosten  kosten  kosten  kosten |
     * </pre>
     */
    private void drawRow(GuiGraphics g, SatelliteUpgrade upgrade, int y) {
        int level = shown.level(upgrade);
        boolean locked = upgrade.isMode() && level == 0;
        boolean maxed = level >= SatelliteUpgrade.MAX_LEVEL;
        int colour = locked ? 0x8A9BA0 : upgrade.isMode() ? HudStyle.CYAN : HudStyle.ORANGE;

        // Net opgewaardeerd? Dan licht de regel even op.
        if (flash == upgrade) {
            long age = Util.getMillis() - flashSince;
            if (age < 1500) {
                int alpha = (int) (70 * (1 - age / 1500.0));
                g.fill(rowsX - 2, y, rowsX + rowsW, y + rowH - 1, HudStyle.argb(alpha, HudStyle.GREEN));
            }
        }
        g.drawString(font, upgrade.label(), rowsX, y + 4, HudStyle.argb(230, colour), true);

        // Tien blokjes: gevuld tot het huidige niveau; het blokje dat eraan komt knippert.
        int pipsX = rowsX + PIPS_AT;
        for (int i = 0; i < SatelliteUpgrade.MAX_LEVEL; i++) {
            int x = pipsX + i * pipStep;
            boolean filled = i < level;
            boolean coming = sending == upgrade && i == level;
            int pip = filled ? HudStyle.argb(230, colour)
                    : coming ? HudStyle.argb((int) (120 + 100 * Math.sin(Util.getMillis() / 150.0)), HudStyle.ORANGE)
                    : HudStyle.argb(45, HudStyle.WHITE);
            g.fill(x, y + 5, x + Math.max(2, pipStep - (pipStep > 4 ? 2 : 1)), y + 11, pip);
        }
        small(g, locked ? Component.translatable("satellite.starktech.upgrade.locked").getString()
                        : level + "/" + SatelliteUpgrade.MAX_LEVEL,
                pipsX + SatelliteUpgrade.MAX_LEVEL * pipStep + 2, y + 6, HudStyle.argb(locked ? 150 : 200, colour));

        // Tweede regel: wat het volgende niveau doet (zo lang als past). Rechts ervan het kostenvakje.
        int line2 = y + 17;
        if (maxed) {
            small(g, Component.translatable("screen.starktech.satellite.fully").getString(), rowsX, line2,
                    HudStyle.argb(200, HudStyle.GREEN));
            return;
        }
        if (sending == upgrade) {
            small(g, Component.translatable("screen.starktech.satellite.sending").getString(), rowsX, line2,
                    HudStyle.argb((int) (170 + 60 * Math.sin(Util.getMillis() / 200.0)), HudStyle.ORANGE));
            return;
        }
        Component next = Component.translatable("screen.starktech.satellite.next", upgrade.effect(level + 1));
        small(g, font.plainSubstrByWidth(next.getString(), (int) (textW / 0.75f)),
                rowsX, line2, HudStyle.argb(200, HudStyle.GREEN));
        drawCost(g, upgrade.costFor(level + 1), SatelliteUpgrade.dronesFor(level + 1), y);
    }

    /** Kleine tekst (75%), links uitgelijnd. */
    private void small(GuiGraphics g, String text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(font, text, 0, 0, colour, true);
        g.pose().popPose();
    }

    /** Kleine tekst (75%), rechts uitgelijnd tegen {@code right}. */
    private void smallRight(GuiGraphics g, String text, int right, int y, int colour) {
        small(g, text, right - (int) Math.ceil(font.width(text) * 0.75f), y, colour);
    }

    /**
     * De kosten in een eigen vakje naast de knop: kleine plaatjes met een aantal, vier per rij;
     * rood als je er te weinig van hebt. Als laatste een drone: zoveel drones moeten de
     * onderdelen naar boven brengen.
     */
    private void drawCost(GuiGraphics g, List<ItemStack> cost, int drones, int rowTop) {
        int boxH = 2 * COST_H + 3;
        int boxY = rowTop + (rowH - boxH) / 2 - 1;
        g.fill(costX, boxY, costX + COST_BOX_W, boxY + boxH, HudStyle.argb(140, 0x02050C));
        g.renderOutline(costX, boxY, COST_BOX_W, boxH, HudStyle.argb(55, HudStyle.ORANGE));

        Inventory inventory = Minecraft.getInstance().player.getInventory();
        List<ItemStack> icons = new ArrayList<>(cost);
        icons.add(new ItemStack(ModItems.STARK_DRONE.get(), drones));
        for (int i = 0; i < icons.size() && i < 2 * COST_COLUMNS; i++) {
            ItemStack needed = icons.get(i);
            boolean enough = i == icons.size() - 1 ? dronesReady() >= drones
                    : have(inventory, needed) >= needed.getCount();
            int x = costX + 2 + (i % COST_COLUMNS) * COST_W;
            int y = boxY + 2 + (i / COST_COLUMNS) * COST_H;
            costCell(g, needed, needed.getCount(), enough, x, y);
        }
    }

    private void costCell(GuiGraphics g, ItemStack icon, int count, boolean enough, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.625f, 0.625f, 1);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();
        small(g, String.valueOf(count), x + 11, y + 3, HudStyle.argb(230, enough ? HudStyle.WHITE : HudStyle.RED));
    }

    // ---------------------------------------------------------------- De getallen onder de satelliet

    /** Eén regel: wat, en hoeveel. */
    private record Stat(Component label, String value) {}

    /** Een groepje regels onder een kopje (de satelliet zelf, of één schietstand). */
    private record StatGroup(Component title, int colour, boolean locked, List<Stat> lines) {}

    /** Alles wat de satelliet nu kan, uitgerekend met dezelfde getallen als de server (SatelliteStats). */
    private List<StatGroup> stats() {
        SatelliteUpgrades u = shown;
        List<StatGroup> groups = new ArrayList<>();
        groups.add(new StatGroup(Component.translatable("screen.starktech.satellite.stats.general"), HudStyle.ORANGE, false, List.of(
                stat("cooldown", seconds(SatelliteStats.cooldownTicks(u.recharge()))),
                stat("charge", seconds(SatelliteStats.chargeTicks(u.targeting()))),
                stat("range", Component.translatable("screen.starktech.satellite.blocks",
                        Math.round(SatelliteStats.range(u.targeting()))).getString()),
                stat("power", "+" + Math.round((SatelliteStats.powerFactor(u.power()) - 1) * 100) + "%"))));
        groups.add(new StatGroup(SatelliteUpgrade.PRECISION.label(), HudStyle.CYAN, u.precision() == 0, List.of(
                stat("damage", String.valueOf(Math.round(SatelliteStats.precisionDamage(u.precision(), u.power())))),
                stat("width", Component.translatable("screen.starktech.satellite.blocks",
                        String.format("%.1f", SatelliteStats.precisionRadius(u.precision()) * 2)).getString()))));
        groups.add(new StatGroup(SatelliteUpgrade.BOMBARDMENT.label(), HudStyle.CYAN, u.bombardment() == 0, List.of(
                stat("impacts", String.valueOf(SatelliteStats.bombardmentShots(u.bombardment()))),
                stat("blast", String.format("%.1f", SatelliteStats.explosionPower(u.bombardment(), u.power()))))));
        groups.add(new StatGroup(SatelliteUpgrade.SWEEP.label(), HudStyle.CYAN, u.sweep() == 0, List.of(
                stat("duration", seconds(SatelliteStats.sweepTicks(u.sweep()))),
                stat("hit", String.format("%.1f", SatelliteStats.sweepDamage(u.sweep(), u.power()))))));
        return groups;
    }

    private static Stat stat(String key, String value) {
        return new Stat(Component.translatable("screen.starktech.satellite.stat." + key), value);
    }

    private static String seconds(int ticks) {
        float s = ticks / 20.0f;
        return (s == Math.round(s) ? String.valueOf(Math.round(s)) : String.format("%.1f", s)) + " s";
    }

    /** Hoe hoog de tabel wordt: per groep een kopregel en een regel per getal, met een kiertje ertussen. */
    private static int tableHeight(List<StatGroup> groups) {
        int height = 0;
        for (StatGroup group : groups) {
            height += TABLE_ROW * (1 + group.lines().size()) + 2;
        }
        return height - 2;
    }

    /**
     * De getallen als tabel: per groep een gekleurde kopregel, dan om en om lichte en donkere
     * regels met links wat het is en rechts (in een eigen kolom) hoeveel, en een kader eromheen.
     */
    private void drawTable(GuiGraphics g, List<StatGroup> groups, int x0, int x1, int y) {
        int valueX = x1 - Math.round((x1 - x0) * 0.36f); // de grens tussen de twee kolommen
        for (StatGroup group : groups) {
            int colour = group.locked() ? 0x8A9BA0 : group.colour();
            int groupTop = y;
            g.fill(x0, y, x1, y + TABLE_ROW, HudStyle.argb(group.locked() ? 35 : 60, colour));
            small(g, group.title().getString().toUpperCase(), x0 + 3, y + 1, HudStyle.argb(240, colour));
            if (group.locked()) {
                smallRight(g, Component.translatable("satellite.starktech.upgrade.locked").getString(), x1 - 3, y + 1,
                        HudStyle.argb(170, 0x8A9BA0));
            }
            y += TABLE_ROW;
            int alpha = group.locked() ? 90 : 220;
            for (int i = 0; i < group.lines().size(); i++) {
                Stat stat = group.lines().get(i);
                g.fill(x0, y, x1, y + TABLE_ROW, i % 2 == 0 ? HudStyle.argb(170, 0x02050C) : HudStyle.argb(150, 0x172833));
                String label = font.plainSubstrByWidth(stat.label().getString(), (int) ((valueX - x0 - 6) / 0.75f));
                small(g, label, x0 + 4, y + 1, HudStyle.argb(alpha, HudStyle.WHITE));
                smallRight(g, group.locked() ? "-" : stat.value(), x1 - 3, y + 1, HudStyle.argb(alpha, HudStyle.WHITE));
                y += TABLE_ROW;
            }
            g.fill(valueX, groupTop + TABLE_ROW, valueX + 1, y, HudStyle.argb(45, colour));
            g.renderOutline(x0, groupTop, x1 - x0, y - groupTop, HudStyle.argb(90, colour));
            y += 2;
        }
    }
}
