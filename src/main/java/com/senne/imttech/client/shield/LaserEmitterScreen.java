package com.senne.imttech.client.shield;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.network.LaserActionPayload;
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
 *   <li><b>Signal</b>: Normal (redstone aan = laser aan) of Inverted (redstone aan = laser uit; zonder
 *       redstone staat hij dan aan).</li>
 *   <li><b>Wall</b>: Vertical (staand, zoals altijd) of Horizontal (plat, als een vloertje). Voor zenders
 *       in de vloer of het plafond loopt de wand omhoog of omlaag; dan kies je Facing south (de wand loopt
 *       van oost naar west) of Facing west (van noord naar zuid).</li>
 * </ul>
 * Alles geldt meteen voor de hele laserpoort (de zenders die eraan vastzitten); er is geen "opslaan". Het
 * scherm leest wat er nu ingesteld is gewoon uit het blok, dus je ziet het veranderen zodra de server het
 * heeft gedaan.
 */
public class LaserEmitterScreen extends Screen {
    private static final int PANEL_W = 270;
    private static final int PANEL_H = 160;
    private static final int MARGIN = 8;
    /** Waar de knoppen beginnen (de naam van de regel staat links daarvan). */
    private static final int VALUE_X = 64;
    private static final int SWATCH = 20;
    private static final int SWATCH_GAP = 4;
    private static final int DONE_W = 80;

    private final BlockPos pos;
    private final String owner;
    private final List<Button> swatches = new ArrayList<>();
    private int left;
    private int top;
    private @Nullable Button invertButton;
    private @Nullable Button turnButton;

    public LaserEmitterScreen(BlockPos pos, String owner) {
        super(Component.translatable("screen.imttech.laser"));
        this.pos = pos;
        this.owner = owner;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** De zender zoals hij nu in de wereld staat, of null als hij weg is. */
    private @Nullable BlockState emitter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        BlockState state = mc.level.getBlockState(pos);
        return state.getBlock() instanceof LaserEmitterBlock ? state : null;
    }

    // ---------------------------------------------------------------- Waar alles staat

    private int colorY() {
        return top + 36;
    }

    private int signalY() {
        return top + 62;
    }

    private int wallY() {
        return top + 88;
    }

    private int footerY() {
        return top + PANEL_H - 28;
    }

    // ---------------------------------------------------------------- Opbouw

    @Override
    protected void init() {
        BlockState state = emitter();
        if (state == null) {
            onClose();
            return;
        }
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - PANEL_H) / 2);

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
        invertButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    BlockState now = emitter();
                    if (now != null) {
                        send(LaserActionPayload.Op.INVERT, now.getValue(LaserEmitterBlock.INVERTED) ? 0 : 1);
                    }
                })
                .bounds(left + VALUE_X, signalY(), valueW, 20).build());
        turnButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    BlockState now = emitter();
                    if (now != null) {
                        send(LaserActionPayload.Op.TURN, now.getValue(LaserEmitterBlock.TURNED) ? 0 : 1);
                    }
                })
                .bounds(left + VALUE_X, wallY(), valueW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - MARGIN - DONE_W, footerY(), DONE_W, 20).build());
        refreshLabels(state);
    }

    private void send(LaserActionPayload.Op op, int value) {
        PacketDistributor.sendToServer(new LaserActionPayload(pos, op, value));
    }

    /** De teksten op de knoppen, uit wat het blok nu zegt. */
    private void refreshLabels(BlockState state) {
        if (invertButton != null) {
            invertButton.setMessage(Component.translatable(state.getValue(LaserEmitterBlock.INVERTED)
                    ? "screen.imttech.laser.inverted" : "screen.imttech.laser.normal"));
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

    /** Elke tick: de knoppen bijwerken, en dicht als de zender weg is (afgebroken). */
    @Override
    public void tick() {
        super.tick();
        BlockState state = emitter();
        if (state == null) {
            onClose();
            return;
        }
        refreshLabels(state);
    }

    // ---------------------------------------------------------------- Tekenen

    /** Een scherp paneel met een lichte verduistering (zoals de Arc Lamp), zodat je de wand erachter ziet. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(205, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        BlockState state = emitter();
        if (state == null) {
            return;
        }
        super.render(g, mouseX, mouseY, a);
        g.drawString(font, title, left + MARGIN, top + 7, HudStyle.argb(255, HudStyle.CYAN), true);
        // Rechtsboven: staat de wand nu aan?
        boolean on = state.getValue(LaserEmitterBlock.POWERED);
        Component status = Component.translatable(on ? "screen.imttech.laser.on" : "screen.imttech.laser.off");
        g.drawString(font, status, left + PANEL_W - MARGIN - font.width(status), top + 7,
                HudStyle.argb(255, on ? HudStyle.GREEN : 0x8095A0), true);
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
        label(g, "screen.imttech.laser.signal", signalY());
        label(g, "screen.imttech.laser.wall", wallY());

        // Onderaan: voor hoeveel zenders dit geldt.
        Minecraft mc = Minecraft.getInstance();
        int gate = mc.level == null ? 1 : LaserEmitterBlock.group(mc.level, pos).size();
        Component scope = gate > 1
                ? Component.translatable("screen.imttech.laser.gate", gate)
                : Component.translatable("screen.imttech.laser.single");
        int y = top + 114;
        for (net.minecraft.util.FormattedCharSequence line : font.split(scope, PANEL_W - 2 * MARGIN - DONE_W - 6)) {
            g.drawString(font, line, left + MARGIN, y, HudStyle.argb(150, HudStyle.WHITE), false);
            y += 11;
        }
    }

    private void label(GuiGraphics g, String key, int y) {
        g.drawString(font, Component.translatable(key), left + MARGIN, y + 6, HudStyle.argb(190, HudStyle.WHITE), true);
    }
}
