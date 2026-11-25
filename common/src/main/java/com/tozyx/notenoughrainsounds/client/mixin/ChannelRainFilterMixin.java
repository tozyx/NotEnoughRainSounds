package com.tozyx.notenoughrainsounds.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.tozyx.notenoughrainsounds.client.audio.RainFilterChannel;
import com.tozyx.notenoughrainsounds.client.weather.OcclusionGain;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** All filter operations run on the channel's sound executor in its own OpenAL context. */
@Mixin(Channel.class)
public abstract class ChannelRainFilterMixin implements RainFilterChannel {
    @Shadow @Final private int source;
    @Unique private OcclusionGain notenoughrainsounds$enclosure;
    @Unique private int notenoughrainsounds$filter;
    @Unique private float notenoughrainsounds$lastGain = 1;

    @Override public void notenoughrainsounds$bindRainFilter(OcclusionGain enclosure) {
        if (AL.getCapabilities().ALC_EXT_EFX) notenoughrainsounds$enclosure = enclosure;
    }

    @Inject(method = "setVolume", at = @At("HEAD"))
    private void notenoughrainsounds$filterVolume(float volume, CallbackInfo ci) {
        if (notenoughrainsounds$enclosure == null) return;
        float hf = notenoughrainsounds$enclosure.highGain();
        if (hf == notenoughrainsounds$lastGain) return;
        if (hf == 1) {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, 0);
        } else {
            if (notenoughrainsounds$filter == 0) {
                notenoughrainsounds$filter = EXTEfx.alGenFilters();
                if (notenoughrainsounds$filter == 0) {
                    notenoughrainsounds$enclosure = null;
                    return;
                }
                EXTEfx.alFilteri(notenoughrainsounds$filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            }
            EXTEfx.alFilterf(notenoughrainsounds$filter, EXTEfx.AL_LOWPASS_GAINHF, hf);
            // OpenAL copies the filter parameters into the source on assignment.
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, notenoughrainsounds$filter);
        }
        notenoughrainsounds$lastGain = hf;
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void notenoughrainsounds$releaseFilter(CallbackInfo ci) {
        if (notenoughrainsounds$filter != 0) {
            EXTEfx.alDeleteFilters(notenoughrainsounds$filter);
            notenoughrainsounds$filter = 0;
        }
        notenoughrainsounds$enclosure = null;
    }
}
