package io.github.apace100.origins.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.apace100.origins.content.PylonControllerBlockEntity;
import io.github.apace100.origins.content.pylon.PylonArea;
import io.github.apace100.origins.content.pylon.PylonControllerState;
import io.github.apace100.origins.content.pylon.PylonState;
import io.github.apace100.origins.content.pylon.PylonTopoEvent;
import io.github.apace100.origins.networking.ModPackets;
import io.github.apace100.server.PylonVisualizer;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class PylonCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("pylon")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("list").executes(PylonCommand::listPylons))
                .then(CommandManager.literal("visualize")
                        // with argument: /pylon visualize true|false
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                    PylonVisualizer.set(player, enabled);
                                    ctx.getSource().sendFeedback(() -> Text.literal("Pylon visualize: " + enabled), false);
                                    return 1;
                                })
                        )
                )
                .then(CommandManager.literal("clear").executes(PylonCommand::clearList))
                    .then(CommandManager.literal("hullitall")
                        .then(CommandManager.argument("seconds", IntegerArgumentType.integer())
                            .executes(ctx -> sendHull(ctx, IntegerArgumentType.getInteger(ctx, "seconds"))))
                            .executes(ctx -> sendHull(ctx, 5)
                    )
                )
                .then(CommandManager.literal("hull").executes(ctx -> hullFromNearest(ctx, 5)))
                .then(CommandManager.literal("hull")
                        .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1, 60))
                                .executes(ctx -> hullFromNearest(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))
                        )
                )
                .then(CommandManager.literal("hulllist")
                        .executes(ctx -> hullListFromNearest(ctx, 96, 5)) // radius=96, force rebuild if needed
                )
        );
    }

    private static int listPylons(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        MinecraftServer server = source.getServer();
        int overallTotal = 0;

        for (ServerWorld world : server.getWorlds()) {
            PylonState state = PylonState.get(world);
            Collection<BlockPos> positions = state.getPositions();
            overallTotal += positions.size();

            source.sendFeedback(() -> Text.literal("Dimension: " + world.getRegistryKey().getValue()), false);

            if (positions.isEmpty()) {
                source.sendFeedback(() -> Text.literal("  (no pylons)"), false);
                continue;
            }

            List<BlockPos> sortedPositions = new ArrayList<>(positions);
            sortedPositions.sort(Comparator
                    .comparingInt(BlockPos::getX)
                    .thenComparingInt(BlockPos::getY)
                    .thenComparingInt(BlockPos::getZ));

            for (BlockPos pos : sortedPositions) {
                source.sendFeedback(() -> Text.literal("  - " + pos.toShortString()), false);
            }

            int dimensionTotal = sortedPositions.size();
            source.sendFeedback(() -> Text.literal("  Total: " + dimensionTotal), false);
        }

        if (overallTotal == 0) {
            source.sendFeedback(() -> Text.literal("No pylons registered."), false);
        } else {
            int total = overallTotal;
            source.sendFeedback(() -> Text.literal("Overall pylons: " + total), false);
        }

        return overallTotal;
    }

    private static int clearList(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        MinecraftServer server = source.getServer();
        if(server.getWorlds() == null) return 0;
        for (ServerWorld world : server.getWorlds()) {
            PylonState state = PylonState.get(world);
            if(state.getPositions().isEmpty()) continue;
            for(BlockPos pos : state.getPositions())
                state.remove(pos);
        }
        source.sendFeedback(() -> Text.literal("Pylons cleared!"), false);
        return 1;
    }

    private static int sendHull(CommandContext<ServerCommandSource> ctx, int seconds) {
        var src = ctx.getSource();
        var player = src.getPlayer();
        var world = player.getServerWorld();

        List<BlockPos> hull = PylonArea.buildHullList(world);
        int durationTicks = Math.max(20, seconds * 20);

        var buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeIdentifier(world.getRegistryKey().getValue()); // dimension
        buf.writeVarInt(durationTicks);
        buf.writeVarInt(hull.size());
        for (BlockPos p : hull) buf.writeBlockPos(p);

        ServerPlayNetworking.send(player, ModPackets.PYLON_LINES, buf);

        src.sendFeedback(() -> Text.literal("She pylon my area till I convex hull"), false);
        src.sendFeedback(() -> Text.literal("Showing convex hull for " + (durationTicks / 20) + "s (" + hull.size() + " points)"), false);
        return 1;
    }

    private static int hullFromNearest(CommandContext<ServerCommandSource> ctx, int seconds) {
        var src = ctx.getSource();
        var player = src.getPlayer();
        var world = player.getServerWorld();

        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for(BlockPos p : PylonControllerState.get(world).getAll()) {
            double d2 = p.getSquaredDistance(player.getBlockPos());
            if(d2 < best && d2 <= (96*96)) { best = d2; nearest = p; }
        }

        if(nearest == null) {
            src.sendFeedback(() -> Text.literal("No controller nearby."), false);
            return 0;
        }

        var blockEntity = world.getBlockEntity(nearest);
        if(!(blockEntity instanceof PylonControllerBlockEntity ctrl)) {
            src.sendFeedback(() -> Text.literal("Pylon controller missing block entity."), false);
            return 0;
        }

        ctrl.forceRefresh(world);
        ctrl.onTopologyEvent(nearest, PylonTopoEvent.CTRL_ADDED);
        ctrl.serverTick();

        var list = ctrl.getHullClosed();
        int duration = Math.max(20, seconds * 20);

        var buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeIdentifier(world.getRegistryKey().getValue());
        buf.writeVarInt(duration);
        buf.writeVarInt(list.size());
        for(BlockPos p : list) buf.writeBlockPos(p);

        ServerPlayNetworking.send(player, ModPackets.PYLON_LINES, buf);
        src.sendFeedback(() -> Text.literal("Showing hull (" + list.size() + " pts)"), false);
        return 1;
    }

    private static int hullListFromNearest(CommandContext<ServerCommandSource> ctx, int searchRadius, int seconds) {
        final ServerCommandSource src = ctx.getSource();
        final ServerPlayerEntity player = src.getPlayer();
        final ServerWorld sw = player.getServerWorld();
        final BlockPos origin = player.getBlockPos();

        // Find nearest controller within radius
        BlockPos nearest = null;
        double bestD2 = (double)searchRadius * searchRadius;
        for (BlockPos cPos : PylonControllerState.get(sw).getAll()) {
            double d2 = cPos.getSquaredDistance(origin);
            if (d2 <= bestD2) { bestD2 = d2; nearest = cPos; }
        }

        if (nearest == null) {
            src.sendFeedback(() -> Text.literal("No pylon controller found within " + searchRadius + " blocks."), false);
            return 0;
        }

        BlockEntity be = sw.getBlockEntity(nearest);
        if (!(be instanceof PylonControllerBlockEntity ctrl)) {
            src.sendFeedback(() -> Text.literal("Nearest controller at " + be.getPos() + " has no valid block entity."), false);
            return 0;
        }

        // Ensure the controller has a fresh local view and hull
        // Use whatever you implemented; prefer forceResync(sw), else resyncIfEmpty(sw).
        ctrl.forceRefresh(sw);
        ctrl.serverTick(); // trigger immediate rebuild if dirty

        // Fetch closed hull (last == first if size >= 2)
        java.util.List<BlockPos> hull = ctrl.getHullClosed();
        int count = hull.size();

        if (count == 0) {
            src.sendFeedback(() -> Text.literal("No hull could be computed (need at least 3 pylons)."), false);
            return 1;
        }

        // Print a compact, ordered list
        StringBuilder sb = new StringBuilder();
        sb.append("Hull vertices (").append(count).append("): ");
        for (int i = 0; i < count; i++) {
            BlockPos p = hull.get(i);
            if (i > 0) sb.append(" -> ");
            sb.append("(").append(p.getX()).append(",").append(p.getY()).append(",").append(p.getZ()).append(")");
        }
        src.sendFeedback(() -> Text.literal(sb.toString()), false);

        return 1;
    }
}
