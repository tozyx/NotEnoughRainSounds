package com.tozyx.notenoughrainsounds.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.tozyx.notenoughrainsounds.client.audio.PauseChannel;
import com.tozyx.notenoughrainsounds.client.audio.PauseChannelState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.lwjgl.openal.AL10;
import java.util.concurrent.atomic.AtomicBoolean;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public abstract class ChannelPauseMixin implements PauseChannel {
    @Unique private final PauseChannelState notenoughrainsounds$fade = new PauseChannelState();

    @Shadow @Final private int source;
    @Shadow @Final private AtomicBoolean initialized;

    // Capture the processed gain at the native OpenAL write, after setVolume argument modifiers.
    // Chaining allows other wrappers at this operation to continue executing on normal writes.
    @WrapOperation(method = "setVolume", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/openal/AL10;alSourcef(IIF)V", remap = false))
    private void notenoughrainsounds$volume(int channel, int property, float processedVolume,
            Operation<Void> original) {
        original.call(channel, property, notenoughrainsounds$fade.volume(processedVolume));
    }

    @Override public void notenoughrainsounds$pauseGain(float gain) {
        notenoughrainsounds$fade.gain(gain);
        if (initialized.get()) {
            // Already on the sound executor. Do not re-enter setVolume and re-run other mods'
            // argument transforms on the cached processed gain every frame.
            AL10.alSourcef(source, AL10.AL_GAIN, notenoughrainsounds$fade.output());
        }
    }

    @Override public void notenoughrainsounds$pauseAfterFade() {
        notenoughrainsounds$fade.pause();
        ((Channel) (Object) this).pause();
    }

    @Override public void notenoughrainsounds$resumeAfterFade() {
        Channel channel = (Channel) (Object) this;
        if (notenoughrainsounds$fade.resume()) channel.play();
        else channel.unpause(); // Native unpause cannot restart a naturally finished or stopped voice.
    }

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void notenoughrainsounds$deferStart(CallbackInfo callback) {
        if (notenoughrainsounds$fade.deferPlay()) callback.cancel();
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void notenoughrainsounds$stop(CallbackInfo callback) {
        notenoughrainsounds$fade.stopped();
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void notenoughrainsounds$destroy(CallbackInfo callback) {
        notenoughrainsounds$fade.reset();
    }
}
