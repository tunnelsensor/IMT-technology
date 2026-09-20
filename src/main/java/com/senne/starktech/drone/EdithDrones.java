package com.senne.starktech.drone;

import com.senne.starktech.arsenal.ArsenalDrone;
import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithConfig;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hulpfuncties om drones op te roepen, te tellen en terug te roepen.
 *
 * Drones komen altijd UIT DE LUCHT aanvliegen: ze verschijnen hoog boven je (of boven het
 * doelwit) en duiken naar beneden. Kan dat niet (je zit in een grot of onder een dak), dan
 * verschijnen ze zo hoog als er nog ruimte is boven je.
 *
 * Elke drone die je hier ziet vertrekken komt uit je arsenaal (zie ArsenalServer): hij wordt
 * uit zijn baan gehaald, doet zijn werk en meldt zich daarna weer terug. Heb je er geen meer
 * hangen, dan vertrekt er niets - dan moet je eerst nieuwe bouwen.
 */
public final class EdithDrones {
    private EdithDrones() {}

    /** Minimale vrije ruimte (in blokken) boven je voordat we van "uit de lucht" kunnen spreken. */
    private static final int MIN_DROP_HEIGHT = 4;

    /** Alle drones van deze speler in de buurt van één soort. */
    public static List<StarkDroneEntity> find(ServerPlayer owner, StarkDroneEntity.Mode mode) {
        return owner.level().getEntitiesOfClass(StarkDroneEntity.class, owner.getBoundingBox().inflate(EdithBalance.SCOUT_RANGE + 16),
                drone -> drone.isAlive() && drone.isOwnedBy(owner) && drone.getMode() == mode);
    }

    /** De verkenningsdrone van deze speler, of null. */
    public static @Nullable StarkDroneEntity findScout(ServerPlayer owner) {
        List<StarkDroneEntity> scouts = find(owner, StarkDroneEntity.Mode.SCOUT);
        return scouts.isEmpty() ? null : scouts.getFirst();
    }

    /**
     * Lanceert de verkenningsdrone vlak voor de speler. Deze komt NIET uit de lucht: je kijkt
     * er meteen door, dus hij moet bij je ogen beginnen.
     */
    public static @Nullable StarkDroneEntity launchScout(ServerPlayer owner) {
        ArsenalDrone available = ArsenalServer.checkOutOne(owner);
        if (available == null) {
            return null;
        }
        Vec3 look = owner.getViewVector(1.0f);
        Vec3 pos = owner.getEyePosition().add(look.x * 1.5, 0.6, look.z * 1.5);
        return spawn(owner, StarkDroneEntity.Mode.SCOUT, 0, -1, pos, false, available);
    }

    /** Lanceert een zwerm aanvalsdrones uit de lucht. Geeft terug hoeveel er gelanceerd zijn. */
    public static int launchSwarm(ServerPlayer owner, ItemStack glasses) {
        // Je bril bepaalt hoeveel drones een zwerm MAG zijn; je arsenaal bepaalt hoeveel het WORDT.
        List<ArsenalDrone> available = ArsenalServer.checkOut(owner, EdithData.getSwarmSize(glasses));
        int launched = 0;
        for (int i = 0; i < available.size(); i++) {
            if (spawnFromSky(owner, StarkDroneEntity.Mode.ATTACK, i, EdithBalance.SWARM_LIFETIME,
                    owner.position(), available.get(i)) != null) {
                launched++;
            }
        }
        return launched;
    }

    /**
     * Stuurt ÉÉN drone op één doelwit af. Hij duikt uit de lucht boven het doelwit en laat
     * niet meer los tot het doelwit dood is (of zijn tijd om is).
     */
    public static @Nullable StarkDroneEntity launchStrike(ServerPlayer owner, LivingEntity target) {
        ArsenalDrone available = ArsenalServer.checkOutOne(owner);
        if (available == null) {
            return null;
        }
        StarkDroneEntity drone = spawnFromSky(owner, StarkDroneEntity.Mode.ATTACK, 0,
                EdithBalance.STRIKE_DRONE_LIFETIME, target.position(), available);
        if (drone != null) {
            drone.lockOn(target);
        }
        return drone;
    }

    /**
     * Stuurt een spionagedrone naar een doelwit. Hij komt uit de lucht boven het doelwit en
     * blijft daar achter hangen. Onzichtbaar, en hij valt niets aan.
     */
    public static @Nullable StarkDroneEntity launchSpy(ServerPlayer owner, LivingEntity target) {
        ArsenalDrone available = ArsenalServer.checkOutOne(owner);
        if (available == null) {
            return null;
        }
        ServerLevel level = target.level() instanceof ServerLevel targetLevel ? targetLevel : owner.serverLevel();
        Vec3 above = target.position();
        double y = freeHeightAbove(level, above.x, above.y, above.z);
        return spawn(owner, StarkDroneEntity.Mode.SPY, 0, -1, new Vec3(above.x, y, above.z), level, true, available);
    }

/**
     * Roept alle drones van één soort van deze speler terug. Geeft terug hoeveel er weg gingen.
     * (Gebruikt door "stand down": alles afbreken.)
     */
    public static int recallAll(ServerPlayer owner, StarkDroneEntity.Mode mode) {
        List<StarkDroneEntity> drones = find(owner, mode);
        for (StarkDroneEntity drone : drones) {
            drone.recall(owner.serverLevel());
        }
        return drones.size();
    }

    // ---------------------------------------------------------------- Transport (fase 7)

    /**
     * Een drone die JOU draagt. Hij komt vlak boven je hangen (niet uit de lucht: je moet er
     * meteen onder kunnen hangen).
     *
     * @param destination null = jij stuurt zelf; anders vliegt de autopiloot daarheen
     */
    public static @Nullable StarkDroneEntity launchCarrier(ServerPlayer owner, @Nullable BlockPos destination) {
        ArsenalDrone available = ArsenalServer.checkOutOne(owner);
        if (available == null) {
            return null;
        }
        Vec3 above = new Vec3(owner.getX(), owner.getY() + owner.getBbHeight() + 1.2, owner.getZ());
        StarkDroneEntity drone = spawn(owner, StarkDroneEntity.Mode.TRANSPORT, 0,
                EdithBalance.TRANSPORT_MAX_TICKS, above, false, available);
        if (drone != null) {
            drone.setupTransport(StarkDroneEntity.TransportRole.CARRIER, destination, List.of());
        }
        return drone;
    }

    /** Een paar drones die met je meevliegen, puur voor het gezicht. */
    public static void launchTransportEscort(ServerPlayer owner, int count) {
        List<ArsenalDrone> available = ArsenalServer.checkOut(owner, count);
        for (int i = 0; i < available.size(); i++) {
            StarkDroneEntity drone = spawnFromSky(owner, StarkDroneEntity.Mode.TRANSPORT, i + 1,
                    EdithBalance.TRANSPORT_MAX_TICKS, owner.position(), available.get(i));
            if (drone != null) {
                drone.setupTransport(StarkDroneEntity.TransportRole.FOLLOWER, null, List.of());
            }
        }
    }

    /** Een vrachtdrone met spullen erin, op weg naar een plek. */
    public static @Nullable StarkDroneEntity launchCargo(ServerPlayer owner, List<ItemStack> cargo, BlockPos destination) {
        ArsenalDrone available = ArsenalServer.checkOutOne(owner);
        if (available == null) {
            return null;
        }
        StarkDroneEntity drone = spawnFromSky(owner, StarkDroneEntity.Mode.TRANSPORT, 0,
                EdithBalance.TRANSPORT_MAX_TICKS, owner.position(), available);
        if (drone != null) {
            drone.setupTransport(StarkDroneEntity.TransportRole.CARGO, destination, cargo);
        }
        return drone;
    }

    /** Een drone die iemand (of iets) oppakt en ergens anders neerzet. */
    public static @Nullable StarkDroneEntity launchGrabber(ServerPlayer owner, LivingEntity target, BlockPos destination) {
        ArsenalDrone available = ArsenalServer.checkOutOne(owner);
        if (available == null) {
            return null;
        }
        Vec3 above = new Vec3(target.getX(), target.getY() + target.getBbHeight() + 1.2, target.getZ());
        StarkDroneEntity drone = spawn(owner, StarkDroneEntity.Mode.TRANSPORT, 0,
                EdithBalance.TRANSPORT_MAX_TICKS, above, false, available);
        if (drone != null) {
            drone.setupTransport(StarkDroneEntity.TransportRole.GRABBER, destination, List.of());
        }
        return drone;
    }

    /**
     * Een cloakingdrone: hij hangt stil in de lucht voor je en projecteert een illusie.
     * Onzichtbaar, maar wel echt aanwezig (dus raakbaar).
     */
    public static @Nullable StarkDroneEntity launchIllusion(ServerPlayer owner) {
        ArsenalDrone available = ArsenalServer.checkOutOne(owner);
        if (available == null) {
            return null;
        }
        return spawn(owner, StarkDroneEntity.Mode.ILLUSION, 0, EdithBalance.ILLUSION_LIFETIME,
                illusionSpot(owner), false, available);
    }

    /**
     * Waar de illusie komt te staan: op de plek waar je naar KIJKT, tot ILLUSION_AIM_RANGE
     * blokken ver. Zo zet je een nep-muur precies in die gang, of een nep-leger op die heuvel.
     *
     * Kijk je de lucht in, dan zetten we hem een stuk voor je neer. De drone zelf hangt twee
     * blokken boven de grond, want zijn beeld hangt eronder: dan staat de muur netjes op de
     * grond in plaats van er half in.
     */
    public static Vec3 illusionSpot(ServerPlayer owner) {
        Vec3 eye = owner.getEyePosition();
        Vec3 far = eye.add(owner.getViewVector(1.0f).scale(EdithBalance.ILLUSION_AIM_RANGE));
        BlockHitResult hit = owner.level().clip(new ClipContext(eye, far,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 aimed = hit.getType() == HitResult.Type.MISS ? far : Vec3.atCenterOf(hit.getBlockPos());
        // Op de grond onder die plek gaan staan (of blijven zweven als daar niets is).
        ServerLevel level = owner.serverLevel();
        BlockPos.MutableBlockPos pos = BlockPos.containing(aimed).mutable();
        for (int i = 0; i < 8 && pos.getY() > level.getMinBuildHeight(); i++) {
            if (!level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                break;
            }
            pos.move(0, -1, 0);
        }
        return new Vec3(aimed.x, pos.getY() + 2.0, aimed.z);
    }

    /** Zorgt dat er genoeg beschermende drones zijn (roept er maximaal één per keer bij). */
    public static void maintainGuards(ServerPlayer owner, ItemStack glasses) {
        List<StarkDroneEntity> guards = find(owner, StarkDroneEntity.Mode.GUARD);
        int wanted = EdithData.getGuardCount(glasses);
        if (guards.size() < wanted) {
            ArsenalDrone available = ArsenalServer.checkOutOne(owner);
            if (available == null) {
                return; // geen drones meer in een baan: dan blijft de escorte zo klein als hij is
            }
            // Kies een vrij plekje in de formatie.
            int slot = 0;
            while (slotTaken(guards, slot)) {
                slot++;
            }
            spawnFromSky(owner, StarkDroneEntity.Mode.GUARD, slot, -1, owner.position(), available);
        }
    }

    private static boolean slotTaken(List<StarkDroneEntity> drones, int slot) {
        for (StarkDroneEntity drone : drones) {
            if (drone.getSlot() == slot) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- Uit de lucht komen

    /**
     * Roept een drone hoog boven "below" op, die daarna naar beneden duikt.
     * Is er boven dat punt geen ruimte (grot, kelder), dan komt hij zo hoog als het kan.
     */
    private static @Nullable StarkDroneEntity spawnFromSky(ServerPlayer owner, StarkDroneEntity.Mode mode, int slot,
                                                           int lifeTicks, Vec3 below, ArsenalDrone fromArsenal) {
        ServerLevel level = owner.serverLevel();
        // De drones komen niet allemaal op dezelfde plek naar beneden: een kringetje eromheen.
        double angle = slot * (Math.PI * 2 / 5) + (slot / 5) * 0.4;
        double radius = 1.5 + (slot % 5) * 0.35;
        double x = below.x + Math.cos(angle) * radius;
        double z = below.z + Math.sin(angle) * radius;
        double y = freeHeightAbove(level, x, below.y, z);
        boolean diving = y - below.y >= MIN_DROP_HEIGHT;
        return spawn(owner, mode, slot, lifeTicks, new Vec3(x, y, z), diving, fromArsenal);
    }

    /**
     * Hoe hoog boven "fromY" is de lucht nog vrij? Geeft de hoogte terug waar de drone mag
     * beginnen: hooguit dropHeight blokken hoger, en nooit door een plafond heen.
     */
    private static double freeHeightAbove(ServerLevel level, double x, double fromY, double z) {
        int maxDrop = EdithConfig.droneDropHeight();
        int startY = (int) Math.floor(fromY) + 2;
        double best = fromY + 1.2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= maxDrop; i++) {
            int y = startY + i;
            if (y >= level.getMaxBuildHeight()) {
                break;
            }
            pos.set((int) Math.floor(x), y, (int) Math.floor(z));
            // Zit er een blok in de weg? Dan stoppen we net eronder.
            if (!level.getBlockState(pos).isAir()) {
                break;
            }
            best = y + 0.1;
        }
        return best;
    }

    /**
     * Roept één drone op.
     *
     * @param diving true = hij komt uit de lucht en duikt naar beneden (met een straaltje
     *               vonken achter zich aan); false = hij verschijnt gewoon op zijn plek.
     */
    private static @Nullable StarkDroneEntity spawn(ServerPlayer owner, StarkDroneEntity.Mode mode, int slot, int lifeTicks,
                                                    Vec3 position, boolean diving, ArsenalDrone fromArsenal) {
        return spawn(owner, mode, slot, lifeTicks, position, owner.serverLevel(), diving, fromArsenal);
    }

    /** Idem, maar in een zelf gekozen wereld (de spionagedrone volgt zijn doelwit door portalen). */
    private static @Nullable StarkDroneEntity spawn(ServerPlayer owner, StarkDroneEntity.Mode mode, int slot, int lifeTicks,
                                                    Vec3 position, ServerLevel level, boolean diving, ArsenalDrone fromArsenal) {
        StarkDroneEntity drone = ModEntities.STARK_DRONE.get().create(level);
        if (drone == null) {
            // De drone staat al als "bezig" geboekt, dus die boeking moeten we terugdraaien.
            ArsenalServer.returnToOrbit(owner, fromArsenal.id(), fromArsenal.health());
            return null;
        }
        drone.setup(owner, mode, slot, lifeTicks);
        // Hij gaat de lucht in met de schade die hij de vorige keer opliep.
        drone.setArsenalId(fromArsenal.id());
        drone.setHealth(fromArsenal.health());
        drone.moveTo(position.x, position.y, position.z, owner.getYRot(), 0.0f);
        if (diving) {
            // Een zetje naar beneden, zodat hij echt "invalt" in plaats van rustig te komen zweven.
            drone.setDeltaMovement(0, -1.1, 0);
            drone.startDive();
        }
        level.addFreshEntity(drone);
        // Een spion maakt geen show: geen vonken en geen geluid bij het doelwit.
        if (mode != StarkDroneEntity.Mode.SPY) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, position.x, position.y, position.z, 10, 0.2, 0.2, 0.2, 0.15);
        }
        // Het geluid hoor je bij de speler zelf (de drone komt van boven, maar je moet het wel horen).
        owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), ModSounds.DRONE_DEPLOY.get(),
                SoundSource.PLAYERS, mode == StarkDroneEntity.Mode.SPY ? 0.4f : 0.8f, 1.2f + slot * 0.05f);
        return drone;
    }
}
