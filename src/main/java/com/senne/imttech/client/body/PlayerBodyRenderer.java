package com.senne.imttech.client.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.body.PlayerBodyEntity;
import com.senne.imttech.client.backpack.BackpackLayer;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Het lichaam van een speler (hoofdstuk 72, stap 4): het mensmodel met zijn eigen huid (breed of smal, zoals de speler),
 * plat op zijn buik met zijn hoofd naar waar hij keek, met zijn harnas aan en zijn rugzak bovenop (met de pouches).
 *
 * De huid: is de speler online, dan die van hem (zoals Minecraft hem kent); anders de standaardhuid die bij zijn naam
 * hoort (Steve of Alex).
 */
public class PlayerBodyRenderer extends LivingEntityRenderer<PlayerBodyEntity, PlayerModel<PlayerBodyEntity>> {
    private final PlayerModel<PlayerBodyEntity> wide;
    private final PlayerModel<PlayerBodyEntity> slim;

    public PlayerBodyRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.0f);
        this.wide = model;
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new PackLayer(this));
        // Hoofdstuk 109: zijn vest (hij ligt op zijn buik, dus de tekst op zijn rug kijkt naar boven).
        addLayer(new com.senne.imttech.client.armor.VestLayer<>(this, PlayerBodyEntity::vest));
    }

    /** De huid van de eigenaar: van de speler als hij online is, anders de standaardhuid bij zijn naam. */
    static PlayerSkin skin(PlayerBodyEntity body) {
        UUID owner = body.owner();
        if (owner == null) {
            return DefaultPlayerSkin.get(body.getUUID());
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
    }

    @Override
    public void render(PlayerBodyEntity body, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model = skin(body).model() == PlayerSkin.Model.SLIM ? slim : wide;
        super.render(body, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerBodyEntity body) {
        return skin(body).texture();
    }

    /**
     * Op zijn buik: dezelfde draai als zwemmen (een kwartslag naar voren om de x-as: het gezicht naar beneden, het hoofd
     * naar voren), iets omhoog zodat zijn borst op de grond ligt, en een halve lengte terug zodat het midden van het lichaam
     * op de plek van het wezen ligt (de klikdoos).
     */
    @Override
    protected void setupRotations(PlayerBodyEntity body, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(body, pose, bob, yBodyRot, partialTick, scale);
        pose.translate(0.0, 0.13, 0.9);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
    }

    @Override
    protected boolean shouldShowName(PlayerBodyEntity body) {
        return false;
    }

    /** De rugzak op zijn rug (die ligt nu bovenop), met de pouches. */
    private static final class PackLayer extends RenderLayer<PlayerBodyEntity, PlayerModel<PlayerBodyEntity>> {
        PackLayer(RenderLayerParent<PlayerBodyEntity, PlayerModel<PlayerBodyEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, PlayerBodyEntity body, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack pack = body.pack();
            if (pack.isEmpty()) {
                return;
            }
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            BackpackLayer.renderOnBack(pose, buffers, light, pack, body.level(), !body.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                    com.senne.imttech.client.armor.VestRenderer.backDepth(body, body.vest()));
            pose.popPose();
        }
    }
}
