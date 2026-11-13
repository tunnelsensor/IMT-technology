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
    /**
     * Wie het wapen vasthoudt dat we zo meteen tekenen, of null (een item op de grond, in je inventaris...). Minecraft
     * zegt dat niet tegen ons, maar wel tegen het model, vlak ervoor (zie FirearmModels.wrapWhole).
     */
    static @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity holder;

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
        // Stap 6: in de hand van iemand die sneakt en stilstaat, staat de tweepoot op zijn pootjes.
        net.minecraft.world.entity.LivingEntity held = holder;
        holder = null;
        boolean inHand = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        boolean braced = inHand && held != null && held.isCrouching() && held.walkAnimation.speed() < 0.05f;
        FirearmModels.renderAttachments(pose, buffers, stack, light, null, braced ? 1 : 0);
    }
}
