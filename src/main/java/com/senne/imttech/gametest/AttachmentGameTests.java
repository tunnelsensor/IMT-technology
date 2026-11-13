package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.Attachment;
import com.senne.imttech.guns.AttachmentItem;
import com.senne.imttech.guns.AttachmentSlot;
import com.senne.imttech.guns.BulletEntity;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.GunAttachments;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunSlot;
import com.senne.imttech.guns.GunsmithTableBlockEntity;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de attachments van de grote update (hoofdstuk 103, stap 5). Jouw wens: "een attachment systeem, de
 * attachments maak je net zoals de gun parts in de Gunsmith Table"; jouw keuzes: alleen een red dot en één sniperkijker
 * van 1x tot 10x, en erop en eraf alleen op de Gunsmith Table.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class AttachmentGameTests {
    private AttachmentGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Een attachment sleep je op de Gunsmith Table op het wapen: de Red Dot Sight past op elk vuurwapen, de Sniper Scope
     * niet op een pistool of een hagelgeweer. Er kan maar één kijker tegelijk op; eraf halen geeft het item terug, en dan
     * past de andere. Een kijker staat op een onderdeel (bij een pistool de slede, bij een lang wapen de behuizing): dat
     * onderdeel krijg je niet uit het wapen zolang hij erop staat, en zonder dat onderdeel kan hij er niet op.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "attachments")
    public static void sights_go_on_and_off_at_the_gunsmith_table(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.GUNSMITH_TABLE.get());
        GunsmithTableBlockEntity table = (GunsmithTableBlockEntity) helper.getBlockEntity(at);
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 6), "sight-smith");
        player.getInventory().clearContent();
        // Wat past waar.
        for (FirearmType type : FirearmType.values()) {
            check(Attachment.RED_DOT.fits(type), "De Red Dot Sight hoort op de " + type.id + " te passen");
            check(GunSlot.SIGHT.fitsOn(type), "De " + type.id + " hoort een plek voor een kijker te hebben");
        }
        check(!Attachment.SNIPER_SCOPE.fits(FirearmType.GLOCK_19) && !Attachment.SNIPER_SCOPE.fits(FirearmType.REMINGTON_870),
                "De Sniper Scope hoort niet op een pistool of een hagelgeweer te passen");
        check(Attachment.SNIPER_SCOPE.fits(FirearmType.AWP) && Attachment.SNIPER_SCOPE.fits(FirearmType.AK_47)
                && Attachment.SNIPER_SCOPE.fits(FirearmType.MP5), "De Sniper Scope hoort op de AWP, de AK-47 en de MP5 te passen");
        // Een Glock op de tafel: de kijker van een sluipschutter past er niet op, de red dot wel.
        player.getInventory().setItem(0, FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL));
        player.getInventory().setItem(1, new ItemStack(ModItems.SNIPER_SCOPE.get()));
        player.getInventory().setItem(2, new ItemStack(ModItems.RED_DOT_SIGHT.get()));
        player.getInventory().setItem(3, new ItemStack(ModItems.RED_DOT_SIGHT.get()));
        check(GunsmithTableBlockEntity.slotOf(player.getInventory().getItem(2)) == GunSlot.SIGHT, "Een red dot hoort op de plek Sight");
        check(!table.install(player, 2), "Een kijker ging op een lege tafel");
        check(table.putGun(player, 0), "De Glock ging niet op de tafel");
        check(!table.install(player, 1), "De Sniper Scope ging op een Glock");
        check(table.install(player, 2), "De Red Dot Sight ging niet op de Glock");
        check(FirearmItem.sight(table.gun()) == Attachment.RED_DOT && player.getInventory().getItem(2).isEmpty(),
                "De red dot zit niet op de Glock, of het item bleef in je inventaris");
        check(!table.install(player, 3), "Er ging een tweede kijker op");
        check(player.getInventory().getItem(3).getCount() == 1, "De tweede red dot verdween");
        // De red dot van een pistool staat op de slede: zolang hij erop staat, krijg je de slede er niet af.
        check(AttachmentSlot.SIGHT.carrier(FirearmType.GLOCK_19) == GunPart.SLIDE && AttachmentSlot.SIGHT.carrier(FirearmType.AWP) == GunPart.FRAME,
                "Een kijker hoort bij een pistool op de slede te staan en bij een lang wapen op de behuizing");
        check(GunsmithTableBlockEntity.sittingOn(table.gun(), GunPart.SLIDE) == Attachment.RED_DOT, "De red dot hoort op de slede te staan");
        check(!table.remove(player, GunPart.SLIDE), "De slede ging eraf met de red dot er nog op");
        check(FirearmItem.parts(table.gun()).has(GunPart.SLIDE), "De slede is weg");
        // Eraf: het item komt terug.
        check(table.remove(player, GunSlot.SIGHT.ordinal()), "De red dot ging er niet af");
        check(FirearmItem.sight(table.gun()) == null, "De red dot zit er nog op");
        check(player.getInventory().countItem(ModItems.RED_DOT_SIGHT.get()) == 2, "De red dot kwam niet terug");
        check(!table.remove(player, GunSlot.SIGHT.ordinal()), "Er kwam nog een kijker van een wapen zonder kijker");
        // Nu kan de slede eraf; en zonder slede kan er geen kijker op.
        check(table.remove(player, GunPart.SLIDE), "De slede ging er niet af zonder kijker");
        int dot = player.getInventory().findSlotMatchingItem(new ItemStack(ModItems.RED_DOT_SIGHT.get()));
        check(!table.install(player, dot), "Een red dot ging op een Glock zonder slede");
        check(player.getInventory().countItem(ModItems.RED_DOT_SIGHT.get()) == 2, "De red dot verdween op een Glock zonder slede");
        int slide = -1;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (GunsmithTableBlockEntity.slotOf(player.getInventory().getItem(i)) == GunSlot.SLIDE) {
                slide = i;
            }
        }
        check(slide >= 0 && table.install(player, slide), "De slede ging niet terug op de Glock");
        check(table.install(player, player.getInventory().findSlotMatchingItem(new ItemStack(ModItems.RED_DOT_SIGHT.get()))),
                "De red dot ging niet op de Glock met zijn slede terug erop");
        check(table.takeGun(player), "De Glock kon niet mee");
        // Een AWP: de Sniper Scope past. Het wapen houdt hem als je het meeneemt; de behuizing waar hij op staat, krijg
        // je er pas uit als de kijker eraf is.
        player.getInventory().clearContent();
        player.getInventory().setItem(0, FirearmItem.withParts(FirearmType.AWP, com.senne.imttech.guns.GunParts.EMPTY
                .with(GunPart.FRAME, com.senne.imttech.guns.PartState.fresh(GunMaterial.STEEL))));
        player.getInventory().setItem(1, new ItemStack(ModItems.SNIPER_SCOPE.get()));
        check(table.putGun(player, 0) && table.install(player, 1), "De Sniper Scope ging niet op de AWP");
        check(table.takeGun(player), "De AWP kon niet mee");
        ItemStack awp = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (FirearmItem.typeOf(stack) == FirearmType.AWP) {
                awp = stack;
            }
        }
        check(FirearmItem.sight(awp) == Attachment.SNIPER_SCOPE, "De AWP verloor zijn kijker toen je hem van de tafel nam");
        int slot = player.getInventory().findSlotMatchingItem(awp);
        check(table.putGun(player, slot), "De AWP ging niet terug op de tafel");
        check(!table.remove(player, GunPart.FRAME), "De Receiver ging eruit met de kijker er nog op");
        check(!table.gun().isEmpty() && FirearmItem.sight(table.gun()) == Attachment.SNIPER_SCOPE, "De AWP of zijn kijker is weg");
        check(table.remove(player, GunSlot.SIGHT.ordinal()), "De kijker ging niet van de AWP af");
        check(player.getInventory().countItem(ModItems.SNIPER_SCOPE.get()) == 1, "De kijker kwam niet terug");
        check(table.remove(player, GunPart.FRAME), "De Receiver ging er niet uit");
        check(table.gun().isEmpty(), "Er ligt nog een wapen op de tafel");
        helper.succeed();
    }

    /**
     * Een kijker maakt een <i>gericht</i> schot preciezer, en helpt niet vanuit de heup. Met de spreiding op 10 graden
     * en de red dot op "maal 0" vliegt een gerichte kogel precies waar je kijkt; vanuit de heup wijkt hij af.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "attachments")
    public static void a_sight_tightens_an_aimed_shot_only(GameTestHelper helper) {
        float[] spread = ImtConfig.Guns.SPREAD;
        float[] aimed = ImtConfig.Guns.SPREAD_AIMING;
        float dot = ImtConfig.Guns.RED_DOT_AIM_SPREAD;
        float jam = ImtConfig.Guns.JAM_CHANCE;
        float misfire = ImtConfig.Guns.MISFIRE_CHANCE;
        ImtConfig.Guns.SPREAD = new float[]{10, 10, 10, 10, 10};
        ImtConfig.Guns.SPREAD_AIMING = new float[]{10, 10, 10, 10, 10};
        ImtConfig.Guns.RED_DOT_AIM_SPREAD = 0;
        ImtConfig.Guns.JAM_CHANCE = 0;
        ImtConfig.Guns.MISFIRE_CHANCE = 0;
        try {
            ItemStack gun = FirearmItem.loaded(FirearmType.MP5, GunMaterial.STEEL);
            FirearmItem.setAttachment(gun, Attachment.RED_DOT);
            check(FirearmItem.attachment(gun, AttachmentSlot.SIGHT) == Attachment.RED_DOT, "De red dot zit niet op de MP5");
            ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 4), "sight-shooter");
            player.getAbilities().instabuild = false;
            player.getInventory().clearContent();
            player.getInventory().selected = 0;
            player.setItemInHand(InteractionHand.MAIN_HAND, gun);
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(4.5, 3.6, 12.5)));
            Vec3 look = player.getViewVector(1.0f);
            Firearms.forgetCooldown(player);
            check(Firearms.fire(player, player.getYRot(), player.getXRot(), true) == Firearms.Result.FIRED, "Het gerichte schot ging niet af");
            List<BulletEntity> bullets = helper.getLevel().getEntitiesOfClass(BulletEntity.class, helper.getBounds().inflate(4));
            check(bullets.size() == 1, "Er hoort precies één kogel te zijn");
            double off = Math.toDegrees(Math.acos(Math.min(1, bullets.getFirst().getDeltaMovement().normalize().dot(look))));
            check(off < 0.05, "Een gericht schot met de red dot week " + off + " graden af");
            bullets.getFirst().discard();
            // Vanuit de heup helpt de kijker niet: tien schoten, en minstens één wijkt duidelijk af.
            double worst = 0;
            for (int i = 0; i < 10; i++) {
                Firearms.forgetCooldown(player);
                check(Firearms.fire(player, player.getYRot(), player.getXRot(), false) == Firearms.Result.FIRED, "Het schot vanuit de heup ging niet af");
                for (BulletEntity bullet : helper.getLevel().getEntitiesOfClass(BulletEntity.class, helper.getBounds().inflate(4))) {
                    worst = Math.max(worst, Math.toDegrees(Math.acos(Math.min(1, bullet.getDeltaMovement().normalize().dot(look)))));
                    bullet.discard();
                }
            }
            check(worst > 0.5, "Vanuit de heup vlogen de kogels ook recht (" + worst + " graden): de kijker hoort daar niet te helpen");
        } finally {
            ImtConfig.Guns.SPREAD = spread;
            ImtConfig.Guns.SPREAD_AIMING = aimed;
            ImtConfig.Guns.RED_DOT_AIM_SPREAD = dot;
            ImtConfig.Guns.JAM_CHANCE = jam;
            ImtConfig.Guns.MISFIRE_CHANCE = misfire;
        }
        helper.succeed();
    }

    /**
     * Wat op een wapen zit, wordt bewaard als een rijtje namen: opslaan en weer inlezen geeft hetzelfde, en een naam die
     * niet (meer) bestaat wordt overgeslagen in plaats van het wapen te breken. Op één plek zit er hoogstens één.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "attachments")
    public static void attachments_are_saved_by_name(GameTestHelper helper) {
        GunAttachments one = GunAttachments.EMPTY.with(Attachment.RED_DOT);
        check(one.get(AttachmentSlot.SIGHT) == Attachment.RED_DOT, "De red dot staat niet op de plek Sight");
        GunAttachments swapped = one.with(Attachment.SNIPER_SCOPE);
        check(swapped.list().size() == 1 && swapped.get(AttachmentSlot.SIGHT) == Attachment.SNIPER_SCOPE,
                "Op één plek horen geen twee attachments te zitten");
        Tag saved = GunAttachments.CODEC.encodeStart(NbtOps.INSTANCE, swapped).getOrThrow();
        check(GunAttachments.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow().equals(swapped), "Opslaan en inlezen gaf iets anders");
        net.minecraft.nbt.ListTag odd = new net.minecraft.nbt.ListTag();
        odd.add(net.minecraft.nbt.StringTag.valueOf("no_such_attachment"));
        odd.add(net.minecraft.nbt.StringTag.valueOf(Attachment.RED_DOT.id));
        GunAttachments read = GunAttachments.CODEC.parse(NbtOps.INSTANCE, odd).getOrThrow();
        check(read.list().equals(List.of(Attachment.RED_DOT)), "Een onbekende naam hoort overgeslagen te worden, niet " + read.list());
        check(swapped.without(AttachmentSlot.SIGHT).isEmpty(), "Zonder kijker hoort er niets meer op te zitten");
        check(AttachmentItem.of(new ItemStack(ModItems.SNIPER_SCOPE.get())) == Attachment.SNIPER_SCOPE, "Het item kent zijn attachment niet");
        helper.succeed();
    }

    // ================================================================ Stap 6: dempers, grips, de tweepoot, de verlengbuis

    /**
     * Een demper past op één soort kaliber (9mm, geweer of hagel). Met een demper horen bewakers het schot van veel
     * minder ver, doet de kogel een tikje minder schade, en klinkt het schot anders.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "attachments")
    public static void a_suppressor_quiets_the_shot_and_softens_the_bullet(GameTestHelper helper) {
        Attachment[] cans = {Attachment.SUPPRESSOR_9MM, Attachment.SUPPRESSOR_RIFLE, Attachment.SUPPRESSOR_SHOTGUN};
        for (FirearmType type : FirearmType.values()) {
            int fitting = 0;
            for (Attachment can : cans) {
                fitting += can.fits(type) ? 1 : 0;
            }
            check(fitting == 1, "Op de " + type.id + " hoort precies één demper te passen, niet " + fitting);
            check(GunSlot.MUZZLE.fitsOn(type), "De " + type.id + " hoort een plek voor een demper te hebben");
            check(type.suppressedSound() != type.fireSound(), "Een gedempt schot hoort anders te klinken (" + type.id + ")");
        }
        check(Attachment.SUPPRESSOR_9MM.fits(FirearmType.GLOCK_19) && Attachment.SUPPRESSOR_9MM.fits(FirearmType.MP5)
                && Attachment.SUPPRESSOR_RIFLE.fits(FirearmType.AK_47) && Attachment.SUPPRESSOR_RIFLE.fits(FirearmType.AWP)
                && Attachment.SUPPRESSOR_SHOTGUN.fits(FirearmType.REMINGTON_870), "Een demper past niet op zijn eigen kaliber");
        check(AttachmentSlot.MUZZLE.carrier(FirearmType.GLOCK_19) == GunPart.BARREL, "Een demper hoort op de loop te zitten");
        float jam = ImtConfig.Guns.JAM_CHANCE;
        float misfire = ImtConfig.Guns.MISFIRE_CHANCE;
        ImtConfig.Guns.JAM_CHANCE = 0;
        ImtConfig.Guns.MISFIRE_CHANCE = 0;
        try {
            ItemStack plain = FirearmItem.loaded(FirearmType.MP5, GunMaterial.STEEL);
            ItemStack quiet = FirearmItem.loaded(FirearmType.MP5, GunMaterial.STEEL);
            FirearmItem.setAttachment(quiet, Attachment.SUPPRESSOR_9MM);
            check(!FirearmItem.suppressed(plain) && FirearmItem.suppressed(quiet), "De MP5 weet niet dat er een demper op zit");
            check(FirearmItem.noiseFactor(plain) == 1 && Math.abs(FirearmItem.noiseFactor(quiet) - ImtConfig.Guns.SUPPRESSOR_NOISE) < 1e-4,
                    "Met een demper horen bewakers het schot van " + ImtConfig.Guns.SUPPRESSOR_NOISE + " keer zo ver te horen");
            check(FirearmType.MP5.noise() * FirearmItem.noiseFactor(quiet) < FirearmType.MP5.noise() * 0.5f,
                    "Een gedempt schot hoort veel minder ver te dragen");
            ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 4), "quiet-shooter");
            player.getAbilities().instabuild = false;
            player.getInventory().clearContent();
            player.getInventory().selected = 0;
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(4.5, 3.6, 12.5)));
            float[] damage = new float[2];
            ItemStack[] guns = {plain, quiet};
            for (int i = 0; i < 2; i++) {
                player.setItemInHand(InteractionHand.MAIN_HAND, guns[i]);
                Firearms.forgetCooldown(player);
                check(Firearms.fire(player, player.getYRot(), player.getXRot(), true) == Firearms.Result.FIRED, "Schot " + (i + 1) + " ging niet af");
                List<BulletEntity> bullets = helper.getLevel().getEntitiesOfClass(BulletEntity.class, helper.getBounds().inflate(4));
                check(bullets.size() == 1, "Er hoort precies één kogel te zijn, niet " + bullets.size());
                damage[i] = bullets.getFirst().damage();
                bullets.getFirst().discard();
            }
            check(Math.abs(damage[1] - damage[0] * ImtConfig.Guns.SUPPRESSOR_DAMAGE) < 0.001f,
                    "Een gedempte kogel hoort " + ImtConfig.Guns.SUPPRESSOR_DAMAGE + " keer de schade te doen: " + damage[0] + " werd " + damage[1]);
        } finally {
            ImtConfig.Guns.JAM_CHANCE = jam;
            ImtConfig.Guns.MISFIRE_CHANCE = misfire;
        }
        helper.succeed();
    }

    /**
     * De Vertical Grip dempt de terugslag, de Angled Grip maakt je sneller (richten en trekken), en de Bipod doet alleen
     * iets als je het wapen steunt (sneaken en stilstaan). Er zit hoogstens één van de drie onder een wapen; de grips
     * passen onder de MP5 en de AK-47, de tweepoot onder de AWP.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "attachments")
    public static void grips_and_the_bipod_change_how_a_gun_handles(GameTestHelper helper) {
        for (FirearmType type : FirearmType.values()) {
            boolean grips = type == FirearmType.MP5 || type == FirearmType.AK_47;
            check(Attachment.VERTICAL_GRIP.fits(type) == grips && Attachment.ANGLED_GRIP.fits(type) == grips,
                    "De grips horen alleen onder de MP5 en de AK-47 te passen (" + type.id + ")");
            check(Attachment.BIPOD.fits(type) == (type == FirearmType.AWP), "De tweepoot hoort alleen onder de AWP te passen (" + type.id + ")");
            check(GunSlot.UNDERBARREL.fitsOn(type) == (grips || type == FirearmType.AWP),
                    "De plek onder de loop klopt niet bij de " + type.id);
        }
        ItemStack ak = FirearmItem.loaded(FirearmType.AK_47, GunMaterial.STEEL);
        check(FirearmItem.recoilFactor(ak, false) == 1 && FirearmItem.aimSpeedFactor(ak) == 1 && FirearmItem.drawSpeedFactor(ak) == 1,
                "Een wapen zonder attachments hoort gewoon te doen");
        FirearmItem.setAttachment(ak, Attachment.VERTICAL_GRIP);
        check(Math.abs(FirearmItem.recoilFactor(ak, false) - ImtConfig.Guns.VERTICAL_GRIP_RECOIL) < 1e-4
                        && Math.abs(FirearmItem.recoilFactor(ak, true) - ImtConfig.Guns.VERTICAL_GRIP_RECOIL) < 1e-4,
                "De Vertical Grip hoort de terugslag te dempen, of je steunt of niet");
        check(FirearmItem.aimSpeedFactor(ak) == 1, "De Vertical Grip hoort je niet sneller te maken");
        // De schuine greep komt op dezelfde plek: de rechte is dan weg.
        FirearmItem.setAttachment(ak, Attachment.ANGLED_GRIP);
        check(FirearmItem.attachments(ak).list().size() == 1 && FirearmItem.attachment(ak, AttachmentSlot.UNDERBARREL) == Attachment.ANGLED_GRIP,
                "Onder een wapen hoort maar één ding te zitten");
        check(FirearmItem.recoilFactor(ak, false) == 1, "De Angled Grip hoort de terugslag niet te dempen");
        check(Math.abs(FirearmItem.aimSpeedFactor(ak) - ImtConfig.Guns.ANGLED_GRIP_AIM_SPEED) < 1e-4, "De Angled Grip hoort je sneller te laten richten");
        // Met een red dot erbij: samen nog sneller.
        FirearmItem.setAttachment(ak, Attachment.RED_DOT);
        check(Math.abs(FirearmItem.aimSpeedFactor(ak) - ImtConfig.Guns.ANGLED_GRIP_AIM_SPEED * ImtConfig.Guns.RED_DOT_AIM_SPEED) < 1e-4,
                "Een red dot en een Angled Grip horen samen te tellen");
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 4), "grip-holder");
        ItemStack plain = FirearmItem.loaded(FirearmType.AK_47, GunMaterial.STEEL);
        int slow = Firearms.ticksFor(player, Firearms.ActionKind.DRAW, Firearms.DRAW_FROM_SLING, plain);
        int fast = Firearms.ticksFor(player, Firearms.ActionKind.DRAW, Firearms.DRAW_FROM_SLING, ak);
        check(fast < slow, "Met een Angled Grip hoor je het wapen sneller te trekken: " + slow + " werd " + fast + " ticks");
        // De tweepoot: alleen als je steunt.
        ItemStack awp = FirearmItem.loaded(FirearmType.AWP, GunMaterial.STEEL);
        FirearmItem.setAttachment(awp, Attachment.BIPOD);
        check(FirearmItem.recoilFactor(awp, false) == 1, "Een tweepoot hoort niets te doen als je rechtop staat of loopt");
        check(Math.abs(FirearmItem.recoilFactor(awp, true) - ImtConfig.Guns.BIPOD_RECOIL) < 1e-4,
                "Een tweepoot hoort de terugslag bijna weg te nemen als je sneakt en stilstaat");
        check(AttachmentSlot.UNDERBARREL.carrier(FirearmType.AWP) == GunPart.FRAME, "Een greep of tweepoot hoort aan de behuizing te zitten");
        helper.succeed();
    }

    /**
     * De Tube Extension: twee patronen meer in de buis van de Remington 870. Haal je hem er op de Gunsmith Table af, dan
     * krijg je de patronen die er niet meer in passen terug. En een demper houdt de loop vast, zoals een kijker de slede.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "attachments")
    public static void a_tube_extension_holds_two_more_shells(GameTestHelper helper) {
        FirearmType type = FirearmType.REMINGTON_870;
        for (FirearmType each : FirearmType.values()) {
            check(Attachment.TUBE_EXTENSION.fits(each) == each.tube() && GunSlot.TUBE.fitsOn(each) == each.tube(),
                    "De verlengbuis hoort alleen op een wapen met een buis te passen (" + each.id + ")");
        }
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.GUNSMITH_TABLE.get());
        GunsmithTableBlockEntity table = (GunsmithTableBlockEntity) helper.getBlockEntity(at);
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 6), "tube-smith");
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        player.getInventory().setItem(0, FirearmItem.withParts(type, com.senne.imttech.guns.GunParts.classic(type)));
        player.getInventory().setItem(1, new ItemStack(ModItems.attachment(Attachment.TUBE_EXTENSION)));
        player.getInventory().setItem(2, new ItemStack(ModItems.attachment(Attachment.SUPPRESSOR_SHOTGUN)));
        player.getInventory().setItem(3, new ItemStack(ModItems.ROUND_12G.get(), 10));
        check(FirearmItem.capacity(player.getInventory().getItem(0)) == 6, "De buis van een gewone 870 hoort 6 patronen te houden");
        check(table.putGun(player, 0) && table.install(player, 1) && table.install(player, 2), "De verlengbuis of de demper ging niet op de 870");
        check(FirearmItem.capacity(table.gun()) == type.capacity(true) && type.capacity(true) == 8,
                "Met de verlengbuis horen er 8 patronen in de buis te passen, niet " + FirearmItem.capacity(table.gun()));
        // De demper zit op de loop: die krijg je er dan niet uit.
        check(GunsmithTableBlockEntity.sittingOn(table.gun(), GunPart.BARREL) == Attachment.SUPPRESSOR_SHOTGUN, "De demper hoort op de loop te zitten");
        check(!table.remove(player, GunPart.BARREL), "De loop ging eruit met de demper er nog op");
        check(table.takeGun(player), "De 870 kon niet mee");
        int slot = -1;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (FirearmItem.typeOf(player.getInventory().getItem(i)) == type) {
                slot = i;
            }
        }
        check(slot >= 0, "De 870 zit niet in je inventaris");
        // In je hand: acht patronen erin, de negende niet.
        ItemStack gun = player.getInventory().getItem(slot);
        player.getInventory().setItem(slot, ItemStack.EMPTY);
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        gun = player.getMainHandItem();
        for (int i = 0; i < 8; i++) {
            check(Firearms.request(player, Firearms.ActionKind.LOAD_TUBE, 0), "Patroon " + (i + 1) + " ging de buis niet in");
            Firearms.finishNow(player);
        }
        check(FirearmItem.roundsInMagazine(gun) == 8, "Er horen 8 patronen in de buis te zitten, niet " + FirearmItem.roundsInMagazine(gun));
        check(!Firearms.request(player, Firearms.ActionKind.LOAD_TUBE, 0), "Een negende patroon ging in de buis");
        check(player.getInventory().countItem(ModItems.ROUND_12G.get()) == 2, "Er horen 2 patronen over te zijn");
        // Terug op de tafel, de verlengbuis eraf: twee patronen komen terug.
        check(table.putGun(player, 0), "De 870 ging niet terug op de tafel");
        check(table.remove(player, GunSlot.TUBE.ordinal()), "De verlengbuis ging er niet af");
        check(FirearmItem.roundsInMagazine(table.gun()) == 6 && FirearmItem.capacity(table.gun()) == 6,
                "Zonder verlengbuis horen er nog 6 patronen in de buis te zitten, niet " + FirearmItem.roundsInMagazine(table.gun()));
        check(player.getInventory().countItem(ModItems.ROUND_12G.get()) == 4, "De twee patronen die niet meer pasten kwamen niet terug");
        check(player.getInventory().countItem(ModItems.attachment(Attachment.TUBE_EXTENSION)) == 1, "De verlengbuis kwam niet terug");
        // De demper eraf: nu kan de loop eruit.
        check(table.remove(player, GunSlot.MUZZLE.ordinal()) && table.remove(player, GunPart.BARREL), "De loop ging er niet uit zonder demper");
        helper.succeed();
    }
}
