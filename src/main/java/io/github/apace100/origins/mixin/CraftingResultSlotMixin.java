package io.github.apace100.origins.mixin;

import io.github.apace100.origins.content.pylon.PylonPermissions;
import io.github.apace100.origins.registry.ModBlocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingScreenHandler.class)
public abstract class CraftingResultSlotMixin {
    @Inject(method = "updateResult", at = @At("TAIL"))
    private static void gatePylonRecipe(ScreenHandler handler, World world, PlayerEntity player, RecipeInputInventory craftingInventory, CraftingResultInventory resultInventory, CallbackInfo ci) {
        if(world.isClient) return;
        if(!(player instanceof ServerPlayerEntity serverPlayer)) return;

        ItemStack out = resultInventory.getStack(0);
        if(out.isEmpty()) return;

        Item output = out.getItem();
        if(output != ModBlocks.PYLON.asItem() && output != ModBlocks.PYLON_CONTROLLER.asItem()) return;

        if(!PylonPermissions.canOwnPylons(serverPlayer)) {
            resultInventory.setStack(0, ItemStack.EMPTY);
            serverPlayer.currentScreenHandler.sendContentUpdates();
        }
    }
}
