package com.senne.imttech.client.c4;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.c4.C4Block;
import com.senne.imttech.c4.C4BlockEntity;
import com.senne.imttech.c4.C4Item;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Tekent wat er op een lading C4 gebeurt (hoofdstuk 10): het rode schermpje dat aftelt ("0:07") en
 * het lampje dat knippert, steeds sneller als de tijd bijna om is. Een remote C4 die bij een
 * detonator hoort, knippert rustig rood en heeft geen tijd ("RF").
 *
 * De cijfers kijken altijd naar je toe (zoals een naambordje) en zweven net voor het schermpje van
 * het pakketje, zodat je ze van elke kant kunt lezen.
 */
public class C4Renderer implements BlockEntityRenderer<C4BlockEntity> {
    /** Zo ver voor het midden van het blok zit het schermpje (naar buiten, van de muur af). */
    private static final double FRONT = -0.15;
    private static final int RED = 0xFF3A2A;

    public C4Renderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(C4BlockEntity c4, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = c4.getLevel();
        if (level == null || !(c4.getBlockState().getBlock() instanceof C4Block)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Direction out = C4Block.outward(c4.getBlockState());
        Vec3 at = new Vec3(0.5 + out.getStepX() * FRONT, 0.5 + out.getStepY() * FRONT, 0.5 + out.getStepZ() * FRONT);
        long now = level.getGameTime();
        String text;
        boolean ledOn;
        if (c4.explodeAt() >= 0) {
            double left = Math.max(0, c4.explodeAt() - now - partialTick);
            int seconds = (int) Math.ceil(left / 20.0);
            text = C4Item.clock(seconds);
            // Het lampje: elke seconde kort aan, de laatste vijf seconden twee keer zo vaak, dan snel.
            int every = left > 100 ? 20 : left > 40 ? 10 : 4;
            ledOn = (now % every) < Math.max(2, every / 3);
        } else if (c4.linked()) {
            text = "RF";
            ledOn = (now % 40) < 4;
        } else {
            text = "--:--";
            ledOn = false;
        }
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.0125f, -0.0125f, 0.0125f);
        Matrix4f m = pose.last().pose();
        Font font = mc.font;
        float x = -font.width(text) / 2.0f;
        font.drawInBatch(text, x, -4, 0xFF000000 | RED, false, m, buffers, Font.DisplayMode.NORMAL,
                0xC0100404, LightTexture.FULL_BRIGHT);
        if (ledOn) {
            // Een klein rood lampje rechts van de cijfers.
            VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
            float lx = -x + 3;
            int r = 255;
            int g = 40;
            int b = 30;
            glow.addVertex(m, lx, -2, -0.01f).setColor(r, g, b, 255);
            glow.addVertex(m, lx, 2, -0.01f).setColor(r, g, b, 255);
            glow.addVertex(m, lx + 4, 2, -0.01f).setColor(r, g, b, 255);
            glow.addVertex(m, lx + 4, -2, -0.01f).setColor(r, g, b, 255);
        }
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return 24;
    }
}
