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
 * De app Messages (hoofdstuk 50, ook op de I.M.T Phone): wat I.M.T je de laatste tijd zei over dit systeem (say(...) uit je
 * diagrammen, alarmen, wie toegang vroeg, wie inlogde) en onderaan een bericht sturen aan een speler (leeg = iedereen
 * met I.M.T), zoals het comms-scherm van de bril, maar zonder bril.
 */
final class MessagesApp {
    private static final int ROW = 11;

    private final SecurityOsScreen screen;
    private @Nullable EditBox toBox;
    private @Nullable EditBox textBox;
    private int scroll;

    MessagesApp(SecurityOsScreen screen) {
        this.screen = screen;
    }

    private List<Tag> messages() {
        return screen.data().getList("messages", Tag.TAG_COMPOUND);
    }

    private EditBox toBox() {
        if (toBox == null) {
            toBox = new EditBox(Minecraft.getInstance().font, 0, 0, 60, 10, Component.literal("To"));
            toBox.setBordered(false);
            toBox.setMaxLength(16);
            toBox.setFilter(value -> value.chars().allMatch(c -> c < 128 && Character.isLetterOrDigit(c) || c == '_'));
            toBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
            toBox.setHint(Component.literal("everyone").withColor(OsDraw.GREY));
        }
        return toBox;
    }

    private EditBox textBox() {
        if (textBox == null) {
            textBox = new EditBox(Minecraft.getInstance().font, 0, 0, 120, 10, Component.literal("Message"));
            textBox.setBordered(false);
            textBox.setMaxLength(100);
            textBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
            textBox.setHint(Component.literal("message...").withColor(OsDraw.GREY));
        }
        return textBox;
    }

    private static int listBottom(SecurityOsScreen.Area a) {
        return a.y() + a.h() - OsDraw.BUTTON_H - 4;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
        g.drawString(font, "Messages", a.x(), a.y() + 1, HudStyle.argb(255, HudStyle.CYAN), false);
        g.drawString(font, "what I.M.T told you about this system", a.x() + font.width("Messages") + 6, a.y() + 1,
                HudStyle.argb(255, OsDraw.GREY), false);
        int top = a.y() + 13;
        int bottom = listBottom(a);
        OsDraw.field(g, a.x(), top - 1, a.x() + a.w(), bottom, false);
        List<Tag> list = messages();
        int rows = Math.max(1, (bottom - top - 2) / ROW);
        // De nieuwste onderaan; scrollen kijkt terug in de tijd.
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        int last = list.size() - scroll;
        int first = Math.max(0, last - rows);
        int y = top + 2;
        for (int i = first; i < last; i++) {
            CompoundTag message = (CompoundTag) list.get(i);
            String ago = ago(message.getInt("ago"));
            boolean alarm = message.getBoolean("a");
            g.drawString(font, ago, a.x() + 4, y + 1, HudStyle.argb(255, OsDraw.GREY), false);
            String text = OsDraw.fitRight(font, message.getString("x"), a.w() - 46);
            g.drawString(font, text, a.x() + 42, y + 1, HudStyle.argb(255, alarm ? HudStyle.RED : HudStyle.WHITE), false);
            y += ROW;
        }
        if (list.isEmpty()) {
            g.drawString(font, "(nothing yet)", a.x() + 5, top + 3, HudStyle.argb(255, OsDraw.GREY), false);
        }
        // Onderaan: een bericht sturen.
        int by = a.y() + a.h() - OsDraw.BUTTON_H;
        g.drawString(font, "To:", a.x(), by + 2, HudStyle.argb(255, HudStyle.WHITE), false);
        int toX = a.x() + font.width("To:") + 4;
        int toW = 66;
        EditBox to = toBox();
        OsDraw.field(g, toX, by, toX + toW, by + OsDraw.BUTTON_H, to.isFocused());
        to.setX(toX + 3);
        to.setY(by + 2);
        to.setWidth(toW - 6);
        to.render(g, mx, my, partialTick);
        int sendW = OsDraw.buttonWidth(font, "Send");
        int textX = toX + toW + 4;
        int textW = a.w() - (textX - a.x()) - sendW - 3;
        EditBox text = textBox();
        OsDraw.field(g, textX, by, textX + textW, by + OsDraw.BUTTON_H, text.isFocused());
        text.setX(textX + 3);
        text.setY(by + 2);
        text.setWidth(textW - 6);
        text.render(g, mx, my, partialTick);
        OsDraw.button(g, font, textX + textW + 3, by, "Send", !text.getValue().isBlank(), HudStyle.CYAN);
    }

    /** "12 s", "4 min" of "2 h" geleden. */
    private static String ago(int seconds) {
        if (seconds < 60) {
            return seconds + " s";
        }
        if (seconds < 3600) {
            return seconds / 60 + " min";
        }
        return seconds / 3600 + " h";
    }

    // ================================================================ Muis en toetsen

    void mouseClicked(Font font, SecurityOsScreen.Area a, int mx, int my) {
        int by = a.y() + a.h() - OsDraw.BUTTON_H;
        EditBox to = toBox();
        EditBox text = textBox();
        boolean inTo = my >= by && my < by + OsDraw.BUTTON_H && mx >= to.getX() - 3 && mx < to.getX() + to.getWidth() + 3;
        boolean inText = my >= by && my < by + OsDraw.BUTTON_H && mx >= text.getX() - 3 && mx < text.getX() + text.getWidth() + 3;
        to.setFocused(inTo);
        text.setFocused(inText);
        if (inTo) {
            to.mouseClicked(mx, my, 0);
        } else if (inText) {
            text.mouseClicked(mx, my, 0);
        } else if (my >= by && mx >= text.getX() + text.getWidth() + 6) {
            submit();
        }
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll + (int) Math.signum(scrollY));
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        EditBox box = focused();
        if (box == null) {
            return false;
        }
        if (keyCode == 257 || keyCode == 335) { // enter
            submit();
        } else if (keyCode == 256) { // esc: alleen uit het vak
            box.setFocused(false);
        } else if (keyCode == 258 && box == toBox) { // tab: naar het bericht
            toBox().setFocused(false);
            textBox().setFocused(true);
        } else {
            box.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        EditBox box = focused();
        if (box == null) {
            return false;
        }
        box.charTyped(codePoint, modifiers);
        return true;
    }

    private @Nullable EditBox focused() {
        if (toBox != null && toBox.isFocused()) {
            return toBox;
        }
        if (textBox != null && textBox.isFocused()) {
            return textBox;
        }
        return null;
    }

    void blur() {
        if (toBox != null) {
            toBox.setFocused(false);
        }
        if (textBox != null) {
            textBox.setFocused(false);
        }
    }

    private void submit() {
        String text = textBox().getValue().strip();
        if (text.isEmpty()) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putString("to", toBox().getValue().strip());
        args.putString("text", text);
        screen.send("msg_send", args);
        textBox().setValue("");
    }
}
