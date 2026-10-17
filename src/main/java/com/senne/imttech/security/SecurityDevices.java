package com.senne.imttech.security;

import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.shield.LaserEmitterBlock;
import com.senne.imttech.turret.TurretBlock;
import com.senne.imttech.turret.TurretBlockEntity;
import com.senne.imttech.turret.TurretKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * De apparaten van een beveiligingsnetwerk: wat er in het gebied van een kern staat en bij hem
 * hoort (de Devices-app op de desktop, jouw wens van 28 september: status, aan/uit en wie ze
 * aanvallen).
 * <ul>
 *   <li><b>Torentjes:</b> de beveiligingstorentjes in het gebied, en de Biebia's Industries-torentjes van de
 *       eigenaar van de kern. Elk torentje kun je los aan- en uitzetten (dezelfde schakelaar als
 *       I.M.T gebruikt).</li>
 *   <li><b>Laserpoorten:</b> zenders die aan elkaar vastzitten. Ze gaan met redstone; de kern kan
 *       ze allemaal tegelijk uitzetten (de schakelaar "Lasers").</li>
 *   <li><b>Drones:</b> de beveiligingsdrones van deze kern. De schakelaar "Drones" laat ze landen.</li>
 *   <li><b>Camera's:</b> de bewakingscamera's in het gebied. De schakelaar "Cameras" zet ze uit
 *       (dan geven ze "NO SIGNAL").</li>
 *   <li><b>Keycardlezers en codepanelen:</b> met hun naam (die kun je daar ook veranderen), of de
 *       server ze hoort, en in hoeveel diagrammen ze staan.</li>
 * </ul>
 *
 * Er is geen lijst die bijhoudt wat er gebouwd wordt: we kijken gewoon in het gebied, en
 * onthouden dat een paar seconden. Zoeken naar laserzenders gaat snel, want elk stukje wereld
 * (16x16x16) weet welke soorten blokken erin zitten: zit er geen zender in, dan slaan we het over.
 */
public final class SecurityDevices {
    private SecurityDevices() {}

    /** Zo lang (ticks) onthouden we wat er in het gebied staat, voor we opnieuw kijken. */
    private static final int RESCAN = 40;

    /**
     * Wat er bij een kern hoort: torentjes, alle laserzenders, van elke laserpoort één zender, de
     * camera's, de serverracks (zie LogicServer) en de keycardlezers en codepanelen (inputs).
     */
    public record Found(List<BlockPos> turrets, List<BlockPos> emitters, List<BlockPos> gates, List<BlockPos> cameras,
                        List<BlockPos> racks, List<BlockPos> inputs, long time) {}

    private static final Map<GlobalPos, Found> CACHE = new HashMap<>();

    // ================================================================ Zoeken

    /** Wat er nu bij deze kern hoort (hooguit RESCAN ticks oud). */
    public static Found scan(ServerLevel level, SecurityCoreBlockEntity core) {
        GlobalPos key = GlobalPos.of(level.dimension(), core.getBlockPos());
        Found cached = CACHE.get(key);
        long now = level.getGameTime();
        if (cached != null && now - cached.time() < RESCAN && now >= cached.time()) {
            return cached;
        }
        List<BlockPos> turrets = new ArrayList<>();
        List<BlockPos> emitters = new ArrayList<>();
        List<BlockPos> cameras = new ArrayList<>();
        List<BlockPos> racks = new ArrayList<>();
        List<BlockPos> inputs = new ArrayList<>();
        AABB area = core.area();
        int minCx = SectionPos.blockToSectionCoord((int) Math.floor(area.minX));
        int maxCx = SectionPos.blockToSectionCoord((int) Math.floor(area.maxX));
        int minCz = SectionPos.blockToSectionCoord((int) Math.floor(area.minZ));
        int maxCz = SectionPos.blockToSectionCoord((int) Math.floor(area.maxZ));
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                // Alleen stukken wereld die al geladen zijn: zoeken mag niets laden.
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof TurretBlockEntity turret && SecurityCores.ownedBy(level, core, turret.getBlockPos())
                            && belongs(core, turret)) {
                        turrets.add(turret.getBlockPos().immutable());
                    } else if (blockEntity instanceof CameraBlockEntity camera && SecurityCores.ownedBy(level, core, camera.getBlockPos())) {
                        cameras.add(camera.getBlockPos().immutable());
                    } else if (blockEntity instanceof com.senne.imttech.logic.ServerRackBlockEntity rack
                            && SecurityCores.ownedBy(level, core, rack.getBlockPos())) {
                        racks.add(rack.getBlockPos().immutable());
                    } else if (blockEntity instanceof com.senne.imttech.logic.InputDeviceBlockEntity input
                            && SecurityCores.ownedBy(level, core, input.getBlockPos())) {
                        inputs.add(input.getBlockPos().immutable());
                    }
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(ModBlocks.LASER_EMITTER.get()))) {
                        continue;
                    }
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (section.getBlockState(x, y, z).is(ModBlocks.LASER_EMITTER.get())) {
                                    BlockPos pos = new BlockPos(SectionPos.sectionToBlockCoord(cx) + x, baseY + y,
                                            SectionPos.sectionToBlockCoord(cz) + z);
                                    if (SecurityCores.ownedBy(level, core, pos)) {
                                        emitters.add(pos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Zenders die aan elkaar vastzitten zijn samen één poort.
        List<BlockPos> gates = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos emitter : emitters) {
            if (seen.add(emitter)) {
                seen.addAll(LaserEmitterBlock.group(level, emitter));
                gates.add(emitter);
            }
        }
        // De camera's in een vaste volgorde (van boven naar beneden, dan noord naar zuid, west naar
        // oost), zodat "Camera 1" niet steeds een andere is.
        cameras.sort(java.util.Comparator.comparingInt((BlockPos pos) -> -pos.getY())
                .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
        // De lezers en panelen ook: zo springt er niets heen en weer als je er een andere naam geeft.
        inputs.sort(java.util.Comparator.comparingInt((BlockPos pos) -> -pos.getY())
                .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
        Found found = new Found(List.copyOf(turrets), List.copyOf(emitters), List.copyOf(gates), List.copyOf(cameras),
                List.copyOf(racks), List.copyOf(inputs), now);
        CACHE.put(key, found);
        return found;
    }

    /** Hoort dit torentje bij de kern? Een beveiligingstorentje altijd, een Biebia's Industries-torentje als het van de eigenaar is. */
    public static boolean belongs(SecurityCoreBlockEntity core, TurretBlockEntity turret) {
        return turret.kind() == TurretKind.SECURITY
                || turret.kind() == TurretKind.ROCKET && core.ownerId() != null && core.ownerId().equals(turret.ownerId());
    }

    /** De oude beveiligingsdrones van deze kern (van voor hoofdstuk 25: de kern maakt er geen meer). */
    public static List<SecurityDroneEntity> drones(ServerLevel level, SecurityCoreBlockEntity core) {
        return level.getEntitiesOfClass(SecurityDroneEntity.class, core.area(),
                drone -> core.getBlockPos().equals(drone.home()));
    }

    /**
     * De schakelaar "Drones" (hoofdstuk 25): zet al je security drones in het gebied van deze kern aan of uit (uit =
     * ze vliegen naar hun dock en landen erop). Alleen drones met verbinding horen het; een drone die nu niet geladen
     * is, krijgt het als hij terug is. Geeft hoeveel drones het hoorden (of zullen horen).
     */
    public static int switchDrones(ServerLevel level, SecurityCoreBlockEntity core, boolean on) {
        int count = 0;
        for (com.senne.imttech.bot.BotNetwork.Entry entry : com.senne.imttech.bot.BotNetwork.get(level)
                .atCore(net.minecraft.core.GlobalPos.of(level.dimension(), core.getBlockPos()))) {
            if (entry.drone()) {
                com.senne.imttech.bot.BotLink.Result result = com.senne.imttech.bot.BotLink.switchOn(level, core, entry, on);
                if (result == com.senne.imttech.bot.BotLink.Result.DONE || result == com.senne.imttech.bot.BotLink.Result.WAITING) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Vergeet wat we zagen (na een schakelaar: dan meteen opnieuw kijken). */
    public static void forget(ServerLevel level, SecurityCoreBlockEntity core) {
        CACHE.remove(GlobalPos.of(level.dimension(), core.getBlockPos()));
    }

    public static void clear() {
        CACHE.clear();
    }

    // ================================================================ Schakelen

    /**
     * Eén torentje aan of uit (vanaf de desktop). Alleen als het echt bij deze kern hoort. Geeft
     * een foutmelding terug, of null als het lukte.
     */
    public static @Nullable String switchTurret(ServerLevel level, SecurityCoreBlockEntity core, BlockPos pos, boolean on) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TurretBlock turretBlock)
                || !(level.getBlockEntity(pos) instanceof TurretBlockEntity turret)
                || !SecurityCores.ownedBy(level, core, pos) || !belongs(core, turret)) {
            return "that turret is not part of this network";
        }
        turretBlock.setSwitched(level, pos, state, on);
        return null;
    }

    /**
     * De lasers van het hele gebied aan of uit. De zenders kijken zelf of de kern ze toestaat (zie
     * LaserEmitterBlock); we geven ze alleen een duwtje, zodat ze dat meteen doen.
     */
    public static void setLasers(ServerLevel level, SecurityCoreBlockEntity core, boolean on) {
        core.setLasersEnabled(on);
        forget(level, core);
        for (BlockPos emitter : scan(level, core).emitters()) {
            level.scheduleTick(emitter, ModBlocks.LASER_EMITTER.get(), 1);
        }
    }

    /**
     * De camera's van het hele gebied aan of uit. Ze kijken zelf om de twee seconden; hier laten we
     * ze dat meteen doen, zodat het beeld meteen verdwijnt of terugkomt.
     */
    public static void setCameras(ServerLevel level, SecurityCoreBlockEntity core, boolean on) {
        core.setCamerasEnabled(on);
        for (BlockPos pos : scan(level, core).cameras()) {
            if (level.getBlockEntity(pos) instanceof CameraBlockEntity camera) {
                camera.updateOnline(level, pos, level.getBlockState(pos));
            }
        }
    }

    /** Een camera een stukje draaien en/of kantelen (de pijltjes op de desktop). Foutmelding, of null. */
    public static @Nullable String aimCamera(ServerLevel level, SecurityCoreBlockEntity core, BlockPos pos, float dPan, float dTilt) {
        if (!(level.getBlockEntity(pos) instanceof CameraBlockEntity camera) || !SecurityCores.ownedBy(level, core, pos)) {
            return "that camera is not part of this network";
        }
        camera.aim(Math.max(-20f, Math.min(20f, dPan)), Math.max(-20f, Math.min(20f, dTilt)));
        return null;
    }

    /**
     * Een camera een andere naam geven (de knop Rename bij het grote beeld). Een lege naam haalt
     * zijn eigen naam weg (dan heet hij weer "Camera 3"). Foutmelding, of null.
     */
    public static @Nullable String renameCamera(ServerLevel level, SecurityCoreBlockEntity core, BlockPos pos, String name) {
        if (!(level.getBlockEntity(pos) instanceof CameraBlockEntity camera) || !SecurityCores.ownedBy(level, core, pos)) {
            return "that camera is not part of this network";
        }
        camera.setName(name);
        return null;
    }

    /** De naam van een camera op de desktop: zijn eigen naam, of anders "Camera 3". */
    private static String cameraName(CameraBlockEntity camera, int number) {
        return camera.name().isEmpty() ? "Camera " + number : camera.name();
    }

    /**
     * De camera's voor de bewakingsmuur (de app Cameras): waar ze hangen, waar hun lens zit, waar
     * ze heen kijken en of ze beeld hebben.
     */
    public static ListTag cameras(ServerLevel level, SecurityCoreBlockEntity core) {
        ListTag list = new ListTag();
        int number = 1;
        for (BlockPos pos : scan(level, core).cameras()) {
            if (!(level.getBlockEntity(pos) instanceof CameraBlockEntity camera)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            CompoundTag tag = position("camera", pos);
            tag.putString("name", cameraName(camera, number++));
            tag.putBoolean("on", state.getValue(CameraBlock.ONLINE));
            net.minecraft.world.phys.Vec3 eye = CameraBlockEntity.eye(pos, state);
            tag.putDouble("ex", eye.x);
            tag.putDouble("ey", eye.y);
            tag.putDouble("ez", eye.z);
            tag.putFloat("yaw", camera.yaw());
            tag.putFloat("pitch", camera.pitch());
            tag.putFloat("pan", camera.pan());
            tag.putFloat("tilt", camera.tilt());
            list.add(tag);
        }
        return list;
    }

    // ================================================================ Voor de desktop

    /** Alle apparaten met hun status, voor de Devices-app. */
    public static ListTag snapshot(ServerLevel level, SecurityCoreBlockEntity core) {
        Found found = scan(level, core);
        ListTag list = new ListTag();
        for (BlockPos pos : found.turrets()) {
            if (!(level.getBlockEntity(pos) instanceof TurretBlockEntity turret)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof TurretBlock)) {
                continue;
            }
            CompoundTag device = position("turret", pos);
            device.putString("name", turret.kind() == TurretKind.SECURITY ? "Security turret" : "rocket turret");
            device.putBoolean("on", state.getValue(TurretBlock.ENABLED));
            device.putString("st", !state.getValue(TurretBlock.ENABLED) ? "switched off"
                    : state.getValue(TurretBlock.POWERED) ? "off (redstone)"
                    : !state.getValue(TurretBlock.ONLINE) ? "no power" : "online");
            if (turret.kind().usesAmmo()) {
                device.putInt("ammo", turret.ammoCount());
            }
            Entity target = turret.target(level);
            if (target != null) {
                device.putString("tgt", target.getName().getString());
            }
            list.add(device);
        }
        // Je security drones (hoofdstuk 25), zoals ze zich het laatst bij BotNetwork meldden.
        for (com.senne.imttech.bot.BotNetwork.Entry entry : com.senne.imttech.bot.BotNetwork.get(level)
                .atCore(net.minecraft.core.GlobalPos.of(level.dimension(), core.getBlockPos()))) {
            if (!entry.drone()) {
                continue;
            }
            CompoundTag device = position("drone", entry.pos().pos());
            device.putString("name", entry.name());
            if (level.getEntity(entry.id()) instanceof com.senne.imttech.robot.GuardDroneEntity drone && drone.isAlive()) {
                device.putInt("hp", (int) Math.ceil(drone.getHealth()));
                device.putInt("max", (int) Math.ceil(drone.getMaxHealth()));
            }
            device.putString("st", entry.state().title().toLowerCase(java.util.Locale.ROOT));
            device.putInt("bat", Math.round(entry.battery() * 100));
            list.add(device);
        }
        // De oude drones (die maakte de kern vroeger zelf).
        int number = 1;
        for (SecurityDroneEntity drone : drones(level, core)) {
            CompoundTag device = position("drone", drone.blockPosition());
            device.putString("name", "Patrol drone " + number++);
            device.putInt("hp", (int) Math.ceil(drone.getHealth()));
            device.putInt("max", (int) Math.ceil(drone.getMaxHealth()));
            device.putString("st", drone.statusText());
            if (drone.getTarget() != null) {
                device.putString("tgt", drone.getTarget().getName().getString());
            }
            list.add(device);
        }
        for (BlockPos gate : found.gates()) {
            if (!level.getBlockState(gate).is(ModBlocks.LASER_EMITTER.get())) {
                continue;
            }
            List<BlockPos> members = LaserEmitterBlock.group(level, gate);
            boolean on = members.stream().anyMatch(member -> {
                BlockState state = level.getBlockState(member);
                return state.is(ModBlocks.LASER_EMITTER.get()) && state.getValue(LaserEmitterBlock.POWERED);
            });
            CompoundTag device = position("laser", gate);
            device.putString("name", "Laser gate (" + members.size() + (members.size() == 1 ? " emitter)" : " emitters)"));
            device.putBoolean("on", on);
            device.putString("st", on ? "active" : core.lasersEnabled() ? "open" : "off (network)");
            list.add(device);
        }
        int cameraNumber = 1;
        for (BlockPos pos : found.cameras()) {
            if (!(level.getBlockEntity(pos) instanceof CameraBlockEntity camera)) {
                continue;
            }
            boolean on = level.getBlockState(pos).getValue(CameraBlock.ONLINE);
            CompoundTag device = position("camera", pos);
            device.putString("name", cameraName(camera, cameraNumber++));
            device.putBoolean("on", on);
            device.putString("st", on ? "online" : core.camerasEnabled() ? "no power" : "off (network)");
            list.add(device);
        }
        boolean server = com.senne.imttech.logic.LogicServer.stats(level, core).online();
        for (BlockPos pos : found.inputs()) {
            if (!(level.getBlockEntity(pos) instanceof com.senne.imttech.logic.InputDeviceBlockEntity input)) {
                continue;
            }
            boolean panel = level.getBlockState(pos).getBlock() instanceof com.senne.imttech.logic.CodePanelBlock;
            CompoundTag device = position(panel ? "panel" : "reader", pos);
            device.putString("name", input.name());
            device.putString("st", server ? "ready" : "no server");
            device.putInt("uses", uses(core, panel ? com.senne.imttech.logic.program.Op.CODEPANEL
                    : com.senne.imttech.logic.program.Op.KEYCARD, input.name()));
            list.add(device);
        }
        return list;
    }

    /**
     * In hoeveel diagrammen staat "when keycard(naam)" (of "when codepanel(naam)")? Zo zie je na een
     * andere naam meteen of een diagram nog de oude naam gebruikt (dan reageert het niet meer).
     */
    private static int uses(SecurityCoreBlockEntity core, com.senne.imttech.logic.program.Op trigger, String name) {
        int count = 0;
        for (var program : core.logic().programs()) {
            if (program.graph().nodes().stream().anyMatch(node -> node.op == trigger && node.text.equalsIgnoreCase(name))) {
                count++;
            }
        }
        return count;
    }

    private static CompoundTag position(String kind, BlockPos pos) {
        CompoundTag device = new CompoundTag();
        device.putString("k", kind);
        device.putInt("x", pos.getX());
        device.putInt("y", pos.getY());
        device.putInt("z", pos.getZ());
        return device;
    }
}
