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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Een "scène": het beeld dat een cloakingdrone projecteert.
 *
 * Belangrijk: alles hierin is NEP. De server rekent de scène één keer uit en stuurt hem naar de
 * spelers in de buurt; hun spel tekent het erbij. In de echte wereld verandert niets, dus als de
 * drone wordt neergehaald is het beeld ook meteen weg (en niemand heeft blokken verloren).
 *
 * Alles staat ten opzichte van een ANKERPUNT: het eerste stukje lucht boven de grond, onder de
 * plek waar je de illusie neerzette (zie {@link #groundBelow}). Een blok met offset (0, 0, 0)
 * staat dus precies op de grond. Nep-wezens zet je spel elk op zijn eigen stukje grond, zodat
 * ze op een heuvel niet half in de grond staan.
 *
 * @param kind   wat voor illusie het is
 * @param blocks nep-blokken, met hun plek ten opzichte van het ankerpunt
 * @param mobs   nep-wezens, met hun plek ten opzichte van het ankerpunt (de hoogte zoekt je
 *               spel zelf: op de grond)
 */
public record IllusionScene(Kind kind, List<BlockElement> blocks, List<MobElement> mobs) {
    /** De soorten illusies die E.D.I.T.H. kan projecteren. */
    public enum Kind {
        /** Een valse muur voor de drone. */
        WALL,
        /** Een nep-leger van wezens rond de drone. */
        ARMY,
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

    /** Een valse muur: breed x hoog, van één bloksoort, van oost naar west op de grond. */
    public static IllusionScene wall(BlockState state, int width, int height) {
        List<BlockElement> blocks = new ArrayList<>();
        for (int x = -width / 2; x <= width / 2; x++) {
            for (int y = 0; y < height; y++) {
                blocks.add(new BlockElement(new BlockPos(x, y, 0), state));
            }
        }
        return new IllusionScene(Kind.WALL, blocks, List.of());
    }

    /** Een nep-leger: count wezens in een kring rond het ankerpunt, met hun gezicht naar buiten. */
    public static IllusionScene army(EntityType<?> type, int count, double radius) {
        List<MobElement> mobs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = i * (Math.PI * 2 / count);
            mobs.add(new MobElement(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius, type,
                    (float) Math.toDegrees(-angle) + 90.0f));
        }
        return new IllusionScene(Kind.ARMY, List.of(), mobs);
    }

    /**
     * Draait de scène zo dat een muur dwars op je kijkrichting staat (je kijkt er recht tegenaan).
     * Kijk je vooral naar het oosten of westen, dan loopt de muur van noord naar zuid. Wezens in
     * een kring hoeven niet te draaien.
     */
    public IllusionScene facing(float yaw) {
        double radians = Math.toRadians(yaw);
        boolean lookingAlongX = Math.abs(Math.sin(radians)) > Math.abs(Math.cos(radians));
        if (!lookingAlongX || blocks.isEmpty()) {
            return this;
        }
        List<BlockElement> turned = new ArrayList<>();
        for (BlockElement block : blocks) {
            BlockPos o = block.offset();
            turned.add(new BlockElement(new BlockPos(o.getZ(), o.getY(), o.getX()), block.state()));
        }
        return new IllusionScene(kind, turned, mobs);
    }

    /** Tot hoe ver je met de drone een plek kunt aanwijzen (in blokken). */
    public static final double AIM_RANGE = 40.0;

    /**
     * Waar je met de drone naar kijkt: daar komt het beeld, op de grond. We volgen de kijklijn
     * vanaf de drone tot hij iets raakt (of tot AIM_RANGE blokken ver), en zakken daar naar de
     * grond. De server en je eigen spel rekenen dit op dezelfde manier uit, zodat het beeld komt
     * waar de voorvertoning stond.
     */
    public static BlockPos aimAnchor(Level level, Vec3 eye, float yaw, float pitch) {
        Vec3 end = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(AIM_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 spot = hit.getType() == HitResult.Type.MISS ? end
                : Vec3.atCenterOf(hit.getBlockPos().relative(hit.getDirection()));
        return groundBelow(level, spot);
    }

    /** Hoe hoog het beeld is (in blokken): de muur, of twee blokken voor wezens. */
    public int height() {
        int top = mobs.isEmpty() ? 0 : 2;
        for (BlockElement block : blocks) {
            top = Math.max(top, block.offset().getY() + 1);
        }
        return top;
    }

    /**
     * Het ankerpunt onder een plek: we zakken recht naar beneden tot we op iets vasts staan, en
     * nemen het stukje lucht daar net boven. Is er binnen 48 blokken geen grond, dan de plek zelf.
     */
    public static BlockPos groundBelow(BlockGetter level, Vec3 from) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(from).mutable();
        for (int i = 0; i < 48; i++) {
            BlockPos below = pos.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return pos.immutable();
            }
            pos.move(0, -1, 0);
        }
        return BlockPos.containing(from);
    }

    /**
     * Op welke hoogte een nep-wezen moet staan: de grond onder (x, z), gezocht vanaf een paar
     * blokken boven het ankerpunt tot een eind eronder. Zo staan ze op een heuvel niet in de grond
     * en in een kuil niet in de lucht.
     */
    public static double feetY(BlockGetter level, double x, double z, int anchorY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Math.floor(x), anchorY + 4, Math.floor(z));
        for (int i = 0; i < 16; i++) {
            BlockPos below = pos.below();
            var shape = level.getBlockState(below).getCollisionShape(level, below);
            if (!shape.isEmpty()) {
                return below.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
            pos.move(0, -1, 0);
        }
        return anchorY;
    }
}
