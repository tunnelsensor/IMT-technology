package com.senne.imttech.client.dev;

import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.guns.Attachment;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hoofdstuk 105 ({@code gradlew runVisualCheck -Ponly=gunscale}): elk wapen op zijn maat, om de verhoudingen te zien.
 * <ol>
 *   <li>In je eigen beeld (gs01 tot gs09): elk wapen vanuit de heup, gericht en terwijl je het bekijkt (H), de Glock
 *       met een demper en een red dot (gs10a/b), en de AK-47 met een Sniper Scope terwijl je aanlegt (gs10c tot g).</li>
 *   <li>In de hand van een ander (gs11 tot gs20): Bram, een nep-speler, met elk wapen, van opzij en schuin van voren.</li>
 *   <li>Op het lijf (gs21 tot gs23): de Glock in de holster met een AWP op zijn rug en een MP5 voor zijn borst; het
 *       laserpistool in de holster met een Laser Rifle en een Laser Sniper; de AK-47 en de Remington 870.</li>
 *   <li>Op de grond (gs25): elk wapen naast elkaar.</li>
 * </ol>
 * De soldaten met hun wapens staan in {@code -Ponly=ravenguns} (rg01 tot rg07). Aan het eind gaat Bram weg: een latere
 * ronde (de handboeien) maakt hem opnieuw, in survival; bleef hij, dan zette de sling-ronde hem in creative.
 */
final class GunScaleTour {
    private GunScaleTour() {}

    /** Het veldje (de vloer): ver van de andere veldjes van de rondleiding. */
    static final BlockPos YARD = new BlockPos(-540, -61, -120);

    /** De negen wapens in je werkbalk, in deze volgorde (voor de namen van de screenshots). */
    private static final String[] NAMES = {"glock_19", "mp5", "ak_47", "awp", "remington_870", "laser_pistol",
            "laser_rifle", "laser_sniper", "laser_shotgun"};

    static void tour(DevScript t) {
        BlockPos y = YARD;
        double cx = y.getX() + 0.5;
        double floor = y.getY() + 1;
        double cz = y.getZ() + 0.5;
        // Eerst erheen, en wachten tot de wereld daar leeft (anders bevriezen de spelers en de items daar).
        t.view(0, cx, floor, cz, 180, 0);
        t.waitUntil(200, () -> VisualCheck.raidLeaveTicking(y));
        t.onServer(5, GunScaleTour::buildYard);
        t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        t.camera(0, CameraType.FIRST_PERSON);
        // Naar het noorden kijken, naar de muur: een rustige achtergrond.
        t.stand(10, cx, floor, cz + 6, 180, 0);
        t.onServer(0, GunScaleTour::giveHotbar);

        // ---- 1. In je eigen beeld.
        for (int i = 0; i < NAMES.length; i++) {
            int slot = i;
            String number = String.format("gs%02d", i + 1);
            t.step(i == 0 ? 10 : 40, () -> select(slot));
            t.shot(28, number + "a_" + NAMES[i] + "_hip");
            t.key(0, () -> Minecraft.getInstance().options.keyUse, true);
            t.shot(16, number + "b_" + NAMES[i] + "_aim");
            t.key(0, () -> Minecraft.getInstance().options.keyUse, false);
            t.step(12, () -> KeyMapping.click(ImtKeys.GUN_INSPECT.getKey()));
            t.shot(10, number + "c_" + NAMES[i] + "_inspect_side");
            t.shot(18, number + "d_" + NAMES[i] + "_inspect_top");
        }
        // De Glock met een demper en een red dot.
        t.onServer(40, player -> player.getInventory().setItem(0, suppressedGlock()));
        t.step(0, () -> select(0));
        t.shot(30, "gs10a_glock_suppressed_hip");
        t.key(0, () -> Minecraft.getInstance().options.keyUse, true);
        t.shot(16, "gs10b_glock_suppressed_red_dot_aim");
        t.key(0, () -> Minecraft.getInstance().options.keyUse, false);
        // De AK-47 met een Sniper Scope: vanuit de heup, terwijl je aanlegt (het oculair mag nooit achter je oog komen) en
        // gericht.
        t.onServer(30, player -> {
            ItemStack ak = FirearmItem.loaded(FirearmType.AK_47, GunMaterial.STEEL);
            FirearmItem.setAttachment(ak, Attachment.SNIPER_SCOPE);
            player.getInventory().setItem(0, ak);
        });
        t.step(0, () -> select(0));
        t.shot(30, "gs10c_ak_47_scope_hip");
        t.key(0, () -> Minecraft.getInstance().options.keyUse, true);
        t.shot(2, "gs10d_ak_47_scope_aiming_2");
        t.shot(1, "gs10e_ak_47_scope_aiming_3");
        t.shot(1, "gs10f_ak_47_scope_aiming_4");
        t.shot(12, "gs10g_ak_47_scope_aim");
        t.key(0, () -> Minecraft.getInstance().options.keyUse, false);
        t.onServer(10, player -> player.getInventory().clearContent());

        // ---- 2. In de hand van een ander: Bram staat naar het westen gekeerd, met zijn rechterhand naar het noorden.
        Vec3 bram = new Vec3(cx, floor, cz);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.step(0, () -> Minecraft.getInstance().options.fov().set(50));
        t.onServer(0, player -> bram(player, bram));
        String[] held = {"glock_19", "glock_19_suppressed", "mp5", "ak_47", "awp", "remington_870", "laser_pistol",
                "laser_rifle", "laser_sniper", "laser_shotgun"};
        for (int i = 0; i < held.length; i++) {
            int index = i;
            String number = String.format("gs%02d", 11 + i);
            t.onServer(i == 0 ? 10 : 2, player -> holdOnly(player, heldGun(index)));
            // Van opzij (zijn rechterkant), en schuin van voren rechts.
            viewAt(t, 2, bram.add(0.0, 1.35, -3.6), bram.add(-0.6, 1.15, 0));
            t.shot(14, number + "a_" + held[i] + "_side");
            viewAt(t, 0, bram.add(-2.6, 1.5, -2.2), bram.add(0, 1.2, 0));
            t.shot(10, number + "b_" + held[i] + "_front");
        }

        // ---- 3. Op het lijf: de holster en de slings, van opzij, van achteren en van voren (ingezoomd).
        t.step(0, () -> Minecraft.getInstance().options.fov().set(30));
        String[][] worn = {{"glock_awp_mp5"}, {"laserpistol_laserrifle_lasersniper"}, {"glock_ak47_870"}};
        for (int i = 0; i < worn.length; i++) {
            int set = i;
            String number = String.format("gs%02d", 21 + i);
            t.onServer(4, player -> wear(player, set));
            viewAt(t, 4, bram.add(0.0, 1.1, -4.2), bram.add(0, 0.95, 0));
            t.shot(14, number + "a_" + worn[i][0] + "_right_side");
            viewAt(t, 0, bram.add(4.6, 1.3, 0.0), bram.add(0, 1.05, 0));
            t.shot(10, number + "b_" + worn[i][0] + "_back");
            viewAt(t, 0, bram.add(-4.6, 1.3, 0.0), bram.add(0, 1.05, 0));
            t.shot(10, number + "c_" + worn[i][0] + "_front");
            // De holster van dichtbij (van opzij en schuin van voren).
            viewAt(t, 0, bram.add(-0.9, 0.95, -2.2), bram.add(-0.05, 0.85, -0.35));
            t.shot(10, number + "d_" + worn[i][0] + "_holster_close");
        }

        // ---- 4. Op de grond: elk wapen naast elkaar, van schuin boven. Bram gaat aan de kant.
        t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
        t.onServer(0, GunScaleTour::bramAway);
        t.onServer(4, GunScaleTour::dropRow);
        viewAt(t, 4, new Vec3(cx, floor + 3.4, cz + 7.5), new Vec3(cx, floor, cz + 11.5));
        t.shot(30, "gs25_on_the_ground");

        t.onServer(0, GunScaleTour::clearYard);
        t.onServer(0, GunScaleTour::bramGone);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        t.camera(0, CameraType.FIRST_PERSON);
    }

    // ================================================================ Het veldje

    /** Een vloer van gladde steen met een witte muur in het noorden (een rustige achtergrond). */
    private static void buildYard(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos y = YARD;
        clearYard(player);
        for (BlockPos pos : BlockPos.betweenClosed(y.offset(-10, 1, -8), y.offset(10, 7, 18))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(y.offset(-10, 0, -8), y.offset(10, 0, 18))) {
            level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(y.offset(-10, 1, -8), y.offset(10, 6, -8))) {
            level.setBlockAndUpdate(pos, Blocks.WHITE_CONCRETE.defaultBlockState());
        }
    }

    /** Wat er nog ligt (de items op de grond), en Bram zijn spullen. */
    private static void clearYard(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB area = AABB.encapsulatingFullBlocks(YARD.offset(-12, 0, -10), YARD.offset(12, 8, 20));
        for (Entity old : level.getEntities((Entity) null, area, e -> e instanceof ItemEntity)) {
            old.discard();
        }
        ServerPlayer bram = level.getServer().getPlayerList().getPlayerByName("Bram");
        if (bram != null) {
            undress(bram);
        }
    }

    // ================================================================ De wapens

    /** De negen wapens in je werkbalk (zie {@link #NAMES}). */
    private static void giveHotbar(ServerPlayer player) {
        var inventory = player.getInventory();
        inventory.clearContent();
        FirearmType[] firearms = FirearmType.values();
        for (int i = 0; i < firearms.length; i++) {
            inventory.setItem(i, FirearmItem.loaded(firearms[i], GunMaterial.STEEL));
        }
        GunType[] lasers = {GunType.PISTOL, GunType.RIFLE, GunType.SNIPER, GunType.SHOTGUN};
        for (int i = 0; i < lasers.length; i++) {
            ItemStack gun = LaserGunItem.complete(lasers[i], GunMaterial.TITANIUM);
            LaserGunItem.setCell(gun, 100);
            inventory.setItem(firearms.length + i, gun);
        }
    }

    /** Een Glock met een 9mm-demper en een red dot. */
    private static ItemStack suppressedGlock() {
        ItemStack gun = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        FirearmItem.setAttachment(gun, Attachment.SUPPRESSOR_9MM);
        FirearmItem.setAttachment(gun, Attachment.RED_DOT);
        return gun;
    }

    /** Het wapen dat Bram vasthoudt in stap 2 (zie {@code held} in {@link #tour}). */
    private static ItemStack heldGun(int index) {
        return switch (index) {
            case 0 -> FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
            case 1 -> suppressedGlock();
            case 2 -> FirearmItem.loaded(FirearmType.MP5, GunMaterial.STEEL);
            case 3 -> FirearmItem.loaded(FirearmType.AK_47, GunMaterial.STEEL);
            case 4 -> FirearmItem.loaded(FirearmType.AWP, GunMaterial.STEEL);
            case 5 -> FirearmItem.loaded(FirearmType.REMINGTON_870, GunMaterial.STEEL);
            case 6 -> laser(GunType.PISTOL);
            case 7 -> laser(GunType.RIFLE);
            case 8 -> laser(GunType.SNIPER);
            default -> laser(GunType.SHOTGUN);
        };
    }

    private static ItemStack laser(GunType type) {
        ItemStack gun = LaserGunItem.complete(type, GunMaterial.TITANIUM);
        LaserGunItem.setCell(gun, 100);
        return gun;
    }

    // ================================================================ Bram

    /** Bram (een nep-speler) op zijn plek, naar het westen gekeerd: zijn rechterkant wijst naar het noorden. */
    private static void bram(ServerPlayer player, Vec3 at) {
        ServerLevel level = player.serverLevel();
        ServerPlayer bram = DevWorld.fakePlayerAt(level, "Bram", BlockPos.containing(at));
        bram.teleportTo(level, at.x, at.y, at.z, 90, 0);
        bram.setYHeadRot(90);
        bram.setYBodyRot(90);
        undress(bram);
    }

    /** Bram houdt alleen dit vast (geen holster, geen sling). */
    private static void holdOnly(ServerPlayer player, ItemStack gun) {
        ServerPlayer bram = player.serverLevel().getServer().getPlayerList().getPlayerByName("Bram");
        if (bram == null) {
            return;
        }
        undress(bram);
        bram.getInventory().selected = 0;
        bram.getInventory().setItem(0, gun);
        bram.setYHeadRot(90);
        bram.setYBodyRot(90);
        showHands(bram);
    }

    /**
     * Wat Bram in zijn handen heeft naar je spel sturen. Een nep-speler tikt niet (er is geen echte verbinding die hem laat
     * tikken), dus Minecraft merkt zelf niet dat hij iets anders vasthoudt.
     */
    private static void showHands(ServerPlayer bram) {
        bram.serverLevel().getChunkSource().broadcast(bram, new ClientboundSetEquipmentPacket(bram.getId(), List.of(
                com.mojang.datafixers.util.Pair.of(EquipmentSlot.MAINHAND, bram.getMainHandItem().copy()),
                com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND, bram.getOffhandItem().copy()))));
    }

    /** Wat Bram draagt in stap 3: zijn handen leeg, een pistool in de holster en twee geweren aan hun sling. */
    private static void wear(ServerPlayer player, int set) {
        ServerPlayer bram = player.serverLevel().getServer().getPlayerList().getPlayerByName("Bram");
        if (bram == null) {
            return;
        }
        undress(bram);
        ItemStack pistol;
        ItemStack back;
        ItemStack chest;
        switch (set) {
            case 0 -> {
                pistol = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
                back = FirearmItem.loaded(FirearmType.AWP, GunMaterial.STEEL);
                chest = FirearmItem.loaded(FirearmType.MP5, GunMaterial.STEEL);
            }
            case 1 -> {
                pistol = laser(GunType.PISTOL);
                back = ShowcaseFeatures.slungGun(GunType.RIFLE, 100);
                chest = ShowcaseFeatures.slungGun(GunType.SNIPER, 100);
            }
            default -> {
                pistol = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
                back = FirearmItem.loaded(FirearmType.AK_47, GunMaterial.STEEL);
                chest = FirearmItem.loaded(FirearmType.REMINGTON_870, GunMaterial.STEEL);
            }
        }
        ItemStack holster = new ItemStack(ModItems.HOLSTER.get());
        GearItem.setContents(holster, List.of(pistol));
        Gear.set(bram, GearItem.Kind.HOLSTER, holster);
        Gear.setSling(bram, 0, back);
        Gear.setSling(bram, 1, chest);
        bram.setYHeadRot(90);
        bram.setYBodyRot(90);
    }

    /** Bram zonder iets: lege handen, geen holster, geen slings. */
    private static void undress(ServerPlayer bram) {
        bram.getInventory().clearContent();
        Gear.set(bram, GearItem.Kind.HOLSTER, ItemStack.EMPTY);
        Gear.setSling(bram, 0, ItemStack.EMPTY);
        Gear.setSling(bram, 1, ItemStack.EMPTY);
        showHands(bram);
    }

    /** Bram gaat aan de kant (naast het veldje), zonder iets. */
    private static void bramAway(ServerPlayer player) {
        ServerPlayer bram = player.serverLevel().getServer().getPlayerList().getPlayerByName("Bram");
        if (bram != null) {
            undress(bram);
            bram.teleportTo(player.serverLevel(), YARD.getX() + 9.5, YARD.getY() + 1, YARD.getZ() - 6.5, 180, 0);
        }
    }

    /**
     * Bram verlaat het spel (zoals een speler die uitlogt), zonder iets bij zich. Een latere ronde die "Bram" nodig heeft,
     * maakt hem opnieuw (DevWorld.fakePlayerAt), met zijn opgeslagen spullen: dus eerst alles uit.
     */
    private static void bramGone(ServerPlayer player) {
        ServerPlayer bram = player.serverLevel().getServer().getPlayerList().getPlayerByName("Bram");
        if (bram != null) {
            undress(bram);
            player.serverLevel().getServer().getPlayerList().remove(bram);
        }
    }

    /** Elk wapen op de grond, op een rij van west naar oost, een eind voor je. */
    private static void dropRow(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ItemStack[] guns = {heldGun(0), heldGun(2), heldGun(3), heldGun(4), heldGun(5), heldGun(6), heldGun(7), heldGun(8),
                heldGun(9)};
        for (int i = 0; i < guns.length; i++) {
            Vec3 at = new Vec3(YARD.getX() + 0.5 + (i - 4) * 1.6, YARD.getY() + 1.05, YARD.getZ() + 11.5);
            ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, guns[i], 0, 0, 0);
            item.setNeverPickUp();
            item.setUnlimitedLifetime();
            level.addFreshEntity(item);
        }
    }

    // ================================================================ Hulpjes

    private static void select(int slot) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.getInventory().selected = slot;
        }
    }

    /** De camera (jij, zwevend) met je ogen op {@code eye}, kijkend naar {@code to} (zoals VisualCheck.viewAt). */
    private static void viewAt(DevScript t, int wait, Vec3 eye, Vec3 to) {
        Vec3 d = to.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        t.view(wait, eye.x, eye.y - 1.62, eye.z, yaw, pitch);
    }
}
