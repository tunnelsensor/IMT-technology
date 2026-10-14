package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.EmitterSavePayload;
import com.senne.starktech.network.RelayActionPayload;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.redstone.EdithRelays;
import com.senne.starktech.redstone.Relay;
import com.senne.starktech.redstone.RelayBlock;
import com.senne.starktech.redstone.RelayEmitterBlockEntity;
import com.senne.starktech.redstone.RelayEmitters;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlocks;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de relais van 13 en 14 oktober. Jouw wensen: "maak een block die relay emitter is zodat je ook
 * relays kunt triggeren door redstone", "relays renamen zonder bril, en een button om het off signaal te
 * inverten", en (14 oktober, in plaats van de tags van de 13e) "groepen in groepen: base > restricted >
 * security room; tags zijn volledig weg, je kunt enkel relays in groepen steken en groepen in groepen, en base
 * kan niet in temp als temp in base zit".
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class RelayGameTests {
    private RelayGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Zet een relais neer, meldt het aan, en geeft het een naam en een groep ("" = geen groep). */
    private static BlockPos relay(GameTestHelper helper, ServerPlayer owner, BlockPos pos, String name, String group) {
        helper.setBlock(pos, ModBlocks.RELAY.get());
        BlockPos abs = helper.absolutePos(pos);
        EdithRedstone.register(owner, abs);
        int index = owner.getData(ModAttachments.RELAYS).indexAt(helper.getLevel().dimension(), abs);
        check(index >= 0, "Het relais " + name + " kwam niet in de lijst");
        EdithRedstone.handleAction(owner, RelayActionPayload.rename(index, name, group));
        return pos;
    }

    private static boolean on(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(RelayBlock.POWERED);
    }

    private static int index(GameTestHelper helper, ServerPlayer owner, BlockPos pos) {
        return owner.getData(ModAttachments.RELAYS).indexAt(helper.getLevel().dimension(), helper.absolutePos(pos));
    }

    /**
     * Groepen in groepen (jouw voorbeeld): deur1 en deur2 zitten in "security room", die in "restricted", die in
     * "base". "base" schakelen zet de deuren om, de tuinlamp niet. Een groep zit in hooguit één groep (verhuizen
     * vervangt de oude plek), een relais ook. Rondjes worden geweigerd: "temp" in "base", en dan "base" in
     * "temp" kan niet. Een relais dat zelf "Base" heet, verstopt de groep niet voor de groepsknoppen.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "relays")
    public static void relay_groups_nest_and_switch(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 1));
        BlockPos door1 = relay(helper, owner, new BlockPos(3, 2, 3), "deur1", "security room");
        BlockPos door2 = relay(helper, owner, new BlockPos(5, 2, 3), "deur2", "Security Room");
        BlockPos lamp = relay(helper, owner, new BlockPos(7, 2, 3), "Base", "garden");
        EdithRelays data = owner.getData(ModAttachments.RELAYS);
        check(data.relays().get(index(helper, owner, door2)).group().equals("security room"),
                "deur2 kwam niet in de bestaande groep 'security room': " + data.relays().get(index(helper, owner, door2)).group());

        // base > restricted (nieuw, meteen in base) > security room (verhuist erin).
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_CREATE, "base", ""));
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_CREATE, "restricted", "base"));
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_PARENT, "security room", "restricted"));
        data = owner.getData(ModAttachments.RELAYS);
        check(data.parentOf("restricted").equals("base"), "'restricted' kwam niet in 'base'");
        check(data.pathOf("security room").equals(List.of("base", "restricted", "security room")),
                "De weg naar 'security room' klopt niet: " + data.pathOf("security room"));
        check(data.inGroup("base").size() == 2, "In 'base' zitten " + data.inGroup("base").size() + " relais in plaats van 2");

        // Rondjes kunnen niet (jouw voorbeeld: temp in base, dan base in temp), ook niet in zichzelf of via een omweg.
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_CREATE, "temp", "base"));
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_PARENT, "base", "temp"));
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_PARENT, "base", "base"));
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_PARENT, "base", "security room"));
        data = owner.getData(ModAttachments.RELAYS);
        check(data.parentOf("base").isEmpty(), "Een rondje van groepen werd toch aangenomen: 'base' zit in '" + data.parentOf("base") + "'");
        check(data.parentOf("temp").equals("base"), "'temp' zit niet meer in 'base'");

        // Eén plek: 'temp' verhuist naar 'restricted' en zit dan niet meer (direct) in 'base'.
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_PARENT, "temp", "restricted"));
        data = owner.getData(ModAttachments.RELAYS);
        check(data.parentOf("temp").equals("restricted") && !data.childrenOf("base").contains("temp"),
                "'temp' zit nu in '" + data.parentOf("temp") + "', of nog steeds direct in 'base'");
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_PARENT, "temp", ""));
        check(owner.getData(ModAttachments.RELAYS).parentOf("temp").isEmpty(), "'temp' kwam niet bovenaan");

        // De groepsknop schakelt de groep, ook al heet de lamp zelf "Base".
        EdithRedstone.handleAction(owner, RelayActionPayload.switchGroup("base", EdithRedstone.Op.ON));
        check(on(helper, door1) && on(helper, door2), "'base' zette de deuren in 'security room' niet aan");
        check(!on(helper, lamp), "De groep 'base' zette ook het relais aan dat zo heet");
        // Op naam (zoals je stem) wint de naam: dan gaat juist de lamp aan.
        EdithRedstone.handleAction(owner, RelayActionPayload.switchNamed("base", EdithRedstone.Op.ON));
        check(on(helper, lamp), "Het relais dat 'Base' heet, ging niet aan op zijn naam");

        // Een relais zit in één groep: deur2 naar 'garden' is weg uit 'base'.
        EdithRedstone.handleAction(owner, RelayActionPayload.rename(index(helper, owner, door2), "deur2", "garden"));
        data = owner.getData(ModAttachments.RELAYS);
        check(data.inGroup("base").size() == 1 && data.inGroup("garden").size() == 2, "deur2 zit nog in 'base', of niet in 'garden'");
        // En zonder groep.
        EdithRedstone.handleAction(owner, RelayActionPayload.rename(index(helper, owner, door2), "deur2", ""));
        check(!owner.getData(ModAttachments.RELAYS).relays().get(index(helper, owner, door2)).hasGroup(), "deur2 zit nog in een groep");

        // Een andere naam: wat erin zit gaat mee. Een naam die al bestaat, mag niet.
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_RENAME, "restricted", "garden"));
        check(owner.getData(ModAttachments.RELAYS).hasGroup("restricted"), "'restricted' kreeg een naam die al bestond");
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_RENAME, "restricted", "secure"));
        data = owner.getData(ModAttachments.RELAYS);
        check(!data.hasGroup("restricted") && data.parentOf("secure").equals("base") && data.parentOf("security room").equals("secure"),
                "Na het hernoemen klopt de boom niet: security room in '" + data.parentOf("security room") + "'");
        check(data.inGroup("base").size() == 1, "Na het hernoemen zit deur1 niet meer in 'base'");

        // Weggooien: wat erin zat, schuift een laag omhoog; de relais blijven.
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_DELETE, "secure", ""));
        data = owner.getData(ModAttachments.RELAYS);
        check(!data.hasGroup("secure") && data.parentOf("security room").equals("base"), "'security room' schoof niet omhoog naar 'base'");
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_DELETE, "security room", ""));
        data = owner.getData(ModAttachments.RELAYS);
        check(data.relays().size() == 3, "Bij het weggooien van een groep verdwenen relais");
        check(data.relays().get(index(helper, owner, door1)).group().equals("base"), "deur1 schoof niet omhoog naar 'base'");
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_DELETE, "base", ""));
        check(!owner.getData(ModAttachments.RELAYS).relays().get(index(helper, owner, door1)).hasGroup(),
                "Na het weggooien van 'base' zit deur1 nog in een groep");

        // Niet meer groepen dan de config toestaat.
        float max = StarkConfig.Relays.MAX_GROUPS;
        try {
            StarkConfig.Relays.MAX_GROUPS = owner.getData(ModAttachments.RELAYS).groups().size();
            EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_CREATE, "one too many", ""));
            check(!owner.getData(ModAttachments.RELAYS).hasGroup("one too many"), "Er kwam een groep bij boven het maximum");
        } finally {
            StarkConfig.Relays.MAX_GROUPS = max;
        }
        helper.succeed();
    }

    /**
     * Oude werelden: een relais met "group" (vóór 13 oktober) of met "tags" (13 oktober) krijgt één groep (de
     * oude standaardgroep of geen tags = geen groep), en "tag_parents" wordt "zit in" (de eerste tag erboven;
     * een rondje wordt gebroken). Opslaan en doorsturen naar je eigen spel geeft hetzelfde terug.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "relays")
    public static void relay_old_tags_become_groups(GameTestHelper helper) {
        Relay fresh = new Relay("Door", "x", Level.OVERWORLD, new BlockPos(1, 2, 3));
        CompoundTag old = (CompoundTag) Relay.CODEC.encodeStart(NbtOps.INSTANCE, fresh).getOrThrow();
        check(old.getString("in_group").equals("x"), "Een relais bewaart zijn groep niet als 'in_group'");
        old.remove("in_group");
        old.putString("group", "house");
        check(Relay.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow().group().equals("house"), "De oude groep 'house' bleef geen groep");
        for (String standard : List.of("general", "algemeen")) {
            old.putString("group", standard);
            check(!Relay.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow().hasGroup(),
                    "De oude standaardgroep '" + standard + "' werd een groep");
        }
        old.remove("group");
        ListTag tags = new ListTag();
        tags.add(net.minecraft.nbt.StringTag.valueOf("security room"));
        tags.add(net.minecraft.nbt.StringTag.valueOf("doors"));
        old.put("tags", tags);
        check(Relay.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow().group().equals("security room"),
                "De eerste tag werd niet de groep");
        old.put("tags", new ListTag());
        check(!Relay.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow().hasGroup(), "Een relais zonder tags kreeg een groep");

        // tag_parents van 13 oktober: de eerste tag erboven wordt de groep erboven; een rondje wordt gebroken.
        CompoundTag oldList = new CompoundTag();
        oldList.put("relays", new ListTag());
        CompoundTag parents = new CompoundTag();
        ListTag above = new ListTag();
        above.add(net.minecraft.nbt.StringTag.valueOf("base"));
        above.add(net.minecraft.nbt.StringTag.valueOf("world"));
        parents.put("security room", above);
        ListTag loopA = new ListTag();
        loopA.add(net.minecraft.nbt.StringTag.valueOf("b"));
        parents.put("a", loopA);
        ListTag loopB = new ListTag();
        loopB.add(net.minecraft.nbt.StringTag.valueOf("a"));
        parents.put("b", loopB);
        oldList.put("tag_parents", parents);
        EdithRelays loaded = EdithRelays.CODEC.codec().parse(NbtOps.INSTANCE, oldList).getOrThrow();
        check(loaded.parentOf("security room").equals("base"), "'security room' kwam niet in 'base' maar in '" + loaded.parentOf("security room") + "'");
        check(!loaded.isInside("a", "b") || !loaded.isInside("b", "a"), "Het oude rondje a in b in a werd niet gebroken");

        EdithRelays data = new EdithRelays(List.of(new Relay("deur1", "security room", Level.OVERWORLD,
                new BlockPos(4, 5, 6))), Map.of("security room", "restricted", "restricted", "base", "base", ""));
        var saved = EdithRelays.CODEC.codec().encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        check(EdithRelays.CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow().equals(data), "Opslaan en weer inladen gaf iets anders");
        ByteBuf buffer = Unpooled.buffer();
        EdithRelays.STREAM_CODEC.encode(buffer, data);
        check(EdithRelays.STREAM_CODEC.decode(buffer).equals(data), "Doorsturen naar je eigen spel gaf iets anders");
        helper.succeed();
    }

    /**
     * Zonder bril: naam en groep geven mag, omkeren en testen ook als je ernaast staat. Omgekeerd geeft het
     * relais redstone als het UIT staat. Van ver weg (zonder bril) mag omkeren niet; met bril wel. Een vreemde
     * die ernaast staat, kan het niet in zijn lijst zetten en dus ook niet omkeren.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "relays")
    public static void relay_rename_and_invert_without_glasses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 1));
        ServerPlayer stranger = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 1), "relay-intruder");
        stranger.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        ItemStack glasses = owner.getItemBySlot(EquipmentSlot.HEAD).copy();
        owner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        check(EdithServer.authorizedGlasses(owner) == null, "De testspeler heeft nog een bril op");
        BlockPos pos = relay(helper, owner, new BlockPos(3, 2, 3), "Front door", "house");
        BlockPos abs = helper.absolutePos(pos);

        helper.startSequence().thenIdle(2).thenExecute(() -> {
            Relay relay = owner.getData(ModAttachments.RELAYS).get(index(helper, owner, pos));
            check(relay != null && relay.name().equals("Front door") && relay.group().equals("house"),
                    "Zonder bril kreeg het relais geen naam en groep: " + relay);

            // Een vreemde (ook als hij het relais in zijn lijst probeert te zetten): niet omkeren.
            EdithRedstone.register(stranger, abs);
            check(stranger.getData(ModAttachments.RELAYS).indexAt(level.dimension(), abs) < 0,
                    "Een vreemde kon jouw relais in zijn lijst zetten");
            EdithRedstone.handleAction(stranger, new RelayActionPayload(RelayActionPayload.Op.INVERT, EdithRedstone.Op.TOGGLE,
                    stranger.getData(ModAttachments.RELAYS).indexAt(level.dimension(), abs), "", ""));
            check(!helper.getBlockState(pos).getValue(RelayBlock.INVERTED), "Een vreemde kon het relais in jouw basis omkeren");

            // Omkeren: uit = redstone.
            check(!level.hasNeighborSignal(abs.east()), "Een nieuw relais geeft al redstone");
            EdithRedstone.handleAction(owner, new RelayActionPayload(RelayActionPayload.Op.INVERT, EdithRedstone.Op.TOGGLE,
                    index(helper, owner, pos), "", ""));
            check(helper.getBlockState(pos).getValue(RelayBlock.INVERTED), "Het relais werd niet omgekeerd");
            check(level.hasNeighborSignal(abs.east()), "Omgekeerd en uit, maar het geeft geen redstone");
            // Testen zonder bril (je staat ernaast): aan = geen redstone meer.
            EdithRedstone.handleAction(owner, RelayActionPayload.switchOne(index(helper, owner, pos), EdithRedstone.Op.ON));
            check(on(helper, pos), "Zonder bril, naast het relais, ging het niet aan");
            check(!level.hasNeighborSignal(abs.east()), "Omgekeerd en aan, maar het geeft nog redstone");

            // Ver weg zonder bril: niet omkeren, niet schakelen.
            owner.moveTo(abs.getX() + 30.5, abs.getY(), abs.getZ() + 0.5);
            EdithRedstone.handleAction(owner, new RelayActionPayload(RelayActionPayload.Op.INVERT, EdithRedstone.Op.TOGGLE,
                    index(helper, owner, pos), "", ""));
            check(helper.getBlockState(pos).getValue(RelayBlock.INVERTED), "Van ver weg, zonder bril, kon je het omkeren");
            EdithRedstone.handleAction(owner, RelayActionPayload.switchOne(index(helper, owner, pos), EdithRedstone.Op.OFF));
            check(on(helper, pos), "Van ver weg, zonder bril, kon je het schakelen");
            // Met bril kan het van ver weg wel.
            owner.setItemSlot(EquipmentSlot.HEAD, glasses);
            EdithRedstone.handleAction(owner, new RelayActionPayload(RelayActionPayload.Op.INVERT, EdithRedstone.Op.TOGGLE,
                    index(helper, owner, pos), "", ""));
            check(!helper.getBlockState(pos).getValue(RelayBlock.INVERTED), "Met bril kon je het van ver weg niet terugzetten");
        }).thenSucceed();
    }

    /**
     * Alleen wie een relais neerzet, kan het bewerken (13 oktober: "zorg dat de relay die je zelf plaatst, jij
     * de enige bent die die kan bewerken"). Een ander (met bril) kan het niet openen, niet in zijn lijst zetten,
     * en als het er van vroeger nog in stond, niet schakelen, omkeren of hernoemen: dan gaat het eruit. Een
     * relais zonder eigenaar (van vóór 13 oktober) wordt van wie het als eerste gebruikt. En een emitter van
     * een ander schakelt het niet, ook niet uit zijn oude kopie.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "relays")
    public static void relay_only_owner_can_edit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 1));
        ServerPlayer stranger = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 2, 1), "relay-stranger");
        // Neergezet door de eigenaar, zoals met de muis.
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.RELAY.get());
        BlockPos abs = helper.absolutePos(pos);
        ModBlocks.RELAY.get().setPlacedBy(level, abs, helper.getBlockState(pos), owner, ItemStack.EMPTY);
        check(owner.getUUID().equals(EdithRedstone.relayOwner(level, abs)), "Wie het neerzette, is niet de eigenaar");
        check(index(helper, owner, pos) >= 0, "Het relais staat niet in de lijst van de eigenaar");

        // Een ander: rechtsklikken of aanmelden doet niets.
        EdithRedstone.openEditor(stranger, abs);
        EdithRedstone.register(stranger, abs);
        check(index(helper, stranger, pos) < 0, "Een ander kon het relais in zijn lijst zetten");

        // Stond het er van vroeger nog in: schakelen, omkeren, hernoemen en opruimen gooien het eruit.
        Relay stolen = new Relay("Loot", "loot", level.dimension(), abs);
        stranger.setData(ModAttachments.RELAYS, EdithRelays.EMPTY.with(stolen));
        EdithRedstone.handleAction(stranger, RelayActionPayload.switchNamed("loot", EdithRedstone.Op.ON));
        check(!on(helper, pos), "Een ander kon het relais schakelen");
        check(index(helper, stranger, pos) < 0, "Na schakelen stond het relais nog in de lijst van de ander");
        stranger.setData(ModAttachments.RELAYS, EdithRelays.EMPTY.with(stolen));
        EdithRedstone.handleAction(stranger, new RelayActionPayload(RelayActionPayload.Op.INVERT, EdithRedstone.Op.TOGGLE, 0, "", ""));
        check(!helper.getBlockState(pos).getValue(RelayBlock.INVERTED), "Een ander kon het relais omkeren");
        stranger.setData(ModAttachments.RELAYS, EdithRelays.EMPTY.with(stolen));
        EdithRedstone.handleAction(stranger, new RelayActionPayload(RelayActionPayload.Op.RENAME, EdithRedstone.Op.TOGGLE, 0, "Mine", ""));
        check(index(helper, stranger, pos) < 0, "Een ander kon het relais hernoemen");
        stranger.setData(ModAttachments.RELAYS, EdithRelays.EMPTY.with(stolen));
        EdithRedstone.pruneGhosts(stranger);
        check(index(helper, stranger, pos) < 0, "Opruimen liet het relais van een ander in de lijst");

        // De eigenaar kan alles nog.
        EdithRedstone.handleAction(owner, RelayActionPayload.switchOne(index(helper, owner, pos), EdithRedstone.Op.ON));
        check(on(helper, pos), "De eigenaar kon zijn eigen relais niet schakelen");

        // Een oud relais zonder eigenaar: wie het als eerste opent, is de eigenaar.
        BlockPos old = new BlockPos(7, 2, 3);
        helper.setBlock(old, ModBlocks.RELAY.get());
        BlockPos oldAbs = helper.absolutePos(old);
        check(EdithRedstone.relayOwner(level, oldAbs) == null, "Een relais zonder neerzetter heeft toch een eigenaar");
        EdithRedstone.openEditor(stranger, oldAbs);
        check(stranger.getUUID().equals(EdithRedstone.relayOwner(level, oldAbs)) && index(helper, stranger, old) >= 0,
                "Wie het oude relais als eerste opende, werd niet de eigenaar");
        EdithRedstone.openEditor(owner, oldAbs);
        check(index(helper, owner, old) < 0, "Daarna kon een ander het oude relais nog in zijn lijst zetten");

        // Een emitter van iemand anders (offline), met een oude kopie die naar dit relais wijst: niets.
        BlockPos emitterPos = new BlockPos(3, 2, 7);
        helper.setBlock(emitterPos, ModBlocks.RELAY_EMITTER.get());
        RelayEmitterBlockEntity emitter = (RelayEmitterBlockEntity) helper.getBlockEntity(emitterPos);
        CompoundTag saved = emitter.saveWithoutMetadata(level.registryAccess());
        saved.putUUID("Owner", UUID.randomUUID());
        saved.putString("Target", "Loot");
        saved.putString("Mode", RelayEmitterBlockEntity.Mode.FOLLOW.name());
        ListTag targets = new ListTag();
        CompoundTag target = new CompoundTag();
        target.putString("D", level.dimension().location().toString());
        target.putInt("X", abs.getX());
        target.putInt("Y", abs.getY());
        target.putInt("Z", abs.getZ());
        target.putInt("C", -1);
        targets.add(target);
        saved.put("Targets", targets);
        emitter.loadWithComponents(saved, level.registryAccess());
        check(emitter.targets().size() == 1, "De oude kopie van de emitter werd niet ingeladen");
        emitter.signalChanged(level, false);
        check(on(helper, pos), "De emitter van een ander zette het relais uit");
        helper.succeed();
    }

    /** De emitter in de wereld: een redstoneblok ernaast zet de relais in "base" aan, weghalen weer uit. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "relays")
    public static void relay_emitter_follows_redstone(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 2, 1));
        BlockPos door = relay(helper, owner, new BlockPos(2, 2, 3), "deur1", "security room");
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_CREATE, "base", ""));
        EdithRedstone.handleAction(owner, RelayActionPayload.group(RelayActionPayload.Op.GROUP_PARENT, "security room", "base"));
        BlockPos emitterPos = new BlockPos(7, 2, 3);
        helper.setBlock(emitterPos, ModBlocks.RELAY_EMITTER.get());
        RelayEmitterBlockEntity emitter = (RelayEmitterBlockEntity) helper.getBlockEntity(emitterPos);
        emitter.setOwner(owner);
        RelayEmitters.handleSave(owner, new EmitterSavePayload(helper.absolutePos(emitterPos), "base",
                RelayEmitterBlockEntity.Mode.FOLLOW.ordinal()));
        check(emitter.target().equals("base") && emitter.targets().size() == 1,
                "De emitter vond niet het ene relais in 'base' (" + emitter.targets().size() + ")");

        // Iemand anders mag hem niet omzetten.
        ServerPlayer stranger = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(7, 2, 5), "relay-stranger");
        RelayEmitters.handleSave(stranger, new EmitterSavePayload(helper.absolutePos(emitterPos), "garden", 0));
        check(emitter.target().equals("base"), "Een andere speler kon de emitter omzetten");

        helper.setBlock(emitterPos.east(), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenWaitUntil(() -> check(on(helper, door), "Redstone in de emitter, maar het relais ging niet aan"))
                .thenExecute(() -> helper.setBlock(emitterPos.east(), Blocks.AIR))
                .thenWaitUntil(() -> check(!on(helper, door), "Redstone weg, maar het relais bleef aan (Follow)"))
                .thenSucceed();
    }

    /**
     * Toggle en Pulse, en: de emitter werkt ook als zijn eigenaar offline is (hij onthoudt zelf waar de
     * relais staan).
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "relays")
    public static void relay_emitter_toggle_pulse_offline(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 2, 1));
        BlockPos door = relay(helper, owner, new BlockPos(2, 2, 3), "deur1", "base");
        BlockPos emitterPos = new BlockPos(7, 2, 3);
        helper.setBlock(emitterPos, ModBlocks.RELAY_EMITTER.get());
        RelayEmitterBlockEntity emitter = (RelayEmitterBlockEntity) helper.getBlockEntity(emitterPos);
        emitter.setOwner(owner);
        emitter.configure(owner, "deur1", RelayEmitterBlockEntity.Mode.TOGGLE);

        // Toggle: elke keer dat de redstone aangaat, omschakelen; uitgaan doet niets.
        emitter.signalChanged(level, true);
        check(on(helper, door), "Toggle: het relais ging niet aan");
        emitter.signalChanged(level, false);
        check(on(helper, door), "Toggle: redstone uit zette het relais ook uit");
        emitter.signalChanged(level, true);
        check(!on(helper, door), "Toggle: de tweede keer ging het relais niet uit");

        // Offline: de eigenaar verlaat de server (en zijn lijst is leeg), de emitter staat op Pulse. Hij weet
        // zelf nog waar het relais staat.
        CompoundTag saved = emitter.saveWithoutMetadata(level.registryAccess());
        saved.putString("Mode", RelayEmitterBlockEntity.Mode.PULSE.name());
        emitter.loadWithComponents(saved, level.registryAccess());
        owner.setData(ModAttachments.RELAYS, EdithRelays.EMPTY);
        level.getServer().getPlayerList().remove(owner);
        check(level.getServer().getPlayerList().getPlayer(owner.getUUID()) == null, "De eigenaar is nog online");
        emitter.signalChanged(level, true);
        check(on(helper, door), "Offline: de emitter gaf geen puls");
        helper.startSequence()
                .thenExecuteAfter(Math.round(StarkConfig.Relays.PULSE_TICKS) + 2,
                        () -> check(!on(helper, door), "De puls ging niet vanzelf weer uit"))
                .thenSucceed();
    }
}
