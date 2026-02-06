package io.github.apace100.origins.content.pylon;

import io.github.apace100.origins.Origins;
import io.github.apace100.origins.content.PylonControllerBlock;
import io.github.apace100.origins.content.PylonControllerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashSet;
import java.util.Set;

public class PylonControllerState extends PersistentState {
    public static final String STORAGE_KEY = Origins.MODID + "_pylon_controllers";
    public static final String CONTROLLER_KEY = "Controllers";
    private static final PersistentState.Type<PylonControllerState> TYPE = new PersistentState.Type<>(PylonControllerState::new, PylonControllerState::fromNbt, null);


    private final Set<BlockPos> controllers = new HashSet<>();

    public static PylonControllerState get(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(TYPE, STORAGE_KEY);
    }

    public static PylonControllerState fromNbt(NbtCompound nbt) {
        PylonControllerState state = new PylonControllerState();
        NbtList list = nbt.getList(CONTROLLER_KEY, NbtElement.COMPOUND_TYPE);
        for(int i = 0; i < list.size(); i++) {
            state.controllers.add(NbtHelper.toBlockPos(list.getCompound(i)));
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for(BlockPos pos : controllers) {
            list.add(NbtHelper.fromBlockPos(pos));
        }
        nbt.put(CONTROLLER_KEY, list);
        return nbt;
    }

    public void add(BlockPos pos) { if (controllers.add(pos)) markDirty(); }
    public void remove(BlockPos pos) { if (controllers.remove(pos)) markDirty(); }
    public Set<BlockPos> getAll() { return controllers; }

    public static void notifyPylonChanged(ServerWorld world, BlockPos changed, boolean added, double linkRadius) {
        PylonControllerState state = get(world);
        double r2 = linkRadius * linkRadius;
        for(BlockPos cpos : state.controllers) {
            if (cpos.getSquaredDistance(changed) <= r2) {
                BlockEntity blockEntity = world.getBlockEntity(cpos);
                if(blockEntity instanceof PylonControllerBlockEntity ctrl) {
                    ctrl.onTopologyEvent(changed, added ? PylonTopoEvent.PYLON_ADDED : PylonTopoEvent.PYLON_REMOVED);
                }
            }
        }
    }

    public static void notifyControllerChanged(ServerWorld world, BlockPos changed, boolean added, double linkRadius) {
        PylonControllerState state = get(world);
        double r2 = linkRadius * linkRadius;
        for(BlockPos cpos : state.controllers) {
            if (cpos.getSquaredDistance(changed) <= r2 || cpos.equals(changed)) {
                BlockEntity blockEntity = world.getBlockEntity(cpos);
                if(blockEntity instanceof PylonControllerBlockEntity ctrl) {
                    ctrl.onTopologyEvent(changed, added ? PylonTopoEvent.CTRL_ADDED : PylonTopoEvent.CTRL_REMOVED);
                }
            }
        }
    }
}
