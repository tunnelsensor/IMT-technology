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
}
