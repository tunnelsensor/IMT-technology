package com.senne.imttech.car;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.security.zone.ZoneData;
import com.senne.imttech.security.zone.Zones;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Wat een RC Car in de wereld doet (hoofdstuk 108): een blok breken, een blok plaatsen, oprapen wat er ligt, en zijn
 * kofferbak lossen. {@link CarMove} (zijn lijf) en {@link CarBrain} (zijn programma) roepen dit aan.
 *
 * <b>Breken.</b> Zijn boor is een pickaxe, schep, bijl en schoffel in één, en zijn boorkop ({@link DrillHead}) bepaalt
 * hoe goed: de ijzeren en de stalen kop breken wat ijzeren gereedschap breekt, titanium en netherite ook wat een
 * diamanten pickaxe vraagt (obsidiaan). Wat niemand kan breken (bedrock), laat hij staan. Met de netherite kop boort hij
 * zo snel als jij met ijzeren gereedschap (config {@code mineSpeed}); de andere koppen zijn trager (jouw wens: "een
 * basic drill head duurt wel even"). Wat eruit valt is wat jij zou krijgen, en gaat in zijn kofferbak; is die vol, dan
 * blijft het op de grond liggen.
 *
 * <b>Een beschermde zone</b> (hoofdstuk 10) geldt ook voor hem: hij breekt en plaatst daar alleen als wie zijn klus
 * startte dat zelf zou mogen (dezelfde regel als voor een speler: ZoneData.Zone.isBuilder; zie
 * {@link RcCarEntity#actorId}). Zo is een RC Car geen manier om bij iemand in te breken, ook niet de auto van iemand die
 * daar wel mag bouwen. Uit zo'n zone raapt hij ook niets op. En de spawnbescherming van een server geldt voor hem zoals
 * voor de speler die hem bestuurt.
 *
 * Alles kost stroom uit zijn energiecel (zie de getallen in [rcCar]).
 *
 * Hij breekt zonder het event van "een speler breekt een blok" (er is geen speler). Wat de mod zelf aan dat event
 * hangt, doet {@link #breakBlock} daarom rechtstreeks: de zone (hierboven), gebarsten blokken, de Biebia's Industries
 * Tower die het merkt, en het lawaai dat bewakers horen.
 */
public final class CarWork {
    private CarWork() {}

    /** Hoe iets afliep. */
    public enum Result {
        /** Gelukt (of: dit kan). */
        OK,
        /** Er is hier niets te doen: lucht of water waar hij wilde breken. */
        NOTHING,
        /** Het kan of mag niet (te hard, beschermd, geen blok in de kofferbak, geen plek). */
        REFUSED,
        /** Zijn energiecel is leeg (of hij heeft er geen). */
        NO_POWER
    }

    /** Hoeveel stroom het breken van dit blok kost: naar zijn hardheid, minstens een derde van een punt. */
    private static double mineCost(float hardness) {
        return ImtConfig.RcCar.MINE_COST * Math.max(1.0 / 3.0, hardness);
    }

    /**
     * Waarom de auto dit blok niet mag veranderen (Engels, voor achter "it may not break blocks "), of null als het mag.
     * Hij doet het namens wie zijn klus startte: buiten de wereld mag niemand iets, in de spawnbescherming alleen een
     * operator, en in een beschermde zone alleen wie daar mag bouwen.
     */
    private static @Nullable String whyNot(ServerLevel level, RcCarEntity car, BlockPos pos, BlockState free) {
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return "outside the world";
        }
        if (spawnProtected(level, car, pos)) {
            return "at the spawn of this server (spawn protection)";
        }
        if (Zones.isFree(free)) {
            return null; // ladders, fakkels...: die mag iedereen in een zone plaatsen en weghalen
        }
        return builderHere(level, car, pos) ? null : "in this protected zone";
    }

    /** Ligt dit blok buiten elke beschermde zone, of mag wie de auto bestuurt in die zone bouwen? */
    private static boolean builderHere(ServerLevel level, RcCarEntity car, BlockPos pos) {
        ZoneData.Zone zone = Zones.activeAt(level, pos);
        return zone == null || zone.isBuilder(level, car.actorId(), car.actorName());
    }

    /**
     * Ligt dit blok in de spawnbescherming van de server (spawn-protection in server.properties), en is wie de auto
     * bestuurt geen operator? Dan breekt en plaatst een speler daar niets, en zijn auto dus ook niet. Alleen een echte
     * server heeft die bescherming. Is de speler nu niet op de server, dan vragen we het voor een "nepspeler" met zijn
     * nummer en naam (Minecraft wil hier een speler zien).
     */
    private static boolean spawnProtected(ServerLevel level, RcCarEntity car, BlockPos pos) {
        net.minecraft.server.MinecraftServer server = level.getServer();
        UUID id = car.actorId();
        if (id == null || !server.isDedicatedServer() || server.getSpawnProtectionRadius() <= 0) {
            return false;
        }
        Player who = server.getPlayerList().getPlayer(id);
        if (who == null) {
            String name = car.actorName();
            who = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(id, name.isEmpty() ? "RC Car" : name));
        }
        return server.isUnderSpawnProtection(level, pos, who);
    }

    // ================================================================ Breken

    /**
     * Kan de auto dit blok nu breken? NOTHING voor lucht en water (dat slaat hij over), REFUSED met een melding voor
     * wat hij niet kan of mag, NO_POWER als zijn cel te leeg is.
     */
    public static Result canMine(ServerLevel level, RcCarEntity car, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
            return Result.NOTHING;
        }
        if (!ImtConfig.RcCar.MAY_CHANGE_BLOCKS) {
            car.brain().report("breaking blocks is switched off on this server");
            return Result.REFUSED;
        }
        float hardness = state.getDestroySpeed(level, pos);
        String name = state.getBlock().getName().getString();
        if (hardness < 0) {
            car.brain().report("it cannot break " + name);
            return Result.REFUSED;
        }
        DrillHead head = car.drill();
        if (!head.canDrill(state)) {
            // Welke kop kan het wel? Dan weet je wat je moet maken.
            DrillHead needed = DrillHead.weakestFor(state);
            car.brain().report(name + " is too hard for its " + head.displayName()
                    + (needed == null ? "" : ": it needs a " + needed.label() + " one"));
            return Result.REFUSED;
        }
        String why = whyNot(level, car, pos, state);
        if (why != null) {
            car.brain().report("it may not break blocks " + why);
            return Result.REFUSED;
        }
        return car.hasEnergy(mineCost(hardness)) ? Result.OK : Result.NO_POWER;
    }

    /**
     * Hoeveel van dit blok hij per tick wegboort (1 = helemaal): dezelfde som als bij een speler met het beste ijzeren
     * gereedschap voor dit blok, keer {@code mineSpeed}, keer de snelheid van zijn boorkop. Met de netherite kop (1) duurt
     * steen zo 8 ticks, aarde 3 en deepslate 15; met de ijzeren kop (een kwart) vier keer zo lang.
     */
    public static float progressPerTick(ServerLevel level, RcCarEntity car, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0) {
            return 1.0f; // een fakkel, gras: meteen
        }
        return DrillHead.toolSpeed(state) * ImtConfig.RcCar.MINE_SPEED * car.drill().speed() / hardness / 30.0f;
    }

    /** Het tikken van de boor op het blok (het geluid van dat blok, zoals wanneer jij erop hakt). */
    public static void drillSound(ServerLevel level, RcCarEntity car, BlockPos pos, BlockState state) {
        SoundType sound = state.getSoundType(level, pos, car);
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0f) / 8.0f, sound.getPitch() * 0.5f);
    }

    /**
     * Breekt het blok: het is weg (met het geluid en de stukjes van breken), wat eruit valt gaat in de kofferbak (de rest
     * blijft liggen), en de cel betaalt.
     */
    public static void breakBlock(ServerLevel level, RcCarEntity car, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(level, pos);
        ItemStack tool = car.drill().toolFor(state);
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        // Twee regels van de mod gelden ook voor een auto: een gebarsten blok geeft een gebarsten blok (hoofdstuk 11), en
        // slopen in een Biebia's Industries Tower valt op: dat komt op rekening van wie de klus startte (als die nu op de
        // server is; in welke wereld hij zelf staat, maakt niet uit: we kijken in de wereld van de auto).
        com.senne.imttech.repair.CrackDrops.breaking(level, pos, state);
        net.minecraft.server.level.ServerPlayer actor = car.actorPlayer();
        if (actor != null) {
            com.senne.imttech.raid.ImtRaids.towerBlockBroken(actor, level, pos);
        }
        level.destroyBlock(pos, false, car);
        // De gewone weg van Minecraft: de buit (en de ervaring van erts) valt op de plek van het blok...
        Block.dropResources(state, level, pos, blockEntity, car, tool);
        // ...en de auto raapt meteen op wat daar ligt.
        collect(level, car, new AABB(pos).inflate(0.75));
        car.spend(mineCost(hardness));
        // Bewakers en robots horen het boren, zoals ze jou een blok horen kapotslaan (hoofdstuk 60).
        com.senne.imttech.raven.Noises.make(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), ImtConfig.Guards.NOISE_BLOCK,
                com.senne.imttech.raven.Noises.Kind.BLOCK, car);
    }

    /**
     * Zit de auto vast in een blok (grind of zand dat op hem viel)? Dan breekt hij dat blok meteen weg, als hij het kan
     * en mag. Geeft true als hij dat deed.
     */
    public static boolean freeItself(ServerLevel level, RcCarEntity car) {
        BlockPos own = car.body().cell();
        BlockState state = level.getBlockState(own);
        if (state.isAir() || state.getCollisionShape(level, own).isEmpty()
                || level.noCollision(car, car.getBoundingBox().deflate(0.05))) {
            return false;
        }
        if (canMine(level, car, own, state) == Result.OK) {
            breakBlock(level, car, own, state);
            return true;
        }
        return false;
    }

    // ================================================================ Oprapen

    /** {@code collect()}: alles wat er binnen {@code collectRange} blokken rond de auto ligt, gaat in zijn kofferbak. */
    public static void collect(ServerLevel level, RcCarEntity car) {
        double range = ImtConfig.RcCar.COLLECT_RANGE;
        collect(level, car, car.getBoundingBox().inflate(range, Math.max(1.0, range / 2), range));
    }

    /**
     * Wat er in dit stuk van de wereld op de grond ligt, gaat in de kofferbak (wat er niet meer bij kan, blijft liggen).
     * Wat in een beschermde zone ligt waar je niet mag bouwen, laat hij liggen: anders raapte een auto die buiten tegen de
     * muur staat op wat er binnen op de grond ligt.
     */
    private static void collect(ServerLevel level, RcCarEntity car, AABB area) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, ItemEntity::isAlive)) {
            ItemStack stack = item.getItem();
            if (!RcCarEntity.fitsInTrunk(stack) || !builderHere(level, car, item.blockPosition())) {
                continue;
            }
            ItemStack rest = car.trunk().addItem(stack.copy());
            if (rest.isEmpty()) {
                item.discard();
            } else if (rest.getCount() != stack.getCount()) {
                item.setItem(rest);
            }
        }
    }

    // ================================================================ Lossen

    /**
     * {@code unload()}: de kofferbak leeg in de kist (of een ander blok met vakjes) vóór de auto. Staat daar geen kist,
     * dan legt hij alles voor zich op de grond. Geeft OK als de kofferbak daarna leeg is, REFUSED als er iets achterbleef
     * (de kist is vol).
     */
    public static Result unload(ServerLevel level, RcCarEntity car, Direction facing) {
        SimpleContainer trunk = car.trunk();
        BlockPos front = car.body().cell().relative(facing);
        IItemHandler chest = level.getCapability(Capabilities.ItemHandler.BLOCK, front, facing.getOpposite());
        boolean left = false;
        for (int slot = 0; slot < trunk.getContainerSize(); slot++) {
            ItemStack stack = trunk.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (chest != null) {
                ItemStack rest = ItemHandlerHelper.insertItemStacked(chest, stack.copy(), false);
                trunk.setItem(slot, rest);
                left |= !rest.isEmpty();
            } else {
                // Geen kist: voor hem op de grond (in zijn eigen blok als er een muur voor hem staat).
                boolean open = level.getBlockState(front).getCollisionShape(level, front).isEmpty();
                double x = car.getX() + (open ? facing.getStepX() * 0.9 : 0);
                double z = car.getZ() + (open ? facing.getStepZ() * 0.9 : 0);
                ItemEntity item = new ItemEntity(level, x, car.getY() + 0.25, z, stack.copy(), 0, 0, 0);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
                trunk.setItem(slot, ItemStack.EMPTY);
            }
        }
        return left ? Result.REFUSED : Result.OK;
    }

    // ================================================================ Plaatsen

    /**
     * Zet een blok uit de kofferbak neer: {@code way} 0 = vóór de auto, 1 = boven hem, -1 = onder hem (dan komt hij zelf
     * een blok hoger te staan, zoals jij een pilaar bouwt). {@code kind} is het soort blok ("cobblestone", "oak planks",
     * "minecraft:glass"), of leeg voor het eerste blok dat in zijn kofferbak ligt.
     */
    public static Result place(ServerLevel level, RcCarEntity car, int way, Direction direction, String kind) {
        if (!ImtConfig.RcCar.MAY_CHANGE_BLOCKS) {
            car.brain().report("placing blocks is switched off on this server");
            return Result.REFUSED;
        }
        SimpleContainer trunk = car.trunk();
        int slot = blockSlot(trunk, kind);
        if (slot < 0) {
            car.brain().report(kind.isBlank() ? "there is no block in its trunk to place"
                    : "there is no \"" + kind.strip() + "\" in its trunk to place");
            return Result.REFUSED;
        }
        if (!car.hasEnergy(ImtConfig.RcCar.PLACE_COST)) {
            return Result.NO_POWER;
        }
        ItemStack stack = trunk.getItem(slot);
        BlockItem item = (BlockItem) stack.getItem();
        BlockPos here = car.body().cell();
        BlockPos pos = way < 0 ? here : here.relative(direction);
        String why = whyNot(level, car, pos, item.getBlock().defaultBlockState());
        if (why != null) {
            car.brain().report("it may not place blocks " + why);
            return Result.REFUSED;
        }
        // Alleen op een vrije plek (lucht, water, gras: wat een blok zo vervangt), en niet op een blok van zijn eigen soort
        // (een halve plaat op een halve plaat, sneeuw op sneeuw): samenvoegen doet de auto niet.
        BlockState there = level.getBlockState(pos);
        if (!there.canBeReplaced() || there.is(item.getBlock())) {
            car.brain().report("it cannot put a block there (something is in the way)");
            return Result.REFUSED;
        }
        double oldY = car.getY();
        if (way < 0) {
            // Onder zichzelf: eerst een blok omhoog (als daar plek is), dan het blok op de plek waar hij stond.
            if (!car.body().fits(here.above())) {
                car.brain().report("there is no room above it to go up");
                return Result.REFUSED;
            }
            car.setPos(car.getX(), here.getY() + 1.0, car.getZ());
        }
        Direction look = direction.getAxis().isVertical() ? car.body().facing() : direction;
        boolean placed = item.place(new CarPlaceContext(level, pos, look, stack)).consumesAction();
        if (!placed) {
            if (way < 0) {
                car.setPos(car.getX(), oldY, car.getZ()); // het lukte niet: weer naar beneden
            }
            car.brain().report("it cannot put a block there (something is in the way)");
            return Result.REFUSED;
        }
        if (stack.isEmpty()) {
            trunk.setItem(slot, ItemStack.EMPTY);
        }
        trunk.setChanged();
        car.spend(ImtConfig.RcCar.PLACE_COST);
        return Result.OK;
    }

    /**
     * Waar en hoe de auto een blok neerzet. Dit is de plaats-context die Minecraft zelf voor een dispenser gebruikt
     * (DirectionalPlaceContext), met één verschil: de vragen "kan hier iets staan?" en "vervang ik het aangeklikte blok?"
     * stellen hun vraag niet opnieuw aan het blok dat er staat. In Minecraft doen ze dat wel, en een halve plaat (of een
     * laagje sneeuw) vraagt het dan weer terug: een eindeloze lus, en de server crasht. (Gevonden door de review.)
     */
    private static final class CarPlaceContext extends DirectionalPlaceContext {
        CarPlaceContext(ServerLevel level, BlockPos pos, Direction look, ItemStack stack) {
            super(level, pos, look, stack, Direction.UP);
        }

        @Override
        public boolean canPlace() {
            return getLevel().getBlockState(getClickedPos()).canBeReplaced();
        }

        @Override
        public boolean replacingClickedOnBlock() {
            return true;
        }
    }

    /**
     * Kan de auto dit plaatsen? Alleen gewone blokken van één blok groot: geen blok met een binnenkant (een kist, een
     * oven, een bed, een machine of beveiliging van de mod: zo'n blok hoort een speler als eigenaar te krijgen, en die
     * heeft een auto niet), en geen deur of hoge plant (die zetten zelf een tweede blok erbij, waar wij niet naar keken).
     */
    public static boolean placeable(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item && !(item instanceof DoubleHighBlockItem)
                && !item.getBlock().defaultBlockState().hasBlockEntity();
    }

    /** Het vakje met het blok dat hij gaat plaatsen: het eerste blok, of het eerste van dit soort; -1 als er geen is. */
    private static int blockSlot(SimpleContainer trunk, String kind) {
        String wanted = kind.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
        for (int slot = 0; slot < trunk.getContainerSize(); slot++) {
            ItemStack stack = trunk.getItem(slot);
            if (!placeable(stack)) {
                continue;
            }
            if (wanted.isEmpty()) {
                return slot;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id.getPath().equals(wanted) || id.toString().equals(wanted)) {
                return slot;
            }
        }
        return -1;
    }

    /** Hoeveel blokken (om te plaatsen) er in de kofferbak liggen ({@code car.blocks}). */
    public static int blocksIn(SimpleContainer trunk) {
        int count = 0;
        for (int slot = 0; slot < trunk.getContainerSize(); slot++) {
            ItemStack stack = trunk.getItem(slot);
            if (placeable(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Hoeveel voorwerpen er in de kofferbak liggen ({@code car.items}). */
    public static int itemsIn(SimpleContainer trunk) {
        int count = 0;
        for (int slot = 0; slot < trunk.getContainerSize(); slot++) {
            count += trunk.getItem(slot).getCount();
        }
        return count;
    }

    /** Is elk vakje van de kofferbak bezet ({@code car.full})? */
    public static boolean full(SimpleContainer trunk) {
        for (int slot = 0; slot < trunk.getContainerSize(); slot++) {
            if (trunk.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Welk blok hier zit, zoals {@code block()} het zegt: het laatste stuk van zijn naam ("stone", "diamond_ore"),
     * "air" voor lucht, en "water" of "lava" voor een vloeistof.
     */
    public static String blockName(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return "air";
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return "air";
        }
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    /** De items in dit lijstje bij elkaar (voor de tests). */
    static int count(List<ItemStack> stacks) {
        return stacks.stream().mapToInt(ItemStack::getCount).sum();
    }
}
