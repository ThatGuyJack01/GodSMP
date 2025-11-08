package io.github.apace100.origins.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.apace100.origins.content.pylon.PylonArea;
import io.github.apace100.origins.content.pylon.PylonState;
import io.github.apace100.origins.networking.ModPackets;
import io.github.apace100.server.PylonVisualizer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
                    .then(CommandManager.literal("hullit")
                        .then(CommandManager.argument("seconds", IntegerArgumentType.integer())
                            .executes(ctx -> sendHull(ctx, IntegerArgumentType.getInteger(ctx, "seconds"))))
                            .executes(ctx -> sendHull(ctx, 5)
                    )
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
//        src.sendFeedback(() -> Text.literal("Showing convex hull for " + (durationTicks / 20) + "s (" + hull.size() + " points)"), false);
        return 1;
    }
}
