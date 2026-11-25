package com.tozyx.notenoughrainsounds.client.weather;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.world.phys.Vec3;

/** The native request still owns the recording lifetime; ticks update only its acoustic path. */
final class FilteredBackgroundRainSound extends BackgroundRainSound implements TickableSoundInstance, FilteredRainVoice {
    private final OcclusionGain shelter;
    private final Vec3 sourcePoint;
    private final boolean initiallyBlocked;
    private WeighedSoundEvents shelteredEvent;

    FilteredBackgroundRainSound(SoundInstance original, double multiplier, boolean blocked, int cutoffHz) {
        super(original, WeatherSoundPolicy.Event.RAIN.replacement(), multiplier);
        initiallyBlocked = blocked;
        shelter = new OcclusionGain(.5F, blocked);
        shelter.filterAt(cutoffHz);
        sourcePoint = new Vec3(x, y, z);
    }

    @Override public WeighedSoundEvents resolve(SoundManager manager) {
        WeighedSoundEvents outdoor = super.resolve(manager);
        if (!initiallyBlocked || outdoor == null || outdoor.getSubtitle() == null) return outdoor;
        if (shelteredEvent == null) {
            shelteredEvent = new WeighedSoundEvents(WeatherSoundPolicy.Event.RAIN_ABOVE.replacement(),
                    "subtitles.notenoughrainsounds.weather.rain.above");
            shelteredEvent.addSound(outdoor);
        }
        return shelteredEvent;
    }

    Vec3 sourcePoint() { return sourcePoint; }
    @Override public OcclusionGain rainOcclusion() { return shelter; }
    @Override public float getVolume() { return super.getVolume() * shelter.current(); }
    @Override public void tick() { shelter.tick(); }
    @Override public boolean isStopped() { return false; }
}
