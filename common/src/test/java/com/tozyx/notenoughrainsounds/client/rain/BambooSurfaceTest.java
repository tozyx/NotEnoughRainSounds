package com.tozyx.notenoughrainsounds.client.rain;

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
import static org.junit.jupiter.api.Assertions.assertEquals;

class BambooSurfaceTest {
    private static final BlockPos TIP = new BlockPos(7, 70, -3);
    private static final MaterialResolver.Match MATCH = new MaterialResolver.Match(RainMaterial.BAMBOO, true, false);

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void tallStalkUsesTheShapeOfTheSegmentTwoBlocksDown() {
        var world = new Stalk(8);
        BlockPos anchor = TIP.below(2);
        Vec3 center = world.getBlockState(anchor).getShape(world, anchor).bounds().getCenter();
        var surface = RainSampler.bambooSurface(world, TIP, MATCH, 10);
        assertEquals(anchor, surface.block());
        assertEquals(Vec3.atLowerCornerOf(anchor).add(center), surface.position());
    }

    @Test void shortStalkStaysInItsUpperHalfAndNeverScansPastAGap() {
        var world = new Stalk(6);
        world.blocks.remove(TIP.below(3));
        assertEquals(TIP.below(), RainSampler.bambooSurface(world, TIP, MATCH, 0).block());
        world.blocks.put(TIP.below(), Blocks.BAMBOO_BLOCK.defaultBlockState());
        assertEquals(TIP, RainSampler.bambooSurface(world, TIP, MATCH, 0).block());
    }

    private static final class Stalk implements BlockGetter {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        Stalk(int height) {
            for (int i = 0; i < height; i++) blocks.put(TIP.below(i), Blocks.BAMBOO.defaultBlockState());
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
    }
}
