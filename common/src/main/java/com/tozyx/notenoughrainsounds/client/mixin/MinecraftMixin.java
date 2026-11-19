package com.tozyx.notenoughrainsounds.client.mixin;

import com.tozyx.notenoughrainsounds.client.NotEnoughRainSoundsClient;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void notenoughrainsounds$tick(CallbackInfo callback) {
        NotEnoughRainSoundsClient.tick((Minecraft) (Object) this);
    }

    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    private void notenoughrainsounds$reload(CallbackInfoReturnable<CompletableFuture<Void>> callback) {
        NotEnoughRainSoundsClient.reloadConfig();
    }
}
