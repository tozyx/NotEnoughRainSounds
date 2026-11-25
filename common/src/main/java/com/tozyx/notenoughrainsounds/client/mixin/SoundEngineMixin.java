package com.tozyx.notenoughrainsounds.client.mixin;

import com.tozyx.notenoughrainsounds.client.NotEnoughRainSoundsClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Channel;
import com.tozyx.notenoughrainsounds.client.audio.RainFilterChannel;
import com.tozyx.notenoughrainsounds.client.weather.FilteredRainVoice;
import java.util.function.Consumer;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @WrapOperation(method = "play", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"))
    private void notenoughrainsounds$bindFilter(ChannelAccess.ChannelHandle handle, Consumer<Channel> setup,
            Operation<Void> original, SoundInstance sound) {
        if (sound instanceof FilteredRainVoice rain) {
            original.call(handle, (Consumer<Channel>) channel -> {
                ((RainFilterChannel) channel).notenoughrainsounds$bindRainFilter(rain.rainOcclusion());
                setup.accept(channel);
            });
        } else {
            original.call(handle, setup);
        }
    }

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void notenoughrainsounds$weather(SoundInstance sound, CallbackInfo callback) {
        if (NotEnoughRainSoundsClient.replaceWeatherSound(sound)) callback.cancel();
    }
}
