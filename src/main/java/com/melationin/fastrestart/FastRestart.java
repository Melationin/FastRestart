package com.melationin.fastrestart;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;

public final class FastRestart {
    public static final Logger LOGGER = LogUtils.getLogger();

    private enum State { STARTING, RUNNING, STOPPING, STOPPED, EXITING }

    private static final Object MONITOR = new Object();
    private static volatile State state = State.STARTING;
    @Nullable
    private static volatile DedicatedServer server;
    @Nullable
    private static Thread shutdownHook;
    private static boolean timerStarted;
    private static boolean restarting;
    private static volatile boolean fabricHookSeen;
    private static volatile boolean consoleHookSeen;
    private static volatile boolean executorsPreserved;
    private static volatile boolean crashed;
    private static boolean startRequested;

    private FastRestart() {
    }

    public static void run(String[] args, Operation<Void> original) {
        boolean hadServer = false;
        DedicatedServer previous = null;
        FastRestartConsole.start();

        try {
            while (true) {
                state = State.STARTING;
                fabricHookSeen = false;
                executorsPreserved = false;
                crashed = false;
                restarting = hadServer;
                original.call((Object) args);
                restarting = false;

                Object instance = FabricLoader.getInstance().getGameInstance();
                if (!(instance instanceof DedicatedServer current) || current == previous) {
                    break;
                }

                hadServer = true;
                server = current;
                state = State.RUNNING;
                joinUninterruptibly(current.getRunningThread());
                if (!removeShutdownHook()) {
                    break;
                }

                if (state != State.STOPPING || !executorsPreserved || crashed) {
                    break;
                }

                synchronized (MONITOR) {
                    state = State.STOPPED;
                    server = null;
                    LOGGER.info("FastRestart: server stopped. Type \"{}\" to start it again, or press Ctrl+C to exit the JVM.",
                            FastRestartConsole.CMD_START);
                    while (!startRequested) {
                        try {
                            MONITOR.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    startRequested = false;
                }

                previous = current;
                LOGGER.info("FastRestart: starting the server again in the same JVM ...");
            }
        } finally {
            state = State.EXITING;
            server = null;
            restarting = false;
            removeShutdownHook();
            if (hadServer) {
                Util.shutdownExecutors();
            }
        }
    }

    public static void startFabric(File runDir, Object gameInstance, Operation<Void> original) {
        fabricHookSeen = true;
        if (!restarting) {
            original.call(runDir, gameInstance);
            return;
        }

        File directory = runDir == null ? new File(".") : runDir;
        FabricLoaderImpl.INSTANCE.prepareModInit(directory.toPath(), gameInstance);
        LOGGER.info("FastRestart: skipped Fabric mod entrypoints on in-process restart.");
    }

    public static void startTimer(Operation<Void> original) {
        if (!timerStarted) {
            original.call();
            timerStarted = true;
        }
    }

    public static void registerShutdownHook(Runtime runtime, Thread hook, Operation<Void> original) {
        original.call(runtime, hook);
        shutdownHook = hook;
    }

    public static void consoleHookSeen() {
        consoleHookSeen = true;
    }

    public static boolean preserveExecutors() {
        if (state == State.STOPPING && !crashed) {
            executorsPreserved = true;
            return true;
        }
        return false;
    }

    public static void markCrash(MinecraftServer current) {
        if (current == server) {
            crashed = true;
        }
    }

    public static void requestTempStop() {
        DedicatedServer current;
        synchronized (MONITOR) {
            current = server;
            if (state != State.RUNNING || current == null || current.isStopped()
                    || !current.isRunning() || !current.isReady()) {
                LOGGER.info("FastRestart: the server is not ready.");
                return;
            }
            if (!fabricHookSeen || !consoleHookSeen || shutdownHook == null || !FastRestartConsole.isRunning()) {
                LOGGER.error("FastRestart: restart hooks are unavailable; the server is still running.");
                return;
            }
            state = State.STOPPING;
        }

        LOGGER.info("FastRestart: temporarily stopping the server.");
        current.halt(false);
    }

    public static void requestStart() {
        synchronized (MONITOR) {
            if (state != State.STOPPING && state != State.STOPPED) {
                LOGGER.info("FastRestart: the server is already running.");
                return;
            }
            startRequested = true;
            MONITOR.notifyAll();
        }
        LOGGER.info("FastRestart: start requested.");
    }

    @Nullable
    public static MinecraftServer currentServer() {
        return state == State.RUNNING ? server : null;
    }

    private static boolean removeShutdownHook() {
        Thread hook = shutdownHook;
        shutdownHook = null;
        if (hook == null) {
            return true;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static void joinUninterruptibly(Thread thread) {
        boolean interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
