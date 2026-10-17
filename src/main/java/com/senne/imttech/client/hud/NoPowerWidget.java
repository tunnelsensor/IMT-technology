package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.RED;
import static com.senne.imttech.client.hud.HudStyle.argb;

import net.minecraft.network.chat.Component;

/** Batterij leeg: één knipperende regel in het midden, en verder niets. */
public final class NoPowerWidget implements HudWidget {

    @Override
    public boolean visible(HudContext c) {
        return !c.powered && !c.menuOpen;
    }

    @Override
    public void render(HudContext c) {
        int alpha = (int) (150 + 100 * Math.sin(c.now / 200.0));
        c.g.drawCenteredString(c.font, Component.translatable("hud.imttech.no_power"),
                c.width / 2, c.height / 2 - 30, argb(alpha, RED));
    }
}
