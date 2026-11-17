package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.network.OsActionPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * De Security OS-desktop (jouw wens, 28 september): een echte desktop met icoontjes, sleepbare
 * vensters en een taakbalk, op de beveiligingskern. Het spel loopt door terwijl hij openstaat.
 *
 * Alles wat je ziet komt van de server (een momentopname in een CompoundTag, zie {@link #update});
 * dit scherm tekent het alleen. Wat je doet (een wachtwoord zetten, een map maken) gaat als een
 * opdracht terug naar de server, die altijd zelf controleert of het mag.
 *
 * Dit bestand is de vensterbeheerder: de achtergrond, de icoontjes links, de taakbalk onderaan, en
 * het slepen, focussen en sluiten van vensters. Elke app ({@link App}) tekent zichzelf in zijn
 * venster en handelt zijn eigen kliks af. De grotere apps hebben een eigen bestand
 * ({@link FilesApp}, {@link NotesApp}); de muis en het toetsenbord gaan naar het venster bovenop.
 */
public class SecurityOsScreen extends Screen {
    /** De apps op de desktop. {@code glyph} is de letter op het icoontje. */
    public enum App {
        STATUS("Status", "i", 240, 120),
        CAMERAS("Cameras", "C", 336, 196),
        /** Sinds 19 oktober een tabel: wat breder, zodat naam, plek, toestand en knop op één regel passen. */
        DEVICES("Devices", "D", 360, 196),
        /** Je relais in groepen, en groepen in groepen (14 oktober). */
        GROUPS("Groups", "G", 340, 186),
        /** De guard robots in het gebied van de kern: aan/uit en hernoemen (hoofdstuk 21). Een tabel sinds 19 oktober. */
        ROBOTS("Robots", "R", 380, 196),
        /** De training die nu bezig is, en de rapporten van de laatste (hoofdstuk 26). */
        TRAINING("Training", "T", 340, 196),
        ACCESS("Access", "A", 280, 190),
        /** Hoofdstuk 49: de toegangsverzoeken van de Auth Consoles, met Accept en Deny. */
        REQUESTS("Requests", "?", 300, 170),
        /** Hoofdstuk 50: wat I.M.T je zei, en een bericht sturen (ook op de telefoon). */
        MESSAGES("Messages", "@", 330, 190),
        /** Hoofdstuk 40: de Computers op het netwerk van deze kern, en wat je op elk ervan mag. */
        COMPUTERS("Computers", "P", 390, 206),
        /** Sinds 25 oktober met de groepen links: breder en iets hoger (past nog net op 1280 x 720). */
        KEYCARDS("Keycards", "K", 396, 200),
        /** De diagrammen (redstone programmeren); dit venster vult het hele scherm. */
        LOGIC("Logic", "{}", 420, 240),
        /**
         * Hoe hard het datacenter werkt (CPU, brein, RAM, SSD, temperatuur), zoals Taakbeheer (8 oktober); sinds hoofdstuk 64
         * ook elk rack met zijn naam en zijn eigen cijfers, dus iets groter.
         */
        MONITOR("Monitor", "M", 380, 206),
        /** Backups naar een ander datacenter van jezelf (8 oktober). */
        BACKUP("Backup", "B", 340, 196),
        /** De zone: kubussen waarin alleen jij en je vrienden bouwen en breken (hoofdstuk 10). */
        ZONE("Zone", "Z", 380, 206),
        FILES("Files", "F", 300, 190),
        NOTES("Notes", "N", 320, 196),
        LOGS("Logs", "L", 330, 200),
        /** Hoofdstuk 54: rechts de apps op het bureaublad (aan/uit, in twee kolommen), dus breder. */
        SETTINGS("Settings", "S", 400, 200);

        final String label;
        final String glyph;
        final int w;
        final int h;

        App(String label, String glyph, int w, int h) {
            this.label = label;
            this.glyph = glyph;
            this.w = w;
            this.h = h;
        }
    }

    /** Eén open venster: welke app, waar het staat, hoe groot, en hoe ver het is doorgescrold. */
    private static final class Win {
        final App app;
        int x;
        int y;
        final int w;
        final int h;
        int scroll;

        Win(App app, int x, int y, int w, int h) {
            this.app = app;
            this.w = w;
            this.h = h;
            this.x = x;
            this.y = y;
        }
    }

    /** Het vak binnen een venster waar een app in tekent (onder de titelbalk). */
    record Area(int x, int y, int w, int h) {}

    private static final int TITLE_H = 13;
    /**
     * Elk venster ligt een laagje (diepte) hoger dan het venster eronder. Tekst met een schaduw
     * ligt in Minecraft net iets naar voren; zonder die lagen zou je hem door een venster erboven
     * heen zien.
     */
    private static final int LAYER = 10;
    private static final int TASKBAR_H = 16;
    private static final int ICON = 30;
    private static final int LINE = 10;
    private static final int BG = 0x0A1016;
    private static final int PANEL = 0x12202B;
    private static final int BAR = 0x0E1A24;

    private final BlockPos core;
    private final boolean remote;
    private CompoundTag data = new CompoundTag();

    private final List<Win> windows = new ArrayList<>();
    private @Nullable Win dragging;
    private int dragDx;
    private int dragDy;
    /** Het venster waar de muisknop in werd ingedrukt (voor slepen binnen een app). */
    private @Nullable Win pressedIn;

    /** De tekst die je in het wachtwoordvak typt (alleen Settings gebruikt dit nu). */
    private boolean editingPassword;
    private String passwordText = "";

    /**
     * De apps met een eigen bestand. Ze bestaan altijd (ook als hun venster dicht is), zodat ze de
     * gegevens van de server bijhouden.
     */
    private final FilesApp files = new FilesApp(this);
    private final NotesApp notes = new NotesApp(this);
    private final DevicesApp devices = new DevicesApp(this);
    private final GroupsApp groups = new GroupsApp(this);
    private final RobotsApp robots = new RobotsApp(this);
    private final TrainingApp training = new TrainingApp(this);
    private final CamerasApp cameras = new CamerasApp(this);
    private final AccessApp access = new AccessApp(this);
    private final RequestsApp requests = new RequestsApp(this);
    private final MessagesApp messages = new MessagesApp(this);
    private final ComputersApp computers = new ComputersApp(this);
    private final KeycardsApp keycards = new KeycardsApp(this);
    private final LogicApp logic = new LogicApp(this, net.minecraft.client.Minecraft.getInstance().font);
    private final MonitorApp monitor = new MonitorApp(this);
    private final BackupApp backup = new BackupApp(this);
    private final ZoneApp zone = new ZoneApp(this);
    /** Kwam de eerste momentopname al binnen? (Dan weten we of er al een wachtwoord is.) */
    private boolean gotFirstSnapshot;
    /**
     * Ging de desktop uit met de knop Shut down (19 oktober)? Dan onthoudt de kern de vensters niet: de volgende keer
     * start hij met alleen Status. Anders (Esc) staat alles de volgende keer weer zoals je het liet.
     */
    private boolean shuttingDown;

    /** Een melding van de server (rechts in de taakbalk) en tot wanneer hij blijft staan. */
    private String notice = "";
    private boolean noticeError;
    private long noticeUntil;

    public SecurityOsScreen(BlockPos core, boolean remote) {
        super(Component.literal("Security OS"));
        this.core = core;
        this.remote = remote;
    }

    public BlockPos core() {
        return core;
    }

    /**
     * Een nieuwe momentopname van de server. De mappenlijst zit er alleen in als hij veranderde, een
     * geopend bestand en een melding maar één keer: die geven we meteen door.
     */
    public void update(CompoundTag snapshot) {
        this.data = snapshot;
        boolean first = !gotFirstSnapshot;
        if (first) {
            gotFirstSnapshot = true;
            // De vensters van de vorige keer weer op hun plek (19 oktober), zoals bij een pc die aan bleef.
            restoreDesktop(snapshot.getList("desk", Tag.TAG_COMPOUND));
            // Jouw eigen kern, nog zonder wachtwoord: meteen Settings open, klaar om er een te typen.
            if (snapshot.getBoolean("isOwner") && !snapshot.getBoolean("hasPassword") && shows(App.SETTINGS)) {
                openApp(App.SETTINGS);
                editingPassword = true;
            }
        }
        // Hoofdstuk 54: de apps die de eigenaar van het bureaublad haalde (Settings).
        hidden.clear();
        for (Tag app : snapshot.getList("hidden", Tag.TAG_STRING)) {
            hidden.add(app.getAsString());
        }
        // Hoofdstuk 40: op een Computer alleen de apps die de kern toestaat (dat kan ook veranderen terwijl je zit te kijken).
        boolean closedAny = false;
        for (Win win : List.copyOf(windows)) {
            if (!shows(win.app)) {
                closeWindow(win);
                closedAny = true;
            }
        }
        // Alleen de eerste keer (of als de kern net een app wegnam) begint hij met een venster open. Sluit je zelf het
        // laatste venster, dan blijft het bureaublad leeg (hoofdstuk 54, jouw notitie: Status kwam steeds terug).
        List<App> visible = visibleApps();
        if ((first || closedAny) && windows.isEmpty() && !visible.isEmpty()) {
            openApp(visible.getFirst());
        }
        if (snapshot.contains("fs")) {
            files.update(snapshot.getList("fs", Tag.TAG_COMPOUND));
        }
        if (snapshot.contains("logic")) {
            logic.update(snapshot.getCompound("logic"));
        }
        // Hoofdstuk 130: de SD-kaart (alleen als er iets veranderde) voor Logic en Files, en de code van een open
        // programma op de kaart waar je om vroeg.
        if (snapshot.contains("sd")) {
            CompoundTag sd = snapshot.getCompound("sd");
            logic.updateCard(sd);
            files.updateCard(sd);
        }
        if (snapshot.contains("sdCode")) {
            logic.updateCardCode(snapshot.getCompound("sdCode"));
        }
        if (snapshot.contains("file")) {
            CompoundTag file = snapshot.getCompound("file");
            Win win = openApp(App.NOTES);
            notes.load(font != null ? font : net.minecraft.client.Minecraft.getInstance().font, contentArea(win),
                    file.getString("p"), file.getString("c"), file.getBoolean("s"));
        }
        if (snapshot.contains("notice")) {
            CompoundTag message = snapshot.getCompound("notice");
            showNotice(message.getString("x"), message.getBoolean("err"));
        }
    }

    /**
     * Zet de vensters terug die de kern onthield (het onderste eerst), op de plek waar ze stonden (binnen het scherm).
     * Onthield hij niets (de eerste keer, of na Shut down), dan blijft alleen Status open.
     */
    private void restoreDesktop(net.minecraft.nbt.ListTag saved) {
        List<Win> restored = new ArrayList<>();
        for (Tag element : saved) {
            CompoundTag window = (CompoundTag) element;
            App app;
            try {
                app = App.valueOf(window.getString("a"));
            } catch (IllegalArgumentException e) {
                continue; // een app die er niet (meer) is
            }
            if (restored.stream().anyMatch(win -> win.app == app) || !shows(app)) {
                continue;
            }
            Win win = placed(app, restored.size());
            if (app != App.LOGIC) {
                win.x = Math.max(0, Math.min(width - 20, window.getInt("x")));
                win.y = Math.max(0, Math.min(height - TASKBAR_H - 6, window.getInt("y")));
            }
            restored.add(win);
        }
        if (restored.isEmpty()) {
            return;
        }
        windows.clear();
        windows.addAll(restored);
        focus(windows.getLast());
    }

    /** Wat de kern moet onthouden: elk open venster (het onderste eerst) met de app en waar het staat. */
    private net.minecraft.nbt.ListTag desktopToSave() {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (Win win : windows) {
            CompoundTag window = new CompoundTag();
            window.putString("a", win.app.name());
            window.putInt("x", win.x);
            window.putInt("y", win.y);
            list.add(window);
        }
        return list;
    }

    /** Een melding rechts in de taakbalk, een paar seconden lang. */
    void showNotice(String text, boolean error) {
        notice = text;
        noticeError = error;
        noticeUntil = Util.getMillis() + 4000;
    }

    /**
     * Stuurt een opdracht naar de server (die controleert altijd zelf of het mag). Op een Computer waar het niet mag (een app
     * op View), zeggen we dat meteen en sturen we niets.
     */
    void send(String op, CompoundTag args) {
        if (!rights().allows(op, args)) {
            String app = com.senne.imttech.security.os.DesktopRights.appOf(op, args);
            showNotice(rights().shows(app) ? "View only on this computer" : "This computer does not allow that", true);
            return;
        }
        PacketDistributor.sendToServer(new OsActionPayload(core, op, args));
    }

    /**
     * Hoofdstuk 40: wat je op deze desktop mag. Op de kern of met de laptop alles; op een Computer stuurt de server mee wat
     * de kern daar toestaat (de server kijkt het zelf ook na; dit is om icoontjes en knoppen te verbergen).
     */
    com.senne.imttech.security.os.DesktopRights rights() {
        return data.contains("rights") ? com.senne.imttech.security.os.DesktopRights.load(data.getCompound("rights"))
                : com.senne.imttech.security.os.DesktopRights.FULL;
    }

    /** Hoofdstuk 54: de apps die de eigenaar van dit bureaublad haalde (leeg op een Computer of de telefoon). */
    private final java.util.Set<String> hidden = new java.util.HashSet<>();

    /** Staat deze app op deze desktop? (Niet op een Computer die hem niet toestaat, en niet als de eigenaar hem weghaalde.) */
    boolean shows(App app) {
        return rights().shows(app.name()) && (app == App.SETTINGS || !hidden.contains(app.name()));
    }

    /** Mag je deze app hier ook bedienen (niet alleen bekijken)? */
    boolean mayUse(App app) {
        return rights().mayUse(app.name());
    }

    /** De apps die op deze desktop staan, in hun vaste volgorde (de icoontjes links). */
    private List<App> visibleApps() {
        List<App> apps = new ArrayList<>();
        for (App app : App.values()) {
            if (shows(app)) {
                apps.add(app);
            }
        }
        return apps;
    }

    /** De naam van de Computer waarop deze desktop draait, of "" (de kern zelf, of de laptop). */
    private String computerName() {
        return data.getString("pc");
    }

    /** Ben jij de eigenaar van dit systeem? (Alleen die ziet en zet valstrikken.) */
    boolean isOwner() {
        return data.getBoolean("isOwner");
    }

    FilesApp files() {
        return files;
    }

    /** De laatste momentopname van de server (de apps lezen hier hun gegevens uit). */
    CompoundTag data() {
        return data;
    }

    /**
     * Alleen voor de screenshot-rondleiding (VisualCheck): het Files-venster openen in deze map, met
     * dit bestand gekozen, en eventueel het rechtsklik-menu erbij.
     */
    public void tourFiles(String folder, String select, boolean menu) {
        Win win = openApp(App.FILES);
        files.tourShow(contentArea(win), folder, select, menu);
    }

    /** Alleen voor de screenshot-rondleiding: de app Keycards, met een naam en kleur al ingevuld. */
    public void tourKeycards(String label, int colour) {
        openApp(App.KEYCARDS);
        keycards.tourFill(label, colour);
    }

    /** Alleen voor de screenshot-rondleiding: in de app Keycards links deze groep gekozen (null = All cards). */
    public void tourKeycardGroup(@Nullable String group) {
        openApp(App.KEYCARDS);
        keycards.tourSelect(group);
    }

    /** Alleen voor de screenshot-rondleiding: in de app Keycards het naamvak van een nieuwe groep of een nieuwe naam. */
    public void tourKeycardInput(boolean rename, String text) {
        openApp(App.KEYCARDS);
        keycards.tourInput(rename, text);
    }

    /** Alleen voor de screenshot-rondleiding: de app Logic met deze code (en eventueel Help open). */
    public void tourLogic(String name, String code, boolean help) {
        openApp(App.LOGIC);
        logic.tourCode(name, code, help);
    }

    /** Alleen voor de screenshot-rondleiding: de app Logic met het bewaarde programma met deze naam open (hoofdstuk 63). */
    public void tourLogicOpen(String name) {
        openApp(App.LOGIC);
        logic.tourOpen(name);
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 130): de app Logic op de pagina van de SD-kaart. */
    public void tourLogicCard() {
        openApp(App.LOGIC);
        logic.tourCard();
    }

    /** Alleen voor de screenshot-rondleiding: de app Logic met een programma van de SD-kaart gekozen. */
    public void tourLogicCardProgram(String name) {
        openApp(App.LOGIC);
        logic.tourCardProgram(name);
    }

    /** Alleen voor de screenshot-rondleiding: de app Logic terug in de editor. */
    public void tourLogicEditor() {
        logic.tourEditor();
    }

    /** Alleen voor de screenshot-rondleiding: de app Logic in de blokjes, met eventueel een blokje gekozen. */
    public void tourLogicBlocks(@Nullable com.senne.imttech.logic.program.Op select) {
        logic.tourBlocks(select);
    }

    /** Alleen voor de screenshot-rondleiding: Ctrl+Spatie in de code van de app Logic. */
    public void tourLogicComplete(String lineText, String after) {
        logic.tourComplete(lineText, after);
    }

    /** Alleen voor de screenshot-rondleiding: in de app Logic na + kiezen wat voor programma (hoofdstuk 23). */
    public void tourLogicChooseKind() {
        openApp(App.LOGIC);
        logic.tourChooseKind();
    }

    /** Alleen voor de screenshot-rondleiding: in de app Logic een nieuw programma van deze soort (met Help). */
    public void tourLogicNew(com.senne.imttech.logic.program.ProgramKind kind) {
        openApp(App.LOGIC);
        logic.tourNew(kind);
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 125): in de app Logic een nieuw, leeg programma (zonder Help). */
    public void tourLogicNewEmpty(com.senne.imttech.logic.program.ProgramKind kind) {
        openApp(App.LOGIC);
        logic.tourNewEmpty(kind);
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 125): in de app Logic + > Global script. */
    public void tourLogicNewGlobal() {
        openApp(App.LOGIC);
        logic.tourNewGlobal();
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 125): in de app Logic het paneel Templates open of dicht. */
    public void tourLogicTemplates(boolean show, @Nullable String name) {
        openApp(App.LOGIC);
        logic.tourTemplates(show, name);
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 125): in de app Logic de knop Save as template. */
    public void tourLogicSaveTemplate() {
        logic.tourSaveTemplate();
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 125): in de app Logic een template aanklikken. */
    public void tourLogicLoadTemplate(String name) {
        logic.tourLoadTemplate(name);
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 125): een template van de rondleiding weggooien. */
    public void tourLogicForgetTemplate(com.senne.imttech.logic.program.ProgramKind kind, String name) {
        logic.tourForgetTemplate(kind, name);
    }

    /** Alleen voor de screenshot-rondleiding: in de app Robots de lijst met programma's voor de eerste robot (hoofdstuk 23). */
    public void tourRobotsProgram() {
        openApp(App.ROBOTS);
        robots.tourChooseProgram();
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 119): in de app Robots de eerste RC Car, eventueel met zijn keuzelijst. */
    public void tourRobotsCar(boolean chooser) {
        openApp(App.ROBOTS);
        robots.tourChooseCar(chooser);
    }

    /** Alleen voor de screenshot-rondleiding: in de app Robots "Sure?" op Delete van de eerste robot ver weg (18 oktober). */
    public void tourRobotsAskDelete() {
        openApp(App.ROBOTS);
        robots.tourAskDelete();
    }

    /** Alleen voor de screenshot-rondleiding: de app Robots, gescrold tot de eerste robot in een squad (die is gekozen). */
    public void tourRobotsSquad() {
        openApp(App.ROBOTS);
        robots.tourSquad();
    }

    /** Alleen voor de screenshot-rondleiding: in de app Logic de Help scrollen tot dit kopje (hoofdstuk 23). */
    public void tourLogicHelpAt(String header) {
        openApp(App.LOGIC);
        logic.tourHelpAt(header);
    }

    /** Alleen voor de screenshot-rondleiding: de knop Save van de app Logic. */
    public void tourSaveLogic() {
        logic.tourSave();
    }

    /** Alleen voor de screenshot-rondleiding: de vraag "Sure?" bij Delete blocked in de app Keycards. */
    public void tourAskDeleteBlocked() {
        openApp(App.KEYCARDS);
        keycards.tourAskDeleteBlocked();
    }

    /** Alleen voor de screenshot-rondleiding: in de app Devices de eerste lezer een andere naam aan het geven. */
    public void tourRenameInput(String name) {
        openApp(App.DEVICES);
        devices.tourRename(name);
    }

    /** Alleen voor de screenshot-rondleiding: die nieuwe naam bewaren (Save). */
    public void tourSaveRename() {
        devices.tourSave();
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 38): de app Devices met de poorten van de eerste switch open. */
    public void tourSwitchPorts() {
        openApp(App.DEVICES);
        devices.tourPorts();
        devices.tourScroll("NETWORK SWITCHES");
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 40): de app Computers, met deze computer gekozen. */
    public void tourComputers(BlockPos computer) {
        openApp(App.COMPUTERS);
        computers.tourSelect(computer);
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 40): de app Logs, met alleen de entries. */
    public void tourLogsEntries() {
        openApp(App.LOGS);
        entriesOnly = true;
        flaggedOnly = false;
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 138): de app Logs, met alles of alleen de gemarkeerde regels. */
    public void tourLogsFlagged(boolean only) {
        openApp(App.LOGS);
        entriesOnly = false;
        flaggedOnly = only;
    }

    /** Alleen voor de screenshot-rondleiding (hoofdstuk 38): de app Devices, naar beneden tot dit kopje ("BUTTONS"). */
    public void tourDevicesAt(String header) {
        openApp(App.DEVICES);
        devices.tourScroll(header);
    }

    /** Alleen voor de screenshot-rondleiding: de app Zone, met deze kubus gekozen. */
    public void tourZone(int box) {
        openApp(App.ZONE);
        zone.tourSelect(box);
    }

    /** Alleen voor de screenshot-rondleiding: de app Groups, met deze groep gekozen (of niets). */
    public void tourGroups(@Nullable String group) {
        openApp(App.GROUPS);
        groups.tourSelect(group);
    }

    /** Alleen voor de screenshot-rondleiding: een app openen (of bovenop zetten). */
    /** Alleen voor de showcase en de screenshots: het eerste toegangsverzoek in de app Requests beantwoorden (hoofdstuk 49). */
    public void tourRequestAnswer(boolean accepted) {
        requests.tourAnswer(accepted);
    }

    public void tourOpen(App app) {
        openApp(app);
    }

    /** Alleen voor de rondleiding en de showcase: de app Monitor met het rack met deze naam gekozen (null = de hele server). */
    public void tourMonitorRack(@Nullable String name) {
        openApp(App.MONITOR);
        monitor.tourSelect(name);
    }

    /** Alleen voor de plaatjes van How it works (hoofdstuk 63): alle vensters dicht, zodat je de desktop ziet. */
    public void tourCloseAll() {
        new java.util.ArrayList<>(windows).forEach(this::closeWindow);
    }

    /** Alleen voor de screenshot-rondleiding: de bewakingsmuur, of het grote beeld van deze camera. */
    public void tourCameras(@Nullable BlockPos camera) {
        openApp(App.CAMERAS);
        cameras.tourBig(camera);
    }

    /** Alleen voor de screenshot-rondleiding: het grote beeld van de eerste robot met een camera (hoofdstuk 24). */
    public void tourRobotCamera() {
        tourRobotCamera(null);
    }

    /** Hetzelfde, voor de robot met deze naam. */
    public void tourRobotCamera(@Nullable String name) {
        openApp(App.CAMERAS);
        cameras.tourBigRobot(name);
    }

    /** Alleen voor de screenshot-rondleiding: het grote beeld van deze camera, met {@code typed} in het naamvak. */
    public void tourRenameCamera(BlockPos camera, String typed) {
        tourCameras(camera);
        cameras.tourRename(typed);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (windows.isEmpty()) {
            openApp(App.STATUS);
        }
    }

    // ================================================================ Vensters openen/focussen

    /** Opent een app (of zet zijn venster bovenop als het al open is). */
    private Win openApp(App app) {
        for (Win win : windows) {
            if (win.app == app) {
                focus(win);
                return win;
            }
        }
        Win win = placed(app, windows.size());
        windows.add(win);
        focus(win);
        return win;
    }

    /**
     * Een nieuw venster voor deze app, op zijn gewone plek als er al {@code open} vensters open staan: rechts van de
     * icoontjes, elk nieuw venster een stukje verder, maar altijd helemaal in beeld.
     */
    private Win placed(App app, int open) {
        int offset = open * 16;
        int left = iconsRight() + 8;
        int x = Math.max(0, Math.min(left + offset, width - app.w - 4));
        int y = Math.min(30 + offset, Math.max(24, height - app.h - TASKBAR_H - 4));
        int w = app.w;
        int h = app.h;
        if (app == App.LOGIC) {
            // Schermvullend (code heeft ruimte nodig); via de taakbalk kom je bij de andere apps.
            x = 4;
            y = 17;
            w = Math.max(260, width - 8);
            h = Math.max(150, height - TASKBAR_H - 20);
        }
        return new Win(app, x, y, w, h);
    }

    /** Zet een venster bovenop (achteraan in de lijst = als laatste getekend = bovenop). */
    private void focus(Win win) {
        windows.remove(win);
        windows.add(win);
        if (win.app != App.NOTES) {
            notes.blur();
        }
        if (win.app != App.ACCESS) {
            access.blur();
        }
        if (win.app != App.MESSAGES) {
            messages.blur();
        }
        if (win.app != App.CAMERAS) {
            cameras.blur();
        }
        if (win.app != App.KEYCARDS) {
            keycards.blur();
        }
        if (win.app != App.LOGIC) {
            logic.blur();
        }
        if (win.app != App.DEVICES) {
            devices.blur();
        }
        if (win.app != App.GROUPS) {
            groups.blur();
        }
        if (win.app != App.ROBOTS) {
            robots.blur();
        }
        if (win.app != App.ZONE) {
            zone.blur();
        }
        if (win.app != App.COMPUTERS) {
            computers.blur();
        }
    }

    private @Nullable Win focused() {
        return windows.isEmpty() ? null : windows.getLast();
    }

    /** Het bovenste venster op dit punt, of null. */
    private @Nullable Win windowAt(int mx, int my) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            Win win = windows.get(i);
            if (mx >= win.x && mx < win.x + win.w && my >= win.y && my < win.y + win.h) {
                return win;
            }
        }
        return null;
    }

    /** Het vak binnen een venster waar zijn app in tekent. */
    private static Area contentArea(Win win) {
        return new Area(win.x + 6, win.y + TITLE_H + 3, win.w - 12, win.h - TITLE_H - 6);
    }

    private void closeWindow(Win win) {
        windows.remove(win);
        if (win.app == App.SETTINGS) {
            editingPassword = false;
        }
        if (win.app == App.CAMERAS) {
            cameras.blur(); // een half getypte naam niet bewaren
        }
        if (win.app == App.DEVICES) {
            devices.blur(); // hier ook niet
        }
        if (win.app == App.GROUPS) {
            groups.blur(); // ook geen half getypte groepsnaam
        }
        if (win.app == App.ROBOTS) {
            robots.blur(); // geen half getypte robotnaam
        }
        if (win.app == App.ZONE) {
            zone.blur(); // half getypte getallen niet bewaren
        }
        if (win.app == App.COMPUTERS) {
            computers.blur(); // een half getypte naam of code niet bewaren
        }
        if (win.app == App.NOTES) {
            notes.save(); // wat je typte, gaat niet verloren
            notes.blur();
        }
    }

    // ================================================================ Muis

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        // Taakbalk onderaan.
        if (my >= height - TASKBAR_H) {
            if (mx >= shutDownX() && mx < width - 4) {
                shutDown();
                return true;
            }
            int tx = 4;
            for (Win win : windows) {
                int chip = 8 + font.width(win.app.label);
                if (mx >= tx && mx < tx + chip) {
                    focus(win);
                    return true;
                }
                tx += chip + 4;
            }
            return true;
        }
        // Vensters, van boven (achteraan) naar onder.
        Win win = windowAt(mx, my);
        if (win != null) {
            focus(win);
            // Het kruisje rechtsboven.
            if (mx >= win.x + win.w - TITLE_H && my < win.y + TITLE_H) {
                closeWindow(win);
                return true;
            }
            // De titelbalk: slepen.
            if (my < win.y + TITLE_H) {
                dragging = win;
                dragDx = mx - win.x;
                dragDy = my - win.y;
                return true;
            }
            pressedIn = win;
            clickContent(win, mx, my, button);
            return true;
        }
        // Icoontjes links (onder de vensters).
        App icon = iconAt(mx, my);
        if (icon != null) {
            openApp(icon);
            return true;
        }
        // Op de lege desktop: stop met typen.
        editingPassword = false;
        notes.blur();
        return true;
    }

    /**
     * Waar het icoontje van deze app staat: {x, y}. Onder elkaar langs de linkerkant; past het niet
     * meer boven de taakbalk, dan begint een nieuwe kolom.
     */
    private int[] iconPos(int index) {
        int perColumn = Math.max(1, (height - TASKBAR_H - 24) / (ICON + 14));
        return new int[] {6 + (index / perColumn) * (ICON + 18), 24 + (index % perColumn) * (ICON + 14)};
    }

    /** Waar de kolommen met icoontjes ophouden (nieuwe vensters openen rechts daarvan). */
    private int iconsRight() {
        int[] last = iconPos(Math.max(0, visibleApps().size() - 1));
        return last[0] + ICON;
    }

    private @Nullable App iconAt(int mx, int my) {
        List<App> visible = visibleApps();
        for (App app : visible) {
            int[] pos = iconPos(visible.indexOf(app));
            if (mx >= pos[0] && mx < pos[0] + ICON && my >= pos[1] && my < pos[1] + ICON) {
                return app;
            }
        }
        return null;
    }

    /** Een klik binnen de inhoud van een venster (de app zelf). */
    private void clickContent(Win win, int mx, int my, int button) {
        switch (win.app) {
            case FILES -> files.mouseClicked(font, contentArea(win), mx, my, button);
            case NOTES -> notes.mouseClicked(font, contentArea(win), mx, my, button);
            case DEVICES -> devices.mouseClicked(font, contentArea(win), mx, my);
            case GROUPS -> groups.mouseClicked(font, contentArea(win), mx, my);
            case ROBOTS -> robots.mouseClicked(font, contentArea(win), mx, my);
            case TRAINING -> training.mouseClicked(font, contentArea(win), mx, my);
            case CAMERAS -> cameras.mouseClicked(font, contentArea(win), mx, my);
            case ACCESS -> access.mouseClicked(font, contentArea(win), mx, my);
            case REQUESTS -> requests.mouseClicked(font, contentArea(win), mx, my);
            case MESSAGES -> messages.mouseClicked(font, contentArea(win), mx, my);
            case KEYCARDS -> keycards.mouseClicked(font, contentArea(win), mx, my);
            case LOGIC -> logic.mouseClicked(font, contentArea(win), mx, my, button);
            case BACKUP -> backup.mouseClicked(font, contentArea(win), mx, my);
            case MONITOR -> monitor.mouseClicked(font, contentArea(win), mx, my);
            case ZONE -> zone.mouseClicked(font, contentArea(win), mx, my);
            case COMPUTERS -> computers.mouseClicked(font, contentArea(win), mx, my);
            case LOGS -> {
                Area area = contentArea(win);
                String label = entriesOnly ? LOGS_ENTRIES : LOGS_ALL;
                int fx = area.x() + OsDraw.buttonWidth(font, label) + 4;
                if (OsDraw.inside(mx, my, area.x(), area.y(), OsDraw.buttonWidth(font, label), OsDraw.BUTTON_H)) {
                    entriesOnly = !entriesOnly;
                    flaggedOnly = false;
                    win.scroll = 0;
                } else if (OsDraw.inside(mx, my, fx, area.y(), OsDraw.buttonWidth(font, flaggedLabel()), OsDraw.BUTTON_H)) {
                    // Hoofdstuk 138: alleen de gemarkeerde regels (van elke soort: dan niet ook "Show: entries").
                    flaggedOnly = !flaggedOnly;
                    if (flaggedOnly) {
                        entriesOnly = false;
                    }
                    win.scroll = 0;
                }
            }
            case SETTINGS -> {
                int cx = win.x + 6;
                int fieldY = win.y + TITLE_H + 60;
                if (mx >= cx && mx < cx + 140 && my >= fieldY && my < fieldY + 14) {
                    editingPassword = true;
                    return;
                }
                if (mx >= cx + 146 && mx < cx + 216 && my >= fieldY && my < fieldY + 14) {
                    submitPassword();
                    return;
                }
                if (clickAuth(cx, win.y + TITLE_H + 3, mx, my) || clickApps(cx, win.y + TITLE_H + 3, mx, my)) {
                    return;
                }
                editingPassword = false;
            }
            default -> { }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null) {
            dragging.x = Math.max(0, Math.min(width - 20, (int) mouseX - dragDx));
            dragging.y = Math.max(0, Math.min(height - TASKBAR_H - 6, (int) mouseY - dragDy));
            return true;
        }
        // Slepen binnen een app (een bestand naar een map, tekst selecteren).
        if (pressedIn != null && pressedIn.app == App.FILES) {
            return files.mouseDragged((int) mouseX, (int) mouseY);
        }
        if (pressedIn != null && pressedIn.app == App.NOTES) {
            return notes.mouseDragged((int) mouseX, (int) mouseY, button, dragX, dragY);
        }
        if (pressedIn != null && pressedIn.app == App.LOGIC) {
            return logic.mouseDragged((int) mouseX, (int) mouseY);
        }
        if (pressedIn != null && pressedIn.app == App.GROUPS) {
            return groups.mouseDragged((int) mouseX, (int) mouseY);
        }
        if (pressedIn != null && pressedIn.app == App.KEYCARDS) {
            return keycards.mouseDragged((int) mouseX, (int) mouseY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        Win win = pressedIn;
        pressedIn = null;
        if (win != null && win.app == App.FILES) {
            files.mouseReleased(contentArea(win), (int) mouseX, (int) mouseY);
        } else if (win != null && win.app == App.NOTES) {
            notes.mouseReleased((int) mouseX, (int) mouseY, button);
        } else if (win != null && win.app == App.CAMERAS) {
            cameras.mouseReleased((int) mouseX, (int) mouseY);
        } else if (win != null && win.app == App.LOGIC) {
            logic.mouseReleased((int) mouseX, (int) mouseY);
        } else if (win != null && win.app == App.GROUPS) {
            groups.mouseReleased(contentArea(win), (int) mouseX, (int) mouseY);
        } else if (win != null && win.app == App.KEYCARDS) {
            keycards.mouseReleased(contentArea(win), (int) mouseX, (int) mouseY);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Het venster onder de muis scrolt (of anders het venster bovenop).
        Win win = windowAt((int) mouseX, (int) mouseY);
        if (win == null) {
            win = focused();
        }
        if (win == null) {
            return true;
        }
        switch (win.app) {
            case FILES -> files.mouseScrolled(scrollY);
            case NOTES -> notes.mouseScrolled((int) mouseX, (int) mouseY, scrollY);
            case DEVICES -> devices.mouseScrolled(scrollY);
            case GROUPS -> groups.mouseScrolled(scrollY);
            case ROBOTS -> robots.mouseScrolled(scrollY);
            case TRAINING -> training.mouseScrolled(scrollY);
            case CAMERAS -> cameras.mouseScrolled(scrollY);
            case ACCESS -> access.mouseScrolled(scrollY);
            case REQUESTS -> requests.mouseScrolled(scrollY);
            case MESSAGES -> messages.mouseScrolled(scrollY);
            case KEYCARDS -> keycards.mouseScrolled(contentArea(win), (int) mouseX, scrollY);
            case LOGIC -> logic.mouseScrolled(contentArea(win), (int) mouseX, (int) mouseY, scrollY);
            case MONITOR -> monitor.mouseScrolled(scrollY);
            case BACKUP -> backup.mouseScrolled(scrollY);
            case COMPUTERS -> computers.mouseScrolled(scrollY);
            default -> win.scroll = Math.max(0, win.scroll - (int) Math.signum(scrollY) * 3);
        }
        return true;
    }

    // ================================================================ Toetsen

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        Win top = focused();
        if (top != null && top.app == App.FILES && files.charTyped(codePoint, modifiers)
                || top != null && top.app == App.NOTES && notes.charTyped(codePoint, modifiers)
                || top != null && top.app == App.ACCESS && access.charTyped(codePoint, modifiers)
                || top != null && top.app == App.MESSAGES && messages.charTyped(codePoint, modifiers)
                || top != null && top.app == App.CAMERAS && cameras.charTyped(codePoint, modifiers)
                || top != null && top.app == App.DEVICES && devices.charTyped(codePoint, modifiers)
                || top != null && top.app == App.GROUPS && groups.charTyped(codePoint, modifiers)
                || top != null && top.app == App.ROBOTS && robots.charTyped(codePoint, modifiers)
                || top != null && top.app == App.KEYCARDS && keycards.charTyped(codePoint, modifiers)
                || top != null && top.app == App.LOGIC && logic.charTyped(codePoint, modifiers)
                || top != null && top.app == App.ZONE && zone.charTyped(codePoint, modifiers)
                || top != null && top.app == App.COMPUTERS && computers.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (editingPassword && passwordText.length() < 8
                && (codePoint >= 'a' && codePoint <= 'z' || codePoint >= '0' && codePoint <= '9')) {
            passwordText += codePoint;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Eerst de app in het venster bovenop (zo sluit Esc in een tekstvak alleen dat vak).
        Win top = focused();
        if (top != null && top.app == App.FILES && files.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.NOTES && notes.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.ACCESS && access.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.MESSAGES && messages.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.CAMERAS && cameras.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.DEVICES && devices.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.GROUPS && groups.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.ROBOTS && robots.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.KEYCARDS && keycards.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.LOGIC && logic.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.ZONE && zone.keyPressed(keyCode, scanCode, modifiers)
                || top != null && top.app == App.COMPUTERS && computers.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (editingPassword) {
            if (keyCode == 259 && !passwordText.isEmpty()) { // backspace
                passwordText = passwordText.substring(0, passwordText.length() - 1);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // enter
                submitPassword();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submitPassword() {
        if (passwordText.isEmpty()) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putString("pw", passwordText);
        send("passwd", args);
        passwordText = "";
        editingPassword = false;
    }

    @Override
    public void removed() {
        // Een notitie die nog niet bewaard is, eerst bewaren; dan de sessie op de server netjes
        // sluiten (hij sluit anders vanzelf als je wegloopt).
        notes.save();
        logic.saveIfValid(); // een diagram dat klopt, bewaren we vanzelf (zoals een notitie)
        if (!shuttingDown) {
            // De pc blijft aan (19 oktober): de kern onthoudt welke vensters waar open staan.
            CompoundTag args = new CompoundTag();
            args.put("w", desktopToSave());
            send("desk_save", args);
        }
        send("close", new CompoundTag());
        super.removed();
    }

    /** De knop Shut down rechts in de taakbalk: de tekst, en waar hij begint. */
    private static final String SHUT_DOWN = "Shut down";

    private int shutDownX() {
        return width - 4 - OsDraw.buttonWidth(font, SHUT_DOWN);
    }

    /**
     * De knop Shut down (19 oktober, jouw notitie: "tenzij de pc wordt uitgezet met de powerknop in de gui"): de desktop
     * gaat uit en vergeet zijn vensters; de volgende keer start hij met alleen Status. Je beveiliging blijft gewoon aan.
     */
    private void shutDown() {
        shuttingDown = true;
        send("desk_off", new CompoundTag());
        onClose();
    }

    // ================================================================ Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, HudStyle.argb(255, BG));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // Kopbalk: de naam van het systeem, en of je op afstand zit.
        String host = data.getString("host");
        String ip = data.getString("ip");
        g.fill(0, 0, width, 14, HudStyle.argb(255, BAR));
        String system = "SECURITY OS  " + host + (ip.isEmpty() ? "" : "  " + ip);
        g.drawString(font, system, 6, 3, HudStyle.argb(255, HudStyle.CYAN), false);
        boolean online = data.getBoolean("online");
        String power = online ? "ONLINE" : "OFFLINE";
        g.drawString(font, power, width - font.width(power) - 6, 3, HudStyle.argb(255, online ? HudStyle.GREEN : HudStyle.RED), false);
        // In het midden: op afstand (de laptop) of op een Computer (hoofdstuk 40). Past het daar niet naast de naam en het
        // IP (een smal scherm), dan erachter, en eventueel ingekort.
        String tag = data.getBoolean("phone") ? "I.M.T PHONE" : remote ? "REMOTE SESSION"
                : computerName().isEmpty() ? "" : "COMPUTER: " + computerName();
        if (!tag.isEmpty()) {
            int left = 6 + font.width(system) + 12;
            int room = width - font.width(power) - 18 - left;
            String shown = OsDraw.fitRight(font, tag, Math.max(0, room));
            int x = Math.max(left, (width - font.width(shown)) / 2);
            g.drawString(font, shown, x, 3, HudStyle.argb(255, remote ? HudStyle.ORANGE : HudStyle.GREEN), false);
        }
        // Icoontjes links (op een Computer alleen de apps die de kern toestaat).
        List<App> visible = visibleApps();
        for (App app : visible) {
            int[] pos = iconPos(visible.indexOf(app));
            int ix = pos[0];
            int iy = pos[1];
            boolean open = windows.stream().anyMatch(win -> win.app == app);
            g.fill(ix, iy, ix + ICON, iy + ICON, HudStyle.argb(open ? 255 : 180, open ? PANEL : 0x0E1822));
            HudStyle.brackets(g, ix, iy, ix + ICON, iy + ICON, 5, HudStyle.argb(200, open ? HudStyle.CYAN : 0x33505F));
            g.drawString(font, app.glyph, ix + (ICON - font.width(app.glyph)) / 2, iy + ICON / 2 - 4,
                    HudStyle.argb(255, HudStyle.WHITE), false);
            HudStyle.smallText(g, font, app.label, ix + ICON / 2, iy + ICON + 1, HudStyle.argb(220, HudStyle.WHITE));
        }
        // De vensters, onderste eerst, elk een laagje hoger. Alleen het venster onder de muis krijgt
        // de muis (voor wat oplicht).
        Win under = windowAt(mouseX, mouseY);
        for (int i = 0; i < windows.size(); i++) {
            Win win = windows.get(i);
            boolean mouseHere = win == under;
            g.pose().pushPose();
            g.pose().translate(0, 0, (i + 1) * LAYER);
            renderWindow(g, win, mouseHere ? mouseX : -1, mouseHere ? mouseY : -1, partialTick);
            g.pose().popPose();
        }
        // Taakbalk onderaan, boven alle vensters.
        g.pose().pushPose();
        g.pose().translate(0, 0, (windows.size() + 1) * LAYER);
        g.fill(0, height - TASKBAR_H, width, height, HudStyle.argb(255, BAR));
        int tx = 4;
        Win top = focused();
        for (Win win : windows) {
            int chip = 8 + font.width(win.app.label);
            boolean active = win == top;
            g.fill(tx, height - TASKBAR_H + 2, tx + chip, height - 2, HudStyle.argb(active ? 255 : 160, active ? PANEL : 0x0E1822));
            g.drawString(font, win.app.label, tx + 4, height - TASKBAR_H + 5, HudStyle.argb(255, active ? HudStyle.CYAN : HudStyle.WHITE), false);
            tx += chip + 4;
        }
        // Helemaal rechts de knop Shut down (19 oktober); een melding van de server staat ervoor.
        OsDraw.button(g, font, shutDownX(), height - TASKBAR_H + 2, SHUT_DOWN, true, HudStyle.RED);
        if (!notice.isEmpty() && Util.getMillis() < noticeUntil) {
            renderNotice(g, tx);
        }
        g.pose().popPose();
    }

    /** Zoveel regels heeft een lange melding hooguit. */
    private static final int NOTICE_MAX_LINES = 3;

    /**
     * De melding van de server (hoofdstuk 116, jouw notitie: "deze tekst komt uit de GUI"). Past hij in de taakbalk
     * tussen de vensterknoppen ({@code taskbarEnd}) en Shut down, dan staat hij daar, zoals altijd. Anders komt hij in een
     * kader óver de taakbalk (links van Shut down; de vensterknoppen zijn dan even bedekt), en is hij nog te lang, dan
     * over hooguit drie regels die naar boven groeien: zo loopt een lange melding nooit meer van je scherm af, en bedekt
     * hij je vensters zo weinig mogelijk (de knoppen onderaan de app Zone blijven te zien).
     */
    private void renderNotice(GuiGraphics g, int taskbarEnd) {
        int colour = HudStyle.argb(255, noticeError ? HudStyle.RED : HudStyle.GREEN);
        int right = shutDownX() - 8;
        if (font.width(notice) <= right - taskbarEnd - 4) {
            g.drawString(font, notice, right - font.width(notice), height - TASKBAR_H + 5, colour, false);
            return;
        }
        List<String> lines = OsDraw.wrap(font, notice, Math.max(40, right - 14), NOTICE_MAX_LINES);
        int textW = 0;
        for (String line : lines) {
            textW = Math.max(textW, font.width(line));
        }
        int x1 = right + 4;
        int x0 = Math.max(2, x1 - textW - 10);
        int y1 = height - 2;
        int y0 = Math.min(height - TASKBAR_H + 2, y1 - lines.size() * 10 - 3);
        g.fill(x0, y0, x1, y1, HudStyle.argb(250, BAR));
        HudStyle.brackets(g, x0, y0, x1, y1, 4, colour);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x0 + 5, y1 - 2 - (lines.size() - i) * 10 + 1, colour, false);
        }
    }

    private void renderWindow(GuiGraphics g, Win win, int mouseX, int mouseY, float partialTick) {
        boolean active = win == focused();
        g.fill(win.x, win.y, win.x + win.w, win.y + win.h, HudStyle.argb(255, PANEL));
        HudStyle.brackets(g, win.x, win.y, win.x + win.w, win.y + win.h, 7,
                HudStyle.argb(active ? 235 : 150, active ? HudStyle.CYAN : 0x33505F));
        // Titelbalk.
        g.fill(win.x, win.y, win.x + win.w, win.y + TITLE_H, HudStyle.argb(255, active ? 0x16303F : 0x111E28));
        g.drawString(font, win.app.label, win.x + 5, win.y + 3, HudStyle.argb(255, active ? HudStyle.CYAN : HudStyle.WHITE), false);
        // Hoofdstuk 40: op een Computer waar deze app op View staat, zie je dat in de titel.
        if (!mayUse(win.app) && !com.senne.imttech.security.os.DesktopRights.LOOK_ONLY.contains(win.app.name())) {
            g.drawString(font, "view only", win.x + 10 + font.width(win.app.label), win.y + 3, HudStyle.argb(255, OsDraw.GREY), false);
        }
        // Kruisje.
        g.drawString(font, "x", win.x + win.w - 9, win.y + 3, HudStyle.argb(255, HudStyle.RED), false);
        // Inhoud, afgeknipt.
        Area area = contentArea(win);
        g.enableScissor(win.x + 2, win.y + TITLE_H, win.x + win.w - 2, win.y + win.h - 2);
        switch (win.app) {
            case STATUS -> renderStatus(g, area.x(), area.y());
            case DEVICES -> devices.render(g, font, area, mouseX, mouseY, partialTick);
            case GROUPS -> groups.render(g, font, area, mouseX, mouseY, partialTick);
            case ROBOTS -> robots.render(g, font, area, mouseX, mouseY, partialTick);
            case TRAINING -> training.render(g, font, area, mouseX, mouseY);
            case CAMERAS -> cameras.render(g, font, area, mouseX, mouseY, partialTick);
            case ACCESS -> access.render(g, font, area, mouseX, mouseY, partialTick);
            case REQUESTS -> requests.render(g, font, area, mouseX, mouseY, partialTick);
            case MESSAGES -> messages.render(g, font, area, mouseX, mouseY, partialTick);
            case KEYCARDS -> keycards.render(g, font, area, mouseX, mouseY, partialTick);
            case LOGIC -> logic.render(g, font, area, mouseX, mouseY, partialTick);
            case MONITOR -> monitor.render(g, font, area, mouseX, mouseY, partialTick);
            case BACKUP -> backup.render(g, font, area, mouseX, mouseY, partialTick);
            case ZONE -> zone.render(g, font, area, mouseX, mouseY, partialTick);
            case COMPUTERS -> computers.render(g, font, area, mouseX, mouseY, partialTick);
            case FILES -> files.render(g, font, area, mouseX, mouseY, partialTick);
            case NOTES -> notes.render(g, font, area, mouseX, mouseY, partialTick);
            case LOGS -> renderLogs(g, area.x(), area.y(), area.w(), area.h(), win.scroll);
            case SETTINGS -> renderSettings(g, area.x(), area.y());
        }
        g.disableScissor();
    }

    private void renderStatus(GuiGraphics g, int cx, int cy) {
        line(g, cx, cy, "Host:  " + data.getString("host"), HudStyle.WHITE);
        line(g, cx, cy + LINE, "IP:    " + data.getString("ip"), HudStyle.WHITE);
        line(g, cx, cy + LINE * 2, "SSH:   port " + data.getInt("port"), HudStyle.WHITE);
        line(g, cx, cy + LINE * 3, "Owner: " + data.getString("owner"), HudStyle.WHITE);
        boolean pw = data.getBoolean("hasPassword");
        line(g, cx, cy + LINE * 4, "Password: " + (pw ? "set" : "NOT SET"), pw ? HudStyle.GREEN : HudStyle.ORANGE);
        boolean online = data.getBoolean("online");
        line(g, cx, cy + LINE * 5, "Power: " + (online ? "online" : "offline"), online ? HudStyle.GREEN : HudStyle.RED);
        // De server: draait hij, en zo niet, waarom niet.
        CompoundTag server = data.getCompound("server");
        if (server.getBoolean("online")) {
            int racks = server.getInt("racks");
            line(g, cx, cy + LINE * 6, "Server: online, " + racks + (racks == 1 ? " rack" : " racks"), HudStyle.GREEN);
        } else {
            line(g, cx, cy + LINE * 6, "Server: " + server.getString("problem"), HudStyle.ORANGE);
        }
        // De autorisatiecode van dit moment (hoofdstuk 49): voor iedereen die hier mag kijken.
        CompoundTag auth = data.getCompound("auth");
        if (!auth.getString("code").isEmpty()) {
            line(g, cx, cy + LINE * 7, "Auth code: " + auth.getString("code") + "   (new in " + clock(auth.getInt("left")) + ")", HudStyle.CYAN);
        }
    }

    /** Seconden als m:ss. */
    private static String clock(int seconds) {
        return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }

    /**
     * Hoofdstuk 40 (jouw voorbeeld: "kijken wie er waar binnengaat"): bovenaan de knop Show: all / Show: entries. Met
     * entries alleen de regels van lezers, codepanelen, knoppen en hendels (wie wat gebruikte), en wie op een computer of
     * de kern inlogde.
     */
    private boolean entriesOnly;
    private static final String LOGS_ALL = "Show: all";
    private static final String LOGS_ENTRIES = "Show: entries";

    /** Hoofdstuk 138: alleen de regels die een diagram markeerde (when logged: met flag), met de knop "Flagged". */
    private boolean flaggedOnly;

    /** De regels van het logboek die je nu ziet: alles, alleen de entries, of (hoofdstuk 138) alleen de gemarkeerde. */
    private List<Tag> shownLog() {
        List<Tag> log = data.getList("log", Tag.TAG_COMPOUND);
        if (!entriesOnly && !flaggedOnly) {
            return log;
        }
        List<Tag> entries = new ArrayList<>();
        for (Tag element : log) {
            CompoundTag line = (CompoundTag) element;
            String kind = line.getString("k");
            boolean entry = kind.equals("ENTRY") || kind.equals("LOGIN");
            if (flaggedOnly ? !line.getString("f").isEmpty() : entry) {
                entries.add(element);
            }
        }
        return entries;
    }

    /** Hoeveel regels er gemarkeerd zijn (de knop "Flagged 3"). */
    private int flaggedCount() {
        int count = 0;
        for (Tag element : data.getList("log", Tag.TAG_COMPOUND)) {
            if (!((CompoundTag) element).getString("f").isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private String flaggedLabel() {
        return "Flagged " + flaggedCount();
    }

    /** Eén regel op het scherm: de tijd (alleen op de eerste regel van een melding), de tekst, en de kleur. */
    private record LogRow(String time, String text, int colour) {}

    /**
     * De app Logs. Hoofdstuk 138: een lange regel breekt af binnen het venster (eerst liep hij er rechts uit), met de tijd in
     * een eigen kolom links; een gemarkeerde regel is rood, met de reden ervoor ("[Wrong password] ..."). Het muiswiel
     * schuift per regel op het scherm.
     */
    private void renderLogs(GuiGraphics g, int cx, int cy, int cw, int ch, int scroll) {
        String show = entriesOnly ? LOGS_ENTRIES : LOGS_ALL;
        OsDraw.button(g, font, cx, cy, show, true, entriesOnly ? HudStyle.ORANGE : HudStyle.CYAN);
        int fx = cx + OsDraw.buttonWidth(font, show) + 4;
        int flagged = flaggedCount();
        int fw = OsDraw.button(g, font, fx, cy, flaggedLabel(), true, flaggedOnly || flagged > 0 ? HudStyle.RED : OsDraw.GREY);
        if (flaggedOnly) {
            g.fill(fx + 3, cy + OsDraw.BUTTON_H - 2, fx + fw - 3, cy + OsDraw.BUTTON_H - 1, HudStyle.argb(255, HudStyle.RED));
        }
        cy += OsDraw.BUTTON_H + 4;
        ch -= OsDraw.BUTTON_H + 4;
        List<Tag> log = shownLog();
        int timeW = 0;
        for (Tag element : log) {
            timeW = Math.max(timeW, font.width(((CompoundTag) element).getInt("ago") + "s"));
        }
        timeW += font.width("  ");
        int textW = Math.max(40, cw - timeW);
        List<LogRow> rows = new ArrayList<>();
        for (Tag element : log) {
            CompoundTag entry = (CompoundTag) element;
            String flag = entry.getString("f");
            String body = (flag.isEmpty() ? "" : "[" + flag + "] ") + entry.getString("x");
            int colour = flag.isEmpty() ? logColour(entry.getString("k")) : HudStyle.RED;
            List<String> parts = OsDraw.wrap(font, body, textW, 4);
            for (int i = 0; i < parts.size(); i++) {
                rows.add(new LogRow(i == 0 ? entry.getInt("ago") + "s" : "", parts.get(i), colour));
            }
        }
        int visible = Math.max(1, ch / LINE);
        int total = rows.size();
        int first = Math.max(0, total - visible - scroll);
        int end = Math.min(total, first + visible);
        int y = cy;
        for (int i = first; i < end; i++) {
            LogRow row = rows.get(i);
            if (!row.time().isEmpty()) {
                line(g, cx, y, row.time(), row.colour());
            }
            line(g, cx + timeW, y, row.text(), row.colour());
            y += LINE;
        }
        if (log.isEmpty()) {
            line(g, cx, cy, OsDraw.fitRight(font, flaggedOnly ? "(nothing flagged yet)"
                    : entriesOnly ? "(nobody used a reader, panel or computer yet)" : "(no events yet)", cw), 0x5A6B72);
        }
    }

    private void renderSettings(GuiGraphics g, int cx, int cy) {
        line(g, cx, cy, "System settings", HudStyle.CYAN);
        line(g, cx, cy + 16, "IP:   " + data.getString("ip"), HudStyle.WHITE);
        line(g, cx, cy + 26, "Host: " + data.getString("host"), HudStyle.WHITE);
        line(g, cx, cy + 36, "SSH port: " + data.getInt("port"), HudStyle.WHITE);
        line(g, cx, cy + 47, data.getBoolean("hasPassword") ? "Change password:" : "Set a password:", HudStyle.WHITE);
        // Het invoervak.
        int fieldY = cy + 57;
        g.fill(cx, fieldY, cx + 140, fieldY + 14, HudStyle.argb(255, 0x0A141B));
        HudStyle.brackets(g, cx, fieldY, cx + 140, fieldY + 14, 4,
                HudStyle.argb(255, editingPassword ? HudStyle.CYAN : 0x33505F));
        String shown = passwordText + (editingPassword && (System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "");
        g.drawString(font, shown, cx + 4, fieldY + 3, HudStyle.argb(255, HudStyle.WHITE), false);
        // De knop.
        g.fill(cx + 146, fieldY, cx + 216, fieldY + 14, HudStyle.argb(255, 0x16303F));
        HudStyle.brackets(g, cx + 146, fieldY, cx + 216, fieldY + 14, 4, HudStyle.argb(255, HudStyle.CYAN));
        String button = data.getBoolean("hasPassword") ? "Change" : "Set";
        g.drawString(font, button, cx + 146 + (70 - font.width(button)) / 2, fieldY + 3, HudStyle.argb(255, HudStyle.CYAN), false);
        line(g, cx, cy + 82, "4-8 chars, a-z and 0-9 only.", 0x8A9BA0);
        if (!data.getBoolean("hasPassword")) {
            line(g, cx, cy + 96, "No password yet: anyone nearby can open this.", HudStyle.ORANGE);
        }
        // De autorisatiecodes (hoofdstuk 49): hoe lang, om de hoeveel minuten, en of de vorige nog mag.
        CompoundTag auth = data.getCompound("auth");
        line(g, cx, cy + AUTH_TITLE, "Authorization code: " + auth.getString("code") + "   (new in " + clock(auth.getInt("left")) + ")", HudStyle.CYAN);
        int[] b = authButtons(cx);
        int row = cy + AUTH_ROW1;
        line(g, cx, row + 2, "Length", HudStyle.WHITE);
        OsDraw.button(g, font, b[0], row, "-", true, HudStyle.CYAN);
        line(g, b[0] + b[5] + 4, row + 2, String.valueOf(auth.getInt("len")), HudStyle.WHITE);
        OsDraw.button(g, font, b[1], row, "+", true, HudStyle.CYAN);
        line(g, b[6], row + 2, "Every", HudStyle.WHITE);
        OsDraw.button(g, font, b[2], row, "-", true, HudStyle.CYAN);
        line(g, b[2] + b[5] + 4, row + 2, auth.getInt("min") + " min", HudStyle.WHITE);
        OsDraw.button(g, font, b[3], row, "+", true, HudStyle.CYAN);
        int row2 = cy + AUTH_ROW2;
        boolean old = auth.getBoolean("old");
        line(g, cx, row2 + 2, "Old codes", HudStyle.WHITE);
        OsDraw.button(g, font, b[4], row2, old ? "ON" : "OFF", true, old ? HudStyle.GREEN : HudStyle.RED);
        line(g, b[4] + OsDraw.buttonWidth(font, "OFF") + 6, row2 + 2, old ? "the previous code still works (it is logged)"
                : "only the current code works", 0x8A9BA0);
        // Hoofdstuk 54 (jouw notitie): rechts de apps op dit bureaublad, met een vinkje: aan = geïnstalleerd. Alleen op de
        // kern zelf (en de laptop), en alleen de eigenaar.
        if (data.contains("hidden") && isOwner()) {
            int x0 = cx + APPS_X;
            line(g, x0, cy, "Apps on this desktop", HudStyle.CYAN);
            int appRow = 0;
            for (App app : App.values()) {
                if (app == App.SETTINGS) {
                    continue;
                }
                int x = x0 + (appRow / APPS_PER_COLUMN) * APPS_COLUMN;
                int y = cy + APPS_Y + (appRow % APPS_PER_COLUMN) * APPS_ROW;
                boolean on = !hidden.contains(app.name());
                g.fill(x, y, x + 7, y + 7, HudStyle.argb(255, on ? HudStyle.CYAN : OsDraw.FIELD));
                HudStyle.brackets(g, x, y, x + 7, y + 7, 2, HudStyle.argb(255, on ? HudStyle.CYAN : OsDraw.DIM));
                line(g, x + 11, y, app.label, on ? HudStyle.WHITE : OsDraw.GREY);
                appRow++;
            }
        }
    }

    /** Waar de lijst met apps in Settings staat (vanaf de linkerkant en de bovenkant van de inhoud): twee kolommen van negen. */
    private static final int APPS_X = 236;
    private static final int APPS_Y = 12;
    private static final int APPS_ROW = 11;
    private static final int APPS_PER_COLUMN = 9;
    private static final int APPS_COLUMN = 78;

    /** Een klik op een app in de lijst van Settings (hoofdstuk 54): weghalen of terugzetten. Geeft true als het er een was. */
    private boolean clickApps(int cx, int cy, int mx, int my) {
        if (!data.contains("hidden") || !isOwner()) {
            return false;
        }
        int x0 = cx + APPS_X;
        int row = 0;
        for (App app : App.values()) {
            if (app == App.SETTINGS) {
                continue;
            }
            int x = x0 + (row / APPS_PER_COLUMN) * APPS_COLUMN;
            int y = cy + APPS_Y + (row % APPS_PER_COLUMN) * APPS_ROW;
            if (OsDraw.inside(mx, my, x, y - 1, 11 + font.width(app.label), APPS_ROW)) {
                CompoundTag args = new CompoundTag();
                args.putString("app", app.name());
                args.putBoolean("on", !hidden.contains(app.name())); // "on" = weghalen
                send("app_hide", args);
                return true;
            }
            row++;
        }
        return false;
    }

    /** Waar de regels van de autorisatiecodes in Settings staan (vanaf de bovenkant van de inhoud). */
    private static final int AUTH_TITLE = 112;
    private static final int AUTH_ROW1 = 124;
    private static final int AUTH_ROW2 = 139;

    /** De knoppen van de autorisatiecodes: x van [-] en [+] bij Length, [-] en [+] bij Every, de ON/OFF, de knopbreedte, en x van "Every". */
    private int[] authButtons(int cx) {
        int bw = OsDraw.buttonWidth(font, "+");
        int lenMinus = cx + font.width("Length") + 4;
        int lenPlus = lenMinus + bw + 4 + font.width("8") + 4;
        int everyX = lenPlus + bw + 10;
        int minMinus = everyX + font.width("Every") + 4;
        int minPlus = minMinus + bw + 4 + font.width("60 min") + 4;
        int oldToggle = cx + font.width("Old codes") + 4;
        return new int[] {lenMinus, lenPlus, minMinus, minPlus, oldToggle, bw, everyX};
    }

    /** Een klik op de knoppen van de autorisatiecodes in Settings; geeft true als het er een was. */
    private boolean clickAuth(int cx, int cy, int mx, int my) {
        CompoundTag auth = data.getCompound("auth");
        int[] b = authButtons(cx);
        int len = auth.getInt("len");
        int min = auth.getInt("min");
        boolean old = auth.getBoolean("old");
        int row = cy + AUTH_ROW1;
        if (OsDraw.inside(mx, my, b[0], row, b[5], OsDraw.BUTTON_H)) {
            sendAuth(len - 1, min, old);
        } else if (OsDraw.inside(mx, my, b[1], row, b[5], OsDraw.BUTTON_H)) {
            sendAuth(len + 1, min, old);
        } else if (OsDraw.inside(mx, my, b[2], row, b[5], OsDraw.BUTTON_H)) {
            sendAuth(len, min - 1, old);
        } else if (OsDraw.inside(mx, my, b[3], row, b[5], OsDraw.BUTTON_H)) {
            sendAuth(len, min + 1, old);
        } else if (OsDraw.inside(mx, my, b[4], cy + AUTH_ROW2, OsDraw.buttonWidth(font, "OFF"), OsDraw.BUTTON_H)) {
            sendAuth(len, min, !old);
        } else {
            return false;
        }
        return true;
    }

    private void sendAuth(int length, int minutes, boolean old) {
        CompoundTag args = new CompoundTag();
        args.putInt("len", Math.max(4, Math.min(8, length)));
        args.putInt("min", Math.max(1, Math.min(60, minutes)));
        args.putBoolean("old", old);
        send("auth_set", args);
    }

    private void line(GuiGraphics g, int x, int y, String text, int rgb) {
        g.drawString(font, text, x, y, HudStyle.argb(255, rgb), false);
    }

    private static int logColour(String type) {
        return switch (type) {
            case "HACK", "TRAP", "LOGIN_FAIL", "ALARM" -> HudStyle.RED;
            case "LOGIC" -> 0xB89CFF;
            case "OWNER", "LOGIN" -> HudStyle.GREEN;
            case "SHOT", "ENTRY", "HEAT" -> HudStyle.ORANGE;
            case "DEVICE", "ZONE" -> HudStyle.CYAN;
            case "TRAINING" -> 0x8FE3FF;
            default -> HudStyle.WHITE;
        };
    }
}
