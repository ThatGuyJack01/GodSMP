package io.github.apace100.origins.content;

import io.github.apace100.origins.content.pylon.OwnablePylon;
import io.github.apace100.origins.content.pylon.PylonControllerState;
import io.github.apace100.origins.content.pylon.PylonPermissions;
import io.github.apace100.origins.power.OriginsPowerTypes;
import io.github.apace100.origins.util.PlayerPylonDataCache;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class PylonControllerBlock extends BlockWithEntity {
    public PylonControllerBlock(Settings settings) {
        super(settings);
    }

    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PylonControllerBlockEntity(pos, state);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if(!world.isClient && world instanceof ServerWorld serverWorld) {
            BlockEntity be = world.getBlockEntity(pos);
            if(be instanceof OwnablePylon ownable) {
                if(placer instanceof ServerPlayerEntity player && PylonPermissions.canOwnPylons(player)) {
                    ownable.setOwner(player.getUuid());
                } else {
                    ownable.setOwner(null);
                }
            }

            PylonControllerState.get(serverWorld).add(pos);
            PylonControllerState.notifyControllerChanged(serverWorld, pos, true, PylonControllerBlockEntity.LINK_RADIUS);

            PlayerPylonDataCache.updatePlayersInWorld(serverWorld);
        }
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        super.onBreak(world, pos, state, player);
        if(!world.isClient && world instanceof ServerWorld serverWorld)
            serverWorld.getServer().execute(() -> PlayerPylonDataCache.updatePlayersInWorld(serverWorld));
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if(state.getBlock() == newState.getBlock()) { super.onStateReplaced(state, world, pos, newState, moved); return; }
        if(!world.isClient && world instanceof ServerWorld serverWorld) {
            PylonControllerState.get(serverWorld).remove(pos);
            PylonControllerState.notifyControllerChanged(serverWorld, pos, false, PylonControllerBlockEntity.LINK_RADIUS);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : (w, p, s, be) -> {
            if (be instanceof PylonControllerBlockEntity ctrl) ctrl.serverTick();
        };
    }
}
