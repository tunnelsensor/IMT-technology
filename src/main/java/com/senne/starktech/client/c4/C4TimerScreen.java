package com.senne.starktech.client.c4;

import com.senne.starktech.c4.C4Item;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.network.C4TimerPayload;
import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Het schermpje om de tijd op een C4 (Timer) te zetten (hoofdstuk 10: "één C4 met een timer die in
 * te stellen is"). Je opent het met rechtsklik in de lucht, met de C4 in je hand.
 * <pre>
 *      C4 TIMER
 *   [ 0:10 ]
 *   [-5 s]      [+5 s]
 *   [5 s] [10 s] [30 s] [1:00] [2:00]
 *               [Done]
 * </pre>
 * Elke klik gaat meteen naar de server (die zet het op het item). Het scroll-wieltje en de pijltjes
 * werken ook (per 5 seconden), Enter of Esc sluit het.
 */
public class C4TimerScreen extends Screen {
    private static final int W = 196;
    private static final int H = 112;
    private static final int[] PRESETS = {5, 10, 30, 60, 120};

    private final InteractionHand hand;
    private int seconds;

    private C4TimerScreen(InteractionHand hand, int seconds) {
        super(Component.literal("C4 Timer"));
        this.hand = hand;
        this.seconds = seconds;
    }

    /** Opent het scherm voor de C4 in deze hand (zie C4Item.use). */
    public static void open(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ItemStack stack = mc.player.getItemInHand(hand);
        mc.setScreen(new C4TimerScreen(hand, C4Item.timerSeconds(stack)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    // ================================================================ De knoppen

    /** Een knop: waar hij staat en wat erop staat. */
    private record Key(int x, int y, int w, String label, int seconds, boolean relative) {}

    private Key[] keys() {
        int x = left();
        int y = top();
        Key[] keys = new Key[2 + PRESETS.length + 1];
        keys[0] = new Key(x + 12, y + 44, 50, "-5 s", -5, true);
        keys[1] = new Key(x + W - 62, y + 44, 50, "+5 s", 5, true);
        int presetW = 32;
        int gap = (W - 24 - PRESETS.length * presetW) / (PRESETS.length - 1);
        for (int i = 0; i < PRESETS.length; i++) {
            keys[2 + i] = new Key(x + 12 + i * (presetW + gap), y + 64, presetW, C4Item.clock(PRESETS[i]), PRESETS[i], false);
        }
        keys[keys.length - 1] = new Key(x + (W - 50) / 2, y + 86, 50, "Done", 0, false);
        return keys;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        g.fill(x, y, x + W, y + H, HudStyle.argb(240, 0x15171C));
        HudStyle.brackets(g, x, y, x + W, y + H, 6, HudStyle.argb(255, HudStyle.RED));
        String title = "C4 TIMER";
        g.drawString(font, title, x + (W - font.width(title)) / 2, y + 8, HudStyle.argb(255, HudStyle.RED), false);
        // Het rode schermpje met de tijd, twee keer zo groot.
        g.fill(x + W / 2 - 34, y + 20, x + W / 2 + 34, y + 39, HudStyle.argb(255, 0x1A0606));
        g.renderOutline(x + W / 2 - 34, y + 20, 68, 19, HudStyle.argb(255, 0x6A1A1A));
        String clock = C4Item.clock(seconds);
        g.pose().pushPose();
        g.pose().translate(x + W / 2.0f - font.width(clock), y + 22, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawString(font, clock, 0, 0, HudStyle.argb(255, 0xFF3A2A), false);
        g.pose().popPose();
        for (Key key : keys()) {
            boolean hover = mouseX >= key.x() && mouseX < key.x() + key.w() && mouseY >= key.y() && mouseY < key.y() + 14;
            boolean chosen = !key.relative() && key.seconds() == seconds && !key.label().equals("Done");
            int accent = key.label().equals("Done") ? HudStyle.GREEN : chosen ? HudStyle.ORANGE : HudStyle.CYAN;
            g.fill(key.x(), key.y(), key.x() + key.w(), key.y() + 14, HudStyle.argb(255, hover ? 0x1E3A48 : 0x16232C));
            HudStyle.brackets(g, key.x(), key.y(), key.x() + key.w(), key.y() + 14, 3, HudStyle.argb(255, accent));
            g.drawString(font, key.label(), key.x() + (key.w() - font.width(key.label())) / 2, key.y() + 3,
                    HudStyle.argb(255, accent), false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Key key : keys()) {
            if (mouseX >= key.x() && mouseX < key.x() + key.w() && mouseY >= key.y() && mouseY < key.y() + 14) {
                if (key.label().equals("Done")) {
                    onClose();
                } else {
                    set(key.relative() ? seconds + key.seconds() : key.seconds());
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        set(seconds + (int) Math.signum(scrollY) * 5);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // enter
            onClose();
            return true;
        }
        if (keyCode == 265 || keyCode == 262) { // pijl omhoog / rechts
            set(seconds + 5);
            return true;
        }
        if (keyCode == 264 || keyCode == 263) { // pijl omlaag / links
            set(seconds - 5);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Een nieuwe tijd: hier laten zien, op het item zetten (je spel en de server) en een tikje. */
    private void set(int newSeconds) {
        int clamped = Mth.clamp(newSeconds, Math.round(StarkConfig.C4.TIMER_MIN), Math.round(StarkConfig.C4.TIMER_MAX));
        if (clamped == seconds) {
            return;
        }
        seconds = clamped;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack stack = mc.player.getItemInHand(hand);
            if (stack.getItem() instanceof C4Item) {
                stack.set(ModDataComponents.C4_TIMER.get(), seconds);
            }
        }
        PacketDistributor.sendToServer(new C4TimerPayload(hand == InteractionHand.OFF_HAND, seconds));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.C4_BEEP.get(), 1.4f, 0.5f));
    }
}
