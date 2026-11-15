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
        // De kist vóór hem, op de hoogte van zijn arm (hoofdstuk 113); mag hij die niet openen, dan legt hij het op de grond.
        BlockPos front = car.body().front();
        Chest target = chestAt(level, car, false);
        IItemHandler chest = target == null ? null : target.slots();
        boolean left = false;
        for (int slot = 0; slot < trunk.getContainerSize(); slot++) {
            ItemStack stack = trunk.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (chest != null) {
                ItemStack rest = ItemHandlerHelper.insertItemStacked(chest, stack.copy(), false);
                if (!target.station() && rest.getCount() < stack.getCount()) {
                    car.brain().orderStored(stack, stack.getCount() - rest.getCount());
                }
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
        if (target != null) {
            com.senne.imttech.storage.Storages.carLooked(level, car, target);
        }
        return left ? Result.REFUSED : Result.OK;
    }

    // ================================================================ Kisten (hoofdstuk 113)

    /**
     * Het blok met vakjes waar de arm van de auto bij kan: een kist, een ton, een trechter, een kast van een andere mod...
     * {@code station} is de Storage Interface zelf (daar haalt de auto wat iemand inlegde, en brengt hij wat besteld is).
     */
    public record Chest(BlockPos pos, IItemHandler slots, boolean station) {}

    /**
     * De kist vóór de auto, op de hoogte van zijn arm (zie CarMove.front), of null als daar geen kist staat of als hij
     * hem niet mag openen. Met {@code tell} zegt hij waarom niet (voor take, put en check); zonder is hij stil (voor
     * {@code in chest()} en {@code chest.count(...)}: daar is het antwoord dan gewoon "nee" of 0).
     *
     * Wie mag wat: een kist in een beschermde zone opent hij alleen als wie zijn klus startte daar mag bouwen (dezelfde
     * regel als bij breken en plaatsen), en een kist met een slot erop (een sleutelnaam) opent hij nooit: hij heeft geen
     * sleutel. Net als een speler opent hij niets buiten de wereldrand of in de spawnbescherming van een server, en bij een
     * dubbele kist tellen allebei de helften (hij bereikt via de ene helft ook de andere). Zo is een RC Car geen manier om
     * bij iemand anders in de kisten te kijken of er iets uit te halen. (De wereldrand, de spawn en de tweede helft: gevonden
     * bij de nalezing van hoofdstuk 113.)
     */
    public static @Nullable Chest chestAt(ServerLevel level, RcCarEntity car, boolean tell) {
        BlockPos pos = car.body().front();
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        boolean station = entity instanceof com.senne.imttech.storage.StorageInterfaceBlockEntity;
        IItemHandler slots = station ? ((com.senne.imttech.storage.StorageInterfaceBlockEntity) entity).carSlots(car)
                : level.getCapability(Capabilities.ItemHandler.BLOCK, pos, car.body().facing().getOpposite());
        if (slots == null && !station) {
            slots = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        }
        String why = null;
        BlockPos other = otherHalf(level, pos);
        if (slots == null) {
            why = station ? "that Storage Interface does not know this car (link it there first, tab Cars), or the one who"
                    + " drives it may not use that interface" : "there is no chest in front of its arm";
        } else if (!ImtConfig.RcCar.MAY_USE_CHESTS) {
            why = "opening chests is switched off on this server";
        } else if (!level.getWorldBorder().isWithinBounds(pos)) {
            why = "that chest is outside the world border";
        } else if (spawnProtected(level, car, pos)) {
            why = "it may not open chests at the spawn of this server (spawn protection)";
        } else if (!builderHere(level, car, pos) || other != null && !builderHere(level, car, other)) {
            why = "it may not open chests in this protected zone";
        } else if (locked(level, car, entity) || other != null && locked(level, car, level.getBlockEntity(other))) {
            why = "that chest is locked";
        }
        if (why != null) {
            if (tell) {
                car.brain().report(why);
            }
            return null;
        }
        return new Chest(pos.immutable(), slots, station);
    }

    /**
     * De andere helft van een dubbele kist vóór de auto, of null (geen dubbele kist, of die helft ligt in een stuk wereld
     * dat niet geladen is). De vakjes van een dubbele kist zijn die van allebei de helften samen; Minecraft opent zo'n kist
     * alleen als je allebei de helften mag openen.
     */
    private static @Nullable BlockPos otherHalf(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
                        != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            BlockPos other = pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
            return level.isLoaded(other) ? other : null;
        }
        return null;
    }

    /**
     * Zit er een slot op deze kist (de sleutelnaam van Minecraft, die je met een commando zet)? Dan opent alleen wie de
     * sleutel in zijn hand heeft hem; een auto heeft geen handen. We vragen het Minecraft zelf, voor een "nepspeler" met
     * lege handen.
     */
    private static boolean locked(ServerLevel level, RcCarEntity car, @Nullable BlockEntity entity) {
        if (!(entity instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity container)) {
            return false;
        }
        Player nobody = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes("imttech:rc_car".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "RC Car"));
        return !container.canOpen(nobody);
    }

    /** Is dit het item met deze naam ("iron_ingot", of met voorvoegsel "imttech:polymer")? Een lege naam past op alles. */
    public static boolean isItem(ItemStack stack, String name) {
        if (stack.isEmpty()) {
            return false;
        }
        if (name.isEmpty()) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getPath().equals(name) || id.toString().equals(name);
    }

    /** De naam van dit item zoals de taal hem schrijft: "iron_ingot", en voor een item van een mod "imttech:polymer". */
    public static String itemName(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) ? id.getPath() : id.toString();
    }

    /**
     * Past dit voorwerp bij wat de auto zoekt? De naam moet kloppen; en zoekt hij voor een bestelling precies één soort
     * ({@code exact}: een betoverd boek, een drankje), dan ook alles wat erop zit.
     */
    private static boolean wanted(ItemStack stack, String name, @Nullable ItemStack exact) {
        return isItem(stack, name) && (exact == null || ItemStack.isSameItemSameComponents(stack, exact));
    }

    /**
     * Hoeveel er van dit item in deze vakjes liggen, dat de auto kan meenemen. Een shulkerdoos of een volle Ammo Box past
     * niet in zijn kofferbak: die telt niet mee, anders zei {@code item("shulker_box") in chest()} ja terwijl
     * {@code take} hem nooit kan nemen (en een lus daarop bleef eeuwig draaien; gevonden bij de nalezing).
     */
    public static int count(IItemHandler slots, String name, @Nullable ItemStack exact) {
        int count = 0;
        for (int slot = 0; slot < slots.getSlots(); slot++) {
            ItemStack stack = slots.getStackInSlot(slot);
            if (wanted(stack, name, exact) && RcCarEntity.fitsInTrunk(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Hoeveel er van dit item in de kofferbak liggen ({@code trunk.count("iron_ingot")}). */
    public static int countInTrunk(SimpleContainer trunk, String name, @Nullable ItemStack exact) {
        int count = 0;
        for (int slot = 0; slot < trunk.getContainerSize(); slot++) {
            ItemStack stack = trunk.getItem(slot);
            if (wanted(stack, name, exact)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Hoeveel er van dit voorwerp nog in de kofferbak past (in lege vakjes en op stapels van hetzelfde). */
    private static int room(SimpleContainer trunk, ItemStack stack) {
        int limit = Math.min(trunk.getMaxStackSize(), stack.getMaxStackSize());
        int room = 0;
        for (int slot = 0; slot < trunk.getContainerSize(); slot++) {
            ItemStack there = trunk.getItem(slot);
            if (there.isEmpty()) {
                room += limit;
            } else if (ItemStack.isSameItemSameComponents(there, stack)) {
                room += Math.max(0, limit - there.getCount());
            }
        }
        return room;
    }

    /**
     * {@code take("iron_ingot", 8)}: uit de kist vóór de auto in zijn kofferbak. Een lege naam = elk item; {@code count}
     * -1 = zoveel als er is. Geeft OK als hij iets meenam (ook als het minder was dan gevraagd: dat zie je aan
     * {@code trunk.count(...)}), REFUSED als hij niets kon nemen (geen kist, niets van dat soort, kofferbak vol).
     */
    public static Result take(ServerLevel level, RcCarEntity car, String item, int count) {
        Chest chest = chestAt(level, car, true);
        if (chest == null) {
            return Result.REFUSED;
        }
        if (!car.hasEnergy(ImtConfig.RcCar.CHEST_COST)) {
            return Result.NO_POWER;
        }
        String name = com.senne.imttech.logic.program.Expr.itemName(item);
        ItemStack exact = car.brain().orderVariant(name);
        SimpleContainer trunk = car.trunk();
        IItemHandler slots = chest.slots();
        int left = count < 0 ? Integer.MAX_VALUE : count;
        int moved = 0;
        boolean found = false;
        boolean full = false;
        boolean unfit = false;
        for (int slot = 0; slot < slots.getSlots() && left > 0; slot++) {
            ItemStack there = slots.getStackInSlot(slot);
            if (!wanted(there, name, exact)) {
                continue;
            }
            if (!RcCarEntity.fitsInTrunk(there)) {
                unfit = true; // een shulkerdoos, een volle Ammo Box: die past niet in zijn kofferbak
                continue;
            }
            found = true;
            // Eerst vragen wat eruit kan (en wat er in de kofferbak past), dan pas echt nemen: zo raakt er niets kwijt.
            ItemStack offered = slots.extractItem(slot, Math.min(left, there.getCount()), true);
            int fits = offered.isEmpty() ? 0 : Math.min(offered.getCount(), room(trunk, offered));
            if (fits <= 0) {
                full |= !offered.isEmpty();
                continue;
            }
            ItemStack taken = slots.extractItem(slot, fits, false);
            ItemStack rest = trunk.addItem(taken);
            int got = taken.getCount() - rest.getCount(); // wat echt in de kofferbak kwam
            if (!rest.isEmpty()) {
                rest = ItemHandlerHelper.insertItemStacked(slots, rest, false); // paste toch niet: terug in de kist
                if (!rest.isEmpty()) {
                    car.spawnAtLocation(rest);
                }
            }
            moved += got;
            left -= got;
        }
        trunk.setChanged();
        com.senne.imttech.storage.Storages.carLooked(level, car, chest);
        if (moved <= 0) {
            car.brain().report(full ? "its trunk is full" : found ? "it could not take anything out of that chest"
                    : unfit ? "it cannot carry that in its trunk (shulker boxes and full boxes or pouches do not fit)"
                    : name.isEmpty() ? "that chest is empty" : "there is no \"" + name + "\" in that chest");
            return Result.REFUSED;
        }
        car.spend(ImtConfig.RcCar.CHEST_COST);
        level.playSound(null, chest.pos(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.25f, 1.3f);
        return Result.OK;
    }

    /**
     * {@code put("iron_ingot", 8)}: uit zijn kofferbak in de kist vóór de auto. Een lege naam = alles; {@code count} -1 =
     * zoveel als hij heeft. Geeft OK als er iets in ging, REFUSED als er niets in kon (geen kist, niets van dat soort in
     * de kofferbak, de kist vol).
     */
    public static Result put(ServerLevel level, RcCarEntity car, String item, int count) {
        Chest chest = chestAt(level, car, true);
        if (chest == null) {
            return Result.REFUSED;
        }
        if (!car.hasEnergy(ImtConfig.RcCar.CHEST_COST)) {
            return Result.NO_POWER;
        }
        String name = com.senne.imttech.logic.program.Expr.itemName(item);
        ItemStack exact = car.brain().orderVariant(name);
        SimpleContainer trunk = car.trunk();
        int left = count < 0 ? Integer.MAX_VALUE : count;
        int moved = 0;
        boolean found = false;
        for (int slot = 0; slot < trunk.getContainerSize() && left > 0; slot++) {
            ItemStack stack = trunk.getItem(slot);
            if (!wanted(stack, name, exact)) {
                continue;
            }
            found = true;
            int offer = Math.min(left, stack.getCount());
            ItemStack rest = ItemHandlerHelper.insertItemStacked(chest.slots(), stack.copyWithCount(offer), false);
            int went = offer - rest.getCount();
            if (went > 0) {
                if (!chest.station()) {
                    car.brain().orderStored(stack, went); // voor een bestelling "store": dit is opgeborgen
                }
                stack.shrink(went);
                if (stack.isEmpty()) {
                    trunk.setItem(slot, ItemStack.EMPTY);
                }
                moved += went;
                left -= went;
            }
        }
        trunk.setChanged();
        com.senne.imttech.storage.Storages.carLooked(level, car, chest);
        if (moved <= 0) {
            car.brain().report(!found ? (name.isEmpty() ? "its trunk is empty" : "there is no \"" + name + "\" in its trunk")
                    : "that chest is full");
            return Result.REFUSED;
        }
        car.spend(ImtConfig.RcCar.CHEST_COST);
        level.playSound(null, chest.pos(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.25f, 0.9f);
        return Result.OK;
    }

    /** {@code check()}: in de kist vóór de auto kijken, zodat zijn Storage Interface weet wat erin zit. REFUSED = geen kist. */
    public static Result check(ServerLevel level, RcCarEntity car) {
        Chest chest = chestAt(level, car, true);
        if (chest == null) {
            return Result.REFUSED;
        }
        // Zijn Storage Interface onthoudt wat erin zit (jouw wens: "de interface weet de dingen door de sensors of doordat
        // de robot dit gecheckt heeft"). Dat gebeurt ook bij take, put, "in chest()" en chest.count(...).
        com.senne.imttech.storage.Storages.carLooked(level, car, chest);
        return Result.OK;
    }

    /**
     * Wat {@code chest.slots}, {@code chest.free} en {@code chest.items} zeggen: {vakjes, lege vakjes, voorwerpen}; nullen
     * zonder kist. Bij zijn Storage Interface: wat {@code put()} en {@code take()} daar bereiken. De vakjes en de lege
     * vakjes zijn die van PICK-UP (daar legt hij in), de voorwerpen die in de vakjes om op te bergen (daar neemt hij uit).
     * Anders telde een lus {@code while chest.items > 0: take()} ook wat er nog in PICK-UP lag, en hield hij nooit op
     * (gevonden bij de nalezing).
     */
    public static int[] chestNumbers(ServerLevel level, RcCarEntity car) {
        Chest chest = chestAt(level, car, false);
        if (chest == null) {
            return new int[] {0, 0, 0};
        }
        if (chest.station()) {
            int in = com.senne.imttech.storage.StorageInterfaceBlockEntity.IN_SLOTS;
            int out = com.senne.imttech.storage.StorageInterfaceBlockEntity.OUT_SLOTS;
            int waiting = 0;
            int empty = 0;
            for (int slot = 0; slot < in; slot++) {
                waiting += chest.slots().getStackInSlot(slot).getCount();
            }
            for (int slot = in; slot < in + out; slot++) {
                if (chest.slots().getStackInSlot(slot).isEmpty()) {
                    empty++;
                }
            }
            return new int[] {out, empty, waiting};
        }
        int free = 0;
        int items = 0;
        for (int slot = 0; slot < chest.slots().getSlots(); slot++) {
            ItemStack stack = chest.slots().getStackInSlot(slot);
            if (stack.isEmpty()) {
                free++;
            } else {
                items += stack.getCount();
            }
        }
        com.senne.imttech.storage.Storages.carLooked(level, car, chest);
        return new int[] {chest.slots().getSlots(), free, items};
    }

    /** {@code item("x") in chest()} en {@code chest.count("x")}: hoeveel er van dit item in de kist vóór de auto liggen (0 zonder kist). */
    public static int countInChest(ServerLevel level, RcCarEntity car, String name) {
        Chest chest = chestAt(level, car, false);
        if (chest == null) {
            return 0;
        }
        com.senne.imttech.storage.Storages.carLooked(level, car, chest);
        if (chest.station()) {
            // Bij zijn Storage Interface: wat er nog ligt om op te bergen (de IN-vakjes), niet wat hij al bracht.
            int waiting = 0;
            ItemStack exact = car.brain().orderVariant(name);
            for (int slot = 0; slot < com.senne.imttech.storage.StorageInterfaceBlockEntity.IN_SLOTS; slot++) {
                ItemStack stack = chest.slots().getStackInSlot(slot);
                if (wanted(stack, name, exact) && RcCarEntity.fitsInTrunk(stack)) {
                    waiting += stack.getCount();
                }
            }
            return waiting;
        }
        return count(chest.slots(), name, car.brain().orderVariant(name));
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
        // Vóór hem: op de hoogte van zijn arm (hoofdstuk 113). Boven hem: het blok boven de auto; onder hem: waar hij staat.
        BlockPos pos = way < 0 ? here : way == 0 ? car.body().front() : here.relative(direction);
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
