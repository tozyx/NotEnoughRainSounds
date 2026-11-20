package com.tozyx.notenoughrainsounds.client.audio;

/** Idempotent game and external holds. One caller cannot release the other kind of pause. */
public final class PauseReasons {
    private boolean game;
    private boolean external;

    public void pause(boolean gameRequest) {
        if (gameRequest) game = true;
        else external = true;
    }

    /** True only when both independent kinds of pause have been released. */
    public boolean resume(boolean gameRequest) {
        if (gameRequest) game = false;
        else external = false;
        return !game && !external;
    }

    public void reset() { game = external = false; }
}
