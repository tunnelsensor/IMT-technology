package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.network.OsLoginPayload;
import com.senne.imttech.security.os.SystemPassword;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het inlogscherm van de Hacking Laptop (hoofdstuk 40, jouw keuze: "IP + wachtwoord", zoals SSH). Je opent het met een
 * rechtsklik in de lucht, waar je ook bent:
 * <pre>
 *   Hacking Laptop: SSH login
 *   IP address
 *   [10.12.34.56                                ]
 *   Password
 *   [********                                   ]
 *   The IP is in the Status app of a security core.
 *   Permission denied, please try again.
 *                              [Cancel] [Connect]
 * </pre>
 * Het wachtwoord zie je als sterretjes. De server kijkt alles na: lukt het, dan stuurt hij de desktop (en gaat dit
 * scherm vanzelf dicht); lukt het niet, dan staat er in het rood waarom. Het IP van de vorige keer staat er al in (de
 * laptop onthoudt het), het wachtwoord nooit.
 */
public class OsLoginScreen extends Screen {
    private static final int PANEL_W = 250;
    private static final int PANEL_H = 150;
    private static final int MARGIN = 10;
    private static final int BUTTON_W = 70;
    /** Een IP is hooguit 15 tekens (255.255.255.255). */
    private static final int MAX_IP = 15;

    private final String startIp;
    private @Nullable EditBox ipBox;
    private @Nullable EditBox passwordBox;
    private @Nullable Button connectButton;
    private int left;
    private int top;
    /** Wat de server terugzei (waarom het niet lukte), of "Connecting..." terwijl we wachten. */
    private String message = "";
    private boolean waiting;

    public OsLoginScreen(String ip) {
        super(Component.literal("Hacking Laptop: SSH login"));
        this.startIp = ip;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - PANEL_H) / 2);

        // De vakjes houden wat je al typte als het scherm opnieuw opbouwt (een andere venstergrootte).
        String ip = ipBox == null ? startIp : ipBox.getValue();
        String password = passwordBox == null ? "" : passwordBox.getValue();
        ipBox = new EditBox(font, left + MARGIN, top + 34, PANEL_W - 2 * MARGIN, 16, Component.literal("IP address"));
        ipBox.setMaxLength(MAX_IP);
        ipBox.setFilter(text -> text.chars().allMatch(c -> c >= '0' && c <= '9' || c == '.'));
        ipBox.setValue(ip);
        ipBox.setHint(Component.literal("10.x.x.x").withColor(OsDraw.GREY));
        addRenderableWidget(ipBox);

        passwordBox = new EditBox(font, left + MARGIN, top + 66, PANEL_W - 2 * MARGIN, 16, Component.literal("Password"));
        passwordBox.setMaxLength(SystemPassword.MAX_LENGTH);
        // Alleen wat een wachtwoord kan zijn (a-z en 0-9), en je ziet sterretjes in plaats van de letters.
        passwordBox.setFilter(text -> text.chars().allMatch(c -> c >= 'a' && c <= 'z' || c >= '0' && c <= '9'));
        passwordBox.setFormatter((text, start) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        passwordBox.setValue(password);
        addRenderableWidget(passwordBox);

        int buttons = top + PANEL_H - 28;
        int connectX = left + PANEL_W - MARGIN - BUTTON_W;
        connectButton = addRenderableWidget(Button.builder(Component.literal("Connect"), b -> connect())
                .bounds(connectX, buttons, BUTTON_W, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(connectX - 6 - BUTTON_W, buttons, BUTTON_W, 20).build());
        // Staat het IP er al, dan typ je meteen het wachtwoord.
        setInitialFocus(ip.isEmpty() ? ipBox : passwordBox);
        refreshConnect();
    }

    private String ip() {
        return ipBox == null ? "" : ipBox.getValue().strip();
    }

    private String password() {
        return passwordBox == null ? "" : passwordBox.getValue();
    }

    /** Verbinden kan pas met een IP en een wachtwoord, en niet terwijl we nog op de server wachten. */
    private void refreshConnect() {
        if (connectButton != null) {
            connectButton.active = !waiting && !ip().isEmpty() && !password().isEmpty();
        }
    }

    private void connect() {
        if (waiting || ip().isEmpty() || password().isEmpty()) {
            return;
        }
        waiting = true;
        message = "Connecting to " + ip() + "...";
        PacketDistributor.sendToServer(new OsLoginPayload(ip(), password()));
        refreshConnect();
    }

    /** Het antwoord van de server: inloggen lukte niet. Het wachtwoord gaat leeg, zodat je het opnieuw typt. */
    public void reply(String text) {
        waiting = false;
        message = text;
        if (passwordBox != null) {
            passwordBox.setValue("");
            setFocused(passwordBox);
        }
        refreshConnect();
    }

    /** Enter: in het IP-vak naar het wachtwoord, in het wachtwoord verbinden. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // enter
            if (getFocused() == ipBox && passwordBox != null && password().isEmpty()) {
                setFocused(passwordBox);
            } else {
                connect();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        refreshConnect();
    }

    // ---------------------------------------------------------------- Tekenen

    /** Een scherp paneel met een lichte verduistering, zoals de andere kleine schermen. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(215, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);
        g.drawString(font, title, left + MARGIN, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);
        g.drawString(font, "IP address", left + MARGIN, top + 24, HudStyle.argb(200, HudStyle.WHITE), false);
        g.drawString(font, "Password", left + MARGIN, top + 56, HudStyle.argb(200, HudStyle.WHITE), false);
        g.drawString(font, "The IP is in the Status app of a security core.", left + MARGIN, top + 88,
                HudStyle.argb(150, HudStyle.WHITE), false);
        if (!message.isEmpty()) {
            int colour = waiting ? HudStyle.CYAN : HudStyle.RED;
            int y = top + 101;
            for (FormattedCharSequence line : font.split(Component.literal(message), PANEL_W - 2 * MARGIN)) {
                g.drawString(font, line, left + MARGIN, y, HudStyle.argb(255, colour), false);
                y += 10;
            }
        }
    }

    // ---------------------------------------------------------------- Voor de screenshots en de showcase

    /** Alleen voor de screenshot-rondleiding en de showcase: dit IP en wachtwoord getypt. */
    public void tourType(String ip, String password) {
        if (ipBox != null && passwordBox != null) {
            ipBox.setValue(ip);
            passwordBox.setValue(password);
        }
        refreshConnect();
    }

    /** Alleen voor de screenshot-rondleiding en de showcase: op Connect geklikt. */
    public void tourConnect() {
        connect();
    }
}
