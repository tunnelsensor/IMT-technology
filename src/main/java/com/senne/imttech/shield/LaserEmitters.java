package com.senne.imttech.shield;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.LaserActionPayload;
import com.senne.imttech.network.LaserOpenPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.BaseTrust;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Het instelscherm van de laserzender, aan de kant van de server (13 oktober): het scherm openen, en wat
 * je erin kiest op de zenders zetten. Het scherm zelf staat in client/shield/LaserEmitterScreen.
 *
 * Jouw wens: "zorg dat je gewoon op ze kunt klikken zonder dat er iets gebeurt, dit zorgt ervoor dat het
 * makkelijker is om cables te leggen. Enkel de persoon die het heeft geplaatst (of trusted players in zone)
 * kunnen hier op sneak klikken en dan een GUI openen."
 *
 * Wie mag het scherm openen (zie {@link #mayEdit}; dezelfde regel als bij de keycardlezer, security/BaseTrust)?
 * <ul>
 *   <li>Wie de zender neerzette.</li>
 *   <li>Wie in deze basis vertrouwd is: de eigenaar van de beveiligingskern, zijn team en de spelers die hij
 *       vertrouwt (Access op de desktop). Dat geldt in zijn zone én in de rest van het gebied van de kern,
 *       net als bij de laserwand zelf (daar lopen die spelers gewoon doorheen).</li>
 *   <li>Een zender van niemand (van vóór 13 oktober) wordt van wie hem als eerste opent, net als een oud
 *       relais. Behalve in iemands basis (dan alleen de vertrouwde spelers daar) en in een gebouw van deze
 *       mod (de Biebia's Industries Tower, de Biebia's Industries-bunker): die lasers kun je niet zomaar overnemen en uitzetten.</li>
 * </ul>
 *
 * Wat je kiest, geldt voor de hele laserpoort: alle zenders die eraan vastzitten (die gaan ook samen aan
 * en uit, zie LaserEmitterBlock.group). Een zender van iemand anders in dezelfde poort blijft zoals hij is.
 *
 * Hoofdstuk 33: een inbreker kan met de Bypass Kit de draden doorknippen (zie bypass/Bypass). Dan blijft de hele
 * poort uit tot iemand die hem mag instellen op Repair wires drukt; dat kost een Computer Chip. Knipt hij de
 * verkeerde draad, dan gaat de poort een tijd op slot (voor iedereen).
 */
public final class LaserEmitters {
    private LaserEmitters() {}

    /** Zo dichtbij moet je staan (blokken), net als bij een lamp of stroomkast. */
    private static final double REACH = 6.0;

    /** Sneak + rechtsklik op de zender: mag je hem instellen, dan opent jouw spel het scherm. */
    public static void open(ServerPlayer player, BlockPos pos) {
        LaserEmitterBlockEntity emitter = emitter(player, pos);
        if (emitter == null) {
            return;
        }
        if (!mayEdit(player, emitter)) {
            player.displayClientMessage(refusal(player.serverLevel(), emitter), true);
            return;
        }
        ModNetwork.sendTo(player, new LaserOpenPayload(pos, emitter.ownerName(),
                com.senne.imttech.redstone.DeviceNames.nameOf(player, LaserEmitterBlock.group(player.serverLevel(), pos))));
    }

    /**
     * Save in het scherm (hoofdstuk 39): de poort krijgt deze naam in de I.M.T-lijst van de speler (staat hij er nog niet
     * in, dan komt hij erin). Alleen wie hem mag instellen.
     */
    public static void handleRename(ServerPlayer player, com.senne.imttech.network.LaserRenamePayload payload) {
        LaserEmitterBlockEntity emitter = emitter(player, payload.pos());
        if (emitter == null || !mayEdit(player, emitter)) {
            return;
        }
        renamed(player, com.senne.imttech.redstone.DeviceNames.rename(player,
                LaserEmitterBlock.group(player.serverLevel(), payload.pos()), payload.pos(), payload.name(),
                "imt.imttech.laser_gate.default_name"), payload.name());
    }

    /** Wat de speler hoort na Save: de nieuwe naam, of dat het niet lukte (een lege naam, of zijn lijst is vol). */
    static void renamed(ServerPlayer player, boolean ok, String name) {
        player.displayClientMessage(ok
                ? Component.translatable("imt.imttech.msg.laser_renamed", com.senne.imttech.redstone.ImtRedstone.clean(name,
                        com.senne.imttech.redstone.DeviceNames.MAX_NAME, "")).withStyle(net.minecraft.ChatFormatting.GREEN)
                : Component.translatable("imt.imttech.msg.laser_rename_failed").withStyle(net.minecraft.ChatFormatting.RED), true);
    }

    /** Een knop uit het scherm: de kleur, aan/uit of liggend/staand, voor de hele poort, of de draden herstellen. */
    public static void handleAction(ServerPlayer player, LaserActionPayload payload) {
        LaserEmitterBlockEntity clicked = emitter(player, payload.pos());
        if (clicked == null || !mayEdit(player, clicked)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (payload.op() == LaserActionPayload.Op.REPAIR) {
            repair(player, level, payload.pos());
            return;
        }
        List<BlockPos> gate = new ArrayList<>();
        for (BlockPos member : LaserEmitterBlock.group(level, payload.pos())) {
            if (level.getBlockEntity(member) instanceof LaserEmitterBlockEntity other && mayEdit(player, other)) {
                gate.add(member);
            }
        }
        switch (payload.op()) {
            case COLOR -> LaserEmitterBlock.applyColour(level, gate,
                    ShieldColor.values()[Math.floorMod(payload.value(), ShieldColor.values().length)]);
            case SWITCH -> {
                // Aan of uit (hoofdstuk 39): zoals I.M.T het doet, zodat ook "when turned_on(...)" het hoort.
                if (!gate.isEmpty()) {
                    com.senne.imttech.redstone.ImtRedstone.switchAt(level, gate.getFirst(), -1,
                            payload.value() != 0 ? com.senne.imttech.redstone.ImtRedstone.Op.ON : com.senne.imttech.redstone.ImtRedstone.Op.OFF, 0);
                }
            }
            case TURN -> LaserEmitterBlock.applyTurned(level, gate, payload.value() != 0);
            case REPAIR -> { } // hierboven al gedaan
        }
    }

    // ---------------------------------------------------------------- Doorgeknipte draden (hoofdstuk 33)

    /**
     * De Bypass Kit heeft de goede draad doorgeknipt (zie bypass/Bypass): de hele poort valt uit, en blijft uit tot
     * iemand de draden herstelt. Stil (jouw keuze): geen alarm, alleen een knip, vonken en de wand die terugschuift.
     */
    public static void cutWires(ServerLevel level, BlockPos pos) {
        List<BlockPos> gate = LaserEmitterBlock.group(level, pos);
        LaserEmitterBlock.applyCut(level, gate, true);
        level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0f, 0.8f);
        for (BlockPos member : gate) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, member.getX() + 0.5, member.getY() + 0.5, member.getZ() + 0.5,
                    8, 0.4, 0.4, 0.4, 0.1);
        }
    }

    /**
     * Een verkeerde knip, of de tijd was om: de hele poort gaat op slot, voor iedereen, tot {@code until} (speltijd).
     * Dat staat in de zenders zelf, dus het blijft ook na een herstart.
     */
    public static void lock(ServerLevel level, BlockPos pos, long until) {
        for (BlockPos member : LaserEmitterBlock.group(level, pos)) {
            if (level.getBlockEntity(member) instanceof LaserEmitterBlockEntity emitter) {
                emitter.setLockedUntil(until);
            }
        }
    }

    /** Hoe lang (ticks) deze poort nog op slot zit (0 = niet): van de zender die het langst op slot zit. */
    public static long lockedFor(ServerLevel level, List<BlockPos> gate) {
        long now = level.getGameTime();
        long longest = 0;
        for (BlockPos member : gate) {
            if (level.getBlockEntity(member) instanceof LaserEmitterBlockEntity emitter) {
                longest = Math.max(longest, emitter.lockedUntil() - now);
            }
        }
        return longest;
    }

    /**
     * Repair wires (in het scherm, voor wie de zender mag instellen): de draden van de hele poort weer vast. Het kost
     * {@code LASER_REPAIR_CHIPS} Computer Chips uit je rugzak (jouw keuze: "knop, maar kost iets"); in creative niets.
     * Heb je ze niet, dan gebeurt er niets en hoor je wat je nodig hebt.
     */
    private static void repair(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<BlockPos> gate = LaserEmitterBlock.group(level, pos);
        if (!LaserEmitterBlock.isCut(level, gate)) {
            return;
        }
        int chips = repairChips();
        if (chips > 0 && !player.getAbilities().instabuild) {
            if (player.getInventory().countItem(ModItems.COMPUTER_CHIP.get()) < chips) {
                player.displayClientMessage(Component.translatable("imt.imttech.msg.laser_repair_needs", chips)
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            takeChips(player, chips);
        }
        LaserEmitterBlock.applyCut(level, gate, false);
        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.7f, 1.4f);
        player.displayClientMessage(Component.translatable("imt.imttech.msg.laser_repaired").withStyle(ChatFormatting.GREEN), true);
    }

    /** Zoveel Computer Chips kost het herstellen (uit de config). */
    public static int repairChips() {
        return Math.max(0, Math.round(ImtConfig.Bypass.LASER_REPAIR_CHIPS));
    }

    /** Haalt zoveel Computer Chips uit de rugzak (we weten al dat ze er zijn). */
    private static void takeChips(ServerPlayer player, int chips) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && chips > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.COMPUTER_CHIP.get())) {
                int taken = Math.min(chips, stack.getCount());
                stack.shrink(taken);
                chips -= taken;
            }
        }
        inventory.setChanged();
    }

    /**
     * Mag deze speler de zender instellen? Is de zender nog van niemand en ligt hij niet in iemands basis
     * of in een gebouw, dan wordt hij meteen van deze speler (zie de uitleg bovenaan).
     */
    public static boolean mayEdit(ServerPlayer player, LaserEmitterBlockEntity emitter) {
        return BaseTrust.mayEdit(player, emitter);
    }

    /** Wat je te horen krijgt als je de zender niet mag instellen. */
    private static Component refusal(ServerLevel level, LaserEmitterBlockEntity emitter) {
        String owner = BaseTrust.belongsTo(level, emitter);
        return owner.isEmpty()
                ? Component.translatable("imt.imttech.msg.laser_locked")
                : Component.translatable("imt.imttech.msg.laser_not_yours", owner);
    }

    /** De zender waar deze speler bij kan, of null (er staat daar geen zender, of hij staat te ver weg). */
    private static @Nullable LaserEmitterBlockEntity emitter(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH
                || !(player.serverLevel().getBlockEntity(pos) instanceof LaserEmitterBlockEntity emitter)) {
            return null;
        }
        return emitter;
    }
}
