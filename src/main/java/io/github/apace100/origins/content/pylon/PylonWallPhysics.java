package io.github.apace100.origins.content.pylon;

import io.github.apace100.origins.Origins;
import io.github.apace100.origins.content.PylonControllerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PylonWallPhysics {

    private static final double EPS = 1e-3; // Prevent jitter
    private static final double MIN_PUSH = 0.06;
    private static final double MAX_PUSH = 0.55;
    private static final int WALL_LOCK_TIME = 6;

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

            // 1) Cancel OUTWARD velocity component every tick
            Vec3d vel = entity.getVelocity();
            double vDot = vel.dotProduct(n);
            if (vDot < 0.0) {
                // vel has component pointing OUTWARD (-n), remove it
                entity.setVelocity(vel.subtract(n.multiply(vDot)));
            }

            // 2) Remove OUTWARD movement component this tick
            double mDot = movement.dotProduct(n);
            Vec3d clipped = movement;
            if (mDot < 0.0) {
                clipped = movement.subtract(n.multiply(mDot));
            }

            // 3) Apply inward push every tick (your “must be inside lock” rule)
            Vec3d push = new Vec3d(n.x * INWARD_PUSH, 0.0, n.z * INWARD_PUSH);

            return new Vec3d(clipped.x + push.x, movement.y, clipped.z + push.z);
        }
        WALL_LOCK_TICKS.remove(entity.getUuid());
        WALL_LOCK_NORMAL.remove(entity.getUuid());


        if(movement.x == 0.0 && movement.z == 0.0) return movement;

        Vec3d start = entity.getPos();
        Vec3d end = start.add(movement);

        PylonControllerBlockEntity ctrl = findContainingWallController(entity.getWorld(), entity, start);
        if(ctrl == null) return movement;

        if(ctrl.isPosInsideHull(end.x, end.y, end.z)) return movement;

        double tHit = earliestHullHitT(ctrl.getHullClosed(), start, end);
        if(tHit < 0.0) {
            return new Vec3d(0.0, movement.y, 0.0);
        }

        WALL_LOCK_TICKS.put(entity.getUuid(), WALL_LOCK_TIME);
        Vec3d n = new Vec3d(-movement.x, 0.0, -movement.z);
        if (n.lengthSquared() > 1e-9) {
            WALL_LOCK_NORMAL.put(entity.getUuid(), n.normalize());
        }

        double factor = Math.max(0.0, tHit - EPS);
        return new Vec3d(movement.x * factor, movement.y, movement.z * factor);
    }

    private static double calculatePushAmount(Entity entity) {
        double speed = entity.getVelocity().horizontalLength();
        double peakSpeed = 0.5; // Bad practice defining this here but I'm lazy

        double t = speed / peakSpeed;
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        return MIN_PUSH + (MAX_PUSH - MIN_PUSH) * t;
    }

    private static PylonControllerBlockEntity findContainingWallController(World world, Entity entity, Vec3d start) {
        // CLIENT: only predict for the local player to avoid messing with other entities
        if (world.isClient) {
            if (!(entity instanceof PlayerEntity pe) || !pe.isMainPlayer()) return null;

            BlockPos center = entity.getBlockPos();
            int r = (int) PylonControllerBlockEntity.LINK_RADIUS;

            // scan nearby blocks for controller BEs
            for (BlockPos p : BlockPos.iterate(center.add(-r, -8, -r), center.add(r, 8, r))) {
                if (!world.isChunkLoaded(p)) continue;
                BlockEntity be = world.getBlockEntity(p);
                if (!(be instanceof PylonControllerBlockEntity ctrl)) continue;

                if (!(ctrl.getOwnerMode() == PylonMode.WALL)) continue;

                UUID owner = ctrl.getOwner();
//            if(owner != null && entity instanceof PlayerEntity pe && Objects.equals(pe.getUuid(), owner)) continue;


                if (!ctrl.isPosInsideHull(start.x, start.y, start.z)) continue;

                return ctrl;
            }
            return null;
        }

        // SERVER: use PersistentState list (fast)
        ServerWorld sw = (ServerWorld) world;
        for (BlockPos cPos : PylonControllerState.get(sw).getAll()) {
            if (!sw.isChunkLoaded(cPos)) continue;
            BlockEntity be = sw.getBlockEntity(cPos);
            if (!(be instanceof PylonControllerBlockEntity ctrl)) continue;

            if (!(ctrl.getOwnerMode() == PylonMode.WALL)) continue;

            UUID owner = ctrl.getOwner();
//            if(owner != null && entity instanceof PlayerEntity pe && Objects.equals(pe.getUuid(), owner)) continue;


            if (!ctrl.isPosInsideHull(start.x, start.y, start.z)) continue;

            return ctrl;
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
