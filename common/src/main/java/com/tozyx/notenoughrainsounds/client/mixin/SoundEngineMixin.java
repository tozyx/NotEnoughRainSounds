package com.tozyx.notenoughrainsounds.client.mixin;

import com.tozyx.notenoughrainsounds.client.NotEnoughRainSoundsClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void notenoughrainsounds$weather(SoundInstance sound, CallbackInfo callback) {
        if (NotEnoughRainSoundsClient.replaceWeatherSound(sound)) callback.cancel();
    }
}
