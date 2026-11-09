package com.senne.imttech.client.armory;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.armory.AmmoBoxBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * Het etiket op de voorkant van een Ammo Box (hoofdstuk 67, jouw keuze: "op de voorkant staan een plaatje en het
 * aantal"): links het plaatje van wat erin zit, rechts hoeveel. Een lege kist heeft een leeg etiket.
 */
public class AmmoBoxRenderer implements BlockEntityRenderer<AmmoBoxBlockEntity> {
    /** De voorkant van de kist ligt op 12 pixels (de kist is 8 diep, van 4 tot 12), het etiket net ervoor. */
    private static final float FRONT = 12.02f;
    /** Het midden van het etiket (pixels): 4 tot 12 breed, 2 tot 6 hoog. */
    private static final float LABEL_Y = 4.0f;
    private static final int INK = 0x2B2A1E;
    private static final int INK_FULL = 0x8A2018;

    private final Font font;

    public AmmoBoxRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(AmmoBoxBlockEntity box, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack sort = box.sort();
        if (sort.isEmpty()) {
            return;
        }
        Direction facing = box.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);

        // Het plaatje: zoals in je inventaris, maar plat op het etiket.
        pose.pushPose();
        pose.translate(6.0, LABEL_Y, FRONT + 0.05);
        pose.scale(3.6f, 3.6f, 0.06f);
        Minecraft.getInstance().getItemRenderer().renderStatic(sort, ItemDisplayContext.GUI, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, box.getLevel(), 0);
        pose.popPose();

        // Het aantal, rechts ervan, zo groot als past (rood als de kist vol is).
        int count = box.count();
        String text = Integer.toString(count);
        int width = Math.max(1, font.width(text));
        float scale = Math.min(0.26f, 3.8f / width);
        boolean full = count >= AmmoBoxBlockEntity.capacity(sort);
        pose.pushPose();
        pose.translate(9.9, LABEL_Y, FRONT + 0.02);
        pose.scale(scale, -scale, scale);
        font.drawInBatch(text, -width / 2f, -3.5f, full ? INK_FULL : INK, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, light);
        pose.popPose();
        pose.popPose();
    }
}
