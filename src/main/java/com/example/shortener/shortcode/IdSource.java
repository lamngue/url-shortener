package com.example.shortener.shortcode;

/**
 * Hands out unique link ids. Each call returns a number no other caller has received.
 */
public interface IdSource {

    long nextId();
}
