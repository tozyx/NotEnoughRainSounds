package com.tozyx.notenoughrainsounds.client.rain;

import com.tozyx.notenoughrainsounds.client.config.SoundConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Locates the exposed block or fluid surface where rain hits a sampled column. */
final class BlockRainSampler {
    private static final int BAMBOO_SOURCE_MAX_DROP = 2;
    private static final Vec3 COLUMN_CENTER = new Vec3(.5, 0, .5);
    private final SoundConfig.Rain settings;
    private final MaterialResolver resolver;

    BlockRainSampler(SoundConfig.Rain settings) {
        this.settings = settings;
        this.resolver = new MaterialResolver(settings);
    }

    boolean inside(RainSurface surface, Vec3 listener) {
        double dx = surface.position().x - listener.x;
        double dz = surface.position().z - listener.z;
        return dx * dx + dz * dz <= settings.radius * settings.radius
                && Math.abs(surface.position().y - listener.y) <= settings.verticalRange;
    }

    RainSurface inspect(ClientLevel world, Vec3 listener, int x, int z, long tick) {
        if (!world.hasChunk(x >> 4, z >> 4)) return null;
        int blocking = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int top = Math.max(blocking, world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)) - 1;
        if (top < world.getMinBuildHeight() || top - listener.y > settings.verticalRange + 1 + BAMBOO_SOURCE_MAX_DROP) return null;
        // Outline shapes supplement the heightmap for thin, non-colliding drip plants.
        int bottom = Math.max(world.getMinBuildHeight(), Math.max(blocking - 1, top - 4));
        for (int y = top; y >= bottom; y--) {
            BlockPos position = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(position);
            if (state.isAir()) continue;
            boolean plant = MaterialResolver.supportsPlantDrips(state) || state.is(Blocks.LILY_PAD);
            boolean stalk = state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING);
            VoxelShape shape = plant || stalk ? state.getShape(world, position) : state.getCollisionShape(world, position);
            // Bamboo outlines include a position-dependent offset. Sample the actual stalk tip.
            Vec3 center = stalk && !shape.isEmpty() ? shape.bounds().getCenter() : COLUMN_CENTER;
            double solidHeight = shape.isEmpty() ? Double.NEGATIVE_INFINITY : shape.max(Direction.Axis.Y, center.x, center.z);
            FluidState fluid = world.getFluidState(position);
            double fluidHeight = fluid.isEmpty() ? Double.NEGATIVE_INFINITY : fluid.getHeight(world, position);
            if (!Double.isFinite(solidHeight) && !Double.isFinite(fluidHeight)) continue;
            double height = Math.max(solidHeight, fluidHeight);
            Vec3 impact = new Vec3(x + center.x, y + height, z + center.z);
            BlockPos above = BlockPos.containing(impact.x, impact.y + 0.01, impact.z);
            if (world.getBiome(above).value().getPrecipitationAt(above) != Biome.Precipitation.RAIN) return null;
            boolean water = fluid.is(FluidTags.WATER) && fluidHeight > solidHeight;
            var match = resolver.resolve(state, water);
            // The exposed tip qualifies the stalk for rain. Playback and range use a fixed point on the upper stalk.
            RainSurface surface = state.is(Blocks.BAMBOO) && match.material() == RainMaterial.BAMBOO && match.enhanced()
                    ? bambooSurface(world, position, match, tick)
                    : new RainSurface(position, impact, match, tick);
            return inside(surface, listener) ? surface : null;
        }
        return null;
    }

    static RainSurface bambooSurface(BlockGetter world, BlockPos tip, MaterialResolver.Match match, long tick) {
        int segmentsBelow = 0;
        while (segmentsBelow < BAMBOO_SOURCE_MAX_DROP * 2
                && world.getBlockState(tip.below(segmentsBelow + 1)).is(Blocks.BAMBOO)) {
            segmentsBelow++;
        }
        BlockPos anchor = tip.below(segmentsBelow / 2);
        VoxelShape shape = world.getBlockState(anchor).getShape(world, anchor);
        Vec3 center = shape.isEmpty() ? Vec3.atCenterOf(anchor)
                : Vec3.atLowerCornerOf(anchor).add(shape.bounds().getCenter());
        return new RainSurface(anchor, center, match, tick);
    }
}
