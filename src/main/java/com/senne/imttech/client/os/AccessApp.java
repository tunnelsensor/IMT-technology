package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Het Access-venster van de Security OS-desktop (jouw wens, 28 september: wie de beveiliging
 * aanvalt). Twee schakelaars (monsters aanvallen, andere spelers aanvallen) en de lijst met
 * vertrouwde spelers: die worden nooit aangevallen, maar komen niet op de desktop.
 *
 * Een speler toevoegen gaat op naam; de server zoekt wie dat is (hij moet al eens op de server
 * geweest zijn).
 */
final class AccessApp {
    private static final int LINE = 14;

    private final SecurityOsScreen screen;
    private @Nullable EditBox nameBox;
    private int scroll;

    AccessApp(SecurityOsScreen screen) {
        this.screen = screen;
    }

    private CompoundTag access() {
        return screen.data().getCompound("access");
    }

    private List<Tag> trusted() {
        return access().getList("trusted", Tag.TAG_STRING);
    }

    private static int listTop(SecurityOsScreen.Area a) {
        return a.y() + 2 * LINE + 26;
    }

    private static int listBottom(SecurityOsScreen.Area a) {
        return a.y() + a.h() - OsDraw.BUTTON_H - 4;
    }

    private EditBox nameBox() {
        if (nameBox == null) {
            nameBox = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 10, Component.literal("Player"));
            nameBox.setBordered(false);
            nameBox.setMaxLength(16); // langer is een Minecraft-naam nooit
            nameBox.setFilter(value -> value.chars().allMatch(c -> c < 128 && Character.isLetterOrDigit(c) || c == '_'));
            nameBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
        }
        return nameBox;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
        CompoundTag access = access();
        // De twee schakelaars.
        toggleLine(g, font, a, a.y(), "Attack monsters", access.getBoolean("mobs"));
        toggleLine(g, font, a, a.y() + LINE, "Attack other players", access.getBoolean("players"));
        if (!access.getBoolean("pvp")) {
            g.drawString(font, "(PvP is off on this server: never)", a.x() + 4, a.y() + 2 * LINE + 1,
                    HudStyle.argb(255, OsDraw.GREY), false);
        }
        // De vertrouwde spelers.
        int headerY = a.y() + 2 * LINE + 13;
        g.drawString(font, "Trusted players", a.x(), headerY, HudStyle.argb(255, HudStyle.CYAN), false);
        g.drawString(font, "never attacked, no desktop", a.x() + font.width("Trusted players") + 6, headerY,
                HudStyle.argb(255, OsDraw.GREY), false);
        int top = listTop(a);
        int bottom = listBottom(a);
        OsDraw.field(g, a.x(), top - 1, a.x() + a.w(), bottom, false);
        List<Tag> names = trusted();
        scroll = Math.max(0, Math.min(scroll, names.size() - 1));
        int y = top + 2;
        for (int i = scroll; i < names.size() && y + 11 <= bottom; i++) {
            g.drawString(font, names.get(i).getAsString(), a.x() + 5, y + 1, HudStyle.argb(255, HudStyle.WHITE), false);
            OsDraw.button(g, font, a.x() + a.w() - OsDraw.buttonWidth(font, "Remove") - 3, y - 1, "Remove", true, HudStyle.RED);
            y += 13;
        }
        if (names.isEmpty()) {
            g.drawString(font, "(nobody yet)", a.x() + 5, top + 3, HudStyle.argb(255, OsDraw.GREY), false);
        }
        // Onderaan: iemand toevoegen.
        int by = a.y() + a.h() - OsDraw.BUTTON_H;
        g.drawString(font, "Add:", a.x(), by + 2, HudStyle.argb(255, HudStyle.WHITE), false);
        int fx = a.x() + font.width("Add:") + 4;
        int addW = OsDraw.buttonWidth(font, "Trust");
        int fw = a.w() - (fx - a.x()) - addW - 3;
        EditBox box = nameBox();
        OsDraw.field(g, fx, by, fx + fw, by + OsDraw.BUTTON_H, box.isFocused());
        box.setX(fx + 3);
        box.setY(by + 2);
        box.setWidth(fw - 6);
        box.render(g, mx, my, partialTick);
        OsDraw.button(g, font, fx + fw + 3, by, "Trust", !box.getValue().isEmpty(), HudStyle.CYAN);
    }

    /** Een regel met tekst links en een knop ON/OFF rechts. */
    private static void toggleLine(GuiGraphics g, Font font, SecurityOsScreen.Area a, int y, String text, boolean on) {
        g.drawString(font, text, a.x(), y + 2, HudStyle.argb(255, HudStyle.WHITE), false);
        String label = on ? "ON" : "OFF";
        OsDraw.button(g, font, a.x() + a.w() - toggleWidth(font), y, label, true, on ? HudStyle.GREEN : HudStyle.RED);
    }

    /** Even breed voor ON en OFF, zodat de knop niet verspringt. */
    private static int toggleWidth(Font font) {
        return OsDraw.buttonWidth(font, "OFF");
    }

    // ================================================================ Muis en toetsen

    void mouseClicked(Font font, SecurityOsScreen.Area a, int mx, int my) {
        CompoundTag access = access();
        int toggleX = a.x() + a.w() - toggleWidth(font);
        if (OsDraw.inside(mx, my, toggleX, a.y(), toggleWidth(font), OsDraw.BUTTON_H)) {
            flag("mobs", !access.getBoolean("mobs"));
            return;
        }
        if (OsDraw.inside(mx, my, toggleX, a.y() + LINE, toggleWidth(font), OsDraw.BUTTON_H)) {
            flag("players", !access.getBoolean("players"));
            return;
        }
        // Iemand weghalen.
        List<Tag> names = trusted();
        int y = listTop(a) + 2;
        int bottom = listBottom(a);
        int removeW = OsDraw.buttonWidth(font, "Remove");
        for (int i = scroll; i < names.size() && y + 11 <= bottom; i++) {
            if (OsDraw.inside(mx, my, a.x() + a.w() - removeW - 3, y - 1, removeW, OsDraw.BUTTON_H)) {
                CompoundTag args = new CompoundTag();
                args.putString("name", names.get(i).getAsString());
                screen.send("trust_remove", args);
                return;
            }
            y += 13;
        }
        // Het naamvak en de knop.
        EditBox box = nameBox();
        int by = a.y() + a.h() - OsDraw.BUTTON_H;
        boolean inBox = my >= by && my < by + OsDraw.BUTTON_H && mx >= box.getX() - 3 && mx < box.getX() + box.getWidth() + 3;
        box.setFocused(inBox);
        if (inBox) {
            box.mouseClicked(mx, my, 0);
        } else if (my >= by && mx >= box.getX() + box.getWidth() + 6) {
            submit();
        }
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        EditBox box = nameBox();
        if (!box.isFocused()) {
            return false;
        }
        if (keyCode == 257 || keyCode == 335) { // enter
            submit();
        } else if (keyCode == 256) { // esc: alleen uit het vak
            box.setFocused(false);
        } else {
            box.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        EditBox box = nameBox();
        if (!box.isFocused()) {
            return false;
        }
        box.charTyped(codePoint, modifiers);
        return true;
    }

    void blur() {
        if (nameBox != null) {
            nameBox.setFocused(false);
        }
    }

    private void submit() {
        String name = nameBox().getValue().strip();
        if (name.isEmpty()) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putString("name", name);
        screen.send("trust_add", args);
        nameBox().setValue("");
    }

    private void flag(String name, boolean on) {
        CompoundTag args = new CompoundTag();
        args.putString("name", name);
        args.putBoolean("on", on);
        screen.send("flag", args);
    }
}
