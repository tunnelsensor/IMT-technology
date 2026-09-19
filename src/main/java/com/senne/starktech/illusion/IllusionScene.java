package com.senne.starktech.illusion;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Een "scène": het beeld dat een cloakingdrone projecteert.
 *
 * Belangrijk: alles hierin is NEP. De server rekent de scène één keer uit en stuurt hem naar de
 * spelers in de buurt; hun spel tekent het erbij. In de echte wereld verandert niets, dus als de
 * drone wordt neergehaald is het beeld ook meteen weg (en niemand heeft blokken verloren).
 *
 * @param kind   wat voor illusie het is
 * @param blocks nep-blokken, met hun plek TEN OPZICHTE VAN de drone
 * @param mobs   nep-wezens, met hun plek ten opzichte van de drone
 */
public record IllusionScene(Kind kind, List<BlockElement> blocks, List<MobElement> mobs) {
    /** De soorten illusies die E.D.I.T.H. kan projecteren. */
    public enum Kind {
        /** Een valse muur voor de drone. */
        WALL,
        /** Een nep-leger van wezens rond de drone. */
        ARMY,
        /** Een kopie van een stuk wereld dat je gescand hebt. */
        COPY,
        /** Geen nep-beeld, maar verbergen: alles in het veld wordt onzichtbaar. */
        CLOAK
    }

    /** Eén nep-blok. */
    public record BlockElement(BlockPos offset, BlockState state) {}

    /** Eén nep-wezen. */
    public record MobElement(double x, double y, double z, EntityType<?> type, float yaw) {}

    public static final IllusionScene CLOAK_ONLY = new IllusionScene(Kind.CLOAK, List.of(), List.of());

    // ---------------------------------------------------------------- Over het netwerk

    private static final StreamCodec<ByteBuf, BlockElement> BLOCK_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BlockElement::offset,
            // Een blokstand sturen we als het nummer uit het grote "alle blokstanden"-register.
            ByteBufCodecs.VAR_INT.map(Block::stateById, Block::getId), BlockElement::state,
            BlockElement::new);

    private static final StreamCodec<ByteBuf, MobElement> MOB_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, MobElement::x,
            ByteBufCodecs.DOUBLE, MobElement::y,
            ByteBufCodecs.DOUBLE, MobElement::z,
            ByteBufCodecs.VAR_INT.map(id -> BuiltInRegistries.ENTITY_TYPE.byId(id), BuiltInRegistries.ENTITY_TYPE::getId),
            MobElement::type,
            ByteBufCodecs.FLOAT, MobElement::yaw,
            MobElement::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, IllusionScene> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Kind.values()[Math.floorMod(i, Kind.values().length)], Kind::ordinal),
            IllusionScene::kind,
            BLOCK_CODEC.apply(ByteBufCodecs.list(1024)), IllusionScene::blocks,
            MOB_CODEC.apply(ByteBufCodecs.list(64)), IllusionScene::mobs,
            IllusionScene::new);

    public IllusionScene {
        blocks = List.copyOf(blocks);
        mobs = List.copyOf(mobs);
    }

    /** Hoeveel "dingen" er in deze scène zitten (voor de HUD en de meldingen). */
    public int size() {
        return blocks.size() + mobs.size();
    }

    // ---------------------------------------------------------------- Scènes maken

    /** Een valse muur: breed x hoog, vlak voor de drone, van één bloksoort. */
    public static IllusionScene wall(BlockState state, int width, int height) {
        List<BlockElement> blocks = new ArrayList<>();
        for (int x = -width / 2; x <= width / 2; x++) {
            for (int y = 0; y < height; y++) {
                blocks.add(new BlockElement(new BlockPos(x, y - 1, 0), state));
            }
        }
        return new IllusionScene(Kind.WALL, blocks, List.of());
    }

    /** Een nep-leger: count wezens in een kring rond de drone. */
    public static IllusionScene army(EntityType<?> type, int count, double radius) {
        List<MobElement> mobs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = i * (Math.PI * 2 / count);
            mobs.add(new MobElement(Math.cos(angle) * radius, -2.0, Math.sin(angle) * radius, type,
                    (float) Math.toDegrees(-angle) + 90.0f));
        }
        return new IllusionScene(Kind.ARMY, List.of(), mobs);
    }

    /** Een kopie van een stuk wereld. */
    public static IllusionScene copy(List<BlockElement> blocks) {
        return new IllusionScene(Kind.COPY, blocks, List.of());
    }
}
