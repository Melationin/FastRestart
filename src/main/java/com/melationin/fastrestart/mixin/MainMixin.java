package com.melationin.fastrestart.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.melationin.fastrestart.FastRestart;
import net.minecraft.server.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.File;

@Mixin(Main.class)
public abstract class MainMixin {
    @WrapMethod(method = "main([Ljava/lang/String;)V")
    private static void FR$run(String[] args, Operation<Void> original) {
        FastRestart.run(args, original);
    }

    @WrapOperation(
            method = "main([Ljava/lang/String;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/fabricmc/loader/impl/game/minecraft/Hooks;startServer(Ljava/io/File;Ljava/lang/Object;)V"),
            require = 0
    )
    private static void FR$startFabric(File runDir, Object gameInstance, Operation<Void> original) {
        FastRestart.startFabric(runDir, gameInstance, original);
    }

    @WrapOperation(
            method = "main([Ljava/lang/String;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;startTimerHackThread()V")
    )
    private static void FR$startTimer(Operation<Void> original) {
        FastRestart.startTimer(original);
    }

    @WrapOperation(
            method = "main([Ljava/lang/String;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Runtime;addShutdownHook(Ljava/lang/Thread;)V")
    )
    private static void FR$registerShutdownHook(Runtime runtime, Thread hook, Operation<Void> original) {
        FastRestart.registerShutdownHook(runtime, hook, original);
    }
}
