package com.senne.imttech.logic;

import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.security.BaseTrust;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De "binnenkant" van een keycardlezer of codepaneel: zijn naam ("Lab door"), en wie hem neerzette. Met die
 * naam noem je hem in je diagram: {@code when keycard("Lab door"):}.
 *
 * De naam komt uit een aambeeld, of krijgt hij bij het neerzetten ("Reader 2"); je verandert hem in
 * de app Logic of Devices op de desktop, of met sneak + rechtsklik (13 oktober, zie InputDevices). Breek je
 * hem af, dan houdt het item de naam (zoals een camera).
 *
 * Wie hem neerzette (13 oktober) bepaalt mee wie hem zo mag hernoemen (zie security/BaseTrust). Een lezer of
 * paneel van vóór die dag heeft nog geen eigenaar.
 */
public class InputDeviceBlockEntity extends BlockEntity implements BaseTrust.Placed {
    /** Zo lang mag de naam hooguit zijn. */
    public static final int MAX_NAME = 24;

    private String name = "";
    private @Nullable UUID owner;
    private String ownerName = "";

    public InputDeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INPUT_DEVICE.get(), pos, state);
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = cleanName(name);
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

    /** Een naam zonder rare tekens of kleurcodes, zonder spaties eromheen, en niet te lang. */
    public static String cleanName(String name) {
        StringBuilder clean = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c >= ' ' && c != 127 && c != '§' && c != '"' && c != '\\') {
                clean.append(c);
            }
        }
        String result = clean.toString().strip();
        return result.length() > MAX_NAME ? result.substring(0, MAX_NAME).strip() : result;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Name", name);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = tag.getString("Name");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
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
    }
}
