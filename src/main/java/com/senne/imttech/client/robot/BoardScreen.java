package com.senne.imttech.client.robot;

import com.senne.imttech.bot.BotBoard;
import com.senne.imttech.bot.BotModule;
import com.senne.imttech.bot.BotModuleItem;
import com.senne.imttech.bot.SolderingIronItem;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.SolderPayload;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.bot.BotEntity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het bordscherm van een guard robot: solderen (hoofdstuk 22). Open je met de knop Board in het robotscherm.
 *
 * <pre>
 *  Guard 1 - circuit board                                [Inspect]
 *  +------------------------------------------------------------+
 *  |   1      2      3      4      5      6      7      8       |
 *  |  [  ]   [  ]   [LS]   [  ]   [  ]   [  ]   [  ]   [  ]     |
 *  |                 ####  (hittebalk boven de pin)             |
 *  +------------------------------------------------------------+
 *  Your modules: [Scan x2] [Laser x1] ...
 *  Soldering Iron: 112 uses left
 * </pre>
 * Sleep een module (onderaan) op een lege pin en houd de muis ingedrukt: de hittebalk boven de pin loopt op.
 * Laat los in het groene vak. Te vroeg: het soldeer pakt niet. Te laat: het loopt vaak naar de pin ernaast,
 * een soldeerbrug, en dat zegt niemand je: een brug is maar een klein zilveren klodderje tussen twee pinnen.
 * Met Inspect zie je ze duidelijk. Rechtsklik op een pin = lossolderen (ook een brug weghalen). Je kunt ook
 * eerst een module aanklikken en dan op een pin drukken.
 *
 * Wat er op het bord zit, leest het scherm van de robot (die stuurt zijn bord mee); de server beslist hoe het
 * solderen afloopt (zie bot/Soldering).
 */
public class BoardScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 196;
    private static final int MARGIN = 8;
    private static final int PAD = 18;
    private static final int PIN_STEP = 34;
    private static final int SLOT = 22;

    private final int entityId;
    private final @Nullable Screen back;
    private int left;
    private int top;
    private boolean inspect;
    /** De module die je vasthoudt (gesleept of aangeklikt), of null. */
    private @Nullable BotModule held;
    private boolean mouseDown;
    /** De pin die je aan het verhitten bent (-1 = geen), en hoe lang al. */
    private int heatPin = -1;
    private int heatTicks;
    private int mouseX;
    private int mouseY;

    public BoardScreen(int entityId, @Nullable Screen back) {
        super(Component.literal("Circuit board"));
        this.entityId = entityId;
        this.back = back;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private @Nullable BotEntity robot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(entityId) instanceof BotEntity robot && robot.isAlive()) {
            return robot;
        }
        return null;
    }

    @Override
    protected void init() {
        if (robot() == null) {
            onClose();
            return;
        }
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - PANEL_H) / 2);
        addRenderableWidget(Button.builder(Component.literal("Inspect"), b -> inspect = !inspect)
                .bounds(left + PANEL_W - MARGIN - 60, top + 4, 60, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(left + PANEL_W - MARGIN - 60, top + PANEL_H - 24, 60, 18).build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(back);
    }

    // ---------------------------------------------------------------- Waar alles staat

    private int boardTop() {
        return top + 26;
    }

    private int pinX(int pin) {
        return left + MARGIN + 18 + (pin - 1) * PIN_STEP;
    }

    private int pinY() {
        return boardTop() + 40;
    }

    /** De pin onder de muis (1-8), of -1. */
    private int pinAt(double mx, double my) {
        for (int pin = 1; pin <= BotBoard.PINS; pin++) {
            if (mx >= pinX(pin) - 2 && mx < pinX(pin) + PAD + 2 && my >= pinY() - 2 && my < pinY() + PAD + 2) {
                return pin;
            }
        }
        return -1;
    }

    /** Je modules (uit je rugzak), met hoeveel je er hebt; de verbrande niet. */
    private Map<BotModule, Integer> modules() {
        Map<BotModule, Integer> map = new EnumMap<>(BotModule.class);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return map;
        }
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.getItem() instanceof BotModuleItem item && item.module() != BotModule.BURNT) {
                map.merge(item.module(), stack.getCount(), Integer::sum);
            }
        }
        if (mc.player.getAbilities().instabuild) {
            for (BotModule module : BotModule.values()) {
                if (module != BotModule.BURNT) {
                    map.putIfAbsent(module, 0);
                }
            }
        }
        return map;
    }

    private int modulesTop() {
        return boardTop() + 104;
    }

    /** De module-vakjes onderaan, in volgorde. */
    private List<BotModule> moduleList() {
        return new ArrayList<>(modules().keySet());
    }

    private int moduleX(int index) {
        return left + MARGIN + index * (SLOT + 4);
    }

    private @Nullable BotModule moduleAt(double mx, double my) {
        List<BotModule> list = moduleList();
        for (int i = 0; i < list.size(); i++) {
            if (mx >= moduleX(i) && mx < moduleX(i) + SLOT && my >= modulesTop() && my < modulesTop() + SLOT) {
                return list.get(i);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- Muis

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        BotEntity robot = robot();
        if (robot == null || robot.isActive()) {
            return false;
        }
        int pin = pinAt(mx, my);
        if (button == 1 && pin > 0) {
            PacketDistributor.sendToServer(new SolderPayload(entityId, true, pin, "", 0));
            return true;
        }
        if (button != 0) {
            return false;
        }
        BotModule module = moduleAt(mx, my);
        if (module != null) {
            held = module;
            mouseDown = true;
            heatPin = -1;
            return true;
        }
        if (pin > 0 && held != null) {
            mouseDown = true;
            heatPin = robot.board().pin(pin).isEmpty() ? pin : -1;
            heatTicks = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && mouseDown) {
            mouseDown = false;
            if (heatPin > 0 && held != null) {
                PacketDistributor.sendToServer(new SolderPayload(entityId, false, heatPin, held.id(), heatTicks));
            }
            heatPin = -1;
            heatTicks = 0;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    /** Elke tick: houd je een module op een lege pin, dan loopt de hitte op (een andere pin: opnieuw). */
    @Override
    public void tick() {
        super.tick();
        BotEntity robot = robot();
        if (robot == null) {
            onClose();
            return;
        }
        if (!mouseDown || held == null) {
            return;
        }
        int pin = pinAt(mouseX, mouseY);
        if (pin > 0 && pin == heatPin) {
            heatTicks++;
        } else if (pin > 0 && robot.board().pin(pin).isEmpty()) {
            heatPin = pin;
            heatTicks = 0;
        } else {
            heatPin = -1;
            heatTicks = 0;
        }
    }

    // ---------------------------------------------------------------- Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(205, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        mouseX = mx;
        mouseY = my;
        BotEntity robot = robot();
        if (robot == null) {
            return;
        }
        super.render(g, mx, my, partialTick);
        String name = robot.displayName();
        g.drawString(font, name + " - circuit board", left + MARGIN, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);
        BotBoard board = robot.board();
        renderBoard(g, board);
        if (heatPin > 0 && held != null) {
            renderHeat(g);
        }
        // Onderaan: je modules, de soldeerbout, en wat je kunt doen.
        g.drawString(font, "Your modules (drag one onto an empty pin and hold):", left + MARGIN, modulesTop() - 11,
                HudStyle.argb(190, HudStyle.WHITE), false);
        List<BotModule> list = moduleList();
        Map<BotModule, Integer> counts = modules();
        for (int i = 0; i < list.size(); i++) {
            int x = moduleX(i);
            BotModule module = list.get(i);
            g.fill(x, modulesTop(), x + SLOT, modulesTop() + SLOT, HudStyle.argb(255, 0x1E2128));
            g.renderOutline(x, modulesTop(), SLOT, SLOT, HudStyle.argb(255, module == held ? HudStyle.CYAN : 0x465460));
            ItemStack icon = new ItemStack(ModItems.robotModule(module));
            g.renderItem(icon, x + 3, modulesTop() + 3);
            g.renderItemDecorations(font, icon.copyWithCount(Math.max(1, counts.get(module))), x + 3, modulesTop() + 3);
        }
        if (list.isEmpty()) {
            g.drawString(font, "none: craft a module first", left + MARGIN, modulesTop() + 6, HudStyle.argb(255, 0x8095A0), false);
        }
        int y = modulesTop() + SLOT + 6;
        Minecraft mc = Minecraft.getInstance();
        ItemStack iron = mc.player == null ? null : SolderingIronItem.find(mc.player);
        if (robot.isActive()) {
            g.drawString(font, "Switch it off before you solder.", left + MARGIN, y, HudStyle.argb(255, HudStyle.RED), false);
        } else if (iron == null) {
            g.drawString(font, "You need a Soldering Iron in your inventory.", left + MARGIN, y, HudStyle.argb(255, HudStyle.RED), false);
        } else {
            g.drawString(font, "Soldering Iron: " + (iron.getMaxDamage() - iron.getDamageValue()) + " uses left.   Right-click a pin = desolder.",
                    left + MARGIN, y, HudStyle.argb(170, HudStyle.WHITE), false);
        }
        // Een gesleepte module volgt de muis.
        if (mouseDown && held != null && heatPin < 0) {
            g.renderItem(new ItemStack(ModItems.robotModule(held)), mx - 8, my - 8);
        }
        // Boven een pin: wat erop zit.
        int hovered = pinAt(mx, my);
        if (hovered > 0 && !mouseDown) {
            BotModule module = board.module(hovered);
            String text = "Pin " + hovered + ": " + (module == null ? "empty" : module.title());
            g.renderTooltip(font, Component.literal(text), mx, my);
        }
    }

    /** Het groene bord met de 8 pinnen, de modules erop en de bruggen (klein, of met Inspect duidelijk). */
    private void renderBoard(GuiGraphics g, BotBoard board) {
        int x0 = left + MARGIN;
        int y0 = boardTop();
        int x1 = left + PANEL_W - MARGIN;
        int y1 = y0 + 84;
        g.fill(x0, y0, x1, y1, HudStyle.argb(255, 0x226E3A));
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, HudStyle.argb(255, 0x144A24));
        // Koperen sporen naar een chip onderaan.
        int chipY = y1 - 18;
        g.fill(x0 + 110, chipY, x1 - 110, chipY + 12, HudStyle.argb(255, 0x1A1C20));
        for (int pin = 1; pin <= BotBoard.PINS; pin++) {
            int cx = pinX(pin) + PAD / 2;
            g.fill(cx, pinY() + PAD, cx + 1, chipY, HudStyle.argb(255, 0xBE783C));
        }
        for (int pin = 1; pin <= BotBoard.PINS; pin++) {
            int px = pinX(pin);
            g.drawString(font, String.valueOf(pin), px + PAD / 2 - 2, pinY() - 12, HudStyle.argb(255, HudStyle.WHITE), false);
            boolean heating = pin == heatPin;
            g.fill(px, pinY(), px + PAD, pinY() + PAD, HudStyle.argb(255, heating ? 0xF0A040 : 0xECC446));
            g.fill(px + 2, pinY() + 2, px + PAD - 2, pinY() + PAD - 2, HudStyle.argb(255, 0xAA8022));
            BotModule module = board.module(pin);
            if (module != null) {
                g.renderItem(new ItemStack(ModItems.robotModule(module)), px + 1, pinY() + 1);
            }
            // Een brug naar de pin rechts ervan.
            if (pin < BotBoard.PINS && board.bridge(pin)) {
                int bx = px + PAD;
                int gap = PIN_STEP - PAD;
                if (inspect) {
                    g.fill(bx, pinY() + PAD / 2 - 3, bx + gap, pinY() + PAD / 2 + 3, HudStyle.argb(255, 0xFF40D0));
                    g.renderOutline(bx - 1, pinY() + PAD / 2 - 4, gap + 2, 8, HudStyle.argb(255, HudStyle.WHITE));
                } else {
                    // Een dun, groenig-zilver draadje soldeer: je ziet het alleen als je goed kijkt.
                    g.fill(bx + 3, pinY() + PAD - 4, bx + gap - 3, pinY() + PAD - 3, HudStyle.argb(255, 0x8FAE98));
                }
            }
        }
        if (inspect) {
            int shorts = board.shorts().size();
            String text = board.bridges() == 0 ? "Inspect: no solder bridges" : shorts > 0
                    ? "Inspect: " + Integer.bitCount(board.bridges()) + " bridge(s) - switching on now would short!"
                    : "Inspect: bridge(s) between empty pins only (harmless)";
            g.drawString(font, text, x0 + 4, y0 + 4, HudStyle.argb(255, shorts > 0 ? HudStyle.RED : HudStyle.GREEN), false);
        }
    }

    /** De hittebalk boven de pin die je verhit: het groene vak is goed, rechts ervan te heet. */
    private void renderHeat(GuiGraphics g) {
        int w = 60;
        int h = 6;
        int x = Math.min(Math.max(pinX(heatPin) + PAD / 2 - w / 2, left + MARGIN), left + PANEL_W - MARGIN - w);
        int y = pinY() - 24;
        double fill = Math.min(1.25, heatTicks / Math.max(1.0, ImtConfig.Robots.SOLDER_FULL_TICKS));
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, HudStyle.argb(255, 0x0A0C10));
        int greenFrom = x + (int) Math.round(w * ImtConfig.Robots.SOLDER_COLD);
        int greenTo = x + (int) Math.round(w * Math.min(1.0, ImtConfig.Robots.SOLDER_HOT));
        g.fill(greenFrom, y, greenTo, y + h, HudStyle.argb(120, HudStyle.GREEN));
        int filled = x + (int) Math.round(w * Math.min(1.0, fill));
        int colour = fill > ImtConfig.Robots.SOLDER_HOT ? HudStyle.RED : fill >= ImtConfig.Robots.SOLDER_COLD ? HudStyle.GREEN : HudStyle.ORANGE;
        g.fill(x, y + 1, filled, y + h - 1, HudStyle.argb(255, colour));
        g.drawString(font, "HEAT", x - 26, y - 1, HudStyle.argb(200, HudStyle.ORANGE), false);
    }

    // ---------------------------------------------------------------- Voor de screenshots

    /** Alleen voor de screenshot-rondleiding: Inspect aan of uit. */
    public void tourInspect(boolean on) {
        inspect = on;
    }

    /** Alleen voor de screenshot-rondleiding: alsof je deze module zoveel ticks op deze pin houdt. */
    public void tourHeat(BotModule module, int pin, int ticks) {
        held = module;
        heatPin = pin;
        heatTicks = ticks;
    }
}
