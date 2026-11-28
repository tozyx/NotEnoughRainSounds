package com.tozyx.notenoughrainsounds.client.weather;

import com.tozyx.notenoughrainsounds.client.rain.RainSound;
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

    @Test void villageRoofHeightIsAvailableInClientChunkPackets() {
        assertTrue(SoundOcclusion.ROOF_HEIGHTMAP.sendToClient());
        var scene = new Scene();
        Vec3 eye = new Vec3(.09, 71.62, .53);
        BlockPos ceiling = new BlockPos(0, 75, 0);
        scene.blocks.put(ceiling, Blocks.OAK_PLANKS.defaultBlockState());
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            scene.blocks.put(ceiling.relative(direction), Blocks.OAK_PLANKS.defaultBlockState());
            scene.blocks.put(BlockPos.containing(eye).relative(direction, 2), Blocks.GLASS_PANE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.IronBarsBlock.NORTH, true)
                    .setValue(net.minecraft.world.level.block.IronBarsBlock.SOUTH, true)
                    .setValue(net.minecraft.world.level.block.IronBarsBlock.EAST, true)
                    .setValue(net.minecraft.world.level.block.IronBarsBlock.WEST, true));
        }
        assertEquals(SoundOcclusion.RainShelter.ENCLOSED,
                SoundOcclusion.rainShelter(scene, eye, pos -> 76, pos -> true));
    }

    @Test void villageRoomWithAnOpenDoorKeepsBackgroundRainSheltered() {
        var scene = new Scene();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                scene.blocks.put(new BlockPos(x, 68, z), Blocks.OAK_STAIRS.defaultBlockState());
                if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    if (x == 2 && z == 0) continue;
                    for (int y = 64; y < 68; y++) {
                        scene.blocks.put(new BlockPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState());
                    }
                }
            }
        }
        assertFalse(rainBlocked(scene, LISTENER, RAIN));
        var shelter = SoundOcclusion.rainShelter(scene, LISTENER, pos -> 69, pos -> true);
        assertEquals(SoundOcclusion.RainShelter.ENCLOSED, shelter);
        assertTrue(SoundOcclusion.shelteredRain(scene, shelter, LISTENER, RAIN, pos -> true));
        assertTrue(SoundOcclusion.shelteredRain(scene, shelter, LISTENER, new Vec3(-7, 64, .5), pos -> true));

        scene.blocks.entrySet().removeIf(e -> e.getKey().getY() < 68);
        var porch = SoundOcclusion.rainShelter(scene, LISTENER, pos -> 69, pos -> true);
        assertEquals(SoundOcclusion.RainShelter.ROOF_ONLY, porch);
        assertFalse(SoundOcclusion.shelteredRain(scene, porch, LISTENER, RAIN, pos -> true));
        assertTrue(SoundOcclusion.shelteredRain(scene, porch, LISTENER, new Vec3(.5, 68.5, .5), pos -> true));
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

    @Test void canopyRainPassesThroughLayersOfLeavesButStillMeetsASolidRoof() {
        var scene = new Scene();
        scene.blocks.put(new BlockPos(0, 67, 0), Blocks.BIRCH_LEAVES.defaultBlockState());
        scene.blocks.put(new BlockPos(0, 68, 0), Blocks.OAK_LEAVES.defaultBlockState());
        var impact = new Vec3(.5, 69.05, .5);
        assertFalse(SoundOcclusion.blockedNearby(scene, LISTENER, impact, pos -> true, true));
        assertTrue(SoundOcclusion.blockedNearby(scene, LISTENER, impact, pos -> true));
        scene.blocks.put(new BlockPos(0, 66, 0), Blocks.GLASS.defaultBlockState());
        assertTrue(SoundOcclusion.blockedNearby(scene, LISTENER, impact, pos -> true, true));
    }

    @Test void bambooEmitterDoesNotMuffleItselfButStillMeetsAWall() {
        var scene = new Scene();
        BlockPos anchor = new BlockPos(7, 65, 0);
        var bamboo = Blocks.BAMBOO.defaultBlockState();
        scene.blocks.put(anchor, bamboo);
        Vec3 point = Vec3.atLowerCornerOf(anchor).add(bamboo.getShape(scene, anchor).bounds().getCenter());
        assertFalse(SoundOcclusion.materialBlocked(scene, LISTENER, point, pos -> true, RainSound.BAMBOO));
        scene.wall();
        assertTrue(SoundOcclusion.materialBlocked(scene, LISTENER, point, pos -> true, RainSound.BAMBOO));
    }

    @Test void bambooRainPassesThroughOtherStalksButNotThroughBambooPlanks() {
        var scene = new Scene();
        BlockPos stem = new BlockPos(3, 65, 0);
        var bamboo = Blocks.BAMBOO.defaultBlockState();
        scene.blocks.put(stem, bamboo);
        Vec3 center = Vec3.atLowerCornerOf(stem).add(bamboo.getCollisionShape(scene, stem).bounds().getCenter());
        Vec3 listener = center.add(-3, 0, 0);
        Vec3 source = center.add(4, 0, 0);
        assertTrue(SoundOcclusion.materialBlocked(scene, listener, source, pos -> true, RainSound.WOOD));
        assertFalse(SoundOcclusion.materialBlocked(scene, listener, source, pos -> true, RainSound.BAMBOO));

        scene.blocks.put(stem, Blocks.BAMBOO_PLANKS.defaultBlockState());
        assertTrue(SoundOcclusion.materialBlocked(scene, listener, source, pos -> true, RainSound.BAMBOO));
    }

    @Test void nearbySlopeAndVegetationDoNotCountAsARoof() {
        var scene = new Scene();
        scene.wall();
        BlockPos overhead = new BlockPos(0, 67, 0);
        for (BlockPos sample : new BlockPos[]{overhead, overhead.east(), overhead.north()}) {
            scene.blocks.put(sample, Blocks.BAMBOO.defaultBlockState());
            scene.blocks.put(sample.above(), Blocks.OAK_LEAVES.defaultBlockState());
        }
        assertTrue(rainBlocked(scene, LISTENER, RAIN));
        assertFalse(SoundOcclusion.hasRoof(scene, LISTENER, pos -> 70, pos -> true));

        for (BlockPos sample : new BlockPos[]{overhead, overhead.east(), overhead.north()}) {
            scene.blocks.put(sample.above(2), Blocks.GLASS.defaultBlockState());
        }
        assertTrue(SoundOcclusion.hasRoof(scene, LISTENER, pos -> 70, pos -> true));
    }

    @Test void isolatedAndOneBlockWideCoverDoNotEnableRainOcclusion() {
        var scene = new Scene();
        BlockPos overhead = new BlockPos(0, 67, 0);
        var roof = Blocks.STONE.defaultBlockState();
        scene.blocks.put(overhead, roof);
        assertFalse(SoundOcclusion.hasRoof(scene, LISTENER, pos -> 68, pos -> true));

        scene.blocks.put(overhead.east(), roof);
        scene.blocks.put(overhead.west(), roof);
        assertFalse(SoundOcclusion.hasRoof(scene, LISTENER, pos -> 68, pos -> true));

        scene.blocks.put(overhead.north(), roof);
        assertTrue(SoundOcclusion.hasRoof(scene, LISTENER, pos -> 68, pos -> true));
        assertFalse(SoundOcclusion.hasRoof(scene, LISTENER, pos -> {
            assertTrue(pos.getZ() >= 0, "Must not read roof height for an unloaded column");
            return 68;
        }, pos -> pos.getZ() >= 0));
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
