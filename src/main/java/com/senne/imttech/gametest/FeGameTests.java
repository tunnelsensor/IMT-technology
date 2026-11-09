package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.config.NumberStyle;
import com.senne.imttech.config.Numbers;
import com.senne.imttech.energy.FeBridge;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.network.NumberStylePayload;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.station.ChargerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor Forge Energy (hoofdstuk 46, 4 november): onze blokken praten via de energie-capability van NeoForge, en
 * alles wat van voor die tijd bewaard was (tien keer kleiner) wordt bij het laden omgerekend.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class FeGameTests {
    private FeGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Een zonnepaneel is voor een andere mod een energie-opslag die je leeg kunt trekken (zoals een Mekanism-kabel doet);
     * een apparaat van ons (een camera) neemt FE aan in zijn buffertje, en het stroomnet gebruikt dat buffertje vóór de
     * generatoren. Het zonnepaneel maakt sinds hoofdstuk 46 24 FE per tick (zoals het zonnepaneel van Mekanism).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "fe")
    public static void fe_capability_on_our_blocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos solar = new BlockPos(4, 3, 4);
        helper.setBlock(solar, ModBlocks.SOLAR_PANEL.get());
        ChargerBlockEntity panel = (ChargerBlockEntity) helper.getBlockEntity(solar);
        panel.fill(10_000);
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(solar), Direction.NORTH);
        check(storage != null && storage.canExtract() && !storage.canReceive(), "Een zonnepaneel zou een leeg te trekken opslag moeten zijn");
        check(storage.getEnergyStored() == 10_000 && storage.getMaxEnergyStored() == Math.round(ImtConfig.Power.SOLAR_CAPACITY),
                "De opslag van het paneel klopt niet: " + storage.getEnergyStored() + " / " + storage.getMaxEnergyStored());
        check(storage.extractEnergy(500, true) == 500 && panel.energy() == 10_000, "Simuleren zou niets mogen veranderen");
        check(storage.extractEnergy(500, false) == 500 && panel.energy() == 9_500, "Leegtrekken haalde geen 500 FE uit het paneel: " + panel.energy());
        // Uit de code 24 FE per tick (het zonnepaneel van Mekanism); een oud configbestand dat 2 zei, is nu 20.
        check(ImtConfig.Power.SOLAR_RATE >= 20, "Een zonnepaneel zou minstens 20 FE per tick moeten maken, niet " + ImtConfig.Power.SOLAR_RATE);
        // Een apparaat van ons: het buffertje.
        BlockPos camera = new BlockPos(8, 3, 8);
        helper.setBlock(camera, ModBlocks.SECURITY_CAMERA.get());
        IEnergyStorage buffer = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(camera), Direction.NORTH);
        check(buffer != null && buffer.canReceive() && !buffer.canExtract(), "Een camera zou FE moeten aannemen (en niet afgeven)");
        check(buffer.receiveEnergy(300, false) == 300, "De camera nam geen 300 FE aan");
        check(FeBridge.buffered(level, helper.absolutePos(camera)) == 300, "Het buffertje van de camera zou 300 moeten zijn");
        check(PowerGrid.available(level, helper.absolutePos(camera)) == 300, "Het net zou het buffertje moeten meetellen (zonder kabel: alleen het buffertje)");
        check(PowerGrid.pay(level, helper.absolutePos(camera), 100), "Betalen uit het buffertje lukte niet");
        check(FeBridge.buffered(level, helper.absolutePos(camera)) == 200, "Na 100 betalen zou er 200 over moeten zijn");
        check(!PowerGrid.pay(level, helper.absolutePos(camera), 500), "Meer betalen dan er is, zou niet mogen");
        int max = Math.round(ImtConfig.Power.FE_DEVICE_BUFFER);
        check(buffer.receiveEnergy(max * 2, false) == max - 200, "Het buffertje zou tot " + max + " vol mogen");
        helper.succeed();
    }

    /**
     * Alles van voor 4 november was tien keer kleiner: een generator en een bril die zonder eenheid bewaard zijn, lezen
     * we maal tien; wat met eenheid bewaard is, blijft zoals het is.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "fe")
    public static void fe_old_energy_is_converted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos solar = new BlockPos(4, 3, 4);
        helper.setBlock(solar, ModBlocks.SOLAR_PANEL.get());
        ChargerBlockEntity panel = (ChargerBlockEntity) helper.getBlockEntity(solar);
        CompoundTag old = panel.saveWithoutMetadata(level.registryAccess());
        old.putDouble("Energy", 1_200);
        old.remove("EnergyUnits");
        panel.loadWithComponents(old, level.registryAccess());
        check(panel.energy() == 12_000, "Een oud paneel met 1200 zou 12000 FE moeten hebben, niet " + panel.energy());
        CompoundTag fresh = panel.saveWithoutMetadata(level.registryAccess());
        check(fresh.getInt("EnergyUnits") == 2 && fresh.getDouble("Energy") == 12_000, "Opnieuw bewaren zou de eenheid 2 moeten schrijven");
        panel.loadWithComponents(fresh, level.registryAccess());
        check(panel.energy() == 12_000, "Een paneel met eenheid 2 zou niet nog eens maal tien mogen: " + panel.energy());
        // De bril: zonder eenheid maal tien (en dan staat de eenheid erop).
        ItemStack glasses = new ItemStack(ModItems.IMT_GLASSES.get());
        glasses.set(ModDataComponents.ENERGY.get(), 2_500.0);
        check(ImtData.getEnergy(glasses) == 25_000, "Een oude bril met 2500 zou 25000 FE moeten hebben, niet " + ImtData.getEnergy(glasses));
        check(glasses.getOrDefault(ModDataComponents.ENERGY_UNITS.get(), 0) == 2, "De bril zou nu de eenheid 2 moeten hebben");
        check(ImtData.getEnergy(glasses) == 25_000, "Nog eens lezen zou niet nog eens maal tien mogen");
        ItemStack full = new ItemStack(ModItems.IMT_GLASSES.get());
        ImtData.setEnergy(full, ImtData.getMaxEnergy(full));
        check(ImtData.getEnergy(full) == Math.round(ImtConfig.Glasses.BASE_CAPACITY), "Een volle bril zou " + ImtConfig.Glasses.BASE_CAPACITY + " FE moeten hebben");
        helper.succeed();
    }

    /**
     * Hoofdstuk 66 (jouw wens: "scheiding met spatie 5 000 of met punt 5.000 of gebruik grootheden zoals 5k FE"): elke
     * stijl zet een getal goed neer (ook kommagetallen, afronden naar de volgende eenheid en een min), de stem krijgt weer
     * gewone cijfers, en de server onthoudt per speler welke stijl hij koos, ook na een reis door het netwerk.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "fe")
    public static void number_styles_for_energy(GameTestHelper helper) {
        String hard = String.valueOf(NumberStyle.HARD_SPACE);
        same(NumberStyle.PLAIN.format(5000), "5000");
        same(NumberStyle.SPACE.format(5000), "5" + hard + "000");
        same(NumberStyle.DOT.format(5000), "5.000");
        same(NumberStyle.COMMA.format(5000), "5,000");
        same(NumberStyle.SHORT.format(5000), "5k");
        same(NumberStyle.SHORT.format(5_000_000), "5M");
        // Kommagetallen: hooguit twee cijfers erachter; met punten ertussen wordt de komma het teken ervoor.
        same(NumberStyle.SPACE.format(1_234_567.5), "1" + hard + "234" + hard + "567.5");
        same(NumberStyle.DOT.format(1_234_567.5), "1.234.567,5");
        same(NumberStyle.COMMA.format(1_234_567.5), "1,234,567.5");
        same(NumberStyle.DOT.format(0.35), "0,35");
        same(NumberStyle.SPACE.format(999), "999");
        same(NumberStyle.SPACE.format(-1500), "-1" + hard + "500");
        same(NumberStyle.SPACE.format(-0.001), "0");
        // Kort: drie cijfers die tellen, en afgerond naar de volgende eenheid (nooit "1000k").
        same(NumberStyle.SHORT.format(2456), "2.46k");
        same(NumberStyle.SHORT.format(24_600), "24.6k");
        same(NumberStyle.SHORT.format(1_728_000), "1.73M");
        same(NumberStyle.SHORT.format(999_999), "1M");
        same(NumberStyle.SHORT.format(999.999), "1k");
        same(NumberStyle.SHORT.format(999.5), "999.5");
        same(NumberStyle.SHORT.format(3_000_000_000.0), "3G");
        // De stem leest gewone cijfers.
        same(NumberStyle.SPACE.spoken("Required: 1" + hard + "500 FE."), "Required: 1500 FE.");
        same(NumberStyle.DOT.spoken("Uses 1.728.000 per hour, 0,5 per tick"), "Uses 1728000 per hour, 0.5 per tick");
        same(NumberStyle.COMMA.spoken("1,500 FE"), "1500 FE");
        same(NumberStyle.SHORT.spoken("5k FE, 2.5M FE"), "5 thousand FE, 2.5 million FE");
        // De server: per speler, wat zijn spel stuurde (heen en terug door het netwerk); nog niets gehoord = de standaard.
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        same(Numbers.amount(player, 5000), NumberStyle.DEFAULT.format(5000));
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        NumberStylePayload.STREAM_CODEC.encode(buffer, new NumberStylePayload(NumberStyle.SHORT));
        NumberStyle.remember(player, NumberStylePayload.STREAM_CODEC.decode(buffer).style());
        same(Numbers.amount(player, 5_000_000), "5M");
        net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buffer, 99); // een stijl die deze versie niet kent
        check(NumberStylePayload.STREAM_CODEC.decode(buffer).style() == NumberStyle.DEFAULT, "Een onbekende stijl zou de standaard moeten worden");
        NumberStyle.forget(player.getUUID());
        same(Numbers.amount(player, 5000), NumberStyle.DEFAULT.format(5000));
        helper.succeed();
    }

    private static void same(String actual, String expected) {
        check(actual.equals(expected), "Verwacht '" + expected + "', maar kreeg '" + actual + "'");
    }
}
