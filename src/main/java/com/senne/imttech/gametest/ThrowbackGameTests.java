package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.armory.Pegboard;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.robot.GuardRobotEntity;
import com.senne.imttech.robot.RobotParts;
import com.senne.imttech.weapons.GrenadeItem;
import com.senne.imttech.weapons.LiveCharge;
import com.senne.imttech.weapons.RemoteChargeBlockEntity;
import com.senne.imttech.weapons.RollingGrenade;
import com.senne.imttech.weapons.StickyGrenade;
import com.senne.imttech.weapons.TriggerData;
import com.senne.imttech.weapons.TriggerItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor granaten oprapen en teruggooien, en ladingen verplaatsen (hoofdstuk 69, 9 november). Jouw wens: "zorg dat
 * je granaten kunt oppakken en teruggooien/plaatsen, bijvoorbeeld iemand gooit een EMP maar jij gooit die terug, of er
 * hangt een remote frag op je base en jij hangt die gewoon ergens anders". Jouw keuzes: de lont loopt door; een sticky
 * mag je ook van een wezen trekken; rechtsklik erop, wat je ook vasthoudt (wat je vasthield valt op de grond, of gaat in
 * je holster of sling); een Remote EMP of Remote Frag haal je eraf met sneak + lege hand, en hij blijft van zijn
 * eigenaar en aan diens trigger.
 *
 * Een testspeler tikt niet vanzelf (zie ImtTechGameTests): waar het aftellen in je inventaris telt, doet de test dat
 * zelf (inventory().tick(), zoals het spel elke tick doet).
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class ThrowbackGameTests {
    private ThrowbackGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, BlockPos pos, String name) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, pos, name);
        player.getAbilities().instabuild = false; // anders mag de testspeler alles gratis (zoals in creative)
        player.getInventory().selected = 0;
        return player;
    }

    /** Een EMP-bal die op scherp staat, op deze plek (relatief), over {@code fuse} ticks. */
    private static RollingGrenade ball(GameTestHelper helper, Vec3 relative, int fuse) {
        RollingGrenade grenade = new RollingGrenade(ModEntities.ROLLING_EMP_GRENADE.get(), helper.getLevel());
        Vec3 at = helper.absoluteVec(relative);
        grenade.setPos(at.x, at.y, at.z);
        grenade.arm(fuse);
        helper.getLevel().addFreshEntity(grenade);
        return grenade;
    }

    private static boolean onTheGround(GameTestHelper helper, net.minecraft.world.item.Item item) {
        return !helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(3),
                entity -> entity.getItem().is(item)).isEmpty();
    }

    // ================================================================ Granaten

    /**
     * Een EMP-bal rolt naar je toe. Rechtsklik erop met een steen in je hand: de granaat zit in je hand, scherp, met de
     * rest van zijn lont (die begint niet opnieuw); de steen valt op de grond. Een scherpe granaat past niet in je pouch
     * of op een Pegboard, en kogels vliegen erlangs. Rechtsklik nog eens: hij vliegt terug, met wat er van zijn lont over
     * was.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "throwback")
    public static void picked_up_grenade_keeps_its_fuse_and_flies_back(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, new BlockPos(4, 2, 4), "grenade-catcher");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 5));
        RollingGrenade grenade = ball(helper, new Vec3(5.5, 2.0, 4.5), 60);
        check(grenade.isPickable() && !grenade.canBeHitByProjectile(),
                "Een scherpe granaat hoort aanklikbaar te zijn, maar kogels moeten erlangs vliegen");
        int[] left = new int[1];
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    left[0] = grenade.ticksLeft();
                    check(grenade.interact(player, InteractionHand.MAIN_HAND).consumesAction(), "Oprapen lukte niet");
                    check(grenade.isRemoved(), "De granaat ligt nog op de grond");
                    ItemStack held = player.getMainHandItem();
                    check(held.is(ModItems.EMP_GRENADE.get()) && GrenadeItem.isLive(held),
                            "Er zit geen scherpe EMP-granaat in je hand, maar " + held);
                    int inHand = GrenadeItem.ticksLeft(held, level);
                    check(Math.abs(inHand - left[0]) <= 1,
                            "De lont begon opnieuw: nog " + inHand + " ticks in plaats van " + left[0]);
                    check(onTheGround(helper, Items.STONE), "De steen viel niet op de grond");
                    check(!GearItem.Kind.GRENADE_POUCH.accepts(held) && !Pegboard.hangable(held),
                            "Een scherpe granaat past in de granaatpouch of op het Pegboard");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    int inHand = GrenadeItem.ticksLeft(player.getMainHandItem(), level);
                    ModItems.EMP_GRENADE.get().use(level, player, InteractionHand.MAIN_HAND);
                    check(player.getMainHandItem().isEmpty(), "De granaat bleef in je hand");
                    List<RollingGrenade> thrown = level.getEntitiesOfClass(RollingGrenade.class,
                            player.getBoundingBox().inflate(4));
                    check(thrown.size() == 1 && thrown.getFirst().getOwner() == player, "Er vloog geen granaat terug");
                    int flying = thrown.getFirst().ticksLeft();
                    check(flying <= inHand && flying >= inHand - 2,
                            "De teruggegooide granaat kreeg een nieuwe lont: " + flying + " ticks in plaats van " + inHand);
                })
                .thenSucceed();
    }

    /**
     * Jouw keuze: wat je vasthield valt op de grond, tenzij het in je holster of sling past. Met een Glock in je hand en
     * een lege holster om: de Glock gaat in de holster, de granaat in je hand.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "throwback")
    public static void picking_up_holsters_your_pistol(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, new BlockPos(4, 2, 4), "grenade-holsterer");
        player.setItemInHand(InteractionHand.MAIN_HAND, FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL));
        check(Gear.set(player, GearItem.Kind.HOLSTER, new ItemStack(ModItems.HOLSTER.get())), "De holster ging niet om");
        RollingGrenade grenade = ball(helper, new Vec3(5.5, 2.0, 4.5), 60);
        helper.runAfterDelay(2, () -> {
            check(grenade.interact(player, InteractionHand.MAIN_HAND).consumesAction(), "Oprapen lukte niet");
            check(GrenadeItem.isLive(player.getMainHandItem()), "De granaat zit niet in je hand");
            check(FirearmItem.typeOf(Gear.contents(player, GearItem.Kind.HOLSTER).getFirst()) == FirearmType.GLOCK_19,
                    "De Glock ging niet in de holster");
            check(!onTheGround(helper, ModItems.GLOCK_19.get()), "De Glock viel op de grond (hij paste in de holster)");
            helper.succeed();
        });
    }

    /**
     * Te lang vastgehouden: de granaat gaat bij jou af (de robot naast je ligt neer) en is weg uit je inventaris. Een
     * andere laat je vallen (Q): die ligt meteen weer als granaat op de grond, met de rest van zijn lont.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "throwback")
    public static void live_grenade_goes_off_in_your_hand_or_rolls_when_dropped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, new BlockPos(4, 2, 4), "grenade-holder");
        GuardRobotEntity robot = RobotGameTests.robot(helper, new BlockPos(6, 2, 4), RobotParts.DEFAULT);
        player.setItemInHand(InteractionHand.MAIN_HAND, GrenadeItem.live(new ItemStack(ModItems.EMP_GRENADE.get()), level, 8));
        // Het spel laat je inventaris elke tick tikken; een testspeler niet: dat doen we hier.
        helper.onEachTick(() -> player.getInventory().tick());
        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> check(!robot.isDown() && GrenadeItem.isLive(player.getMainHandItem()),
                        "De granaat ging te vroeg af"))
                .thenWaitUntil(() -> check(robot.isDown(), "De granaat in je hand ging niet af (de robot staat nog)"))
                .thenExecute(() -> {
                    check(player.getMainHandItem().isEmpty(), "De afgegane granaat zit nog in je hand");
                    ItemStack dropped = GrenadeItem.live(new ItemStack(ModItems.FRAG_GRENADE.get()), level, 40);
                    player.drop(dropped, false);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    check(!onTheGround(helper, ModItems.FRAG_GRENADE.get()), "De scherpe granaat ligt er als item");
                    List<RollingGrenade> balls = level.getEntitiesOfClass(RollingGrenade.class, helper.getBounds().inflate(3),
                            ball -> ball.kind() == RollingGrenade.Kind.FRAG);
                    check(balls.size() == 1 && balls.getFirst().ticksLeft() <= 38 && balls.getFirst().ticksLeft() > 30,
                            "Er rolt geen frag met de rest van zijn lont: " + balls.stream().map(RollingGrenade::ticksLeft).toList());
                    balls.getFirst().discard(); // niet laten ontploffen naast de robot
                })
                .thenSucceed();
    }

    /**
     * Jouw keuze: een sticky mag je ook van een wezen trekken. Een Sticky EMP plakt op een robot; rechtsklik erop: hij zit
     * in je hand (de robot staat nog). Gooi je hem tegen een muur, dan plakt hij daar en gaat hij af met de rest van zijn
     * lont. Een smokebom die al rookt, raap je niet op.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "throwback")
    public static void sticky_pulled_off_a_robot_and_thrown_at_a_wall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 2; y <= 6; y++) {
            for (int z = 1; z <= 9; z++) {
                helper.setBlock(new BlockPos(1, y, z), Blocks.STONE);
            }
        }
        ServerPlayer player = player(helper, new BlockPos(8, 2, 6), "sticky-puller");
        GuardRobotEntity robot = RobotGameTests.robot(helper, new BlockPos(10, 2, 6), RobotParts.DEFAULT);
        StickyGrenade sticky = new StickyGrenade(ModEntities.STICKY_EMP_GRENADE.get(), level);
        Vec3 start = helper.absoluteVec(new Vec3(14.5, 3.2, 6.5));
        sticky.setPos(start.x, start.y, start.z);
        sticky.shoot(-1, 0.05, 0, 1.0f, 0);
        level.addFreshEntity(sticky);
        RollingGrenade smoke = new RollingGrenade(ModEntities.SMOKE_GRENADE.get(), level);
        Vec3 smokeAt = helper.absoluteVec(new Vec3(6.5, 2.0, 9.5));
        smoke.setPos(smokeAt.x, smokeAt.y, smokeAt.z);
        smoke.arm(1);
        level.addFreshEntity(smoke);
        int[] left = new int[1];
        StickyGrenade[] thrown = new StickyGrenade[1];
        helper.startSequence()
                .thenWaitUntil(() -> check(sticky.host() == robot, "De sticky plakte niet op de robot"))
                .thenExecute(() -> {
                    left[0] = sticky.ticksLeft();
                    check(sticky.interact(player, InteractionHand.MAIN_HAND).consumesAction(), "Van de robot trekken lukte niet");
                    check(sticky.isRemoved() && player.getMainHandItem().is(ModItems.STICKY_EMP_GRENADE.get())
                            && GrenadeItem.isLive(player.getMainHandItem()), "De sticky zit niet scherp in je hand");
                    check(!robot.isDown(), "De robot ligt neer");
                    check(smoke.isSmoking() && !smoke.canBePickedUp(), "Een smokebom die rookt, kon je oprapen");
                    // Terug, naar de muur in het westen.
                    player.setYRot(90);
                    player.setXRot(0);
                    ModItems.STICKY_EMP_GRENADE.get().use(level, player, InteractionHand.MAIN_HAND);
                    List<StickyGrenade> flying = level.getEntitiesOfClass(StickyGrenade.class, player.getBoundingBox().inflate(4));
                    check(flying.size() == 1, "Er vloog geen sticky");
                    thrown[0] = flying.getFirst();
                    check(thrown[0].isArmed() && thrown[0].ticksLeft() <= left[0],
                            "De gegooide sticky kreeg een nieuwe lont: " + thrown[0].ticksLeft() + " in plaats van " + left[0]);
                })
                .thenWaitUntil(() -> check(thrown[0].isStuck() || thrown[0].isRemoved(), "De sticky plakte niet tegen de muur"))
                .thenExecute(() -> check(thrown[0].isRemoved() || thrown[0].host() == null, "De sticky plakte op een wezen"))
                .thenWaitUntil(() -> check(thrown[0].isRemoved(), "De sticky ging niet af"))
                .thenSucceed();
    }

    // ================================================================ Ladingen verplaatsen

    /**
     * De aanvaller (met een Frag Trigger) plakt een Remote Frag op een muur. De verdediger haalt hem eraf met sneak +
     * lege hand: hij is scherp, van de aanvaller en aan diens trigger. De verdediger hangt hem op een andere muur (met zijn
     * eigen Frag Trigger bij zich: daar gaat hij niet aan). De trigger van de aanvaller hoort waar hij nu hangt, en zijn
     * knop blaast de andere muur open; de eerste muur blijft heel.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "throwback")
    public static void moved_remote_frag_stays_on_its_owners_trigger(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RemoteFragGameTests.wall(helper, 6, Blocks.STONE);
        RemoteFragGameTests.wall(helper, 14, Blocks.STONE);
        ServerPlayer attacker = player(helper, new BlockPos(10, 2, 2), "frag-attacker");
        ServerPlayer defender = player(helper, new BlockPos(9, 2, 8), "frag-defender");
        ItemStack attackerTrigger = new ItemStack(ModItems.FRAG_TRIGGER.get());
        attacker.getInventory().setItem(8, attackerTrigger);
        ItemStack defenderTrigger = new ItemStack(ModItems.FRAG_TRIGGER.get());
        defender.getInventory().setItem(8, defenderTrigger);
        // De aanvaller plakt hem tegen de oostkant van de muur op x = 6.
        BlockPos support = new BlockPos(6, 4, 8);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMOTE_FRAG.get()));
        BlockPos supportAbs = helper.absolutePos(support);
        attacker.getMainHandItem().useOn(new UseOnContext(attacker, InteractionHand.MAIN_HAND, new BlockHitResult(
                Vec3.atCenterOf(supportAbs).relative(Direction.EAST, 0.5), Direction.EAST, supportAbs, false)));
        BlockPos first = support.east();
        TriggerData data = TriggerItem.data(attackerTrigger);
        check(helper.getBlockEntity(first) instanceof RemoteChargeBlockEntity planted && data != null
                && data.id().equals(planted.trigger()), "De Remote Frag hoort niet bij de trigger van de aanvaller");
        // De verdediger: sneak + rechtsklik met een lege hand (zoals het spel het doet, via useItemOn).
        defender.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        defender.setShiftKeyDown(true);
        BlockPos firstAbs = helper.absolutePos(first);
        defender.gameMode.useItemOn(defender, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(firstAbs), Direction.EAST, firstAbs, false));
        defender.setShiftKeyDown(false);
        helper.assertBlockNotPresent(ModBlocks.REMOTE_FRAG.get(), first);
        ItemStack carried = defender.getMainHandItem();
        LiveCharge live = carried.get(ModDataComponents.LIVE_CHARGE.get());
        check(carried.is(ModItems.REMOTE_FRAG.get()) && live != null && live.trigger().equals(data.id())
                        && live.owner().isPresent() && live.owner().get().equals(attacker.getUUID()),
                "De verdediger heeft geen scherpe Remote Frag van de aanvaller in zijn hand, maar " + carried);
        // Ophangen tegen de westkant van de muur op x = 14.
        BlockPos other = new BlockPos(14, 4, 8);
        BlockPos otherAbs = helper.absolutePos(other);
        carried.useOn(new UseOnContext(defender, InteractionHand.MAIN_HAND, new BlockHitResult(
                Vec3.atCenterOf(otherAbs).relative(Direction.WEST, 0.5), Direction.WEST, otherAbs, false)));
        BlockPos second = other.west();
        check(helper.getBlockEntity(second) instanceof RemoteChargeBlockEntity hung && data.id().equals(hung.trigger())
                        && attacker.getUUID().equals(hung.owner()),
                "De opgehangen Remote Frag hoort niet meer bij de aanvaller");
        check(TriggerItem.data(defenderTrigger) == null || TriggerItem.data(defenderTrigger).charges().isEmpty(),
                "De Remote Frag ging aan de trigger van de verdediger");
        // De trigger van de aanvaller hoort het (zoals elke tick in zijn inventaris).
        TriggerItem.catchUp(level, attackerTrigger);
        List<GlobalPos> charges = TriggerItem.data(attackerTrigger).charges();
        check(charges.size() == 1 && charges.getFirst().pos().equals(helper.absolutePos(second)),
                "De trigger weet niet waar de Remote Frag nu hangt: " + charges);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, attackerTrigger);
        check(TriggerItem.fire(level, attacker, attackerTrigger).fired() == 1, "De knop liet de verplaatste Remote Frag niet afgaan");
        helper.runAfterDelay(6, () -> {
            helper.assertBlockNotPresent(ModBlocks.REMOTE_FRAG.get(), second);
            helper.assertBlockNotPresent(Blocks.STONE, other);
            helper.assertBlockPresent(Blocks.STONE, support);
            helper.succeed();
        });
    }

    /**
     * Jouw keuze: draag je een scherpe lading nog als de eigenaar drukt (en ben je in bereik), dan gaat hij bij jou af.
     * Een Remote EMP in de rugzak van de verdediger: de robot naast hem ligt neer, en de lading is weg.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "throwback")
    public static void carried_remote_emp_goes_off_in_your_pocket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RemoteFragGameTests.wall(helper, 6, Blocks.STONE);
        ServerPlayer attacker = player(helper, new BlockPos(14, 2, 2), "emp-attacker");
        ServerPlayer defender = player(helper, new BlockPos(9, 2, 8), "emp-defender");
        GuardRobotEntity robot = RobotGameTests.robot(helper, new BlockPos(11, 2, 8), RobotParts.DEFAULT);
        ItemStack trigger = new ItemStack(ModItems.EMP_TRIGGER.get());
        attacker.getInventory().setItem(8, trigger);
        BlockPos support = new BlockPos(6, 4, 8);
        BlockPos supportAbs = helper.absolutePos(support);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMOTE_EMP.get()));
        attacker.getMainHandItem().useOn(new UseOnContext(attacker, InteractionHand.MAIN_HAND, new BlockHitResult(
                Vec3.atCenterOf(supportAbs).relative(Direction.EAST, 0.5), Direction.EAST, supportAbs, false)));
        BlockPos stuck = support.east();
        check(helper.getBlockEntity(stuck) instanceof RemoteChargeBlockEntity, "De Remote EMP plakte niet");
        defender.setShiftKeyDown(true);
        BlockPos stuckAbs = helper.absolutePos(stuck);
        defender.gameMode.useItemOn(defender, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(stuckAbs), Direction.EAST, stuckAbs, false));
        defender.setShiftKeyDown(false);
        // In zijn rugzak stoppen.
        ItemStack carried = defender.getMainHandItem();
        check(carried.has(ModDataComponents.LIVE_CHARGE.get()), "De Remote EMP is niet scherp van de muur gekomen");
        defender.getInventory().setItem(20, carried);
        defender.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(!robot.isDown(), "De robot lag al neer");
        attacker.setItemInHand(InteractionHand.MAIN_HAND, trigger);
        check(TriggerItem.fire(level, attacker, trigger).fired() == 1, "De knop liet de gedragen Remote EMP niet afgaan");
        check(defender.getInventory().getItem(20).isEmpty(), "De Remote EMP zit nog in de rugzak");
        helper.runAfterDelay(2, () -> {
            check(robot.isDown(), "De EMP ging niet bij de verdediger af (de robot naast hem staat nog)");
            helper.succeed();
        });
    }
}
