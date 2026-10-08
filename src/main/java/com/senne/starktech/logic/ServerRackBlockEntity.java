package com.senne.starktech.logic;

import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * De "binnenkant" van een serverrack: zijn zes vakjes (CPU, GPU, RAM, SSD, fan), en of hij meedraait.
 *
 * Elke twee seconden kijkt hij:
 * <ul>
 *   <li>staat hij in het gebied van een beveiligingskern? (de server hangt aan je Stark OS);</li>
 *   <li>hoort hij bij de server van die kern? Racks die elkaar raken zijn samen één server; staan
 *       er losse groepjes, dan telt alleen de grootste groep (zie LogicServer);</li>
 *   <li>heeft hij stroom? Dan betaalt hij RACK_UPKEEP.</li>
 * </ul>
 * Is alles in orde, dan gaan zijn lampjes knipperen (ServerRackBlock.ONLINE) en tellen zijn
 * onderdelen mee.
 */
public class ServerRackBlockEntity extends BlockEntity implements MenuProvider {
    /** Waarom hij niet meedraait (voor het scherm). */
    public enum Status {
        ONLINE, NO_NETWORK, NOT_CONNECTED, NO_POWER
    }

    private final SimpleContainer parts = new SimpleContainer(LogicBalance.RACK_SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof ServerPartItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(ServerRackBlockEntity.this, player);
        }
    };

    private Status status = Status.NO_NETWORK;

    /** Wat het scherm te zien krijgt; zie ServerRackMenu. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index == ServerRackMenu.DATA_STATUS) {
                return status.ordinal();
            }
            if (!(level instanceof ServerLevel serverLevel)) {
                return 0;
            }
            SecurityCoreBlockEntity core = SecurityCores.coreAt(serverLevel, worldPosition);
            if (core == null) {
                return 0;
            }
            LogicServer.Stats stats = LogicServer.stats(serverLevel, core);
            return switch (index) {
                case ServerRackMenu.DATA_RACKS -> stats.racks();
                case ServerRackMenu.DATA_CPU -> stats.cpus();
                case ServerRackMenu.DATA_GPU -> stats.gpus();
                case ServerRackMenu.DATA_FAN -> stats.fans();
                case ServerRackMenu.DATA_RAM -> stats.ram();
                case ServerRackMenu.DATA_SSD -> stats.ssd();
                case ServerRackMenu.DATA_CORE_ONLINE -> stats.coreOnline() ? 1 : 0;
                case ServerRackMenu.DATA_TEMP -> tenths(core.datacenter().temperature());
                case ServerRackMenu.DATA_HEAT -> tenths(core.datacenter().heat());
                case ServerRackMenu.DATA_FULL_HEAT -> tenths(core.datacenter().fullHeat());
                case ServerRackMenu.DATA_COOLING -> tenths(core.datacenter().cooling());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Alleen de server rekent; het scherm leest alleen.
        }

        @Override
        public int getCount() {
            return ServerRackMenu.DATA_COUNT;
        }
    };

    /** 72.44 wordt 724 (het scherm krijgt alleen hele getallen, en niet groter dan 32767). */
    private static int tenths(double value) {
        return (int) Math.max(-32000, Math.min(32000, Math.round(value * 10)));
    }

    public ServerRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVER_RACK.get(), pos, state);
        parts.addListener(container -> {
            setChanged();
            LogicServer.forgetAll(); // de server telt opnieuw
        });
    }

    public SimpleContainer parts() {
        return parts;
    }

    public Status status() {
        return status;
    }

    /** Hoeveel onderdelen van deze soort in dit rack zitten. */
    public int count(ServerPartItem.Kind kind) {
        int count = 0;
        for (int i = 0; i < parts.getContainerSize(); i++) {
            if (parts.getItem(i).getItem() instanceof ServerPartItem part && part.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    // ================================================================ Elke twee seconden

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if ((level.getGameTime() + pos.hashCode()) % 40 == 0) {
            update(level, pos, state);
        }
    }

    /** Hoort hij bij een server, heeft hij stroom? Zet dat in het blok (en betaal de stroom). */
    public void update(ServerLevel level, BlockPos pos, BlockState state) {
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        Status now;
        if (core == null) {
            now = Status.NO_NETWORK;
        } else if (!LogicServer.inMainCluster(level, core, pos)) {
            now = Status.NOT_CONNECTED;
        } else if (StarkGrid.available(level, pos) < LogicBalance.RACK_UPKEEP) {
            now = Status.NO_POWER;
        } else {
            StarkGrid.draw(level, pos, LogicBalance.RACK_UPKEEP);
            now = Status.ONLINE;
        }
        if (now != status) {
            status = now;
            setChanged();
            LogicServer.forgetAll();
        }
        boolean online = now == Status.ONLINE;
        if (online != state.getValue(ServerRackBlock.ONLINE)) {
            level.setBlock(pos, state.setValue(ServerRackBlock.ONLINE, online), Block.UPDATE_CLIENTS);
        }
    }

    // ================================================================ Het scherm

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.starktech.server_rack");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ServerRackMenu(containerId, inventory, parts, data);
    }

    // ================================================================ Opslaan

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NonNullList<ItemStack> items = NonNullList.withSize(parts.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, parts.getItem(i));
        }
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putString("Status", status.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> items = NonNullList.withSize(parts.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int i = 0; i < items.size(); i++) {
            parts.setItem(i, items.get(i));
        }
        try {
            status = Status.valueOf(tag.getString("Status"));
        } catch (IllegalArgumentException e) {
            status = Status.NO_NETWORK;
        }
    }
}
