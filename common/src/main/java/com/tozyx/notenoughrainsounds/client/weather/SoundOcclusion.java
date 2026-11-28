package com.tozyx.notenoughrainsounds.client.weather;

import com.tozyx.notenoughrainsounds.client.rain.RainSound;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Looks for solid cover along the sound path without querying unloaded chunks. */
public final class SoundOcclusion {
    private static final double MAX_TRACE_DISTANCE = 32;

    // Initial client chunk packets carry this heightmap. The vertical collision ray skips leaves.
    static final Heightmap.Types ROOF_HEIGHTMAP = Heightmap.Types.MOTION_BLOCKING;

    private SoundOcclusion() {}

    static boolean spatial(SoundInstance sound) {
        return !sound.isRelative() && sound.getAttenuation() != SoundInstance.Attenuation.NONE;
    }

    /** Require cover over a majority of five points, spanning both horizontal axes. */
    public static boolean hasRoof(ClientLevel world, Vec3 listener) {
        return hasRoof(world, listener,
                pos -> world.getHeight(ROOF_HEIGHTMAP, pos.getX(), pos.getZ()),
                pos -> loaded(world, pos));
    }

    static boolean hasRoof(BlockGetter world, Vec3 listener, ToIntFunction<BlockPos> roofHeight,
                           Predicate<BlockPos> loaded) {
        if (!Double.isFinite(listener.x) || !Double.isFinite(listener.y) || !Double.isFinite(listener.z)) return false;

        int coveredX = roofSample(world, listener.add(-1, 0, 0), roofHeight, loaded)
                + roofSample(world, listener.add(1, 0, 0), roofHeight, loaded);
        if (coveredX == 0) return false;
        int coveredZ = roofSample(world, listener.add(0, 0, -1), roofHeight, loaded)
                + roofSample(world, listener.add(0, 0, 1), roofHeight, loaded);
        if (coveredZ == 0) return false;
        return coveredX + coveredZ >= 3 || roofSample(world, listener, roofHeight, loaded) == 1;
    }

    private static int roofSample(BlockGetter world, Vec3 point, ToIntFunction<BlockPos> roofHeight,
                                  Predicate<BlockPos> loaded) {
        BlockPos cell = BlockPos.containing(point);
        if (!loaded.test(cell)) return 0;
        int top = roofHeight.applyAsInt(cell);
        return top > point.y && trace(world, point, new Vec3(point.x, top, point.z), loaded, true, true) ? 1 : 0;
    }

    enum RainShelter { OPEN, ROOF_ONLY, ENCLOSED }

    static RainShelter rainShelter(ClientLevel world, Vec3 listener) {
        return rainShelter(world, listener,
                pos -> world.getHeight(ROOF_HEIGHTMAP, pos.getX(), pos.getZ()),
                pos -> loaded(world, pos));
    }

    static RainShelter rainShelter(BlockGetter world, Vec3 listener, ToIntFunction<BlockPos> roofHeight,
                                    Predicate<BlockPos> loaded) {
        if (!hasRoof(world, listener, roofHeight, loaded)) return RainShelter.OPEN;
        int walls = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Vec3 end = listener.add(8 * direction.getStepX(), 0, 8 * direction.getStepZ());
            if (trace(world, listener, end, loaded, true, true) && ++walls == 3) return RainShelter.ENCLOSED;
        }
        return RainShelter.ROOF_ONLY;
    }

    static boolean shelteredRain(BlockGetter world, RainShelter shelter, Vec3 listener, Vec3 source,
                                 Predicate<BlockPos> loaded) {
        return shelter == RainShelter.ENCLOSED
                || shelter == RainShelter.ROOF_ONLY && rainBlocked(world, listener, source, loaded);
    }

    static boolean shelteredRain(ClientLevel world, RainShelter shelter, Vec3 listener, Vec3 source) {
        return shelteredRain(world, shelter, listener, source, pos -> loaded(world, pos));
    }

    static boolean rainBlocked(BlockGetter world, Vec3 listener, Vec3 source, Predicate<BlockPos> loaded) {
        double distanceSquared = listener.distanceToSqr(source);
        if (!Double.isFinite(distanceSquared) || distanceSquared > MAX_TRACE_DISTANCE * MAX_TRACE_DISTANCE) return false;
        BlockPos surface = BlockPos.containing(source);
        if (!loaded.test(surface)) return false;
        var shape = world.getBlockState(surface).getCollisionShape(world, surface);
        double top = Math.max(shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y),
                world.getFluidState(surface).getHeight(world, surface));
        // Native rain emits at the block centre. Use a point above the hit surface for the ray.
        Vec3 impact = new Vec3(source.x, Math.max(source.y, surface.getY() + top + .01), source.z);
        return trace(world, listener, impact, loaded, false, false);
    }

    public static boolean blockedNearby(ClientLevel world, Vec3 listener, Vec3 source) {
        return blockedNearby(world, listener, source, pos -> loaded(world, pos));
    }

    static boolean blockedNearby(BlockGetter world, Vec3 listener, Vec3 source, Predicate<BlockPos> loaded) {
        return blockedNearby(world, listener, source, loaded, false);
    }

    public static boolean materialBlocked(ClientLevel world, Vec3 listener, Vec3 source, RainSound sound) {
        return materialBlocked(world, listener, source, pos -> loaded(world, pos), sound);
    }

    static boolean materialBlocked(BlockGetter world, Vec3 listener, Vec3 source, Predicate<BlockPos> loaded,
                                   RainSound sound) {
        return blockedNearby(world, listener, source, loaded, sound == RainSound.FOLIAGE,
                sound == RainSound.BAMBOO);
    }

    static boolean blockedNearby(BlockGetter world, Vec3 listener, Vec3 source, Predicate<BlockPos> loaded,
                                 boolean throughLeaves) {
        return blockedNearby(world, listener, source, loaded, throughLeaves, false);
    }

    private static boolean blockedNearby(BlockGetter world, Vec3 listener, Vec3 source, Predicate<BlockPos> loaded,
                                         boolean throughLeaves, boolean throughBamboo) {
        Vec3 direction = source.subtract(listener);
        double distance = direction.length();
        if (!Double.isFinite(distance)) return false;
        // A far thunderclap still meets nearby walls. Only trace the local part of its path.
        Vec3 end = distance > MAX_TRACE_DISTANCE
                ? listener.add(direction.scale(MAX_TRACE_DISTANCE / distance)) : source;
        return trace(world, listener, end, loaded, throughLeaves, throughBamboo);
    }

    private static boolean trace(BlockGetter world, Vec3 listener, Vec3 end, Predicate<BlockPos> loaded,
                                 boolean throughLeaves, boolean throughBamboo) {
        return BlockGetter.traverseBlocks(listener, end, world, (scene, pos) -> {
            // Stop at unknown geometry, while keeping any solid hit already found along the path.
            if (!loaded.test(pos)) return Boolean.FALSE;
            var state = scene.getBlockState(pos);
            if (throughBamboo && (state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING))) return null;
            // Follow canopy rain through the foliage to its impact point, still checking for solid cover.
            if (throughLeaves && (state.getBlock() instanceof LeavesBlock || state.is(BlockTags.LEAVES))) return null;
            var shape = state.getCollisionShape(scene, pos);
            return shape.clip(listener, end, pos) == null ? null : Boolean.TRUE;
        }, scene -> Boolean.FALSE);
    }

    private static boolean loaded(ClientLevel world, BlockPos pos) {
        return world.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }
}
