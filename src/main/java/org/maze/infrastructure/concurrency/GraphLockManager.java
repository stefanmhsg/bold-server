package org.maze.infrastructure.concurrency;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fine-grained locking manager for RDF named graphs.
 * Provides per-graph locks to allow concurrent operations on different graphs
 * while ensuring consistency within a single graph.
 * 
 * <p>This replaces coarse-grained repository-level locking with efficient
 * graph-level locking, significantly improving concurrency for multi-agent scenarios.</p>
 */
public class GraphLockManager {
    
    private static final Logger log = LoggerFactory.getLogger(GraphLockManager.class);
    
    // Per-graph locks for fine-grained concurrency control
    private final ConcurrentHashMap<String, Lock> graphLocks = new ConcurrentHashMap<>();
    
    /**
     * Acquire a lock for the specified graph URI.
     * Creates a new lock if one doesn't exist for this graph.
     * 
     * @param graphUri The graph URI to lock
     * @return Lock for the graph
     */
    public Lock getLock(String graphUri) {
        return graphLocks.computeIfAbsent(graphUri, uri -> {
            log.debug("Creating new lock for graph: {}", uri);
            return new ReentrantLock();
        });
    }
    
    /**
     * Execute an operation with a lock on a single graph.
     * 
     * @param graphUri The graph URI to lock
     * @param operation The operation to execute
     */
    public void withLock(String graphUri, Runnable operation) {
        Lock lock = getLock(graphUri);
        lock.lock();
        try {
            log.trace("Acquired lock for graph: {}", graphUri);
            operation.run();
        } finally {
            lock.unlock();
            log.trace("Released lock for graph: {}", graphUri);
        }
    }
    
    /**
     * Execute an operation with locks on multiple graphs.
     * Locks are acquired in sorted order to prevent deadlocks.
     * 
     * @param graphUris The graph URIs to lock (will be sorted)
     * @param operation The operation to execute
     */
    public void withLocks(String[] graphUris, Runnable operation) {
        // Sort to ensure consistent lock ordering and prevent deadlock
        java.util.Arrays.sort(graphUris);
        
        Lock[] locks = new Lock[graphUris.length];
        for (int i = 0; i < graphUris.length; i++) {
            locks[i] = getLock(graphUris[i]);
        }
        
        // Acquire all locks in order
        for (int i = 0; i < locks.length; i++) {
            locks[i].lock();
            log.trace("Acquired lock for graph: {}", graphUris[i]);
        }
        
        try {
            operation.run();
        } finally {
            // Release all locks in reverse order
            for (int i = locks.length - 1; i >= 0; i--) {
                locks[i].unlock();
                log.trace("Released lock for graph: {}", graphUris[i]);
            }
        }
    }
    
    /**
     * Get the number of graph locks currently tracked.
     * Useful for monitoring and debugging.
     * 
     * @return Number of graph locks
     */
    public int getLockCount() {
        return graphLocks.size();
    }
    
    /**
     * Clear unused locks (optional cleanup).
     * Note: Only safe to call when no operations are in progress.
     */
    public void cleanup() {
        graphLocks.clear();
        log.info("Cleared all graph locks");
    }
}
