package com.tozyx.notenoughrainsounds.client.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PauseReasonsTest {
    @Test void focusReturnCannotReleaseAnEscPause() {
        var reasons = new PauseReasons();
        reasons.pause(true);
        reasons.pause(false);
        assertFalse(reasons.resume(false));
        assertTrue(reasons.resume(true));
    }

    @Test void gameResumeCannotReleaseABackgroundPause() {
        var reasons = new PauseReasons();
        reasons.pause(true);
        reasons.pause(false);
        assertFalse(reasons.resume(true));
        assertTrue(reasons.resume(false));
    }

    @Test void backgroundPauseBeforeEscWorksInEitherReleaseOrder() {
        var reasons = new PauseReasons();
        reasons.pause(false);
        reasons.pause(true);
        assertFalse(reasons.resume(false));
        assertTrue(reasons.resume(true));
        reasons.pause(false);
        reasons.pause(true);
        assertFalse(reasons.resume(true));
        assertTrue(reasons.resume(false));
    }

    @Test void anUnrelatedExternalResumeCannotReleaseGameOwnership() {
        var reasons = new PauseReasons();
        reasons.pause(true);
        assertFalse(reasons.resume(false));
        assertFalse(reasons.resume(false));
        assertTrue(reasons.resume(true));
    }

    @Test void repeatedRequestsFromOneKindOfPauseAreIdempotent() {
        var reasons = new PauseReasons();
        reasons.pause(true);
        reasons.pause(true);
        reasons.pause(false);
        reasons.pause(false);
        assertFalse(reasons.resume(true));
        assertTrue(reasons.resume(false));
    }

    @Test void externalOnlyPauseAndResumeRetainTheirNativeSequence() {
        var reasons = new PauseReasons();
        reasons.pause(false);
        assertTrue(reasons.resume(false));
        assertTrue(reasons.resume(true));
    }

    @Test void worldOrDeviceResetDiscardsAllPreviousHolds() {
        var reasons = new PauseReasons();
        reasons.pause(true);
        reasons.pause(false);
        reasons.reset();
        assertTrue(reasons.resume(true));
        assertTrue(reasons.resume(false));
    }

    @Test void anIdleResumeDoesNotAcquireAnyPauseHold() {
        var reasons = new PauseReasons();
        assertTrue(reasons.resume(true));
        assertTrue(reasons.resume(false));
    }
}
