package io.github.apace100.origins.mixin;

import io.github.apace100.origins.inventory.PortableJukeboxSlot;
import io.github.apace100.origins.registry.ModComponents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerScreenHandler.class)
public abstract class PlayerScreenHandlerMixin extends ScreenHandler {
    protected PlayerScreenHandlerMixin(@Nullable ScreenHandlerType<?> type, int syncId) {
        super(type, syncId);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void origins$addPortableJukeboxSlot(PlayerInventory playerInventory, boolean onServer, PlayerEntity owner, CallbackInfo ci) {
        Inventory inventory = ModComponents.PORTABLE_JUKEBOX.get(owner).getInventory();
        this.addSlot(new PortableJukeboxSlot(owner, inventory, 0));
    }
}
