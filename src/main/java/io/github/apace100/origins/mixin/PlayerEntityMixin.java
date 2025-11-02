package io.github.apace100.origins.mixin;

import io.github.apace100.origins.component.PortableJukeboxComponent;
import io.github.apace100.origins.registry.ModComponents;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "dropInventory", at = @At("TAIL"))
    private void origins$dropPortableJukeboxSlot(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        PortableJukeboxComponent component = ModComponents.PORTABLE_JUKEBOX.get(player);
        component.dropAll();
    }
}
