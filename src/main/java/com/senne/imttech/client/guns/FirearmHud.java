package com.senne.imttech.client.guns;

import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModItems;
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
import org.jetbrains.annotations.Nullable;

/**
 * Wat je op je scherm ziet met een pistool of magazijn in je hand (hoofdstuk 52), zoals GunHud voor de lasergeweren:
 * rechtsonder de patronen in het magazijn (groot), "+1" als er een in de kamer zit, de naam, hoeveel magazijnen en losse
 * patronen je bij je hebt; wat er aan de hand is (storing, leeg, geen magazijn, een kapot onderdeel) en het balkje van
 * een handeling; in het midden een kruisje dat opengaat als je minder nauwkeurig schiet.
 */
public final class FirearmHud implements LayeredDraw.Layer {
    private static final int GOLD = 0xD6AC58;
    private static final int WHITE = 0xE8FBFF;
    private static final int RED = 0xFF4545;
    private static final int CYAN = 0x3FD8FF;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        FirearmType type = FirearmControl.held();
        float pt = deltaTracker.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth();
        int h = g.guiHeight();
        if (FirearmControl.heldMagazine()) {
            magazine(g, mc.font, player, w, h, pt);
            return;
        }
        if (type == null) {
            return;
        }
        float aim = FirearmControl.aim(pt);
        if (mc.options.getCameraType().isFirstPerson() && mc.screen == null && aim < 0.5f) {
            crosshair(g, w, h, type, pt, 1 - aim * 2);
        }
        ammo(g, mc.font, player, type, w, h, pt);
    }

    /** Minecraft's eigen kruisje niet, met een pistool in je hand. */
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR) && FirearmControl.held() != null) {
            event.setCanceled(true);
        }
    }

    private static void crosshair(GuiGraphics g, int w, int h, FirearmType type, float pt, float alpha) {
        float since = FirearmControl.sinceShot(pt);
        float bloom = since < 0 ? 0 : (float) Math.exp(-Math.max(0, since) * 0.3) * 7;
        int gap = Math.round(3 + type.spread(false) * GunSkillsClient.spreadFactor(type) * 1.4f + bloom + 4 * FirearmControl.sprint(pt));
        int cx = w / 2;
        int cy = h / 2;
        int color = argb(alpha * 0.9f, WHITE);
        g.fill(cx - gap - 4, cy, cx - gap, cy + 1, color);
        g.fill(cx + gap + 1, cy, cx + gap + 5, cy + 1, color);
        g.fill(cx, cy - gap - 4, cx + 1, cy - gap, color);
        g.fill(cx, cy + gap + 1, cx + 1, cy + gap + 5, color);
        g.fill(cx, cy, cx + 1, cy + 1, argb(alpha, GOLD));
    }

    private static void ammo(GuiGraphics g, Font font, LocalPlayer player, FirearmType type, int w, int h, float pt) {
        ItemStack gun = player.getMainHandItem();
        int rounds = FirearmItem.roundsInMagazine(gun);
        boolean chambered = FirearmItem.chambered(gun);
        boolean noMag = !FirearmItem.hasMagazine(gun);
        int max = type.capacity();
        int right = w - 10;
        int bottom = h - 12;
        boolean low = rounds + (chambered ? 1 : 0) <= Math.max(1, max / 5);
        boolean blink = (player.level().getGameTime() / 6) % 2 == 0;
        String big = noMag ? "--" : String.valueOf(rounds);
        String plus = chambered ? "+1" : "";
        String rest = " / " + max;
        int restW = font.width(rest) + font.width(plus) + (plus.isEmpty() ? 0 : 2);
        g.pose().pushPose();
        g.pose().translate(right - font.width(big) * 2 - restW - 2, bottom - 26, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, big, 0, 0, argb(1, low && (noMag || blink) ? RED : WHITE), true);
        g.pose().popPose();
        int x = right - restW;
        if (!plus.isEmpty()) {
            g.drawString(font, plus, x, bottom - 24, argb(1, GOLD), true);
            x += font.width(plus) + 2;
        }
        g.drawString(font, rest, x, bottom - 18, argb(0.8f, GOLD), true);
        Component name = type.text();
        g.drawString(font, name, right - font.width(name), bottom - 38, argb(0.9f, GOLD), true);
        int mags = 0;
        int loose = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (MagazineItem.typeOf(stack) == type && MagazineItem.rounds(stack) > 0) {
                mags++;
            } else if (stack.is(ModItems.ROUND_9MM.get())) {
                loose += stack.getCount();
            }
        }
        mags += GunGearClient.magazinesInPouch(player, type);
        Component carried = Component.translatable("hud.imttech.firearm.carried", mags, loose);
        g.drawString(font, carried, right - font.width(carried), bottom - 6, argb(0.75f, mags == 0 && loose == 0 ? RED : WHITE), true);

        // Wat er aan de hand is, of het balkje van een handeling.
        Firearms.ActionKind action = FirearmControl.action();
        float progress = FirearmControl.action(pt);
        int y0 = bottom - 48;
        if (action != null && progress >= 0) {
            int barW = 70;
            g.fill(right - barW, y0, right, y0 + 3, argb(0.5f, 0x2A2010));
            g.fill(right - barW, y0, right - barW + Math.round(barW * progress), y0 + 3, argb(0.95f, GOLD));
            Component text = Component.translatable("hud.imttech.firearm.action." + action.name().toLowerCase(java.util.Locale.ROOT));
            g.drawString(font, text, right - font.width(text), y0 - 10, argb(0.9f, WHITE), true);
            return;
        }
        Component status = status(gun, type);
        if (status != null) {
            g.drawString(font, status, right - font.width(status), y0, argb(blink ? 1 : 0.6f, RED), true);
        }
    }

    /** De rode regel: wat er mis is, of null. */
    private static @Nullable Component status(ItemStack gun, FirearmType type) {
        GunParts parts = FirearmItem.parts(gun);
        if (!parts.complete()) {
            return Component.translatable("hud.imttech.firearm.incomplete");
        }
        if (!parts.broken().isEmpty()) {
            GunPart broken = parts.broken().getFirst();
            return Component.translatable("hud.imttech.firearm.broken", broken.text());
        }
        int jam = FirearmItem.jam(gun);
        if (jam == FirearmItem.JAM_STOVEPIPE) {
            return Component.translatable("hud.imttech.firearm.jammed");
        }
        if (jam == FirearmItem.JAM_MISFIRE) {
            return Component.translatable("hud.imttech.firearm.misfire");
        }
        if (!FirearmItem.hasMagazine(gun)) {
            return Component.translatable(FirearmItem.chambered(gun) ? "hud.imttech.firearm.no_magazine_one"
                    : "hud.imttech.firearm.no_magazine");
        }
        if (FirearmItem.totalRounds(gun) == 0) {
            return Component.translatable("hud.imttech.firearm.empty");
        }
        return null;
    }

    /** Met een magazijn in je hand: hoe vol het is en hoeveel losse patronen je hebt. */
    private static void magazine(GuiGraphics g, Font font, LocalPlayer player, int w, int h, float pt) {
        ItemStack magazine = player.getMainHandItem();
        FirearmType type = MagazineItem.typeOf(magazine);
        if (type == null) {
            return;
        }
        int loose = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ROUND_9MM.get())) {
                loose += stack.getCount();
            }
        }
        int right = w - 10;
        int bottom = h - 12;
        String big = MagazineItem.rounds(magazine) + " / " + type.capacity();
        g.pose().pushPose();
        g.pose().translate(right - font.width(big) * 2, bottom - 26, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, big, 0, 0, argb(1, MagazineItem.rounds(magazine) == 0 ? RED : GOLD), true);
        g.pose().popPose();
        Component name = magazine.getHoverName();
        g.drawString(font, name, right - font.width(name), bottom - 38, argb(0.9f, GOLD), true);
        Component hint = Component.translatable(MagazineItem.isFull(magazine) ? "hud.imttech.magazine.full"
                : loose == 0 ? "hud.imttech.magazine.no_rounds" : "hud.imttech.magazine.load", loose);
        g.drawString(font, hint, right - font.width(hint), bottom - 6, argb(0.8f, loose == 0 ? RED : WHITE), true);
        float progress = FirearmControl.action(pt);
        if (FirearmControl.action() == Firearms.ActionKind.LOAD_ROUND && progress >= 0) {
            int barW = 70;
            g.fill(right - barW, bottom - 48, right, bottom - 45, argb(0.5f, 0x2A2010));
            g.fill(right - barW, bottom - 48, right - barW + Math.round(barW * progress), bottom - 45, argb(0.95f, CYAN));
        }
    }

    private static int argb(float alpha, int rgb) {
        return Mth.clamp(Math.round(alpha * 255), 0, 255) << 24 | rgb;
    }
}
