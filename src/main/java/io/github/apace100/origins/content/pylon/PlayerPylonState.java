package io.github.apace100.origins.content.pylon;

import io.github.apace100.origins.Origins;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.*;

public class PlayerPylonState extends PersistentState {
    private final Map<UUID, PylonMode> modes = new HashMap<>();

    private static final String STORAGE_KEY = Origins.MODID + "_player_pylon_states";
    private static final PersistentState.Type<PlayerPylonState> TYPE = new PersistentState.Type<>(PlayerPylonState::new, PlayerPylonState::fromNbt, null);

    public PylonMode getMode( UUID player) {
        return modes.getOrDefault(player, PylonMode.NONE);
    }

    public void setMode(UUID player, PylonMode mode) {
        modes.put(player, mode);
        markDirty();
    }

    public static PlayerPylonState get(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(TYPE, STORAGE_KEY);
    }

    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (var e : modes.entrySet()) {
            NbtCompound tag = new NbtCompound();
            tag.putUuid("uuid", e.getKey());
            tag.putString("mode", e.getValue().name());
            list.add(tag);
        }
        nbt.put("players", list);
        return nbt;
    }

    public static PlayerPylonState fromNbt(NbtCompound nbt) {
        PlayerPylonState state = new PlayerPylonState();
        NbtList list = nbt.getList("players", NbtElement.COMPOUND_TYPE);
        for (NbtElement el : list) {
            NbtCompound tag = (NbtCompound) el;
            UUID id = tag.getUuid("uuid");
            PylonMode mode = PylonMode.valueOf(tag.getString("mode"));
            state.modes.put(id, mode);
        }
        return state;
    }
}
