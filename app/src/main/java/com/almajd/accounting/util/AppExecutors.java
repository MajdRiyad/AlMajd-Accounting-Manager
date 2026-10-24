package com.almajd.accounting.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Small shared helper used to move database (and other blocking) work off the
 * main thread while keeping results delivered back on the main thread.
 * <p>
 * A <b>single</b> worker thread is used on purpose: writes (and reads) keep
 * their submission order, so two saves can never interleave, and the same
 * thread is reused for PDF/Excel/CSV generation (those helpers cache shared
 * state that is not thread-safe).
 * <p>
 * There are no third-party dependencies: the executor is a plain
 * {@link Executors#newSingleThreadExecutor()} and results are posted with a
 * {@link Handler} bound to the main {@link Looper}.
 */
public final class AppExecutors {

    /**
     * Single background thread for all database and file work.
     * Created lazily by the JVM on first use and reused for the app's lifetime.
     */
    private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "almajd-db");
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                }
            });

    /** Main-thread handler used to post UI updates. */
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    /**
     * Runs {@code task} on the single background worker thread. Tasks run in the
     * order they are submitted.
     */
    public static void runOnDb(Runnable task) {
        DB_EXECUTOR.execute(task);
    }

    /**
     * Posts {@code task} to the main thread. Callers that touch views MUST invoke
     * this from a background task and should guard the body with
     * {@code isFinishing()/isDestroyed()} checks on the target Activity.
     */
    public static void runOnMain(Runnable task) {
        MAIN_HANDLER.post(task);
    }
}
