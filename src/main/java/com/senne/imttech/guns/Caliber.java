package com.senne.imttech.guns;

import com.senne.imttech.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Een kaliber (hoofdstuk 103): welke patroon een vuurwapen schiet. Elk kaliber heeft zijn eigen patroon, zijn eigen
 * lege huls (die springt uit het wapen en raap je op) en zijn eigen kogelpunt; op de Reloading Bench pers je er weer een
 * patroon van. De Glock 19 en de MP5 schieten allebei <b>9mm</b>: hun patronen zijn dezelfde, hun magazijnen niet.
 */
public enum Caliber {
    /** 9x19mm Parabellum: de Glock 19 en de MP5. */
    MM9("9mm", "round_9mm", "casing_9mm", "bullet_9mm", 1);

    /** De naam in de bestanden ("9mm"). */
    public final String id;
    public final String roundId;
    public final String casingId;
    public final String bulletId;
    /** Zoveel Crushed Gunpowder kost één patroon op de Reloading Bench (een grotere patroon meer). */
    public final int powder;

    Caliber(String id, String roundId, String casingId, String bulletId, int powder) {
        this.id = id;
        this.roundId = roundId;
        this.casingId = casingId;
        this.bulletId = bulletId;
        this.powder = powder;
    }

    /** De patroon (het item). */
    public Item round() {
        return ModItems.round(this);
    }

    /** De lege huls. */
    public Item casing() {
        return ModItems.casing(this);
    }

    /** De kogelpunt (bij hagel: de korrels). */
    public Item bullet() {
        return ModItems.bullet(this);
    }

    public Component text() {
        return Component.translatable("caliber.imttech." + id);
    }

    public static Caliber byId(int id) {
        Caliber[] all = values();
        return all[Math.floorMod(id, all.length)];
    }

    /** Van welk kaliber deze patroon is, of null als het geen patroon is. */
    public static @Nullable Caliber ofRound(ItemStack stack) {
        for (Caliber caliber : values()) {
            if (stack.is(caliber.round())) {
                return caliber;
            }
        }
        return null;
    }

    /** Van welk kaliber deze lege huls is, of null. */
    public static @Nullable Caliber ofCasing(ItemStack stack) {
        for (Caliber caliber : values()) {
            if (stack.is(caliber.casing())) {
                return caliber;
            }
        }
        return null;
    }

    /** Van welk kaliber deze kogelpunt is, of null. */
    public static @Nullable Caliber ofBullet(ItemStack stack) {
        for (Caliber caliber : values()) {
            if (stack.is(caliber.bullet())) {
                return caliber;
            }
        }
        return null;
    }
}
