package com.senne.starktech.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.network.CommsPayload;
import com.senne.starktech.network.PingPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Het comms-scherm: stuur een bericht naar iedereen met een E.D.I.T.H.-bril, of naar één
 * speler. Met snelknoppen voor veelgebruikte berichten. Openen met O (of via het G-menu).
 */
public class CommsScreen extends Screen {
    private static final int PANEL_W = 300;
    /** Snelberichten (vertaalsleutels). De tekst gaat in jouw taal naar de ontvanger. */
    private static final String[] PRESETS = {
            "screen.starktech.comms.preset.help",
            "screen.starktech.comms.preset.follow",
            "screen.starktech.comms.preset.enemy",
            "screen.starktech.comms.preset.omw",
            "screen.starktech.comms.preset.regroup",
            "screen.starktech.comms.preset.clear"
    };

    private final List<String> recipients = new ArrayList<>();
    private int recipientIndex;
    private EditBox messageBox;
    private String pendingMessage = "";
    private int left;
    private int top;

    public CommsScreen() {
        super(Component.translatable("screen.starktech.comms"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(8, (height - 210) / 2);

        // Ontvangers: "iedereen" en alle andere spelers die online zijn.
        recipients.clear();
        recipients.add("");
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && mc.player != null) {
            String self = mc.player.getGameProfile().getName();
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                String name = info.getProfile().getName();
                if (!name.equals(self)) {
                    recipients.add(name);
                }
            }
        }
        recipients.subList(1, recipients.size()).sort(String.CASE_INSENSITIVE_ORDER);
        recipientIndex = Math.min(recipientIndex, recipients.size() - 1);

        addRenderableWidget(Button.builder(recipientLabel(), button -> {
            recipientIndex = (recipientIndex + 1) % recipients.size();
            button.setMessage(recipientLabel());
        }).bounds(left + 8, top + 24, PANEL_W - 16, 20).build());

        messageBox = new EditBox(font, left + 8, top + 50, PANEL_W - 84, 20, Component.translatable("screen.starktech.comms.message"));
        messageBox.setMaxLength(EdithBalance.MAX_MESSAGE_LENGTH);
        messageBox.setValue(pendingMessage);
        messageBox.setResponder(value -> pendingMessage = value);
        messageBox.setHint(Component.translatable("screen.starktech.comms.message_hint"));
        addRenderableWidget(messageBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.comms.send"), button -> sendTyped())
                .bounds(left + PANEL_W - 72, top + 50, 64, 20).build());

        // Snelberichten in twee kolommen
        int columnWidth = (PANEL_W - 20) / 2;
        for (int i = 0; i < PRESETS.length; i++) {
            String key = PRESETS[i];
            int x = left + 8 + (i % 2) * (columnWidth + 4);
            int y = top + 80 + (i / 2) * 24;
            addRenderableWidget(Button.builder(Component.translatable(key), button -> send(Component.translatable(key).getString()))
                    .bounds(x, y, columnWidth, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.comms.ping_me"), button -> pingMyLocation())
                .bounds(left + 8, top + 156, PANEL_W - 16, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_W - 88, top + 182, 80, 20).build());
        setInitialFocus(messageBox);
    }

    private Component recipientLabel() {
        String recipient = recipients.get(recipientIndex);
        return recipient.isEmpty()
                ? Component.translatable("screen.starktech.comms.to_all")
                : Component.translatable("screen.starktech.comms.to", recipient);
    }

    private void sendTyped() {
        if (!pendingMessage.isBlank()) {
            send(pendingMessage);
        }
    }

    private void send(String text) {
        PacketDistributor.sendToServer(new CommsPayload(recipients.get(recipientIndex), text));
        pendingMessage = "";
        onClose();
    }

    private void pingMyLocation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            PacketDistributor.sendToServer(new PingPayload(mc.player.position().add(0, mc.player.getBbHeight() + 0.5, 0)));
        }
        onClose();
    }

    /** Enter in het tekstvak = versturen. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_RETURN && messageBox.isFocused()) {
            sendTyped();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (!EdithClientState.authorized) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + 208, EdithHud.argb(200, 0x08121A));
        EdithHud.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + 208, 8, EdithHud.argb(220, EdithHud.GREEN));
        super.render(g, mouseX, mouseY, a);
        g.drawString(font, title, left + 8, top + 8, EdithHud.argb(255, EdithHud.GREEN), true);
    }
}
