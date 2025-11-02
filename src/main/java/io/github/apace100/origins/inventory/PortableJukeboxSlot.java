package io.github.apace100.origins.inventory;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.origins.power.OriginsPowerTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.Slot;

public class PortableJukeboxSlot extends Slot {
    public static final int SLOT_X = 77;
    public static final int SLOT_Y = 17;

    private final PlayerEntity player;

    public PortableJukeboxSlot(PlayerEntity player, Inventory inventory, int index) {
        super(inventory, index, SLOT_X, SLOT_Y);
        this.player = player;
    }

    @Override
    public boolean canInsert(ItemStack stack) {
        return stack.isEmpty() || stack.isIn(ItemTags.MUSIC_DISCS);
    }

    @Override
    public int getMaxItemCount() {
        return 1;
    }

    @Override
    public boolean isEnabled() {
        return PowerHolderComponent.KEY.get(player).hasPower(OriginsPowerTypes.PORTABLE_JUKEBOX);
    }
}
