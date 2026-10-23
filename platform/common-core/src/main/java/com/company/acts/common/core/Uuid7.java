package com.company.acts.common.core;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Генератор UUID версии 7 (RFC 9562): первые 48 бит — время в миллисекундах, поэтому ключи
 * упорядочены по времени создания и хорошо ложатся в индекс B-tree.
 */
public final class Uuid7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuid7() {
    }

    public static UUID next() {
        long millis = System.currentTimeMillis();
        long randA = RANDOM.nextInt(1 << 12);
        long msb = (millis << 16) | (0x7L << 12) | randA;
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
