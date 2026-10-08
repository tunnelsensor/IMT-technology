package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.client.voice.BrainMode;
import com.senne.starktech.client.voice.BrainModel;
import com.senne.starktech.client.voice.EdithBrain;
import com.senne.starktech.client.voice.EdithMemory;
import com.senne.starktech.client.voice.VoiceFeature;
import com.senne.starktech.client.voice.VoiceInstaller;
import java.io.IOException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * G > Systemen > AI Brain: kies hoe E.D.I.T.H. begrijpt wat je zegt.
 *
 * - Phrase book: het woordenboek (vaste zinnen).
 * - Local model: een taalmodel op je pc; de eerste keer wordt het gedownload (de mod kiest de
 *   maat bij je pc).
 * - Claude: via internet, met je eigen API-sleutel (van console.anthropic.com). De sleutel typ
 *   of plak je hier; je ziet hem als sterretjes.
 *
 * Verder: of ze uit zichzelf mag praten, en hoeveel ze over je onthouden heeft (met een knop om
 * alles te vergeten).
 */
public class BrainScreen extends Screen {
    private static final int PANEL_W = 330;
    private static final int PANEL_H = 234;

    private EditBox keyBox;
    private Button proactiveButton;
    private int left;
    private int top;
    private Component notice = Component.empty();

    public BrainScreen() {
        super(Component.translatable("screen.starktech.brain"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - PANEL_H) / 2);

        int y = top + 26;
        for (BrainMode mode : BrainMode.values()) {
            addRenderableWidget(Button.builder(label(mode), button -> EdithBrain.choose(mode))
                    .bounds(left + 16, y, 110, 20).build());
            y += 24;
        }

        keyBox = new EditBox(font, left + 10, top + 124, PANEL_W - 146, 18, Component.translatable("screen.starktech.brain.key"));
        keyBox.setMaxLength(256);
        // Een sleutel is geheim: we laten hem zien als sterretjes.
        keyBox.setFormatter((text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        keyBox.setHint(Component.translatable("screen.starktech.brain.key.hint").withStyle(style -> style.withColor(0x6A7B80)));
        addRenderableWidget(keyBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.brain.key.save"), button -> saveKey(keyBox.getValue()))
                .bounds(left + PANEL_W - 132, top + 123, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.brain.key.clear"), button -> saveKey(""))
                .bounds(left + PANEL_W - 68, top + 123, 58, 20).build());

        proactiveButton = addRenderableWidget(Button.builder(proactiveLabel(), button -> toggleProactive())
                .bounds(left + 10, top + 162, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.brain.forget_all"), button -> {
            EdithMemory.forgetAll();
            notice = Component.translatable("screen.starktech.brain.forgotten");
        }).bounds(left + PANEL_W - 110, top + 162, 100, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_W / 2 - 40, top + PANEL_H - 26, 80, 20).build());
    }

    private static Component label(BrainMode mode) {
        Component name = Component.translatable(mode.translationKey());
        return mode == BrainMode.LOCAL
                ? Component.translatable("screen.starktech.brain.local_size", name, BrainModel.current().size())
                : name;
    }

    private void saveKey(String key) {
        try {
            EdithBrain.saveClaudeKey(key);
            keyBox.setValue("");
            notice = Component.translatable(key.isBlank() ? "screen.starktech.brain.key.removed" : "screen.starktech.brain.key.saved");
        } catch (IOException e) {
            notice = Component.translatable("screen.starktech.brain.key.error", e.getMessage());
        }
    }

    private static Component proactiveLabel() {
        return Component.translatable("screen.starktech.brain.proactive", Component.translatable(
                EdithClientConfig.PROACTIVE.get() ? "screen.starktech.brain.on" : "screen.starktech.brain.off"));
    }

    private void toggleProactive() {
        EdithClientConfig.PROACTIVE.set(!EdithClientConfig.PROACTIVE.get());
        EdithClientConfig.SPEC.save();
        proactiveButton.setMessage(proactiveLabel());
    }

    /** "Datacenter: dev-lab (+2.0 s)", "Creative: no datacenter needed", of "No datacenter: no brain". */
    private static Component datacenterLine() {
        String source = EdithClientState.brainSource;
        int delay = EdithClientState.brainDelayTicks;
        if (delay < 0) {
            return Component.translatable("screen.starktech.brain.datacenter.none");
        }
        if ("creative".equals(source)) {
            return Component.translatable("screen.starktech.brain.datacenter.creative");
        }
        String extra = delay == 0 ? "no delay" : String.format(java.util.Locale.ROOT, "+%.1f s", delay / 20.0);
        return Component.translatable("screen.starktech.brain.datacenter", source, extra);
    }

    /** Wat er achter een keuze staat: wat het is, en voor het lokale model de download. */
    private static Component describe(BrainMode mode) {
        if (mode == BrainMode.LOCAL) {
            if (VoiceInstaller.state(VoiceFeature.COMMANDS) != VoiceInstaller.State.INSTALLED) {
                return Component.translatable("screen.starktech.brain.local.needs_ear");
            }
            return switch (VoiceInstaller.state(VoiceFeature.BRAIN)) {
                case INSTALLED -> Component.translatable("screen.starktech.brain.desc.local");
                case INSTALLING -> Component.translatable("screen.starktech.brain.local.installing", VoiceInstaller.percent(VoiceFeature.BRAIN));
                case UNSUPPORTED -> Component.translatable("screen.starktech.voice.unsupported");
                default -> Component.translatable("screen.starktech.brain.local.download", VoiceFeature.BRAIN.missingSize());
            };
        }
        return Component.translatable("screen.starktech.brain.desc." + mode.name().toLowerCase(java.util.Locale.ROOT));
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
        int white = HudStyle.argb(210, HudStyle.WHITE);
        int dim = HudStyle.argb(150, HudStyle.WHITE);
        g.drawString(font, title, left + 10, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);
        // Op welk datacenter het brein draait, en hoeveel het erbij zet (8 oktober).
        Component datacenter = datacenterLine();
        int dcColour = !EdithBrain.hasDatacenter() ? HudStyle.ORANGE : HudStyle.GREEN;
        g.drawString(font, datacenter, left + PANEL_W - 10 - font.width(datacenter), top + 8, HudStyle.argb(230, dcColour), false);

        // De drie keuzes: een streepje voor de actieve, en uitleg ernaast.
        BrainMode active = EdithBrain.mode();
        int y = top + 26;
        for (BrainMode mode : BrainMode.values()) {
            if (mode == active) {
                g.fill(left + 8, y + 2, left + 11, y + 18, HudStyle.argb(255, HudStyle.CYAN));
            }
            g.drawString(font, describe(mode), left + 132, y + 6, mode == active ? white : dim, false);
            y += 24;
        }

        g.drawString(font, Component.translatable("screen.starktech.brain.key"), left + 10, top + 112, white, false);
        Component keyState = EdithBrain.hasClaudeKey()
                ? Component.translatable("screen.starktech.brain.key.set", EdithBrain.maskedClaudeKey())
                : Component.translatable("screen.starktech.brain.key.missing");
        g.drawString(font, keyState, left + PANEL_W - 10 - font.width(keyState), top + 112, dim, false);
        g.drawString(font, Component.translatable("screen.starktech.brain.model", EdithClientConfig.CLAUDE_MODEL.get()),
                left + 10, top + 146, dim, false);

        g.drawString(font, Component.translatable("screen.starktech.brain.memory", EdithMemory.facts().size()),
                left + 10, top + 188, dim, false);
        if (!notice.getString().isEmpty()) {
            g.drawString(font, notice, left + PANEL_W - 10 - font.width(notice), top + 188, HudStyle.argb(230, HudStyle.GREEN), false);
        }
    }
}
