package com.tozyx.notenoughrainsounds.client.audio;

/** Output-side gain: never feeds a temporary fade zero back into native voice lifetime decisions. */
public final class PauseChannelState {
    private float baseVolume = 1;
    private float gain = 1;
    private boolean paused;
    private boolean deferredStart;
    private boolean cancelled;

    public float volume(float nativeVolume) {
        baseVolume = nativeVolume;
        return output();
    }

    /** Return the untouched native volume so reapplying an envelope never compounds it. */
    public float gain(float next) {
        gain = Math.clamp(next, 0, 1);
        return baseVolume;
    }

    /** Reapply only the output envelope; never feed the cached processed gain upstream again. */
    public float output() { return baseVolume * gain; }

    public void pause() { paused = true; }

    /** A buffer may finish decoding after pause has completed. Keep that start silent and pending. */
    public boolean deferPlay() {
        if (!paused) return false;
        deferredStart = !cancelled;
        return true;
    }

    public boolean resume() {
        paused = false;
        boolean start = deferredStart;
        deferredStart = false;
        return start;
    }

    public void stopped() { cancelled = true; deferredStart = false; }

    public void reset() {
        baseVolume = gain = 1;
        paused = deferredStart = cancelled = false;
    }
}
