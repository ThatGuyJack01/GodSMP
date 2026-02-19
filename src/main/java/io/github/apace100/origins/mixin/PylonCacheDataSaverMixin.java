package io.github.apace100.origins.mixin;

import io.github.apace100.origins.util.IEntityDataSaver;
import io.github.apace100.origins.util.PlayerPylonDataCache;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class PylonCacheDataSaverMixin implements IEntityDataSaver {
    private NbtCompound persistentData;

    @Override
    public NbtCompound getPersistentData() {
        if(this.persistentData == null)
            this.persistentData = new NbtCompound();
        return persistentData;
    }

    @Inject(method = "writeNbt", at = @At("HEAD"))
    protected void godsmp$writePersistentData(NbtCompound nbt, CallbackInfoReturnable ci) {
        if(persistentData != null) {
            nbt.put(PlayerPylonDataCache.ROOT_KEY, persistentData);
        }
    }

    @Inject(method = "writeNbt", at = @At("HEAD"))
    protected void godsmp$readPersistentData(NbtCompound nbt, CallbackInfoReturnable ci) {
        if(nbt.contains(PlayerPylonDataCache.ROOT_KEY, 10)) {
            persistentData = nbt.getCompound(PlayerPylonDataCache.ROOT_KEY);
        }
    }
}
