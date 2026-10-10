package com.example.shortener;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Hands out unique ids from an in-memory block and claims a new block when it runs out.
 * Ids left in the block when the instance stops are never used.
 */
@Component
public class IdBlockAllocator {

    private final IdBlockSource source;
    private final ReentrantLock lock = new ReentrantLock();

    // Guarded by lock. The block is empty when next == end (end is exclusive).
    private long next;
    private long end;

    public IdBlockAllocator(IdBlockSource source) {
        this.source = source;
    }

    public long nextId() {
        lock.lock();
        try {
            if (next == end) {
                // Only the thread holding the lock refills, so two threads never claim a block at once.
                IdBlockSource.IdBlock block = source.nextBlock();
                next = block.start();
                end = block.start() + block.size();
            }
            return next++;
        } finally {
            lock.unlock();
        }
    }
}
