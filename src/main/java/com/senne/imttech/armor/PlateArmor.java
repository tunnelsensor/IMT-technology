package com.senne.imttech.armor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Wat de plates tegenhouden (hoofdstuk 109). Jouw wens: "hoe sterker de plate, hoe minder damage je neemt"; jouw keuzes:
 * een plate voor en een achter, ze slijten per treffer, en het vest komt over je borstplaat (de bescherming telt op).
 * <ul>
 *   <li><b>Welke plate</b>: komt de treffer van voren (de schutter, de klap of de ontploffing ligt voor je lijf), dan de
 *       voorste; van achteren de achterste. Iemand die je van achteren besluipt, krijgt dus met je achterste plate te maken,
 *       en zonder achterste plate met niets.</li>
 *   <li><b>Een headshot</b> gaat langs de plates (een kogel of laserstraal weet waar hij raakte, zie HitSource; een pijl die
 *       boven je ogen min een stukje binnenkomt ook).</li>
 *   <li><b>Wat telt</b>: alles wat een richting heeft en niet door harnas heen gaat: kogels, lasers, pijlen, klappen,
 *       ontploffingen. Vallen, vuur, lava, verdrinken, honger, magie en de leegte niet.</li>
 *   <li><b>Hoeveel</b>: de plate houdt zijn deel van de treffer tegen ({@code [armor] plateReduction}), nadat je gewone
 *       harnas zijn deel deed; dat tegengehouden deel kost de plate duurzaamheid. Een gebarsten plate houdt niets tegen.</li>
 * </ul>
 * Je hoort het: een doffe metalen tik als een plate een treffer opvangt (de schutter hoort hem ook), en een krak als hij
 * barst; dan zegt een regel boven je werkbalk ook welke plate het was.
 */
public final class PlateArmor {
    private PlateArmor() {}

    /** Van welke kant een treffer komt, of null als de plates hem niet opvangen. */
    public enum Side {
        FRONT, BACK;

        public boolean front() {
            return this == FRONT;
        }
    }

    /**
     * Waar de ontploffing van deze tick zat, per wezen dat ze raakte (ExplosionEvent.Detonate komt vlak voor de schade). Een
     * ontploffing zonder iemand erachter (een Remote Frag, TNT) geeft Minecraft geen plek mee in de schade; zo weten we toch
     * van welke kant ze kwam.
     */
    private static final java.util.Map<java.util.UUID, Vec3> BLASTS = new java.util.HashMap<>();
    private static long blastTick = -1;

    /** ExplosionEvent.Detonate: de plek van de ontploffing onthouden voor de wezens die ze raakt (alleen deze tick). */
    public static void onDetonate(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() != blastTick) {
            BLASTS.clear();
            blastTick = level.getGameTime();
        }
        Vec3 centre = event.getExplosion().center();
        for (net.minecraft.world.entity.Entity entity : event.getAffectedEntities()) {
            BLASTS.put(entity.getUUID(), centre);
        }
    }

    /** LivingDamageEvent.Pre: na het gewone harnas, vlak voor je hartjes eraf gaan. */
    public static void onDamage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        float amount = event.getNewDamage();
        if (amount <= 0) {
            return;
        }
        ItemStack vest = Vests.worn(victim);
        if (!VestItem.isVest(vest)) {
            return;
        }
        Side side = side(victim, event.getSource());
        if (side == null) {
            return;
        }
        VestData data = VestItem.data(vest);
        ItemStack plate = data.plate(side.front());
        PlateTier tier = PlateItem.tierOf(plate);
        if (tier == null || PlateItem.broken(plate)) {
            return;
        }
        // Alleen het deel op het lijf (bij een laser shotgun die ook het hoofd raakte, niet de stralen in het hoofd).
        float share = event.getSource() instanceof HitSource hit ? hit.bodyShare() : 1.0f;
        float absorbed = amount * share * tier.reduction();
        event.setNewDamage(amount - absorbed);
        ItemStack worn = PlateItem.wear(plate, absorbed);
        boolean cracked = PlateItem.broken(worn);
        Vests.setWorn(victim, VestItem.with(vest, data.withPlate(side.front(), worn)));
        // Een tik: metaal klinkt hoger en helderder dan polymeer.
        float pitch = switch (tier) {
            case POLYMER -> 0.75f;
            case STEEL -> 1.0f;
            case TITANIUM -> 1.12f;
            case NETHERITE -> 0.9f;
        };
        level.playSound(null, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), ArmorRegistry.PLATE_HIT.get(),
                SoundSource.PLAYERS, 0.9f, pitch + victim.getRandom().nextFloat() * 0.1f);
        if (cracked) {
            level.playSound(null, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), ArmorRegistry.PLATE_CRACK.get(),
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            if (victim instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable(side.front() ? "vest.imttech.front_cracked" : "vest.imttech.back_cracked")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /**
     * Welke plate deze treffer opvangt (de voorste of de achterste), of null: als hij door harnas heen gaat (vallen,
     * verdrinken, magie, de leegte), geen richting heeft (vuur, lava) of in het hoofd zat.
     */
    public static @Nullable Side side(LivingEntity victim, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return null;
        }
        // Een straal van de satelliet komt van recht boven: daar zit geen plate.
        if (source.is(com.senne.imttech.registry.ModDamageTypes.ORBITAL_LASER)) {
            return null;
        }
        if (head(victim, source)) {
            return null;
        }
        Vec3 from = source.getSourcePosition();
        if (from == null && source.is(DamageTypeTags.IS_EXPLOSION) && victim.level().getGameTime() == blastTick) {
            from = BLASTS.get(victim.getUUID());
        }
        if (from == null) {
            return null;
        }
        return inFront(victim, from) ? Side.FRONT : Side.BACK;
    }

    /**
     * Zat de treffer in het hoofd? Een kogel of laser weet het zelf. Bij een pijl of drietand rekenen we uit waar zijn baan je
     * klikdoos raakt (als de schade komt, staat hij nog waar hij de tick ervoor was, soms ruim boven je hoofd). Een
     * ontploffing is nooit een headshot, ook niet als de granaat hoger lag.
     */
    static boolean head(LivingEntity victim, DamageSource source) {
        if (source instanceof HitSource hit) {
            return hit.head();
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION) || !(source.getDirectEntity() instanceof Projectile projectile)) {
            return false;
        }
        Vec3 from = projectile.position();
        Vec3 to = from.add(projectile.getDeltaMovement());
        Vec3 at = victim.getBoundingBox().inflate(0.1).clip(from, to).orElse(from);
        return at.y >= victim.getEyeY() - 0.25;
    }

    /**
     * Ligt dit punt voor het lijf van dit wezen (de kant waar zijn borst naar wijst)? Precies opzij telt als voor; recht boven
     * of onder hem ook.
     */
    public static boolean inFront(LivingEntity victim, Vec3 point) {
        double dx = point.x - victim.getX();
        double dz = point.z - victim.getZ();
        if (dx * dx + dz * dz < 1.0e-6) {
            return true;
        }
        float yaw = victim.yBodyRot * Mth.DEG_TO_RAD;
        // Minecraft: met draai 0 kijk je naar het zuiden (+z), met 90 naar het westen (-x).
        double facingX = -Mth.sin(yaw);
        double facingZ = Mth.cos(yaw);
        return facingX * dx + facingZ * dz >= 0;
    }
}
