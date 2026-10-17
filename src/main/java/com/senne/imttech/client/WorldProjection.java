package com.senne.imttech.client;

import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jetbrains.annotations.Nullable;

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
    /** Hoe ver we tussen twee ticks in zitten; zie entityBox(). */
    private float partialTick;

    /** Een punt op het scherm (in GUI-pixels). */
    public record ScreenPoint(float x, float y) {}

    /** Een rechthoek op het scherm. */
    public record ScreenRect(float minX, float minY, float maxX, float maxY) {}

    /**
     * De matrices waarmee Minecraft de wereld dit frame getekend heeft.
     *
     * Die pakken we op tijdens het tekenen van de wereld (zie capture), want zelf naspelen
     * hoe de camera staat gaat altijd net mis: je mist de echte kijkhoek, het wiegen bij het
     * lopen en de zoom. En dan staan de beugels net naast je doelwit in plaats van eromheen.
     */
    private static final Matrix4f CAPTURED_VIEW = new Matrix4f();
    private static final Matrix4f CAPTURED_PROJECTION = new Matrix4f();
    private static boolean captured;

    /** Wordt tijdens het tekenen van de wereld aangeroepen (RenderLevelStageEvent). */
    public static void capture(Matrix4f modelView, Matrix4f projection) {
        CAPTURED_VIEW.set(modelView);
        CAPTURED_PROJECTION.set(projection);
        captured = true;
    }

    public void begin(int guiWidth, int guiHeight, float partialTick) {
        this.partialTick = partialTick;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        cameraPos = camera.getPosition();
        if (captured) {
            viewProjection.set(CAPTURED_PROJECTION).mul(CAPTURED_VIEW);
        } else {
            // Nog geen frame gezien (of een mod tekent de wereld anders): dan doen we het zelf.
            Matrix4f view = new Matrix4f();
            view.rotate(Axis.XP.rotationDegrees(camera.getXRot()));
            view.rotate(Axis.YP.rotationDegrees(camera.getYRot() + 180.0f));
            viewProjection.set(mc.gameRenderer.getProjectionMatrix(ImtClientState.fieldOfView)).mul(view);
        }
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

    /**
     * De doos rond een entity, op de plek waar hij op dit moment getekend staat.
     *
     * Een entity verspringt 20 keer per seconde, maar je scherm tekent veel vaker. Daartussenin
     * schuift het model alvast een stukje op ("partial tick"). Rekenden we met de kale positie,
     * dan loopt de markering steeds een stukje achter en lijkt hij rond de entity te zweven.
     */
    public @Nullable ScreenRect entityBox(Entity entity) {
        return projectBox(entity.getBoundingBox().move(drawnPosition(entity).subtract(entity.position())));
    }

    /** Waar de entity nu echt getekend staat (tussen twee ticks in). */
    public Vec3 drawnPosition(Entity entity) {
        return entity.getPosition(partialTick);
    }

    public boolean isOnScreen(ScreenPoint point, float margin) {
        return point.x() >= -margin && point.y() >= -margin && point.x() <= screenWidth + margin && point.y() <= screenHeight + margin;
    }
}
