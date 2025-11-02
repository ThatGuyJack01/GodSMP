package io.github.apace100.origins.component;

import dev.onyxstudios.cca.api.v3.component.CopyableComponent;
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.CommonTickingComponent;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public interface PortableJukeboxComponent extends AutoSyncedComponent, CopyableComponent<PortableJukeboxComponent>, CommonTickingComponent {
    Inventory getInventory();
    ItemStack getDisc();
    void dropAll();
    void clear();
    void sync();
}
