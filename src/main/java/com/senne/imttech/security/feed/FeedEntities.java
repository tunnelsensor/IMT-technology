package com.senne.imttech.security.feed;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Het tweede bericht van de camerabeelden (hoofdstuk 94, CameraFeedPayload), tien keer per seconde: per camera of je hem
 * mag zien en waar hij heen kijkt, en wie er rondloopt. De server schrijft ({@link #writeSource}, {@link #writeEntity}),
 * je spel leest ({@link #read}).
 *
 * Een wezen gaat er altijd mee met waar het is en welke kant het op kijkt, en af en toe ("volledig") ook hoe het eruitziet:
 * zijn gegevens zoals Minecraft ze zelf naar je spel stuurt (de kleur van een schaap, de skin-instellingen van een speler,
 * of het brandt...) en wat het vasthoudt en draagt. Elk wezen staat in zijn eigen pakketje met zijn lengte ervoor: lukt het
 * lezen van één wezen niet (een wezen van een andere mod met iets vreemds), dan slaan we alleen dat ene over.
 */
public final class FeedEntities {
    private FeedEntities() {}

    /** Wat de server over een camera zegt. */
    public static final byte OK = 0;
    public static final byte OFFLINE = 1;
    public static final byte NO_ACCESS = 2;
    public static final byte GONE = 3;

    /** De vlaggetjes van een wezen (sluipen, rennen, zwemmen, onzichtbaar, oplichten, vliegen met elytra). */
    private static final int CROUCH = 1;
    private static final int SPRINT = 2;
    private static final int SWIM = 4;
    private static final int INVISIBLE = 8;
    private static final int GLOWING = 16;
    private static final int ELYTRA = 32;

    /** Een camera (of robot) zoals de server hem doorgeeft: wat er mag, en (als hij er is) waar hij kijkt. */
    public record SourceState(boolean robot, long id, byte status, @Nullable Vec3 eye, float yaw, float pitch) {}

    /** Een wezen zoals de server het doorgeeft. {@code look} is alleen er bij een volledige beschrijving. */
    public record EntityState(int id, int type, Vec3 pos, float yRot, float xRot, float headRot, float bodyRot, int pose,
                              int flags, @Nullable Look look) {
        public boolean crouching() {
            return (flags & CROUCH) != 0;
        }

        public boolean sprinting() {
            return (flags & SPRINT) != 0;
        }

        public boolean swimming() {
            return (flags & SWIM) != 0;
        }

        public boolean invisible() {
            return (flags & INVISIBLE) != 0;
        }

        public boolean glowing() {
            return (flags & GLOWING) != 0;
        }

        public boolean elytra() {
            return (flags & ELYTRA) != 0;
        }
    }

    /**
     * Hoe een wezen eruitziet: voor een speler wie het is ({@code uuid}, {@code name}, voor zijn skin), zijn gegevens, en
     * (voor een levend wezen) wat het in zijn handen heeft en draagt, in de volgorde van EquipmentSlot.
     */
    public record Look(@Nullable UUID uuid, String name, List<SynchedEntityData.DataValue<?>> data, @Nullable ItemStack[] equipment) {}

    /** Alles uit één bericht. */
    public record Feed(List<SourceState> sources, List<EntityState> entities) {}

    // ================================================================ Schrijven (op de server)

    public static void writeSource(RegistryFriendlyByteBuf buf, boolean robot, long id, byte status, @Nullable Vec3 eye,
                                   float yaw, float pitch) {
        buf.writeBoolean(robot);
        if (robot) {
            buf.writeVarInt((int) id);
        } else {
            buf.writeLong(id);
        }
        buf.writeByte(status);
        boolean hasEye = eye != null;
        buf.writeBoolean(hasEye);
        if (hasEye) {
            buf.writeDouble(eye.x);
            buf.writeDouble(eye.y);
            buf.writeDouble(eye.z);
            buf.writeFloat(yaw);
            buf.writeFloat(pitch);
        }
    }

    /**
     * Schrijft één wezen, in een eigen pakketje met zijn lengte ervoor. {@code full}: ook hoe het eruitziet. Lukt het niet
     * (een wezen van een andere mod met gegevens die niet mee willen), dan schrijven we niets en geeft dit false.
     */
    public static boolean writeEntity(RegistryFriendlyByteBuf buf, Entity entity, boolean full) {
        RegistryFriendlyByteBuf one = new RegistryFriendlyByteBuf(Unpooled.buffer(), buf.registryAccess());
        try {
            one.writeVarInt(entity.getId());
            one.writeVarInt(BuiltInRegistries.ENTITY_TYPE.getId(entity.getType()));
            one.writeDouble(entity.getX());
            one.writeDouble(entity.getY());
            one.writeDouble(entity.getZ());
            one.writeFloat(entity.getYRot());
            one.writeFloat(entity.getXRot());
            one.writeFloat(entity.getYHeadRot());
            one.writeFloat(entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot());
            one.writeByte(entity.getPose().ordinal());
            one.writeByte(flags(entity));
            one.writeBoolean(full);
            if (full) {
                boolean player = entity instanceof Player;
                one.writeBoolean(player);
                if (entity instanceof Player p) {
                    one.writeUUID(p.getUUID());
                    one.writeUtf(p.getGameProfile().getName(), 16);
                }
                List<SynchedEntityData.DataValue<?>> values = entity.getEntityData().getNonDefaultValues();
                if (values != null) {
                    for (SynchedEntityData.DataValue<?> value : values) {
                        value.write(one);
                    }
                }
                one.writeByte(255);
                boolean living = entity instanceof LivingEntity;
                one.writeBoolean(living);
                if (entity instanceof LivingEntity l) {
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(one, l.getItemBySlot(slot));
                    }
                }
            }
        } catch (RuntimeException e) {
            one.release();
            return false;
        }
        buf.writeVarInt(one.readableBytes());
        buf.writeBytes(one);
        one.release();
        return true;
    }

    private static int flags(Entity entity) {
        int flags = 0;
        if (entity.isShiftKeyDown()) {
            flags |= CROUCH;
        }
        if (entity.isSprinting()) {
            flags |= SPRINT;
        }
        if (entity.isSwimming()) {
            flags |= SWIM;
        }
        if (entity.isInvisible()) {
            flags |= INVISIBLE;
        }
        if (entity.hasGlowingTag()) {
            flags |= GLOWING;
        }
        if (entity instanceof LivingEntity living && living.isFallFlying()) {
            flags |= ELYTRA;
        }
        return flags;
    }

    // ================================================================ Lezen (in je spel)

    /** Leest een heel bericht. Een wezen dat niet te lezen is, slaan we over. */
    public static Feed read(RegistryFriendlyByteBuf buf) {
        int sourceCount = buf.readVarInt();
        List<SourceState> sources = new ArrayList<>(sourceCount);
        for (int i = 0; i < sourceCount; i++) {
            boolean robot = buf.readBoolean();
            long id = robot ? buf.readVarInt() : buf.readLong();
            byte status = buf.readByte();
            Vec3 eye = null;
            float yaw = 0;
            float pitch = 0;
            if (buf.readBoolean()) {
                eye = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                yaw = buf.readFloat();
                pitch = buf.readFloat();
            }
            sources.add(new SourceState(robot, id, status, eye, yaw, pitch));
        }
        int entityCount = buf.readVarInt();
        List<EntityState> entities = new ArrayList<>(entityCount);
        for (int i = 0; i < entityCount; i++) {
            int length = buf.readVarInt();
            int start = buf.readerIndex();
            try {
                entities.add(readEntity(buf));
            } catch (RuntimeException e) {
                // dit ene wezen niet: verder met het volgende
            }
            buf.readerIndex(start + length);
        }
        return new Feed(sources, entities);
    }

    private static EntityState readEntity(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int type = buf.readVarInt();
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float yRot = buf.readFloat();
        float xRot = buf.readFloat();
        float headRot = buf.readFloat();
        float bodyRot = buf.readFloat();
        int pose = buf.readUnsignedByte();
        int flags = buf.readUnsignedByte();
        Look look = null;
        if (buf.readBoolean()) {
            UUID uuid = null;
            String name = "";
            if (buf.readBoolean()) {
                uuid = buf.readUUID();
                name = buf.readUtf(16);
            }
            List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
            for (int at = buf.readUnsignedByte(); at != 255; at = buf.readUnsignedByte()) {
                data.add(SynchedEntityData.DataValue.read(buf, at));
            }
            ItemStack[] equipment = null;
            if (buf.readBoolean()) {
                equipment = new ItemStack[EquipmentSlot.values().length];
                for (int slot = 0; slot < equipment.length; slot++) {
                    equipment[slot] = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                }
            }
            look = new Look(uuid, name, data, equipment);
        }
        return new EntityState(id, type, pos, yRot, xRot, headRot, bodyRot, pose, flags, look);
    }

    /** De plek van een camera uit zijn nummer (voor wie het leest). */
    public static BlockPos cameraPos(long id) {
        return BlockPos.of(id);
    }
}
