package com.tozyx.notenoughrainsounds.client.audio;

/** Invoked only through ChannelHandle.execute on Minecraft's sound executor. */
public interface PauseChannel {
    void notenoughrainsounds$pauseGain(float gain);
    void notenoughrainsounds$pauseAfterFade();
    void notenoughrainsounds$resumeAfterFade();
}
