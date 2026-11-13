package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.backpack.BackpackItem;
import com.senne.imttech.backpack.RavenPacks;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.Attachment;
import com.senne.imttech.guns.AttachmentItem;
import com.senne.imttech.guns.AttachmentSlot;
import com.senne.imttech.guns.BulletEntity;
import com.senne.imttech.guns.Caliber;
import com.senne.imttech.guns.CasingEntity;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.raven.RavenArms;
import com.senne.imttech.raven.RavenBodyEntity;
import com.senne.imttech.raven.RavenSoldierEntity;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.LaserPart;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor stap 8 van de grote update (hoofdstuk 103). Jouw keuzes: "Raven-soldaten met de nieuwe wapens" en
 * "attachments in buit". Een soldaat draagt een van zeven wapens (RavenArms), schiet ermee zoals dat wapen schiet (een
 * salvo met de MP5, hagel met de Remington 870, doorladen na elk schot met de AWP), en in zijn rugzak, op zijn lichaam
 * en in de kisten van de bunker en de toren vind je munitie voor de nieuwe wapens en af en toe een attachment.
 *
 * De tests waarin een soldaat zelf denkt, staan elk in een eigen batch: soldaten in de buurt waarschuwen elkaar, ook
 * die van een andere test.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class RavenArmsGameTests {
    private RavenArmsGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.001;
    }

    /** Het getal van dit vuurwapen uit een rijtje van [raven]. */
    private static float per(float[] list, FirearmType type) {
        return list[Math.min(type.ordinal(), list.length - 1)];
    }

    /** Een doelwit dat stil blijft staan en tegen een stootje kan (100 levenspunten). */
    private static IronGolem target(GameTestHelper helper, BlockPos at) {
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, at);
        golem.setNoAi(true);
        return golem;
    }

    // ================================================================ Wat hij draagt

    /**
     * Een soldaat kiest zijn wapen met de gewichten uit [raven] weaponWeights: elk van de zeven komt voor, ongeveer zo
     * vaak als zijn gewicht zegt, en een soort met gewicht 0 nooit. Het wapen is compleet en geladen; de AWP heeft
     * altijd zijn Sniper Scope en nooit een demper; wat er verder op zit, past op dat wapen. Een lasergeweer heeft een
     * volle cel en geen Nether Star.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "raven_arms")
    public static void raven_soldiers_carry_the_new_guns(GameTestHelper helper) {
        RandomSource random = RandomSource.create(103);
        Map<RavenArms.Kind, Integer> seen = new EnumMap<>(RavenArms.Kind.class);
        int draws = 4000;
        for (int i = 0; i < draws; i++) {
            seen.merge(RavenArms.pick(random), 1, Integer::sum);
        }
        float total = 0;
        for (RavenArms.Kind kind : RavenArms.Kind.values()) {
            total += kind.weight();
        }
        for (RavenArms.Kind kind : RavenArms.Kind.values()) {
            double expected = kind.weight() / total;
            double share = seen.getOrDefault(kind, 0) / (double) draws;
            check(kind.weight() <= 0 || share > 0, "Geen enkele soldaat draagt een " + kind);
            check(Math.abs(share - expected) < 0.04, kind + ": " + Math.round(share * 100) + "% van de soldaten, verwacht "
                    + Math.round(expected * 100) + "%");
        }
        // Een soort op 0 komt niet meer voor; alles op 0 geeft toch een Glock (een soldaat zonder wapen doet niets).
        float[] weights = ImtConfig.Raven.WEAPON_WEIGHTS;
        try {
            ImtConfig.Raven.WEAPON_WEIGHTS = new float[] {1, 0, 0, 1, 0, 0, 0};
            for (int i = 0; i < 300; i++) {
                RavenArms.Kind kind = RavenArms.pick(random);
                check(kind == RavenArms.Kind.GLOCK_19 || kind == RavenArms.Kind.AWP, "Een soort met gewicht 0 kwam toch voor: " + kind);
            }
            ImtConfig.Raven.WEAPON_WEIGHTS = new float[] {0, 0, 0, 0, 0, 0, 0};
            check(RavenArms.pick(random) == RavenArms.Kind.GLOCK_19, "Met alle gewichten op 0 hoort hij een Glock te krijgen");
        } finally {
            ImtConfig.Raven.WEAPON_WEIGHTS = weights;
        }

        // Het wapen zelf.
        float chance = ImtConfig.Raven.ATTACHMENT_CHANCE;
        try {
            for (RavenArms.Kind kind : RavenArms.Kind.values()) {
                Set<Attachment> mounted = EnumSet.noneOf(Attachment.class);
                for (int i = 0; i < 200; i++) {
                    ImtConfig.Raven.ATTACHMENT_CHANCE = i % 2 == 0 ? 1.0f : 0.0f;
                    ItemStack weapon = RavenArms.weapon(kind, random);
                    if (kind.laser != null) {
                        check(LaserGunItem.typeOf(weapon) == kind.laser, kind + " gaf geen " + kind.laser + ": " + weapon);
                        check(!LaserGunItem.parts(weapon).has(LaserPart.CORE), "Het lasergeweer van een soldaat heeft een Nether Star");
                        check(LaserGunItem.cell(weapon) > 0, "Het lasergeweer van een soldaat heeft geen cel");
                        check(FirearmItem.attachments(weapon).list().isEmpty(), "Op het lasergeweer van een soldaat zit iets");
                        continue;
                    }
                    FirearmType type = kind.firearm;
                    check(FirearmItem.typeOf(weapon) == type, kind + " gaf geen " + type + ": " + weapon);
                    check(FirearmItem.works(weapon), "Het wapen van een soldaat is niet compleet: " + type);
                    check(FirearmItem.chambered(weapon) && FirearmItem.totalRounds(weapon) > 1, "Het wapen van een soldaat is niet geladen: " + type);
                    List<Attachment> on = FirearmItem.attachments(weapon).list();
                    for (Attachment attachment : on) {
                        check(attachment.fits(type), attachment + " past niet op de " + type + " van een soldaat");
                        mounted.add(attachment);
                    }
                    int extra = type == FirearmType.AWP ? 1 : 0; // de kijker van de sluipschutter zit er altijd op
                    check(on.size() == extra + (i % 2 == 0 ? 1 : 0), "Op de " + type + " zitten " + on.size()
                            + " attachments (kans " + ImtConfig.Raven.ATTACHMENT_CHANCE + ")");
                    if (type == FirearmType.AWP) {
                        check(FirearmItem.sight(weapon) == Attachment.SNIPER_SCOPE, "Een sluipschutter zonder Sniper Scope");
                        check(FirearmItem.attachment(weapon, AttachmentSlot.MUZZLE) == null, "Een sluipschutter met een demper");
                    } else {
                        check(FirearmItem.sight(weapon) != Attachment.SNIPER_SCOPE, "Een Sniper Scope op de " + type + " van een soldaat");
                    }
                }
                if (kind.firearm == FirearmType.MP5) {
                    check(mounted.contains(Attachment.RED_DOT) && mounted.contains(Attachment.SUPPRESSOR_9MM)
                            && (mounted.contains(Attachment.VERTICAL_GRIP) || mounted.contains(Attachment.ANGLED_GRIP)),
                            "Op de MP5's van honderd soldaten zat geen red dot, greep of demper: " + mounted);
                }
            }
        } finally {
            ImtConfig.Raven.ATTACHMENT_CHANCE = chance;
        }
        helper.succeed();
    }

    // ================================================================ Wat het kan, en het schot

    /**
     * Elk vuurwapen schiet bij een soldaat zijn eigen kogels: één per schot, acht korrels bij de Remington 870, met de
     * schade uit [raven] gunDamage. Een halfautomatisch wapen werpt meteen een huls van zijn kaliber uit; een grendel of
     * een pomp pas als hij doorlaadt, een halve seconde later. De getallen per wapen (salvo, tijd, bereik, spreiding)
     * komen uit [raven]; een demper remt de kogel af en een kijker maakt hem preciezer, zoals bij jou.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "raven_arms")
    public static void a_raven_soldier_fires_the_gun_he_carries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        IronGolem target = target(helper, new BlockPos(4, 2, 6));
        RavenSoldierEntity soldier = GuardGameTests.soldier(helper, new BlockPos(12, 2, 6), 90, false);
        soldier.setPack(ItemStack.EMPTY);
        AABB arena = helper.getBounds().inflate(4);
        for (FirearmType type : FirearmType.values()) {
            ItemStack gun = FirearmItem.loaded(type, GunMaterial.STEEL);
            soldier.arm(gun);
            check(near(soldier.attackRange(), per(ImtConfig.Raven.GUN_RANGE, type)), "Het bereik met de " + type + " is " + soldier.attackRange());
            check(RavenArms.burst(gun) == Math.round(per(ImtConfig.Raven.GUN_BURST, type)), "Het salvo van de " + type + " klopt niet");
            check(RavenArms.interval(gun) == Math.round(per(ImtConfig.Raven.GUN_INTERVAL, type)), "De tijd tussen twee schoten van de " + type + " klopt niet");
            check(near(RavenArms.spread(gun), per(ImtConfig.Raven.GUN_SPREAD, type)), "De spreiding van de " + type + " klopt niet");
            check(RavenArms.aimTicks(gun) == (type == FirearmType.AWP ? 20 : 5), "Hoe lang hij aanlegt met de " + type + " klopt niet");

            soldier.performRangedAttack(target, 1.0f);
            List<BulletEntity> bullets = level.getEntitiesOfClass(BulletEntity.class, arena);
            check(bullets.size() == type.pellets(), "Een schot met de " + type + " gaf " + bullets.size() + " kogels, verwacht " + type.pellets());
            for (BulletEntity bullet : bullets) {
                check(bullet.getOwner() == soldier, "De kogel is niet van de soldaat");
                check(near(bullet.damage(), per(ImtConfig.Raven.GUN_DAMAGE, type)), "Een kogel uit de " + type + " van een soldaat doet "
                        + bullet.damage() + " schade, verwacht " + per(ImtConfig.Raven.GUN_DAMAGE, type));
                bullet.discard();
            }
            List<CasingEntity> casings = level.getEntitiesOfClass(CasingEntity.class, arena);
            if (type.manual()) {
                check(casings.isEmpty(), "De " + type + " werpt zijn huls al uit voor hij doorlaadt");
            } else {
                check(casings.size() == 1 && casings.getFirst().caliber() == type.caliber, "Geen huls van " + type.caliber + " uit de " + type + ": " + casings.size());
            }
            casings.forEach(CasingEntity::discard);
        }
        // Wat erop zit, werkt ook voor hem.
        ItemStack quiet = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        FirearmItem.setAttachment(quiet, Attachment.SUPPRESSOR_9MM);
        check(near(RavenArms.damage(quiet), per(ImtConfig.Raven.GUN_DAMAGE, FirearmType.GLOCK_19) * ImtConfig.Guns.SUPPRESSOR_DAMAGE),
                "Een demper remt de kogel van een soldaat niet af: " + RavenArms.damage(quiet));
        ItemStack scoped = FirearmItem.loaded(FirearmType.AWP, GunMaterial.STEEL);
        FirearmItem.setAttachment(scoped, Attachment.SNIPER_SCOPE);
        check(near(RavenArms.spread(scoped), per(ImtConfig.Raven.GUN_SPREAD, FirearmType.AWP) * ImtConfig.Guns.SCOPE_AIM_SPREAD),
                "Een kijker maakt een soldaat niet preciezer: " + RavenArms.spread(scoped));
        // Een lasergeweer houdt de oude getallen van [raven].
        ItemStack laser = RavenArms.weapon(RavenArms.Kind.LASER_RIFLE, RandomSource.create(1));
        check(near(RavenArms.damage(laser), ImtConfig.Raven.LASER_DAMAGE) && near(RavenArms.range(laser), ImtConfig.Raven.ATTACK_RANGE)
                && RavenArms.interval(laser) == Math.round(ImtConfig.Raven.FIRE_INTERVAL) && RavenArms.burst(laser) == 1
                && near(RavenArms.spread(laser), ImtConfig.Raven.SPREAD), "Een lasergeweer hoort de oude getallen van [raven] te houden");
        // Het laatste schot was met de Remington 870 (een pomp): een halve seconde later laadt hij door en valt de huls.
        helper.runAfterDelay(14, () -> {
            List<CasingEntity> casings = level.getEntitiesOfClass(CasingEntity.class, arena);
            check(casings.size() == 1 && casings.getFirst().caliber() == Caliber.G12,
                    "Na het doorladen ligt er geen huls van de Remington 870: " + casings.size());
            casings.forEach(CasingEntity::discard);
            helper.succeed();
        });
    }

    /** Houdt bij op welke tick er kogels bij kwamen in de arena (elke tick één keer vragen), per schot één regel. */
    private static List<Long> watchShots(GameTestHelper helper) {
        List<Long> shots = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        AABB arena = helper.getBounds().inflate(6);
        helper.onEachTick(() -> {
            boolean fired = false;
            for (BulletEntity bullet : helper.getLevel().getEntitiesOfClass(BulletEntity.class, arena)) {
                fired |= seen.add(bullet.getId());
            }
            if (fired) {
                shots.add(helper.getLevel().getGameTime());
            }
        });
        return shots;
    }

    /**
     * Een soldaat met een MP5 schiet in salvo's: drie schoten vlak na elkaar (om de twee ticks), dan een pauze van
     * minstens [raven] gunInterval, dan weer drie.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "raven_arms_burst")
    public static void an_mp5_soldier_fires_bursts_of_three(GameTestHelper helper) {
        IronGolem target = target(helper, new BlockPos(4, 2, 12));
        RavenSoldierEntity soldier = GuardGameTests.soldier(helper, new BlockPos(14, 2, 12), 90, true);
        ItemStack mp5 = FirearmItem.loaded(FirearmType.MP5, GunMaterial.STEEL);
        soldier.arm(mp5);
        soldier.setTarget(target);
        List<Long> shots = watchShots(helper);
        int burst = RavenArms.burst(mp5);
        int gap = RavenArms.burstGap(mp5);
        int interval = RavenArms.interval(mp5);
        helper.succeedWhen(() -> {
            check(shots.size() >= 2 * burst + 1, "Nog maar " + shots.size() + " schoten: " + shots);
            for (int i = 0; i < 2 * burst; i++) {
                long wait = shots.get(i + 1) - shots.get(i);
                if ((i + 1) % burst == 0) {
                    check(wait >= interval - 1, "Tussen twee salvo's zat maar " + wait + " ticks: " + shots);
                } else {
                    check(wait == gap, "In een salvo zat " + wait + " ticks tussen twee schoten, verwacht " + gap + ": " + shots);
                }
            }
        });
    }

    /**
     * Een sluipschutter met een AWP: één schot, dan een halve seconde later de grendel (de lege huls springt er dan pas
     * uit), en het volgende schot komt pas na [raven] gunInterval.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "raven_arms_sniper")
    public static void a_sniper_works_the_bolt_after_every_shot(GameTestHelper helper) {
        IronGolem target = target(helper, new BlockPos(3, 2, 12));
        RavenSoldierEntity soldier = GuardGameTests.soldier(helper, new BlockPos(19, 2, 12), 90, true);
        ItemStack awp = RavenArms.weapon(RavenArms.Kind.AWP, RandomSource.create(7));
        soldier.arm(awp);
        soldier.setTarget(target);
        List<Long> shots = watchShots(helper);
        List<Long> casings = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        AABB arena = helper.getBounds().inflate(6);
        helper.onEachTick(() -> {
            for (CasingEntity casing : helper.getLevel().getEntitiesOfClass(CasingEntity.class, arena)) {
                if (seen.add(casing.getId())) {
                    check(casing.caliber() == Caliber.L338, "De huls van een AWP is van " + casing.caliber());
                    casings.add(helper.getLevel().getGameTime());
                }
            }
        });
        int interval = RavenArms.interval(awp);
        helper.succeedWhen(() -> {
            check(shots.size() >= 2 && !casings.isEmpty(), "Nog maar " + shots.size() + " schoten en " + casings.size() + " hulzen");
            long bolt = casings.getFirst() - shots.getFirst();
            check(bolt >= 8 && bolt <= 12, "De huls sprong er " + bolt + " ticks na het schot uit, verwacht een halve seconde");
            check(shots.get(1) - shots.getFirst() >= interval - 1, "Het tweede schot kwam al na " + (shots.get(1) - shots.getFirst()) + " ticks");
        });
    }

    // ================================================================ De buit

    /**
     * In de rugzak van een gevallen soldaat zit munitie voor het wapen dat hij droeg: patronen van zijn kaliber, soms een
     * magazijn van zijn wapen (nooit bij de Remington 870), en een onderdeel is er een van zijn wapen. Een lasersoldaat
     * heeft een energiecel bij zich. Af en toe zit er een los attachment in ([raven] packAttachmentChance).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "raven_arms")
    public static void a_raven_pack_holds_ammo_for_the_gun_he_carried(GameTestHelper helper) {
        RandomSource random = RandomSource.create(8);
        float chance = ImtConfig.Raven.PACK_ATTACHMENT_CHANCE;
        try {
            for (FirearmType type : FirearmType.values()) {
                ItemStack weapon = FirearmItem.loaded(type, GunMaterial.STEEL);
                int magazines = 0;
                for (int i = 0; i < 200; i++) {
                    ImtConfig.Raven.PACK_ATTACHMENT_CHANCE = i % 2 == 0 ? 1.0f : 0.0f;
                    int rounds = 0;
                    int attachments = 0;
                    for (ItemStack stack : BackpackItem.contents(RavenPacks.filled(RavenPacks.pack(), random, weapon))) {
                        Caliber caliber = Caliber.ofRound(stack);
                        if (caliber != null) {
                            check(caliber == type.caliber, "In de rugzak van een soldaat met een " + type + " zit " + caliber);
                            rounds += stack.getCount();
                        }
                        if (stack.getItem() instanceof MagazineItem) {
                            check(MagazineItem.typeOf(stack) == type, "Een magazijn van een ander wapen in de rugzak van de " + type);
                            check(MagazineItem.rounds(stack) >= 1 && MagazineItem.rounds(stack) <= type.capacity(), "Een magazijn met "
                                    + MagazineItem.rounds(stack) + " patronen");
                            magazines++;
                        }
                        if (stack.getItem() instanceof GunPartItem) {
                            check(GunPartItem.typeOf(stack) == type, "Een onderdeel van een ander wapen in de rugzak van de " + type);
                        }
                        check(!(stack.getItem() instanceof EnergyCellItem), "Een energiecel bij een soldaat met een " + type);
                        if (stack.getItem() instanceof AttachmentItem) {
                            attachments++;
                        }
                    }
                    check(rounds > 0, "Geen patronen in de rugzak van een soldaat met een " + type);
                    check(attachments == (i % 2 == 0 ? 1 : 0), attachments + " attachments in de rugzak (kans " + ImtConfig.Raven.PACK_ATTACHMENT_CHANCE + ")");
                }
                check(type.tube() ? magazines == 0 : magazines > 30, magazines + " magazijnen in 200 rugzakken van soldaten met een " + type);
            }
            // Een lasersoldaat: een cel, geen patronen.
            ItemStack laser = RavenArms.weapon(RavenArms.Kind.LASER_RIFLE, random);
            List<ItemStack> contents = BackpackItem.contents(RavenPacks.filled(RavenPacks.pack(), random, laser));
            check(contents.stream().anyMatch(stack -> stack.getItem() instanceof EnergyCellItem), "Geen energiecel bij een lasersoldaat");
            check(contents.stream().noneMatch(stack -> Caliber.ofRound(stack) != null), "Patronen bij een lasersoldaat");
        } finally {
            ImtConfig.Raven.PACK_ATTACHMENT_CHANCE = chance;
        }
        helper.succeed();
    }

    /**
     * Een soldaat die valt, laat (als zijn wapen valt) het wapen achter met alles wat erop zat, en zijn rugzak met
     * munitie voor dat wapen: beide zitten in zijn lichaam.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "raven_arms_body")
    public static void a_fallen_soldier_leaves_his_gun_with_what_was_on_it(GameTestHelper helper) {
        RavenSoldierEntity soldier = GuardGameTests.soldier(helper, new BlockPos(6, 2, 6), 0, false);
        ItemStack ak = FirearmItem.loaded(FirearmType.AK_47, GunMaterial.STEEL);
        FirearmItem.setAttachment(ak, Attachment.VERTICAL_GRIP);
        FirearmItem.setAttachment(ak, Attachment.RED_DOT);
        soldier.arm(ak);
        soldier.setPack(RavenPacks.pack());
        soldier.setDropChance(EquipmentSlot.MAINHAND, 2.0f); // altijd (ook zonder een speler die hem raakte)
        soldier.kill();
        helper.succeedWhen(() -> {
            List<RavenBodyEntity> bodies = helper.getLevel().getEntitiesOfClass(RavenBodyEntity.class, helper.getBounds().inflate(2));
            check(!bodies.isEmpty(), "De soldaat liet geen lichaam achter");
            ItemStack gun = ItemStack.EMPTY;
            ItemStack pack = ItemStack.EMPTY;
            for (int i = 0; i < bodies.getFirst().loot().getContainerSize(); i++) {
                ItemStack stack = bodies.getFirst().loot().getItem(i);
                if (FirearmItem.typeOf(stack) == FirearmType.AK_47) {
                    gun = stack;
                } else if (BackpackItem.isBackpack(stack)) {
                    pack = stack;
                }
            }
            check(!gun.isEmpty(), "Zijn AK-47 zit niet in zijn lichaam");
            check(FirearmItem.attachment(gun, AttachmentSlot.UNDERBARREL) == Attachment.VERTICAL_GRIP
                    && FirearmItem.sight(gun) == Attachment.RED_DOT, "De attachments zitten niet meer op zijn AK-47");
            check(!pack.isEmpty(), "Zijn rugzak zit niet in zijn lichaam");
            // Ook al viel zijn wapen (zijn hand is dan leeg): de munitie in zijn rugzak is die van de AK-47.
            check(BackpackItem.contents(pack).stream().anyMatch(stack -> Caliber.ofRound(stack) == Caliber.R762),
                    "In zijn rugzak zit geen 7.62 voor zijn AK-47");
            bodies.getFirst().discard();
        });
    }

    /** Hoe vaak (van {@code rolls} worpen) geeft deze kist iets waar {@code wanted} ja op zegt? */
    private static double share(ServerLevel level, String table, int rolls, java.util.function.Predicate<ItemStack> wanted) {
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, table));
        LootTable loot = level.getServer().reloadableRegistries().getLootTable(key);
        check(loot != LootTable.EMPTY, "De loot table " + table + " bestaat niet (of is stuk)");
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        int hits = 0;
        for (long seed = 1; seed <= rolls; seed++) {
            if (loot.getRandomItems(params, seed).stream().anyMatch(wanted)) {
                hits++;
            }
        }
        return hits / (double) rolls;
    }

    private static void between(String what, double share, double low, double high) {
        check(share >= low && share <= high, what + ": " + Math.round(share * 100) + "% van de worpen, verwacht tussen "
                + Math.round(low * 100) + "% en " + Math.round(high * 100) + "%");
    }

    /**
     * Attachments in buit (jouw keuze): in de kisten van de bunker en de toren zit af en toe een attachment (in de kluizen
     * vaker, en daar ook de lange magazijnen), in de voorraadtonnen van de bunker munitie voor de nieuwe wapens, en een
     * soldaat die door een speler valt, laat er soms een vallen (door iets anders gedood: nooit).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "raven_arms")
    public static void attachments_turn_up_in_bunker_and_tower_loot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        java.util.function.Predicate<ItemStack> attachment = stack -> stack.getItem() instanceof AttachmentItem;
        java.util.function.Predicate<ItemStack> special = attachment.or(stack -> stack.getItem() instanceof MagazineItem);
        between("Een attachment in de slaapzaal van de bunker", share(level, "chests/raven_barracks", 600, attachment), 0.2, 0.4);
        between("Een attachment of lang magazijn in de kluis van de bunker", share(level, "chests/raven_vault", 600, special), 0.55, 0.8);
        between("Munitie in een voorraadton van de bunker", share(level, "chests/raven_supplies", 600, stack -> Caliber.ofRound(stack) != null), 0.55, 0.8);
        between("Een attachment in een kist van de toren", share(level, "chests/biebia_tower", 600, attachment), 0.2, 0.4);
        between("Een attachment in de kluis van de toren", share(level, "chests/biebia_vault", 600, attachment), 0.55, 0.8);
        // Een lang magazijn uit de kluis is leeg en van zijn eigen soort.
        check(share(level, "chests/raven_vault", 600, stack -> stack.getItem() instanceof MagazineItem
                && MagazineItem.extendedOf(stack) && MagazineItem.rounds(stack) == 0) > 0.05, "Geen lange magazijnen in de kluis van de bunker");

        // De soldaat zelf: alleen als een speler hem doodde.
        RavenSoldierEntity soldier = GuardGameTests.soldier(helper, new BlockPos(6, 2, 6), 0, false);
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 2, 6), "raven-loot-test");
        LootTable loot = level.getServer().reloadableRegistries().getLootTable(soldier.getLootTable());
        LootParams byPlayer = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, soldier)
                .withParameter(LootContextParams.ORIGIN, soldier.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, player)
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .create(LootContextParamSets.ENTITY);
        LootParams byFall = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, soldier)
                .withParameter(LootContextParams.ORIGIN, soldier.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().fall())
                .create(LootContextParamSets.ENTITY);
        int withPlayer = 0;
        int without = 0;
        int rolls = 3000;
        for (long seed = 1; seed <= rolls; seed++) {
            if (loot.getRandomItems(byPlayer, seed).stream().anyMatch(attachment)) {
                withPlayer++;
            }
            if (loot.getRandomItems(byFall, seed).stream().anyMatch(attachment)) {
                without++;
            }
        }
        between("Een attachment van een soldaat die door een speler viel", withPlayer / (double) rolls, 0.03, 0.07);
        check(without == 0, "Een soldaat die niet door een speler viel, liet " + without + " keer een attachment vallen");
        soldier.discard();
        player.discard();
        helper.succeed();
    }
}
