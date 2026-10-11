package com.senne.starktech.workbench;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.suit.RocketPackItem;
import com.senne.starktech.suit.StarkSuitItem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De "binnenkant" van de harnas-werkbank: het hologram van het harnas dat erboven zweeft.
 *
 * Er is geen scherm meer. Je kijkt naar een stuk van het hologram (borstplaat, broek of laarzen,
 * zie {@link HoloPiece}) en klikt met rechts. Wat er dan gebeurt, hangt af van wat je vasthoudt:
 * - NIETS (of iets anders): is het stuk nog een hologram, dan BOUWT de werkbank het. De
 *   materialen komen uit je rugzak (zie {@link Blueprint}), het stuk bouwt zich op in het
 *   hologram, en als het klaar is vliegt het naar je toe. Heb je dat stuk nog niet aan, dan
 *   klikt het meteen om je heen, net als in de films. Ligt er al een echt stuk op het
 *   hologram, dan PAK je dat (het vliegt ook naar je toe).
 * - EEN STUK HARNAS: dat LEG je op het hologram, op zijn eigen plek (ligt daar al een stuk,
 *   dan wisselen ze).
 * - EEN UPGRADE (of het raketpakket): die BOUW je IN het stuk waar je op klikt. Er moet dan wel
 *   een echt stuk liggen, en de upgrade moet er (nog) in passen.
 *
 * Bouwen en inbouwen kosten tijd en stroom van het net (een Stark-kabel naar een zonnepaneel of
 * warmtegenerator, of er direct tegenaan). Is er even geen stroom, dan wacht de werkbank.
 *
 * Alles wat de spelers in de buurt moeten zien (de gelegde stukken, de voortgang, het vliegende
 * stuk) gaat met {@link #sync()} naar hun computer; daar tekent SuitHologramRenderer het.
 */
public class SuitWorkbenchBlockEntity extends BlockEntity {
    /** Wat hij aan het doen is. */
    public static final int JOB_NONE = 0;
    public static final int JOB_BUILD = 1;
    public static final int JOB_INSTALL = 2;

    /** Hoe lang een klaar (of gepakt) stuk onderweg is naar je toe (ticks). */
    public static final int FLY_TICKS = 16;
    /** Verder weg dan dit? Dan blijft het stuk gewoon op het hologram liggen. */
    private static final double FLY_RANGE = 24.0;

    /** De echte stukken die op het hologram liggen (borst, broek, laarzen). */
    private final ItemStack[] laid = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private int job = JOB_NONE;
    private HoloPiece jobPiece = HoloPiece.CHEST;
    /** De upgrade die er nu in gaat. */
    private ItemStack module = ItemStack.EMPTY;
    /** De materialen van het stuk dat nu gebouwd wordt (terug als de werkbank kapot gaat). */
    private final List<ItemStack> spent = new ArrayList<>();
    /** Wie het stuk bestelde: daar vliegt het naartoe. */
    private @Nullable UUID orderedBy;
    private int progress;
    private boolean powered;

    /** Het stuk dat nu door de lucht naar een speler vliegt. */
    private ItemStack flying = ItemStack.EMPTY;
    private HoloPiece flyFrom = HoloPiece.CHEST;
    private @Nullable UUID flyTo;
    private long flyStart;

    /** Spullen uit een oude werkbank (met vakjes) die nog uitgedeeld moeten worden. */
    private final List<ItemStack> legacy = new ArrayList<>();

    public SuitWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUIT_WORKBENCH.get(), pos, state);
    }

    // ================================================================ Voor de tekenaar en de tests

    public ItemStack laid(HoloPiece piece) {
        return laid[piece.ordinal()];
    }

    public int job() {
        return job;
    }

    public HoloPiece jobPiece() {
        return jobPiece;
    }

    public ItemStack module() {
        return module;
    }

    public int progress() {
        return progress;
    }

    public int total() {
        return job == JOB_INSTALL ? StarkConfig.Workbench.INSTALL_TICKS : job == JOB_BUILD ? StarkConfig.Workbench.BUILD_TICKS : 0;
    }

    public boolean powered() {
        return powered;
    }

    public ItemStack flying() {
        return flying;
    }

    public HoloPiece flyFrom() {
        return flyFrom;
    }

    public @Nullable UUID flyTo() {
        return flyTo;
    }

    public long flyStart() {
        return flyStart;
    }

    /** Past deze upgrade (nog) in dit stuk? */
    public static boolean fits(ItemStack upgrade, ItemStack piece) {
        if (upgrade.getItem() instanceof SuitModuleItem suitModule) {
            return suitModule.canApplyTo(piece);
        }
        return upgrade.getItem() instanceof RocketPackItem && piece.getItem() instanceof StarkSuitItem suit
                && suit.getType() == ArmorItem.Type.CHESTPLATE && !RocketPackItem.installedOn(piece);
    }

    /** Is dit een upgrade die je aan de werkbank inbouwt? */
    public static boolean isUpgrade(ItemStack stack) {
        return stack.getItem() instanceof SuitModuleItem || stack.getItem() instanceof RocketPackItem;
    }

    // ================================================================ Klikken op het hologram

    /**
     * Een speler klikt met rechts op een stuk van het hologram. Zie de uitleg bovenaan wat er
     * gebeurt. Geeft true als er iets gebeurde; anders krijgt de speler te horen waarom niet.
     */
    public boolean interact(ServerPlayer player, HoloPiece piece) {
        if (!flying.isEmpty()) {
            return refuse(player, "workbench.starktech.busy");
        }
        ItemStack hand = player.getMainHandItem();
        if (hand.getItem() instanceof StarkSuitItem suit) {
            return lay(player, suit, hand);
        }
        if (isUpgrade(hand)) {
            return startInstall(player, piece, hand);
        }
        if (!laid(piece).isEmpty()) {
            if (job != JOB_NONE && jobPiece == piece) {
                return refuse(player, "workbench.starktech.busy");
            }
            ItemStack taken = laid(piece);
            laid[piece.ordinal()] = ItemStack.EMPTY;
            launch(taken, piece, player);
            return true;
        }
        if (job != JOB_NONE) {
            return refuse(player, "workbench.starktech.busy");
        }
        return startBuild(player, piece);
    }

    /** Een stuk harnas uit je hand op het hologram leggen (en wat er lag, komt in je hand). */
    private boolean lay(ServerPlayer player, StarkSuitItem suit, ItemStack hand) {
        HoloPiece target = HoloPiece.of(suit.getType());
        if (target == null) {
            return false;
        }
        if (job != JOB_NONE && jobPiece == target) {
            return refuse(player, "workbench.starktech.busy");
        }
        ItemStack old = laid(target);
        laid[target.ordinal()] = hand.copyWithCount(1);
        hand.shrink(1);
        if (!old.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, old);
        }
        play(ModSounds.EDITH_SELECT.get(), 0.8f, 1.2f);
        play(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.7f, 1.0f);
        sync();
        return true;
    }

    /** Een upgrade uit je hand inbouwen in het stuk dat op het hologram ligt. */
    private boolean startInstall(ServerPlayer player, HoloPiece piece, ItemStack hand) {
        if (job != JOB_NONE) {
            return refuse(player, "workbench.starktech.busy");
        }
        ItemStack onIt = laid(piece);
        if (onIt.isEmpty()) {
            return refuse(player, Component.translatable("workbench.starktech.need_piece",
                    Component.translatable(piece.langKey())));
        }
        if (!fits(hand, onIt)) {
            return refuse(player, Component.translatable("workbench.starktech.no_fit", hand.getHoverName(),
                    Component.translatable(piece.langKey())));
        }
        module = hand.split(1);
        job = JOB_INSTALL;
        jobPiece = piece;
        progress = 0;
        play(ModSounds.EDITH_CONFIRM.get(), 0.8f, 1.0f);
        sync();
        return true;
    }

    /** Een stuk bouwen met de materialen uit je rugzak (in creative gratis). */
    public boolean startBuild(Player player, HoloPiece piece) {
        if (job != JOB_NONE || !laid(piece).isEmpty() || !flying.isEmpty()) {
            return false;
        }
        Blueprint blueprint = piece.blueprint();
        spent.clear();
        if (!player.getAbilities().instabuild) {
            if (!blueprint.affordable(player.getInventory())) {
                if (player instanceof ServerPlayer serverPlayer) {
                    refuse(serverPlayer, Component.translatable("workbench.starktech.missing", missing(player, blueprint)));
                }
                return false;
            }
            spent.addAll(blueprint.take(player.getInventory()));
        }
        job = JOB_BUILD;
        jobPiece = piece;
        orderedBy = player.getUUID();
        progress = 0;
        play(ModSounds.EDITH_CONFIRM.get(), 0.8f, 1.0f);
        sync();
        return true;
    }

    /** "2x Netherite Ingot, 1x Stark Chip": wat je nog tekort komt. */
    private static Component missing(Player player, Blueprint blueprint) {
        net.minecraft.network.chat.MutableComponent list = Component.empty();
        boolean first = true;
        for (Blueprint.Cost cost : blueprint.costs()) {
            int short_ = cost.count() - Blueprint.count(player.getInventory(), cost);
            if (short_ <= 0) {
                continue;
            }
            if (!first) {
                list.append(", ");
            }
            first = false;
            ItemStack[] examples = cost.ingredient().getItems();
            Component name = examples.length > 0 ? examples[0].getHoverName() : Component.literal("?");
            list.append(short_ + "x ").append(name);
        }
        return list;
    }

    private boolean refuse(ServerPlayer player, String key) {
        return refuse(player, Component.translatable(key));
    }

    private boolean refuse(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
        play(ModSounds.EDITH_DENIED.get(), 0.6f, 1.0f);
        return false;
    }

    // ================================================================ Elke tick

    public void serverTick(ServerLevel level, BlockPos pos) {
        if (!legacy.isEmpty()) {
            for (ItemStack stack : legacy) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, stack);
            }
            legacy.clear();
            sync();
        }
        if (!flying.isEmpty() && level.getGameTime() - flyStart >= FLY_TICKS) {
            land(level);
        }
        if (job == JOB_INSTALL && !fits(module, laid(jobPiece))) {
            // Kan eigenlijk niet (het stuk zit vast tijdens het werk), maar voor de zekerheid.
            giveBack(level, module);
            module = ItemStack.EMPTY;
            job = JOB_NONE;
            sync();
        }
        if (job == JOB_NONE) {
            setPowered(StarkGrid.available(level, pos) > 0);
            return;
        }
        float cost = job == JOB_INSTALL ? StarkConfig.Power.WORKBENCH_INSTALL : StarkConfig.Power.WORKBENCH_BUILD;
        setPowered(StarkGrid.pay(level, pos, cost));
        if (!powered) {
            return; // wachten op stroom
        }
        progress++;
        if (level.getGameTime() % 10 == 0) {
            play(SoundEvents.SMITHING_TABLE_USE, 0.25f, 1.6f);
        }
        if (progress >= total()) {
            finish(level);
        } else if (progress % 4 == 0) {
            sync(); // de voortgang voor het hologram
        } else {
            setChanged();
        }
    }

    private void setPowered(boolean now) {
        if (now != powered) {
            powered = now;
            sync();
        }
    }

    private void finish(ServerLevel level) {
        if (job == JOB_INSTALL) {
            ItemStack piece = laid(jobPiece);
            if (module.getItem() instanceof SuitModuleItem suitModule) {
                suitModule.applyTo(piece);
            } else if (module.getItem() instanceof RocketPackItem) {
                piece.set(ModDataComponents.ROCKET_PACK.get(), true);
            }
            module = ItemStack.EMPTY;
        } else {
            ItemStack built = new ItemStack(jobPiece.blueprint().result());
            spent.clear();
            Player player = orderedBy == null ? null : level.getPlayerByUUID(orderedBy);
            if (inRange(player)) {
                launch(built, jobPiece, player);
            } else {
                layOrDrop(level, built, jobPiece);
            }
            orderedBy = null;
        }
        play(ModSounds.STATION_UPGRADE.get(), 0.7f, 1.2f);
        job = JOB_NONE;
        progress = 0;
        sync();
    }

    // ================================================================ Het vliegende stuk

    /** Het stuk vliegt vanaf zijn plek op het hologram naar de speler. */
    private void launch(ItemStack stack, HoloPiece from, Player player) {
        flying = stack;
        flyFrom = from;
        flyTo = player.getUUID();
        flyStart = level == null ? 0 : level.getGameTime();
        play(SoundEvents.ARMOR_EQUIP_ELYTRA.value(), 0.8f, 1.3f);
        sync();
    }

    /** Aangekomen: aantrekken als die plek nog vrij is, anders in je rugzak. */
    private void land(ServerLevel level) {
        ItemStack stack = flying;
        HoloPiece piece = flyFrom;
        Player player = flyTo == null ? null : level.getPlayerByUUID(flyTo);
        flying = ItemStack.EMPTY;
        flyTo = null;
        if (!inRange(player)) {
            layOrDrop(level, stack, piece);
        } else if (player.getItemBySlot(piece.slot()).isEmpty()) {
            player.setItemSlot(piece.slot(), stack);
            level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(),
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        } else if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        sync();
    }

    private boolean inRange(@Nullable Player player) {
        return player != null && player.isAlive() && player.level() == level
                && player.distanceToSqr(worldPosition.getCenter()) < FLY_RANGE * FLY_RANGE;
    }

    private void layOrDrop(ServerLevel level, ItemStack stack, HoloPiece piece) {
        if (laid(piece).isEmpty()) {
            laid[piece.ordinal()] = stack;
        } else {
            giveBack(level, stack);
        }
    }

    private void giveBack(Level level, ItemStack stack) {
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2,
                    worldPosition.getZ() + 0.5, stack);
        }
    }

    // ================================================================ Afbreken

    /** De werkbank gaat kapot: alles wat erop of erin zat, valt eruit. */
    public void dropAll(Level level) {
        for (int i = 0; i < laid.length; i++) {
            giveBack(level, laid[i]);
            laid[i] = ItemStack.EMPTY;
        }
        giveBack(level, module);
        giveBack(level, flying);
        for (ItemStack stack : spent) {
            giveBack(level, stack);
        }
        for (ItemStack stack : legacy) {
            giveBack(level, stack);
        }
        module = ItemStack.EMPTY;
        flying = ItemStack.EMPTY;
        spent.clear();
        legacy.clear();
    }

    // ================================================================ Opslaan en doorsturen

    private void play(SoundEvent sound, float volume, float pitch) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    /** Opslaan en de spelers in de buurt laten weten dat het hologram veranderd is. */
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag, registries);
        ListTag spentList = new ListTag();
        for (ItemStack stack : spent) {
            spentList.add(stack.save(registries));
        }
        tag.put("Spent", spentList);
        if (orderedBy != null) {
            tag.putUUID("OrderedBy", orderedBy);
        }
    }

    /** Wat zowel in de save als naar de spelers gaat. */
    private void write(CompoundTag tag, HolderLookup.Provider registries) {
        for (HoloPiece piece : HoloPiece.values()) {
            if (!laid(piece).isEmpty()) {
                tag.put("Laid_" + piece.name(), laid(piece).save(registries));
            }
        }
        tag.putInt("Job", job);
        tag.putInt("JobPiece", jobPiece.ordinal());
        tag.putInt("Progress", progress);
        tag.putBoolean("Powered", powered);
        if (!module.isEmpty()) {
            tag.put("Module", module.save(registries));
        }
        if (!flying.isEmpty() && flyTo != null) {
            tag.put("Flying", flying.save(registries));
            tag.putInt("FlyFrom", flyFrom.ordinal());
            tag.putUUID("FlyTo", flyTo);
            tag.putLong("FlyStart", flyStart);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (HoloPiece piece : HoloPiece.values()) {
            laid[piece.ordinal()] = read(tag, "Laid_" + piece.name(), registries);
        }
        job = tag.getInt("Job");
        HoloPiece piece = HoloPiece.byId(tag.getInt("JobPiece"));
        jobPiece = piece == null ? HoloPiece.CHEST : piece;
        progress = tag.getInt("Progress");
        powered = tag.getBoolean("Powered");
        module = read(tag, "Module", registries);
        flying = read(tag, "Flying", registries);
        HoloPiece from = HoloPiece.byId(tag.getInt("FlyFrom"));
        flyFrom = from == null ? HoloPiece.CHEST : from;
        flyTo = tag.hasUUID("FlyTo") ? tag.getUUID("FlyTo") : null;
        flyStart = tag.getLong("FlyStart");
        orderedBy = tag.hasUUID("OrderedBy") ? tag.getUUID("OrderedBy") : null;
        spent.clear();
        for (Tag entry : tag.getList("Spent", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, entry).ifPresent(spent::add);
        }
        if (tag.contains("Items")) {
            loadOldBench(tag, registries);
        }
    }

    /**
     * Een werkbank uit een oudere versie had vakjes (9 materialen, een werkvak en een
     * upgradevak). Een stuk harnas uit het werkvak legt hij op het hologram; de rest valt er bij
     * de eerste tick uit, zodat niemand iets kwijtraakt.
     */
    private void loadOldBench(CompoundTag tag, HolderLookup.Provider registries) {
        NonNullList<ItemStack> old = NonNullList.withSize(11, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, old, registries);
        legacy.clear();
        for (ItemStack stack : old) {
            if (stack.isEmpty()) {
                continue;
            }
            HoloPiece piece = stack.getItem() instanceof StarkSuitItem suit ? HoloPiece.of(suit.getType()) : null;
            if (piece != null && laid(piece).isEmpty()) {
                laid[piece.ordinal()] = stack;
            } else {
                legacy.add(stack);
            }
        }
        // De oude werkbank had andere nummers voor zijn werk: 1-3 was een stuk bouwen (de
        // materialen zijn dan al op, dus dat maken we af), 10 was inbouwen (de upgrade valt eruit).
        int oldJob = tag.getInt("Job");
        if (oldJob >= 1 && oldJob <= 3) {
            job = JOB_BUILD;
            jobPiece = HoloPiece.values()[oldJob - 1];
        } else {
            job = JOB_NONE;
            progress = 0;
        }
    }

    private static ItemStack read(CompoundTag tag, String key, HolderLookup.Provider registries) {
        return tag.contains(key) ? ItemStack.parse(registries, tag.getCompound(key)).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        write(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
