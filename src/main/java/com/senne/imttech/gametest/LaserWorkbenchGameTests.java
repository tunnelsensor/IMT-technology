package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunsmithRecipe;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.LaserGuns;
import com.senne.imttech.weapons.LaserPart;
import com.senne.imttech.weapons.LaserPartItem;
import com.senne.imttech.weapons.LaserParts;
import com.senne.imttech.weapons.LaserWorkbenchBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de lasergeweren uit onderdelen (hoofdstuk 55, stap 1). Jouw keuzes: de lasergeweren bouw je net als de
 * Glock uit onderdelen op een nieuwe tafel; twee kwaliteiten (titanium en netherite), heel duur; een Nether Star als
 * kern die je zichtbaar insteekt en weer uithaalt; een sling als los onderdeel (niet op een pistool); alles slijt
 * zoals bij de Glock, maar minder en zonder storingen.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class LaserWorkbenchGameTests {
    private LaserWorkbenchGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 4), name);
        player.getAbilities().instabuild = false;
        player.getInventory().selected = 0;
        return player;
    }

    private static LaserWorkbenchBlockEntity bench(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, ModBlocks.LASER_WORKBENCH.get());
        return (LaserWorkbenchBlockEntity) helper.getBlockEntity(at);
    }

    private static LaserGuns.Result fire(ServerPlayer player) {
        LaserGuns.forgetCooldown(player);
        return LaserGuns.fire(player, player.getYRot(), player.getXRot(), false);
    }

    /**
     * De Laser Workbench: het frame eerst (dat zegt welk geweer het wordt), een onderdeel van een ander geweer past niet,
     * zonder Nether Star schiet het niet, met de kern wel; de sling erop (niet op een pistool); alles komt er weer uit
     * met zijn conditie, en als het frame er als laatste uit gaat, is het geweer weg en krijg je de cel terug.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "weapons")
    public static void laser_workbench_builds_a_rifle_from_parts_core_and_sling(GameTestHelper helper) {
        ServerPlayer player = player(helper, "laser-smith");
        LaserWorkbenchBlockEntity bench = bench(helper, new BlockPos(2, 2, 2));
        var inventory = player.getInventory();
        inventory.setItem(0, LaserPartItem.of(GunType.RIFLE, LaserPart.FRAME, GunMaterial.TITANIUM));
        inventory.setItem(1, LaserPartItem.of(GunType.SNIPER, LaserPart.EMITTER, GunMaterial.TITANIUM));
        inventory.setItem(2, LaserPartItem.of(GunType.RIFLE, LaserPart.RECEIVER, GunMaterial.NETHERITE, 80));
        inventory.setItem(3, LaserPartItem.of(GunType.RIFLE, LaserPart.EMITTER, GunMaterial.TITANIUM));
        inventory.setItem(4, LaserPartItem.of(GunType.RIFLE, LaserPart.OPTICS, GunMaterial.TITANIUM));
        inventory.setItem(5, LaserPartItem.of(GunType.RIFLE, LaserPart.CAPACITOR, GunMaterial.TITANIUM));
        inventory.setItem(6, LaserPartItem.of(GunType.RIFLE, LaserPart.TRIGGER, GunMaterial.TITANIUM));
        inventory.setItem(7, new ItemStack(Items.NETHER_STAR));
        inventory.setItem(8, new ItemStack(ModItems.GUN_SLING.get()));
        inventory.setItem(9, LaserPartItem.of(GunType.PISTOL, LaserPart.FRAME, GunMaterial.TITANIUM));
        check(!bench.install(player, 2), "Een receiver op een lege tafel hoort niet te kunnen (eerst een frame)");
        check(!bench.install(player, 7), "Een Nether Star op een lege tafel hoort niet te kunnen (eerst een frame)");
        check(bench.install(player, 0), "Het frame ging er niet in");
        check(LaserGunItem.typeOf(bench.gun()) == GunType.RIFLE, "Het frame van een rifle maakte geen rifle");
        check(inventory.getItem(0).isEmpty(), "Het frame bleef in je inventaris");
        check(!bench.install(player, 1), "Een emitter van een sniper paste op een rifle");
        for (int slot = 2; slot <= 6; slot++) {
            check(bench.install(player, slot), "Onderdeel uit vakje " + slot + " ging er niet in");
        }
        check(!bench.install(player, 3), "Een onderdeel ging in een bezet vak (vakje 3 is nu leeg, dus install hoort false te geven)");
        LaserParts parts = LaserGunItem.parts(bench.gun());
        check(!parts.complete() && parts.missing().equals(List.of(LaserPart.CORE)), "Zonder Nether Star hoort alleen de kern te ontbreken: " + parts.missing());
        check(parts.get(LaserPart.RECEIVER).material() == GunMaterial.NETHERITE && parts.get(LaserPart.RECEIVER).condition() == 80,
                "De netherite receiver hield zijn kwaliteit en conditie niet");
        // Zonder kern: meenemen, een cel erin, en schieten gaat niet.
        check(bench.takeGun(player), "Meenemen lukte niet");
        ItemStack gun = ItemStack.EMPTY;
        int gunSlot = -1;
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (LaserGunItem.typeOf(inventory.getItem(slot)) == GunType.RIFLE) {
                gun = inventory.getItem(slot);
                gunSlot = slot;
            }
        }
        check(gunSlot >= 0, "Het geweer kwam niet in je inventaris");
        LaserGunItem.setCell(gun, EnergyCellItem.FULL);
        inventory.setItem(gunSlot, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        check(fire(player) == LaserGuns.Result.INCOMPLETE, "Zonder Nether Star schoot het geweer");
        // Terug op de tafel: de kern en de sling erin.
        check(bench.putGun(player, 0), "Het geweer ging niet terug op de tafel");
        check(bench.install(player, 7), "De Nether Star ging er niet in");
        check(bench.install(player, 8), "De sling ging er niet op");
        parts = LaserGunItem.parts(bench.gun());
        check(parts.complete() && parts.hasCore() && parts.hasSling(), "Het geweer hoort nu compleet te zijn, met kern en sling");
        check(bench.takeGun(player), "Meenemen lukte niet (2)");
        gun = inventory.getItem(0);
        check(LaserGunItem.typeOf(gun) == GunType.RIFLE && LaserGunItem.hasSling(gun), "Het geweer met sling kwam niet in vakje 0");
        check(fire(player) == LaserGuns.Result.FIRED, "Het complete geweer schoot niet");
        // Alles er weer uit: de sling als sling, de kern als Nether Star, het frame pas als laatste, en de cel terug.
        check(bench.putGun(player, 0), "Het geweer ging niet terug op de tafel (2)");
        check(!bench.remove(player, LaserPart.FRAME), "Het frame kon eruit terwijl de rest er nog in zat");
        check(bench.remove(player, LaserPart.SLING), "De sling kon er niet af");
        check(inventory.countItem(ModItems.GUN_SLING.get()) == 1, "De sling kwam niet terug als Gun Sling");
        check(bench.remove(player, LaserPart.CORE), "De kern kon er niet uit");
        check(inventory.countItem(Items.NETHER_STAR) == 1, "De Nether Star kwam niet terug");
        for (LaserPart part : new LaserPart[]{LaserPart.RECEIVER, LaserPart.EMITTER, LaserPart.OPTICS, LaserPart.CAPACITOR, LaserPart.TRIGGER}) {
            check(bench.remove(player, part), "Het onderdeel " + part + " kon er niet uit");
        }
        check(bench.remove(player, LaserPart.FRAME), "Het frame kon er niet als laatste uit");
        check(bench.gun().isEmpty(), "De tafel hoort leeg te zijn");
        int cells = 0;
        int netherite = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.is(ModItems.ENERGY_CELL.get())) {
                cells++;
            }
            if (LaserPartItem.partOf(stack) == LaserPart.RECEIVER && LaserPartItem.materialOf(stack) == GunMaterial.NETHERITE
                    && LaserPartItem.conditionOf(stack) == 80) {
                netherite++;
            }
        }
        check(cells == 1, "De cel kwam niet terug uit het uit elkaar gehaalde geweer: " + cells);
        check(netherite == 1, "De netherite receiver kwam niet terug met zijn conditie");
        // Een pistool: wel een frame, geen sling.
        check(bench.install(player, 9), "Het frame van het pistool ging er niet in");
        check(LaserGunItem.typeOf(bench.gun()) == GunType.PISTOL, "Het frame van een pistool maakte geen pistool");
        int slingSlot = -1;
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (inventory.getItem(slot).is(ModItems.GUN_SLING.get())) {
                slingSlot = slot;
            }
        }
        check(slingSlot >= 0 && !bench.install(player, slingSlot), "Een sling paste op een pistool");
        helper.succeed();
    }

    /**
     * Slijtage (jouw keuze: zoals bij de Glock, maar minder en zonder storingen): met de kans op 100% slijt elk onderdeel
     * bij elk schot een punt; een versleten capacitor kost meer lading per schot, een versleten trigger schiet trager; een
     * kapot onderdeel = het geweer doet niets tot je het vervangt.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "weapons")
    public static void laser_parts_wear_and_a_broken_part_stops_the_gun(GameTestHelper helper) {
        ServerPlayer player = player(helper, "laser-wearer");
        ItemStack gun = LaserGunItem.loaded(GunType.PISTOL);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        float[] wear = ImtConfig.Guns.WEAR_CHANCE.clone();
        float factor = ImtConfig.Guns.LASER_WEAR_FACTOR;
        ImtConfig.Guns.WEAR_CHANCE = new float[]{4.0f, 2.0f, 100.0f, 100.0f};
        ImtConfig.Guns.LASER_WEAR_FACTOR = 1.0f;
        try {
            check(fire(player) == LaserGuns.Result.FIRED, "Het pistool schoot niet");
            LaserParts parts = LaserGunItem.parts(player.getMainHandItem());
            for (LaserPart part : LaserPart.wearing()) {
                check(parts.get(part).condition() == PartState.NEW - 1, "Het onderdeel " + part + " sleet niet precies een punt: " + parts.get(part).condition());
            }
            check(parts.get(LaserPart.CORE).condition() == PartState.NEW, "De Nether Star sleet, en die hoort niet te slijten");
            // Een versleten capacitor en trigger: duurder en trager; een halve wearChance geeft minder slijtage dan de Glock.
            ImtConfig.Guns.LASER_WEAR_FACTOR = 0.5f;
            check(LaserGuns.wearChance(GunMaterial.TITANIUM) == 50.0f, "laserWearFactor telt niet mee in de slijtagekans");
            float plainCost = LaserGuns.shotCost(gun);
            int plainInterval = LaserGuns.interval(gun);
            LaserGunItem.setParts(gun, parts.with(LaserPart.CAPACITOR, new PartState(GunMaterial.TITANIUM, 10))
                    .with(LaserPart.TRIGGER, new PartState(GunMaterial.TITANIUM, 10)));
            check(LaserGuns.shotCost(gun) > plainCost, "Een versleten capacitor kost niet meer lading");
            check(LaserGuns.interval(gun) > plainInterval, "Een versleten trigger schiet niet trager");
            // Kapot: dood tot je het vervangt.
            LaserGunItem.setParts(gun, LaserGunItem.parts(gun).with(LaserPart.EMITTER, new PartState(GunMaterial.TITANIUM, 0)));
            check(fire(player) == LaserGuns.Result.BROKEN, "Met een kapotte emitter schoot het pistool");
            check(!LaserGunItem.works(gun), "works() zegt dat een geweer met een kapot onderdeel werkt");
        } finally {
            ImtConfig.Guns.WEAR_CHANCE = wear;
            ImtConfig.Guns.LASER_WEAR_FACTOR = factor;
        }
        helper.succeed();
    }

    /**
     * De recepten (jouw keuze: heel duur, endgame): een netherite onderdeel is een titanium onderdeel plus twee Netherite
     * Ingots; de oude recepten van de geweren zelf zijn weg (die bouw je op de tafel). Sinds hoofdstuk 78 maak je het op
     * de Gunsmith Table (een eigen soort recept), niet meer in de crafting table. En een geweer uit een oude wereld
     * (zonder onderdelen) krijgt er complete titanium onderdelen en een kern bij zodra het in een inventaris ligt.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "weapons")
    public static void netherite_upgrade_recipe_and_old_guns_get_parts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var holder = level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "laser_rifle_emitter_netherite"));
        check(holder.isPresent() && holder.get().value() instanceof GunsmithRecipe,
                "Er is geen recept op de Gunsmith Table voor een netherite emitter (titanium emitter + 2 netherite ingots)");
        GunsmithRecipe recipe = (GunsmithRecipe) holder.get().value();
        List<ItemStack> ingredients = List.of(LaserPartItem.of(GunType.RIFLE, LaserPart.EMITTER, GunMaterial.TITANIUM, 55),
                new ItemStack(Items.NETHERITE_INGOT), new ItemStack(Items.NETHERITE_INGOT));
        var input = new GunsmithRecipe.Carried(ingredients);
        check(recipe.matches(input, level), "Een gebruikte titanium emitter met twee Netherite Ingots past niet op het recept");
        ItemStack result = recipe.assemble(input, level.registryAccess());
        check(LaserPartItem.partOf(result) == LaserPart.EMITTER && LaserPartItem.gunOf(result) == GunType.RIFLE
                && LaserPartItem.materialOf(result) == GunMaterial.NETHERITE && LaserPartItem.conditionOf(result) == PartState.NEW,
                "Het recept geeft geen nieuwe netherite rifle emitter: " + result);
        var wrong = new GunsmithRecipe.Carried(List.of(LaserPartItem.of(GunType.RIFLE, LaserPart.EMITTER, GunMaterial.NETHERITE),
                new ItemStack(Items.NETHERITE_INGOT), new ItemStack(Items.NETHERITE_INGOT)));
        check(!recipe.matches(wrong, level), "Een netherite onderdeel werd nog eens netherite");
        check(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 1, ingredients), level).isEmpty(),
                "De crafting table maakt nog een netherite emitter");
        for (GunType type : GunType.values()) {
            check(level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, type.id)).isEmpty(),
                    "Het oude recept van " + type.id + " bestaat nog");
        }
        check(level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "laser_workbench")).isPresent(),
                "Er is geen recept voor de Laser Workbench");
        // Een oud geweer.
        ServerPlayer player = player(helper, "laser-veteran");
        ItemStack old = new ItemStack(ModItems.LASER_RIFLE.get());
        check(!LaserGunItem.works(old), "Een geweer zonder onderdelen hoort niet te werken");
        ModItems.LASER_RIFLE.get().inventoryTick(old, level, player, 0, false);
        check(LaserGunItem.works(old) && LaserGunItem.parts(old).hasCore() && !LaserGunItem.hasSling(old),
                "Een oud geweer kreeg geen complete titanium onderdelen met een kern");
        helper.succeed();
    }
}
