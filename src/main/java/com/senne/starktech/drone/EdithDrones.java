package com.senne.starktech.drone;

import com.senne.starktech.arsenal.ArsenalDrone;
import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
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

    /**
     * Alle drones van deze speler van één soort, in de buurt.
     *
     * "In de buurt" moet ruim genoeg zijn om ook de drones te vinden die nog hoog boven je
     * hangen: ze vallen van ORBIT_HEIGHT blokken naar beneden en stijgen net zo hoog weer op.
     * Was dit te krap, dan zou "stand down" een invallende drone gewoon missen.
     */
    public static List<StarkDroneEntity> find(ServerPlayer owner, StarkDroneEntity.Mode mode) {
        double range = Math.max(StarkConfig.Recon.SCOUT_RANGE + 16, Math.round(StarkConfig.Drones.ORBIT_HEIGHT) + 16);
        return owner.level().getEntitiesOfClass(StarkDroneEntity.class, owner.getBoundingBox().inflate(range),
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
            if (spawnFromSky(owner, StarkDroneEntity.Mode.ATTACK, i, Math.round(StarkConfig.Drones.SWARM_LIFETIME),
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
                Math.round(StarkConfig.Drones.STRIKE_LIFETIME), target.position(), available);
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
        StarkDroneEntity drone = spawn(owner, StarkDroneEntity.Mode.SPY, 0, -1,
                new Vec3(above.x, y, above.z), level, true, available);
        if (drone != null) {
            // Een spion maakt geen show: hij zakt recht naar beneden, niet in een grote boog.
            drone.startDive(new Vec3(above.x, above.y + StarkConfig.Recon.SPY_FOLLOW_HEIGHT, above.z));
        }
        return drone;
    }

    /**
     * Zet een teruggeroepen drone naast de speler neer. Hij komt in een boog uit de lucht, vóór
     * je (zodat je hem ziet aankomen), en blijft daar staan tot je hem repareert (rechtsklik) of
     * weer omhoog stuurt (shift-rechtsklik). Roep je er meer terug, dan landen ze naast elkaar.
     */
    public static @Nullable StarkDroneEntity landRecalled(ServerPlayer owner, ArsenalDrone fromArsenal) {
        ServerLevel level = owner.serverLevel();
        Vec3 landing = freeLandingSpot(owner, level);
        float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
        double ahead = Math.atan2(Mth.cos(yaw), -Mth.sin(yaw)); // de kant waar je naartoe kijkt
        Vec3 start = arcStart(level, landing, ahead, Math.round(StarkConfig.Drones.ORBIT_HEIGHT));
        StarkDroneEntity drone = spawn(owner, StarkDroneEntity.Mode.GROUNDED, 0, -1,
                start, level, start.y - landing.y >= MIN_DROP_HEIGHT, fromArsenal);
        if (drone != null) {
            drone.startDive(landing);
        }
        return drone;
    }

    /**
     * Een vrij plekje om te landen: eerst twee blokken voor je, anders links of rechts daarvan,
     * of een rij verder. Een plek is bezet als er al een teruggeroepen drone staat (of naartoe
     * onderweg is). Zo landen twee drones naast elkaar in plaats van op elkaar.
     */
    private static Vec3 freeLandingSpot(ServerPlayer owner, ServerLevel level) {
        List<Vec3> taken = find(owner, StarkDroneEntity.Mode.GROUNDED).stream()
                .map(StarkDroneEntity::landingSpot).toList();
        float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
        double forwardX = -Mth.sin(yaw);
        double forwardZ = Mth.cos(yaw);
        // {hoe ver vooruit, hoe ver opzij}
        double[][] spots = {{2, 0}, {2, 1.3}, {2, -1.3}, {2, 2.6}, {2, -2.6},
                {3.3, 0.65}, {3.3, -0.65}, {3.3, 1.95}, {3.3, -1.95}, {4.6, 0}, {4.6, 1.3}, {4.6, -1.3}};
        for (double[] spot : spots) {
            double x = owner.getX() + forwardX * spot[0] - forwardZ * spot[1];
            double z = owner.getZ() + forwardZ * spot[0] + forwardX * spot[1];
            if (taken.stream().noneMatch(other -> Math.hypot(other.x - x, other.z - z) < 1.1)) {
                return new Vec3(x, groundBelow(level, x, owner.getY() + 1, z), z);
            }
        }
        double x = owner.getX() + forwardX * 2;
        double z = owner.getZ() + forwardZ * 2;
        return new Vec3(x, groundBelow(level, x, owner.getY() + 1, z), z);
    }

    /**
     * Waar een drone hoog in de lucht begint om in een boog naar {@code landing} te duiken (zie
     * SkyDive): een stuk opzij in richting {@code angle} (in radialen), hoe hoger hoe verder.
     * Daar moet de lucht vrij zijn en de boog mag nergens door een blok gaan; anders proberen we
     * het half zo ver opzij, en op het laatst gewoon recht erboven (zoals vroeger).
     *
     * Ook de raiddrones gebruiken dit, met hun eigen maximale hoogte.
     */
    public static Vec3 arcStart(ServerLevel level, Vec3 landing, double angle, int maxDrop) {
        double offset = Math.min(StarkConfig.Drones.DIVE_ARC_MAX, maxDrop * StarkConfig.Drones.DIVE_ARC_RATIO);
        for (double part : new double[] {1.0, 0.5}) {
            double x = landing.x + Math.cos(angle) * offset * part;
            double z = landing.z + Math.sin(angle) * offset * part;
            Vec3 start = new Vec3(x, freeHeightAbove(level, x, landing.y, z, maxDrop), z);
            if (start.y - landing.y >= MIN_DROP_HEIGHT && arcIsClear(level, start, landing)) {
                return start;
            }
        }
        return new Vec3(landing.x, freeHeightAbove(level, landing.x, landing.y, landing.z, maxDrop), landing.z);
    }

    /** Gaat de boog van start naar landing nergens door een blok? We controleren hem in stukjes. */
    private static boolean arcIsClear(ServerLevel level, Vec3 start, Vec3 landing) {
        // Een halve blok hoger, zodat een landing óp de grond niet als "botsing" telt.
        Vec3 lift = new Vec3(0, 0.5, 0);
        SkyDive path = new SkyDive(start.add(lift), landing.add(lift));
        Vec3 last = start.add(lift);
        for (int i = 1; i <= 12; i++) {
            Vec3 point = path.at(i / 12.0);
            if (level.clip(new ClipContext(last, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    CollisionContext.empty())).getType() != HitResult.Type.MISS) {
                return false;
            }
            last = point;
        }
        return true;
    }

    /** De drone uit je arsenaal die nu naast je op de grond staat, of null. */
    public static @Nullable StarkDroneEntity findGrounded(ServerPlayer owner, java.util.UUID arsenalId) {
        for (StarkDroneEntity drone : find(owner, StarkDroneEntity.Mode.GROUNDED)) {
            if (arsenalId.equals(drone.getArsenalId())) {
                return drone;
            }
        }
        return null;
    }

    /** De eerste vaste grond onder een punt (of de bodem van de wereld). */
    private static double groundBelow(ServerLevel level, double x, double fromY, double z) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, fromY, z).mutable();
        for (int i = 0; i < 24 && pos.getY() > level.getMinBuildHeight(); i++) {
            if (!level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                return pos.getY();
            }
            pos.move(0, -1, 0);
        }
        return fromY;
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
                Math.round(StarkConfig.Transport.MAX_TICKS), above, false, available);
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
                    Math.round(StarkConfig.Transport.MAX_TICKS), owner.position(), available.get(i));
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
                Math.round(StarkConfig.Transport.MAX_TICKS), owner.position(), available);
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
                Math.round(StarkConfig.Transport.MAX_TICKS), above, false, available);
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
        return spawn(owner, StarkDroneEntity.Mode.ILLUSION, 0, Math.round(StarkConfig.Illusions.LIFETIME),
                illusionSpot(owner), false, available);
    }

    /**
     * Een cloakingdrone om mee te RICHTEN: hij vertrekt vlak voor je (zoals de verkenner), en jij
     * kijkt door hem heen tot je de illusie neerzet. Hij blijft tot dan zonder tijdslimiet.
     */
    public static @Nullable StarkDroneEntity launchIllusionAim(ServerPlayer owner) {
        ArsenalDrone available = ArsenalServer.checkOutOne(owner);
        if (available == null) {
            return null;
        }
        Vec3 look = owner.getViewVector(1.0f);
        Vec3 pos = owner.getEyePosition().add(look.x * 1.5, 0.6, look.z * 1.5);
        return spawn(owner, StarkDroneEntity.Mode.ILLUSION, 0, -1, pos, false, available);
    }

    /**
     * Waar de illusie komt te staan: op de plek waar je naar KIJKT, tot ILLUSION_AIM_RANGE
     * blokken ver. Zo zet je een nep-muur precies in die gang, of een nep-leger op die heuvel.
     *
     * Kijk je naar een wezen (een koe, een speler), dan komt hij daar: niet op de muur erachter.
     * Kijk je tegen de zijkant van een muur, dan op de vrije plek ervoor (en niet ín de muur).
     * Kijk je de lucht in, dan zetten we hem een stuk voor je neer. De drone zelf hangt twee
     * blokken boven de grond, want zijn beeld hangt eronder: dan staat de muur netjes op de
     * grond in plaats van er half in.
     */
    public static Vec3 illusionSpot(ServerPlayer owner) {
        Vec3 eye = owner.getEyePosition();
        Vec3 far = eye.add(owner.getViewVector(1.0f).scale(StarkConfig.Illusions.AIM_RANGE));
        BlockHitResult hit = owner.level().clip(new ClipContext(eye, far,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        net.minecraft.world.entity.LivingEntity looked = com.senne.starktech.edith.EdithServer.lookTarget(owner,
                StarkConfig.Illusions.AIM_RANGE);
        Vec3 aimed;
        if (looked != null) {
            aimed = looked.position().add(0, 0.5, 0);
        } else if (hit.getType() == HitResult.Type.MISS) {
            aimed = far;
        } else {
            aimed = Vec3.atCenterOf(hit.getBlockPos().relative(hit.getDirection()));
        }
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
     * Roept een drone hoog in de lucht op, die daarna in een boog naar "below" duikt (en net
     * daarboven uitkomt). Elke drone van een zwerm komt uit een andere richting, dus ze waaieren
     * van alle kanten binnen. Is er geen ruimte (grot, kelder), dan komt hij zo hoog als het kan.
     */
    private static @Nullable StarkDroneEntity spawnFromSky(ServerPlayer owner, StarkDroneEntity.Mode mode, int slot,
                                                           int lifeTicks, Vec3 below, ArsenalDrone fromArsenal) {
        ServerLevel level = owner.serverLevel();
        // De drones komen niet allemaal op dezelfde plek uit: een kringetje eromheen.
        double angle = slot * (Math.PI * 2 / 5) + (slot / 5) * 0.4;
        double radius = 1.5 + (slot % 5) * 0.35;
        Vec3 landing = new Vec3(below.x + Math.cos(angle) * radius, below.y + StarkConfig.Drones.DIVE_BRAKE_HEIGHT,
                below.z + Math.sin(angle) * radius);
        Vec3 start = arcStart(level, landing, angle, Math.round(StarkConfig.Drones.ORBIT_HEIGHT));
        boolean diving = start.y - landing.y >= MIN_DROP_HEIGHT;
        StarkDroneEntity drone = spawn(owner, mode, slot, lifeTicks, start, diving, fromArsenal);
        if (drone != null && diving) {
            drone.startDive(landing);
        }
        return drone;
    }

    /**
     * Hoe hoog boven "fromY" is de lucht nog vrij? Geeft de hoogte terug waar de drone mag
     * beginnen: hooguit dropHeight blokken hoger, en nooit door een plafond heen.
     */
    private static double freeHeightAbove(ServerLevel level, double x, double fromY, double z) {
        return freeHeightAbove(level, x, fromY, z, Math.round(StarkConfig.Drones.ORBIT_HEIGHT));
    }

    /**
     * Hoe hoog boven "fromY" is de lucht nog vrij, tot maximaal "maxDrop" blokken? Stopt net
     * onder het eerste blok dat in de weg zit, zodat een drone nooit door een dak heen komt.
     *
     * Ook de vijandige raiddrones gebruiken dit, zodat ze op dezelfde manier binnenkomen.
     */
    public static double freeHeightAbove(ServerLevel level, double x, double fromY, double z, int maxDrop) {
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
        // Duikt hij, dan zegt de beller met startDive() waar hij uitkomt (in een boog, zie SkyDive).
        drone.moveTo(position.x, position.y, position.z, owner.getYRot(), 0.0f);
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
