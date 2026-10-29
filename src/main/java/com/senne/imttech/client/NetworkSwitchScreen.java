package com.senne.imttech.client;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.energy.PowerCabinets;
import com.senne.imttech.lan.NetworkConnectable;
import com.senne.imttech.lan.NetworkSwitchBlock;
import com.senne.imttech.network.SwitchActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Het deurtje van de Network Switch (hoofdstuk 38): zes poorten, één per kant, net als de stroomkast.
 *
 * <pre>
 *  NETWORK SWITCH   Switch 1            ● online
 *  ●  Front    (north)   wired      [ ON  ]
 *  ●  Back     (south)   nothing    [ OFF ]
 *  ...
 *                                       [Done]
 * </pre>
 * Per poort: een lampje (groen = aan), de naam gezien vanaf het deurtje, of er een netwerk- of PoE-kabel (of een
 * apparaat) op die kant zit, en de schakelaar. Bovenaan of de switch stroom heeft: zonder stroom gaat er geen netwerk
 * doorheen. Wat je klikt, gaat meteen naar de switch; het scherm leest de stand uit het blok, dus je ziet het meteen.
 */
public class NetworkSwitchScreen extends Screen {
    private static final int PANEL_W = 250;
    private static final int PANEL_H = 196;
    private static final int ROW = 22;
    private static final int GREY = 0x8A9BA0;
    /** Wit-blauw, zoals een netwerkkabel. */
    private static final int ACCENT = 0xCFE3FF;

    private final BlockPos pos;
    private final String name;
    private int left;
    private int top;
    private BlockState shown;

    public NetworkSwitchScreen(BlockPos pos, String name) {
        super(Component.translatable("screen.imttech.switch"));
        this.pos = pos;
        this.name = name;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private BlockState state() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.getBlockState(pos);
    }

    /** De zes kanten in de volgorde van het deurtje: voor, achter, links, rechts, boven, onder. */
    private static Direction[] sides(BlockState state) {
        Direction front = state.getValue(NetworkSwitchBlock.FACING);
        return new Direction[] {front, front.getOpposite(), front.getClockWise(), front.getCounterClockWise(),
                Direction.UP, Direction.DOWN};
    }

    private int rowY(int row) {
        return top + 30 + row * ROW;
    }

    @Override
    protected void init() {
        BlockState state = state();
        if (state == null || !(state.getBlock() instanceof NetworkSwitchBlock)) {
            onClose();
            return;
        }
        shown = state;
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - PANEL_H) / 2);
        Direction[] sides = sides(state);
        for (int row = 0; row < sides.length; row++) {
            Direction side = sides[row];
            boolean on = NetworkSwitchBlock.isOn(state, side);
            addRenderableWidget(Button.builder(Component.translatable(on ? "screen.imttech.on" : "screen.imttech.off"),
                            b -> PacketDistributor.sendToServer(new SwitchActionPayload(pos, side.get3DDataValue())))
                    .bounds(left + PANEL_W - 70, rowY(row), 60, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - 70, top + PANEL_H - 26, 60, 20).build());
    }

    /** Een poort veranderd (door jou, iemand anders of de desktop), of de stroom? Dan de knoppen opnieuw. */
    @Override
    public void tick() {
        BlockState state = state();
        if (state == null || !(state.getBlock() instanceof NetworkSwitchBlock)) {
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
        HudStyle.brackets(g, left, top, left + PANEL_W, top + PANEL_H, 8, HudStyle.argb(220, ACCENT));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        BlockState state = state();
        if (state == null || !(state.getBlock() instanceof NetworkSwitchBlock)) {
            return;
        }
        g.drawString(font, title, left + 10, top + 10, HudStyle.argb(255, ACCENT), true);
        g.drawString(font, name, left + 12 + font.width(title), top + 10, HudStyle.argb(220, HudStyle.WHITE), false);
        // Heeft hij stroom? (Een lampje en een woord, rechtsboven.)
        boolean online = state.getValue(NetworkSwitchBlock.ONLINE);
        Component power = Component.translatable(online ? "screen.imttech.switch.online" : "screen.imttech.switch.no_power");
        int powerX = left + PANEL_W - 12 - font.width(power);
        g.fill(powerX - 10, top + 11, powerX - 4, top + 17, HudStyle.argb(255, online ? HudStyle.GREEN : HudStyle.RED));
        g.drawString(font, power, powerX, top + 10, HudStyle.argb(230, online ? HudStyle.GREEN : HudStyle.RED), false);
        Direction[] sides = sides(state);
        Minecraft mc = Minecraft.getInstance();
        for (int row = 0; row < sides.length; row++) {
            Direction side = sides[row];
            boolean on = NetworkSwitchBlock.isOn(state, side);
            int y = rowY(row);
            g.fill(left + 12, y + 5, left + 20, y + 13, HudStyle.argb(255, on ? HudStyle.GREEN : HudStyle.RED));
            g.fill(left + 13, y + 6, left + 15, y + 8, HudStyle.argb(200, 0xFFFFFF));
            g.drawString(font, Component.translatable(PowerCabinets.sideKey(state, side)), left + 28, y + 5,
                    HudStyle.argb(240, HudStyle.WHITE), true);
            String compass = "(" + side.getName() + ")";
            g.pose().pushPose();
            g.pose().translate(left + 80, y + 6, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, compass, 0, 0, HudStyle.argb(160, GREY), false);
            g.pose().popPose();
            // Zit er iets aan deze kant: een netwerk- of PoE-kabel, of een apparaat met een netwerkaansluiting?
            BlockPos next = pos.relative(side);
            boolean wired = mc.level != null && (Cables.carriesNetwork(mc.level, next)
                    || mc.level.getBlockState(next).getBlock() instanceof NetworkConnectable);
            g.drawString(font, Component.translatable(wired ? "screen.imttech.cabinet.wired" : "screen.imttech.cabinet.nothing"),
                    left + 124, y + 5, HudStyle.argb(wired ? 220 : 140, wired ? ACCENT : GREY), false);
        }
    }
}
