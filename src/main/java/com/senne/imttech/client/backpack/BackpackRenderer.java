package com.senne.imttech.client.backpack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.backpack.BackpackBlock;
import com.senne.imttech.backpack.BackpackBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Een neergezette rugzak (hoofdstuk 72): het model van de rugzak zelf (zijn kleur of camo, en in stap 2 zijn pouches),
 * rechtop op de grond, de MOLLE-banden naar wie hem neerzette. Het model kijkt naar het noorden; voor een andere kant
 * draaien we het (dezelfde draai als de vorm in BackpackBlock).
 */
public class BackpackRenderer implements BlockEntityRenderer<BackpackBlockEntity> {
    public BackpackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BackpackBlockEntity backpack, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack pack = backpack.pack();
        if (pack.isEmpty()) {
            return;
        }
        Direction facing = backpack.getBlockState().getValue(BackpackBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        Minecraft.getInstance().getItemRenderer().renderStatic(pack, ItemDisplayContext.NONE, light, overlay, pose, buffers,
                backpack.getLevel(), 0);
        BackpackLayer.renderPouches(pose, buffers, light, overlay, pack, backpack.getLevel());
        pose.popPose();
    }
}
