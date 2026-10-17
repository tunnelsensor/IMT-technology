package com.senne.imttech.repair;

import com.senne.imttech.c4.CrackData;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.zone.Zones;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Een blok met barsten (of een geplamuurd blok) weghakken geeft een <b>gebarsten blok</b> (hoofdstuk 11,
 * jouw wens: "als je de blok vervangt hou je dus de gebarsten blok"). Dat steek je in de Crusher.
 *
 * <ul>
 *   <li>Valt het blok normaal zelf (obsidiaan, bakstenen), dan wordt dat een "Cracked ...", evenveel
 *       als anders (een dubbele plaat geeft er twee).</li>
 *   <li>Valt er normaal iets anders (steen geeft keien), dan krijg je in de plaats één gebarsten blok
 *       van het blok zelf: gebarsten steen, geen keien (mijn invulling).</li>
 *   <li>Valt er niets (geen goed gereedschap, of glas zonder zijdezacht), dan ook geen gebarsten blok.</li>
 * </ul>
 * Alleen als een speler het weghakt: wat C4 of TNT kapotmaakt, valt eruit zoals altijd. Blokken met
 * iets erin (een kist, een shulkerdoos...) en de vrije blokken van een zone (ladders, fakkels...) gaan
 * ook zoals altijd.
 *
 * Hoe: bij het breken (BreakEvent) kijken we of het blok barsten had, want daarna is het weg en
 * vergeet de wereld zijn barsten (zie LevelChunkMixin); vlak daarna (BlockDropsEvent) passen we aan
 * wat eruit valt.
 */
public final class CrackDrops {
    private CrackDrops() {}

    /** Blokken die een speler deze tick brak en barsten hadden: plek -> welk blok. */
    private static final Map<GlobalPos, Block> BREAKING = new HashMap<>();

    /** Kan dit blok een gebarsten blok geven? */
    public static boolean canCrack(BlockState state) {
        return !state.hasBlockEntity() && state.getBlock().asItem() != Items.AIR && !Zones.isFree(state);
    }

    /** Een speler breekt een blok (en niemand hield het tegen): had het barsten, dan onthouden we dat even. */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !canCrack(event.getState())) {
            return;
        }
        CrackData.Crack crack = CrackData.get(level).at(level, event.getPos());
        if (crack != null && (crack.damage() > 0 || crack.patches() > 0)) {
            BREAKING.put(GlobalPos.of(level.dimension(), event.getPos().immutable()), crack.block());
        }
    }

    /** Wat eruit valt: het blok zelf wordt een gebarsten blok (zie boven). */
    public static void onDrops(BlockDropsEvent event) {
        if (BREAKING.isEmpty()) {
            return;
        }
        Block block = BREAKING.remove(GlobalPos.of(event.getLevel().dimension(), event.getPos()));
        List<ItemEntity> drops = event.getDrops();
        if (block == null || event.getState().getBlock() != block || drops.isEmpty()) {
            return;
        }
        Item cracked = ModItems.CRACKED_BLOCK.get();
        boolean itself = false;
        for (ItemEntity drop : drops) {
            if (drop.getItem().is(block.asItem())) {
                drop.setItem(FromBlockItem.of(cracked, block, drop.getItem().getCount()));
                itself = true;
            }
        }
        if (!itself) {
            ItemEntity first = drops.get(0);
            first.setItem(FromBlockItem.of(cracked, block, 1));
            drops.removeIf(drop -> drop != first);
        }
    }

    /** Wat deze tick niet gebruikt werd (er viel niets uit), vergeten we weer. */
    public static void onServerTick(ServerTickEvent.Post event) {
        BREAKING.clear();
    }
}
