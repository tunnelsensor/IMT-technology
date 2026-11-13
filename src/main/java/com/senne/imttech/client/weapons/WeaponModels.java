package com.senne.imttech.client.weapons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.ImtTech;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserPart;
import com.senne.imttech.weapons.LaserPartItem;
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
 * De losse modellen van de wapens (hoofdstuk 35): elke granaat in twee stukken (body, light), en sinds hoofdstuk 55 elk
 * lasergeweer in zijn <b>onderdelen</b>: per onderdeel en per kwaliteit een model (weapon/laser_rifle/emitter_titanium),
 * voor wat bij een schot beweegt nog een los model (..._moving), plus de cel, de kern (de Nether Star), de sling, en de
 * band van de sling over je lijf (weapon/sling_strap). Ze staan in models/weapon/ en worden gemaakt door
 * tools/generate_weapon_models.py. Minecraft laadt zulke losse modellen alleen als je ze hier aanmeldt.
 */
public final class WeaponModels {
    private WeaponModels() {}

    public static final String[] GRENADES = {"emp_grenade", "sticky_emp_grenade", "frag_grenade", "sticky_frag_grenade",
            "smoke_grenade", "flashbang"};
    public static final String[] GRENADE_PARTS = {"body", "light"};

    public static ModelResourceLocation location(String name, String part) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID,
                "weapon/" + name + "/" + part));
    }

    /** Een los model in models/weapon/ zonder map (de band van de sling). */
    public static ModelResourceLocation location(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "weapon/" + path));
    }

    /** Het model van dit onderdeel van dit geweer in deze kwaliteit (het stilstaande deel). */
    public static ModelResourceLocation part(GunType type, LaserPart part, GunMaterial material) {
        GunMaterial shown = LaserPart.MATERIALS.contains(material) ? material : GunMaterial.TITANIUM;
        return location(type.id, part.id + "_" + shown.id);
    }

    /** Het bewegende deel van dit onderdeel (de slede, de spanhendel...), alleen voor onderdelen die er een hebben (zie LaserRig.moves). */
    public static ModelResourceLocation moving(GunType type, LaserPart part, GunMaterial material) {
        GunMaterial shown = LaserPart.MATERIALS.contains(material) ? material : GunMaterial.TITANIUM;
        return location(type.id, part.id + "_" + shown.id + "_moving");
    }

    /** Bij het opstarten: zeg Minecraft dat het deze modellen moet inladen. */
    public static void register(ModelEvent.RegisterAdditional event) {
        for (GunType type : GunType.values()) {
            for (LaserPart part : LaserPart.wearing()) {
                for (GunMaterial material : LaserPart.MATERIALS) {
                    event.register(part(type, part, material));
                    if (LaserRig.moves(type, part)) {
                        event.register(moving(type, part, material));
                    }
                }
            }
            event.register(location(type.id, "cell"));
            event.register(location(type.id, "core"));
            if (LaserPart.SLING.fitsOn(type)) {
                event.register(location(type.id, "sling"));
            }
        }
        event.register(location("sling_strap"));
        for (String grenade : GRENADES) {
            for (String part : GRENADE_PARTS) {
                event.register(location(grenade, part));
            }
        }
    }

    public static BakedModel get(String name, String part) {
        return Minecraft.getInstance().getModelManager().getModel(location(name, part));
    }

    /**
     * Tekent één onderdeel, met zijn hoek (0, 0, 0) op de plek waar {@code pose} nu staat en 1 blok = 16 pixels van het
     * model. Wat in het model gloeit, gloeit vanzelf (vol licht, zie "neoforge_data" in de modellen).
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, String name, String part, int light, int overlay) {
        render(pose, buffers, name, part, light, overlay, ItemStack.EMPTY);
    }

    /**
     * Hetzelfde, met een item erbij: delen van het model die een kleur krijgen ("tintindex", zoals de band van een
     * gekleurde smokebom), krijgen de kleur van dat item.
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, String name, String part, int light, int overlay,
                              ItemStack stack) {
        render(pose, buffers, location(name, part), light, overlay, stack);
    }

    /** Tekent dit model (zie hierboven). */
    public static void render(PoseStack pose, MultiBufferSource buffers, ModelResourceLocation location, int light, int overlay) {
        render(pose, buffers, location, light, overlay, ItemStack.EMPTY);
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, ModelResourceLocation location, int light, int overlay,
                              ItemStack stack) {
        Minecraft.getInstance().getItemRenderer().renderModelLists(Minecraft.getInstance().getModelManager().getModel(location),
                stack, light, overlay, pose, buffers.getBuffer(Sheets.cutoutBlockSheet()));
    }

    /** De losse laseronderdelen als item: het plaatje van hun kwaliteit (zie de overrides in models/item/laser_*_*.json). */
    public static void registerProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (GunType type : GunType.values()) {
                for (LaserPart part : LaserPart.wearing()) {
                    ItemProperties.register(ModItems.laserPart(type, part), ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "material"),
                            (stack, level, entity, seed) -> LaserPartItem.materialOf(stack).ordinal());
                }
            }
        });
    }

    /**
     * Een lasergeweer houd je in derde persoon met twee handen vast, zoals een geladen kruisboog (voor anderen, en voor
     * jezelf met F5).
     */
    public static void registerExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity entity,
                    net.minecraft.world.InteractionHand hand, ItemStack stack) {
                return net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }

            /** Hoofdstuk 103, stap 7: als item tekenen we het geweer zelf, zodat je ziet wat erop zit (FirearmItemRenderer). */
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.senne.imttech.client.guns.FirearmModels.itemRenderer();
            }
        }, ModItems.LASER_PISTOL.get(), ModItems.LASER_RIFLE.get(), ModItems.LASER_SNIPER.get(), ModItems.LASER_SHOTGUN.get());
    }

    /** De band van een smokebom heeft de kleur van zijn rook (grijs, of de kleurstof waarmee je hem maakte). */
    public static void registerItemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0
                        ? 0xFF000000 | SmokeView.rgb(stack.get(com.senne.imttech.registry.ModDataComponents.SMOKE_COLOR.get()))
                        : -1,
                ModItems.SMOKE_GRENADE.get());
    }
}
