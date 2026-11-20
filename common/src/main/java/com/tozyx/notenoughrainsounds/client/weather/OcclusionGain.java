package com.tozyx.notenoughrainsounds.client.weather;

/** Applies cover at the onset and eases later changes over four audio ticks. */
public final class OcclusionGain {
    public static final int CHECK_INTERVAL_TICKS = 4;
    private final float covered;
    private final float step;
    private float gain;
    private float target;

    public OcclusionGain(float covered, boolean blocked) {
        this.covered = covered;
        this.step = (1 - covered) / 4;
        target(blocked);
        gain = target;
    }

    public float current() { return gain; }

    public void target(boolean blocked) {
        target = blocked ? covered : 1;
    }

    public float tick() {
        float difference = target - gain;
        gain = Math.abs(difference) <= step + 1e-6F ? target : gain + Math.copySign(step, difference);
        return gain;
    }
}
