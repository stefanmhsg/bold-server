package org.maze.application;

import java.util.function.Supplier;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Coordinates repository mutations that may otherwise interleave with a full reset.
 */
public class MazeMutationCoordinator {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    public <T> T withSharedMutation(Supplier<T> operation) {
        lock.readLock().lock();
        try {
            return operation.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    public <T> T withExclusiveMutation(CheckedSupplier<T> operation) throws Exception {
        lock.writeLock().lock();
        try {
            return operation.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
