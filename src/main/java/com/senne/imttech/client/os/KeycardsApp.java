package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.logic.KeycardData;
import com.senne.imttech.logic.KeycardItem;
import com.senne.imttech.logic.LogicStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
 * Het Keycards-venster van de Security OS-desktop: kaarten schrijven, blokkeren en weggooien (7 en 8 oktober),
 * en sinds 25 oktober hun groepen (jouw wens: "keycards groups ipv levels").
 * <pre>
 *  Write a new card                                   1 blank keycard in your inventory
 *  Name [Lab access____]  Colour ■■■■■■■■             [Write card]
 *  The new card goes into: security
 *  GROUPS (4/32)          | IN SECURITY (2, +1 inside)
 *  All cards          (5) | #1001 ■ Lab access  lab, security   [Remove] [Block]
 *  ▸ staff            (0) | #1003 ■ Lotte       security        [Remove] [Block]
 *     ▸ security      (2) |
 *        ▸ night shift(1) |
 *  ▸ lab              (1) |
 *  No group           (1) |
 *  [New] [Rename] [Delete]  Drag a card onto a group to add it to that group.
 * </pre>
 * <ul>
 *   <li><b>Links</b> je groepen als een boom (een groep in een groep staat eronder, wat verder naar rechts), met
 *       bovenaan "All cards" en onderaan "No group". Klik er een aan: rechts staan dan zijn kaarten.</li>
 *   <li><b>Slepen</b>: sleep een kaart op een groep, dan zit hij (ook) in die groep. Een kaart kan in meer dan één
 *       groep zitten (jouw keuze); Remove haalt hem uit de groep die links gekozen is. Sleep een groep op een groep om
 *       hem erin te steken, of op "All cards" om hem bovenaan te zetten. Een groep kan niet in een groep die er zelf al
 *       in zit: die regel kleurt dan rood.</li>
 *   <li><b>New</b> maakt een groep in de gekozen groep (of bovenaan), <b>Rename</b> geeft hem een andere naam (en zegt
 *       "Change it in your diagrams too."), <b>Delete</b> gooit hem weg na "Sure?": wat erin zat, schuift een laag
 *       omhoog.</li>
 *   <li><b>Write card</b> schrijft de eerste lege keycard in je rugzak, met de naam en de kleur die je koos; hij komt
 *       in de groep die links gekozen is. Een kaart blokkeren maakt hem overal in dit netwerk ongeldig
 *       ({@code card.valid} en {@code card.in_group(...)} worden false), zonder dat je de kaart terug hoeft te hebben.</li>
 * </ul>
 * Geblokkeerde kaarten staan onderaan, en die kun je ook echt weggooien (jouw wens: "zodat je keycard tab niet
 * overvol en rommelig wordt"): per kaart met Delete, of allemaal tegelijk met "Delete blocked" (bij All cards). Dat
 * vraagt eerst "Sure?" (nog een keer klikken). Het nummer van een weggegooide kaart komt nooit meer terug.
 *
 * De lijst komt van de server (de kern weet welke kaarten in welke groep zitten); wat je verandert, gaat als een
 * opdracht naar de server, die zelf nakijkt of het mag (geen rondjes, niet te veel groepen).
 */
final class KeycardsApp {
    private static final int CARD_ROW = 13;
    private static final int GROUP_ROW = 11;
    private static final int INDENT = 8;
    private static final int MAX_DEPTH = 8;
    /** Zo breed is de lijst met groepen links. */
    private static final int LEFT_W = 128;
    /** Zo groot is een kleurvakje (en de ruimte ertussen). */
    private static final int SWATCH = 9;
    private static final int SWATCH_GAP = 2;

    /** Een kaart zoals de server hem stuurde. */
    private record CardRow(int number, String label, int colour, boolean blocked, String writer, List<String> groups) {
        boolean isIn(String group) {
            return groups.stream().anyMatch(own -> own.equalsIgnoreCase(group));
        }
    }

    private enum Kind { ALL, GROUP, NONE }

    /** Een regel links: "All cards", een groep (met hoe diep hij zit), of "No group". */
    private record Row(Kind kind, String group, int depth) {
        boolean same(@Nullable Row other) {
            return other != null && kind == other.kind && (kind != Kind.GROUP || group.equalsIgnoreCase(other.group));
        }
    }

    private static final Row ALL = new Row(Kind.ALL, "", 0);
    private static final Row NONE = new Row(Kind.NONE, "", 0);

    /** Wat je aan het typen bent onderaan: niets, de naam van een nieuwe groep, of een nieuwe naam. */
    private enum Input { OFF, NEW_GROUP, RENAME }

    private final SecurityOsScreen screen;
    private @Nullable EditBox labelBox;
    private @Nullable EditBox nameBox;
    private Input input = Input.OFF;
    /** De kleur van de nieuwe kaart, 1 tot en met 8 (25 oktober: "kleur kies je per kaart"). */
    private int colour = 1;
    /** Wat links gekozen is. */
    private Row selected = ALL;
    private int groupScroll;
    private int cardScroll;
    /** Waar je op drukte (misschien begint daar een sleep): een kaart (nummer) of een groep. */
    private int pressedCard = -1;
    private @Nullable String pressedGroup;
    private int pressX;
    private int pressY;
    private boolean dragging;
    /** Weggooien vraagt eerst "Sure?": welke kaart (of ALL_BLOCKED), en tot wanneer de vraag geldt. */
    private int pendingDelete = NOTHING;
    private long pendingUntil;
    private static final int NOTHING = -1;
    private static final int ALL_BLOCKED = 0;
    /** Een groep weggooien vraagt ook eerst "Sure?". */
    private @Nullable String pendingGroup;
    private long pendingGroupUntil;

    KeycardsApp(SecurityOsScreen screen) {
        this.screen = screen;
    }

    // ================================================================ Wat de server stuurde

    /** Alle kaarten: eerst de actieve, dan de geblokkeerde (elk op nummer). */
    private List<CardRow> allCards() {
        List<CardRow> active = new ArrayList<>();
        List<CardRow> blocked = new ArrayList<>();
        for (Tag element : screen.data().getList("cards", Tag.TAG_COMPOUND)) {
            CompoundTag card = (CompoundTag) element;
            List<String> groups = new ArrayList<>();
            for (Tag group : card.getList("g", Tag.TAG_STRING)) {
                groups.add(group.getAsString());
            }
            CardRow row = new CardRow(card.getInt("n"), card.getString("l"), card.getInt("c"), card.getBoolean("b"),
                    card.getString("w"), groups);
            (row.blocked() ? blocked : active).add(row);
        }
        active.addAll(blocked);
        return active;
    }

    /** Per groep: in welke groep hij zit ("" = bovenaan). Op alfabet, hoofdletters maken niet uit. */
    private Map<String, String> parents() {
        Map<String, String> parents = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Tag element : screen.data().getList("cardGroups", Tag.TAG_COMPOUND)) {
            CompoundTag group = (CompoundTag) element;
            parents.put(group.getString("n"), group.getString("p"));
        }
        return parents;
    }

    /** Zit groep {@code group} in {@code outer} (dezelfde, of via groepen in groepen)? Zoals LogicStore.isInside. */
    private static boolean isInside(Map<String, String> parents, String group, String outer) {
        String at = group;
        for (int steps = 0; !at.isEmpty() && steps <= parents.size(); steps++) {
            if (at.equalsIgnoreCase(outer)) {
                return true;
            }
            at = parents.getOrDefault(at, "");
        }
        return false;
    }

    /** De regels links: "All cards", de boom van groepen, en "No group". */
    private static List<Row> rows(Map<String, String> parents) {
        List<Row> rows = new ArrayList<>();
        rows.add(ALL);
        addGroups(rows, parents, "", 1);
        rows.add(NONE);
        return rows;
    }

    private static void addGroups(List<Row> rows, Map<String, String> parents, String parent, int depth) {
        if (depth > MAX_DEPTH) {
            return; // een rondje kan niet, maar zo loopt het nooit vast
        }
        parents.forEach((group, above) -> {
            if (above.equalsIgnoreCase(parent)) {
                rows.add(new Row(Kind.GROUP, group, depth));
                addGroups(rows, parents, group, depth + 1);
            }
        });
    }

    /**
     * Wat links gekozen is, als die groep (nog) bestaat; anders All cards. (Een groep kan intussen weg zijn, of een nieuwe
     * groep staat er nog niet: de server moet hem eerst maken.)
     */
    private Row selection(Map<String, String> parents) {
        return selected.kind() == Kind.GROUP && !parents.containsKey(selected.group()) ? ALL : selected;
    }

    /** De kaarten van wat links gekozen is. */
    private static List<CardRow> cardsOf(Row row, List<CardRow> cards) {
        return switch (row.kind()) {
            case ALL -> cards;
            case NONE -> cards.stream().filter(card -> card.groups().isEmpty()).toList();
            case GROUP -> cards.stream().filter(card -> card.isIn(row.group())).toList();
        };
    }

    private static long blockedCount(List<CardRow> cards) {
        return cards.stream().filter(CardRow::blocked).count();
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

    // ================================================================ "Sure?"

    /** Staat er een "Sure?" open voor deze kaart (of voor alle geblokkeerde)? */
    private boolean asking(int what) {
        return pendingDelete == what && net.minecraft.Util.getMillis() < pendingUntil;
    }

    private boolean askingGroup(String group) {
        return pendingGroup != null && pendingGroup.equalsIgnoreCase(group) && net.minecraft.Util.getMillis() < pendingGroupUntil;
    }

    /** De eerste klik vraagt "Sure?" (4 seconden lang), de tweede doet het echt. */
    private boolean confirm(int what) {
        if (asking(what)) {
            pendingDelete = NOTHING;
            return true;
        }
        pendingDelete = what;
        pendingUntil = net.minecraft.Util.getMillis() + 4000;
        return false;
    }

    private boolean confirmGroup(String group) {
        if (askingGroup(group)) {
            pendingGroup = null;
            return true;
        }
        pendingGroup = group;
        pendingGroupUntil = net.minecraft.Util.getMillis() + 4000;
        return false;
    }

    private String deleteBlockedLabel(long blocked) {
        return asking(ALL_BLOCKED) ? "Sure? Delete " + blocked : "Delete blocked (" + blocked + ")";
    }

    private String deleteGroupLabel(Row row) {
        return row.kind() == Kind.GROUP && askingGroup(row.group()) ? "Sure?" : "Delete";
    }

    // ================================================================ Waar alles staat

    private static int writeY(SecurityOsScreen.Area a) {
        return a.y() + 11;
    }

    private static int fieldX(Font font, SecurityOsScreen.Area a) {
        return a.x() + font.width("Name") + 5;
    }

    private static int fieldW() {
        return 96;
    }

    /** Waar het eerste kleurvakje staat. */
    private static int swatchX(Font font, SecurityOsScreen.Area a) {
        return fieldX(font, a) + fieldW() + 10 + font.width("Colour") + 4;
    }

    private static int writeX(Font font, SecurityOsScreen.Area a) {
        return a.x() + a.w() - OsDraw.buttonWidth(font, "Write card");
    }

    private static int listTop(SecurityOsScreen.Area a) {
        return a.y() + 51;
    }

    private static int listBottom(SecurityOsScreen.Area a) {
        return a.y() + a.h() - OsDraw.BUTTON_H - 4;
    }

    private static int bottomY(SecurityOsScreen.Area a) {
        return a.y() + a.h() - OsDraw.BUTTON_H;
    }

    /** Waar de lijst met kaarten begint (rechts van de groepen). */
    private static int cardsX(SecurityOsScreen.Area a) {
        return a.x() + LEFT_W + 6;
    }

    private static int cardsW(SecurityOsScreen.Area a) {
        return a.w() - LEFT_W - 6;
    }

    /** De regel links onder de muis, of null. */
    private @Nullable Row rowAt(SecurityOsScreen.Area a, List<Row> rows, int mx, int my) {
        int top = listTop(a);
        if (mx < a.x() || mx >= a.x() + LEFT_W || my < top || my >= listBottom(a)) {
            return null;
        }
        int index = groupScroll + (my - top) / GROUP_ROW;
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    /** Hoeveel kaarten er onder elkaar passen. */
    private static int visibleCards(SecurityOsScreen.Area a) {
        return Math.max(1, (listBottom(a) - listTop(a) - 2) / CARD_ROW);
    }

    /** De kaart rechts onder de muis, of null. */
    private @Nullable CardRow cardAt(SecurityOsScreen.Area a, List<CardRow> cards, int mx, int my) {
        int top = listTop(a) + 2;
        if (mx < cardsX(a) || mx >= cardsX(a) + cardsW(a) || my < top - 1 || my >= listBottom(a)) {
            return null;
        }
        int index = cardScroll + (my - top + 1) / CARD_ROW;
        return index >= 0 && index < cards.size() && index < cardScroll + visibleCards(a) ? cards.get(index) : null;
    }

    /** De knoppen van een kaart, van rechts naar links: Block/Unblock, Delete (geblokkeerd), Remove (in een groep). */
    private List<String> cardButtons(CardRow card, Row row) {
        List<String> buttons = new ArrayList<>();
        buttons.add(card.blocked() ? "Unblock" : "Block");
        if (card.blocked()) {
            buttons.add(asking(card.number()) ? "Sure?" : "Delete");
        }
        if (row.kind() == Kind.GROUP) {
            buttons.add("Remove");
        }
        return buttons;
    }

    // ================================================================ Slepen

    /** Mag wat je sleept op deze regel links? Een kaart op een groep waar hij nog niet in zit; een groep zonder rondje. */
    private boolean canDrop(Map<String, String> parents, List<CardRow> cards, @Nullable Row target) {
        if (target == null || target.kind() == Kind.NONE) {
            return false;
        }
        if (pressedCard >= 0) {
            CardRow card = cards.stream().filter(row -> row.number() == pressedCard).findFirst().orElse(null);
            return target.kind() == Kind.GROUP && card != null && !card.isIn(target.group());
        }
        if (pressedGroup != null) {
            // Niet in zichzelf of in een groep die er al in zit, en niet waar hij al zit.
            String into = target.kind() == Kind.ALL ? "" : target.group();
            return (into.isEmpty() || !isInside(parents, into, pressedGroup))
                    && !parents.getOrDefault(pressedGroup, "").equalsIgnoreCase(into);
        }
        return false;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
        Map<String, String> parents = parents();
        List<CardRow> cards = allCards();
        Row row = selection(parents);
        renderWrite(g, font, a, row, mx, my, partialTick);
        renderGroups(g, font, a, parents, cards, mx, my);
        renderCards(g, font, a, parents, cards, row, mx, my);
        // Onderaan: het naamvak, of de knoppen van de groepen en een uitleg.
        if (input != Input.OFF && nameBox != null) {
            renderInput(g, font, a, mx, my, partialTick);
        } else {
            int bx = a.x();
            boolean group = row.kind() == Kind.GROUP;
            bx += OsDraw.button(g, font, bx, bottomY(a), "New", parents.size() < LogicStore.MAX_GROUPS, HudStyle.CYAN) + 3;
            bx += OsDraw.button(g, font, bx, bottomY(a), "Rename", group, HudStyle.CYAN) + 3;
            OsDraw.button(g, font, bx, bottomY(a), deleteGroupLabel(row), group, HudStyle.RED);
            String hint = hint(a, parents, cards, row, mx, my);
            int hx = cardsX(a);
            g.drawString(font, OsDraw.fitRight(font, hint, cardsW(a)), hx, bottomY(a) + 2,
                    HudStyle.argb(255, hint.startsWith("Can't") ? HudStyle.RED : OsDraw.GREY), false);
        }
        // Slepen: wat je sleept, hangt aan de muis.
        if (dragging) {
            String name = pressedCard >= 0 ? "#" + pressedCard : pressedGroup;
            if (name != null) {
                g.fill(mx + 6, my + 2, mx + 12 + font.width(name), my + 13, HudStyle.argb(220, 0x16303F));
                g.drawString(font, name, mx + 9, my + 4, HudStyle.argb(255, HudStyle.CYAN), false);
            }
        }
    }

    /** Bovenaan: een nieuwe kaart schrijven (naam, kleur, de knop) en in welke groep hij komt. */
    private void renderWrite(GuiGraphics g, Font font, SecurityOsScreen.Area a, Row row, int mx, int my, float partialTick) {
        g.drawString(font, "Write a new card", a.x(), a.y(), HudStyle.argb(255, HudStyle.CYAN), false);
        int blanks = blankCards();
        String blankHint = blanks == 0 ? "No blank keycard in your inventory (craft one first)"
                : blanks == 1 ? "1 blank keycard in your inventory" : blanks + " blank keycards in your inventory";
        g.drawString(font, blankHint, a.x() + a.w() - font.width(blankHint), a.y(),
                HudStyle.argb(255, blanks == 0 ? HudStyle.ORANGE : OsDraw.GREY), false);
        int rowY = writeY(a);
        g.drawString(font, "Name", a.x(), rowY + 2, HudStyle.argb(255, HudStyle.WHITE), false);
        int fx = fieldX(font, a);
        EditBox box = labelBox();
        OsDraw.field(g, fx, rowY, fx + fieldW(), rowY + OsDraw.BUTTON_H, box.isFocused());
        box.setX(fx + 3);
        box.setY(rowY + 2);
        box.setWidth(fieldW() - 6);
        box.render(g, mx, my, partialTick);
        // De kleur: acht vakjes met een grijs randje (zo zie je zwart ook), de gekozen met een witte rand.
        int cx = fx + fieldW() + 10;
        g.drawString(font, "Colour", cx, rowY + 2, HudStyle.argb(255, HudStyle.WHITE), false);
        int sx = swatchX(font, a);
        for (int i = 1; i <= KeycardData.COLOURS.length; i++) {
            int x = sx + (i - 1) * (SWATCH + SWATCH_GAP);
            swatch(g, x, rowY + 1, SWATCH, i, i == colour ? HudStyle.WHITE : EDGE);
        }
        OsDraw.button(g, font, writeX(font, a), rowY, "Write card", blanks > 0, HudStyle.GREEN);
        // Waar hij in komt: de groep die links gekozen is.
        int iy = rowY + 16;
        String into = "The new card goes into: ";
        g.drawString(font, into, a.x(), iy, HudStyle.argb(255, OsDraw.GREY), false);
        int nx = a.x() + font.width(into);
        if (row.kind() == Kind.GROUP) {
            g.drawString(font, OsDraw.fitRight(font, row.group(), a.w() - (nx - a.x())), nx, iy, HudStyle.argb(255, HudStyle.WHITE), false);
        } else {
            g.drawString(font, "no group (pick a group on the left)", nx, iy, HudStyle.argb(255, OsDraw.GREY), false);
        }
        // Wijs je een kleurvakje aan? Dan zie je zijn naam.
        int over = swatchAt(font, a, mx, my);
        if (over > 0) {
            String name = KeycardData.COLOUR_NAMES[over - 1];
            g.drawString(font, name, a.x() + a.w() - font.width(name), iy, HudStyle.argb(255, KeycardData.rgb(over)), false);
        }
    }

    /** Het randje om een kleurvakje. */
    private static final int EDGE = 0x5A6B72;

    /** Een kleurvakje van {@code size} pixels in kleur {@code colour} (1-8; 0 = grijs), met een randje eromheen. */
    private static void swatch(GuiGraphics g, int x, int y, int size, int colour, int edge) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, HudStyle.argb(255, edge));
        g.fill(x, y, x + size, y + size, HudStyle.argb(255, colour == 0 ? EDGE : KeycardData.rgb(colour)));
    }

    /** Het kleurvakje onder de muis (1-8), of 0. */
    private static int swatchAt(Font font, SecurityOsScreen.Area a, int mx, int my) {
        int y = writeY(a) + 1;
        if (my < y - 1 || my >= y + SWATCH + 1) {
            return 0;
        }
        int sx = swatchX(font, a);
        for (int i = 1; i <= KeycardData.COLOURS.length; i++) {
            int x = sx + (i - 1) * (SWATCH + SWATCH_GAP);
            if (mx >= x - 1 && mx < x + SWATCH + 1) {
                return i;
            }
        }
        return 0;
    }

    private void renderGroups(GuiGraphics g, Font font, SecurityOsScreen.Area a, Map<String, String> parents, List<CardRow> cards,
                              int mx, int my) {
        int top = listTop(a);
        int bottom = listBottom(a);
        g.drawString(font, "GROUPS (" + parents.size() + "/" + LogicStore.MAX_GROUPS + ")", a.x(), top - 11,
                HudStyle.argb(255, HudStyle.CYAN), false);
        OsDraw.field(g, a.x(), top - 1, a.x() + LEFT_W, bottom, false);
        List<Row> rows = rows(parents);
        int visible = Math.max(1, (bottom - top) / GROUP_ROW);
        groupScroll = Math.max(0, Math.min(groupScroll, rows.size() - visible));
        Row hover = rowAt(a, rows, mx, my);
        for (int i = groupScroll; i < Math.min(rows.size(), groupScroll + visible); i++) {
            Row row = rows.get(i);
            int ry = top + (i - groupScroll) * GROUP_ROW;
            if (dragging && row.same(hover) && row.kind() != Kind.NONE) {
                // Hier loslaten: groen als het mag, rood als het niet kan (een rondje, of hij zit er al in).
                boolean ok = canDrop(parents, cards, row);
                g.fill(a.x() + 1, ry, a.x() + LEFT_W - 1, ry + GROUP_ROW, HudStyle.argb(255, ok ? 0x1E5A3A : 0x5A1E24));
            } else if (row.same(selection(parents))) {
                g.fill(a.x() + 1, ry, a.x() + LEFT_W - 1, ry + GROUP_ROW, HudStyle.argb(255, 0x1E4A60));
            } else if (row.same(hover)) {
                g.fill(a.x() + 1, ry, a.x() + LEFT_W - 1, ry + GROUP_ROW, HudStyle.argb(255, 0x14303D));
            }
            int count = cardsOf(row, cards).size();
            String right = String.valueOf(count);
            int rx = a.x() + LEFT_W - 4 - font.width(right);
            g.drawString(font, right, rx, ry + 2, HudStyle.argb(255, OsDraw.GREY), false);
            if (row.kind() == Kind.GROUP) {
                // Een mapje (zoals in de app Groups) en de naam, wat verder naar rechts als hij in een groep zit.
                int x = a.x() + 3 + Math.min(row.depth() - 1, MAX_DEPTH) * INDENT;
                int folder = HudStyle.argb(255, HudStyle.CYAN);
                g.fill(x, ry + 2, x + 4, ry + 3, folder);
                g.fill(x, ry + 3, x + 9, ry + 9, folder);
                g.drawString(font, OsDraw.fitRight(font, row.group(), rx - x - 16), x + 12, ry + 2, HudStyle.argb(255, HudStyle.WHITE), false);
            } else {
                String label = row.kind() == Kind.ALL ? "All cards" : "No group";
                g.drawString(font, label, a.x() + 3, ry + 2, HudStyle.argb(255, OsDraw.GREY), false);
            }
        }
    }

    private void renderCards(GuiGraphics g, Font font, SecurityOsScreen.Area a, Map<String, String> parents, List<CardRow> all,
                             Row row, int mx, int my) {
        int x0 = cardsX(a);
        int w = cardsW(a);
        int top = listTop(a);
        int bottom = listBottom(a);
        List<CardRow> cards = cardsOf(row, all);
        long blocked = blockedCount(cards);
        String header = switch (row.kind()) {
            case ALL -> blocked == 0 ? "ALL CARDS (" + cards.size() + ")" : "ALL CARDS (" + cards.size() + ", " + blocked + " blocked)";
            case NONE -> "WITHOUT A GROUP (" + cards.size() + ")";
            case GROUP -> {
                // Ook de kaarten in groepen die erin zitten: die laat card.in_group(...) ook binnen.
                long inside = all.stream().filter(card -> !card.isIn(row.group())
                        && card.groups().stream().anyMatch(own -> isInside(parents, own, row.group()))).count();
                yield "IN " + row.group().toUpperCase(java.util.Locale.ROOT) + " (" + cards.size() + (inside > 0 ? ", +" + inside + " inside" : "") + ")";
            }
        };
        int headerRoom = w;
        if (row.kind() == Kind.ALL && blocked > 0) {
            // Rechtsboven: alle geblokkeerde kaarten in één keer weggooien.
            String label = deleteBlockedLabel(blocked);
            int bw = OsDraw.buttonWidth(font, label);
            OsDraw.button(g, font, x0 + w - bw, top - 14, label, true, HudStyle.RED);
            headerRoom = w - bw - 4;
        }
        g.drawString(font, OsDraw.fitRight(font, header, headerRoom), x0, top - 11, HudStyle.argb(255, HudStyle.CYAN), false);
        OsDraw.field(g, x0, top - 1, x0 + w, bottom, false);
        if (cards.isEmpty()) {
            String empty = row.kind() == Kind.ALL ? "(no cards written yet)"
                    : row.kind() == Kind.NONE ? "(every card is in a group)" : "(no cards: drag one onto this group)";
            g.drawString(font, empty, x0 + 5, top + 3, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        int visible = visibleCards(a);
        cardScroll = Math.max(0, Math.min(cardScroll, cards.size() - visible));
        CardRow hover = dragging ? null : cardAt(a, cards, mx, my);
        int y = top + 2;
        for (int i = cardScroll; i < Math.min(cards.size(), cardScroll + visible); i++) {
            CardRow card = cards.get(i);
            if (card.equals(hover) || dragging && card.number() == pressedCard) {
                g.fill(x0 + 1, y - 1, x0 + w - 1, y + CARD_ROW - 2, HudStyle.argb(255, 0x14303D));
            }
            renderCard(g, font, x0, w, y, card, row);
            y += CARD_ROW;
        }
    }

    private void renderCard(GuiGraphics g, Font font, int x0, int w, int y, CardRow card, Row row) {
        boolean blocked = card.blocked();
        int dim = 0x5A6B72;
        g.drawString(font, "#" + card.number(), x0 + 3, y + 1, HudStyle.argb(255, blocked ? dim : HudStyle.CYAN), false);
        swatch(g, x0 + 37, y + 1, 6, blocked ? 0 : card.colour(), EDGE);
        g.drawString(font, OsDraw.fitRight(font, card.label(), 64), x0 + 47, y + 1, HudStyle.argb(255, blocked ? dim : HudStyle.WHITE), false);
        // De knoppen naast elkaar, rechts.
        int bx = x0 + w - 3;
        for (String label : cardButtons(card, row)) {
            int bw = OsDraw.buttonWidth(font, label);
            bx -= bw;
            int accent = label.equals("Unblock") ? HudStyle.GREEN : label.equals("Remove") ? HudStyle.CYAN : HudStyle.RED;
            OsDraw.button(g, font, bx, y - 1, label, true, accent);
            bx -= 3;
        }
        // Daartussen: zijn groepen (of in het rood dat hij geblokkeerd is).
        int gx = x0 + 115;
        int room = bx - 3 - gx;
        if (blocked) {
            g.drawString(font, "BLOCKED", gx, y + 1, HudStyle.argb(255, HudStyle.RED), false);
        } else if (room > 10) {
            String groups = card.groups().isEmpty() ? "-" : String.join(", ", card.groups());
            g.drawString(font, OsDraw.fitRight(font, groups, room), gx, y + 1, HudStyle.argb(255, OsDraw.GREY), false);
        }
    }

    /** De uitleg onderaan rechts: wat je aanwijst of sleept, of hoe slepen werkt. */
    private String hint(SecurityOsScreen.Area a, Map<String, String> parents, List<CardRow> cards, Row row, int mx, int my) {
        if (dragging) {
            Row target = rowAt(a, rows(parents), mx, my);
            if (pressedGroup != null && target != null && target.kind() == Kind.GROUP && isInside(parents, target.group(), pressedGroup)
                    && !target.group().equalsIgnoreCase(pressedGroup)) {
                return "Can't: '" + target.group() + "' is inside '" + pressedGroup + "' (that would make a loop)";
            }
            return pressedCard >= 0 ? "Drop it on a group: the card is then also in that group."
                    : "Drop it on a group to put it inside, or on All cards for the top.";
        }
        CardRow card = cardAt(a, cardsOf(row, cards), mx, my);
        if (card != null) {
            return "#" + card.number() + ", written by " + card.writer() + ". Drag it onto a group.";
        }
        return "Drag a card onto a group to add it to that group.";
    }

    private void renderInput(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
        int by = bottomY(a);
        String label = input == Input.NEW_GROUP ? "Group:" : "Name:";
        g.drawString(font, label, a.x(), by + 2, HudStyle.argb(255, HudStyle.WHITE), false);
        int fx = a.x() + font.width(label) + 4;
        int fw = 120;
        OsDraw.field(g, fx, by, fx + fw, by + OsDraw.BUTTON_H, true);
        EditBox box = nameBox();
        box.setX(fx + 3);
        box.setY(by + 2);
        box.setWidth(fw - 6);
        box.render(g, mx, my, partialTick);
        int okX = fx + fw + 3;
        int okW = OsDraw.button(g, font, okX, by, "OK", true, HudStyle.CYAN);
        int cancelW = OsDraw.button(g, font, okX + okW + 3, by, "Cancel", true, HudStyle.CYAN);
        // Een groep hernoemen: je diagrammen noemen hem bij zijn naam (card.in_group("...")).
        if (input == Input.RENAME && !box.getValue().strip().equalsIgnoreCase(selected.group())) {
            int tx = okX + okW + cancelW + 10;
            g.drawString(font, OsDraw.fitRight(font, "Change it in your diagrams too.", a.x() + a.w() - tx), tx, by + 2,
                    HudStyle.argb(255, HudStyle.ORANGE), false);
        }
    }

    // ================================================================ Muis

    void mouseClicked(Font font, SecurityOsScreen.Area a, int mx, int my) {
        Map<String, String> parents = parents();
        List<CardRow> all = allCards();
        Row row = selection(parents);
        // Het naamvak onderaan (een nieuwe groep, of een nieuwe naam).
        if (input != Input.OFF && nameBox != null && my >= bottomY(a) && my < bottomY(a) + OsDraw.BUTTON_H) {
            int okX = nameBox.getX() + nameBox.getWidth() + 6;
            int okW = OsDraw.buttonWidth(font, "OK");
            int cancelW = OsDraw.buttonWidth(font, "Cancel");
            if (mx >= okX && mx < okX + okW) {
                submit();
            } else if (mx >= okX + okW + 3 && mx < okX + okW + 3 + cancelW) {
                input = Input.OFF;
            } else {
                nameBox.mouseClicked(mx, my, 0);
            }
            return;
        }
        // Het vak met de naam van een nieuwe kaart.
        int rowY = writeY(a);
        EditBox box = labelBox();
        boolean inBox = OsDraw.inside(mx, my, fieldX(font, a), rowY, fieldW(), OsDraw.BUTTON_H);
        box.setFocused(inBox);
        if (inBox) {
            input = Input.OFF;
            box.mouseClicked(mx, my, 0);
            return;
        }
        int swatch = swatchAt(font, a, mx, my);
        if (swatch > 0) {
            colour = swatch;
            return;
        }
        if (OsDraw.inside(mx, my, writeX(font, a), rowY, OsDraw.buttonWidth(font, "Write card"), OsDraw.BUTTON_H)) {
            write();
            return;
        }
        // De knoppen van de groepen, onderaan links.
        if (input == Input.OFF && my >= bottomY(a) && my < bottomY(a) + OsDraw.BUTTON_H) {
            int newW = OsDraw.buttonWidth(font, "New");
            int renameW = OsDraw.buttonWidth(font, "Rename");
            int deleteW = OsDraw.buttonWidth(font, deleteGroupLabel(row));
            if (OsDraw.inside(mx, my, a.x(), bottomY(a), newW, OsDraw.BUTTON_H)) {
                if (parents.size() < LogicStore.MAX_GROUPS) {
                    startInput(Input.NEW_GROUP, "");
                }
            } else if (OsDraw.inside(mx, my, a.x() + newW + 3, bottomY(a), renameW, OsDraw.BUTTON_H)) {
                if (row.kind() == Kind.GROUP) {
                    startInput(Input.RENAME, row.group());
                }
            } else if (OsDraw.inside(mx, my, a.x() + newW + renameW + 6, bottomY(a), deleteW, OsDraw.BUTTON_H)) {
                if (row.kind() == Kind.GROUP && confirmGroup(row.group())) {
                    CompoundTag args = new CompoundTag();
                    args.putString("group", row.group());
                    screen.send("cgroup_delete", args);
                    selected = ALL;
                }
            }
            return;
        }
        // Links een regel kiezen (en misschien begint daar een sleep van een groep).
        Row clicked = rowAt(a, rows(parents), mx, my);
        if (clicked != null) {
            input = Input.OFF;
            if (!clicked.same(row)) {
                cardScroll = 0;
            }
            selected = clicked;
            pendingDelete = NOTHING;
            pressedGroup = clicked.kind() == Kind.GROUP ? clicked.group() : null;
            pressedCard = -1;
            pressX = mx;
            pressY = my;
            dragging = false;
            return;
        }
        List<CardRow> cards = cardsOf(row, all);
        // Rechtsboven: alle geblokkeerde kaarten weggooien.
        long blocked = blockedCount(cards);
        if (row.kind() == Kind.ALL && blocked > 0) {
            String label = deleteBlockedLabel(blocked);
            int w = OsDraw.buttonWidth(font, label);
            if (OsDraw.inside(mx, my, cardsX(a) + cardsW(a) - w, listTop(a) - 14, w, OsDraw.BUTTON_H)) {
                if (confirm(ALL_BLOCKED)) {
                    screen.send("card_delete_blocked", new CompoundTag());
                }
                return;
            }
        }
        // Een kaart: een van zijn knoppen, of misschien begint hier een sleep.
        CardRow card = cardAt(a, cards, mx, my);
        if (card != null) {
            int index = cards.indexOf(card) - cardScroll;
            int y = listTop(a) + 2 + index * CARD_ROW;
            int bx = cardsX(a) + cardsW(a) - 3;
            for (String label : cardButtons(card, row)) {
                int bw = OsDraw.buttonWidth(font, label);
                bx -= bw;
                if (OsDraw.inside(mx, my, bx, y - 1, bw, OsDraw.BUTTON_H)) {
                    cardButton(card, label, row);
                    return;
                }
                bx -= 3;
            }
            pressedCard = card.number();
            pressedGroup = null;
            pressX = mx;
            pressY = my;
            dragging = false;
        }
        pendingDelete = NOTHING; // ergens anders geklikt: de vraag vervalt
    }

    /** Een knop van een kaart: Block, Unblock, Delete (eerst "Sure?") of Remove (uit de gekozen groep). */
    private void cardButton(CardRow card, String label, Row row) {
        CompoundTag args = new CompoundTag();
        args.putInt("n", card.number());
        switch (label) {
            case "Block", "Unblock" -> {
                args.putBoolean("on", !card.blocked());
                screen.send("card_block", args);
                pendingDelete = NOTHING;
            }
            case "Remove" -> {
                args.putString("group", row.group());
                screen.send("card_group_remove", args);
            }
            default -> {
                if (confirm(card.number())) {
                    screen.send("card_delete", args);
                }
            }
        }
    }

    boolean mouseDragged(int mx, int my) {
        boolean pressed = pressedCard >= 0 || pressedGroup != null;
        if (pressed && !dragging && Math.abs(mx - pressX) + Math.abs(my - pressY) > 4) {
            dragging = true;
        }
        return pressed;
    }

    /** Losgelaten: lag je een kaart of groep op een groep (of een groep op "All cards")? Dan gaat hij erin. */
    void mouseReleased(SecurityOsScreen.Area a, int mx, int my) {
        int card = pressedCard;
        String group = pressedGroup;
        boolean wasDragging = dragging;
        Map<String, String> parents = parents();
        Row target = rowAt(a, rows(parents), mx, my);
        boolean ok = wasDragging && canDrop(parents, allCards(), target);
        pressedCard = -1;
        pressedGroup = null;
        dragging = false;
        if (!wasDragging || target == null) {
            return;
        }
        if (!ok) {
            if (group != null && target.kind() == Kind.GROUP && isInside(parents, target.group(), group)
                    && !target.group().equalsIgnoreCase(group)) {
                screen.showNotice("'" + group + "' can't go inside '" + target.group() + "': that would make a loop", true);
            }
            return;
        }
        CompoundTag args = new CompoundTag();
        if (card >= 0) {
            args.putInt("n", card);
            args.putString("group", target.group());
            screen.send("card_group_add", args);
        } else if (group != null) {
            args.putString("group", group);
            args.putString("parent", target.kind() == Kind.ALL ? "" : target.group());
            screen.send("cgroup_move", args);
        }
    }

    /** Scrollen: de lijst onder de muis (de groepen links, of de kaarten rechts). */
    void mouseScrolled(SecurityOsScreen.Area a, int mx, double scrollY) {
        int step = (int) Math.signum(scrollY);
        if (mx < cardsX(a)) {
            groupScroll = Math.max(0, groupScroll - step);
        } else {
            cardScroll = Math.max(0, cardScroll - step);
        }
    }

    // ================================================================ Typen

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

    /** Het vakje voor de naam van een groep: geen aanhalingstekens of \ (die horen bij je code). */
    private EditBox nameBox() {
        if (nameBox == null) {
            nameBox = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 10, Component.literal("Group name"));
            nameBox.setBordered(false);
            nameBox.setMaxLength(LogicStore.MAX_GROUP_NAME);
            nameBox.setFilter(value -> value.chars().noneMatch(c -> c == '"' || c == '\'' || c == '\\' || c == '§'));
            nameBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
        }
        return nameBox;
    }

    private void startInput(Input kind, String value) {
        labelBox().setFocused(false);
        EditBox box = nameBox();
        box.setValue(value);
        box.moveCursorToEnd(false);
        box.setHighlightPos(0);
        box.setFocused(true);
        input = kind;
    }

    /** OK of Enter: de nieuwe groep maken (in de gekozen groep, of bovenaan), of de gekozen groep hernoemen. */
    private void submit() {
        Input kind = input;
        input = Input.OFF;
        String text = nameBox().getValue().strip();
        if (text.isEmpty()) {
            return;
        }
        CompoundTag args = new CompoundTag();
        Row row = selection(parents());
        if (kind == Input.NEW_GROUP) {
            args.putString("name", text);
            args.putString("parent", row.kind() == Kind.GROUP ? row.group() : "");
            screen.send("cgroup_new", args);
            selected = new Row(Kind.GROUP, text, 1); // de nieuwe groep is meteen gekozen (zodra de server hem stuurt)
        } else if (kind == Input.RENAME && row.kind() == Kind.GROUP) {
            args.putString("group", row.group());
            args.putString("name", text);
            screen.send("cgroup_rename", args);
            selected = new Row(Kind.GROUP, text, row.depth());
        }
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (input != Input.OFF) {
            switch (keyCode) {
                case 257, 335 -> submit(); // enter
                case 256 -> input = Input.OFF; // esc: alleen uit het vak
                default -> nameBox().keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
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
        if (input != Input.OFF) {
            nameBox().charTyped(codePoint, modifiers);
            return true;
        }
        EditBox box = labelBox();
        if (!box.isFocused()) {
            return false;
        }
        box.charTyped(codePoint, modifiers);
        return true;
    }

    /** Een ander venster kwam bovenop, of dit venster ging dicht: stop met typen en slepen. */
    void blur() {
        if (labelBox != null) {
            labelBox.setFocused(false);
        }
        input = Input.OFF;
        pressedCard = -1;
        pressedGroup = null;
        dragging = false;
    }

    /** Write card: de naam, de kleur, en de groep die links gekozen is ("" = geen). */
    private void write() {
        CompoundTag args = new CompoundTag();
        args.putString("label", labelBox().getValue().strip());
        args.putInt("colour", colour);
        Row row = selection(parents());
        args.putString("group", row.kind() == Kind.GROUP ? row.group() : "");
        screen.send("card_write", args);
        labelBox().setValue("");
        labelBox().setFocused(false);
    }

    // ================================================================ Voor de screenshot-rondleiding

    /** De vraag "Sure?" bij de knop Delete blocked. */
    void tourAskDeleteBlocked() {
        selected = ALL;
        input = Input.OFF;
        confirm(ALL_BLOCKED);
    }

    /** Een naam en kleur alvast ingevuld. */
    void tourFill(String label, int colour) {
        labelBox().setValue(label);
        this.colour = KeycardData.clampColour(colour);
    }

    /** Links deze groep kiezen (null = All cards). */
    void tourSelect(@Nullable String group) {
        selected = group == null ? ALL : new Row(Kind.GROUP, group, 1);
        cardScroll = 0;
    }

    /** Onderaan het naamvak open, voor een nieuwe groep of (met {@code rename}) een nieuwe naam, met deze tekst. */
    void tourInput(boolean rename, String text) {
        startInput(rename ? Input.RENAME : Input.NEW_GROUP, text);
    }
}
