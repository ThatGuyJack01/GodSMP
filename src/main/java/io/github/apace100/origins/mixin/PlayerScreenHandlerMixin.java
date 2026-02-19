package io.github.apace100.origins.mixin;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.origins.inventory.PortableJukeboxSlot;
import io.github.apace100.origins.power.PortableJukeboxPower;
import io.github.apace100.origins.registry.ModComponents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
    private void origins$transferPortableJukebox(PlayerEntity player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        if(player.getAbilities().creativeMode) return;

        if(!PowerHolderComponent.hasPower(player, PortableJukeboxPower.class)) return;

        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasStack()) {
            return;
        }

        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        Slot jukeboxSlot = null;

        for (Slot other : this.slots) {
            if (other instanceof PortableJukeboxSlot) {
                jukeboxSlot = other;
                break;
            }
        }

        if (jukeboxSlot == null) {
            return;
        }

        if (slot instanceof PortableJukeboxSlot) {
            if (!player.getInventory().insertStack(stack)) {
                cir.setReturnValue(ItemStack.EMPTY);
                cir.cancel();
                return;
            }

            slot.markDirty();
            if (stack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            }
            slot.onTakeItem(player, original);
            cir.setReturnValue(original);
            cir.cancel();
            return;
        }

        if (!stack.isIn(ItemTags.MUSIC_DISCS)) {
            return;
        }

        ItemStack jukeboxStack = jukeboxSlot.getStack();
        if (!jukeboxStack.isEmpty()) {
            return;
        }

        ItemStack moved = stack.split(1);
        jukeboxSlot.setStack(moved);
        jukeboxSlot.markDirty();
        slot.markDirty();
        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        }
        slot.onTakeItem(player, moved);
        cir.setReturnValue(original);
        cir.cancel();
    }
}
