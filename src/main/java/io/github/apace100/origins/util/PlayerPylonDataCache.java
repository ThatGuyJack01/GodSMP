package io.github.apace100.origins.util;

import io.github.apace100.origins.content.pylon.PylonControllerState;
import io.github.apace100.origins.content.pylon.PylonState;
import io.github.apace100.origins.networking.ModPackets;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlayerPylonDataCache {
    public static final String ROOT_KEY = "pylon_cache";
    public static final String CONTROLLER_STORAGE_KEY = "controllers";
    public static final String NODE_STORAGE_KEY = "nodes";

    public static Set<BlockPos> getPylonControllers(IEntityDataSaver player) {
        return readPosSet(player, CONTROLLER_STORAGE_KEY);
    }

    public static Set<BlockPos> getPylonNodes(IEntityDataSaver player) {
        return readPosSet(player, NODE_STORAGE_KEY);
    }

    private static Set<BlockPos> readPosSet(IEntityDataSaver player, String key) {
        Set<BlockPos> out = new HashSet<>();

        NbtCompound nbt = player.getPersistentData().getCompound(ROOT_KEY);
        NbtList list = nbt.getList(key, NbtElement.COMPOUND_TYPE);

        for(int i = 0; i < list.size(); i++) {
            out.add(NbtHelper.toBlockPos(list.getCompound(i)));
        }

        return out;
    }

    private static NbtList writePosSet(Iterable<BlockPos> positions) {
        NbtList list = new NbtList();
        for(BlockPos pos : positions) {
            list.add(NbtHelper.fromBlockPos(pos));
        }
        return list;
    }

    public static void updatePlayerCache(IEntityDataSaver player, PylonState pylonState, PylonControllerState pylonControllerState) {
        NbtCompound nbt = new NbtCompound();
        nbt.put(NODE_STORAGE_KEY, writePosSet(pylonState.getPositions()));
        nbt.put(CONTROLLER_STORAGE_KEY, writePosSet(pylonControllerState.getAll()));

        player.getPersistentData().put(ROOT_KEY, nbt);
    }

    public static void updateAndSyncCache(ServerPlayerEntity player, PylonState pylonState, PylonControllerState pylonControllerState) {
        updatePlayerCache((IEntityDataSaver) player, pylonState, pylonControllerState);

        NbtCompound root = ((IEntityDataSaver) player).getPersistentData().getCompound(ROOT_KEY);
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeNbt(root);

    ServerPlayNetworking.send(player, ModPackets.PYLON_SYNC_DATA, buffer);
    }

    public static void updatePlayersInWorld(ServerWorld world) {
        List<ServerPlayerEntity> players = world.getPlayers();
        PylonState nodes = PylonState.get(world);
        PylonControllerState controllers = PylonControllerState.get(world);

        for(ServerPlayerEntity player : players) updateAndSyncCache(player, nodes, controllers);
    }
}
