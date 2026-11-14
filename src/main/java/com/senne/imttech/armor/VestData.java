package com.senne.imttech.armor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/**
 * Wat er in een Plate Carrier zit (hoofdstuk 109): de plate voor ({@code front}) en de plate achter ({@code back}), de
 * drie magazijnen (jouw wens: "de bodyarmor kan ook mags hebben (3)"), en de tekst op je rug met zijn kleur (jouw wens:
 * "op de achterkant van je vest zelf een tekst zetten zoals 'SWAT'"). Een data component moet {@code equals} hebben en een
 * ItemStack heeft dat niet; daarom vergelijkt dit record de items op wat ze zijn (zoals GearContents).
 *
 * @param textColour de kleur van de tekst: het nummer van een Minecraft-kleurstof (0 = wit, 15 = zwart)
 */
public record VestData(ItemStack front, ItemStack back, List<ItemStack> mags, String text, int textColour) {
    /** Zoveel magazijnen passen in het vest (jouw wens: drie). */
    public static final int MAGS = 3;
    /** Zo lang mag de tekst op je rug zijn (SWAT, POLICE, SECURITY...). */
    public static final int MAX_TEXT = 12;

    public static final VestData EMPTY = new VestData(ItemStack.EMPTY, ItemStack.EMPTY, List.of(), "", DyeColor.WHITE.getId());

    public static final Codec<VestData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("front", ItemStack.EMPTY).forGetter(VestData::front),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("back", ItemStack.EMPTY).forGetter(VestData::back),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("mags", List.of()).forGetter(VestData::mags),
            Codec.STRING.optionalFieldOf("text", "").forGetter(VestData::text),
            Codec.INT.optionalFieldOf("text_colour", DyeColor.WHITE.getId()).forGetter(VestData::textColour)
    ).apply(instance, VestData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, VestData> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, VestData::front,
            ItemStack.OPTIONAL_STREAM_CODEC, VestData::back,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, VestData::mags,
            ByteBufCodecs.STRING_UTF8, VestData::text,
            ByteBufCodecs.VAR_INT, VestData::textColour,
            VestData::new);

    public VestData {
        front = front.copy();
        back = back.copy();
        // Altijd precies drie vakjes (een oud of kapot vest vult het rijtje aan).
        List<ItemStack> copy = new ArrayList<>(MAGS);
        for (int i = 0; i < MAGS; i++) {
            copy.add(i < mags.size() ? mags.get(i).copy() : ItemStack.EMPTY);
        }
        mags = List.copyOf(copy);
        text = clean(text);
        textColour = Mth.clamp(textColour, 0, DyeColor.values().length - 1);
    }

    /**
     * Wat er van een getypte tekst op je rug komt: hooguit {@link #MAX_TEXT} tekens, zonder tekens die het lettertype niet
     * kan tekenen (enters, tabs, kleurcodes met §), en zonder spaties aan het begin of eind.
     */
    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length() && out.length() < MAX_TEXT; i++) {
            char c = text.charAt(i);
            if (c >= ' ' && c != '§' && c != 127) {
                out.append(c);
            }
        }
        return out.toString().strip();
    }

    /** De kleur van de tekst als kleurstof. */
    public DyeColor dye() {
        return DyeColor.byId(textColour);
    }

    /** De plate aan deze kant (een kopie). */
    public ItemStack plate(boolean front) {
        return (front ? this.front : this.back).copy();
    }

    /** Hetzelfde vest met deze plate aan deze kant. */
    public VestData withPlate(boolean front, ItemStack plate) {
        return front ? new VestData(plate, back, mags, text, textColour) : new VestData(this.front, plate, mags, text, textColour);
    }

    /** De magazijnen (veranderbare kopieën, altijd drie). */
    public List<ItemStack> magazines() {
        List<ItemStack> list = new ArrayList<>(MAGS);
        for (ItemStack stack : mags) {
            list.add(stack.copy());
        }
        return list;
    }

    public VestData withMagazines(List<ItemStack> magazines) {
        return new VestData(front, back, magazines, text, textColour);
    }

    public VestData withText(String text, int colour) {
        return new VestData(front, back, mags, text, colour);
    }

    /** Zoveel magazijnvakjes zijn bezet. */
    public int filledMagazines() {
        int count = 0;
        for (ItemStack stack : mags) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public boolean isEmpty() {
        return front.isEmpty() && back.isEmpty() && filledMagazines() == 0 && text.isEmpty() && textColour == DyeColor.WHITE.getId();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VestData that) || that.textColour != textColour || !that.text.equals(text)
                || !ItemStack.matches(front, that.front) || !ItemStack.matches(back, that.back) || that.mags.size() != mags.size()) {
            return false;
        }
        for (int i = 0; i < mags.size(); i++) {
            if (!ItemStack.matches(mags.get(i), that.mags.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 31 * text.hashCode() + textColour;
        hash = 31 * hash + ItemStack.hashItemAndComponents(front) + front.getCount();
        hash = 31 * hash + ItemStack.hashItemAndComponents(back) + back.getCount();
        for (ItemStack stack : mags) {
            hash = 31 * hash + ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount();
        }
        return hash;
    }
}
