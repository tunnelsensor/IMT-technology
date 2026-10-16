package com.senne.starktech.client.os;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.client.os.camera.CameraFeeds;
import com.senne.starktech.security.CameraBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Het Cameras-venster van de Stark OS-desktop: de bewakingsmuur (jouw wens, 28 september). Een
 * raster met een live beeldje van elke camera in het gebied van de kern; klik er een aan voor het
 * grote beeld, waar je de camera draait en kantelt (pijltjes, of het beeld verslepen) en hem een
 * andere naam geeft (de knop Rename: typ de naam, Enter bewaart, Esc laat het zoals het was).
 *
 * Sinds hoofdstuk 24 staan hier ook de guard robots met een Camera-module (met "ROBOT" erbij): hun beeld
 * kijkt mee met hun hoofd. Een robot draai je niet van hieruit (dat doet zijn programma), dus bij zijn grote
 * beeld staat alleen Back. Zolang je naar een robot kijkt, weet hij dat ({@code read(cameraPin)} is dan true):
 * de app zegt het om de seconde tegen de server.
 *
 * Elk beeldje heeft een CRT-randje (scanlijnen, hoekjes, de naam en een knipperend REC) zoals het
 * camerabeeld van de verkenningsdrone. Heeft een camera geen stroom of staat hij uit: "NO SIGNAL";
 * ligt hij buiten het stuk wereld dat je spel klaar heeft staan: "OUT OF RANGE".
 *
 * De beelden zelf maakt CameraFeeds (vlak voor elk frame, een paar keer per seconde).
 */
final class CamerasApp {
    private static final int TILE_W = 104;
    private static final int TILE_H = 58;
    private static final int GAP = 6;
    /** Hoeveel graden één klik op een pijltje draait. */
    private static final float STEP = 10f;
    /** Hoeveel graden één pixel slepen draait. */
    private static final float DRAG = 0.4f;
    /** Waar de knop Rename staat, onder de pijltjes (ten opzichte van de bovenkant van het beeld). */
    private static final int RENAME_DY = 106;
    /** Zoveel graden kijkt de camera van een drone naar beneden. */
    private static final float DRONE_PITCH = 20.0f;

    /** Zo vaak (ms) zegt de app tegen de server dat je naar een robot kijkt. */
    private static final long WATCH_EVERY = 1000;

    private final StarkOsScreen screen;
    /** Het grote beeld: van welke camera (zie {@link #key}), of null = de muur met alle beeldjes. */
    private @Nullable String big;
    private int scroll;
    // Slepen in het grote beeld
    private boolean dragging;
    private int dragStartX;
    private int dragStartY;
    /** Ben je de naam van de camera in het grote beeld aan het typen? */
    private boolean renaming;
    private @Nullable EditBox nameBox;
    /** Wanneer we de server voor het laatst zeiden dat je naar deze robot kijkt. */
    private final Map<Integer, Long> watched = new HashMap<>();

    CamerasApp(StarkOsScreen screen) {
        this.screen = screen;
    }

    private List<CompoundTag> cameras() {
        List<CompoundTag> list = new ArrayList<>();
        for (Tag element : screen.data().getList("cameras", Tag.TAG_COMPOUND)) {
            list.add((CompoundTag) element);
        }
        return list;
    }

    private static BlockPos posOf(CompoundTag camera) {
        return new BlockPos(camera.getInt("x"), camera.getInt("y"), camera.getInt("z"));
    }

    /** Is dit het beeld van een robot (hoofdstuk 24)? */
    private static boolean isRobot(CompoundTag camera) {
        return camera.contains("robot");
    }

    /** Waar we een camera aan herkennen: een camerablok aan zijn plek, een robot (die loopt) aan zijn nummer. */
    private static String key(CompoundTag camera) {
        return isRobot(camera) ? "r" + camera.getInt("robot") : blockKey(posOf(camera));
    }

    private static String blockKey(BlockPos pos) {
        return "b" + pos.asLong();
    }

    private @Nullable CompoundTag find(String key) {
        for (CompoundTag camera : cameras()) {
            if (key(camera).equals(key)) {
                return camera;
            }
        }
        return null;
    }

    private EditBox nameBox() {
        if (nameBox == null) {
            nameBox = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 10, Component.literal("Camera name"));
            nameBox.setBordered(false);
            nameBox.setMaxLength(CameraBlockEntity.MAX_NAME);
            nameBox.setFilter(value -> value.indexOf('§') < 0); // geen kleurcodes
            nameBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
        }
        return nameBox;
    }

    /** Het grote beeld: hoe breed (de rest van het venster is voor de knoppen). */
    private static int bigWidth(StarkOsScreen.Area a) {
        return Math.min(256, a.w() - 70);
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my, float partialTick) {
        if (big != null) {
            CompoundTag camera = find(big);
            if (camera != null) {
                renderBig(g, font, a, camera, mx, my, partialTick);
                return;
            }
            showWall(); // de camera (of robot) is weg
        }
        List<CompoundTag> cameras = cameras();
        String head = cameras.isEmpty() ? "No cameras in this network's area yet."
                : cameras.size() + (cameras.size() == 1 ? " camera" : " cameras") + "   click one for the big view";
        g.drawString(font, head, a.x(), a.y(), HudStyle.argb(255, OsDraw.GREY), false);
        int columns = Math.max(1, (a.w() + GAP) / (TILE_W + GAP));
        int top = a.y() + 12;
        int rowsVisible = Math.max(1, (a.y() + a.h() - top + GAP) / (TILE_H + GAP));
        int rows = (cameras.size() + columns - 1) / columns;
        scroll = Math.max(0, Math.min(scroll, rows - rowsVisible));
        for (int i = 0; i < cameras.size(); i++) {
            int row = i / columns - scroll;
            if (row < 0 || row >= rowsVisible) {
                continue;
            }
            int x = a.x() + (i % columns) * (TILE_W + GAP);
            int y = top + row * (TILE_H + GAP);
            boolean hover = OsDraw.inside(mx, my, x, y, TILE_W, TILE_H);
            renderFeed(g, font, cameras.get(i), x, y, TILE_W, TILE_H, false, hover, partialTick);
        }
    }

    private void renderBig(GuiGraphics g, Font font, StarkOsScreen.Area a, CompoundTag camera, int mx, int my, float partialTick) {
        int fw = bigWidth(a);
        int fh = fw * 9 / 16;
        int fy = a.y() + 12;
        boolean robot = isRobot(camera);
        if (renaming && !robot) {
            // Bovenaan het vak waar je de nieuwe naam typt (in plaats van de naam).
            EditBox box = nameBox();
            OsDraw.field(g, a.x(), a.y() - 2, a.x() + fw, a.y() + 10, true);
            box.setX(a.x() + 3);
            box.setY(a.y());
            box.setWidth(fw - 6);
            box.render(g, mx, my, partialTick);
        } else {
            BlockPos pos = robot ? robotPos(camera) : posOf(camera);
            g.drawString(font, camera.getString("name") + (robot ? camera.getBoolean("drone") ? "  DRONE" : "  ROBOT" : "") + "   " + pos.getX() + " " + pos.getY() + " "
                    + pos.getZ(), a.x(), a.y(), HudStyle.argb(255, HudStyle.CYAN), false);
        }
        renderFeed(g, font, camera, a.x(), fy, fw, fh, true, false, partialTick);
        String hint = robot ? "You look through its eyes: its program decides where it looks"
                : renaming ? "Enter = save   Esc = cancel   empty = number"
                : "Drag the image or use the arrow keys to aim";
        g.drawString(font, OsDraw.fitLeft(font, hint, a.w()), a.x(), fy + fh + 4, HudStyle.argb(255, OsDraw.GREY), false);
        // Rechts: terug, en (alleen bij een camerablok) de pijltjes en een andere naam geven.
        int px = a.x() + fw + 8;
        OsDraw.button(g, font, px, fy, "Back", true, HudStyle.CYAN);
        if (robot) {
            return;
        }
        for (Arrow arrow : Arrow.values()) {
            OsDraw.button(g, font, px + arrow.dx, fy + arrow.dy, arrow.label, true, HudStyle.CYAN);
        }
        g.drawString(font, String.format("pan  %+.0f", camera.getFloat("pan")), px, fy + 80, HudStyle.argb(255, HudStyle.WHITE), false);
        g.drawString(font, String.format("tilt %+.0f", camera.getFloat("tilt")), px, fy + 91, HudStyle.argb(255, HudStyle.WHITE), false);
        OsDraw.button(g, font, px, fy + RENAME_DY, renameLabel(), true, renaming ? HudStyle.GREEN : HudStyle.CYAN);
    }

    /** De knop onder de pijltjes: Rename, of Save terwijl je de naam typt. */
    private String renameLabel() {
        return renaming ? "Save" : "Rename";
    }

    /** De vier pijltjes bij het grote beeld: waar ze staan (ten opzichte van de knoppenkolom) en wat ze doen. */
    private enum Arrow {
        UP("▲", 20, 24, 0, -STEP),
        LEFT("◀", 0, 40, -STEP, 0),
        RIGHT("▶", 40, 40, STEP, 0),
        DOWN("▼", 20, 56, 0, STEP);

        final String label;
        final int dx;
        final int dy;
        final float pan;
        final float tilt;

        Arrow(String label, int dx, int dy, float pan, float tilt) {
            this.label = label;
            this.dx = dx;
            this.dy = dy;
            this.pan = pan;
            this.tilt = tilt;
        }
    }

    /** De robot met dit beeld, zoals je spel hem kent (null als hij te ver weg is om te zien). */
    private static @Nullable Entity robotEntity(CompoundTag camera) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.getEntity(camera.getInt("robot"));
    }

    /** Waar de robot nu staat (zoals je spel hem ziet; anders wat de server laatst zei). */
    private static BlockPos robotPos(CompoundTag camera) {
        Entity entity = robotEntity(camera);
        return entity != null ? entity.blockPosition() : posOf(camera);
    }

    /** Eén camerabeeld, met zijn CRT-randje, of de reden waarom er geen beeld is. */
    private void renderFeed(GuiGraphics g, Font font, CompoundTag camera, int x, int y, int w, int h, boolean large, boolean hover,
                            float partialTick) {
        boolean robot = isRobot(camera);
        Entity entity = robot ? robotEntity(camera) : null;
        Vec3 eye;
        float yaw;
        float pitch;
        if (entity != null) {
            // Het hoofd van de robot, een stukje naar voren (anders kijk je tegen de binnenkant van zijn hoofd).
            float headYaw = entity instanceof net.minecraft.world.entity.LivingEntity living
                    ? net.minecraft.util.Mth.rotLerp(partialTick, living.yHeadRotO, living.yHeadRot) : entity.getYRot();
            eye = entity.getEyePosition(partialTick).add(Vec3.directionFromRotation(0, headYaw).scale(0.35));
            yaw = headYaw;
            // Een drone (hoofdstuk 25) kijkt een beetje naar beneden: zo zie je wat er onder hem gebeurt.
            pitch = entity.getXRot() + (camera.getBoolean("drone") ? DRONE_PITCH : 0);
        } else {
            eye = new Vec3(camera.getDouble("ex"), camera.getDouble("ey"), camera.getDouble("ez"));
            yaw = camera.getFloat("yaw");
            pitch = camera.getFloat("pitch");
        }
        g.fill(x, y, x + w, y + h, HudStyle.argb(255, 0x05080B));
        String problem = null;
        if (!camera.getBoolean("on")) {
            problem = "NO SIGNAL";
        } else if (robot && entity == null || !CameraFeeds.inRange(eye)) {
            problem = "OUT OF RANGE";
        } else if (robot ? CameraFeeds.brokenRobot(camera.getInt("robot")) : CameraFeeds.broken(posOf(camera))) {
            problem = "FEED ERROR";
        }
        boolean live = false;
        if (problem == null) {
            int texture = robot ? CameraFeeds.requestRobot(camera.getInt("robot"), eye, yaw, pitch, large)
                    : CameraFeeds.request(posOf(camera), eye, yaw, pitch, large);
            if (texture >= 0) {
                blit(g, texture, x, y, w, h);
                live = true;
            }
            if (robot) {
                watching(camera.getInt("robot"));
            }
        }
        if (problem != null) {
            noise(g, x, y, w, h);
            g.drawString(font, problem, x + (w - font.width(problem)) / 2, y + h / 2 - 4, HudStyle.argb(255, HudStyle.RED), false);
        } else if (!live) {
            String wait = "CONNECTING...";
            g.drawString(font, wait, x + (w - font.width(wait)) / 2, y + h / 2 - 4, HudStyle.argb(255, OsDraw.GREY), false);
        }
        // Het CRT-randje: scanlijnen, hoekjes, de naam en een knipperend REC.
        g.drawManaged(() -> {
            for (int line = y; line < y + h; line += 3) {
                g.fill(x, line, x + w, line + 1, HudStyle.argb(30, 0x000000));
            }
        });
        HudStyle.brackets(g, x, y, x + w, y + h, large ? 8 : 5, HudStyle.argb(230, hover ? HudStyle.CYAN : 0x6F8A96));
        String name = camera.getString("name");
        g.fill(x + 1, y + 1, x + 6 + font.width(name), y + 11, HudStyle.argb(170, 0x000000));
        g.drawString(font, name, x + 3, y + 2, HudStyle.argb(255, HudStyle.WHITE), false);
        if (robot) {
            // "ROBOT" linksonder: dit beeld loopt mee met een robot.
            String tag = camera.getBoolean("drone") ? "DRONE" : "ROBOT";
            g.fill(x + 1, y + h - 11, x + 6 + font.width(tag), y + h - 1, HudStyle.argb(170, 0x000000));
            g.drawString(font, tag, x + 3, y + h - 10, HudStyle.argb(255, 0xF1E87C), false);
        }
        if (live && (Util.getMillis() / 600) % 2 == 0) {
            int rx = x + w - font.width("REC") - 4;
            g.fill(rx - 6, y + 4, rx - 2, y + 8, HudStyle.argb(255, HudStyle.RED));
            g.drawString(font, "REC", rx, y + 2, HudStyle.argb(255, HudStyle.RED), false);
        }
    }

    /** Je kijkt naar deze robot: zeg het (hooguit om de seconde) tegen de server, zodat zijn read(cameraPin) true is. */
    private void watching(int robot) {
        long now = Util.getMillis();
        Long last = watched.get(robot);
        if (last != null && now - last < WATCH_EVERY) {
            return;
        }
        watched.put(robot, now);
        CompoundTag args = new CompoundTag();
        args.putInt("robot", robot);
        screen.send("cam_watch", args);
    }

    /** Ruis zoals op een oude tv (grijze vlekjes die elke 80 ms veranderen). */
    private static void noise(GuiGraphics g, int x, int y, int w, int h) {
        long seed = Util.getMillis() / 80;
        g.drawManaged(() -> {
            for (int ny = y; ny < y + h; ny += 4) {
                for (int nx = x; nx < x + w; nx += 4) {
                    long hash = (nx * 73856093L) ^ (ny * 19349663L) ^ (seed * 83492791L);
                    hash ^= hash >>> 13;
                    hash *= 0x5DEECE66DL;
                    int grey = 20 + (int) ((hash >>> 24) & 63);
                    g.fill(nx, ny, Math.min(nx + 4, x + w), Math.min(ny + 4, y + h), HudStyle.argb(255, grey << 16 | grey << 8 | grey));
                }
            }
        });
    }

    /**
     * Een camerabeeld (een plaatje van de grafische kaart, met zijn nummer) op het scherm zetten. Zo'n
     * plaatje staat "op zijn kop" (de onderste rij eerst), dus we draaien de hoeken om.
     */
    private static void blit(GuiGraphics g, int texture, int x, int y, int w, int h) {
        g.flush(); // eerst wat er al klaarstaat, zodat de volgorde klopt
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Matrix4f matrix = g.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, x, y, 0).setUv(0, 1);
        builder.addVertex(matrix, x, y + h, 0).setUv(0, 0);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(1, 0);
        builder.addVertex(matrix, x + w, y, 0).setUv(1, 1);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    // ================================================================ Muis en toetsen

    /** Het camerablok in het grote beeld, of null (de muur, of een robot: die draai of hernoem je niet). */
    private @Nullable BlockPos bigBlock() {
        CompoundTag camera = big == null ? null : find(big);
        return camera == null || isRobot(camera) ? null : posOf(camera);
    }

    void mouseClicked(Font font, StarkOsScreen.Area a, int mx, int my) {
        if (big != null) {
            int fw = bigWidth(a);
            int fh = fw * 9 / 16;
            int fy = a.y() + 12;
            int px = a.x() + fw + 8;
            if (OsDraw.inside(mx, my, px, fy, OsDraw.buttonWidth(font, "Back"), OsDraw.BUTTON_H) && !renaming) {
                showWall();
                return;
            }
            if (bigBlock() == null) {
                return; // een robot: alleen Back
            }
            boolean onRename = OsDraw.inside(mx, my, px, fy + RENAME_DY, OsDraw.buttonWidth(font, renameLabel()), OsDraw.BUTTON_H);
            if (renaming) {
                if (onRename) {
                    submitName();
                } else if (OsDraw.inside(mx, my, a.x(), a.y() - 2, fw, OsDraw.BUTTON_H)) {
                    nameBox().mouseClicked(mx, my, 0); // de cursor verplaatsen
                } else {
                    renaming = false; // ergens anders geklikt: de naam blijft zoals hij was
                }
                return;
            }
            if (onRename) {
                startRename();
                return;
            }
            for (Arrow arrow : Arrow.values()) {
                if (OsDraw.inside(mx, my, px + arrow.dx, fy + arrow.dy, OsDraw.buttonWidth(font, arrow.label), OsDraw.BUTTON_H)) {
                    aim(arrow.pan, arrow.tilt);
                    return;
                }
            }
            if (OsDraw.inside(mx, my, a.x(), fy, fw, fh)) {
                dragging = true;
                dragStartX = mx;
                dragStartY = my;
            }
            return;
        }
        // De muur: welk beeldje?
        List<CompoundTag> cameras = cameras();
        int columns = Math.max(1, (a.w() + GAP) / (TILE_W + GAP));
        int top = a.y() + 12;
        for (int i = 0; i < cameras.size(); i++) {
            int row = i / columns - scroll;
            int x = a.x() + (i % columns) * (TILE_W + GAP);
            int y = top + row * (TILE_H + GAP);
            if (row >= 0 && OsDraw.inside(mx, my, x, y, TILE_W, TILE_H)) {
                big = key(cameras.get(i));
                return;
            }
        }
    }

    /** Losgelaten na slepen in het grote beeld: zoveel draaien als je sleepte (zoals rondkijken met de muis). */
    void mouseReleased(int mx, int my) {
        if (!dragging) {
            return;
        }
        dragging = false;
        int dx = mx - dragStartX;
        int dy = my - dragStartY;
        if (Math.abs(dx) + Math.abs(dy) > 3) {
            aim(dx * DRAG, dy * DRAG);
        }
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
    }

    /**
     * Pijltjestoetsen in het grote beeld; Esc of Backspace gaat terug naar de muur. Typ je een
     * naam, dan gaan de toetsen naar het naamvak: Enter bewaart, Esc laat het zoals het was.
     */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (big == null) {
            return false;
        }
        if (renaming) {
            switch (keyCode) {
                case 257, 335 -> submitName(); // enter
                case 256 -> renaming = false; // esc
                default -> nameBox().keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        switch (keyCode) {
            case 263 -> aim(-STEP, 0); // links
            case 262 -> aim(STEP, 0); // rechts
            case 265 -> aim(0, -STEP); // omhoog
            case 264 -> aim(0, STEP); // omlaag
            case 256, 259 -> showWall(); // esc, backspace
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Een letter getypt: alleen als je een naam aan het typen bent. */
    boolean charTyped(char codePoint, int modifiers) {
        if (!renaming) {
            return false;
        }
        nameBox().charTyped(codePoint, modifiers);
        return true;
    }

    /** Een ander venster kwam bovenop: stop met typen (de naam blijft zoals hij was). */
    void blur() {
        renaming = false;
    }

    /** Terug naar de muur met alle beeldjes. */
    private void showWall() {
        big = null;
        renaming = false;
    }

    /** De knop Rename: het naamvak met de huidige naam, helemaal geselecteerd (typen vervangt hem). */
    private void startRename() {
        CompoundTag camera = big == null ? null : find(big);
        if (camera == null || isRobot(camera)) {
            return;
        }
        EditBox box = nameBox();
        box.setValue(camera.getString("name"));
        box.moveCursorToEnd(false);
        box.setHighlightPos(0);
        box.setFocused(true);
        renaming = true;
    }

    /** De nieuwe naam naar de server (die maakt hem netjes; leeg = weer "Camera 3"). */
    private void submitName() {
        renaming = false;
        BlockPos pos = bigBlock();
        if (pos == null) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putInt("x", pos.getX());
        args.putInt("y", pos.getY());
        args.putInt("z", pos.getZ());
        args.putString("name", nameBox().getValue());
        screen.send("cam_rename", args);
    }

    private void aim(float pan, float tilt) {
        BlockPos pos = bigBlock();
        if (pos == null) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putInt("x", pos.getX());
        args.putInt("y", pos.getY());
        args.putInt("z", pos.getZ());
        args.putFloat("pan", pan);
        args.putFloat("tilt", tilt);
        screen.send("cam_aim", args);
    }

    /** Alleen voor de screenshot-rondleiding: het grote beeld van deze camera (of null: de muur). */
    void tourBig(@Nullable BlockPos camera) {
        big = camera == null ? null : blockKey(camera);
        renaming = false;
    }

    /** Alleen voor de screenshot-rondleiding: het grote beeld van de robot met deze naam (null: de eerste). */
    void tourBigRobot(@Nullable String name) {
        for (CompoundTag camera : cameras()) {
            if (isRobot(camera) && (name == null || camera.getString("name").equals(name))) {
                big = key(camera);
                renaming = false;
                return;
            }
        }
    }

    /** Alleen voor de screenshot-rondleiding: op Rename geklikt en {@code typed} getypt. */
    void tourRename(String typed) {
        startRename();
        nameBox().setValue(typed);
    }
}
