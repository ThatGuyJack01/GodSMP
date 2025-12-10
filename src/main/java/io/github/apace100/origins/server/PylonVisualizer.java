package io.github.apace100.origins.server;

import io.github.apace100.origins.content.PylonControllerBlockEntity;
import io.github.apace100.origins.content.pylon.PylonControllerState;
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

/**
 * Unified visualizer: streams particles for various modes.
 * Modes:
 *  - OFF: no streaming
 *  - ALL: (legacy) stream all pylons in a stable order (optionally closed)
 *  - HULL: stream a controller/network hull chosen by your existing logic (nearest controller or player's bound controller)
 *  - NEAREST_HULL: stream the hull of the nearest controller within VIEW_RADIUS (auto-switches; this backs /pylon view true|false)
 */
public final class PylonVisualizer {

    public enum VisualizeMode { OFF, ALL, HULL, NEAREST_HULL }

    private static final Map<UUID, VisualizeMode> WATCH = new ConcurrentHashMap<>();

    public static double VIEW_RADIUS = 96.0;

    private static final int PACKET_DURATION_TICKS = 25;

    private PylonVisualizer() {}

    public static void initServerHooks() {
        // Push once per second
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if ((server.getTicks() % 20) != 0) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                VisualizeMode mode = WATCH.getOrDefault(p.getUuid(), VisualizeMode.OFF);
                if (mode == VisualizeMode.OFF) continue;
                try {
                    switch (mode) {
                        case NEAREST_HULL -> sendNearestHullSnapshot(p);
                        case HULL         -> sendChosenHullSnapshot(p);   // optional: your existing rule (nearest controller/network)
                        case ALL          -> sendAllPylonsSnapshot(p);    // legacy/debug
                        default -> {}
                    }
                } catch (Exception ignored) {}
            }
        });

        // Cleanup on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) WATCH.remove(handler.player.getUuid());
        });
    }

    public static void set(ServerPlayerEntity player, VisualizeMode mode) {
        if (mode == VisualizeMode.OFF) WATCH.remove(player.getUuid());
        else WATCH.put(player.getUuid(), mode);
    }

    public static VisualizeMode getMode(ServerPlayerEntity player) {
        return WATCH.getOrDefault(player.getUuid(), VisualizeMode.OFF);
    }

    public static void setView(ServerPlayerEntity player, boolean enabled) {
        set(player, enabled ? VisualizeMode.NEAREST_HULL : VisualizeMode.OFF);
    }

    public static void setLegacyAll(ServerPlayerEntity player, boolean enabled) {
        set(player, enabled ? VisualizeMode.ALL : VisualizeMode.OFF);
    }

    public static boolean isEnabled(ServerPlayerEntity player) {
        return getMode(player) != VisualizeMode.OFF;
    }

    private static void sendNearestHullSnapshot(ServerPlayerEntity player) {
        ServerWorld sw = player.getServerWorld();
        BlockPos playerPos = player.getBlockPos();

        BlockPos bestCtrl = null;
        double bestD2 = VIEW_RADIUS * VIEW_RADIUS;

        for (BlockPos cpos : PylonControllerState.get(sw).getAll()) {
            var be = sw.getBlockEntity(cpos);
            if (!(be instanceof PylonControllerBlockEntity ctrl)) continue;

            List<BlockPos> hull = ctrl.getHullClosed();
            if (hull.isEmpty()) {
                ctrl.forceRefresh(sw);
                ctrl.serverTick();
                hull = ctrl.getHullClosed();
                if (hull.isEmpty()) continue;
            }

            BlockPos centroid = centroid(hull);
            double d2 = centroid.getSquaredDistance(playerPos);
            if (d2 <= bestD2) { bestD2 = d2; bestCtrl = cpos; }
        }

        if (bestCtrl == null) return; // too far from any hull -> show nothing

        var be = sw.getBlockEntity(bestCtrl);
        if (!(be instanceof PylonControllerBlockEntity ctrl)) return;
        List<BlockPos> hull = ctrl.getHullClosed();
        if (hull.isEmpty()) return;

        sendHullPacket(player, sw, hull);
    }

    private static void sendChosenHullSnapshot(ServerPlayerEntity player) {
        sendNearestHullSnapshot(player);
    }

    private static void sendAllPylonsSnapshot(ServerPlayerEntity player) {
        ServerWorld sw = player.getServerWorld();
        var positions = PylonState.get(sw).getPositions();

        List<BlockPos> list = positions.stream()
                .filter(sw::isChunkLoaded)
                .sorted(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getZ)
                        .thenComparingInt(BlockPos::getX))
                .toList();

        if (list.isEmpty()) return;

        // (Optional) close loop so the debug view draws a ring
        if (list.size() >= 2) {
            List<BlockPos> closed = new ArrayList<>(list.size() + 1);
            closed.addAll(list);
            closed.add(list.get(0));
            list = closed;
        }

        sendHullPacket(player, sw, list);
    }

    private static void sendHullPacket(ServerPlayerEntity player, ServerWorld sw, List<BlockPos> hullClosed) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeIdentifier(sw.getRegistryKey().getValue()); // dimension
        buf.writeVarInt(PACKET_DURATION_TICKS);
        buf.writeVarInt(hullClosed.size());
        for (BlockPos p : hullClosed) buf.writeBlockPos(p);

        ServerPlayNetworking.send(player, ModPackets.PYLON_LINES, buf);
    }

    private static BlockPos centroid(List<BlockPos> ring) {
        long sx = 0, sy = 0, sz = 0; int n = ring.size();
        for (BlockPos p : ring) { sx += p.getX(); sy += p.getY(); sz += p.getZ(); }
        return new BlockPos((int)(sx / Math.max(1, n)),
                (int)(sy / Math.max(1, n)),
                (int)(sz / Math.max(1, n)));
    }
}
