package com.senne.imttech.car;

import com.senne.imttech.bot.BotNetwork;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.network.RcCarActionPayload;
import com.senne.imttech.network.RcCarOpenPayload;
import com.senne.imttech.security.BaseTrust;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.TeamFriends;
import com.senne.imttech.security.zone.ZoneData;
import com.senne.imttech.security.zone.Zones;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De RC Car aan de kant van de server (hoofdstuk 108): zijn scherm openen, de knoppen uitvoeren, de kofferbak, de
 * energiecel, oppakken, en de RC Controller. Het scherm zelf staat in client/car/RcCarScreen.
 *
 * <b>Wie mag wat.</b> De kofferbak mag iedereen openen (zoals een kist: "als je binnen bent kun je alles opendoen").
 * Al de rest (zijn scherm, een functie starten, zijn programma, zijn naam, de cel, de boorkop) mag wie hem mag
 * <b>besturen</b> ({@link #mayControl}): wie hem neerzette, en wie vertrouwd is in de basis waar hij staat als zijn
 * eigenaar daar zelf ook bij hoort. Stilzetten en oppakken mag bovendien wie mag bouwen in de beschermde zone waar hij
 * in staat ({@link #mayRemove}). Een auto van niemand wordt van wie hem als eerste opent, behalve in iemands basis.
 * Tijdens een klus breekt en plaatst hij met de rechten van wie die klus startte (zie RcCarEntity.actorId).
 *
 * <b>Van hoe ver.</b> Zijn scherm open je op de auto zelf (sneak + rechtsklik), of van ver met een RC Controller die
 * met deze auto gekoppeld is. Staat het scherm eenmaal open, dan werken de knoppen tot {@code controlRange} blokken ver
 * (64), in dezelfde wereld: de auto rijdt van je weg terwijl je kijkt, en dan moet Stop het nog doen. Alleen oppakken
 * en de cel eruit halen moet van dichtbij (8 blokken).
 */
public final class RcCars {
    private RcCars() {}

    /** Zo dichtbij moet je staan om hem op te pakken of zijn cel eruit te halen (blokken). */
    public static final double REACH = 8.0;

    /**
     * Elke auto die nu geladen is, op zijn vaste nummer: zo vindt een RC Controller zijn auto. Een auto meldt zich elke
     * seconde aan (RcCarEntity.serverTick) en af als hij weggaat. Alleen op de server.
     */
    private static final Map<UUID, RcCarEntity> BY_LINK = new HashMap<>();

    public static void register(RcCarEntity car) {
        BY_LINK.put(car.link(), car);
    }

    public static void unregister(RcCarEntity car) {
        BY_LINK.remove(car.link(), car);
    }

    /** De server stopt: niets onthouden voor de volgende wereld. */
    public static void clear() {
        BY_LINK.clear();
    }

    /** De auto met dit vaste nummer, als hij nu geladen is; anders null. */
    public static @Nullable RcCarEntity find(UUID link) {
        RcCarEntity car = BY_LINK.get(link);
        if (car != null && (car.isRemoved() || !car.isAlive())) {
            BY_LINK.remove(link);
            return null;
        }
        return car;
    }

    // ================================================================ Zijn scherm

    /** Sneak + rechtsklik op de auto (of de RC Controller): mag je hem instellen, dan opent jouw spel zijn scherm. */
    public static void open(ServerPlayer player, RcCarEntity car, boolean remote) {
        if (!mayEdit(player, car)) {
            return;
        }
        ModNetwork.sendTo(player, new RcCarOpenPayload(car.getId(), car.ownerName(), remote, false, ""));
    }

    /**
     * Mag deze speler de auto besturen en instellen (zijn scherm, een functie starten, zijn programma, zijn naam, de cel,
     * de boorkop)? Zijn eigenaar altijd. Iemand anders alleen als hij vertrouwd is in de basis waar de auto staat (de
     * regel van BaseTrust) én de eigenaar van de auto daar zelf ook bij hoort: zo gebruiken vrienden in één basis elkaars
     * auto's, maar neemt een vreemde jouw auto niet over door een Security Core in de buurt te zetten. (Gevonden door de
     * review: met alleen de regel van BaseTrust kon dat.) Een auto van niemand wordt van wie hem als eerste opent.
     */
    public static boolean mayControl(ServerPlayer player, RcCarEntity car) {
        if (!BaseTrust.mayEdit(player, car)) {
            return false;
        }
        UUID owner = car.owner();
        return owner == null || owner.equals(player.getUUID()) || ownerBelongsHere(player.serverLevel(), car);
    }

    /**
     * Zoals {@link #mayControl}, maar zonder dat er iets verandert: een auto van niemand wordt hier niet van deze speler.
     * Voor lijsten (de Storage Interface toont de auto's in de buurt die je kunt koppelen, hoofdstuk 113).
     */
    public static boolean wouldControl(ServerPlayer player, RcCarEntity car) {
        if (!BaseTrust.wouldAllow(player, car)) {
            return false;
        }
        UUID owner = car.owner();
        return owner == null || owner.equals(player.getUUID()) || ownerBelongsHere(player.serverLevel(), car);
    }

    /**
     * Zoals {@link #wouldControl(ServerPlayer, RcCarEntity)}, op nummer en naam: wie iets bestelde bij een Storage
     * Interface hoeft niet online te zijn (hoofdstuk 113). De auto is van hem; of hij is vertrouwd waar de auto staat, en de
     * eigenaar van de auto hoort daar ook bij. De interface kijkt dit na bij elke bestelling die hij start: een auto die
     * intussen van een ander is (opgepakt in diens zone en weer neergezet), of van iemand die niet meer bij de basis hoort,
     * krijgt zo geen bestellingen meer, en draait dus nooit het programma van een ander met jouw rechten. (Gevonden bij de
     * nalezing.)
     */
    public static boolean wouldControl(ServerLevel level, @Nullable UUID id, String name, RcCarEntity car) {
        UUID owner = car.owner();
        if (owner == null) {
            return BaseTrust.wouldAllow(level, car, id, name);
        }
        if (owner.equals(id)) {
            return true;
        }
        return BaseTrust.trustedHere(level, car.blockPosition(), id, name) && ownerBelongsHere(level, car);
    }

    /**
     * Hoort de eigenaar van de auto bij de basis waar de auto nu staat? Dezelfde regel als BaseTrust.trustedHere, maar op
     * nummer en naam: de eigenaar hoeft niet op de server te zijn.
     */
    private static boolean ownerBelongsHere(ServerLevel level, RcCarEntity car) {
        BlockPos pos = car.blockPosition();
        ZoneData.Zone zone = ZoneData.get(level).at(level.dimension(), pos);
        if (zone != null && zone.isBuilder(level, car.owner(), car.ownerName())) {
            return true;
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        if (core == null || core.ownerId() == null || car.owner() == null) {
            return false;
        }
        return core.ownerId().equals(car.owner()) || core.isTrusted(car.owner())
                || TeamFriends.allied(level, core.ownerName(), car.ownerName());
    }

    /**
     * Mag deze speler de auto stilzetten en oppakken? Wie hem mag besturen. En wie mag bouwen in de beschermde zone waar
     * de auto in staat: een auto kan niet kapot, dus wie er een in jouw zone achterlaat, is hem kwijt.
     */
    public static boolean mayRemove(ServerPlayer player, RcCarEntity car) {
        return mayControl(player, car) || inOwnZone(player, car);
    }

    /** Staat de auto in een beschermde zone waar deze speler mag bouwen? */
    private static boolean inOwnZone(ServerPlayer player, RcCarEntity car) {
        ZoneData.Zone zone = Zones.activeAt(player.serverLevel(), car.blockPosition());
        return zone != null && zone.isBuilder(player.serverLevel(), player);
    }

    /** Mag deze speler de auto besturen? Zo niet, dan leest hij van wie hij is (en of hij hem wel mag oppakken). */
    private static boolean mayEdit(ServerPlayer player, RcCarEntity car) {
        if (mayControl(player, car)) {
            return true;
        }
        String owner = BaseTrust.belongsTo(player.serverLevel(), car);
        player.displayClientMessage(owner.isEmpty() ? Component.translatable("message.imttech.rc_car.locked")
                : Component.translatable(inOwnZone(player, car) ? "message.imttech.rc_car.not_yours_zone"
                        : "message.imttech.rc_car.not_yours", owner), true);
        return false;
    }

    /**
     * Rechtsklik op een auto met een naamkaartje. Minecraft zelf geeft elke mob de naam van het kaartje, van wie hij ook
     * is en wat er ook op staat; dat houden we tegen. De naam komt er alleen op als je de auto mag besturen, en met
     * dezelfde regels als de knop Rename. (Gevonden door de review.)
     */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof RcCarEntity car) || !event.getItemStack().is(Items.NAME_TAG)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        Component typed = event.getItemStack().get(DataComponents.CUSTOM_NAME);
        if (typed == null || !(event.getEntity() instanceof ServerPlayer player) || !mayEdit(player, car)) {
            return; // een kaartje zonder naam doet niets
        }
        String name = BotNetwork.cleanName(typed.getString());
        if (name == null) {
            player.displayClientMessage(Component.translatable("message.imttech.robot.bad_name", BotNetwork.MAX_NAME), true);
            return;
        }
        car.setCustomName(Component.literal(name));
        event.getItemStack().consume(1, player);
    }

    // ================================================================ De kofferbak

    /** Rechtsklik op de auto: zijn kofferbak gaat open (twee rijen, zoals een kleine kist). */
    public static void openTrunk(ServerPlayer player, RcCarEntity car) {
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new TrunkMenu(id, inventory, car),
                Component.translatable("container.imttech.rc_car_trunk", car.displayName())));
    }

    /**
     * Het scherm van de kofferbak: de gewone kist van twee rijen, maar wat niet in de kofferbak mag (een RC Car, een
     * shulker box), leg je er niet in.
     */
    private static final class TrunkMenu extends ChestMenu {
        TrunkMenu(int id, Inventory inventory, RcCarEntity car) {
            super(MenuType.GENERIC_9x2, id, inventory, car.trunk(), 2);
            for (int i = 0; i < RcCarEntity.TRUNK_SIZE; i++) {
                Slot old = slots.get(i);
                Slot slot = new Slot(car.trunk(), i, old.x, old.y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return RcCarEntity.fitsInTrunk(stack);
                    }
                };
                slot.index = i;
                slots.set(i, slot);
            }
        }
    }

    // ================================================================ De energiecel

    /**
     * Rechtsklik op de auto met een Energy Cell: die gaat erin, en de cel die erin zat krijg je terug (in dezelfde hand
     * als die leeg is, anders in je rugzak).
     */
    public static void loadCell(ServerPlayer player, RcCarEntity car, InteractionHand hand) {
        if (!mayEdit(player, car)) {
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        ItemStack old = car.swapCell(held.split(1));
        if (!old.isEmpty()) {
            if (player.getItemInHand(hand).isEmpty()) {
                player.setItemInHand(hand, old);
            } else {
                give(player, old);
            }
        }
        car.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.7f, 1.5f);
        player.displayClientMessage(Component.translatable("message.imttech.rc_car.cell_in", car.displayName(),
                Math.round(car.cellPercent())), true);
    }

    // ================================================================ De boorkop

    /**
     * Rechtsklik op de auto met een boorkop (jouw wens: "zorg dat je de drill head kunt upgraden"): die zit erop, en de
     * kop die erop zat krijg je terug (in dezelfde hand als die leeg is, anders in je rugzak). Dat mag ook terwijl hij
     * boort: het volgende blok gaat dan al sneller.
     */
    public static void swapDrill(ServerPlayer player, RcCarEntity car, InteractionHand hand) {
        if (!mayEdit(player, car)) {
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        DrillHead fresh = DrillHead.of(held);
        if (fresh == null) {
            return;
        }
        DrillHead old = car.drill();
        if (fresh == old) {
            player.displayClientMessage(Component.translatable("message.imttech.rc_car.drill_same", car.displayName(),
                    old.displayName()), true);
            return;
        }
        held.shrink(1);
        car.setDrill(fresh);
        ItemStack back = new ItemStack(old.item());
        if (player.getItemInHand(hand).isEmpty()) {
            player.setItemInHand(hand, back);
        } else {
            give(player, back);
        }
        car.playSound(SoundEvents.ANVIL_USE, 0.35f, 1.6f);
        player.displayClientMessage(Component.translatable("message.imttech.rc_car.drill_on", car.displayName(),
                fresh.displayName(), Math.round(fresh.speed() * 100)), true);
    }

    // ================================================================ De RC Controller

    /** Rechtsklik op de auto met een RC Controller: koppelen. Was hij al met deze auto gekoppeld, dan opent zijn scherm. */
    public static void link(ServerPlayer player, RcCarEntity car, ItemStack controller) {
        if (!mayEdit(player, car)) {
            return;
        }
        RcLink old = RcControllerItem.link(controller);
        if (old != null && old.car().equals(car.link())) {
            open(player, car, false);
            return;
        }
        RcControllerItem.setLink(controller, car);
        car.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.6f);
        player.displayClientMessage(Component.translatable("message.imttech.rc_car.linked", car.displayName()), true);
    }

    /** Net een auto neergezet: een RC Controller in je rugzak die nog nergens mee gekoppeld is, koppelt met deze. */
    public static void linkFreeController(ServerPlayer player, RcCarEntity car) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof RcControllerItem && RcControllerItem.link(stack) == null) {
                RcControllerItem.setLink(stack, car);
                player.displayClientMessage(Component.translatable("message.imttech.rc_car.linked", car.displayName()), true);
                return;
            }
        }
    }

    /** Rechtsklik met de RC Controller: het scherm van zijn auto, als die binnen bereik is. */
    public static void useController(ServerPlayer player, ItemStack controller) {
        RcLink link = RcControllerItem.link(controller);
        if (link == null) {
            player.displayClientMessage(Component.translatable("message.imttech.rc_controller.unlinked"), true);
            return;
        }
        RcCarEntity car = find(link.car());
        if (car == null || !inRange(player, car)) {
            player.displayClientMessage(Component.translatable("message.imttech.rc_controller.no_signal", link.name()), true);
            return;
        }
        open(player, car, true);
    }

    /** Staat de auto in dezelfde wereld en binnen het bereik van zijn scherm en de controller? */
    private static boolean inRange(ServerPlayer player, RcCarEntity car) {
        double range = ImtConfig.RcCar.CONTROL_RANGE;
        return car.level() == player.level() && player.distanceToSqr(car) <= range * range;
    }

    // ================================================================ De knoppen

    /** Een knop uit het scherm van de auto. */
    public static void handleAction(ServerPlayer player, RcCarActionPayload payload) {
        if (!(player.serverLevel().getEntity(payload.entityId()) instanceof RcCarEntity car) || !car.isAlive()) {
            return;
        }
        // Stilzetten en oppakken mag ook wie de auto in zijn eigen zone vindt; al de rest alleen wie hem mag besturen.
        boolean lifting = payload.op() == RcCarActionPayload.Op.STOP || payload.op() == RcCarActionPayload.Op.PICK_UP;
        if (!(lifting ? mayRemove(player, car) : mayControl(player, car))) {
            return;
        }
        if (!inRange(player, car)) {
            player.displayClientMessage(Component.translatable("message.imttech.rc_controller.no_signal", car.displayName()), true);
            return;
        }
        boolean near = player.distanceToSqr(car) <= REACH * REACH;
        String name = car.displayName();
        switch (payload.op()) {
            case START -> {
                if (ready(player, car) && !car.brain().startMain(player)) {
                    player.displayClientMessage(Component.translatable("message.imttech.rc_car.no_main", name), true);
                }
            }
            case RUN -> {
                if (ready(player, car) && !car.brain().startFunction(payload.text(), player)) {
                    player.displayClientMessage(Component.translatable("message.imttech.rc_car.no_function", name, payload.text()), true);
                }
            }
            case STEP -> {
                CarMove.Plan plan = step(payload.text());
                if (plan != null && ready(player, car)) {
                    car.brain().startManual(plan, player);
                }
            }
            case STOP -> car.brain().stop();
            case RENAME -> rename(player, car, payload.text());
            case PICK_UP -> {
                if (near) {
                    pickUp(player, car);
                } else {
                    player.displayClientMessage(Component.translatable("message.imttech.rc_car.too_far", name), true);
                }
            }
            case CELL_OUT -> {
                if (!near) {
                    player.displayClientMessage(Component.translatable("message.imttech.rc_car.too_far", name), true);
                } else if (car.hasCell()) {
                    car.brain().stop();
                    give(player, car.swapCell(ItemStack.EMPTY));
                    car.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.7f, 1.1f);
                }
            }
            case CODE -> ModNetwork.sendTo(player, new RcCarOpenPayload(car.getId(), car.ownerName(), !near, true, car.source()));
            case SAVE -> {
                String problem = car.setSource(payload.text());
                player.displayClientMessage(problem != null ? Component.translatable("message.imttech.rc_car.code_problem", problem)
                        : Component.translatable("message.imttech.rc_car.code_saved", name, car.functions().size()), true);
            }
        }
    }

    /**
     * Kan de auto nu iets beginnen? Niet als hij al bezig is, na een EMP-granaat stilligt, of geen stroom heeft: dan
     * leest de speler waarom.
     */
    private static boolean ready(ServerPlayer player, RcCarEntity car) {
        String name = car.displayName();
        String problem = car.brain().busy() ? "message.imttech.rc_car.busy"
                : car.isJammed() ? "message.imttech.rc_car.jammed"
                : !car.hasCell() ? "message.imttech.rc_car.no_cell_hint"
                : car.cellPercent() <= 0 ? "message.imttech.rc_car.cell_empty_hint" : null;
        if (problem != null) {
            player.displayClientMessage(Component.translatable(problem, name), true);
        }
        return problem == null;
    }

    /** De stap van een pijltje op het scherm: vooruit, achteruit, draaien of breken. Null als we hem niet kennen. */
    private static CarMove.@Nullable Plan step(String what) {
        return switch (what) {
            case "forward" -> new CarMove.Drive(1);
            case "back" -> new CarMove.Drive(-1);
            case "left" -> new CarMove.Turn(false);
            case "right" -> new CarMove.Turn(true);
            case "mine" -> new CarMove.Mine(0);
            case "mine_up" -> new CarMove.Mine(1);
            case "mine_down" -> new CarMove.Mine(-1);
            case "arm_up" -> new CarMove.Arm(1);
            case "arm_down" -> new CarMove.Arm(-1);
            default -> null;
        };
    }

    /** Een nieuwe naam (niet te lang); leeg = weer gewoon "RC Car". */
    private static void rename(ServerPlayer player, RcCarEntity car, String typed) {
        if (typed.isBlank()) {
            car.setCustomName(null);
            return;
        }
        String name = BotNetwork.cleanName(typed);
        if (name == null) {
            player.displayClientMessage(Component.translatable("message.imttech.robot.bad_name", BotNetwork.MAX_NAME), true);
            return;
        }
        car.setCustomName(Component.literal(name));
    }

    /** Oppakken: de auto wordt een item in je rugzak, met zijn programma, zijn naam, zijn cel, zijn kofferbak en zijn boorkop. */
    public static void pickUp(ServerPlayer player, RcCarEntity car) {
        ServerLevel level = player.serverLevel();
        car.brain().stop();
        ItemStack stack = car.toItem();
        // De naam nu al: zit de auto straks in je rugzak, dan is dit stapeltje leeg (en heette hij "Air").
        Component name = stack.getHoverName();
        level.playSound(null, car.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 0.5f, 1.4f);
        car.discard();
        give(player, stack);
        player.displayClientMessage(Component.translatable("message.imttech.robot.picked_up", name), true);
    }

    /**
     * In je rugzak, of op de grond als daar geen plek is. Eerst zelf kijken of er plek is: in creative zegt Minecraft bij
     * een volle rugzak "gelukt" en gooit het voorwerp weg (dan was je auto met alles erin verdwenen).
     */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        boolean room = inventory.getFreeSlot() >= 0 || inventory.getSlotWithRemainingSpace(stack) >= 0;
        if (!room || !inventory.add(stack)) {
            player.drop(stack, false);
        }
    }
}
