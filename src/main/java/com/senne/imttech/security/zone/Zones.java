package com.senne.imttech.security.zone;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De spelregels van een zone (hoofdstuk 10 van het plan, 8 oktober).
 *
 * In een actieve zone mag alleen wie toestemming heeft (de eigenaar, zijn team, zijn vertrouwde
 * spelers, en wie in creative speelt) blokken plaatsen of breken. Jouw woorden: "zo realistisch
 * mogelijk: in het echt kun je moeilijk inbreken, maar als je binnen bent kun je alles opendoen en
 * stelen". Kisten, deuren, knoppen en dieren blijven dus voor iedereen bruikbaar; alleen plaatsen
 * en breken is op slot.
 *
 * Wel mag iedereen (jouw wens, 9 oktober): een emmer leeggieten of vullen, maar alleen op de grond
 * (op de bovenkant van een blok), niet tegen een muur, zodat je niet met water omhoog klimt; vuur
 * aansteken; met een bijl of schoffel een blok veranderen; en stroomkabels leggen of knippen. Nog
 * steeds niet: met een schep een pad maken en beendermeel (die veranderen ook een blok).
 *
 * Wat iedereen wel mag (ook weer weghalen): de "vrije" blokken uit de tag
 * {@code imttech:zone_free}: ladders, scaffolding, klimplanten, fakkels en lantaarns (en straks
 * C4). "In het echt zou dat ook gaan."
 */
public final class Zones {
    private Zones() {}

    /** Blokken die iedereen in een zone mag plaatsen en weghalen (data/imttech/tags/block/zone_free.json). */
    public static final TagKey<Block> FREE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "zone_free"));

    /** Wanneer (speltijd) zei de zone het laatst tegen deze speler? Zo krijg je het niet tien keer per seconde te horen. */
    private static final Map<UUID, Long> LAST_TOLD = new HashMap<>();
    /** Zo lang wachten we voor we hetzelfde opnieuw zeggen (ticks). */
    private static final int TELL_PAUSE = 10;

    // ================================================================ Opzoeken

    /**
     * De zone die dit blok nu beschermt, of null (geen zone, of hij staat uit: geen stroom, geen
     * kubussen, of de kern is van niemand).
     */
    public static @Nullable ZoneData.Zone activeAt(ServerLevel level, BlockPos pos) {
        ZoneData data = ZoneData.get(level);
        ZoneData.Zone zone = data.at(level.dimension(), pos);
        if (zone == null) {
            return null;
        }
        // Is de kern er nog? (Hij kan weg zijn zonder dat we het merkten, bijvoorbeeld met /setblock.)
        BlockPos core = zone.core().pos();
        if (level.isLoaded(core) && !(level.getBlockEntity(core) instanceof SecurityCoreBlockEntity)) {
            data.forget(zone.core());
            return null;
        }
        return zone.active() ? zone : null;
    }

    /** Mag {@code actor} in deze zone bouwen? Alleen spelers: een monster of machine nooit. */
    public static boolean mayBuild(ServerLevel level, ZoneData.Zone zone, @Nullable Entity actor) {
        if (!(actor instanceof Player player)) {
            return false;
        }
        return player.isCreative() || zone.isBuilder(level, player);
    }

    /** Houdt een zone {@code actor} hier tegen? (null = niemand: een machine, vuur, een explosie zonder schuldige.) */
    public static boolean denies(ServerLevel level, BlockPos pos, @Nullable Entity actor) {
        ZoneData.Zone zone = activeAt(level, pos);
        return zone != null && !mayBuild(level, zone, actor);
    }

    /** Mag iedereen dit blok plaatsen en weghalen (een ladder, een fakkel...)? */
    public static boolean isFree(BlockState state) {
        return state.is(FREE);
    }

    /**
     * Zegt het tegen de speler: onderaan "Protected zone" en een kort piepje (alleen voor hem). Niet
     * wiens zone het is: je ziet hem pas als je er iets probeert (jouw keuze: alleen de eigenaar en
     * zijn vrienden zien de rand).
     */
    public static void tellProtected(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        long now = serverPlayer.serverLevel().getGameTime();
        Long last = LAST_TOLD.get(serverPlayer.getUUID());
        if (last != null && now - last < TELL_PAUSE && now >= last) {
            return;
        }
        LAST_TOLD.put(serverPlayer.getUUID(), now);
        serverPlayer.displayClientMessage(Component.translatable("zone.imttech.msg.protected")
                .withStyle(ChatFormatting.RED), true);
        serverPlayer.playNotifySound(ModSounds.IMT_DENIED.get(), SoundSource.PLAYERS, 0.5f, 1.3f);
    }

    // ================================================================ Breken

    /**
     * Beginnen met hakken (linksklik): in een zone waar je niet mag, begint het niet eens (zo hak je
     * niet eerst zeven seconden op obsidiaan voor niets).
     */
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (isFree(level.getBlockState(pos)) || !denies(level, pos, player)) {
            return;
        }
        event.setCanceled(true);
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        tellProtected(player);
    }

    /** Het blok echt breken: dit is de echte controle (ook voor gereedschap dat in één klap breekt). */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || isFree(event.getState())) {
            return;
        }
        if (denies(level, event.getPos(), event.getPlayer())) {
            event.setCanceled(true);
            tellProtected(event.getPlayer());
        }
    }

    // ================================================================ Plaatsen

    /**
     * Een blok neerzetten. NeoForge stuurt dit ook als een item een blok verandert (een bijl die een
     * stam schilt, een aansteker die vuur maakt, beendermeel dat een boom laat groeien): dat telt
     * allemaal als plaatsen. Bij een deur of bed kijken we naar alle blokken die erbij komen.
     */
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || isFree(event.getPlacedBlock()) || allowedChange(event)) {
            return;
        }
        List<BlockSnapshot> placed = event instanceof BlockEvent.EntityMultiPlaceEvent multi
                ? multi.getReplacedBlockSnapshots() : List.of(event.getBlockSnapshot());
        for (BlockSnapshot snapshot : placed) {
            if (denies(level, snapshot.getPos(), event.getEntity())) {
                event.setCanceled(true);
                if (event.getEntity() instanceof ServerPlayer player) {
                    tellProtected(player);
                    // Je spel dacht al dat het blok uit je hand weg was: laat het weer zien.
                    player.inventoryMenu.sendAllDataToRemote();
                }
                return;
            }
        }
    }

    /** Een bijl of schoffel die net een blok verandert (zie onToolModification): wie, waar en wanneer. */
    private record ToolChange(UUID player, BlockPos pos, long time) {}

    private static @Nullable ToolChange lastToolChange;

    /**
     * Een bijl schilt een stam (of schraapt koper), een schoffel maakt akkerland: dat mag iedereen in een
     * zone. NeoForge meldt het eerst hier; vlak daarna komt het als "plaatsen" langs onPlace, en daar
     * laten we precies dit blok dan door.
     */
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.isSimulated() || event.getPlayer() == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        net.neoforged.neoforge.common.ItemAbility ability = event.getItemAbility();
        if (ability == net.neoforged.neoforge.common.ItemAbilities.AXE_STRIP || ability == net.neoforged.neoforge.common.ItemAbilities.AXE_SCRAPE
                || ability == net.neoforged.neoforge.common.ItemAbilities.AXE_WAX_OFF || ability == net.neoforged.neoforge.common.ItemAbilities.HOE_TILL) {
            lastToolChange = new ToolChange(event.getPlayer().getUUID(), event.getPos().immutable(), level.getGameTime());
        }
    }

    /**
     * Wat iedereen in een zone mag, ook al komt het als "plaatsen" langs: vuur aansteken (het vuur
     * zelf, of een kampvuur of kaars die aangaat) en een bijl of schoffel op een blok. We kijken naar
     * wat er echt verandert (niet naar wat je vasthoudt): zo zet niemand stiekem een blok neer met een
     * aansteker in zijn andere hand.
     */
    private static boolean allowedChange(BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent || !(event.getEntity() instanceof Player player)) {
            return false;
        }
        BlockState placed = event.getPlacedBlock();
        BlockState before = event.getBlockSnapshot().getState();
        if (placed.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) {
            return true;
        }
        if (placed.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                && placed.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                && before.is(placed.getBlock())) {
            return true;
        }
        ToolChange change = lastToolChange;
        return change != null && change.player().equals(player.getUUID()) && change.pos().equals(event.getPos())
                && change.time() == event.getLevel().getLevelData().getGameTime();
    }

    /**
     * Een emmer leeggieten of vullen. Dat mag iedereen in een zone, maar alleen op de grond: op de
     * bovenkant van een blok (jouw wens, 9 oktober: "niet tegen muren, zo kun je niet met water
     * klimmen"). Dat gaat buiten het plaats-event om, dus kijken we zelf waar de emmer heen wijst
     * (zoals de emmer zelf doet): het blok waar je naar kijkt, en (bij een volle emmer) de plek ervoor.
     */
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getItemStack().getItem() instanceof BucketItem bucket)) {
            return;
        }
        boolean empty = bucket.content == Fluids.EMPTY;
        // Precies zoals de emmer zelf mikt (met de richting van je lichaam, niet van je hoofd).
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(Vec3.directionFromRotation(player.getXRot(), player.getYRot()).scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                empty ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos at = hit.getBlockPos();
        BlockPos before = at.relative(hit.getDirection());
        if (hit.getDirection() == net.minecraft.core.Direction.UP
                || !denies(level, at, player) && (empty || !denies(level, before, player))) {
            return; // op de grond (of buiten een zone): mag
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        // Je spel liet het water misschien al stromen, en dacht dat de emmer leeg was: zet het terug.
        player.connection.send(new ClientboundBlockUpdatePacket(level, at));
        player.connection.send(new ClientboundBlockUpdatePacket(level, before));
        player.inventoryMenu.sendAllDataToRemote();
        tellProtected(player);
    }

    // ================================================================ Alleen C4 komt erdoor (stap 4)
    //
    // Anders kon je met TNT, een aansteker of een zuiger om de zone heen. C4 breekt zelf zijn blokken
    // (zie item/C4), dus die gaat hier niet langs.

    /**
     * Staat er nu een raket van het satellietbombardement te ontploffen? (Zie SatelliteStrikes: die
     * zet dit aan vlak voor zijn explosie, en daarna weer uit.)
     */
    private static boolean satelliteBlast;

    /** Laat {@code explosion} ontploffen als een raket van de satelliet (voor de breekpunten in een zone). */
    public static void satelliteExplosion(Runnable explosion) {
        satelliteBlast = true;
        try {
            explosion.run();
        } finally {
            satelliteBlast = false;
        }
    }

    /**
     * Een explosie: de blokken van een actieve zone gaan niet zomaar mee, behalve als iemand die er mag
     * bouwen hem veroorzaakte (je eigen TNT in je eigen basis). Wezens krijgen de klap gewoon.
     *
     * TNT en raketten van de satelliet komen er wel door (jouw wens, 9 oktober), maar met breekpunten
     * zoals C4 en veel zwakker: TNT {@link ImtConfig.C4#TNT_BREACH} (C4 doet 10),
     * een satellietraket {@link ImtConfig.C4#SATELLITE_BREACH}. Zo maakt TNT maar
     * een klein beetje kapot aan een muur, en tellen de barsten op. Creepers, vuurballen en de rest
     * maken in een zone niets kapot.
     */
    public static void onExplosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        net.minecraft.world.level.Explosion explosion = event.getExplosion();
        Entity cause = explosion.getIndirectSourceEntity();
        event.getAffectedBlocks().removeIf(pos -> {
            ZoneData.Zone zone = activeAt(level, pos);
            return zone != null && !mayBuild(level, zone, cause);
        });
        Entity direct = explosion.getDirectSourceEntity();
        boolean tnt = direct instanceof net.minecraft.world.entity.item.PrimedTnt
                || direct instanceof net.minecraft.world.entity.vehicle.MinecartTNT;
        if (!tnt && !satelliteBlast || explosion.getBlockInteraction() == net.minecraft.world.level.Explosion.BlockInteraction.KEEP) {
            return;
        }
        net.minecraft.world.phys.Vec3 centre = explosion.center();
        float radius = ImtConfig.C4.EXPLOSION_BREACH_RADIUS;
        ZoneData.Zone zone = activeNear(level, BlockPos.containing(centre), radius);
        if (zone == null) {
            return;
        }
        com.senne.imttech.c4.C4Blast.Breach result = com.senne.imttech.c4.C4Blast.breach(level, centre,
                tnt ? ImtConfig.C4.TNT_BREACH : ImtConfig.C4.SATELLITE_BREACH,
                radius, pos -> {
                    ZoneData.Zone here = activeAt(level, pos);
                    return here != null && !mayBuild(level, here, cause);
                }, zone);
        com.senne.imttech.c4.C4Alarm.blasted(level, BlockPos.containing(centre), zone, result.inZone(), false,
                tnt ? "TNT" : "Satellite strike");
    }

    /**
     * Een zuiger duwt of trekt geen blokken in, uit of door een zone, tenzij hij zelf in diezelfde
     * zone staat (de machines van de eigenaar werken gewoon).
     */
    public static void onPiston(net.neoforged.neoforge.event.level.PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        net.minecraft.world.level.block.piston.PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return; // hij kan toch niet bewegen
        }
        ZoneData.Zone own = activeAt(level, event.getPos());
        net.minecraft.core.Direction move = event.getPistonMoveType().isExtend
                ? event.getDirection() : event.getDirection().getOpposite();
        java.util.List<BlockPos> touched = new java.util.ArrayList<>();
        for (BlockPos pos : resolver.getToPush()) {
            touched.add(pos);
            touched.add(pos.relative(move));
        }
        touched.addAll(resolver.getToDestroy());
        for (BlockPos pos : touched) {
            ZoneData.Zone zone = activeAt(level, pos);
            if (zone != null && zone != own) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * Monsters (endermen, withers, ravagers, zombies bij een deur, zilvervisjes) breken geen blokken
     * in of vlak bij een actieve zone. Dieren en dorpelingen wel gewoon (een dorpeling oogst je akker).
     */
    public static void onMobGriefing(net.neoforged.neoforge.event.entity.EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof net.minecraft.world.entity.monster.Enemy) || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (touchesActiveZone(level, entity.getBoundingBox().inflate(3.0))) {
            event.setCanGrief(false);
        }
    }

    /**
     * Lava steekt in een zone niets aan (het vuur zelf brandt daar ook niets weg, zie FireBlockMixin).
     * Annuleren helpt bij dit event niet (Minecraft kijkt alleen naar het nieuwe blok), dus zetten we
     * het oude blok terug.
     */
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getNewState().getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock
                && activeAt(level, event.getPos()) != null) {
            event.setNewState(event.getOriginalState());
        }
    }

    /** De actieve zone die dit blok raakt of tot {@code reach} blokken ervan ligt, of null. */
    public static @Nullable ZoneData.Zone activeNear(ServerLevel level, BlockPos pos, double reach) {
        ZoneData.Zone inside = activeAt(level, pos);
        if (inside != null) {
            return inside;
        }
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(pos).inflate(reach);
        for (ZoneData.Zone zone : ZoneData.get(level).in(level.dimension())) {
            if (zone.active() && zone.intersects(area)) {
                return zone;
            }
        }
        return null;
    }

    /** Ligt er een actieve zone (deels) in dit stuk ruimte? */
    public static boolean touchesActiveZone(ServerLevel level, net.minecraft.world.phys.AABB area) {
        for (ZoneData.Zone zone : ZoneData.get(level).in(level.dimension())) {
            if (zone.active() && zone.intersects(area)) {
                return true;
            }
        }
        return false;
    }

    /** Mag vuur dit blok wegbranden? Niet in een actieve zone (zie FireBlockMixin). */
    public static boolean fireMayBurn(net.minecraft.world.level.Level level, BlockPos pos) {
        return !(level instanceof ServerLevel server) || activeAt(server, pos) == null;
    }

    // ================================================================ Opruimen

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_TOLD.remove(event.getEntity().getUUID());
        ZoneMarkerItem.forget(event.getEntity().getUUID());
        lastToolChange = null;
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_TOLD.clear();
        ZoneMarkerItem.forget(null);
    }
}
