package io.github.apace100.origins.mixin;

import io.github.apace100.origins.component.OriginComponent;
import io.github.apace100.origins.origin.OriginLayers;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(OriginLayers.class)
public abstract class OriginLayersMixin {

    @Inject(
        method = "postLoading",
        at = @At(value = "INVOKE", target = "Lio/github/apace100/origins/component/OriginComponent;selectingOrigin(Z)V"),
        cancellable = true,
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    @SuppressWarnings("unused")
    private void godsmp$skipOriginSelection(ServerPlayerEntity player, boolean init, CallbackInfo ci, OriginComponent component, boolean mismatch) {
        if (!init) {
            return;
        }

        component.selectingOrigin(false);
        component.sync();
        ci.cancel();
    }
}
