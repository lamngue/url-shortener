package com.example.shortener;

/**
 * Hands out blocks of ids that no other instance will receive.
 */
public interface IdBlockSource {

    /** Claims a new block. Ids from {@code start} up to {@code start + size - 1} belong to the caller. */
    IdBlock nextBlock();

    record IdBlock(long start, long size) {
    }
}
