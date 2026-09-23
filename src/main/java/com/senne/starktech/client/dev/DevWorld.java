package com.senne.starktech.client.dev;

import com.mojang.authlib.GameProfile;
import com.senne.starktech.StarkTech;
import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.arsenal.EdithArsenal;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithOwner;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.jetbrains.annotations.Nullable;

/**
 * Hulpjes voor de testwereld van de screenshot-rondleiding en de showcase: een platte wereld
 * maken, iets op de server uitvoeren, wezens neerzetten, de bril opzetten...
 */
public final class DevWorld {
    private DevWorld() {}

    /** Gooit de testwereld van vorige keer weg en maakt een nieuwe, platte wereld. */
    public static void createFlatWorld(Minecraft mc, String name) {
        Path folder = mc.gameDirectory.toPath().resolve("saves").resolve(name);
        if (Files.exists(folder)) {
            try (Stream<Path> files = Files.walk(folder)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            } catch (IOException e) {
                StarkTech.LOGGER.warn("[dev] oude wereld niet weg te krijgen", e);
            }
        }
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.EASY, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }

    /** Geen tutorial, niet pauzeren als het venster de focus kwijt is, eventueel zonder geluid. */
    public static void quietOptions(Minecraft mc, boolean mute) {
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        // Anders staat de uitleg "Move with W, A, S and D" over de helft van het beeld.
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.getTutorial().setStep(TutorialSteps.NONE);
        // Geen chat in beeld: die stond over de voeten (en straalvlammen) van het harnas.
        mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        if (mute) {
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        }
        mc.options.save();
    }

    /** Voert iets uit op de server (in hetzelfde spel), met de echte speler daar. */
    public static void onServer(Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                action.accept(player);
            }
        });
    }

    public static void command(String command) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(), command));
        }
    }

    /** Een wezen dat stil blijft staan waar je het neerzet (geen eigen wil). */
    public static @Nullable Entity spawnStill(ServerLevel level, EntityType<? extends Entity> type, BlockPos pos, float yaw) {
        Entity entity = type.create(level);
        if (entity == null) {
            return null;
        }
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        entity.setYHeadRot(yaw);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.yBodyRot = yaw;
        }
        level.addFreshEntity(entity);
        return entity;
    }

    /**
     * Een rij stilstaande drones, van heel tot bijna kapot, zodat je de schade naast elkaar ziet:
     * 100%, 70%, 45% en 15% leven, en achteraan een gehavende raiddrone.
     */
    public static void damagedDroneRow(ServerLevel level, BlockPos leftmost, float yaw) {
        float[] health = {1.0f, 0.7f, 0.45f, 0.15f};
        for (int i = 0; i < health.length; i++) {
            Entity drone = spawnStill(level, com.senne.starktech.registry.ModEntities.STARK_DRONE.get(),
                    leftmost.offset(i * 2, 0, 0), yaw);
            if (drone instanceof net.minecraft.world.entity.LivingEntity living) {
                living.setHealth(living.getMaxHealth() * health[i]);
            }
        }
        Entity raid = spawnStill(level, com.senne.starktech.registry.ModEntities.RAID_DRONE.get(),
                leftmost.offset(health.length * 2, 0, 0), yaw);
        if (raid instanceof net.minecraft.world.entity.LivingEntity living) {
            living.setHealth(living.getMaxHealth() * 0.2f);
        }
    }

    /** Een nieuwe, volle bril die van deze speler is. */
    public static ItemStack ownGlasses(ServerPlayer player) {
        ItemStack glasses = new ItemStack(ModItems.EDITH_GLASSES.get());
        EdithData.setEnergy(glasses, EdithData.getMaxEnergy(glasses));
        EdithData.setOwner(glasses, new EdithOwner(player.getUUID(), player.getGameProfile().getName(), false));
        return glasses;
    }

    /** Zet de speler een eigen, volle bril op. */
    public static void wearGlasses(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, ownGlasses(player));
    }

    /** Vult de bril die de speler op heeft weer helemaal op. */
    public static void refill(ServerPlayer player) {
        ItemStack glasses = EdithData.getWornGlasses(player);
        if (!glasses.isEmpty()) {
            EdithData.setEnergy(glasses, EdithData.getMaxEnergy(glasses));
        }
    }

    /** Maakt het arsenaal leeg en zet er {@code count} nieuwe drones in. */
    public static void freshArsenal(ServerPlayer player, int count) {
        player.setData(ModAttachments.ARSENAL, EdithArsenal.EMPTY);
        for (int i = 0; i < count; i++) {
            ArsenalServer.add(player);
        }
    }

    /**
     * Een tweede speler zonder echt spel erachter (zoals de automatische tests er een maken).
     * Hij staat stil, maar voor de mod is hij een gewone speler: je kunt hem hacken,
     * bespioneren en berichten sturen.
     */
    public static ServerPlayer fakePlayer(ServerLevel level, String name, BlockPos pos) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.nameUUIDFromBytes(("dev-" + name).getBytes()), name), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180, 0);
        return player;
    }
}
