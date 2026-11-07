package com.senne.imttech.client.guns;

import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Het keuzescherm van de magazijnpouch (hoofdstuk 52, stap 2, jouw wens: "R opent de magpouch, jij klikt dan op het
 * magazijn dat je wilt en die wordt dan gebruikt"). Elk vakje van de pouch is een kaartje met het magazijn en hoeveel
 * erin zit; klik er een, of druk nog eens R voor het volste. Esc = toch niet.
 *
 * Sinds hoofdstuk 55 ook voor de <b>energiecellen</b> van een lasergeweer: R met een lasergeweer en een cel in je pouch
 * toont de cellen (met hoe vol), je kiest er een, en de oude cel komt op zijn plek in de pouch (zie LaserGuns).
 */
public class MagPickerScreen extends Screen {
    private static final int CARD_W = 60;
    private static final int CARD_H = 52;
    /** Het pistool (magazijnen) of het lasergeweer (cellen) waarvoor je kiest. */
    private final @Nullable FirearmType type;
    private final @Nullable GunType laser;

    public MagPickerScreen(FirearmType type) {
        super(Component.translatable("screen.imttech.mag_picker"));
        this.type = type;
        this.laser = null;
    }

    private MagPickerScreen(GunType laser) {
        super(Component.translatable("screen.imttech.cell_picker"));
        this.type = null;
        this.laser = laser;
    }

    /** Het keuzescherm voor de cellen van dit lasergeweer (hoofdstuk 55). */
    public static MagPickerScreen cells(GunType laser) {
        return new MagPickerScreen(laser);
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

    /** Past dit uit de pouch in het wapen in je hand: een magazijn van dit pistool, of een cel? */
    private boolean fits(ItemStack stack) {
        return laser != null ? stack.is(ModItems.ENERGY_CELL.get()) : MagazineItem.typeOf(stack) == type;
    }

    /** Hoe vol: patronen, of de lading van een cel (0 tot 100). */
    private int amount(ItemStack stack) {
        return laser != null ? Math.round(EnergyCellItem.charge(stack)) : MagazineItem.rounds(stack);
    }

    private int full() {
        return laser != null ? Math.round(EnergyCellItem.FULL) : type == null ? 1 : type.capacity();
    }

    @Override
    public void tick() {
        super.tick();
        LocalPlayer player = Minecraft.getInstance().player;
        boolean stillHeld = player != null && (laser != null ? LaserGunItem.typeOf(player.getMainHandItem()) == laser
                : FirearmItem.typeOf(player.getMainHandItem()) == type);
        if (!stillHeld) {
            onClose();
            return;
        }
        while (ImtKeys.GUN_RELOAD.consumeClick()) {
            pick(fullest());
        }
    }

    /** Het volste magazijn (of de volste cel) in de pouch, of -1. */
    private int fullest() {
        List<ItemStack> pouch = pouch();
        int best = -1;
        int most = 0;
        for (int i = 0; i < pouch.size(); i++) {
            if (fits(pouch.get(i)) && amount(pouch.get(i)) > most) {
                most = amount(pouch.get(i));
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
        LocalPlayer player = Minecraft.getInstance().player;
        if (laser != null) {
            if (player != null) {
                com.senne.imttech.client.weapons.GunControl.startReloadFromPouch(player, index);
            }
        } else {
            FirearmControl.request(Firearms.ActionKind.RELOAD_POUCH, index);
        }
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
        g.drawCenteredString(font, title, width / 2, top - 16, HudStyle.argb(255, HudStyle.ORANGE));
        for (int i = 0; i < pouch.size(); i++) {
            ItemStack inside = pouch.get(i);
            int x = left(pouch.size()) + i * (CARD_W + 8);
            boolean fits = fits(inside);
            boolean hot = fits && mouseX >= x && mouseX < x + CARD_W && mouseY >= top && mouseY < top + CARD_H;
            g.fill(x, top, x + CARD_W, top + CARD_H, HudStyle.argb(220, 0x0E1116));
            g.renderOutline(x, top, CARD_W, CARD_H, fits ? HudStyle.argb(255, hot ? HudStyle.CYAN : HudStyle.ORANGE) : HudStyle.argb(100, 0x8A9BA0));
            if (inside.isEmpty()) {
                g.drawCenteredString(font, Component.translatable("screen.imttech.mag_picker.empty_slot"), x + CARD_W / 2, top + 22,
                        HudStyle.argb(120, 0x8A9BA0));
                continue;
            }
            g.renderItem(inside, x + CARD_W / 2 - 8, top + 8);
            // Wat er niet in dit wapen past (een magazijn tussen de cellen, of andersom): alleen een streepje.
            String text = !fits ? "-" : laser != null ? amount(inside) + "%" : amount(inside) + " / " + full();
            g.drawCenteredString(font, text, x + CARD_W / 2, top + 30, HudStyle.argb(255, !fits ? 0x8A9BA0 : amount(inside) == 0
                    ? HudStyle.RED : hot ? HudStyle.CYAN : HudStyle.WHITE));
            g.pose().pushPose();
            g.pose().translate(x + CARD_W / 2f, top + 42, 0);
            g.pose().scale(0.7f, 0.7f, 1);
            g.drawCenteredString(font, Component.literal(String.valueOf(i + 1)), 0, 0, HudStyle.argb(150, HudStyle.WHITE));
            g.pose().popPose();
        }
        g.drawCenteredString(font, Component.translatable(laser != null ? "screen.imttech.cell_picker.hint" : "screen.imttech.mag_picker.hint"),
                width / 2, top + CARD_H + 10, HudStyle.argb(150, HudStyle.WHITE));
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<ItemStack> pouch = pouch();
        int top = height / 2 - CARD_H / 2;
        for (int i = 0; i < pouch.size(); i++) {
            int x = left(pouch.size()) + i * (CARD_W + 8);
            if (mouseX >= x && mouseX < x + CARD_W && mouseY >= top && mouseY < top + CARD_H && fits(pouch.get(i))) {
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
            if (index < pouch.size() && fits(pouch.get(index))) {
                pick(index);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
