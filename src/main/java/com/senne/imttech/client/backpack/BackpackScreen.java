package com.senne.imttech.client.backpack;

import com.mojang.blaze3d.platform.InputConstants;
import com.senne.imttech.backpack.BackpackActionPayload;
import com.senne.imttech.backpack.BackpackItem;
import com.senne.imttech.backpack.BackpackMenu;
import com.senne.imttech.client.hud.HudStyle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Het scherm van een open rugzak (hoofdstuk 72): bovenaan de vakjes van de rugzak, eronder je inventaris, in de donkere
 * stijl van de mod. Rechtsboven de sorteerknop (jouw keuze). Stap 2: rechts naast het scherm een strook met de
 * MOLLE-plekken, waar je pouches aan de rugzak hangt (een vaag plaatje van een pouch in een lege plek).
 *
 * Met de rugzak open <b>kun je traag doorlopen</b> (jouw keuze bij "openen kost tijd": "zolang hij open is, loop je
 * traag"): de server maakt je trager (Backpacks.onPlayerTick), en dit scherm laat de looptoetsen (vooruit, achteruit,
 * links, rechts) door naar het spel, zoals het wiel van de holster dat doet (GearWheelScreen). Sluipen, springen en
 * sprinten niet: Shift heb je nodig voor shift-klikken. Omdat W hier lopen is, gaat "How it works" (standaard ook W) in
 * dit scherm niet open terwijl je loopt (zie HowToHover).
 */
public class BackpackScreen extends AbstractContainerScreen<BackpackMenu> {
    private static final int PANEL = 0x10141A;
    private static final int FRAME = 0x465460;
    private static final int SLOT = 0x1A2028;
    private static final int SORT_W = 30;
    private static final int SORT_H = 11;

    /** De looptoetsen die wij "aan" zetten. */
    private final Set<KeyMapping> pressedHere = new HashSet<>();
    /** Waar elke looptoets telde voor wij het scherm openden (zie letMovementThrough). */
    private final Map<KeyMapping, IKeyConflictContext> contexts = new HashMap<>();

    public BackpackScreen(BackpackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 114 + menu.size().rows * 18;
        inventoryLabelY = BackpackMenu.inventoryY(menu.size()) - 11;
        titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        letMovementThrough();
        syncMovement();
    }

    // ================================================================ Tekenen

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, HudStyle.argb(245, PANEL));
        g.renderOutline(x, y, imageWidth, imageHeight, HudStyle.argb(255, FRAME));
        int packBottom = BackpackMenu.PACK_Y + menu.size().rows * 18;
        HudStyle.brackets(g, x + 2, y + 2, x + imageWidth - 3, y + packBottom + 2, 6, HudStyle.argb(255, HudStyle.ORANGE));
        for (Slot slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, HudStyle.argb(255, SLOT));
            g.renderOutline(sx - 1, sy - 1, 18, 18, HudStyle.argb(255, FRAME));
        }
        // Stap 2: de strook met de MOLLE-plekken, rechts naast het scherm.
        int molle = menu.size().molle;
        int sx0 = x + BackpackMenu.MOLLE_X - 6;
        int sy1 = y + BackpackMenu.PACK_Y + molle * 18 + 5;
        g.fill(sx0, y, sx0 + 30, sy1, HudStyle.argb(245, PANEL));
        g.renderOutline(sx0, y, 30, sy1 - y, HudStyle.argb(255, FRAME));
        for (int i = 0; i < molle; i++) {
            Slot slot = menu.getSlot(menu.size().slots() + i);
            if (!slot.hasItem()) {
                int px = x + slot.x;
                int py = y + slot.y;
                g.renderFakeItem(new net.minecraft.world.item.ItemStack(com.senne.imttech.backpack.BackpackRegistry.UTILITY_POUCH.get()), px, py);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(px, py, px + 16, py + 16, HudStyle.argb(180, SLOT));
                g.pose().popPose();
            }
        }
        // De sorteerknop.
        int[] sort = sortButton();
        boolean hover = over(sort, mouseX, mouseY);
        g.fill(sort[0], sort[1], sort[0] + SORT_W, sort[1] + SORT_H, HudStyle.argb(255, hover ? 0x24323C : 0x1A2028));
        g.renderOutline(sort[0], sort[1], SORT_W, SORT_H, HudStyle.argb(255, hover ? HudStyle.CYAN : FRAME));
        Component label = Component.translatable("screen.imttech.backpack.sort");
        g.pose().pushPose();
        g.pose().translate(sort[0] + SORT_W / 2.0f, sort[1] + 2.5f, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, label, -font.width(label) / 2, 0, HudStyle.argb(255, hover ? HudStyle.CYAN : HudStyle.WHITE), false);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, HudStyle.argb(255, HudStyle.ORANGE), false);
        int filled = 0;
        for (int i = 0; i < menu.size().slots(); i++) {
            if (menu.getSlot(i).hasItem()) {
                filled++;
            }
        }
        String count = filled + " / " + menu.size().slots();
        int countX = imageWidth - 8 - SORT_W - 6 - font.width(count);
        g.drawString(font, count, countX, titleLabelY, HudStyle.argb(200, HudStyle.WHITE), false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, HudStyle.argb(170, HudStyle.WHITE), false);
        // Boven de MOLLE-strook: "MOLLE", klein.
        g.pose().pushPose();
        g.pose().translate(BackpackMenu.MOLLE_X + 9, 7, 0);
        g.pose().scale(0.6f, 0.6f, 1);
        Component molle = Component.translatable("screen.imttech.backpack.molle");
        g.drawString(font, molle, -font.width(molle) / 2, 0, HudStyle.argb(220, HudStyle.ORANGE), false);
        g.pose().popPose();
    }

    /** Stap 2: een klik op de MOLLE-strook (buiten het scherm zelf) is geen klik "ernaast" (dan viel wat je vasthoudt). */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button) {
        int sx0 = guiLeft + BackpackMenu.MOLLE_X - 6;
        int sy1 = guiTop + BackpackMenu.PACK_Y + menu.size().molle * 18 + 5;
        boolean inStrip = mouseX >= sx0 && mouseX < sx0 + 30 && mouseY >= guiTop && mouseY < sy1;
        return !inStrip && super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Slot hovered = getSlotUnderMouse();
        if (over(sortButton(), mouseX, mouseY)) {
            g.renderTooltip(font, Component.translatable("screen.imttech.backpack.sort_hint"), mouseX, mouseY);
        } else if (hovered != null && !hovered.hasItem() && hovered.index >= menu.size().slots() && hovered.index < menu.inventoryStart()
                && menu.getCarried().isEmpty()) {
            g.renderTooltip(font, Component.translatable("screen.imttech.backpack.molle_hint"), mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    /** Waar de sorteerknop staat (linksboven op het scherm). */
    private int[] sortButton() {
        return new int[]{leftPos + imageWidth - 8 - SORT_W, topPos + 4};
    }

    private static boolean over(int[] at, double mouseX, double mouseY) {
        return mouseX >= at[0] && mouseX < at[0] + SORT_W && mouseY >= at[1] && mouseY < at[1] + SORT_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && over(sortButton(), mouseX, mouseY)) {
            PacketDistributor.sendToServer(new BackpackActionPayload(BackpackActionPayload.SORT, 0));
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.5f, 1.3f);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ================================================================ Doorlopen (zoals het wiel)

    /** Loop je nu (houd je een looptoets vast die dit scherm doorgeeft)? Dan opent "How it works" niet. */
    public boolean walking() {
        return !pressedHere.isEmpty();
    }

    private static List<KeyMapping> movementKeys() {
        Options o = Minecraft.getInstance().options;
        return List.of(o.keyUp, o.keyDown, o.keyLeft, o.keyRight);
    }

    private static boolean physicallyDown(InputConstants.Key key) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(window, key.getValue());
    }

    /**
     * NeoForge laat de looptoetsen alleen in het spel tellen (KeyConflictContext.IN_GAME); zolang dit scherm open is,
     * tellen ze overal, en bij het sluiten krijgen ze hun eigen context terug (zie GearWheelScreen.letMovementThrough).
     */
    private void letMovementThrough() {
        for (KeyMapping key : movementKeys()) {
            if (!contexts.containsKey(key)) {
                contexts.put(key, key.getKeyConflictContext());
                key.setKeyConflictContext(KeyConflictContext.UNIVERSAL);
            }
        }
    }

    /** Elke tick, als vangnet: wat echt ingedrukt is "aan", wat wij aanzetten en losgelaten is weer "uit". */
    private void syncMovement() {
        for (KeyMapping key : movementKeys()) {
            boolean down = physicallyDown(key.getKey());
            if (down && pressedHere.add(key)) {
                key.setDown(true);
            } else if (!down && pressedHere.remove(key)) {
                key.setDown(false);
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncMovement();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (KeyMapping key : movementKeys()) {
            if (key.matches(keyCode, scanCode)) {
                if (pressedHere.add(key)) {
                    key.setDown(true);
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        for (KeyMapping key : movementKeys()) {
            if (key.matches(keyCode, scanCode) && pressedHere.remove(key)) {
                key.setDown(false);
            }
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        super.removed();
        // Wat je nog vasthoudt blijft "aan" (zoals bij het wiel): na het sluiten loop je gewoon door, en loslaten zet het uit.
        pressedHere.clear();
        contexts.forEach(KeyMapping::setKeyConflictContext);
        contexts.clear();
    }

    /** De maat van de open rugzak (voor de showcase en de screenshots). */
    public BackpackItem.Size size() {
        return menu.size();
    }
}
