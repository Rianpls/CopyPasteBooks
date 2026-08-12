package io.github.rianpls.copypastebooks.mc;

import io.github.rianpls.copypastebooks.CopyPasteBooks;
import io.github.rianpls.copypastebooks.core.Config;
import io.github.rianpls.copypastebooks.core.I18n;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/** Shared client runtime state and next-tick task queue. */
public final class CPB {
    private static Config config = new Config();
    private static Path configFile;
    private static final Deque<Runnable> QUEUE = new ArrayDeque<>();

    private CPB() {
    }

    public static void init(Path configDir) {
        configFile = configDir.resolve("copypastebooks.json");
        config = Config.load(configFile);
        I18n.bootstrap();
        CopyPasteBooks.LOGGER.info("CopyPasteBooks ready (config: {})", configFile);
    }

    public static Config config() {
        return config;
    }

    public static void saveConfig() {
        if (configFile == null) {
            return;
        }
        try {
            config.save(configFile);
        } catch (Exception e) {
            CopyPasteBooks.LOGGER.error("Failed to save config", e);
        }
    }

    /**
     * Runs a task on the next client tick. Needed e.g. to open a screen from a command:
     * the chat screen is still closing when the command executes.
     */
    public static void later(Runnable task) {
        synchronized (QUEUE) {
            QUEUE.add(task);
        }
    }

    /** Clears client-only state that must not survive a server disconnect. */
    public static void onDisconnect() {
        FileDialogs.abortActive();
        synchronized (QUEUE) {
            QUEUE.clear();
        }
        VolumeTracker.clear();
        BookSendQueue.clear();
        VolumeState.clear();
    }

    /** Called once per client tick from each loader's tick event. */
    public static void tick() {
        BookSendQueue.tick();
        VolumeTracker.tick();
        Runnable task;
        while (true) {
            synchronized (QUEUE) {
                task = QUEUE.poll();
            }
            if (task == null) {
                break;
            }
            run(task);
        }
    }

    private static void run(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            CopyPasteBooks.LOGGER.error("Deferred task failed", e);
        }
    }
}
