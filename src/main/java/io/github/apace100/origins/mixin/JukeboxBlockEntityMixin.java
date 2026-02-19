package io.github.apace100.origins.mixin;

import io.github.apace100.origins.music.DiscRules;
import io.github.apace100.origins.music.MusicAuraManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxBlockEntityMixin {

    @Inject(method = "startPlaying", at = @At("TAIL"))
    private void origins$trackStart(CallbackInfo ci) {
        BlockEntity blockEntity = (BlockEntity)(Object)this;
        if (!(blockEntity.getWorld() instanceof ServerWorld world)) {
            return;
        }
        ItemStack stack = ((JukeboxBlockEntity)(Object)this).getStack();
        Identifier soundId = DiscRules.resolve(stack);
        if (soundId != null) {
            BlockPos pos = blockEntity.getPos();
            MusicAuraManager.onJukeboxStarted(world, pos, soundId);
        }
    }

    @Inject(method = "stopPlaying", at = @At("HEAD"))
    private void origins$trackStop(CallbackInfo ci) {
        BlockEntity blockEntity = (BlockEntity)(Object)this;
        if (blockEntity.getWorld() instanceof ServerWorld world) {
            MusicAuraManager.onJukeboxStopped(world, blockEntity.getPos());
        }
    }
}
