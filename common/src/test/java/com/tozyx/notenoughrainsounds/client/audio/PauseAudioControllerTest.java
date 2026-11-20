package com.tozyx.notenoughrainsounds.client.audio;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PauseAudioControllerTest {
    private static final long MS = 1_000_000L;

    @Test void fadesBeforePausingAndHoldsTheSameVoice() {
        var scene = new Scene();
        var voice = scene.add("minecraft:music", .8F);
        scene.pause(0);
        scene.time(200);
        assertEquals(.4F, voice.output, 1e-6);
        assertEquals(0, voice.pauses);
        scene.time(400);
        assertEquals(0, voice.output);
        assertEquals(1, voice.pauses);
        scene.time(10_000);
        assertEquals(1, voice.pauses);
        assertTrue(scene.controller.active());
    }

    @Test void resumesAtSilenceThenFadesBackToTheCurrentNativeGain() {
        var scene = new Scene();
        var voice = scene.add("othermod:music", .6F);
        scene.pause(0);
        scene.time(400);
        voice.events.clear();
        scene.resume(500);
        assertEquals(List.of("volume", "resume"), voice.events);
        assertEquals(0, voice.output);
        assertEquals(1, voice.resumes);
        scene.time(625);
        assertEquals(.3F, voice.output, 1e-6);
        scene.time(750);
        assertEquals(.6F, voice.output, 1e-6);
        assertFalse(scene.controller.active());
    }

    @Test void quickUnpauseReversesWithoutJumpingOrPhysicallyPausing() {
        var scene = new Scene();
        var voice = scene.add("rain", 1);
        scene.pause(0);
        scene.time(100);
        float before = voice.output;
        scene.resume(100);
        assertEquals(before, voice.output);
        scene.time(200);
        assertEquals(1, voice.output);
        assertEquals(0, voice.pauses);
        assertEquals(0, voice.resumes);
    }

    @Test void quickRepauseReversesAnOngoingFadeIn() {
        var scene = new Scene();
        var voice = scene.add("rain", 1);
        scene.pause(0);
        scene.time(400);
        scene.resume(500);
        scene.time(625);
        assertEquals(.5F, voice.output, 1e-6);
        scene.pause(625);
        assertEquals(.5F, voice.output, 1e-6);
        scene.time(825);
        assertEquals(0, voice.output);
        assertEquals(2, voice.pauses);
    }

    @Test void repeatedPauseCallsDoNotRestartTheFade() {
        var scene = new Scene();
        var voice = scene.add("rain", 1);
        scene.pause(0);
        scene.pause(100);
        scene.time(400);
        assertEquals(0, voice.output);
        assertEquals(1, voice.pauses);
    }

    @Test void repeatedResumeCallsDoNotRestartTheFade() {
        var scene = new Scene();
        var voice = scene.add("rain", 1);
        scene.pause(0);
        scene.time(400);
        scene.resume(500);
        scene.resume(600);
        scene.time(750);
        assertEquals(1, voice.output);
        assertEquals(1, voice.resumes);
        assertFalse(scene.controller.active());
    }

    @Test void newMenuSoundsAreOutsideThePauseSnapshot() {
        var scene = new Scene();
        var music = scene.add("music", 1);
        scene.pause(0);
        var button = scene.add("menu_button", .8F);
        scene.time(400);
        assertEquals(0, music.output);
        assertEquals(.8F, button.output);
        assertEquals(1, scene.controller.multiplier(button.key));
        assertEquals(0, button.pauses);
        scene.resume(500);
        assertEquals(0, button.resumes);
    }

    @Test void sliderChangesDuringPauseKeepTheirNewValueOnResume() {
        var scene = new Scene();
        var music = scene.add("music", .8F);
        scene.pause(0);
        scene.time(400);
        music.nativeVolume(.3F);
        assertEquals(0, music.output);
        scene.resume(500);
        scene.time(750);
        assertEquals(.3F, music.output, 1e-6);
    }

    @Test void aStoppedOrNaturallyEndedVoiceIsNotResumed() {
        var scene = new Scene();
        var music = scene.add("music", 1);
        scene.pause(0);
        scene.time(400);
        music.alive = false;
        scene.resume(500);
        assertEquals(0, music.resumes);
    }

    @Test void stopAllResetDiscardsTheTransitionWithoutLaterResumingAnything() {
        var scene = new Scene();
        var voice = scene.add("music", 1);
        scene.pause(0);
        scene.time(400);
        scene.controller.reset();
        int actions = voice.events.size();
        scene.time(10_000);
        assertFalse(scene.controller.resume(10_000 * MS));
        assertEquals(actions, voice.events.size());
        assertEquals(1, scene.controller.multiplier(voice.key));
    }

    @Test void realElapsedTimeCompletesTheFadeWithoutAnyIntermediateGameTicks() {
        var scene = new Scene();
        var voice = scene.add("music", 1);
        scene.pause(1_000);
        scene.time(5_000);
        assertEquals(0, voice.output);
        assertEquals(1, voice.pauses);
    }

    @Test void coversVanillaMusicWeatherAndOtherModSourcesTogether() {
        var scene = new Scene();
        var music = scene.add("minecraft:music", 1);
        var rain = scene.add("minecraft:weather.rain", .2F);
        var custom = scene.add("anothermod:ambience", .5F);
        scene.pause(0);
        scene.time(400);
        for (var voice : List.of(music, rain, custom)) {
            assertEquals(0, voice.output);
            assertEquals(1, voice.pauses);
        }
    }

    @Test void anIdleEngineWithNoVoicesUsesNativePauseAndResume() {
        var controller = new PauseAudioController<Object>();
        assertFalse(controller.pause(Map.of(), 0));
        assertFalse(controller.resume(10_000 * MS));
    }

    @Test void disablingMidFadeOutPausesBeforeRestoringNativeGain() {
        var scene = new Scene();
        var voice = scene.add("music", .8F);
        scene.pause(0);
        scene.time(200);
        voice.events.clear();
        scene.controller.finishWithoutFade();
        assertEquals(List.of("volume", "pause", "volume"), voice.events);
        assertEquals(1, voice.pauses);
        assertEquals(.8F, voice.output, 1e-6);
        assertTrue(scene.controller.active(), "Retain paused handles for safe native resume");
        assertTrue(scene.controller.resumeWithoutFade());
        assertEquals(1, voice.resumes);
        assertEquals(.8F, voice.output, 1e-6);
        assertFalse(scene.controller.active());
    }

    @Test void disablingWhilePausedPreservesMenuSoundsAndDoesNotRepeatChannelUpdates() {
        var scene = new Scene();
        var voice = scene.add("music", .8F);
        scene.pause(0);
        scene.time(400);
        var menu = scene.add("menu", .5F);
        scene.controller.finishWithoutFade();
        int actions = voice.events.size();
        scene.controller.finishWithoutFade();
        assertEquals(actions, voice.events.size());
        assertEquals(.5F, menu.output);
        assertEquals(0, menu.pauses);
        scene.controller.resumeWithoutFade();
        assertEquals(1, voice.resumes);
        assertEquals(0, menu.resumes);
    }

    @Test void disablingMidFadeInImmediatelyRestoresTheCurrentNativeGain() {
        var scene = new Scene();
        var voice = scene.add("music", .8F);
        scene.pause(0);
        scene.time(400);
        scene.resume(500);
        scene.time(625);
        voice.nativeVolume(.6F);
        assertEquals(.3F, voice.output, 1e-6);
        scene.controller.finishWithoutFade();
        assertEquals(.6F, voice.output, 1e-6);
        assertFalse(scene.controller.active());
        assertEquals(1, voice.resumes);
        scene.time(10_000);
        assertEquals(.6F, voice.output, 1e-6);
    }

    @Test void resumingWithOptionOffCanCancelAnUnfinishedFadeOut() {
        var scene = new Scene();
        var voice = scene.add("music", .8F);
        scene.pause(0);
        scene.time(100);
        assertTrue(scene.controller.resumeWithoutFade());
        assertEquals(.8F, voice.output, 1e-6);
        assertEquals(0, voice.pauses);
        assertEquals(0, voice.resumes);
        assertFalse(scene.controller.active());
    }

    @Test void reEnablingBeforeResumeRestartsFromSilenceWithoutAFullVolumeBurst() {
        var scene = new Scene();
        var voice = scene.add("music", .8F);
        scene.pause(0);
        scene.time(400);
        scene.controller.finishWithoutFade();
        assertEquals(.8F, voice.output, 1e-6);
        scene.resume(500);
        assertEquals(0, voice.output);
        scene.time(625);
        assertEquals(.4F, voice.output, 1e-6);
        scene.time(750);
        assertEquals(.8F, voice.output, 1e-6);
    }

    @Test void disablingAndNativeResumeNeverResurrectAStoppedVoice() {
        var scene = new Scene();
        var voice = scene.add("music", .8F);
        scene.pause(0);
        scene.time(400);
        voice.alive = false;
        scene.controller.finishWithoutFade();
        scene.controller.resumeWithoutFade();
        assertEquals(0, voice.resumes);
    }

    @Test void disabledIdleHandoffDoesNotTakeOverVanillaResume() {
        var controller = new PauseAudioController<Object>();
        controller.finishWithoutFade();
        assertFalse(controller.resumeWithoutFade());
        assertFalse(controller.active());
    }

    private static final class Scene {
        final PauseAudioController<Object> controller = new PauseAudioController<>();
        final Map<Object, PauseAudioController.Voice> current = new IdentityHashMap<>();
        Voice add(String name, float volume) {
            var voice = new Voice(this, new Object(), volume);
            current.put(voice.key, voice);
            return voice;
        }
        void pause(long millis) { assertTrue(controller.pause(current, millis * MS)); }
        void resume(long millis) { assertTrue(controller.resume(millis * MS)); }
        void time(long millis) { controller.update(millis * MS); }
    }

    private static final class Voice implements PauseAudioController.Voice {
        final Scene scene;
        final Object key;
        final PauseChannelState channel = new PauseChannelState();
        final List<String> events = new ArrayList<>();
        boolean alive = true;
        float output;
        int pauses;
        int resumes;
        Voice(Scene scene, Object key, float nativeGain) {
            this.scene = scene;
            this.key = key;
            nativeVolume(nativeGain);
        }
        void nativeVolume(float volume) { output = channel.volume(volume); }
        @Override public boolean alive() { return alive; }
        @Override public void updateVolume() {
            output = channel.volume(channel.gain(scene.controller.multiplier(key)));
            events.add("volume");
        }
        @Override public void pause() { channel.pause(); pauses++; events.add("pause"); }
        @Override public void resume() { channel.resume(); resumes++; events.add("resume"); }
    }
}
