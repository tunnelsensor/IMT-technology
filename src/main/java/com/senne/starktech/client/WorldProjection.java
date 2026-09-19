package com.senne.starktech.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Rekent uit waar een punt uit de 3D-wereld op jouw 2D-scherm terechtkomt.
 * Zo kan de HUD markeringen tekenen "op" ertsen en vijanden, net als echte AR.
 *
 * Gebruik: roep eerst begin() aan (één keer per frame), daarna project(...).
 */
public final class WorldProjection {
    private final Matrix4f viewProjection = new Matrix4f();
    private final Vector4f temp = new Vector4f();
    private Vec3 cameraPos = Vec3.ZERO;
    private int screenWidth;
    private int screenHeight;

    /** Een punt op het scherm (in GUI-pixels). */
    public record ScreenPoint(float x, float y) {}

    /** Een rechthoek op het scherm. */
    public record ScreenRect(float minX, float minY, float maxX, float maxY) {}

    public void begin(int guiWidth, int guiHeight) {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        camera.getViewRotationProjectionMatrix(viewProjection);
        cameraPos = camera.position();
        screenWidth = guiWidth;
        screenHeight = guiHeight;
    }

    /** Geeft de plek op het scherm, of null als het punt achter je ligt. */
    public @Nullable ScreenPoint project(Vec3 world) {
        temp.set((float) (world.x - cameraPos.x), (float) (world.y - cameraPos.y), (float) (world.z - cameraPos.z), 1.0f);
        viewProjection.transform(temp);
        if (temp.w <= 0.05f) {
            return null; // achter de camera
        }
        float ndcX = temp.x / temp.w;
        float ndcY = temp.y / temp.w;
        float x = (ndcX * 0.5f + 0.5f) * screenWidth;
        float y = (0.5f - ndcY * 0.5f) * screenHeight;
        return new ScreenPoint(x, y);
    }

    /** Projecteert de 8 hoeken van een doos en geeft de rechthoek eromheen (of null als hij achter je is). */
    public @Nullable ScreenRect projectBox(AABB box) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? box.minX : box.maxX;
            double y = (i & 2) == 0 ? box.minY : box.maxY;
            double z = (i & 4) == 0 ? box.minZ : box.maxZ;
            ScreenPoint point = project(new Vec3(x, y, z));
            if (point == null) {
                return null;
            }
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        return new ScreenRect(minX, minY, maxX, maxY);
    }

    public boolean isOnScreen(ScreenPoint point, float margin) {
        return point.x() >= -margin && point.y() >= -margin && point.x() <= screenWidth + margin && point.y() <= screenHeight + margin;
    }
}
