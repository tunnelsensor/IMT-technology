package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.network.EmitterOpenPayload;
import com.senne.starktech.network.EmitterSavePayload;
import com.senne.starktech.redstone.EdithRelays;
import com.senne.starktech.redstone.RelayEmitterBlock;
import com.senne.starktech.redstone.RelayEmitterBlockEntity;
import com.senne.starktech.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Het scherm van de relay emitter (13 oktober): welke relais hij schakelt, en wat hij doet als hij
 * redstone krijgt.
 *
 * <ul>
 *   <li><b>Relay or tag</b>: de naam van één relais ("voordeur"), of een tag ("base" = alles wat erin
 *       zit, ook in tags eronder). Terwijl je typt, zie je hoeveel apparaten dat zijn.</li>
 *   <li><b>Mode</b>: Follow (redstone aan = aan, uit = uit), Toggle (elke keer omschakelen) of Pulse
 *       (elke keer een korte puls).</li>
 * </ul>
 * Is de emitter van iemand anders, dan zie je alleen van wie hij is en wat hij doet.
 */
public class RelayEmitterScreen extends Screen {
    private static final int PANEL_W = 260;
    private static final int PANEL_H = 170;

    private final EmitterOpenPayload start;
    private RelayEmitterBlockEntity.Mode mode;
    private EditBox targetBox;
    private Button modeButton;
    private int left;
    private int top;

    public RelayEmitterScreen(EmitterOpenPayload start) {
        super(Component.translatable("screen.starktech.relay_emitter"));
        this.start = start;
        this.mode = RelayEmitterBlockEntity.Mode.of(start.mode());
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
        top = Math.max(8, (height - PANEL_H) / 2);

        targetBox = new EditBox(font, left + 10, top + 46, PANEL_W - 20, 18, Component.translatable("screen.starktech.relay_emitter.target"));
        targetBox.setMaxLength(Math.round(StarkConfig.Relays.MAX_NAME));
        targetBox.setValue(start.target());
        targetBox.setHint(Component.translatable("screen.starktech.relay_emitter.target_hint"));
        targetBox.setEditable(start.mine());
        addRenderableWidget(targetBox);

        modeButton = Button.builder(modeLabel(), button -> {
                    mode = RelayEmitterBlockEntity.Mode.of(mode.ordinal() + 1);
                    button.setMessage(modeLabel());
                })
                .bounds(left + 10, top + 84, PANEL_W - 20, 20).build();
        modeButton.active = start.mine();
        addRenderableWidget(modeButton);

        int buttons = top + PANEL_H - 28;
        if (start.mine()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relay_edit.save"), button -> save())
                    .bounds(left + 10, buttons, 110, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + PANEL_W - 120, buttons, 110, 20).build());
        if (start.mine()) {
            setInitialFocus(targetBox);
        }
    }

    private Component modeLabel() {
        return Component.translatable("screen.starktech.relay_emitter.mode", Component.translatable(mode.key()));
    }

    private void save() {
        PacketDistributor.sendToServer(new EmitterSavePayload(start.pos(), targetBox.getValue(), mode.ordinal()));
        onClose();
    }

    /** Enter = opslaan. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (start.mine() && (keyCode == 257 || keyCode == 335)) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(210, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);
        g.drawString(font, title, left + 10, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);

        // Krijgt hij nu redstone? (Het lampje op het blok.)
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.level == null ? null : mc.level.getBlockState(start.pos());
        boolean powered = state != null && state.getBlock() instanceof RelayEmitterBlock && state.getValue(RelayEmitterBlock.POWERED);
        Component input = Component.translatable(powered ? "screen.starktech.relay_emitter.input_on" : "screen.starktech.relay_emitter.input_off");
        g.drawString(font, input, left + PANEL_W - 10 - font.width(input), top + 8,
                HudStyle.argb(255, powered ? 0xFF5A4A : 0x8095A0), true);

        g.drawString(font, Component.translatable("screen.starktech.relay_emitter.owner", start.owner()), left + 10, top + 22,
                HudStyle.argb(150, 0x9FC9D6), false);
        g.drawString(font, Component.translatable("screen.starktech.relay_emitter.target"), left + 10, top + 36,
                HudStyle.argb(200, HudStyle.WHITE), false);
        // Hoeveel apparaten die naam of tag nu zijn (in jouw eigen lijst; van een ander: wat de server zei).
        int found = start.mine() ? data().named(targetBox.getValue()).size() : start.found();
        if (!targetBox.getValue().isBlank()) {
            Component line = found == 0
                    ? Component.translatable("screen.starktech.relay_emitter.none")
                    : Component.translatable("screen.starktech.relay_emitter.found", found);
            g.drawString(font, line, left + 10, top + 68, HudStyle.argb(200, found == 0 ? HudStyle.ORANGE : HudStyle.GREEN), false);
        }
        HudStyle.wrappedCentered(g, font, Component.translatable(mode.key() + ".tip"), left + PANEL_W / 2, top + 108,
                PANEL_W - 20, HudStyle.argb(150, HudStyle.WHITE));
    }
}
