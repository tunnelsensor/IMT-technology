package com.senne.imttech.client.os.camera;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.security.wall.CameraMonitorBlock;
import com.senne.imttech.security.wall.CameraMonitorBlockEntity;
import com.senne.imttech.security.wall.CameraWall;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Tekent de camerabeelden op een schermmuur (zie security/wall/CameraWall). Elke tegel wordt getekend door de monitor van zijn
 * linkerbovenhoek, over zoveel blokken als hij groot is, net voor het scherm. Zoals in de app Cameras: het live beeld met
 * scanlijnen, hoekjes, de naam en een knipperend REC, en ruis met "NO SIGNAL" (of "NO ACCESS") als er geen beeld is. Een beeld
 * (16:9) wordt niet uitgerekt: we snijden af wat niet past, zoals een tv dat doet (zie CameraFeeds.crop).
 *
 * Een beeld vragen kost niets als de tegel niet in beeld is: deze renderer wordt dan niet aangeroepen, en de FeedPlanner tekent
 * alleen wat gevraagd wordt (zie ook het tijdbudget daar). {@link #shouldRenderOffScreen}: anders tekent Minecraft een tegel alleen
 * als het stuk wereld van zijn linkerbovenhoek in beeld is; bovendien tekent een camera (FeedRenderer) zo dit scherm niet zelf,
 * zodat een beeld nooit in zichzelf getekend wordt.
 */
public class CameraWallRenderer implements BlockEntityRenderer<CameraMonitorBlockEntity> {
    /** Waar het scherm ligt (vanaf het midden van het blok, naar de kijker toe), en net daarvoor het beeld. */
    private static final float SCREEN = (float) -((8 - CameraMonitorBlock.DEPTH) / 16.0);
    private static final float IMAGE = SCREEN + 0.004f;
    private static final float OVERLAY = IMAGE + 0.002f;
    private static final float BACKDROP = OVERLAY + 0.001f;
    private static final float TEXT = BACKDROP + 0.002f;
    /** Zo groot is de tekst: 72 pixels per blok. */
    private static final float TEXT_SCALE = 1 / 72f;
    /** Een tegel van minstens zoveel blokken krijgt het scherpe beeld (twee keer zo veel pixels). */
    private static final int BIG = 4;

    private final Font font;

    public CameraWallRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(CameraMonitorBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        CameraMonitorBlockEntity.Tile tile = entity.tile();
        BlockState state = entity.getBlockState();
        if (tile == null || !(state.getBlock() instanceof CameraMonitorBlock) || !state.getValue(CameraMonitorBlock.ON)) {
            return;
        }
        CameraWall.Shape shape = entity.shape();
        if (!shape.valid()) {
            return;
        }
        BlockPos pos = entity.getBlockPos();
        // Staat er intussen een monitor minder, dan past de tegel op wat er nog staat.
        int width = Math.min(tile.width(), shape.width() - shape.columnOf(pos));
        int height = Math.min(tile.height(), shape.height() - shape.rowOf(pos));
        if (width < 1 || height < 1) {
            return;
        }
        pose.pushPose();
        // Vanaf hier: x naar rechts, y omhoog, z naar wie ernaar kijkt, met (0, 0) in het midden van deze monitor.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(CameraMonitorBlock.FACING).toYRot()));
        float x0 = -0.5f;
        float y0 = 0.5f;
        float x1 = x0 + width;
        float y1 = y0 - height;
        Matrix4f matrix = pose.last().pose();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);

        String problem = null;
        int texture = -1;
        boolean big = width * height >= BIG;
        if (!entity.live()) {
            problem = "NO SIGNAL";
        } else {
            // Hoofdstuk 94: ook een camera ver weg heeft beeld (de server stuurt de wereld eromheen, zie CameraFeeds).
            CameraFeeds.Picture picture = tile.camera() != null ? CameraFeeds.camera(tile.camera(), null, 0, 0, big)
                    : CameraFeeds.robot(entity.robotEntity(), big, partialTick);
            texture = picture.texture();
            problem = picture.problem();
        }
        if (texture >= 0) {
            image(matrix, texture, x0, y0, x1, y1, width / (float) height);
        } else if (problem != null) {
            noise(matrix, x0, y0, width, height);
        } else {
            quads(builder -> quad(builder, matrix, x0, y0, x1, y1, IMAGE, HudStyle.argb(255, 0x05080B)));
        }
        frame(matrix, x0, y0, x1, y1);
        text(entity, tile, pose, buffers, width, height, problem, texture >= 0);
        pose.popPose();
    }

    // ================================================================ Tekenen

    /** Het camerabeeld. Zo'n plaatje staat op zijn kop (de onderste rij eerst), dus de onderkant krijgt de kleine v. */
    private static void image(Matrix4f matrix, int texture, float x0, float y0, float x1, float y1, float aspect) {
        float[] crop = CameraFeeds.crop(aspect);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, x0, y0, IMAGE).setUv(crop[0], crop[3]);
        builder.addVertex(matrix, x0, y1, IMAGE).setUv(crop[0], crop[1]);
        builder.addVertex(matrix, x1, y1, IMAGE).setUv(crop[2], crop[1]);
        builder.addVertex(matrix, x1, y0, IMAGE).setUv(crop[2], crop[3]);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /** Ruis zoals op een oude tv: grijze vlekjes die elke 80 ms veranderen (zoals in de app Cameras). */
    private static void noise(Matrix4f matrix, float x0, float y0, int width, int height) {
        long seed = Util.getMillis() / 80;
        int perBlock = 8;
        float size = 1f / perBlock;
        quads(builder -> {
            for (int row = 0; row < height * perBlock; row++) {
                for (int column = 0; column < width * perBlock; column++) {
                    long hash = (column * 73856093L) ^ (row * 19349663L) ^ (seed * 83492791L);
                    hash ^= hash >>> 13;
                    hash *= 0x5DEECE66DL;
                    int grey = 20 + (int) ((hash >>> 24) & 63);
                    float x = x0 + column * size;
                    float y = y0 - row * size;
                    quad(builder, matrix, x, y, x + size, y - size, IMAGE, HudStyle.argb(255, grey << 16 | grey << 8 | grey));
                }
            }
        });
    }

    /** Het CRT-randje: scanlijnen en hoekjes, half doorzichtig over het beeld. */
    private static void frame(Matrix4f matrix, float x0, float y0, float x1, float y1) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        quads(builder -> {
            int lines = Math.round((y0 - y1) * 24);
            for (int i = 0; i < lines; i++) {
                float y = y0 - i / 24f;
                quad(builder, matrix, x0, y, x1, y - 1 / 72f, OVERLAY, HudStyle.argb(30, 0x000000));
            }
            float arm = 0.1f;
            float thick = 0.012f;
            float inset = 0.07f;
            int colour = HudStyle.argb(230, 0x6F8A96);
            float left = x0 + inset;
            float right = x1 - inset;
            float top = y0 - inset;
            float bottom = y1 + inset;
            quad(builder, matrix, left, top, left + arm, top - thick, OVERLAY, colour);
            quad(builder, matrix, left, top, left + thick, top - arm, OVERLAY, colour);
            quad(builder, matrix, right - arm, top, right, top - thick, OVERLAY, colour);
            quad(builder, matrix, right - thick, top, right, top - arm, OVERLAY, colour);
            quad(builder, matrix, left, bottom + thick, left + arm, bottom, OVERLAY, colour);
            quad(builder, matrix, left, bottom + arm, left + thick, bottom, OVERLAY, colour);
            quad(builder, matrix, right - arm, bottom + thick, right, bottom, OVERLAY, colour);
            quad(builder, matrix, right - thick, bottom + arm, right, bottom, OVERLAY, colour);
        });
        RenderSystem.disableBlend();
    }

    /**
     * De naam linksboven, REC rechtsboven (knippert als er beeld is), ROBOT linksonder, en in het midden wat er mis is. De
     * donkere vlakjes erachter tekenen we zelf: de achtergrond die de tekst zelf kan meekrijgen, ligt op deze schaal op dezelfde
     * diepte als de letters, en dan verdwijnt een deel van de letters.
     */
    private void text(CameraMonitorBlockEntity entity, CameraMonitorBlockEntity.Tile tile, PoseStack pose, MultiBufferSource buffers,
                      int width, int height, String problem, boolean live) {
        int pixels = Math.round(width / TEXT_SCALE);
        int rows = Math.round(height / TEXT_SCALE);
        int margin = 6;
        String name = font.plainSubstrByWidth(entity.label(), pixels - margin * 2 - (live ? 26 : 0));
        Entity robot = tile.robot() == null || Minecraft.getInstance().level == null ? null
                : Minecraft.getInstance().level.getEntity(entity.robotEntity());
        String tag = tile.robot() == null ? null : robot instanceof com.senne.imttech.robot.GuardDroneEntity ? "DRONE" : "ROBOT";
        int tagY = rows - margin - font.lineHeight;
        int problemX = problem == null ? 0 : (pixels - font.width(problem)) / 2;
        int problemY = (rows - font.lineHeight) / 2;
        Matrix4f matrix = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        quads(builder -> {
            if (!name.isEmpty()) {
                backdrop(builder, matrix, margin, margin, font.width(name));
            }
            if (tag != null) {
                backdrop(builder, matrix, margin, tagY, font.width(tag));
            }
            if (problem != null) {
                backdrop(builder, matrix, problemX, problemY, font.width(problem));
            }
        });
        RenderSystem.disableBlend();
        pose.pushPose();
        pose.translate(-0.5f, 0.5f, TEXT);
        pose.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        draw(pose, buffers, name, margin, margin, HudStyle.WHITE);
        if (live && (Util.getMillis() / 600) % 2 == 0) {
            draw(pose, buffers, "REC", pixels - margin - font.width("REC"), margin, HudStyle.RED);
        }
        if (tag != null) {
            draw(pose, buffers, tag, margin, tagY, 0xF1E87C);
        }
        if (problem != null) {
            draw(pose, buffers, problem, problemX, problemY, HudStyle.RED);
        }
        pose.popPose();
    }

    /** Een donker vlakje achter tekst op deze plek (in tekst-pixels vanaf linksboven), zoveel pixels breed. */
    private void backdrop(BufferBuilder builder, Matrix4f matrix, int x, int y, int textWidth) {
        float left = -0.5f + (x - 2) * TEXT_SCALE;
        float top = 0.5f - (y - 1) * TEXT_SCALE;
        quad(builder, matrix, left, top, left + (textWidth + 4) * TEXT_SCALE, top - (font.lineHeight + 1) * TEXT_SCALE, BACKDROP,
                HudStyle.argb(170, 0x000000));
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, String text, int x, int y, int rgb) {
        font.drawInBatch(text, x, y, HudStyle.argb(255, rgb), false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET,
                0, LightTexture.FULL_BRIGHT);
    }

    /** Een rijtje gekleurde vlakken, in één keer naar de grafische kaart. */
    private static void quads(java.util.function.Consumer<BufferBuilder> content) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        content.accept(builder);
        var mesh = builder.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /** Een vlak van linksboven naar rechtsonder, tegen de klok in gezien van de kijker (anders wordt hij niet getekend). */
    private static void quad(BufferBuilder builder, Matrix4f matrix, float x0, float y0, float x1, float y1, float z, int argb) {
        builder.addVertex(matrix, x0, y0, z).setColor(argb);
        builder.addVertex(matrix, x0, y1, z).setColor(argb);
        builder.addVertex(matrix, x1, y1, z).setColor(argb);
        builder.addVertex(matrix, x1, y0, z).setColor(argb);
    }

    // ================================================================ Wanneer hij getekend wordt

    @Override
    public boolean shouldRenderOffScreen(CameraMonitorBlockEntity entity) {
        return true;
    }

    /** De hele tegel, van deze monitor tot de monitor rechtsonder, zodat hij pas verdwijnt als hij helemaal uit beeld is. */
    @Override
    public AABB getRenderBoundingBox(CameraMonitorBlockEntity entity) {
        BlockPos pos = entity.getBlockPos();
        CameraMonitorBlockEntity.Tile tile = entity.tile();
        BlockState state = entity.getBlockState();
        if (tile == null || !(state.getBlock() instanceof CameraMonitorBlock)) {
            return new AABB(pos);
        }
        BlockPos end = pos.relative(state.getValue(CameraMonitorBlock.FACING).getCounterClockWise(), tile.width() - 1).below(tile.height() - 1);
        return AABB.encapsulatingFullBlocks(pos, end);
    }
}
