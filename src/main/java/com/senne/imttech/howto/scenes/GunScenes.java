package com.senne.imttech.howto.scenes;

import static com.senne.imttech.howto.scenes.WeaponScenes.BULLET;
import static com.senne.imttech.howto.scenes.WeaponScenes.ahead;
import static com.senne.imttech.howto.scenes.WeaponScenes.bench;
import static com.senne.imttech.howto.scenes.WeaponScenes.husk;
import static com.senne.imttech.howto.scenes.WeaponScenes.muzzle;
import static com.senne.imttech.howto.scenes.WeaponScenes.right;
import static com.senne.imttech.howto.scenes.WeaponScenes.row;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.Attachment;
import com.senne.imttech.guns.Caliber;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * De animaties van de grote update (hoofdstuk 103, stap 9): de MP5 en de AK-47, de AWP, de Remington 870 en de
 * attachments. Jouw wens: "meer guns, deze werken net zoals de Glock"; wat ze met de Glock delen (het magazijn kogel per
 * kogel vullen, storingen, slijtage, de skills) staat bij "Glock 19", dus hier staat wat anders is. Hoe het echt werkt
 * staat in LEESMIJ ("De grote update 2.0").
 *
 * Zoals bij de andere wapens (zie WeaponScenes): wie schiet staat onderaan in het midden van het beeld, het doelwit
 * bovenaan in het midden, en de tekstballonnen aan de zijkanten. Je ziet de schutter dan van achteren, dus het wapen
 * zelf zweeft eerst groot in beeld (het draait rond). Wat ik op de foto's leerde: een lange tekst bovenaan hangt voor
 * een rij voorwerpen, dus een rij staat links en zijn tekst rechts ernaast ({@link #rowWithText}); een tekst en een
 * plaatje komen na elkaar, nooit samen.
 */
public final class GunScenes {
    private GunScenes() {}

    /** Van links naar rechts op het scherm, in de wereld (de camera staat op 40: zie WeaponScenes.row). */
    private static final Vec3 ACROSS = new Vec3(0.766, 0, 0.643);
    /** Naar de camera toe, in de wereld: op het scherm is dat omlaag. */
    private static final Vec3 NEARER = new Vec3(-0.643, 0, 0.766);

    // ================================================================ Hulpjes

    static ItemStack loaded(FirearmType type) {
        return FirearmItem.loaded(type, GunMaterial.STEEL);
    }

    static ItemStack with(ItemStack gun, Attachment... attachments) {
        ItemStack out = gun.copy();
        for (Attachment attachment : attachments) {
            FirearmItem.setAttachment(out, attachment);
        }
        return out;
    }

    static ItemStack attachment(Attachment attachment) {
        return new ItemStack(ModItems.attachment(attachment));
    }

    /** De zes onderdelen van dit wapen, van staal. */
    static List<ItemStack> parts(FirearmType type) {
        List<ItemStack> parts = new ArrayList<>();
        for (GunPart part : GunPart.values()) {
            parts.add(GunPartItem.of(type, part, GunMaterial.STEEL));
        }
        return parts;
    }

    /** Een huls van dit kaliber (zo bewaart het spel hem: zie CasingEntity). */
    static CompoundTag casing(Caliber caliber) {
        CompoundTag tag = new CompoundTag();
        tag.putByte("caliber", (byte) caliber.ordinal());
        return tag;
    }

    /**
     * Een rij voorwerpen links in beeld met zijn tekst rechts ernaast. {@code centre} is het midden van de vloer (op de
     * hoogte van de rij); de rij schuift zo ver naar links dat hij vrij blijft van de tekstballon aan de rechterrand.
     * De klok loopt alleen het verschijnen van de rij door (drie ticks per voorwerp), zoals bij {@code row}.
     */
    static void rowWithText(HowToSceneBuilder s, int duration, Vec3 centre, float spacing, float scale, List<ItemStack> stacks,
                            String english, Object... args) {
        double half = (stacks.size() - 1) / 2.0 * spacing + scale * 0.35;
        Vec3 middle = centre.add(ACROSS.scale(0.9 - half));
        row(s, duration, middle, spacing, scale, stacks);
        s.text(duration - 3 * stacks.size(), middle.add(ACROSS.scale(half + 0.2)), Side.RIGHT, english, args);
    }

    /**
     * Schoten met echte kogels van {@code you} naar {@code to}, om de {@code interval} ticks, elk met een huls van
     * {@code caliber} die rechts uit het wapen springt en {@code stay} ticks blijft liggen. De klok staat daarna erachter.
     */
    static void shoot(HowToSceneBuilder s, String key, Vec3 you, float aim, Vec3 to, int shots, int interval, SoundEvent sound,
                      Caliber caliber, int stay) {
        Vec3 gun = muzzle(you, aim);
        Vec3 port = gun.add(ahead(aim).scale(-0.35)).add(0, 0.05, 0);
        for (int i = 0; i < shots; i++) {
            s.beam(Math.min(4, Math.max(2, interval - 1)), gun, to, BULLET);
            s.sound(sound, 0.35f, 1.0f);
            Vec3 land = you.add(right(aim).scale(1.1 + (i % 4) * 0.2)).add(ahead(aim).scale(0.3 - (i % 3) * 0.25));
            s.actor(key + i, ModEntities.CASING.get(), stay, 10, aim + 90.0f, casing(caliber),
                    port, port.add(right(aim).scale(0.6)).add(0, 0.5, 0), land);
            s.idle(interval);
        }
    }

    /** Eén huls die pas uit het wapen springt als je doorlaadt (een grendel of een pomp). */
    static void eject(HowToSceneBuilder s, String key, Vec3 you, float aim, Caliber caliber, int stay) {
        Vec3 port = muzzle(you, aim).add(ahead(aim).scale(-0.45)).add(0, 0.05, 0);
        Vec3 land = you.add(right(aim).scale(1.2)).add(ahead(aim).scale(0.1));
        s.actor(key, ModEntities.CASING.get(), stay, 10, aim + 90.0f, casing(caliber),
                port, port.add(right(aim).scale(0.6)).add(0, 0.5, 0), land);
    }

    // ================================================================ De MP5 en de AK-47

    /**
     * De MP5 en de AK-47: het wapen groot in beeld, een salvo, de keuzeschakelaar (de HUD als plaatje, en de drie standen
     * na elkaar), de AK-47, de spanhendel na een nieuw magazijn, de sling (een lang wapen past niet in de holster) en de
     * zes onderdelen.
     */
    public static List<HowToScene> automatics() {
        HowToSceneBuilder s = new HowToSceneBuilder("mp5_ak47", "MP5 and AK-47").plate(7, 6).height(3);
        Vec3 centre = new Vec3(3.5, 1.0, 3.0);
        Vec3 you = new Vec3(2.0, 1.0, 4.8);
        Vec3 target = new Vec3(5.0, 1.0, 1.2);
        Vec3 chest = target.add(0, 1.3, 0);
        float aim = RobotScenes.yawTo(you, target);
        Vec3 gun = muzzle(you, aim);
        ItemStack mp5 = loaded(FirearmType.MP5);
        ItemStack ak = loaded(FirearmType.AK_47);
        s.idle(6);

        s.chapter("The MP5");
        rowWithText(s, 100, centre.add(0, 1.4, 0), 0.0f, 5.0f, List.of(mp5),
                "The *MP5* is a submachine gun. It fires the same *9mm Round* as the Glock, from its own magazine of %s.",
                HowToSceneBuilder.number(ImtConfig.Guns.MAGAZINE_CAPACITY[1]));
        s.idle(100);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(EquipmentSlot.MAINHAND, mp5), you);
        husk(s, "husk", 150, 70, target.add(1.2, 0, -0.9), target);
        s.text(110, target.add(0, 2.2, 0), Side.RIGHT,
                "A bullet does a little less damage (%s) than the Glock's, but the MP5 fires much faster and straighter.",
                HowToSceneBuilder.number(ImtConfig.Guns.DAMAGE[1]));
        s.idle(70);
        shoot(s, "a", you, aim, chest, 6, 2, ModSounds.MP5_FIRE.get(), Caliber.MM9, 180);
        s.sound(SoundEvents.HUSK_DEATH, 0.4f, 1.0f);
        s.idle(40);
        s.text(110, gun.add(0, 0.6, 0), Side.LEFT,
                "The rest works like the *Glock 19*: fill the magazine round by round with *[R]*, right-click pushes it in,"
                        + " *[R]* swaps it, and its parts wear and jam.");
        s.idle(115);

        s.chapter("The fire selector");
        s.text(120, gun.add(0, 0.6, 0), Side.LEFT,
                "*[X]* turns the *fire selector*: *SAFE* (the trigger does nothing), *SEMI* (one shot per click), *BURST* (%s"
                        + " shots per click, the MP5 only) and *AUTO* (hold the button).",
                HowToSceneBuilder.number(ImtConfig.Guns.BURST_SHOTS));
        s.idle(125);
        s.picture(130, "mp5_hud", 426, 240,
                s.arrow(0.885f, 0.805f, "The selector: *BURST*"),
                s.arrow(0.86f, 0.875f, "Rounds in the magazine, *+1* in the chamber"));
        s.idle(135);
        husk(s, "husk2", 172, 60, target.add(1.2, 0, -0.9), target);
        s.idle(62);
        // Elk label staat anderhalve seconde of langer in beeld, en het volgende komt pas als het vorige weg is.
        s.sound(ModSounds.GUN_SELECTOR.get(), 0.6f, 1.0f);
        s.text(32, gun.add(0, 0.6, 0), Side.LEFT, "*SEMI*: one shot per click");
        s.idle(10);
        shoot(s, "b", you, aim, chest, 1, 24, ModSounds.MP5_FIRE.get(), Caliber.MM9, 120);
        s.sound(ModSounds.GUN_SELECTOR.get(), 0.6f, 1.0f);
        s.text(34, gun.add(0, 0.6, 0), Side.LEFT, "*BURST*: %s shots per click", HowToSceneBuilder.number(ImtConfig.Guns.BURST_SHOTS));
        s.idle(10);
        shoot(s, "c", you, aim, chest, Math.round(ImtConfig.Guns.BURST_SHOTS), 2, ModSounds.MP5_FIRE.get(), Caliber.MM9, 110);
        s.idle(20);
        s.sound(ModSounds.GUN_SELECTOR.get(), 0.6f, 1.0f);
        s.text(36, gun.add(0, 0.6, 0), Side.LEFT, "*AUTO*: hold the button");
        s.idle(10);
        shoot(s, "d", you, aim, chest, 9, 2, ModSounds.MP5_FIRE.get(), Caliber.MM9, 90);
        s.sound(SoundEvents.HUSK_DEATH, 0.4f, 1.0f);
        s.idle(40);

        s.chapter("The AK-47");
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(EquipmentSlot.MAINHAND, ak), you);
        // Je staat er nu bij (onderaan in het midden): het wapen zweeft boven je hoofd.
        rowWithText(s, 100, centre.add(0, 1.9, 0), 0.0f, 5.0f, List.of(ak),
                "The *AK-47* is an assault rifle with its own *7.62x39mm Round*, %s in a magazine.",
                HowToSceneBuilder.number(ImtConfig.Guns.MAGAZINE_CAPACITY[2]));
        s.idle(100);
        husk(s, "husk3", 150, 70, target.add(1.2, 0, -0.9), target);
        s.text(125, target.add(0, 2.2, 0), Side.RIGHT,
                "A bullet does %s damage and flies further, but the rifle kicks harder and guards hear it from further away."
                        + " Its selector: *SAFE*, *SEMI* and *AUTO*.",
                HowToSceneBuilder.number(ImtConfig.Guns.DAMAGE[2]));
        s.idle(80);
        shoot(s, "e", you, aim, chest, 5, 3, ModSounds.AK47_FIRE.get(), Caliber.R762, 150);
        s.sound(SoundEvents.HUSK_DEATH, 0.4f, 1.0f);
        s.idle(50);

        s.chapter("A new magazine");
        s.item(110, you.add(1.0, 1.8, -0.8), MagazineItem.of(FirearmType.AK_47, 30), 1.2f);
        s.text(110, you.add(1.0, 2.0, -0.8), Side.RIGHT,
                "After a new magazine on an empty chamber you pull the *charging handle*: on the MP5 it sits on the left, on"
                        + " the AK-47 on the right. Then the first round is in the chamber.");
        s.idle(50);
        s.sound(ModSounds.AK47_CHARGE.get(), 0.6f, 1.0f);
        s.idle(65);

        s.chapter("On a sling");
        List<ItemStack> worn = List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ak, ItemStack.EMPTY);
        s.item(50, new Vec3(1.0, 1.9, 5.2), new ItemStack(ModItems.GUN_SLING.get()), 1.0f);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 220.0f, Map.of(), worn, you);
        s.text(130, you.add(0, 2.1, 0), Side.RIGHT,
                "A long gun doesn't fit the holster. Drag a *Gun Sling* onto it at the Gunsmith Table: then it hangs on your"
                        + " back or in front of your chest, and you draw it with the wheel (hold *[left Alt]*).");
        s.idle(65);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 130.0f, Map.of(), worn, you);
        s.idle(70);

        s.chapter("Six parts");
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(), you);
        rowWithText(s, 160, centre.add(0, 1.5, 0), 0.75f, 1.1f, parts(FirearmType.MP5),
                "Six parts, like the Glock's, with their own names: *Receiver*, *Bolt Carrier*, *Barrel*, *Recoil Spring*,"
                        + " *Firing Pin* and *Trigger Group*, in *steel* or *titanium*. You make them at the Gunsmith Table (tab"
                        + " *Make parts*) under *MP5* or *AK-47*.");
        s.idle(145);
        return List.of(s.build());
    }

    // ================================================================ De AWP

    /**
     * De AWP: het geweer groot in beeld, één zwaar schot van ver, de grendel na elk schot (de HUD met PRESS R als
     * plaatje), richten door de Sniper Scope (als plaatje), de tweepoot (sluipen en stilstaan, van opzij gezien) en zijn
     * onderdelen en magazijn.
     */
    public static List<HowToScene> awp() {
        HowToSceneBuilder s = new HowToSceneBuilder("awp", "AWP").plate(7, 6).height(3);
        Vec3 centre = new Vec3(3.5, 1.0, 3.0);
        Vec3 you = new Vec3(2.0, 1.0, 4.8);
        Vec3 target = new Vec3(5.4, 1.0, 0.9);
        Vec3 chest = target.add(0, 1.3, 0);
        float aim = RobotScenes.yawTo(you, target);
        Vec3 gun = muzzle(you, aim);
        ItemStack awp = loaded(FirearmType.AWP);
        s.idle(6);

        s.chapter("A sniper rifle");
        rowWithText(s, 100, centre.add(0, 1.4, 0), 0.0f, 5.5f, List.of(awp),
                "The *AWP* is a bolt-action sniper rifle. It fires *.338 Lapua Magnum* from a magazine of %s.",
                HowToSceneBuilder.number(ImtConfig.Guns.MAGAZINE_CAPACITY[3]));
        s.idle(100);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(EquipmentSlot.MAINHAND, awp), you);
        husk(s, "husk", 120, 0, target);
        s.text(110, target.add(0, 2.2, 0), Side.RIGHT,
                "One bullet does %s damage, up to %s blocks away: enough for almost anything without armor.",
                HowToSceneBuilder.number(ImtConfig.Guns.DAMAGE[3]), HowToSceneBuilder.number(ImtConfig.Guns.FULL_RANGE[3]));
        s.idle(85);
        s.beam(5, gun, chest, BULLET);
        s.beam(5, gun.add(0, 0.04, 0), chest.add(0, 0.04, 0), 0xFFF6D8);
        s.sound(ModSounds.AWP_FIRE.get(), 0.4f, 1.0f);
        s.idle(6);
        s.sound(SoundEvents.HUSK_DEATH, 0.4f, 1.0f);
        s.idle(40);

        s.chapter("Press R after every shot");
        s.text(110, gun.add(0, 0.6, 0), Side.LEFT,
                "After a shot the empty casing stays in the chamber: *PRESS R* shows in red, and the trigger does nothing"
                        + " until you work the bolt.");
        s.idle(115);
        s.picture(120, "awp_hud", 426, 240,
                s.arrow(0.93f, 0.765f, "*PRESS R*: work the bolt"),
                s.arrow(0.905f, 0.875f, "Rounds left in the magazine"));
        s.idle(125);
        s.text(125, gun.add(0, 0.6, 0), Side.LEFT,
                "*[R]* works the bolt: handle up, back (now the casing flies out), forward (the next round goes in) and down."
                        + " That takes over a second. With an empty magazine it only throws the casing out.");
        s.idle(30);
        s.sound(ModSounds.AWP_BOLT.get(), 0.6f, 1.0f);
        s.idle(8);
        eject(s, "bolt", you, aim, Caliber.L338, 200);
        s.idle(90);

        s.chapter("Through the scope");
        s.item(115, centre.add(0, 1.8, 0).add(ACROSS.scale(1.4)), with(awp, Attachment.SNIPER_SCOPE), 4.5f);
        s.text(115, gun.add(0, 0.6, 0), Side.LEFT,
                "Without a scope you aim over iron sights. With the *Sniper Scope* (an attachment) you look through its ring"
                        + " and the whole picture zooms in, from 1x to %sx: *scroll while aiming*.",
                HowToSceneBuilder.number(ImtConfig.Guns.SCOPE_MAX_ZOOM));
        s.idle(120);
        s.picture(140, "scope_view", 426, 240,
                s.arrow(0.5f, 0.5f, "The crosshair: that's where the bullet goes"),
                s.arrow(0.92f, 0.715f, "How far you're zoomed in"),
                s.arrow(0.12f, 0.4f, "No black screen: you still see around the ring"));
        s.idle(145);

        // De tweepoot zie je alleen van opzij: je staat in het midden, dwars in beeld, en kijkt naar rechts.
        s.chapter("The bipod");
        ItemStack onLegs = with(awp, Attachment.SNIPER_SCOPE, Attachment.BIPOD);
        Vec3 prone = centre.add(ACROSS.scale(-1.0));
        float sideways = RobotScenes.yawTo(prone, prone.add(ACROSS));
        CompoundTag sneaking = new CompoundTag();
        sneaking.putBoolean("Crouch", true);
        s.person("you", new CompoundTag(), 60, 0, sideways, Map.of(EquipmentSlot.MAINHAND, onLegs), List.of(), prone);
        s.text(55, null, Side.RIGHT, "A *Bipod* is an attachment for the AWP: its legs lie folded under the barrel.");
        s.idle(60);
        s.person("you", sneaking, HowToSceneBuilder.UNTIL_END, 0, sideways, Map.of(EquipmentSlot.MAINHAND, onLegs), List.of(), prone);
        s.text(120, null, Side.RIGHT,
                "*Sneak and stand still*: the legs fold down, *BIPOD DOWN* shows, and the rifle hardly kicks. Move or stand"
                        + " up and they fold back.");
        s.idle(70);
        Vec3 sideGun = muzzle(prone, sideways).add(0, -0.3, 0);
        s.beam(5, sideGun, sideGun.add(ACROSS.scale(3.2)), BULLET);
        s.sound(ModSounds.AWP_FIRE.get(), 0.4f, 1.0f);
        s.idle(55);

        s.chapter("Parts and magazines");
        s.person("you", new CompoundTag(), HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(), List.of(), you);
        rowWithText(s, 160, centre.add(0, 1.5, 0), 0.75f, 1.1f, parts(FirearmType.AWP),
                "Its six parts: *Receiver*, *Bolt*, *Barrel*, *Mainspring*, *Firing Pin* and *Trigger Group*, in steel or"
                        + " titanium (under *AWP* on the Gunsmith Table). The *AWP Magazine* holds %s rounds, the *AWP Extended"
                        + " Magazine* %s.",
                HowToSceneBuilder.number(ImtConfig.Guns.MAGAZINE_CAPACITY[3]), HowToSceneBuilder.number(ImtConfig.Guns.EXTENDED_CAPACITY[3]));
        s.idle(145);
        return List.of(s.build());
    }

    // ================================================================ De Remington 870

    /**
     * De Remington 870: het geweer groot in beeld, hagel (acht korrels die uitwaaieren), pompen na elk schot, de buis die
     * je patroon per patroon laadt, de verlengbuis en zijn onderdelen.
     */
    public static List<HowToScene> remington() {
        HowToSceneBuilder s = new HowToSceneBuilder("remington_870", "Remington 870").plate(7, 6).height(3);
        Vec3 centre = new Vec3(3.5, 1.0, 3.0);
        Vec3 you = new Vec3(2.0, 1.0, 4.8);
        Vec3 close = new Vec3(3.9, 1.0, 2.5);
        float aim = RobotScenes.yawTo(you, close);
        Vec3 gun = muzzle(you, aim);
        ItemStack shotgun = loaded(FirearmType.REMINGTON_870);
        int pellets = Math.round(ImtConfig.Guns.PELLETS[4]);
        s.idle(6);

        s.chapter("A pump shotgun");
        rowWithText(s, 100, centre.add(0, 1.4, 0), 0.0f, 5.0f, List.of(shotgun),
                "The *Remington 870* is a pump shotgun. It fires *12 Gauge Shells*: every shot is %s pellets that fan out.",
                HowToSceneBuilder.number(pellets));
        s.idle(100);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(EquipmentSlot.MAINHAND, shotgun), you);
        husk(s, "husk", 130, 60, new Vec3(5.2, 1.0, 1.0), close);
        s.text(120, close.add(0, 2.3, 0), Side.RIGHT,
                "Up close they all hit (%s damage together); beyond %s blocks it drops fast, and the pellets fly %s blocks at"
                        + " most.",
                HowToSceneBuilder.number(pellets * ImtConfig.Guns.DAMAGE[4]), HowToSceneBuilder.number(ImtConfig.Guns.FULL_RANGE[4]),
                HowToSceneBuilder.number(ImtConfig.Guns.RANGE[4]));
        s.idle(95);
        fan(s, gun, close.add(0, 1.2, 0), aim, pellets);
        s.sound(ModSounds.SHOTGUN_FIRE.get(), 0.4f, 1.0f);
        s.idle(8);
        s.sound(SoundEvents.HUSK_DEATH, 0.4f, 1.0f);
        s.idle(40);

        s.chapter("Pump after every shot");
        s.text(130, gun.add(0, 0.6, 0), Side.LEFT,
                "It's a pump: after every shot *PRESS R* shows and the trigger does nothing. *[R]* pulls the forend back (the"
                        + " empty shell flies out) and pushes it forward (the next shell goes into the chamber).");
        s.idle(60);
        s.sound(ModSounds.SHOTGUN_PUMP.get(), 0.6f, 1.0f);
        s.idle(5);
        eject(s, "pump", you, aim, Caliber.G12, 260);
        s.idle(70);

        s.chapter("A tube, not a magazine");
        List<ItemStack> shells = new ArrayList<>();
        int tube = Math.round(ImtConfig.Guns.MAGAZINE_CAPACITY[4]);
        for (int i = 0; i < tube; i++) {
            shells.add(new ItemStack(ModItems.ROUND_12G.get()));
        }
        rowWithText(s, 170, centre.add(0, 1.5, 0), 0.4f, 0.8f, shells,
                "There is no magazine: %s shells sit in the *tube* under the barrel, plus one in the chamber. *Hold [R]*: you"
                        + " push in shell after shell until the tube is full. Top up whenever you like; *[U]* takes one out.",
                HowToSceneBuilder.number(tube));
        s.idle(30);
        for (int i = 0; i < 4; i++) {
            s.sound(ModSounds.SHOTGUN_SHELL.get(), 0.5f, 1.0f + i * 0.04f);
            s.idle(Math.round(ImtConfig.Guns.LOAD_SHELL_TICKS));
        }
        s.idle(95);

        s.chapter("Two more shells");
        s.item(110, you.add(1.0, 1.8, -0.8), attachment(Attachment.TUBE_EXTENSION), 1.3f);
        s.text(105, you.add(1.0, 2.1, -0.8), Side.RIGHT,
                "A *Tube Extension* (an attachment from the Gunsmith Table) makes the tube longer: %s shells instead of %s.",
                HowToSceneBuilder.number(ImtConfig.Guns.EXTENDED_CAPACITY[4]), HowToSceneBuilder.number(tube));
        s.idle(110);

        s.chapter("Six parts");
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(), you);
        rowWithText(s, 160, centre.add(0, 1.5, 0), 0.75f, 1.1f, parts(FirearmType.REMINGTON_870),
                "Its six parts: *Receiver*, *Forend* (the part you pump), *Barrel*, *Magazine Spring*, *Firing Pin* and"
                        + " *Trigger Group*, in steel or titanium (under *Remington 870* on the Gunsmith Table). You make no"
                        + " magazine for it.");
        s.idle(145);
        return List.of(s.build());
    }

    /** Een schot hagel: {@code pellets} sporen die uitwaaieren rond de lijn van {@code from} naar {@code to}. */
    private static void fan(HowToSceneBuilder s, Vec3 from, Vec3 to, float aim, int pellets) {
        Vec3 dir = to.subtract(from);
        Vec3 side = right(aim);
        for (int i = 0; i < pellets; i++) {
            double spread = (i - (pellets - 1) / 2.0) * 0.07;
            double lift = ((i * 37) % 5 - 2) * 0.06;
            s.beam(4, from, from.add(dir.scale(1.2)).add(side.scale(spread * dir.length())).add(0, lift, 0), BULLET);
        }
    }

    // ================================================================ Attachments

    /**
     * De attachments, de eerste animatie: wat het zijn, erop en eraf op de Gunsmith Table (als plaatje), en de kijkers
     * (het beeld door de Red Dot Sight en de Sniper Scope als plaatje). De rest staat in {@link #attachmentsMore}: samen
     * waren ze te lang voor één animatie (hoogstens anderhalve minuut).
     */
    public static List<HowToScene> attachments() {
        HowToSceneBuilder s = new HowToSceneBuilder("attachments", "Attachments").plate(5, 5).height(3);
        BlockPos table = new BlockPos(2, 1, 1);
        Vec3 centre = new Vec3(2.5, 1.0, 2.5);
        s.idle(6);

        s.chapter("What they are");
        rowWithText(s, 150, centre.add(0, 1.1, 0), 0.7f, 1.0f, List.of(attachment(Attachment.RED_DOT), attachment(Attachment.SNIPER_SCOPE),
                        attachment(Attachment.SUPPRESSOR_9MM), attachment(Attachment.SUPPRESSOR_RIFLE),
                        attachment(Attachment.SUPPRESSOR_SHOTGUN)),
                "An *attachment* goes on a firearm and never wears out: sights, suppressors, grips, a bipod and a tube"
                        + " extension.");
        Vec3 second = centre.add(0, 0.3, 0).add(ACROSS.scale(0.9 - (1.5 * 0.7 + 0.35)));
        row(s, 135, second, 0.7f, 1.0f, List.of(attachment(Attachment.VERTICAL_GRIP), attachment(Attachment.ANGLED_GRIP),
                attachment(Attachment.BIPOD), attachment(Attachment.TUBE_EXTENSION)));
        s.idle(125);

        s.chapter("On and off at the table");
        s.place(table, bench(ModBlocks.GUNSMITH_TABLE.get(), Direction.SOUTH));
        s.idle(10);
        s.text(100, HowToSceneBuilder.top(table), Side.RIGHT,
                "You make them at the *Gunsmith Table* (tab *Make parts*, under *Attachments*), and they go on and off only"
                        + " there, on a gun that is complete. A gun has up to four places; each takes one.");
        s.idle(105);
        s.rightClick(HowToSceneBuilder.face(table, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(14);
        s.picture(160, "gunsmith_attachments", 368, 214,
                s.arrow(0.12f, 0.3f, "Your attachments, and where each one goes"),
                s.arrow(0.58f, 0.49f, "Drag one onto its place: it clicks on"),
                s.arrow(0.29f, 0.5f, "A free place is dotted"),
                s.arrow(0.82f, 0.61f, "What's on the gun; *x* takes it off"));
        s.idle(165);

        s.chapter("Sights");
        s.remove(table);
        s.item(235, centre.add(0, 0.9, 0).add(ACROSS.scale(-1.0)), attachment(Attachment.RED_DOT), 1.6f);
        s.text(110, centre.add(0, 1.3, 0), Side.RIGHT,
                "The *Red Dot Sight* fits every firearm. Aiming, its red dot sits in the middle of your view and you still"
                        + " see your gun. You aim %sx faster and an aimed shot strays %sx as far.",
                HowToSceneBuilder.number(ImtConfig.Guns.RED_DOT_AIM_SPEED), HowToSceneBuilder.number(ImtConfig.Guns.RED_DOT_AIM_SPREAD));
        s.idle(115);
        s.picture(120, "red_dot_view", 426, 240,
                s.arrow(0.5f, 0.5f, "The dot: that's where the bullet goes"));
        s.idle(125);
        s.item(245, centre.add(0, 0.9, 0).add(ACROSS.scale(-1.0)), attachment(Attachment.SNIPER_SCOPE), 1.8f);
        s.text(120, centre.add(0, 1.3, 0), Side.RIGHT,
                "The *Sniper Scope* fits the MP5, the AK-47 and the AWP. It zooms from 1x to %sx: *scroll while aiming*. An"
                        + " aimed shot strays %sx as far, but you bring it up slower.",
                HowToSceneBuilder.number(ImtConfig.Guns.SCOPE_MAX_ZOOM), HowToSceneBuilder.number(ImtConfig.Guns.SCOPE_AIM_SPREAD));
        s.idle(125);
        s.picture(120, "scope_view", 426, 240,
                s.arrow(0.5f, 0.5f, "You look through the ring, not at a black screen"),
                s.arrow(0.92f, 0.715f, "How far you're zoomed in"));
        s.idle(125);

        return List.of(s.build());
    }

    /**
     * De attachments, de tweede animatie: de dempers (een luid en een gedempt schot, van opzij), de grips, de tweepoot en
     * de verlengbuis, de lange magazijnen, de lasergeweren, en waar je ze vindt.
     */
    public static List<HowToScene> attachmentsMore() {
        HowToSceneBuilder s = new HowToSceneBuilder("attachments_more", "Suppressors, grips and magazines").plate(5, 5).height(3);
        Vec3 centre = new Vec3(2.5, 1.0, 2.5);
        s.idle(6);

        // De dempers: je staat links, dwars in beeld (je ziet het wapen van opzij), en schiet naar rechts.
        s.chapter("Suppressors");
        Vec3 you = centre.add(ACROSS.scale(-1.6)).add(NEARER.scale(1.5));
        float aim = RobotScenes.yawTo(you, you.add(ACROSS));
        Vec3 gun = muzzle(you, aim);
        s.player("you", 105, 0, aim, Map.of(EquipmentSlot.MAINHAND, loaded(FirearmType.MP5)), you);
        s.text(100, null, Side.RIGHT,
                "Without a suppressor a shot is loud: guards hear it from far away, and it flashes.");
        s.idle(55);
        s.beam(3, gun, gun.add(ACROSS.scale(2.6)), BULLET);
        s.sound(ModSounds.MP5_FIRE.get(), 0.4f, 1.0f);
        s.pulse(16, gun, 2.4f, HowToSceneBuilder.RED);
        s.idle(50);
        ItemStack quiet = with(loaded(FirearmType.MP5), Attachment.SUPPRESSOR_9MM);
        s.player("you", 265, 0, aim, Map.of(EquipmentSlot.MAINHAND, quiet), you);
        s.text(125, null, Side.RIGHT,
                "With one it is a dull plop: guards hear it from a quarter of the distance, other players from %s blocks, and"
                        + " there is no flash. The bullet does a little less damage (x%s).",
                HowToSceneBuilder.number(ImtConfig.Guns.SUPPRESSOR_HEARING), HowToSceneBuilder.number(ImtConfig.Guns.SUPPRESSOR_DAMAGE));
        s.idle(55);
        Vec3 tip = gun.add(ACROSS.scale(0.4));
        s.beam(3, tip, gun.add(ACROSS.scale(2.6)), BULLET);
        s.sound(ModSounds.SUPPRESSED_9MM.get(), 0.6f, 1.0f);
        s.pulse(12, tip, 0.7f, HowToSceneBuilder.GREEN);
        s.idle(75);
        s.text(125, null, Side.RIGHT,
                "One per caliber: the *9mm Suppressor* (Glock, MP5), the *Rifle Suppressor* (AK-47, AWP) and the *Shotgun"
                        + " Suppressor* (Remington 870).");
        s.idle(130);

        s.chapter("Grips, bipod, tube");
        // Eén rij die blijft staan, met twee teksten na elkaar op dezelfde plek ernaast (zoals rowWithText).
        double gripsHalf = 1.5 * 0.8 + 1.2 * 0.35;
        Vec3 grips = centre.add(0, 0.9, 0).add(ACROSS.scale(0.9 - gripsHalf));
        row(s, 300, grips, 0.8f, 1.2f, List.of(attachment(Attachment.VERTICAL_GRIP), attachment(Attachment.ANGLED_GRIP),
                attachment(Attachment.BIPOD), attachment(Attachment.TUBE_EXTENSION)));
        s.text(135, centre.add(0, 0.9, 0).add(ACROSS.scale(1.1)), Side.RIGHT,
                "Under the MP5 and the AK-47 goes one grip. The *Vertical Grip*: less recoil (x%s). The *Angled Grip*: you"
                        + " aim and draw the gun %sx faster.",
                HowToSceneBuilder.number(ImtConfig.Guns.VERTICAL_GRIP_RECOIL), HowToSceneBuilder.number(ImtConfig.Guns.ANGLED_GRIP_AIM_SPEED));
        s.idle(140);
        s.text(140, centre.add(0, 0.9, 0).add(ACROSS.scale(1.1)), Side.RIGHT,
                "The *Bipod* (AWP): sneak and stand still, and the rifle hardly kicks. The *Tube Extension* (Remington 870):"
                        + " %s shells in the tube instead of %s.",
                HowToSceneBuilder.number(ImtConfig.Guns.EXTENDED_CAPACITY[4]), HowToSceneBuilder.number(ImtConfig.Guns.MAGAZINE_CAPACITY[4]));
        s.idle(150);

        s.chapter("Extended magazines");
        rowWithText(s, 160, centre.add(0, 0.9, 0), 0.8f, 1.3f, List.of(MagazineItem.of(FirearmType.GLOCK_19, true, 33),
                        MagazineItem.of(FirearmType.MP5, true, 40), MagazineItem.of(FirearmType.AK_47, true, 40),
                        MagazineItem.of(FirearmType.AWP, true, 10)),
                "Every gun with a magazine also has a longer one, an item of its own: the Glock %s rounds, the MP5 %s, the"
                        + " AK-47 %s, the AWP %s. You make it on the row *Extended Magazine*, and it only fits its own gun.",
                HowToSceneBuilder.number(ImtConfig.Guns.EXTENDED_CAPACITY[0]), HowToSceneBuilder.number(ImtConfig.Guns.EXTENDED_CAPACITY[1]),
                HowToSceneBuilder.number(ImtConfig.Guns.EXTENDED_CAPACITY[2]), HowToSceneBuilder.number(ImtConfig.Guns.EXTENDED_CAPACITY[3]));
        s.idle(150);

        s.chapter("On laser guns too");
        ItemStack rifle = LaserGunItem.loaded(GunType.RIFLE);
        FirearmItem.setAttachment(rifle, Attachment.RED_DOT);
        FirearmItem.setAttachment(rifle, Attachment.VERTICAL_GRIP);
        LaserGunItem.setCell(rifle, EnergyCellItem.FULL);
        ItemStack sniper = LaserGunItem.loaded(GunType.SNIPER);
        FirearmItem.setAttachment(sniper, Attachment.SNIPER_SCOPE);
        rowWithText(s, 150, centre.add(0, 0.9, 0), 1.3f, 1.6f, List.of(rifle, sniper),
                "At the *Laser Workbench*: the Red Dot Sight goes on every laser gun, the Sniper Scope on the Laser Sniper,"
                        + " and a grip under the Laser Rifle or the Laser Sniper. A sight takes the place of the gun's own.");
        s.idle(145);

        s.chapter("Finding them");
        rowWithText(s, 130, centre.add(0, 0.9, 0), 0.9f, 1.2f, List.of(attachment(Attachment.SUPPRESSOR_RIFLE),
                        attachment(Attachment.RED_DOT), attachment(Attachment.ANGLED_GRIP)),
                "You also find attachments in the chests of the *Biebia's Industries* bunker and tower, and on *Raven"
                        + " soldiers*: what is on a soldier's gun is still on it when he drops it.");
        s.idle(125);
        return List.of(s.build());
    }
}
