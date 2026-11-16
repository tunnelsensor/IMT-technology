package com.senne.imttech.lan;

import com.senne.imttech.energy.PowerGrid;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Stroom voor de kleine beveiligingsapparaten op het netwerk (hoofdstuk 38, jouw wens: "deze dingen hebben allemaal stroom
 * nodig, dus de keycardreader heeft een netwerkkabel én een powerkabel"): de keycardlezer, het codepaneel, de
 * netwerkknop en de netwerkhendel. (De switch betaalt zijn stroom zelf, zie NetworkSwitchBlockEntity.)
 *
 * Ze hebben geen eigen klok: de kern waar ze met een netwerkkabel aan hangen, betaalt om de twee seconden hun stroom (zie
 * SecurityCoreBlockEntity.powerTick), van hun eigen stroomnet (een stroomkabel ernaast, of straks een PoE-kabel). Gebruik
 * je ze, dan kijken we of er genoeg stroom op hun net staat.
 *
 * Hoofdstuk 124: de kern werkt ook de stand bij van de apparaten op zijn net die in een stuk wereld staan dat niet draait
 * (zie {@link #refreshIdle}).
 */
public final class DevicePower {
    private DevicePower() {}

    /** Een blok dat zo'n klein apparaat is: hoeveel stroom het elke twee seconden gebruikt (uit [power]). */
    public interface Powered {
        float upkeep();
    }

    /**
     * Een apparaat dat zelf om de paar seconden kijkt of het stroom en netwerk heeft, en dat in zijn blok zet: een camera,
     * een torentje, een switch, een router. Dat doet het in zijn eigen tick, dus alleen als zijn stuk wereld draait; anders
     * doet zijn kern het (zie {@link #refreshIdle}).
     *
     * Zo'n apparaat mag daarbij alleen naar zichzelf kijken en alleen zichzelf veranderen, zonder blokupdates of
     * vormupdates naar zijn buren (UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE): in een stuk wereld dat niet draait, liggen de
     * buren aan de rand soms in een stuk dat niet geladen is, en dat zou de server dan meteen moeten laden (dat hapert).
     * Daarom niet: de Camera Monitor en de Computer (die kijken naar de monitoren naast zich en sturen hun buren een
     * update).
     *
     * En de kern zet zo'n apparaat alleen AAN, nooit uit (gevonden door de nalezing): in zo'n stuk wereld ziet de kern de
     * eigen stroom van het apparaat niet altijd (een batterij of zonnepaneel in het stuk ernaast is dan niet geladen). Hij
     * zou een camera die gewoon werkt dan op "no power" zetten, en een switch uit, met het hele net erachter. Uit gaat het
     * apparaat in zijn eigen tick (als er iemand in de buurt is), of als je het uitzet.
     */
    public interface Idle {
        /**
         * Staat het uit, kijk dan nu of het aan kan, net als in zijn eigen tick (en betaal dan zijn stroom, als het dat daar
         * ook doet). Staat het al aan, dan niets.
         */
        void refreshIdle(ServerLevel level);
    }

    /**
     * Hoofdstuk 124 (jouw melding: "mijn poe kabel is connected en is heel lang hierdoor denk ik dat die niet verbind"): om de
     * twee seconden, voor de apparaten op het net van deze kern die in een stuk wereld staan dat wel geladen is maar niet
     * draait. Zo staat het vaak aan het eind van een lange kabel: NetworkKeeper houdt het net van je kern geladen, maar daar
     * tikken geen blokken. Zo'n camera werkte zijn stand dan nooit bij: had hij één keer "no power" (toen zijn stroom nog
     * maar 512 stukjes kabel ver zocht, zie PowerGrid.MAX_GRID), dan bleef hij zo in je desktop staan, en een switch zonder
     * stroom liet het net erachter nooit meer door. Nu zet de kern zo'n apparaat aan als het kan (alleen aan, zie
     * {@link Idle}). Draait het stuk wereld wel, dan doet het apparaat het zelf (niet dubbel). Een stuk wereld dat niet
     * geladen is, laden we hiervoor niet. Met {@code [network] required = false} (het oude gedrag) niet: dan hangt niets aan
     * een kabel.
     */
    public static void refreshIdle(ServerLevel level, BlockPos core) {
        if (!LanGrid.required()) {
            return;
        }
        for (BlockPos pos : LanGrid.devicesOf(level, core)) {
            net.minecraft.world.level.chunk.LevelChunk chunk =
                    level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && !level.shouldTickBlocksAt(pos) && chunk.getBlockEntity(pos) instanceof Idle idle) {
                idle.refreshIdle(level);
            }
        }
    }

    /**
     * Heeft het apparaat op deze plek stroom? Er moet genoeg op zijn net staan voor twee seconden. Een apparaat dat (volgens
     * de config) niets gebruikt, heeft altijd stroom. Met {@code [network] required = false} (het oude gedrag, voor een oude
     * wereld) hebben lezers en panelen geen stroom nodig, zoals vroeger.
     */
    public static boolean hasPower(Level level, BlockPos pos) {
        if (!LanGrid.required() || !(level.getBlockState(pos).getBlock() instanceof Powered powered) || powered.upkeep() <= 0) {
            return true;
        }
        return PowerGrid.available(level, pos) >= Math.max(1.0, Math.ceil(powered.upkeep()));
    }

    /**
     * Heeft dit apparaat geen stroom? Dan hoort de speler (als die er is) een droog klikje en "No power", en geeft dit
     * true: de aanroeper doet dan verder niets.
     */
    public static boolean dead(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (hasPower(level, pos)) {
            return false;
        }
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.4f, 1.6f);
        if (player != null) {
            player.displayClientMessage(Component.translatable("logic.imttech.input.no_power").withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    /** Om de twee seconden: de stroom van deze apparaten betalen (ieder van zijn eigen net). Niet in het oude gedrag. */
    public static void payUpkeep(ServerLevel level, Iterable<BlockPos> devices) {
        if (!LanGrid.required()) {
            return;
        }
        for (BlockPos pos : devices) {
            if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof Powered powered && powered.upkeep() > 0) {
                PowerGrid.pay(level, pos, powered.upkeep());
            }
        }
    }
}
