package com.tozyx.notenoughrainsounds.client.weather;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RainOcclusionTest {
    private static final Vec3 LISTENER = new Vec3(.5, 65.6, .5);
    private static final Vec3 RAIN = new Vec3(7.5, 63.5, .5);

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void exposedFloorIsNotMisclassifiedByItsOwnCollisionShape() {
        assertFalse(rainBlocked(new Scene(), LISTENER, RAIN));
    }

    @Test void wallBetweenListenerAndRainSelectsShelteredProfile() {
        var world = new Scene();
        world.wall();
        assertTrue(rainBlocked(world, LISTENER, RAIN));
    }

    @Test void openWindowLeavesTheOutsideRainDirect() {
        var world = new Scene();
        world.wall();
        world.blocks.put(new BlockPos(3, 65, 0), Blocks.AIR.defaultBlockState());
        world.blocks.put(new BlockPos(3, 64, 0), Blocks.AIR.defaultBlockState());
        assertFalse(rainBlocked(world, LISTENER, RAIN));
    }

    @Test void glassWindowStillObstructsSound() {
        var world = new Scene();
        world.wall();
        world.blocks.put(new BlockPos(3, 65, 0), Blocks.GLASS.defaultBlockState());
        assertTrue(rainBlocked(world, LISTENER, RAIN));
    }

    @Test void roofSeparatesListenerFromItsRainHitSurface() {
        var world = new Scene();
        world.blocks.put(new BlockPos(0, 68, 0), Blocks.STONE.defaultBlockState());
        assertTrue(rainBlocked(world, LISTENER, new Vec3(.5, 68.5, .5)));
    }

    @Test void slabAndWaterSurfaceDoNotBecomeTheirOwnOccluders() {
        var world = new Scene();
        world.floor = Blocks.STONE_SLAB.defaultBlockState();
        assertFalse(rainBlocked(world, LISTENER, RAIN));
        world.floor = Blocks.WATER.defaultBlockState();
        assertFalse(rainBlocked(world, LISTENER, RAIN));
    }

    @Test void nearbyRoofObstructsMaterialRainAtItsActualImpactPoint() {
        var scene = new Scene();
        scene.blocks.put(new BlockPos(0, 68, 0), Blocks.OAK_PLANKS.defaultBlockState());
        var impact = new Vec3(.5, 69.05, .5);
        assertTrue(SoundOcclusion.blockedNearby(scene, LISTENER, impact, pos -> true));
        assertFalse(SoundOcclusion.blockedNearby(scene, new Vec3(.5, 71, .5), impact, pos -> true));
    }

    @Test void distantThunderMeetsLocalCoverEvenIfTheStrikeIsOutsideLoadedChunks() {
        var scene = new Scene();
        scene.wall();
        Vec3 thunder = new Vec3(210.5, 66, .5);
        assertTrue(SoundOcclusion.blockedNearby(scene, LISTENER, thunder, pos -> pos.getX() < 16));
        scene.blocks.clear();
        assertFalse(SoundOcclusion.blockedNearby(scene, LISTENER, thunder, pos -> pos.getX() < 16));
        // A far wall is not part of the local obstruction check.
        scene.blocks.put(new BlockPos(35, 65, 0), Blocks.STONE.defaultBlockState());
        assertFalse(SoundOcclusion.blockedNearby(scene, LISTENER, thunder, pos -> {
            assertTrue(pos.getX() <= 33, "Trace must stop near the listener");
            return true;
        }));
    }

    @Test void noBlockReadPastTheFirstUnloadedChunk() {
        var scene = new Scene();
        scene.failAtX = 16;
        assertFalse(SoundOcclusion.blockedNearby(scene, LISTENER, new Vec3(90, 66, 0), pos -> pos.getX() < 16));
    }

    private static boolean rainBlocked(BlockGetter world, Vec3 listener, Vec3 source) {
        return SoundOcclusion.rainBlocked(world, listener, source, pos -> true);
    }

    private static final class Scene implements BlockGetter {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockState floor = Blocks.STONE.defaultBlockState();
        int failAtX = Integer.MAX_VALUE;
        void wall() {
            for (int y = 64; y <= 68; y++) {
                for (int z = -2; z <= 2; z++) blocks.put(new BlockPos(3, y, z), Blocks.STONE.defaultBlockState());
            }
        }
        @Override public BlockState getBlockState(BlockPos pos) {
            assertTrue(pos.getX() < failAtX, "Read unloaded block " + pos);
            return blocks.getOrDefault(pos, pos.getY() == 63 ? floor : Blocks.AIR.defaultBlockState());
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
    }
}
