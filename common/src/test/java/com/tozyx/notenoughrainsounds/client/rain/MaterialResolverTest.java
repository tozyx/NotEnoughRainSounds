package com.tozyx.notenoughrainsounds.client.rain;

import com.tozyx.notenoughrainsounds.client.config.SoundConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialResolverTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void recognizesMetalAndDistinctPlantAndWaterSurfaces() {
        MaterialResolver resolver = new MaterialResolver(new SoundConfig().rain);
        assertEquals(RainMaterial.METAL, resolver.resolve(Blocks.COPPER_BLOCK.defaultBlockState(), false).material());
        for (Block block : List.of(Blocks.BIG_DRIPLEAF, Blocks.SMALL_DRIPLEAF)) {
            var state = block.defaultBlockState();
            var plant = resolver.resolve(state, false);
            assertEquals(RainSound.AQUATIC_PLANT, RainSound.forMaterial(plant.material()));
            assertTrue(plant.plantDrips());
            var waterlogged = state.setValue(BlockStateProperties.WATERLOGGED, true);
            assertEquals(RainMaterial.AQUATIC_PLANT, resolver.resolve(waterlogged, true).material());
        }
        assertEquals(RainMaterial.AQUATIC_PLANT, resolver.resolve(Blocks.LILY_PAD.defaultBlockState(), false).material());
        assertEquals(RainMaterial.AQUATIC_PLANT, resolver.resolve(Blocks.SEAGRASS.defaultBlockState(), true).material());
        var water = resolver.resolve(Blocks.WATER.defaultBlockState(), true);
        assertEquals(RainMaterial.WATER, water.material());
        assertFalse(water.plantDrips());
    }

    @Test void bambooSurfacesAndRaftsHaveTheirOwnMaterial() {
        var config = new SoundConfig();
        config.rain.materials.get("wood").enabled = false;
        var resolver = new MaterialResolver(config.rain);
        for (Block block : List.of(Blocks.BAMBOO, Blocks.BAMBOO_SAPLING, Blocks.BAMBOO_BLOCK,
                Blocks.STRIPPED_BAMBOO_BLOCK, Blocks.BAMBOO_PLANKS, Blocks.BAMBOO_MOSAIC_STAIRS,
                Blocks.BAMBOO_SIGN, Blocks.BAMBOO_HANGING_SIGN, Blocks.BAMBOO_TRAPDOOR)) {
            var match = resolver.resolve(block.defaultBlockState(), false);
            assertEquals(RainSound.BAMBOO, RainSound.forMaterial(match.material()), block.toString());
            assertTrue(match.enhanced());
        }
        for (var type : net.minecraft.world.entity.vehicle.Boat.Type.values()) {
            var expected = type == net.minecraft.world.entity.vehicle.Boat.Type.BAMBOO ? RainMaterial.BAMBOO : RainMaterial.WOOD;
            assertEquals(expected, BoatRainSampler.hullMaterial(type).material());
        }
    }

    @Test void soilAndGravelSharingVanillaSoundTypeRemainDistinct() {
        MaterialResolver resolver = new MaterialResolver(new SoundConfig().rain);
        assertSame(Blocks.DIRT.defaultBlockState().getSoundType(), Blocks.GRAVEL.defaultBlockState().getSoundType());
        assertEquals(RainMaterial.GROUND, resolver.resolve(Blocks.DIRT.defaultBlockState(), false).material());
        assertEquals(RainMaterial.GRANULAR, resolver.resolve(Blocks.GRAVEL.defaultBlockState(), false).material());
        assertEquals(RainMaterial.CERAMIC,
                resolver.resolve(Blocks.WHITE_GLAZED_TERRACOTTA.defaultBlockState(), false).material());
    }

    @Test void exclusionAndDisabledCategoryDoNotReclassify() {
        SoundConfig config = new SoundConfig();
        config.rain.materials.get("metal").exclude.add("minecraft:copper_block");
        MaterialResolver resolver = new MaterialResolver(config.rain);
        var match = resolver.resolve(Blocks.COPPER_BLOCK.defaultBlockState(), false);
        assertEquals(RainMaterial.METAL, match.material());
        assertFalse(match.enhanced());
        config.rain.materials.get("wood").enabled = false;
        var wood = resolver.resolve(Blocks.OAK_PLANKS.defaultBlockState(), false);
        assertEquals(RainMaterial.WOOD, wood.material());
        assertFalse(wood.enhanced());
    }

    @Test void explicitOverrideWinsAndHotSurfacesFallBack() {
        SoundConfig config = new SoundConfig();
        SoundConfig.Override override = new SoundConfig.Override();
        override.match = "minecraft:copper_block";
        override.material = "glass";
        config.rain.overrides.add(override);
        MaterialResolver resolver = new MaterialResolver(config.rain);
        assertEquals(RainMaterial.GLASS, resolver.resolve(Blocks.COPPER_BLOCK.defaultBlockState(), false).material());
        var magma = resolver.resolve(Blocks.MAGMA_BLOCK.defaultBlockState(), false);
        assertEquals(RainMaterial.VANILLA, magma.material());
        assertFalse(magma.enhanced());
    }

    @Test void syncedTagSelectorsWorkAndExactOverridePrecedesTag() {
        TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("test", "roofs"));
        BuiltInRegistries.BLOCK.bindTags(Map.of(tag, List.<Holder<Block>>of(Blocks.COPPER_BLOCK.builtInRegistryHolder())));
        try {
            SoundConfig config = new SoundConfig();
            config.rain.materials.get("metal").exclude.add("#test:roofs");
            assertFalse(new MaterialResolver(config.rain).resolve(Blocks.COPPER_BLOCK.defaultBlockState(), false).enhanced());
            SoundConfig.Override tagRule = new SoundConfig.Override();
            tagRule.match = "#test:roofs";
            tagRule.material = "wood";
            config.rain.overrides.add(tagRule);
            SoundConfig.Override exactRule = new SoundConfig.Override();
            exactRule.match = "minecraft:copper_block";
            exactRule.material = "glass";
            config.rain.overrides.add(exactRule);
            assertEquals(RainMaterial.GLASS,
                    new MaterialResolver(config.rain).resolve(Blocks.COPPER_BLOCK.defaultBlockState(), false).material());
        } finally {
            BuiltInRegistries.BLOCK.bindTags(Map.of());
        }
    }
}
