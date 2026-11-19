package com.tozyx.notenoughrainsounds.neoforge;

import com.tozyx.notenoughrainsounds.NotEnoughRainSounds;
import com.tozyx.notenoughrainsounds.client.NotEnoughRainSoundsClient;
import com.tozyx.notenoughrainsounds.client.gui.SoundConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

@Mod(value = NotEnoughRainSounds.MOD_ID, dist = Dist.CLIENT)
public final class NotEnoughRainSoundsNeoForge {
    public NotEnoughRainSoundsNeoForge(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::onClientSetup);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new SoundConfigScreen(parent));
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> NotEnoughRainSoundsClient.init(FMLPaths.CONFIGDIR.get()));
    }
}
