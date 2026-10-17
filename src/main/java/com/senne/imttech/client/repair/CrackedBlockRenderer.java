package com.senne.imttech.client.repair;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.repair.FromBlockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Hoe een gebarsten blok eruitziet (hoofdstuk 11): het blok waar het van komt, met barsten erover
 * zoals wanneer iemand eraan hakt. Zo zie je in je inventaris (en in de Crusher) meteen dat het
 * gebarsten stenen bakstenen zijn, of gebarsten obsidiaan.
 *
 * Hoe: eerst tekenen we het gewone blok (zoals Minecraft dat altijd doet), daarna dezelfde vorm nog
 * een keer met het plaatje van de barsten erop geplakt. Dat laatste is precies wat Minecraft doet als
 * je aan een blok hakt (de "crumbling"-laag); Minecraft tekent die laag altijd na de blokken.
 *
 * Ook hier: de kleur van het poeder (de kleur van het blok, zie FromBlockItem.color).
 */
public class CrackedBlockRenderer extends BlockEntityWithoutLevelRenderer {
    /** Hoe ver de barsten zijn, zoals bij hakken: 0 = net begonnen, 9 = bijna kapot. */
    private static final int STAGE = 6;

    public CrackedBlockRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        Block block = FromBlockItem.block(stack);
        ItemStack inner = new ItemStack(block == null ? Blocks.COBBLESTONE : block);
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer items = minecraft.getItemRenderer();
        BakedModel model = items.getModel(inner, minecraft.level, null, 0);
        boolean leftHand = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;

        pose.pushPose();
        // Minecraft schoof ons al een half blok op; het blok hieronder doet dat zelf nog een keer.
        pose.translate(0.5f, 0.5f, 0.5f);
        items.render(inner, context, leftHand, pose, buffers, light, overlay, model);

        // De barsten: dezelfde vorm, gedraaid en verkleind zoals het blok net, met de crumbling-laag.
        pose.pushPose();
        BakedModel placed = ClientHooks.handleCameraTransforms(pose, model, context, leftHand);
        pose.translate(-0.5f, -0.5f, -0.5f);
        VertexConsumer cracks = new SheetedDecalTextureGenerator(
                buffers.getBuffer(ModelBakery.DESTROY_TYPES.get(STAGE)), pose.last(), 1.0f);
        for (BakedModel pass : placed.getRenderPasses(inner, true)) {
            items.renderModelLists(pass, inner, light, overlay, pose, cracks);
        }
        pose.popPose();
        pose.popPose();
    }

    /** Vertel Minecraft dat het gebarsten blok zo getekend wordt (zie het itemmodel: builtin/entity). */
    public static void registerExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private CrackedBlockRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new CrackedBlockRenderer();
                }
                return renderer;
            }
        }, ModItems.CRACKED_BLOCK.get());
    }

    /** Het poeder krijgt de kleur van zijn blok (het plaatje zelf is grijs). */
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? FromBlockItem.color(stack) : -1,
                ModItems.BLOCK_POWDER.get());
    }
}
