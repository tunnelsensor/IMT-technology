package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.logic.KeycardData;
import com.senne.starktech.logic.KeycardItem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Het Keycards-venster van de Stark OS-desktop (jouw wens, 7 oktober: keycards met een niveau en
 * een eigen nummer).
 * <pre>
 *  Write a new card
 *  Name  [Lab access______]   Level [-] 3 [+]   [Write card]
 *  1 blank keycard in your inventory
 *  CARDS (3)
 *  #1001  Lab access      L3  Senne      [Block]
 *  #1002  Guest           L1  Senne      [Unblock]  BLOCKED
 * </pre>
 * "Write card" schrijft de eerste lege keycard in je rugzak: hij krijgt de naam, het niveau en het
 * volgende nummer. Een kaart blokkeren maakt hem overal in dit netwerk ongeldig ({@code card.valid}
 * wordt false), zonder dat je de kaart terug hoeft te hebben.
 */
final class KeycardsApp {
    private static final int ROW = 13;

    private final StarkOsScreen screen;
    private @Nullable EditBox labelBox;
    private int level = 1;
    private int scroll;

    KeycardsApp(StarkOsScreen screen) {
        this.screen = screen;
    }

    private List<Tag> cards() {
        return screen.data().getList("cards", Tag.TAG_COMPOUND);
    }

    private EditBox labelBox() {
        if (labelBox == null) {
            labelBox = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 10, Component.literal("Name"));
            labelBox.setBordered(false);
            labelBox.setMaxLength(KeycardData.MAX_LABEL);
            labelBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
            labelBox.setHint(Component.literal("Lab access").withColor(HudStyle.argb(255, 0x4A5A62)));
        }
        return labelBox;
    }

    /** Hoeveel lege keycards je bij je hebt (dat weet je eigen spel, daar hoeft de server niets voor te sturen). */
    private static int blankCards() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (KeycardItem.isBlank(player.getInventory().getItem(i))) {
                count += player.getInventory().getItem(i).getCount();
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- Waar alles staat

    private static int fieldX(Font font, StarkOsScreen.Area a) {
        return a.x() + font.width("Name") + 5;
    }

    private static int levelX(StarkOsScreen.Area a) {
        return a.x() + 138;
    }

    private static int writeX(Font font, StarkOsScreen.Area a) {
        return a.x() + a.w() - OsDraw.buttonWidth(font, "Write card");
    }

    private static int listTop(StarkOsScreen.Area a) {
        return a.y() + 54;
    }

    /** Waar de knop "-" staat; het cijfer en de knop "+" komen erachter. */
    private static int minusX(Font font, StarkOsScreen.Area a) {
        return levelX(a) + font.width("Level") + 4;
    }

    private static int numberX(Font font, StarkOsScreen.Area a) {
        return minusX(font, a) + OsDraw.buttonWidth(font, "-") + 3;
    }

    private static int plusX(Font font, StarkOsScreen.Area a) {
        return numberX(font, a) + font.width("5") + 3;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my, float partialTick) {
        g.drawString(font, "Write a new card", a.x(), a.y(), HudStyle.argb(255, HudStyle.CYAN), false);
        int rowY = a.y() + 12;
        g.drawString(font, "Name", a.x(), rowY + 2, HudStyle.argb(255, HudStyle.WHITE), false);
        int fx = fieldX(font, a);
        int fw = levelX(a) - fx - 6;
        EditBox box = labelBox();
        OsDraw.field(g, fx, rowY, fx + fw, rowY + OsDraw.BUTTON_H, box.isFocused());
        box.setX(fx + 3);
        box.setY(rowY + 2);
        box.setWidth(fw - 6);
        box.render(g, mx, my, partialTick);
        // Het niveau: [-] 3 [+], met de kleur van de kaart.
        int lx = levelX(a);
        g.drawString(font, "Level", lx, rowY + 2, HudStyle.argb(255, HudStyle.WHITE), false);
        OsDraw.button(g, font, minusX(font, a), rowY, "-", level > 1, HudStyle.CYAN);
        g.drawString(font, String.valueOf(level), numberX(font, a), rowY + 2, HudStyle.argb(255, levelColour(level)), false);
        OsDraw.button(g, font, plusX(font, a), rowY, "+", level < KeycardData.MAX_LEVEL, HudStyle.CYAN);
        // De knop, en of je een lege kaart bij je hebt.
        int blanks = blankCards();
        OsDraw.button(g, font, writeX(font, a), rowY, "Write card", blanks > 0, HudStyle.GREEN);
        String hint = blanks == 0 ? "No blank keycard in your inventory (craft one first)"
                : blanks == 1 ? "1 blank keycard in your inventory" : blanks + " blank keycards in your inventory";
        g.drawString(font, hint, a.x(), rowY + 17, HudStyle.argb(255, blanks == 0 ? HudStyle.ORANGE : OsDraw.GREY), false);

        // De lijst met kaarten.
        List<Tag> cards = cards();
        g.drawString(font, "CARDS (" + cards.size() + ")", a.x(), listTop(a) - 11, HudStyle.argb(255, HudStyle.CYAN), false);
        int top = listTop(a);
        int bottom = a.y() + a.h();
        OsDraw.field(g, a.x(), top - 1, a.x() + a.w(), bottom, false);
        if (cards.isEmpty()) {
            g.drawString(font, "(no cards written yet)", a.x() + 5, top + 3, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        scroll = Math.max(0, Math.min(scroll, cards.size() - 1));
        int y = top + 2;
        for (int i = scroll; i < cards.size() && y + 11 <= bottom; i++) {
            renderCard(g, font, a, y, (CompoundTag) cards.get(i));
            y += ROW;
        }
    }

    private void renderCard(GuiGraphics g, Font font, StarkOsScreen.Area a, int y, CompoundTag card) {
        boolean blocked = card.getBoolean("b");
        int text = blocked ? 0x5A6B72 : HudStyle.WHITE;
        g.drawString(font, "#" + card.getInt("n"), a.x() + 4, y + 1, HudStyle.argb(255, blocked ? 0x5A6B72 : HudStyle.CYAN), false);
        g.drawString(font, OsDraw.fitLeft(font, card.getString("l"), 86), a.x() + 42, y + 1, HudStyle.argb(255, text), false);
        g.drawString(font, "L" + card.getInt("v"), a.x() + 132, y + 1,
                HudStyle.argb(255, blocked ? 0x5A6B72 : levelColour(card.getInt("v"))), false);
        String label = blocked ? "Unblock" : "Block";
        int bw = OsDraw.buttonWidth(font, label);
        OsDraw.button(g, font, a.x() + a.w() - bw - 3, y - 1, label, true, blocked ? HudStyle.GREEN : HudStyle.RED);
        // Wie hem schreef (of, als hij geblokkeerd is, dat in het rood).
        int room = a.w() - bw - 10 - 150;
        if (blocked) {
            g.drawString(font, "BLOCKED", a.x() + 150, y + 1, HudStyle.argb(255, HudStyle.RED), false);
        } else {
            g.drawString(font, OsDraw.fitLeft(font, "by " + card.getString("w"), room), a.x() + 150, y + 1,
                    HudStyle.argb(255, OsDraw.GREY), false);
        }
    }

    /** De kleur van een kaart van dit niveau: wit, groen, blauw, paars, goud (zoals het item). */
    static int levelColour(int level) {
        return switch (level) {
            case 2 -> 0x5BE37D;
            case 3 -> 0x4FA8FF;
            case 4 -> 0xC27BFF;
            case 5 -> 0xFFC93C;
            default -> 0xE8EEF2;
        };
    }

    // ================================================================ Muis en toetsen

    void mouseClicked(Font font, StarkOsScreen.Area a, int mx, int my) {
        int rowY = a.y() + 12;
        EditBox box = labelBox();
        int fx = fieldX(font, a);
        int fw = levelX(a) - fx - 6;
        boolean inBox = OsDraw.inside(mx, my, fx, rowY, fw, OsDraw.BUTTON_H);
        box.setFocused(inBox);
        if (inBox) {
            box.mouseClicked(mx, my, 0);
            return;
        }
        if (OsDraw.inside(mx, my, minusX(font, a), rowY, OsDraw.buttonWidth(font, "-"), OsDraw.BUTTON_H)) {
            level = Math.max(1, level - 1);
            return;
        }
        if (OsDraw.inside(mx, my, plusX(font, a), rowY, OsDraw.buttonWidth(font, "+"), OsDraw.BUTTON_H)) {
            level = Math.min(KeycardData.MAX_LEVEL, level + 1);
            return;
        }
        if (OsDraw.inside(mx, my, writeX(font, a), rowY, OsDraw.buttonWidth(font, "Write card"), OsDraw.BUTTON_H)) {
            write();
            return;
        }
        // Block of Unblock bij een kaart.
        List<Tag> cards = cards();
        int y = listTop(a) + 2;
        int bottom = a.y() + a.h();
        for (int i = scroll; i < cards.size() && y + 11 <= bottom; i++) {
            CompoundTag card = (CompoundTag) cards.get(i);
            String label = card.getBoolean("b") ? "Unblock" : "Block";
            int bw = OsDraw.buttonWidth(font, label);
            if (OsDraw.inside(mx, my, a.x() + a.w() - bw - 3, y - 1, bw, OsDraw.BUTTON_H)) {
                CompoundTag args = new CompoundTag();
                args.putInt("n", card.getInt("n"));
                args.putBoolean("on", !card.getBoolean("b"));
                screen.send("card_block", args);
                return;
            }
            y += ROW;
        }
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        EditBox box = labelBox();
        if (!box.isFocused()) {
            return false;
        }
        if (keyCode == 257 || keyCode == 335) { // enter
            write();
        } else if (keyCode == 256) { // esc: alleen uit het vak
            box.setFocused(false);
        } else {
            box.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        EditBox box = labelBox();
        if (!box.isFocused()) {
            return false;
        }
        box.charTyped(codePoint, modifiers);
        return true;
    }

    void blur() {
        if (labelBox != null) {
            labelBox.setFocused(false);
        }
    }

    private void write() {
        CompoundTag args = new CompoundTag();
        args.putString("label", labelBox().getValue().strip());
        args.putInt("level", level);
        screen.send("card_write", args);
        labelBox().setValue("");
        labelBox().setFocused(false);
    }

    /** Alleen voor de screenshot-rondleiding: een naam en niveau alvast ingevuld. */
    void tourFill(String label, int level) {
        labelBox().setValue(label);
        this.level = level;
    }
}
