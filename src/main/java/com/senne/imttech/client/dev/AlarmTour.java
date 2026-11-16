package com.senne.imttech.client.dev;

import com.senne.imttech.ImtTech;
import com.senne.imttech.alarm.AlarmSettings;
import com.senne.imttech.alarm.AlarmSounds;
import com.senne.imttech.alarm.AlarmSpeakerBlock;
import com.senne.imttech.alarm.AlarmSpeakerBlockEntity;
import com.senne.imttech.alarm.AlarmSpeakers;
import com.senne.imttech.alarm.audio.Adpcm;
import com.senne.imttech.alarm.audio.AlarmAudio;
import com.senne.imttech.client.alarm.AlarmClient;
import com.senne.imttech.client.alarm.AlarmSpeakerScreen;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.lan.LanGrid;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlock;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.station.ChargerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

/**
 * De rondleiding van de Alarm Speaker (hoofdstuk 126): {@code gradlew runVisualCheck -Ponly=alarm}, en het plaatje van zijn
 * scherm voor How it works met {@code -Ponly=howtopics:alarm} (dat verandert een bestand van de mod).
 *
 * Het toneel: een Security Core met een PoE-kabel naar een speaker aan een muurtje en een speaker op de vloer. De foto's:
 * de speaker klaar (groen lampje), afgaand (rood licht) na een alarm van de kern, het overzicht, de zin boven je hotbar
 * (rechtsklik), zijn scherm (met een eigen geluid, met een gekozen bestand, tijdens het uploaden) en de tooltip van het item.
 *
 * Een echt "bestand openen"-venster kan de rondleiding niet bedienen: het scherm krijgt het geluid via tourPending (een
 * sirene die we hier zelf maken), en de rest loopt zoals bij een echt bestand.
 */
final class AlarmTour {
    private AlarmTour() {}

    /** Een open plek, ver van de rest (de Router staat op -560/380, het opslagsysteem op -620/340). */
    static final BlockPos YARD = new BlockPos(-680, -60, 420);
    private static final BlockPos CORE = YARD;
    /** De speaker aan het muurtje (de muur ten noorden ervan) en de speaker op de vloer. */
    static final BlockPos SPEAKER = YARD.offset(5, 1, 0);
    private static final BlockPos FLOOR_SPEAKER = YARD.offset(8, 0, 1);

    /** Het toneel opbouwen (op de server). */
    private static void stage(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : BlockPos.betweenClosed(YARD.offset(-6, 0, -6), YARD.offset(14, 5, 12))) {
            for (CableKind kind : CableKind.values()) {
                Cables.remove(kind, level, pos);
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        // De kern, met stroom, en van jou.
        level.setBlockAndUpdate(CORE.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(CORE.below()) instanceof ChargerBlockEntity charger) {
            charger.fill(200_000);
        }
        level.setBlockAndUpdate(CORE, ModBlocks.SECURITY_CORE.get().defaultBlockState().setValue(SecurityCoreBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(CORE) instanceof SecurityCoreBlockEntity core) {
            core.setOwner(player);
            core.ensureSystem(level);
        }
        // Een muurtje met de speaker eraan, en een PoE-kabel over de grond van de kern naar beide speakers.
        for (int x = 4; x <= 6; x++) {
            for (int y = 0; y <= 2; y++) {
                level.setBlockAndUpdate(YARD.offset(x, y, -1), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int dx = 1; dx <= 8; dx++) {
            Cables.add(CableKind.POE, level, CORE.east(dx), player.getUUID());
        }
        speaker(player, SPEAKER, AttachFace.WALL, Direction.SOUTH, "Alarm 1");
        speaker(player, FLOOR_SPEAKER, AttachFace.FLOOR, Direction.NORTH, "Alarm 2");
        // Een eigen geluid op de speaker aan de muur (zoals na een upload): een sirene die we hier maken.
        short[] siren = siren(6.0);
        byte[] adpcm = Adpcm.encode(siren);
        AlarmSounds.store(level.getServer(), siren.length, adpcm, id -> {
            if (id != null && level.getBlockEntity(SPEAKER) instanceof AlarmSpeakerBlockEntity speaker) {
                speaker.setSound(id, "air_raid_siren.mp4", siren.length / (float) AlarmAudio.RATE);
            }
        });
    }

    private static void speaker(ServerPlayer player, BlockPos pos, AttachFace face, Direction facing, String name) {
        ServerLevel level = player.serverLevel();
        BlockState state = ModBlocks.ALARM_SPEAKER.get().defaultBlockState()
                .setValue(AlarmSpeakerBlock.FACE, face).setValue(AlarmSpeakerBlock.FACING, facing);
        level.setBlockAndUpdate(pos, state);
        if (level.getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity speaker) {
            speaker.setOwner(player);
            speaker.setName(name);
            speaker.updateOnline(level);
        }
    }

    /** Een sirene van {@code seconds} seconden: een toon die op en neer gaat ("wie-woe"), voor de foto's. */
    static short[] siren(double seconds) {
        short[] samples = new short[(int) (seconds * AlarmAudio.RATE)];
        double phase = 0;
        for (int i = 0; i < samples.length; i++) {
            double t = i / (double) AlarmAudio.RATE;
            double freq = 700 + 400 * Math.sin(2 * Math.PI * 0.5 * t);
            phase += 2 * Math.PI * freq / AlarmAudio.RATE;
            samples[i] = (short) Math.round(22000 * Math.sin(phase));
        }
        return samples;
    }

    private static void sound(ServerPlayer player, boolean on) {
        ImtRedstone.switchAt(player.serverLevel(), SPEAKER, -1, on ? ImtRedstone.Op.ON : ImtRedstone.Op.OFF, 0);
    }

    // ================================================================ De rondleiding

    static void tour(DevScript t) {
        double x = YARD.getX() + 0.5;
        double z = YARD.getZ() + 0.5;
        t.stand(0, x + 3, YARD.getY(), z + 5, 180, 10);
        t.waitUntil(600, () -> VisualCheck.raidLeaveTicking(YARD));
        t.command(0, "time set noon");
        t.step(0, () -> LanGrid.overrideRequired(true));
        t.onServer(10, AlarmTour::stage);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        // De speaker van dichtbij (ingezoomd: hij is klein): klaar (groen lampje), en afgaand na een alarm (rood licht).
        t.step(0, () -> zoom(true));
        // De ogen op de hoogte van de speaker (je voeten staan zo'n 1,6 blok lager), een klein beetje naar beneden kijkend.
        t.view(20, SPEAKER.getX() + 2.2, SPEAKER.getY() - 0.9, SPEAKER.getZ() + 3.4, 148, 4);
        t.shot(30, "al01_speaker_ready");
        t.onServer(0, player -> {
            if (player.serverLevel().getBlockEntity(CORE) instanceof SecurityCoreBlockEntity core) {
                core.logEvent(EventLog.Type.ALARM, "Intruder at Front door");
            }
        });
        t.waitUntil(80, () -> AlarmClient.playingAt(SPEAKER));
        t.shot(10, "al02_speaker_sounding");
        t.step(5, () -> zoom(false));
        // Het overzicht: de kern, de kabel, de speaker aan de muur en die op de vloer (allebei aan).
        t.onServer(0, player -> ImtRedstone.switchAt(player.serverLevel(), FLOOR_SPEAKER, -1, ImtRedstone.Op.ON, 0));
        t.view(10, x + 4.5, YARD.getY() + 2.4, z + 5.5, 180, 26);
        t.shot(25, "al03_overview_sounding");
        t.onServer(0, player -> {
            sound(player, false);
            ImtRedstone.switchAt(player.serverLevel(), FLOOR_SPEAKER, -1, ImtRedstone.Op.OFF, 0);
        });
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        // Rechtsklik: de zin boven je hotbar.
        t.stand(0, SPEAKER.getX() + 0.5, YARD.getY(), SPEAKER.getZ() + 2.5, 180, 25);
        t.onServer(10, player -> {
            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(SPEAKER) instanceof AlarmSpeakerBlockEntity speaker) {
                player.displayClientMessage(AlarmSpeakerBlock.status(level, SPEAKER, level.getBlockState(SPEAKER), speaker), true);
            }
        });
        t.shot(10, "al04_status_line");
        // Sneak + rechtsklik: zijn scherm, met het eigen geluid dat erop staat.
        t.onServer(30, player -> AlarmSpeakers.open(player, SPEAKER));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof AlarmSpeakerScreen);
        t.step(5, () -> {
            HowToTour.mouseTo(2, 2);
            if (Minecraft.getInstance().screen instanceof AlarmSpeakerScreen screen) {
                screen.tourUnfocus();
            }
        });
        t.step(0, () -> Minecraft.getInstance().getToasts().clear());
        t.shot(15, "al05_screen_with_a_sound");
        // Een bestand gekozen (zoals met Choose file): klaar om te uploaden.
        t.step(0, () -> {
            if (Minecraft.getInstance().screen instanceof AlarmSpeakerScreen screen) {
                screen.tourPending("siren_from_my_phone.mp4", siren(30.0));
                screen.tourUnfocus();
            }
        });
        t.shot(10, "al06_screen_file_ready");
        // Save: uploaden (de balk), en daarna gaat het scherm dicht met "gelukt" boven je hotbar.
        t.step(0, () -> {
            if (Minecraft.getInstance().screen instanceof AlarmSpeakerScreen screen) {
                screen.tourSave();
            }
        });
        t.shot(2, "al07_uploading");
        t.waitUntil(200, () -> !(Minecraft.getInstance().screen instanceof AlarmSpeakerScreen));
        t.shot(5, "al08_uploaded");
        // Het item in je inventaris met zijn tooltip (in survival: in creative opent het creative-menu), met een geluid erop.
        t.command(0, "gamemode survival @a");
        t.onServer(0, player -> {
            ItemStack stack = new ItemStack(ModItems.ALARM_SPEAKER.get());
            stack.set(ModDataComponents.ALARM_SETTINGS.get(), new AlarmSettings(AlarmSounds.idOf(1, new byte[Adpcm.encodedLength(1)]),
                    "air_raid_siren.mp4", 6.0f, 1f, 32, 30));
            player.getInventory().setItem(0, stack);
            player.getInventory().selected = 4;
        });
        t.open(10, () -> new InventoryScreen(Minecraft.getInstance().player));
        t.step(5, () -> {
            if (Minecraft.getInstance().screen instanceof InventoryScreen screen) {
                HowToTour.mouseTo(screen.getGuiLeft() + 16, screen.getGuiTop() + 150);
            }
        });
        t.shot(8, "al09_item_tooltip");
        t.close(5);
        t.onServer(0, player -> player.getInventory().setItem(0, ItemStack.EMPTY));
        t.command(0, "gamemode creative @a");
        t.step(0, () -> LanGrid.overrideRequired(false));
    }

    /**
     * Het plaatje van het echte scherm voor de animatie van How it works (DefenceScenes.alarm). Het komt in
     * src/main/resources te staan (zie HowToTour.capture).
     */
    static void pictures(DevScript t) {
        double x = YARD.getX() + 0.5;
        double z = YARD.getZ() + 0.5;
        t.stand(0, x + 3, YARD.getY(), z + 5, 180, 10);
        t.waitUntil(600, () -> VisualCheck.raidLeaveTicking(YARD));
        t.command(0, "time set noon");
        t.step(0, () -> LanGrid.overrideRequired(true));
        t.onServer(10, AlarmTour::stage);
        // Geen HUD achter het scherm: die schijnt anders door het paneel heen.
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.stand(0, SPEAKER.getX() + 0.5, YARD.getY(), SPEAKER.getZ() + 2.5, 180, 25);
        t.onServer(30, player -> AlarmSpeakers.open(player, SPEAKER));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof AlarmSpeakerScreen);
        t.step(5, () -> {
            HowToTour.mouseTo(2, 2);
            if (Minecraft.getInstance().screen instanceof AlarmSpeakerScreen screen) {
                screen.tourUnfocus();
            }
        });
        t.step(0, () -> Minecraft.getInstance().getToasts().clear());
        t.step(15, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof AlarmSpeakerScreen screen) {
                // Het paneel van AlarmSpeakerScreen: 300 x 226, in het midden.
                HowToTour.capture("alarm_screen", (screen.width - 300) / 2, Math.max(6, (screen.height - 226) / 2), 300, 226);
            } else {
                ImtTech.LOGGER.warn("[visual check] het scherm van de Alarm Speaker staat niet open (maar {})", mc.screen);
            }
        });
        t.close(5);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        t.step(0, () -> LanGrid.overrideRequired(false));
    }

    // ================================================================ De showcase

    /** Waar je staat in de showcase: ten zuiden van de speakers, met de kern links. */
    static final Vec3 SHOW_FROM = Vec3.atBottomCenterOf(YARD.offset(5, 0, 6));

    /**
     * De showcase-functie "Alarm Speaker": het toneel, een alarm van de kern (beide speakers gaan af, de ene met een eigen
     * geluid, de andere met de ingebouwde sirene), de speaker stopt, zijn scherm, en hem op zijn naam aan en uit zetten.
     */
    static void showcase(DevScript s) {
        s.command(0, "time set noon")
                .step(0, () -> LanGrid.overrideRequired(true))
                .onServer(0, AlarmTour::stage)
                .pause(80)
                .onServer(0, player -> {
                    if (player.serverLevel().getBlockEntity(CORE) instanceof SecurityCoreBlockEntity core) {
                        core.logEvent(EventLog.Type.ALARM, "Intruder at Front door");
                    }
                })
                .pause(200)
                .onServer(0, player -> {
                    sound(player, false);
                    ImtRedstone.switchAt(player.serverLevel(), FLOOR_SPEAKER, -1, ImtRedstone.Op.OFF, 0);
                })
                .pause(30)
                .stand(0, SPEAKER.getX() + 0.5, YARD.getY(), SPEAKER.getZ() + 2.5, 180, 25)
                .onServer(10, player -> AlarmSpeakers.open(player, SPEAKER))
                .pause(160)
                .close(10)
                .onServer(0, player -> ImtRedstone.switchAt(player.serverLevel(), SPEAKER, -1, ImtRedstone.Op.PULSE, 60))
                .pause(100)
                .step(0, () -> LanGrid.overrideRequired(false));
    }

    // ================================================================ Hulpjes

    private static int normalFov = 70;

    /** Inzoomen (de smalste kijkhoek van Minecraft: 30 graden) voor een foto van iets kleins, of weer terug. */
    private static void zoom(boolean in) {
        var fov = Minecraft.getInstance().options.fov();
        if (in) {
            normalFov = fov.get();
            fov.set(30);
        } else {
            fov.set(normalFov);
        }
    }
}
