package com.senne.imttech.client.car;

import com.senne.imttech.bot.BotNetwork;
import com.senne.imttech.car.CarBrain;
import com.senne.imttech.car.DrillHead;
import com.senne.imttech.car.RcCarEntity;
import com.senne.imttech.car.RcCars;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.network.RcCarActionPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het scherm van een RC Car (hoofdstuk 108). Openen: sneak + rechtsklik op de auto met een lege hand, of van ver met de
 * RC Controller (de server kijkt na of je het mag, zie car/RcCars).
 *
 * <pre>
 *  Digger                                          RUNNING strip_mine
 *  Owner: Senne                                    via RC Controller
 *  Battery [##########--------]  56%
 *  Trunk   23 items
 *  Drill   Iron Drill Head  speed 25%
 *  Arm     RC Robot Arm  height 1 of 2          [^] [v] [Arm off]
 *  FUNCTIONS                        DRIVE BY HAND
 *  [ strip_mine            ]            [Forward]
 *  [ room                  ]        [Left]    [Right]
 *  [ stairs_down           ]             [Back]
 *  [ fill_up               ]        [Mine] [Up] [Down]
 *  Obsidian is too hard for its drill
 *  Name    [Digger           ] [Rename]
 *  [Start] [Stop] [Program] [Cell out] [Pick up]         [Done]
 * </pre>
 * Links staan de functies van zijn programma (elke def zonder parameters): klik er een aan en de auto voert haar uit
 * vanaf de plek waar hij staat. Start loopt zijn hoofdprogramma (when started), Stop houdt alles tegen. Rechts rijd je
 * hem met de hand, één stap per klik: zo zet je hem goed voor je een functie start.
 *
 * Drill is zijn boorkop en hoe snel die is (een betere kop zet je erop met rechtsklik op de auto, zie car/DrillHead).
 * Arm is zijn RC Robot Arm (hoofdstuk 118): hoe hoog die staat, twee pijltjes om hem met de hand te bewegen, en Arm off
 * (van dichtbij) om hem eraf te halen. Zonder arm staat er hoe je er een opzet, en kunnen Up en Down (boven en onder
 * zich boren) niet.
 *
 * Wat er nu is (naam, wat hij doet, batterij, kofferbak, boorkop, zijn functies) leest het scherm elke keer van de auto zelf:
 * dat kent jouw spel al, dus je ziet het meteen veranderen. Rijdt hij van je weg, dan blijven de knoppen werken tot
 * {@code controlRange} blokken (64); verder weg staat er NO SIGNAL. Oppakken en de cel eruit kan alleen van dichtbij.
 */
public class RcCarScreen extends Screen {
    private static final int PANEL_W = 330;
    /** Zo hoog is het paneel: het past in een venster van 240 pixels hoog (GUI-schaal 3 op 1280 x 720). */
    private static final int PANEL_H = 234;
    /** Zoveel pixels tussen twee regels bovenaan, en tussen twee knoppen in de lijsten. */
    private static final int LINE = 13;
    private static final int ROW_STEP = 19;
    private static final int MARGIN = 8;
    private static final int VALUE_X = 58;
    /** Zoveel functies passen er onder elkaar; de rest met het muiswiel. */
    private static final int ROWS = 4;
    private static final int LIST_W = 176;

    private final int entityId;
    private final String owner;
    private final boolean remote;
    private int left;
    private int top;
    private @Nullable EditBox nameBox;
    private @Nullable Button startButton;
    private @Nullable Button stopButton;
    private @Nullable Button cellButton;
    private @Nullable Button pickUpButton;
    /** De arm (hoofdstuk 118): omhoog, omlaag, eraf; en boren boven en onder hem (dat kan alleen met de arm). */
    private @Nullable Button armUpButton;
    private @Nullable Button armDownButton;
    private @Nullable Button armOffButton;
    private @Nullable Button mineUpButton;
    private @Nullable Button mineDownButton;
    /** De functies waar nu knoppen voor staan, en die knoppen zelf (van boven naar beneden). */
    private List<String> shown = List.of();
    private final List<Button> functionButtons = new ArrayList<>();
    private final List<Button> stepButtons = new ArrayList<>();
    private int scroll;

    public RcCarScreen(int entityId, String owner, boolean remote) {
        super(Component.literal("RC Car"));
        this.entityId = entityId;
        this.owner = owner;
        this.remote = remote;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** De auto zoals jouw spel hem kent, of null als hij weg is (opgepakt, of te ver). */
    private @Nullable RcCarEntity car() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(entityId) instanceof RcCarEntity car && car.isAlive()) {
            return car;
        }
        return null;
    }

    private int footerY() {
        return top + PANEL_H - 26;
    }

    /** De regels bovenaan: 0 = Battery, 1 = Trunk, 2 = Drill, 3 = Arm (hoofdstuk 118). */
    private int lineY(int line) {
        return top + 31 + line * LINE;
    }

    private int listY() {
        return top + 95;
    }

    /** De regel van de arm (hoofdstuk 118), onder die van de boorkop. */
    private int armY() {
        return lineY(3);
    }

    @Override
    protected void init() {
        RcCarEntity car = car();
        if (car == null) {
            onClose();
            return;
        }
        left = (width - PANEL_W) / 2;
        top = Math.max(4, (height - PANEL_H) / 2);
        functionButtons.clear();
        stepButtons.clear();

        // De functies: een knop per def (hooguit ROWS tegelijk; scrollen met het muiswiel).
        shown = car.functions();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - ROWS)));
        for (int row = 0; row < Math.min(ROWS, shown.size()); row++) {
            String name = shown.get(scroll + row);
            functionButtons.add(addRenderableWidget(Button.builder(Component.literal(name),
                            b -> send(RcCarActionPayload.Op.RUN, name))
                    .bounds(left + MARGIN, listY() + row * ROW_STEP, LIST_W, ROW_STEP - 2).build()));
        }

        // Met de hand: één stap per klik.
        int padX = left + MARGIN + LIST_W + 14;
        int padW = PANEL_W - 2 * MARGIN - LIST_W - 14;
        int half = (padW - 4) / 2;
        int third = (padW - 8) / 3;
        step("Forward", "forward", padX + (padW - half) / 2, listY(), half);
        step("Left", "left", padX, listY() + 18, half);
        step("Right", "right", padX + half + 4, listY() + 18, half);
        step("Back", "back", padX + (padW - half) / 2, listY() + 36, half);
        step("Mine", "mine", padX, listY() + 56, third);
        mineUpButton = step("Up", "mine_up", padX + third + 4, listY() + 56, third);
        mineDownButton = step("Down", "mine_down", padX + 2 * (third + 4), listY() + 56, third);
        // De arm (hoofdstuk 113, 118): één blok omhoog of omlaag per klik, en Arm off, rechts op de regel van de arm.
        int offW = 50;
        int armX = left + PANEL_W - MARGIN - offW - 4 - 44;
        armUpButton = addRenderableWidget(Button.builder(Component.literal("\u25B2"), b -> send(RcCarActionPayload.Op.STEP, "arm_up"))
                .bounds(armX, armY() - 3, 20, 14).build());
        armDownButton = addRenderableWidget(Button.builder(Component.literal("\u25BC"), b -> send(RcCarActionPayload.Op.STEP, "arm_down"))
                .bounds(armX + 24, armY() - 3, 20, 14).build());
        stepButtons.add(armUpButton);
        stepButtons.add(armDownButton);
        armOffButton = addRenderableWidget(Button.builder(Component.literal("Arm off"), b -> send(RcCarActionPayload.Op.ARM_OFF, ""))
                .bounds(left + PANEL_W - MARGIN - offW, armY() - 3, offW, 14).build());

        // De naam.
        nameBox = addRenderableWidget(new EditBox(font, left + VALUE_X, footerY() - 24, 130, 18, Component.literal("Name")));
        nameBox.setMaxLength(BotNetwork.MAX_NAME);
        nameBox.setValue(car.hasCustomName() ? car.getCustomName().getString() : "");
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> rename())
                .bounds(left + VALUE_X + 136, footerY() - 25, 60, 20).build());

        // Onderaan: Start, Stop, Program, de cel eruit, oppakken, Done.
        int x = left + MARGIN;
        startButton = addRenderableWidget(Button.builder(Component.literal("Start"), b -> send(RcCarActionPayload.Op.START, ""))
                .bounds(x, footerY(), 44, 20).build());
        stopButton = addRenderableWidget(Button.builder(Component.literal("Stop"), b -> send(RcCarActionPayload.Op.STOP, ""))
                .bounds(x + 48, footerY(), 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Program"), b -> send(RcCarActionPayload.Op.CODE, ""))
                .bounds(x + 92, footerY(), 58, 20).build());
        cellButton = addRenderableWidget(Button.builder(Component.literal("Cell out"), b -> send(RcCarActionPayload.Op.CELL_OUT, ""))
                .bounds(x + 154, footerY(), 54, 20).build());
        pickUpButton = addRenderableWidget(Button.builder(Component.literal("Pick up"), b -> {
            send(RcCarActionPayload.Op.PICK_UP, "");
            onClose();
        }).bounds(x + 212, footerY(), 52, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - MARGIN - 44, footerY(), 44, 20).build());
        refresh(car);
    }

    private Button step(String label, String what, int x, int y, int w) {
        Button button = addRenderableWidget(Button.builder(Component.literal(label), b -> send(RcCarActionPayload.Op.STEP, what))
                .bounds(x, y, w, 16).build());
        stepButtons.add(button);
        return button;
    }

    private void send(RcCarActionPayload.Op op, String text) {
        PacketDistributor.sendToServer(new RcCarActionPayload(entityId, op, text));
    }

    private void rename() {
        if (nameBox != null) {
            send(RcCarActionPayload.Op.RENAME, nameBox.getValue());
        }
    }

    /** Hoe ver jij van de auto staat (blokken, in het kwadraat). */
    private double distanceSqr(RcCarEntity car) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : mc.player.distanceToSqr(car);
    }

    /** Is de auto binnen het bereik van zijn scherm en de controller? */
    private boolean signal(RcCarEntity car) {
        double range = ImtConfig.RcCar.CONTROL_RANGE;
        return distanceSqr(car) <= range * range;
    }

    /**
     * Welke knoppen nu kunnen: starten alleen als hij niets doet, stroom heeft en binnen bereik is, Stop alleen als hij
     * bezig is, oppakken en de cel eruit alleen van dichtbij.
     */
    private void refresh(RcCarEntity car) {
        boolean busy = !car.job().isEmpty();
        boolean power = car.batteryFraction() > 0;
        boolean signal = signal(car);
        boolean near = distanceSqr(car) <= RcCars.REACH * RcCars.REACH;
        boolean canStart = !busy && power && signal;
        for (int row = 0; row < functionButtons.size(); row++) {
            Button button = functionButtons.get(row);
            String name = shown.get(scroll + row);
            button.active = canStart;
            button.setMessage(Component.literal(car.job().equals(name) ? "> " + name : name));
        }
        for (Button button : stepButtons) {
            button.active = canStart;
        }
        // Hoofdstuk 118: boven en onder zich boren en de arm bewegen kan alleen met een RC Robot Arm.
        boolean arm = car.hasRobotArm();
        Tooltip needsArm = arm ? null : Tooltip.create(Component.literal("Needs an RC Robot Arm (right-click the car with one)"));
        for (Button button : new Button[] {mineUpButton, mineDownButton}) {
            if (button != null) {
                button.active = canStart && arm;
                button.setTooltip(needsArm);
            }
        }
        for (Button button : new Button[] {armUpButton, armDownButton, armOffButton}) {
            if (button != null) {
                button.visible = arm;
            }
        }
        if (startButton != null) {
            startButton.active = canStart && car.hasMain();
        }
        if (stopButton != null) {
            stopButton.active = busy && signal;
        }
        // Oppakken en de cel eruit kan alleen van dichtbij: de knop zegt dat als je er met je muis op staat.
        Tooltip walk = near ? null : Tooltip.create(Component.literal("Stand next to the car for this"));
        if (cellButton != null) {
            cellButton.active = near && car.batteryFraction() >= 0;
            cellButton.setTooltip(walk);
        }
        if (pickUpButton != null) {
            pickUpButton.active = near;
            pickUpButton.setTooltip(walk);
        }
        if (armOffButton != null) {
            armOffButton.active = near;
            armOffButton.setTooltip(walk);
        }
    }

    /** Elke tick: de knoppen bijwerken, opnieuw opbouwen als zijn functies veranderden, en dicht als de auto weg is. */
    @Override
    public void tick() {
        super.tick();
        RcCarEntity car = car();
        if (car == null) {
            onClose();
            return;
        }
        if (!car.functions().equals(shown)) {
            rebuildWidgets();
            return;
        }
        refresh(car);
    }

    /** Enter in het naamvak = Rename. */
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (nameBox != null && nameBox.isFocused() && (key == 257 || key == 335)) {
            rename();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    /** Het muiswiel boven de functies: door de lijst scrollen als ze er niet allemaal op passen. */
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (shown.size() > ROWS && mx >= left && mx < left + MARGIN + LIST_W + 8 && my >= listY() - 12 && my < listY() + ROWS * ROW_STEP) {
            int next = Math.max(0, Math.min(shown.size() - ROWS, scroll - (int) Math.signum(scrollY)));
            if (next != scroll) {
                scroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ---------------------------------------------------------------- Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        // Bijna dicht: anders lees je de HUD van je bril door de bovenste regels heen.
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(245, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8, HudStyle.argb(220, HudStyle.ORANGE));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        RcCarEntity car = car();
        if (car == null) {
            return;
        }
        super.render(g, mouseX, mouseY, a);
        String name = car.hasCustomName() ? car.getCustomName().getString() : "RC Car";
        g.drawString(font, name, left + MARGIN, top + 7, HudStyle.argb(255, HudStyle.ORANGE), true);

        // Wat hij doet: rechtsboven, in een kleur.
        float battery = car.batteryFraction();
        String job = car.job();
        String state;
        int stateColour;
        if (!signal(car)) {
            state = "NO SIGNAL";
            stateColour = HudStyle.RED;
        } else if (!job.isEmpty()) {
            // Hoofdstuk 113: een bestelling van zijn Storage Interface.
            state = job.equals(CarBrain.MAIN) ? "RUNNING" : job.equals(CarBrain.MANUAL) ? "DRIVING"
                    : job.equals(CarBrain.FETCH) ? "FETCHING" : job.equals(CarBrain.STORE) ? "STORING" : "RUNNING " + job;
            stateColour = HudStyle.GREEN;
        } else if (battery < 0) {
            state = "NO CELL";
            stateColour = HudStyle.RED;
        } else if (battery <= 0) {
            state = "CELL EMPTY";
            stateColour = HudStyle.RED;
        } else {
            state = "READY";
            stateColour = HudStyle.CYAN;
        }
        state = fit(state, PANEL_W - 2 * MARGIN - font.width(name) - 12);
        g.drawString(font, state, left + PANEL_W - MARGIN - font.width(state), top + 7, HudStyle.argb(255, stateColour), true);
        g.drawString(font, owner.isEmpty() ? "No owner yet" : "Owner: " + owner, left + MARGIN, top + 20,
                HudStyle.argb(150, 0x9FC9D6), false);
        if (remote) {
            String via = "via RC Controller";
            g.drawString(font, via, left + PANEL_W - MARGIN - font.width(via), top + 20, HudStyle.argb(150, 0x9FC9D6), false);
        }

        // De batterij (zijn energiecel): een balk, groen, oranje onder 30%, rood onder 10%.
        label(g, "Battery", lineY(0));
        if (battery < 0) {
            g.drawString(font, "No Energy Cell: right-click the car with one", left + VALUE_X, lineY(0),
                    HudStyle.argb(230, HudStyle.RED), false);
        } else {
            int colour = battery < 0.1f ? HudStyle.RED : battery < 0.3f ? HudStyle.ORANGE : HudStyle.GREEN;
            int barX = left + VALUE_X;
            int barW = PANEL_W - VALUE_X - MARGIN - 34;
            g.fill(barX, lineY(0), barX + barW, lineY(0) + 8, HudStyle.argb(255, 0x1E2128));
            g.fill(barX, lineY(0), barX + Math.round(barW * Math.min(1.0f, battery)), lineY(0) + 8, HudStyle.argb(255, colour));
            g.renderOutline(barX - 1, lineY(0) - 1, barW + 2, 10, HudStyle.argb(180, 0x465460));
            String value = Math.round(battery * 100) + "%";
            g.drawString(font, value, left + PANEL_W - MARGIN - font.width(value), lineY(0), HudStyle.argb(230, HudStyle.WHITE), false);
        }

        // De kofferbak.
        label(g, "Trunk", lineY(1));
        int items = car.trunkItems();
        g.drawString(font, items == 0 ? "Empty (right-click the car to open it)" : items + (items == 1 ? " item" : " items"),
                left + VALUE_X, lineY(1), HudStyle.argb(items == 0 ? 170 : 230, HudStyle.WHITE), false);

        // De boorkop: zijn naam in de kleur van het materiaal, en hoe snel hij is.
        label(g, "Drill", lineY(2));
        DrillHead head = car.drill();
        g.drawString(font, head.displayName(), left + VALUE_X, lineY(2), HudStyle.argb(255, head.colour()), false);
        g.drawString(font, "speed " + Math.round(head.speed() * 100) + "%", left + VALUE_X + font.width(head.displayName()) + 8,
                lineY(2), HudStyle.argb(170, HudStyle.WHITE), false);

        // De arm (hoofdstuk 113, 118): of hij er een heeft, en hoe hoog die staat (links van zijn knoppen).
        label(g, "Arm", armY());
        if (car.hasRobotArm()) {
            g.drawString(font, "RC Robot Arm", left + VALUE_X, armY(), HudStyle.argb(230, HudStyle.WHITE), false);
            g.drawString(font, "height " + car.arm() + " of " + com.senne.imttech.logic.program.Op.MAX_ARM,
                    left + VALUE_X + font.width("RC Robot Arm") + 8, armY(), HudStyle.argb(170, HudStyle.WHITE), false);
        } else {
            g.drawString(font, fit("None (right-click the car with an RC Robot Arm)", PANEL_W - VALUE_X - MARGIN),
                    left + VALUE_X, armY(), HudStyle.argb(170, 0x9FC9D6), false);
        }

        // De kopjes boven de functies en de pijltjes.
        String functions = shown.size() > ROWS
                ? String.format(Locale.ROOT, "FUNCTIONS %d-%d of %d", scroll + 1, scroll + ROWS, shown.size()) : "FUNCTIONS";
        g.drawString(font, functions, left + MARGIN, listY() - 11, HudStyle.argb(170, HudStyle.ORANGE), false);
        g.drawString(font, "DRIVE BY HAND", left + MARGIN + LIST_W + 14, listY() - 11, HudStyle.argb(170, HudStyle.ORANGE), false);
        if (shown.isEmpty()) {
            g.drawString(font, "No functions yet.", left + MARGIN, listY() + 4, HudStyle.argb(200, 0x9FC9D6), false);
            g.drawString(font, "Press Program and write", left + MARGIN, listY() + 16, HudStyle.argb(200, 0x9FC9D6), false);
            g.drawString(font, "def my_job():", left + MARGIN, listY() + 28, HudStyle.argb(230, 0xC9A0FF), false);
        }

        // Het laatste dat niet lukte (of wat er mis is met zijn programma).
        String note = car.note();
        if (!note.isEmpty()) {
            g.drawString(font, fit(Character.toUpperCase(note.charAt(0)) + note.substring(1), PANEL_W - 2 * MARGIN),
                    left + MARGIN, footerY() - 35, HudStyle.argb(230, HudStyle.RED), false);
        }
        label(g, "Name", footerY() - 19);
    }

    /** Knipt tekst af (met ".." erachter) zodat hij in {@code width} pixels past. */
    private String fit(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && font.width(cut + "..") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "..";
    }

    private void label(GuiGraphics g, String text, int y) {
        g.drawString(font, text, left + MARGIN, y, HudStyle.argb(190, HudStyle.WHITE), true);
    }
}
