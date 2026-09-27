package com.senne.starktech.security;

import com.senne.starktech.network.HackCommandPayload;
import com.senne.starktech.network.HackScreenPayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * De Stark Tower overnemen met een laptop.
 *
 * 1. Je klikt met de laptop op de beveiligingskern: je zet hem opengeklapt bovenop de kern
 *    (StarkLaptopBlock) en de hack begint. Het alarm gaat af, en alle beveiligingsdrones van de
 *    toren komen op je af.
 * 2. De terminal gaat open (client/HackScreen, de puzzel zelf is HackTerminal). Elk commando dat je
 *    typt, gaat naar de laptop en die onthoudt het. Het spel loopt door: terwijl jij typt,
 *    schieten de torentjes en drones gewoon op je.
 * 3. Sluit je het scherm (Esc), loop je weg (verder dan MAX_DISTANCE) of ga je dood, dan gaat
 *    alleen het scherm dicht. Je voortgang staat op de laptop: rechtsklik erop en je gaat verder
 *    waar je was.
 * 4. Na elk commando speelt de server alles na. Is het beveiligingsprogramma nu van jou (en duurde
 *    het niet onmogelijk kort), dan is de toren van jou en krijg je de laptop terug.
 */
public final class TowerHack {
    private TowerHack() {}

    /** Zo dicht bij de laptop moet je blijven om te typen (in blokken). */
    public static final double MAX_DISTANCE = 6.0;
    /** Sneller dan dit kan niemand de hack uitspelen (in ticks): anders klopt er iets niet. */
    public static final int MIN_TICKS = 160;

    /** Wie nu de terminal van welke laptop open heeft. */
    private static final Map<UUID, BlockPos> VIEWING = new HashMap<>();

    /**
     * Met de laptop op de kern geklikt: hem erop zetten en de hack beginnen. Staat jouw laptop
     * er al, dan ga je gewoon verder. Geeft true als er iets gebeurde.
     */
    public static boolean placeLaptop(ServerPlayer player, BlockPos corePos, ItemStack laptopItem) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(corePos) instanceof SecurityCoreBlockEntity core)) {
            return false;
        }
        BlockPos top = corePos.above();
        if (level.getBlockState(top).is(ModBlocks.STARK_LAPTOP.get())) {
            open(player, top);
            return true;
        }
        if (SecurityCores.isFriend(level, core, player)) {
            player.displayClientMessage(Component.translatable("security.starktech.msg.already_yours")
                    .withStyle(ChatFormatting.AQUA), true);
            return false;
        }
        if (!level.getBlockState(top).canBeReplaced()) {
            player.displayClientMessage(Component.translatable("security.starktech.msg.no_room")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Opengeklapt, met het scherm naar jou toe.
        level.setBlockAndUpdate(top, ModBlocks.STARK_LAPTOP.get().defaultBlockState()
                .setValue(StarkLaptopBlock.FACING, player.getDirection().getOpposite()));
        if (!(level.getBlockEntity(top) instanceof StarkLaptopBlockEntity laptop)) {
            return false;
        }
        laptop.start(player, level.random.nextLong(), level.getGameTime());
        if (!player.getAbilities().instabuild) {
            laptopItem.shrink(1);
        }
        level.playSound(null, top, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1.0f, 1.2f);
        alarm(level, corePos, core, player);
        open(player, top);
        return true;
    }

    /** Alarm! De drones van deze kern komen allemaal op je af. */
    private static void alarm(ServerLevel level, BlockPos corePos, SecurityCoreBlockEntity core, ServerPlayer player) {
        level.playSound(null, corePos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 0.6f);
        if (SecurityCores.isIntruder(level, core, player)) {
            for (SecurityDroneEntity drone : level.getEntitiesOfClass(SecurityDroneEntity.class, core.area(),
                    drone -> corePos.equals(drone.home()))) {
                drone.setTarget(player);
            }
        }
        player.displayClientMessage(Component.translatable("security.starktech.msg.hack_started")
                .withStyle(ChatFormatting.RED), true);
    }

    /** De terminal van deze laptop openen, met alles wat je al typte. */
    public static void open(ServerPlayer player, BlockPos laptopPos) {
        if (!(player.serverLevel().getBlockEntity(laptopPos) instanceof StarkLaptopBlockEntity laptop)) {
            return;
        }
        if (!laptop.isOwner(player)) {
            player.displayClientMessage(Component.translatable("security.starktech.msg.not_your_laptop",
                    laptop.ownerName()).withStyle(ChatFormatting.RED), true);
            return;
        }
        VIEWING.put(player.getUUID(), laptopPos.immutable());
        ModNetwork.sendTo(player, new HackScreenPayload(laptopPos, true, laptop.seed(),
                List.copyOf(laptop.commands())));
    }

    /** Je typte een commando: de laptop onthoudt het, en de server kijkt of je klaar bent. */
    public static void handleCommand(ServerPlayer player, HackCommandPayload payload) {
        BlockPos pos = payload.pos();
        ServerLevel level = player.serverLevel();
        if (!pos.equals(VIEWING.get(player.getUUID())) || !level.isLoaded(pos) || !closeEnough(player, pos)
                || !(level.getBlockEntity(pos) instanceof StarkLaptopBlockEntity laptop) || !laptop.isOwner(player)) {
            return;
        }
        String command = payload.command().strip();
        if (command.isEmpty() || !laptop.add(command)) {
            return;
        }
        if (laptop.terminal().stage() == HackTerminal.Stage.DONE) {
            finish(player, level, pos, laptop);
        }
    }

    /** Het beveiligingsprogramma is van jou: de toren ook, en de laptop gaat terug in je rugzak. */
    private static void finish(ServerPlayer player, ServerLevel level, BlockPos laptopPos, StarkLaptopBlockEntity laptop) {
        BlockPos corePos = laptopPos.below();
        if (!(level.getBlockEntity(corePos) instanceof SecurityCoreBlockEntity core)) {
            return;
        }
        long took = level.getGameTime() - laptop.startedAt();
        if (took < MIN_TICKS || !player.isAlive()) {
            // Onmogelijk snel: een valsspelend spel. Dan een nieuwe puzzel, en opnieuw beginnen.
            laptop.start(player, level.random.nextLong(), level.getGameTime());
            VIEWING.remove(player.getUUID());
            ModNetwork.sendTo(player, HackScreenPayload.close(laptopPos));
            player.displayClientMessage(Component.translatable("security.starktech.msg.hack_failed")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        core.setOwner(player);
        level.playSound(null, corePos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.4f);
        player.displayClientMessage(Component.translatable("security.starktech.msg.hack_done")
                .withStyle(ChatFormatting.GREEN), true);
        player.sendSystemMessage(Component.translatable("security.starktech.msg.tower_yours")
                .withStyle(ChatFormatting.AQUA));
        // Wie jou nu nog aanvalt, stopt daarmee.
        for (SecurityDroneEntity drone : level.getEntitiesOfClass(SecurityDroneEntity.class, core.area(),
                drone -> corePos.equals(drone.home()))) {
            drone.setTarget(null);
        }
        // Het scherm laat zelf nog even "ACCESS GRANTED" zien; daarna gaat het dicht.
        VIEWING.remove(player.getUUID());
        level.removeBlock(laptopPos, false);
        ItemStack back = new ItemStack(ModItems.STARK_LAPTOP.get());
        if (!player.getInventory().add(back)) {
            player.drop(back, false);
        }
    }

    /** Elke seconde: sta je nog bij de laptop, en leef je nog? */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            check(player);
        }
    }

    /** Weggelopen of dood: het scherm gaat dicht (je voortgang blijft op de laptop). */
    public static void check(ServerPlayer player) {
        BlockPos laptop = VIEWING.get(player.getUUID());
        if (laptop == null) {
            return;
        }
        if (!player.isAlive() || !closeEnough(player, laptop)) {
            VIEWING.remove(player.getUUID());
            ModNetwork.sendTo(player, HackScreenPayload.close(laptop));
            player.displayClientMessage(Component.translatable("security.starktech.msg.hack_paused")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    /** De laptop is weg (afgebroken, of de kern is weg): wie hem open had, krijgt het scherm dicht. */
    public static void laptopGone(Level level, BlockPos pos) {
        if (level.getServer() == null) {
            return;
        }
        VIEWING.entrySet().removeIf(entry -> {
            if (!entry.getValue().equals(pos)) {
                return false;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                ModNetwork.sendTo(player, HackScreenPayload.close(pos));
            }
            return true;
        });
    }

    private static boolean closeEnough(ServerPlayer player, BlockPos laptop) {
        return player.position().distanceTo(Vec3.atCenterOf(laptop)) <= MAX_DISTANCE;
    }

    /** Heeft deze speler nu de terminal van een laptop open? (Voor de test.) */
    public static boolean isHacking(ServerPlayer player) {
        return VIEWING.containsKey(player.getUUID());
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        VIEWING.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        VIEWING.clear();
    }
}
