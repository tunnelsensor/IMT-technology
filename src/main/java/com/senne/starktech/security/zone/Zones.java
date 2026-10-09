package com.senne.starktech.security.zone;

import com.senne.starktech.StarkTech;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.security.SecurityCoreBlockEntity;
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
 * Als plaatsen of breken telt ook: een emmer leeggieten of vullen, iets aansteken, met een bijl of
 * schoffel een blok veranderen, beendermeel, en Stark-kabels leggen of knippen (zie
 * CableSpoolItem). Die laatste gaan allemaal via het plaats-event van NeoForge, behalve de emmer.
 *
 * Wat iedereen wel mag (ook weer weghalen): de "vrije" blokken uit de tag
 * {@code starktech:zone_free}: ladders, scaffolding, klimplanten, fakkels en lantaarns (en straks
 * C4). "In het echt zou dat ook gaan."
 */
public final class Zones {
    private Zones() {}

    /** Blokken die iedereen in een zone mag plaatsen en weghalen (data/starktech/tags/block/zone_free.json). */
    public static final TagKey<Block> FREE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "zone_free"));

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
        serverPlayer.displayClientMessage(Component.translatable("zone.starktech.msg.protected")
                .withStyle(ChatFormatting.RED), true);
        serverPlayer.playNotifySound(ModSounds.EDITH_DENIED.get(), SoundSource.PLAYERS, 0.5f, 1.3f);
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
        if (!(event.getLevel() instanceof ServerLevel level) || isFree(event.getPlacedBlock())) {
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

    /**
     * Een emmer leeggieten of vullen. Dat gaat buiten het plaats-event om, dus kijken we zelf waar
     * de emmer heen wijst (zoals de emmer zelf doet): het blok waar je naar kijkt, en (bij een volle
     * emmer) de plek ervoor.
     */
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getItemStack().getItem() instanceof BucketItem bucket)) {
            return;
        }
        boolean empty = bucket.content == Fluids.EMPTY;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                empty ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos at = hit.getBlockPos();
        BlockPos before = at.relative(hit.getDirection());
        if (!denies(level, at, player) && (empty || !denies(level, before, player))) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        // Je spel liet het water misschien al stromen, en dacht dat de emmer leeg was: zet het terug.
        player.connection.send(new ClientboundBlockUpdatePacket(level, at));
        player.connection.send(new ClientboundBlockUpdatePacket(level, before));
        player.inventoryMenu.sendAllDataToRemote();
        tellProtected(player);
    }

    // ================================================================ Opruimen

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_TOLD.remove(event.getEntity().getUUID());
        ZoneMarkerItem.forget(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_TOLD.clear();
        ZoneMarkerItem.forget(null);
    }
}
