package com.senne.imttech.backpack;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.Gear;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * De rugzakken op de server (hoofdstuk 72): wat er elke tick gebeurt en wat de berichten van jouw spel doen.
 * <ul>
 *   <li>Met een rugzak open <b>loop je traag</b> ({@code [backpacks] openWalkSpeed}, 40 %; jouw keuze bij "openen kost
 *       tijd"). Dat is een tijdelijke aanpassing van je loopsnelheid zolang het scherm open is (elke tick van de server
 *       bekeken, voor elke speler).</li>
 *   <li>De rits telt af (BackpackZip.tick).</li>
 *   <li>De rugzaktoets en de knop in het wiel: de rugzak op je rug openritsen ({@link #openWorn}); de sorteerknop.</li>
 * </ul>
 */
public final class Backpacks {
    private Backpacks() {}

    /** De naam van de tragere loopsnelheid met een open rugzak. */
    public static final ResourceLocation SLOW = ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "backpack_open");

    /** Bij het opstarten (ImtTech): de lijsten, de berichten en de gebeurtenissen aanmelden. */
    public static void register(IEventBus modEventBus) {
        BackpackRegistry.register(modEventBus);
        modEventBus.addListener(Backpacks::registerPayloads);
        NeoForge.EVENT_BUS.addListener(BackpackTheft::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            BackpackZip.tick(event.getServer());
            BackpackTheft.tick(event.getServer());
            // Elke speler, ook de nep-spelers van de tests (bij hen komt er geen PlayerTickEvent: die loopt via hun verbinding).
            boolean dumpTick = event.getServer().getTickCount() % 4 == 0;
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                updateSpeed(player);
                if (dumpTick) {
                    Molle.tickDump(player);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            BackpackZip.clear();
            BackpackInventory.clear();
            Molle.clear();
            BackpackTheft.clear();
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(BackpackActionPayload.TYPE, BackpackActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                switch (payload.action()) {
                    case BackpackActionPayload.OPEN_WORN -> openWorn(player);
                    case BackpackActionPayload.SORT -> sort(player);
                    default -> { }
                }
            }
        });
        registrar.playToClient(BackpackZipPayload.TYPE, BackpackZipPayload.STREAM_CODEC,
                (payload, context) -> com.senne.imttech.client.backpack.BackpackClient.zip(payload.ticks()));
        registrar.playToClient(BackpackTheftPayload.TYPE, BackpackTheftPayload.STREAM_CODEC,
                (payload, context) -> com.senne.imttech.client.backpack.BackpackClient.theftMeter(payload.meter()));
    }

    /** De rugzak op je rug openritsen (de rugzaktoets, of de knop in het wiel). */
    public static boolean openWorn(ServerPlayer player) {
        if (Gear.back(player).isEmpty()) {
            player.displayClientMessage(Component.translatable("backpack.imttech.not_wearing").withStyle(ChatFormatting.RED), true);
            return false;
        }
        return BackpackZip.start(player, BackpackPlace.worn(player));
    }

    /** De sorteerknop: de rugzak die je open hebt sorteren. */
    public static void sort(ServerPlayer player) {
        if (player.containerMenu instanceof BackpackMenu menu && menu.open() != null && menu.stillValid(player)) {
            menu.open().sort();
            menu.broadcastChanges();
        }
    }

    /** Elke tick: traag lopen zolang je een rugzak open hebt. */
    private static void updateSpeed(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean open = player.containerMenu instanceof BackpackMenu;
        if (open) {
            double amount = ImtConfig.Backpacks.OPEN_WALK_SPEED - 1.0;
            AttributeModifier current = speed.getModifier(SLOW);
            if (current == null || current.amount() != amount) {
                speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else if (speed.hasModifier(SLOW)) {
            speed.removeModifier(SLOW);
        }
    }
}
