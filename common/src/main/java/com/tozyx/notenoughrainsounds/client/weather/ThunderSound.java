package com.tozyx.notenoughrainsounds.client.weather;

import com.tozyx.notenoughrainsounds.NotEnoughRainSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Keeps native timing/position/range while making the mod gain independent of thunder's huge source volume. */
final class ThunderSound extends AbstractTickableSoundInstance {
    private final int nativeRange;
    private final float dryGain;
    private final OcclusionGain shelter;
    private boolean resolved;
    private WeighedSoundEvents event;
    private float resourceVolume = 1;
    private float resourcePitch = 1;

    ThunderSound(SoundInstance original, ResourceLocation event, double gain, boolean blocked) {
        super(SoundEvent.createVariableRangeEvent(event), SoundSource.WEATHER, RandomSource.create());
        nativeRange = range(original.getVolume(), original.getSound().getAttenuationDistance());
        dryGain = gain(original.getVolume(), gain);
        shelter = new OcclusionGain(.65F, blocked);
        volume = dryGain * shelter.current();
        pitch = original.getPitch();
        x = original.getX();
        y = original.getY();
        z = original.getZ();
        relative = original.isRelative();
        attenuation = original.getAttenuation();
    }

    void obstructed(boolean blocked) {
        shelter.target(blocked);
    }

    Vec3 tracePoint() { return new Vec3(x, y + .05, z); }

    @Override
    public void tick() {
        volume = dryGain * shelter.tick();
    }

    @Override public float getVolume() { return volume * resourceVolume; }
    @Override public float getPitch() { return pitch * resourcePitch; }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        if (resolved) return event;
        event = super.resolve(manager);
        resolved = true;
        if (event != null && sound != SoundManager.EMPTY_SOUND && sound != SoundManager.INTENTIONALLY_EMPTY_SOUND) {
            resourceVolume = sound.getVolume().sample(random);
            resourcePitch = sound.getPitch().sample(random);
            sound = new Sound(sound.getLocation(), sound.getVolume(), sound.getPitch(), sound.getWeight(),
                    sound.getType(), sound.shouldStream(), sound.shouldPreload(), nativeRange);
        }
        return event;
    }

    static ResourceLocation profile(double distanceSquared) {
        String band = distanceSquared < 64 * 64 ? "close" : distanceSquared < 160 * 160 ? "medium" : "far";
        return NotEnoughRainSounds.id("weather.thunder." + band);
    }

    static float gain(float nativeVolume, double multiplier) {
        if (!Float.isFinite(nativeVolume) || !Double.isFinite(multiplier)) return 0;
        return (float) (Math.clamp(nativeVolume, 0.0F, 1.0F) * Math.clamp(multiplier, 0.0, 1.0));
    }

    static int range(float nativeVolume, int attenuationDistance) {
        double range = Math.max(1.0, Float.isFinite(nativeVolume) ? nativeVolume : 1.0) * Math.max(1, attenuationDistance);
        return (int) Math.clamp(Math.ceil(range), 1, Integer.MAX_VALUE);
    }
}
