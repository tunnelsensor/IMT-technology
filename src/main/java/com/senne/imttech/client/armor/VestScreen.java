package com.senne.imttech.client.armor;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.armor.PlateItem;
import com.senne.imttech.armor.PlateTier;
import com.senne.imttech.armor.VestData;
import com.senne.imttech.armor.VestMenu;
import com.senne.imttech.armor.VestTextPayload;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Het scherm van een Plate Carrier (hoofdstuk 109, zie VestMenu): links de plate voor en achter (met hoeveel ze nog
 * tegenhouden), in het midden het vest zelf van achteren (met je tekst erop, terwijl je typt), rechts de drie magazijnen, de
 * tekst op je rug en zijn kleur (de 16 kleuren van Minecraft). Dezelfde donkere stijl als de andere schermen van de mod.
 */
public class VestScreen extends AbstractContainerScreen<VestMenu> {
    private static final int PANEL = 0x10141A;
    private static final int FRAME = 0x465460;
    private static final int SLOT = 0x1A2028;
    /** De kleurvakjes: waar ze beginnen, hoe groot, en zoveel per rij. */
    private static final int SWATCH_X = 106;
    private static final int SWATCH_Y = 86;
    private static final int SWATCH = 7;
    private static final int SWATCH_STEP = 8;
    private static final int PER_ROW = 8;
    /** Het tekstvak. */
    private static final int TEXT_X = 106;
    private static final int TEXT_Y = 61;
    private static final int TEXT_W = 62;
    /** Het vest in het midden: waar het midden staat, en hoe groot (schermpixels per model-pixel). */
    private static final int PREVIEW_X = 74;
    private static final int PREVIEW_Y = 62;
    private static final float PREVIEW_SCALE = 4.8f;

    private EditBox text;
    private int colour;

    public VestScreen(VestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = VestMenu.HEIGHT;
        inventoryLabelY = VestMenu.INVENTORY_Y - 11;
        titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        VestData data = menu.data();
        colour = data.textColour();
        text = new EditBox(font, leftPos + TEXT_X, topPos + TEXT_Y, TEXT_W, 12, Component.translatable("screen.imttech.vest.text"));
        text.setMaxLength(VestData.MAX_TEXT);
        text.setValue(data.text());
        text.setHint(Component.translatable("screen.imttech.vest.text_hint"));
        text.setResponder(value -> send());
        addRenderableWidget(text);
    }

    /** De tekst en de kleur naar de server (die zet ze op het vest). */
    private void send() {
        PacketDistributor.sendToServer(new VestTextPayload(text.getValue(), colour));
    }

    // ================================================================ Tekenen

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, HudStyle.argb(245, PANEL));
        g.renderOutline(x, y, imageWidth, imageHeight, HudStyle.argb(255, FRAME));
        HudStyle.brackets(g, x + 2, y + 2, x + imageWidth - 3, y + VestMenu.INVENTORY_Y - 14, 6, HudStyle.argb(255, HudStyle.ORANGE));
        for (Slot slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, HudStyle.argb(255, SLOT));
            g.renderOutline(sx - 1, sy - 1, 18, 18, HudStyle.argb(255, FRAME));
        }
        // Een vaag voorbeeld in een leeg vakje: een plate, of een magazijn.
        for (int i = 0; i < VestMenu.SIZE; i++) {
            Slot slot = menu.getSlot(i);
            if (!slot.hasItem()) {
                ItemStack ghost = i < VestMenu.MAGS ? new ItemStack(com.senne.imttech.armor.ArmorRegistry.plate(PlateTier.STEEL))
                        : new ItemStack(ModItems.GLOCK_19_MAGAZINE.get());
                int sx = x + slot.x;
                int sy = y + slot.y;
                g.renderFakeItem(ghost, sx, sy);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(sx, sy, sx + 16, sy + 16, HudStyle.argb(180, SLOT));
                g.pose().popPose();
            }
        }
        // De kleuren van de tekst.
        DyeColor[] dyes = DyeColor.values();
        for (int i = 0; i < dyes.length; i++) {
            int sx = x + SWATCH_X + (i % PER_ROW) * SWATCH_STEP;
            int sy = y + SWATCH_Y + (i / PER_ROW) * SWATCH_STEP;
            g.fill(sx, sy, sx + SWATCH, sy + SWATCH, HudStyle.argb(255, dyes[i].getTextColor()));
            boolean chosen = i == colour;
            boolean over = mouseX >= sx && mouseX < sx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH;
            if (chosen || over) {
                g.renderOutline(sx - 1, sy - 1, SWATCH + 2, SWATCH + 2, HudStyle.argb(255, chosen ? HudStyle.WHITE : HudStyle.CYAN));
            }
        }
        renderPreview(g, partialTick);
    }

    /** Het vest zelf, van achteren (zodat je je tekst ziet), een beetje schommelend. */
    private void renderPreview(GuiGraphics g, float partialTick) {
        ItemStack vest = menu.vest();
        if (vest.isEmpty() || minecraft == null || minecraft.player == null) {
            return;
        }
        float time = minecraft.player.tickCount + partialTick;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(leftPos + PREVIEW_X, topPos + PREVIEW_Y, 120);
        float scale = PREVIEW_SCALE * 16;
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(8));
        pose.mulPose(Axis.YP.rotationDegrees(180 + Mth.sin(time * 0.04f) * 22));
        // Het midden van het vest (8, 10, 8 model-pixels) in het midden van het vlak.
        pose.translate(-0.5, -10 / 16.0, -0.5);
        Lighting.setupForEntityInInventory();
        VestData shown = menu.data().withText(text == null ? menu.data().text() : text.getValue(), colour);
        VestRenderer.renderModel(pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, vest, minecraft.level, shown);
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, HudStyle.argb(255, HudStyle.ORANGE), false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, HudStyle.argb(170, HudStyle.WHITE), false);
        VestData data = menu.data();
        small(g, Component.translatable("screen.imttech.vest.front"), 8, VestMenu.FRONT_Y - 10, HudStyle.argb(220, HudStyle.WHITE));
        small(g, Component.translatable("screen.imttech.vest.back"), 8, VestMenu.BACK_Y - 10, HudStyle.argb(220, HudStyle.WHITE));
        plateStatus(g, data.plate(true), VestMenu.FRONT_Y + 19);
        plateStatus(g, data.plate(false), VestMenu.BACK_Y + 19);
        small(g, Component.translatable("screen.imttech.vest.mags", data.filledMagazines(), VestData.MAGS), VestMenu.MAGS_X - 1,
                VestMenu.MAGS_Y - 10, HudStyle.argb(220, HudStyle.WHITE));
        small(g, Component.translatable("screen.imttech.vest.text"), TEXT_X - 1, TEXT_Y - 9, HudStyle.argb(220, HudStyle.WHITE));
        small(g, Component.translatable("screen.imttech.vest.colour"), SWATCH_X - 1, SWATCH_Y - 8, HudStyle.argb(170, HudStyle.WHITE));
    }

    /**
     * Onder een plate-vakje, op één regel (anders liep hij in de naam van de volgende plate): hoeveel hij nog tegenhoudt en
     * welk deel van een treffer ("80/80  40%"), of "Cracked" of "Empty".
     */
    private void plateStatus(GuiGraphics g, ItemStack plate, int y) {
        PlateTier tier = PlateItem.tierOf(plate);
        Component line;
        int argb;
        if (tier == null) {
            line = Component.translatable("screen.imttech.vest.no_plate");
            argb = HudStyle.argb(150, HudStyle.WHITE);
        } else if (PlateItem.broken(plate)) {
            line = Component.translatable("screen.imttech.vest.cracked");
            argb = HudStyle.argb(255, HudStyle.RED);
        } else {
            line = Component.translatable("screen.imttech.vest.status", Math.round(PlateItem.left(plate)), Math.round(tier.durability()),
                    Math.round(tier.reduction() * 100));
            argb = HudStyle.argb(255, PlateItem.health(plate) < 0.25f ? HudStyle.ORANGE : HudStyle.GREEN);
        }
        small(g, line, 8, y, argb);
    }

    /** Kleine tekst (driekwart), zodat alles naast elkaar past zonder over elkaar te lopen. */
    private void small(GuiGraphics g, Component text, int x, int y, int argb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, text, 0, 0, argb, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    // ================================================================ Klikken en typen

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            DyeColor[] dyes = DyeColor.values();
            for (int i = 0; i < dyes.length; i++) {
                int sx = leftPos + SWATCH_X + (i % PER_ROW) * SWATCH_STEP;
                int sy = topPos + SWATCH_Y + (i / PER_ROW) * SWATCH_STEP;
                if (mouseX >= sx && mouseX < sx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH) {
                    colour = i;
                    send();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Typ je in het tekstvak, dan sluit de inventaristoets (E) het scherm niet (zoals bij een aambeeld). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
            return true;
        }
        if (text != null && text.isFocused() && (text.keyPressed(keyCode, scanCode, modifiers) || text.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Voor de tests en de rondleiding: deze tekst en kleur typen. */
    public void type(String value, DyeColor dye) {
        colour = dye.getId();
        text.setValue(value);
        send();
    }
}
