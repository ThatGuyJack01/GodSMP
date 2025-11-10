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
import io.github.apace100.origins.server.PylonVisualizer;
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
                .then(CommandManager.literal("list")
                    .then(CommandManager.literal("pylons")
                        .executes(ctx -> listPylons(ctx, ListType.PYLON))
                    )
                    .then(CommandManager.literal("controllers")
                            .executes(ctx -> listPylons(ctx, ListType.PYLON_CONTROLLER))
                    )
                    .then(CommandManager.literal("nearest_hull")
                            .executes(ctx -> hullListFromNearest(ctx, 96, 5))
                    )
                )
                .then(CommandManager.literal("view")
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    var p = ctx.getSource().getPlayer();
                                    boolean on = BoolArgumentType.getBool(ctx, "enabled");
                                    PylonVisualizer.setView(p, on);  // maps to NEAREST_HULL or OFF
                                    ctx.getSource().sendFeedback(() -> Text.literal("Pylon view: " + (on ? "ON (nearest hull)" : "OFF")), false);
                                    return 1;
                                })
                        )
                        .executes(ctx -> {
                            var p = ctx.getSource().getPlayer();
                            var mode = PylonVisualizer.getMode(p);
                            ctx.getSource().sendFeedback(() -> Text.literal("Pylon view is " + (mode == PylonVisualizer.VisualizeMode.NEAREST_HULL ? "ON" : "OFF")), false);
                            return 1;
                        })
                )
        );
    }

    private static int listPylons(CommandContext<ServerCommandSource> context, ListType listType) {
        ServerCommandSource source = context.getSource();
        MinecraftServer server = source.getServer();
        int overallTotal = 0;
        if(listType == ListType.PYLON)
        {
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
        else if (listType == ListType.PYLON_CONTROLLER)
        {
            for (ServerWorld world : server.getWorlds()) {
                PylonControllerState state = PylonControllerState.get(world);
                Collection<BlockPos> positions = state.getAll();
                overallTotal += positions.size();

                source.sendFeedback(() -> Text.literal("Dimension: " + world.getRegistryKey().getValue()), false);

                if (positions.isEmpty()) {
                    source.sendFeedback(() -> Text.literal("  (no pylon controllers)"), false);
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
                source.sendFeedback(() -> Text.literal("No pylons controllers found."), false);
            } else {
                int total = overallTotal;
                source.sendFeedback(() -> Text.literal("Total pylon controllers: " + total), false);
            }

            return overallTotal;
        }
        else { return 0; }
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

    private enum ListType {
        PYLON,
        PYLON_CONTROLLER
    }
}
