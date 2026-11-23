package com.tozyx.notenoughrainsounds.client.rain;

import com.tozyx.notenoughrainsounds.client.config.SoundConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Samples the upper hull of the ridden boat and a small number of nearby boats. */
final class BoatRainSampler {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int MAX_BOATS = 4;
    private static final MaterialResolver.Match WOOD = new MaterialResolver.Match(RainMaterial.WOOD, true, false);
    private List<RainSurface> candidates = List.of();
    private Entity previousVehicle;
    private long nextScan;

    void clear() {
        candidates = List.of();
        previousVehicle = null;
        nextScan = 0;
    }

    List<RainSurface> sample(ClientLevel world, Vec3 listener, Entity vehicle, SoundConfig.Rain settings, long tick) {
        if (tick < nextScan && vehicle == previousVehicle) return candidates;
        nextScan = tick + SCAN_INTERVAL_TICKS;
        previousVehicle = vehicle;
        var found = new ArrayList<RainSurface>(MAX_BOATS);
        if (vehicle instanceof Boat boat) {
            RainSurface ridden = inspect(world, listener, boat, true, settings, tick);
            if (ridden != null) found.add(ridden);
        }
        int radius = Math.min(settings.radius, RainSound.ATTENUATION_DISTANCE);
        int height = Math.min(settings.verticalRange, RainSound.ATTENUATION_DISTANCE);
        AABB area = AABB.ofSize(listener, radius * 2, height * 2, radius * 2);
        world.getEntitiesOfClass(Boat.class, area, boat -> boat != vehicle && boat.isAlive()).stream()
                .sorted(Comparator.comparingDouble(boat -> boat.distanceToSqr(listener)))
                .limit(MAX_BOATS - found.size())
                .map(boat -> inspect(world, listener, boat, false, settings, tick))
                .filter(surface -> surface != null)
                .forEach(found::add);
        candidates = found;
        return candidates;
    }

    static RainSurface inspect(ClientLevel world, Vec3 listener, Boat boat, boolean ridden, SoundConfig.Rain settings,
            long tick) {
        if (!boat.isAlive() || boat.isUnderWater()) return null;
        Vec3 hull = new Vec3(boat.getX(), boat.getBoundingBox().maxY, boat.getZ());
        double dx = hull.x - listener.x;
        double dz = hull.z - listener.z;
        if (dx * dx + dz * dz > settings.radius * settings.radius
                || Math.abs(hull.y - listener.y) > settings.verticalRange) return null;

        BlockPos cell = BlockPos.containing(hull);
        if (!world.hasChunkAt(cell) || world.isOutsideBuildHeight(cell)) return null;
        int roof = world.getHeight(Heightmap.Types.MOTION_BLOCKING, cell.getX(), cell.getZ());
        if (roof > cell.getY() + 1) return null;

        // A floating hull can sit above a partial water block without reaching the next block.
        var fluid = world.getFluidState(cell);
        if (fluid.is(FluidTags.WATER) && hull.y <= cell.getY() + fluid.getHeight(world, cell)) return null;
        if (roof > cell.getY()) {
            var shape = world.getBlockState(cell).getCollisionShape(world, cell);
            double top = shape.max(Direction.Axis.Y, hull.x - cell.getX(), hull.z - cell.getZ());
            if (cell.getY() + top >= hull.y) return null;
        }
        if (!world.isRainingAt(cell.atY(Math.max(cell.getY(), roof)))) return null;
        return new RainSurface(cell, hull, WOOD, tick, boat.getId(), ridden);
    }
}
