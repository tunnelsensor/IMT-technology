package com.senne.imttech.armor;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;

/**
 * De body armor (hoofdstuk 109): het vest dat je draagt, wat erin zit, en alles wat je ermee doet.
 * <ul>
 *   <li><b>Wie het draagt</b> ({@link #worn}): een speler in zijn eigen vest-vakje (Gear.vest; met Curios het slot "body"), of
 *       een Raven-soldaat (jouw keuze: sommige soldaten dragen er een).</li>
 *   <li><b>De magazijnen</b> ({@link #magazines}, {@link #store}): de drie vakjes op je borst. Jouw wens: "deze komen bij de
 *       ander 6 mags van een magpouch". R toont ze na je magazijnpouch en voor de MOLLE-pouches op je rugzak (zie
 *       backpack/Molle.magazines); wegsteken en een leeg magazijn na het herladen gaan in dezelfde volgorde.</li>
 *   <li><b>De plates</b>: wat ze tegenhouden staat in PlateArmor; erin steken doe je in het scherm van het vest, of met een
 *       rechtsklik met de plate in je hand ({@link #insertPlate}).</li>
 *   <li><b>Het scherm</b> ({@link #openHeld}, {@link #openWorn}): de plates, de magazijnen en de tekst op je rug (VestMenu).</li>
 * </ul>
 */
public final class Vests {
    private Vests() {}

    /** Bij het opstarten (ImtTech): de lijsten, het bericht van de tekst en de schade aanmelden. */
    public static void register(IEventBus modEventBus) {
        ArmorRegistry.register(modEventBus);
        modEventBus.addListener(Vests::registerPayloads);
        NeoForge.EVENT_BUS.addListener(PlateArmor::onDamage);
    }

    /** Eigen berichten (net als de rugzakken een eigen lijst, los van network/ModNetwork). */
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(VestTextPayload.TYPE, VestTextPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof VestMenu menu && menu.stillValid(player)) {
                menu.setText(payload.text(), payload.colour());
            }
        });
    }

    // ================================================================ Wie draagt wat

    /** Het vest dat dit wezen draagt (een kopie), of leeg. */
    public static ItemStack worn(LivingEntity entity) {
        if (entity instanceof Player player) {
            return Gear.vest(player);
        }
        if (entity instanceof com.senne.imttech.raven.RavenSoldierEntity soldier) {
            return soldier.vest();
        }
        return ItemStack.EMPTY;
    }

    /** Dit vest (veranderd) terug op dit wezen. */
    public static void setWorn(LivingEntity entity, ItemStack vest) {
        if (entity instanceof Player player) {
            Gear.setVest(player, vest);
        } else if (entity instanceof com.senne.imttech.raven.RavenSoldierEntity soldier) {
            soldier.setVest(vest);
        }
    }

    // ================================================================ De magazijnen

    /** Past dit in een magazijnvakje van het vest? Hetzelfde als in de magazijnpouch: een magazijn of een energiecel. */
    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && GearItem.Kind.MAG_POUCH.accepts(stack);
    }

    /** De drie magazijnvakjes van het vest dat je draagt (kopieën), of een lege lijst zonder vest. */
    public static List<ItemStack> magazines(Player player) {
        ItemStack vest = Gear.vest(player);
        return VestItem.isVest(vest) ? VestItem.data(vest).magazines() : new ArrayList<>();
    }

    /** De magazijnvakjes van je vest vervangen (alleen als je er een draagt en er iets verandert). */
    public static void setMagazines(Player player, List<ItemStack> magazines) {
        ItemStack vest = Gear.vest(player);
        if (!VestItem.isVest(vest)) {
            return;
        }
        VestData data = VestItem.data(vest);
        VestData changed = data.withMagazines(magazines);
        if (!changed.equals(data)) {
            Gear.setVest(player, VestItem.with(vest, changed));
        }
    }

    /** Is er in je vest nog plek voor dit magazijn of deze cel? */
    public static boolean roomFor(Player player, ItemStack stack) {
        if (!accepts(stack)) {
            return false;
        }
        for (ItemStack inside : magazines(player)) {
            if (inside.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Dit magazijn (of deze cel) in een leeg vakje van je vest. Geeft false als het niet past. */
    public static boolean store(Player player, ItemStack stack) {
        if (!accepts(stack) || stack.getCount() != 1) {
            return false;
        }
        List<ItemStack> magazines = magazines(player);
        for (int i = 0; i < magazines.size(); i++) {
            if (magazines.get(i).isEmpty()) {
                magazines.set(i, stack.copy());
                setMagazines(player, magazines);
                return true;
            }
        }
        return false;
    }

    // ================================================================ Aantrekken en plates erin

    /** Sneak + rechtsklik met een vest in je hand: aantrekken (een vest dat je al droeg komt in je hand). */
    public static boolean putOn(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!VestItem.isVest(held)) {
            return false;
        }
        ItemStack old = Gear.vest(player);
        if (!Gear.setVest(player, held.copyWithCount(1))) {
            tell(player, Component.translatable("vest.imttech.no_slot"));
            return false;
        }
        player.setItemInHand(hand, old);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS, 1.0f, 0.9f);
        return true;
    }

    /**
     * Rechtsklik met een plate in je hand terwijl je een vest draagt: hij gaat erin. Eerst voorop (als daar niets zit of de
     * voorste gebarsten is), anders achterop; een gebarsten plate die eruit komt, krijg je in je hand.
     */
    public static boolean insertPlate(ServerPlayer player, InteractionHand hand) {
        ItemStack plate = player.getItemInHand(hand);
        ItemStack vest = Gear.vest(player);
        if (PlateItem.tierOf(plate) == null || !VestItem.isVest(vest)) {
            return false;
        }
        VestData data = VestItem.data(vest);
        Boolean front = null;
        for (boolean side : new boolean[] {true, false}) {
            if (front == null && data.plate(side).isEmpty()) {
                front = side;
            }
        }
        for (boolean side : new boolean[] {true, false}) {
            if (front == null && PlateItem.broken(data.plate(side))) {
                front = side;
            }
        }
        if (front == null) {
            tell(player, Component.translatable("vest.imttech.plates_full"));
            return false;
        }
        ItemStack out = data.plate(front);
        Gear.setVest(player, VestItem.with(vest, data.withPlate(front, plate.copyWithCount(1))));
        plate.shrink(1);
        if (plate.isEmpty()) {
            player.setItemInHand(hand, out);
        } else if (!out.isEmpty() && !player.getInventory().add(out)) {
            player.drop(out, false);
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                SoundSource.PLAYERS, 0.8f, 1.1f);
        player.displayClientMessage(Component.translatable(front ? "vest.imttech.plate_in_front" : "vest.imttech.plate_in_back")
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    // ================================================================ Het scherm

    /** Rechtsklik met het vest in je hand: zijn scherm (de plates, de magazijnen, de tekst). */
    public static void openHeld(ServerPlayer player, InteractionHand hand) {
        if (VestItem.isVest(player.getItemInHand(hand))) {
            open(player, hand == InteractionHand.MAIN_HAND ? VestMenu.MAIN_HAND : VestMenu.OFF_HAND, player.getItemInHand(hand).getHoverName());
        }
    }

    /** Uit het wiel (linker Alt): het scherm van het vest dat je draagt. */
    public static boolean openWorn(ServerPlayer player) {
        ItemStack vest = Gear.vest(player);
        if (!VestItem.isVest(vest)) {
            tell(player, Component.translatable("vest.imttech.not_wearing"));
            return false;
        }
        open(player, VestMenu.WORN, vest.getHoverName());
        return true;
    }

    private static void open(ServerPlayer player, int where, Component title) {
        SimpleMenuProvider provider = new SimpleMenuProvider((id, inventory, p) -> new VestMenu(id, inventory, where), title);
        if (player.connection != null && player.connection.hasChannel(AdvancedOpenScreenPayload.TYPE)) {
            player.openMenu(provider, buf -> buf.writeVarInt(where));
        } else {
            player.openMenu(provider);
        }
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS, 0.5f, 1.3f);
    }

    // ================================================================ De Raven-soldaten

    /**
     * Een vest voor een Raven-soldaat (jouw keuze: "sommige soldaten dragen een vest met plates"): antracietgrijs zoals hun
     * rugzakken, RAVEN in rood op zijn rug, voor en achter een stalen plate (soms een polymeren, af en toe titanium), al wat
     * versleten, en soms een magazijn of twee van zijn wapen.
     */
    public static ItemStack ravenVest(RandomSource random, ItemStack weapon) {
        ItemStack vest = new ItemStack(ArmorRegistry.PLATE_CARRIER.get());
        vest.set(net.minecraft.core.component.DataComponents.DYED_COLOR,
                new net.minecraft.world.item.component.DyedItemColor(com.senne.imttech.backpack.RavenPacks.RAVEN_GREY, true));
        List<ItemStack> magazines = new ArrayList<>();
        com.senne.imttech.guns.FirearmType type = com.senne.imttech.guns.FirearmItem.typeOf(weapon);
        for (int i = 0; i < VestData.MAGS; i++) {
            if (type != null && !type.tube() && random.nextFloat() < 0.35f) {
                int least = Math.max(1, type.capacity() / 3);
                magazines.add(com.senne.imttech.guns.MagazineItem.of(type, least + random.nextInt(type.capacity() - least + 1)));
            } else {
                magazines.add(ItemStack.EMPTY);
            }
        }
        VestData data = new VestData(ravenPlate(random), ravenPlate(random), magazines, "RAVEN", DyeColor.RED.getId());
        VestItem.setData(vest, data);
        return vest;
    }

    private static ItemStack ravenPlate(RandomSource random) {
        float roll = random.nextFloat();
        PlateTier tier = roll < 0.25f ? PlateTier.POLYMER : roll < 0.9f ? PlateTier.STEEL : PlateTier.TITANIUM;
        ItemStack plate = new ItemStack(ArmorRegistry.plate(tier));
        plate.setDamageValue(random.nextInt(plate.getMaxDamage() / 2));
        return plate;
    }

    /** Een soldaat krijgt bij het verschijnen soms een vest ({@code [armor] ravenVestChance}). */
    public static boolean ravenGetsVest(RandomSource random) {
        return random.nextFloat() < ImtConfig.Armor.RAVEN_VEST_CHANCE;
    }

    // ================================================================ Hulpjes

    static void tell(ServerPlayer player, Component text) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
    }
}
