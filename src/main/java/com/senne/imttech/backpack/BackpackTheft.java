package com.senne.imttech.backpack;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.pickpocket.Pickpocket;
import com.senne.imttech.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Een rugzak van achteren openen en eruit stelen (hoofdstuk 72, stap 3, jouw keuze: "een ander kan van achteren naar je toe
 * sluipen, je rugzak opendoen en eruit stelen; er loopt een meter zoals bij zakkenrollen").
 * <ul>
 *   <li><b>Beginnen</b>: sluip met een lege hand achter iemand die een rugzak draagt en rechtsklik op zijn rugzak (de
 *       bovenste helft van zijn lijf; op zijn heupen is het zakkenrollen, zie Pickpocket). Dan rits je hem open, net als
 *       je eigen rugzak (stilstaan), maar zachter: hij kan het horen.</li>
 *   <li><b>De meter</b> ("NOTICED", in het scherm van de rugzak): elke greep in zijn rugzak maakt geluid
 *       ({@code theftNoisePerTake}; een hele stapel met shift-klik dubbel, een MOLLE-pouch eraf driedubbel), en als je
 *       even niets doet zakt hij weer ({@code theftNoiseDecay}). Vol = hij merkt het.</li>
 *   <li><b>Betrapt</b> (de meter vol, of hij draait zich om en ziet je): hij krijgt een melding met je naam en een
 *       piep, jij licht een tijdje op (zoals bij zakkenrollen), je scherm gaat dicht, en je moet even wachten voor je het
 *       bij hem weer mag proberen.</li>
 *   <li>Loopt hij weg, of sta je niet meer achter hem, dan gaat het scherm gewoon dicht (zonder betrapt).</li>
 * </ul>
 * De afstanden, het oplichten en de wachttijd zijn die van zakkenrollen ({@code [bypass]}); aan en uit met
 * {@code [backpacks] stealAllowed}.
 */
public final class BackpackTheft {
    private BackpackTheft() {}

    /** Een dief bij een slachtoffer: hoe vol de meter is. */
    private static final class State {
        final UUID victim;
        float meter;

        State(UUID victim) {
            this.victim = victim;
        }
    }

    private static final Map<UUID, State> THEFTS = new HashMap<>();
    /** Dief -> (slachtoffer -> tot wanneer hij het daar niet opnieuw mag proberen). */
    private static final Map<UUID, Map<UUID, Long>> COOLDOWNS = new HashMap<>();

    // ================================================================ Beginnen

    /**
     * Rechtsklik op een speler, op een plek van zijn lijf (NeoForge geeft dan ook waar je klikte): sluipend met een lege
     * hand, van achteren, op zijn rugzak = zijn rugzak openritsen. Jouw spel houdt dan de gewone klik tegen (anders begon
     * ook het zakkenrollen), zie client/backpack/BackpackClient.
     */
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer thief) || !(event.getTarget() instanceof ServerPlayer victim)) {
            return;
        }
        if (!aimsAtPack(thief, victim, event.getLocalPos())) {
            return;
        }
        tryStart(thief, victim);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * Is dit de klik van een dief op een rugzak: sluipend, lege hand, het slachtoffer draagt een rugzak, en je klikte op
     * de bovenste helft van zijn lijf ({@code local} = waar, ten opzichte van zijn voeten)? Ook jouw spel vraagt dit.
     */
    public static boolean aimsAtPack(Player thief, Player victim, Vec3 local) {
        return thief != victim && thief.isShiftKeyDown() && thief.getMainHandItem().isEmpty() && !Gear.back(victim).isEmpty()
                && local.y >= victim.getBbHeight() * 0.55;
    }

    /** Staat de dief achter het slachtoffer (meer dan ongeveer 100 graden naast waar hij heen kijkt; zoals zakkenrollen)? */
    public static boolean behind(Player victim, Player thief) {
        Vec3 look = victim.getViewVector(1.0f);
        Vec3 flatLook = new Vec3(look.x, 0, look.z).normalize();
        Vec3 to = thief.position().subtract(victim.position());
        Vec3 flatTo = new Vec3(to.x, 0, to.z);
        if (flatTo.lengthSqr() < 1.0E-4) {
            return true;
        }
        return flatLook.dot(flatTo.normalize()) < -0.17;
    }

    /** Ruimer: staat de dief nog ongeveer achter hem (de halve cirkel achter zijn rug)? Zo niet, dan gaat het scherm dicht. */
    static boolean stillBehind(Player victim, Player thief) {
        Vec3 look = victim.getViewVector(1.0f);
        Vec3 flatLook = new Vec3(look.x, 0, look.z).normalize();
        Vec3 to = thief.position().subtract(victim.position());
        Vec3 flatTo = new Vec3(to.x, 0, to.z);
        return flatTo.lengthSqr() < 1.0E-4 || flatLook.dot(flatTo.normalize()) < 0.2;
    }

    /** Probeert de rugzak van {@code victim} open te ritsen. Geeft true als de rits begint; zo niet, dan zegt hij waarom. */
    public static boolean tryStart(ServerPlayer thief, ServerPlayer victim) {
        if (!ImtConfig.Backpacks.STEAL_ALLOWED) {
            thief.displayClientMessage(Component.translatable("backpack.imttech.theft_disabled").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (thief == victim || thief.isSpectator() || victim.isSpectator() || victim.isCreative() || !victim.isAlive()
                || thief.level() != victim.level() || Gear.back(victim).isEmpty()) {
            return false;
        }
        if (thief.distanceTo(victim) > ImtConfig.Bypass.PICKPOCKET_REACH) {
            return false;
        }
        if (!behind(victim, thief)) {
            thief.displayClientMessage(Component.translatable("backpack.imttech.not_behind", victim.getDisplayName())
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        long wait = waitFor(thief, victim);
        if (wait > 0) {
            thief.displayClientMessage(Component.translatable("backpack.imttech.theft_wait", (wait + 19) / 20, victim.getDisplayName())
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        THEFTS.put(thief.getUUID(), new State(victim.getUUID()));
        if (!BackpackZip.start(thief, BackpackPlace.theft(victim, thief))) {
            THEFTS.remove(thief.getUUID());
            return false;
        }
        return true;
    }

    private static long waitFor(ServerPlayer thief, ServerPlayer victim) {
        Map<UUID, Long> mine = COOLDOWNS.get(thief.getUUID());
        Long until = mine == null ? null : mine.get(victim.getUUID());
        return until == null ? 0 : Math.max(0, until - thief.serverLevel().getGameTime());
    }

    /** Is deze speler (als dief) nu bezig, met de rits of met het scherm? */
    public static boolean active(ServerPlayer thief) {
        return THEFTS.containsKey(thief.getUUID());
    }

    /** Hoe vol zijn meter is (0 tot 1), of 0. */
    public static float meter(ServerPlayer thief) {
        State state = THEFTS.get(thief.getUUID());
        return state == null ? 0 : state.meter;
    }

    // ================================================================ De meter

    /** Een greep in de rugzak van het slachtoffer (uit BackpackMenu.clicked): de meter loopt op. */
    public static void noise(ServerPlayer thief, float times) {
        State state = THEFTS.get(thief.getUUID());
        if (state == null) {
            return;
        }
        state.meter = Math.min(1, state.meter + ImtConfig.Backpacks.THEFT_NOISE_PER_TAKE * times);
        ModNetwork.sendTo(thief, new BackpackTheftPayload(state.meter));
        if (state.meter >= 1) {
            ServerPlayer victim = thief.getServer() == null ? null : thief.getServer().getPlayerList().getPlayer(state.victim);
            if (victim != null) {
                caught(thief, victim);
            } else {
                end(thief, state);
            }
        }
    }

    /**
     * Elke tick van de server, voor elke dief: ziet het slachtoffer hem (hij draaide zich om), dan is hij betrapt. Is de
     * rits of het scherm weg, dan is het voorbij. Anders zakt de meter een beetje.
     */
    public static void tick(MinecraftServer server) {
        if (THEFTS.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, State> entry : new ArrayList<>(THEFTS.entrySet())) {
            ServerPlayer thief = server.getPlayerList().getPlayer(entry.getKey());
            State state = entry.getValue();
            if (thief == null) {
                THEFTS.remove(entry.getKey());
                continue;
            }
            ServerPlayer victim = server.getPlayerList().getPlayer(state.victim);
            boolean open = thief.containerMenu instanceof BackpackMenu menu && menu.theft();
            if (victim == null || (!open && !BackpackZip.busy(thief))) {
                end(thief, state);
                continue;
            }
            if (Pickpocket.sees(victim, thief)) {
                caught(thief, victim);
                continue;
            }
            if (open && state.meter > 0) {
                state.meter = Math.max(0, state.meter - ImtConfig.Backpacks.THEFT_NOISE_DECAY / 20f);
                if (server.getTickCount() % 4 == 0) {
                    ModNetwork.sendTo(thief, new BackpackTheftPayload(state.meter));
                }
            }
        }
    }

    /** Betrapt: hij hoort het (met je naam), jij licht op, je scherm gaat dicht, en de wachttijd begint. */
    private static void caught(ServerPlayer thief, ServerPlayer victim) {
        State state = THEFTS.get(thief.getUUID());
        end(thief, state);
        BackpackZip.cancel(thief, null);
        if (thief.containerMenu instanceof BackpackMenu menu && menu.theft()) {
            thief.closeContainer();
        }
        victim.sendSystemMessage(Component.translatable("backpack.imttech.theft_victim", thief.getDisplayName()).withStyle(ChatFormatting.RED));
        victim.playNotifySound(ModSounds.IMT_ALERT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        thief.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.round(ImtConfig.Bypass.PICKPOCKET_GLOW_SECONDS * 20), 0, false, false));
        thief.displayClientMessage(Component.translatable("backpack.imttech.theft_caught", victim.getDisplayName())
                .withStyle(ChatFormatting.RED), true);
        ModNetwork.sendTo(thief, new BackpackTheftPayload(-1));
    }

    /** Voorbij: de wachttijd voor dit slachtoffer begint. */
    private static void end(ServerPlayer thief, State state) {
        THEFTS.remove(thief.getUUID());
        if (state != null) {
            COOLDOWNS.computeIfAbsent(thief.getUUID(), id -> new HashMap<>()).put(state.victim,
                    thief.serverLevel().getGameTime() + Math.round(ImtConfig.Bypass.PICKPOCKET_COOLDOWN_SECONDS * 20));
        }
    }

    /** Alles vergeten (de server stopt). */
    public static void clear() {
        THEFTS.clear();
        COOLDOWNS.clear();
    }

    /** Voor de tests: hoe vol de meter is, rond op 2 cijfers (0 als er geen diefstal is). */
    public static float meterRounded(ServerPlayer thief) {
        return Mth.floor(meter(thief) * 100) / 100f;
    }
}
