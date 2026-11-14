package com.senne.imttech.client.armor;

import com.senne.imttech.armor.PlateItem;
import com.senne.imttech.armor.PlateTier;
import com.senne.imttech.armor.VestData;
import com.senne.imttech.armor.VestItem;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.Gear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Je plates in beeld (hoofdstuk 109): draag je een vest, dan staan links naast je hartjes twee kleine plates, de voorste en
 * de achterste, in de kleur van hun materiaal, met eronder een balkje hoe heel ze nog zijn (groen, oranje, rood). Een
 * gebarsten plate is donker met een rood kruis; een lege plek alleen een grijze omtrek. Zo zie je midden in een gevecht of je
 * nog bescherming hebt, zonder je inventaris te openen. Het staat boven het vakje van je andere hand, dus het zit niets in de weg.
 */
public final class PlateHud {
    private PlateHud() {}

    /** Een plate is 9 breed en 11 hoog, met een balkje van 2 eronder. */
    private static final int W = 9;
    private static final int H = 11;

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator()) {
            return;
        }
        ItemStack vest = Gear.vest(player);
        if (!VestItem.isVest(vest)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        VestData data = VestItem.data(vest);
        // Links naast de hartjes (die beginnen 91 pixels links van het midden), op hun hoogte.
        int x = g.guiWidth() / 2 - 91 - 2 * W - 8;
        int y = g.guiHeight() - 41;
        draw(g, x, y, data.plate(true));
        draw(g, x + W + 3, y, data.plate(false));
    }

    /** Eén plate: de vorm met een shooter's cut (de bovenhoeken schuin), en eronder hoe heel hij is. */
    private static void draw(GuiGraphics g, int x, int y, ItemStack plate) {
        PlateTier tier = PlateItem.tierOf(plate);
        boolean broken = tier != null && PlateItem.broken(plate);
        int fill = tier == null ? 0 : broken ? HudStyle.argb(230, 0x2A1414) : HudStyle.argb(235, tier.colour);
        int edge = tier == null ? HudStyle.argb(140, 0x8A9BA0) : HudStyle.argb(255, 0x0E1116);
        for (int row = 0; row < H; row++) {
            int cut = Math.max(0, 3 - row);
            int x0 = x + cut;
            int x1 = x + W - cut;
            if (row == 0 || row == H - 1) {
                g.fill(x0, y + row, x1, y + row + 1, edge);
            } else {
                g.fill(x0, y + row, x0 + 1, y + row + 1, edge);
                g.fill(x1 - 1, y + row, x1, y + row + 1, edge);
                if (fill != 0) {
                    g.fill(x0 + 1, y + row, x1 - 1, y + row + 1, fill);
                }
            }
        }
        if (broken) {
            // Een rood kruis over de gebarsten plate.
            for (int i = 0; i < 5; i++) {
                g.fill(x + 2 + i, y + 3 + i, x + 3 + i, y + 4 + i, HudStyle.argb(255, HudStyle.RED));
                g.fill(x + 6 - i, y + 3 + i, x + 7 - i, y + 4 + i, HudStyle.argb(255, HudStyle.RED));
            }
        }
        if (tier != null && !broken) {
            float health = PlateItem.health(plate);
            int colour = health < 0.25f ? HudStyle.RED : health < 0.5f ? HudStyle.ORANGE : HudStyle.GREEN;
            g.fill(x, y + H + 1, x + W, y + H + 3, HudStyle.argb(200, 0x0E1116));
            HudStyle.bar(g, x, y + H + 1, W, 2, health, colour);
        }
    }
}
