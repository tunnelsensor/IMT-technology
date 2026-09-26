package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.workbench.Blueprint;
import com.senne.starktech.workbench.SuitWorkbenchBlockEntity;
import com.senne.starktech.workbench.SuitWorkbenchMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Het scherm van de harnas-werkbank, in de stijl van E.D.I.T.H. (zelf getekend, geen plaatje).
 *
 * Links de materialen, in het midden drie knoppen om een stuk harnas te bouwen (wat het kost
 * zie je als je de muis erop houdt), rechts het werkvak en het vak voor een upgrade, met de
 * voortgang ertussen.
 */
public class SuitWorkbenchScreen extends AbstractContainerScreen<SuitWorkbenchMenu> {
    private final List<Button> buildButtons = new ArrayList<>();

    public SuitWorkbenchScreen(SuitWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 210;
        inventoryLabelY = SuitWorkbenchMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        buildButtons.clear();
        for (Blueprint blueprint : Blueprint.values()) {
            String name = blueprint.name().toLowerCase(Locale.ROOT);
            Button button = Button.builder(Component.translatable("screen.starktech.workbench.build." + name),
                            b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, blueprint.ordinal()))
                    .bounds(leftPos + 66, topPos + 20 + blueprint.ordinal() * 22, 62, 20)
                    .tooltip(Tooltip.create(costText(blueprint)))
                    .build();
            buildButtons.add(addRenderableWidget(button));
        }
    }

    /** "Needs: 5 Netherite Ingot, 1 Amethyst Shard, 2 Stark Chip". */
    private static Component costText(Blueprint blueprint) {
        List<String> parts = new ArrayList<>();
        for (Blueprint.Cost cost : blueprint.costs()) {
            var items = cost.ingredient().getItems();
            String name = items.length > 0 ? items[0].getHoverName().getString() : "?";
            parts.add(cost.count() + " " + name);
        }
        return Component.translatable("screen.starktech.workbench.needs", String.join(", ", parts));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        boolean free = menu.job() == SuitWorkbenchBlockEntity.JOB_NONE && menu.workItem().isEmpty();
        for (Blueprint blueprint : Blueprint.values()) {
            buildButtons.get(blueprint.ordinal()).active = free && menu.hasMaterials(blueprint);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, HudStyle.argb(235, 0x061820));
        HudStyle.brackets(g, x, y, x + imageWidth, y + imageHeight, 8, HudStyle.argb(230, HudStyle.CYAN));
        // Een donker vakje met een lichte rand achter elk slot.
        for (Slot slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, HudStyle.argb(255, 0x1D4A58));
            g.fill(sx, sy, sx + 16, sy + 16, HudStyle.argb(255, 0x0A2530));
        }
        // De voortgangsbalk tussen het werkvak en het upgradevak.
        int bx = x + SuitWorkbenchMenu.WORK_X - 2;
        int by = y + SuitWorkbenchMenu.WORK_Y + 24;
        HudStyle.bar(g, bx, by, 20, 4, menu.progress(), menu.powered() ? HudStyle.CYAN : HudStyle.ORANGE);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 7, HudStyle.argb(255, HudStyle.CYAN), false);
        g.drawCenteredString(font, Component.translatable("screen.starktech.workbench.suit"),
                SuitWorkbenchMenu.WORK_X + 8, SuitWorkbenchMenu.WORK_Y - 10, HudStyle.argb(220, HudStyle.WHITE));
        g.drawCenteredString(font, Component.translatable("screen.starktech.workbench.upgrade"),
                SuitWorkbenchMenu.MODULE_X + 8, SuitWorkbenchMenu.MODULE_Y - 10, HudStyle.argb(220, HudStyle.WHITE));
        Component status = switch (menu.job()) {
            case SuitWorkbenchBlockEntity.JOB_NONE -> Component.empty();
            case SuitWorkbenchBlockEntity.JOB_INSTALL -> Component.translatable("screen.starktech.workbench.installing");
            default -> Component.translatable("screen.starktech.workbench.building");
        };
        if (menu.job() != SuitWorkbenchBlockEntity.JOB_NONE && !menu.powered()) {
            status = Component.translatable("screen.starktech.workbench.no_power");
        }
        g.drawCenteredString(font, status, 101, 92, HudStyle.argb(255, menu.powered() ? HudStyle.GREEN : HudStyle.ORANGE));
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, HudStyle.argb(200, HudStyle.WHITE), false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
