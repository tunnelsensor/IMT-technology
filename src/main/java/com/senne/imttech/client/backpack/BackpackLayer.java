package com.senne.imttech.client.backpack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.backpack.BackpackItem;
import com.senne.imttech.guns.Gear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Je rugzak op je rug (hoofdstuk 72), voor anderen en voor jezelf met F5: het model uit
 * tools/generate_backpack_models.py, in zijn kleur (of camo), met de schouderbanden tegen je rug en de MOLLE-banden naar
 * buiten. De bovenkant zit op je schouders; een Rucksack steekt met zijn slaapmat boven je hoofd uit en zakt tot je
 * heupen, zoals een echte. Draag je een borstplaat, dan zit hij een pixel verder naar achteren. Van voren zie je de
 * schouderbanden over je schouders en voor je borst, met een borstband (en bij de Rucksack een heupband): de modellen
 * models/backpack/straps.json en hip_belt.json.
 *
 * Het lichaam van een speler (stap 4) en een Raven-soldaat met een rugzak (stap 5) tekenen hem met
 * {@link #renderOnBack}.
 */
public class BackpackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** De schouderbanden en de heupband (geen items: losse modellen, zie register). */
    public static final ModelResourceLocation STRAPS = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(com.senne.imttech.ImtTech.MODID, "backpack/straps"));
    public static final ModelResourceLocation HIP_BELT = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(com.senne.imttech.ImtTech.MODID, "backpack/hip_belt"));

    /** Bij het opstarten: zeg Minecraft dat het de banden moet inladen. */
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(STRAPS);
        event.register(HIP_BELT);
    }

    public BackpackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new BackpackLayer(renderer));
            }
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        ItemStack pack = Gear.back(player);
        if (pack.isEmpty()) {
            return;
        }
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        if (player.isCrouching()) {
            pose.translate(0, 0.05, 0);
        }
        renderOnBack(pose, buffers, light, pack, player.level(), !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty());
        pose.popPose();
    }

    /** Per maat: de bovenkant van de klep en de achterkant van de schouderbanden (model-pixels), en waar de bovenkant komt. */
    private static float[] anchor(BackpackItem.Size size) {
        return switch (size) {
            case ASSAULT -> new float[]{10.75f, 10.6f, 0.0f};
            case PATROL -> new float[]{12.9f, 11.1f, -0.02f};
            case RUCKSACK -> new float[]{15.0f, 11.9f, -0.06f};
        };
    }

    /** Zo dik is de rugzak (van je rug tot de voorkant van de voorste zak), in blokken. */
    public static float depth(BackpackItem.Size size) {
        return switch (size) {
            case ASSAULT -> 6.6f / 16f;
            case PATROL -> 7.7f / 16f;
            case RUCKSACK -> 9.4f / 16f;
        };
    }

    /**
     * Zoveel moet een geweer op je rug (GearLayer) naar achteren, zodat het over de rugzak hangt in plaats van erdoor
     * (jouw keuze: "het geweer hangt dan over de rugzak heen"). 0 zonder rugzak.
     */
    public static float slingShift(AbstractClientPlayer player) {
        BackpackItem.Size size = BackpackItem.sizeOf(Gear.back(player));
        return size == null ? 0 : depth(size) + 0.02f;
    }

    /**
     * De rugzak tekenen, met {@code pose} in de ruimte van het lijf van een mensmodel (na body.translateAndRotate: +y is
     * omlaag, +z is je rug).
     */
    public static void renderOnBack(PoseStack pose, MultiBufferSource buffers, int light, ItemStack pack, Level level, boolean armour) {
        BackpackItem.Size size = BackpackItem.sizeOf(pack);
        if (size == null) {
            return;
        }
        float[] a = anchor(size);
        pose.pushPose();
        // Het model staat rechtop met zijn voorkant naar -z; op je rug moet het ondersteboven (+y is hier omlaag) en
        // achterstevoren (de banden tegen je rug): samen een halve draai om de x-as.
        float top = a[2] + (a[0] - 8) / 16f;
        float back = 0.13f + (armour ? 1 / 16f : 0) + (a[1] - 8) / 16f;
        pose.translate(0, top, back);
        pose.mulPose(Axis.XP.rotationDegrees(180));
        Minecraft.getInstance().getItemRenderer().renderStatic(pack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, level, 0);
        pose.popPose();
        renderStraps(pose, buffers, light, size, armour);
    }

    /**
     * De banden voor je borst. Hun model staat in de pixels van je lijf (romp x 4 tot 12, y 4 tot 16 met je schouders
     * boven, z 6 tot 10 met je borst op 10); een halve draai om de x-as zet dat in de ruimte van het lijf (+y omlaag, -z
     * voor). Met een borstplaat een maatje groter, zodat ze over het harnas lopen (dat is een pixel dikker dan je lijf).
     */
    private static void renderStraps(PoseStack pose, MultiBufferSource buffers, int light, BackpackItem.Size size, boolean armour) {
        pose.pushPose();
        if (armour) {
            pose.translate(0, 0.375, 0);
            pose.scale(1.25f, 1.17f, 1.5f);
            pose.translate(0, -0.375, 0);
        }
        pose.mulPose(Axis.XP.rotationDegrees(180));
        pose.translate(-0.5, -1.0, -0.5);
        var renderer = Minecraft.getInstance().getItemRenderer();
        var models = Minecraft.getInstance().getModelManager();
        renderer.renderModelLists(models.getModel(STRAPS), ItemStack.EMPTY, light, OverlayTexture.NO_OVERLAY, pose,
                buffers.getBuffer(Sheets.cutoutBlockSheet()));
        if (size == BackpackItem.Size.RUCKSACK) {
            renderer.renderModelLists(models.getModel(HIP_BELT), ItemStack.EMPTY, light, OverlayTexture.NO_OVERLAY, pose,
                    buffers.getBuffer(Sheets.cutoutBlockSheet()));
        }
        pose.popPose();
    }
}
