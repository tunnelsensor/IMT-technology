package com.senne.starktech.arsenal;

import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.edith.StarkAdvancements;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.registry.ModSounds;
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
 * Een gebouwde Stark-drone, klaar om gelanceerd te worden.
 *
 * Gebruik hem (rechtsklik) en hij schiet omhoog, de lucht uit, naar een baan om de planeet.
 * Daar wacht hij tot E.D.I.T.H. hem nodig heeft - waar jij ook bent. Vanaf dat moment staat
 * hij in je arsenaal (zie het arsenaal-scherm) en niet meer in je rugzak.
 *
 * Zonder bril gebeurt er niets: het arsenaal is van E.D.I.T.H., en zonder haar heb je geen
 * manier om een drone in een baan te houden of terug te roepen.
 */
public class StarkDroneItem extends Item {
    public StarkDroneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            player.swing(hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (EdithServer.authorizedGlasses(serverPlayer) == null) {
            EdithServer.send(serverPlayer, Component.translatable("arsenal.starktech.msg.need_glasses"),
                    EdithMessagePayload.Level.WARNING);
            return InteractionResultHolder.fail(stack);
        }

        ArsenalServer.add(serverPlayer);
        StarkAdvancements.award(serverPlayer, StarkAdvancements.FIRST_DRONE);
        int total = ArsenalServer.get(serverPlayer).size();
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        ArsenalLaunch.spawn(serverPlayer);
        serverPlayer.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.3, 0.3, 0.3, 0.2);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DRONE_DEPLOY.get(),
                SoundSource.PLAYERS, 0.9f, 0.9f);
        EdithServer.send(serverPlayer, Component.translatable("arsenal.starktech.msg.launched", total),
                EdithMessagePayload.Level.INFO);
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.stark_drone").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.stark_drone.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
