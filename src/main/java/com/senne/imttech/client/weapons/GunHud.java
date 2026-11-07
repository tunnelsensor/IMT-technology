package com.senne.imttech.client.weapons;

import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Wat je op je scherm ziet met een lasergeweer in je hand (hoofdstuk 35), zoals in TACZ:
 * <ul>
 *   <li>rechtsonder: hoeveel schoten er nog in je cel zitten (groot), hoeveel in een volle cel gaan, de naam van je
 *       geweer en hoeveel geladen cellen je bij je hebt; tijdens herladen een balkje;</li>
 *   <li>in het midden: een eigen kruisje van vier streepjes, dat uit elkaar gaat als je minder nauwkeurig schiet (vanuit
 *       de heup, net na een schot) en weg is als je richt;</li>
 *   <li>de sniper, helemaal gericht: een kijker over je hele scherm, met een cyaan dradenkruis.</li>
 * </ul>
 */
public final class GunHud implements LayeredDraw.Layer {
    private static final int CYAN = 0x3FD8FF;
    private static final int WHITE = 0xE8FBFF;
    private static final int RED = 0xFF4545;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GunType type = GunControl.held();
        if (player == null || type == null || mc.options.hideGui) {
            return;
        }
        float pt = deltaTracker.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth();
        int h = g.guiHeight();
        float aim = GunControl.aim(pt);
        if (type == GunType.SNIPER && aim > 0.92f) {
            scope(g, w, h);
        }
        if (mc.options.getCameraType().isFirstPerson() && mc.screen == null && aim < 0.5f) {
            crosshair(g, w, h, type, pt, 1 - aim * 2);
        }
        ammo(g, mc.font, player, type, w, h, pt);
    }

    /** Minecraft's eigen kruisje niet, met een geweer in je hand (wij tekenen er een die meebeweegt). */
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR) && GunControl.held() != null) {
            event.setCanceled(true);
        }
    }

    /** Vier streepjes rond het midden; hoe verder uit elkaar, hoe minder nauwkeurig. */
    private static void crosshair(GuiGraphics g, int w, int h, GunType type, float pt, float alpha) {
        float since = GunControl.sinceShot(pt);
        float bloom = since < 0 ? 0 : (float) Math.exp(-Math.max(0, since) * 0.35) * 6;
        int gap = Math.round(3 + type.spread(false) * 1.6f * com.senne.imttech.client.guns.GunSkillsClient.spreadFactor(type.id)
                + bloom + 4 * GunControl.sprint(pt));
        int len = type == GunType.SHOTGUN ? 3 : 4;
        int cx = w / 2;
        int cy = h / 2;
        int color = argb(alpha * 0.9f, WHITE);
        g.fill(cx - gap - len, cy, cx - gap, cy + 1, color);
        g.fill(cx + gap + 1, cy, cx + gap + 1 + len, cy + 1, color);
        g.fill(cx, cy - gap - len, cx + 1, cy - gap, color);
        g.fill(cx, cy + gap + 1, cx + 1, cy + gap + 1 + len, color);
        g.fill(cx, cy, cx + 1, cy + 1, argb(alpha, CYAN));
    }

    /**
     * De kijker van de sniper: een rond venster met zwart eromheen (regel voor regel getekend), een donkere rand met
     * een cyaan ring, en een dradenkruis met streepjes om te schatten hoe ver iets is.
     */
    private static void scope(GuiGraphics g, int w, int h) {
        int size = Math.min(w, h) - 8;
        int cx = w / 2;
        int cy = h / 2;
        float r = size / 2f;
        for (int y = 0; y < h; y++) {
            float dy = y + 0.5f - cy;
            if (Math.abs(dy) >= r) {
                g.fill(0, y, w, y + 1, 0xFF000000);
                continue;
            }
            int half = Math.round((float) Math.sqrt(r * r - dy * dy));
            g.fill(0, y, cx - half, y + 1, 0xFF000000);
            g.fill(cx + half, y, w, y + 1, 0xFF000000);
            // Een donkere rand binnen het venster, en een dunne cyaan ring.
            int edge = Math.max(2, Math.round(r * 0.04f));
            g.fill(cx - half, y, cx - half + edge, y + 1, 0xC0000000);
            g.fill(cx + half - edge, y, cx + half, y + 1, 0xC0000000);
            g.fill(cx - half + edge, y, cx - half + edge + 1, y + 1, argb(0.5f, CYAN));
            g.fill(cx + half - edge - 1, y, cx + half - edge, y + 1, argb(0.5f, CYAN));
        }
        int line = argb(0.85f, CYAN);
        g.fill(cx - size / 3, cy, cx - 6, cy + 1, line);
        g.fill(cx + 7, cy, cx + size / 3, cy + 1, line);
        g.fill(cx, cy + 7, cx + 1, cy + size / 3, line);
        g.fill(cx, cy - size / 4, cx + 1, cy - 6, line);
        g.fill(cx, cy, cx + 1, cy + 1, argb(1, RED));
        for (int i = 1; i <= 3; i++) {
            g.fill(cx - 3, cy + 6 + i * size / 14, cx + 4, cy + 7 + i * size / 14, line);
        }
    }

    /** Rechtsonder: schoten over, naam, cellen, en het herlaadbalkje. */
    private static void ammo(GuiGraphics g, Font font, LocalPlayer player, GunType type, int w, int h, float pt) {
        ItemStack gun = player.getMainHandItem();
        int shots = LaserGunItem.shotsLeft(gun);
        int max = type.shotsPerCell();
        boolean noCell = LaserGunItem.cell(gun) < 0;
        int cells = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ENERGY_CELL.get()) && EnergyCellItem.charge(stack) > 0.5f) {
                cells++;
            }
        }
        // En de cellen in je magazijnpouch (hoofdstuk 55).
        for (ItemStack stack : com.senne.imttech.guns.Gear.contents(player, com.senne.imttech.guns.GearItem.Kind.MAG_POUCH)) {
            if (stack.is(ModItems.ENERGY_CELL.get()) && EnergyCellItem.charge(stack) > 0.5f) {
                cells++;
            }
        }
        int right = w - 10;
        int bottom = h - 12;
        boolean low = shots <= Math.max(1, max / 5);
        boolean blink = (player.level().getGameTime() / 6) % 2 == 0;
        // De grote teller, twee keer zo groot.
        String big = noCell ? "--" : String.valueOf(shots);
        g.pose().pushPose();
        g.pose().translate(right - font.width(big) * 2 - font.width(" / " + max) - 2, bottom - 26, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, big, 0, 0, argb(1, low && (noCell || blink) ? RED : WHITE), true);
        g.pose().popPose();
        g.drawString(font, " / " + max, right - font.width(" / " + max), bottom - 18, argb(0.8f, CYAN), true);
        // De naam en de cellen.
        Component name = Component.translatable("item.imttech." + type.id);
        g.drawString(font, name, right - font.width(name), bottom - 38, argb(0.9f, CYAN), true);
        Component cellLine = Component.translatable("hud.imttech.gun.cells", cells);
        g.drawString(font, cellLine, right - font.width(cellLine), bottom - 6, argb(0.75f, cells == 0 ? RED : WHITE), true);
        // Herladen: een balkje dat volloopt.
        float reload = GunControl.reload(pt);
        if (reload >= 0) {
            int barW = 70;
            int x0 = right - barW;
            int y0 = bottom - 48;
            g.fill(x0, y0, right, y0 + 3, argb(0.5f, 0x0A2A33));
            g.fill(x0, y0, x0 + Math.round(barW * reload), y0 + 3, argb(0.95f, CYAN));
            Component text = Component.translatable(GunControl.ejectOnly() ? "hud.imttech.gun.ejecting" : "hud.imttech.gun.reloading");
            g.drawString(font, text, right - font.width(text), y0 - 10, argb(0.9f, WHITE), true);
        } else if (!LaserGunItem.works(gun)) {
            // Hoofdstuk 55: niet compleet, geen Nether Star, of een onderdeel kapot.
            com.senne.imttech.weapons.LaserParts parts = LaserGunItem.parts(gun);
            Component text;
            if (!parts.broken().isEmpty()) {
                text = Component.translatable("hud.imttech.gun.broken",
                        parts.broken().getFirst().text().getString().toUpperCase(java.util.Locale.ROOT));
            } else if (parts.missing().size() == 1 && parts.missing().getFirst() == com.senne.imttech.weapons.LaserPart.CORE) {
                text = Component.translatable("hud.imttech.gun.no_core");
            } else {
                text = Component.translatable("hud.imttech.gun.incomplete");
            }
            g.drawString(font, text, right - font.width(text), bottom - 48, argb(blink ? 1 : 0.6f, RED), true);
        } else if (noCell || shots == 0) {
            // Hoofdstuk 57: zonder cel rechtsklik je er een in (of R met een cel in je pouch), zoals bij de Glock.
            boolean pouch = com.senne.imttech.client.guns.GunGearClient.cellsInPouch(player, -1) > 0;
            Component text = Component.translatable(noCell ? (pouch ? "hud.imttech.gun.no_cell_pouch" : "hud.imttech.gun.no_cell")
                    : "hud.imttech.gun.empty");
            g.drawString(font, text, right - font.width(text), bottom - 48, argb(blink ? 1 : 0.6f, RED), true);
        }
    }

    private static int argb(float alpha, int rgb) {
        return Mth.clamp(Math.round(alpha * 255), 0, 255) << 24 | rgb;
    }
}
