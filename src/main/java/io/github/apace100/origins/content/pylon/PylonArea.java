package io.github.apace100.origins.content.pylon;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public final class PylonArea {
    private final List<Vec2f> verticesXZ;

    private final List<BlockPos> vertexBlocks;

    private final double areaXZ;

    private final int yMin;
    private final int yMax;

    private PylonArea(List<Vec2f> verts, List<BlockPos> blocks, int yMin, int yMax) {
        this.verticesXZ = Collections.unmodifiableList(verts);
        this.vertexBlocks = Collections.unmodifiableList(blocks);
        this.yMin = yMin;
        this.yMax = yMax;
        this.areaXZ = shoelaceArea(verts);
    }

    public static PylonArea build(Collection<BlockPos> pylons, YPolicy yPolicy) {
        Objects.requireNonNull(pylons, "pylons");
        if (pylons.size() < 3) {
            // uh?
        }

        Map<Long, BlockPos> unique = new HashMap<>();
        for (BlockPos p : pylons) {
            long key = (((long)p.getX()) << 32) ^ (p.getZ() & 0xffffffffL);
            BlockPos prev = unique.get(key);
            if (prev == null || p.getY() < prev.getY()) unique.put(key, p);
        }

        List<Vec2f> points = new ArrayList<>(unique.size());
        List<BlockPos> backing = new ArrayList<>(unique.size());
        for (BlockPos p : unique.values()) {
            points.add(new Vec2f(p.getX() + 0.5f, p.getZ() + 0.5f)); // center of block for lines
            backing.add(p);
        }

        // 2) Convex hull on XZ
        List<Integer> hullIdx = convexHullIndices(points);
        List<Vec2f> verts = new ArrayList<>(hullIdx.size());
        List<BlockPos> vertsBlocks = new ArrayList<>(hullIdx.size());
        for (int idx : hullIdx) {
            verts.add(points.get(idx));
            vertsBlocks.add(backing.get(idx));
        }

        int[] yBand = yPolicy != null ? yPolicy.compute(backing) : YPolicy.fixedBand( -4, +6 ).compute(backing);
        int yMin = yBand[0];
        int yMax = yBand[1];

        return new PylonArea(verts, vertsBlocks, yMin, yMax);
    }

    private static List<Integer> convexHullIndices(List<Vec2f> pts) {
        int n = pts.size();
        if (n <= 1) return indexRange(n);

        // sort by x, then z
        List<Integer> ord = indexRange(n);
        ord.sort((i, j) -> {
            Vec2f a = pts.get(i), b = pts.get(j);
            int cx = Float.compare(a.x, b.x);
            return (cx != 0) ? cx : Float.compare(a.y, b.y); // y=Z here
        });

        List<Integer> lower = new ArrayList<>();
        for (int i : ord) {
            while (lower.size() >= 2 && cross(pts.get(lower.get(lower.size()-2)), pts.get(lower.get(lower.size()-1)), pts.get(i)) <= 0f) {
                lower.remove(lower.size()-1);
            }
            lower.add(i);
        }
        List<Integer> upper = new ArrayList<>();
        for (int k = ord.size()-1; k >= 0; k--) {
            int i = ord.get(k);
            while (upper.size() >= 2 && cross(pts.get(upper.get(upper.size()-2)), pts.get(upper.get(upper.size()-1)), pts.get(i)) <= 0f) {
                upper.remove(upper.size()-1);
            }
            upper.add(i);
        }

        lower.remove(lower.size()-1);
        upper.remove(upper.size()-1);
        lower.addAll(upper);
        return lower;
    }

    private static float cross(Vec2f a, Vec2f b, Vec2f c) {
        float abx = b.x - a.x, abz = b.y - a.y;
        float bcx = c.x - b.x, bcz = c.y - b.y;
        return abx * bcz - abz * bcx;
    }

    private static List<Integer> indexRange(int n) {
        List<Integer> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(i);
        return out;
    }

    private static double shoelaceArea(List<Vec2f> v) {
        int m = v.size();
        if (m < 3) return 0.0;
        double sum = 0;
        for (int i = 0; i < m; i++) {
            Vec2f a = v.get(i);
            Vec2f b = v.get((i + 1) % m);
            sum += (double)a.x * b.y - (double)b.x * a.y;
        }
        return Math.abs(sum) * 0.5;
    }

    public List<Vec2f> getVerticesXZ() { return verticesXZ; }

    public List<BlockPos> getVertexBlocks() { return vertexBlocks; }

    public int edgeCount() { return verticesXZ.size(); }

    public Vec3d getEdgeStart3D(int i) {
        int n = verticesXZ.size();
        if (n == 0) return Vec3d.ZERO;
        Vec2f v = verticesXZ.get(i % n);
        double y = (yMin + yMax) * 0.5;
        return new Vec3d(v.x, y, v.y);
    }

    public Vec3d getEdgeEnd3D(int i) {
        int n = verticesXZ.size();
        if (n == 0) return Vec3d.ZERO;
        Vec2f v = verticesXZ.get((i + 1) % n);
        double y = (yMin + yMax) * 0.5;
        return new Vec3d(v.x, y, v.y);
    }

    public int getYMin() { return yMin; }
    public int getYMax() { return yMax; }

    public double getAreaXZ() { return areaXZ; }

    public boolean containsXZ(double x, double z) {
        int n = verticesXZ.size();
        if (n < 3) return false;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Vec2f a = verticesXZ.get(i);
            Vec2f b = verticesXZ.get(j);
            boolean intersect = ((a.y > z) != (b.y > z))
                    && (x < (b.x - a.x) * (z - a.y) / (b.y - a.y + 1e-9) + a.x);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    public interface YPolicy {
        int[] compute(Collection<BlockPos> all);

        static YPolicy aroundMean(int below, int above) {
            return all -> {
                if (all.isEmpty()) return new int[]{0, 0};
                long sum = 0;
                int c = 0;
                for (BlockPos p : all) {
                    sum += p.getY();
                    c++;
                }
                int mean = (int) Math.round(sum / Math.max(1, c));
                return new int[]{mean + below, mean + above};
            };
        }

        static YPolicy tightToExtents(int padDown, int padUp) {
            return all -> {
                if (all.isEmpty()) return new int[]{0, 0};
                int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                for (BlockPos p : all) {
                    min = Math.min(min, p.getY());
                    max = Math.max(max, p.getY());
                }
                return new int[]{min + padDown, max + padUp};
            };
        }

        static YPolicy fixedBand(int below, int above) {
            return all -> {
                if (all.isEmpty()) return new int[]{0, 0};
                long sum = 0;
                int c = 0;
                for (BlockPos p : all) {
                    sum += p.getY();
                    c++;
                }
                int mid = (int) Math.round(sum / Math.max(1, c));
                return new int[]{mid + below, mid + above};
            };
        }
    }

    public static List<BlockPos> buildHullList(ServerWorld world) {
        var positions = PylonState.get(world).getPositions();
        if (positions.isEmpty()) return List.of();

        var area = PylonArea.build(positions, PylonArea.YPolicy.aroundMean(-4, +6));

        var verts = area.getVertexBlocks();
        if (verts.size() < 2) return List.of();

        var out = new ArrayList<BlockPos>(verts.size() + 1);
        out.addAll(verts);
        out.add(verts.get(0));
        return out;
    }
}
