package com.senne.imttech.howto.scenes;

import com.senne.imttech.armor.ArmorRegistry;
import com.senne.imttech.armor.PlateTier;
import com.senne.imttech.armor.VestData;
import com.senne.imttech.armor.VestItem;
import com.senne.imttech.backpack.BackpackRegistry;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * How it works voor de body armor (hoofdstuk 109): het vest en zijn plates, voor en achter (een headshot gaat erlangs),
 * slijten en herstellen, de drie magazijnen, je eigen tekst op je rug, kleuren, en de Raven-soldaten met een vest.
 */
public final class ArmorScenes {
    private ArmorScenes() {}

    private static final int BULLET = WeaponScenes.BULLET;

    // ================================================================ Hulpjes

    /** Een vest met twee plates van deze soort, zoveel magazijnen van de Glock, en deze tekst op zijn rug. */
    static ItemStack vest(PlateTier plates, int magazines, String text, DyeColor colour) {
        ItemStack vest = new ItemStack(ArmorRegistry.PLATE_CARRIER.get());
        List<ItemStack> mags = new ArrayList<>();
        for (int i = 0; i < VestData.MAGS; i++) {
            mags.add(i < magazines ? MagazineItem.of(FirearmType.GLOCK_19, 15) : ItemStack.EMPTY);
        }
        ItemStack plate = new ItemStack(ArmorRegistry.plate(plates));
        VestItem.setData(vest, new VestData(plate, plate, mags, text, colour.getId()));
        return vest;
    }

    /** Wat een speler in de animatie draagt: alleen dit vest (op plek Gear.VEST van zijn uitrusting). */
    private static List<ItemStack> wearing(ItemStack vest) {
        List<ItemStack> worn = new ArrayList<>();
        for (int i = 0; i < Gear.VEST; i++) {
            worn.add(ItemStack.EMPTY);
        }
        worn.add(vest);
        return worn;
    }

    private static ItemStack dyed(ItemStack vest, int rgb) {
        ItemStack out = vest.copy();
        out.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, true));
        return out;
    }

    private static String percent(PlateTier tier) {
        return HowToSceneBuilder.number(Math.round(tier.reduction() * 100));
    }

    // ================================================================ Body armor

    /**
     * Het vest groot in beeld, de vier plates, iemand met een vest die van voren en van achteren beschoten wordt (en een
     * headshot), een gebarsten plate en een aambeeld, de magazijnen, de tekst op zijn rug (hij draait zich om), drie kleuren,
     * en een Raven-soldaat met RAVEN op zijn rug.
     */
    public static List<HowToScene> bodyArmor() {
        HowToSceneBuilder s = new HowToSceneBuilder("body_armor", "Body armor").plate(7, 6).height(3);
        Vec3 centre = new Vec3(3.5, 1.0, 3.0);
        ItemStack swat = vest(PlateTier.STEEL, 2, "SWAT", DyeColor.WHITE);
        s.idle(6);

        s.chapter("The Plate Carrier");
        // Zwevend in coyote-bruin: antracietgrijs verdwijnt bijna tegen de donkere achtergrond.
        GunScenes.rowWithText(s, 120, centre.add(0, 1.3, 0), 0.0f, 3.0f, List.of(dyed(swat, 0xA08560)),
                "The *Plate Carrier* is body armor: a *front* and a *back plate*, three magazine pouches, and your own text on"
                        + " the back. Wear it in the *vest slot*, over your chestplate.");
        s.idle(120);

        s.chapter("Plates");
        List<ItemStack> plates = new ArrayList<>();
        for (PlateTier tier : PlateTier.values()) {
            plates.add(new ItemStack(ArmorRegistry.plate(tier)));
        }
        GunScenes.rowWithText(s, 140, centre.add(0, 1.3, 0), 0.75f, 1.1f, plates,
                "Four plates, from weak to strong: *Polymer*, *Steel*, *Titanium* and *Netherite*. The stronger the plate, the"
                        + " more of a hit it stops: %s, %s, %s and %s %%.",
                percent(PlateTier.POLYMER), percent(PlateTier.STEEL), percent(PlateTier.TITANIUM), percent(PlateTier.NETHERITE));
        s.idle(140);

        s.chapter("Front and back");
        // Jij midden in beeld, met je borst naar de camera (zuidwest); een schutter links voor je, een achter je.
        Vec3 you = new Vec3(3.6, 1.0, 2.8);
        Vec3 front = new Vec3(1.6, 1.0, 3.6);
        Vec3 back = new Vec3(5.4, 1.0, 1.3);
        Vec3 chest = you.add(0, 1.15, 0);
        ItemStack glock = GunScenes.loaded(FirearmType.GLOCK_19);
        s.player("you", 330, 0, 45.0f, Map.of(), wearing(swat), you);
        s.stranger("front", "Bram", 330, 0, RobotScenes.yawTo(front, you), Map.of(EquipmentSlot.MAINHAND, glock), front);
        s.stranger("back", "Lotte", 330, 0, RobotScenes.yawTo(back, you), Map.of(EquipmentSlot.MAINHAND, glock), back);
        s.idle(10);
        s.text(110, you.add(0, 2.2, 0), Side.RIGHT,
                "A hit from the front lands on your *front plate*: it stops its part of the hit, and you hear a dull tick.");
        s.idle(20);
        for (int i = 0; i < 3; i++) {
            s.beam(3, WeaponScenes.muzzle(front, RobotScenes.yawTo(front, you)), chest, BULLET);
            s.sound(com.senne.imttech.registry.ModSounds.GLOCK_FIRE.get(), 0.3f, 1.0f);
            s.sound(ArmorRegistry.PLATE_HIT.get(), 0.6f, 1.0f);
            s.idle(12);
        }
        s.idle(54);
        s.text(100, you.add(0, 2.2, 0), Side.RIGHT,
                "From behind it's your *back plate*. Without a back plate, a shot in the back hits in full.");
        s.idle(15);
        for (int i = 0; i < 2; i++) {
            s.beam(3, WeaponScenes.muzzle(back, RobotScenes.yawTo(back, you)), chest.add(0.1, 0, -0.15), BULLET);
            s.sound(com.senne.imttech.registry.ModSounds.GLOCK_FIRE.get(), 0.3f, 1.0f);
            s.sound(ArmorRegistry.PLATE_HIT.get(), 0.6f, 1.0f);
            s.idle(12);
        }
        s.idle(64);
        s.text(90, you.add(0, 2.2, 0), Side.RIGHT, "A *headshot* goes past both plates: aim for the head.");
        s.idle(15);
        s.beam(3, WeaponScenes.muzzle(front, RobotScenes.yawTo(front, you)), you.add(0, 1.7, 0), BULLET);
        s.sound(com.senne.imttech.registry.ModSounds.GLOCK_FIRE.get(), 0.3f, 1.0f);
        s.sound(SoundEvents.PLAYER_HURT, 0.5f, 1.0f);
        s.idle(80);

        s.chapter("Wear and repair");
        ItemStack fresh = new ItemStack(ArmorRegistry.plate(PlateTier.STEEL));
        ItemStack cracked = fresh.copy();
        cracked.setDamageValue(cracked.getMaxDamage());
        BlockPos anvil = new BlockPos(4, 1, 2);
        s.change(anvil, Blocks.ANVIL.defaultBlockState());
        s.sound(SoundEvents.ANVIL_PLACE, 0.3f, 1.0f);
        GunScenes.rowWithText(s, 150, centre.add(0, 1.3, 0), 1.0f, 1.3f, List.of(fresh, cracked),
                "Every hit wears the plate out. A *cracked* plate stops nothing until you repair it on an *anvil* with its own"
                        + " material (a Steel Plate with steel ingots), or swap it. Next to your hearts you see both plates.");
        s.idle(150);
        s.remove(anvil);

        s.chapter("Magazines");
        ItemStack pouch = new ItemStack(ModItems.MAG_POUCH.get());
        GunScenes.rowWithText(s, 140, centre.add(0, 1.3, 0), 1.6f, 2.2f, List.of(pouch, dyed(vest(PlateTier.STEEL, 3, "SWAT", DyeColor.WHITE), 0x5E6B3A)),
                "The vest holds *%s magazines* (or energy cells). *[R]* shows them after the ones in your *Magazine Pouch*,"
                        + " before the MOLLE pouches on your backpack, and stowing fills them in that order.",
                HowToSceneBuilder.number(VestData.MAGS));
        s.idle(140);

        s.chapter("Your text");
        Vec3 turned = new Vec3(3.6, 1.0, 3.2);
        s.player("you2", 160, 0, 225.0f, Map.of(), wearing(swat), turned);
        s.idle(10);
        s.text(140, turned.add(0, 2.2, 0), Side.RIGHT,
                "Right-click the vest (worn: from the *Alt wheel*) to put in plates and magazines, and to type *your own text*"
                        + " for the back (up to %s letters) in one of 16 colours.", HowToSceneBuilder.number(VestData.MAX_TEXT));
        s.idle(150);
        s.player("you3", 90, 0, 225.0f, Map.of(), wearing(vest(PlateTier.TITANIUM, 1, "POLICE", DyeColor.YELLOW)), turned);
        s.idle(90);

        s.chapter("Colours");
        ItemStack tan = dyed(vest(PlateTier.STEEL, 0, "", DyeColor.WHITE), 0xA08560);
        ItemStack olive = dyed(vest(PlateTier.STEEL, 0, "", DyeColor.WHITE), 0x5E6B3A);
        ItemStack camo = vest(PlateTier.STEEL, 0, "", DyeColor.WHITE);
        camo.set(BackpackRegistry.CAMO.get(), true);
        GunScenes.rowWithText(s, 130, centre.add(0, 1.3, 0), 1.2f, 2.0f, List.of(tan, olive, camo),
                "Dye it like a backpack: the 16 dyes (mixed like leather armour), or *brown + lime + green* for *camo*.");
        s.idle(130);

        s.chapter("Raven soldiers");
        Vec3 soldier = new Vec3(3.6, 1.0, 3.0);
        CompoundTag data = new CompoundTag();
        data.put("Vest", ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, com.senne.imttech.armor.Vests.ravenVest(
                net.minecraft.util.RandomSource.create(109), ItemStack.EMPTY)).getOrThrow());
        s.actor("soldier", ModEntities.RAVEN_SOLDIER.get(), HowToSceneBuilder.UNTIL_END, 0, 225.0f, data, soldier);
        s.idle(10);
        s.text(140, soldier.add(0, 2.2, 0), Side.RIGHT,
                "Some *Raven soldiers* wear a vest too (%s %% of them), with plates: aim for the head. After the fight, their vest"
                        + " lies in their body.", HowToSceneBuilder.number(Math.round(ImtConfig.Armor.RAVEN_VEST_CHANCE * 100)));
        s.idle(145);
        return List.of(s.build());
    }
}
