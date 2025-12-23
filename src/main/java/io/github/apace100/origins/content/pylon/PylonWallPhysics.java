package io.github.apace100.origins.content.pylon;

import io.github.apace100.origins.Origins;
import io.github.apace100.origins.content.PylonControllerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PylonWallPhysics {

    private static final double EPS = 1e-3; // Prevent jitter
    private static final double MIN_PUSH = 0.06;
    private static final double MAX_PUSH = 0.35;

    private static final Map<UUID, Integer> WALL_LOCK_TICKS = new HashMap<>();
    private static final Map<UUID, Vec3d> WALL_LOCK_NORMAL = new HashMap<>();


    private PylonWallPhysics() {}

    public static Vec3d clipMovementIfLeavingHull(Entity entity, Vec3d movement) {
        double INWARD_PUSH = calculatePushAmount(entity);

        Integer lock = WALL_LOCK_TICKS.get(entity.getUuid());
        if (lock != null && lock > 0) {
            WALL_LOCK_TICKS.put(entity.getUuid(), lock - 1);

            Vec3d n = WALL_LOCK_NORMAL.get(entity.getUuid());
            if (n == null) {
                return new Vec3d(0.0, movement.y, 0.0);
            }

            return new Vec3d(n.x * INWARD_PUSH, movement.y, n.z * INWARD_PUSH);
        } else {
            WALL_LOCK_TICKS.remove(entity.getUuid());
        }


        if (!(entity.getWorld() instanceof ServerWorld world)) return movement;

        if(movement.x == 0.0 && movement.z == 0.0) return movement;

        Vec3d start = entity.getPos();
        Vec3d end = start.add(movement);

        PylonControllerBlockEntity ctrl = findContainingWallController(world, entity, start);
        if(ctrl == null) return movement;

        if(ctrl.isPosInsideHull(end.x, end.y, end.z)) return movement;

        double tHit = earliestHullHitT(ctrl.getHullClosed(), start, end);
        if(tHit < 0.0) {
            return new Vec3d(0.0, movement.y, 0.0);
        }

        WALL_LOCK_TICKS.put(entity.getUuid(), 2);
        Vec3d n = new Vec3d(-movement.x, 0.0, -movement.z);
        if (n.lengthSquared() > 1e-9) {
            WALL_LOCK_NORMAL.put(entity.getUuid(), n.normalize());
        }

        double factor = Math.max(0.0, tHit - EPS);
        return new Vec3d(movement.x * factor, movement.y, movement.z * factor);
    }

    private static double calculatePushAmount(Entity entity) {
        double speed = entity.getVelocity().horizontalLength();
        double peakSpeed = 0.30; // Bad practice defining this here but I'm lazy

        double t = speed / peakSpeed;
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        return MIN_PUSH + (MAX_PUSH - MIN_PUSH) * t;
    }

    private static PylonControllerBlockEntity findContainingWallController(ServerWorld serverWorld, Entity entity, Vec3d start) {
        for(BlockPos cPos : PylonControllerState.get(serverWorld).getAll()) {
            BlockEntity be = serverWorld.getBlockEntity(cPos);
            if(!(be instanceof PylonControllerBlockEntity ctrl)) continue;

            if(ctrl.getOwnerMode() != PylonMode.WALL) continue;

            UUID owner = ctrl.getOwner();

//            if(owner != null && entity instanceof PlayerEntity pe && Objects.equals(pe.getUuid(), owner)) continue;

            if(ctrl.isPosInsideHull(start.x, start.y, start.z)) return ctrl;
        }
        return null;
    }

    private static double earliestHullHitT(List<BlockPos> hull, Vec3d start, Vec3d end) {
        if(hull == null || hull.size() < 2) return -1.0;

        Vec2f p = new Vec2f((float) start.x, (float) start.z);
        Vec2f q = new Vec2f((float) end.x, (float) end.z);

        double bestT = Double.POSITIVE_INFINITY;

        int n = hull.size();

        for(int i = 0; i < n; i++) {
            BlockPos aPos = hull.get(i);
            BlockPos bPos = hull.get((i+1) % n);

            Vec2f a = new Vec2f(aPos.getX() + 0.5f, aPos.getZ() + 0.5f);
            Vec2f b = new Vec2f(bPos.getX() + 0.5f, bPos.getZ() + 0.5f);

            Double t = segmentIntersectionT(p, q, a, b);
            if (t != null && t >= 0.0 && t <= 1.0 && t < bestT) {
                bestT = t;
            }
        }

        return bestT == Double.POSITIVE_INFINITY ? -1.0 : bestT;
    }

    private static Double segmentIntersectionT(Vec2f p, Vec2f q, Vec2f a, Vec2f b) {
        float rX = q.x - p.x;
        float rY = q.y - p.y;
        float sX = b.x - a.x;
        float sY = b.y - a.y;

        float denom = rX * sY - rY * sX;
        if (Math.abs(denom) < 1e-8f) return null;

        float aPX = a.x - p.x;
        float aPY = a.y - p.y;

        float t = (aPX * sY - aPY * sX) / denom;
        float u = (aPX * rY - aPY * rX) / denom;

        if (t >= 0f && t <= 1f && u >= 0f && u <= 1f) {
            return (double) t;
        }
        return null;
    }
}
