package com.senne.starktech.client.light;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.senne.starktech.light.ArcLampBlock;
import com.senne.starktech.light.ArcLampBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * De gloed om een brandende Arc Lamp: een zachte, blauwig witte stralenkrans die altijd naar je toe
 * kijkt (zoals een zon in een film). Het licht zelf (tot 100 blokken ver) doet het spel; dit is alleen
 * om te laten zien dat deze lamp veel feller is dan een gewone.
 *
 * Hoe: een plat rondje van drie ringen, midden voor de lamp, dat met de camera meedraait. Elke ring
 * loopt van een feller naar een zachter randje. Het wordt "opgeteld" bij wat erachter zit (zoals licht),
 * dus het maakt alles erachter lichter in plaats van het te bedekken. Achter een muur zie je het niet.
 */
public class ArcLampRenderer implements BlockEntityRenderer<ArcLampBlockEntity> {
    /** Een effen kleur die bij het beeld opgeteld wordt (dezelfde tekenstijl als bliksem). */
    private static final RenderType GLOW = RenderType.create("starktech_arc_lamp_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1024, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** Uit hoeveel stukjes een ring bestaat (meer = ronder). */
    private static final int SEGMENTS = 28;
    /** De kleur van booglicht: blauwig wit. */
    private static final int RED = 205;
    private static final int GREEN = 232;
    private static final int BLUE = 255;

    public ArcLampRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ArcLampBlockEntity lamp, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (!lamp.getBlockState().getValue(ArcLampBlock.LIT) || lamp.getLevel() == null || lamp.glow() <= 0) {
            return;
        }
        // De instellingen van de lamp (10 oktober): "Glow" maakt de gloed feller of zachter (0% = geen), en
        // een lamp die je zachter zet, krijgt ook een kleinere gloed (op 100 de volle maat, op 15 de helft).
        float strength = lamp.glow() / (float) ArcLampBlockEntity.MAX_GLOW;
        float bright = Mth.clamp(lamp.brightness() / 100.0f, 0.0f, 1.28f);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        // Draai mee met de camera, en schuif een stukje naar je toe: dan zit de gloed vóór het blok.
        pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        pose.translate(0.0, 0.0, 0.75);
        // Heel zacht "ademen", zoals een echte booglamp een beetje leeft.
        float time = lamp.getLevel().getGameTime() + partialTick;
        float size = (0.45f + 0.55f * bright) * (1.0f + 0.04f * Mth.sin(time * 0.21f) + 0.02f * Mth.sin(time * 0.53f));
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(GLOW);
        ring(m, vc, 0.0f, 0.6f * size, 0.55f * strength, 0.32f * strength);
        ring(m, vc, 0.6f * size, 1.5f * size, 0.32f * strength, 0.09f * strength);
        ring(m, vc, 1.5f * size, 3.2f * size, 0.09f * strength, 0.0f);
        pose.popPose();
    }

    /** Een ring van straal {@code inner} tot {@code outer}, van doorzichtigheid {@code from} naar {@code to}. */
    private static void ring(Matrix4f m, VertexConsumer vc, float inner, float outer, float from, float to) {
        int a = Math.round(Mth.clamp(from, 0.0f, 1.0f) * 255);
        int b = Math.round(Mth.clamp(to, 0.0f, 1.0f) * 255);
        for (int i = 0; i < SEGMENTS; i++) {
            float angle0 = (float) (i * Math.PI * 2 / SEGMENTS);
            float angle1 = (float) ((i + 1) * Math.PI * 2 / SEGMENTS);
            float c0 = Mth.cos(angle0);
            float s0 = Mth.sin(angle0);
            float c1 = Mth.cos(angle1);
            float s1 = Mth.sin(angle1);
            vc.addVertex(m, c0 * inner, s0 * inner, 0.0f).setColor(RED, GREEN, BLUE, a);
            vc.addVertex(m, c0 * outer, s0 * outer, 0.0f).setColor(RED, GREEN, BLUE, b);
            vc.addVertex(m, c1 * outer, s1 * outer, 0.0f).setColor(RED, GREEN, BLUE, b);
            vc.addVertex(m, c1 * inner, s1 * inner, 0.0f).setColor(RED, GREEN, BLUE, a);
        }
    }

    /** Ook als het blok net buiten beeld is, kan zijn gloed nog in beeld zijn. */
    @Override
    public boolean shouldRenderOffScreen(ArcLampBlockEntity lamp) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(ArcLampBlockEntity lamp) {
        return new AABB(lamp.getBlockPos()).inflate(3.5);
    }

    /** Een lamp van 100 zie je van ver. */
    @Override
    public int getViewDistance() {
        return 256;
    }
}
