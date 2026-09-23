package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.network.RelayActionPayload;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.redstone.EdithRelays;
import com.senne.starktech.redstone.Relay;
import com.senne.starktech.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Eén relais een naam en een groep geven.
 *
 * Dit scherm opent als je met de bril op rechtsklikt op een E.D.I.T.H.-relais, of via
 * "wijzig" in het redstone-scherm.
 *
 * Tip: geef alles wat bij elkaar hoort dezelfde groep ("huis", "val", "stal"), dan kun je
 * met één commando alles in die groep tegelijk aan- of uitzetten.
 */
public class RelayEditScreen extends Screen {
    private static final int PANEL_W = 260;

    private final int index;
    private EditBox nameBox;
    private EditBox groupBox;
    private int left;
    private int top;

    public RelayEditScreen(int index) {
        super(Component.translatable("screen.starktech.relay_edit"));
        this.index = index;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static EdithRelays data() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? EdithRelays.EMPTY : player.getData(ModAttachments.RELAYS);
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(8, height / 2 - 60);
        Relay relay = data().get(index);

        nameBox = new EditBox(font, left + 10, top + 34, PANEL_W - 20, 18, Component.translatable("screen.starktech.relay_edit.name"));
        nameBox.setMaxLength(EdithBalance.MAX_RELAY_NAME);
        nameBox.setValue(relay == null ? "" : relay.name());
        nameBox.setHint(Component.translatable("screen.starktech.relay_edit.name_hint"));
        addRenderableWidget(nameBox);

        groupBox = new EditBox(font, left + 10, top + 68, PANEL_W - 20, 18, Component.translatable("screen.starktech.relay_edit.group"));
        groupBox.setMaxLength(EdithBalance.MAX_RELAY_NAME);
        groupBox.setValue(relay == null ? Relay.DEFAULT_GROUP : relay.group());
        groupBox.setHint(Component.translatable("screen.starktech.relay_edit.group_hint"));
        addRenderableWidget(groupBox);

        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relay_edit.save"), button -> save())
                .bounds(left + 10, top + 96, 100, 20).build());
        // Even testen of het werkt: een korte puls.
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relays.pulse"),
                        button -> PacketDistributor.sendToServer(RelayActionPayload.switchOne(index, EdithRedstone.Op.PULSE)))
                .bounds(left + 116, top + 96, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relay_edit.forget"), button -> forget())
                .bounds(left + 180, top + 96, 70, 20).build());
        setInitialFocus(nameBox);
    }

    private void save() {
        PacketDistributor.sendToServer(new RelayActionPayload(RelayActionPayload.Op.RENAME, EdithRedstone.Op.TOGGLE,
                index, nameBox.getValue(), groupBox.getValue()));
        onClose();
    }

    private void forget() {
        PacketDistributor.sendToServer(new RelayActionPayload(RelayActionPayload.Op.REMOVE, EdithRedstone.Op.TOGGLE,
                index, "", ""));
        onClose();
    }

    /**
     * Het paneel hoort bij de achtergrond. Minecraft tekent in render() eerst een wazige
     * achtergrond; wat daarvóór getekend was, werd dus mee vervaagd en verduisterd.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        int panelH = 128;
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + panelH, HudStyle.argb(210, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + panelH, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);

        g.drawString(font, title, left + 10, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);
        Relay relay = data().get(index);
        if (relay != null) {
            String where = relay.pos().getX() + " " + relay.pos().getY() + " " + relay.pos().getZ();
            g.drawString(font, where, left + PANEL_W - 10 - font.width(where), top + 8, HudStyle.argb(160, HudStyle.WHITE), true);
        }
        g.drawString(font, Component.translatable("screen.starktech.relay_edit.name"), left + 10, top + 24,
                HudStyle.argb(200, HudStyle.WHITE), false);
        g.drawString(font, Component.translatable("screen.starktech.relay_edit.group"), left + 10, top + 58,
                HudStyle.argb(200, HudStyle.WHITE), false);
    }
}
