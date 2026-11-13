package com.senne.imttech.client.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Tekent een vuurwapen overal waar Minecraft het als item tekent (hoofdstuk 103, stap 5): in je inventaris en je
 * werkbalk, op de grond, in een item frame, en in de hand van een andere speler of van een soldaat. Het wapen zelf is
 * één model (models/item/mp5.json), en daar zit zijn kijker niet in: die hangt van het wapen af dat je vasthoudt. Dus
 * tekenen we eerst dat model, precies zoals Minecraft het zelf zou doen, en dan wat erop zit, op zijn montage.
 *
 * Minecraft roept dit alleen aan voor een model dat zegt dat het "zelf getekend" wordt: dat regelt
 * {@link FirearmModels#wrapWhole}. In je eigen beeld (eerste persoon) tekent FirearmRenderer het wapen, uit zijn losse
 * onderdelen.
 */
public final class FirearmItemRenderer extends BlockEntityWithoutLevelRenderer {
    public FirearmItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light,
                             int overlay) {
        FirearmType type = FirearmItem.typeOf(stack);
        if (type == null) {
            return;
        }
        // Minecraft heeft het wapen al op zijn plek gezet (de houding uit het model, en een half blok terug): hier staat
        // de hoek (0, 0, 0) van het model, met 1 blok = 16 pixels.
        ItemRenderer items = Minecraft.getInstance().getItemRenderer();
        BakedModel model = FirearmModels.get(FirearmModels.whole(type));
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            for (RenderType renderType : pass.getRenderTypes(stack, true)) {
                VertexConsumer out = ItemRenderer.getFoilBufferDirect(buffers, renderType, true, stack.hasFoil());
                items.renderModelLists(pass, stack, light, overlay, pose, out);
            }
        }
        FirearmModels.renderAttachments(pose, buffers, stack, light);
    }
}
