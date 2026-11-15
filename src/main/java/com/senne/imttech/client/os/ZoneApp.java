package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
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
 * De app Zone op de Security OS-desktop (hoofdstuk 10, 8 oktober): de kubussen waarin alleen jij en je
 * vrienden mogen bouwen en breken, "tot op elk blok" in te stellen.
 *
 * <pre>
 *  PROTECTED                         1 840 blocks · power 20 + 1 / 2 s
 *  +--------------------+  Box 2 of 3  [<] [>]            [New box]
 *  |   kaart van boven  |  From  x [ 162] y [ -61] z [  62]
 *  |   (je kern en je   |  To    x [ 171] y [ -54] z [  69]
 *  |    kubussen)       |  Size  10 x 8 x 8 (640 blocks)
 *  |   met je kubussen  |  West  [-][+]     East  [-][+]
 *  |                    |  Down  [-][+]     Up    [-][+]
 *  |                    |  North [-][+]     South [-][+]
 *  +--------------------+  [Delete box]
 * </pre>
 *
 * De kaart toont je kern van bovenaf (noord boven): je kubussen in blauw, de gekozen kubus wit, de
 * kern als wit blokje en jij als geel stipje (niet via SSH). Hij zoomt zelf: minstens 30 blokken om
 * de kern, en groot genoeg voor al je kubussen (een zone mag tot 200 blokken van de kern; die grens
 * is een rode lijn als hij op de kaart staat). Klik op een kubus om hem te kiezen. Een + schuift die
 * kant een blok naar buiten (groter), een − naar binnen (kleiner). De getallen kun je ook typen:
 * Enter bewaart ze, Esc niet. Alles gaat naar de server, die het passend maakt (zie
 * security/zone/ZoneApp).
 */
final class ZoneApp {
    private static final int ROW = 15;
    private static final long CONFIRM_MILLIS = 4000;
    /** De kanten zoals de server ze nummert: 0 onder, 1 boven, 2 noord, 3 zuid, 4 west, 5 oost. */
    private static final String[] SIDE_NAMES = {"Down", "Up", "North", "South", "West", "East"};
    /** Welke kanten naast elkaar staan: west en oost, onder en boven, noord en zuid. */
    private static final int[][] SIDE_ROWS = {{4, 5}, {0, 1}, {2, 3}};
    private static final String[] AXES = {"x", "y", "z"};
    /** De kaart toont minstens zoveel blokken om de kern heen (zoals het gebied van de kern). */
    private static final int MIN_VIEW = 30;
    /** Zoveel blokken rand om je kubussen (en jou) op de kaart. */
    private static final int MARGIN = 5;

    /** Een knop op het scherm (bij het tekenen onthouden, zodat een klik weet wat hij raakte). */
    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final SecurityOsScreen screen;
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
    /** Hoofdstuk 116: het verslag van de Autoscan dat je wegklikte (het volgnummer; een nieuw verslag staat er weer). */
    private long dismissedReport = -1;
    /** Waar de kaart stond bij het tekenen, en welk stuk wereld hij toont (voor een klik erop). */
    private int mapX;
    private int mapY;
    private int mapSize;
    private int lowX;
    private int lowZ;
    private float scale = 1;

    ZoneApp(SecurityOsScreen screen) {
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

    void render(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
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
        com.senne.imttech.client.PowerUnit unit = com.senne.imttech.client.PowerUnit.current();
        String info = String.format("%,d blocks · power %s + %s", zone.getLong("volume"),
                com.senne.imttech.config.Numbers.amount(zone.getDouble("coreUpkeep") / 40.0 * unit.ticks()),
                unit.rate(zone.getInt("upkeep") / 40.0).getString());
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
        // De Autoscan (hoofdstuk 45): de kamers rond de kern (of rond jou) plus de muur, of een doos om het gebouw.
        String scan = "Autoscan";
        int scanW = OsDraw.buttonWidth(font, scan);
        int scanX = right - addW - 4 - scanW;
        button(g, font, scanX, y, scan, true, HudStyle.GREEN, () -> screen.send("zone_scan", new CompoundTag()));
        CompoundTag report = report(zone);
        if (boxes.isEmpty()) {
            g.drawString(font, "No boxes yet.", px, y + 2, HudStyle.argb(255, HudStyle.WHITE), false);
            // De uitleg, afgebroken zodat hij in het venster past (hoofdstuk 116: hij liep er rechts een stukje uit).
            int hy = y + ROW + 2;
            String help = "Autoscan: the closed rooms around the core (or you) + " + zone.getInt("margin")
                    + " blocks of wall; an open base gets a box. Or New box, or two corners with a Zone Marker (sneak +"
                    + " right-click adds it; without corners sneak + right-click scans that room).";
            for (String line : OsDraw.wrap(font, help, right - px, 7)) {
                hint(g, font, line, px, hy);
                hy += 10;
            }
            for (String line : OsDraw.wrap(font, "Reach: " + zone.getInt("reach") + " blocks from the core.", right - px, 2)) {
                hint(g, font, line, px, hy + 4);
                hy += 10;
            }
            // Hoofdstuk 116: wat je laatste Autoscan zag, onder de uitleg, met de knoppen eronder.
            int ry = hy + 12;
            if (report != null) {
                ry += renderReport(g, font, report, px, right, ry) + 1;
            }
            reportButtons(g, font, zone, report, px, ry);
            return;
        }
        String which = "Box " + (selected + 1) + " of " + boxes.size();
        // Past het niet voor de knop Autoscan (box 10 of meer), dan korter: anders lag ">" over Autoscan heen.
        int arrows = OsDraw.buttonWidth(font, "<") + 2 + OsDraw.buttonWidth(font, ">");
        if (px + font.width(which) + 5 + arrows > scanX - 4) {
            which = (selected + 1) + "/" + boxes.size();
        }
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
        // Hoofdstuk 116: heeft je laatste Autoscan iets te melden, dan staat dat hier in plaats van de uitleg over + en -.
        if (report == null) {
            hint(g, font, "+ moves a side 1 block out, - in.", px, y + 1);
            hint(g, font, "Or type the numbers and press Enter.", px, y + 11);
            y += 23;
        } else {
            y += 2 + renderReport(g, font, report, px, right, y + 1);
        }
        String delete = asking("delete:" + selected) ? "Sure?" : "Delete box";
        int deleteW = button(g, font, px, y, delete, true, HudStyle.RED, () -> {
            if (confirm("delete:" + selected)) {
                CompoundTag args = new CompoundTag();
                args.putInt("i", selected);
                screen.send("zone_delete", args);
            }
        });
        reportButtons(g, font, zone, report, px + deleteW + 4, y);
    }

    // ================================================================ Wat de Autoscan zag (hoofdstuk 116)

    /** Het verslag van je laatste Autoscan (van de server), of null als er geen is of je het wegklikte. */
    private @Nullable CompoundTag report(CompoundTag zone) {
        if (!zone.contains("scan")) {
            return null;
        }
        CompoundTag scan = zone.getCompound("scan");
        return scan.getLong("id") == dismissedReport ? null : scan;
    }

    /** De pings uit het verslag: elk vier getallen (x, y, z, en wat voor ping, zie ZoneScan.Kind). */
    private static List<int[]> marks(@Nullable CompoundTag report) {
        List<int[]> marks = new ArrayList<>();
        if (report != null) {
            for (Tag tag : report.getList("marks", Tag.TAG_INT_ARRAY)) {
                int[] numbers = ((IntArrayTag) tag).getAsIntArray();
                if (numbers.length == 4) {
                    marks.add(numbers);
                }
            }
        }
        return marks;
    }

    /** De kleur van een ping, net als in de wereld: roze = grot, rood = naar buiten, paars = de uitgang van een grot. */
    private static int markColour(int kind) {
        com.senne.imttech.security.zone.ZoneScan.Kind[] kinds = com.senne.imttech.security.zone.ZoneScan.Kind.values();
        return com.senne.imttech.client.zone.ZoneScanMarkRenderer.colour(kinds[Math.max(0, Math.min(kinds.length - 1, kind))]);
    }

    /**
     * Wat je laatste Autoscan zag (jouw notitie: de tekst van de Autoscan kwam buiten het venster): een kopje in de kleur
     * van de ping, daarnaast waar de opening is, en de uitleg over hooguit twee regels die altijd binnen het venster
     * blijven. Het kruisje rechts haalt het weg. Geeft terug hoe hoog het werd (de knoppen zet de app er zelf bij, zie
     * {@link #reportButtons}).
     */
    private int renderReport(GuiGraphics g, Font font, CompoundTag report, int px, int right, int y) {
        List<int[]> marks = marks(report);
        List<String> lines = OsDraw.wrap(font, report.getString("text"), right - px, 2);
        int colour = marks.isEmpty() ? HudStyle.ORANGE : markColour(marks.getFirst()[3]);
        String title = report.getString("title").isEmpty() ? "AUTOSCAN" : report.getString("title");
        int closeX = right - 8;
        g.drawString(font, OsDraw.fitRight(font, title, closeX - 4 - px), px, y, HudStyle.argb(255, colour), false);
        if (!marks.isEmpty()) {
            // Waar de (eerste) opening is, zonder komma's: zo past het ook met grote getallen naast het kopje.
            int[] first = marks.getFirst();
            int wx = px + font.width(title) + 6;
            String where = OsDraw.fitRight(font, first[0] + " " + first[1] + " " + first[2], closeX - 6 - wx);
            if (closeX - 6 - wx > 10) {
                g.drawString(font, where, wx, y, HudStyle.argb(255, OsDraw.GREY), false);
            }
        }
        g.drawString(font, "x", closeX + 1, y, HudStyle.argb(255, HudStyle.RED), false);
        long id = report.getLong("id");
        hits.add(new Hit(closeX - 1, y - 2, 9, 11, () -> dismissedReport = id));
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), px, y + 11 + i * 10, HudStyle.argb(255, HudStyle.WHITE), false);
        }
        return 11 + lines.size() * 10;
    }

    /**
     * De knoppen van de Autoscan op een rij vanaf {@code x}: "Include cave" (jouw wens: "bevestigen dat cave er bij moet":
     * dezelfde scan, met de grot erbij) of "Scan bigger" als het verslag dat aanbiedt, en Undo (je laatste Autoscan
     * terugdraaien) zolang dat kan. Naast "Delete box" is er net plaats voor beide.
     */
    private void reportButtons(GuiGraphics g, Font font, CompoundTag zone, @Nullable CompoundTag report, int x, int y) {
        String offer = report == null ? "" : report.getString("offer");
        if (!offer.isEmpty()) {
            x += button(g, font, x, y, offer, true, HudStyle.ORANGE, () -> {
                CompoundTag args = new CompoundTag();
                args.putBoolean("cave", true);
                screen.send("zone_scan", args);
            }) + 4;
        }
        if (zone.getBoolean("undo")) {
            button(g, font, x, y, offer.isEmpty() ? "Undo scan" : "Undo", true, HudStyle.CYAN,
                    () -> screen.send("zone_undo", new CompoundTag()));
        }
    }

    /** De kaart van bovenaf: de kern, de kubussen en jij (zie {@link #fitView} voor hoe ver hij kijkt). */
    private void renderMap(GuiGraphics g, Font font, CompoundTag zone, List<int[]> boxes) {
        int[] low = zone.getIntArray("low");
        int[] high = zone.getIntArray("high");
        int[] core = zone.getIntArray("core");
        OsDraw.field(g, mapX, mapY, mapX + mapSize, mapY + mapSize, false);
        if (low.length != 3 || high.length != 3 || core.length != 3) {
            return;
        }
        List<int[]> marks = marks(report(zone));
        int span = fitView(low, high, core, boxes, zone.getIntArray("player"), marks);
        scale = (mapSize - 4) / (float) span;
        g.enableScissor(mapX + 1, mapY + 1, mapX + mapSize - 1, mapY + mapSize - 1);
        // Een fijn raster: om de 10 blokken, of om de 50 als de kaart ver uitgezoomd is.
        int step = span <= 150 ? 10 : 50;
        for (int i = step; i < span; i += step) {
            int p = mapX + 2 + Math.round(i * scale);
            g.fill(p, mapY + 2, p + 1, mapY + mapSize - 2, HudStyle.argb(40, HudStyle.CYAN));
            int q = mapY + 2 + Math.round(i * scale);
            g.fill(mapX + 2, q, mapX + mapSize - 2, q + 1, HudStyle.argb(40, HudStyle.CYAN));
        }
        // De grens: verder dan dit mag de zone niet (een dunne rode lijn, alleen als hij op de kaart staat).
        int limit = HudStyle.argb(120, HudStyle.RED);
        int rx0 = mapPx(low[0]);
        int rx1 = mapPx(high[0] + 1);
        int rz0 = mapPz(low[2]);
        int rz1 = mapPz(high[2] + 1);
        g.fill(rx0, rz0, rx1, rz0 + 1, limit);
        g.fill(rx0, rz1 - 1, rx1, rz1, limit);
        g.fill(rx0, rz0, rx0 + 1, rz1, limit);
        g.fill(rx1 - 1, rz0, rx1, rz1, limit);
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
        // Hoofdstuk 116: de pings van de Autoscan als klein kruisje dat zacht klopt (waar je kamers openliggen).
        int beat = 150 + (int) (105 * Math.sin(Util.getMillis() / 160.0));
        for (int[] mark : marks) {
            int mx = mapPx(mark[0]) + Math.max(0, Math.round(scale / 2) - 1);
            int mz = mapPz(mark[2]) + Math.max(0, Math.round(scale / 2) - 1);
            int colour = HudStyle.argb(beat, markColour(mark[3]));
            for (int k = -2; k <= 2; k++) {
                g.fill(mx + k, mz + k, mx + k + 1, mz + k + 1, colour);
                g.fill(mx + k, mz - k, mx + k + 1, mz - k + 1, colour);
            }
        }
        int cx = mapPx(core[0]);
        int cz = mapPz(core[2]);
        g.fill(cx - 1, cz - 1, cx + 2, cz + 2, HudStyle.argb(255, HudStyle.WHITE));
        int[] player = zone.getIntArray("player");
        if (player.length == 3) {
            int px = mapPx(player[0]);
            int pz = mapPz(player[2]);
            g.fill(px - 1, pz - 1, px + 2, pz + 2, HudStyle.argb(255, 0xFFE14A));
        }
        g.disableScissor();
    }

    /**
     * Welk stuk wereld de kaart toont: minstens MIN_VIEW blokken om de kern, en al je kubussen en jij
     * met MARGIN blokken rand, maar nooit verder dan de zone mag komen ({@code low} tot {@code high}).
     * Zet {@link #lowX} en {@link #lowZ} (de hoek linksboven) en geeft terug hoeveel blokken breed hij is.
     */
    private int fitView(int[] low, int[] high, int[] core, List<int[]> boxes, int[] player, List<int[]> marks) {
        int x0 = core[0] - MIN_VIEW;
        int x1 = core[0] + MIN_VIEW;
        int z0 = core[2] - MIN_VIEW;
        int z1 = core[2] + MIN_VIEW;
        for (int[] box : boxes) {
            x0 = Math.min(x0, box[0] - MARGIN);
            x1 = Math.max(x1, box[3] + MARGIN);
            z0 = Math.min(z0, box[2] - MARGIN);
            z1 = Math.max(z1, box[5] + MARGIN);
        }
        // De pings van de Autoscan horen ook op de kaart (hoofdstuk 116).
        for (int[] mark : marks) {
            x0 = Math.min(x0, mark[0] - MARGIN);
            x1 = Math.max(x1, mark[0] + MARGIN);
            z0 = Math.min(z0, mark[2] - MARGIN);
            z1 = Math.max(z1, mark[2] + MARGIN);
        }
        if (player.length == 3) {
            x0 = Math.min(x0, player[0] - MARGIN);
            x1 = Math.max(x1, player[0] + MARGIN);
            z0 = Math.min(z0, player[2] - MARGIN);
            z1 = Math.max(z1, player[2] + MARGIN);
        }
        x0 = Math.max(x0, low[0]);
        x1 = Math.min(x1, high[0]);
        z0 = Math.max(z0, low[2]);
        z1 = Math.min(z1, high[2]);
        int span = Math.max(x1 - x0, z1 - z0) + 1;
        // Vierkant: de smalle kant komt in het midden.
        lowX = x0 - (span - (x1 - x0 + 1)) / 2;
        lowZ = z0 - (span - (z1 - z0 + 1)) / 2;
        return span;
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

    void mouseClicked(Font font, SecurityOsScreen.Area a, int mx, int my) {
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
