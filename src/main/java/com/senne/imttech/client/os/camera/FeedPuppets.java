package com.senne.imttech.client.os.camera;

import com.mojang.authlib.GameProfile;
import com.senne.imttech.security.feed.FeedEntities;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De "poppen" van de camerabeelden (hoofdstuk 94): wie er rondloopt bij een camera die te ver weg is om zelf te kennen.
 *
 * Je spel kent alleen de spelers, dieren en monsters in de buurt van jou. Kijk je met een camera naar een plek ver weg,
 * dan vertelt de server tien keer per seconde wie daar rondloopt (FeedEntities): welke soort, waar, welke kant op, en af en
 * toe hoe het eruitziet. Daarvan maken we hier een pop: een echt wezen van dezelfde soort (een speler met zijn skin, een
 * zombie, een robot), maar niet in je wereld: het doet niets en denkt niets, we zetten het alleen neer waar de server zegt
 * en laten zijn benen meelopen. FeedRenderer tekent de pop alleen als je spel het echte wezen niet kent (dan tekent het
 * het echte, dat al vloeiend beweegt).
 *
 * Tussen twee berichten (twee ticks) schuift een pop rustig naar zijn nieuwe plek. Hoort de server er anderhalve seconde
 * niets meer over (weg uit beeld, of dood), dan ruimen we hem op.
 */
final class FeedPuppets {
    private FeedPuppets() {}

    /** Zo lang (ticks) zonder nieuws blijft een pop nog staan. */
    private static final int FORGET_AFTER = 30;
    /** In zoveel ticks schuift een pop naar zijn nieuwe plek (de server stuurt elke 2 ticks). */
    private static final int STEPS = 3;

    private static final class Puppet {
        final Entity entity;
        final int type;
        double x;
        double y;
        double z;
        float yRot;
        float xRot;
        float headRot;
        float bodyRot;
        int steps;
        long lastSeen;

        Puppet(Entity entity, int type) {
            this.entity = entity;
            this.type = type;
        }
    }

    private static final Int2ObjectOpenHashMap<Puppet> PUPPETS = new Int2ObjectOpenHashMap<>();
    private static long tick;

    /** Nieuws van de server over de wezens rond de camera's. */
    static void update(List<FeedEntities.EntityState> states) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (FeedEntities.EntityState state : states) {
            Puppet puppet = PUPPETS.get(state.id());
            if (puppet != null && puppet.type != state.type()) {
                PUPPETS.remove(state.id());
                puppet = null;
            }
            if (puppet == null) {
                if (state.look() == null) {
                    continue; // eerst moeten we weten hoe het eruitziet (dat komt meteen bij het eerste bericht)
                }
                Entity entity = create(mc, state);
                if (entity == null) {
                    continue;
                }
                puppet = new Puppet(entity, state.type());
                entity.setId(state.id());
                entity.moveTo(state.pos().x, state.pos().y, state.pos().z, state.yRot(), state.xRot());
                entity.setOldPosAndRot();
                if (entity instanceof LivingEntity living) {
                    living.yHeadRot = living.yHeadRotO = state.headRot();
                    living.yBodyRot = living.yBodyRotO = state.bodyRot();
                }
                PUPPETS.put(state.id(), puppet);
            }
            apply(puppet, state);
        }
    }

    /** Een nieuwe pop: een speler met zijn eigen profiel (zijn skin), of een wezen van dezelfde soort. */
    private static @Nullable Entity create(Minecraft mc, FeedEntities.EntityState state) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.byId(state.type());
        FeedEntities.Look look = state.look();
        try {
            if (type == EntityType.PLAYER) {
                if (look == null || look.uuid() == null) {
                    return null;
                }
                UUID uuid = look.uuid();
                PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(uuid);
                GameProfile profile = info != null ? info.getProfile() : new GameProfile(uuid, look.name());
                return new RemotePlayer(mc.level, profile);
            }
            return type.create(mc.level);
        } catch (RuntimeException e) {
            return null; // een wezen van een andere mod dat zo niet gemaakt wil worden
        }
    }

    /** Wat de server zei op de pop zetten: hoe het eruitziet (als dat erbij zat), en waar het heen moet. */
    private static void apply(Puppet puppet, FeedEntities.EntityState state) {
        Entity entity = puppet.entity;
        FeedEntities.Look look = state.look();
        if (look != null) {
            try {
                entity.getEntityData().assignValues(look.data());
            } catch (RuntimeException e) {
                // gegevens die niet bij deze pop passen: dan ziet hij er gewoon uit zoals hij al was
            }
            if (look.equipment() != null && entity instanceof LivingEntity living) {
                EquipmentSlot[] slots = EquipmentSlot.values();
                for (int i = 0; i < slots.length && i < look.equipment().length; i++) {
                    ItemStack stack = look.equipment()[i];
                    try {
                        living.setItemSlot(slots[i], stack == null ? ItemStack.EMPTY : stack);
                    } catch (RuntimeException e) {
                        // dit ene vak niet
                    }
                }
            }
        }
        Pose[] poses = Pose.values();
        if (state.pose() >= 0 && state.pose() < poses.length) {
            entity.setPose(poses[state.pose()]);
        }
        entity.setShiftKeyDown(state.crouching());
        entity.setSprinting(state.sprinting());
        entity.setSwimming(state.swimming());
        entity.setInvisible(state.invisible());
        entity.setGlowingTag(state.glowing());
        puppet.x = state.pos().x;
        puppet.y = state.pos().y;
        puppet.z = state.pos().z;
        puppet.yRot = state.yRot();
        puppet.xRot = state.xRot();
        puppet.headRot = state.headRot();
        puppet.bodyRot = state.bodyRot();
        puppet.steps = STEPS;
        puppet.lastSeen = tick;
    }

    /** Elke tick (in je spel): de poppen een stukje verder schuiven, hun benen laten meelopen, en oude opruimen. */
    static void tick() {
        tick++;
        if (PUPPETS.isEmpty()) {
            return;
        }
        PUPPETS.values().removeIf(puppet -> tick - puppet.lastSeen > FORGET_AFTER);
        for (Puppet puppet : PUPPETS.values()) {
            Entity entity = puppet.entity;
            double oldX = entity.getX();
            double oldZ = entity.getZ();
            entity.setOldPosAndRot();
            LivingEntity living = entity instanceof LivingEntity l ? l : null;
            if (living != null) {
                living.yHeadRotO = living.yHeadRot;
                living.yBodyRotO = living.yBodyRot;
            }
            if (puppet.steps > 0) {
                int steps = puppet.steps;
                entity.setPos(entity.getX() + (puppet.x - entity.getX()) / steps, entity.getY() + (puppet.y - entity.getY()) / steps,
                        entity.getZ() + (puppet.z - entity.getZ()) / steps);
                entity.setYRot(entity.getYRot() + Mth.wrapDegrees(puppet.yRot - entity.getYRot()) / steps);
                entity.setXRot(entity.getXRot() + (puppet.xRot - entity.getXRot()) / steps);
                if (living != null) {
                    living.yHeadRot += Mth.wrapDegrees(puppet.headRot - living.yHeadRot) / steps;
                    living.yBodyRot += Mth.wrapDegrees(puppet.bodyRot - living.yBodyRot) / steps;
                }
                puppet.steps--;
            }
            if (living != null) {
                // Zoals Minecraft zelf (LivingEntity.calculateEntityAnimation): hoe verder hij deze tick ging, hoe meer zijn
                // benen bewegen.
                double dx = entity.getX() - oldX;
                double dz = entity.getZ() - oldZ;
                living.walkAnimation.update(Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0f, 1.0f), 0.4f);
            }
            entity.tickCount++;
        }
    }

    /** De poppen binnen dit bereik van deze plek. */
    static List<Entity> near(Vec3 eye, double distance) {
        List<Entity> found = new ArrayList<>();
        double reach = distance * distance;
        for (Puppet puppet : PUPPETS.values()) {
            if (puppet.entity.distanceToSqr(eye) <= reach) {
                found.add(puppet.entity);
            }
        }
        return found;
    }

    /** Hoeveel poppen er nu zijn (voor de tests en het logboek). */
    static int size() {
        return PUPPETS.size();
    }

    static void clear() {
        PUPPETS.clear();
    }
}
