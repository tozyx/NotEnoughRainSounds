package com.tozyx.notenoughrainsounds.client.weather;

import com.tozyx.notenoughrainsounds.client.rain.RainSound;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/** Looks for solid cover along the sound path without querying unloaded chunks. */
public final class SoundOcclusion {
    private static final double MAX_TRACE_DISTANCE = 32;

    private SoundOcclusion() {}

    static boolean spatial(SoundInstance sound) {
        return !sound.isRelative() && sound.getAttenuation() != SoundInstance.Attenuation.NONE;
    }

    static boolean rainBlocked(ClientLevel world, Vec3 listener, Vec3 source) {
        return rainBlocked(world, listener, source, pos -> loaded(world, pos));
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
        return trace(world, listener, impact, loaded, false);
    }

    public static boolean blockedNearby(ClientLevel world, Vec3 listener, Vec3 source) {
        return blockedNearby(world, listener, source, pos -> loaded(world, pos));
    }

    static boolean blockedNearby(BlockGetter world, Vec3 listener, Vec3 source, Predicate<BlockPos> loaded) {
        return blockedNearby(world, listener, source, loaded, false);
    }

    public static boolean materialBlocked(ClientLevel world, Vec3 listener, Vec3 source, RainSound sound) {
        return blockedNearby(world, listener, source, pos -> loaded(world, pos), sound == RainSound.FOLIAGE);
    }

    static boolean blockedNearby(BlockGetter world, Vec3 listener, Vec3 source, Predicate<BlockPos> loaded,
                                 boolean throughLeaves) {
        Vec3 direction = source.subtract(listener);
        double distance = direction.length();
        if (!Double.isFinite(distance)) return false;
        // A far thunderclap still meets nearby walls. Only trace the local part of its path.
        Vec3 end = distance > MAX_TRACE_DISTANCE
                ? listener.add(direction.scale(MAX_TRACE_DISTANCE / distance)) : source;
        return trace(world, listener, end, loaded, throughLeaves);
    }

    private static boolean trace(BlockGetter world, Vec3 listener, Vec3 end, Predicate<BlockPos> loaded,
                                 boolean throughLeaves) {
        return BlockGetter.traverseBlocks(listener, end, world, (scene, pos) -> {
            // Stop at unknown geometry, while keeping any solid hit already found along the path.
            if (!loaded.test(pos)) return Boolean.FALSE;
            var state = scene.getBlockState(pos);
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
