package com.example.shortener;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdBlockAllocatorTest {

    /** Hands out consecutive blocks like the sequence does, and counts how often it is asked. */
    static class FakeBlockSource implements IdBlockSource {
        private final long blockSize;
        private final AtomicInteger calls = new AtomicInteger();

        FakeBlockSource(long blockSize) {
            this.blockSize = blockSize;
        }

        @Override
        public IdBlock nextBlock() {
            return new IdBlock(calls.getAndIncrement() * blockSize, blockSize);
        }
    }

    @Test
    void handsOutIdsInOrderAndClaimsNextBlockWhenEmpty() {
        FakeBlockSource source = new FakeBlockSource(3);
        IdBlockAllocator allocator = new IdBlockAllocator(source);

        List<Long> ids = List.of(allocator.nextId(), allocator.nextId(), allocator.nextId(), allocator.nextId());

        assertEquals(List.of(0L, 1L, 2L, 3L), ids);
        assertEquals(2, source.calls.get());
    }

    @Test
    void concurrentCallersGetUniqueIdsAndEachBlockIsClaimedOnce() throws Exception {
        int threads = 8;
        int idsPerThread = 5_000;
        long blockSize = 1_000;
        FakeBlockSource source = new FakeBlockSource(blockSize);
        IdBlockAllocator allocator = new IdBlockAllocator(source);

        Set<Long> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                results.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < idsPerThread; i++) {
                        ids.add(allocator.nextId());
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get();
            }
        }

        int total = threads * idsPerThread;
        // A duplicate id would make the set smaller than the number of calls.
        assertEquals(total, ids.size());
        // No block was skipped or claimed twice: ids are exactly 0..total-1.
        assertEquals(0L, ids.stream().mapToLong(Long::longValue).min().orElseThrow());
        assertEquals(total - 1L, ids.stream().mapToLong(Long::longValue).max().orElseThrow());
        assertEquals(total / blockSize, source.calls.get());
    }
}
