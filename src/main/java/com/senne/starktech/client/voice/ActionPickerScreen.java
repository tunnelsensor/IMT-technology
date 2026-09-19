package com.senne.starktech.client.voice;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

/**
 * Kies een actie voor een spraakcommando. Alle acties staan in vier kolommen: Gevecht, Scanners,
 * Navigatie en Vragen. Klik er één aan, dan ga je terug naar het bewerkscherm.
 */
public class ActionPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<VoiceAction> onChosen;

    public ActionPickerScreen(Screen parent, Consumer<VoiceAction> onChosen) {
        super(Component.translatable("screen.starktech.voice_edit.pick_action"));
        this.parent = parent;
        this.onChosen = onChosen;
    }

    @Override
    protected void init() {
        VoiceAction.Group[] groups = VoiceAction.Group.values();
        int columns = groups.length;
        int columnWidth = Math.min(130, (width - 20) / columns - 4);
        int left = (width - columns * (columnWidth + 4)) / 2;
        int top = 40;
        for (int column = 0; column < columns; column++) {
            int y = top;
            for (VoiceAction action : VoiceAction.values()) {
                if (action.group() != groups[column]) {
                    continue;
                }
                Button button = Button.builder(Component.translatable(action.translationKey()), b -> {
                            onChosen.accept(action);
                            minecraft.setScreen(parent);
                        })
                        .bounds(left + column * (columnWidth + 4), y, columnWidth, 16)
                        .build();
                button.setTooltip(Tooltip.create(Component.translatable(action.translationKey() + ".desc")));
                addRenderableWidget(button);
                y += 18;
            }
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        VoiceAction.Group[] groups = VoiceAction.Group.values();
        int columns = groups.length;
        int columnWidth = Math.min(130, (width - 20) / columns - 4);
        int left = (width - columns * (columnWidth + 4)) / 2;
        for (int column = 0; column < columns; column++) {
            Component header = Component.translatable("screen.starktech.voice_edit.group." + groups[column].name().toLowerCase(java.util.Locale.ROOT));
            g.drawCenteredString(font, header, left + column * (columnWidth + 4) + columnWidth / 2, 28, 0xFF3FD8FF);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
