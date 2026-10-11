package com.senne.starktech.pickpocket;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.logic.KeycardData;
import com.senne.starktech.logic.KeycardItem;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.network.PocketPayload;
import com.senne.starktech.registry.ModSounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Zakkenrollen (hoofdstuk 12, jouw keuze: een zak-minigame). Sluip met een lege hand achter iemand en
 * rechtsklik op hem: op jouw scherm opent zijn zak. Daarin liggen zijn keycards omgekeerd tussen rommel;
 * jij sleept dingen opzij en trekt een kaart naar boven, terwijl een meter volloopt (zie PocketScreen).
 *
 * Wat de server bijhoudt:
 * <ul>
 *   <li>welke kaarten er in de zak zitten (de vakjes van zijn inventaris, door elkaar). Je spel weet alleen
 *       HOEVEEL; welke kaart achter "kaart 2" zit, hoor je pas als je hem eruit hebt;</li>
 *   <li>of het slachtoffer zich omdraait en je ziet (dan ben je betrapt), of wegloopt (dan houdt het op);</li>
 *   <li>hoe lang je moet wachten voor je het bij dezelfde speler weer mag proberen.</li>
 * </ul>
 * Betrapt: hij krijgt een melding met je naam en jij licht een tijdje op. Lukt het, dan merkt hij niets.
 */
public final class Pickpocket {
    private Pickpocket() {}

    /** Staat de dief achter het slachtoffer? Kleiner dan dit = meer dan ongeveer 100 graden naast zijn kijkrichting. */
    private static final double BEHIND = -0.17;
    /** Ziet het slachtoffer de dief? Groter dan dit = binnen ongeveer 60 graden van waar hij kijkt. */
    private static final double SEES = 0.5;
    /** Zo lang mag een zak openstaan (2 minuten). */
    private static final int MAX_TICKS = 2400;

    /** Eén poging: van wie de zak is, welke vakjes van zijn inventaris de kaarten zijn, en sinds wanneer. */
    private record Theft(UUID victim, int victimId, List<Integer> slots, long startedAt) {}

    private static final Map<UUID, Theft> THEFTS = new HashMap<>();
    /** Dief -> (slachtoffer -> tot wanneer hij het daar niet opnieuw mag proberen). */
    private static final Map<UUID, Map<UUID, Long>> COOLDOWNS = new HashMap<>();

    // ================================================================ Beginnen

    /** Rechtsklik op een speler: sluipend en met een lege hand is dat zakkenrollen. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer thief)
                || !(event.getTarget() instanceof ServerPlayer victim)) {
            return;
        }
        if (!thief.isShiftKeyDown() || !thief.getMainHandItem().isEmpty()) {
            return;
        }
        tryStart(thief, victim);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * Probeert de zak van {@code victim} te openen. Geeft true als het scherm opengaat. Zo niet, dan zegt
     * de server waarom (te ver, hij zou je zien, nog even wachten), behalve bij spelers in creative of
     * spectator (daar valt niets te pikken).
     */
    public static boolean tryStart(ServerPlayer thief, ServerPlayer victim) {
        if (!StarkConfig.Rules.PICKPOCKET_ALLOWED) {
            thief.displayClientMessage(Component.translatable("pickpocket.starktech.msg.disabled")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (thief == victim || thief.isSpectator() || victim.isSpectator() || victim.isCreative() || !victim.isAlive()
                || thief.level() != victim.level()) {
            return false;
        }
        if (thief.distanceTo(victim) > StarkConfig.Bypass.PICKPOCKET_REACH) {
            return false;
        }
        if (!isBehind(victim, thief)) {
            thief.displayClientMessage(Component.translatable("pickpocket.starktech.msg.not_behind", victim.getDisplayName())
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        long wait = waitFor(thief, victim);
        if (wait > 0) {
            thief.displayClientMessage(Component.translatable("pickpocket.starktech.msg.wait", victim.getDisplayName(),
                    (wait + 19) / 20).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        // Zijn keycards: alles in zijn inventaris, behalve wat hij in zijn hand houdt (die zit niet in een zak).
        Inventory inventory = victim.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (slot != inventory.selected && inventory.items.get(slot).getItem() instanceof KeycardItem) {
                slots.add(slot);
            }
        }
        Collections.shuffle(slots, new java.util.Random(thief.getRandom().nextLong()));
        if (slots.size() > Math.round(StarkConfig.Bypass.POCKET_MAX_CARDS)) {
            slots = new ArrayList<>(slots.subList(0, Math.round(StarkConfig.Bypass.POCKET_MAX_CARDS)));
        }
        THEFTS.put(thief.getUUID(), new Theft(victim.getUUID(), victim.getId(), slots, thief.serverLevel().getGameTime()));
        CompoundTag args = new CompoundTag();
        args.putString("name", victim.getGameProfile().getName());
        args.putInt("cards", slots.size());
        args.putLong("seed", thief.getRandom().nextLong());
        ModNetwork.sendTo(thief, new PocketPayload(victim.getId(), "open", args));
        return true;
    }

    /** Staat {@code thief} achter {@code victim} (meer dan ongeveer 100 graden naast waar hij heen kijkt)? */
    public static boolean isBehind(ServerPlayer victim, ServerPlayer thief) {
        Vec3 look = victim.getViewVector(1.0f);
        Vec3 flatLook = new Vec3(look.x, 0, look.z).normalize();
        Vec3 toThief = thief.position().subtract(victim.position());
        Vec3 flatTo = new Vec3(toThief.x, 0, toThief.z).normalize();
        return flatLook.dot(flatTo) < BEHIND;
    }

    /** Ziet {@code victim} de dief: kijkt hij ongeveer zijn kant op, zonder muur ertussen? */
    public static boolean sees(ServerPlayer victim, ServerPlayer thief) {
        Vec3 look = victim.getViewVector(1.0f).normalize();
        Vec3 toThief = thief.getEyePosition().subtract(victim.getEyePosition()).normalize();
        return look.dot(toThief) > SEES && victim.hasLineOfSight(thief);
    }

    // ================================================================ Wat het scherm stuurt

    public static void handle(ServerPlayer thief, PocketPayload payload) {
        Theft theft = THEFTS.get(thief.getUUID());
        if (theft == null || theft.victimId() != payload.victim()) {
            return;
        }
        ServerPlayer victim = victimOf(thief.getServer(), theft);
        switch (payload.op()) {
            case "take" -> {
                if (victim != null) {
                    take(thief, victim, theft, payload.args().getInt("index"));
                }
            }
            case "caught" -> {
                if (victim != null) {
                    caught(thief, victim, theft);
                }
            }
            case "cancel" -> end(thief, theft);
            default -> { }
        }
    }

    /**
     * De dief trok kaart {@code index} uit de zak. Zit er in dat vakje nog een keycard (en houdt het
     * slachtoffer hem niet net vast), dan gaat er één naar de dief. Anders is hij "weggeglipt".
     */
    private static void take(ServerPlayer thief, ServerPlayer victim, Theft theft, int index) {
        end(thief, theft);
        CompoundTag args = new CompoundTag();
        int slot = index >= 0 && index < theft.slots().size() ? theft.slots().get(index) : -1;
        Inventory inventory = victim.getInventory();
        ItemStack stack = slot >= 0 ? inventory.items.get(slot) : ItemStack.EMPTY;
        if (slot == inventory.selected || !(stack.getItem() instanceof KeycardItem)) {
            args.putString("result", "slipped");
            ModNetwork.sendTo(thief, new PocketPayload(theft.victimId(), "result", args));
            return;
        }
        ItemStack taken = stack.split(1);
        inventory.setChanged();
        KeycardData data = KeycardItem.data(taken);
        args.putString("result", "taken");
        args.putString("name", taken.getHoverName().getString());
        args.putInt("level", data == null ? 0 : data.level());
        if (!thief.getInventory().add(taken)) {
            thief.drop(taken, false);
        }
        thief.serverLevel().playSound(null, thief.blockPosition(), net.minecraft.sounds.SoundEvents.BUNDLE_REMOVE_ONE,
                SoundSource.PLAYERS, 0.15f, 1.3f);
        ModNetwork.sendTo(thief, new PocketPayload(theft.victimId(), "result", args));
    }

    /** Betrapt: het slachtoffer hoort het (met je naam), en jij licht een tijdje op. */
    private static void caught(ServerPlayer thief, ServerPlayer victim, Theft theft) {
        end(thief, theft);
        victim.sendSystemMessage(Component.translatable("pickpocket.starktech.msg.victim", thief.getDisplayName())
                .withStyle(ChatFormatting.RED));
        victim.playNotifySound(ModSounds.EDITH_ALERT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        thief.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.round(StarkConfig.Bypass.PICKPOCKET_GLOW_SECONDS * 20), 0, false, false));
        thief.displayClientMessage(Component.translatable("pickpocket.starktech.msg.caught", victim.getDisplayName())
                .withStyle(ChatFormatting.RED), true);
        CompoundTag args = new CompoundTag();
        args.putString("result", "caught");
        ModNetwork.sendTo(thief, new PocketPayload(theft.victimId(), "result", args));
    }

    /** De poging is voorbij: de wachttijd voor deze speler begint. */
    private static void end(ServerPlayer thief, Theft theft) {
        THEFTS.remove(thief.getUUID());
        COOLDOWNS.computeIfAbsent(thief.getUUID(), id -> new HashMap<>()).put(theft.victim(),
                thief.serverLevel().getGameTime() + Math.round(StarkConfig.Bypass.PICKPOCKET_COOLDOWN_SECONDS * 20));
    }

    private static @Nullable ServerPlayer victimOf(@Nullable MinecraftServer server, Theft theft) {
        return server == null ? null : server.getPlayerList().getPlayer(theft.victim());
    }

    // ================================================================ Bijhouden

    /**
     * Elke tick, voor elke open zak: draait het slachtoffer zich om en ziet hij je, dan ben je betrapt.
     * Loopt hij weg, is hij weg, of sta jij niet meer naast hem, dan houdt het op.
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (THEFTS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Theft>> it = new ArrayList<>(THEFTS.entrySet()).iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Theft> entry = it.next();
            ServerPlayer thief = server.getPlayerList().getPlayer(entry.getKey());
            Theft theft = entry.getValue();
            if (thief == null) {
                THEFTS.remove(entry.getKey());
                continue;
            }
            ServerPlayer victim = victimOf(server, theft);
            boolean gone = victim == null || !victim.isAlive() || !thief.isAlive() || victim.level() != thief.level()
                    || thief.serverLevel().getGameTime() - theft.startedAt() > MAX_TICKS;
            if (!gone && sees(victim, thief)) {
                caught(thief, victim, theft);
            } else if (gone || thief.distanceTo(victim) > StarkConfig.Bypass.POCKET_LEAVE) {
                end(thief, theft);
                CompoundTag args = new CompoundTag();
                args.putString("result", "away");
                ModNetwork.sendTo(thief, new PocketPayload(theft.victimId(), "result", args));
            }
        }
    }

    /** Hoe lang (ticks) deze dief nog moet wachten voor hij het bij deze speler weer mag proberen. */
    public static long waitFor(ServerPlayer thief, ServerPlayer victim) {
        Map<UUID, Long> mine = COOLDOWNS.get(thief.getUUID());
        if (mine == null) {
            return 0;
        }
        long now = thief.serverLevel().getGameTime();
        mine.values().removeIf(until -> until <= now);
        Long until = mine.get(victim.getUUID());
        return until == null ? 0 : until - now;
    }

    /** Doorzoekt deze speler nu iemands zak? */
    public static boolean isStealing(ServerPlayer thief) {
        return THEFTS.containsKey(thief.getUUID());
    }

    /** Hoeveel kaarten er in de zak liggen die deze dief doorzoekt (voor de tests), of -1. */
    public static int cardsInPocket(ServerPlayer thief) {
        Theft theft = THEFTS.get(thief.getUUID());
        return theft == null ? -1 : theft.slots().size();
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        THEFTS.remove(id);
        THEFTS.values().removeIf(theft -> theft.victim().equals(id));
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        clear();
    }

    /** Alles vergeten (bij het stoppen van de server, en in de tests). */
    public static void clear() {
        THEFTS.clear();
        COOLDOWNS.clear();
    }
}
