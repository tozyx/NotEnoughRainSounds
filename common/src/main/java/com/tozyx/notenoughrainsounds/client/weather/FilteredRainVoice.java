package com.tozyx.notenoughrainsounds.client.weather;

/** A rain playback channel whose low-pass follows its existing occlusion envelope. */
public interface FilteredRainVoice {
    OcclusionGain rainOcclusion();
}
