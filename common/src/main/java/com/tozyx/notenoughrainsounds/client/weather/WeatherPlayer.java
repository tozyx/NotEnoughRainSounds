package com.tozyx.notenoughrainsounds.client.weather;

import com.tozyx.notenoughrainsounds.NotEnoughRainSounds;
import com.tozyx.notenoughrainsounds.client.config.SoundConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Owns only replacement weather sounds. The sound-engine hook executes after native delay scheduling. */
public final class WeatherPlayer {
    private final List<Rain> rain = new ArrayList<>();
    private final List<Thunder> thunder = new ArrayList<>();
    private ClientLevel level;
    private Vec3 previousListener;
    private long ticks;
    private long nextRainLog;

    public void clear(SoundManager sounds) {
        clearRain(sounds);
        for (Thunder playing : thunder) sounds.stop(playing.sound());
        thunder.clear();
        level = null;
        previousListener = null;
        ticks = 0;
        nextRainLog = 0;
    }

    private void clearRain(SoundManager sounds) {
        for (Rain playing : rain) sounds.stop(playing.sound());
        rain.clear();
    }

    private void updateWorld(Minecraft minecraft) {
        Vec3 listener = minecraft.gameRenderer.getMainCamera().getPosition();
        if (level != minecraft.level || previousListener != null && previousListener.distanceToSqr(listener) > 64 * 64) {
            clear(minecraft.getSoundManager());
            level = minecraft.level;
        }
        previousListener = listener;
    }

    public void tick(Minecraft minecraft, SoundConfig config) {
        SoundManager sounds = minecraft.getSoundManager();
        if (!config.enabled || minecraft.level == null || minecraft.player == null) {
            clear(sounds);
            return;
        }
        updateWorld(minecraft);
        if (minecraft.isPaused()) return;
        if (minecraft.options.getSoundSourceVolume(SoundSource.MASTER) <= 0
                || minecraft.options.getSoundSourceVolume(SoundSource.WEATHER) <= 0) {
            clear(sounds);
            return;
        }
        long tick = ticks++;
        thunder.removeIf(playing -> tick - playing.started() > 20 && !sounds.isActive(playing.sound()));
        for (Thunder playing : thunder) {
            ThunderSound sound = playing.sound();
            if ((tick - playing.started()) % OcclusionGain.CHECK_INTERVAL_TICKS == 0
                    && SoundOcclusion.spatial(sound)) {
                sound.obstructed(SoundOcclusion.blockedNearby(level, previousListener, sound.tracePoint()));
            }
        }
        if (!config.weather.rain.enabled || config.volume * config.weather.rain.volume <= 0) {
            clearRain(sounds);
            return;
        }
        rain.removeIf(playing -> tick - playing.started() > 20 && !sounds.isActive(playing.sound()));
        SoundOcclusion.RainShelter shelter = null;
        for (Rain playing : rain) {
            if (playing.sound() instanceof FilteredBackgroundRainSound sound && SoundOcclusion.spatial(sound)
                    && (tick - playing.started()) % OcclusionGain.CHECK_INTERVAL_TICKS == 0) {
                if (shelter == null) shelter = SoundOcclusion.rainShelter(level, previousListener);
                sound.rainOcclusion().target(SoundOcclusion.shelteredRain(level, shelter, previousListener,
                        sound.sourcePoint()));
            }
        }
        // Background starts only in replace(), from a genuine native weather.rain / rain.above request.
    }

    /** Return true only when this instance has been replaced or deliberately muted. */
    public boolean replace(Minecraft minecraft, SoundConfig config, SoundInstance original) {
        if (minecraft.level == null || minecraft.player == null || original.getSource() != SoundSource.WEATHER) return false;
        SoundManager sounds = minecraft.getSoundManager();
        ResourceLocation request = original.getLocation();
        boolean rainRequest = request.equals(WeatherSoundPolicy.Event.RAIN.original())
                || request.equals(WeatherSoundPolicy.Event.RAIN_ABOVE.original());
        Vec3 listener = minecraft.gameRenderer.getMainCamera().getPosition();
        boolean blockedRain = false;
        if (rainRequest && config.enabled && config.weather.rain.enabled && SoundOcclusion.spatial(original)) {
            var shelter = SoundOcclusion.rainShelter(minecraft.level, listener);
            blockedRain = SoundOcclusion.shelteredRain(minecraft.level, shelter, listener,
                    new Vec3(original.getX(), original.getY(), original.getZ()));
            if (config.rain.debugLogging && ticks >= nextRainLog) {
                NotEnoughRainSounds.LOGGER.info("Background rain: listener={}, shelter={}, blocked={}, lowPassEnabled={}",
                        listener, shelter, blockedRain, config.rainLowPass);
                nextRainLog = ticks + 20;
            }
        }
        var decision = WeatherSoundPolicy.select(request, config, blockedRain, id -> available(sounds, id));
        if (decision.action() == WeatherSoundPolicy.Action.KEEP_ORIGINAL) return false;
        if (decision.action() == WeatherSoundPolicy.Action.MUTE) return true;
        if (!original.canPlaySound()) return true;
        updateWorld(minecraft);
        if (rainRequest) {
            BackgroundRainSound sound;
            if (config.rainLowPass && decision.event().equals(WeatherSoundPolicy.Event.RAIN.replacement())) {
                sound = new FilteredBackgroundRainSound(original, decision.volumeMultiplier(), blockedRain,
                        config.backgroundCutoffHz);
            } else {
                sound = new BackgroundRainSound(original, decision.event(), decision.volumeMultiplier());
            }
            if (sound.resolve(sounds) == null || sound.getSound() == SoundManager.EMPTY_SOUND
                    || sound.getSound() == SoundManager.INTENTIONALLY_EMPTY_SOUND) return false;
            // Guard command/mod-driven floods without stopping older native-triggered recordings.
            if (rain.size() >= 64) return true;
            sounds.play(sound);
            rain.add(new Rain(sound, ticks));
            return true;
        }
        // Let long tails finish. A full budget consumes the request without starting a vanilla fallback.
        if (!config.weather.thunder.hasRoom(thunder.size())) return true;
        // Resolve native range and gain once, at the already-scheduled native playback time.
        if (original.resolve(sounds) == null || original.getSound() == SoundManager.EMPTY_SOUND
                || original.getSound() == SoundManager.INTENTIONALLY_EMPTY_SOUND) return false;
        Vec3 source = new Vec3(original.getX(), original.getY(), original.getZ());
        double distance = original.isRelative() ? 0 : previousListener.distanceToSqr(source);
        ResourceLocation profile = ThunderSound.profile(distance);
        ResourceLocation event = available(sounds, profile) ? profile : decision.event();
        boolean blocked = SoundOcclusion.spatial(original)
                && SoundOcclusion.blockedNearby(level, previousListener, source.add(0, .05, 0));
        ThunderSound sound = new ThunderSound(original, event, decision.volumeMultiplier(), blocked);
        // Reserve before play: a sound-engine callback can synchronously request another thunderclap.
        Thunder voice = new Thunder(sound, ticks);
        thunder.add(voice);
        try {
            sounds.play(sound);
        } catch (RuntimeException | Error failure) {
            thunder.remove(voice);
            throw failure;
        }
        return true;
    }

    public static boolean available(SoundManager sounds, ResourceLocation id) {
        var event = sounds.getSoundEvent(id);
        return event != null && event.getWeight() > 0;
    }

    private record Rain(BackgroundRainSound sound, long started) {}
    private record Thunder(ThunderSound sound, long started) {}
}
