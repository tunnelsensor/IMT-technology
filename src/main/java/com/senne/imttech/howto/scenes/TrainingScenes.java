package com.senne.imttech.howto.scenes;

import com.senne.imttech.bot.BotBoard;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.logic.RackBay;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.robot.RobotStyle;
import com.senne.imttech.training.TrainingEndBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * De training (hoofdstuk 63, de robots): je eigen robots schieten met trainingsschoten op jou. De drie blokken, het
 * instelscherm, aftellen, een treffer (de blauwe flits, het lampje van Training End), squads en golven, het doel en het
 * rapport, en de oefeningen. Hoe het echt werkt staat in LEESMIJ ("Training zoals Rookie squad").
 */
public final class TrainingScenes {
    private TrainingScenes() {}

    /** De blauwe flits rond de trainee bij een treffer met trainingsschoten. */
    private static final int HIT_BLUE = 0x5AA9FF;

    public static List<HowToScene> training() {
        HowToSceneBuilder s = new HowToSceneBuilder("training", "Training").plate(9, 8).height(3);
        BlockPos gen = new BlockPos(0, 1, 0);
        BlockPos core = new BlockPos(1, 1, 0);
        BlockPos rack = new BlockPos(2, 1, 0);
        BlockPos dock1 = new BlockPos(5, 1, 0);
        BlockPos solar = new BlockPos(6, 1, 0);
        BlockPos dock2 = new BlockPos(7, 1, 0);
        // De blokken en de trainee onderaan in het midden van het beeld, de robots bovenaan, het doel rechts: zo staan de
        // tekstballonnen (links) nergens voor.
        BlockPos start = new BlockPos(2, 1, 6);
        BlockPos end = new BlockPos(1, 1, 7);
        BlockPos lamp = new BlockPos(0, 1, 7);
        BlockPos goal = new BlockPos(7, 1, 5);
        Vec3 home1 = new Vec3(5.5, 1 + RobotScenes.DOCK_FLOOR, 0.5);
        Vec3 home2 = new Vec3(7.5, 1 + RobotScenes.DOCK_FLOOR, 0.5);
        Vec3 out1 = new Vec3(5.5, 1.0, 2.5);
        Vec3 out2 = new Vec3(7.5, 1.0, 2.5);
        BotBoard board = RobotScenes.board("scan", "face_recognition", "laser");
        CompoundTag off = RobotScenes.robot(RobotStyle.TRAINER, board, false);
        CompoundTag on = RobotScenes.robot(RobotStyle.TRAINER, board, true);
        s.idle(6);
        Map<BlockPos, BlockState> base = new LinkedHashMap<>();
        base.put(gen, SecurityScenes.heat(Direction.SOUTH));
        base.put(core, SecurityScenes.core(Direction.SOUTH, true));
        base.put(rack, DatacenterScenes.rack(true, RackBay.CPU, RackBay.CPU, RackBay.RAM, RackBay.SSD, RackBay.FAN, RackBay.FAN,
                RackBay.FAN, RackBay.FAN));
        base.put(dock1, RobotScenes.dock(Direction.SOUTH));
        base.put(solar, RobotScenes.solar());
        base.put(dock2, RobotScenes.dock(Direction.SOUTH));
        s.place(base, HowToSceneBuilder.DROP);
        s.idle(8);
        s.actor("r1", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 0.0f, off, home1);
        s.actor("r2", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 0.0f, off, home2);
        s.idle(6);

        s.chapter("Three blocks");
        s.place(start, ModBlocks.TRAINING_START.get().defaultBlockState());
        s.idle(6);
        s.place(end, ModBlocks.TRAINING_END.get().defaultBlockState());
        s.idle(4);
        s.place(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        s.idle(6);
        s.place(goal, ModBlocks.TRAINING_GOAL.get().defaultBlockState());
        s.idle(8);
        s.text(110, HowToSceneBuilder.top(start), Side.LEFT,
                "Let your own defence shoot at you, with *training shots* that do no damage. *Training Start* begins it,"
                        + " *Training Goal* is the finish, and *Training End* gives a pulse on every hit. Put them in your"
                        + " core's area.");
        s.idle(115);

        s.chapter("Settings");
        s.sneakRightClick(HowToSceneBuilder.top(start), ItemStack.EMPTY);
        s.idle(14);
        s.picture(140, "training_start", 316, 204,
                s.arrow(0.4f, 0.16f, "*Name*: what your diagrams call it"),
                s.arrow(0.34f, 0.285f, "*Kind*: a training or a drill"),
                s.arrow(0.34f, 0.4f, "*Training shots* (no damage) or *Lethal*"),
                s.arrow(0.32f, 0.53f, "*Lives*, and a time limit below"));
        s.idle(145);

        s.chapter("Go!");
        Vec3 you = new Vec3(2.5, 1.0, 5.3);
        float faceRobots = RobotScenes.yawTo(you, out1);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, faceRobots, Map.of(), you);
        s.idle(10);
        s.rightClick(HowToSceneBuilder.top(start), ItemStack.EMPTY);
        s.idle(10);
        s.text(70, you.add(0, 2.1, 0), Side.LEFT,
                "Click it: you are the *trainee*. I.M.T counts down (%s, 2, 1) and says *go!*",
                HowToSceneBuilder.number(ImtConfig.Training.COUNTDOWN_SECONDS));
        s.sound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.4f, 1.0f);
        s.idle(20);
        s.sound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.4f, 1.0f);
        s.idle(20);
        s.sound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.4f, 1.0f);
        s.idle(20);
        s.sound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 2.0f);
        s.idle(10);
        s.actor("r1", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 20, RobotScenes.yawTo(out1, you), on, home1, out1);
        s.actor("r2", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 20, RobotScenes.yawTo(out2, you), on, home2, out2);
        s.sound(ModSounds.ROBOT_POWER_ON.get(), 0.6f, 1.0f);
        s.text(95, out1.add(0, 2.1, 0), Side.LEFT,
                "Your diagram hears *when training_started(\"Bootcamp\"):* and switches on a squad of robots:"
                        + " *activate(\"Wave 1\")*. With *write(facePin, \"trainee\")* they only see you.");
        s.idle(100);

        s.chapter("A hit");
        Vec3 mid = new Vec3(5.0, 1.0, 4.0);
        List<Vec3> walk = List.of(you, mid);
        s.player("you", HowToSceneBuilder.UNTIL_END, 40, RobotScenes.yawTo(mid, out1), Map.of(), you, mid);
        int startWalk = s.now();
        s.idle(28);
        Vec3 hitAt = HowToSceneBuilder.along(walk, Math.min(1.0f, (s.now() - startWalk) / 40.0f));
        s.beam(5, RobotScenes.arm(out1, RobotScenes.yawTo(out1, hitAt), true), hitAt.add(0, 1.2, 0), RobotScenes.LASER);
        s.sound(ModSounds.ROBOT_LASER.get(), 0.35f, 1.0f);
        s.flash(6, hitAt, 0.9, 2.0, HIT_BLUE);
        s.change(end, ModBlocks.TRAINING_END.get().defaultBlockState().setValue(TrainingEndBlock.POWERED, true));
        s.change(lamp, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
        s.text(100, mid.add(0, 2.1, 0), Side.LEFT,
                "With training shots a hit does no damage: a blue flash around you, a second slower, and one life less"
                        + " (*\"Hit by Guard 1: 2 lives left\"*). *Lethal* does real damage.");
        s.idle(Math.round(ImtConfig.Training.END_PULSE));
        s.change(end, ModBlocks.TRAINING_END.get().defaultBlockState());
        s.change(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        s.idle(95);

        s.chapter("Training End");
        s.text(70, HowToSceneBuilder.top(end), Side.LEFT,
                "*Training End* lights up red and gives a redstone pulse on every hit. Sneak + click it for its own little"
                        + " program.");
        s.idle(75);
        s.picture(130, "training_end", 415, 228,
                s.arrow(0.29f, 0.183f, "*when trainee_hit:* runs on every hit"),
                s.arrow(0.275f, 0.27f, "*lives*: how many the trainee has left"),
                s.arrow(0.41f, 0.314f, "It may switch robots and squads"),
                s.arrow(0.74f, 0.91f, "It only saves a program that checks out"));
        s.idle(135);

        s.chapter("Waves");
        ItemStack blaster = new ItemStack(ModItems.ION_BLASTER.get());
        float aim = RobotScenes.yawTo(mid, out1);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(EquipmentSlot.MAINHAND, blaster), mid);
        s.idle(12);
        s.beam(8, mid.add(0, 1.3, 0), out1.add(0, 1.1, 0), RobotScenes.ION);
        s.sound(ModSounds.ION_BLASTER_FIRE.get(), 0.4f, 1.0f);
        s.idle(4);
        s.actor("r1", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, RobotScenes.yawTo(out1, mid),
                RobotScenes.down(on), out1);
        s.text(120, out1.add(0, 1.2, 0), Side.LEFT,
                "A robot you knock down stays down until the training ends. *when squad_down(\"Wave 1\"):* starts when every"
                        + " robot of the squad is down: then send the next wave, *activate(\"Wave 2\")*.");
        s.idle(30);
        s.beam(8, mid.add(0, 1.3, 0), out2.add(0, 1.1, 0), RobotScenes.ION);
        s.sound(ModSounds.ION_BLASTER_FIRE.get(), 0.4f, 1.0f);
        s.idle(4);
        s.actor("r2", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, RobotScenes.yawTo(out2, mid),
                RobotScenes.down(on), out2);
        s.idle(95);

        s.chapter("The goal");
        // Het doel is een heel blok: je stapt erop.
        Vec3 onGoal = new Vec3(7.5, 2.0, 5.5);
        s.player("you", HowToSceneBuilder.UNTIL_END, 34, 40.0f, Map.of(EquipmentSlot.MAINHAND, blaster), mid,
                new Vec3(6.7, 1.0, 5.3), onGoal);
        s.idle(34);
        s.sound(SoundEvents.PLAYER_LEVELUP, 0.4f, 1.0f);
        s.text(80, onGoal.add(0, 2.1, 0), Side.LEFT,
                "Step on the *Training Goal*: passed! I.M.T tells you and your base's owner how it went.");
        s.idle(85);
        s.picture(130, "training_app", 426, 240,
                s.arrow(0.27f, 0.2f, "What is going on now (with a *Stop* button)"),
                s.arrow(0.48f, 0.355f, "Passed or failed, how long, and who hit you"));
        s.idle(135);

        s.chapter("Drills");
        s.text(130, null, Side.RIGHT,
                "*Kind*: a *Break-in drill* makes your whole base treat you as an intruder (hack your own core with a"
                        + " *Hacking Laptop*); in a *Break-out drill* you escape from your cell to the goal. Nothing is reset"
                        + " afterwards.");
        s.idle(135);
        return List.of(s.build());
    }
}
