package com.senne.imttech.client.weapons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.ImtTech;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * De losse onderdelen van de wapens (hoofdstuk 35): elk geweer in vier stukken (body, moving, trigger, cell) en elke
 * granaat in twee (body, light). Ze staan in models/weapon/ en worden gemaakt door tools/generate_weapon_models.py.
 * Minecraft laadt zulke losse modellen alleen als je ze hier aanmeldt.
 */
public final class WeaponModels {
    private WeaponModels() {}

    public static final String[] GUNS = {"laser_pistol", "laser_rifle", "laser_sniper", "laser_shotgun"};
    public static final String[] GUN_PARTS = {"body", "moving", "trigger", "cell"};
    public static final String[] GRENADES = {"emp_grenade", "sticky_emp_grenade", "frag_grenade", "sticky_frag_grenade",
            "smoke_grenade", "flashbang"};
    public static final String[] GRENADE_PARTS = {"body", "light"};

    public static ModelResourceLocation location(String name, String part) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID,
                "weapon/" + name + "/" + part));
    }

    /** Bij het opstarten: zeg Minecraft dat het deze modellen moet inladen. */
    public static void register(ModelEvent.RegisterAdditional event) {
        for (String gun : GUNS) {
            for (String part : GUN_PARTS) {
                event.register(location(gun, part));
            }
        }
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
        Minecraft.getInstance().getItemRenderer().renderModelLists(get(name, part), stack, light, overlay, pose,
                buffers.getBuffer(Sheets.cutoutBlockSheet()));
    }

    /** De band van een smokebom heeft de kleur van zijn rook (grijs, of de kleurstof waarmee je hem maakte). */
    public static void registerItemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0
                        ? 0xFF000000 | SmokeView.rgb(stack.get(com.senne.imttech.registry.ModDataComponents.SMOKE_COLOR.get()))
                        : -1,
                com.senne.imttech.registry.ModItems.SMOKE_GRENADE.get());
    }
}
