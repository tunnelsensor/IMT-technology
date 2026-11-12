package com.senne.imttech.client.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.ImtTech;
import com.senne.imttech.guns.Caliber;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.registry.ModItems;
import java.util.List;
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
import org.jetbrains.annotations.Nullable;

/**
 * De losse modellen van de vuurwapens (hoofdstuk 52): elk onderdeel van elk wapen per materiaal
 * (models/firearm/glock_19/slide_steel.json...), het magazijn, de patroon, de huls, en de holster en pouches. Gemaakt
 * door tools/generate_gun_models.py. In je eigen beeld en op de Gunsmith Table tekent de mod ze los. Sinds hoofdstuk 74
 * zijn de Glock, het magazijn en de munitie OBJ-modellen (NeoForge laadt ze in; zie tools/glock19_model.py): voor deze
 * klasse maakt dat niets uit, het blijven gewone gebakken modellen.
 *
 * Ook: de losse onderdelen als item kiezen hun plaatje naar hun materiaal (de "override" imttech:material in
 * models/item/gun_slide.json: 0 = polymeer, 1 = staal, 2 = titanium).
 *
 * Hoofdstuk 103: elk wapen zijn eigen map (models/firearm/mp5/...). Een onderdeel kan losse bewegende stukken hebben
 * ({@link #extras}: de spanhendel van de MP5 hoort bij zijn Bolt Carrier, maar beweegt alleen als je doorlaadt), en een
 * lang wapen heeft een model van zijn sling.
 */
public final class FirearmModels {
    private FirearmModels() {}

    public static final String[] GEAR = {"holster", "grenade_pouch", "mag_pouch"};

    public static ModelResourceLocation location(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "firearm/" + path));
    }

    /** Het model van dit onderdeel van dit wapen in dit materiaal. */
    public static ModelResourceLocation part(FirearmType type, GunPart part, GunMaterial material) {
        GunMaterial shown = part.accepts(type, material) ? material : GunMaterial.STEEL;
        return location(type.id + "/" + part.id + "_" + shown.id);
    }

    /**
     * De losse bewegende stukken van een onderdeel (hoofdstuk 103), bij naam: "slide_handle" is de spanhendel van de
     * MP5. Ze hebben het materiaal van hun onderdeel en zitten erin zodra het onderdeel erin zit. Moet kloppen met de
     * modelbestanden (tools/mp5_model.py: de namen in build()).
     */
    public static List<String> extras(FirearmType type, GunPart part) {
        if (type == FirearmType.MP5 && part == GunPart.SLIDE) {
            return List.of("slide_handle");
        }
        return List.of();
    }

    /** Het model van een los bewegend stuk ("slide_handle") van dit wapen in dit materiaal. */
    public static ModelResourceLocation extra(FirearmType type, String name, GunMaterial material) {
        GunMaterial shown = material == GunMaterial.TITANIUM ? material : GunMaterial.STEEL;
        return location(type.id + "/" + name + "_" + shown.id);
    }

    /** Het model van de sling aan dit wapen (alleen de lange wapens). */
    public static ModelResourceLocation sling(FirearmType type) {
        return location(type.id + "/sling_steel");
    }

    /** Het hele wapen in één model (zoals in je inventaris): voor op je rug, aan je sling. */
    public static ModelResourceLocation whole(FirearmType type) {
        return ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, type.id));
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

    /** Het model van een losse patroon van dit kaliber (hoofdstuk 103). */
    public static ModelResourceLocation round(Caliber caliber) {
        return switch (caliber) {
            case MM9 -> location("round");
        };
    }

    /** Het model van een lege huls van dit kaliber (hoofdstuk 103). */
    public static ModelResourceLocation casing(Caliber caliber) {
        return switch (caliber) {
            case MM9 -> location("casing");
        };
    }

    /** Bij het opstarten: zeg Minecraft dat het deze modellen moet inladen. */
    public static void register(ModelEvent.RegisterAdditional event) {
        for (FirearmType type : FirearmType.values()) {
            for (GunPart part : GunPart.values()) {
                for (GunMaterial material : part.materials(type)) {
                    event.register(part(type, part, material));
                    for (String extra : extras(type, part)) {
                        event.register(extra(type, extra, material));
                    }
                }
            }
            if (type.longGun) {
                event.register(sling(type));
            }
            if (!type.tube()) {
                event.register(magazine(type, false));
                event.register(magazine(type, true));
            }
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
     * Het hele wapen uit zijn losse onderdelen (elk in zijn materiaal), met zijn magazijn en (als je wilt) zijn sling:
     * voor de Gunsmith Table en op je lijf. {@code skip}: een onderdeel dat je net wegsleept. 1 blok = 16 pixels.
     */
    public static void renderParts(PoseStack pose, MultiBufferSource buffers, ItemStack gun, int light, boolean withSling,
                                   @Nullable GunPart skip) {
        FirearmType type = FirearmItem.typeOf(gun);
        if (type == null) {
            return;
        }
        GunParts parts = FirearmItem.parts(gun);
        for (GunPart part : GunPart.values()) {
            PartState state = parts.get(part);
            if (state == null || part == skip) {
                continue;
            }
            render(pose, buffers, part(type, part, state.material()), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            for (String extra : extras(type, part)) {
                render(pose, buffers, extra(type, extra, state.material()), light,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            }
        }
        if (!type.tube() && FirearmItem.hasMagazine(gun)) {
            render(pose, buffers, magazine(type, FirearmItem.roundsInMagazine(gun) == 0), light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        }
        if (withSling && type.longGun && FirearmItem.hasSling(gun)) {
            render(pose, buffers, sling(type), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        }
    }

    /**
     * De losse onderdelen als item: het plaatje van hun materiaal (zie de overrides in models/item/gun_*.json). Sinds
     * hoofdstuk 74 ook het magazijn: leeg = het model zonder patroon (models/item/glock_19_magazine_empty.json).
     */
    public static void registerProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (FirearmType type : FirearmType.values()) {
                for (GunPart part : GunPart.values()) {
                    ItemProperties.register(ModItems.gunPart(type, part), ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "material"),
                            (stack, level, entity, seed) -> GunPartItem.materialOf(stack).ordinal());
                }
            }
            for (MagazineItem magazine : ModItems.magazines()) {
                ItemProperties.register(magazine, ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "empty"),
                        (stack, level, entity, seed) -> MagazineItem.rounds(stack) == 0 ? 1 : 0);
            }
        });
    }

    /** Een vuurwapen houd je in derde persoon met twee handen vast, zoals de lasergeweren. */
    public static void registerExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        for (FirearmType type : FirearmType.values()) {
            event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
                @Override
                public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity entity,
                        net.minecraft.world.InteractionHand hand, ItemStack stack) {
                    return net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD;
                }
            }, ModItems.firearm(type));
        }
    }
}
