package io.github.apace100.origins.component;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.origins.power.PortableJukeboxPower;
import io.github.apace100.origins.registry.ModComponents;
import io.github.apace100.origins.util.PortableJukeboxHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.collection.DefaultedList;

public class PlayerPortableJukeboxComponent implements PortableJukeboxComponent {
    private final PlayerEntity player;
    private final DefaultedList<ItemStack> discInventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
    private final Inventory inventory;

    private int playCooldown;
    private int exhaustionTicker;

    public PlayerPortableJukeboxComponent(PlayerEntity player) {
        this.player = player;
        this.inventory = new Inventory() {
            @Override
            public int size() {
                return discInventory.size();
            }

            @Override
            public boolean isEmpty() {
                return  discInventory.get(0).isEmpty();
            }

            @Override
            public ItemStack getStack(int slot) {
                return discInventory.get(slot);
            }

            @Override
            public ItemStack removeStack(int slot, int amount) {
                ItemStack stack = Inventories.splitStack(discInventory, slot, amount);
                if(!stack.isEmpty()) {
                    onInventoryChanged();
                }
                return stack;
            }

            @Override
            public ItemStack removeStack(int slot) {
                ItemStack stack = Inventories.removeStack(discInventory, slot);
                if(!stack.isEmpty()) {
                    onInventoryChanged();
                }
                return stack;
            }

            @Override
            public void setStack(int slot, ItemStack stack) {
                discInventory.set(slot, stack);
                if(stack.getCount() > getMaxCountPerStack()) {
                    stack.setCount(getMaxCountPerStack());
                }
                onInventoryChanged();
            }

            @Override
            public void markDirty() {
                onInventoryChanged();
            }

            @Override
            public boolean canPlayerUse(PlayerEntity player) {
                return true;
            }

            @Override
            public void clear() {
                discInventory.clear();
                onInventoryChanged();
            }
        };
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public ItemStack getDisc() {
        return discInventory.get(0);
    }

    @Override
    public void dropAll() {
        ItemStack stack = getDisc();
        if(!stack.isEmpty()) {
            if(!player.getWorld().isClient) {
                player.dropItem(stack.copy(), false);
            }
            discInventory.set(0, ItemStack.EMPTY);
            onInventoryChanged();
        }
    }

    @Override
    public void clear() {
        discInventory.set(0, ItemStack.EMPTY);
        onInventoryChanged();
    }

    @Override
    public void sync() {
        if(!player.getWorld().isClient) {
            ModComponents.PORTABLE_JUKEBOX.sync(player);
        }
    }

    @Override
    public void copyFrom(PortableJukeboxComponent other) {
        discInventory.set(0, other.getDisc().copy());
        playCooldown = 0;
        exhaustionTicker = 0;
    }

    @Override
    public void tick() {
        if(player.getWorld().isClient) {
            return;
        }

        PortableJukeboxPower power = PowerHolderComponent.getPowers(player, PortableJukeboxPower.class)
                .stream().findFirst().orElse(null);

        ItemStack stack = getDisc();

        if(power==null) {
            if(!stack.isEmpty()) {
                player.dropItem(stack.copy(), false);
                discInventory.set(0, ItemStack.EMPTY);
                sync();
            }
            playCooldown = 0;
            exhaustionTicker = 0;
            return;
        }

        if(!(stack.getItem() instanceof MusicDiscItem disc)) {
            if(!stack.isEmpty()) {
                player.dropItem(stack.copy(), false);
                discInventory.set(0, ItemStack.EMPTY);
                sync();
            }
            playCooldown = 0;
            exhaustionTicker = 0;
            return;
        }

        if(stack.isEmpty()) {
            playCooldown = 0;
            exhaustionTicker = 0;
            return;
        }

        if(playCooldown-- <= 0) {
            SoundEvent sound = disc.getSound();
            if(sound != null) {
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.RECORDS, 4.0F, 1.0F);
            }
            playCooldown = PortableJukeboxHelper.getPlaybackInterval(disc);
        }

        exhaustionTicker++;
        if(exhaustionTicker >= power.getExhaustionInterval()) {
            player.addExhaustion(power.getExhaustionAmount());
            exhaustionTicker = 0;
        }
    }

    @Override
    public void readFromNbt(NbtCompound tag) {
        Inventories.readNbt(tag, discInventory);
        playCooldown = tag.getInt("PlayCooldown");
        exhaustionTicker = tag.getInt("ExhaustionTicker");
    }

    @Override
    public void writeToNbt(NbtCompound tag) {
        Inventories.writeNbt(tag, discInventory);
        tag.putInt("PlayCooldown", playCooldown);
        tag.putInt("ExhaustionTicker", exhaustionTicker);
    }

    private void onInventoryChanged() {
        ItemStack stack = discInventory.get(0);
        if(!stack.isEmpty() && !stack.isIn(ItemTags.MUSIC_DISCS)) {
            if(!player.getWorld().isClient) {
                player.dropItem(stack.copy(), false);
            }
            discInventory.set(0, ItemStack.EMPTY);
        }
        playCooldown = 0;
        exhaustionTicker = 0;
        sync();
    }
}
