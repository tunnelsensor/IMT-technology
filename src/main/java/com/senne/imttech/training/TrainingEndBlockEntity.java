package com.senne.imttech.training;

import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.security.BaseTrust;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van een Training End-blok (hoofdstuk 26): zijn programmaatje (een "Training End program": alleen
 * {@code when trainee_hit:}), en wie hem neerzette. Hij meldt zich aan en af bij TrainingEnds, zodat een treffer hem vindt.
 */
public class TrainingEndBlockEntity extends BlockEntity implements BaseTrust.Placed {
    /** Zo begint een nieuw Training End-blok. */
    public static final String DEFAULT_CODE = """
            # Runs when the trainee is hit: trainee, lives and hits tell you more.
            when trainee_hit:
                say(trainee + " is hit: " + lives + " lives left")
            """;

    private @Nullable UUID owner;
    private String ownerName = "";
    private String source = DEFAULT_CODE;
    /** Het programma uit {@link #source} (gemaakt als het nodig is), of null als de code niet klopt. */
    private @Nullable LogicProgram program;
    private boolean built;

    public TrainingEndBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAINING_END.get(), pos, state);
    }

    /** De code van zijn programma. */
    public String source() {
        return source;
    }

    /**
     * Nieuwe code. Klopt hij niet, dan blijft de oude en krijg je de fout (Engels, met het regelnummer) terug; anders
     * null.
     */
    public @Nullable String setSource(String code) {
        LogicProgram compiled;
        try {
            compiled = compile(code);
        } catch (LogicException e) {
            return e.describe();
        }
        source = code;
        program = compiled;
        built = true;
        setChanged();
        return null;
    }

    /** Zijn programma (of null als de bewaarde code niet meer klopt). */
    public @Nullable LogicProgram program() {
        if (!built) {
            built = true;
            try {
                program = compile(source);
            } catch (LogicException e) {
                program = null;
            }
        }
        return program;
    }

    private LogicProgram compile(String code) {
        var script = ScriptParser.parse(code, ProgramKind.TRAINING_END);
        var problems = com.senne.imttech.logic.program.ScriptChecker.check(script, ProgramKind.TRAINING_END);
        if (!problems.isEmpty()) {
            throw new LogicException(Math.max(1, problems.getFirst().ref()), problems.getFirst().message());
        }
        return new LogicProgram("Training End", true, GraphConverter.toGraph(script, null), code, ProgramKind.TRAINING_END);
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

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            TrainingEnds.add(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            TrainingEnds.remove(level, worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            TrainingEnds.remove(level, worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.putString("Code", source);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        source = tag.contains("Code") ? tag.getString("Code") : DEFAULT_CODE;
        built = false;
    }
}
