package com.lion.villagertradingplus.tradeoffers;

public final class LevelCounts {

    private LevelCounts() {}

    /// Negative entries from edited or damaged saves would break the offer ranges, so they count as 0.
    public static int[] clamp(int[] counts) {
        if (counts == null) {
            return null;
        }

        int[] clamped = new int[counts.length];

        for (int index = 0; index < counts.length; index++) {
            clamped[index] = Math.max(0, counts[index]);
        }

        return clamped;
    }
}
