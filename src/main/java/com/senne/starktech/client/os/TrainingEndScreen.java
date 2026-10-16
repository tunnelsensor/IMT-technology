package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.logic.program.LogicException;
import com.senne.starktech.logic.program.ProgramKind;
import com.senne.starktech.logic.program.ScriptChecker;
import com.senne.starktech.logic.program.ScriptParser;
import com.senne.starktech.network.TrainingActionPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * De mini-editor van een Training End-blok (hoofdstuk 26, jouw antwoord: "een mini-code-editor in het blok"). Dezelfde
 * code-editor als in de app Logic (kleuren, Ctrl+Spatie, ongedaan maken), maar voor één programmaatje: wat er gebeurt
 * als de trainee geraakt wordt ({@code when trainee_hit:}), met {@code trainee}, {@code lives} en {@code hits}.
 *
 * <pre>
 *  Training End                       runs when the trainee is hit
 *  ┌────────────────────────────────────────────────────────────┐
 *  │ when trainee_hit:                                                  │
 *  │     say("You're hit, cadet")                               │
 *  │     if lives == 0:                                         │
 *  │         deactivate("Wave 1")                               │
 *  └────────────────────────────────────────────────────────────┘
 *  No problems                              [Check] [Save] [Cancel]
 * </pre>
 * Save stuurt de code naar de server; die kijkt hem nog eens na (en bewaart hem alleen als hij klopt).
 */
public class TrainingEndScreen extends Screen {
    private static final int MARGIN = 8;

    private final BlockPos pos;
    private final CodeEditor editor;
    private final List<String> outputs = new ArrayList<>();
    private final List<String> robots = new ArrayList<>();
    private String status = "";
    private boolean statusError;
    private int left;
    private int top;
    private int panelW;
    private int panelH;

    public TrainingEndScreen(BlockPos pos, CompoundTag data) {
        super(Component.literal("Training End"));
        this.pos = pos;
        this.editor = new CodeEditor(net.minecraft.client.Minecraft.getInstance().font);
        for (Tag tag : data.getList("Outputs", Tag.TAG_STRING)) {
            outputs.add(tag.getAsString());
        }
        for (Tag tag : data.getList("Robots", Tag.TAG_STRING)) {
            robots.add(tag.getAsString());
        }
        editor.setCompletionData(() -> new CodeCompletion.Data(outputs, List.of(), List.of(), ProgramKind.TRAINING_END, robots));
        editor.setText(data.getString("Code"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 20, 420);
        panelH = Math.min(height - 20, 240);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        editor.setBounds(left + MARGIN, top + 22, panelW - 2 * MARGIN, panelH - 22 - 32);
        editor.setFocused(true);
        int y = top + panelH - 26;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(left + panelW - MARGIN - 60, y, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(left + panelW - MARGIN - 126, y, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Check"), b -> check())
                .bounds(left + panelW - MARGIN - 192, y, 60, 20).build());
        check();
    }

    /** Klopt de code? Dan "No problems", anders de fout met zijn regel (die kleurt rood). */
    private boolean check() {
        try {
            var script = ScriptParser.parse(editor.text(), ProgramKind.TRAINING_END);
            var problems = ScriptChecker.check(script, ProgramKind.TRAINING_END);
            if (!problems.isEmpty()) {
                throw new LogicException(Math.max(1, problems.getFirst().ref()), problems.getFirst().message());
            }
            editor.setErrorLine(0);
            status = "No problems";
            statusError = false;
            return true;
        } catch (LogicException e) {
            editor.setErrorLine(e.line());
            status = e.describe();
            statusError = true;
            return false;
        }
    }

    private void save() {
        if (!check()) {
            return; // eerst de fout oplossen
        }
        CompoundTag data = new CompoundTag();
        data.putString("Code", editor.text());
        PacketDistributor.sendToServer(new TrainingActionPayload(pos, data));
        onClose();
    }

    // ---------------------------------------------------------------- Toetsen en muis naar de editor

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256) { // Esc: dicht (zonder bewaren)
            onClose();
            return true;
        }
        if (editor.keyPressed(key, scan, modifiers)) {
            editor.setErrorLine(0);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return editor.charTyped(c, modifiers) || super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        return editor.mouseClicked((int) mx, (int) my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return editor.mouseDragged((int) mx, (int) my) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        editor.mouseReleased();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        editor.mouseScrolled(scrollY);
        return true;
    }

    // ---------------------------------------------------------------- Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + panelW + 4, top + panelH + 4, HudStyle.argb(215, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + panelW + 4, top + panelH + 4, 8, HudStyle.argb(220, HudStyle.RED));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);
        g.drawString(font, "Training End", left + MARGIN, top + 7, HudStyle.argb(255, HudStyle.RED), true);
        String hint = "when trainee_hit: runs when the trainee is hit (trainee, lives, hits)";
        g.drawString(font, OsDraw.fitLeft(font, hint, panelW - 2 * MARGIN - font.width("Training End") - 10),
                left + panelW - MARGIN - Math.min(font.width(hint), panelW - 2 * MARGIN - font.width("Training End") - 10), top + 7,
                HudStyle.argb(180, 0x9FC9D6), false);
        editor.render(g, mouseX, mouseY);
        g.drawString(font, OsDraw.fitLeft(font, status, panelW - 2 * MARGIN - 200), left + MARGIN, top + panelH - 20,
                HudStyle.argb(255, statusError ? HudStyle.RED : HudStyle.GREEN), false);
    }
}
