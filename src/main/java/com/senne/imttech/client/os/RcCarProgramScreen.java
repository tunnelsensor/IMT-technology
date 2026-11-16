package com.senne.imttech.client.os;

import com.senne.imttech.car.RcCarEntity;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.network.RcCarActionPayload;
import com.senne.imttech.network.RcCarProgramsPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * De RC programs van je server, op het scherm van een RC Car (hoofdstuk 119, jouw wens: "zorg dat je de rc car code op
 * de server moet schrijven"). De knop Program op de auto opent dit.
 *
 * <pre>
 *  RC programs on the server of Senne                           Write them in the Logic app
 *  ┌──────────────────┐ ┌─────────────────────────────────────────────────┐
 *  │ Strip miner  (on)│ │ def strip_mine():                               │
 *  │ Room             │ │     for i in range(20):                         │
 *  │ Fetcher          │ │         mine()                                  │
 *  └──────────────────┘ └─────────────────────────────────────────────────┘
 *  On the car: Strip miner                               [Load onto car] [Back]
 * </pre>
 * Links de RC programs van de server van de basis waar de auto staat; rechts de code van het programma dat je aanklikt
 * (lezen, niet veranderen: dat doe je in de app Logic; klik erin en Ctrl+A, Ctrl+C kopieert hem). Load onto car zet er
 * een kopie van op de auto (jouw keuze: verander je het later op de server, dan houdt de auto zijn kopie tot je het
 * opnieuw laadt). Staat de auto niet in een basis, of draait de server niet, dan staat hier waarom. De server stuurt de
 * lijst (zie car/RcCars.sendPrograms).
 *
 * Na de nalezing: onderaan staat een paar seconden wat het laden zei (gelukt, of waarom niet: de melding boven je
 * werkbalk valt achter dit scherm); de auto in je spel wordt gevolgd (is hij weg, dan gaat het scherm dicht; te ver weg:
 * NO SIGNAL, en Load kan niet); heeft de auto nog zijn eigen code van vóór hoofdstuk 119, dan staat die bovenaan in de
 * lijst (alleen lezen), zodat je hem kunt overtypen of kopiëren in een RC program.
 */
public class RcCarProgramScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int LIST_W = 128;
    private static final int ROW_H = 11;
    private static final int BUTTON_W = 80;
    /** Zo lang (milliseconden) staat wat het laden zei onderaan. */
    private static final long RESULT_MS = 6000;

    /** Eén regel in de lijst: een RC program van de server, of de eigen code van de auto ({@code own}). */
    private record Entry(String name, String source, boolean own) {}

    /** Wat je ziet bij een RC program op slot (hoofdstuk 130): de code zelf komt nooit naar je spel. */
    private static final String LOCKED_TEXT = """
            # This RC program is locked (it came from an SD card).
            # Load it onto your car and use it: start, stop and
            # its functions on the RC Controller all work.
            # Its code stays secret: nobody can read or change it.
            """;

    private final int entityId;
    private final String owner;
    private final boolean remote;
    private RcCarProgramsPayload data;
    private List<Entry> entries = List.of();
    private final CodeEditor preview;
    /** De regel die je aanklikte (-1 = geen). */
    private int selected = -1;
    private int scroll;
    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private @Nullable Button loadButton;
    /** Tot wanneer wat het laden zei onderaan staat. */
    private long resultUntil;
    /** Is de auto binnen bereik (in jouw spel nagekeken, elke tick)? */
    private boolean signal = true;

    public RcCarProgramScreen(RcCarProgramsPayload data) {
        super(Component.literal("RC programs"));
        this.entityId = data.entityId();
        this.owner = data.owner();
        this.remote = data.remote();
        this.preview = new CodeEditor(Minecraft.getInstance().font);
        preview.setReadOnly(true);
        preview.setCompletionData(() -> new CodeCompletion.Data(List.of(), List.of(), List.of(), ProgramKind.CAR));
        apply(data);
        // Eerst het programma dat al op de auto staat (zo zie je meteen wat hij heeft), anders de eerste regel.
        int current = indexOf(liveName());
        select(current >= 0 ? current : entries.isEmpty() ? -1 : 0);
    }

    /** Is dit het scherm van deze auto? (Dan komt een nieuwe lijst van de server hier terecht, zie ClientPayloadHandlers.) */
    public boolean isFor(int entityId) {
        return this.entityId == entityId;
    }

    /** Een nieuwe lijst van de server (na Load onto car): de keuze blijft staan, als die regel er nog is. */
    public void update(RcCarProgramsPayload fresh) {
        Entry keep = selected >= 0 && selected < entries.size() ? entries.get(selected) : null;
        apply(fresh);
        // Een kortere lijst: niet voorbij het einde gescrold blijven.
        scroll = Math.max(0, Math.min(scroll, entries.size() - 1));
        int index = keep == null ? -1 : keep.own() ? ownIndex() : indexOf(keep.name());
        select(index >= 0 ? index : entries.isEmpty() ? -1 : 0);
    }

    /** Neemt de lijst van de server over: de eigen code van de auto (als hij die heeft) bovenaan, dan de RC programs. */
    private void apply(RcCarProgramsPayload fresh) {
        data = fresh;
        List<Entry> list = new ArrayList<>();
        if (!fresh.own().isBlank()) {
            list.add(new Entry("(its own code)", fresh.own(), true));
        }
        for (RcCarProgramsPayload.Program program : fresh.programs()) {
            // Hoofdstuk 130: een RC program op slot (van een SD-kaart) laad je wel, maar zijn code krijgt je spel nooit.
            list.add(new Entry(program.name(), program.locked() ? LOCKED_TEXT : program.source(), false));
        }
        entries = list;
        if (!fresh.result().isEmpty()) {
            resultUntil = net.minecraft.Util.getMillis() + RESULT_MS;
        }
    }

    private int indexOf(String name) {
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).own() && !name.isEmpty() && entries.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private int ownIndex() {
        return !entries.isEmpty() && entries.getFirst().own() ? 0 : -1;
    }

    private void select(int index) {
        selected = index;
        preview.setText(index < 0 ? "" : entries.get(index).source());
    }

    /** De auto zoals jouw spel hem kent, of null als hij weg is. */
    private @Nullable RcCarEntity car() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(entityId) instanceof RcCarEntity car && car.isAlive() ? car : null;
    }

    /** Het RC program dat nu op de auto staat (live van de auto; leeg als het van de server van een andere basis komt). */
    private String liveName() {
        RcCarEntity car = car();
        String name = car == null ? data.current() : car.programName();
        return data.otherServer() && name.equalsIgnoreCase(data.current()) ? "" : name;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 20, 560);
        panelH = Math.min(height - 20, 320);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        preview.setBounds(left + MARGIN + LIST_W + MARGIN, top + 22, panelW - 3 * MARGIN - LIST_W, panelH - 22 - 32);
        int y = top + panelH - 26;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> back())
                .bounds(left + panelW - MARGIN - 48, y, 48, 20).build());
        loadButton = addRenderableWidget(Button.builder(Component.literal("Load onto car"), b -> load())
                .bounds(left + panelW - MARGIN - 48 - 4 - BUTTON_W, y, BUTTON_W, 20).build());
    }

    private void load() {
        if (selected >= 0 && selected < entries.size() && !entries.get(selected).own()) {
            PacketDistributor.sendToServer(new RcCarActionPayload(entityId, RcCarActionPayload.Op.LOAD,
                    entries.get(selected).name()));
        }
    }

    /** Terug naar het scherm van de auto. */
    private void back() {
        minecraft.setScreen(new com.senne.imttech.client.car.RcCarScreen(entityId, owner, remote));
    }

    @Override
    public void tick() {
        super.tick();
        RcCarEntity car = car();
        if (car == null) {
            onClose(); // opgepakt, of weg: net als het scherm van de auto zelf
            return;
        }
        double range = ImtConfig.RcCar.CONTROL_RANGE;
        signal = minecraft.player != null && minecraft.player.distanceToSqr(car) <= range * range;
        if (loadButton != null) {
            loadButton.active = signal && data.problem().isEmpty() && selected >= 0 && selected < entries.size()
                    && !entries.get(selected).own();
        }
    }

    // ---------------------------------------------------------------- Muis en toetsen

    private int listX() {
        return left + MARGIN;
    }

    private int listY() {
        return top + 22;
    }

    private int listH() {
        return panelH - 22 - 32;
    }

    /** Zoveel regels passen er in de lijst. */
    private int rows() {
        return Math.max(1, (listH() - 4) / ROW_H);
    }

    /** Zoveel regels tonen we: past niet alles, dan blijft de onderste vrij voor "1-13 of 16". */
    private int visibleRows() {
        return entries.size() > rows() ? Math.max(1, rows() - 1) : rows();
    }

    private int rowAt(double mx, double my) {
        if (mx < listX() || mx >= listX() + LIST_W || my < listY() + 2 || my >= listY() + listH()) {
            return -1;
        }
        int row = (int) ((my - listY() - 2) / ROW_H);
        int index = scroll + row;
        // Alleen een regel die er ook staat (gevonden door de nalezing: de strook onder de laatste regel koos er een die
        // je niet zag).
        return row < visibleRows() && index >= 0 && index < entries.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int row = rowAt(mx, my);
        if (row >= 0) {
            select(row);
            preview.setFocused(false);
            return true;
        }
        return preview.mouseClicked((int) mx, (int) my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        // Tekst kiezen in de code (om te kopiëren).
        return preview.mouseDragged((int) mx, (int) my) || super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        preview.mouseReleased();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx < listX() + LIST_W) {
            scroll = Math.max(0, Math.min(Math.max(0, entries.size() - visibleRows()), scroll - (int) Math.signum(scrollY)));
        } else {
            preview.mouseScrolled(scrollY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256) { // Esc: terug naar het scherm van de auto
            back();
            return true;
        }
        // In de code: alleen lezen, maar Ctrl+A en Ctrl+C werken (zo kopieer je hem naar de app Logic).
        if (preview.keyPressed(key, scan, modifiers)) {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // ---------------------------------------------------------------- Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + panelW + 4, top + panelH + 4, HudStyle.argb(245, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + panelW + 4, top + panelH + 4, 8, HudStyle.argb(220, HudStyle.ORANGE));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);
        // De titel links, de uitleg rechts; een lange naam wordt afgeknipt vóór de uitleg (ze mogen elkaar niet raken).
        String hint = "Write them in the Logic app";
        int hintW = font.width(hint);
        g.drawString(font, hint, left + panelW - MARGIN - hintW, top + 7, HudStyle.argb(180, 0x9FC9D6), false);
        String title = data.server().isEmpty() ? "RC programs" : "RC programs on the server of " + data.server();
        g.drawString(font, OsDraw.fitRight(font, title, panelW - 2 * MARGIN - hintW - 10), left + MARGIN, top + 7,
                HudStyle.argb(255, HudStyle.ORANGE), true);

        // Links: de lijst, of waarom er geen is.
        int x = listX();
        int y = listY();
        OsDraw.field(g, x, y, x + LIST_W, y + listH(), false);
        if (!data.problem().isEmpty()) {
            wrap(g, data.problem(), x + 4, y + 4, LIST_W - 8, HudStyle.RED);
        } else {
            if (data.programs().isEmpty()) {
                int ny = y + 4 + (ownIndex() >= 0 ? ROW_H + 2 : 0);
                wrap(g, "No RC programs on this server yet. In the Logic app: + then RC program.", x + 4, ny, LIST_W - 8, OsDraw.GREY);
            }
            int rows = visibleRows();
            int hover = rowAt(mouseX, mouseY);
            String onCar = liveName();
            for (int i = scroll; i < entries.size() && i < scroll + rows; i++) {
                Entry entry = entries.get(i);
                int ry = y + 3 + (i - scroll) * ROW_H;
                if (i == selected) {
                    g.fill(x + 1, ry - 1, x + LIST_W - 1, ry + ROW_H - 2, HudStyle.argb(255, 0x16303F));
                } else if (i == hover) {
                    g.fill(x + 1, ry - 1, x + LIST_W - 1, ry + ROW_H - 2, HudStyle.argb(255, 0x0F2230));
                }
                boolean mark = !entry.own() && entry.name().equalsIgnoreCase(onCar);
                String tag = mark ? " (on the car)" : "";
                int colour = entry.own() ? HudStyle.ORANGE : i == selected ? HudStyle.WHITE : 0x9ED8FF;
                g.drawString(font, OsDraw.fitRight(font, entry.name(), LIST_W - 8 - font.width(tag)), x + 4, ry,
                        HudStyle.argb(255, colour), false);
                if (mark) {
                    g.drawString(font, tag, x + LIST_W - 4 - font.width(tag), ry, HudStyle.argb(255, HudStyle.GREEN), false);
                }
            }
            // Meer dan er passen: welke je ziet, in de onderste regel (zoals FUNCTIONS 1-14 of 16 op het scherm van de auto).
            if (entries.size() > rows) {
                String more = String.format(Locale.ROOT, "%d-%d of %d  (scroll)", scroll + 1,
                        Math.min(entries.size(), scroll + rows), entries.size());
                g.drawString(font, more, x + 4, y + 3 + rows * ROW_H, HudStyle.argb(200, OsDraw.GREY), false);
            }
        }
        // Rechts: de code (alleen lezen).
        preview.render(g, mouseX, mouseY);

        // Onderaan: wat het laden net zei, of anders wat er op de auto staat (met NO SIGNAL als hij te ver is).
        int statusW = panelW - 3 * MARGIN - 48 - 4 - BUTTON_W;
        int sy = top + panelH - 20;
        boolean showResult = !data.result().isEmpty() && net.minecraft.Util.getMillis() < resultUntil;
        String status;
        int colour;
        if (!signal) {
            status = "NO SIGNAL: the car is too far away";
            colour = HudStyle.RED;
        } else if (showResult) {
            status = data.result();
            colour = data.error() ? HudStyle.RED : HudStyle.GREEN;
        } else {
            String name = liveName();
            if (!data.own().isBlank()) {
                status = "On the car: its own old code";
                colour = HudStyle.ORANGE;
            } else if (data.otherServer() && !data.current().isEmpty()) {
                status = "On the car: " + data.current() + " (from another server)";
                colour = HudStyle.ORANGE;
            } else if (name.isEmpty()) {
                status = "On the car: no RC program";
                colour = OsDraw.GREY;
            } else if (data.old() && name.equalsIgnoreCase(data.current())) {
                status = "On the car: " + name + " (older than on the server)";
                colour = HudStyle.ORANGE;
            } else {
                status = "On the car: " + name;
                colour = HudStyle.GREEN;
            }
        }
        g.drawString(font, OsDraw.fitRight(font, status, statusW), left + MARGIN, sy, HudStyle.argb(255, colour), false);
    }

    /** Tekst over een paar regels binnen deze breedte. */
    private void wrap(GuiGraphics g, String text, int x, int y, int width, int colour) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, y + i * 10, HudStyle.argb(255, colour), false);
        }
    }
}
