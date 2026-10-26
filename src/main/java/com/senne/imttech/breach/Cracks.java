package com.senne.imttech.c4;

import com.senne.imttech.config.ImtConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * De barsten in blokken (hoofdstuk 10 en 11).
 *
 * Een klap die een blok niet breekt (C4, of TNT en de satelliet in een zone: zie C4Blast), geeft het
 * barsten; de volgende klap telt erbij op. Sinds hoofdstuk 11 genezen ze niet meer vanzelf (jouw keuze):
 * ze blijven, ook na een herstart (ze staan in {@link CrackData}), tot je
 * <ul>
 *   <li>ze <b>opvult</b> met Repair Paste: de barsten zijn weg, maar het blok is elke keer
 *       {@link ImtConfig.Repair#PATCH_WEAKER} zwakker (nooit zwakker dan {@link ImtConfig.Repair#PATCH_WEAKEST}); of</li>
 *   <li>het blok <b>vervangt</b>: een nieuw blok is weer helemaal sterk.</li>
 * </ul>
 * Je ziet de barsten zoals wanneer iemand aan een blok hakt.
 */
public final class Cracks {
    private Cracks() {}

    /** Om de zoveel ticks sturen we de barsten opnieuw (je spel laat ze na 400 ticks zelf vallen). */
    private static final int RESEND = 200;

    /** Hoeveel breekpunten dit blok al heeft (0 = geen barsten). */
    public static float damage(ServerLevel level, BlockPos pos) {
        CrackData.Crack crack = CrackData.get(level).at(level, pos);
        return crack == null ? 0 : crack.damage();
    }

    /** Hoe vaak dit blok al geplamuurd is (0 = nooit). */
    public static int patches(ServerLevel level, BlockPos pos) {
        CrackData.Crack crack = CrackData.get(level).at(level, pos);
        return crack == null ? 0 : crack.patches();
    }

    /** Hoe sterk een blok nog is na zoveel keer plamuren: 1 (nooit), 0,85, 0,70... nooit minder dan 0,40. */
    public static float factor(int patches) {
        return Math.max(ImtConfig.Repair.PATCH_WEAKEST, 1.0f - ImtConfig.Repair.PATCH_WEAKER * patches);
    }

    /**
     * Een klap die dit blok niet brak: het heeft nu {@code damage} breekpunten, van de {@code hp} die het
     * kan hebben (zie C4Blast.strength). Hoe vaak het geplamuurd is, blijft.
     */
    static void crack(ServerLevel level, BlockPos pos, Block block, float damage, float hp) {
        CrackData data = CrackData.get(level);
        CrackData.Crack old = data.at(level, pos);
        data.put(level, pos, new CrackData.Crack(damage, block, old == null ? 0 : old.patches()));
        show(level, pos, damage / hp);
    }

    /**
     * De barsten opvullen (Repair Paste): ze zijn weg, maar het blok is een keer vaker geplamuurd, en dus
     * zwakker. Geeft false als er geen barsten zijn (dan verandert er niets).
     */
    public static boolean patch(ServerLevel level, BlockPos pos) {
        CrackData data = CrackData.get(level);
        CrackData.Crack crack = data.at(level, pos);
        if (crack == null || crack.damage() <= 0) {
            return false;
        }
        data.put(level, pos, new CrackData.Crack(0, crack.block(), crack.patches() + 1));
        hide(level, pos);
        return true;
    }

    /**
     * Het blok op deze plek is net een ander blok geworden (gebroken, vervangen, opgeblazen,
     * weggeduwd...): dat nieuwe blok heeft geen barsten en is nooit geplamuurd. Komt uit
     * mixin/LevelChunkMixin, dus bij élk blok dat ergens verandert: daarom eerst snel kijken of er in
     * deze wereld wel iets bijgehouden wordt.
     */
    public static void blockChanged(ServerLevel level, BlockPos pos) {
        CrackData data = CrackData.get(level);
        if (!data.any(level.dimension())) {
            return;
        }
        CrackData.Crack removed = data.remove(level, pos);
        if (removed != null && removed.damage() > 0) {
            hide(level, pos);
        }
    }

    // ================================================================ Wat je ziet

    /**
     * Een eigen nummer per blok voor de barsten (Minecraft onthoudt per nummer maar één blok). Negatief,
     * zodat het nooit het nummer van een speler of wezen is.
     */
    private static int id(BlockPos pos) {
        return -1 - (Long.hashCode(pos.asLong()) & 0x3FFFFFFF);
    }

    /** Barsten laten zien, van 0 (net een beetje) tot 9 (bijna kapot). */
    private static void show(ServerLevel level, BlockPos pos, float fraction) {
        level.destroyBlockProgress(id(pos), pos, Mth.clamp((int) (fraction * 10), 0, 9));
    }

    private static void hide(ServerLevel level, BlockPos pos) {
        level.destroyBlockProgress(id(pos), pos, -1);
    }

    /**
     * Om de tien seconden alle barsten opnieuw sturen naar wie in de buurt is: anders laat je spel ze na
     * twintig seconden vallen, en zo zie je ook barsten die er al waren voor je kwam.
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % RESEND != 0) {
            return;
        }
        CrackData data = CrackData.get(event.getServer());
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Long2ObjectMap.Entry<CrackData.Crack> entry : data.in(level.dimension()).long2ObjectEntrySet()) {
                CrackData.Crack crack = entry.getValue();
                BlockPos pos = BlockPos.of(entry.getLongKey());
                if (crack.damage() <= 0 || !level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                float hp = C4Blast.strength(level, pos, state);
                if (hp > 0 && state.getBlock() == crack.block()) {
                    show(level, pos, crack.damage() / hp);
                }
            }
        }
    }
}
