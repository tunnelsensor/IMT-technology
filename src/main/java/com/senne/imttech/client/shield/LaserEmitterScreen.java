package com.senne.imttech.client.shield;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.network.LaserActionPayload;
import com.senne.imttech.shield.LaserBridgeBlock;
import com.senne.imttech.shield.LaserEmitterBlock;
import com.senne.imttech.shield.ShieldColor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het instelscherm van een laserzender (13 oktober). Openen: sneak + rechtsklik met een lege hand, als jij
 * hem neerzette of als je in deze basis vertrouwd bent (de server kijkt dat na, zie shield/LaserEmitters).
 *
 * <ul>
 *   <li><b>Color</b>: zes vakjes, één per kleur van het veld.</li>
 *   <li><b>Name</b> (hoofdstuk 39): de naam van de poort in jouw I.M.T-lijst, en dus in je diagrammen
 *       ({@code off("poort1")}) en voor je stem. Typ en druk op Save (of Enter).</li>
 *   <li><b>Switch</b> (hoofdstuk 39): aan (de wand staat zolang er stroom is) of uit (open). Redstone doet niets
 *       meer.</li>
 *   <li><b>Wall</b>: Vertical (staand, zoals altijd) of Horizontal (plat, als een vloertje). Voor zenders
 *       in de vloer of het plafond loopt de wand omhoog of omlaag; dan kies je Facing south (de wand loopt
 *       van oost naar west) of Facing west (van noord naar zuid).</li>
 * </ul>
 * Alles geldt meteen voor de hele laserpoort (de zenders die eraan vastzitten); er is geen "opslaan". Het
 * scherm leest wat er nu ingesteld is gewoon uit het blok, dus je ziet het veranderen zodra de server het
 * heeft gedaan.
 *
 * Heeft iemand de draden doorgeknipt (hoofdstuk 33, met de Bypass Kit), dan staat rechtsboven WIRES CUT en
 * komt er een knop <b>Repair wires</b> bij: die zet de draden van de hele poort weer vast, en kost een Computer
 * Chip (zie shield/LaserEmitters). Of de draden doorgeknipt zijn, weet je spel uit de blok-entiteit.
 *
 * Hetzelfde scherm stelt ook de <b>laserbrug</b> in (hoofdstuk 34, zie shield/LaserBridges): dan heet het Laser Bridge,
 * en zijn er alleen Color, Name en Switch (een brug ligt altijd plat, en de Bypass Kit werkt er niet op).
 */
public class LaserEmitterScreen extends Screen {
    private static final int PANEL_W = 270;
    private static final int PANEL_H = 186;
    /** Zoveel lager is het scherm van een laserbrug: die heeft geen regel Wall. */
    private static final int BRIDGE_LESS = 26;
    /** Zoveel hoger is het scherm als de draden doorgeknipt zijn (de uitleg en de knop Repair wires). */
    private static final int CUT_EXTRA = 30;
    private static final int MARGIN = 8;
    /** Waar de knoppen beginnen (de naam van de regel staat links daarvan). */
    private static final int VALUE_X = 64;
    private static final int SWATCH = 20;
    private static final int SWATCH_GAP = 4;
    private static final int DONE_W = 80;
    private static final int SAVE_W = 44;

    private final BlockPos pos;
    private final String owner;
    /** De naam van de poort in jouw I.M.T-lijst toen het scherm openging ("" = hij staat er niet in). */
    private final String name;
    /** Is dit een laserbrug (hoofdstuk 34) in plaats van een laserzender? */
    private final boolean bridge;
    private final List<Button> swatches = new ArrayList<>();
    private int left;
    private int top;
    private @Nullable Button switchButton;
    private @Nullable Button turnButton;
    private @Nullable net.minecraft.client.gui.components.EditBox nameBox;
    /** Is het scherm opgebouwd met de knop Repair wires (de draden waren doorgeknipt)? */
    private boolean cutLayout;

    public LaserEmitterScreen(BlockPos pos, String owner, String name) {
        super(Component.translatable(isBridge(pos) ? "screen.imttech.laser_bridge" : "screen.imttech.laser"));
        this.pos = pos;
        this.owner = owner;
        this.name = name;
        this.bridge = isBridge(pos);
    }

    /** Staat er op deze plek een laserbrug (hoofdstuk 34)? */
    private static boolean isBridge(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockState(pos).getBlock() instanceof LaserBridgeBlock;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * De zender zoals hij nu in de wereld staat, of null als hij weg is. (De kleur en aan/uit lezen we bij een laserzender
     * en een laserbrug met dezelfde eigenschappen: zie LaserBridgeBlock.)
     */
    private @Nullable BlockState emitter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        BlockState state = mc.level.getBlockState(pos);
        boolean right = bridge ? state.getBlock() instanceof LaserBridgeBlock : state.getBlock() instanceof LaserEmitterBlock;
        return right ? state : null;
    }

    /** Zijn de draden van deze poort doorgeknipt (hoofdstuk 33)? Dat zegt de server via de blok-entiteiten. */
    private boolean gateIsCut() {
        Minecraft mc = Minecraft.getInstance();
        return !bridge && mc.level != null && LaserEmitterBlock.isCut(mc.level, LaserEmitterBlock.group(mc.level, pos));
    }

    private int panelHeight() {
        return PANEL_H - (bridge ? BRIDGE_LESS : 0) + (cutLayout ? CUT_EXTRA : 0);
    }

    // ---------------------------------------------------------------- Waar alles staat

    private int colorY() {
        return top + 36;
    }

    private int nameY() {
        return top + 62;
    }

    private int switchY() {
        return top + 88;
    }

    private int wallY() {
        return top + 114;
    }

    private int footerY() {
        return top + panelHeight() - 28;
    }

    /** De uitleg dat de draden doorgeknipt zijn, en de knop Repair wires (alleen als ze dat zijn). */
    private int cutInfoY() {
        return top + 140;
    }

    private int repairY() {
        return top + 164;
    }

    // ---------------------------------------------------------------- Opbouw

    @Override
    protected void init() {
        BlockState state = emitter();
        if (state == null) {
            onClose();
            return;
        }
        cutLayout = gateIsCut();
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - panelHeight()) / 2);

        swatches.clear();
        for (ShieldColor colour : ShieldColor.values()) {
            int x = left + VALUE_X + colour.ordinal() * (SWATCH + SWATCH_GAP);
            Button swatch = Button.builder(Component.empty(), b -> send(LaserActionPayload.Op.COLOR, colour.ordinal()))
                    .bounds(x, colorY(), SWATCH, SWATCH)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("screen.imttech.laser.color." + colour.getSerializedName())))
                    .build();
            swatches.add(addRenderableWidget(swatch));
        }
        int valueW = PANEL_W - VALUE_X - MARGIN;
        // De naam (hoofdstuk 39): wat je typte blijft staan als het scherm opnieuw opbouwt.
        String typed = nameBox == null ? name : nameBox.getValue();
        nameBox = addRenderableWidget(new net.minecraft.client.gui.components.EditBox(font, left + VALUE_X, nameY(),
                valueW - SAVE_W - 4, 20, Component.translatable("screen.imttech.laser.name")));
        nameBox.setMaxLength(com.senne.imttech.shield.LaserGates.MAX_NAME);
        nameBox.setValue(typed);
        nameBox.setHint(Component.translatable("screen.imttech.laser.name_hint"));
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.laser.save"), b -> saveName())
                .bounds(left + PANEL_W - MARGIN - SAVE_W, nameY(), SAVE_W, 20).build());
        switchButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    BlockState now = emitter();
                    if (now != null) {
                        send(LaserActionPayload.Op.SWITCH, now.getValue(LaserEmitterBlock.SWITCHED_ON) ? 0 : 1);
                    }
                })
                .bounds(left + VALUE_X, switchY(), valueW, 20).build());
        // Een laserbrug ligt altijd plat: die heeft geen knop voor staand of liggend.
        turnButton = bridge ? null : addRenderableWidget(Button.builder(Component.empty(), b -> {
                    BlockState now = emitter();
                    if (now != null) {
                        send(LaserActionPayload.Op.TURN, now.getValue(LaserEmitterBlock.TURNED) ? 0 : 1);
                    }
                })
                .bounds(left + VALUE_X, wallY(), valueW, 20).build());
        if (cutLayout) {
            // Repair wires: de server kijkt na of je mag en of je de chips hebt (zie LaserEmitters.repair). In creative is
            // het gratis.
            Minecraft mc = Minecraft.getInstance();
            boolean free = mc.player != null && mc.player.getAbilities().instabuild;
            int chips = free ? 0 : com.senne.imttech.shield.LaserEmitters.repairChips();
            Component label = chips == 0 ? Component.translatable("screen.imttech.laser.repair_free")
                    : Component.translatable(chips == 1 ? "screen.imttech.laser.repair" : "screen.imttech.laser.repair_chips", chips);
            addRenderableWidget(Button.builder(label, b -> send(LaserActionPayload.Op.REPAIR, 0))
                    .bounds(left + MARGIN, repairY(), PANEL_W - 2 * MARGIN, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - MARGIN - DONE_W, footerY(), DONE_W, 20).build());
        refreshLabels(state);
    }

    private void send(LaserActionPayload.Op op, int value) {
        PacketDistributor.sendToServer(new LaserActionPayload(pos, op, value));
    }

    /** Save (of Enter in het naamvak): de naam naar de server, die hem in jouw I.M.T-lijst zet. */
    private void saveName() {
        if (nameBox != null && !nameBox.getValue().isBlank()) {
            PacketDistributor.sendToServer(new com.senne.imttech.network.LaserRenamePayload(pos, nameBox.getValue()));
        }
    }

    /** Enter in het naamvak bewaart de naam (en sluit het scherm niet). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox != null && nameBox.isFocused() && (keyCode == 257 || keyCode == 335)) {
            saveName();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** De teksten op de knoppen, uit wat het blok nu zegt. */
    private void refreshLabels(BlockState state) {
        if (switchButton != null) {
            String screen = bridge ? "screen.imttech.laser_bridge" : "screen.imttech.laser";
            switchButton.setMessage(Component.translatable(screen + (state.getValue(LaserEmitterBlock.SWITCHED_ON)
                    ? ".switch_on" : ".switch_off")));
        }
        if (turnButton != null) {
            turnButton.setMessage(Component.translatable(wallKey(state)));
        }
    }

    /** "Vertical" of "Horizontal"; voor een wand die omhoog of omlaag loopt "Facing south" of "Facing west". */
    private static String wallKey(BlockState state) {
        boolean turned = state.getValue(LaserEmitterBlock.TURNED);
        if (state.getValue(LaserEmitterBlock.FACING).getAxis() == Direction.Axis.Y) {
            return turned ? "screen.imttech.laser.facing_west" : "screen.imttech.laser.facing_south";
        }
        return turned ? "screen.imttech.laser.horizontal" : "screen.imttech.laser.vertical";
    }

    /**
     * Elke tick: de knoppen bijwerken, en dicht als de zender weg is (afgebroken). Zijn de draden net hersteld (of net
     * doorgeknipt), dan bouwt het scherm zich opnieuw op, met of zonder de knop Repair wires.
     */
    @Override
    public void tick() {
        super.tick();
        BlockState state = emitter();
        if (state == null) {
            onClose();
            return;
        }
        if (gateIsCut() != cutLayout) {
            rebuildWidgets();
            return;
        }
        refreshLabels(state);
    }

    // ---------------------------------------------------------------- Tekenen

    /** Een scherp paneel met een lichte verduistering (zoals de Arc Lamp), zodat je de wand erachter ziet. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + panelHeight(), HudStyle.argb(205, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + panelHeight(), 8,
                HudStyle.argb(220, cutLayout ? HudStyle.RED : HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        BlockState state = emitter();
        if (state == null) {
            return;
        }
        super.render(g, mouseX, mouseY, a);
        g.drawString(font, title, left + MARGIN, top + 7, HudStyle.argb(255, HudStyle.CYAN), true);
        // Rechtsboven: staat de wand nu aan? (Of zijn de draden doorgeknipt?)
        boolean on = state.getValue(LaserEmitterBlock.POWERED);
        Component status = Component.translatable(cutLayout ? "screen.imttech.laser.cut"
                : on ? "screen.imttech.laser.on" : "screen.imttech.laser.off");
        g.drawString(font, status, left + PANEL_W - MARGIN - font.width(status), top + 7,
                HudStyle.argb(255, cutLayout ? HudStyle.RED : on ? HudStyle.GREEN : 0x8095A0), true);
        Component placedBy = owner.isEmpty()
                ? Component.translatable("screen.imttech.laser.no_owner")
                : Component.translatable("screen.imttech.laser.owner", owner);
        g.drawString(font, placedBy, left + MARGIN, top + 20, HudStyle.argb(150, 0x9FC9D6), false);

        label(g, "screen.imttech.laser.color", colorY());
        // In elk knopje een vakje in zijn kleur; de gekozen kleur krijgt een witte rand.
        ShieldColor current = state.getValue(LaserEmitterBlock.COLOR);
        for (ShieldColor colour : ShieldColor.values()) {
            Button swatch = swatches.get(colour.ordinal());
            int x0 = swatch.getX() + 4;
            int y0 = swatch.getY() + 4;
            g.fill(x0, y0, x0 + SWATCH - 8, y0 + SWATCH - 8, HudStyle.argb(255, colour.rgb()));
            if (colour == current) {
                g.renderOutline(swatch.getX() - 1, swatch.getY() - 1, SWATCH + 2, SWATCH + 2, HudStyle.argb(255, HudStyle.WHITE));
            }
        }
        label(g, "screen.imttech.laser.name", nameY());
        label(g, "screen.imttech.laser.switch", switchY());
        if (!bridge) {
            label(g, "screen.imttech.laser.wall", wallY());
        }

        // Doorgeknipt: wat er aan de hand is (boven de knop Repair wires).
        if (cutLayout) {
            int y = cutInfoY();
            for (net.minecraft.util.FormattedCharSequence line : font.split(Component.translatable("screen.imttech.laser.cut_info"),
                    PANEL_W - 2 * MARGIN)) {
                g.drawString(font, line, left + MARGIN, y, HudStyle.argb(255, HudStyle.ORANGE), false);
                y += 11;
            }
        }

        // Onderaan: voor hoeveel zenders dit geldt.
        Minecraft mc = Minecraft.getInstance();
        int gate = mc.level == null ? 1
                : (bridge ? LaserBridgeBlock.group(mc.level, pos) : LaserEmitterBlock.group(mc.level, pos)).size();
        String screen = bridge ? "screen.imttech.laser_bridge" : "screen.imttech.laser";
        Component scope = gate > 1
                ? Component.translatable(screen + ".gate", gate)
                : Component.translatable(screen + ".single");
        int y = cutLayout ? footerY() : bridge ? wallY() : top + 140;
        for (net.minecraft.util.FormattedCharSequence line : font.split(scope, PANEL_W - 2 * MARGIN - DONE_W - 6)) {
            g.drawString(font, line, left + MARGIN, y, HudStyle.argb(150, HudStyle.WHITE), false);
            y += 11;
        }
    }

    private void label(GuiGraphics g, String key, int y) {
        g.drawString(font, Component.translatable(key), left + MARGIN, y + 6, HudStyle.argb(190, HudStyle.WHITE), true);
    }
}
