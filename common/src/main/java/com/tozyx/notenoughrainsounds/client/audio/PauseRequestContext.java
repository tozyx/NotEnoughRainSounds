package com.tozyx.notenoughrainsounds.client.audio;

/** Marks only the actual vanilla pause/resume call sites; other callers remain external. */
public final class PauseRequestContext {
    private static final ThreadLocal<Boolean> GAME_REQUEST = new ThreadLocal<>();
    private PauseRequestContext() {}

    public static boolean gameRequest() { return Boolean.TRUE.equals(GAME_REQUEST.get()); }

    public static void game(Runnable action) {
        Boolean previous = GAME_REQUEST.get();
        GAME_REQUEST.set(true);
        try {
            action.run();
        } finally {
            if (previous == null) GAME_REQUEST.remove();
            else GAME_REQUEST.set(previous);
        }
    }
}
