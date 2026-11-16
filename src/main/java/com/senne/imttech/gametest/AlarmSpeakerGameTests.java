package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.alarm.AlarmSettings;
import com.senne.imttech.alarm.AlarmSounds;
import com.senne.imttech.alarm.AlarmSpeakerBlock;
import com.senne.imttech.alarm.AlarmSpeakerBlockEntity;
import com.senne.imttech.alarm.AlarmSpeakers;
import com.senne.imttech.alarm.AlarmUploads;
import com.senne.imttech.alarm.audio.Adpcm;
import com.senne.imttech.alarm.audio.AlarmAudio;
import com.senne.imttech.alarm.audio.AlarmAudioException;
import com.senne.imttech.alarm.audio.Mp4Decoder;
import com.senne.imttech.alarm.audio.Pcm;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.network.AlarmSavePayload;
import com.senne.imttech.network.AlarmSoundDataPayload;
import com.senne.imttech.network.AlarmUploadPayload;
import com.senne.imttech.redstone.DeviceNames;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import com.senne.imttech.security.os.EventLog;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de Alarm Speaker (hoofdstuk 126, jouw wens van 16 november: "Voeg een nieuwe blok toe namelijk een alarm waar je
 * zelf een mp4 bestand naar kunt uploaden zodat je custom alarmen kunt hebben").
 *
 * In de bestaande batch "lan" (geen nieuwe batchnaam: zie de 192-regel in het geheugen van de tests): die zet de netwerk-eis
 * aan, net als in een echte wereld (NetworkGameTests.networkOn).
 *
 * Wat een server niet kan, testen we hier niet: een mp4 lezen gebeurt in je spel, maar dat is gewone Java (AlarmAudio),
 * dus dat testen we wel, met echte testbestanden (tools/generate_alarm_test_audio.py); alleen ogg (Minecraft's eigen
 * lezer) en het afspelen zelf zitten alleen in je spel.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class AlarmSpeakerGameTests {
    private AlarmSpeakerGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een speaker op de vloer op deze plek (met steen eronder), neergezet door {@code owner} (dan heet hij "Alarm 1"). */
    private static BlockPos placeSpeaker(GameTestHelper helper, BlockPos pos, ServerPlayer owner) {
        if (helper.getBlockState(pos.below()).isAir()) {
            helper.setBlock(pos.below(), Blocks.STONE);
        }
        BlockState state = ModBlocks.ALARM_SPEAKER.get().defaultBlockState()
                .setValue(AlarmSpeakerBlock.FACE, AttachFace.FLOOR).setValue(AlarmSpeakerBlock.FACING, Direction.SOUTH);
        helper.setBlock(pos, state);
        BlockPos abs = helper.absolutePos(pos);
        ModBlocks.ALARM_SPEAKER.get().setPlacedBy(helper.getLevel(), abs, helper.getBlockState(pos), owner, ItemStack.EMPTY);
        return pos;
    }

    private static AlarmSpeakerBlockEntity speaker(GameTestHelper helper, BlockPos pos) {
        return (AlarmSpeakerBlockEntity) helper.getBlockEntity(pos);
    }

    /** Legt een kabel langs een rechte lijn in x, van {@code from} tot en met {@code to} (dezelfde y en z). */
    private static void lineX(GameTestHelper helper, CableKind kind, BlockPos from, int toX) {
        for (int x = from.getX(); x <= toX; x++) {
            Cables.add(kind, helper.getLevel(), helper.absolutePos(new BlockPos(x, from.getY(), from.getZ())));
        }
    }

    /**
     * Een kern met een speaker eraan (een PoE-kabel over de vloer van de kern naar de speaker, die de stroom van de kern
     * meebrengt), al online.
     */
    private static BlockPos wiredSpeaker(GameTestHelper helper, ServerPlayer owner, SecurityCoreBlockEntity[] coreOut, int z) {
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, z), owner);
        lineX(helper, CableKind.POE, new BlockPos(3, 3, z), 8);
        BlockPos pos = placeSpeaker(helper, new BlockPos(8, 4, z), owner);
        speaker(helper, pos).updateOnline(helper.getLevel());
        coreOut[0] = core;
        return pos;
    }

    /** Een toon (sinus), om te uploaden. */
    private static short[] tone(double seconds, double hz) {
        short[] samples = new short[(int) (seconds * AlarmAudio.RATE)];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) Math.round(16000 * Math.sin(2 * Math.PI * hz * i / AlarmAudio.RATE));
        }
        return samples;
    }

    // ================================================================ Op het netwerk

    /**
     * Wie hem neerzet, heeft hem in zijn I.M.T-lijst als "Alarm 1". Zonder kabel naar een kern werkt hij niet (zijn lampje
     * blijft uit); met een PoE-kabel naar de kern (die ook stroom brengt) is hij online, hoort hij bij die kern en staat hij in
     * Devices als "ready".
     */
    @GameTest(template = ARENA, batch = "lan")
    public static void alarm_speaker_needs_a_network_and_power(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "lan-alarm-owner1");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos pos = placeSpeaker(helper, new BlockPos(8, 4, 2), owner);
        BlockPos abs = helper.absolutePos(pos);
        AlarmSpeakerBlockEntity speaker = speaker(helper, pos);
        check(speaker.name().equals("Alarm 1") && DeviceNames.nameOf(owner, List.of(abs)).equals("Alarm 1"),
                "De speaker heet niet Alarm 1 in het blok en in de I.M.T-lijst: " + speaker.name());
        speaker.updateOnline(level);
        helper.assertBlockProperty(pos, AlarmSpeakerBlock.ONLINE, false);
        check(SecurityCores.deviceCore(level, abs) == null, "Zonder kabel hoort de speaker toch bij een kern");

        lineX(helper, CableKind.POE, new BlockPos(3, 3, 2), 8);
        speaker.updateOnline(level);
        helper.assertBlockProperty(pos, AlarmSpeakerBlock.ONLINE, true);
        check(SecurityCores.deviceCore(level, abs) == core, "Met een PoE-kabel hoort de speaker niet bij de kern");
        SecurityDevices.forget(level, core);
        check(SecurityDevices.scan(level, core).alarms().contains(abs), "De kern kent zijn speaker niet (SecurityDevices)");
        String status = "";
        for (net.minecraft.nbt.Tag tag : SecurityDevices.snapshot(level, core)) {
            CompoundTag device = (CompoundTag) tag;
            if (device.getString("k").equals("alarm") && device.getInt("x") == abs.getX() && device.getInt("z") == abs.getZ()) {
                status = device.getString("st");
            }
        }
        check(status.equals("ready"), "In Devices staat de speaker niet als ready, maar als '" + status + "'");
        helper.succeed();
    }

    /**
     * Hij gaat nooit vanzelf af (hoofdstuk 128): een alarm in het logboek van zijn kern (zoals {@code alarm("...")} in een
     * diagram, iemand die aan een lezer prutst, een ontploffing of een brand: die komen allemaal via logEvent) laat hem stil,
     * ook drie seconden later nog (na zijn controles van stroom en netwerk). Het alarm staat wel gewoon in het logboek. Pas
     * op zijn naam aangezet, zoals {@code on("Alarm 1")} in een diagram dat je runt, gaat hij af, zo lang als ingesteld (hier
     * 5 seconden), en dan stopt hij vanzelf. Een speaker aan een andere kern blijft al die tijd stil.
     */
    @GameTest(template = ARENA, batch = "lan", timeoutTicks = 260)
    public static void alarm_speaker_never_sounds_by_itself(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 7), "lan-alarm-owner2");
        SecurityCoreBlockEntity[] cores = new SecurityCoreBlockEntity[1];
        BlockPos pos = wiredSpeaker(helper, owner, cores, 2);
        SecurityCoreBlockEntity core = cores[0];
        BlockPos other = wiredSpeaker(helper, owner, cores, 12);
        helper.assertBlockProperty(pos, AlarmSpeakerBlock.ONLINE, true);
        helper.assertBlockProperty(other, AlarmSpeakerBlock.ONLINE, true);
        speaker(helper, pos).setSettings(1f, 32, 5);
        SecurityDevices.forget(level, core);

        core.logEvent(EventLog.Type.ALARM, "Intruder at the alarm test door");
        helper.assertBlockProperty(pos, AlarmSpeakerBlock.SOUNDING, false);
        helper.assertBlockProperty(other, AlarmSpeakerBlock.SOUNDING, false);
        check(speaker(helper, pos).soundUntil() == 0, "Na een ALARM in het logboek heeft de speaker toch een eindtijd");
        long alarms = core.log().entries().stream()
                .filter(entry -> entry.type() == EventLog.Type.ALARM && entry.text().contains("alarm test door")).count();
        check(alarms == 1, "Het alarm staat " + alarms + " keer in het logboek (verwacht: 1)");

        BlockPos abs = helper.absolutePos(pos);
        long[] switchedAt = new long[1];
        helper.runAfterDelay(60, () -> {
            helper.assertBlockProperty(pos, AlarmSpeakerBlock.SOUNDING, false);
            helper.assertBlockProperty(other, AlarmSpeakerBlock.SOUNDING, false);
            // Zoals on("Alarm 1") in een diagram dat je runt.
            switchedAt[0] = level.getGameTime();
            ImtRedstone.switchAt(level, abs, -1, ImtRedstone.Op.ON, 0);
            helper.assertBlockProperty(pos, AlarmSpeakerBlock.SOUNDING, true);
            helper.assertBlockProperty(other, AlarmSpeakerBlock.SOUNDING, false);
            check(speaker(helper, pos).soundUntil() == switchedAt[0] + 100,
                    "Aan op zijn naam stopt niet na 5 seconden: " + (speaker(helper, pos).soundUntil() - switchedAt[0]));
        });
        helper.runAfterDelay(60 + 130, () -> {
            helper.assertBlockProperty(pos, AlarmSpeakerBlock.SOUNDING, false);
            check(speaker(helper, pos).soundUntil() == 0, "Na het stoppen staat er nog een eindtijd");
            helper.succeed();
        });
    }

    /**
     * Op zijn naam (zoals {@code on("Alarm 1")}, {@code off}, {@code toggle}, {@code pulse} in een diagram, je bril of
     * Devices): aan gaat hij af met de ingestelde tijd (of tot je hem uitzet), uit is stil. De kern kent de naam. Devices kan
     * hem aan- en uitzetten en hernoemen. Zonder kabel kan hij niet aan.
     */
    @GameTest(template = ARENA, batch = "lan", timeoutTicks = 100)
    public static void alarm_speaker_switches_by_name(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "lan-alarm-owner3");
        SecurityCoreBlockEntity[] cores = new SecurityCoreBlockEntity[1];
        BlockPos pos = wiredSpeaker(helper, owner, cores, 2);
        SecurityCoreBlockEntity core = cores[0];
        BlockPos abs = helper.absolutePos(pos);
        AlarmSpeakerBlockEntity speaker = speaker(helper, pos);

        long now = level.getGameTime();
        ImtRedstone.switchAt(level, abs, -1, ImtRedstone.Op.ON, 0);
        helper.assertBlockProperty(pos, AlarmSpeakerBlock.SOUNDING, true);
        check(speaker.soundUntil() == now + AlarmSpeakerBlockEntity.DEFAULT_ALARM_SECONDS * 20L, "Aan op zijn naam heeft niet de ingestelde tijd");
        ImtRedstone.switchAt(level, abs, -1, ImtRedstone.Op.OFF, 0);
        helper.assertBlockProperty(pos, AlarmSpeakerBlock.SOUNDING, false);
        check(speaker.soundUntil() == 0, "Uit heeft nog een eindtijd");

        speaker.setSettings(1f, 32, 0); // tot je hem uitzet
        ImtRedstone.switchAt(level, abs, -1, ImtRedstone.Op.TOGGLE, 0);
        helper.assertBlockProperty(pos, AlarmSpeakerBlock.SOUNDING, true);
        check(speaker.soundUntil() == 0, "Tot je hem uitzet heeft toch een eindtijd");
        ImtRedstone.switchAt(level, abs, -1, ImtRedstone.Op.TOGGLE, 0);
        helper.assertBlockProperty(pos, AlarmSpeakerBlock.SOUNDING, false);

        com.senne.imttech.logic.LogicDevices.refresh(level, core, owner);
        check(!core.logic().devices("Alarm 1").isEmpty(), "De kern kent de naam Alarm 1 niet (voor diagrammen)");

        SecurityDevices.forget(level, core);
        check(SecurityDevices.switchDevice(level, core, abs, true) == null, "Devices kan de speaker niet aanzetten");
        helper.assertBlockProperty(pos, AlarmSpeakerBlock.SOUNDING, true);
        check(SecurityDevices.switchDevice(level, core, abs, false) == null, "Devices kan de speaker niet uitzetten");
        helper.assertBlockProperty(pos, AlarmSpeakerBlock.SOUNDING, false);
        String error = SecurityDevices.renameDevice(owner, core, abs, "Lobby siren");
        check(error == null && speaker.name().equals("Lobby siren") && DeviceNames.nameOf(owner, List.of(abs)).equals("Lobby siren"),
                "Hernoemen in Devices werkt niet: " + error + " / " + speaker.name());

        // Een puls langer dan de speaker zelf (5 seconden) duurt zo lang als de puls.
        speaker.setSettings(1f, 32, 5);
        ImtRedstone.switchAt(level, abs, -1, ImtRedstone.Op.PULSE, 120);
        check(speaker.soundUntil() == level.getGameTime() + 120,
                "Een puls van 6 seconden kreeg de 5 seconden van de speaker: " + (speaker.soundUntil() - level.getGameTime()));
        ImtRedstone.switchAt(level, abs, -1, ImtRedstone.Op.OFF, 0);
        // Ook op "tot je hem uitzet" bewaart een puls zijn eigen eindtijd: stopt de server midden in de puls (die
        // ImtRedstone niet bewaart), dan stopt de speaker na het laden toch op tijd.
        speaker.setSettings(1f, 32, 0);
        ImtRedstone.switchAt(level, abs, -1, ImtRedstone.Op.PULSE, 10);
        helper.assertBlockProperty(pos, AlarmSpeakerBlock.SOUNDING, true);
        check(speaker.soundUntil() == level.getGameTime() + 10,
                "Een puls bewaart zijn eigen eindtijd niet (na een herstart zou hij blijven loeien): " + speaker.soundUntil());
        helper.runAfterDelay(14, () -> {
            helper.assertBlockProperty(pos, AlarmSpeakerBlock.SOUNDING, false);
            // Zonder kabel: geen netwerk en geen stroom, dus hij kan niet aan.
            for (int x = 3; x <= 8; x++) {
                Cables.remove(CableKind.POE, level, helper.absolutePos(new BlockPos(x, 3, 2)));
            }
            speaker.updateOnline(level);
            helper.assertBlockProperty(pos, AlarmSpeakerBlock.ONLINE, false);
            ImtRedstone.switchAt(level, abs, -1, ImtRedstone.Op.ON, 0);
            helper.assertBlockProperty(pos, AlarmSpeakerBlock.SOUNDING, false);
            helper.succeed();
        });
    }

    // ================================================================ Uploaden

    /**
     * Een geluid uploaden gaat in stukjes, en alleen voor wie de speaker mag instellen: een vreemde komt niet verder dan het
     * eerste stukje, een stukje dat niet op zijn beurt komt stopt de upload, een geluid langer dan de config toestaat mag
     * niet beginnen. Een goede upload staat daarna op de speaker (naam, lengte) en als bestand bij de wereld. Save in het
     * scherm houdt alles binnen de grenzen.
     */
    @GameTest(template = ARENA, batch = "lan", timeoutTicks = 200)
    public static void alarm_upload_in_chunks_only_for_whoever_may_edit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(7, 3, 5), "lan-alarm-owner4");
        BlockPos pos = placeSpeaker(helper, new BlockPos(8, 4, 2), owner);
        BlockPos abs = helper.absolutePos(pos);
        AlarmSpeakerBlockEntity speaker = speaker(helper, pos);
        @SuppressWarnings("removal")
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        stranger.teleportTo(abs.getX() + 2.5, abs.getY(), abs.getZ() + 0.5);

        short[] clip = tone(7.0, 880);
        byte[] adpcm = Adpcm.encode(clip);
        List<AlarmUploadPayload> parts = AlarmUploads.payloads(abs, 7, clip.length, "siren.mp4", adpcm);
        check(parts.size() >= 3, "Een geluid van 7 seconden zou in minstens 3 stukjes moeten, niet " + parts.size());

        for (AlarmUploadPayload part : parts) {
            AlarmUploads.handleUpload(stranger, part);
        }
        check(!AlarmUploads.uploading(stranger.getUUID()) && speaker.soundId().isEmpty(), "Een vreemde kon uploaden");

        AlarmUploads.handleUpload(owner, parts.get(0));
        check(AlarmUploads.uploading(owner.getUUID()), "Het eerste stukje begint geen upload");
        AlarmUploads.handleUpload(owner, parts.get(2));
        check(!AlarmUploads.uploading(owner.getUUID()), "Een stukje dat niet op zijn beurt kwam, stopte de upload niet");
        // Meteen weer een eerste stukje: dat mag pas na een seconde (een rem tegen bestoken), dus geen upload.
        AlarmUploads.handleUpload(owner, parts.get(0));
        check(!AlarmUploads.uploading(owner.getUUID()), "Een tweede poging binnen een seconde begon toch een upload");
        // Een eerste stukje dat niet zo groot is als het hoort, begint niets (en legt geen geheugen vast).
        helper.runAfterDelay(25, () -> {
            AlarmUploads.handleUpload(owner, new AlarmUploadPayload(abs, 8, 0, parts.get(0).chunks(), clip.length, "siren.mp4", new byte[0]));
            check(!AlarmUploads.uploading(owner.getUUID()), "Een leeg eerste stukje begon toch een upload");
        });

        int tooMany = AlarmSounds.maxSamples() + 1;
        helper.runAfterDelay(50, () -> {
            AlarmUploads.handleUpload(owner, new AlarmUploadPayload(abs, 9, 0, AlarmUploads.chunksFor(Adpcm.encodedLength(tooMany)),
                    tooMany, "long.mp3", new byte[AlarmUploadPayload.CHUNK_BYTES]));
            check(!AlarmUploads.uploading(owner.getUUID()), "Een geluid langer dan de config toestaat, mocht beginnen");
        });

        String id = AlarmSounds.idOf(clip.length, adpcm);
        helper.runAfterDelay(75, () -> {
            for (AlarmUploadPayload part : parts) {
                AlarmUploads.handleUpload(owner, part);
            }
            AlarmSpeakers.handleSave(owner, new AlarmSavePayload(abs, "Front siren", 7f, 9999, 9999));
        });
        helper.succeedWhen(() -> {
            check(speaker.soundId().equals(id), "Na het uploaden staat het geluid niet op de speaker");
            check(speaker.soundName().equals("siren.mp4") && Math.abs(speaker.soundSeconds() - 7.0f) < 0.01f,
                    "Naam of lengte van het geluid klopt niet: " + speaker.soundName() + " " + speaker.soundSeconds());
            check(AlarmSounds.storedNow(level.getServer(), id), "Het geluid staat niet als bestand bij de wereld");
            check(speaker.name().equals("Front siren"), "Save gaf de speaker geen nieuwe naam: " + speaker.name());
            check(speaker.volume() == 1.0f && speaker.range() == AlarmSpeakerBlockEntity.maxRange()
                            && speaker.alarmSeconds() == AlarmSpeakerBlockEntity.clampAlarmSeconds(9999),
                    "Save hield de instellingen niet binnen de grenzen: " + speaker.volume() + " " + speaker.range() + " " + speaker.alarmSeconds());
        });
    }

    /**
     * Afgebroken houdt het item zijn naam, zijn geluid en zijn instellingen (en zet je hem weer neer, dan heeft hij ze nog);
     * opslaan en laden verliest niets, ook de eindtijd niet.
     */
    @GameTest(template = ARENA, batch = "lan")
    public static void alarm_speaker_keeps_its_sound_when_mined(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "lan-alarm-owner5");
        BlockPos pos = placeSpeaker(helper, new BlockPos(8, 4, 2), owner);
        BlockPos abs = helper.absolutePos(pos);
        AlarmSpeakerBlockEntity speaker = speaker(helper, pos);
        String id = AlarmSounds.idOf(5, new byte[Adpcm.encodedLength(5)]);
        speaker.setSound(id, "siren.mp4", 2.5f);
        speaker.setSettings(0.6f, 40, 60);
        speaker.setName("Lobby siren");

        List<ItemStack> drops = Block.getDrops(helper.getBlockState(pos), level, abs, speaker);
        check(drops.size() == 1, "De speaker laat " + drops.size() + " dingen vallen");
        ItemStack drop = drops.get(0);
        AlarmSettings settings = drop.get(ModDataComponents.ALARM_SETTINGS.get());
        check(settings != null && settings.soundId().equals(id) && settings.range() == 40 && settings.alarmSeconds() == 60
                && Math.abs(settings.volume() - 0.6f) < 0.01f, "Het item houdt het geluid of de instellingen niet: " + settings);
        check(drop.getHoverName().getString().equals("Lobby siren"), "Het item houdt de naam niet: " + drop.getHoverName().getString());

        BlockPos again = new BlockPos(5, 4, 9);
        helper.setBlock(again.below(), Blocks.STONE);
        helper.setBlock(again, helper.getBlockState(pos));
        AlarmSpeakerBlockEntity placed = speaker(helper, again);
        placed.applyComponentsFromItemStack(drop);
        check(placed.soundId().equals(id) && placed.range() == 40 && placed.alarmSeconds() == 60 && placed.soundName().equals("siren.mp4"),
                "Weer neergezet heeft de speaker zijn geluid niet meer");

        CompoundTag saved = speaker.saveWithoutMetadata(level.registryAccess());
        AlarmSpeakerBlockEntity loaded = new AlarmSpeakerBlockEntity(abs, helper.getBlockState(pos));
        loaded.loadWithComponents(saved, level.registryAccess());
        check(loaded.soundId().equals(id) && loaded.name().equals("Lobby siren") && loaded.range() == 40
                && loaded.alarmSeconds() == 60 && loaded.soundUntil() == speaker.soundUntil()
                && owner.getUUID().equals(loaded.owner()), "Opslaan en laden verliest iets");
        helper.succeed();
    }

    // ================================================================ Bestanden inlezen

    /** Een testbestand uit de mod naar een tijdelijk bestand (AlarmAudio leest bestanden). */
    private static Path resource(String name) throws IOException {
        try (InputStream in = AlarmSpeakerGameTests.class.getResourceAsStream("/imttech_test_audio/" + name)) {
            if (in == null) {
                throw new GameTestAssertException("Testbestand ontbreekt: " + name);
            }
            Path file = Files.createTempFile("imttech-alarm-", "-" + name);
            Files.write(file, in.readAllBytes());
            file.toFile().deleteOnExit();
            return file;
        }
    }

    /** Hoe vaak per seconde het geluid door nul gaat, gedeeld door twee: ongeveer de toonhoogte. */
    private static double pitch(short[] clip) {
        int crossings = 0;
        for (int i = 1; i < clip.length; i++) {
            if ((clip[i - 1] < 0) != (clip[i] < 0)) {
                crossings++;
            }
        }
        return crossings / 2.0 / (clip.length / (double) AlarmAudio.RATE);
    }

    private static void checkClip(String name, double expectedSeconds, double tolerance) throws IOException, AlarmAudioException {
        short[] clip = AlarmAudio.read(resource(name), 30.0);
        double seconds = clip.length / (double) AlarmAudio.RATE;
        int peak = 0;
        for (short sample : clip) {
            peak = Math.max(peak, Math.abs(sample));
        }
        double pitch = pitch(clip);
        check(Math.abs(seconds - expectedSeconds) <= tolerance, name + " duurt " + seconds + " s in plaats van " + expectedSeconds);
        check(peak > 20000, name + " is te stil (piek " + peak + ")");
        check(pitch > 500 && pitch < 1400, name + " heeft de verkeerde toonhoogte (" + Math.round(pitch) + " Hz, de sirene is 600-1200 Hz)"
                + rawStats(name));
        // Geen stille gaten (een stuk dat de decoder verkeerd deed): elk stuk van 2048 monsters, behalve aan het begin en
        // einde, is minstens een vijfde zo hard als het midden.
        java.util.List<Double> loudness = new java.util.ArrayList<>();
        for (int start = clip.length / 10; start + 2048 <= clip.length - clip.length / 10; start += 2048) {
            double energy = 0;
            for (int i = start; i < start + 2048; i++) {
                energy += clip[i] * (double) clip[i];
            }
            loudness.add(Math.sqrt(energy / 2048));
        }
        if (loudness.size() >= 3) {
            java.util.List<Double> sorted = new java.util.ArrayList<>(loudness);
            java.util.Collections.sort(sorted);
            double median = sorted.get(sorted.size() / 2);
            check(sorted.get(0) >= 0.2 * median, name + " heeft stille gaten (zachtste stuk " + Math.round(sorted.get(0))
                    + ", midden " + Math.round(median) + ")");
        }
        // Zuiver: de sirene is één toon (twee vlak bij elkaar in stereo). Klopt het decoderen niet (zoals zonder TNS), dan
        // smeert de toon uit over veel frequenties.
        double purity = purity(clip);
        ImtTech.LOGGER.info("[alarm test] {}: {} s, toonhoogte {} Hz, zuiverheid {}", name, String.format(java.util.Locale.ROOT, "%.3f", seconds),
                Math.round(pitch), String.format(java.util.Locale.ROOT, "%.3f", purity));
        check(purity >= 0.75, name + " is niet zuiver genoeg (" + String.format(java.util.Locale.ROOT, "%.2f", purity)
                + " van de energie zit rond de toon, verwacht minstens 0,75)");
        short[] back = Adpcm.decode(Adpcm.encode(clip), clip.length);
        double error = 0;
        double signal = 0;
        for (int i = 0; i < clip.length; i++) {
            double difference = clip[i] - back[i];
            error += difference * difference;
            signal += clip[i] * (double) clip[i];
        }
        double snr = 10 * Math.log10(signal / Math.max(1e-9, error));
        check(snr > 15, name + ": klein gemaakt (ADPCM) klinkt het te ruisig (" + Math.round(snr) + " dB)");
    }

    /**
     * Hoe zuiver het geluid is: per stuk van 1024 monsters (met een Hann-venster) het deel van de energie dat binnen drie
     * frequentievakjes van de sterkste toon zit; het slechtste stuk telt (begin en einde niet).
     */
    private static double purity(short[] clip) {
        int n = 1024;
        double worst = 1.0;
        double[] window = new double[n];
        for (int i = 0; i < n; i++) {
            window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1));
        }
        for (int start = clip.length / 10; start + n <= clip.length - clip.length / 10; start += n) {
            double[] x = new double[n];
            double total = 0;
            for (int i = 0; i < n; i++) {
                x[i] = clip[start + i] * window[i];
                total += x[i] * x[i];
            }
            if (total < 1e3) {
                continue; // stil stuk (dat vangt de controle op gaten)
            }
            double[] power = new double[n / 2];
            int peak = 1;
            for (int k = 1; k < n / 2; k++) {
                // Goertzel: de sterkte van frequentievakje k
                double coefficient = 2 * Math.cos(2 * Math.PI * k / n);
                double s1 = 0;
                double s2 = 0;
                for (int i = 0; i < n; i++) {
                    double s0 = x[i] + coefficient * s1 - s2;
                    s2 = s1;
                    s1 = s0;
                }
                power[k] = s1 * s1 + s2 * s2 - coefficient * s1 * s2;
                if (power[k] > power[peak]) {
                    peak = k;
                }
            }
            double near = 0;
            double all = 0;
            for (int k = 1; k < n / 2; k++) {
                all += power[k];
                if (Math.abs(k - peak) <= 3) {
                    near += power[k];
                }
            }
            if (all > 0) {
                worst = Math.min(worst, near / all);
            }
        }
        return worst;
    }

    /** Wat het decoderen zelf gaf (voor het omrekenen): voor een duidelijke melding als de toonhoogte niet klopt. */
    private static String rawStats(String name) {
        try {
            Path file = resource(name);
            Pcm pcm = name.endsWith(".mp3") ? com.senne.imttech.alarm.audio.Mp3Decoder.decode(file, 30)
                    : name.endsWith(".wav") ? com.senne.imttech.alarm.audio.WavDecoder.decode(file, 30)
                    : com.senne.imttech.alarm.audio.Mp4Decoder.decode(file, 30);
            float[] mono = pcm.mono();
            int crossings = 0;
            double sum = 0;
            float peak = 0;
            for (int i = 0; i < mono.length; i++) {
                sum += mono[i];
                peak = Math.max(peak, Math.abs(mono[i]));
                if (i > 0 && (mono[i - 1] < 0) != (mono[i] < 0)) {
                    crossings++;
                }
            }
            double seconds = mono.length / (double) pcm.rate;
            StringBuilder first = new StringBuilder();
            for (int i = 2000; i < Math.min(mono.length, 2012); i++) {
                first.append(String.format(java.util.Locale.ROOT, " %.3f", mono[i]));
            }
            return String.format(java.util.Locale.ROOT, " [ruw: %d Hz, %d kanalen, %.3f s, toonhoogte %.0f Hz, gemiddeld %.4f, piek %.3f, vanaf 2000:%s]",
                    pcm.rate, pcm.channels, seconds, crossings / 2.0 / seconds, sum / Math.max(1, mono.length), peak, first);
        } catch (Exception problem) {
            return " [ruw: " + describe(problem) + "]";
        }
    }

    /** Een kleine wav (16 bit mono) in het geheugen, voor de gevallen die we geen bestand waard vinden. */
    private static Path wav(short[] samples, int rate) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(44 + samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes()).putInt(36 + samples.length * 2).put("WAVE".getBytes());
        buffer.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                .putShort((short) 2).putShort((short) 16);
        buffer.put("data".getBytes()).putInt(samples.length * 2);
        for (short sample : samples) {
            buffer.putShort(sample);
        }
        Path file = Files.createTempFile("imttech-alarm-", ".wav");
        Files.write(file, buffer.array());
        file.toFile().deleteOnExit();
        return file;
    }

    /**
     * Elk formaat dat je kunt uploaden, uit echte bestanden: een mp4-filmpje (beeld + AAC-geluid, de inhoudsopgave
     * achteraan, met een udta/meta-doosje zoals ffmpeg en gsm's schrijven: daar struikelde de mp4-lezer van JAAD over), een
     * m4a, een gefragmenteerde m4a (zoals van YouTube), een QuickTime-.mov (de AAC-instellingen in een wave-doosje), een mp3
     * met een ID3-label ervoor, een gewone wav, een wav met kommagetallen (8 kHz, dus omhoog omrekenen), een los .aac en een
     * .aac met een ID3-label ervoor (dat moet naar de AAC-lezer, niet naar mp3). Elk wordt even lang, hard genoeg, en heeft
     * de toonhoogte van de sirene. Een tekstbestand is geen geluid, stilte is geen alarm, een geluid van 40 seconden wordt
     * 30, een mp3 van 22 kHz (die JavaMP3 niet kent) geeft een nette zin, en de beschrijving van HE-AAC wordt gewone AAC met
     * de snelheid van de kern (zonder dat lezen we HE-AAC fout).
     */
    @GameTest(template = ARENA, batch = "lan")
    public static void alarm_audio_reads_every_format(GameTestHelper helper) {
        // Elk bestand apart: valt er een om, dan zien we toch hoe het met de andere ging.
        StringBuilder problems = new StringBuilder();
        Object[][] files = {{"alarm_test.mp4", 1.5, 0.12}, {"alarm_test.m4a", 1.0, 0.12}, {"alarm_test_fragmented.m4a", 1.0, 0.15},
                {"alarm_test.mov", 1.0, 0.15}, {"alarm_test.mp3", 1.0, 0.15}, {"alarm_test.wav", 0.2, 0.03},
                {"alarm_test_float.wav", 0.5, 0.03}, {"alarm_test.aac", 1.0, 0.15}, {"alarm_test_id3.aac", 1.0, 0.15}};
        for (Object[] file : files) {
            try {
                checkClip((String) file[0], (double) file[1], (double) file[2]);
            } catch (GameTestAssertException | IOException | AlarmAudioException problem) {
                problems.append(problems.length() == 0 ? "" : " ||| ").append(file[0]).append(": ")
                        .append(problem instanceof GameTestAssertException ? problem.getMessage() : describe(problem));
            }
        }
        check(problems.length() == 0, problems.toString());
        try {

            Path text = Files.createTempFile("imttech-alarm-", ".mp4");
            Files.writeString(text, "This is not a video, just some words.");
            text.toFile().deleteOnExit();
            try {
                AlarmAudio.read(text, 30.0);
                check(false, "Een tekstbestand werd als geluid gelezen");
            } catch (AlarmAudioException expected) {
                check(expected.key.equals("unsupported"), "Een tekstbestand gaf de fout " + expected.key);
            }
            try {
                AlarmAudio.read(wav(new short[22050], 22050), 30.0);
                check(false, "Stilte werd een alarm");
            } catch (AlarmAudioException expected) {
                check(expected.key.equals("silent"), "Stilte gaf de fout " + expected.key);
            }
            try {
                AlarmAudio.read(resource("alarm_test_22khz.mp3"), 30.0);
                check(false, "Een mp3 van 22 kHz werd gelezen (dat kan JavaMP3 niet echt)");
            } catch (AlarmAudioException expected) {
                check(expected.key.equals("mp3_low_rate") && expected.args.length == 1 && Integer.valueOf(22050).equals(expected.args[0]),
                        "Een mp3 van 22 kHz gaf de fout " + expected.key + " " + java.util.Arrays.toString(expected.args));
            }

            // HE-AAC stereo 44,1 kHz (2B 92 08 00) wordt gewone AAC, 22.050, stereo (13 90), en JAAD leest het zo.
            byte[] core = Mp4Decoder.coreConfig(new byte[]{0x2B, (byte) 0x92, 0x08, 0x00});
            check(core.length >= 2 && core[0] == 0x13 && core[1] == (byte) 0x90,
                    "HE-AAC werd niet gewone AAC maar " + java.util.HexFormat.of().formatHex(core));
            com.senne.imttech.lib.jaad.aac.DecoderConfig config = new com.senne.imttech.lib.jaad.aac.Decoder(core).getConfig();
            check(config.getSampleFrequency().getFrequency() == 22050 && config.getChannelConfiguration().getChannelCount() == 2,
                    "JAAD las de kern van HE-AAC als " + config.getSampleFrequency().getFrequency() + " Hz, "
                            + config.getChannelConfiguration().getChannelCount() + " kanalen");
            byte[] plain = {0x12, 0x10};
            check(Mp4Decoder.coreConfig(plain) == plain, "Gewone AAC werd veranderd");

            Pcm long40 = new Pcm(48000, 2, 40.0);
            int i = 0;
            while (long40.add((float) (0.3 * Math.sin(2 * Math.PI * 1000 * (i / 2) / 48000.0)))) {
                i++;
            }
            short[] cut = AlarmAudio.process(long40, 30.0);
            check(cut.length == 30 * AlarmAudio.RATE, "Een geluid van 40 seconden werd niet 30 maar " + cut.length / (double) AlarmAudio.RATE);
        } catch (IOException | AlarmAudioException problem) {
            throw new GameTestAssertException("Inlezen mislukte: " + describe(problem));
        }
        helper.succeed();
    }

    // ================================================================ Ophalen

    /**
     * Een speler krijgt een geluid alleen als er een speaker met dat geluid in de buurt staat, hooguit zes geluiden per tien
     * seconden, en hetzelfde geluid niet steeds opnieuw. De server stuurt het in stukjes die samen weer het geluid zijn;
     * een stukje dat te groot is, kan niet eens verstuurd worden.
     */
    @GameTest(template = ARENA, batch = "lan")
    public static void alarm_sound_requests_are_limited(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "lan-alarm-owner6");
        BlockPos pos = placeSpeaker(helper, new BlockPos(8, 4, 2), owner);
        BlockPos abs = helper.absolutePos(pos);
        short[] clip = tone(12.0, 600);
        byte[] adpcm = Adpcm.encode(clip);
        String id = AlarmSounds.idOf(clip.length, adpcm);
        speaker(helper, pos).setSound(id, "siren.wav", 12f);

        // Een blok-entiteit meldt zich pas bij de volgende tick aan (onLoad): dan pas kent de server de speaker.
        helper.runAfterDelay(3, () -> {
            Vec3 near = Vec3.atCenterOf(abs).add(10, 0, 0);
            Vec3 far = Vec3.atCenterOf(abs).add(AlarmSpeakerBlockEntity.maxRange() + 60, 0, 0);
            check(AlarmSpeakers.usedNear(level, near, id), "De server ziet de speaker met dit geluid in de buurt niet");
            check(!AlarmSpeakers.usedNear(level, far, id), "Een speaker ver weg telt als in de buurt");
            check(!AlarmSpeakers.usedNear(level, near, AlarmSounds.idOf(1, new byte[Adpcm.encodedLength(1)])),
                    "Een geluid dat niemand hier heeft, telt toch");
            helper.succeed();
        });

        UUID player = UUID.randomUUID();
        long now = level.getGameTime();
        check(AlarmUploads.allowRequest(player, id, now), "De eerste vraag mag niet");
        check(!AlarmUploads.allowRequest(player, id, now + 1), "Hetzelfde geluid mag meteen nog eens");
        for (int n = 1; n < 6; n++) {
            check(AlarmUploads.allowRequest(player, AlarmSounds.idOf(n + 1, new byte[Adpcm.encodedLength(n + 1)]), now + n),
                    "Vraag " + (n + 1) + " mag niet");
        }
        check(!AlarmUploads.allowRequest(player, AlarmSounds.idOf(99, new byte[Adpcm.encodedLength(99)]), now + 7),
                "Een zevende vraag binnen tien seconden mag toch");
        check(AlarmUploads.allowRequest(player, AlarmSounds.idOf(99, new byte[Adpcm.encodedLength(99)]), now + 260),
                "Na tien seconden mag er weer iets");

        List<AlarmSoundDataPayload> parts = AlarmUploads.dataPayloads(new AlarmSounds.Stored(id, clip.length, adpcm));
        java.io.ByteArrayOutputStream joined = new java.io.ByteArrayOutputStream();
        for (AlarmSoundDataPayload part : parts) {
            check(part.data().length <= AlarmSoundDataPayload.CHUNK_BYTES && part.chunks() == parts.size(), "Een stukje is te groot");
            joined.writeBytes(part.data());
        }
        check(java.util.Arrays.equals(joined.toByteArray(), adpcm), "De stukjes samen zijn niet het geluid");

        ByteBuf buffer = Unpooled.buffer();
        AlarmUploadPayload chunk = AlarmUploads.payloads(abs, 1, clip.length, "siren.wav", adpcm).get(0);
        AlarmUploadPayload.STREAM_CODEC.encode(buffer, chunk);
        AlarmUploadPayload decoded = AlarmUploadPayload.STREAM_CODEC.decode(buffer);
        check(java.util.Arrays.equals(decoded.data(), chunk.data()) && decoded.samples() == clip.length, "Een stukje komt anders aan");
        boolean refused;
        try {
            AlarmUploadPayload.STREAM_CODEC.encode(Unpooled.buffer(),
                    new AlarmUploadPayload(abs, 1, 0, 1, 1, "x", new byte[AlarmUploadPayload.CHUNK_BYTES + 1]));
            refused = false;
        } catch (RuntimeException tooBig) {
            refused = true;
        }
        check(refused, "Een te groot stukje kon toch verstuurd worden");
    }

    /** De hele foutketen (de diepste oorzaak met zijn eerste regels code), voor een duidelijke melding als een test faalt. */
    private static String describe(Throwable problem) {
        StringBuilder text = new StringBuilder(problem.toString());
        Throwable root = problem;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
            text.append(" <- ").append(root);
        }
        StackTraceElement[] stack = root.getStackTrace();
        for (int i = 0; i < Math.min(6, stack.length); i++) {
            text.append(" @ ").append(stack[i]);
        }
        return text.toString();
    }
}
