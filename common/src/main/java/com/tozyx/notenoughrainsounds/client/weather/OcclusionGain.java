package com.tozyx.notenoughrainsounds.client.weather;

/** Applies cover at the onset and eases later changes over four audio ticks. */
public final class OcclusionGain {
    public static final int CHECK_INTERVAL_TICKS = 4;
    private final float covered;
    private final float step;
    // The sound executor observes the same smoothed coverage as the client tick's volume.
    private volatile float gain;
    private float coveredHighGain = 1;
    private float target;

    public OcclusionGain(float covered, boolean blocked) {
        this.covered = covered;
        this.step = (1 - covered) / 4;
        target(blocked);
        gain = target;
    }

    public void filterAt(int cutoffHz) {
        coveredHighGain = referenceGain(cutoffHz);
    }

    // EFX exposes gain at a 5 kHz reference. Solve the unity-slope OpenAL Soft shelf
    // for a -3 dB point at the requested frequency, using a nominal 48 kHz mixer.
    static float referenceGain(int cutoffHz) {
        double ratio = Math.tan(Math.PI * 5000 / 48000) / Math.tan(Math.PI * cutoffHz / 48000);
        double k = Math.pow(ratio, 4);
        return (float) Math.sqrt(2 / (k + Math.hypot(k, Math.sqrt(8))));
    }

    public float highGain() {
        return 1 - (1 - coveredHighGain) * ((1 - gain) / (1 - covered));
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
