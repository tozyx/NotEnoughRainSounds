package com.tozyx.notenoughrainsounds.client.audio;

import java.util.IdentityHashMap;
import java.util.Map;

/** A real-time envelope over the voices present at pause, independent of frozen game ticks. */
public final class PauseAudioController<K> {
    public static final long FADE_OUT_NANOS = 400_000_000L;
    public static final long FADE_IN_NANOS = 250_000_000L;

    public interface Voice {
        boolean alive();
        void updateVolume();
        void pause();
        void resume();
    }

    private enum State { IDLE, FADING_OUT, PAUSED, FADING_IN }
    private final Map<K, Voice> voices = new IdentityHashMap<>();
    private State state = State.IDLE;
    private float gain = 1;
    private float from = 1;
    private float target = 1;
    private long startedAt;
    private long duration;

    public boolean active() { return state != State.IDLE; }

    /** New menu sounds are absent from this snapshot and retain their normal volume. */
    public float multiplier(K voice) { return voices.containsKey(voice) ? gain : 1; }

    public boolean pause(Map<K, Voice> current, long now) {
        update(now);
        if (state == State.FADING_OUT || state == State.PAUSED) return true;
        if (current.isEmpty() && !active()) return false;
        voices.putAll(current);
        begin(State.FADING_OUT, 0, FADE_OUT_NANOS, now);
        update(now);
        return true;
    }

    public boolean resume(long now) {
        if (!active()) return false;
        update(now);
        if (state == State.IDLE || state == State.FADING_IN) return true;
        boolean wasPaused = state == State.PAUSED;
        if (wasPaused) gain = 0;
        begin(State.FADING_IN, 1, FADE_IN_NANOS, now);
        // Set the quiet gain before unpausing channels, preventing a full-volume burst.
        refresh();
        if (wasPaused) voices.values().forEach(Voice::resume);
        return true;
    }

    public void update(long now) {
        if (state == State.IDLE) return;
        voices.values().removeIf(voice -> !voice.alive());
        if (state == State.PAUSED) return;
        double progress = duration == 0 ? 1 : Math.clamp((now - startedAt) / (double) duration, 0, 1);
        double smooth = progress * progress * (3 - 2 * progress);
        gain = (float) (from + (target - from) * smooth);
        refresh();
        if (progress < 1) return;
        if (state == State.FADING_OUT) {
            voices.values().forEach(Voice::pause);
            state = State.PAUSED;
        } else {
            reset();
        }
    }

    /** Apply native pause state immediately when the option is disabled mid-transition. */
    public void finishWithoutFade() {
        if (!active()) return;
        voices.values().removeIf(voice -> !voice.alive());
        if (state == State.FADING_OUT) {
            gain = 0;
            refresh();
            voices.values().forEach(Voice::pause);
            state = State.PAUSED;
        }
        if (gain != 1) {
            gain = 1;
            refresh();
        }
        // Keep paused handles until resume to clear channel-side deferred-start flags safely.
        if (state == State.FADING_IN) reset();
    }

    /** Release any previous smooth-pause ownership before vanilla resumes all channels. */
    public boolean resumeWithoutFade() {
        if (!active()) return false;
        voices.values().removeIf(voice -> !voice.alive());
        boolean wasPaused = state == State.PAUSED;
        gain = 1;
        refresh();
        if (wasPaused) voices.values().forEach(Voice::resume);
        reset();
        return true;
    }

    /** Called alongside native stopAll/destroy: never resume or recreate disposed voices. */
    public void reset() {
        voices.clear();
        state = State.IDLE;
        gain = from = target = 1;
    }

    private void begin(State next, float destination, long fullDuration, long now) {
        from = gain;
        target = destination;
        startedAt = now;
        duration = (long) (fullDuration * Math.abs(target - from));
        state = next;
    }

    private void refresh() { voices.values().forEach(Voice::updateVolume); }
}
