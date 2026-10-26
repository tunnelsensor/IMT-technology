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
 * Test voor de acht creative-tabbladen (26 oktober). Jouw wens: "Voeg creative tabs toe in deze volgorde:
 * resources, equipment, weapons, power, interieur, security, robots, datacenter".
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
        check(order.equals(List.of("resources", "equipment", "weapons", "power", "interior", "security", "robots",
                "datacenter")), "De tabbladen staan in de verkeerde volgorde: " + order);

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

        Set<Item> nowhere = Set.of(ModItems.CRACKED_BLOCK.get(), ModItems.BLOCK_POWDER.get(), ModItems.RAVEN_RIFLE.get());
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
}
