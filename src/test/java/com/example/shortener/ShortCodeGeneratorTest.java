package com.example.shortener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator(2654435761L);

    @Test
    void knownValueMatches() {
        assertEquals("02tdk01", generator.generate(1));
    }

    @Test
    void testTopBoundary() {
        assertEquals("zx6MFzz", generator.generate(62L*62L*62L*62L*62L*62L*62L - 1));
    }

    @Test 
    void testCodeRange() {
        HashSet<String> seenCodes = new HashSet<>();
        for (long i = 0; i < 1000000; i++) {
            String code = generator.generate(i);
            assertEquals(7, code.length(), "Code length should be 7");
            for (char c : code.toCharArray()) {
                assertTrue((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'), "Code should be base62");
            }
            assertTrue(seenCodes.add(code), "Generated code should be unique");
        }
        assertEquals(1000000, seenCodes.size(), "All generated codes should be unique");
    }

    @ParameterizedTest
    @ValueSource(longs = { /* your invalid K values */ -1, 2, 62L*62L*62L*62L*62L*62L*62L, 31, 62L*62L*62L*62L*62L*62L*31L })
    void invalidKIsRejected(long k) { 
        /* assertThrows here */
        assertThrows(IllegalArgumentException.class, () -> new ShortCodeGenerator(k));
    }
}