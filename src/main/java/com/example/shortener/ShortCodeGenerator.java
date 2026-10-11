package com.example.shortener;
import java.math.BigInteger;

class ShortCodeGenerator {
    private final long K;
    private static final BigInteger MODULUS = BigInteger.valueOf(62).pow(7);
    public ShortCodeGenerator(long K) {
        if (K % 2 == 0 || K <= 0 || K >= MODULUS.longValue() || BigInteger.valueOf(K).gcd(MODULUS).compareTo(BigInteger.ONE) != 0) {
            throw new IllegalArgumentException("K must be a positive integer less than 62^7 and coprime to 62^7");
        }
        this.K = K;
    }

    private BigInteger shuffle(BigInteger counter) {
        if (counter.compareTo(BigInteger.ZERO) < 0 || counter.compareTo(MODULUS) >= 0) {
            throw new IllegalArgumentException("Counter must be within the valid range (0 to 62^7 - 1)");
        }
        return (counter.multiply(BigInteger.valueOf(K))).mod(MODULUS);
    }

    private String encode(BigInteger counter) {
       
        // shuffle using formula (n × K) mod 62⁷, K is a odd number and not a multiple of 31
        counter = shuffle(counter);

        StringBuilder base62 = new StringBuilder();
        while (counter.compareTo(BigInteger.ZERO) > 0) {
            int remainder = counter.mod(BigInteger.valueOf(62)).intValue();
            if (remainder < 10) {
                base62.append((char) ('0' + remainder));
            } else if (remainder < 36) {
                base62.append((char) ('A' + remainder - 10));
            } else {
                base62.append((char) ('a' + remainder - 36));
            }
            counter = counter.divide(BigInteger.valueOf(62));
        }
        StringBuilder padding = new StringBuilder();
        while (padding.length() + base62.length() < 7) {
            padding.append('0');
        }
        return padding.append(base62.reverse()).toString();
    }

    public String generate(long counter) {
        return encode(BigInteger.valueOf(counter));
    }

    public static boolean isValidCode(String code) {
        if (code == null || code.length() != 7) {
            return false;
        }
        for (char c : code.toCharArray()) {
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) {
                return false;
            }
        }
        return true;
    }

}