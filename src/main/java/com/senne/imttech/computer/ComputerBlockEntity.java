package com.senne.imttech.computer;

import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.lan.LanGrid;
import com.senne.imttech.lan.NamedDevice;
import com.senne.imttech.logic.InputDeviceBlockEntity;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.security.BaseTrust;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.os.DesktopRights;
import com.senne.imttech.security.os.SystemPassword;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van een Computer (zie ComputerBlock): zijn naam ("Computer 1"), wie hem neerzette, en om de twee seconden:
 * werkt hij? Dat staat dan in het blok ({@link ComputerBlock#ON}), zodat de ring brandt en de monitor oplicht.
 *
 * Hier staat ook wat de eigenaar van de kern in de app Computers koos (hoofdstuk 40): welke apps erop staan en of je ze
 * alleen bekijkt of ook bedient ({@link #rights}), wie hem mag gebruiken ({@link #who}) en de code ({@link #code}). En
 * welke vensters er de vorige keer open stonden, los van de desktop van de kern.
 */
public class ComputerBlockEntity extends BlockEntity implements BaseTrust.Placed, NamedDevice,
        com.senne.imttech.sdcard.SdCardHolder {
    /** Wie deze computer mag gebruiken (de eigenaar van de kern mag het altijd). */
    public enum Who {
        /** Iedereen die ervoor staat (bijvoorbeeld in de receptie). */
        EVERYONE,
        /** De eigenaar, zijn team en de spelers die hij vertrouwt (Access). Zo begint een nieuwe computer. */
        TRUSTED,
        /** Wie een geldige keycard van dit netwerk uit groep {@link #group()} bij zich heeft. */
        GROUP
    }

    private String name = "";
    private @Nullable UUID owner;
    private String ownerName = "";
    private DesktopRights rights = DesktopRights.NONE;
    private Who who = Who.TRUSTED;
    /** De keycard-groep bij {@link Who#GROUP}. */
    private String group = "";
    /** De code die je moet typen (behalve de eigenaar), of null: geen code. Alleen een hash, net als het wachtwoord. */
    private @Nullable SystemPassword code;
    /** Welke vensters er de vorige keer open stonden (zoals de desktop van de kern, zie SecurityOs "desk_save"). */
    private ListTag desktop = new ListTag();
    /** Hoofdstuk 130: de SD-kaart die in de kast zit (of leeg), en wie hem erin stak. Hak je hem weg, dan valt hij eruit. */
    private net.minecraft.world.item.ItemStack sdCard = net.minecraft.world.item.ItemStack.EMPTY;
    private @Nullable UUID sdCardBy;

    public ComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPUTER.get(), pos, state);
    }

    // ================================================================ Naam en eigenaar

    @Override
    public String name() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = InputDeviceBlockEntity.cleanName(name);
        setChanged();
    }

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    @Override
    public String ownerName() {
        return ownerName;
    }

    @Override
    public void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    // ================================================================ Wat de kern toestaat (de app Computers)

    public DesktopRights rights() {
        return rights;
    }

    public void setRights(DesktopRights rights) {
        this.rights = rights;
        setChanged();
    }

    public Who who() {
        return who;
    }

    public String group() {
        return group;
    }

    /** Wie hem mag gebruiken; bij {@link Who#GROUP} ook welke keycard-groep. */
    public void setWho(Who who, String group) {
        this.who = who;
        this.group = who == Who.GROUP ? group : "";
        setChanged();
    }

    public boolean hasCode() {
        return code != null;
    }

    /** Klopt deze code? (Zonder code klopt alles.) */
    public boolean checkCode(String attempt) {
        return code == null || code.matches(attempt);
    }

    /** Een nieuwe code (null of leeg = geen code meer). De code moet SystemPassword.isValid zijn. */
    public void setCode(@Nullable String newCode, RandomSource random) {
        code = newCode == null || newCode.isEmpty() ? null : SystemPassword.create(newCode, random);
        setChanged();
    }

    public ListTag desktop() {
        return desktop.copy();
    }

    public void setDesktop(ListTag windows) {
        desktop = windows.copy();
        setChanged();
    }

    // ================================================================ Werkt hij?

    public void serverTick(ServerLevel level, BlockPos pos) {
        if ((level.getGameTime() + pos.hashCode()) % 40 == 0) {
            updateOn(level);
            showSdCard(); // hoofdstuk 130: het kaartje vooraan klopt altijd met wat erin zit (ook na laden)
        }
    }

    // ================================================================ De SD-kaart (hoofdstuk 130)

    @Override
    public net.minecraft.world.item.ItemStack sdCard() {
        return sdCard;
    }

    @Override
    public @Nullable UUID sdCardBy() {
        return sdCard.isEmpty() ? null : sdCardBy;
    }

    @Override
    public void putSdCard(net.minecraft.world.item.ItemStack card, @Nullable UUID by) {
        sdCard = card;
        sdCardBy = card.isEmpty() ? null : by;
        setChanged();
        showSdCard();
    }

    @Override
    public net.minecraft.world.item.ItemStack takeSdCard() {
        net.minecraft.world.item.ItemStack card = sdCard;
        sdCard = net.minecraft.world.item.ItemStack.EMPTY;
        sdCardBy = null;
        setChanged();
        showSdCard();
        return card;
    }

    @Override
    public void sdCardChanged() {
        setChanged();
    }

    /** Zet de blokstand {@code card} zoals het is. Met de stand die NU in de wereld staat (bij weghalen al lucht). */
    private void showSdCard() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState live = level.getBlockState(worldPosition);
        boolean has = !sdCard.isEmpty();
        if (live.getBlock() instanceof ComputerBlock && live.getValue(ComputerBlock.CARD) != has) {
            level.setBlock(worldPosition, live.setValue(ComputerBlock.CARD, has), Block.UPDATE_CLIENTS);
        }
    }

    /** De kern waar deze computer bij hoort (via zijn netwerkkabel; zonder netwerk-eis: in wiens gebied hij staat). */
    public @Nullable SecurityCoreBlockEntity core(ServerLevel level) {
        return SecurityCores.deviceCore(level, worldPosition);
    }

    /**
     * Werkt hij? Hij moet aan een kern hangen die zelf stroom heeft, niet plat liggen door een EMP-puls, en zijn eigen
     * stroom kunnen betalen (van een stroomkabel, generator of PoE-kabel ernaast; met {@code [network] required = false}
     * hoeft dat niet). Verandert het, dan zet hij het in het blok: de monitoren ertegen lichten dan op of gaan uit (zie
     * MonitorBlock.updateShape).
     */
    public void updateOn(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof ComputerBlock block)) {
            return;
        }
        SecurityCoreBlockEntity core = core(level);
        boolean on = core != null && core.isOnline() && !com.senne.imttech.weapons.Emp.isOut(level, worldPosition)
                && (!LanGrid.required() || block.upkeep() <= 0 || PowerGrid.pay(level, worldPosition, block.upkeep()));
        if (on != state.getValue(ComputerBlock.ON)) {
            level.setBlock(worldPosition, state.setValue(ComputerBlock.ON, on), Block.UPDATE_ALL);
        }
    }

    // ================================================================ Bewaren

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Name", name);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.put("Rights", rights.save());
        tag.putString("Who", who.name());
        tag.putString("Group", group);
        if (code != null) {
            tag.put("Code", code.save());
        }
        tag.put("Desktop", desktop);
        if (!sdCard.isEmpty()) {
            tag.put("SdCard", sdCard.save(registries));
            if (sdCardBy != null) {
                tag.putUUID("SdCardBy", sdCardBy);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = tag.getString("Name");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        rights = DesktopRights.load(tag.getCompound("Rights"));
        try {
            who = Who.valueOf(tag.getString("Who"));
        } catch (IllegalArgumentException e) {
            who = Who.TRUSTED;
        }
        group = tag.getString("Group");
        code = tag.contains("Code") ? SystemPassword.load(tag.getCompound("Code")) : null;
        desktop = tag.getList("Desktop", Tag.TAG_COMPOUND);
        sdCard = tag.contains("SdCard")
                ? net.minecraft.world.item.ItemStack.parseOptional(registries, tag.getCompound("SdCard"))
                : net.minecraft.world.item.ItemStack.EMPTY;
        sdCardBy = tag.hasUUID("SdCardBy") ? tag.getUUID("SdCardBy") : null;
    }

    /** Breek je hem af, dan krijgt het item zijn naam mee (zie de loot table: copy_components). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!name.isEmpty()) {
            components.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Name");
        // Hoofdstuk 130: een kopie van de kast (creative: middelklik met Ctrl) krijgt de SD-kaart nooit mee.
        tag.remove("SdCard");
        tag.remove("SdCardBy");
    }
}
