package com.example.shortener.recorder;

/**
 * Records that a short link was followed. Implementations must never slow down or fail the
 * redirect: if a click cannot be recorded quickly, it is dropped (statistics are approximate).
 */
public interface ClickRecorder {

    void record(String code);
}
