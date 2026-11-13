package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.registry.ModCreativeTabs;
import com.senne.imttech.registry.ModItems;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Test voor de creative-tabbladen. Eerst acht (26 oktober, jouw wens: "Voeg creative tabs toe in deze volgorde:
 * resources, equipment, weapons, power, interieur, security, robots, datacenter"), sinds 13 november tien (hoofdstuk 104,
 * jouw wens: "holster tot en met utility pouch moet verhuizen naar equipment, al de gun onderdelen (niet mags, bullets)
 * moeten op hun eigen tab 'Gun parts', al de attachments moeten ook op een eigen tab 'Attachments'").
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class CreativeTabGameTests {
    private CreativeTabGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Elk item van de mod staat in precies één tabblad, en de tabbladen staan in de goede volgorde.
     * Alleen deze drie staan nergens: een gebarsten blok en blokpoeder bestaan alleen "van" een blok,
     * en het Raven-geweer hebben alleen de Raven-soldaten.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void every_item_is_in_one_creative_tab(GameTestHelper helper) {
        List<String> order = ModCreativeTabs.TABS.getEntries().stream().map(tab -> tab.getId().getPath()).toList();
        check(order.equals(List.of("resources", "equipment", "weapons", "gun_parts", "attachments", "power", "interior",
                "security", "robots", "datacenter")), "De tabbladen staan in de verkeerde volgorde: " + order);

        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
        Map<Item, Set<String>> tabsOf = new HashMap<>();
        for (DeferredHolder<CreativeModeTab, ? extends CreativeModeTab> tab : ModCreativeTabs.TABS.getEntries()) {
            tab.get().buildContents(parameters);
            check(!tab.get().getDisplayItems().isEmpty(), "Het tabblad " + tab.getId() + " is leeg");
            for (ItemStack stack : tab.get().getDisplayItems()) {
                tabsOf.computeIfAbsent(stack.getItem(), item -> new HashSet<>()).add(tab.getId().getPath());
            }
        }

        // De oude Raven Turret (hoofdstuk 54: nu een laserturret) blijft bestaan voor oude werelden, maar staat nergens meer.
        Set<Item> nowhere = Set.of(ModItems.CRACKED_BLOCK.get(), ModItems.BLOCK_POWDER.get(), ModItems.RAVEN_TURRET.get());
        for (DeferredHolder<Item, ? extends Item> holder : ModItems.ITEMS.getEntries()) {
            Item item = holder.get();
            Set<String> tabs = tabsOf.getOrDefault(item, Set.of());
            if (nowhere.contains(item)) {
                check(tabs.isEmpty(), holder.getId() + " hoort in geen enkel tabblad, maar staat in " + tabs);
            } else {
                check(tabs.size() == 1, holder.getId() + " moet in precies één tabblad staan, maar staat in " + tabs);
            }
        }
        // En andersom: geen items van een andere mod of van vanilla in onze tabbladen.
        for (Item item : tabsOf.keySet()) {
            check(BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ImtTech.MODID),
                    BuiltInRegistries.ITEM.getKey(item) + " staat in een I.M.T-tabblad maar is niet van I.M.T");
        }
        helper.succeed();
    }

    /** Wat in elk tabblad staat (zoals in het creative-menu): per tabblad de items. */
    private static Map<String, Set<Item>> contents(GameTestHelper helper) {
        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
        Map<String, Set<Item>> contents = new HashMap<>();
        for (DeferredHolder<CreativeModeTab, ? extends CreativeModeTab> tab : ModCreativeTabs.TABS.getEntries()) {
            tab.get().buildContents(parameters);
            Set<Item> items = new HashSet<>();
            for (ItemStack stack : tab.get().getDisplayItems()) {
                items.add(stack.getItem());
            }
            contents.put(tab.getId().getPath(), items);
        }
        return contents;
    }

    /**
     * Hoofdstuk 104: de holster, de pouches, de rugzakken en de MOLLE-pouches (tot en met de Utility Pouch) staan bij
     * Equipment; alle onderdelen van de vuurwapens en de lasergeweren, en de Gun Sling, bij Gun Parts (en daar niets
     * anders); de attachments bij Attachments (en daar niets anders). De geweren zelf, hun magazijnen (ook de lange) en
     * hun munitie blijven bij Weapons.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void gear_gun_parts_and_attachments_have_their_own_tabs(GameTestHelper helper) {
        Map<String, Set<Item>> tabs = contents(helper);
        Set<Item> equipment = tabs.get("equipment");
        Set<Item> weapons = tabs.get("weapons");
        Set<Item> parts = tabs.get("gun_parts");
        Set<Item> attachments = tabs.get("attachments");
        check(equipment != null && weapons != null && parts != null && attachments != null, "Een tabblad ontbreekt: " + tabs.keySet());

        // Equipment: de holster tot en met de Utility Pouch.
        List<Item> gear = new java.util.ArrayList<>(List.of(ModItems.HOLSTER.get(), ModItems.GRENADE_POUCH.get(), ModItems.MAG_POUCH.get()));
        for (com.senne.imttech.backpack.BackpackItem.Size size : com.senne.imttech.backpack.BackpackItem.Size.values()) {
            gear.add(com.senne.imttech.backpack.BackpackRegistry.item(size));
        }
        for (com.senne.imttech.backpack.MolleItem.Kind kind : com.senne.imttech.backpack.MolleItem.Kind.values()) {
            gear.add(com.senne.imttech.backpack.BackpackRegistry.molle(kind));
        }
        for (Item item : gear) {
            check(equipment.contains(item), item + " staat niet bij Equipment");
            check(!weapons.contains(item), item + " staat nog bij Weapons");
        }

        // Gun Parts: elk onderdeel van elk vuurwapen en elk lasergeweer, en de sling; niets anders.
        Set<Item> wantedParts = new HashSet<>();
        for (com.senne.imttech.guns.FirearmType type : com.senne.imttech.guns.FirearmType.values()) {
            for (com.senne.imttech.guns.GunPart part : com.senne.imttech.guns.GunPart.values()) {
                wantedParts.add(ModItems.gunPart(type, part));
            }
        }
        for (com.senne.imttech.weapons.GunType type : com.senne.imttech.weapons.GunType.values()) {
            for (com.senne.imttech.weapons.LaserPart part : com.senne.imttech.weapons.LaserPart.wearing()) {
                wantedParts.add(ModItems.laserPart(type, part));
            }
        }
        wantedParts.add(ModItems.GUN_SLING.get());
        check(parts.equals(wantedParts), "Gun Parts klopt niet: te veel " + difference(parts, wantedParts) + ", te weinig "
                + difference(wantedParts, parts));

        // Attachments: alle attachments, niets anders.
        Set<Item> wantedAttachments = new HashSet<>();
        for (com.senne.imttech.guns.Attachment attachment : com.senne.imttech.guns.Attachment.values()) {
            wantedAttachments.add(ModItems.attachment(attachment));
        }
        check(attachments.equals(wantedAttachments), "Attachments klopt niet: te veel " + difference(attachments, wantedAttachments)
                + ", te weinig " + difference(wantedAttachments, attachments));

        // Weapons: de geweren, hun magazijnen (ook de lange) en hun munitie blijven daar.
        for (com.senne.imttech.guns.FirearmType type : com.senne.imttech.guns.FirearmType.values()) {
            check(weapons.contains(ModItems.firearm(type)), type + " staat niet bij Weapons");
            if (!type.tube()) {
                check(weapons.contains(ModItems.magazine(type)) && weapons.contains(ModItems.magazine(type, true)),
                        "De magazijnen van de " + type + " staan niet bij Weapons");
            }
        }
        for (com.senne.imttech.guns.Caliber caliber : com.senne.imttech.guns.Caliber.values()) {
            check(weapons.contains(caliber.round()) && weapons.contains(caliber.casing()) && weapons.contains(caliber.bullet()),
                    "De munitie van " + caliber + " staat niet bij Weapons");
        }
        helper.succeed();
    }

    /** Wat in {@code a} zit en niet in {@code b}, als namen. */
    private static List<String> difference(Set<Item> a, Set<Item> b) {
        return a.stream().filter(item -> !b.contains(item)).map(item -> BuiltInRegistries.ITEM.getKey(item).getPath()).sorted().toList();
    }
}
