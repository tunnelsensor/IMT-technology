package com.senne.imttech.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.ImtTech;
import com.senne.imttech.armor.VestData;
import com.senne.imttech.armor.VestItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Het vest tekenen (hoofdstuk 109): het model uit tools/generate_vest_models.py in zijn kleur (of camo), de magazijnen die
 * boven uit de vakjes op je buik steken, en je eigen tekst op het zwarte klittenbandvlak op je rug, in zijn kleur.
 *
 * Het model staat in de pixels van je lijf (zoals de schouderbanden van de rugzak): je romp is x 4 tot 12, y 4 (je middel)
 * tot 16 (je schouders), z 6 (je rug) tot 10 (je borst). {@link #renderModel} tekent het in die ruimte (1 = 16 pixels);
 * {@link #renderOnBody} zet het eerst over een lijf. Draag je ook een borstplaat (jouw keuze: het vest gaat eroverheen), dan
 * tekenen we het een maatje groter en een tikje hoger, zodat het over het harnas zit en niet erin verdwijnt.
 */
public final class VestRenderer {
    private VestRenderer() {}

    /** Een magazijn dat boven uit een vakje steekt (een los model, zie register). */
    public static final ModelResourceLocation MAG_TOP = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "vest/mag_top"));

    /** Zo ver liggen de drie magazijnvakjes uit elkaar (model-pixels, zoals POUCH_STEP in de generator). */
    private static final float POUCH_STEP = 2.2f;
    /** Het klittenbandvlak voor de tekst (model-pixels): het midden, en de buitenkant (z) naar achteren. */
    private static final float TEXT_X = 8.0f;
    private static final float TEXT_Y = 12.6f;
    private static final float TEXT_Z = 4.6f;
    /** Zo breed en hoog mag de tekst daar worden (model-pixels), en zo veel hoger dan breed mogen de letters zijn. */
    private static final float TEXT_WIDTH = 5.0f;
    private static final float TEXT_HEIGHT = 1.9f;
    private static final float TEXT_STRETCH = 1.45f;
    /** Over een borstplaat: zoveel breder en dieper, en zoveel pixels hoger (de borstplaat is een pixel dikker dan je lijf). */
    private static final float OVER_ARMOUR_SCALE = 1.15f;
    private static final float OVER_ARMOUR_RAISE = 0.8f;

    /** Bij het opstarten: zeg Minecraft dat het het losse magazijnmodel moet inladen. */
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(MAG_TOP);
    }

    /** Draagt dit wezen een borstplaat die Minecraft over zijn lijf tekent (dan moet het vest eroverheen)? */
    public static boolean wearsChestArmour(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem;
    }

    /**
     * Het vest over een lijf, met {@code pose} in de ruimte van het lijf van een mensmodel (na body.translateAndRotate: +y is
     * omlaag, -z is je borst).
     */
    public static void renderOnBody(PoseStack pose, MultiBufferSource buffers, int light, ItemStack vest, @Nullable Level level,
                                    boolean overArmour) {
        pose.pushPose();
        // Van de pixels van het lijf naar de ruimte van het lijf: een halve draai om x (boven wordt onder, borst wordt voor).
        pose.mulPose(Axis.XP.rotationDegrees(180));
        pose.translate(-0.5, -1.0, -0.5);
        if (overArmour) {
            pose.translate(0.5, 0, 0.5);
            pose.scale(OVER_ARMOUR_SCALE, 1.0f, OVER_ARMOUR_SCALE);
            pose.translate(-0.5, OVER_ARMOUR_RAISE / 16f, -0.5);
        }
        renderModel(pose, buffers, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, vest, level, null);
        pose.popPose();
    }

    /**
     * Het vest in zijn eigen ruimte (1 = 16 model-pixels, de hoek (0, 0, 0) van het model hier): het model, de magazijnen en
     * de tekst. {@code shown}: wat erin zit zoals het getekend moet worden (het scherm toont de tekst die je net typt), of
     * null voor wat er echt in zit.
     */
    public static void renderModel(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ItemStack vest,
                                   @Nullable Level level, @Nullable VestData shown) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer renderer = mc.getItemRenderer();
        BakedModel model = renderer.getModel(vest, level, null, 0);
        pose.pushPose();
        // ItemRenderer schuift het model zelf een half blok terug (naar zijn midden); dat zetten we hier recht.
        pose.translate(0.5, 0.5, 0.5);
        renderer.render(vest, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, model);
        pose.popPose();
        VestData data = shown != null ? shown : VestItem.data(vest);
        BakedModel top = mc.getModelManager().getModel(MAG_TOP);
        for (int i = 0; i < data.mags().size(); i++) {
            if (data.mags().get(i).isEmpty()) {
                continue;
            }
            pose.pushPose();
            pose.translate(i * POUCH_STEP / 16f, 0, 0);
            renderer.renderModelLists(top, ItemStack.EMPTY, light, overlay, pose, buffers.getBuffer(Sheets.cutoutBlockSheet()));
            pose.popPose();
        }
        renderText(pose, buffers, light, data);
    }

    /**
     * De tekst op je rug (jouw wens: "zelf een tekst zetten zoals 'SWAT'"), zo groot als op het klittenbandvlak past: een
     * korte tekst groot (de letters iets hoger dan breed, zoals op een echt vest), een lange kleiner. Te lezen voor wie achter
     * je staat: het lettertype tekent naar rechts en naar beneden, dus van achteren gezien spiegelen we x, y en z.
     */
    public static void renderText(PoseStack pose, MultiBufferSource buffers, int light, VestData data) {
        String text = data.text();
        if (text.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int width = font.width(text);
        if (width <= 0) {
            return;
        }
        float sx = TEXT_WIDTH / width;
        float sy = Math.min(sx * TEXT_STRETCH, TEXT_HEIGHT / 7.0f);
        pose.pushPose();
        pose.translate(TEXT_X / 16f, TEXT_Y / 16f, (TEXT_Z - 0.04f) / 16f);
        pose.scale(-sx / 16f, -sy / 16f, -sx / 16f);
        int colour = 0xFF000000 | data.dye().getTextColor();
        font.drawInBatch(text, -width / 2.0f, -3.5f, colour, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }

    /**
     * Zoveel dikker maakt het vest je rug (blokken): de rugzak hangt dan zoveel verder naar achteren. 0 zonder vest. Met een
     * borstplaat eronder iets meer (het vest is dan een maatje groter).
     */
    public static float backDepth(LivingEntity entity, ItemStack vest) {
        if (!VestItem.isVest(vest)) {
            return 0;
        }
        float px = wearsChestArmour(entity) ? 8 - (8 - TEXT_Z) * OVER_ARMOUR_SCALE - 6 : TEXT_Z - 6;
        return -px / 16f;
    }

    /** Zoveel steekt het vest voor je borst uit (blokken, tot de voorkant van de magazijnvakjes). 0 zonder vest. */
    public static float frontDepth(LivingEntity entity, ItemStack vest) {
        if (!VestItem.isVest(vest)) {
            return 0;
        }
        float front = 12.6f;
        float px = wearsChestArmour(entity) ? 8 + (front - 8) * OVER_ARMOUR_SCALE - 10 : front - 10;
        return px / 16f;
    }
}
