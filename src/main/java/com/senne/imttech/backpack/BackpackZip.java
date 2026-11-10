package com.senne.imttech.backpack;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.registry.ModSounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;

/**
 * De rits (hoofdstuk 72, jouw keuze: "openen kost tijd"). Een rugzak open je niet meteen: eerst rits je hem open, en dat
 * duurt {@code [backpacks] zipSeconds} (1,5 s). Je moet stilstaan; beweeg je meer dan {@code zipMoveTolerance}, dan stopt
 * de rits ("Hold still to unzip"). Je hoort het ritsen, en wie bij je staat ook. Op je scherm loopt een balkje
 * (BackpackZipPayload, client/backpack/BackpackClient).
 *
 * Dat geldt voor elke rugzak: op je rug, in je hand, neergezet, en (stap 3 en 4) die van een ander of van een lichaam.
 */
public final class BackpackZip {
    private BackpackZip() {}

    private static final class Job {
        final BackpackPlace place;
        final Vec3 start;
        final int total;
        int left;

        Job(BackpackPlace place, Vec3 start, int total) {
            this.place = place;
            this.start = start;
            this.total = total;
            this.left = total;
        }
    }

    private static final Map<UUID, Job> JOBS = new HashMap<>();

    /** Begin met openritsen. Geeft false als er daar geen rugzak is (of de speler er niet bij kan). */
    public static boolean start(ServerPlayer player, BackpackPlace place) {
        if (!BackpackItem.isBackpack(place.get()) || !place.reachable(player)) {
            return false;
        }
        Job running = JOBS.get(player.getUUID());
        if (running != null) {
            return true; // je bent al aan het ritsen
        }
        int ticks = Math.round(ImtConfig.Backpacks.ZIP_SECONDS * 20);
        if (ticks <= 0) {
            return open(player, place);
        }
        JOBS.put(player.getUUID(), new Job(place, player.position(), ticks));
        ModNetwork.sendTo(player, new BackpackZipPayload(ticks));
        Vec3 at = place.position();
        // Van achteren (stap 3) rits je zachter, maar hij kan het nog horen.
        float volume = place instanceof BackpackPlace.Theft ? ImtConfig.Backpacks.THEFT_ZIP_VOLUME : 0.7f;
        player.serverLevel().playSound(null, at.x, at.y, at.z, ModSounds.BACKPACK_ZIP.get(), SoundSource.PLAYERS, volume,
                0.95f + player.getRandom().nextFloat() * 0.1f);
        return true;
    }

    /** Is deze speler aan het ritsen? */
    public static boolean busy(ServerPlayer player) {
        return JOBS.containsKey(player.getUUID());
    }

    /** Stoppen (je bewoog, of de rugzak is weg). */
    public static void cancel(ServerPlayer player, Component why) {
        if (JOBS.remove(player.getUUID()) != null) {
            ModNetwork.sendTo(player, new BackpackZipPayload(0));
            if (why != null) {
                player.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /** Elke tick van de server: de ritsen aftellen. */
    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Job>> it = JOBS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Job> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Job job = entry.getValue();
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            Component why = null;
            double moved = Math.sqrt(Math.pow(player.getX() - job.start.x, 2) + Math.pow(player.getZ() - job.start.z, 2));
            if (moved > ImtConfig.Backpacks.ZIP_MOVE_TOLERANCE || Math.abs(player.getY() - job.start.y) > 0.6) {
                why = Component.translatable("backpack.imttech.hold_still");
            } else if (!BackpackItem.isBackpack(job.place.get()) || !job.place.reachable(player)) {
                why = Component.translatable("backpack.imttech.gone");
            }
            if (why != null) {
                it.remove();
                ModNetwork.sendTo(player, new BackpackZipPayload(0));
                player.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
                continue;
            }
            if (--job.left <= 0) {
                it.remove();
                ModNetwork.sendTo(player, new BackpackZipPayload(0));
                open(player, job.place);
            }
        }
    }

    /** Het scherm openen (na de rits). */
    public static boolean open(ServerPlayer player, BackpackPlace place) {
        BackpackInventory inventory = BackpackInventory.open(place);
        if (inventory == null) {
            return false;
        }
        BackpackItem.Size size = inventory.size();
        int locked = place.lockedSlot(player);
        boolean theft = place instanceof BackpackPlace.Theft;
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (id, playerInventory, p) -> new BackpackMenu(id, playerInventory, size, inventory, locked, theft), place.title());
        if (player.connection != null && player.connection.hasChannel(AdvancedOpenScreenPayload.TYPE)) {
            player.openMenu(provider, buf -> {
                buf.writeVarInt(size.ordinal());
                buf.writeVarInt(locked);
                buf.writeBoolean(theft);
            });
        } else {
            // Een nep-speler (de automatische tests) heeft geen spel om de maat naar te sturen: gewoon openen.
            player.openMenu(provider);
        }
        return true;
    }

    /** Alles vergeten (de server stopt). */
    public static void clear() {
        JOBS.clear();
    }
}
