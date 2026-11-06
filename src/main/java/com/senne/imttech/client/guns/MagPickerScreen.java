package com.senne.imttech.client.guns;

import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.MagazineItem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Het keuzescherm van de magazijnpouch (hoofdstuk 52, stap 2, jouw wens: "R opent de magpouch, jij klikt dan op het
 * magazijn dat je wilt en die wordt dan gebruikt"). Elk vakje van de pouch is een kaartje met het magazijn en hoeveel
 * erin zit; klik er een, of druk nog eens R voor het volste. Esc = toch niet.
 */
public class MagPickerScreen extends Screen {
    private static final int CARD_W = 60;
    private static final int CARD_H = 52;
    private final FirearmType type;

    public MagPickerScreen(FirearmType type) {
        super(Component.translatable("screen.imttech.mag_picker"));
        this.type = type;
    }

    /** Geen wazige achtergrond van Minecraft: alleen onze eigen lichte verduistering (zie HudStyle.dim). */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<ItemStack> pouch() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? List.of() : Gear.contents(player, GearItem.Kind.MAG_POUCH);
    }

    @Override
    public void tick() {
        super.tick();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || FirearmItem.typeOf(player.getMainHandItem()) != type) {
            onClose();
            return;
        }
        while (ImtKeys.GUN_RELOAD.consumeClick()) {
            pick(fullest());
        }
    }

    /** Het volste magazijn in de pouch, of -1. */
    private int fullest() {
        List<ItemStack> pouch = pouch();
        int best = -1;
        int most = 0;
        for (int i = 0; i < pouch.size(); i++) {
            if (MagazineItem.typeOf(pouch.get(i)) == type && MagazineItem.rounds(pouch.get(i)) > most) {
                most = MagazineItem.rounds(pouch.get(i));
                best = i;
            }
        }
        return best;
    }

    /** Voor de showcase en de screenshots: dit vakje kiezen. */
    public void pickIndex(int index) {
        pick(index);
    }

    private void pick(int index) {
        if (index < 0) {
            return;
        }
        FirearmControl.request(Firearms.ActionKind.RELOAD_POUCH, index);
        onClose();
    }

    private int left(int count) {
        return width / 2 - (count * (CARD_W + 8) - 8) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudStyle.dim(g, width, height);
        List<ItemStack> pouch = pouch();
        int top = height / 2 - CARD_H / 2;
        g.drawCenteredString(font, Component.translatable("screen.imttech.mag_picker"), width / 2, top - 16, HudStyle.argb(255, HudStyle.ORANGE));
        for (int i = 0; i < pouch.size(); i++) {
            ItemStack magazine = pouch.get(i);
            int x = left(pouch.size()) + i * (CARD_W + 8);
            boolean fits = MagazineItem.typeOf(magazine) == type;
            boolean hot = fits && mouseX >= x && mouseX < x + CARD_W && mouseY >= top && mouseY < top + CARD_H;
            g.fill(x, top, x + CARD_W, top + CARD_H, HudStyle.argb(220, 0x0E1116));
            g.renderOutline(x, top, CARD_W, CARD_H, fits ? HudStyle.argb(255, hot ? HudStyle.CYAN : HudStyle.ORANGE) : HudStyle.argb(100, 0x8A9BA0));
            if (magazine.isEmpty()) {
                g.drawCenteredString(font, Component.translatable("screen.imttech.mag_picker.empty_slot"), x + CARD_W / 2, top + 22,
                        HudStyle.argb(120, 0x8A9BA0));
                continue;
            }
            g.renderItem(magazine, x + CARD_W / 2 - 8, top + 8);
            String rounds = MagazineItem.rounds(magazine) + " / " + type.capacity();
            g.drawCenteredString(font, rounds, x + CARD_W / 2, top + 30, HudStyle.argb(255, MagazineItem.rounds(magazine) == 0
                    ? HudStyle.RED : hot ? HudStyle.CYAN : HudStyle.WHITE));
            g.pose().pushPose();
            g.pose().translate(x + CARD_W / 2f, top + 42, 0);
            g.pose().scale(0.7f, 0.7f, 1);
            g.drawCenteredString(font, Component.literal(String.valueOf(i + 1)), 0, 0, HudStyle.argb(150, HudStyle.WHITE));
            g.pose().popPose();
        }
        g.drawCenteredString(font, Component.translatable("screen.imttech.mag_picker.hint"), width / 2, top + CARD_H + 10,
                HudStyle.argb(150, HudStyle.WHITE));
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<ItemStack> pouch = pouch();
        int top = height / 2 - CARD_H / 2;
        for (int i = 0; i < pouch.size(); i++) {
            int x = left(pouch.size()) + i * (CARD_W + 8);
            if (mouseX >= x && mouseX < x + CARD_W && mouseY >= top && mouseY < top + CARD_H && MagazineItem.typeOf(pouch.get(i)) == type) {
                pick(i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // De cijfers 1-9 kiezen een vakje.
        if (keyCode >= 49 && keyCode <= 57) {
            int index = keyCode - 49;
            List<ItemStack> pouch = pouch();
            if (index < pouch.size() && MagazineItem.typeOf(pouch.get(index)) == type) {
                pick(index);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
