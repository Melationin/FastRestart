package com.melationin.fastrestart.mixin;

import com.melationin.fastrestart.FastRestart;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(
            method = "runServer()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;constructOrExtractCrashReport(Ljava/lang/Throwable;)Lnet/minecraft/CrashReport;")
    )
    private void FR$markCrash(CallbackInfo ci) {
        FastRestart.markCrash((MinecraftServer) (Object) this);
    }
}
