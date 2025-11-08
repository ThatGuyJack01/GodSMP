package io.github.apace100.server;

import io.github.apace100.origins.content.pylon.PylonState;
import io.github.apace100.origins.networking.ModPackets;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PylonVisualizer {
    private static final Set<UUID> WATCHING = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static void set(ServerPlayerEntity player, boolean enabled) {
        if (enabled) WATCHING.add(player.getUuid()); else WATCHING.remove(player.getUuid());
    }
    public static boolean isWatching(ServerPlayerEntity player) { return WATCHING.contains(player.getUuid()); }
    public static void clear(ServerPlayerEntity player) { WATCHING.remove(player.getUuid()); }

    public static void initServerHooks() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(server.getTicks() % 20 != 0) return;
            for(ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if(!isWatching(player)) continue;
                sendSnapshot(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if(handler.player != null) clear(handler.player);
        });
    }

    private static void sendSnapshot(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Set<BlockPos> positions = (Set<BlockPos>) PylonState.get(world).getPositions();
        List<BlockPos> list = positions.stream()
                .filter(world::isChunkLoaded)
                .sorted(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getZ)
                        .thenComparingInt(BlockPos::getX))
                .toList();

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeIdentifier(world.getRegistryKey().getValue());
        buf.writeVarInt(25);
        buf.writeVarInt(list.size());
        for (BlockPos p : list) buf.writeBlockPos(p);

        ServerPlayNetworking.send(player, ModPackets.PYLON_LINES, buf);
    }
}
