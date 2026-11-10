package com.senne.imttech.backpack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * De vakjes van een open rugzak, op de server (hoofdstuk 72). Elke verandering gaat meteen terug naar de rugzak zelf
 * (het data component minecraft:container), waar hij ook zit (zie BackpackPlace).
 *
 * Hebben twee mensen dezelfde rugzak open (twee spelers bij een neergezette rugzak, of in stap 3 jij en een dief), dan
 * delen ze <b>deze ene</b> inhoud, net als bij een kist: wat de een pakt, verdwijnt bij de ander. Daarvoor krijgt elke
 * rugzak een eigen nummer (BackpackRegistry.PACK_ID) en houden we per nummer bij welke inhoud er open is.
 */
public class BackpackInventory extends SimpleContainer {
    /** Wat er nu open is, per rugzaknummer. */
    private static final Map<UUID, BackpackInventory> OPEN = new HashMap<>();

    private final BackpackPlace place;
    private final UUID id;
    private final BackpackItem.Size size;
    private int viewers;
    /** Bezig met inladen (dan schrijven we niet terug). */
    private boolean loading;

    private BackpackInventory(BackpackPlace place, UUID id, BackpackItem.Size size, List<ItemStack> contents) {
        super(size.slots());
        this.place = place;
        this.id = id;
        this.size = size;
        loading = true;
        for (int i = 0; i < contents.size() && i < size.slots(); i++) {
            setItem(i, contents.get(i));
        }
        loading = false;
    }

    /**
     * De inhoud van de rugzak op deze plek, om te openen: dezelfde als iemand hem al open heeft, anders een nieuwe. Geeft
     * null als daar geen rugzak (meer) zit.
     */
    public static BackpackInventory open(BackpackPlace place) {
        ItemStack pack = place.get();
        BackpackItem.Size size = BackpackItem.sizeOf(pack);
        if (size == null) {
            return null;
        }
        UUID id = pack.get(BackpackRegistry.PACK_ID.get());
        if (id == null) {
            id = UUID.randomUUID();
            ItemStack numbered = pack.copy();
            numbered.set(BackpackRegistry.PACK_ID.get(), id);
            place.set(numbered);
        }
        BackpackInventory open = OPEN.get(id);
        if (open != null && open.valid()) {
            return open;
        }
        open = new BackpackInventory(place, id, size, BackpackItem.contents(pack));
        OPEN.put(id, open);
        return open;
    }

    /** Alles vergeten (de server stopt). */
    public static void clear() {
        OPEN.clear();
    }

    /** Is deze rugzak nu open (bij iemand)? */
    public static boolean isOpen(ItemStack pack) {
        UUID id = pack.get(BackpackRegistry.PACK_ID.get());
        return id != null && OPEN.containsKey(id);
    }

    public BackpackPlace place() {
        return place;
    }

    public BackpackItem.Size size() {
        return size;
    }

    /** Zit dezelfde rugzak nog op zijn plek? (Anders gaat elk scherm ervan dicht, zodat er niets dubbel komt.) */
    public boolean valid() {
        ItemStack pack = place.get();
        return BackpackItem.sizeOf(pack) == size && id.equals(pack.get(BackpackRegistry.PACK_ID.get()));
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return BackpackItem.mayHold(stack);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (loading || !valid()) {
            return;
        }
        ItemStack pack = place.get().copy();
        List<ItemStack> contents = new ArrayList<>(getContainerSize());
        for (int i = 0; i < getContainerSize(); i++) {
            contents.add(getItem(i).copy());
        }
        BackpackItem.setContents(pack, contents);
        place.set(pack);
    }

    @Override
    public void startOpen(Player player) {
        super.startOpen(player);
        viewers++;
    }

    @Override
    public void stopOpen(Player player) {
        super.stopOpen(player);
        viewers = Math.max(0, viewers - 1);
        if (viewers == 0 && OPEN.get(id) == this) {
            OPEN.remove(id);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // het menu kijkt zelf (BackpackMenu.stillValid)
    }

    /**
     * De sorteerknop (jouw keuze): gelijke stapels samenvoegen en alles op een rij, op soort (de naam van het item) en
     * dan de grootste stapel eerst. Lege vakjes achteraan.
     */
    public void sort() {
        List<ItemStack> merged = new ArrayList<>();
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i).copy();
            if (stack.isEmpty()) {
                continue;
            }
            for (ItemStack into : merged) {
                if (stack.isEmpty()) {
                    break;
                }
                if (ItemStack.isSameItemSameComponents(into, stack) && into.getCount() < into.getMaxStackSize()) {
                    int moved = Math.min(stack.getCount(), into.getMaxStackSize() - into.getCount());
                    into.grow(moved);
                    stack.shrink(moved);
                }
            }
            if (!stack.isEmpty()) {
                merged.add(stack);
            }
        }
        merged.sort((a, b) -> {
            int byName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(a.getItem()).toString()
                    .compareTo(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(b.getItem()).toString());
            return byName != 0 ? byName : Integer.compare(b.getCount(), a.getCount());
        });
        loading = true;
        for (int i = 0; i < getContainerSize(); i++) {
            setItem(i, i < merged.size() ? merged.get(i) : ItemStack.EMPTY);
        }
        loading = false;
        setChanged();
    }
}
