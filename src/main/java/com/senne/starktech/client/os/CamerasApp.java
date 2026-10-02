package com.senne.starktech.client.os;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.client.os.camera.CameraFeeds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Het Cameras-venster van de Stark OS-desktop: de bewakingsmuur (jouw wens, 28 september). Een
 * raster met een live beeldje van elke camera in het gebied van de kern; klik er een aan voor het
 * grote beeld, waar je de camera draait en kantelt (pijltjes, of het beeld verslepen).
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

    private final StarkOsScreen screen;
    /** Het grote beeld: van welke camera (de plek), of null = de muur met alle beeldjes. */
    private @Nullable BlockPos big;
    private int scroll;
    // Slepen in het grote beeld
    private boolean dragging;
    private int dragStartX;
    private int dragStartY;

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

    private @Nullable CompoundTag find(BlockPos pos) {
        for (CompoundTag camera : cameras()) {
            if (posOf(camera).equals(pos)) {
                return camera;
            }
        }
        return null;
    }

    /** Het grote beeld: hoe breed (de rest van het venster is voor de knoppen). */
    private static int bigWidth(StarkOsScreen.Area a) {
        return Math.min(256, a.w() - 70);
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my) {
        if (big != null) {
            CompoundTag camera = find(big);
            if (camera != null) {
                renderBig(g, font, a, camera, mx, my);
                return;
            }
            big = null; // de camera is weg
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
            renderFeed(g, font, cameras.get(i), x, y, TILE_W, TILE_H, false, hover);
        }
    }

    private void renderBig(GuiGraphics g, Font font, StarkOsScreen.Area a, CompoundTag camera, int mx, int my) {
        BlockPos pos = posOf(camera);
        g.drawString(font, camera.getString("name") + "   " + pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                a.x(), a.y(), HudStyle.argb(255, HudStyle.CYAN), false);
        int fw = bigWidth(a);
        int fh = fw * 9 / 16;
        int fy = a.y() + 12;
        renderFeed(g, font, camera, a.x(), fy, fw, fh, true, false);
        g.drawString(font, "Drag the image or use the arrow keys to aim", a.x(), fy + fh + 4,
                HudStyle.argb(255, OsDraw.GREY), false);
        // Rechts: terug, en de pijltjes.
        int px = a.x() + fw + 8;
        OsDraw.button(g, font, px, fy, "Back", true, HudStyle.CYAN);
        for (Arrow arrow : Arrow.values()) {
            OsDraw.button(g, font, px + arrow.dx, fy + arrow.dy, arrow.label, true, HudStyle.CYAN);
        }
        g.drawString(font, String.format("pan  %+.0f", camera.getFloat("pan")), px, fy + 80, HudStyle.argb(255, HudStyle.WHITE), false);
        g.drawString(font, String.format("tilt %+.0f", camera.getFloat("tilt")), px, fy + 91, HudStyle.argb(255, HudStyle.WHITE), false);
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

    /** Eén camerabeeld, met zijn CRT-randje, of de reden waarom er geen beeld is. */
    private void renderFeed(GuiGraphics g, Font font, CompoundTag camera, int x, int y, int w, int h, boolean large, boolean hover) {
        BlockPos pos = posOf(camera);
        Vec3 eye = new Vec3(camera.getDouble("ex"), camera.getDouble("ey"), camera.getDouble("ez"));
        g.fill(x, y, x + w, y + h, HudStyle.argb(255, 0x05080B));
        String problem = null;
        if (!camera.getBoolean("on")) {
            problem = "NO SIGNAL";
        } else if (!CameraFeeds.inRange(eye)) {
            problem = "OUT OF RANGE";
        } else if (CameraFeeds.broken(pos)) {
            problem = "FEED ERROR";
        }
        boolean live = false;
        if (problem == null) {
            int texture = CameraFeeds.request(pos, eye, camera.getFloat("yaw"), camera.getFloat("pitch"), large);
            if (texture >= 0) {
                blit(g, texture, x, y, w, h);
                live = true;
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
        if (live && (Util.getMillis() / 600) % 2 == 0) {
            int rx = x + w - font.width("REC") - 4;
            g.fill(rx - 6, y + 4, rx - 2, y + 8, HudStyle.argb(255, HudStyle.RED));
            g.drawString(font, "REC", rx, y + 2, HudStyle.argb(255, HudStyle.RED), false);
        }
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

    void mouseClicked(Font font, StarkOsScreen.Area a, int mx, int my) {
        if (big != null) {
            int fw = bigWidth(a);
            int fh = fw * 9 / 16;
            int fy = a.y() + 12;
            int px = a.x() + fw + 8;
            if (OsDraw.inside(mx, my, px, fy, OsDraw.buttonWidth(font, "Back"), OsDraw.BUTTON_H)) {
                big = null;
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
                big = posOf(cameras.get(i));
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

    /** Pijltjestoetsen in het grote beeld; Esc of Backspace gaat terug naar de muur. */
    boolean keyPressed(int keyCode) {
        if (big == null) {
            return false;
        }
        switch (keyCode) {
            case 263 -> aim(-STEP, 0); // links
            case 262 -> aim(STEP, 0); // rechts
            case 265 -> aim(0, -STEP); // omhoog
            case 264 -> aim(0, STEP); // omlaag
            case 256, 259 -> big = null; // esc, backspace
            default -> {
                return false;
            }
        }
        return true;
    }

    private void aim(float pan, float tilt) {
        if (big == null) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putInt("x", big.getX());
        args.putInt("y", big.getY());
        args.putInt("z", big.getZ());
        args.putFloat("pan", pan);
        args.putFloat("tilt", tilt);
        screen.send("cam_aim", args);
    }

    /** Alleen voor de screenshot-rondleiding: het grote beeld van deze camera (of null: de muur). */
    void tourBig(@Nullable BlockPos camera) {
        big = camera;
    }
}
