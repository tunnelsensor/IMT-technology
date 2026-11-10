package com.senne.imttech.client.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.ImtTech;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * De losse modellen van de vuurwapens (hoofdstuk 52): elk onderdeel van de Glock 19 per materiaal
 * (models/firearm/glock_19/slide_steel.json...), het magazijn, de patroon, de huls, en de holster en pouches. Gemaakt
 * door tools/generate_gun_models.py. In je eigen beeld en op de Gunsmith Table tekent de mod ze los. Sinds hoofdstuk 74
 * zijn de Glock, het magazijn en de munitie OBJ-modellen (NeoForge laadt ze in; zie tools/glock19_model.py): voor deze
 * klasse maakt dat niets uit, het blijven gewone gebakken modellen.
 *
 * Ook: de losse onderdelen als item kiezen hun plaatje naar hun materiaal (de "override" imttech:material in
 * models/item/gun_slide.json: 0 = polymeer, 1 = staal, 2 = titanium).
 */
public final class FirearmModels {
    private FirearmModels() {}

    public static final String[] GEAR = {"holster", "grenade_pouch", "mag_pouch"};

    public static ModelResourceLocation location(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "firearm/" + path));
    }

    /** Het model van dit onderdeel van dit pistool in dit materiaal. */
    public static ModelResourceLocation part(FirearmType type, GunPart part, GunMaterial material) {
        GunMaterial shown = part.accepts(material) ? material : GunMaterial.STEEL;
        return location(type.id + "/" + part.id + "_" + shown.id);
    }

    public static ModelResourceLocation magazine(FirearmType type) {
        return location(type.id + "/magazine");
    }

    /**
     * Hoofdstuk 74: een leeg magazijn ziet er leeg uit (bovenin de opvolger in plaats van een patroon), zoals TACZ de
     * patronen in een magazijn laat zien.
     */
    public static ModelResourceLocation magazine(FirearmType type, boolean empty) {
        return location(type.id + (empty ? "/magazine_empty" : "/magazine"));
    }

    /** Het model van dit magazijn (vol of leeg). */
    public static ModelResourceLocation magazine(ItemStack magazine) {
        FirearmType type = MagazineItem.typeOf(magazine);
        return magazine(type == null ? FirearmType.GLOCK_19 : type, MagazineItem.rounds(magazine) == 0);
    }

    /** Bij het opstarten: zeg Minecraft dat het deze modellen moet inladen. */
    public static void register(ModelEvent.RegisterAdditional event) {
        for (FirearmType type : FirearmType.values()) {
            for (GunPart part : GunPart.values()) {
                for (GunMaterial material : part.materials()) {
                    event.register(part(type, part, material));
                }
            }
            event.register(magazine(type, false));
            event.register(magazine(type, true));
        }
        event.register(location("casing"));
        event.register(location("round"));
        for (String gear : GEAR) {
            event.register(location(gear));
        }
    }

    public static BakedModel get(ModelResourceLocation location) {
        return Minecraft.getInstance().getModelManager().getModel(location);
    }

    /**
     * Tekent één model, met zijn hoek (0, 0, 0) waar {@code pose} nu staat en 1 blok = 16 pixels van het model. Wat in
     * het model gloeit (het stipje op de korrel), gloeit vanzelf.
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, ModelResourceLocation location, int light, int overlay) {
        Minecraft.getInstance().getItemRenderer().renderModelLists(get(location), ItemStack.EMPTY, light, overlay, pose,
                buffers.getBuffer(Sheets.cutoutBlockSheet()));
    }

    /**
     * De losse onderdelen als item: het plaatje van hun materiaal (zie de overrides in models/item/gun_*.json). Sinds
     * hoofdstuk 74 ook het magazijn: leeg = het model zonder patroon (models/item/glock_19_magazine_empty.json).
     */
    public static void registerProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (GunPart part : GunPart.values()) {
                ItemProperties.register(ModItems.gunPart(part), ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "material"),
                        (stack, level, entity, seed) -> GunPartItem.materialOf(stack).ordinal());
            }
            ItemProperties.register(ModItems.GLOCK_19_MAGAZINE.get(), ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "empty"),
                    (stack, level, entity, seed) -> MagazineItem.rounds(stack) == 0 ? 1 : 0);
        });
    }

    /** Een pistool houd je in derde persoon met twee handen vast, zoals de lasergeweren. */
    public static void registerExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity entity,
                    net.minecraft.world.InteractionHand hand, ItemStack stack) {
                return net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        }, ModItems.GLOCK_19.get());
    }
}
