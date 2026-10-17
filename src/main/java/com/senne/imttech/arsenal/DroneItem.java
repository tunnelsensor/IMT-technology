package com.senne.imttech.arsenal;

import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.imt.ImtAdvancements;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Een gebouwde drone, klaar om gelanceerd te worden.
 *
 * Gebruik hem (rechtsklik) en hij schiet omhoog, de lucht uit, naar een baan om de planeet.
 * Daar wacht hij tot I.M.T hem nodig heeft - waar jij ook bent. Vanaf dat moment staat
 * hij in je arsenaal (zie het arsenaal-scherm) en niet meer in je rugzak.
 *
 * Zonder bril gebeurt er niets: het arsenaal is van I.M.T, en zonder haar heb je geen
 * manier om een drone in een baan te houden of terug te roepen.
 */
public class DroneItem extends Item {
    public DroneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            player.swing(hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (ImtServer.authorizedGlasses(serverPlayer) == null) {
            ImtServer.send(serverPlayer, Component.translatable("arsenal.imttech.msg.need_glasses"),
                    ImtMessagePayload.Level.WARNING);
            return InteractionResultHolder.fail(stack);
        }

        ArsenalServer.add(serverPlayer);
        ImtAdvancements.award(serverPlayer, ImtAdvancements.FIRST_DRONE);
        int total = ArsenalServer.get(serverPlayer).size();
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        ArsenalLaunch.spawn(serverPlayer);
        serverPlayer.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.3, 0.3, 0.3, 0.2);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DRONE_DEPLOY.get(),
                SoundSource.PLAYERS, 0.9f, 0.9f);
        ImtServer.send(serverPlayer, Component.translatable("arsenal.imttech.msg.launched", total),
                ImtMessagePayload.Level.INFO);
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.drone").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.drone.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
