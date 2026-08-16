package org.example.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility for thread-safe lazy initialization
 * Provides double-check locking pattern
 */
public class LazyInitializer {

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Execute initialization with double-check locking
     */
    public void initialize(Runnable initAction) {
        if (initialized.get()) {
            return;
        }

        lock.lock();
        try {
            if (initialized.get()) {
                return;
            }

            initAction.run();
            initialized.set(true);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Execute initialization with exception handling
     */
    public <T> T initializeWithResult(Initializer<T> initializer) {
        if (initialized.get()) {
            return initializer.getResult();
        }

        lock.lock();
        try {
            if (initialized.get()) {
                return initializer.getResult();
            }

            T result = initializer.initialize();
            initialized.set(true);
            return result;
        } finally {
            lock.unlock();
        }
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public void reset() {
        lock.lock();
        try {
            initialized.set(false);
        } finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    public interface Initializer<T> {
        T initialize();

        default T getResult() {
            return null;
        }
    }
}