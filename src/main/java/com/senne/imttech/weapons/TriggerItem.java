package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De triggers (hoofdstuk 35): de <b>EMP Trigger</b> voor de Remote EMP's (jouw keuze: een eigen item) en de <b>Frag
 * Trigger</b> voor de Remote Frags (26 oktober, jouw keuze: "een nieuwe mooie 3D-model zoals de EMP Trigger, en de oude
 * C4-trigger moet weg"). Ze werken allebei zo:
 * <ul>
 *   <li>plak je een lading terwijl je een trigger van dezelfde soort bij je hebt, dan hoort hij bij die trigger; een
 *       lading die nog nergens bij hoort, koppel je met een rechtsklik met de trigger erop;</li>
 *   <li><b>rechtsklik</b>: al zijn ladingen binnen TRIGGER_RANGE blokken gaan af, kort na elkaar;</li>
 *   <li><b>sneak + rechtsklik</b>: hoeveel er bij hem horen en hoeveel er in bereik zijn.</li>
 * </ul>
 * Hoofdstuk 69: haalt iemand een lading met sneak + lege hand van de muur, dan blijft die aan deze trigger (jouw keuze).
 * Hangt hij hem ergens anders op, dan hoort de trigger dat via {@link MovedCharges} ({@link #catchUp}). Draagt iemand hem
 * nog als je drukt (in bereik), of ligt hij op de grond, dan gaat hij daar af.
 * Met een trigger in je hand zie je boven elke lading een bordje (zie client/weapons/TriggerMarkers). Wat hij onthoudt
 * (zijn nummer en waar zijn ladingen zitten, {@link TriggerData}) staat per soort in een eigen component, zodat een EMP
 * Trigger nooit een Remote Frag laat afgaan en andersom.
 */
public class TriggerItem extends Item {
    /** Zoveel ticks zit er tussen twee ladingen die na elkaar afgaan. */
    private static final int BETWEEN = 3;

    private final RemoteChargeBlock.Kind kind;

    public TriggerItem(RemoteChargeBlock.Kind kind, Properties properties) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    /** Welke ladingen deze trigger laat afgaan. */
    public RemoteChargeBlock.Kind kind() {
        return kind;
    }

    /** De kleur van zijn teksten: cyaan (EMP) of rood (frag). */
    public ChatFormatting colour() {
        return kind == RemoteChargeBlock.Kind.EMP ? ChatFormatting.AQUA : ChatFormatting.RED;
    }

    /** Waar deze soort trigger zijn gegevens bewaart. */
    private DataComponentType<TriggerData> component() {
        return kind == RemoteChargeBlock.Kind.EMP ? ModDataComponents.EMP_TRIGGER.get() : ModDataComponents.FRAG_TRIGGER.get();
    }

    /** De naam van een tekst voor deze soort: "item.imttech.emp_trigger.fired" of "item.imttech.frag_trigger.fired". */
    private String key(String prefix, String what) {
        return prefix + (kind == RemoteChargeBlock.Kind.EMP ? "emp_trigger" : "frag_trigger") + "." + what;
    }

    /** Wat deze trigger onthoudt, of null als hij nog nooit gebruikt is (of geen trigger is). */
    public static @Nullable TriggerData data(ItemStack stack) {
        return stack.getItem() instanceof TriggerItem trigger ? stack.get(trigger.component()) : null;
    }

    /** Wat deze trigger onthoudt; heeft hij nog geen nummer, dan krijgt hij er nu een. */
    public static TriggerData ensure(ItemStack stack) {
        TriggerData data = data(stack);
        if (data == null) {
            data = new TriggerData(UUID.randomUUID(), List.of());
            if (stack.getItem() instanceof TriggerItem trigger) {
                stack.set(trigger.component(), data);
            }
        }
        return data;
    }

    /** De trigger van deze soort die deze speler bij zich heeft (eerst in zijn andere hand), of null. */
    public static @Nullable ItemStack find(Player player, RemoteChargeBlock.Kind kind) {
        if (player.getOffhandItem().getItem() instanceof TriggerItem trigger && trigger.kind == kind) {
            return player.getOffhandItem();
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof TriggerItem trigger && trigger.kind == kind) {
                return stack;
            }
        }
        return null;
    }

    /** Koppelt de lading op {@code pos} aan deze trigger. Geeft false als de trigger vol zit. */
    public static boolean link(ServerLevel level, BlockPos pos, RemoteChargeBlockEntity charge, ItemStack stack) {
        if (!(stack.getItem() instanceof TriggerItem trigger)) {
            return false;
        }
        TriggerData data = ensure(stack);
        GlobalPos here = GlobalPos.of(level.dimension(), pos.immutable());
        TriggerData linked = data.with(here);
        if (linked == data && !data.charges().contains(here)) {
            return false;
        }
        stack.set(trigger.component(), linked);
        charge.link(linked.id());
        return true;
    }

    /**
     * Hoofdstuk 69: ladingen van deze trigger die iemand verplaatste (zie MovedCharges): de oude plekken eruit, de nieuwe
     * erbij. Zo staan de bordjes boven je ladingen (TriggerMarkers) weer goed.
     */
    public static void catchUp(ServerLevel level, ItemStack stack) {
        TriggerData data = data(stack);
        if (data == null || !(stack.getItem() instanceof TriggerItem trigger)) {
            return;
        }
        MovedCharges moved = MovedCharges.get(level.getServer());
        if (moved.has(data.id())) {
            stack.set(trigger.component(), moved.apply(data));
        }
    }

    /** Elke tick in je inventaris: bijhouden waar je ladingen nu hangen. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (level instanceof ServerLevel server) {
            catchUp(server, stack);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                status(serverLevel, serverPlayer, stack);
            } else {
                fire(serverLevel, serverPlayer, stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Tot hoe ver het signaal reikt (blokken). */
    public static double range() {
        return Math.round(ImtConfig.Weapons.TRIGGER_RANGE);
    }

    /** Wat er met een signaal gebeurt: zoveel gingen af, zoveel waren te ver. */
    public record Fired(int fired, int outOfRange) {}

    /**
     * De knop: al zijn ladingen in bereik gaan af, de dichtstbijzijnde eerst en dan om de drie ticks de volgende. Wat er
     * niet meer is (afgegaan, eraf gehaald of onschadelijk gemaakt), vergeet hij.
     */
    public static Fired fire(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof TriggerItem trigger)) {
            return new Fired(0, 0);
        }
        level.playSound(null, player.blockPosition(), ModSounds.TRIGGER_CLICK.get(), SoundSource.PLAYERS, 1.0f,
                trigger.kind == RemoteChargeBlock.Kind.EMP ? 1.3f : 1.0f);
        catchUp(level, stack);
        TriggerData data = data(stack);
        // Hoofdstuk 69: wie een lading van deze trigger nog bij zich heeft (in bereik), of waar hij op de grond ligt: daar
        // gaat hij af.
        int loose = data == null ? 0 : goOffLoose(level, player.position(), trigger.kind, data.id(), player.getUUID());
        if ((data == null || data.charges().isEmpty()) && loose == 0) {
            player.displayClientMessage(Component.translatable(trigger.key("item.imttech.", "none")).withStyle(ChatFormatting.GRAY), true);
            return new Fired(0, 0);
        }
        List<GlobalPos> kept = new ArrayList<>();
        List<RemoteChargeBlockEntity> ready = new ArrayList<>();
        int outOfRange = 0;
        for (GlobalPos charge : data.charges()) {
            if (!charge.dimension().equals(level.dimension())
                    || charge.pos().distToCenterSqr(player.position()) > range() * range()
                    || !level.isLoaded(charge.pos())) {
                outOfRange++;
                kept.add(charge);
                continue;
            }
            if (level.getBlockEntity(charge.pos()) instanceof RemoteChargeBlockEntity found && found.kind() == trigger.kind
                    && data.id().equals(found.trigger())) {
                ready.add(found);
            }
        }
        ready.sort((a, b) -> Double.compare(a.getBlockPos().distToCenterSqr(player.position()),
                b.getBlockPos().distToCenterSqr(player.position())));
        for (int i = 0; i < ready.size(); i++) {
            ready.get(i).goOffIn(1 + i * BETWEEN);
        }
        stack.set(trigger.component(), data.withCharges(kept));
        int fired = ready.size() + loose;
        Component message = outOfRange > 0
                ? Component.translatable(trigger.key("item.imttech.", "fired_some"), fired, outOfRange, (int) range())
                : Component.translatable(trigger.key("item.imttech.", "fired"), fired);
        player.displayClientMessage(message.copy().withStyle(fired == 0 ? ChatFormatting.GRAY : trigger.colour()), true);
        return new Fired(fired, outOfRange);
    }

    /**
     * Hoofdstuk 69: scherpe ladingen van deze trigger die niet aan een muur hangen, binnen bereik van {@code from}: in de
     * inventaris van een speler (ook aan zijn muis), of los op de grond. Ze gaan meteen af, waar ze zijn. Geeft hoeveel.
     */
    private static int goOffLoose(ServerLevel level, Vec3 from, RemoteChargeBlock.Kind kind, UUID id, UUID owner) {
        double reach = range() * range();
        int count = 0;
        for (ServerPlayer carrier : List.copyOf(level.players())) {
            if (carrier.isSpectator() || carrier.position().distanceToSqr(from) > reach) {
                continue;
            }
            int carried = takeCarried(carrier, kind, id);
            Vec3 at = new Vec3(carrier.getX(), carrier.getY() + carrier.getBbHeight() * 0.45, carrier.getZ());
            for (int i = 0; i < carried; i++) {
                RemoteChargeBlockEntity.goOffLoose(level, at, kind, owner);
            }
            count += carried;
        }
        double r = range();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(from, from).inflate(r), item -> isLiveFor(item.getItem(), kind, id)
                        && item.position().distanceToSqr(from) <= reach)) {
            int n = item.getItem().getCount();
            Vec3 at = item.position().add(0, 0.2, 0);
            item.discard();
            for (int i = 0; i < n; i++) {
                RemoteChargeBlockEntity.goOffLoose(level, at, kind, owner);
            }
            count += n;
        }
        return count;
    }

    /** Haalt de scherpe ladingen van deze trigger uit de inventaris van {@code carrier} (en van zijn muis). Geeft hoeveel. */
    private static int takeCarried(ServerPlayer carrier, RemoteChargeBlock.Kind kind, UUID id) {
        int count = 0;
        net.minecraft.world.entity.player.Inventory inventory = carrier.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isLiveFor(stack, kind, id)) {
                count += stack.getCount();
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        ItemStack carried = carrier.containerMenu.getCarried();
        if (isLiveFor(carried, kind, id)) {
            count += carried.getCount();
            carrier.containerMenu.setCarried(ItemStack.EMPTY);
        }
        if (count > 0) {
            carrier.containerMenu.broadcastChanges();
        }
        return count;
    }

    /** Is dit een scherpe lading van deze soort, aan deze trigger? */
    private static boolean isLiveFor(ItemStack stack, RemoteChargeBlock.Kind kind, UUID id) {
        if (!(stack.getItem() instanceof RemoteChargeItem charge) || charge.kind() != kind) {
            return false;
        }
        LiveCharge live = stack.get(ModDataComponents.LIVE_CHARGE.get());
        return live != null && live.trigger().equals(id);
    }

    /** Sneak + rechtsklik: hoeveel ladingen horen erbij, en hoeveel zijn in bereik. */
    private static void status(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof TriggerItem trigger)) {
            return;
        }
        catchUp(level, stack);
        TriggerData data = data(stack);
        int linked = data == null ? 0 : data.charges().size();
        int inRange = 0;
        if (data != null) {
            for (GlobalPos charge : data.charges()) {
                if (charge.dimension().equals(level.dimension())
                        && charge.pos().distToCenterSqr(player.position()) <= range() * range()) {
                    inRange++;
                }
            }
        }
        player.displayClientMessage(Component.translatable(trigger.key("item.imttech.", "status"), linked, inRange, (int) range())
                .withStyle(trigger.colour()), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        TriggerData data = data(stack);
        tooltip.add(Component.translatable(key("tooltip.imttech.", "linked"), data == null ? 0 : data.charges().size())
                .withStyle(colour()));
        tooltip.add(Component.translatable(key("tooltip.imttech.", "fire"), (int) range()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(key("tooltip.imttech.", "status")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(key("tooltip.imttech.", "link")).withStyle(ChatFormatting.GRAY));
    }
}
