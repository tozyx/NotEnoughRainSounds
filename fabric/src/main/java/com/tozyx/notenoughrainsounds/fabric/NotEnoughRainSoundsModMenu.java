package com.tozyx.notenoughrainsounds.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.tozyx.notenoughrainsounds.client.gui.SoundConfigScreen;

/** Loaded by Mod Menu only when it is installed; no runtime menu dependency. */
public final class NotEnoughRainSoundsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SoundConfigScreen::new;
    }
}
