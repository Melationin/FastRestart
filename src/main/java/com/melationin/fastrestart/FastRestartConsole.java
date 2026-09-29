package com.melationin.fastrestart;

import com.mojang.logging.LogUtils;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class FastRestartConsole {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String CMD_STOP = "/stop temp";
    public static final String CMD_START = "/start";

    private static volatile Thread readerThread;

    private FastRestartConsole() {
    }

    public static synchronized void start() {
        if (readerThread != null) {
            return;
        }

        Thread thread = new Thread(FastRestartConsole::run, "FastRestart console");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        thread.start();
        readerThread = thread;
    }

    public static boolean isRunning() {
        Thread thread = readerThread;
        return thread != null && thread.isAlive();
    }

    public static boolean isVanillaConsoleThread(Thread thread) {
        return isRunning() && "Server console handler".equals(thread.getName());
    }

    private static void run() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                handle(line);
            }
        } catch (IOException e) {
            LOGGER.error("Exception handling console input", e);
        }
    }

    private static void handle(String rawLine) {
        String line = rawLine.trim();
        if (line.isEmpty()) {
            return;
        }

        String command = line.startsWith("/") ? line.substring(1).trim().toLowerCase(Locale.ROOT) : line.toLowerCase(Locale.ROOT);
        switch (command) {
            case "stop temp" -> FastRestart.requestTempStop();
            case "start" -> FastRestart.requestStart();
            default -> {
                MinecraftServer server = FastRestart.currentServer();
                if (server instanceof DedicatedServer dedicated && dedicated.isRunning() && !dedicated.isStopped()) {
                    dedicated.handleConsoleInput(line, dedicated.createCommandSourceStack());
                } else {
                    LOGGER.info("FastRestart: no server is running. Type \"{}\" to start it again.", CMD_START);
                }
            }
        }
    }

}
