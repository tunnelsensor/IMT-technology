package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.logic.Datacenters;
import com.senne.starktech.logic.LogicStore;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.security.PackedCore;
import com.senne.starktech.security.PackedCores;
import com.senne.starktech.security.SecurityCoreBlock;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.net.StarkNetworkData;
import com.senne.starktech.security.os.VirtualFs;
import com.senne.starktech.security.zone.ZoneBox;
import com.senne.starktech.security.zone.ZoneData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Tests voor een beveiligingskern die je weghakt (jouw wens, 9 oktober: "zorg dat al de config
 * bewaard wordt in de security block, zodat als je die hakt niet alles weg is").
 *
 * De kernen staan 210 blokken boven de testvloer, net als in security_core_placed_by_a_player, en
 * worden neergezet met het bereik even op 30: een kern neerzetten mag niet te dicht bij een ander
 * netwerk, en de kernen van andere tests staan onderaan. Elke test staat in zijn eigen batch (dan
 * lopen ze niet tegelijk, en merkt niemand het kleinere bereik) en ruimt zijn kern op.
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class CoreConfigGameTests {
    private CoreConfigGameTests() {}

    private static final String ARENA = "empty";
    private static final BlockPos CORE = new BlockPos(8, 210, 8);

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een kern op {@code pos}, van {@code owner} (of van niemand, zoals de Stark Tower), met een systeem. */
    private static SecurityCoreBlockEntity core(GameTestHelper helper, BlockPos pos, @Nullable ServerPlayer owner) {
        helper.setBlock(pos, ModBlocks.SECURITY_CORE.get());
        SecurityCoreBlockEntity core = (SecurityCoreBlockEntity) helper.getBlockEntity(pos);
        if (owner != null) {
            core.setOwner(owner);
        }
        core.ensureSystem(helper.getLevel());
        return core;
    }

    /**
     * Een speler in survival. Een testspeler komt binnen in creative (dan valt er bij hakken niets uit,
     * en blijft wat je neerzet in je hand), dus zetten we hem echt op survival.
     */
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 3, 5), name);
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        return player;
    }

    /** De speler hakt de kern weg met een diamanten houweel. Geeft terug wat er op de grond viel. */
    private static ItemStack mine(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        check(player.gameMode.destroyBlock(helper.absolutePos(pos)), "Weghakken lukte niet");
        return pickUp(helper, pos);
    }

    /** Raapt de kern op die rond {@code pos} op de grond ligt (en haalt hem uit de wereld). */
    private static ItemStack pickUp(GameTestHelper helper, BlockPos pos) {
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(2), drop -> drop.getItem().is(ModItems.SECURITY_CORE.get()));
        check(drops.size() == 1, "Er viel geen kern uit (of meer dan één): " + drops.size());
        ItemStack stack = drops.getFirst().getItem().copy();
        drops.forEach(Entity::discard);
        return stack;
    }

    /**
     * Zet de kern neer zoals een rechtsklik dat doet, met het bereik even op 30 (zie
     * StarkTechGameTests.withReach: anders staat er altijd wel een kern van een andere test te dichtbij).
     * Geeft terug of het lukte.
     */
    private static boolean place(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockItem item = (BlockItem) ModItems.SECURITY_CORE.get();
        return StarkTechGameTests.withReach(30, () -> item.place(new BlockPlaceContext(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, stack, new BlockHitResult(helper.absoluteVec(Vec3.atBottomCenterOf(pos)),
                        Direction.UP, helper.absolutePos(pos), false))).consumesAction());
    }

    /** Klaar: de kern weg (dan is zijn IP, zone en zo ook weer vrij), en geslaagd. */
    private static void finish(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.AIR);
        helper.succeed();
    }

    /**
     * De eigenaar hakt zijn kern weg en zet hem terug: alles is er weer. Het wachtwoord, de naam, een
     * notitie, het logboek, een vertrouwde speler, "attack players", een keycard, het IP (de keycard
     * hoort daarbij), de zone en een backup die op dit datacenter stond. Zolang de kern als item
     * rondgaat, staat dat allemaal niet meer in de wereld (geen zone, IP vrij), maar wel op de server.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "core_config_mine")
    public static void mined_core_keeps_all_its_settings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper, "test-core-owner");
        SecurityCoreBlockEntity core = core(helper, CORE, owner);
        core.setPassword("abc123", level.random);
        core.setHostname("vault");
        check(core.fs().write("/notes.txt", "the cake is a lie") == null, "Een notitie schrijven lukte niet");
        UUID pepper = UUID.randomUUID();
        core.addTrusted(pepper, "Pepper");
        core.setAttackPlayers(true);
        core.setLasersEnabled(false);
        LogicStore.Card card = core.logic().issue("Front door", 3, "test-core-owner");
        check(card != null, "Een keycard maken lukte niet");
        List<ZoneBox> zone = List.of(ZoneBox.of(helper.absolutePos(CORE.offset(-4, -2, -4)), helper.absolutePos(CORE.offset(4, 3, 4))));
        check(core.setZoneBoxes(zone).equals(zone), "De zone paste niet");
        GlobalPos here = GlobalPos.of(level.dimension(), helper.absolutePos(CORE));
        GlobalPos elsewhere = GlobalPos.of(level.dimension(), helper.absolutePos(CORE).offset(500, 0, 0));
        Datacenters datacenters = Datacenters.get(level);
        CompoundTag backupData = new CompoundTag();
        backupData.put("Logic", new LogicStore().save());
        datacenters.add(here, elsewhere, "10.99.99.99", "other-dc", owner.getUUID(), level.getGameTime(), 1, false,
                backupData, StarkConfig.Logic.BACKUPS_KEPT);
        String ip = core.ip();

        ItemStack stack = mine(helper, owner, CORE);
        PackedCore packed = stack.get(ModDataComponents.PACKED_CORE.get());
        check(packed != null, "De weggehakte kern heeft geen etiket: zijn instellingen zijn weg");
        check(packed.owner().equals("test-core-owner") && packed.host().equals("vault") && packed.ip().equals(ip),
                "Het etiket klopt niet: " + packed);
        check(PackedCores.get(level).has(packed.id()), "De server bewaart de instellingen niet");
        check(ZoneData.get(level).zone(here) == null, "De zone bleef staan zonder kern");
        check(StarkNetworkData.get(level).find(ip) == null, "Het IP bleef bezet zonder kern");
        check(datacenters.backupsOf(owner.getUUID()).isEmpty(), "De backup bleef op een datacenter dat er niet meer is");

        check(place(helper, owner, stack, CORE), "Terugzetten lukte niet");
        check(stack.isEmpty(), "De kern bleef in je hand");
        check(!PackedCores.get(level).has(packed.id()), "De instellingen staan nog op de server: twee keer neerzetten zou kunnen");
        SecurityCoreBlockEntity back = (SecurityCoreBlockEntity) helper.getBlockEntity(CORE);
        helper.assertBlockProperty(CORE, SecurityCoreBlock.CLAIMED, true);
        check(owner.getUUID().equals(back.ownerId()), "De kern is niet meer van de eigenaar");
        check(back.hostname().equals("vault") && back.ip().equals(ip), "Naam of IP veranderd: " + back.hostname() + " " + back.ip());
        check(back.checkPassword("abc123") && !back.checkPassword("abc124"), "Het wachtwoord is weg");
        VirtualFs.Node note = back.fs().get("/notes.txt");
        check(note != null && note.content().equals("the cake is a lie"), "De notitie is weg");
        check(back.log().entries().stream().anyMatch(entry -> entry.text().contains("re-installed by test-core-owner")),
                "Het logboek zegt niet dat de kern opnieuw geïnstalleerd is");
        check(back.isTrusted(pepper) && back.attackPlayers() && !back.lasersEnabled(), "De toegang is weg");
        check(back.logic().card(card.number()) != null, "De keycard is weg");
        check(back.zoneBoxes().equals(zone), "De zone is weg: " + back.zoneBoxes());
        List<Datacenters.Backup> backups = datacenters.backupsOf(owner.getUUID());
        check(backups.size() == 1 && backups.getFirst().target().equals(here) && backups.getFirst().sourceHost().equals("other-dc"),
                "De backup staat niet terug op dit datacenter: " + backups);
        back.setZoneBoxes(List.of());
        finish(helper, CORE);
    }

    /**
     * Een dief hakt je kern weg en zet hem bij zich neer: hij blijft van jou, met jouw wachtwoord
     * (de dief moet hem nog steeds hacken), en in het logboek staat wie hem waar neerzette.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "core_config_steal")
    public static void stolen_core_still_belongs_to_its_owner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper, "test-core-owner");
        ServerPlayer thief = player(helper, "test-core-thief");
        SecurityCoreBlockEntity core = core(helper, CORE, owner);
        core.setPassword("tony42", level.random);

        ItemStack stack = mine(helper, thief, CORE);
        check(stack.has(ModDataComponents.PACKED_CORE.get()), "Een gestolen kern viel leeg uit");
        BlockPos away = CORE.offset(0, 0, 12);
        check(place(helper, thief, stack, away), "Neerzetten lukte niet");
        SecurityCoreBlockEntity stolen = (SecurityCoreBlockEntity) helper.getBlockEntity(away);
        check(owner.getUUID().equals(stolen.ownerId()) && !thief.getUUID().equals(stolen.ownerId()),
                "De dief kreeg de kern door hem weg te hakken en neer te zetten");
        helper.assertBlockProperty(away, SecurityCoreBlock.CLAIMED, true);
        check(stolen.checkPassword("tony42"), "Het wachtwoord van de eigenaar ging niet mee");
        check(stolen.log().entries().stream().anyMatch(entry -> entry.text().contains("re-installed by test-core-thief")),
                "Het logboek zegt niet wie de kern neerzette");
        finish(helper, away);
    }

    /**
     * Opgeblazen (C4 breekt de kern zoals {@code destroyBlock}: eerst valt het item, dan is de kern
     * weg, andersom dan bij hakken): ook dan gaan de zone en de rest mee. Een kern van niemand (de
     * Stark Tower van Tony, nog niet gehackt) valt als een nieuwe kern, zoals altijd: wie die neerzet,
     * is de eigenaar.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "core_config_blast")
    public static void blown_up_core_keeps_its_zone_and_tower_core_drops_new(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper, "test-core-owner");
        SecurityCoreBlockEntity core = core(helper, CORE, owner);
        List<ZoneBox> zone = List.of(ZoneBox.of(helper.absolutePos(CORE.offset(-2, -1, -2)), helper.absolutePos(CORE.offset(2, 2, 2))));
        core.setZoneBoxes(zone);
        level.destroyBlock(helper.absolutePos(CORE), true);
        PackedCore packed = pickUp(helper, CORE).get(ModDataComponents.PACKED_CORE.get());
        check(packed != null, "Een opgeblazen kern viel leeg uit");
        CompoundTag config = PackedCores.get(level).take(packed.id());
        check(config != null && config.getCompound("World").getList("Zone", net.minecraft.nbt.Tag.TAG_INT_ARRAY).size() == 1,
                "De zone ging niet mee met een opgeblazen kern");

        core(helper, CORE, null);
        ItemStack tower = mine(helper, owner, CORE);
        check(!tower.has(ModDataComponents.PACKED_CORE.get()), "Een kern van niemand viel uit met een etiket");
        check(place(helper, owner, tower, CORE), "Neerzetten lukte niet");
        SecurityCoreBlockEntity mine = (SecurityCoreBlockEntity) helper.getBlockEntity(CORE);
        check(owner.getUUID().equals(mine.ownerId()) && mine.hostname().equals("test-core-owner-sec"),
                "Een nieuwe kern is niet van wie hem neerzet: " + mine.ownerName() + " " + mine.hostname());
        finish(helper, CORE);
    }

    /**
     * In creative valt er normaal niets uit een blok, maar een kern met instellingen wel (net als een
     * volle shulkerdoos): anders was alles met één klik weg. Precies één, met etiket.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "core_config_creative")
    public static void creative_break_still_drops_a_packed_core(GameTestHelper helper) {
        ServerPlayer builder = helper.makeMockServerPlayerInLevel();
        builder.setGameMode(GameType.CREATIVE);
        core(helper, CORE, builder);
        check(builder.gameMode.destroyBlock(helper.absolutePos(CORE)), "Breken in creative lukte niet");
        helper.assertBlockNotPresent(ModBlocks.SECURITY_CORE.get(), CORE);
        check(pickUp(helper, CORE).has(ModDataComponents.PACKED_CORE.get()), "De kern uit creative heeft geen etiket");
        helper.succeed();
    }
}
