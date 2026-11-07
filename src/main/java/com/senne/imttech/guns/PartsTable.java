package com.senne.imttech.guns;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Een tafel waarop je een wapen uit onderdelen in elkaar sleept (hoofdstuk 55): de Gunsmith Table (de Glock, zie
 * {@link GunsmithTableBlockEntity}) en de Laser Workbench (de lasergeweren, zie weapons/LaserWorkbenchBlockEntity).
 * Allebei gebruiken ze hetzelfde menu ({@link GunsmithMenu}): alles wat je sleept gaat als "knop" naar de server, en
 * dit zijn de vier dingen die zo'n knop kan vragen. Welk onderdeel {@code partIndex} is, weet de tafel zelf (de
 * volgorde van zijn eigen soort onderdelen).
 */
public interface PartsTable {
    /** Het wapen dat op de tafel ligt (leeg als er geen ligt). */
    ItemStack gun();

    /** Het onderdeel uit dit vakje van je inventaris in het wapen. */
    boolean install(ServerPlayer player, int inventorySlot);

    /** Dit onderdeel (zijn nummer in de lijst van de tafel) uit het wapen, terug in je inventaris. */
    boolean remove(ServerPlayer player, int partIndex);

    /** Een wapen uit dit vakje van je inventaris op de tafel leggen. */
    boolean putGun(ServerPlayer player, int inventorySlot);

    /** Het wapen van de tafel meenemen. */
    boolean takeGun(ServerPlayer player);
}
