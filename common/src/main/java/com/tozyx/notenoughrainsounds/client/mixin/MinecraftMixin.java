package com.tozyx.notenoughrainsounds.client.mixin;

import com.tozyx.notenoughrainsounds.client.NotEnoughRainSoundsClient;
import com.tozyx.notenoughrainsounds.client.audio.PauseRequestContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.sounds.SoundManager;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    // WrapOperation preserves other wrappers; request markers are restored even on exceptions.
    @WrapOperation(method = "pauseGame", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundManager;pause()V"))
    private void notenoughrainsounds$gamePause(SoundManager sounds, Operation<Void> original) {
        PauseRequestContext.game(() -> original.call(sounds));
    }

    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundManager;resume()V"))
    private void notenoughrainsounds$gameResume(SoundManager sounds, Operation<Void> original) {
        PauseRequestContext.game(() -> original.call(sounds));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void notenoughrainsounds$tick(CallbackInfo callback) {
        NotEnoughRainSoundsClient.tick((Minecraft) (Object) this);
    }

    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    private void notenoughrainsounds$reload(CallbackInfoReturnable<CompletableFuture<Void>> callback) {
        NotEnoughRainSoundsClient.reloadConfig();
    }
}
