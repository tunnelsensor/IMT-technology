package com.senne.imttech.howto.scenes;

import com.senne.imttech.backpack.BackpackBlock;
import com.senne.imttech.backpack.BackpackItem;
import com.senne.imttech.backpack.BackpackRegistry;
import com.senne.imttech.backpack.MolleItem;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * How it works voor de militaire rugzakken (hoofdstuk 72): "Military backpacks" (de maten, op je rug, de rits, neerzetten,
 * verven, van achteren openen, het lichaam van een speler) en "MOLLE pouches" (ophangen, zonder rits, de magazijn- en
 * granaatpouch, de dump pouch, de IFAK, de utility pouch).
 */
public final class BackpackScenes {
    private BackpackScenes() {}

    /** Zo'n blauwachtige kleur gebruikt de mod voor "iets gaat open of wordt gemeten". */
    private static final int ORANGE = 0xFFA93F;
    private static final int RED = 0xFF4545;

    // ================================================================ Hulpjes

    /** Een rugzak van deze maat in deze kleur ({@code -1} = olijfgroen, {@code -2} = camo). */
    private static ItemStack pack(BackpackItem.Size size, int colour) {
        ItemStack pack = new ItemStack(BackpackRegistry.item(size));
        if (colour == -2) {
            pack.set(BackpackRegistry.CAMO.get(), true);
        } else if (colour >= 0) {
            pack.set(DataComponents.DYED_COLOR, new DyedItemColor(colour, true));
        }
        return pack;
    }

    /** Dezelfde rugzak met MOLLE-pouches op al zijn plekken (in zijn kleur). */
    private static ItemStack withPouches(ItemStack pack) {
        BackpackItem.Size size = BackpackItem.sizeOf(pack);
        MolleItem.Kind[] order = switch (size) {
            case ASSAULT -> new MolleItem.Kind[]{MolleItem.Kind.DUMP, MolleItem.Kind.IFAK};
            case PATROL -> new MolleItem.Kind[]{MolleItem.Kind.MAG, MolleItem.Kind.GRENADE, MolleItem.Kind.UTILITY};
            case RUCKSACK -> new MolleItem.Kind[]{MolleItem.Kind.IFAK, MolleItem.Kind.DUMP, MolleItem.Kind.MAG, MolleItem.Kind.UTILITY};
        };
        List<ItemStack> pouches = new ArrayList<>();
        for (MolleItem.Kind kind : order) {
            ItemStack pouch = new ItemStack(BackpackRegistry.molle(kind));
            if (BackpackItem.camo(pack)) {
                pouch.set(BackpackRegistry.CAMO.get(), true);
            } else if (pack.has(DataComponents.DYED_COLOR)) {
                pouch.set(DataComponents.DYED_COLOR, pack.get(DataComponents.DYED_COLOR));
            }
            pouches.add(pouch);
        }
        ItemStack out = pack.copy();
        BackpackItem.setMolle(out, pouches);
        return out;
    }

    /** Een neergezette rugzak (het blok), met de MOLLE-banden naar de camera (zuid). */
    private static BlockState block(BackpackItem.Size size) {
        return BackpackRegistry.BACKPACK_BLOCK.get().defaultBlockState()
                .setValue(BackpackBlock.FACING, Direction.SOUTH)
                .setValue(BackpackBlock.SIZE, BackpackBlock.sizeProperty(size));
    }

    /** De gegevens van de block entity van een neergezette rugzak: de rugzak zelf. */
    private static CompoundTag packData(ItemStack pack) {
        CompoundTag tag = new CompoundTag();
        tag.put("Pack", ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, pack).getOrThrow());
        return tag;
    }

    /** Een rugzak neerzetten (het blok en zijn inhoud). */
    private static void put(HowToSceneBuilder s, BlockPos pos, ItemStack pack) {
        s.place(pos, block(BackpackItem.sizeOf(pack)));
        s.blockData(pos, packData(pack));
    }

    /** De uitrusting van een speler: alleen een rugzak op zijn rug (de holster, pouches en slings leeg). */
    private static List<ItemStack> wearing(ItemStack pack) {
        return List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, pack);
    }

    // ================================================================ Military backpacks

    /**
     * De rugzakken: drie maten op een rij, een speler met een Patrol Pack (de rits, neerzetten), de kleuren en camo, van
     * achteren in de rugzak van een ander, en het lichaam van een speler met zijn rugzak bovenop.
     */
    public static List<HowToScene> backpacks() {
        HowToSceneBuilder s = new HowToSceneBuilder("backpacks", "Military backpacks").plate(8, 6).height(3);
        BlockPos left = new BlockPos(2, 1, 1);
        BlockPos middle = new BlockPos(4, 1, 1);
        BlockPos right = new BlockPos(6, 1, 1);
        Vec3 you = new Vec3(4.5, 1.0, 4.2);
        s.idle(6);
        put(s, left, pack(BackpackItem.Size.ASSAULT, -1));
        s.idle(4);
        put(s, middle, pack(BackpackItem.Size.PATROL, -1));
        s.idle(4);
        put(s, right, pack(BackpackItem.Size.RUCKSACK, -1));
        s.sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.5f, 0.9f);
        s.idle(10);

        s.chapter("Three sizes");
        s.text(140, Vec3.atCenterOf(middle).add(0, 0.8, 0), Side.RIGHT,
                "Three military backpacks: the *Assault Pack* (18 slots), the *Patrol Pack* (27) and the *Rucksack* (36, on a"
                        + " titanium frame). No upgrades and no boxes in boxes: no backpack, shulker box or ammo box fits inside, and"
                        + " a holster or pouch only when it's empty.");
        s.idle(145);

        s.chapter("On your back");
        ItemStack worn = withPouches(pack(BackpackItem.Size.PATROL, -1));
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(), wearing(worn), you);
        s.idle(10);
        s.text(110, you.add(0, 2.1, 0), Side.LEFT,
                "Wear it in the *Back* slot of your inventory, next to the sling slots. A rifle on your back sling hangs over it.");
        s.idle(115);

        s.chapter("Unzip it");
        s.sound(ModSounds.BACKPACK_ZIP.get(), 0.5f, 1.0f);
        s.meter(30, you.add(0, 2.4, 0), "Unzipping", 0.0f, 1.0f, ORANGE);
        s.idle(32);
        s.text(140, you.add(0, 2.1, 0), Side.LEFT,
                "[B] (or the Alt wheel) unzips it: *1.5 s*, and you must stand still. While it's open you walk at 40 %."
                        + " The *SORT* button lines everything up. In your hand: right-click.");
        s.idle(145);

        s.chapter("Put it down");
        BlockPos down = new BlockPos(3, 1, 3);
        s.click(HowToSceneBuilder.CLICK, List.of(Vec3.atBottomCenterOf(down)), com.senne.imttech.howto.HowToCue.Button.RIGHT, false, true,
                pack(BackpackItem.Size.ASSAULT, -1));
        s.idle(12);
        put(s, down, pack(BackpackItem.Size.ASSAULT, -1));
        s.sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.5f, 0.8f);
        s.idle(8);
        s.text(150, Vec3.atCenterOf(down).add(0, 0.6, 0), Side.LEFT,
                "*Sneak + right-click* on the ground puts it down. Then *anyone* can open it (right-click, with the zip), and two"
                        + " players see the same things. Sneak + right-click with an empty hand picks it up again.");
        s.idle(155);

        s.chapter("Colours");
        s.blockData(left, packData(pack(BackpackItem.Size.ASSAULT, 0xB02E26)));
        s.idle(6);
        s.blockData(middle, packData(pack(BackpackItem.Size.PATROL, 0x3C44AA)));
        s.idle(6);
        s.blockData(right, packData(pack(BackpackItem.Size.RUCKSACK, -2)));
        s.idle(8);
        s.text(140, Vec3.atCenterOf(right).add(0, 0.9, 0), Side.RIGHT,
                "Dye it in a crafting table, like leather armour (mixing works too). *Brown + lime + green* dye makes *camo*: its"
                        + " own pattern, not a mixed colour.");
        s.idle(145);

        s.chapter("From behind");
        s.remove(down);
        Vec3 them = new Vec3(5.2, 1.0, 3.2);
        Vec3 sneak = new Vec3(5.2, 1.0, 4.5);
        CompoundTag raven = new CompoundTag();
        raven.putString("Profile", "Raven");
        s.person("them", raven, HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(),
                wearing(withPouches(pack(BackpackItem.Size.PATROL, 0x6B5A3A))), them);
        CompoundTag crouch = new CompoundTag();
        crouch.putBoolean("Crouch", true);
        s.person("you", crouch, HowToSceneBuilder.UNTIL_END, 20, 180.0f, Map.of(), wearing(worn), you, sneak);
        s.idle(24);
        s.rightClick(them.add(0, 1.3, 0.25), ItemStack.EMPTY);
        s.sound(ModSounds.BACKPACK_ZIP.get(), 0.25f, 1.0f);
        s.idle(10);
        s.meter(70, them.add(0, 2.3, 0), "Noticed", 0.0f, 0.7f, RED);
        s.text(150, them.add(0, 2.1, 0), Side.RIGHT,
                "Sneak up *behind* someone with an empty hand and right-click their *backpack* (lower is their pocket). Every grab"
                        + " fills the *NOTICED* meter. Full, or they turn around and see you: caught, and you glow for a while.");
        s.idle(155);

        s.chapter("Bodies");
        Vec3 body = new Vec3(2.2, 1.0, 3.6);
        CompoundTag fallen = new CompoundTag();
        fallen.putUUID("Owner", UUID.nameUUIDFromBytes("OfflinePlayer:Raven".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        fallen.putString("OwnerName", "Raven");
        fallen.put("Pack", ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, withPouches(pack(BackpackItem.Size.ASSAULT, -1))).getOrThrow());
        s.actor("body", BackpackRegistry.PLAYER_BODY.get(), HowToSceneBuilder.UNTIL_END, 0, 30.0f, fallen, body);
        s.sound(SoundEvents.PLAYER_DEATH, 0.4f, 1.0f);
        s.idle(10);
        s.text(170, body.add(0, 1.2, 0), Side.LEFT,
                "Die without keepInventory and you leave a *body*: everything in its own slot, your backpack on top. Anyone can"
                        + " *search* it (right-click), and sneak + right-click on the backpack takes it off (3 s). It stays until"
                        + " it's empty. Find your own and press *Everything back*.");
        s.idle(175);
        return List.of(s.build());
    }

    // ================================================================ MOLLE pouches

    /** De MOLLE-pouches: een Rucksack met vier pouches, en elke pouch apart. */
    public static List<HowToScene> molle() {
        HowToSceneBuilder s = new HowToSceneBuilder("molle_pouches", "MOLLE pouches").plate(7, 5).height(3);
        BlockPos rucksack = new BlockPos(3, 1, 1);
        Vec3 you = new Vec3(3.5, 1.0, 3.6);
        s.idle(6);
        put(s, rucksack, withPouches(pack(BackpackItem.Size.RUCKSACK, -1)));
        s.sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.5f, 0.9f);
        s.idle(10);

        s.chapter("Hang them on");
        s.text(150, Vec3.atCenterOf(rucksack).add(0, 1.0, 0), Side.RIGHT,
                "*MOLLE pouches* hang on the webbing of a backpack: 2 on an Assault Pack, 3 on a Patrol Pack, 4 on a Rucksack. Put"
                        + " them in the *MOLLE* slots on the right of its screen; you see them hanging on it.");
        s.idle(155);

        s.chapter("No zip");
        ItemStack worn = withPouches(pack(BackpackItem.Size.RUCKSACK, -1));
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(), wearing(worn), you);
        s.idle(10);
        s.text(120, you.add(0, 2.1, 0), Side.LEFT,
                "A pouch on your back opens *without the zip*: the *Alt wheel* has a little button for each one.");
        s.idle(125);

        Vec3 show = new Vec3(1.2, 2.4, 3.0);
        s.chapter("Mags and grenades");
        s.item(130, show, new ItemStack(BackpackRegistry.MOLLE_MAG_POUCH.get()), 0.6f);
        s.item(130, show.add(1.0, 0, 0), new ItemStack(BackpackRegistry.MOLLE_GRENADE_POUCH.get()), 0.6f);
        s.text(125, show.add(0.5, 0.6, 0), Side.RIGHT,
                "The *MOLLE Mag Pouch* (3 magazines or cells) and the *MOLLE Grenade Pouch* (2): R and the wheel take from your hip"
                        + " pouch first, then from these. An ejected magazine goes into one when your hip pouch is full.");
        s.idle(135);

        s.chapter("Dump pouch");
        s.item(110, show, new ItemStack(BackpackRegistry.DUMP_POUCH.get()), 0.6f);
        for (int i = 0; i < 4; i++) {
            s.item(30, you.add(-0.6 + i * 0.4, 0.05, -0.5), new ItemStack(ModItems.CASING_9MM.get()), 0.25f);
        }
        s.text(105, show.add(0, 0.6, 0), Side.RIGHT,
                "The *Dump Pouch* picks up the casings around your feet by itself (for the Reloading Bench).");
        s.idle(32);
        s.sound(ModSounds.CASING_TINK.get(), 0.4f, 1.3f);
        s.idle(85);

        s.chapter("IFAK");
        s.item(120, show, new ItemStack(BackpackRegistry.IFAK.get()), 0.6f);
        s.item(120, show.add(1.0, 0, 0), new ItemStack(Items.GOLDEN_APPLE), 0.5f);
        s.text(115, show.add(0, 0.6, 0), Side.RIGHT,
                "The *IFAK* holds 3 golden apples or potions. In the wheel each one is a button: point at it and let go of Alt,"
                        + " and you use it at once.");
        s.idle(125);

        s.chapter("Utility pouch");
        s.item(110, show, new ItemStack(BackpackRegistry.UTILITY_POUCH.get()), 0.6f);
        s.item(110, show.add(1.0, 0, 0), new ItemStack(Items.SHEARS), 0.5f);
        s.text(105, show.add(0, 0.6, 0), Side.RIGHT,
                "The *Utility Pouch*: 4 quick slots for anything, except boxes and full pouches.");
        s.idle(115);
        return List.of(s.build());
    }
}
