package com.tozyx.notenoughrainsounds.fabric;

import com.tozyx.notenoughrainsounds.client.NotEnoughRainSoundsClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class NotEnoughRainSoundsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NotEnoughRainSoundsClient.init(FabricLoader.getInstance().getConfigDir());
    }
}
