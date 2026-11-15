package com.senne.imttech.howto.scenes;

import com.senne.imttech.computer.ComputerBlock;
import com.senne.imttech.computer.MonitorBlock;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.logic.InputDeviceBlock;
import com.senne.imttech.logic.InputLight;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.security.HackingLaptopBlock;
import com.senne.imttech.shield.ShieldColor;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * De laatste animaties van het tabblad Security (hoofdstuk 63, jouw vraag van 8 november: "ga verder met een volgende
 * categorie: security items"): de computer met een monitor, de Block Reinforcer, handboeien, autorisatiecodes en de Auth
 * Console, de Bypass Kit (lezers en panelen, laserdraden knippen, zakkenrollen), de Hacking Laptop en de I.M.T Phone. Hoe
 * het echt werkt staat in LEESMIJ ("Je desktop van overal (de laptop) en computers voor je basis", "De I.M.T Phone",
 * "Autorisatiecodes en de Auth Console", "Blokken verstevigen", "Inbreken", "Handboeien").
 */
public final class SecurityItemScenes {
    private SecurityItemScenes() {}

    /** Het randje dat je met de bril ziet om een verstevigd blok (zie ReinforcedRenderer). */
    private static final int REINFORCED = 0x9FD3FF;
    /** De ketting van de handboeien (zie CuffRenderer). */
    private static final int CHAIN = 0x3A4048;

    // ================================================================ Hulpjes

    static BlockState computer(boolean on) {
        return ModBlocks.COMPUTER.get().defaultBlockState().setValue(ComputerBlock.FACING, Direction.SOUTH).setValue(ComputerBlock.ON, on);
    }

    static BlockState monitor(boolean on) {
        return ModBlocks.MONITOR.get().defaultBlockState().setValue(MonitorBlock.FACING, Direction.SOUTH).setValue(MonitorBlock.ON, on);
    }

    static BlockState console(InputLight light) {
        return ModBlocks.AUTH_CONSOLE.get().defaultBlockState().setValue(InputDeviceBlock.FACING, Direction.SOUTH)
                .setValue(InputDeviceBlock.LIGHT, light);
    }

    /** Gegevens voor een speler: wie (een vreemde met een gewone skin, of null = jij), sluipt hij, is hij geboeid. */
    static CompoundTag who(String profile, boolean crouch, boolean cuffed) {
        CompoundTag tag = new CompoundTag();
        if (profile != null) {
            tag.putString("Profile", profile);
        }
        if (crouch) {
            tag.putBoolean("Crouch", true);
        }
        if (cuffed) {
            tag.putBoolean("Cuffed", true);
        }
        return tag;
    }

    /**
     * Een muur van stenen bakstenen van x 1 tot 5 op {@code z}, drie hoog, met een ijzeren deur (dicht) op x = 3 en een relais
     * links van de deur in de muur (dat opent de deur, zoals in de animatie van de keycards).
     */
    static void doorWall(HowToSceneBuilder s, int z) {
        BlockPos low = new BlockPos(3, 1, z);
        BlockPos high = new BlockPos(3, 2, z);
        BlockPos lock = new BlockPos(2, 1, z);
        Map<BlockPos, BlockState> wall = HowToSceneBuilder.box(new BlockPos(1, 1, z), new BlockPos(5, 3, z), pos -> {
            if (pos.equals(low) || pos.equals(high)) {
                return null;
            }
            return pos.equals(lock) ? SecurityScenes.relay(false) : Blocks.STONE_BRICKS.defaultBlockState();
        });
        s.place(wall, HowToSceneBuilder.DROP);
        s.idle(6);
        Map<BlockPos, BlockState> door = new LinkedHashMap<>();
        door.put(low, SecurityScenes.door(DoubleBlockHalf.LOWER, false));
        door.put(high, SecurityScenes.door(DoubleBlockHalf.UPPER, false));
        s.place(door, HowToSceneBuilder.DROP);
    }

    /**
     * De rechterbovenhoek van de muur van {@link #doorWall} op z = 1, voor een ballon met algemene uitleg: bovenaan in het
     * midden (zonder plek) stond die voor de muur en de lezer.
     */
    static Vec3 wallCorner() {
        return HowToSceneBuilder.top(new BlockPos(5, 3, 1));
    }

    /** De deur in de muur op {@code z} opent {@code seconds} seconden (het relais krijgt stroom). */
    static void openDoor(HowToSceneBuilder s, int z, int seconds) {
        BlockPos low = new BlockPos(3, 1, z);
        BlockPos high = new BlockPos(3, 2, z);
        BlockPos lock = new BlockPos(2, 1, z);
        s.change(lock, SecurityScenes.relay(true));
        s.change(low, SecurityScenes.door(DoubleBlockHalf.LOWER, true));
        s.change(high, SecurityScenes.door(DoubleBlockHalf.UPPER, true));
        s.sound(SoundEvents.IRON_DOOR_OPEN, 0.5f, 1.0f);
        s.idle(seconds * 20);
        s.change(lock, SecurityScenes.relay(false));
        s.change(low, SecurityScenes.door(DoubleBlockHalf.LOWER, false));
        s.change(high, SecurityScenes.door(DoubleBlockHalf.UPPER, false));
        s.sound(SoundEvents.IRON_DOOR_CLOSE, 0.5f, 1.0f);
    }

    // ================================================================ Computer en monitor

    /**
     * Een computer met een monitor erop: aansluiten met een PoE-kabel op de kern (dan gaan ze aan), instellen in de app
     * Computers (als plaatje), gebruiken (de desktop van de computer als plaatje), en wie er binnenkwam.
     */
    public static List<HowToScene> computer() {
        HowToSceneBuilder s = new HowToSceneBuilder("computer", "Computer and monitor").plate(6, 5).height(3);
        BlockPos pc = new BlockPos(4, 1, 2);
        BlockPos screen = pc.above();
        BlockPos core = new BlockPos(1, 1, 1);
        BlockPos gen = new BlockPos(0, 1, 1);
        s.idle(6);
        s.chapter("A computer for your base");
        s.place(pc, computer(false));
        s.idle(8);
        s.place(screen, monitor(false));
        s.idle(10);
        s.text(90, HowToSceneBuilder.top(screen), Side.LEFT,
                "A *Computer* with a *Monitor* against it (beside it or on top): for your reception, to watch the cameras or"
                        + " see who came in.");
        s.idle(95);

        s.chapter("Connect it");
        s.place(core, SecurityScenes.core(Direction.SOUTH, true));
        s.idle(4);
        s.place(gen, SecurityScenes.heat(Direction.SOUTH));
        s.idle(10);
        Vec3[] cable = {new Vec3(2.0, 1.05, 1.5), new Vec3(3.5, 1.05, 1.5), new Vec3(3.5, 1.05, 2.5), new Vec3(4.0, 1.05, 2.5)};
        s.holdRight(24, new ItemStack(ModItems.POE_CABLE.get()), cable);
        s.cable(CableKind.POE, 24, cable);
        s.idle(28);
        s.change(pc, computer(true));
        s.change(screen, monitor(true));
        s.sound(ModSounds.IMT_BOOT.get(), 0.3f, 1.2f);
        s.text(100, HowToSceneBuilder.face(pc, Direction.SOUTH), Side.RIGHT,
                "Connect it to your *Security Core* with a network cable and give it a little power: a *PoE* cable does both."
                        + " When it works, the ring round its power button lights up and the screen comes on.");
        s.idle(105);

        s.chapter("Set it up");
        s.text(70, HowToSceneBuilder.top(core), Side.RIGHT,
                "Set it up on the desktop of your core (or with the laptop), in the *Computers* app.");
        s.idle(75);
        s.picture(150, "computers_app", 426, 240,
                s.arrow(0.17f, 0.255f, "Your computers"),
                s.arrow(0.53f, 0.315f, "*Who* may use it: everyone, trusted, or a keycard group"),
                s.arrow(0.6f, 0.383f, "An own *code*, if you like"),
                s.arrow(0.527f, 0.57f, "Per app: *Off*, *View* or *Use*"),
                s.arrow(0.92f, 0.19f, "*Rename*"));
        s.idle(155);

        s.chapter("Use it");
        s.rightClick(HowToSceneBuilder.face(screen, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(14);
        s.picture(140, "pc_desktop", 426, 240,
                s.arrow(0.58f, 0.03f, "*COMPUTER*: its name"),
                s.arrow(0.05f, 0.4f, "Only the apps you chose (a viewed app has no buttons)"),
                s.arrow(0.7f, 0.15f, "It remembers its own windows"));
        s.idle(145);

        s.chapter("Who came in?");
        s.text(110, HowToSceneBuilder.top(screen), Side.LEFT,
                "In *Logs*, *Show: entries* lists only which card was used at which reader (never who), who used which code"
                        + " panel, button or lever, and who logged in where (\"Lotte logged in at Reception\"). Walk more than 8"
                        + " blocks away and it closes.");
        s.idle(115);
        return List.of(s.build());
    }

    // ================================================================ De Block Reinforcer

    /**
     * De Block Reinforcer: één blok verstevigen, een hele muur, het randje dat alleen jij ziet, en een frag: een gewoon
     * blok vliegt eruit, een verstevigd blok krijgt alleen barsten.
     */
    public static List<HowToScene> reinforcer() {
        HowToSceneBuilder s = new HowToSceneBuilder("block_reinforcer", "Block Reinforcer").plate(6, 5).height(3);
        Vec3 you = new Vec3(3.0, 1.0, 3.9);
        ItemStack tool = new ItemStack(ModItems.BLOCK_REINFORCER.get());
        BlockState quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();
        List<BlockPos> wall = List.copyOf(HowToSceneBuilder.box(new BlockPos(2, 1, 1), new BlockPos(4, 2, 1), pos -> quartz).keySet());
        s.idle(6);
        s.place(HowToSceneBuilder.box(new BlockPos(2, 1, 1), new BlockPos(4, 2, 1), pos -> quartz), HowToSceneBuilder.DROP);
        s.idle(8);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(EquipmentSlot.MAINHAND, tool), you);
        s.idle(6);
        s.chapter("As strong as obsidian");
        // Rechts naast de muur (bovenaan in het midden stond de ballon voor de muur).
        s.text(110, HowToSceneBuilder.face(new BlockPos(4, 2, 1), Direction.SOUTH), Side.RIGHT,
                "A wall of quartz, wood or glass is as weak as paper against frags. The *Block Reinforcer* makes a block as"
                        + " strong as obsidian (%s break points: three frags), and it looks just the same.",
                HowToSceneBuilder.number(ImtConfig.Breach.REINFORCED));
        s.idle(115);

        s.chapter("One block");
        BlockPos one = new BlockPos(3, 2, 1);
        s.rightClick(HowToSceneBuilder.face(one, Direction.SOUTH), tool);
        s.idle(12);
        s.sound(SoundEvents.ANVIL_USE, 0.25f, 1.6f);
        int shown = 100 + 115 + 125;
        s.outline(shown, one, one, REINFORCED);
        s.text(100, HowToSceneBuilder.face(one, Direction.SOUTH), Side.RIGHT,
                "Right-click a block: reinforced. It costs one *Reinforcement Plate* from your bag (two iron and an obsidian"
                        + " make four).");
        s.idle(105);

        s.chapter("A whole wall");
        s.sneakRightClick(HowToSceneBuilder.face(new BlockPos(2, 1, 1), Direction.SOUTH), tool);
        s.idle(12);
        s.sound(SoundEvents.ANVIL_USE, 0.3f, 1.4f);
        for (BlockPos pos : wall) {
            if (!pos.equals(one)) {
                s.outline(shown - 117, pos, pos, REINFORCED);
            }
        }
        s.text(110, HowToSceneBuilder.face(new BlockPos(2, 1, 1), Direction.SOUTH), Side.LEFT,
                "Sneak + right-click: the whole wall at once, all connected blocks of the same kind (up to %s), as long as you"
                        + " have plates. Only where you may build.", HowToSceneBuilder.number(ImtConfig.Breach.REINFORCER_WALL));
        s.idle(115);

        s.chapter("Only you see it");
        s.text(110, HowToSceneBuilder.top(new BlockPos(4, 2, 1)), Side.RIGHT,
                "With your *glasses* on and the Reinforcer in your hand you see a thin blue outline round reinforced blocks."
                        + " Without them, an intruder can't tell which wall is the strong one.");
        s.idle(125);

        s.chapter("Against a frag");
        BlockPos plain = new BlockPos(0, 1, 1);
        s.place(plain, quartz);
        s.idle(4);
        s.place(plain.above(), quartz);
        s.idle(10);
        ItemStack sticky = new ItemStack(ModItems.STICKY_FRAG_GRENADE.get(), 4);
        Vec3 plainFace = HowToSceneBuilder.face(plain, Direction.SOUTH);
        float aimPlain = RobotScenes.yawTo(you, plainFace);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aimPlain, Map.of(EquipmentSlot.MAINHAND, sticky), you);
        s.idle(6);
        int fuse = Math.round(ImtConfig.Weapons.STICKY_FUSE_SECONDS * 20) + 14;
        GrenadeScenes.lob(s, "frag", ModEntities.STICKY_FRAG_GRENADE.get(), new CompoundTag(), 14, fuse,
                GrenadeScenes.arc(WeaponScenes.muzzle(you, aimPlain), plainFace.add(0, 0, 0.05), 0.6));
        s.pulse(12, plainFace, 1.8f, GrenadeScenes.BLAST);
        s.sound(SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.0f);
        s.clear(plain);
        s.crack(plain.above(), 6);
        s.idle(10);
        BlockPos strong = new BlockPos(3, 1, 1);
        Vec3 strongFace = HowToSceneBuilder.face(strong, Direction.SOUTH);
        float aimStrong = RobotScenes.yawTo(you, strongFace);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aimStrong, Map.of(EquipmentSlot.MAINHAND, sticky), you);
        s.idle(4);
        GrenadeScenes.lob(s, "frag2", ModEntities.STICKY_FRAG_GRENADE.get(), new CompoundTag(), 14, fuse,
                GrenadeScenes.arc(WeaponScenes.muzzle(you, aimStrong), strongFace.add(0, 0, 0.05), 0.6));
        s.pulse(12, strongFace, 1.8f, GrenadeScenes.BLAST);
        s.sound(SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.0f);
        s.crack(strong, 3);
        s.idle(8);
        s.text(110, strongFace, Side.RIGHT,
                "A frag blasts a normal block out; a reinforced block only gets cracks. Cracks and repairs count as usual,"
                        + " and mining it (or replacing it) undoes the reinforcing.");
        s.idle(115);
        return List.of(s.build());
    }

    // ================================================================ Handboeien

    /**
     * Handboeien: van achteren boeien (rechtsklik vasthouden), geboeid kan hij niets, meenemen (hij loopt vóór je, met een
     * ketting), en los met de sleutel of na een halve minuut worstelen.
     */
    public static List<HowToScene> handcuffs() {
        HowToSceneBuilder s = new HowToSceneBuilder("handcuffs", "Handcuffs").plate(6, 5).height(3);
        Vec3 soldier = new Vec3(3.5, 1.0, 1.7);
        Vec3 behind = new Vec3(3.5, 1.0, 3.1);
        ItemStack cuffs = new ItemStack(ModItems.HANDCUFFS.get());
        CompoundTag free = new CompoundTag();
        CompoundTag cuffed = who(null, false, true);
        s.idle(6);
        s.actor("soldier", ModEntities.RAVEN_SOLDIER.get(), HowToSceneBuilder.UNTIL_END, 0, 180.0f, free, soldier);
        s.chapter("From behind");
        s.player("you", HowToSceneBuilder.UNTIL_END, 30, 180.0f, Map.of(EquipmentSlot.MAINHAND, cuffs), new Vec3(4.6, 1.0, 4.4), behind);
        s.idle(32);
        int hold = Math.round(ImtConfig.Handcuffs.HOLD_SECONDS * 20);
        s.holdRight(hold, cuffs, soldier.add(0, 1.2, 0.25));
        s.text(hold + 70, behind.add(0, 2.1, 0), Side.RIGHT,
                "With *Handcuffs* you capture a player, a Raven soldier or a guard robot. Only *from behind*: hold right-click"
                        + " on him for about %s seconds.", HowToSceneBuilder.number(ImtConfig.Handcuffs.HOLD_SECONDS));
        s.idle(hold);
        s.actor("soldier", ModEntities.RAVEN_SOLDIER.get(), HowToSceneBuilder.UNTIL_END, 0, 180.0f, cuffed, soldier);
        s.sound(SoundEvents.CHAIN_PLACE, 0.6f, 1.0f);
        s.idle(75);

        s.chapter("Helpless");
        s.text(110, soldier.add(0, 2.1, 0), Side.LEFT,
                "Cuffed, his hands are on his back: no items, no hitting or building, no chests, at half speed. A robot stops"
                        + " its program. Someone helpless (blinded, asleep, nearly dead, a robot that's down) is cuffed at once.");
        s.idle(115);

        s.chapter("Take him along");
        // Hij staat recht vóór je, met zijn rug naar jou, en loopt precies mee; de ketting loopt van je handen naar zijn polsen.
        float west = 90.0f;
        Vec3 youFrom = new Vec3(4.2, 1.0, 2.6);
        Vec3 youTo = new Vec3(2.2, 1.0, 2.6);
        double gap = ImtConfig.Handcuffs.HOLD_DISTANCE;
        Vec3 himFrom = youFrom.add(WeaponScenes.ahead(west).scale(gap));
        Vec3 himTo = youTo.add(WeaponScenes.ahead(west).scale(gap));
        int walk = 50;
        s.player("you", HowToSceneBuilder.UNTIL_END, walk, west, Map.of(EquipmentSlot.MAINHAND, cuffs), youFrom, youTo);
        s.actor("soldier", ModEntities.RAVEN_SOLDIER.get(), HowToSceneBuilder.UNTIL_END, walk, west, cuffed, himFrom, himTo);
        // Ze lopen naar links: de ballon rechts, anders stond hij voor de soldaat.
        s.text(walk + 60, himTo.add(0, 2.1, 0), Side.RIGHT,
                "Whoever cuffs him holds on: he stands right in front of you, back to you, and comes along exactly as you walk"
                        + " or turn. Right-click him with the cuffs to let go or grab him again.");
        for (int t = 0; t <= walk; t += 5) {
            float f = t / (float) walk;
            Vec3 me = youFrom.lerp(youTo, f);
            Vec3 him = himFrom.lerp(himTo, f);
            s.beam(5, me.add(WeaponScenes.ahead(west).scale(0.45)).add(0, 1.05, 0),
                    him.add(WeaponScenes.ahead(west).scale(-0.25)).add(0, 0.95, 0), CHAIN);
            s.idle(5);
        }
        s.idle(60);

        s.chapter("Free again");
        ItemStack key = new ItemStack(ModItems.HANDCUFF_KEY.get());
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, west, Map.of(EquipmentSlot.MAINHAND, key), youTo);
        s.idle(6);
        s.rightClick(himTo.add(0, 1.1, 0), key);
        s.idle(14);
        s.actor("soldier", ModEntities.RAVEN_SOLDIER.get(), HowToSceneBuilder.UNTIL_END, 0, west, free, himTo);
        s.sound(SoundEvents.CHAIN_BREAK, 0.6f, 1.2f);
        s.text(110, himTo.add(0, 2.1, 0), Side.RIGHT,
                "A *Handcuff Key* opens them, and you get the cuffs back. Or he struggles free: %s seconds of sneaking, and"
                        + " then the cuffs are broken. A robot never gets free by itself.",
                HowToSceneBuilder.number(ImtConfig.Handcuffs.STRUGGLE_SECONDS));
        s.idle(115);
        return List.of(s.build());
    }

    // ================================================================ Autorisatiecodes en de Auth Console

    /**
     * De autorisatiecode (Status als plaatje), ermee een codepaneel openen, de Auth Console: iemand vraagt toegang, jij
     * antwoordt in Requests (als plaatje), en het antwoord opent de deur.
     */
    public static List<HowToScene> auth() {
        HowToSceneBuilder s = new HowToSceneBuilder("auth_console", "Auth codes and the Auth Console").plate(7, 5).height(3);
        int z = 1;
        BlockPos panel = new BlockPos(2, 2, 2);
        BlockPos consolePos = new BlockPos(4, 2, 2);
        Vec3 you = new Vec3(2.4, 1.0, 3.6);
        Vec3 guest = new Vec3(4.6, 1.0, 3.5);
        s.idle(6);
        doorWall(s, z);
        s.idle(6);
        s.place(panel, SecurityScenes.panel(Direction.SOUTH, InputLight.OFF));
        s.idle(4);
        s.place(consolePos, console(InputLight.OFF));
        s.idle(8);
        s.chapter("A code that changes");
        s.text(80, HowToSceneBuilder.face(panel, Direction.SOUTH), Side.LEFT,
                "Your core has an *authorization code*: %s digits that change every %s minutes. You see it in *Status* on the"
                        + " desktop, the laptop and the phone.", HowToSceneBuilder.number(ImtConfig.Security.AUTH_CODE_LENGTH),
                HowToSceneBuilder.number(ImtConfig.Security.AUTH_CODE_MINUTES));
        s.idle(85);
        s.picture(130, "auth_status", 426, 240,
                s.arrow(0.6f, 0.5f, "The code of now, and when the next one comes"));
        s.idle(135);

        s.chapter("On a code panel");
        s.player("you", 150, 0, 180.0f, Map.of(), you);
        s.idle(6);
        s.rightClick(HowToSceneBuilder.face(panel, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(14);
        s.change(panel, SecurityScenes.panel(Direction.SOUTH, InputLight.BUSY));
        s.idle(8);
        s.change(panel, SecurityScenes.panel(Direction.SOUTH, InputLight.OK));
        s.text(110, HowToSceneBuilder.face(panel, Direction.SOUTH), Side.LEFT,
                "In your diagram you use it on a code panel, without a fixed code: *if auth.valid: accept()*. *auth.old* means"
                        + " it was the previous code, and that always goes in the log.");
        openDoor(s, z, 3);
        s.change(panel, SecurityScenes.panel(Direction.SOUTH, InputLight.OFF));
        s.idle(60);

        s.chapter("The Auth Console");
        CompoundTag stranger = who("Bram", false, false);
        s.person("guest", stranger, HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(), List.of(), guest);
        s.idle(10);
        s.rightClick(HowToSceneBuilder.face(consolePos, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(12);
        s.change(consolePos, console(InputLight.BUSY));
        s.sound(ModSounds.IMT_NOTIFY.get(), 0.5f, 1.0f);
        s.text(90, HowToSceneBuilder.face(consolePos, Direction.SOUTH), Side.RIGHT,
                "Whoever presses the *Auth Console* asks for access: its light turns orange and you get a message.");
        s.idle(95);

        s.chapter("Accept or deny");
        s.text(70, wallCorner(), Side.RIGHT, "Answer in the *Requests* app: on the desktop, the laptop or the phone, wherever you are.");
        s.idle(75);
        s.picture(130, "auth_requests", 426, 240,
                s.arrow(0.36f, 0.283f, "Who asks, at which console"),
                s.arrow(0.75f, 0.283f, "Seconds left"),
                s.arrow(0.88f, 0.283f, "*Accept* or *Deny*"));
        s.idle(135);

        s.chapter("The answer");
        s.change(consolePos, console(InputLight.OK));
        s.sound(ModSounds.IMT_CONFIRM.get(), 0.5f, 1.0f);
        s.text(150, HowToSceneBuilder.face(consolePos, Direction.SOUTH), Side.RIGHT,
                "The answer starts your diagram: *when request(\"Gate\"): if request.accepted: pulse(\"Gate door\", 5)*. No answer"
                        + " within %s seconds means denied, and everything goes in the log.",
                HowToSceneBuilder.number(ImtConfig.Security.AUTH_REQUEST_SECONDS));
        s.person("guest", stranger, HowToSceneBuilder.UNTIL_END, 40, 180.0f, Map.of(), List.of(), guest,
                new Vec3(3.5, 1.0, 2.6), new Vec3(3.5, 1.0, 0.3));
        openDoor(s, z, 5);
        s.change(consolePos, console(InputLight.OFF));
        s.idle(50);
        return List.of(s.build());
    }

    // ================================================================ De Bypass Kit

    /**
     * Lezers en panelen hacken met de Bypass Kit: het stroomcircuit van een lezer (als plaatje; de deur gaat open), de
     * vingerafdrukken op een codepaneel (als plaatje), en wat er gebeurt als het mislukt.
     */
    public static List<HowToScene> bypassReaders() {
        HowToSceneBuilder s = new HowToSceneBuilder("bypass_readers", "Hacking readers and code panels").plate(7, 5).height(3);
        int z = 1;
        BlockPos reader = new BlockPos(2, 2, 2);
        BlockPos panel = new BlockPos(4, 2, 2);
        Vec3 you = new Vec3(3.0, 1.0, 3.7);
        ItemStack kit = new ItemStack(ModItems.BYPASS_KIT.get());
        s.idle(6);
        doorWall(s, z);
        s.idle(6);
        s.place(reader, SecurityScenes.reader(Direction.SOUTH, InputLight.OFF));
        s.idle(4);
        s.place(panel, SecurityScenes.panel(Direction.SOUTH, InputLight.OFF));
        s.idle(8);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(EquipmentSlot.MAINHAND, kit), you);
        s.chapter("No card, no code");
        s.text(110, wallCorner(), Side.RIGHT,
                "No card and no code? With a *Bypass Kit* you hack someone else's keycard reader, code panel or laser emitter"
                        + " (%s uses). The game keeps running: turrets still shoot at you. Esc or walking away stops without an"
                        + " alarm.", ImtConfig.Bypass.KIT_USES);
        s.idle(115);

        s.chapter("A reader: the circuit");
        s.rightClick(HowToSceneBuilder.face(reader, Direction.SOUTH), kit);
        s.idle(12);
        s.change(reader, SecurityScenes.reader(Direction.SOUTH, InputLight.BUSY));
        s.text(70, HowToSceneBuilder.face(reader, Direction.SOUTH), Side.LEFT,
                "First you pick which group's card you fake. Then you open up the reader.");
        s.idle(75);
        s.picture(150, "bypass_circuit", 426, 240,
                s.arrow(0.2f, 0.43f, "*CARD*: the signal starts here"),
                s.arrow(0.81f, 0.43f, "Lead it to *ACCEPT*"),
                s.arrow(0.527f, 0.53f, "Left-click turns a piece right, right-click left"),
                s.arrow(0.252f, 0.528f, "A red *ALARM* point: 1 second to turn that wire away"),
                s.arrow(0.83f, 0.057f, "The clock"));
        s.idle(155);

        s.chapter("Access");
        s.change(reader, SecurityScenes.reader(Direction.SOUTH, InputLight.OK));
        s.text(100, HowToSceneBuilder.face(reader, Direction.SOUTH), Side.LEFT,
                "The signal reaches ACCEPT: the reader reads a valid card of the group you chose, and the owner's diagram"
                        + " decides, as with a real card. (In his log it has a number that isn't in his Keycards app.)");
        openDoor(s, z, 3);
        s.change(reader, SecurityScenes.reader(Direction.SOUTH, InputLight.OFF));
        s.idle(50);

        s.chapter("A code panel: fingerprints");
        s.rightClick(HowToSceneBuilder.face(panel, Direction.SOUTH), kit);
        s.idle(12);
        s.change(panel, SecurityScenes.panel(Direction.SOUTH, InputLight.BUSY));
        s.text(70, HowToSceneBuilder.face(panel, Direction.SOUTH), Side.RIGHT,
                "On a code panel you dust the keys: prints show which digits are in the code.");
        s.idle(75);
        s.picture(150, "bypass_prints", 426, 240,
                s.arrow(0.165f, 0.45f, "Prints on the digits of the code (more = used more often)"),
                s.arrow(0.62f, 0.155f, "What the dust tells you"),
                s.arrow(0.52f, 0.24f, "Your tries: one click per digit in the right place"),
                s.arrow(0.46f, 0.357f, "Code length + %s tries", HowToSceneBuilder.number(ImtConfig.Bypass.ORDER_EXTRA_TRIES)));
        s.idle(155);
        s.change(panel, SecurityScenes.panel(Direction.SOUTH, InputLight.OK));
        s.text(70, HowToSceneBuilder.face(panel, Direction.SOUTH), Side.RIGHT,
                "Cracked: the kit types the code (*CODE CRACKED*), and you know it from now on.");
        openDoor(s, z, 3);
        s.change(panel, SecurityScenes.panel(Direction.SOUTH, InputLight.OFF));
        s.idle(20);

        s.chapter("Caught");
        s.rightClick(HowToSceneBuilder.face(reader, Direction.SOUTH), kit);
        s.idle(20);
        s.change(reader, SecurityScenes.reader(Direction.SOUTH, InputLight.DENIED));
        s.sound(ModSounds.IMT_ALERT.get(), 0.5f, 1.0f);
        s.flash(10, you, 0.9, 2.0, HowToSceneBuilder.RED);
        s.text(120, HowToSceneBuilder.face(reader, Direction.SOUTH), Side.LEFT,
                "Failed (an ALARM point, out of time or out of tries): the light turns red, the alarm goes off with your name,"
                        + " the owner is warned, and that device won't let you try again for %s seconds.",
                HowToSceneBuilder.number(ImtConfig.Bypass.LOCKOUT_SECONDS));
        s.idle(125);
        return List.of(s.build());
    }

    /**
     * Een laserpoort hacken: de draden doorknippen in het donker (als plaatje), de goede draad (de hele poort uit tot
     * Repair wires), en een verkeerde (een schok, op slot, het alarm).
     */
    public static List<HowToScene> bypassWires() {
        HowToSceneBuilder s = new HowToSceneBuilder("bypass_wires", "Cutting laser wires").plate(5, 6).height(3);
        BlockPos low = new BlockPos(2, 1, 0);
        BlockPos high = new BlockPos(2, 2, 0);
        BlockPos gen = new BlockPos(1, 1, 0);
        Vec3 you = new Vec3(3.6, 1.0, 1.6);
        ItemStack kit = new ItemStack(ModItems.BYPASS_KIT.get());
        s.idle(6);
        Map<BlockPos, BlockState> gate = new LinkedHashMap<>();
        gate.put(low, SecurityScenes.emitter(true, ShieldColor.BLUE));
        gate.put(high, SecurityScenes.emitter(true, ShieldColor.BLUE));
        gate.put(gen, SecurityScenes.heat(Direction.WEST));
        gate.putAll(HowToSceneBuilder.box(new BlockPos(2, 1, 4), new BlockPos(2, 2, 4), pos -> Blocks.STONE_BRICKS.defaultBlockState()));
        gate.putAll(HowToSceneBuilder.box(new BlockPos(2, 1, 1), new BlockPos(2, 2, 3), pos -> SecurityScenes.wall(ShieldColor.BLUE)));
        s.place(gate, HowToSceneBuilder.DROP);
        s.idle(10);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 90.0f, Map.of(EquipmentSlot.MAINHAND, kit), you);
        s.chapter("Open it up");
        s.rightClick(HowToSceneBuilder.face(high, Direction.EAST), kit);
        s.idle(12);
        s.text(80, HowToSceneBuilder.face(high, Direction.EAST), Side.RIGHT,
                "Right-click someone's *Laser Emitter* with the Bypass Kit (any emitter, also one without a core): you open it"
                        + " up. Inside it's dark.");
        s.idle(85);
        s.picture(150, "bypass_wires", 426, 240,
                s.arrow(0.059f, 0.47f, "The plugs: 1, 2, 3..."),
                s.arrow(0.905f, 0.32f, "Where each wire goes: find the *LENS*"),
                s.arrow(0.426f, 0.317f, "Only where your mouse is, it lights up: follow the wire"),
                s.arrow(0.5f, 0.8f, "Pick the plug and *CUT*: only once"));
        s.idle(155);

        s.chapter("The right wire");
        s.sound(ModSounds.RAYSHIELD_OFF.get(), 0.4f, 1.0f);
        for (int zz = 3; zz >= 1; zz--) {
            s.clear(new BlockPos(2, 1, zz), new BlockPos(2, 2, zz));
            s.idle(3);
        }
        s.change(low, SecurityScenes.emitter(false, ShieldColor.BLUE));
        s.change(high, SecurityScenes.emitter(false, ShieldColor.BLUE));
        s.pulse(8, HowToSceneBuilder.center(high), 0.6f, 0xFFD27A);
        s.sound(SoundEvents.FIRE_EXTINGUISH, 0.3f, 1.8f);
        s.text(110, HowToSceneBuilder.face(high, Direction.EAST), Side.RIGHT,
                "The right wire: the whole gate (all emitters against it) goes off and stays off, also with redstone. Quietly,"
                        + " no alarm; the emitter sparks, and Devices says *cut wires*.");
        s.idle(115);

        s.chapter("Repair wires");
        s.text(100, HowToSceneBuilder.face(low, Direction.EAST), Side.RIGHT,
                "Only *Repair wires* in its screen fixes it, by someone who may set it up: %s Computer Chip. Mined, a cut"
                        + " emitter stays cut.", HowToSceneBuilder.number(ImtConfig.Bypass.LASER_REPAIR_CHIPS));
        s.idle(105);

        s.chapter("The wrong wire");
        s.change(low, SecurityScenes.emitter(true, ShieldColor.BLUE));
        s.change(high, SecurityScenes.emitter(true, ShieldColor.BLUE));
        s.sound(ModSounds.RAYSHIELD_ON.get(), 0.4f, 1.0f);
        for (int zz = 1; zz <= 3; zz++) {
            s.change(new BlockPos(2, 1, zz), SecurityScenes.wall(ShieldColor.BLUE));
            s.change(new BlockPos(2, 2, zz), SecurityScenes.wall(ShieldColor.BLUE));
            s.idle(3);
        }
        s.idle(10);
        s.rightClick(HowToSceneBuilder.face(high, Direction.EAST), kit);
        s.idle(20);
        s.flash(10, you, 0.9, 2.0, HowToSceneBuilder.RED);
        s.sound(SoundEvents.PLAYER_HURT_ON_FIRE, 0.5f, 1.0f);
        s.sound(ModSounds.IMT_ALERT.get(), 0.4f, 1.0f);
        s.text(110, HowToSceneBuilder.face(high, Direction.EAST), Side.RIGHT,
                "A wrong wire: a shock (%s hearts), the gate locks for %s minutes for everyone, and if it belongs to a core the"
                        + " alarm goes off. Bigger gates have more wires.",
                HowToSceneBuilder.number(ImtConfig.Bypass.LASER_SHOCK_DAMAGE / 2), HowToSceneBuilder.number(ImtConfig.Bypass.LASER_LOCK_SECONDS / 60));
        s.idle(115);
        return List.of(s.build());
    }

    /** Zakkenrollen: sluipen achter iemand, zijn zak (als plaatje), en betrapt worden. */
    public static List<HowToScene> pickpocket() {
        HowToSceneBuilder s = new HowToSceneBuilder("pickpocket", "Pickpocketing").plate(4, 4).height(3);
        Vec3 her = new Vec3(2.0, 1.0, 1.4);
        Vec3 near = new Vec3(2.0, 1.0, 2.5);
        CompoundTag lotte = who("Lotte", false, false);
        CompoundTag sneaking = who(null, true, false);
        s.idle(6);
        s.person("lotte", lotte, HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(), List.of(), her);
        s.chapter("Sneak up behind");
        s.person("you", sneaking, HowToSceneBuilder.UNTIL_END, 40, 180.0f, Map.of(), List.of(), new Vec3(3.2, 1.0, 3.6), near);
        s.text(90, near.add(0, 1.8, 0), Side.RIGHT,
                "Sneak (Shift) up behind someone with an *empty hand* and right-click him: his pocket opens.");
        s.idle(46);
        s.rightClick(her.add(0, 1.1, 0.3), ItemStack.EMPTY);
        s.idle(50);

        s.chapter("His pocket");
        s.picture(150, "pickpocket", 426, 240,
                s.arrow(0.45f, 0.62f, "Keycards lie upside down between junk: drag the junk aside"),
                s.arrow(0.44f, 0.22f, "Pull a card up, out of the pocket: it's yours"),
                s.arrow(0.775f, 0.55f, "*NOTICED*: every move fills it, holding still lets it drop"));
        s.idle(155);

        s.chapter("Caught");
        s.person("lotte", lotte, HowToSceneBuilder.UNTIL_END, 0, 0.0f, Map.of(), List.of(), her);
        s.flash(12, near, 0.9, 1.6, 0xF2FCFF);
        s.sound(ModSounds.IMT_ALERT.get(), 0.4f, 1.0f);
        s.text(110, her.add(0, 2.1, 0), Side.RIGHT,
                "If the meter fills up, or he turns round and sees you, you're caught: he gets a message with your name and"
                        + " you glow for %s seconds. Then wait %s seconds before you try him again.",
                HowToSceneBuilder.number(ImtConfig.Bypass.PICKPOCKET_GLOW_SECONDS),
                HowToSceneBuilder.number(ImtConfig.Bypass.PICKPOCKET_COOLDOWN_SECONDS));
        s.idle(115);
        return List.of(s.build());
    }

    // ================================================================ De Hacking Laptop

    /**
     * De Hacking Laptop: je desktop van overal (de inlog en de remote session als plaatje), en de kern van een ander hacken
     * (de laptop op de kern, de terminal als plaatje).
     */
    public static List<HowToScene> laptop() {
        HowToSceneBuilder s = new HowToSceneBuilder("hacking_laptop", "Hacking Laptop").plate(6, 5).height(3);
        Vec3 you = new Vec3(2.0, 1.0, 3.8);
        ItemStack laptop = new ItemStack(ModItems.HACKING_LAPTOP.get());
        BlockPos core = new BlockPos(4, 1, 1);
        BlockPos gen = new BlockPos(5, 1, 1);
        s.idle(6);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 40.0f, Map.of(EquipmentSlot.MAINHAND, laptop), you);
        s.chapter("Your desktop from anywhere");
        s.rightClick(you.add(0.6, 1.4, -0.2), laptop);
        s.idle(12);
        s.text(80, you.add(0, 2.1, 0), Side.RIGHT,
                "Right-click the *Hacking Laptop* in the air, anywhere in the same dimension: type your core's *IP* (it's in"
                        + " Status) and its *password*.");
        s.idle(85);
        s.picture(130, "ssh_login", 426, 240,
                s.arrow(0.5f, 0.36f, "The IP of the core"),
                s.arrow(0.5f, 0.495f, "Its password (stars)"));
        s.idle(135);
        s.picture(120, "remote_session", 426, 240,
                s.arrow(0.56f, 0.03f, "*REMOTE SESSION*: your desktop, from far away"));
        s.idle(125);

        s.chapter("Keep it safe");
        s.text(110, you.add(0, 2.1, 0), Side.RIGHT,
                "A core without a password lets nobody in from far away. A wrong password goes in the log and the owner is"
                        + " warned; after %s tries you have to wait. The laptop remembers the IP, never the password.",
                HowToSceneBuilder.number(ImtConfig.Security.PASSWORD_ATTEMPTS));
        s.idle(115);

        s.chapter("Hacking a core");
        Map<BlockPos, BlockState> theirs = new LinkedHashMap<>();
        theirs.put(core, SecurityScenes.core(Direction.SOUTH, true));
        theirs.put(gen, SecurityScenes.heat(Direction.SOUTH));
        s.place(theirs, HowToSceneBuilder.DROP);
        s.idle(10);
        Vec3 atCore = new Vec3(4.5, 1.0, 2.6);
        s.player("you", HowToSceneBuilder.UNTIL_END, 30, 180.0f, Map.of(EquipmentSlot.MAINHAND, laptop), you, atCore);
        s.idle(32);
        s.rightClick(HowToSceneBuilder.top(core), laptop);
        s.idle(12);
        s.place(core.above(), ModBlocks.HACKING_LAPTOP.get().defaultBlockState().setValue(HackingLaptopBlock.FACING, Direction.SOUTH));
        s.sound(ModSounds.IMT_ALERT.get(), 0.5f, 1.0f);
        s.text(80, HowToSceneBuilder.top(core.above()), Side.RIGHT,
                "Someone else's core: click it with the laptop. It opens on top of the core (there must be room), and the alarm"
                        + " goes off.");
        s.idle(85);
        s.picture(150, "hack_terminal", 426, 240,
                s.arrow(0.8f, 0.07f, "Four stages: recon, breach, search, override"),
                s.arrow(0.25f, 0.3f, "The memory dump: one of these words is the password"),
                s.arrow(0.3f, 0.68f, "A wrong word shows the letters in the right place"),
                s.arrow(0.15f, 0.805f, "The words that are still possible"));
        s.idle(155);

        s.chapter("It's yours");
        s.sound(ModSounds.IMT_CONFIRM.get(), 0.5f, 1.0f);
        s.text(120, HowToSceneBuilder.top(core.above()), Side.RIGHT,
                "Find your way in, take over the security program, and the core is yours. Meanwhile the security shoots at"
                        + " you; your progress is on the laptop, so Esc, walking away or dying only closes the screen.");
        s.idle(125);
        return List.of(s.build());
    }

    // ================================================================ De I.M.T Phone

    /** De I.M.T Phone: verbinden met een PIN (als plaatje), de kleine desktop (als plaatje), en een gestolen telefoon blokkeren. */
    public static List<HowToScene> phone() {
        HowToSceneBuilder s = new HowToSceneBuilder("phone", "I.M.T Phone").plate(3, 3).height(3);
        Vec3 you = new Vec3(1.5, 1.0, 1.5);
        ItemStack phone = new ItemStack(ModItems.PHONE.get());
        s.idle(6);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 40.0f, Map.of(EquipmentSlot.MAINHAND, phone), you);
        s.chapter("Connect it");
        s.rightClick(you.add(0.6, 1.4, -0.2), phone);
        s.idle(12);
        s.text(90, you.add(0, 2.0, 0), Side.RIGHT,
                "The *I.M.T Phone* is the laptop in your pocket. The first time you connect it to your core (its IP and"
                        + " password), and choose a *PIN* of four digits.");
        s.idle(95);
        s.picture(120, "phone_pin", 426, 240,
                s.arrow(0.5f, 0.49f, "Four digits: asked every time you open it"));
        s.idle(125);

        s.chapter("Your base in your pocket");
        s.picture(150, "phone_desktop", 426, 240,
                s.arrow(0.53f, 0.03f, "*I.M.T PHONE*"),
                s.arrow(0.05f, 0.4f, "Status (with the auth code), Cameras, Devices"),
                s.arrow(0.16f, 0.25f, "Messages and Logs"),
                s.arrow(0.05f, 0.75f, "*Requests*: answer wherever you are"));
        s.idle(155);

        s.chapter("Stolen?");
        s.text(120, you.add(0, 2.0, 0), Side.RIGHT,
                "The PIN is kept at the core, not on the phone. %s wrong PINs and it locks for %s seconds (the owner hears"
                        + " it). In *Access* on the core every phone is listed with *Block*.",
                HowToSceneBuilder.number(ImtConfig.Security.PHONE_PIN_ATTEMPTS),
                HowToSceneBuilder.number(ImtConfig.Security.PHONE_PIN_LOCK_SECONDS));
        s.idle(125);
        return List.of(s.build());
    }
}
