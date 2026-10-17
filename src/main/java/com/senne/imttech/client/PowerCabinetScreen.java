package com.senne.imttech.client;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.energy.EnergySource;
import com.senne.imttech.energy.PowerCabinetBlock;
import com.senne.imttech.energy.PowerCabinets;
import com.senne.imttech.energy.PowerConnectable;
import com.senne.imttech.energy.PowerCables;
import com.senne.imttech.network.CabinetActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Het deurtje van de stroomkast: zes schakelaars, één per kant.
 *
 * <pre>
 *  POWER CABINET
 *  ●  Front    (north)   wired      [ ON  ]
 *  ●  Back     (south)   nothing    [ OFF ]
 *  ...
 *  [Link to I.M.T]               [Done]
 * </pre>
 * Per kant: een lampje (groen = aan), de naam gezien vanaf het deurtje, of er iets op die kant
 * aangesloten zit (een kabel of apparaat), en de schakelaar zelf. Wat je klikt, gaat meteen naar
 * de kast; het scherm leest de stand van de schakelaars uit het blok, dus je ziet het meteen.
 */
public class PowerCabinetScreen extends Screen {
    private static final int PANEL_W = 250;
    private static final int PANEL_H = 196;
    private static final int ROW = 22;
    private static final int GREY = 0x8A9BA0;
    /** De volgorde op het deurtje: eerst voor/achter, dan links/rechts, dan boven/onder. */
    private static final int[] ORDER = {0, 1, 2, 3, 4, 5};

    private final BlockPos pos;
    private final boolean owner;
    private int left;
    private int top;
    private BlockState shown;

    public PowerCabinetScreen(BlockPos pos, boolean owner) {
        super(Component.translatable("screen.imttech.cabinet"));
        this.pos = pos;
        this.owner = owner;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private BlockState state() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.getBlockState(pos);
    }

    /** De zes kanten in de volgorde van het deurtje. */
    private Direction[] sides(BlockState state) {
        Direction front = state.getValue(PowerCabinetBlock.FACING);
        return new Direction[] {front, front.getOpposite(), front.getClockWise(), front.getCounterClockWise(),
                Direction.UP, Direction.DOWN};
    }

    private int rowY(int row) {
        return top + 24 + row * ROW;
    }

    @Override
    protected void init() {
        BlockState state = state();
        if (state == null || !(state.getBlock() instanceof PowerCabinetBlock)) {
            onClose();
            return;
        }
        shown = state;
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - PANEL_H) / 2);
        Direction[] sides = sides(state);
        for (int row : ORDER) {
            Direction side = sides[row];
            boolean on = PowerCabinetBlock.isOn(state, side);
            Button toggle = Button.builder(Component.translatable(on ? "screen.imttech.on" : "screen.imttech.off"),
                            b -> PacketDistributor.sendToServer(new CabinetActionPayload(pos, CabinetActionPayload.Op.TOGGLE,
                                    side.get3DDataValue())))
                    .bounds(left + PANEL_W - 70, rowY(row), 60, 18).build();
            addRenderableWidget(toggle);
        }
        Button link = Button.builder(Component.translatable("screen.imttech.cabinet.link"),
                        b -> PacketDistributor.sendToServer(new CabinetActionPayload(pos, CabinetActionPayload.Op.LINK, 0)))
                .bounds(left + 10, top + PANEL_H - 28, 130, 20).build();
        link.active = owner;
        link.setTooltip(Tooltip.create(Component.translatable(owner ? "screen.imttech.cabinet.link.hint"
                : "screen.imttech.cabinet.not_owner")));
        addRenderableWidget(link);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - 70, top + PANEL_H - 28, 60, 20).build());
    }

    /** Een schakelaar veranderd (door jou of door iemand anders)? Dan de knoppen opnieuw. */
    @Override
    public void tick() {
        BlockState state = state();
        if (state == null || !(state.getBlock() instanceof PowerCabinetBlock)) {
            onClose();
            return;
        }
        if (state != shown) {
            rebuildWidgets();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudStyle.dim(g, width, height);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, HudStyle.argb(225, 0x10161C));
        HudStyle.brackets(g, left, top, left + PANEL_W, top + PANEL_H, 8, HudStyle.argb(220, 0xFFC83F));
        // Een waarschuwingsstreep bovenaan, zoals op een echte hoogspanningskast.
        for (int x = left + 2; x < left + PANEL_W - 2; x += 8) {
            g.fill(x, top + 2, Math.min(x + 4, left + PANEL_W - 2), top + 5, HudStyle.argb(200, 0xFFC83F));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        BlockState state = state();
        if (state == null || !(state.getBlock() instanceof PowerCabinetBlock)) {
            return;
        }
        g.drawString(font, title, left + 10, top + 10, HudStyle.argb(255, 0xFFC83F), true);
        Direction[] sides = sides(state);
        Minecraft mc = Minecraft.getInstance();
        for (int row : ORDER) {
            Direction side = sides[row];
            boolean on = PowerCabinetBlock.isOn(state, side);
            int y = rowY(row);
            // Het lampje.
            int lamp = on ? HudStyle.GREEN : HudStyle.RED;
            g.fill(left + 12, y + 5, left + 20, y + 13, HudStyle.argb(255, lamp));
            g.fill(left + 13, y + 6, left + 15, y + 8, HudStyle.argb(200, 0xFFFFFF));
            // De naam van de kant, en in het klein de windrichting.
            g.drawString(font, Component.translatable(PowerCabinets.sideKey(state, side)), left + 28, y + 5,
                    HudStyle.argb(240, HudStyle.WHITE), true);
            String compass = "(" + side.getName() + ")";
            g.pose().pushPose();
            g.pose().translate(left + 80, y + 6, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, compass, 0, 0, HudStyle.argb(160, GREY), false);
            g.pose().popPose();
            // Zit er iets aan deze kant?
            BlockPos next = pos.relative(side);
            boolean wired = mc.level != null && (PowerCables.has(mc.level, next)
                    || mc.level.getBlockState(next).getBlock() instanceof PowerConnectable
                    || mc.level.getBlockEntity(next) instanceof EnergySource);
            g.drawString(font, Component.translatable(wired ? "screen.imttech.cabinet.wired" : "screen.imttech.cabinet.nothing"),
                    left + 124, y + 5, HudStyle.argb(wired ? 220 : 140, wired ? HudStyle.CYAN : GREY), false);
        }
    }
}
