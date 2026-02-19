package io.github.apace100.origins.content.pylon;

import io.github.apace100.origins.content.PylonControllerBlockEntity;
import io.github.apace100.origins.util.IEntityDataSaver;
import io.github.apace100.origins.util.PlayerPylonDataCache;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class PylonWallPhysics {
    private PylonWallPhysics() {}

    public static Vec3d clipMovementIfLeavingHull(Entity entity, Vec3d movement) {
        World world = entity.getWorld();
        if(!world.isClient && world instanceof ServerWorld serverWorld)
            return validateMovement(entity, movement, PylonState.get(serverWorld).getPositions(), (Set<BlockPos>) PylonControllerState.get(serverWorld).getAll());
        if(world.isClient) return validateMovement(entity, movement, PlayerPylonDataCache.getPylonNodes((IEntityDataSaver) entity), PlayerPylonDataCache.getPylonControllers((IEntityDataSaver) entity));
        return movement;
    }

    private static Vec3d validateMovement(Entity entity, Vec3d movement, Set<BlockPos> nodes, Set<BlockPos>  controllers) {
        World world = entity.getWorld();
        for (BlockPos controllerPos : controllers) {
            if (!world.isChunkLoaded(controllerPos)) continue;
            BlockEntity blockEntity = world.getBlockEntity(controllerPos);

            if (!(blockEntity instanceof PylonControllerBlockEntity ctrl)) continue;

            if (ctrl.getOwnerMode() != PylonMode.WALL) continue;

            UUID owner = ctrl.getOwner();
//            if(owner != null && entity instanceof PlayerEntity pe && Objects.equals(pe.getUuid(), owner)) continue;

            if (ctrl.isEntityInside(entity)) {
                Box box = entity.getBoundingBox().stretch(movement);
                boolean test = boxHitsArea(box, ctrl);
                if (test) return calculateMovement(entity, movement, ctrl);
            }
        }
        return movement;
    }

    private static boolean boxHitsArea(Box box, PylonControllerBlockEntity ctrl) {
        double minX = box.minX, minY = box.minY, minZ = box.minZ;
        double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;

        double[] xs = {minX, maxX};
        double[] ys = {minY, maxY};
        double[] zs = {minZ, maxZ};

        for (double x : xs)
            for (double y : ys)
                for (double z : zs)
                    if(ctrl.isPosInside(x, y, z)) return true;

        // center checks just in case
        double cx = (minX + maxX)/2.0;
        double cy = (minY + maxY)/2.0;
        double cz = (minZ + maxZ)/2.0;
        if(ctrl.isPosInside(cx, cy, cz)) return true;

        if (ctrl.isPosInside(minX, cy, cz)) return true;
        if (ctrl.isPosInside(maxX, cy, cz)) return true;
        if (ctrl.isPosInside(cx, minY, cz)) return true;
        if (ctrl.isPosInside(cx, maxY, cz)) return true;
        if (ctrl.isPosInside(cx, cy, minZ)) return true;
        if (ctrl.isPosInside(cx, cy, maxZ)) return true;

        return false;
    }

    private static Vec3d calculateMovement(Entity entity, Vec3d movement, PylonControllerBlockEntity ctrl) {
        Box bb = entity.getBoundingBox();
        double r = 0.5 * Math.max(bb.maxX - bb.minX, bb.maxZ - bb.minZ);

        List<Vec2> verts = getHullXZ(ctrl);
        if (verts.size() < 3) return movement;

        Vec2[] inwardNormals = buildInwardNormals(verts);

        Vec2 p0 = new Vec2(entity.getX(), entity.getZ());
        Vec2 v0 = new Vec2(movement.x, movement.z);

        if (Math.abs(v0.x) < 1e-12 && Math.abs(v0.z) < 1e-12) return movement;

        boolean startInside = ctrl.isPosInside(entity.getX(), entity.getY(), entity.getZ());

        double target = startInside ? r : -r;

        double d0 = minDistToEdges(p0, verts, inwardNormals);
        if (startInside && d0 < r) {
            int k = minDistEdgeIndex(p0, verts, inwardNormals);
            Vec2 n = inwardNormals[k];
            double push = (r - d0) + EPS;
            p0 = p0.add(n.mul(push));
        } else if (!startInside && d0 > -r) {
            int k = minDistEdgeIndex(p0, verts, inwardNormals);
            Vec2 n = inwardNormals[k].mul(-1);
            double push = (d0 + r) + EPS;
            p0 = p0.add(n.mul(push));
        }

        Vec2 startP = p0;
        Vec2 p = p0;
        Vec2 v = v0;

        final double cornerTol = 2e-3;

        for (int iter = 0; iter < 3; iter++) {
            if (Math.abs(v.x) < 1e-12 && Math.abs(v.z) < 1e-12) break;

            Vec2 pEnd = p.add(v);
            double dEnd = minDistToEdges(pEnd, verts, inwardNormals);
            boolean okEnd = startInside ? (dEnd >= target) : (dEnd <= target);
            if (okEnd) break;

            double lo = 0.0, hi = 1.0;
            for (int i = 0; i < 22; i++) {
                double mid = (lo + hi) * 0.5;
                Vec2 pm = p.add(v.mul(mid));
                double dm = minDistToEdges(pm, verts, inwardNormals);
                boolean ok = startInside ? (dm >= target) : (dm <= target);
                if (ok) lo = mid; else hi = mid;
            }

            double t = Math.max(0.0, lo - 1e-6);
            Vec2 pHit = p.add(v.mul(t));

            Vec2 rem = v.mul(1.0 - t);

            for (int e = 0; e < verts.size(); e++) {
                Vec2 inward = inwardNormals[e];
                Vec2 nAllowed = startInside ? inward : inward.mul(-1);

                double de = pHit.sub(verts.get(e)).dot(inward);

                boolean active = startInside
                        ? (de <= r + cornerTol)
                        : (de >= -r - cornerTol);

                if (!active) continue;

                double into = rem.dot(nAllowed);
                if (into < 0) rem = rem.sub(nAllowed.mul(into));
            }

            p = p.add(v.mul(t));
            v = rem;
        }

        Vec2 out = p.sub(startP).add(v);
        return new Vec3d(out.x, movement.y, out.z);

    }

    private static final double EPS = 1e-4;

    private record Vec2(double x, double z) {
        Vec2 add(Vec2 o) { return new Vec2(x + o.x, z + o.z); }
        Vec2 mul(double s) { return new Vec2(x * s, z * s); }
        Vec2 sub(Vec2 o) { return new Vec2(x - o.x, z - o.z); }
        double dot(Vec2 o) { return x * o.x + z * o.z; }
        double len() { return Math.sqrt(x * x + z * z); }
        Vec2 norm() {
            double l = len();
            return l <= 1e-12 ? new Vec2(0, 0) : new Vec2(x / l, z / l);
        }
    }

    private static List<Vec2> getHullXZ(PylonControllerBlockEntity ctrl) {
        var hull = ctrl.getHullClosed();
        int n = hull.size();
        if (n < 3) return List.of();

        BlockPos first = hull.get(0);
        BlockPos last = hull.get(n - 1);
        if (first.getX() == last.getX() && first.getZ() == last.getZ()) n -= 1;
        if (n < 3) return List.of();

        var out = new java.util.ArrayList<Vec2>(n);
        for (int i = 0; i < n; i++) {
            BlockPos p = hull.get(i);
            out.add(new Vec2(p.getX() + 0.5, p.getZ() + 0.5));
        }
        return out;
    }

    private static boolean isCCW(List<Vec2> v) {
        double a2 = 0.0;
        for (int i = 0; i < v.size(); i++) {
            Vec2 a = v.get(i);
            Vec2 b = v.get((i + 1) % v.size());
            a2 += a.x * b.z - b.x * a.z;
        }
        return a2 > 0.0;
    }

    private static Vec2[] buildInwardNormals(List<Vec2> verts) {
        boolean ccw = isCCW(verts);
        int n = verts.size();
        Vec2[] normals = new Vec2[n];

        for (int i = 0; i < n; i++) {
            Vec2 a = verts.get(i);
            Vec2 b = verts.get((i + 1) % n);
            double dx = b.x - a.x;
            double dz = b.z - a.z;

            Vec2 inward = ccw ? new Vec2(-dz, dx) : new Vec2(dz, -dx);
            normals[i] = inward.norm();
        }
        return normals;
    }

    private static double minDistToEdges(Vec2 p, List<Vec2> verts, Vec2[] inwardNormals) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < verts.size(); i++) {
            Vec2 vi = verts.get(i);
            double d = p.sub(vi).dot(inwardNormals[i]);
            if (d < min) min = d;
        }
        return min;
    }

    private static int minDistEdgeIndex(Vec2 p, List<Vec2> verts, Vec2[] inwardNormals) {
        int idx = 0;
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < verts.size(); i++) {
            Vec2 vi = verts.get(i);
            double d = p.sub(vi).dot(inwardNormals[i]);
            if (d < min) { min = d; idx = i; }
        }
        return idx;
    }
}
