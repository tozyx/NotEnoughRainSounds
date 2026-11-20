package com.tozyx.notenoughrainsounds.client.weather;

import net.minecraft.SharedConstants;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeatherPlaybackTest {
    @Test void nativeThunderMagnitudeCannotSaturateModVolumeSlider() {
        assertEquals(0.25F, ThunderSound.gain(10000, 0.25));
        assertEquals(0.125F, ThunderSound.gain(0.5F, 0.25));
        assertEquals(0, ThunderSound.gain(10000, 0));
        assertEquals(1, ThunderSound.gain(10000, 20));
        assertEquals(0, ThunderSound.gain(Float.NaN, 1));
        assertEquals(0, ThunderSound.gain(1, Double.POSITIVE_INFINITY));
    }

    @Test void thunderRangeIsNativeAndIndependentOfUserGain() {
        assertEquals(160000, ThunderSound.range(10000, 16));
        assertEquals(16, ThunderSound.range(0.1F, 16));
        assertEquals(1600, ThunderSound.range(100, 16));
        assertEquals(Integer.MAX_VALUE, ThunderSound.range(Float.MAX_VALUE, 256));
        assertEquals(1, ThunderSound.range(-1, 0));
        assertEquals(16, ThunderSound.range(Float.NaN, 16));
    }

    @Test void coveredThunderEasesBackToDryVolumeWithoutRestarting() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var thunder = WeatherSoundPolicy.Event.THUNDER;
        var original = new SimpleSoundInstance(thunder.original(), SoundSource.WEATHER, 10000, 1,
                RandomSource.create(1), false, 0, SoundInstance.Attenuation.LINEAR, 20, 65, 0, false) {
            @Override public float getVolume() { return 10000; }
            @Override public float getPitch() { return 1; }
            @Override public net.minecraft.client.resources.sounds.Sound getSound() { return SoundManager.EMPTY_SOUND; }
        };
        var sound = new ThunderSound(original, thunder.replacement(), .8, true);
        assertEquals(.52F, sound.getVolume(), 1e-6);
        sound.obstructed(false);
        sound.tick();
        assertTrue(sound.getVolume() > .52F && sound.getVolume() < .8F);
        for (int i = 0; i < 3; i++) sound.tick();
        assertEquals(.8F, sound.getVolume(), 1e-6);
        assertFalse(sound.isLooping());
        assertFalse(sound.isStopped());
    }

    @Test void thunderProfilesFollowDistanceBoundaries() {
        assertEquals("notenoughrainsounds:weather.thunder.close", ThunderSound.profile(0).toString());
        assertEquals("notenoughrainsounds:weather.thunder.close", ThunderSound.profile(4095).toString());
        assertEquals("notenoughrainsounds:weather.thunder.medium", ThunderSound.profile(64 * 64).toString());
        assertEquals("notenoughrainsounds:weather.thunder.medium", ThunderSound.profile(160 * 160 - 1).toString());
        assertEquals("notenoughrainsounds:weather.thunder.far", ThunderSound.profile(160 * 160).toString());
    }
}
