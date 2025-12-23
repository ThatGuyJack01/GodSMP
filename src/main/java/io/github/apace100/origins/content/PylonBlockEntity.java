package io.github.apace100.origins.content;

import io.github.apace100.origins.Origins;
import io.github.apace100.origins.content.pylon.OwnablePylon;
import io.github.apace100.origins.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PylonBlockEntity extends BlockEntity implements OwnablePylon {
    private UUID owner;

    public PylonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PYLON, pos, state);
    }

    @Override
    public @Nullable UUID getOwner() {
        return owner;
    }

    @Override
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (owner != null) nbt.putUuid("Owner", owner);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
    }
}
