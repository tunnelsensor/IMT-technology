package com.senne.imttech.client;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.logic.CodePanelBlock;
import com.senne.imttech.network.CodePanelPayload;
import com.senne.imttech.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * De toetsen van een codepaneel (jouw wens, 7 oktober).
 * <pre>
 *   FRONT DOOR
 *  [ * * * *      ]
 *  [1] [2] [3]
 *  [4] [5] [6]
 *  [7] [8] [9]
 *  [C] [0] [OK]
 * </pre>
 * Je klikt de cijfers aan of typt ze op je toetsenbord; Backspace wist het laatste cijfer, C alles,
 * Enter of OK stuurt de code naar de server (en sluit het scherm). Op het schermpje zie je sterretjes,
 * niet de cijfers: iemand die over je schouder meekijkt, ziet je code niet.
 */
public class CodePanelScreen extends Screen {
    private static final int KEY = 22;
    private static final int GAP = 4;
    private static final int PAD = 10;
    private static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK"};

    private final BlockPos pos;
    private final String panelName;
    private String code = "";
    /** Welke toets net is ingedrukt (licht even op), en tot wanneer. */
    private int pressed = -1;
    private long pressedUntil;

    public CodePanelScreen(BlockPos pos, String panelName) {
        super(Component.literal(panelName));
        this.pos = pos;
        this.panelName = panelName;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelW() {
        return 3 * KEY + 2 * GAP + 2 * PAD;
    }

    private int panelH() {
        return PAD + 12 + 18 + 4 * KEY + 3 * GAP + PAD;
    }

    private int left() {
        return (width - panelW()) / 2;
    }

    private int top() {
        return (height - panelH()) / 2;
    }

    private int keyX(int index) {
        return left() + PAD + (index % 3) * (KEY + GAP);
    }

    private int keyY(int index) {
        return top() + PAD + 12 + 18 + (index / 3) * (KEY + GAP);
    }

    // ================================================================ Tekenen

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        g.fill(x, y, x + panelW(), y + panelH(), HudStyle.argb(240, 0x15171C));
        HudStyle.brackets(g, x, y, x + panelW(), y + panelH(), 6, HudStyle.argb(255, HudStyle.CYAN));
        // De naam van het paneel.
        String name = font.plainSubstrByWidth(panelName.toUpperCase(java.util.Locale.ROOT), panelW() - 2 * PAD);
        g.drawString(font, name, x + (panelW() - font.width(name)) / 2, y + PAD, HudStyle.argb(255, HudStyle.CYAN), false);
        // Het schermpje met sterretjes.
        int sy = y + PAD + 12;
        g.fill(x + PAD, sy, x + panelW() - PAD, sy + 14, HudStyle.argb(255, 0x0A1418));
        g.renderOutline(x + PAD, sy, panelW() - 2 * PAD, 14, HudStyle.argb(255, 0x33505F));
        String stars = code.isEmpty() ? "ENTER CODE" : "*".repeat(code.length());
        boolean blink = code.isEmpty() || (System.currentTimeMillis() / 400) % 2 == 0;
        int starColour = code.isEmpty() ? 0x3A6A7A : HudStyle.GREEN;
        g.drawString(font, stars + (code.isEmpty() || code.length() >= CodePanelBlock.MAX_CODE || !blink ? "" : "_"),
                x + PAD + 4, sy + 3, HudStyle.argb(255, starColour), false);
        // De toetsen.
        for (int i = 0; i < KEYS.length; i++) {
            int kx = keyX(i);
            int ky = keyY(i);
            boolean hover = mouseX >= kx && mouseX < kx + KEY && mouseY >= ky && mouseY < ky + KEY;
            boolean lit = i == pressed && System.currentTimeMillis() < pressedUntil;
            int accent = KEYS[i].equals("OK") ? HudStyle.GREEN : KEYS[i].equals("C") ? HudStyle.RED : HudStyle.CYAN;
            g.fill(kx, ky, kx + KEY, ky + KEY, HudStyle.argb(255, lit ? 0x2A5A6A : hover ? 0x1E3A48 : 0x16232C));
            HudStyle.brackets(g, kx, ky, kx + KEY, ky + KEY, 4, HudStyle.argb(255, accent));
            g.drawString(font, KEYS[i], kx + (KEY - font.width(KEYS[i])) / 2, ky + (KEY - 8) / 2,
                    HudStyle.argb(255, lit ? HudStyle.WHITE : accent), false);
        }
    }

    // ================================================================ Toetsen

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < KEYS.length; i++) {
            if (mouseX >= keyX(i) && mouseX < keyX(i) + KEY && mouseY >= keyY(i) && mouseY < keyY(i) + KEY) {
                press(i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint >= '0' && codePoint <= '9') {
            press(codePoint == '0' ? 10 : codePoint - '1');
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 259) { // backspace: het laatste cijfer weg
            if (!code.isEmpty()) {
                code = code.substring(0, code.length() - 1);
                beep(1.2f);
            }
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // enter
            press(11);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Een toets indrukken: een cijfer erbij, C = alles wissen, OK = versturen. */
    private void press(int index) {
        pressed = index;
        pressedUntil = System.currentTimeMillis() + 150;
        String key = KEYS[index];
        switch (key) {
            case "C" -> {
                code = "";
                beep(0.9f);
            }
            case "OK" -> submit();
            default -> {
                if (code.length() < CodePanelBlock.MAX_CODE) {
                    code += key;
                    beep(1.5f + 0.05f * (key.charAt(0) - '0'));
                }
            }
        }
    }

    private void submit() {
        if (code.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new CodePanelPayload(pos, code));
        code = "";
        onClose();
    }

    private static void beep(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.IMT_SELECT.get(), pitch, 0.35f));
    }

    /** Alleen voor de screenshot-rondleiding: deze cijfers alvast ingetypt. */
    public void tourType(String digits) {
        code = digits;
    }

    public BlockPos pos() {
        return pos;
    }
}
