package com.senne.imttech.client.robot;

import com.senne.imttech.ImtTech;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.robot.GuardRobotEntity;
import com.senne.imttech.robot.RobotAssemblerBlockEntity;
import com.senne.imttech.robot.RobotAssemblerMenu;
import com.senne.imttech.robot.RobotSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * Het scherm van de Robot Assembler (hoofdstuk 21).
 *
 * <pre>
 *  Robot Assembler
 *  [HEAD]  +-----------+   BOARD   ROBOT
 *  [BODY]  |   robot   |   [   ] > [   ]
 *  [ARMS]  |  (draait) |   [ Assemble  ]
 *  [LEGS]  +-----------+   [ Take apart]
 *                          Missing: legs.
 * </pre>
 * In het midden draait de robot zoals hij wordt: een onderdeel dat nog niet in zijn vakje ligt, zie je
 * niet. Ligt er een robot in het robotvakje, dan zie je die. Onder de knoppen staat wat er nog ontbreekt.
 */
public class RobotAssemblerScreen extends AbstractContainerScreen<RobotAssemblerMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/gui/charger.png");
    /** Hier begint in charger.png de streep boven je rugzak. */
    private static final int TEXTURE_SPLIT = 79;
    private static final int FRAME = HudStyle.argb(255, 0x465460);
    private static final int SLOT = HudStyle.argb(255, 0x1E2128);
    private static final int PREVIEW = HudStyle.argb(255, 0x0E1116);
    /** Het voorbeeldvak. */
    private static final int PREVIEW_X = 30, PREVIEW_Y = 16, PREVIEW_W = 68, PREVIEW_H = 72;
    private static final int RIGHT_X = 104, RIGHT_W = 66;

    private Button assemble;
    private Button takeApart;
    /** De robot van het voorbeeld (alleen in je spel, hij staat nergens in de wereld). */
    private @Nullable GuardRobotEntity preview;

    public RobotAssemblerScreen(RobotAssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166 + RobotAssemblerMenu.EXTRA_HEIGHT;
        inventoryLabelY = RobotAssemblerMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        assemble = addRenderableWidget(Button.builder(Component.literal("Assemble"),
                b -> click(RobotAssemblerMenu.BUTTON_ASSEMBLE)).bounds(leftPos + RIGHT_X, topPos + 42, RIGHT_W, 16).build());
        takeApart = addRenderableWidget(Button.builder(Component.literal("Take apart"),
                b -> click(RobotAssemblerMenu.BUTTON_TAKE_APART)).bounds(leftPos + RIGHT_X, topPos + 60, RIGHT_W, 16).build());
        if (minecraft != null && minecraft.level != null) {
            preview = ModEntities.GUARD_ROBOT.get().create(minecraft.level);
        }
    }

    private void click(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        assemble.active = RobotAssemblerBlockEntity.canAssemble(menu.items());
        takeApart.active = RobotAssemblerBlockEntity.canTakeApart(menu.items());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // De achtergrond in drie stukken (zoals het serverrack): bovenstuk, een stuk leeg paneel, je rugzak.
        int extra = RobotAssemblerMenu.EXTRA_HEIGHT;
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, TEXTURE_SPLIT, 256, 256);
        g.blit(TEXTURE, x, y + TEXTURE_SPLIT, 0, 40, imageWidth, extra, 256, 256);
        g.blit(TEXTURE, x, y + TEXTURE_SPLIT + extra, 0, TEXTURE_SPLIT, imageWidth, 166 - TEXTURE_SPLIT, 256, 256);
        for (int i = 0; i < RobotAssemblerBlockEntity.SIZE; i++) {
            int sx = x + RobotAssemblerMenu.slotX(i);
            int sy = y + RobotAssemblerMenu.slotY(i);
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT);
            g.renderOutline(sx - 1, sy - 1, 18, 18, i == RobotAssemblerBlockEntity.ROBOT ? HudStyle.argb(255, HudStyle.CYAN) : FRAME);
        }
        // Een pijltje van de printplaat naar de robot.
        int arrowY = y + RobotAssemblerMenu.BOARD_Y + 7;
        int arrowX = x + RobotAssemblerMenu.BOARD_X + 21;
        g.fill(arrowX, arrowY, arrowX + 14, arrowY + 2, FRAME);
        g.fill(arrowX + 10, arrowY - 2, arrowX + 12, arrowY + 4, FRAME);
        g.fill(arrowX + 12, arrowY - 1, arrowX + 14, arrowY + 3, FRAME);
        // Het voorbeeld.
        int px = x + PREVIEW_X;
        int py = y + PREVIEW_Y;
        g.fill(px, py, px + PREVIEW_W, py + PREVIEW_H, PREVIEW);
        g.renderOutline(px, py, PREVIEW_W, PREVIEW_H, FRAME);
        renderPreview(g, px, py);
    }

    /** De robot zoals hij nu wordt, langzaam ronddraaiend. */
    private void renderPreview(GuiGraphics g, int px, int py) {
        int mask = RobotAssemblerBlockEntity.shownSlots(menu.items());
        if (preview == null || mask == 0) {
            return;
        }
        preview.setParts(RobotAssemblerBlockEntity.partsIn(menu.items()));
        preview.setShownSlots(mask);
        float spin = (System.currentTimeMillis() % 9000L) / 9000.0f * 360.0f;
        preview.yBodyRot = spin;
        preview.yBodyRotO = spin;
        preview.setYRot(spin);
        preview.yRotO = spin;
        preview.yHeadRot = spin;
        preview.yHeadRotO = spin;
        preview.setXRot(0);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(-0.25f);
        pose.mul(camera);
        g.enableScissor(px + 1, py + 1, px + PREVIEW_W - 1, py + PREVIEW_H - 1);
        InventoryScreen.renderEntityInInventory(g, px + PREVIEW_W / 2.0f, py + PREVIEW_H / 2.0f + 2, 34.0f,
                new Vector3f(0, preview.getBbHeight() / 2.0f, 0), pose, camera, preview);
        g.disableScissor();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, HudStyle.argb(255, HudStyle.CYAN), false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, HudStyle.argb(200, HudStyle.WHITE), false);
        int label = HudStyle.argb(170, HudStyle.ORANGE);
        small(g, "BOARD", RobotAssemblerMenu.BOARD_X - 1, RobotAssemblerMenu.BOARD_Y - 8, label);
        small(g, "ROBOT", RobotAssemblerMenu.ROBOT_X - 1, RobotAssemblerMenu.ROBOT_Y - 8, label);
        // Onder de knoppen: wat er nog ontbreekt, of dat het kan.
        boolean ready = RobotAssemblerBlockEntity.canAssemble(menu.items()) || RobotAssemblerBlockEntity.canTakeApart(menu.items());
        int colour = HudStyle.argb(220, ready ? HudStyle.GREEN : HudStyle.WHITE);
        int y = 80;
        for (FormattedCharSequence line : font.split(Component.literal(RobotAssemblerBlockEntity.problem(menu.items())),
                (int) (RIGHT_W / 0.75f))) {
            g.pose().pushPose();
            g.pose().translate(RIGHT_X, y, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            g.drawString(font, line, 0, 0, colour, false);
            g.pose().popPose();
            y += 7;
        }
    }

    private void small(GuiGraphics g, String text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    /** Boven een leeg vakje: wat erin hoort ("Head", "Circuit board"...). */
    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        Slot slot = hoveredSlot;
        if (slot != null && !slot.hasItem() && slot.index < RobotAssemblerBlockEntity.SIZE && menu.getCarried().isEmpty()) {
            String what = slot.index < RobotAssemblerBlockEntity.BOARD ? RobotSlot.values()[slot.index].title()
                    : slot.index == RobotAssemblerBlockEntity.BOARD ? "Robot Circuit Board" : "A finished robot comes here";
            g.renderTooltip(font, Component.literal(what), mouseX, mouseY);
            return;
        }
        super.renderTooltip(g, mouseX, mouseY);
    }
}
