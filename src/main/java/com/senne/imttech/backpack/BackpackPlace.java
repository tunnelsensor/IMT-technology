package com.senne.imttech.backpack;

import com.senne.imttech.guns.Gear;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Waar een rugzak zit die iemand opent (hoofdstuk 72): <b>op iemands rug</b>, <b>in iemands hand</b>, of <b>neergezet</b>
 * als blok (en in stap 4 op een lichaam). Het menu en de rits praten alleen met dit ding: "geef de rugzak" en "zet hem
 * terug", en "kan deze speler er nog bij?".
 */
public abstract class BackpackPlace {
    /** De rugzak die hier nu zit (een kopie of het echte item; leeg als hij weg is). */
    public abstract ItemStack get();

    /** De (veranderde) rugzak hier terugzetten. */
    public abstract void set(ItemStack pack);

    /** Kan {@code viewer} er (nog) bij? Zo niet, dan stopt de rits of gaat het scherm dicht. */
    public abstract boolean reachable(ServerPlayer viewer);

    /** Waar het ritsgeluid klinkt. */
    public abstract Vec3 position();

    /** De titel van het scherm. */
    public Component title() {
        return get().getHoverName();
    }

    /** Het vakje van je werkbalk dat op slot moet zolang het scherm open is (de rugzak in je hand), of -1. */
    public int lockedSlot(ServerPlayer viewer) {
        return -1;
    }

    // ================================================================ Op je rug

    public static BackpackPlace worn(ServerPlayer wearer) {
        return new Worn(wearer);
    }

    /** Op de rug van {@code wearer} (het vakje "Back", of Curios). */
    public static final class Worn extends BackpackPlace {
        private final ServerPlayer wearer;

        Worn(ServerPlayer wearer) {
            this.wearer = wearer;
        }

        public ServerPlayer wearer() {
            return wearer;
        }

        @Override
        public ItemStack get() {
            return wearer.isRemoved() ? ItemStack.EMPTY : Gear.back(wearer);
        }

        @Override
        public void set(ItemStack pack) {
            Gear.setBack(wearer, pack);
        }

        @Override
        public boolean reachable(ServerPlayer viewer) {
            if (!wearer.isAlive() || wearer.isRemoved()) {
                return false;
            }
            // Je eigen rugzak kun je altijd openen; die van een ander alleen van achteren (stap 3).
            return viewer == wearer;
        }

        @Override
        public Vec3 position() {
            return wearer.position().add(0, 1.2, 0);
        }
    }

    // ================================================================ In je hand

    public static BackpackPlace hand(ServerPlayer player, InteractionHand hand) {
        return new Hand(player, hand);
    }

    public static final class Hand extends BackpackPlace {
        private final ServerPlayer player;
        private final InteractionHand hand;

        Hand(ServerPlayer player, InteractionHand hand) {
            this.player = player;
            this.hand = hand;
        }

        @Override
        public ItemStack get() {
            ItemStack held = player.getItemInHand(hand);
            return BackpackItem.isBackpack(held) ? held : ItemStack.EMPTY;
        }

        @Override
        public void set(ItemStack pack) {
            player.setItemInHand(hand, pack);
        }

        @Override
        public boolean reachable(ServerPlayer viewer) {
            return viewer == player && player.isAlive();
        }

        @Override
        public Vec3 position() {
            return player.position().add(0, 1.0, 0);
        }

        @Override
        public int lockedSlot(ServerPlayer viewer) {
            return hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : -1;
        }
    }

    // ================================================================ Neergezet

    public static BackpackPlace block(ServerLevel level, BlockPos pos) {
        return new Placed(level, pos);
    }

    /** Een neergezette rugzak (BackpackBlock): iedereen mag erin (jouw keuze). */
    public static final class Placed extends BackpackPlace {
        private final ServerLevel level;
        private final BlockPos pos;

        Placed(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos.immutable();
        }

        private BackpackBlockEntity entity() {
            return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BackpackBlockEntity backpack ? backpack : null;
        }

        @Override
        public ItemStack get() {
            BackpackBlockEntity backpack = entity();
            return backpack == null ? ItemStack.EMPTY : backpack.pack();
        }

        @Override
        public void set(ItemStack pack) {
            BackpackBlockEntity backpack = entity();
            if (backpack != null) {
                backpack.setPack(pack);
            }
        }

        @Override
        public boolean reachable(ServerPlayer viewer) {
            return viewer.level() == level && viewer.isAlive() && viewer.distanceToSqr(Vec3.atCenterOf(pos)) <= 64;
        }

        @Override
        public Vec3 position() {
            return Vec3.atCenterOf(pos);
        }
    }
}
