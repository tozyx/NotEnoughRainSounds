package com.tozyx.notenoughrainsounds.client;

import com.tozyx.notenoughrainsounds.NotEnoughRainSounds;
import com.tozyx.notenoughrainsounds.client.config.ConfigStore;
import com.tozyx.notenoughrainsounds.client.config.SoundConfig;
import java.io.IOException;
import com.tozyx.notenoughrainsounds.client.rain.RainPlayer;
import com.tozyx.notenoughrainsounds.client.rain.RainSampler;
import com.tozyx.notenoughrainsounds.client.weather.WeatherPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/** Shared client lifecycle; loader modules supply only their configuration directory. */
public final class NotEnoughRainSoundsClient {
    private static final RainSampler RAIN = new RainSampler();
    private static final RainPlayer PLAYER = new RainPlayer();
    private static final WeatherPlayer WEATHER = new WeatherPlayer();
    private static ConfigStore config;
    private NotEnoughRainSoundsClient() {
    }

    public static void init(Path configDirectory) {
        config = new ConfigStore(configDirectory);
        reloadConfig();
        NotEnoughRainSounds.LOGGER.info("Not Enough Rain Sounds client initialized ({})", NotEnoughRainSounds.id("client"));
    }

    public static void reloadConfig() {
        if (config == null) return;
        PLAYER.clear(Minecraft.getInstance().getSoundManager());
        WEATHER.clear(Minecraft.getInstance().getSoundManager());
        // A broken first-launch file still leaves valid defaults available.
        if (config.reload()) NotEnoughRainSounds.LOGGER.info("Rain sound config loaded");
        RAIN.configure(config.current().rain);
    }

    public static SoundConfig configSnapshot() {
        return config == null ? new SoundConfig() : config.snapshot();
    }

    /** Write successfully before replacing live settings or clearing any active sounds. */
    public static void saveConfig(SoundConfig draft) throws IOException {
        if (config == null) throw new IOException("Client configuration is not initialized");
        config.save(draft);
        PLAYER.clear(Minecraft.getInstance().getSoundManager());
        WEATHER.clear(Minecraft.getInstance().getSoundManager());
        RAIN.configure(config.current().rain);
    }

    public static boolean replaceWeatherSound(SoundInstance sound) {
        return config != null && WEATHER.replace(Minecraft.getInstance(), config.current(), sound);
    }

    public static void tick(Minecraft minecraft) {
        if (config == null) return;
        WEATHER.tick(minecraft, config.current());
        if (!config.current().enabled || minecraft.level == null || minecraft.player == null) {
            RAIN.clear();
            PLAYER.clear(minecraft.getSoundManager());
            return;
        }
        if (minecraft.isPaused()) return;
        var listener = minecraft.gameRenderer.getMainCamera().getPosition();
        RAIN.tick(minecraft.level, listener);
        PLAYER.tick(minecraft, RAIN, config.current(), listener);
    }
}
