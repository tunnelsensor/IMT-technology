package com.senne.imttech.client.body;

import com.senne.imttech.backpack.BackpackActionPayload;
import com.senne.imttech.body.BodyMenu;
import com.senne.imttech.body.PlayerBodyEntity;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Een lichaam doorzoeken (hoofdstuk 72, stap 4, zie BodyMenu): 6 rijen met wat erin zit (bovenaan het harnas, de andere
 * hand, de holster, de pouches en de geweren, met een vaag plaatje in een lege plek), eronder je inventaris. Is het jouw
 * lichaam, dan staat rechtsboven de knop "Everything back". Helemaal rechtsboven (hoofdstuk 98) het vakje van de rugzak:
 * klik erop en je haalt hem eraf (3 s stilstaan, het scherm gaat dicht).
 */
public class BodyScreen extends AbstractContainerScreen<BodyMenu> {
    private static final int PANEL = 0x10141A;
    private static final int FRAME = 0x465460;
    private static final int SLOT = 0x1A2028;
    private static final int BUTTON_H = 11;

    public BodyScreen(BodyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 228;
        inventoryLabelY = BodyMenu.TOP + 6 * 18 + 2;
        titleLabelY = 9;
    }

    /** Het vage plaatje in een lege plek van rij 0 (en het geweer voor je borst), of leeg. */
    private static ItemStack ghost(int slotIndex) {
        return switch (slotIndex) {
            case PlayerBodyEntity.ARMOR + 3 -> new ItemStack(Items.IRON_HELMET);
            case PlayerBodyEntity.ARMOR + 2 -> new ItemStack(Items.IRON_CHESTPLATE);
            case PlayerBodyEntity.ARMOR + 1 -> new ItemStack(Items.IRON_LEGGINGS);
            case PlayerBodyEntity.ARMOR -> new ItemStack(Items.IRON_BOOTS);
            case PlayerBodyEntity.OFFHAND -> new ItemStack(Items.SHIELD);
            case PlayerBodyEntity.GEAR -> new ItemStack(ModItems.HOLSTER.get());
            case PlayerBodyEntity.GEAR + 1 -> new ItemStack(ModItems.GRENADE_POUCH.get());
            case PlayerBodyEntity.GEAR + 2 -> new ItemStack(ModItems.MAG_POUCH.get());
            case PlayerBodyEntity.GEAR + 3, PlayerBodyEntity.GEAR + 4 -> new ItemStack(ModItems.LASER_RIFLE.get());
            case PlayerBodyEntity.NECK -> new ItemStack(ModItems.KEYCARD.get());
            case PlayerBodyEntity.VEST -> new ItemStack(com.senne.imttech.armor.ArmorRegistry.PLATE_CARRIER.get());
            default -> ItemStack.EMPTY;
        };
    }

    /** De knop "Everything back": links van het rugzakvakje (hoofdstuk 98), op de hoogte van de titel. */
    private int[] button() {
        Component label = Component.translatable("body.imttech.everything_back");
        int w = (int) (font.width(label) * 0.75f) + 10;
        return new int[]{leftPos + BodyMenu.PACK_X - 5 - w, topPos + 7, w};
    }

    /** Staat je muis op het rugzakvakje? */
    private boolean overPack(double mouseX, double mouseY) {
        Slot pack = menu.packSlot();
        int x = leftPos + pack.x;
        int y = topPos + pack.y;
        return mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
    }

    private boolean overButton(double mouseX, double mouseY) {
        int[] b = button();
        return menu.owner() && mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + BUTTON_H;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, HudStyle.argb(245, PANEL));
        g.renderOutline(x, y, imageWidth, imageHeight, HudStyle.argb(255, FRAME));
        HudStyle.brackets(g, x + 2, y + 2, x + imageWidth - 3, y + BodyMenu.TOP + 6 * 18 + 2, 6, HudStyle.argb(255, HudStyle.RED));
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, HudStyle.argb(255, SLOT));
            g.renderOutline(sx - 1, sy - 1, 18, 18, HudStyle.argb(255, FRAME));
            boolean bodySlot = i < PlayerBodyEntity.SIZE || slot == menu.packSlot();
            if (bodySlot && !slot.hasItem()) {
                ItemStack ghost = slot == menu.packSlot() ? new ItemStack(com.senne.imttech.backpack.BackpackRegistry.ASSAULT_PACK.get())
                        : ghost(slot.getContainerSlot());
                if (!ghost.isEmpty()) {
                    g.renderFakeItem(ghost, sx, sy);
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.fill(sx, sy, sx + 16, sy + 16, HudStyle.argb(185, SLOT));
                    g.pose().popPose();
                }
            }
        }
        if (menu.owner()) {
            int[] b = button();
            boolean hover = overButton(mouseX, mouseY);
            g.fill(b[0], b[1], b[0] + b[2], b[1] + BUTTON_H, HudStyle.argb(255, hover ? 0x24323C : 0x1A2028));
            g.renderOutline(b[0], b[1], b[2], BUTTON_H, HudStyle.argb(255, hover ? HudStyle.CYAN : HudStyle.GREEN));
            Component label = Component.translatable("body.imttech.everything_back");
            g.pose().pushPose();
            g.pose().translate(b[0] + b[2] / 2.0f, b[1] + 2.5f, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, label, -font.width(label) / 2, 0, HudStyle.argb(255, hover ? HudStyle.CYAN : HudStyle.GREEN), false);
            g.pose().popPose();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, HudStyle.argb(255, HudStyle.RED), false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, HudStyle.argb(170, HudStyle.WHITE), false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (overButton(mouseX, mouseY)) {
            g.renderTooltip(font, Component.translatable("body.imttech.everything_back_hint",
                    Math.round(ImtConfig.Backpacks.BODY_RESTORE_SECONDS * 10) / 10.0f), mouseX, mouseY);
        } else if (overPack(mouseX, mouseY)) {
            packTooltip(g, mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    /** Hoofdstuk 98: de tooltip van de rugzak, met eronder hoe je hem eraf haalt (of "No backpack"). */
    private void packTooltip(GuiGraphics g, int mouseX, int mouseY) {
        ItemStack pack = menu.packSlot().getItem();
        java.util.List<Component> lines = new java.util.ArrayList<>();
        if (pack.isEmpty()) {
            lines.add(Component.translatable("body.imttech.pack_slot_empty"));
        } else {
            lines.addAll(getTooltipFromContainerItem(pack));
            lines.add(Component.translatable("body.imttech.pack_slot_hint",
                    Math.round(ImtConfig.Backpacks.BODY_TAKE_PACK_SECONDS * 10) / 10.0f)
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overButton(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new BackpackActionPayload(BackpackActionPayload.RESTORE_BODY, 0));
            return true;
        }
        // Hoofdstuk 98: op de rugzak klikken (ook met shift of rechts) = hem eraf halen; de server sluit het scherm en telt 3 s af.
        if (overPack(mouseX, mouseY)) {
            if (menu.packSlot().hasItem()) {
                PacketDistributor.sendToServer(new BackpackActionPayload(BackpackActionPayload.TAKE_BODY_PACK, 0));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
