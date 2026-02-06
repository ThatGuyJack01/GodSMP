package io.github.apace100.origins.networking.packet.s2c;

import io.github.apace100.origins.util.IEntityDataSaver;
import io.github.apace100.origins.util.PlayerPylonDataCache;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

public class PylonSyncDataS2CPacket {
    public static void receive(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        NbtCompound nbt = buf.readNbt();
        if(nbt == null) return;

        client.execute(() -> {
            if(client.player == null) return;
            IEntityDataSaver player = (IEntityDataSaver) client.player;
            player.getPersistentData().put(PlayerPylonDataCache.ROOT_KEY, nbt);
        });
    }
}
