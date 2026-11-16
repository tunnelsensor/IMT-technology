package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerCables;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.illusion.IllusionScene;
import com.senne.imttech.imt.ImtThreats;
import com.senne.imttech.light.ArcLampBlock;
import com.senne.imttech.network.ImtStatusPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.station.ChargerBlockEntity;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Hoofdstuk 134: lag en een kick op de server. Wat we hier testen:
 * <ul>
 *   <li>het statusbericht van de bril past altijd (meer dan 32 dreigingen tijdens een raid gooide je van de server), en
 *       een bericht dat niet in te pakken is, wordt niet verstuurd in plaats van de speler eruit te gooien;</li>
 *   <li>een valse muur past altijd in zijn bericht, ook de grootste die de config toelaat;</li>
 *   <li>een Arc Lamp die uitging door een tekort, wacht even voor hij het opnieuw probeert (tenzij het net verandert).</li>
 * </ul>
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class LagGameTests {
    private LagGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een statusbericht met deze dreigingen (de rest leeg). */
    private static ImtStatusPayload status(List<Integer> threats) {
        return new ImtStatusPayload(0, 0, List.of(), List.of(), Optional.empty(), 0, 0, threats, -1, "", 0, 0, "", "", 0);
    }

    /**
     * 40 wezens en 10 kogels komen op je af: de HUD krijgt er 32, eerst de wezens (dichtstbij eerst). Met 5 wezens passen
     * de kogels er ook in. Een bericht met 33 dreigingen kan niet ingepakt worden: dat sturen we niet (vroeger gooide de
     * server je dan van de server, met "Connection reset"); met 32 wel. Een valse muur van 64 bij 64 blijft binnen wat er in
     * zijn bericht past, en de gewone muur (9 bij 5) is nog precies zo groot.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "cables")
    public static void status_and_illusion_messages_always_fit(GameTestHelper helper) {
        List<Integer> creatures = IntStream.range(0, 40).boxed().toList();
        List<Integer> projectiles = IntStream.range(1000, 1010).boxed().toList();
        List<Integer> hud = ImtThreats.forHud(creatures, projectiles, ImtStatusPayload.MAX_THREATS);
        check(hud.size() == ImtStatusPayload.MAX_THREATS, "De HUD kreeg " + hud.size() + " dreigingen, niet 32");
        check(hud.getFirst() == 0 && hud.getLast() == 31 && !hud.contains(1000),
                "Eerst de wezens (dichtstbij eerst), dan pas de kogels: " + hud);
        List<Integer> few = ImtThreats.forHud(creatures.subList(0, 5), projectiles, ImtStatusPayload.MAX_THREATS);
        check(few.size() == 15 && few.get(5) == 1000, "Met 5 wezens passen de 10 kogels er ook in: " + few);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        check(ModNetwork.encodes(player, status(hud)), "Een statusbericht met 32 dreigingen zou gewoon moeten passen");
        check(!ModNetwork.encodes(player, status(IntStream.range(0, 33).boxed().toList())),
                "Een statusbericht met 33 dreigingen kan niet ingepakt worden, maar encodes zegt van wel");

        BlockState stone = Blocks.STONE.defaultBlockState();
        int big = IllusionScene.wall(stone, 64, 64).blocks().size();
        check(big <= IllusionScene.MAX_BLOCKS && big > IllusionScene.MAX_BLOCKS - 65,
                "Een valse muur van 64 bij 64 heeft " + big + " blokken (hooguit " + IllusionScene.MAX_BLOCKS + ")");
        check(IllusionScene.wall(stone, 9, 5).blocks().size() == 45, "De gewone valse muur (9 bij 5) is niet meer 45 blokken");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static boolean lit(GameTestHelper helper, BlockPos lamp) {
        BlockState state = helper.getBlockState(lamp);
        return state.is(ModBlocks.ARC_LAMP.get()) && state.getValue(ArcLampBlock.LIT);
    }

    /** Een lege generator op deze plek (de oude eruit): dat verandert het net, zoals een generator die je weghaalt. */
    private static void emptyGenerator(GameTestHelper helper, BlockPos generator) {
        helper.setBlock(generator, Blocks.AIR);
        helper.setBlock(generator, ModBlocks.HEAT_GENERATOR.get());
    }

    /**
     * Een lamp aan een generator die leeg raakt, gaat uit. Vul je de generator dan bij (dat verandert niets aan het net),
     * dan wacht de lamp {@code arcLampRetryTicks} voor hij het opnieuw probeert: op een te zwak net ging hij anders elke
     * tien seconden aan en uit, en elke keer rekende de server het licht tot 85 blokken ver opnieuw uit. Verandert er wel iets
     * aan het net (hier: PowerGrid.changed, zoals een schakelaar of een kabel), dan mag hij meteen weer.
     *
     * Andere tests kunnen tegelijk het net veranderen (de teller is van de hele server): dan mag de lamp eerder aan, en
     * slaan we de controle op het wachten over.
     */
    @GameTest(template = ARENA, timeoutTicks = 1000, batch = "arc_lamp")
    public static void arc_lamp_waits_after_running_out(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos generator = new BlockPos(3, 2, 4);
        BlockPos lamp = new BlockPos(9, 2, 4);
        helper.setBlock(generator, ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(generator)).fill(50_000);
        for (int x = 4; x <= 8; x++) {
            PowerCables.add(level, helper.absolutePos(new BlockPos(x, 2, 4)));
        }
        helper.setBlock(lamp, ModBlocks.ARC_LAMP.get());
        int retry = Math.round(ImtConfig.Power.ARC_LAMP_RETRY_TICKS);
        long[] outAt = new long[1];
        int[] versionAtOut = new int[1];
        boolean[] gridChanged = new boolean[1];
        helper.startSequence()
                .thenWaitUntil(() -> check(lit(helper, lamp), "De lamp brandt niet aan een volle generator"))
                .thenExecute(() -> emptyGenerator(helper, generator))
                .thenWaitUntil(() -> check(!lit(helper, lamp), "De lamp brandt nog aan een lege generator"))
                .thenExecute(() -> {
                    outAt[0] = level.getGameTime();
                    versionAtOut[0] = PowerGrid.version();
                    ((ChargerBlockEntity) helper.getBlockEntity(generator)).fill(50_000);
                })
                .thenExecuteAfter(40, () -> {
                    if (PowerGrid.version() == versionAtOut[0]) {
                        check(!lit(helper, lamp), "De lamp ging na een tekort al na 40 ticks weer aan, zonder te wachten");
                    }
                })
                .thenWaitUntil(() -> {
                    gridChanged[0] |= PowerGrid.version() != versionAtOut[0];
                    check(lit(helper, lamp), "De lamp gaat na het wachten niet meer aan, terwijl de generator weer vol is");
                })
                .thenExecute(() -> {
                    long waited = level.getGameTime() - outAt[0];
                    check(gridChanged[0] || waited >= retry - 2,
                            "De lamp wachtte maar " + waited + " ticks na een tekort, niet " + retry);
                })
                .thenExecute(() -> emptyGenerator(helper, generator))
                .thenWaitUntil(() -> check(!lit(helper, lamp), "De lamp brandt nog aan een lege generator (2)"))
                .thenExecute(() -> {
                    ((ChargerBlockEntity) helper.getBlockEntity(generator)).fill(50_000);
                    PowerGrid.changed(); // er verandert iets aan het net: dan hoeft hij niet te wachten
                })
                .thenExecuteAfter(5, () -> check(lit(helper, lamp), "Na een verandering aan het net wacht de lamp nog steeds"))
                .thenExecute(() -> helper.setBlock(lamp, Blocks.AIR))
                .thenSucceed();
    }
}
