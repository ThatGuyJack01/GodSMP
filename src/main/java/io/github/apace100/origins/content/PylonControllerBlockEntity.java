package io.github.apace100.origins.content;

import io.github.apace100.origins.Origins;
import io.github.apace100.origins.content.pylon.*;
import io.github.apace100.origins.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.core.jmx.Server;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class PylonControllerBlockEntity extends BlockEntity implements OwnablePylon {
    public static final double LINK_RADIUS = 20.0;
    public static final double SCAN_RADIUS = 16.0;
    private static final int MAX_CONTROLLERS = 4;
    private static final int REBUILD_DEBOUNCE_TICKS = 8;

    private final Set<BlockPos> localPylons = new HashSet<>();
    private final Set<BlockPos> neighborControllers = new HashSet<>();
    private UUID networkId = UUID.randomUUID();

    private boolean hullDirty = true;
    private long nextRebuildTick = 0L;

    private List<BlockPos> hullVerticesClosed = List.of(); // ordered + last==first
    private int yMin = 0, yMax = 0;

    private UUID owner;

    public PylonControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PYLON_CONTROLLER, pos, state);
    }

    public void onTopologyEvent(BlockPos changed, PylonTopoEvent type) {
        if (!(world instanceof ServerWorld serverWorld)) return;

        boolean inPylonRange = changed.isWithinDistance(this.pos, SCAN_RADIUS);
        boolean inCtrlRange = changed.isWithinDistance(this.pos, LINK_RADIUS);
        boolean touched = false;

        switch (type) {
            case PYLON_ADDED -> {
                if(inPylonRange) touched = localPylons.add(changed);
            }
            case PYLON_REMOVED -> {
                if(inPylonRange) touched = localPylons.remove(changed);
            }
            case CTRL_ADDED -> {
                if(inCtrlRange && !changed.equals(this.pos)) touched = neighborControllers.add(changed);
            }
            case CTRL_REMOVED -> {
                if(neighborControllers.remove(changed)) touched = true;
            }
        }

        if(!touched && type != PylonTopoEvent.CTRL_ADDED && type != PylonTopoEvent.CTRL_REMOVED) return;

        reconcileNetwork(serverWorld);

        hullDirty = true;
        nextRebuildTick = serverWorld.getTime() + REBUILD_DEBOUNCE_TICKS;
    }

    private void reconcileNetwork(ServerWorld serverWorld) {
        List<BlockPos> candidates = neighborControllers.stream()
                .filter(p -> p.isWithinDistance(this.pos, LINK_RADIUS))
                .collect(Collectors.toCollection(ArrayList::new));
        candidates.add(this.pos);

        candidates.sort((Comparator.comparingDouble(p -> p.getSquaredDistance(this.pos))));
        if(candidates.size() > MAX_CONTROLLERS) {
            candidates = candidates.subList(0, MAX_CONTROLLERS);
        }

        BlockPos winner = Collections.min(candidates, Comparator
                .comparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ));

        UUID id = uuidFromPos(serverWorld, winner);
        this.networkId = id;

        for(BlockPos other : candidates) {
            if(other.equals(this.pos)) continue;
            var blockEntity = serverWorld.getBlockEntity(other);
            if(blockEntity instanceof PylonControllerBlockEntity ctrl) {
                ctrl.networkId = id;
            }
        }
    }

    private static UUID uuidFromPos(ServerWorld world, BlockPos pos) {
        String dim = world.getRegistryKey().getValue().toString();
        String key = "yourmod:controller@" + dim + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    public void serverTick() {
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (!hullDirty || serverWorld.getTime() < nextRebuildTick) return;

        List<BlockPos> members = new ArrayList<>();
        members.add(this.pos);
        for(BlockPos other : neighborControllers) {
            var blockEntity = serverWorld.getBlockEntity(other);
            if(blockEntity instanceof PylonControllerBlockEntity ctrl && networkId.equals(ctrl.networkId)) {
                members.add(other);
            }
        }

        Set<BlockPos> union = new HashSet<>(localPylons);
        for(BlockPos other : members) {
            if(other.equals(this.pos)) continue;
            var blockEntity = serverWorld.getBlockEntity(other);
            if(blockEntity instanceof PylonControllerBlockEntity ctrl) {
                union.addAll(ctrl.localPylons);
            }
        }

        var area = PylonArea.build(union, PylonArea.YPolicy.aroundMean(-4, +6));
        var verts = area.getVertexBlocks();
        List<BlockPos> closed = new ArrayList<>(verts.size() + (verts.size() >= 2 ? 1 : 0));
        closed.addAll(verts);
        if (verts.size() >= 2) closed.add(verts.get(0));

        this.hullVerticesClosed = closed;
        this.yMin = area.getYMin();
        this.yMax = area.getYMax();

        this.hullDirty = false;
        this.markDirty();
    }

    public List<BlockPos> getHullClosed() { return hullVerticesClosed; }
    public int getYMin() { return yMin; }
    public int getYMax() { return yMax; }
    public UUID getNetworkId() { return networkId; }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putUuid("net", networkId);
        nbt.putInt("yMin", yMin);
        nbt.putInt("yMax", yMax);
        if (owner != null) nbt.putUuid("Owner", owner);
        Origins.LOGGER.info("[PylonCtrlBE] writeNbt pos={} owner={}", this.pos, this.owner);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.containsUuid("net")) networkId = nbt.getUuid("net");
        yMin = nbt.getInt("yMin");
        yMax = nbt.getInt("yMax");
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        hullDirty = true;
        Origins.LOGGER.info("[PylonCtrlBE] readNbt pos={} owner={}", this.pos, this.owner);
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        if(world instanceof ServerWorld serverWorld) forceRefresh(serverWorld);
    }

    private void refresh(ServerWorld world) {
        localPylons.clear();
        double r2 = SCAN_RADIUS * SCAN_RADIUS;
        for(BlockPos p : PylonState.get(world).getPositions()) {
//            if(!world.isChunkLoaded(p)) continue;
            if (p.getSquaredDistance(this.pos) <= r2) {
                if(this.owner != null) {
                    BlockEntity be = world.getBlockEntity(p);
                    if(!(be instanceof PylonBlockEntity pylonBe)) continue;
                    UUID pOwner = pylonBe.getOwner();
                    if(!Objects.equals(this.owner, pOwner)) continue;
                } else {
                    continue;
                }
                localPylons.add(p);
            }
        }

        neighborControllers.clear();
        double lr2 = LINK_RADIUS * LINK_RADIUS;
        for(BlockPos c : PylonControllerState.get(world).getAll()) {
            if(c.equals(this.pos)) continue;
//            if(!world.isChunkLoaded(c)) continue;
            if(c.getSquaredDistance(this.pos) <= lr2) {
                BlockEntity be = world.getBlockEntity(c);
                if(!(be instanceof PylonControllerBlockEntity otherCtrl)) continue;
                if(!Objects.equals(this.owner, otherCtrl.getOwner())) continue;

                neighborControllers.add(c);
            }
        }
    }

    public void forceRefresh(ServerWorld world) {
        refresh(world);
        reconcileNetwork(world);
        hullDirty = true;
        nextRebuildTick = world.getTime();
    }

    public boolean isEntityInside(Entity e) {
        var pos = e.getPos();
        return isPosInsideHull(pos.x, pos.y, pos.z);
    }

    public boolean isPosInsideHull(double x, double y, double z) {
        if (hullVerticesClosed == null || hullVerticesClosed.isEmpty()) return false;

        if (y < this.yMin || y > this.yMax) return false;

        return isPointInPolygonXZ(x, z);
    }

    private boolean isPointInPolygonXZ(double x, double z) {
        int n = hullVerticesClosed.size();
        if (n < 3) return false;

        int effectiveN = n;
        if (n >= 2) {
            BlockPos first = hullVerticesClosed.get(0);
            BlockPos last = hullVerticesClosed.get(n - 1);
            if(first.getX() == last.getX() && first.getZ() == last.getZ())
            {
                effectiveN = n - 1;
                if (effectiveN < 3) return false;
            }
        }

        boolean inside = false;
        int j = effectiveN -1;

        for(int i = 0; i < effectiveN; i++) {
            BlockPos pi = hullVerticesClosed.get(i);
            BlockPos pj = hullVerticesClosed.get(j);

            double xi = pi.getX() + 0.5;
            double zi = pi.getZ() + 0.5;
            double xj = pj.getX() + 0.5;
            double zj = pj.getZ() + 0.5;

            boolean intersect = ((zi > z) != (zj > z)) && (x < (xj - xi) * (z - zi) / (zj - zi + 1e-9) + xi);

            if (intersect) inside = !inside;
            j = i;
        }

        return inside;
    }

    @Override
    public @Nullable UUID getOwner() {
        return owner;
    }

    @Override
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    public PylonMode getOwnerMode() {
        if(owner == null || !(world instanceof ServerWorld sw)) {
            return PylonMode.NONE;
        }
        return PlayerPylonState.get(sw).getMode(owner);
    }
}