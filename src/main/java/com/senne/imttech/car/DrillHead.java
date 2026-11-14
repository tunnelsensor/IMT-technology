package com.senne.imttech.car;

import com.mojang.serialization.Codec;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModItems;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De boorkop van een RC Car (hoofdstuk 108, jouw wens: "zorg ook dat je de drill head kunt upgraden, zodat een basic
 * drill head wel even duurt om iets te hakken; netherite is de snelheid dat die nu hakt").
 *
 * Vier soorten, in de materialen van de mod (dezelfde ladder als je wapens en je plates): ijzer, steel, titanium en
 * netherite. Een nieuwe auto heeft de ijzeren kop. Een betere kop zet je erop met rechtsklik (de oude krijg je terug).
 * <ul>
 *   <li><b>Hoe snel</b>: netherite boort zo snel als jij met ijzeren gereedschap (dat was de snelheid van de auto vóór er
 *       koppen waren); de andere zijn een deel daarvan (config [rcCar] drillSpeedIron, ...Steel, ...Titanium,
 *       ...Netherite). Met de ijzeren kop duurt een blok steen anderhalve seconde, met netherite nog geen halve.</li>
 *   <li><b>Wat hij aankan</b>: ijzer en steel breken wat ijzeren gereedschap breekt; titanium (daar zit diamant in) en
 *       netherite breken ook wat een diamanten pickaxe vraagt: obsidiaan en ancient debris.</li>
 * </ul>
 */
public enum DrillHead implements StringRepresentable {
    IRON("iron", "Iron", 0xD8D4CC),
    STEEL("steel", "Steel", 0x9AA6BA),
    TITANIUM("titanium", "Titanium", 0xA8D0EC),
    NETHERITE("netherite", "Netherite", 0x8E7C84);

    public static final Codec<DrillHead> CODEC = StringRepresentable.fromEnum(DrillHead::values);
    public static final StreamCodec<ByteBuf, DrillHead> STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(DrillHead::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), DrillHead::ordinal);

    private final String id;
    private final String label;
    private final int colour;
    /** Het gereedschap waar deze kop voor telt (pickaxe, schep, bijl en schoffel); pas gemaakt als het nodig is. */
    private ItemStack @Nullable [] tools;

    DrillHead(String id, String label, int colour) {
        this.id = id;
        this.label = label;
        this.colour = colour;
    }

    /** Zijn naam in opgeslagen gegevens: "iron", "steel", "titanium" of "netherite". */
    @Override
    public String getSerializedName() {
        return id;
    }

    /** Het materiaal, in het Engels ("Iron"). */
    public String label() {
        return label;
    }

    /** De naam van het item ("Iron Drill Head"). */
    public String displayName() {
        return label + " Drill Head";
    }

    /** De kleur van dit materiaal, voor tekst op een scherm. */
    public int colour() {
        return colour;
    }

    /** De kop met deze naam; een naam die we niet kennen (of geen) is de ijzeren kop. */
    public static DrillHead byId(@Nullable String id) {
        if (id != null) {
            for (DrillHead head : values()) {
                if (head.id.equals(id.toLowerCase(Locale.ROOT))) {
                    return head;
                }
            }
        }
        return IRON;
    }

    /** De kop met dit nummer (0 = ijzer ... 3 = netherite); een nummer dat niet bestaat is de ijzeren kop. */
    public static DrillHead byIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : IRON;
    }

    /** Welke kop dit item is, of null als het geen boorkop is. */
    public static @Nullable DrillHead of(ItemStack stack) {
        return stack.getItem() instanceof DrillHeadItem item ? item.head() : null;
    }

    /** Het item van deze kop. */
    public Item item() {
        return switch (this) {
            case IRON -> ModItems.IRON_DRILL_HEAD.get();
            case STEEL -> ModItems.STEEL_DRILL_HEAD.get();
            case TITANIUM -> ModItems.TITANIUM_DRILL_HEAD.get();
            case NETHERITE -> ModItems.NETHERITE_DRILL_HEAD.get();
        };
    }

    /**
     * Hoe snel deze kop boort, als deel van de snelheid van netherite (config; netherite is 1). Nooit nul: dan zou een
     * blok nooit breken.
     */
    public float speed() {
        float speed = switch (this) {
            case IRON -> ImtConfig.RcCar.DRILL_SPEED_IRON;
            case STEEL -> ImtConfig.RcCar.DRILL_SPEED_STEEL;
            case TITANIUM -> ImtConfig.RcCar.DRILL_SPEED_TITANIUM;
            case NETHERITE -> ImtConfig.RcCar.DRILL_SPEED_NETHERITE;
        };
        return Math.max(0.01f, speed);
    }

    /**
     * Het gereedschap waar deze kop voor telt: een pickaxe, schep, bijl en schoffel in één. IJzer en steel zijn ijzeren
     * gereedschap, titanium is diamanten, netherite is netherite: dat bepaalt wat hij kan breken en wat eruit valt.
     */
    private ItemStack[] tools() {
        if (tools == null) {
            tools = switch (this) {
                case IRON, STEEL -> new ItemStack[] {new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_SHOVEL),
                        new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_HOE)};
                case TITANIUM -> new ItemStack[] {new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND_SHOVEL),
                        new ItemStack(Items.DIAMOND_AXE), new ItemStack(Items.DIAMOND_HOE)};
                case NETHERITE -> new ItemStack[] {new ItemStack(Items.NETHERITE_PICKAXE), new ItemStack(Items.NETHERITE_SHOVEL),
                        new ItemStack(Items.NETHERITE_AXE), new ItemStack(Items.NETHERITE_HOE)};
            };
        }
        return tools;
    }

    /** Kan deze kop dit blok aan? Een blok dat geen gereedschap eist altijd; anders moet zijn gereedschap ervoor tellen. */
    public boolean canDrill(BlockState state) {
        if (!state.requiresCorrectToolForDrops()) {
            return true;
        }
        for (ItemStack tool : tools()) {
            if (tool.isCorrectToolForDrops(state)) {
                return true;
            }
        }
        return false;
    }

    /** De zwakste kop die dit blok wel aankan, of null als geen enkele kop dat kan. */
    public static @Nullable DrillHead weakestFor(BlockState state) {
        for (DrillHead head : values()) {
            if (head.canDrill(state)) {
                return head;
            }
        }
        return null;
    }

    /**
     * Het gereedschap van deze kop dat bij dit blok past (voor wat eruit valt): het gereedschap dat ervoor telt, anders het
     * snelste, en bij gelijk de pickaxe.
     */
    public ItemStack toolFor(BlockState state) {
        ItemStack best = tools()[0];
        float bestSpeed = 0;
        for (ItemStack tool : tools()) {
            if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
                continue;
            }
            float speed = tool.getDestroySpeed(state);
            if (speed > bestSpeed) {
                best = tool;
                bestSpeed = speed;
            }
        }
        return best;
    }

    /**
     * Hoe goed het soort gereedschap bij dit blok past: 6 als een pickaxe, schep, bijl of schoffel ervoor dient (de
     * snelheid van ijzeren gereedschap), anders 1 (zoals met je blote hand). Dit is voor elke kop hetzelfde: de kop zelf
     * telt mee via {@link #speed}. Zo boort netherite precies zo snel als de auto deed vóór er koppen waren.
     */
    public static float toolSpeed(BlockState state) {
        float best = 1.0f;
        for (ItemStack tool : IRON.tools()) {
            best = Math.max(best, tool.getDestroySpeed(state));
        }
        return best;
    }
}
