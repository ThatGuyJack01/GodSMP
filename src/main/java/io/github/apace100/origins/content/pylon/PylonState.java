package io.github.apace100.origins.content.pylon;

import io.github.apace100.origins.Origins;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PylonState extends PersistentState {
    public static final String STORAGE_KEY = Origins.MODID + "_pylons";
    public static final String POSITIONS_KEY = "Positions";
    private static final PersistentState.Type<PylonState> TYPE = new PersistentState.Type<>(PylonState::new, PylonState::fromNbt, null);

    private final Set<BlockPos> pylons = new HashSet<>();

    private PylonState() { }

    public static PylonState get(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(TYPE, STORAGE_KEY);
    }

    public static PylonState fromNbt(NbtCompound nbt) {
        PylonState state = new PylonState();
        NbtList list = nbt.getList(POSITIONS_KEY, NbtElement.COMPOUND_TYPE);
        for(int i = 0; i < list.size(); i++) {
            state.pylons.add(NbtHelper.toBlockPos(list.getCompound(i)));
        }
        return state;
    }

    public boolean add(BlockPos pos) {
        BlockPos immutable = pos.toImmutable();
        if (pylons.add(immutable)) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean remove(BlockPos pos) {
        if(pylons.remove(pos)) {
            markDirty();
            return true;
        }
        return false;
    }

    public Set<BlockPos> getPositions() {
        return Collections.unmodifiableSet(pylons);
    }

    public int size() {
        return pylons.size();
    }

    public boolean isEmpty() {
        return pylons.isEmpty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for(BlockPos pos : pylons) {
            list.add(NbtHelper.fromBlockPos(pos));
        }
        nbt.put(POSITIONS_KEY, list);
        return nbt;
    }
}