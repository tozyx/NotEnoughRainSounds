package com.tozyx.notenoughrainsounds.client.audio;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PauseRequestContextTest {
    @Test void onlyTheVanillaCallScopeHasGameOwnership() {
        assertFalse(PauseRequestContext.gameRequest());
        PauseRequestContext.game(() -> assertTrue(PauseRequestContext.gameRequest()));
        assertFalse(PauseRequestContext.gameRequest());
    }

    @Test void anExceptionCannotLeakGameOwnershipToLaterModRequests() {
        assertThrows(IllegalStateException.class, () -> PauseRequestContext.game(() -> {
            assertTrue(PauseRequestContext.gameRequest());
            throw new IllegalStateException("other wrapper failed");
        }));
        assertFalse(PauseRequestContext.gameRequest());
    }

    @Test void nestedWrappersRestoreTheOuterContext() {
        PauseRequestContext.game(() -> {
            PauseRequestContext.game(() -> assertTrue(PauseRequestContext.gameRequest()));
            assertTrue(PauseRequestContext.gameRequest());
        });
        assertFalse(PauseRequestContext.gameRequest());
    }

    @Test void audioThreadOrOtherModThreadDoesNotInheritTheCallerMarker() {
        AtomicBoolean inherited = new AtomicBoolean(true);
        PauseRequestContext.game(() -> {
            Thread thread = Thread.ofPlatform().start(() -> inherited.set(PauseRequestContext.gameRequest()));
            try { thread.join(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
        });
        assertFalse(inherited.get());
        assertFalse(PauseRequestContext.gameRequest());
    }
}
