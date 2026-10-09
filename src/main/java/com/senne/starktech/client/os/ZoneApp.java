package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * De app Zone op de Stark OS-desktop (hoofdstuk 10, 8 oktober): de kubussen waarin alleen jij en je
 * vrienden mogen bouwen en breken, "tot op elk blok" in te stellen.
 *
 * <pre>
 *  PROTECTED                         1 840 blocks · power 20 + 1 / 2 s
 *  +--------------------+  Box 2 of 3  [<] [>]            [New box]
 *  |   kaart van boven  |  From  x [ 162] y [ -61] z [  62]
 *  |   (het gebied van  |  To    x [ 171] y [ -54] z [  69]
 *  |    je kern, 61x61) |  Size  10 x 8 x 8 (640 blocks)
 *  |   met je kubussen  |  West  [-][+]     East  [-][+]
 *  |                    |  Down  [-][+]     Up    [-][+]
 *  |                    |  North [-][+]     South [-][+]
 *  +--------------------+  [Delete box]
 * </pre>
 *
 * De kaart is het gebied van je kern van bovenaf (noord boven): je kubussen in blauw, de gekozen
 * kubus wit, de kern als wit blokje en jij als geel stipje (niet via SSH). Klik op een kubus om hem
 * te kiezen. Een + schuift die kant een blok naar buiten (groter), een − naar binnen (kleiner). De
 * getallen kun je ook typen: Enter bewaart ze, Esc niet. Alles gaat naar de server, die het passend
 * maakt in het gebied van je kern (zie security/zone/ZoneApp).
 */
final class ZoneApp {
    private static final int ROW = 15;
    private static final long CONFIRM_MILLIS = 4000;
    /** De kanten zoals de server ze nummert: 0 onder, 1 boven, 2 noord, 3 zuid, 4 west, 5 oost. */
    private static final String[] SIDE_NAMES = {"Down", "Up", "North", "South", "West", "East"};
    /** Welke kanten naast elkaar staan: west en oost, onder en boven, noord en zuid. */
    private static final int[][] SIDE_ROWS = {{4, 5}, {0, 1}, {2, 3}};
    private static final String[] AXES = {"x", "y", "z"};

    /** Een knop op het scherm (bij het tekenen onthouden, zodat een klik weet wat hij raakte). */
    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final StarkOsScreen screen;
    private final List<Hit> hits = new ArrayList<>();
    /** De zes getallen die je kunt typen: van x, y, z en tot x, y, z. */
    private final EditBox[] fields = new EditBox[6];
    /** Waar elk vakje stond bij het tekenen (x, y, breedte), voor de muis. */
    private final int[][] fieldAt = new int[6][3];
    /** Welke kubus gekozen is (0 = de eerste). */
    private int selected;
    /** Hoeveel kubussen er de vorige keer waren (kwam er een bij, dan kiezen we die meteen). */
    private int lastCount = -1;
    /** Welke kubus en getallen nu in de vakjes staan; verandert dat, dan vullen we ze opnieuw. */
    private int shownIndex = -1;
    private int @Nullable [] shown;
    /** De knop die "Sure?" vraagt, en tot wanneer. */
    private String asking = "";
    private long askingUntil;
    /** Waar de kaart stond bij het tekenen, en welk stuk wereld hij toont (voor een klik erop). */
    private int mapX;
    private int mapY;
    private int mapSize;
    private int lowX;
    private int lowZ;
    private float scale = 1;

    ZoneApp(StarkOsScreen screen) {
        this.screen = screen;
    }

    private CompoundTag zone() {
        return screen.data().getCompound("zone");
    }

    /** De kubussen uit de momentopname: elk zes getallen (van x, y, z, tot x, y, z). */
    private static List<int[]> boxes(CompoundTag zone) {
        List<int[]> boxes = new ArrayList<>();
        for (Tag tag : zone.getList("boxes", Tag.TAG_INT_ARRAY)) {
            int[] numbers = ((IntArrayTag) tag).getAsIntArray();
            if (numbers.length == 6) {
                boxes.add(numbers);
            }
        }
        return boxes;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my, float partialTick) {
        hits.clear();
        CompoundTag zone = zone();
        List<int[]> boxes = boxes(zone);
        if (lastCount >= 0 && boxes.size() > lastCount) {
            selected = boxes.size() - 1; // net toegevoegd: die wil je nu bijstellen
        }
        lastCount = boxes.size();
        selected = Math.max(0, Math.min(selected, boxes.size() - 1));

        // Bovenaan: beschermt hij, hoe groot, en hoeveel stroom.
        String status;
        int colour;
        if (boxes.isEmpty()) {
            status = "NO ZONE YET";
            colour = OsDraw.GREY;
        } else if (zone.getBoolean("active")) {
            status = "PROTECTED";
            colour = HudStyle.GREEN;
        } else {
            status = "NO POWER: anyone can build here";
            colour = HudStyle.RED;
        }
        g.drawString(font, status, a.x(), a.y() + 1, HudStyle.argb(255, colour), false);
        String info = String.format("%,d blocks · power %d + %d / 2 s", zone.getLong("volume"),
                zone.getInt("coreUpkeep"), zone.getInt("upkeep"));
        g.drawString(font, info, a.x() + a.w() - font.width(info), a.y() + 1, HudStyle.argb(255, OsDraw.GREY), false);

        // Links de kaart.
        int top = a.y() + 13;
        mapSize = Math.max(60, Math.min(a.h() - 14, a.w() / 2 - 24));
        mapX = a.x();
        mapY = top;
        renderMap(g, font, zone, boxes);

        // Rechts de gekozen kubus.
        int px = a.x() + mapSize + 8;
        int right = a.x() + a.w();
        int y = top;
        String add = "New box";
        int addW = OsDraw.buttonWidth(font, add);
        button(g, font, right - addW, y, add, boxes.size() < zone.getInt("max"), HudStyle.CYAN,
                () -> screen.send("zone_new", new CompoundTag()));
        if (boxes.isEmpty()) {
            g.drawString(font, "No boxes yet.", px, y + 2, HudStyle.argb(255, HudStyle.WHITE), false);
            hint(g, font, "Press New box, or click two corners", px, y + ROW + 2);
            hint(g, font, "with a Zone Marker in the world", px, y + 2 * ROW + 2);
            hint(g, font, "(sneak + right-click adds the box).", px, y + 3 * ROW + 2);
            return;
        }
        String which = "Box " + (selected + 1) + " of " + boxes.size();
        g.drawString(font, which, px, y + 2, HudStyle.argb(255, HudStyle.CYAN), false);
        int bx = px + font.width(which) + 5;
        bx += button(g, font, bx, y, "<", boxes.size() > 1, HudStyle.CYAN, () -> select(selected - 1, boxes.size())) + 2;
        button(g, font, bx, y, ">", boxes.size() > 1, HudStyle.CYAN, () -> select(selected + 1, boxes.size()));

        int[] box = boxes.get(selected);
        fill(box);
        y += ROW + 1;
        numberRow(g, font, "From", 0, px, y, mx, my, partialTick);
        y += ROW;
        numberRow(g, font, "To", 3, px, y, mx, my, partialTick);
        y += ROW;
        int sx = box[3] - box[0] + 1;
        int sy = box[4] - box[1] + 1;
        int sz = box[5] - box[2] + 1;
        g.drawString(font, "Size", px, y + 2, HudStyle.argb(255, OsDraw.GREY), false);
        g.drawString(font, sx + " × " + sy + " × " + sz + "  (" + String.format("%,d", (long) sx * sy * sz) + " blocks)",
                px + 30, y + 2, HudStyle.argb(255, HudStyle.WHITE), false);

        // Per kant een - en een +.
        y += ROW;
        int half = (right - px) / 2;
        for (int[] pair : SIDE_ROWS) {
            for (int k = 0; k < 2; k++) {
                int side = pair[k];
                int cx = px + k * half;
                g.drawString(font, SIDE_NAMES[side], cx, y + 2, HudStyle.argb(255, OsDraw.GREY), false);
                int gx = cx + 32;
                gx += button(g, font, gx, y, "-", true, HudStyle.ORANGE, () -> grow(side, -1)) + 2;
                button(g, font, gx, y, "+", true, HudStyle.GREEN, () -> grow(side, 1));
            }
            y += ROW;
        }
        hint(g, font, "+ moves a side 1 block out, - in.", px, y + 1);
        hint(g, font, "Or type the numbers and press Enter.", px, y + 11);
        y += 23;
        String delete = asking("delete:" + selected) ? "Sure?" : "Delete box";
        button(g, font, px, y, delete, true, HudStyle.RED, () -> {
            if (confirm("delete:" + selected)) {
                CompoundTag args = new CompoundTag();
                args.putInt("i", selected);
                screen.send("zone_delete", args);
            }
        });
    }

    /** De kaart van bovenaf: het gebied van de kern, de kubussen, de kern en jij. */
    private void renderMap(GuiGraphics g, Font font, CompoundTag zone, List<int[]> boxes) {
        int[] low = zone.getIntArray("low");
        int[] high = zone.getIntArray("high");
        OsDraw.field(g, mapX, mapY, mapX + mapSize, mapY + mapSize, false);
        if (low.length != 3 || high.length != 3) {
            return;
        }
        lowX = low[0];
        lowZ = low[2];
        int span = Math.max(high[0] - low[0], high[2] - low[2]) + 1;
        scale = (mapSize - 4) / (float) span;
        // Een fijn raster om de 10 blokken.
        for (int i = 10; i < span; i += 10) {
            int p = mapX + 2 + Math.round(i * scale);
            g.fill(p, mapY + 2, p + 1, mapY + mapSize - 2, HudStyle.argb(40, HudStyle.CYAN));
            int q = mapY + 2 + Math.round(i * scale);
            g.fill(mapX + 2, q, mapX + mapSize - 2, q + 1, HudStyle.argb(40, HudStyle.CYAN));
        }
        for (int i = 0; i < boxes.size(); i++) {
            int[] box = boxes.get(i);
            boolean chosen = i == selected;
            int x0 = mapPx(box[0]);
            int x1 = Math.max(x0 + 1, mapPx(box[3] + 1));
            int z0 = mapPz(box[2]);
            int z1 = Math.max(z0 + 1, mapPz(box[5] + 1));
            g.fill(x0, z0, x1, z1, HudStyle.argb(chosen ? 110 : 60, HudStyle.CYAN));
            int edge = HudStyle.argb(chosen ? 255 : 200, chosen ? HudStyle.WHITE : HudStyle.CYAN);
            g.fill(x0, z0, x1, z0 + 1, edge);
            g.fill(x0, z1 - 1, x1, z1, edge);
            g.fill(x0, z0, x0 + 1, z1, edge);
            g.fill(x1 - 1, z0, x1, z1, edge);
            if (x1 - x0 >= 8 && z1 - z0 >= 9) {
                HudStyle.smallText(g, font, String.valueOf(i + 1), x0 + 4, z0 + 2, edge);
            }
        }
        int[] core = zone.getIntArray("core");
        if (core.length == 3) {
            int cx = mapPx(core[0]);
            int cz = mapPz(core[2]);
            g.fill(cx - 1, cz - 1, cx + 2, cz + 2, HudStyle.argb(255, HudStyle.WHITE));
        }
        int[] player = zone.getIntArray("player");
        if (player.length == 3) {
            int px = mapPx(player[0]);
            int pz = mapPz(player[2]);
            g.fill(px - 1, pz - 1, px + 2, pz + 2, HudStyle.argb(255, 0xFFE14A));
        }
    }

    private int mapPx(int worldX) {
        return mapX + 2 + Math.round((worldX - lowX) * scale);
    }

    private int mapPz(int worldZ) {
        return mapY + 2 + Math.round((worldZ - lowZ) * scale);
    }

    /** Een rij met drie getallen (van of tot): x, y en z, elk in een vakje dat je kunt typen. */
    private void numberRow(GuiGraphics g, Font font, String label, int first, int x, int y, int mx, int my, float partialTick) {
        g.drawString(font, label, x, y + 2, HudStyle.argb(255, OsDraw.GREY), false);
        int fx = x + 30;
        for (int k = 0; k < 3; k++) {
            g.drawString(font, AXES[k], fx, y + 2, HudStyle.argb(255, OsDraw.GREY), false);
            int boxX = fx + 7;
            int w = 38;
            EditBox box = field(font, first + k);
            OsDraw.field(g, boxX, y, boxX + w, y + OsDraw.BUTTON_H, box.isFocused());
            box.setX(boxX + 3);
            box.setY(y + 2);
            box.setWidth(w - 6);
            box.render(g, mx, my, partialTick);
            fieldAt[first + k] = new int[] {boxX, y, w};
            fx += w + 12;
        }
    }

    private EditBox field(Font font, int index) {
        if (fields[index] == null) {
            EditBox box = new EditBox(font, 0, 0, 32, 10, Component.literal(AXES[index % 3]));
            box.setBordered(false);
            box.setMaxLength(9);
            box.setFilter(value -> value.isEmpty() || value.matches("-?\\d*"));
            box.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
            fields[index] = box;
        }
        return fields[index];
    }

    /**
     * Zet de getallen van deze kubus in de vakjes, als dat een andere kubus is dan er stond of de
     * server hem veranderde (maar niet terwijl je aan het typen bent).
     */
    private void fill(int[] box) {
        boolean otherBox = shownIndex != selected;
        if (!otherBox && Arrays.equals(shown, box)) {
            return;
        }
        if (!otherBox && typing()) {
            return;
        }
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        for (int i = 0; i < 6; i++) {
            EditBox field = field(font, i);
            field.setValue(String.valueOf(box[i]));
            field.setFocused(false);
        }
        shown = box.clone();
        shownIndex = selected;
    }

    private boolean typing() {
        for (EditBox field : fields) {
            if (field != null && field.isFocused()) {
                return true;
            }
        }
        return false;
    }

    private static void hint(GuiGraphics g, Font font, String text, int x, int y) {
        g.drawString(font, text, x, y, HudStyle.argb(255, OsDraw.GREY), false);
    }

    /** Een knop tekenen en onthouden waar hij staat; geeft zijn breedte terug. Een grijze knop doet niets. */
    private int button(GuiGraphics g, Font font, int x, int y, String text, boolean enabled, int colour, Runnable action) {
        int w = OsDraw.button(g, font, x, y, text, enabled, colour);
        if (enabled) {
            hits.add(new Hit(x, y, w, OsDraw.BUTTON_H, action));
        }
        return w;
    }

    // ================================================================ Wat de knoppen doen

    private void select(int index, int count) {
        if (count > 0) {
            selected = Math.floorMod(index, count);
            blur();
        }
    }

    private void grow(int side, int by) {
        CompoundTag args = new CompoundTag();
        args.putInt("i", selected);
        args.putInt("side", side);
        args.putInt("by", by);
        screen.send("zone_grow", args);
    }

    /** De getypte getallen naar de server (die maakt ze passend in het gebied van je kern). */
    private void submit() {
        int[] numbers = new int[6];
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        for (int i = 0; i < 6; i++) {
            try {
                numbers[i] = Integer.parseInt(field(font, i).getValue());
            } catch (NumberFormatException e) {
                screen.showNotice("Type a whole number in every field", true);
                return;
            }
        }
        CompoundTag args = new CompoundTag();
        args.putInt("i", selected);
        args.putIntArray("box", numbers);
        screen.send("zone_set", args);
        blur();
        shown = null; // straks opnieuw vullen met wat de server ervan maakte
    }

    private boolean asking(String key) {
        return asking.equals(key) && Util.getMillis() < askingUntil;
    }

    /** De eerste klik vraagt "Sure?" (4 seconden lang), de tweede doet het echt. */
    private boolean confirm(String key) {
        if (asking(key)) {
            asking = "";
            return true;
        }
        asking = key;
        askingUntil = Util.getMillis() + CONFIRM_MILLIS;
        return false;
    }

    // ================================================================ Muis en toetsen

    void mouseClicked(Font font, StarkOsScreen.Area a, int mx, int my) {
        // Een getal om te typen?
        for (int i = 0; i < 6; i++) {
            int[] at = fieldAt[i];
            if (fields[i] != null && at[2] > 0 && OsDraw.inside(mx, my, at[0], at[1], at[2], OsDraw.BUTTON_H)) {
                for (int j = 0; j < 6; j++) {
                    if (fields[j] != null) {
                        fields[j].setFocused(j == i);
                    }
                }
                fields[i].mouseClicked(mx, my, 0);
                return;
            }
        }
        if (typing()) {
            blur();
            shown = null; // half getypt en weggeklikt: de echte getallen terug
        }
        for (Hit hit : List.copyOf(hits)) {
            if (OsDraw.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                hit.action().run();
                return;
            }
        }
        // Op de kaart: de kubus kiezen waar je op klikt (liggen er twee over elkaar, dan de laatste).
        if (OsDraw.inside(mx, my, mapX, mapY, mapSize, mapSize) && scale > 0) {
            int worldX = lowX + (int) Math.floor((mx - mapX - 2) / scale);
            int worldZ = lowZ + (int) Math.floor((my - mapY - 2) / scale);
            List<int[]> boxes = boxes(zone());
            for (int i = boxes.size() - 1; i >= 0; i--) {
                int[] box = boxes.get(i);
                if (worldX >= box[0] && worldX <= box[3] && worldZ >= box[2] && worldZ <= box[5]) {
                    select(i, boxes.size());
                    return;
                }
            }
        }
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int focused = -1;
        for (int i = 0; i < 6; i++) {
            if (fields[i] != null && fields[i].isFocused()) {
                focused = i;
            }
        }
        if (focused < 0) {
            return false;
        }
        if (keyCode == 257 || keyCode == 335) { // enter
            submit();
        } else if (keyCode == 256) { // esc: alleen uit het vakje, en de echte getallen terug
            blur();
            shown = null;
        } else if (keyCode == 258) { // tab: naar het volgende vakje
            fields[focused].setFocused(false);
            Font font = net.minecraft.client.Minecraft.getInstance().font;
            field(font, (focused + 1) % 6).setFocused(true);
        } else {
            fields[focused].keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        for (EditBox field : fields) {
            if (field != null && field.isFocused()) {
                field.charTyped(codePoint, modifiers);
                return true;
            }
        }
        return false;
    }

    void blur() {
        for (EditBox field : fields) {
            if (field != null) {
                field.setFocused(false);
            }
        }
    }

    /** Alleen voor de screenshot-rondleiding: deze kubus kiezen. */
    void tourSelect(int index) {
        selected = index;
        blur();
    }
}
