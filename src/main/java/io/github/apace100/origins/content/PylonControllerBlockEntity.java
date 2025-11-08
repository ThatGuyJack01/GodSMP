package io.github.apace100.origins.content;

import io.github.apace100.origins.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public class PylonControllerBlockEntity extends BlockEntity {
    public PylonControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PYLON_CONTROLLER, pos, state);
    }
}