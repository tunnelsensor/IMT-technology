package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerCables;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.logic.CodePanelBlock;
import com.senne.imttech.logic.KeycardReaderBlock;
import com.senne.imttech.network.EmpBlastPayload;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.security.CameraBlockEntity;
import com.senne.imttech.shield.ImtDomes;
import com.senne.imttech.suit.TechSuitItem;
import com.senne.imttech.turret.TurretBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De EMP-puls van een EMP-granaat (hoofdstuk 35, jouw wens: "deze schakelen al de elektrische dingen uit zoals robots
 * maar ook turrets, drones, openliggende elektrische wires"). Alles binnen een bol rond de granaat valt een tijdje uit
 * en gaat daarna vanzelf weer aan.
 *
 * Er zijn twee soorten dingen:
 * - Wat stilstaat (turrets, camera's, keycardlezers, codepanelen, kabels die open liggen) vraagt zelf
 *   {@link #isOut}: ligt het binnen een puls die nog bezig is? Daarvoor onthouden we per wereld de pulsen.
 * - Wat beweegt (robots, drones, spelers) krijgt bij de ontploffing een tijd mee (het attachment EMP_UNTIL), want
 *   een drone die neerstort, blijft ook buiten de bol nog even uit. Zie {@link #isJammed}.
 *
 * Een kabel IN een muur (in een volledig blok) is beschermd, en een schildkoepel beschermt alles erin tegen een
 * granaat die buiten de koepel afgaat (dezelfde regel als bij schoten, zie ImtDomes.blockedAt). Rayshields,
 * laserbruggen, koepels en lampen laat hij met rust (jouw keuze), en de lasergeweren ook.
 */
public final class Emp {
    private Emp() {}

    /** Eén puls: waar hij afging, hoe groot, tot wanneer (speltijd), en van wie de granaat was (of null). */
    public record Pulse(Vec3 center, double radius, long until, @Nullable UUID thrower) {
        boolean reaches(Vec3 pos) {
            return pos.distanceToSqr(center) <= radius * radius;
        }
    }

    /** Hooguit zoveel apparaten laten we tegelijk "uitzakken" (geluid), anders klinkt het als één brij. */
    private static final int MAX_SOUNDS = 6;

    /** Per wereld de pulsen die nog bezig zijn. */
    private static final Map<ResourceKey<Level>, List<Pulse>> PULSES = new HashMap<>();

    // ================================================================ Afgaan

    /** Een EMP-granaat gaat hier af, met de grootte en duur uit de config. Geeft hoeveel dingen hij uitschakelde. */
    public static int blast(ServerLevel level, Vec3 center, @Nullable Entity thrower) {
        int ticks = Math.max(1, Math.round(ImtConfig.Weapons.EMP_SECONDS * 20));
        return blast(level, center, ImtConfig.Weapons.EMP_RADIUS, ticks, thrower == null ? null : thrower.getUUID());
    }

    /** Een EMP-puls: alles binnen {@code radius} blokken van {@code center} valt {@code ticks} lang uit. */
    public static int blast(ServerLevel level, Vec3 center, double radius, int ticks, @Nullable UUID thrower) {
        long until = level.getGameTime() + ticks;
        Pulse pulse = new Pulse(center, radius, until, thrower);
        PULSES.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(pulse);
        // De kabels die nu dood zijn: alle netten opnieuw zoeken.
        PowerGrid.changed();

        int hit = 0;
        int sounds = 0;
        // Wat beweegt: robots, drones en spelers.
        for (Entity entity : level.getEntities((Entity) null, new AABB(center, center).inflate(radius),
                entity -> entity.isAlive() && !entity.isSpectator())) {
            Vec3 at = entity.getBoundingBox().getCenter();
            if (!pulse.reaches(at) || shielded(level, pulse, at)) {
                continue;
            }
            if (entity instanceof EmpTarget target) {
                entity.setData(ModAttachments.EMP_UNTIL, until);
                target.onEmp(level, ticks);
                sparks(level, at, 20);
                if (sounds++ < MAX_SOUNDS) {
                    powerDown(level, at);
                }
                hit++;
            } else if (entity instanceof ServerPlayer player && hitPlayer(player, until, ticks)) {
                hit++;
            }
        }
        // Wat stilstaat: turrets, camera's, lezers en panelen. Die kijken zelf naar isOut; hier alleen meteen bijwerken.
        for (BlockEntity device : blockEntitiesNear(level, center, radius)) {
            BlockPos pos = device.getBlockPos();
            if (!pulse.reaches(Vec3.atCenterOf(pos)) || shielded(level, pulse, Vec3.atCenterOf(pos))) {
                continue;
            }
            boolean electric = true;
            if (device instanceof TurretBlockEntity turret) {
                turret.refreshOnline(level);
            } else if (device instanceof CameraBlockEntity camera) {
                camera.updateOnline(level, pos, device.getBlockState());
            } else if (device instanceof com.senne.imttech.security.wall.CameraMonitorBlockEntity monitor) {
                monitor.updateOn(level);
            } else {
                Block block = device.getBlockState().getBlock();
                // Hoofdstuk 38: ook een Network Switch valt even uit (zie NetworkSwitchBlock.passes).
                electric = block instanceof KeycardReaderBlock || block instanceof CodePanelBlock
                        || block instanceof com.senne.imttech.lan.NetworkSwitchBlock;
            }
            if (electric) {
                sparks(level, Vec3.atCenterOf(pos), 12);
                if (sounds++ < MAX_SOUNDS) {
                    powerDown(level, Vec3.atCenterOf(pos));
                }
                hit++;
            }
        }
        // De kabels die open liggen: vonken (hun stroom stopt vanzelf, zie PowerGrid).
        cableSparks(level, pulse, 24);

        level.playSound(null, center.x, center.y, center.z, ModSounds.EMP_BLAST.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 60, radius * 0.3, radius * 0.3,
                radius * 0.3, 0.4);
        ModNetwork.sendNear(level, center, 96, new EmpBlastPayload(center, (float) radius));
        return hit;
    }

    /** Een speler in de bol: zijn harnas en bril vallen uit. Geeft true als hij iets droeg dat uitviel. */
    private static boolean hitPlayer(ServerPlayer player, long until, int ticks) {
        player.setData(ModAttachments.EMP_UNTIL, until);
        boolean glasses = !ImtData.getWornGlasses(player).isEmpty();
        boolean suit = TechSuitItem.wornPieces(player) > 0;
        if (!glasses && !suit) {
            return false;
        }
        sparks(player.serverLevel(), player.getBoundingBox().getCenter(), 16);
        powerDown(player.serverLevel(), player.position());
        ImtServer.send(player, Component.translatable("weapon.imttech.emp.player_hit", Mth.ceil(ticks / 20f))
                .withStyle(ChatFormatting.AQUA), ImtMessagePayload.Level.ALERT);
        return true;
    }

    // ================================================================ Opvragen

    /**
     * Ligt deze plek binnen een puls die nog bezig is (en niet achter een schildkoepel)? Voor wat stilstaat: turrets,
     * camera's, lezers, panelen en kabels. Alleen op de server; in je eigen spel altijd false.
     */
    public static boolean isOut(Level level, BlockPos pos) {
        List<Pulse> pulses = PULSES.get(level.dimension());
        if (pulses == null || pulses.isEmpty() || !(level instanceof ServerLevel server)) {
            return false;
        }
        Vec3 at = Vec3.atCenterOf(pos);
        long now = level.getGameTime();
        for (Pulse pulse : pulses) {
            if (pulse.until > now && pulse.reaches(at) && !shielded(server, pulse, at)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Is de kabel op deze plek doorgebrand? Alleen een kabel die open ligt (niet in een volledig blok) en binnen een
     * puls. Geldt voor elke soort kabel (stroom, netwerk, PoE). (Of er echt een kabel ligt, kijken PowerGrid en het
     * netwerk zelf.)
     */
    public static boolean cutsCable(Level level, BlockPos pos) {
        List<Pulse> pulses = PULSES.get(level.dimension());
        return pulses != null && !pulses.isEmpty() && !PowerCables.isSolid(level, pos) && isOut(level, pos);
    }

    /** Is dit wezen (een drone, robot of speler) nu uitgeschakeld door een EMP? Werkt ook in je eigen spel. */
    public static boolean isJammed(Entity entity) {
        return entity.getData(ModAttachments.EMP_UNTIL) > entity.level().getGameTime();
    }

    /** Hoeveel ticks dit wezen nog uit is (0 = niet). */
    public static int ticksLeft(Entity entity) {
        return (int) Math.max(0, entity.getData(ModAttachments.EMP_UNTIL) - entity.level().getGameTime());
    }

    /**
     * Een lezer of paneel dat uit is: vonkjes en "dood" als melding. Geeft true als het apparaat uit is (dan moet de
     * aanroeper verder niets doen).
     */
    public static boolean deadDevice(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (!isOut(level, pos)) {
            return false;
        }
        sparks(level, Vec3.atCenterOf(pos), 6);
        if (player != null) {
            player.displayClientMessage(Component.translatable("weapon.imttech.emp.device_dead")
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return true;
    }

    // ================================================================ Elke tick

    /** Pulsen die voorbij zijn vergeten (de kabels werken dan weer), en zolang ze bezig zijn: knetterende kabels. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PULSES.isEmpty()) {
            return;
        }
        boolean ended = false;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<Pulse> pulses = PULSES.get(level.dimension());
            if (pulses == null) {
                continue;
            }
            long now = level.getGameTime();
            for (Iterator<Pulse> it = pulses.iterator(); it.hasNext(); ) {
                Pulse pulse = it.next();
                if (pulse.until <= now) {
                    it.remove();
                    ended = true;
                } else if (now % 10 == 0) {
                    cableSparks(level, pulse, 3);
                }
            }
            if (pulses.isEmpty()) {
                PULSES.remove(level.dimension());
            }
        }
        if (ended) {
            PowerGrid.changed();
        }
    }

    /** Bij het afsluiten van de wereld: alles vergeten. */
    public static void clear() {
        PULSES.clear();
    }

    // ================================================================ Hulpjes

    /** Beschermt een schildkoepel deze plek tegen deze puls? (Kost geen schildkracht en geeft geen flits.) */
    private static boolean shielded(ServerLevel level, Pulse pulse, Vec3 at) {
        return ImtDomes.blockedAt(level, at, pulse.center, pulse.thrower, 0) != null;
    }

    /** Alle blokentiteiten in de chunks die de bol raakt. */
    private static List<BlockEntity> blockEntitiesNear(ServerLevel level, Vec3 center, double radius) {
        List<BlockEntity> found = new ArrayList<>();
        int minX = (Mth.floor(center.x - radius)) >> 4;
        int maxX = (Mth.floor(center.x + radius)) >> 4;
        int minZ = (Mth.floor(center.z - radius)) >> 4;
        int maxZ = (Mth.floor(center.z + radius)) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (level.hasChunk(cx, cz)) {
                    found.addAll(level.getChunk(cx, cz).getBlockEntities().values());
                }
            }
        }
        return found;
    }

    /**
     * De kabels binnen deze puls die open liggen (niet in een muur, niet achter een koepel): stroom-, netwerk- en
     * PoE-kabels (hoofdstuk 38). Een plek met twee soorten telt één keer.
     */
    private static List<BlockPos> openCables(ServerLevel level, Pulse pulse) {
        java.util.Set<BlockPos> cables = new java.util.LinkedHashSet<>();
        double r = pulse.radius;
        ChunkPos from = new ChunkPos(BlockPos.containing(pulse.center.x - r, 0, pulse.center.z - r));
        ChunkPos to = new ChunkPos(BlockPos.containing(pulse.center.x + r, 0, pulse.center.z + r));
        for (int cx = from.x; cx <= to.x; cx++) {
            for (int cz = from.z; cz <= to.z; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (com.senne.imttech.energy.CableKind kind : com.senne.imttech.energy.CableKind.values()) {
                    com.senne.imttech.energy.Cables.in(kind, chunk).forEach(pos -> {
                        Vec3 at = Vec3.atCenterOf(pos);
                        if (pulse.reaches(at) && !PowerCables.isSolid(level, pos) && !shielded(level, pulse, at)) {
                            cables.add(pos);
                        }
                    });
                }
            }
        }
        return new ArrayList<>(cables);
    }

    /** Vonkjes op een paar willekeurige open kabels in deze puls. */
    private static void cableSparks(ServerLevel level, Pulse pulse, int count) {
        List<BlockPos> cables = openCables(level, pulse);
        for (int i = 0; i < count && !cables.isEmpty(); i++) {
            BlockPos pos = cables.get(level.getRandom().nextInt(cables.size()));
            sparks(level, Vec3.atCenterOf(pos), 3);
        }
    }

    private static void sparks(ServerLevel level, Vec3 at, int count) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, count, 0.3, 0.3, 0.3, 0.15);
    }

    private static void powerDown(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, ModSounds.EMP_POWER_DOWN.get(), SoundSource.BLOCKS, 0.8f,
                0.9f + level.getRandom().nextFloat() * 0.2f);
    }
}
