package com.tozyx.notenoughrainsounds.client.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PauseChannelStateTest {
    @Test void outputGainDoesNotChangeTheStoredNativeGainOrCompoundEachFrame() {
        var state = new PauseChannelState();
        assertEquals(.8F, state.volume(.8F));
        for (int i = 0; i < 100; i++) assertEquals(.4F, state.volume(state.gain(.5F)), 1e-6);
        assertEquals(.8F, state.volume(state.gain(1)), 1e-6);
    }

    @Test void nativeVolumeUpdatesStillRespectFadeWithoutOverwritingUserValues() {
        var state = new PauseChannelState();
        state.gain(.5F);
        assertEquals(.3F, state.volume(.6F), 1e-6);
        assertEquals(.6F, state.volume(state.gain(1)), 1e-6);
    }

    @Test void temporaryFadeZeroIsSeparateFromTheNativeVolumeAndMuteStaysMuted() {
        var state = new PauseChannelState();
        state.volume(.8F);
        assertEquals(.8F, state.gain(0));
        assertEquals(0, state.volume(.8F));
        assertEquals(.8F, state.volume(state.gain(1)));
        assertEquals(0, state.volume(0));
        assertEquals(0, state.volume(state.gain(1)));
    }

    @Test void lateDecodedBuffersWaitUntilResumeAndStartOnlyOnce() {
        var state = new PauseChannelState();
        state.pause();
        assertTrue(state.deferPlay());
        assertTrue(state.resume());
        assertFalse(state.resume());
        assertFalse(state.deferPlay());
    }

    @Test void aBufferCompletingAfterResumeCanStartNormally() {
        var state = new PauseChannelState();
        state.pause();
        assertFalse(state.resume());
        assertFalse(state.deferPlay());
    }

    @Test void stoppedPendingAudioCannotBeResurrectedEvenIfDecodeFinishesLate() {
        var state = new PauseChannelState();
        state.pause();
        assertTrue(state.deferPlay());
        state.stopped();
        assertTrue(state.deferPlay());
        assertFalse(state.resume());
    }

    @Test void newMenuChannelsRemainIndependentFromFadedGameChannels() {
        var game = new PauseChannelState();
        var menu = new PauseChannelState();
        game.gain(0);
        game.pause();
        assertEquals(0, game.volume(.8F));
        assertEquals(.8F, menu.volume(.8F));
        assertFalse(menu.deferPlay());
    }

    @Test void destroyResetRemovesEnvelopeAndPendingStartState() {
        var state = new PauseChannelState();
        state.volume(.2F);
        state.gain(0);
        state.pause();
        state.deferPlay();
        state.stopped();
        state.reset();
        assertEquals(1, state.gain(1));
        assertFalse(state.deferPlay());
        assertFalse(state.resume());
    }
    @Test void processedModGainIsNotSentThroughItsModifierAgainForEachFadeFrame() {
        var state = new PauseChannelState();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.DoubleUnaryOperator otherMod = raw -> {
            calls.incrementAndGet();
            return raw * .5;
        };
        state.volume((float) otherMod.applyAsDouble(.8));
        for (int i = 0; i < 1_000; i++) {
            state.gain(.5F);
            assertEquals(.2F, state.output(), 1e-6);
        }
        assertEquals(1, calls.get());
        state.gain(1);
        assertEquals(.4F, state.output(), 1e-6);
    }

    @Test void perChannelBackgroundMuteRemainsMutedAcrossPauseGainChanges() {
        var state = new PauseChannelState();
        state.volume(.8F);
        state.gain(.5F);
        state.volume(0); // Another mod's normal setVolume write.
        state.gain(1);
        assertEquals(0, state.output());
        state.gain(.5F);
        assertEquals(.4F, state.volume(.8F), 1e-6); // That mod explicitly restores its input.
        state.gain(1);
        assertEquals(.8F, state.output(), 1e-6);
    }

    @Test void nativeProcessedVolumeChangesReplaceTheCachedBaseDuringAnEnvelope() {
        var state = new PauseChannelState();
        state.volume(.8F);
        state.gain(.5F);
        assertEquals(.4F, state.output(), 1e-6);
        assertEquals(.15F, state.volume(.3F), 1e-6);
        state.gain(.25F);
        assertEquals(.075F, state.output(), 1e-6);
        state.gain(1);
        assertEquals(.3F, state.output(), 1e-6);
    }
}
