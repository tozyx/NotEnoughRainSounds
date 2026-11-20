package com.tozyx.notenoughrainsounds.client.mixin;

import com.tozyx.notenoughrainsounds.client.NotEnoughRainSoundsClient;
import com.tozyx.notenoughrainsounds.client.audio.PauseAudioController;
import com.tozyx.notenoughrainsounds.client.audio.PauseChannel;
import com.tozyx.notenoughrainsounds.client.audio.PauseReasons;
import com.tozyx.notenoughrainsounds.client.audio.PauseRequestContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Consumer;
import java.util.stream.Stream;
import com.mojang.blaze3d.audio.Channel;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies equally to vanilla and other mods using Minecraft's sound engine. */
@Mixin(SoundEngine.class)
public abstract class SoundEnginePauseMixin {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    @Unique private final PauseAudioController<SoundInstance> notenoughrainsounds$pause = new PauseAudioController<>();

    @Unique private final PauseReasons notenoughrainsounds$reasons = new PauseReasons();

    // Keep the engine method itself running, including other mods' HEAD/TAIL callbacks.
    // Only substitute the native channel dispatch when the game owns a smooth pause.
    @WrapOperation(method = "pause", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/ChannelAccess;executeOnChannels(Ljava/util/function/Consumer;)V"))
    private void notenoughrainsounds$pause(ChannelAccess channels, Consumer<Stream<Channel>> action,
            Operation<Void> original) {
        boolean game = PauseRequestContext.gameRequest();
        notenoughrainsounds$reasons.pause(game);
        if (!game || !NotEnoughRainSoundsClient.smoothPauseEnabled()) {
            if (game) notenoughrainsounds$pause.finishWithoutFade();
            original.call(channels, action);
            return;
        }
        Map<SoundInstance, PauseAudioController.Voice> voices = new IdentityHashMap<>();
        instanceToChannel.forEach((sound, handle) -> voices.put(sound, new PauseAudioController.Voice() {
            @Override public boolean alive() {
                return instanceToChannel.get(sound) == handle && !handle.isStopped();
            }
            @Override public void updateVolume() {
                float gain = notenoughrainsounds$pause.multiplier(sound);
                handle.execute(channel -> ((PauseChannel) channel).notenoughrainsounds$pauseGain(gain));
            }
            @Override public void pause() {
                handle.execute(channel -> ((PauseChannel) channel).notenoughrainsounds$pauseAfterFade());
            }
            @Override public void resume() {
                handle.execute(channel -> ((PauseChannel) channel).notenoughrainsounds$resumeAfterFade());
            }
        }));
        if (notenoughrainsounds$pause.pause(voices, System.nanoTime())) {
            original.call(channels, notenoughrainsounds$noChannelAction());
        } else original.call(channels, action);
    }

    @WrapOperation(method = "resume", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/ChannelAccess;executeOnChannels(Ljava/util/function/Consumer;)V"))
    private void notenoughrainsounds$resume(ChannelAccess channels, Consumer<Stream<Channel>> action,
            Operation<Void> original) {
        boolean game = PauseRequestContext.gameRequest();
        boolean released = notenoughrainsounds$reasons.resume(game);
        boolean enabled = NotEnoughRainSoundsClient.smoothPauseEnabled();
        if (!enabled && !notenoughrainsounds$pause.active()) {
            original.call(channels, action);
            return;
        }
        if (!released) {
            if (game) notenoughrainsounds$pause.finishWithoutFade();
            // Focus returning cannot release Esc pause; Esc resume cannot release a background hold.
            original.call(channels, notenoughrainsounds$noChannelAction());
            return;
        }
        if (enabled) notenoughrainsounds$pause.resume(System.nanoTime());
        else notenoughrainsounds$pause.resumeWithoutFade();
        // Our gain/unpause commands precede this native dispatch on the same sound executor.
        // This also resumes any externally paused menu channels outside our earlier snapshot.
        original.call(channels, action);
    }

    @Unique
    private static Consumer<Stream<Channel>> notenoughrainsounds$noChannelAction() {
        return stream -> {};
    }

    // updateSource is called per rendered frame, including the pause screen. The tick fallback
    // also runs while paused and keeps progressing when rendering is throttled or minimized.
    @Inject(method = "updateSource", at = @At("HEAD"))
    private void notenoughrainsounds$frame(Camera camera, CallbackInfo callback) {
        notenoughrainsounds$updatePause();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void notenoughrainsounds$tick(boolean paused, CallbackInfo callback) {
        notenoughrainsounds$updatePause();
    }

    @Unique
    private void notenoughrainsounds$updatePause() {
        if (NotEnoughRainSoundsClient.smoothPauseEnabled()) notenoughrainsounds$pause.update(System.nanoTime());
        else notenoughrainsounds$pause.finishWithoutFade();
    }

    @Inject(method = {"stopAll", "destroy", "emergencyShutdown"}, at = @At("HEAD"))
    private void notenoughrainsounds$clear(CallbackInfo callback) {
        notenoughrainsounds$pause.reset();
        notenoughrainsounds$reasons.reset();
    }
}
