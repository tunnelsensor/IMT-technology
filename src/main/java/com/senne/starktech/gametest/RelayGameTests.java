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
 * Tests voor de relais van 13 oktober. Jouw wensen: "maak een block die relay emitter is zodat je ook relays
 * kunt triggeren door redstone", "relays renamen zonder bril, en een button om het off signaal te inverten",
 * en "relaygroups worden tags zodat je groepen in groepen hebt: deur1 en deur2 zitten in security room en
 * die zit in base".
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

    /** Zet een relais neer, meldt het aan, en geeft het een naam en tags ("a, b"). */
    private static BlockPos relay(GameTestHelper helper, ServerPlayer owner, BlockPos pos, String name, String tags) {
        helper.setBlock(pos, ModBlocks.RELAY.get());
        BlockPos abs = helper.absolutePos(pos);
        EdithRedstone.register(owner, abs);
        int index = owner.getData(ModAttachments.RELAYS).indexAt(helper.getLevel().dimension(), abs);
        check(index >= 0, "Het relais " + name + " kwam niet in de lijst");
        EdithRedstone.handleAction(owner, new RelayActionPayload(RelayActionPayload.Op.RENAME, EdithRedstone.Op.TOGGLE,
                index, name, tags));
        return pos;
    }

    private static boolean on(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(RelayBlock.POWERED);
    }

    private static int index(GameTestHelper helper, ServerPlayer owner, BlockPos pos) {
        return owner.getData(ModAttachments.RELAYS).indexAt(helper.getLevel().dimension(), helper.absolutePos(pos));
    }

    /**
     * Tags in tags: deur1 en deur2 hebben de tag "security room", die zit in "base". "base" schakelen zet
     * de deuren om, de tuinlamp niet. Een rondje (base in security room) wordt geweigerd. Een relais dat
     * zelf "Base" heet, verstopt de tag niet voor de tag-knoppen. Een tag weggooien laat de relais staan.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "relays")
    public static void relay_tags_nest_and_switch(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 1));
        BlockPos door1 = relay(helper, owner, new BlockPos(3, 2, 3), "deur1", "security room");
        BlockPos door2 = relay(helper, owner, new BlockPos(5, 2, 3), "deur2", "Security Room, doors");
        BlockPos lamp = relay(helper, owner, new BlockPos(7, 2, 3), "Base", "garden");
        EdithRelays data = owner.getData(ModAttachments.RELAYS);
        check(data.relays().get(index(helper, owner, door2)).tags().equals(List.of("Security Room", "doors")),
                "deur2 kreeg niet de tags 'Security Room' en 'doors': " + data.relays().get(index(helper, owner, door2)).tags());

        EdithRedstone.handleAction(owner, RelayActionPayload.tag(RelayActionPayload.Op.TAG_CREATE, "base", ""));
        EdithRedstone.handleAction(owner, RelayActionPayload.tag(RelayActionPayload.Op.TAG_PARENT, "security room", "base"));
        data = owner.getData(ModAttachments.RELAYS);
        check(data.isInside("security room", "base"), "'security room' zit niet in 'base'");
        check(data.inTag("base").size() == 2, "In 'base' zitten " + data.inTag("base").size() + " relais in plaats van 2");

        // Rondjes kunnen niet.
        EdithRedstone.handleAction(owner, RelayActionPayload.tag(RelayActionPayload.Op.TAG_PARENT, "base", "security room"));
        EdithRedstone.handleAction(owner, RelayActionPayload.tag(RelayActionPayload.Op.TAG_PARENT, "base", "base"));
        check(owner.getData(ModAttachments.RELAYS).parentsOf("base").isEmpty(), "Een rondje van tags werd toch aangenomen");

        // De tag-knop schakelt de tag, ook al heet de lamp zelf "Base".
        EdithRedstone.handleAction(owner, RelayActionPayload.switchTag("base", EdithRedstone.Op.ON));
        check(on(helper, door1) && on(helper, door2), "'base' zette de deuren in 'security room' niet aan");
        check(!on(helper, lamp), "De tag 'base' zette ook het relais aan dat zo heet");
        // Op naam (zoals je stem) wint de naam: dan gaat juist de lamp aan.
        EdithRedstone.handleAction(owner, RelayActionPayload.switchNamed("base", EdithRedstone.Op.ON));
        check(on(helper, lamp), "Het relais dat 'Base' heet, ging niet aan op zijn naam");
        // Een tag van een tag van een tag: alles eronder gaat mee.
        EdithRedstone.handleAction(owner, RelayActionPayload.tag(RelayActionPayload.Op.TAG_CREATE, "world", ""));
        EdithRedstone.handleAction(owner, RelayActionPayload.tag(RelayActionPayload.Op.TAG_PARENT, "base", "world"));
        EdithRedstone.handleAction(owner, RelayActionPayload.tag(RelayActionPayload.Op.TAG_PARENT, "garden", "world"));
        EdithRedstone.handleAction(owner, RelayActionPayload.switchNamed("world", EdithRedstone.Op.OFF));
        check(!on(helper, door1) && !on(helper, door2) && !on(helper, lamp), "'world' zette niet alles eronder uit");

        // Weggooien: de relais blijven, de tag is overal weg.
        EdithRedstone.handleAction(owner, RelayActionPayload.tag(RelayActionPayload.Op.TAG_DELETE, "security room", ""));
        data = owner.getData(ModAttachments.RELAYS);
        check(data.relays().size() == 3, "Bij het weggooien van een tag verdwenen relais");
        check(!data.hasTag("security room"), "De tag 'security room' bestaat nog");
        check(data.inTag("base").isEmpty(), "Na het weggooien zit er nog iets in 'base'");
        check(data.relays().get(index(helper, owner, door2)).tags().equals(List.of("doors")), "deur2 verloor ook zijn andere tag");
        helper.succeed();
    }

    /**
     * Opslaan: een relais uit een oude wereld (met "group") krijgt die groep als tag, behalve de oude
     * standaardgroep. De lijst met tags-in-tags overleeft opslaan en doorsturen naar je eigen spel.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "relays")
    public static void relay_old_groups_become_tags(GameTestHelper helper) {
        Relay fresh = new Relay("Door", List.of("x"), Level.OVERWORLD, new BlockPos(1, 2, 3));
        CompoundTag old = (CompoundTag) Relay.CODEC.encodeStart(NbtOps.INSTANCE, fresh).getOrThrow();
        old.remove("tags");
        old.putString("group", "house");
        check(Relay.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow().tags().equals(List.of("house")),
                "De oude groep 'house' werd geen tag");
        for (String standard : List.of("general", "algemeen")) {
            old.putString("group", standard);
            check(Relay.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow().tags().isEmpty(),
                    "De oude standaardgroep '" + standard + "' werd een tag");
        }

        EdithRelays data = new EdithRelays(List.of(new Relay("deur1", List.of("security room", "doors"), Level.OVERWORLD,
                new BlockPos(4, 5, 6))), Map.of("security room", List.of("base")));
        var saved = EdithRelays.CODEC.codec().encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        check(EdithRelays.CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow().equals(data), "Opslaan en weer inladen gaf iets anders");
        ByteBuf buffer = Unpooled.buffer();
        EdithRelays.STREAM_CODEC.encode(buffer, data);
        check(EdithRelays.STREAM_CODEC.decode(buffer).equals(data), "Doorsturen naar je eigen spel gaf iets anders");
        helper.succeed();
    }

    /**
     * Zonder bril: naam en tags geven mag, omkeren en testen ook als je ernaast staat. Omgekeerd geeft het
     * relais redstone als het UIT staat. Van ver weg (zonder bril) mag omkeren niet; met bril wel. En in de
     * basis van een ander (het gebied van zijn kern) mag een vreemde het niet omkeren.
     *
     * De test zet zijn eigen kern neer (van de eigenaar): de testgebieden van eerdere tests blijven staan, en
     * een kern daarin (bereik 200 blokken) zou de eigenaar anders een indringer maken. Een nieuwe kern meldt
     * zich pas een tick later aan, dus eerst even wachten.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "relays")
    public static void relay_rename_and_invert_without_glasses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 1));
        StarkTechGameTests.placeCore(helper, new BlockPos(9, 2, 9), owner);
        ServerPlayer stranger = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 1), "relay-intruder");
        stranger.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        ItemStack glasses = owner.getItemBySlot(EquipmentSlot.HEAD).copy();
        owner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        check(EdithServer.authorizedGlasses(owner) == null, "De testspeler heeft nog een bril op");
        BlockPos pos = relay(helper, owner, new BlockPos(3, 2, 3), "Front door", "house, doors");
        BlockPos abs = helper.absolutePos(pos);

        helper.startSequence().thenIdle(2).thenExecute(() -> {
            Relay relay = owner.getData(ModAttachments.RELAYS).get(index(helper, owner, pos));
            check(relay != null && relay.name().equals("Front door") && relay.tags().equals(List.of("house", "doors")),
                    "Zonder bril kreeg het relais geen naam en tags: " + relay);

            // Een vreemde in jouw basis (ook als hij het relais in zijn lijst zet): niet omkeren.
            EdithRedstone.register(stranger, abs);
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

    /** De emitter in de wereld: een redstoneblok ernaast zet de relais in "base" aan, weghalen weer uit. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "relays")
    public static void relay_emitter_follows_redstone(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 2, 1));
        BlockPos door = relay(helper, owner, new BlockPos(2, 2, 3), "deur1", "security room");
        EdithRedstone.handleAction(owner, RelayActionPayload.tag(RelayActionPayload.Op.TAG_CREATE, "base", ""));
        EdithRedstone.handleAction(owner, RelayActionPayload.tag(RelayActionPayload.Op.TAG_PARENT, "security room", "base"));
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

        // Offline: dezelfde emitter, maar van iemand die er niet is, op Pulse. De eigenaar is zijn relais
        // intussen "vergeten": de emitter weet nog waar het staat.
        CompoundTag saved = emitter.saveWithoutMetadata(level.registryAccess());
        saved.putUUID("Owner", UUID.randomUUID());
        saved.putString("Mode", RelayEmitterBlockEntity.Mode.PULSE.name());
        emitter.loadWithComponents(saved, level.registryAccess());
        owner.setData(ModAttachments.RELAYS, EdithRelays.EMPTY);
        emitter.signalChanged(level, true);
        check(on(helper, door), "Offline: de emitter gaf geen puls");
        helper.startSequence()
                .thenExecuteAfter(Math.round(StarkConfig.Relays.PULSE_TICKS) + 2,
                        () -> check(!on(helper, door), "De puls ging niet vanzelf weer uit"))
                .thenSucceed();
    }
}
