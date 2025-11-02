package io.github.apace100.origins.mixin;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.origins.inventory.PortableJukeboxSlot;
import io.github.apace100.origins.power.OriginsPowerTypes;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractInventoryScreen<PlayerScreenHandler> {
    @Shadow @Final private static Identifier TEXTURE;
    // new Identifier("minecraft", "textures/gui/sprites/container/slot.png");

    protected InventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "drawBackground", at = @At("TAIL"))
    private void origins$drawPortableJukeboxSlot(DrawContext context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        if(!PowerHolderComponent.KEY.get(this.handler.player).hasPower(OriginsPowerTypes.PORTABLE_JUKEBOX)) {
            return;
        }

        int slotX = this.x + PortableJukeboxSlot.SLOT_X - 1;
        int slotY = this.y + PortableJukeboxSlot.SLOT_Y - 1;
        context.drawTexture(TEXTURE, slotX, slotY, 0, this.backgroundHeight, 18, 18);
    }
}
