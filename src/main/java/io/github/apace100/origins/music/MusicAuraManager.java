package io.github.apace100.origins.music;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.origins.component.PortableJukeboxComponent;
import io.github.apace100.origins.power.OriginsPowerTypes;
import io.github.apace100.origins.registry.ModComponents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public final class MusicAuraManager {
    private static final Map<RegistryKey<World>, Map<BlockPos, Identifier>> ACTIVE_JUKEBOXES = new HashMap<>();
    private static final Map<UUID, Set<MusicSource>> HEARD_SOURCES = new HashMap<>();

    private MusicAuraManager() { }

    public static void onJukeboxStarted(ServerWorld world, BlockPos pos, Identifier soundId) {
        if (world == null || pos == null || soundId == null || !DiscRules.has(soundId)) {
            onJukeboxStopped(world, pos);
            return;
        }
        RegistryKey<World> key = world.getRegistryKey();
        ACTIVE_JUKEBOXES.computeIfAbsent(key, unused -> new HashMap<>()).put(pos.toImmutable(), soundId);
    }

    public static void onJukeboxStopped(ServerWorld world, BlockPos pos) {
        if(world == null || pos == null)
            return;
        Map<BlockPos, Identifier> map = ACTIVE_JUKEBOXES.get(world.getRegistryKey());
        if (map != null) {
            map.remove(pos.toImmutable());
            if(map.isEmpty()) ACTIVE_JUKEBOXES.remove(world.getRegistryKey());
        }
    }

    public static void tick(MinecraftServer server) {
        List<PortableSource> portableSources = collectPortableSources(server);
        for(ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if(!PowerHolderComponent.KEY.get(player).hasPower(OriginsPowerTypes.MUSIC_AURA)) {
                HEARD_SOURCES.remove(player.getUuid());
                continue;
            }
            Set<MusicSource> heardNow = collectAudibleSources(player, portableSources);
            Set<MusicSource> previous = HEARD_SOURCES.getOrDefault(player.getUuid(), Collections.emptySet());

            Set<MusicSource> started = new HashSet<>(heardNow);
            started.removeAll(previous);
            if(!started.isEmpty())
                System.out.println("[MusicAura] " + player.getEntityName() + " started hearing " + describe(started));

            Set<MusicSource> stopped = new HashSet<>(previous);
            stopped.removeAll(heardNow);
            if (!stopped.isEmpty())
                System.out.println("[MusicAura] " + player.getEntityName() + " stopped hearing " + describe(stopped));

            HEARD_SOURCES.put(player.getUuid(), new HashSet<>(heardNow));
        }
    }

    private static List<PortableSource> collectPortableSources(MinecraftServer server) {
        List<PortableSource> sources = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PortableSource source = resolvePortableSource(player);
            if (source != null) sources.add(source);
        }
        return sources;
    }

    private static PortableSource resolvePortableSource(ServerPlayerEntity player) {
        PortableJukeboxComponent component = ModComponents.PORTABLE_JUKEBOX.get(player);
        if (component == null || !component.isPlaying()) return null;

        Identifier soundId = fetchPortableSoundId(component);
        if (soundId == null || !DiscRules.has(soundId)) return null;

        Vec3d pos = player.getPos();
        return new PortableSource(player.getUuid(), player.getWorld().getRegistryKey(), soundId, pos);
    }
    private static Identifier fetchPortableSoundId(PortableJukeboxComponent component) {
        if (component == null) return null;
        try {
            Method method = component.getClass().getMethod("getCurrentSoundId");
            Object result = method.invoke(component);
            if(result instanceof Identifier identifier) {
                return identifier;
            }
        } catch (ReflectiveOperationException | ClassCastException ignored) { }
        return DiscRules.resolve(component.getDisc());
    }

    private static Set<MusicSource> collectAudibleSources(ServerPlayerEntity player, List<PortableSource> portableSources) {
        Set<MusicSource> heard = new HashSet<>();
        ServerWorld world = player.getServerWorld();
        RegistryKey<World> key = world.getRegistryKey();
        Map<BlockPos, Identifier> blockSources = ACTIVE_JUKEBOXES.getOrDefault(key, Collections.emptyMap());
        for (Map.Entry<BlockPos, Identifier> entry : blockSources.entrySet()) {
            Identifier soundId = entry.getValue();
            DiscRules.get(soundId).ifPresent(rule -> {
                BlockPos pos = entry.getKey();
                double distSq = player.squaredDistanceTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
                double range = rule.range();
                if(distSq <= range * range) {
                    applyEffects(player, rule);
                    heard.add(new MusicSource(soundId, "block:" + pos.asLong()));
                }
            });
        }
        for(PortableSource source : portableSources) {
            if (!source.worldKey().equals(key)) {
                continue;
            }
            DiscRules.get(source.soundId()).ifPresent(rule -> {
                Vec3d pos = source.position();
                double distSq = player.squaredDistanceTo(pos.x, pos.y, pos.z);
                double range = rule.range();
                if (distSq <= range * range) {
                    applyEffects(player, rule);
                    heard.add(new MusicSource(source.soundId(), "portable:" + source.owner()));
                }
            });
        }
        return heard;
    }

    private static void applyEffects(ServerPlayerEntity holder, DiscRules.DiscRule rule) {
        List<ServerPlayerEntity> recipients = resolveRecipients(holder, rule);
        if (recipients.isEmpty()) {
            return;
        }
        for (ServerPlayerEntity target : recipients) {
            for (StatusEffectInstance effect : rule.effects()) {
                target.addStatusEffect(new StatusEffectInstance(effect));
            }
        }
    }

    private static List<ServerPlayerEntity> resolveRecipients(ServerPlayerEntity holder, DiscRules.DiscRule rule) {
        DiscRules.Target target = rule.target();
        if (target == DiscRules.Target.SELF) {
            return Collections.singletonList(holder);
        }
        MinecraftServer server = holder.getServer();
        if (server == null) {
            return Collections.emptyList();
        }
        double rangeSq = rule.range() * rule.range();
        List<ServerPlayerEntity> recipients = new ArrayList<>();
        for (ServerPlayerEntity candidate : server.getPlayerManager().getPlayerList()) {
            if (candidate == null || candidate.getWorld() != holder.getWorld()) {
                continue;
            }
            if (candidate.squaredDistanceTo(holder) > rangeSq) {
                continue;
            }
            if (target == DiscRules.Target.ALL_EXCLUDE_SELF && candidate.getUuid().equals(holder.getUuid())) {
                continue;
            }
            recipients.add(candidate);
        }
        return recipients;
    }

    private static String describe(Set<MusicSource> sources) {
        return sources.stream().map(source -> source.soundId().toString()).collect(Collectors.joining(", "));
    }

    private record MusicSource(Identifier soundId, String sourceId) { }

    private record PortableSource(UUID owner, RegistryKey<World> worldKey, Identifier soundId, Vec3d position) { }
}
