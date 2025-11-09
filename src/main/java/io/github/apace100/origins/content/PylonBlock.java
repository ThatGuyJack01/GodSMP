package io.github.apace100.origins.content;

import io.github.apace100.origins.content.pylon.PylonControllerState;
import io.github.apace100.origins.content.pylon.PylonState;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class PylonBlock extends BlockWithEntity {
    public PylonBlock(Settings settings) {
        super(settings);
    }

    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PylonBlockEntity(pos, state);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if(!world.isClient && world instanceof ServerWorld serverWorld) {
            PylonState.get(serverWorld).add(pos);
            PylonControllerState.notifyPylonChanged(serverWorld, pos, true, PylonControllerBlockEntity.LINK_RADIUS);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            if (!world.isClient && world instanceof ServerWorld serverWorld) {
                PylonState.get(serverWorld).remove(pos);
                PylonControllerState.notifyPylonChanged(serverWorld, pos, false, PylonControllerBlockEntity.LINK_RADIUS);
            }
        }

        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
