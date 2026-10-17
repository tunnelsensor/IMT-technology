package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.logic.KeycardData;
import com.senne.imttech.logic.KeycardItem;
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
 * Het Keycards-venster van de Security OS-desktop (jouw wens, 7 oktober: keycards met een niveau en
 * een eigen nummer).
 * <pre>
 *  Write a new card
 *  Name  [Lab access______]   Level [-] 3 [+]   [Write card]
 *  1 blank keycard in your inventory
 *  CARDS (2 active, 1 blocked)                       [Delete blocked (1)]
 *  #1001  Lab access      L3  by Senne                        [Block]
 *  #1003  Lotte          L5  by Senne                        [Block]
 *  #1002  Guest           L1  BLOCKED               [Delete] [Unblock]
 * </pre>
 * "Write card" schrijft de eerste lege keycard in je rugzak: hij krijgt de naam, het niveau en het
 * volgende nummer. Een kaart blokkeren maakt hem overal in dit netwerk ongeldig ({@code card.valid}
 * wordt false), zonder dat je de kaart terug hoeft te hebben.
 *
 * Geblokkeerde kaarten staan onderaan, en die kun je ook echt weggooien (jouw wens: "zodat je
 * keycard tab niet overvol en rommelig wordt"): per kaart met Delete, of allemaal tegelijk met
 * "Delete blocked". Dat vraagt eerst "Sure?" (nog een keer klikken). Een actieve kaart moet je eerst
 * blokkeren. Het nummer van een weggegooide kaart komt nooit meer terug, dus een oude kaart die nog
 * ergens rondzwerft, wordt nooit meer geldig.
 */
final class KeycardsApp {
    private static final int ROW = 13;

    private final SecurityOsScreen screen;
    private @Nullable EditBox labelBox;
    private int level = 1;
    private int scroll;
    /** Weggooien vraagt eerst "Sure?": welke kaart (of ALL_BLOCKED), en tot wanneer de vraag geldt. */
    private int pendingDelete = NONE;
    private long pendingUntil;
    private static final int NONE = -1;
    private static final int ALL_BLOCKED = 0;

    KeycardsApp(SecurityOsScreen screen) {
        this.screen = screen;
    }

    /** Alle kaarten: eerst de actieve, dan de geblokkeerde (elk op nummer). */
    private List<CompoundTag> cards() {
        List<CompoundTag> active = new java.util.ArrayList<>();
        List<CompoundTag> blocked = new java.util.ArrayList<>();
        for (Tag element : screen.data().getList("cards", Tag.TAG_COMPOUND)) {
            CompoundTag card = (CompoundTag) element;
            (card.getBoolean("b") ? blocked : active).add(card);
        }
        active.addAll(blocked);
        return active;
    }

    private static long blockedCount(List<CompoundTag> cards) {
        return cards.stream().filter(card -> card.getBoolean("b")).count();
    }

    /** Staat er een "Sure?" open voor deze kaart (of voor alle geblokkeerde)? */
    private boolean asking(int what) {
        return pendingDelete == what && net.minecraft.Util.getMillis() < pendingUntil;
    }

    /** De tekst op de knop rechtsboven de lijst. */
    private String deleteBlockedLabel(long blocked) {
        return asking(ALL_BLOCKED) ? "Sure? Delete " + blocked : "Delete blocked (" + blocked + ")";
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

    private static int fieldX(Font font, SecurityOsScreen.Area a) {
        return a.x() + font.width("Name") + 5;
    }

    private static int levelX(SecurityOsScreen.Area a) {
        return a.x() + 138;
    }

    private static int writeX(Font font, SecurityOsScreen.Area a) {
        return a.x() + a.w() - OsDraw.buttonWidth(font, "Write card");
    }

    private static int listTop(SecurityOsScreen.Area a) {
        return a.y() + 54;
    }

    /** Waar de knop "-" staat; het cijfer en de knop "+" komen erachter. */
    private static int minusX(Font font, SecurityOsScreen.Area a) {
        return levelX(a) + font.width("Level") + 4;
    }

    private static int numberX(Font font, SecurityOsScreen.Area a) {
        return minusX(font, a) + OsDraw.buttonWidth(font, "-") + 3;
    }

    private static int plusX(Font font, SecurityOsScreen.Area a) {
        return numberX(font, a) + font.width("5") + 3;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
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

        // De lijst met kaarten, met rechtsboven alle geblokkeerde in één keer weggooien.
        List<CompoundTag> cards = cards();
        long blocked = blockedCount(cards);
        String header = blocked == 0 ? "CARDS (" + cards.size() + ")"
                : "CARDS (" + (cards.size() - blocked) + " active, " + blocked + " blocked)";
        g.drawString(font, header, a.x(), listTop(a) - 11, HudStyle.argb(255, HudStyle.CYAN), false);
        if (blocked > 0) {
            String label = deleteBlockedLabel(blocked);
            OsDraw.button(g, font, a.x() + a.w() - OsDraw.buttonWidth(font, label), listTop(a) - 14, label, true, HudStyle.RED);
        }
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
            renderCard(g, font, a, y, cards.get(i));
            y += ROW;
        }
    }

    /** De knop Delete van een geblokkeerde kaart ("Sure?" als je al één keer klikte). */
    private String deleteLabel(CompoundTag card) {
        return asking(card.getInt("n")) ? "Sure?" : "Delete";
    }

    private void renderCard(GuiGraphics g, Font font, SecurityOsScreen.Area a, int y, CompoundTag card) {
        boolean blocked = card.getBoolean("b");
        int text = blocked ? 0x5A6B72 : HudStyle.WHITE;
        g.drawString(font, "#" + card.getInt("n"), a.x() + 4, y + 1, HudStyle.argb(255, blocked ? 0x5A6B72 : HudStyle.CYAN), false);
        g.drawString(font, OsDraw.fitLeft(font, card.getString("l"), 86), a.x() + 42, y + 1, HudStyle.argb(255, text), false);
        g.drawString(font, "L" + card.getInt("v"), a.x() + 132, y + 1,
                HudStyle.argb(255, blocked ? 0x5A6B72 : levelColour(card.getInt("v"))), false);
        String label = blocked ? "Unblock" : "Block";
        int bw = OsDraw.buttonWidth(font, label);
        OsDraw.button(g, font, a.x() + a.w() - bw - 3, y - 1, label, true, blocked ? HudStyle.GREEN : HudStyle.RED);
        if (blocked) {
            // Een geblokkeerde kaart kun je echt weggooien.
            String delete = deleteLabel(card);
            int dw = OsDraw.buttonWidth(font, delete);
            OsDraw.button(g, font, a.x() + a.w() - bw - 6 - dw, y - 1, delete, true, HudStyle.RED);
        }
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

    void mouseClicked(Font font, SecurityOsScreen.Area a, int mx, int my) {
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
        // Alle geblokkeerde kaarten weggooien (de knop rechtsboven de lijst).
        List<CompoundTag> cards = cards();
        long blocked = blockedCount(cards);
        if (blocked > 0) {
            String label = deleteBlockedLabel(blocked);
            int w = OsDraw.buttonWidth(font, label);
            if (OsDraw.inside(mx, my, a.x() + a.w() - w, listTop(a) - 14, w, OsDraw.BUTTON_H)) {
                if (confirm(ALL_BLOCKED)) {
                    screen.send("card_delete_blocked", new CompoundTag());
                }
                return;
            }
        }
        // Block, Unblock of Delete bij een kaart.
        int y = listTop(a) + 2;
        int bottom = a.y() + a.h();
        for (int i = scroll; i < cards.size() && y + 11 <= bottom; i++) {
            CompoundTag card = cards.get(i);
            String label = card.getBoolean("b") ? "Unblock" : "Block";
            int bw = OsDraw.buttonWidth(font, label);
            if (OsDraw.inside(mx, my, a.x() + a.w() - bw - 3, y - 1, bw, OsDraw.BUTTON_H)) {
                CompoundTag args = new CompoundTag();
                args.putInt("n", card.getInt("n"));
                args.putBoolean("on", !card.getBoolean("b"));
                screen.send("card_block", args);
                pendingDelete = NONE;
                return;
            }
            if (card.getBoolean("b")) {
                int dw = OsDraw.buttonWidth(font, deleteLabel(card));
                if (OsDraw.inside(mx, my, a.x() + a.w() - bw - 6 - dw, y - 1, dw, OsDraw.BUTTON_H)) {
                    if (confirm(card.getInt("n"))) {
                        CompoundTag args = new CompoundTag();
                        args.putInt("n", card.getInt("n"));
                        screen.send("card_delete", args);
                    }
                    return;
                }
            }
            y += ROW;
        }
        pendingDelete = NONE; // ergens anders geklikt: de vraag vervalt
    }

    /** De eerste klik vraagt "Sure?" (4 seconden lang), de tweede doet het echt. */
    private boolean confirm(int what) {
        if (asking(what)) {
            pendingDelete = NONE;
            return true;
        }
        pendingDelete = what;
        pendingUntil = net.minecraft.Util.getMillis() + 4000;
        return false;
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

    /** Alleen voor de screenshot-rondleiding: de vraag "Sure?" bij de knop Delete blocked. */
    void tourAskDeleteBlocked() {
        confirm(ALL_BLOCKED);
    }

    /** Alleen voor de screenshot-rondleiding: een naam en niveau alvast ingevuld. */
    void tourFill(String label, int level) {
        labelBox().setValue(label);
        this.level = level;
    }
}
