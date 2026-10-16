package com.senne.starktech.bot;

import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.logic.program.ProgramKind;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Wat een guard robot en een programmeerbare security drone delen (hoofdstuk 25): een printplaat met modules, een
 * batterij, een thuis (zijn dock), een eigenaar, aan en uit, neer (de Ion Blaster), en een brein dat zijn
 * programma draait. Het brein (BotBrain), zijn pinnen (BotPins), wat hij ziet (BotSense), zijn schoten (BotShot)
 * en zijn verbinding (BotLink) werken met elke Bot; alleen hoe hij beweegt (lopen of vliegen) verschilt (BotBody).
 *
 * De methodes bovenaan heeft elk wezen in Minecraft al (een robot en een drone zijn allebei een "mob"); we zetten
 * ze hier alleen bij, zodat de code hierboven ze kan gebruiken.
 */
public interface Bot {
    // ---------------------------------------------------------------- Wat elk wezen al heeft
    Level level();

    UUID getUUID();

    int getId();

    BlockPos blockPosition();

    Vec3 position();

    Vec3 getEyePosition();

    float getYRot();

    float getYHeadRot();

    float getXRot();

    Component getName();

    boolean hasCustomName();

    boolean isAlive();

    boolean hasLineOfSight(Entity entity);

    RandomSource getRandom();

    void playSound(SoundEvent sound, float volume, float pitch);

    PathNavigation getNavigation();

    /** Hijzelf als wezen (een Bot is altijd een wezen). */
    default Entity entity() {
        return (Entity) this;
    }

    /** Hoe het met hem gaat: aan, uit, geen stroom, neer. */
    com.senne.starktech.robot.RobotState state();

    /** Hoe vol zijn batterij is, van 0 tot 1. */
    float batteryFraction();

    // ---------------------------------------------------------------- Wat een Bot is
    /** Wat voor programma hij draait: een robotprogramma of een droneprogramma. */
    ProgramKind programKind();

    /** Zijn printplaat: wat er op zijn pinnen gesoldeerd is. */
    BotBoard board();

    void setBoard(BotBoard board);

    /** Hoeveel stroom er in zijn batterij zit, en hoeveel er hoogstens in past. */
    double battery();

    double maxBattery();

    void setBattery(double energy);

    /** Staat hij aan? Aanzetten met een soldeerbrug geeft kortsluiting: dan blijft hij uit en geeft dit false. */
    boolean isActive();

    boolean setActive(boolean on);

    /** Ligt hij neer (de Ion Blaster)? */
    boolean isDown();

    /** Zijn dock, of null. */
    @Nullable BlockPos home();

    @Nullable UUID owner();

    String ownerName();

    /** Zijn brein: het programma dat hij draait. */
    BotBrain brain();

    default @Nullable LogicProgram program() {
        return brain().program();
    }

    default void setProgram(@Nullable LogicProgram program) {
        brain().setProgram(program);
    }

    /** Zijn squad (hoofdstuk 26: "Wave 1"; leeg = geen): activate("Wave 1") zet ze allemaal tegelijk aan. */
    String squad();

    /** Is hij nog uit zijn dock aan het stappen (of opstijgen)? Dan begint zijn programma pas daarna. */
    boolean isLeavingDock();

    /** Hoeveel Robot Ammo er in zijn geweer zit. */
    int ammo();

    void setAmmo(int ammo);

    /** Hoeveel zon er op zijn zonnepaneel valt, van 0 tot 1. */
    float sunlight(ServerLevel level);

    /** Hoe ver en hoe breed hij kijkt (zijn Scan), en hoeveel ticks tussen twee schoten. */
    double scanRange();

    double scanAngle();

    double fireInterval();

    /**
     * Waar een schot uit zijn wapen komt (de laser rechts, het geweer links bij een robot; onder zijn buik bij een
     * drone), als hij kijkt in de richting {@code forward}.
     */
    Vec3 muzzle(boolean laser, Vec3 forward);

    /** Hoe hoog (boven zijn voeten) zijn afstandssensor zit: bij een robot op heuphoogte. */
    default double sensorHeight() {
        return 0.9;
    }
}
