package com.tozyx.notenoughrainsounds.client.weather;

import com.google.gson.JsonParser;
import com.tozyx.notenoughrainsounds.client.config.SoundConfig;
import com.tozyx.notenoughrainsounds.client.mixin.AbstractSoundInstanceAccessor;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRainReplacementTest {
    private static SimpleSoundInstance original(WeatherSoundPolicy.Event profile, float volume, float pitch,
            double x, double y, double z) {
        return new NativeRequest(profile, volume, pitch, false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, false);
    }

    // Unit tests do not launch Mixin. This fixture exposes the same raw request fields through
    // the accessor contract, and rejects resource-dependent getters before/after resolution.
    private static final class NativeRequest extends SimpleSoundInstance implements AbstractSoundInstanceAccessor {
        NativeRequest(WeatherSoundPolicy.Event profile, float volume, float pitch, boolean looping, int delay,
                SoundInstance.Attenuation attenuation, double x, double y, double z, boolean relative) {
            super(profile.original(), SoundSource.WEATHER, volume, pitch, RandomSource.create(2),
                    looping, delay, attenuation, x, y, z, relative);
        }
        @Override public float notenoughrainsounds$getBaseVolume() { return volume; }
        @Override public float notenoughrainsounds$getBasePitch() { return pitch; }
        @Override public float getVolume() { throw new AssertionError("Do not inherit old resource gain"); }
        @Override public float getPitch() { throw new AssertionError("Do not inherit old resource pitch"); }
    }

    @Test void outdoorReplacementKeepsNativePositionPitchAndBaseVolume() {
        var nativeRain = original(WeatherSoundPolicy.Event.RAIN, .2F, 1, 12.5, 65, -7.5);
        var replacement = new BackgroundRainSound(nativeRain, WeatherSoundPolicy.Event.RAIN.replacement(), .5);
        assertEquals(.1F, replacement.getVolume(), 1e-6);
        assertEquals(1, replacement.getPitch());
        assertEquals(nativeRain.getX(), replacement.getX());
        assertEquals(nativeRain.getY(), replacement.getY());
        assertEquals(nativeRain.getZ(), replacement.getZ());
        assertEquals(SoundSource.WEATHER, replacement.getSource());
        assertFalse(replacement.isRelative());
        assertEquals(SoundInstance.Attenuation.LINEAR, replacement.getAttenuation());
        assertFalse(replacement.isLooping());
        assertEquals(0, replacement.getDelay());
    }

    @Test void shelteredReplacementUsesNaturalSpeedAndNativeQuietGain() {
        var nativeRain = original(WeatherSoundPolicy.Event.RAIN_ABOVE, .1F, .5F, -2, 70, 8);
        var replacement = new BackgroundRainSound(nativeRain, WeatherSoundPolicy.Event.RAIN_ABOVE.replacement(), 1);
        assertEquals(.1F, replacement.getVolume(), 1e-6);
        assertEquals(1F, replacement.getPitch());
        assertEquals(nativeRain.getY(), replacement.getY());
    }

    @Test void relativeAttenuationAndRepeatPropertiesAreForwardedRatherThanInvented() {
        var nativeRain = new NativeRequest(WeatherSoundPolicy.Event.RAIN,
                .3F, .9F, true, 7, SoundInstance.Attenuation.NONE, 4, 5, 6, true);
        var replacement = new BackgroundRainSound(nativeRain, WeatherSoundPolicy.Event.RAIN.replacement(), .8);
        assertEquals(.24F, replacement.getVolume(), 1e-6);
        assertEquals(.9F, replacement.getPitch());
        assertTrue(replacement.isRelative());
        assertEquals(SoundInstance.Attenuation.NONE, replacement.getAttenuation());
        assertTrue(replacement.isLooping());
        assertEquals(7, replacement.getDelay());
    }

    @Test void separateNativeRequestsKeepSeparateWorldPositionsAndDoNotRewriteOldVoices() {
        var first = new BackgroundRainSound(original(WeatherSoundPolicy.Event.RAIN, .2F, 1, 1, 64, 2),
                WeatherSoundPolicy.Event.RAIN.replacement(), 1);
        var second = new BackgroundRainSound(original(WeatherSoundPolicy.Event.RAIN, .2F, 1, 7, 67, 9),
                WeatherSoundPolicy.Event.RAIN.replacement(), 1);
        assertEquals(1, first.getX());
        assertEquals(7, second.getX());
        assertEquals(.2F, first.getVolume(), 1e-6);
        assertEquals(.2F, second.getVolume(), 1e-6);
        assertFalse(TickableSoundInstance.class.isAssignableFrom(BackgroundRainSound.class),
                "Native audio lifetime must not use another Java tick/EOF envelope");
    }

    @Test void modGainIsAppliedOnceWithoutRainStrengthOrLayerCompensation() {
        var nativeRain = original(WeatherSoundPolicy.Event.RAIN, .2F, 1, 0, 64, 0);
        var config = new SoundConfig();
        config.volume = .8;
        config.weather.rain.volume = .5;
        var decision = WeatherSoundPolicy.select(nativeRain.getLocation(), config, false, id -> true);
        var replacement = new BackgroundRainSound(nativeRain, decision.event(), decision.volumeMultiplier());
        assertEquals(.08F, replacement.getVolume(), 1e-6);
    }

    @Test void replacementCannotEnterTheNativeWeatherHookAgain() {
        var replacement = new BackgroundRainSound(original(WeatherSoundPolicy.Event.RAIN, .2F, 1, 0, 64, 0),
                WeatherSoundPolicy.Event.RAIN.replacement(), 1);
        assertEquals(WeatherSoundPolicy.Action.KEEP_ORIGINAL,
                WeatherSoundPolicy.select(replacement.getLocation(), new SoundConfig(), false, id -> {
                    fail("Replacement must not be intercepted recursively");
                    return false;
                }).action());
    }

    @Test void sharedRainPreservesNativeShelteredLevelAndOpensUpSmoothly() {
        var request = original(WeatherSoundPolicy.Event.RAIN_ABOVE, .1F, .5F, 1, 64, 0);
        var sound = new FilteredBackgroundRainSound(request, 1, true, 2600);
        assertEquals(WeatherSoundPolicy.Event.RAIN.replacement(), sound.getLocation());
        assertEquals(.1F, sound.getVolume(), 1e-6);
        assertEquals(1F, sound.getPitch());
        float filtered = sound.rainOcclusion().highGain();
        assertTrue(filtered < OcclusionGain.referenceGain(4000));
        sound.rainOcclusion().target(false);
        sound.tick();
        assertTrue(sound.rainOcclusion().highGain() > filtered && sound.rainOcclusion().highGain() < 1);
        for (int i = 0; i < 3; i++) sound.tick();
        assertEquals(1F, sound.rainOcclusion().highGain());
        assertEquals(.2F, sound.getVolume(), 1e-6);
    }

    @Test void modResourcesLeaveSoundRegistrationToExternalPacks() {
        assertNull(getClass().getResource("/assets/notenoughrainsounds/sounds.json"),
                "Custom sound events must come from an enabled resource pack");
    }

    @Test void wallObstructedNormalRainUsesShelteredGainWithoutSlowingOrMoving() {
        var request = original(WeatherSoundPolicy.Event.RAIN, .2F, 1, 7.5, 63.5, .5);
        var sound = new BackgroundRainSound(request, WeatherSoundPolicy.Event.RAIN_ABOVE.replacement(), .8);
        assertEquals(.08F, sound.getVolume(), 1e-6);
        assertEquals(1, sound.getPitch());
        assertEquals(request.getX(), sound.getX());
        assertEquals(request.getY(), sound.getY());
        assertEquals(request.getZ(), sound.getZ());
        assertFalse(sound.isLooping());
    }

    @Test void nativePropertyAccessorIsRegisteredInSharedClientMixins() throws Exception {
        try (var input = getClass().getResourceAsStream("/notenoughrainsounds.client.mixins.json")) {
            assertNotNull(input);
            var mixins = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean found = false;
            for (var value : mixins.getAsJsonArray("client")) {
                if (value.getAsString().equals("AbstractSoundInstanceAccessor")) found = true;
            }
            assertTrue(found);
        }
    }
}
