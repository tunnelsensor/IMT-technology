package com.senne.imttech.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.armor.VestItem;
import java.util.function.Function;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Het vest op een lijf (hoofdstuk 109): voor spelers (voor anderen, en voor jezelf met F5 en in je inventaris), de
 * Raven-soldaten met een vest, en het lichaam van een speler (daar ligt het op zijn rug, met de tekst naar boven). Het vest
 * beweegt mee met het lijf (bukken, zwemmen, op je buik liggen).
 *
 * @param <T> het soort wezen
 * @param <M> zijn model (een mensmodel: speler, soldaat, lichaam)
 */
public class VestLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    /** Welk vest dit wezen draagt (leeg = geen). */
    private final Function<T, ItemStack> vest;

    public VestLayer(RenderLayerParent<T, M> parent, Function<T, ItemStack> vest) {
        super(parent);
        this.vest = vest;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        ItemStack stack = vest.apply(entity);
        if (!VestItem.isVest(stack)) {
            return;
        }
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        VestRenderer.renderOnBody(pose, buffers, light, stack, entity.level(), VestRenderer.wearsChestArmour(entity));
        pose.popPose();
    }
}
