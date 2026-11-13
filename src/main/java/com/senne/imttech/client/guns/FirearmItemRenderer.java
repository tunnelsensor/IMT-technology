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
 * tekenen we het wapen zelf: uit zijn losse onderdelen (stap 6b: zo zie je ook zijn materialen en welk magazijn erin
 * zit), met erop wat erop zit, elk op zijn plek.
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
        // Hoofdstuk 103, stap 7: ook een lasergeweer: uit zijn onderdelen, met zijn kijker in de plaats van zijn eigen
        // vizier en zijn greep eronder (zoals op je rug en op het Pegboard).
        if (com.senne.imttech.weapons.LaserGunItem.typeOf(stack) != null) {
            holder = null;
            if (!com.senne.imttech.weapons.LaserGunItem.parts(stack).parts().isEmpty()) {
                com.senne.imttech.client.weapons.LaserGunModels.render(pose, buffers, stack, light, true, null);
                return;
            }
            // Een geweer zonder onderdelen (uit een commando): het model uit de json.
            ItemRenderer vanilla = Minecraft.getInstance().getItemRenderer();
            BakedModel whole = vanilla.getItemModelShaper().getItemModel(stack);
            for (BakedModel pass : whole.getRenderPasses(stack, true)) {
                for (RenderType renderType : pass.getRenderTypes(stack, true)) {
                    vanilla.renderModelLists(pass, stack, light, overlay, pose, ItemRenderer.getFoilBufferDirect(buffers, renderType, true, stack.hasFoil()));
                }
            }
            return;
        }
        FirearmType type = FirearmItem.typeOf(stack);
        if (type == null) {
            return;
        }
        // Minecraft heeft het wapen al op zijn plek gezet (de houding uit het model, en een half blok terug): hier staat
        // de hoek (0, 0, 0) van het model, met 1 blok = 16 pixels.
        // Stap 6: in de hand van iemand die sneakt en stilstaat, staat de tweepoot op zijn pootjes.
        net.minecraft.world.entity.LivingEntity held = holder;
        holder = null;
        boolean inHand = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        boolean braced = inHand && held != null && held.isCrouching() && held.walkAnimation.speed() < 0.05f;
        if (!FirearmItem.parts(stack).parts().isEmpty()) {
            // Stap 6b: uit zijn losse onderdelen, zoals op je rug en op het Pegboard. Zo zie je wat er echt in zit: elk
            // onderdeel in zijn materiaal, het gewone of het lange magazijn (of geen), de sling, en wat erop zit.
            FirearmModels.renderParts(pose, buffers, stack, light, true, null, braced ? 1 : 0);
            return;
        }
        // Een wapen zonder onderdelen (uit een commando): het hele model, zoals Minecraft het zelf zou tekenen.
        ItemRenderer items = Minecraft.getInstance().getItemRenderer();
        BakedModel model = FirearmModels.get(FirearmModels.whole(type));
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            for (RenderType renderType : pass.getRenderTypes(stack, true)) {
                VertexConsumer out = ItemRenderer.getFoilBufferDirect(buffers, renderType, true, stack.hasFoil());
                items.renderModelLists(pass, stack, light, overlay, pose, out);
            }
        }
        FirearmModels.renderAttachments(pose, buffers, stack, light, null, braced ? 1 : 0);
    }
}
