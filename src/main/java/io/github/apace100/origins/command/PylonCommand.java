package io.github.apace100.origins.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.apace100.origins.content.PylonControllerBlockEntity;
import io.github.apace100.origins.content.pylon.*;
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

import java.util.*;
import java.util.stream.Collectors;

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
                .then(CommandManager.literal("testinside")
                        .executes(PylonCommand::testInside)
                )
                .then(CommandManager.literal("mode")
                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                .executes(PylonCommand::setMode)
                        )
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

    private static int testInside(CommandContext<ServerCommandSource> ctx) {
        var src = ctx.getSource();
        var player = src.getPlayer();
        var sw = player.getServerWorld();
        var playerPos = player.getBlockPos();

        // 1) Find nearest controller (tune radius as needed)
        int searchRadius = 96;
        BlockPos nearest = null;
        double bestD2 = searchRadius * searchRadius;

        for (BlockPos cPos : PylonControllerState.get(sw).getAll()) {
            double d2 = cPos.getSquaredDistance(playerPos);
            if (d2 <= bestD2) {
                bestD2 = d2;
                nearest = cPos;
            }
        }

        if (nearest == null) {
            src.sendFeedback(() -> net.minecraft.text.Text.literal("No pylon controller found within " + searchRadius + " blocks."), false);
            return 0;
        }

        var be = sw.getBlockEntity(nearest);
        if (!(be instanceof PylonControllerBlockEntity ctrl)) {
            BlockPos finalNearest1 = nearest;
            src.sendFeedback(() -> net.minecraft.text.Text.literal("Controller at " + finalNearest1 + " has no valid block entity."), false);
            return 0;
        }

        // 2) Make sure hull is up to date (uses your strong resync)
        ctrl.forceRefresh(sw); // or resyncIfEmpty(sw) if that’s what you have
        ctrl.serverTick();

        var hull = ctrl.getHullClosed();
        if (hull == null || hull.size() < 3) {
            src.sendFeedback(() -> net.minecraft.text.Text.literal("Controller hull is empty or has fewer than 3 points."), false);
            return 0;
        }

        int yMin = ctrl.getYMin();
        int yMax = ctrl.getYMax();

        // 3) Build an AABB around hull extents
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : hull) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }

        // Expand by 1 block to fully cover edges
        var box = new net.minecraft.util.math.Box(
                minX,      yMin,      minZ,
                maxX + 1,  yMax + 1,  maxZ + 1
        );

        // 4) Collect entities in box and filter precisely with isEntityInside
        java.util.List<net.minecraft.entity.Entity> inside = sw.getOtherEntities(
                null,
                box,
                e -> ctrl.isEntityInside(e)
        );

        // 5) Report results
        int count = inside.size();
        BlockPos finalNearest = nearest;
        src.sendFeedback(() -> net.minecraft.text.Text.literal(
                "Controller at " + finalNearest + " hull contains " + count + " entities."
        ), false);

        // Optionally list some names
        if (!inside.isEmpty()) {
            StringBuilder sb = new StringBuilder("Entities: ");
            int show = Math.min(inside.size(), 8);
            for (int i = 0; i < show; i++) {
                if (i > 0) sb.append(", ");
                sb.append(inside.get(i).getDisplayName().getString());
            }
            if (inside.size() > show) sb.append(" (+").append(inside.size() - show).append(" more)");
            src.sendFeedback(() -> net.minecraft.text.Text.literal(sb.toString()), false);
        }

        // Also tell you whether *you* are inside
        boolean playerInside = ctrl.isEntityInside(player);
        src.sendFeedback(() -> net.minecraft.text.Text.literal("You are " + (playerInside ? "INSIDE" : "OUTSIDE") + " this hull."), false);

        return 1;
    }

    private static int setMode(CommandContext<ServerCommandSource> context) {
        ServerCommandSource src = context.getSource();
        ServerPlayerEntity player = src.getPlayer();
        ServerWorld world = src.getWorld();

        String raw = StringArgumentType.getString(context, "mode");
        String upper = raw.toUpperCase(Locale.ROOT);

        PylonMode mode;
        try {
            mode = PylonMode.valueOf(upper);
        } catch (IllegalArgumentException e) {
            String valid = Arrays.stream(PylonMode.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            src.sendError(Text.literal(
                    "Unknown pylon mode '" + raw + "'. Valid: " + valid
            ));
            return 0;
        }

        PlayerPylonState.get(world).setMode(player.getUuid(), mode);
        src.sendFeedback(
                () -> Text.literal("Set your pylon mode to " + mode.name()),
                false
        );
        return 1;
    }
}
