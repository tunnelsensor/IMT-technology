package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.logic.program.Checks;
import com.senne.starktech.logic.program.Expr;
import com.senne.starktech.logic.program.LogicException;
import com.senne.starktech.logic.program.LogicGraph;
import com.senne.starktech.logic.program.Op;
import com.senne.starktech.logic.program.ProgramKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * De blokjes-editor van de app Logic (jouw wens, 7 oktober: "blokjes met draadjes"). Hetzelfde
 * diagram als de code, maar getekend:
 * <ul>
 *   <li>een blokje per when, if en actie; triggers cyaan, if roze, acties lichtblauw;</li>
 *   <li>draadjes van een uitgang (rechts; bij een IF: groen = yes, rood = no, onderaan = after) naar
 *       de ingang (links) van het volgende blokje;</li>
 *   <li>slepen: een blokje verplaatsen, of het vlak zelf (op een lege plek, of met rechts);</li>
 *   <li>een draadje trekken: sleep vanaf een uitgang naar een blokje; loslaten op een lege plek haalt
 *       het draadje weg;</li>
 *   <li>rechts: niets gekozen = het palet (klik om een blokje toe te voegen); een blokje gekozen =
 *       zijn instellingen (welk apparaat, hoeveel seconden, de voorwaarde), met suggesties en meteen
 *       een melding als er iets niet klopt. Delete haalt het gekozen blokje weg.</li>
 * </ul>
 * Een blokje met een fout heeft een rode rand; een los blokje (niet aan een when of def) is grijs:
 * het doet niets. Je eigen blokken zijn paars: een DEF begint een rij (net als een when) en een
 * "use def"-blokje voert die rij uit.
 */
final class BlockCanvas {
    static final int NODE_W = 96;
    static final int NODE_H = 24;
    private static final int PANEL_W = 112;
    private static final int PORT = 3;
    private static final int GRID = 16;

    private static final int TRIGGER = HudStyle.CYAN;
    private static final int IF = 0xFF79C6;
    private static final int ACTION = 0x9ED8FF;
    /** Je eigen blokken (def en het gebruik ervan). */
    private static final int FUNCTION = 0xC9A0FF;
    /** Een naam een waarde geven (SET, en AT START helemaal links): geel, zoals gegevens in de code. */
    private static final int VARIABLE = 0xF1E87C;
    private static final int YES = 0x5BE37D;
    private static final int NO = 0xFF5A50;
    private static final int AFTER = 0xC8D2D8;

    /**
     * De blokjes in het palet, per kopje, voor elke soort programma (hoofdstuk 23: een robot kent andere
     * blokjes). Ze komen uit Op: een nieuw blokje staat er vanzelf in.
     */
    private static final java.util.Map<ProgramKind, Op[][]> PALETTES = new java.util.EnumMap<>(ProgramKind.class);

    static {
        for (ProgramKind kind : ProgramKind.values()) {
            PALETTES.put(kind, palette(kind));
        }
    }
    private static final String[] PALETTE_HEADERS = {"START (when, def)", "CHECK AND DO"};

    /** Eerst alles waar een rij mee begint (triggers, def, AT START), dan de if, de lussen en alle acties. */
    private static Op[][] palette(ProgramKind kind) {
        List<Op> start = new ArrayList<>();
        List<Op> rest = new ArrayList<>(List.of(Op.IF));
        for (Op op : Op.values()) {
            if (op.kind() == Op.Kind.LOOP) {
                rest.add(op);
            }
        }
        for (Op op : Op.values()) {
            if (!op.allowedIn(kind)) {
                continue;
            }
            if (op.isRoot()) {
                start.add(op);
            } else if (op.kind() == Op.Kind.ACTION || op.kind() == Op.Kind.SET) {
                rest.add(op);
            }
        }
        return new Op[][] {start.toArray(new Op[0]), rest.toArray(new Op[0])};
    }

    private final Supplier<CompoundTag> info;
    private final Runnable changed;
    /** Telt elke verandering; de fouten rekenen we alleen opnieuw uit als hij veranderde. */
    private int version;
    private int problemsVersion = -1;
    private List<LogicGraph.Problem> problemsCache = List.of();
    private final Consumer<String> hint;
    private LogicGraph graph = new LogicGraph();
    /** Wat voor programma je bewerkt (hoofdstuk 23): bepaalt het palet en de controle. */
    private ProgramKind kind = ProgramKind.SERVER;

    /** Welk stuk van het vlak je ziet (linksboven, in de maat van de blokjes) en hoe ver ingezoomd. */
    private double panX;
    private double panY;
    private float zoom = 0.75f;
    private static final float[] ZOOMS = {0.5f, 0.625f, 0.75f, 0.875f, 1.0f};
    private int selected = -1;
    /** Hoe ver het palet rechts gescrold is (pixels): er zijn meer blokjes dan er onder elkaar passen. */
    private int paletteScroll;

    // Wat de muis aan het doen is.
    private enum Drag { NONE, NODE, PAN, WIRE }

    private Drag drag = Drag.NONE;
    private int dragNode = -1;
    private LogicGraph.Port dragPort = LogicGraph.Port.NEXT;
    private int dragDx;
    private int dragDy;
    private int lastMx;
    private int lastMy;
    private boolean moved;

    // Waar het vlak en het paneel staan (van de laatste keer tekenen).
    private int cx;
    private int cy;
    private int cw;
    private int ch;
    private int px;

    private @Nullable EditBox textBox;
    private @Nullable EditBox numberBox;
    /** De waarde van {@code naam = waarde} (SET en AT START), als code. */
    private @Nullable EditBox exprBox;
    /** Zetten we zelf een waarde in een vak? Dan niet doen alsof jij typte. */
    private boolean filling;

    /**
     * @param info    de gegevens van de app (apparaten, lezers, panelen) voor de suggesties
     * @param changed wordt aangeroepen als je iets verandert (dan is het diagram niet meer bewaard)
     * @param hint    een melding voor de statusregel van de app
     */
    BlockCanvas(Supplier<CompoundTag> info, Runnable changed, Consumer<String> hint) {
        this.info = info;
        this.changed = changed;
        this.hint = hint;
    }

    LogicGraph graph() {
        return graph;
    }

    /** Er veranderde iets aan de blokjes (door jou). */
    private void edited() {
        version++;
        changed.run();
    }

    /** Wat er mis is met de blokjes (onthouden tot de volgende verandering). */
    private List<LogicGraph.Problem> problems() {
        if (problemsVersion != version) {
            problemsCache = graph.problems(kind);
            problemsVersion = version;
        }
        return problemsCache;
    }

    /** Wat voor programma je bewerkt: een ander palet, en de controle opnieuw. */
    void setKind(ProgramKind kind) {
        if (this.kind != kind) {
            this.kind = kind;
            paletteScroll = 0;
            problemsVersion = -1;
        }
    }

    /** De blokjes van het palet voor de soort programma die je bewerkt. */
    private Op[][] palette() {
        return PALETTES.get(kind);
    }

    /** Een ander diagram: het vlak schuift naar de blokjes. */
    void setGraph(LogicGraph graph) {
        this.graph = graph;
        version++;
        selected = -1;
        drag = Drag.NONE;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (LogicGraph.Node node : graph.nodes()) {
            minX = Math.min(minX, node.x);
            minY = Math.min(minY, node.y);
        }
        panX = minX == Integer.MAX_VALUE ? 0 : minX - 12;
        panY = minY == Integer.MAX_VALUE ? 0 : minY - 12;
    }

    /**
     * Dezelfde blokjes, maar net terug van de server (na Save): het beeld en het gekozen blokje
     * blijven waar ze waren.
     */
    void replaceGraph(LogicGraph graph) {
        int keep = selected;
        this.graph = graph;
        version++;
        drag = Drag.NONE;
        selectNode(graph.node(keep) != null ? keep : -1);
    }

    /** Kies dit blokje (bijvoorbeeld het blokje met een fout) en schuif het in beeld. */
    void select(int id) {
        selectNode(id);
        LogicGraph.Node node = graph.node(id);
        if (node != null && cw > 0) {
            double vw = cw / zoom;
            double vh = ch / zoom;
            if (node.x < panX || node.x + NODE_W > panX + vw || node.y < panY || node.y + NODE_H > panY + vh) {
                panX = node.x - vw / 2 + NODE_W / 2.0;
                panY = node.y - vh / 2 + NODE_H / 2.0;
            }
        }
    }

    // ---------------------------------------------------------------- Scherm <-> vlak

    /** Waar een punt van het scherm op het vlak ligt (in de maat van de blokjes). */
    private double worldX(int mx) {
        return (mx - cx) / zoom + panX;
    }

    private double worldY(int my) {
        return (my - cy) / zoom + panY;
    }

    /** In- of uitzoomen (een stap), met het punt onder de muis (of het midden) op zijn plek. */
    private void zoomStep(int direction, int mx, int my) {
        int index = 0;
        for (int i = 0; i < ZOOMS.length; i++) {
            if (Math.abs(ZOOMS[i] - zoom) < 0.01f) {
                index = i;
            }
        }
        int next = Math.max(0, Math.min(ZOOMS.length - 1, index + direction));
        double wx = worldX(mx);
        double wy = worldY(my);
        zoom = ZOOMS[next];
        panX = wx - (mx - cx) / zoom;
        panY = wy - (my - cy) / zoom;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my, float partialTick) {
        cx = x;
        cy = y;
        cw = w - PANEL_W - 4;
        ch = h;
        px = x + cw + 4;
        // Het vlak met een raster van stipjes.
        g.fill(cx, cy, cx + cw, cy + ch, HudStyle.argb(255, 0x081016));
        g.enableScissor(cx + 1, cy + 1, cx + cw - 1, cy + ch - 1);
        double step = GRID * zoom;
        double startX = ((-panX * zoom) % step + step) % step;
        double startY = ((-panY * zoom) % step + step) % step;
        for (double gx = cx + startX; gx < cx + cw; gx += step) {
            for (double gy = cy + startY; gy < cy + ch; gy += step) {
                g.fill((int) gx, (int) gy, (int) gx + 1, (int) gy + 1, HudStyle.argb(255, 0x1C2E38));
            }
        }
        // Alles op het vlak tekenen we in de maat van de blokjes; de "pose" schaalt het naar het scherm.
        double wmx = worldX(mx);
        double wmy = worldY(my);
        g.pose().pushPose();
        g.pose().translate((float) (cx - panX * zoom), (float) (cy - panY * zoom), 0);
        g.pose().scale(zoom, zoom, 1);
        Set<Integer> reachable = graph.reachable();
        java.util.Map<Integer, String> problems = new java.util.HashMap<>();
        for (LogicGraph.Problem problem : problems()) {
            problems.put(problem.node(), problem.message());
        }
        // Eerst de draadjes, dan de blokjes erover.
        for (LogicGraph.Node node : graph.nodes()) {
            for (LogicGraph.Port port : node.ports()) {
                LogicGraph.Node target = graph.node(node.link(port));
                if (target != null) {
                    int[] from = portPos(node, port);
                    int[] to = inputPos(target);
                    wire(g, from[0], from[1], to[0], to[1], portColour(port), reachable.contains(node.id));
                }
            }
        }
        if (drag == Drag.WIRE) {
            LogicGraph.Node node = graph.node(dragNode);
            if (node != null) {
                int[] from = portPos(node, dragPort);
                wire(g, from[0], from[1], (int) wmx, (int) wmy, portColour(dragPort), true);
            }
        }
        LogicGraph.Node hovered = null;
        for (LogicGraph.Node node : graph.nodes()) {
            renderNode(g, font, node, reachable.contains(node.id), problems.get(node.id));
            if (inCanvas(mx, my) && inNode(node, wmx, wmy)) {
                hovered = node;
            }
        }
        g.pose().popPose();
        g.disableScissor();
        HudStyle.brackets(g, cx, cy, cx + cw, cy + ch, 4, HudStyle.argb(255, OsDraw.DIM));
        // Zoomen: - en + rechtsboven, met hoe ver je ingezoomd bent.
        int zx = cx + cw - zoomButtonsWidth(font) - 3;
        String percent = Math.round(zoom * 100) + "%";
        g.drawString(font, percent, zx, cy + 4, HudStyle.argb(255, OsDraw.GREY), false);
        zx += font.width(percent) + 3;
        zx += OsDraw.button(g, font, zx, cy + 3, "-", zoom > ZOOMS[0], HudStyle.CYAN) + 2;
        OsDraw.button(g, font, zx, cy + 3, "+", zoom < ZOOMS[ZOOMS.length - 1], HudStyle.CYAN);
        if (graph.size() == 0) {
            g.drawString(font, "Empty: click a block on the right", cx + 8, cy + 8, HudStyle.argb(255, OsDraw.GREY), false);
            g.drawString(font, "(start with a WHEN).", cx + 8, cy + 18, HudStyle.argb(255, OsDraw.GREY), false);
        }
        // Rechts: het palet, of de instellingen van het gekozen blokje.
        LogicGraph.Node node = graph.node(selected);
        if (node == null) {
            renderPalette(g, font, mx, my);
        } else {
            renderSettings(g, font, node, mx, my, partialTick);
        }
        // Wat er onder de muis is, in de statusregel.
        if (hovered != null && drag == Drag.NONE) {
            String problem = problems.get(hovered.id);
            hint.accept(problem != null ? problem : describe(hovered) + (reachable.contains(hovered.id) ? ""
                    : "  (loose: connect it to a when, or it does nothing)"));
        }
    }

    /** Hoe breed "75% [-] [+]" is. */
    private static int zoomButtonsWidth(Font font) {
        return font.width("100%") + 3 + OsDraw.buttonWidth(font, "-") + 2 + OsDraw.buttonWidth(font, "+");
    }

    /** Eén blokje, in de maat van het vlak (de pose schaalt het). */
    private void renderNode(GuiGraphics g, Font font, LogicGraph.Node node, boolean connected, @Nullable String problem) {
        int x = node.x;
        int y = node.y;
        if (x > panX + cw / zoom || y > panY + ch / zoom || x + NODE_W < panX || y + NODE_H < panY) {
            return;
        }
        int colour = colour(node.op);
        int alpha = connected ? 255 : 120;
        g.fill(x, y, x + NODE_W, y + NODE_H, HudStyle.argb(255, connected ? 0x13222C : 0x0E161B));
        g.fill(x, y, x + NODE_W, y + 10, HudStyle.argb(alpha, darker(colour)));
        int border = problem != null ? HudStyle.RED : node.id == selected ? HudStyle.WHITE : colour;
        g.renderOutline(x, y, NODE_W, NODE_H, HudStyle.argb(connected || node.id == selected ? 255 : 110, border));
        if (node.id == selected) {
            g.renderOutline(x - 1, y - 1, NODE_W + 2, NODE_H + 2, HudStyle.argb(120, HudStyle.WHITE));
        }
        g.drawString(font, title(node.op), x + 3, y + 1, HudStyle.argb(alpha, HudStyle.WHITE), false);
        String detail = detail(node);
        g.drawString(font, OsDraw.fitRight(font, detail, NODE_W - 6), x + 3, y + 13,
                HudStyle.argb(alpha, detail.isEmpty() ? OsDraw.GREY : 0xD8E2E8), false);
        // De poorten.
        if (!node.op.isRoot()) {
            int[] in = inputPos(node);
            port(g, in[0], in[1], 0x8A9BA0);
        }
        for (LogicGraph.Port port : node.ports()) {
            int[] at = portPos(node, port);
            port(g, at[0], at[1], portColour(port));
        }
    }

    private static void port(GuiGraphics g, int x, int y, int colour) {
        g.fill(x - PORT, y - PORT, x + PORT, y + PORT, HudStyle.argb(255, 0x081016));
        g.fill(x - PORT + 1, y - PORT + 1, x + PORT - 1, y + PORT - 1, HudStyle.argb(255, colour));
    }

    /** Een draadje met rechte hoeken: opzij, dan omhoog of omlaag, dan opzij naar de ingang. */
    private static void wire(GuiGraphics g, int x0, int y0, int x1, int y1, int colour, boolean bright) {
        int c = HudStyle.argb(bright ? 230 : 90, colour);
        int mid = x1 > x0 + 4 ? (x0 + x1) / 2 : x0 + 8;
        if (x1 > x0 + 4) {
            hline(g, x0, mid, y0, c);
            vline(g, mid, y0, y1, c);
            hline(g, mid, x1, y1, c);
        } else {
            // Terug naar links: eerst opzij, dan naar een rij tussen de blokjes, dan naar links.
            int between = y1 - NODE_H / 2 - 5;
            hline(g, x0, mid, y0, c);
            vline(g, mid, y0, between, c);
            hline(g, x1 - 8, mid, between, c);
            vline(g, x1 - 8, between, y1, c);
            hline(g, x1 - 8, x1, y1, c);
        }
    }

    private static void hline(GuiGraphics g, int x0, int x1, int y, int colour) {
        g.fill(Math.min(x0, x1), y, Math.max(x0, x1) + 1, y + 1, colour);
    }

    private static void vline(GuiGraphics g, int x, int y0, int y1, int colour) {
        g.fill(x, Math.min(y0, y1), x + 1, Math.max(y0, y1) + 1, colour);
    }

    private static int colour(Op op) {
        return op.isTrigger() ? TRIGGER : op == Op.IF || op.kind() == Op.Kind.LOOP || op == Op.BREAK ? IF
                : op == Op.DEF || op == Op.CALL ? FUNCTION : op.kind() == Op.Kind.SET ? VARIABLE : ACTION;
    }

    /** De titel bovenop een blokje: "WHEN keycard", "IF", "REPEAT", "DEF", "use def", "SET", "AT START", "pulse". */
    private static String title(Op op) {
        return op.isTrigger() ? "WHEN " + op.code() : op == Op.IF ? "IF" : op == Op.DEF ? "DEF"
                : op == Op.CALL ? "use def" : op == Op.SET ? "SET" : op == Op.GLOBAL ? "AT START"
                : op.kind() == Op.Kind.LOOP || op == Op.BREAK ? op.code().toUpperCase(Locale.ROOT) : op.code();
    }

    private static int darker(int rgb) {
        int r = (rgb >> 16 & 0xFF) / 3;
        int gr = (rgb >> 8 & 0xFF) / 3;
        int b = (rgb & 0xFF) / 3;
        return r << 16 | gr << 8 | b;
    }

    private static int portColour(LogicGraph.Port port) {
        return switch (port) {
            case YES -> YES;
            case NO -> NO;
            case NEXT -> AFTER;
        };
    }

    /** Wat er op de tweede regel van een blokje staat: het apparaat, de seconden, de voorwaarde. */
    private static String detail(LogicGraph.Node node) {
        if (node.op.kind() == Op.Kind.SET) {
            return node.text + " = " + node.expr;
        }
        if (node.op == Op.REPEAT) {
            return node.text + " times";
        }
        if (node.op == Op.FOREVER) {
            return "again and again";
        }
        if (node.op == Op.BREAK) {
            return "out of the loop";
        }
        if (node.op.textArg() == Op.Arg.VALUES) {
            return node.expr; // goto(x, y, z): het rijtje waarden
        }
        if (!node.expr.isEmpty()) {
            // Een berekend bericht of berekend getal (hoofdstuk 20).
            return Checks.exprIsText(node.op) ? node.expr
                    : (node.op.textArg() != Op.Arg.NONE ? node.text + ", " : "") + node.expr + unitSuffix(node.op);
        }
        return switch (node.op) {
            case EVERY, WAIT, FORWARD, BACK -> seconds(node.number) + unitSuffix(node.op);
            case PULSE -> node.text + ", " + seconds(node.number) + " s";
            case CHARGE -> "to " + seconds(node.number) + "%";
            case ACCEPT -> "green light";
            case DENY -> "red light";
            case STARTED -> "switched on";
            case TURN_LEFT -> "a quarter left";
            case TURN_RIGHT -> "a quarter right";
            case STOP -> "switch off";
            case DEF, CALL -> node.text + "()";
            default -> node.text;
        };
    }

    /** Achter een getal op een blokje: " s" (seconden), " blocks" of "%". */
    private static String unitSuffix(Op op) {
        return switch (op.unit()) {
            case "blocks" -> " blocks";
            case "percent" -> "%";
            default -> " s";
        };
    }

    private static String seconds(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "");
    }

    /** Het blokje zoals in de code (voor de statusregel). */
    private static String describe(LogicGraph.Node node) {
        if (node.op == Op.IF) {
            return "if " + node.text + ":";
        }
        if (node.op.kind() == Op.Kind.SET) {
            return node.text + " = " + node.expr;
        }
        if (node.op.kind() == Op.Kind.LOOP) {
            return com.senne.starktech.logic.program.ScriptPrinter.loopLine(node.op, node.text);
        }
        String call = com.senne.starktech.logic.program.ScriptPrinter.call(node.op, node.text, node.number, node.expr);
        return node.op.isTrigger() ? "when " + call + ":" : node.op == Op.DEF ? call + ":" : call;
    }

    // ---------------------------------------------------------------- Waar de poorten zitten

    // (Op het vlak, in de maat van de blokjes.)

    private int[] inputPos(LogicGraph.Node node) {
        return new int[] {node.x, node.y + NODE_H / 2};
    }

    private int[] portPos(LogicGraph.Node node, LogicGraph.Port port) {
        int x = node.x;
        int y = node.y;
        // Een if en een lus: YES ("yes", of "do" bij een lus) rechtsboven, NO rechtsonder, NEXT ("after") onderaan.
        if (node.op != Op.IF && node.op.kind() != Op.Kind.LOOP) {
            return new int[] {x + NODE_W, y + NODE_H / 2};
        }
        return switch (port) {
            case YES -> new int[] {x + NODE_W, y + 6};
            case NO -> new int[] {x + NODE_W, y + NODE_H - 6};
            case NEXT -> new int[] {x + NODE_W / 2, y + NODE_H};
        };
    }

    private static boolean inNode(LogicGraph.Node node, double wx, double wy) {
        return wx >= node.x && wx < node.x + NODE_W && wy >= node.y && wy < node.y + NODE_H;
    }

    private boolean inCanvas(int mx, int my) {
        return mx >= cx && mx < cx + cw && my >= cy && my < cy + ch;
    }

    // ================================================================ Het palet

    /** Hoe hoog alles in het palet samen is (de kopjes en de rijen knoppen). */
    private int paletteHeight() {
        int height = 3;
        for (Op[] ops : palette()) {
            height += 10 + ((ops.length + 1) / 2) * 12 + 3;
        }
        return height;
    }

    /** Hoe ver het palet hooguit kan scrollen (0 als alles past). */
    private int maxPaletteScroll() {
        return Math.max(0, paletteHeight() - ch);
    }

    /** De naam van een blokje in het palet: zoals in de code, behalve AT START (in de code gewoon count = 0). */
    private static String paletteLabel(Op op) {
        return op == Op.GLOBAL ? "at start" : op.code();
    }

    private void renderPalette(GuiGraphics g, Font font, int mx, int my) {
        OsDraw.field(g, px, cy, px + PANEL_W, cy + ch, false);
        // Er zijn meer blokjes dan er passen: het palet scrollt (muiswiel boven het palet).
        paletteScroll = Math.max(0, Math.min(paletteScroll, maxPaletteScroll()));
        g.enableScissor(px + 1, cy + 1, px + PANEL_W - 1, cy + ch - 1);
        int y = cy + 3 - paletteScroll;
        boolean inPanel = my >= cy && my < cy + ch;
        for (int section = 0; section < palette().length; section++) {
            g.drawString(font, PALETTE_HEADERS[section], px + 4, y, HudStyle.argb(255, HudStyle.ORANGE), false);
            y += 10;
            Op[] ops = palette()[section];
            for (int i = 0; i < ops.length; i++) {
                int bx = px + 3 + (i % 2) * 54;
                int by = y + (i / 2) * 12;
                boolean hover = inPanel && OsDraw.inside(mx, my, bx, by, 52, 11);
                g.fill(bx, by, bx + 52, by + 11, HudStyle.argb(255, hover ? 0x1E3A48 : 0x13222C));
                g.renderOutline(bx, by, 52, 11, HudStyle.argb(hover ? 255 : 110, colour(ops[i])));
                String label = paletteLabel(ops[i]);
                float scale = Math.min(1f, 48f / font.width(label));
                g.pose().pushPose();
                g.pose().translate(bx + 3, by + 2 + (1 - scale) * 4, 0);
                g.pose().scale(scale, scale, 1);
                g.drawString(font, label, 0, 0, HudStyle.argb(255, colour(ops[i])), false);
                g.pose().popPose();
                if (hover) {
                    hint.accept(ops[i].help() + " (click to add)");
                }
            }
            y += ((ops.length + 1) / 2) * 12 + 3;
        }
        g.disableScissor();
        // Pijltjes: er is nog meer boven of onder (scroll met het muiswiel boven het palet).
        if (paletteScroll > 0) {
            g.drawString(font, "▲", px + PANEL_W - 10, cy + 2, HudStyle.argb(255, OsDraw.GREY), false);
        }
        if (paletteScroll < maxPaletteScroll()) {
            g.drawString(font, "▼", px + PANEL_W - 10, cy + ch - 10, HudStyle.argb(255, OsDraw.GREY), false);
        }
    }

    /** Het blokje uit het palet onder de muis, of null. */
    private @Nullable Op paletteAt(int mx, int my) {
        if (my < cy || my >= cy + ch) {
            return null;
        }
        int y = cy + 3 - paletteScroll;
        for (Op[] ops : palette()) {
            y += 10;
            for (int i = 0; i < ops.length; i++) {
                if (OsDraw.inside(mx, my, px + 3 + (i % 2) * 54, y + (i / 2) * 12, 52, 11)) {
                    return ops[i];
                }
            }
            y += ((ops.length + 1) / 2) * 12 + 3;
        }
        return null;
    }

    /** Een nieuw blokje midden in beeld (een beetje verschoven als daar al iets staat). */
    private void add(Op op) {
        int x = (int) (panX + cw / zoom / 2) - NODE_W / 2;
        int y = (int) (panY + ch / zoom / 2) - NODE_H / 2;
        x -= Math.floorMod(x, 4);
        y -= Math.floorMod(y, 4);
        boolean taken = true;
        for (int tries = 0; taken && tries < 20; tries++) {
            taken = false;
            for (LogicGraph.Node node : graph.nodes()) {
                if (Math.abs(node.x - x) < 8 && Math.abs(node.y - y) < 8) {
                    taken = true;
                    x += 12;
                    y += 12;
                    break;
                }
            }
        }
        LogicGraph.Node node = graph.add(op, x, y);
        if (op.textArg() == Op.Arg.DEVICE) {
            List<String> names = suggestions(op);
            node.text = names.isEmpty() ? "" : names.getFirst();
        } else if (op == Op.IF) {
            node.text = "true";
        } else if (op.textArg() == Op.Arg.TEXT) {
            node.text = op == Op.SAY ? "Hello" : op.textExample();
        } else if (op.textArg() == Op.Arg.ROBOT) {
            node.text = op.textExample();
        } else if (op.textArg() == Op.Arg.VALUES) {
            // Een begin dat al klopt: goto naar een plek, pin 1 aan, een opdracht aan "Guard 1".
            node.expr = switch (op) {
                case GOTO -> "robot.x + 5, robot.y, robot.z";
                case WRITE -> "1, true";
                default -> "\"Guard 1\", \"attack\"";
            };
        } else if (op == Op.DEF) {
            node.text = freeDefName();
        } else if (op == Op.CALL) {
            List<String> names = suggestions(op);
            node.text = names.isEmpty() ? "" : names.getFirst();
        } else if (op.kind() == Op.Kind.SET) {
            node.text = "count";
            node.expr = op == Op.SET ? "count + 1" : "0";
        } else if (op == Op.REPEAT) {
            node.text = "3";
        } else if (op == Op.WHILE) {
            node.text = "true";
        }
        edited();
        selectNode(node.id);
    }

    /** Een naam voor een nieuwe def die nog niet bestaat: my_block, my_block_2, ... */
    private String freeDefName() {
        java.util.Set<String> used = new java.util.HashSet<>(suggestions(Op.CALL));
        String name = "my_block";
        for (int number = 2; used.contains(name); number++) {
            name = "my_block_" + number;
        }
        return name;
    }

    // ================================================================ De instellingen van een blokje

    private EditBox textBox() {
        if (textBox == null) {
            textBox = new EditBox(Minecraft.getInstance().font, 0, 0, 80, 10, Component.literal("Text"));
            textBox.setBordered(false);
            textBox.setMaxLength(Op.MAX_TEXT * 2);
            textBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
            textBox.setResponder(value -> {
                LogicGraph.Node node = graph.node(selected);
                if (!filling && node != null) {
                    if (node.op == Op.DEF) {
                        // Een def een andere naam geven: de blokjes die hem gebruiken, gaan mee.
                        for (LogicGraph.Node other : graph.nodes()) {
                            if (other.op == Op.CALL && other.text.equals(node.text)) {
                                other.text = value;
                            }
                        }
                    }
                    node.text = value;
                    edited();
                }
            });
        }
        return textBox;
    }

    private EditBox numberBox() {
        if (numberBox == null) {
            numberBox = new EditBox(Minecraft.getInstance().font, 0, 0, 40, 10, Component.literal("Seconds"));
            numberBox.setBordered(false);
            numberBox.setMaxLength(7);
            numberBox.setFilter(value -> value.chars().allMatch(c -> Character.isDigit(c) || c == '.'));
            numberBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
            numberBox.setResponder(value -> {
                LogicGraph.Node node = graph.node(selected);
                if (!filling && node != null) {
                    try {
                        node.number = Double.parseDouble(value);
                    } catch (NumberFormatException e) {
                        node.number = 0;
                    }
                    edited();
                }
            });
        }
        return numberBox;
    }

    private EditBox exprBox() {
        if (exprBox == null) {
            exprBox = new EditBox(Minecraft.getInstance().font, 0, 0, 80, 10, Component.literal("Value"));
            exprBox.setBordered(false);
            exprBox.setMaxLength(Op.MAX_TEXT * 2);
            exprBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
            exprBox.setResponder(value -> {
                LogicGraph.Node node = graph.node(selected);
                if (!filling && node != null) {
                    node.expr = value;
                    edited();
                }
            });
        }
        return exprBox;
    }

    private void selectNode(int id) {
        selected = id;
        LogicGraph.Node node = graph.node(id);
        filling = true;
        textBox().setValue(node == null ? "" : node.text);
        textBox().moveCursorToStart(false);
        numberBox().setValue(node == null ? "" : seconds(node.number));
        exprBox().setValue(node == null ? "" : node.expr);
        exprBox().moveCursorToStart(false);
        filling = false;
        textBox().setFocused(false);
        numberBox().setFocused(false);
        exprBox().setFocused(false);
    }

    /**
     * Staat er een berekening (code) in het vak "Value"? Bij SET altijd (de waarde); bij een actie als
     * het bericht of de seconden een berekening zijn ({@code say("Count: " + count)}, {@code wait(delay)}).
     */
    private static boolean showsExpr(LogicGraph.Node node) {
        return node.op.kind() == Op.Kind.SET || node.op.textArg() == Op.Arg.VALUES || !node.expr.isEmpty();
    }

    /** Het gewone tekstvak, behalve als het bericht een berekening is (dan staat het in het code-vak). */
    private static boolean showsText(LogicGraph.Node node) {
        return textLabel(node.op) != null && !(Checks.exprIsText(node.op) && !node.expr.isEmpty());
    }

    /** Het gewone getalvak (seconden), behalve als de seconden een berekening zijn. */
    private static boolean showsNumber(LogicGraph.Node node) {
        return node.op.numberArg() != Op.Arg.NONE && node.expr.isEmpty();
    }

    private static String exprLabel(LogicGraph.Node node) {
        if (node.op.textArg() == Op.Arg.VALUES) {
            // goto: "x, y, z (code)"; order: "robot, order (x, y, z)"
            String[] names = node.op.valueNames();
            int required = node.op.requiredValues();
            String label = String.join(", ", java.util.Arrays.copyOf(names, required));
            if (required < names.length) {
                label += " (" + String.join(", ", java.util.Arrays.copyOfRange(names, required, names.length)) + ")";
            }
            return label;
        }
        return node.op.kind() == Op.Kind.SET ? "Value" : Checks.exprIsText(node.op) ? "Message (code)"
                : unitLabel(node.op) + " (code)";
    }

    /** Hoe het getalvak heet: "Seconds", "Blocks" of "Percent". */
    private static String unitLabel(Op op) {
        String unit = op.unit();
        return Character.toUpperCase(unit.charAt(0)) + unit.substring(1);
    }

    /** Hoe het tekstvak van dit blokje heet. */
    private static @Nullable String textLabel(Op op) {
        if (op == Op.IF || op == Op.WHILE) {
            return "Check";
        }
        if (op == Op.REPEAT) {
            return "Times (a number)";
        }
        return switch (op.textArg()) {
            case DEVICE -> op == Op.KEYCARD ? "Reader" : op == Op.CODEPANEL ? "Panel" : "Device";
            case TEXT -> op == Op.ORDERED ? "Order" : "Message";
            case ROBOT -> "Robot";
            case NAME -> op == Op.DEF ? "Name (letters, digits, _)" : "Def to use";
            case VARIABLE -> "Name";
            default -> null;
        };
    }

    /** De namen die bij dit blokje passen: lezers, panelen, of je apparaten. */
    private List<String> suggestions(Op op) {
        List<String> names = new ArrayList<>();
        if (op == Op.KEYCARD || op == Op.CODEPANEL) {
            for (Tag element : info.get().getList("inputs", Tag.TAG_COMPOUND)) {
                CompoundTag input = (CompoundTag) element;
                if (input.getBoolean("p") == (op == Op.CODEPANEL)) {
                    names.add(input.getString("n"));
                }
            }
        } else if (op.textArg() == Op.Arg.DEVICE) {
            for (Tag tag : info.get().getList("outputs", Tag.TAG_STRING)) {
                names.add(tag.getAsString());
            }
        } else if (op == Op.CALL) {
            // Je eigen blokken (de DEF-blokjes), in de volgorde waarin je ze maakte.
            List<LogicGraph.Node> defs = new ArrayList<>();
            for (LogicGraph.Node node : graph.nodes()) {
                if (node.op == Op.DEF && !node.text.isBlank()) {
                    defs.add(node);
                }
            }
            defs.sort(java.util.Comparator.comparingInt(node -> node.id));
            for (LogicGraph.Node def : defs) {
                names.add(def.text);
            }
        } else if (op == Op.IF || op == Op.WHILE) {
            if (kind == ProgramKind.SERVER) {
                names.addAll(List.of("card.valid and card.level >= 3", "code == \"1234\"", "player.trusted"));
            } else {
                names.addAll(List.of("robot.battery < 10", "robot.blocked", "read(1)"));
            }
            names.add("is_night");
            for (Tag tag : info.get().getList("outputs", Tag.TAG_STRING)) {
                names.add("is_on(\"" + tag.getAsString() + "\")");
            }
        }
        return names;
    }

    /** Wat er mis is met dit blokje (ook als het los ligt), of null. */
    private @Nullable String problemOf(LogicGraph.Node node) {
        for (LogicGraph.Problem problem : problems()) {
            if (problem.node() == node.id) {
                return problem.message();
            }
        }
        if (graph.reachable().contains(node.id)) {
            return null;
        }
        if (node.op == Op.IF) {
            try {
                Expr.parseLenient(node.text, true);
                return null;
            } catch (LogicException e) {
                return e.getMessage();
            }
        }
        if (node.op.kind() == Op.Kind.LOOP) {
            return LogicGraph.loopProblem(node);
        }
        if (node.op.kind() == Op.Kind.SET) {
            String problem = Checks.variableProblem(node.text, java.util.Set.of());
            if (problem != null) {
                return problem;
            }
            try {
                Expr.parseLenient(node.expr, false);
                return null;
            } catch (LogicException e) {
                return e.getMessage();
            }
        }
        String problem = Checks.argsProblem(node.op, node.op.textArg() == Op.Arg.NONE ? null : node.text,
                node.op.numberArg() == Op.Arg.NONE ? null : node.number, node.expr);
        return problem != null ? problem : Checks.exprProblem(node.op, node.expr);
    }

    private void renderSettings(GuiGraphics g, Font font, LogicGraph.Node node, int mx, int my, float partialTick) {
        OsDraw.field(g, px, cy, px + PANEL_W, cy + ch, true);
        int x = px + 4;
        int y = cy + 4;
        g.drawString(font, title(node.op), x, y, HudStyle.argb(255, colour(node.op)), false);
        y += 10;
        for (FormattedCharSequence line : font.split(Component.literal(node.op.help()), PANEL_W - 8)) {
            g.drawString(font, line, x, y, HudStyle.argb(255, OsDraw.GREY), false);
            y += 9;
        }
        y += 3;
        String label = textLabel(node.op);
        if (label != null && showsText(node)) {
            g.drawString(font, label, x, y, HudStyle.argb(255, HudStyle.WHITE), false);
            y += 10;
            EditBox box = textBox();
            OsDraw.field(g, x - 1, y, px + PANEL_W - 3, y + OsDraw.BUTTON_H, box.isFocused());
            box.setX(x + 2);
            box.setY(y + 2);
            box.setWidth(PANEL_W - 12);
            box.render(g, mx, my, partialTick);
            y += OsDraw.BUTTON_H + 3;
        }
        if (showsExpr(node)) {
            // De waarde, als code: 0, count + 1, "Level " + card.level...
            g.drawString(font, exprLabel(node), x, y, HudStyle.argb(255, HudStyle.WHITE), false);
            y += 10;
            EditBox box = exprBox();
            OsDraw.field(g, x - 1, y, px + PANEL_W - 3, y + OsDraw.BUTTON_H, box.isFocused());
            box.setX(x + 2);
            box.setY(y + 2);
            box.setWidth(PANEL_W - 12);
            box.render(g, mx, my, partialTick);
            y += OsDraw.BUTTON_H + 3;
        }
        if (showsNumber(node)) {
            String unit = unitLabel(node.op);
            g.drawString(font, unit, x, y + 2, HudStyle.argb(255, HudStyle.WHITE), false);
            EditBox box = numberBox();
            int fx = x + font.width(unit) + 4;
            OsDraw.field(g, fx, y, fx + 40, y + OsDraw.BUTTON_H, box.isFocused());
            box.setX(fx + 3);
            box.setY(y + 2);
            box.setWidth(34);
            box.render(g, mx, my, partialTick);
            y += OsDraw.BUTTON_H + 3;
        }
        // Suggesties: klik om in te vullen.
        List<String> names = suggestions(node.op);
        if (!names.isEmpty()) {
            boolean ideas = node.op == Op.IF || node.op == Op.WHILE;
            g.drawString(font, ideas ? "Ideas:" : "Yours:", x, y, HudStyle.argb(255, HudStyle.ORANGE), false);
            y += 10;
            for (String name : names) {
                if (y + 9 > cy + ch - 36) {
                    break;
                }
                boolean hover = OsDraw.inside(mx, my, px + 2, y - 1, PANEL_W - 4, 10);
                if (hover) {
                    g.fill(px + 2, y - 1, px + PANEL_W - 2, y + 9, HudStyle.argb(255, 0x16303F));
                }
                g.drawString(font, OsDraw.fitRight(font, name, PANEL_W - 10), x + 2, y,
                        HudStyle.argb(255, ideas ? 0xF1E87C : 0x9ED8FF), false);
                y += 10;
            }
        }
        // Onderaan: klopt het, en de knoppen.
        String problem = problemOf(node);
        int by = cy + ch - OsDraw.BUTTON_H - 3;
        List<FormattedCharSequence> lines = font.split(Component.literal(problem == null ? "OK" : problem), PANEL_W - 8);
        int ly = by - 2 - Math.min(2, lines.size()) * 9;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(font, lines.get(i), x, ly + i * 9, HudStyle.argb(255, problem == null ? HudStyle.GREEN : HudStyle.RED), false);
        }
        OsDraw.button(g, font, x - 1, by, "Done", true, HudStyle.CYAN);
        String delete = "Delete";
        OsDraw.button(g, font, px + PANEL_W - 3 - OsDraw.buttonWidth(font, delete), by, delete, true, HudStyle.RED);
    }

    private void clickSettings(Font font, LogicGraph.Node node, int mx, int my) {
        int x = px + 4;
        int by = cy + ch - OsDraw.BUTTON_H - 3;
        if (OsDraw.inside(mx, my, x - 1, by, OsDraw.buttonWidth(font, "Done"), OsDraw.BUTTON_H)) {
            selectNode(-1);
            return;
        }
        int deleteW = OsDraw.buttonWidth(font, "Delete");
        if (OsDraw.inside(mx, my, px + PANEL_W - 3 - deleteW, by, deleteW, OsDraw.BUTTON_H)) {
            deleteSelected();
            return;
        }
        // De vakken (zelfde plek als bij het tekenen).
        int y = cy + 14 + font.split(Component.literal(node.op.help()), PANEL_W - 8).size() * 9 + 3;
        textBox().setFocused(false);
        numberBox().setFocused(false);
        exprBox().setFocused(false);
        if (textLabel(node.op) != null && showsText(node)) {
            y += 10;
            if (OsDraw.inside(mx, my, x - 1, y, PANEL_W - 6, OsDraw.BUTTON_H)) {
                textBox().setFocused(true);
                textBox().mouseClicked(mx, my, 0);
                return;
            }
            y += OsDraw.BUTTON_H + 3;
        }
        if (showsExpr(node)) {
            y += 10;
            if (OsDraw.inside(mx, my, x - 1, y, PANEL_W - 6, OsDraw.BUTTON_H)) {
                exprBox().setFocused(true);
                exprBox().mouseClicked(mx, my, 0);
                return;
            }
            y += OsDraw.BUTTON_H + 3;
        }
        if (showsNumber(node)) {
            if (OsDraw.inside(mx, my, x, y, PANEL_W - 8, OsDraw.BUTTON_H)) {
                numberBox().setFocused(true);
                numberBox().mouseClicked(mx, my, 0);
                return;
            }
            y += OsDraw.BUTTON_H + 3;
        }
        List<String> names = suggestions(node.op);
        if (!names.isEmpty()) {
            y += 10;
            for (String name : names) {
                if (y + 9 > cy + ch - 36) {
                    break;
                }
                if (OsDraw.inside(mx, my, px + 2, y - 1, PANEL_W - 4, 10)) {
                    textBox().setValue(name); // de responder zet hem in het blokje
                    return;
                }
                y += 10;
            }
        }
    }

    private void deleteSelected() {
        if (graph.node(selected) != null) {
            graph.remove(selected);
            edited();
        }
        selectNode(-1);
    }

    // ================================================================ Muis

    boolean mouseClicked(Font font, int mx, int my, int button) {
        if (mx >= px && mx < px + PANEL_W && my >= cy && my < cy + ch) {
            LogicGraph.Node node = graph.node(selected);
            if (node == null) {
                Op op = paletteAt(mx, my);
                if (op != null) {
                    add(op);
                }
            } else {
                clickSettings(font, node, mx, my);
            }
            return true;
        }
        if (!inCanvas(mx, my)) {
            return false;
        }
        textBox().setFocused(false);
        numberBox().setFocused(false);
        exprBox().setFocused(false);
        // De zoomknoppen rechtsboven.
        int zx = cx + cw - zoomButtonsWidth(font) - 3 + font.width(Math.round(zoom * 100) + "%") + 3;
        if (OsDraw.inside(mx, my, zx, cy + 3, OsDraw.buttonWidth(font, "-"), OsDraw.BUTTON_H)) {
            zoomStep(-1, cx + cw / 2, cy + ch / 2);
            return true;
        }
        zx += OsDraw.buttonWidth(font, "-") + 2;
        if (OsDraw.inside(mx, my, zx, cy + 3, OsDraw.buttonWidth(font, "+"), OsDraw.BUTTON_H)) {
            zoomStep(1, cx + cw / 2, cy + ch / 2);
            return true;
        }
        double wx = worldX(mx);
        double wy = worldY(my);
        lastMx = mx;
        lastMy = my;
        moved = false;
        if (button == 1) {
            drag = Drag.PAN;
            return true;
        }
        // Een uitgang: een draadje trekken. (Van achteren naar voren: het bovenste blokje eerst.)
        List<LogicGraph.Node> nodes = new ArrayList<>(graph.nodes());
        for (int i = nodes.size() - 1; i >= 0; i--) {
            LogicGraph.Node node = nodes.get(i);
            for (LogicGraph.Port port : node.ports()) {
                int[] at = portPos(node, port);
                if (Math.abs(wx - at[0]) <= PORT + 2 && Math.abs(wy - at[1]) <= PORT + 2) {
                    drag = Drag.WIRE;
                    dragNode = node.id;
                    dragPort = port;
                    return true;
                }
            }
        }
        for (int i = nodes.size() - 1; i >= 0; i--) {
            LogicGraph.Node node = nodes.get(i);
            if (inNode(node, wx, wy)) {
                selectNode(node.id);
                drag = Drag.NODE;
                dragNode = node.id;
                dragDx = (int) Math.round(wx - node.x);
                dragDy = (int) Math.round(wy - node.y);
                return true;
            }
        }
        selectNode(-1);
        drag = Drag.PAN;
        return true;
    }

    boolean mouseDragged(int mx, int my) {
        if (drag == Drag.NONE) {
            return false;
        }
        if (Math.abs(mx - lastMx) + Math.abs(my - lastMy) > 0) {
            moved = true;
        }
        switch (drag) {
            case PAN -> {
                panX -= (mx - lastMx) / zoom;
                panY -= (my - lastMy) / zoom;
            }
            case NODE -> {
                LogicGraph.Node node = graph.node(dragNode);
                if (node != null) {
                    int nx = (int) Math.round(worldX(mx)) - dragDx;
                    int ny = (int) Math.round(worldY(my)) - dragDy;
                    nx -= Math.floorMod(nx, 4);
                    ny -= Math.floorMod(ny, 4);
                    if (nx != node.x || ny != node.y) {
                        node.x = nx;
                        node.y = ny;
                        edited();
                    }
                }
            }
            default -> { }
        }
        lastMx = mx;
        lastMy = my;
        return true;
    }

    void mouseReleased(int mx, int my) {
        if (drag == Drag.WIRE) {
            LogicGraph.Node from = graph.node(dragNode);
            LogicGraph.Node target = null;
            for (LogicGraph.Node node : graph.nodes()) {
                if (inNode(node, worldX(mx), worldY(my)) && node.id != dragNode) {
                    target = node;
                }
            }
            if (from != null && target != null) {
                String problem = graph.connect(from.id, dragPort, target.id);
                if (problem != null) {
                    hint.accept(problem);
                } else {
                    edited();
                }
            } else if (from != null && moved && from.link(dragPort) >= 0) {
                graph.disconnect(from.id, dragPort);
                edited();
            }
        }
        drag = Drag.NONE;
    }

    /** Scrollen schuift het vlak (met Shift opzij); met Ctrl zoom je in en uit. */
    void mouseScrolled(int mx, int my, double scrollY, boolean sideways, boolean zoomKey) {
        if (mx >= px && graph.node(selected) == null) {
            // Boven het palet: het palet scrollen (er zijn meer blokjes dan er passen).
            paletteScroll = Math.max(0, Math.min(maxPaletteScroll(), paletteScroll - (int) Math.signum(scrollY) * 12));
            return;
        }
        if (zoomKey) {
            zoomStep((int) Math.signum(scrollY), mx, my);
        } else if (sideways) {
            panX -= Math.signum(scrollY) * 24 / zoom;
        } else {
            panY -= Math.signum(scrollY) * 24 / zoom;
        }
    }

    // ================================================================ Toetsen

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox box : new EditBox[] {textBox(), numberBox(), exprBox()}) {
            if (box.isFocused()) {
                if (keyCode == 256 || keyCode == 257 || keyCode == 335) {
                    box.setFocused(false);
                } else {
                    box.keyPressed(keyCode, scanCode, modifiers);
                }
                return true;
            }
        }
        if ((keyCode == 261 || keyCode == 259) && graph.node(selected) != null) { // Delete of Backspace
            deleteSelected();
            return true;
        }
        if (keyCode == 256 && graph.node(selected) != null) { // Esc: eerst alleen niets meer gekozen
            selectNode(-1);
            return true;
        }
        return false;
    }

    boolean charTyped(char codePoint, int modifiers) {
        for (EditBox box : new EditBox[] {textBox(), numberBox(), exprBox()}) {
            if (box.isFocused()) {
                box.charTyped(codePoint, modifiers);
                return true;
            }
        }
        return false;
    }

    void blur() {
        textBox().setFocused(false);
        numberBox().setFocused(false);
        exprBox().setFocused(false);
    }
}
