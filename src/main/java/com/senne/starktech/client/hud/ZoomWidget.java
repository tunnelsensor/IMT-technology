package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.CYAN;
import static com.senne.starktech.client.hud.HudStyle.WHITE;
import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.brackets;

import com.senne.starktech.client.DroneCamera;
import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.edith.EdithBalance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Extra's tijdens inzoomen: donkere randen en een afstandsmeter. */
public final class ZoomWidget implements HudWidget {

    @Override
    public boolean visible(HudContext c) {
        // Kijk je door de drone? Dan heeft het camerabeeld voorrang.
        return c.powered && !c.menuOpen && EdithClientState.zooming && !DroneCamera.isActive();
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        int w = c.width;
        int h = c.height;
        int border = Math.min(w, h) / 8;
        int dark = argb(110, 0x000814);
        g.fill(0, 0, w, border, dark);
        g.fill(0, h - border, w, h, dark);
        g.fill(0, border, border, h - border, dark);
        g.fill(w - border, border, w, h - border, dark);
        brackets(g, border, border, w - border, h - border, 30, argb(200, CYAN));

        int cx = w / 2;
        int cy = h / 2;
        g.fill(cx - 20, cy, cx - 6, cy + 1, argb(200, CYAN));
        g.fill(cx + 7, cy, cx + 21, cy + 1, argb(200, CYAN));
        g.fill(cx, cy - 20, cx + 1, cy - 6, argb(200, CYAN));
        g.fill(cx, cy + 7, cx + 1, cy + 21, argb(200, CYAN));

        // Afstandsmeter: hoe ver is het blok waar je naar kijkt?
        Vec3 eye = c.player.getEyePosition();
        Vec3 end = eye.add(c.player.getViewVector(1.0f).scale(256));
        HitResult hit = c.player.level().clip(
                new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, c.player));
        String range = hit.getType() == HitResult.Type.MISS ? "---" : String.format("%.1fm", hit.getLocation().distanceTo(eye));
        g.drawString(c.font, Component.translatable("hud.starktech.zoom", String.format("%.0f", EdithBalance.ZOOM_FACTOR)),
                cx + 26, cy + 8, argb(230, CYAN), true);
        g.drawString(c.font, Component.translatable("hud.starktech.range", range), cx + 26, cy + 18, argb(230, WHITE), true);
    }
}
