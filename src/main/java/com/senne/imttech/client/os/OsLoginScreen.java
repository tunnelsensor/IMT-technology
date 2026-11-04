package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.network.OsLoginPayload;
import com.senne.imttech.security.os.SystemPassword;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
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
 *
 * Hetzelfde scherm vraagt ook de code van een Computer (hoofdstuk 40, een rechtsklik op zijn monitor): dan staat er
 * alleen het vak voor de code, met de naam van de computer bovenaan. En sinds hoofdstuk 50 de schermen van de I.M.T
 * Phone: verbinden (IP + wachtwoord), een PIN kiezen, en de PIN intypen (zie {@link Mode}).
 */
public class OsLoginScreen extends Screen {
    /** Wat dit scherm vraagt. */
    public enum Mode {
        /** De laptop: IP en wachtwoord (SSH). */
        LAPTOP,
        /** Een Computer: alleen zijn code. */
        COMPUTER,
        /** De telefoon de eerste keer: IP en wachtwoord van de kern. */
        PHONE_LOGIN,
        /** De telefoon openen: de PIN van vier cijfers. */
        PHONE_PIN,
        /** De telefoon net verbonden: een PIN kiezen. */
        PHONE_SET_PIN
    }

    private static final int PANEL_W = 250;
    private static final int PANEL_H = 150;
    private static final int MARGIN = 10;
    private static final int BUTTON_W = 70;
    /** Een IP is hooguit 15 tekens (255.255.255.255). */
    private static final int MAX_IP = 15;

    private final Mode mode;
    private final String startIp;
    /** De monitor van een Computer met een code, of null. */
    private final @Nullable BlockPos monitor;
    private @Nullable EditBox ipBox;
    private @Nullable EditBox passwordBox;
    private @Nullable Button connectButton;
    private int left;
    private int top;
    /** Wat de server terugzei (waarom het niet lukte), of "Connecting..." terwijl we wachten. */
    private String message = "";
    private boolean waiting;

    public OsLoginScreen(String ip) {
        this(Mode.LAPTOP, ip);
    }

    /** Het codescherm van een Computer (de monitor waarop je klikte, en de naam van de computer). */
    public OsLoginScreen(BlockPos monitor, String computerName) {
        super(Component.literal(computerName + ": enter the code"));
        this.mode = Mode.COMPUTER;
        this.startIp = "";
        this.monitor = monitor;
    }

    /** Een scherm van de laptop of de telefoon (hoofdstuk 50); {@code label} is het IP of de naam van het systeem. */
    public OsLoginScreen(Mode mode, String label) {
        super(Component.literal(switch (mode) {
            case LAPTOP -> "Hacking Laptop: SSH login";
            case COMPUTER -> label + ": enter the code";
            case PHONE_LOGIN -> "I.M.T Phone: connect to a system";
            case PHONE_PIN -> "I.M.T Phone: " + label;
            case PHONE_SET_PIN -> "I.M.T Phone: choose a PIN for " + label;
        }));
        this.mode = mode;
        this.startIp = mode == Mode.LAPTOP || mode == Mode.PHONE_LOGIN ? label : "";
        this.monitor = null;
    }

    public Mode mode() {
        return mode;
    }

    /** Vraagt dit scherm een IP en een wachtwoord (de laptop, of de telefoon de eerste keer)? */
    private boolean asksIp() {
        return mode == Mode.LAPTOP || mode == Mode.PHONE_LOGIN;
    }

    /** Vraagt dit scherm een PIN van vier cijfers? */
    private boolean asksPin() {
        return mode == Mode.PHONE_PIN || mode == Mode.PHONE_SET_PIN;
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
        if (asksIp()) {
            addRenderableWidget(ipBox); // een Computer of een PIN heeft geen IP-vak
        }

        passwordBox = new EditBox(font, left + MARGIN, top + 66, PANEL_W - 2 * MARGIN, 16,
                Component.literal(asksPin() ? "PIN" : "Password"));
        if (asksPin()) {
            passwordBox.setMaxLength(4);
            passwordBox.setFilter(text -> text.chars().allMatch(c -> c >= '0' && c <= '9'));
        } else {
            passwordBox.setMaxLength(SystemPassword.MAX_LENGTH);
            // Alleen wat een wachtwoord kan zijn (a-z en 0-9).
            passwordBox.setFilter(text -> text.chars().allMatch(c -> c >= 'a' && c <= 'z' || c >= '0' && c <= '9'));
        }
        // Je ziet sterretjes in plaats van de letters.
        passwordBox.setFormatter((text, start) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        passwordBox.setValue(password);
        addRenderableWidget(passwordBox);

        int buttons = top + PANEL_H - 28;
        int connectX = left + PANEL_W - MARGIN - BUTTON_W;
        String label = switch (mode) {
            case LAPTOP, PHONE_LOGIN -> "Connect";
            case COMPUTER -> "Connect";
            case PHONE_PIN -> "Unlock";
            case PHONE_SET_PIN -> "Set PIN";
        };
        connectButton = addRenderableWidget(Button.builder(Component.literal(label), b -> connect())
                .bounds(connectX, buttons, BUTTON_W, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(connectX - 6 - BUTTON_W, buttons, BUTTON_W, 20).build());
        // Staat het IP er al, dan typ je meteen het wachtwoord.
        setInitialFocus(asksIp() && ip.isEmpty() ? ipBox : passwordBox);
        refreshConnect();
    }

    private String ip() {
        if (!asksIp()) {
            return "-"; // een Computer of een PIN heeft geen IP: alleen de code telt
        }
        return ipBox == null ? "" : ipBox.getValue().strip();
    }

    private String password() {
        return passwordBox == null ? "" : passwordBox.getValue();
    }

    /** Verbinden kan pas met een IP en een wachtwoord (of een PIN van vier cijfers), en niet terwijl we op de server wachten. */
    private void refreshConnect() {
        if (connectButton != null) {
            boolean complete = asksPin() ? password().length() == 4 : !password().isEmpty();
            connectButton.active = !waiting && !ip().isEmpty() && complete;
        }
    }

    private void connect() {
        if (waiting || ip().isEmpty() || password().isEmpty() || asksPin() && password().length() != 4) {
            return;
        }
        waiting = true;
        switch (mode) {
            case COMPUTER -> {
                message = "Checking the code...";
                PacketDistributor.sendToServer(new com.senne.imttech.network.ComputerCodePayload(monitor, password()));
            }
            case LAPTOP -> {
                message = "Connecting to " + ip() + "...";
                PacketDistributor.sendToServer(new OsLoginPayload(ip(), password()));
            }
            case PHONE_LOGIN -> {
                message = "Connecting to " + ip() + "...";
                PacketDistributor.sendToServer(new com.senne.imttech.network.PhoneLoginPayload(ip(), password()));
            }
            case PHONE_PIN, PHONE_SET_PIN -> {
                message = mode == Mode.PHONE_PIN ? "Checking the PIN..." : "Saving the PIN...";
                PacketDistributor.sendToServer(new com.senne.imttech.network.PhonePinPayload(password()));
            }
        }
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
        switch (mode) {
            case COMPUTER -> {
                g.drawString(font, "Code", left + MARGIN, top + 56, HudStyle.argb(200, HudStyle.WHITE), false);
                g.drawString(font, "This computer asks for a code.", left + MARGIN, top + 30, HudStyle.argb(150, HudStyle.WHITE), false);
            }
            case PHONE_PIN -> {
                g.drawString(font, "PIN", left + MARGIN, top + 56, HudStyle.argb(200, HudStyle.WHITE), false);
                g.drawString(font, "Four digits. Wrong too often: the phone locks, and the owner is told.", left + MARGIN, top + 30,
                        HudStyle.argb(150, HudStyle.WHITE), false);
            }
            case PHONE_SET_PIN -> {
                g.drawString(font, "New PIN", left + MARGIN, top + 56, HudStyle.argb(200, HudStyle.WHITE), false);
                g.drawString(font, "Four digits. You type it every time you open the phone.", left + MARGIN, top + 30,
                        HudStyle.argb(150, HudStyle.WHITE), false);
            }
            default -> {
                g.drawString(font, "IP address", left + MARGIN, top + 24, HudStyle.argb(200, HudStyle.WHITE), false);
                g.drawString(font, "Password", left + MARGIN, top + 56, HudStyle.argb(200, HudStyle.WHITE), false);
                g.drawString(font, mode == Mode.PHONE_LOGIN ? "The IP (Status) and the password (Settings) of your core."
                        : "The IP is in the Status app of a security core.", left + MARGIN, top + 88, HudStyle.argb(150, HudStyle.WHITE), false);
            }
        }
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

    /** Alleen voor de screenshot-rondleiding en de showcase: dit IP en wachtwoord (of deze PIN) getypt. */
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
