package com.melationin.fastrestart.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.melationin.fastrestart.FastRestart;
import com.melationin.fastrestart.FastRestartConsole;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin {
    @WrapOperation(
            method = "initServer()Z",
            at = @At(value = "INVOKE", target = "Ljava/lang/Thread;start()V")
    )
    private void FR$useSharedConsole(Thread thread, Operation<Void> original) {
        if (FastRestartConsole.isVanillaConsoleThread(thread)) {
            FastRestart.consoleHookSeen();
            return;
        }
        original.call(thread);
    }

    @WrapOperation(
            method = "stopServer()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;shutdownExecutors()V")
    )
    private void FR$preserveExecutors(Operation<Void> original) {
        if (!FastRestart.preserveExecutors()) {
            original.call();
        }
    }
}
