package io.github.apace100.origins.util;

import io.github.apace100.origins.component.OriginComponent;
import io.github.apace100.origins.origin.Origin;
import io.github.apace100.origins.origin.OriginLayer;
import io.github.apace100.origins.origin.OriginLayers;
import io.github.apace100.origins.origin.OriginRegistry;
import io.github.apace100.origins.registry.ModComponents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class DefaultOriginAssigner {
    private static final Identifier DEFAULT_ORIGIN_ID = new Identifier("origins", "test");

    public static void register()
    {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;

            OriginComponent comp = ModComponents.ORIGIN.get(player);

            for (OriginLayer layer : OriginLayers.getLayers())
            {
                if(layer.isEnabled() && comp.hasOrigin(layer))
                {
                    Origin origin = OriginRegistry.get(DEFAULT_ORIGIN_ID);
                    if (origin != null)
                        comp.setOrigin(layer, origin);
                }
            }

            comp.sync();
        });
    }
}
