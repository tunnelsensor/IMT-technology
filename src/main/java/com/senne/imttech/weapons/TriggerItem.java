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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
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
        TriggerData data = data(stack);
        if (data == null || data.charges().isEmpty()) {
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
        Component message = outOfRange > 0
                ? Component.translatable(trigger.key("item.imttech.", "fired_some"), ready.size(), outOfRange, (int) range())
                : Component.translatable(trigger.key("item.imttech.", "fired"), ready.size());
        player.displayClientMessage(message.copy().withStyle(ready.isEmpty() ? ChatFormatting.GRAY : trigger.colour()), true);
        return new Fired(ready.size(), outOfRange);
    }

    /** Sneak + rechtsklik: hoeveel ladingen horen erbij, en hoeveel zijn in bereik. */
    private static void status(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof TriggerItem trigger)) {
            return;
        }
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
