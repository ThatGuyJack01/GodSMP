package io.github.apace100.origins.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class PylonRenderClient {
    private static RenderJob currentJob;

    public static void init(Identifier packetId) {
        ClientPlayNetworking.registerGlobalReceiver(packetId, (client, handler, buf, responseSender) ->
                client.execute(() -> {
                    if(client.world == null) return;
                    Identifier dim = buf.readIdentifier();
                    int duration = buf.readVarInt();
                    int count = buf.readVarInt();
                    List<Vec3d> pts = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) pts.add(Vec3d.ofCenter(buf.readBlockPos()));
                    if (!client.world.getRegistryKey().getValue().equals(dim)) return;
                    currentJob = new RenderJob(client.world.getRegistryKey(), client.world.getTime() + Math.max(1, duration), pts);
                })
            );
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> currentJob = null);
    }

    private static void tick(MinecraftClient mc) {
        if(mc == null || mc.world == null || currentJob == null) return;
        if (!mc.world.getRegistryKey().equals(currentJob.dimensionId)) { currentJob = null; return; }
        long now = mc.world.getTime();
        if (now >= currentJob.expiryTick) { currentJob = null; return; }
        if ((now & 1L) != 0) return;

        ParticleEffect fx = new DustParticleEffect(new Vector3f(0.95f, 0.25f, 0.25f), 1.0f);
        for (Vec3d p : currentJob.points) mc.world.addParticle(fx, p.x, p.y + 0.05, p.z, 0, 0.01, 0);
        double spacing = 0.4;
        for(int i = 0; i < currentJob.points.size() - 1; i++) spawnSegment(mc, fx, currentJob.points.get(i), currentJob.points.get(i+1), spacing);
    }

    private static void spawnSegment(MinecraftClient mc, ParticleEffect fx, Vec3d a, Vec3d b, double spacing) {
        Vec3d d = b.subtract(a); double len = d.length(); if (len < 1e-4) return;
        Vec3d step = d.normalize().multiply(spacing); int n = Math.max(1, (int)Math.floor(len/spacing));
        Vec3d cur = a; for (int s = 0; s <= n; s++) { mc.world.addParticle(fx, cur.x, cur.y, cur.z, 0, 0, 0); cur = cur.add(step); }
    }

    private record RenderJob(RegistryKey<World> dimensionId, long expiryTick, List<Vec3d> points) {}
}
