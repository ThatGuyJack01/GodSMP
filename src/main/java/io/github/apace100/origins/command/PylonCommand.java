package io.github.apace100.origins.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.github.apace100.origins.content.pylon.PylonState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
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
                        .executes(PylonCommand::listPylons)));
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
}
