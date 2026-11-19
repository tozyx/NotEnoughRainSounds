package com.tozyx.notenoughrainsounds;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NotEnoughRainSounds {
    public static final String MOD_ID = "notenoughrainsounds";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private NotEnoughRainSounds() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
