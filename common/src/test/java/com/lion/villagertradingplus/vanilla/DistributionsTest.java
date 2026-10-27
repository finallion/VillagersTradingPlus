package com.lion.villagertradingplus.vanilla;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DistributionsTest {

    private static Map<String, Integer> sample(long seed, double weightOfA) {
        Random random = new Random(seed);
        return Distributions.count(
                20000,
                () -> random.nextDouble() < weightOfA ? "a" : random.nextBoolean() ? "b" : "c",
                key -> List.of(key)
        );
    }

    @Test
    void samplesOfOneDistributionPass() {
        assertNull(Distributions.compare(sample(1, 0.5), sample(2, 0.5)));
    }

    @Test
    void shiftedWeightsFail() {
        assertNotNull(Distributions.compare(sample(1, 0.5), sample(2, 0.47)));
    }

    @Test
    void anOutcomeOnlyOneSideProducesFails() {
        Map<String, Integer> vanilla = sample(1, 0.5);
        Map<String, Integer> actual = sample(2, 0.5);
        actual.put("d", 30);
        assertNotNull(Distributions.compare(vanilla, actual));
    }
}
