package com.senne.imttech.client;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.logic.CodePanelBlock;
import com.senne.imttech.logic.InputDeviceBlock;
import com.senne.imttech.logic.InputDeviceBlockEntity;
import com.senne.imttech.network.InputRenamePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het naamscherm van een keycardlezer of codepaneel (13 oktober). Openen: sneak + rechtsklik met een lege hand,
 * als jij hem neerzette of als je in deze basis vertrouwd bent (de server kijkt dat na, zie logic/InputDevices).
 * Bij een codepaneel staat er {@code when codepanel("...")} onder het vakje.
 * <pre>
 *   Keycard Reader                    Placed by Senne
 *   Name
 *   [Lab door                                    ]
 *   In a diagram:
 *   when keycard("Lab door"):
 *                                   [Cancel] [Save]
 * </pre>
 * Enter of Save bewaart, Esc of Cancel laat het zoals het was. Onder het vakje zie je meteen hoe je de lezer in
 * een diagram noemt (in de kleuren van de app Logic). Typ je een andere naam, dan zegt het scherm dat je die ook
 * in je diagrammen moet veranderen: een diagram met de oude naam reageert anders niet meer op deze lezer.
 */
public class InputNameScreen extends Screen {
    private static final int PANEL_W = 250;
    private static final int PANEL_H = 126;
    private static final int MARGIN = 10;
    private static final int BUTTON_W = 70;

    /** De kleuren van de app Logic (client/os/CodeEditor): when, de trigger, tekst tussen aanhalingstekens. */
    private static final int KEYWORD = 0xFF79C6;
    private static final int STRING = 0x6BE88B;
    private static final int PLAIN = 0xE8EEF2;

    private final BlockPos pos;
    private final String oldName;
    private final String owner;
    private @Nullable EditBox nameBox;
    private @Nullable Button saveButton;
    private int left;
    private int top;

    public InputNameScreen(BlockPos pos, String name, String owner) {
        super(nameOf(pos));
        this.pos = pos;
        this.oldName = name;
        this.owner = owner;
    }

    /** De titel: "Keycard Reader" of "Code Panel", zoals het blok heet. */
    private static Component nameOf(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? Component.translatable("block.imttech.keycard_reader")
                : mc.level.getBlockState(pos).getBlock().getName();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** De lezer (of het paneel) zoals hij nu in de wereld hangt, of null als hij weg is. */
    private @Nullable BlockState device() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        BlockState state = mc.level.getBlockState(pos);
        return state.getBlock() instanceof InputDeviceBlock ? state : null;
    }

    /** De naam zoals de server hem bewaart (zonder spaties eromheen, aanhalingstekens of te lange staart). */
    private String typed() {
        return nameBox == null ? oldName : InputDeviceBlockEntity.cleanName(nameBox.getValue());
    }

    @Override
    protected void init() {
        BlockState state = device();
        if (state == null) {
            onClose();
            return;
        }
        left = (width - PANEL_W) / 2;
        top = Math.max(6, (height - PANEL_H) / 2);

        // Het vakje houdt wat je al typte als het scherm opnieuw opbouwt (bijvoorbeeld een ander venstergrootte).
        String value = nameBox == null ? oldName : nameBox.getValue();
        nameBox = new EditBox(font, left + MARGIN, top + 34, PANEL_W - 2 * MARGIN, 18,
                Component.translatable("screen.imttech.input_name.name"));
        nameBox.setMaxLength(InputDeviceBlockEntity.MAX_NAME);
        // Aanhalingstekens en een backslash kunnen niet in een naam (dan klopt de code van je diagram niet meer).
        nameBox.setFilter(text -> text.indexOf('"') < 0 && text.indexOf('\\') < 0);
        nameBox.setValue(value);
        // Alles geselecteerd: typen vervangt de oude naam (zoals Rename in de app Devices).
        nameBox.moveCursorToEnd(false);
        nameBox.setHighlightPos(0);
        nameBox.setHint(Component.translatable("screen.imttech.input_name.hint"));
        addRenderableWidget(nameBox);

        int buttons = top + PANEL_H - 28;
        int saveX = left + PANEL_W - MARGIN - BUTTON_W;
        saveButton = addRenderableWidget(Button.builder(Component.translatable("screen.imttech.input_name.save"), b -> save())
                .bounds(saveX, buttons, BUTTON_W, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(saveX - 6 - BUTTON_W, buttons, BUTTON_W, 20).build());
        setInitialFocus(nameBox);
        refreshSave();
    }

    /** Een lege naam kun je niet bewaren. */
    private void refreshSave() {
        if (saveButton != null) {
            saveButton.active = !typed().isEmpty();
        }
    }

    private void save() {
        if (typed().isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new InputRenamePayload(pos, typed()));
        onClose();
    }

    /** Enter bewaart (Esc sluit zonder bewaren, dat doet Minecraft zelf). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // enter
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Elke tick: de knop Save bijwerken, en dicht als de lezer weg is (afgebroken). */
    @Override
    public void tick() {
        super.tick();
        if (device() == null) {
            onClose();
            return;
        }
        refreshSave();
    }

    // ---------------------------------------------------------------- Tekenen

    /** Een scherp paneel met een lichte verduistering (zoals de laserzender), zodat je de lezer erachter ziet. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(205, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        BlockState state = device();
        if (state == null) {
            return;
        }
        super.render(g, mouseX, mouseY, a);
        g.drawString(font, title, left + MARGIN, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);
        // Rechtsboven: wie hem neerzette.
        Component placedBy = owner.isEmpty()
                ? Component.translatable("screen.imttech.input_name.no_owner")
                : Component.translatable("screen.imttech.input_name.owner", owner);
        g.drawString(font, placedBy, left + PANEL_W - MARGIN - font.width(placedBy), top + 8,
                HudStyle.argb(150, 0x9FC9D6), false);
        g.drawString(font, Component.translatable("screen.imttech.input_name.name"), left + MARGIN, top + 24,
                HudStyle.argb(200, HudStyle.WHITE), false);

        // Zo noem je hem in een diagram: when keycard("Lab door"):
        g.drawString(font, Component.translatable("screen.imttech.input_name.in_diagram"), left + MARGIN, top + 60,
                HudStyle.argb(150, HudStyle.WHITE), false);
        String trigger = state.getBlock() instanceof CodePanelBlock ? "codepanel" : "keycard";
        Component code = Component.literal("when ").withColor(KEYWORD)
                .append(Component.literal(trigger).withColor(HudStyle.CYAN))
                .append(Component.literal("(").withColor(PLAIN))
                .append(Component.literal("\"" + typed() + "\"").withColor(STRING))
                .append(Component.literal("):").withColor(PLAIN));
        g.drawString(font, code, left + MARGIN, top + 72, HudStyle.argb(255, PLAIN), false);

        // Een andere naam: dan ook in je diagrammen (die zoeken de lezer of het paneel op zijn naam).
        String now = typed();
        if (!oldName.isEmpty() && !now.isEmpty() && !now.equalsIgnoreCase(oldName)) {
            g.drawString(font, Component.translatable("screen.imttech.input_name.changed"), left + MARGIN, top + 84,
                    HudStyle.argb(230, HudStyle.ORANGE), false);
        }
    }

    // ---------------------------------------------------------------- Voor de screenshots en de showcase

    /** Alleen voor de screenshot-rondleiding en de showcase: deze naam getypt. */
    public void tourType(String name) {
        if (nameBox != null) {
            nameBox.setValue(name);
        }
        refreshSave();
    }

    /** Alleen voor de screenshot-rondleiding: op Save geklikt. */
    public void tourSave() {
        save();
    }
}
