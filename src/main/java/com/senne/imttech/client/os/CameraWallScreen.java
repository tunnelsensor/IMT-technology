package com.senne.imttech.client.os;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.client.os.camera.CameraFeeds;
import com.senne.imttech.network.CameraWallSavePayload;
import com.senne.imttech.security.wall.CameraMonitorBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Het instelscherm van een schermmuur (19 oktober, jouw notitie: "je komt op een GUI van het hele scherm, hier kies je welke
 * kamer waar en hoe groot staat").
 * <pre>
 *  CAMERA WALL  3 x 2                     [Clear] [Done]
 *  +--------+--------+--------+   CAMERAS
 *  | Lobby           | Vault  |   > Lobby
 *  |                 +--------+     Vault
 *  |                 |        |     Hall
 *  +-----------------+--------+     Guard 1  ROBOT
 *  Pick a camera, then drag a box on the screen.
 * </pre>
 * Links het hele scherm als rooster, met één vak per monitor; rechts je camera's. Kies een camera en sleep een vak op het
 * rooster: daar staat hij dan (wat eronder lag, gaat weg). Rechtsklik op een tegel haalt hem weg. Elke verandering gaat meteen
 * naar de server, die alles zelf nakijkt (zie CameraWall.handleSave), en je ziet het meteen op het scherm zelf.
 */
public class CameraWallScreen extends Screen {
    private static final int LIST_W = 138;
    private static final int ROW = 13;
    private static final int MIN_CELL = 16;
    private static final int MAX_CELL = 64;

    /** Eén camera in de lijst: hoe we hem herkennen ("c" + plek, of "r" + het vaste nummer van een robot), en wat de server zei. */
    private record Camera(String key, CompoundTag tag) {
        String name() {
            return tag.getString("name");
        }

        boolean robot() {
            return tag.contains("robot");
        }
    }

    /** Eén camera op het rooster: waar, hoe groot, en welke (zie {@link Camera#key}). */
    private record Tile(int column, int row, int width, int height, String key) {
        boolean covers(int c, int r) {
            return c >= column && c < column + width && r >= row && r < row + height;
        }

        boolean overlaps(int c, int r, int w, int h) {
            return c < column + width && column < c + w && r < row + height && row < r + h;
        }
    }

    private final BlockPos anchor;
    private final int wallWidth;
    private final int wallHeight;
    private final List<Camera> cameras = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    /** De camera die je gekozen hebt (zijn sleutel), of null. */
    private @Nullable String selected;
    private int listScroll;

    /** Welk vak je aan het slepen bent: het vak waar het begon, en waar de muis nu is. -1 = je sleept niet. */
    private int dragColumn = -1;
    private int dragRow;
    private int hoverColumn;
    private int hoverRow;

    private int panelW;
    private int panelH;
    private int left;
    private int top;
    private int cell;
    private int gridX;
    private int gridY;
    private int listX;
    private int listY;

    public CameraWallScreen(BlockPos anchor, int width, int height, CompoundTag data) {
        super(Component.translatable("block.imttech.camera_monitor"));
        this.anchor = anchor;
        this.wallWidth = width;
        this.wallHeight = height;
        for (Tag element : data.getList("cameras", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) element;
            cameras.add(new Camera(keyOf(tag), tag));
        }
        for (Tag element : data.getList("tiles", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) element;
            String key = tag.contains("cam") ? "c" + tag.getLong("cam") : "r" + tag.getString("id");
            tiles.add(new Tile(tag.getInt("c"), tag.getInt("r"), tag.getInt("w"), tag.getInt("h"), key));
        }
        selected = cameras.isEmpty() ? null : cameras.getFirst().key();
    }

    private static String keyOf(CompoundTag camera) {
        return camera.contains("robot") ? "r" + camera.getString("id")
                : "c" + new BlockPos(camera.getInt("x"), camera.getInt("y"), camera.getInt("z")).asLong();
    }

    private @Nullable Camera camera(@Nullable String key) {
        for (Camera camera : cameras) {
            if (camera.key().equals(key)) {
                return camera;
            }
        }
        return null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        // Het rooster zo groot als past: het scherm is smal op een klein venster, en een 8 x 6 moet er ook op.
        int roomW = width - LIST_W - 40;
        int roomH = height - 62;
        cell = Mth.clamp(Math.min(roomW / wallWidth, roomH / wallHeight), MIN_CELL, MAX_CELL);
        int contentH = Math.max(cell * wallHeight, 110);
        panelW = cell * wallWidth + LIST_W + 30;
        panelH = contentH + 52;
        left = Math.max(4, (width - panelW) / 2);
        top = Math.max(4, (height - panelH) / 2);
        gridX = left + 10;
        gridY = top + 24;
        listX = gridX + cell * wallWidth + 10;
        listY = gridY + 12;
    }

    @Override
    public void tick() {
        // De monitor is weg (afgebroken): dan is er niets meer om in te stellen.
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockState(anchor).getBlock() instanceof CameraMonitorBlock)) {
            onClose();
        }
    }

    // ================================================================ Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudStyle.dim(g, width, height);
        g.fill(left, top, left + panelW, top + panelH, HudStyle.argb(235, 0x10161C));
        HudStyle.brackets(g, left, top, left + panelW, top + panelH, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, "CAMERA WALL", left + 10, top + 8, HudStyle.argb(255, HudStyle.CYAN), false);
        g.drawString(font, wallWidth + " x " + wallHeight, left + 16 + font.width("CAMERA WALL"), top + 8, HudStyle.argb(255, OsDraw.GREY), false);
        OsDraw.button(g, font, doneX(), top + 5, "Done", true, HudStyle.CYAN);
        OsDraw.button(g, font, clearX(), top + 5, "Clear", !tiles.isEmpty(), HudStyle.RED);
        renderGrid(g, mouseX, mouseY, partialTick);
        renderList(g, mouseX, mouseY);
        String hint = cameras.isEmpty() ? "No cameras on this network yet: connect one to this core."
                : "Pick a camera and drag a box. Right-click a box to clear it.";
        g.drawString(font, OsDraw.fitRight(font, hint, panelW - 20), left + 10, top + panelH - 13, HudStyle.argb(255, OsDraw.GREY), false);
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Het rooster: één vak per monitor.
        for (int r = 0; r < wallHeight; r++) {
            for (int c = 0; c < wallWidth; c++) {
                int x = gridX + c * cell;
                int y = gridY + r * cell;
                g.fill(x, y, x + cell, y + cell, HudStyle.argb(255, OsDraw.FIELD));
                HudStyle.brackets(g, x, y, x + cell, y + cell, 3, HudStyle.argb(200, OsDraw.DIM));
            }
        }
        for (Tile tile : tiles) {
            renderTile(g, tile, partialTick);
        }
        // Waar je nu sleept (of de muis nu staat).
        int column = columnAt(mouseX);
        int row = rowAt(mouseY);
        if (dragColumn >= 0) {
            int c0 = Math.min(dragColumn, hoverColumn);
            int r0 = Math.min(dragRow, hoverRow);
            int w = Math.abs(dragColumn - hoverColumn) + 1;
            int h = Math.abs(dragRow - hoverRow) + 1;
            int x = gridX + c0 * cell;
            int y = gridY + r0 * cell;
            g.fill(x, y, x + w * cell, y + h * cell, HudStyle.argb(90, HudStyle.CYAN));
            HudStyle.brackets(g, x, y, x + w * cell, y + h * cell, 6, HudStyle.argb(255, HudStyle.CYAN));
        } else if (column >= 0 && row >= 0 && selected != null) {
            int x = gridX + column * cell;
            int y = gridY + row * cell;
            g.fill(x, y, x + cell, y + cell, HudStyle.argb(50, HudStyle.CYAN));
        }
    }

    /** Eén tegel: het live beeld van zijn camera, met de naam erop. */
    private void renderTile(GuiGraphics g, Tile tile, float partialTick) {
        int x = gridX + tile.column() * cell;
        int y = gridY + tile.row() * cell;
        int w = tile.width() * cell;
        int h = tile.height() * cell;
        Camera camera = camera(tile.key());
        g.fill(x, y, x + w, y + h, HudStyle.argb(255, 0x05080B));
        String problem = null;
        if (camera == null) {
            problem = "GONE";
        } else if (!camera.tag().getBoolean("on")) {
            problem = "NO SIGNAL";
        } else {
            int texture = feed(camera, partialTick);
            if (texture >= 0) {
                blit(g, texture, x, y, w, h);
            }
        }
        if (problem != null) {
            g.drawString(font, problem, x + (w - font.width(problem)) / 2, y + h / 2 - 4, HudStyle.argb(255, HudStyle.RED), false);
        }
        HudStyle.brackets(g, x, y, x + w, y + h, 5, HudStyle.argb(255, tile.key().equals(selected) ? HudStyle.CYAN : 0x6F8A96));
        String name = camera == null ? "?" : camera.name();
        String shown = OsDraw.fitRight(font, name, w - 6);
        g.fill(x + 1, y + 1, x + 4 + font.width(shown), y + 10, HudStyle.argb(170, 0x000000));
        g.drawString(font, shown, x + 2, y + 1, HudStyle.argb(255, HudStyle.WHITE), false);
    }

    /** Het beeld van deze camera (of -1 als er nog geen is). */
    private static int feed(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        CompoundTag tag = camera.tag();
        if (camera.robot()) {
            Entity robot = mc.level == null ? null : mc.level.getEntity(tag.getInt("robot"));
            return robot == null || !CameraFeeds.inRange(robot.getEyePosition(partialTick)) ? -1
                    : CameraFeeds.requestRobot(robot, false, partialTick);
        }
        Vec3 eye = new Vec3(tag.getDouble("ex"), tag.getDouble("ey"), tag.getDouble("ez"));
        return !CameraFeeds.inRange(eye) ? -1 : CameraFeeds.request(
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")), eye, tag.getFloat("yaw"), tag.getFloat("pitch"), false);
    }

    /** Een camerabeeld op het scherm, afgesneden tot de verhouding van het vak (zo'n plaatje staat op zijn kop). */
    private static void blit(GuiGraphics g, int texture, int x, int y, int w, int h) {
        float[] crop = CameraFeeds.crop(w / (float) h);
        g.flush();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Matrix4f matrix = g.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, x, y, 0).setUv(crop[0], crop[3]);
        builder.addVertex(matrix, x, y + h, 0).setUv(crop[0], crop[1]);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(crop[2], crop[1]);
        builder.addVertex(matrix, x + w, y, 0).setUv(crop[2], crop[3]);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "CAMERAS", listX, gridY, HudStyle.argb(255, 0x6F8C99), false);
        int rows = visibleRows();
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, cameras.size() - rows));
        int y = listY;
        for (int i = listScroll; i < cameras.size() && i < listScroll + rows; i++) {
            Camera camera = cameras.get(i);
            boolean chosen = camera.key().equals(selected);
            if (chosen) {
                g.fill(listX - 2, y - 2, listX + LIST_W - 4, y + ROW - 3, HudStyle.argb(255, 0x16303F));
                g.fill(listX - 2, y - 2, listX, y + ROW - 3, HudStyle.argb(255, HudStyle.CYAN));
            } else if (OsDraw.inside(mouseX, mouseY, listX - 2, y - 2, LIST_W - 2, ROW)) {
                g.fill(listX - 2, y - 2, listX + LIST_W - 4, y + ROW - 3, HudStyle.argb(255, 0x0F1E27));
            }
            String tag = camera.robot() ? (camera.tag().getBoolean("drone") ? "DRONE" : "ROBOT") : "";
            int tagW = tag.isEmpty() ? 0 : font.width(tag) + 4;
            g.drawString(font, OsDraw.fitRight(font, camera.name(), LIST_W - 12 - tagW), listX + 3, y,
                    HudStyle.argb(255, camera.tag().getBoolean("on") ? HudStyle.WHITE : OsDraw.GREY), false);
            if (!tag.isEmpty()) {
                g.drawString(font, tag, listX + LIST_W - 7 - font.width(tag), y, HudStyle.argb(255, 0xF1E87C), false);
            }
            y += ROW;
        }
        if (listScroll > 0 || listScroll + rows < cameras.size()) {
            String more = (listScroll > 0 ? "▲ " : "") + (listScroll + rows < cameras.size() ? "▼" : "");
            g.drawString(font, more, listX + LIST_W - 8 - font.width(more), gridY, HudStyle.argb(255, OsDraw.GREY), false);
        }
    }

    // ================================================================ Waar alles staat

    private int doneX() {
        return left + panelW - 8 - OsDraw.buttonWidth(font, "Done");
    }

    private int clearX() {
        return doneX() - 4 - OsDraw.buttonWidth(font, "Clear");
    }

    private int visibleRows() {
        return Math.max(1, (top + panelH - 18 - listY) / ROW);
    }

    /** In welke kolom van het rooster deze x ligt, of -1. */
    private int columnAt(int x) {
        return x < gridX || x >= gridX + cell * wallWidth ? -1 : (x - gridX) / cell;
    }

    /** In welke rij van het rooster deze y ligt, of -1. */
    private int rowAt(int y) {
        return y < gridY || y >= gridY + cell * wallHeight ? -1 : (y - gridY) / cell;
    }

    // ================================================================ Muis

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (OsDraw.inside(mx, my, doneX(), top + 5, OsDraw.buttonWidth(font, "Done"), OsDraw.BUTTON_H)) {
            onClose();
            return true;
        }
        if (OsDraw.inside(mx, my, clearX(), top + 5, OsDraw.buttonWidth(font, "Clear"), OsDraw.BUTTON_H)) {
            if (!tiles.isEmpty()) {
                tiles.clear();
                save();
            }
            return true;
        }
        int row = (my - listY + 2) / ROW + listScroll;
        if (mx >= listX - 2 && mx < listX + LIST_W - 4 && my >= listY - 2 && row >= listScroll && row < listScroll + visibleRows()
                && row < cameras.size()) {
            selected = cameras.get(row).key();
            return true;
        }
        int gridColumn = columnAt(mx);
        int gridRow = rowAt(my);
        if (gridColumn >= 0 && gridRow >= 0) {
            if (button == 1) {
                if (tiles.removeIf(tile -> tile.covers(gridColumn, gridRow))) {
                    save();
                }
            } else if (button == 0 && selected != null) {
                dragColumn = gridColumn;
                dragRow = gridRow;
                hoverColumn = gridColumn;
                hoverRow = gridRow;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragColumn >= 0 && button == 0) {
            place(Math.min(dragColumn, hoverColumn), Math.min(dragRow, hoverRow),
                    Math.abs(dragColumn - hoverColumn) + 1, Math.abs(dragRow - hoverRow) + 1);
            dragColumn = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragColumn >= 0) {
            int column = columnAt((int) mouseX);
            int row = rowAt((int) mouseY);
            hoverColumn = column < 0 ? hoverColumn : column;
            hoverRow = row < 0 ? hoverRow : row;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        listScroll = Math.max(0, listScroll - (int) Math.signum(scrollY));
        return true;
    }

    /** De gekozen camera op dit vak: wat eronder lag, gaat weg. */
    private void place(int column, int row, int w, int h) {
        tiles.removeIf(tile -> tile.overlaps(column, row, w, h));
        tiles.add(new Tile(column, row, w, h, selected));
        save();
    }

    /** De hele indeling naar de server. */
    private void save() {
        ListTag list = new ListTag();
        for (Tile tile : tiles) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("c", tile.column());
            tag.putInt("r", tile.row());
            tag.putInt("w", tile.width());
            tag.putInt("h", tile.height());
            if (tile.key().startsWith("c")) {
                tag.putLong("cam", Long.parseLong(tile.key().substring(1)));
            } else {
                tag.putString("id", tile.key().substring(1));
            }
            list.add(tag);
        }
        CompoundTag args = new CompoundTag();
        args.put("tiles", list);
        PacketDistributor.sendToServer(new CameraWallSavePayload(anchor, args));
    }

    // ================================================================ Voor de rondleiding

    /** Alleen voor de screenshot-rondleiding: deze camera kiezen. Geeft false als hij er (nog) niet is. */
    public boolean tourSelect(String name) {
        for (Camera camera : cameras) {
            if (camera.name().equals(name)) {
                selected = camera.key();
                return true;
            }
        }
        return false;
    }

    /** Alleen voor de screenshot-rondleiding: een vak aan het slepen, van deze kolom en rij tot die. */
    public void tourDrag(int fromColumn, int fromRow, int toColumn, int toRow) {
        dragColumn = fromColumn;
        dragRow = fromRow;
        hoverColumn = toColumn;
        hoverRow = toRow;
    }

    /** Alleen voor de screenshot-rondleiding: het vak loslaten. */
    public void tourDrop() {
        if (dragColumn < 0) {
            return;
        }
        place(Math.min(dragColumn, hoverColumn), Math.min(dragRow, hoverRow),
                Math.abs(dragColumn - hoverColumn) + 1, Math.abs(dragRow - hoverRow) + 1);
        dragColumn = -1;
    }
}
