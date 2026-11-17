package com.senne.imttech.client.dev;

import com.senne.imttech.ImtTech;
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
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlock;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.VirtualFs;
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
 * De rondleiding van de Speaker (hoofdstuk 126; sinds hoofdstuk 136 speelt hij wat een diagram zegt):
 * {@code gradlew runVisualCheck -Ponly=alarm}, en het plaatje van zijn scherm voor How it works met
 * {@code -Ponly=howtopics:alarm} (dat verandert een bestand van de mod).
 *
 * Het toneel: een Security Core met een geluid in /sounds (air_raid_siren) en een PoE-kabel naar een speaker aan een
 * muurtje en een speaker op de vloer. De foto's: de speaker klaar (groen lampje), spelend (rood licht) na
 * {@code playsound("air_raid_siren", "Speaker 1")}, het overzicht na {@code playsound("default", "all")}, de zin boven
 * je hotbar (rechtsklik), zijn scherm, Files met de map sounds, de chatregel van {@code playtext} (de rondleiding heeft de
 * stem van I.M.T niet) en de tooltip van het item.
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
        speaker(player, SPEAKER, AttachFace.WALL, Direction.SOUTH, "Speaker 1");
        speaker(player, FLOOR_SPEAKER, AttachFace.FLOOR, Direction.NORTH, "Speaker 2");
        // Een geluid in /sounds van de kern (zoals na +Sound in Files): een sirene die we hier maken.
        short[] siren = siren(6.0);
        byte[] adpcm = Adpcm.encode(siren);
        AlarmSounds.store(level.getServer(), siren.length, adpcm, id -> {
            if (id != null && level.getBlockEntity(CORE) instanceof SecurityCoreBlockEntity core && core.fs().sound(SOUND) == null) {
                core.fs().putSound(VirtualFs.SOUNDS, SOUND, id, siren.length, siren.length / (float) AlarmAudio.RATE);
                core.setChanged();
            }
        });
    }

    /** De naam van het geluid in /sounds. */
    private static final String SOUND = "air_raid_siren";

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

    /** Zoals een diagram: playsound(sound, target) op de kern van het toneel. */
    private static void playSound(ServerPlayer player, String sound, String target) {
        if (player.serverLevel().getBlockEntity(CORE) instanceof SecurityCoreBlockEntity core) {
            AlarmSpeakers.playSound(player.serverLevel(), core, sound, target);
        }
    }

    /** Zoals een diagram: playtext(text, target) op de kern van het toneel. */
    private static void playText(ServerPlayer player, String text, String target) {
        if (player.serverLevel().getBlockEntity(CORE) instanceof SecurityCoreBlockEntity core) {
            AlarmSpeakers.playText(player.serverLevel(), core, text, target);
        }
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
        // De speaker van dichtbij (ingezoomd: hij is klein): klaar (groen lampje), en spelend (rood licht) als een diagram
        // playsound(...) zegt: vanzelf speelt hij nooit iets (hoofdstuk 128).
        t.step(0, () -> zoom(true));
        // De ogen op de hoogte van de speaker (je voeten staan zo'n 1,6 blok lager), een klein beetje naar beneden kijkend.
        t.view(20, SPEAKER.getX() + 2.2, SPEAKER.getY() - 0.9, SPEAKER.getZ() + 3.4, 148, 4);
        t.shot(30, "sp01_speaker_ready");
        t.onServer(0, player -> playSound(player, SOUND, "Speaker 1"));
        t.waitUntil(80, () -> AlarmClient.playingAt(SPEAKER));
        t.shot(10, "sp02_speaker_playing");
        t.step(5, () -> zoom(false));
        // Het overzicht: de kern, de kabel, de speaker aan de muur en die op de vloer (allebei de ingebouwde sirene).
        t.onServer(0, player -> playSound(player, "default", "all"));
        t.view(10, x + 4.5, YARD.getY() + 2.4, z + 5.5, 180, 26);
        t.shot(25, "sp03_overview_playing");
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        // Rechtsklik: de zin boven je hotbar.
        t.stand(40, SPEAKER.getX() + 0.5, YARD.getY(), SPEAKER.getZ() + 2.5, 180, 25);
        t.onServer(10, player -> {
            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(SPEAKER) instanceof AlarmSpeakerBlockEntity speaker) {
                player.displayClientMessage(AlarmSpeakerBlock.status(level, SPEAKER, level.getBlockState(SPEAKER), speaker), true);
            }
        });
        t.shot(10, "sp04_status_line");
        // Sneak + rechtsklik: zijn scherm (naam, hoe hard, hoe ver, Test, en waar de geluiden staan).
        t.onServer(30, player -> AlarmSpeakers.open(player, SPEAKER));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof AlarmSpeakerScreen);
        t.step(5, () -> {
            HowToTour.mouseTo(2, 2);
            if (Minecraft.getInstance().screen instanceof AlarmSpeakerScreen screen) {
                screen.tourUnfocus();
            }
        });
        t.step(0, () -> Minecraft.getInstance().getToasts().clear());
        t.shot(15, "sp05_speaker_screen");
        t.close(5);
        // Files op de kern, in de map sounds: het geluid met zijn lengte, en de knop +Sound.
        t.onServer(10, player -> com.senne.imttech.security.os.SecurityOs.open(player, CORE));
        t.waitUntil(80, () -> Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen);
        t.step(10, () -> {
            if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen screen) {
                screen.tourFiles(VirtualFs.SOUNDS, VirtualFs.SOUNDS + "/" + SOUND, false);
            }
        });
        t.shot(15, "sp06_files_sounds");
        t.close(5);
        // playtext: de rondleiding heeft de stem van I.M.T niet, dus staat de zin in de chat.
        t.onServer(5, player -> playText(player, "Alarm armed", "all"));
        t.shot(20, "sp07_playtext_chat");
        // Het item in je inventaris met zijn tooltip (in survival: in creative opent het creative-menu).
        t.command(0, "gamemode survival @a");
        t.onServer(0, player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALARM_SPEAKER.get()));
            player.getInventory().selected = 4;
        });
        t.open(10, () -> new InventoryScreen(Minecraft.getInstance().player));
        t.step(5, () -> {
            if (Minecraft.getInstance().screen instanceof InventoryScreen screen) {
                HowToTour.mouseTo(screen.getGuiLeft() + 16, screen.getGuiTop() + 150);
            }
        });
        t.shot(8, "sp08_item_tooltip");
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
                // Het paneel van AlarmSpeakerScreen: 300 x 186, in het midden.
                HowToTour.capture("alarm_screen", (screen.width - 300) / 2, Math.max(6, (screen.height - 186) / 2), 300, 186);
            } else {
                ImtTech.LOGGER.warn("[visual check] het scherm van de Speaker staat niet open (maar {})", mc.screen);
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
     * De showcase-functie "Speaker": het toneel, playsound op Speaker 1 (het geluid uit /sounds), playsound("default", "all")
     * op beide, playtext("Alarm armed", "all"), en zijn scherm.
     */
    static void showcase(DevScript s) {
        s.command(0, "time set noon")
                .step(0, () -> LanGrid.overrideRequired(true))
                .onServer(0, AlarmTour::stage)
                .pause(80)
                .onServer(0, player -> playSound(player, SOUND, "Speaker 1"))
                .pause(140)
                .onServer(0, player -> playSound(player, "default", "all"))
                .pause(60)
                .onServer(0, player -> playText(player, "Alarm armed", "all"))
                .pause(80)
                .stand(0, SPEAKER.getX() + 0.5, YARD.getY(), SPEAKER.getZ() + 2.5, 180, 25)
                .onServer(10, player -> AlarmSpeakers.open(player, SPEAKER))
                .pause(160)
                .close(10)
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
