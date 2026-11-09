package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModSounds;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De granaten als item (hoofdstuk 35): de EMP Grenade (een bol die rolt) en de Sticky EMP Grenade (een halve bol die
 * plakt), hun kopieën als explosief (de Frag Grenade en de Sticky Frag Grenade), en de Smoke Grenade en de Flashbang
 * (allebei een bol). Rechtsklik gooit er een; met sneak gooi je hem zachtjes
 * onderhands (een bol rolt dan over de vloer, zoals bij bowlen). Ze stapelen tot 16.
 *
 * Een smokebom kan een kleur hebben (het data component SMOKE_COLOR): je maakt hem met een kleurstof (jouw keuze).
 *
 * <b>Scherp</b> (hoofdstuk 69): raap je een granaat op die al op scherp staat (zie GrenadePickup), dan krijgt het item
 * het component LIVE_FUSE: de speltijd waarop hij afgaat (jouw keuze: "de lont loopt door"). Zo'n granaat glimt, piept
 * in je inventaris en zegt onderaan je scherm hoe lang hij nog heeft. Gooi je hem, dan vliegt hij met de rest van zijn
 * lont; laat je hem vallen (Q), dan ligt hij weer als granaat op de grond; wacht je te lang, dan gaat hij bij jou af. Een
 * scherpe granaat past niet in je granaatpouch, op een Pegboard, in een bundel of in een shulkerdoos (daar zou hij stil
 * blijven staan). In een kist blijft hij wel scherp: wie hem eruit haalt, krijgt hem meteen af.
 */
public class GrenadeItem extends Item {
    public enum Kind { EMP, STICKY_EMP, FRAG, STICKY_FRAG, SMOKE, FLASH }

    /** Zo lang (ticks) moet je wachten tussen twee worpen. */
    private static final int COOLDOWN = 10;

    private final Kind kind;

    public GrenadeItem(Properties properties, Kind kind) {
        super(properties.stacksTo(16));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean toss = player.isShiftKeyDown();
        if (!level.isClientSide) {
            GrenadeEntity grenade = switch (kind) {
                case STICKY_EMP -> new StickyGrenade(level, player, false);
                case STICKY_FRAG -> new StickyGrenade(level, player, true);
                case EMP -> RollingGrenade.thrown(RollingGrenade.Kind.EMP, level, player);
                case FRAG -> RollingGrenade.thrown(RollingGrenade.Kind.FRAG, level, player);
                case SMOKE -> RollingGrenade.thrown(RollingGrenade.Kind.SMOKE, level, player);
                case FLASH -> RollingGrenade.thrown(RollingGrenade.Kind.FLASH, level, player);
            };
            if (grenade instanceof RollingGrenade ball) {
                ball.setSmokeColor(stack.get(ModDataComponents.SMOKE_COLOR.get()));
            }
            // Hoofdstuk 69: een opgeraapte granaat vliegt met de rest van zijn lont (een sticky ook al voor hij plakt).
            int left = ticksLeft(stack, level);
            if (left >= 0) {
                grenade.arm(Math.max(1, left));
            }
            float speed = toss ? ImtConfig.Weapons.TOSS_SPEED : ImtConfig.Weapons.THROW_SPEED;
            // Onderhands: vanaf je heup, schuin naar de vloer voor je (maar nooit omhoog of recht naar beneden).
            float pitch = toss ? Mth.clamp(player.getXRot(), 8, 40) : player.getXRot();
            if (toss) {
                grenade.setPos(player.getX(), player.getY() + 0.8, player.getZ());
            }
            grenade.shootFromRotation(player, pitch, player.getYRot(), 0, speed, 1.0f);
            level.addFreshEntity(grenade);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                    SoundSource.PLAYERS, 0.5f, 0.5f);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        player.awardStat(Stats.ITEM_USED.get(this));
        if (isLive(stack)) {
            // Een opgeraapte granaat is altijd weg als je hem gooit, ook in creative (anders bleef hij in je hand en ging
            // hij daar af).
            stack.shrink(1);
        } else {
            stack.consume(1, player);
        }
        // De laatste van deze soort gegooid? Dan komt de volgende uit je granaatpouch in je hand (hoofdstuk 52).
        if (!level.isClientSide && stack.isEmpty() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.senne.imttech.guns.Gear.refillGrenade(serverPlayer, this, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // ================================================================ Scherp (hoofdstuk 69)

    /** Is dit een opgeraapte granaat die nog op scherp staat? */
    public static boolean isLive(ItemStack stack) {
        return stack.getItem() instanceof GrenadeItem && stack.has(ModDataComponents.LIVE_FUSE.get());
    }

    /** Hoeveel ticks deze scherpe granaat nog heeft (0 = nu), of -1 als hij niet scherp is. */
    public static int ticksLeft(ItemStack stack, Level level) {
        Long at = stack.get(ModDataComponents.LIVE_FUSE.get());
        return at == null ? -1 : (int) Mth.clamp(at - level.getGameTime(), 0, Integer.MAX_VALUE);
    }

    /** Maakt van dit item een scherpe granaat die over {@code ticks} afgaat. */
    public static ItemStack live(ItemStack stack, Level level, int ticks) {
        stack.set(ModDataComponents.LIVE_FUSE.get(), level.getGameTime() + Math.max(1, ticks));
        return stack;
    }

    /** Piept deze soort (zoals de granaat zelf: de EMP- en frag-granaten wel, de smokebom en de flashbang niet)? */
    private boolean beeps() {
        return kind != Kind.SMOKE && kind != Kind.FLASH;
    }

    /**
     * Elke tick in je inventaris: een scherpe granaat piept (steeds sneller, net als op de grond), zegt onderaan je scherm
     * hoe lang hij nog heeft, en gaat bij jou af als zijn tijd op is.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !isLive(stack)) {
            return;
        }
        int left = ticksLeft(stack, level);
        if (left <= 0) {
            goOffAt(server, stack, holder);
            return;
        }
        if (beeps() && left % GrenadeEntity.beepInterval(left) == 0) {
            server.playSound(null, holder.getX(), holder.getEyeY(), holder.getZ(), ModSounds.EMP_GRENADE_BEEP.get(),
                    SoundSource.PLAYERS, 0.7f, left <= 14 ? 1.25f : 1.0f);
        }
        if (holder instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("hud.imttech.live_grenade", stack.getHoverName(), seconds(left))
                    .withStyle(left <= 14 ? ChatFormatting.RED : ChatFormatting.GOLD), true);
        }
    }

    /** Zijn tijd is op terwijl iemand hem draagt: hij valt uit je hand en gaat meteen af, bij jou. */
    public static void goOffAt(ServerLevel level, ItemStack stack, Entity holder) {
        ItemStack one = stack.copyWithCount(1);
        stack.setCount(0);
        Vec3 at = new Vec3(holder.getX(), holder.getY() + holder.getBbHeight() * 0.45, holder.getZ());
        release(level, one, holder, at, Vec3.ZERO, 1);
    }

    /**
     * Een scherpe granaat (weer) als granaat in de wereld: op {@code at}, met deze vaart, en hij gaat over {@code fuse}
     * ticks af. Voor als je hem laat vallen of als zijn tijd in je hand op is.
     */
    public static @Nullable GrenadeEntity release(ServerLevel level, ItemStack stack, @Nullable Entity owner, Vec3 at,
                                                  Vec3 velocity, int fuse) {
        if (!(stack.getItem() instanceof GrenadeItem item)) {
            return null;
        }
        GrenadeEntity grenade = switch (item.kind) {
            case STICKY_EMP -> new StickyGrenade(ModEntities.STICKY_EMP_GRENADE.get(), level);
            case STICKY_FRAG -> new StickyGrenade(ModEntities.STICKY_FRAG_GRENADE.get(), level);
            case EMP -> new RollingGrenade(RollingGrenade.type(RollingGrenade.Kind.EMP), level);
            case FRAG -> new RollingGrenade(RollingGrenade.type(RollingGrenade.Kind.FRAG), level);
            case SMOKE -> new RollingGrenade(RollingGrenade.type(RollingGrenade.Kind.SMOKE), level);
            case FLASH -> new RollingGrenade(RollingGrenade.type(RollingGrenade.Kind.FLASH), level);
        };
        if (grenade instanceof RollingGrenade ball) {
            ball.setSmokeColor(stack.get(ModDataComponents.SMOKE_COLOR.get()));
        }
        grenade.setOwner(owner);
        grenade.setPos(at.x, at.y, at.z);
        grenade.setDeltaMovement(velocity);
        grenade.arm(Math.max(1, fuse));
        level.addFreshEntity(grenade);
        return grenade;
    }

    /**
     * Op de grond gevallen (Q, of je ging dood): een scherpe granaat is meteen weer een granaat die rolt of plakt, met de
     * rest van zijn lont. (Als item zou hij daar nooit afgaan.)
     */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!isLive(stack)) {
            return false;
        }
        if (entity.level() instanceof ServerLevel level) {
            int left = ticksLeft(stack, level);
            release(level, stack, entity.getOwner(), entity.position(), entity.getDeltaMovement(), Math.max(1, left));
            entity.discard();
        }
        return true;
    }

    /** Een scherpe granaat glimt (zo zie je in je hotbar welke de opgeraapte is). */
    @Override
    public boolean isFoil(ItemStack stack) {
        return isLive(stack) || super.isFoil(stack);
    }

    /** Niet in een bundel of shulkerdoos: daar zou de lont stilstaan. */
    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return !isLive(stack);
    }

    /** "1.4" (seconden, een cijfer achter de komma). */
    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20f);
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = baseName(stack);
        return isLive(stack) ? Component.translatable("item.imttech.live", name) : name;
    }

    private Component baseName(ItemStack stack) {
        DyeColor color = stack.get(ModDataComponents.SMOKE_COLOR.get());
        if (kind == Kind.SMOKE && color != null) {
            return Component.translatable("item.imttech.smoke_grenade.colored",
                    Component.translatable("color.minecraft." + color.getName()));
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (isLive(stack)) {
            Level level = context.level();
            tooltip.add((level == null ? Component.translatable("tooltip.imttech.live_grenade.short")
                    : Component.translatable("tooltip.imttech.live_grenade", seconds(ticksLeft(stack, level))))
                    .withStyle(ChatFormatting.RED));
        }
        switch (kind) {
            case EMP, STICKY_EMP -> {
                tooltip.add(Component.translatable(kind == Kind.STICKY_EMP ? "tooltip.imttech.sticky_emp_grenade"
                        : "tooltip.imttech.emp_grenade").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tooltip.imttech.emp_grenade.what", (int) ImtConfig.Weapons.EMP_RADIUS,
                        Math.round(ImtConfig.Weapons.EMP_SECONDS)).withStyle(ChatFormatting.DARK_AQUA));
            }
            case FRAG, STICKY_FRAG -> {
                tooltip.add(Component.translatable(kind == Kind.STICKY_FRAG ? "tooltip.imttech.sticky_emp_grenade"
                        : "tooltip.imttech.emp_grenade").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tooltip.imttech.frag_grenade.what").withStyle(ChatFormatting.RED));
            }
            case SMOKE -> {
                tooltip.add(Component.translatable("tooltip.imttech.smoke_grenade").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tooltip.imttech.smoke_grenade.what").withStyle(ChatFormatting.DARK_AQUA));
                if (stack.get(ModDataComponents.SMOKE_COLOR.get()) == null) {
                    tooltip.add(Component.translatable("tooltip.imttech.smoke_grenade.dye").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            case FLASH -> {
                tooltip.add(Component.translatable("tooltip.imttech.flashbang").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tooltip.imttech.flashbang.what").withStyle(ChatFormatting.DARK_AQUA));
            }
        }
        if (kind != Kind.STICKY_EMP && kind != Kind.STICKY_FRAG) {
            tooltip.add(Component.translatable("tooltip.imttech.emp_grenade.toss").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!isLive(stack)) {
            tooltip.add(Component.translatable("tooltip.imttech.grenade.catch").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
