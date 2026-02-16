package io.github.apace100.origins.content;

import io.github.apace100.origins.content.pylon.OwnablePylon;
import io.github.apace100.origins.content.pylon.PylonControllerState;
import io.github.apace100.origins.content.pylon.PylonPermissions;
import io.github.apace100.origins.content.pylon.PylonState;
import io.github.apace100.origins.power.OriginsPowerTypes;
import io.github.apace100.origins.util.PlayerPylonDataCache;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
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
            BlockEntity be = world.getBlockEntity(pos);
            if(be instanceof OwnablePylon ownable) {
                if(placer instanceof ServerPlayerEntity player && PylonPermissions.canOwnPylons(player)) {
                    ownable.setOwner(player.getUuid());
                } else {
                    ownable.setOwner(null);
                }
            }

            PylonState.get(serverWorld).add(pos);
            PylonControllerState.notifyPylonChanged(serverWorld, pos, true, PylonControllerBlockEntity.LINK_RADIUS);

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
