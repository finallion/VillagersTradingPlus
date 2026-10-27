package com.lion.villagertradingplus.compat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.lion.villagertradingplus.tradeoffers.LevelCounts;
import org.junit.jupiter.api.Test;

class LevelCountsTest {

    @Test
    void negativeEntriesBecomeZero() {
        assertArrayEquals(new int[] {0, 0, 3, 0}, LevelCounts.clamp(new int[] {-5, 0, 3, -1}));
    }

    @Test
    void validCountsStayTheSame() {
        int[] counts = {0, 2, 2, 1, 0, 0};
        int[] clamped = LevelCounts.clamp(counts);

        assertArrayEquals(counts, clamped);
        assertNotSame(counts, clamped);
    }

    @Test
    void missingCountsStayMissing() {
        assertNull(LevelCounts.clamp(null));
    }
}
